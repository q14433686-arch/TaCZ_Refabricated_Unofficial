package com.tacz.probe;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.resource.network.CommonNetworkCache;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.tacz.guns.init.ModItems;

/** The server JVM has its own game directory and no test mod. Only a real S2C install
 * can introduce these indexes in the consumer. Never read the dedicated server's files. */
public final class PackagedConnectionProbe implements FabricClientGameTest {
    private static final Identifier FIRST = Identifier.parse("net_probe:first"), SECOND = Identifier.parse("net_probe:second");
    private static final Identifier ADAPTER = Identifier.parse("tacz:riser_pistol_high");
    private static final CommonNetworkCache CACHE = CommonNetworkCache.INSTANCE;

    @Override public void runTest(ClientGameTestContext ctx) {
        var loader = FabricLoader.getInstance();
        try {
            Path jar = Path.of(System.getProperty("tacz.probe.jar"));
            if (loader.getModContainer("tacz").orElseThrow().getOrigin().getPaths().stream()
                    .noneMatch(p -> Files.isRegularFile(p) && sameFile(p, jar))) throw new AssertionError("TaCZ was not loaded from " + jar);
            String profile = System.getProperty("tacz.probe.viewer", "none");
            if (loader.isModLoaded("jei") != profile.equals("jei") || loader.isModLoaded("roughlyenoughitems") != profile.equals("rei"))
                throw new AssertionError("Wrong viewer combination: " + profile);
            for (String id : List.of("controllable", "player_animation_library", "iris", "sodium", "modmenu")) {
                if (loader.isModLoaded(id)) throw new AssertionError("Unexpected optional mod: " + id);
            }
            if (profile.equals("none") && loader.isModLoaded("cloth-config")) throw new AssertionError("Not a minimal mod set");
            var viewer = ViewerProbe.load(profile);
            Path ready = Path.of(System.getProperty("tacz.probe.ready"));
            connect(ctx);
            ctx.waitFor(mc -> online(mc) && CACHE.getGunIndex(FIRST) != null && CACHE.getSlotAdapterIndex(ADAPTER) != null
                    && TimelessAPI.getClientGunIndex(FIRST).isPresent(), 1200);
            if (ctx.computeOnClient(mc -> CACHE.getGunIndex(SECOND) != null)) throw new AssertionError("Stale reload fixture");
            // Native ServerGamePacketListener drops interaction packets until PlayerLoaded.
            // Registry sync can complete while the receiving-level screen is still open.
            ctx.waitFor(mc -> mc.getConnection().hasClientLoaded() && mc.screen == null, 1200);
            Files.createFile(ready.resolveSibling("joined"));
            expectGeneration(ctx, viewer, FIRST, SECOND);
            DedicatedCrafting.craft(ctx, 0);
            DedicatedCrafting.tableShort(ctx, FIRST, 0);
            Files.createFile(ready.resolveSibling("short-first"));
            DedicatedCrafting.table(ctx, FIRST, 0, viewer);
            Files.createFile(ready);
            ctx.waitFor(mc -> CACHE.getGunIndex(FIRST) == null && CACHE.getGunIndex(SECOND) != null && hasNamedStock(mc), 1200);
            expectGeneration(ctx, viewer, SECOND, FIRST);
            DedicatedCrafting.craft(ctx, 1);
            DedicatedCrafting.tableShort(ctx, SECOND, 1);
            Files.createFile(ready.resolveSibling("short-second"));
            DedicatedCrafting.table(ctx, SECOND, 1, viewer);
            var before = ctx.computeOnClient(mc -> CACHE.getGunIndex(SECOND));
            disconnect(ctx);
            ctx.waitFor(mc -> CACHE.getGunIndex(SECOND) == null && CACHE.getSlotAdapterIndex(ADAPTER) == null);
            connect(ctx);
            ctx.waitFor(mc -> online(mc) && CACHE.getGunIndex(SECOND) != null && CACHE.getGunIndex(SECOND) != before
                    && CACHE.getGunIndex(FIRST) == null && CACHE.getSlotAdapterIndex(ADAPTER) != null && hasNamedStock(mc), 1200);
            expectGeneration(ctx, viewer, SECOND, FIRST);
            ctx.waitFor(mc -> DedicatedCrafting.recipeBookHasTable(mc) && DedicatedCrafting.inventory(mc, ModItems.GUN_SMITH_TABLE) == 2
                    && DedicatedCrafting.inventory(mc, Items.OAK_LOG) == 0 && DedicatedCrafting.inventory(mc, Items.IRON_INGOT) == 0
                    && DedicatedCrafting.inventory(mc, Items.IRON_BLOCK) == 0 && DedicatedCrafting.shells(mc) == 10
                    && DedicatedCrafting.inventory(mc, Items.STICK) == 0 && DedicatedCrafting.inventory(mc, Items.FLINT) == 0);
            System.out.println("PORT_CRAFT: " + profile + " dedicated survival 3x3 and bench, one short and funded before and after reload; server confirmed inventory and recipe book on rejoin");
            ctx.takeScreenshot("tacz_distribution_rejoined");
            disconnect(ctx);
            System.out.println("PORT_DISTRIBUTION: production JAR, dedicated TCP join, server-only index replacement, disconnect and fresh rejoin with 7 named items passed");
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
    private static void expectGeneration(ClientGameTestContext ctx, ViewerProbe viewer, Identifier live, Identifier retired) {
        try {
            ctx.waitFor(mc -> CACHE.getTableRecipe(live) != null && CACHE.getTableRecipe(retired) == null
                    && (viewer == null || viewer.hasOrdinaryCrafting() && viewer.hasGun(live) && !viewer.hasGun(retired)
                    && viewer.hasWorkbenchRecipe(live) && !viewer.hasWorkbenchRecipe(retired)), 1200);
        } catch (AssertionError timeout) {
            String detail = ctx.computeOnClient(mc -> "cache live/retired=" + (CACHE.getTableRecipe(live) != null) + "/" + (CACHE.getTableRecipe(retired) != null)
                    + (viewer == null ? " (no viewer)" : ", ordinary=" + viewer.hasOrdinaryCrafting() + ", gun live/retired=" + viewer.hasGun(live) + "/" + viewer.hasGun(retired)
                    + ", bench live/retired=" + viewer.hasWorkbenchRecipe(live) + "/" + viewer.hasWorkbenchRecipe(retired) + "; " + viewer.detail()));
            throw new AssertionError("Viewer generation " + live + ": " + detail, timeout);
        }
        if (viewer != null) viewer.checkInstalled(ctx, live);
    }
    private static boolean sameFile(Path p, Path jar) {
        try { return Files.isSameFile(p, jar); } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
    private static boolean online(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.getSingleplayerServer() == null && mc.getConnection() != null
                && !mc.getConnection().getConnection().isMemoryConnection();
    }
    private static boolean hasNamedStock(Minecraft mc) {
        if (!online(mc)) return false;
        var inv = mc.player.getInventory();
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (stack.is(Items.PAPER) && stack.getHoverName().getString().equals("target receipt")) count += stack.getCount();
        }
        return count == 7;
    }
    private static void connect(ClientGameTestContext ctx) {
        String addr = "127.0.0.1:" + Integer.parseInt(System.getProperty("tacz.probe.port"));
        ctx.runOnClient(mc -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(addr),
                new ServerData("TaCZ target", addr, ServerData.Type.OTHER), false, null));
    }
    private static void disconnect(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            mc.level.disconnect(Component.literal("distribution test"));
            mc.disconnectWithSavingScreen();
        });
        ctx.waitFor(mc -> mc.level == null);
        ctx.setScreen(TitleScreen::new);
    }
}
