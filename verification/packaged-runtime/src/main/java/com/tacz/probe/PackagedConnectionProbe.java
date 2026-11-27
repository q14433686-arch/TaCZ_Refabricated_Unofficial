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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** No test-only packets and no access to the dedicated server's game directory. */
public final class PackagedConnectionProbe implements FabricClientGameTest {
    private static final Identifier FIRST = Identifier.parse("net_probe:first"), SECOND = Identifier.parse("net_probe:second");
    private static final Identifier WRONG = Identifier.parse("net_probe:wrong_version");
    private static final CommonNetworkCache CACHE = CommonNetworkCache.INSTANCE;

    @Override public void runTest(ClientGameTestContext ctx) {
        try {
            var loader = FabricLoader.getInstance();
            requireProduct();
            String mode = System.getProperty("tacz.probe.viewer", "none");
            if (loader.isModLoaded("jei") != mode.equals("jei") || loader.isModLoaded("roughlyenoughitems") != mode.equals("rei")
                    || loader.isModLoaded("cloth-config") != mode.equals("rei")) throw new AssertionError("Wrong optional mods: " + mode);
            var viewer = ViewerProbe.load(mode);
            for (String id : List.of("controllable", "iris", "sodium", "modmenu", "player_animation_library")) {
                if (loader.isModLoaded(id)) throw new AssertionError("Unexpected optional mod: " + id);
            }
            connect(ctx);
            expect(ctx, FIRST, 111, SECOND);
            ctx.runOnClient(mc -> { checkIndexes(); checkServerStanceConfig(); });
            var ready = Path.of(System.getProperty("tacz.probe.ready"));
            if (ctx.computeOnClient(mc -> table("shells") != null)) throw new AssertionError("Stale converted recipe before reload");
            ctx.waitFor(mc -> mc.getConnection().hasClientLoaded() && mc.screen == null, 1200);
            Files.createFile(ready.resolveSibling("joined"));
            observeViewer(ctx, viewer, FIRST, SECOND);
            DedicatedCrafting.craft(ctx, 0);
            DedicatedCrafting.tableShort(ctx, FIRST, 0);
            Files.createFile(ready.resolveSibling("short-first"));
            DedicatedCrafting.table(ctx, FIRST, 0, viewer);
            Files.createFile(ready);
            expect(ctx, SECOND, 222, FIRST);
            ctx.runOnClient(mc -> { checkConvertedTable(); checkIndexes(); });
            observeViewer(ctx, viewer, SECOND, FIRST);
            DedicatedCrafting.craft(ctx, 1);
            DedicatedCrafting.tableShort(ctx, SECOND, 1);
            Files.createFile(ready.resolveSibling("short-second"));
            DedicatedCrafting.table(ctx, SECOND, 1, viewer);
            Files.createFile(ready.resolveSibling("converter-reloaded"));
            ctx.waitFor(mc -> {
                for (var stack : mc.player.getInventory().getNonEquipmentItems()) {
                    if (stack.is(net.minecraft.world.item.Items.STICK) && com.tacz.guns.util.ItemNbtUtils.getTag(stack).getByteOr("delivered", (byte) 0) == 9) return true;
                }
                return false;
            }, 1200);
            var stock = mode.equals("none") ? new PersistentInventoryProbe() : null;
            if (stock != null) {
                var scripted = CACHE.getGunIndex(Identifier.parse("port_test:scripted"));
                if (scripted == null || !scripted.hasUnloadAmmoScript() || !scripted.canUnloadAmmo() || scripted.getScript() != null)
                    throw new AssertionError("Missing server-derived unload capability, or client received executable Lua");
                stock.deposit(ctx);
            }
            var exiting = ctx.computeOnClient(mc -> {
                var machine = new com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine<>(
                        new com.tacz.guns.api.client.animation.AnimationController(List.of(), (node, type) -> null));
                machine.setExitingTime(60_000);
                if (machine.getExitingTime() <= com.tacz.guns.util.time.GunTime.nowMillis())
                    throw new AssertionError("New animation exit deadline was not active");
                return machine;
            });
            long oldEpoch = com.tacz.guns.util.time.GunTime.epoch();
            var previous = ctx.computeOnClient(mc -> CACHE.getGunIndex(SECOND));
            disconnect(ctx);
            ctx.waitFor(mc -> CACHE.getGunIndex(SECOND) == null && TimelessAPI.getClientGunIndex(SECOND).isEmpty());
            if (com.tacz.guns.util.time.GunTime.epoch() <= oldEpoch) throw new AssertionError("Left play but did not invalidate pending game timers");
            if (ctx.computeOnClient(mc -> exiting.getExitingTime()) != -1) throw new AssertionError("Disconnected animation retained an exit deadline");
            System.out.println("PORT_CLOCK_EPOCH: real disconnect invalidated the pending animation exit deadline");
            if (mode.equals("rei")) System.out.println("PORT_REI: first connection closed; starting rejoin");
            if (stock != null) {
                Files.createFile(ready.resolveSibling("restart-requested"));
                ctx.waitFor(mc -> Files.exists(ready.resolveSibling("restart-ready")), 12000);
            }
            connect(ctx);
            expect(ctx, SECOND, 222, FIRST);
            ctx.runOnClient(mc -> { checkConvertedTable(); checkIndexes(); checkServerStanceConfig(); });
            System.out.println("PORT_SERVER_CONFIG: actual FCAP TCP sync applied server-only CrawlMaxUpAngle=12/CrawlMaxDownAngle=7/UseHipFireInaccuracyWhileAirborne=true on both joins; no test payload or local client mutation");
            observeViewer(ctx, viewer, SECOND, FIRST);
            ctx.waitFor(mc -> DedicatedCrafting.recipeBookHasTable(mc) && DedicatedCrafting.shells(mc) == 10
                    && DedicatedCrafting.inventory(mc, com.tacz.guns.init.ModItems.GUN_SMITH_TABLE) == 2
                    && DedicatedCrafting.inventory(mc, net.minecraft.world.item.Items.OAK_LOG) == 0
                    && DedicatedCrafting.inventory(mc, net.minecraft.world.item.Items.IRON_INGOT) == 0
                    && DedicatedCrafting.inventory(mc, net.minecraft.world.item.Items.IRON_BLOCK) == 0
                    && DedicatedCrafting.inventory(mc, net.minecraft.world.item.Items.FLINT) == 0
                    && DedicatedCrafting.inventory(mc, net.minecraft.world.item.Items.STICK) == 1, 1200); // tagged loot only
            if (stock != null) stock.recover(ctx);
            System.out.println("PORT_CRAFT: " + mode + " survival 3x3, recipe book and two short/funded table settlements confirmed on rejoin");
            if (ctx.computeOnClient(mc -> CACHE.getGunIndex(SECOND) == previous)) throw new AssertionError("Reused disconnected cache");
            disconnect(ctx);
            if (stock != null) checkPausedClock(ctx);
            System.out.println("PORT_TCP: directory winner on join, ZIP winner after reload, retired index absent, cache cleared and fresh on rejoin");
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static void checkServerStanceConfig() {
        if (com.tacz.guns.config.sync.SyncConfig.CRAWL_MAX_UP_ANGLE.get() != 12d
                || com.tacz.guns.config.sync.SyncConfig.CRAWL_MAX_DOWN_ANGLE.get() != 7d
                || !com.tacz.guns.config.sync.SyncConfig.USE_HIP_FIRE_INACCURACY_WHILE_AIRBORNE.get())
            throw new AssertionError("Client did not receive nondefault server stance config through FCAP: up="
                    +com.tacz.guns.config.sync.SyncConfig.CRAWL_MAX_UP_ANGLE.get()+" down="
                    +com.tacz.guns.config.sync.SyncConfig.CRAWL_MAX_DOWN_ANGLE.get()+" airborne="
                    +com.tacz.guns.config.sync.SyncConfig.USE_HIP_FIRE_INACCURACY_WHILE_AIRBORNE.get());
    }

    private static void checkPausedClock(ClientGameTestContext ctx) {
        try (var world = ctx.worldBuilder().create()) {
            ctx.waitFor(mc -> mc.getSingleplayerServer() != null && mc.player != null && mc.screen == null);
            ctx.setScreen(() -> new net.minecraft.client.gui.screens.PauseScreen(true));
            ctx.waitFor(mc -> mc.isPaused() && !com.tacz.guns.util.time.GunTime.shouldTick());
            long frozen = com.tacz.guns.util.time.GunTime.nowNanos();
            long wall = System.nanoTime();
            ctx.waitFor(mc -> System.nanoTime() - wall > 150_000_000L);
            if (com.tacz.guns.util.time.GunTime.nowNanos() != frozen) throw new AssertionError("Pause screen advanced the action clock");
            ctx.setScreen(() -> null);
            ctx.waitFor(mc -> !mc.isPaused() && com.tacz.guns.util.time.GunTime.nowNanos() > frozen);
            System.out.println("PORT_CLOCK: disconnect advanced epoch; integrated pause froze elapsed time across real frames, resume advanced it");
        }
    }

    private static void observeViewer(ClientGameTestContext ctx, ViewerProbe viewer, Identifier live, Identifier retired) {
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
    private static void expect(ClientGameTestContext ctx, Identifier live, int sort, Identifier absent) {
        ctx.waitFor(mc -> online(mc) && CACHE.getGunIndex(live) != null && CACHE.getGunIndex(live).getSort() == sort
                && TimelessAPI.getClientGunIndex(live).isPresent() && CACHE.getGunIndex(absent) == null
                && TimelessAPI.getClientGunIndex(absent).isEmpty() && CACHE.getGunIndex(WRONG) == null, 1200);
    }

    private static com.tacz.guns.resource.pojo.data.recipe.TableRecipe table(String path) {
        return CACHE.getTableRecipe(Identifier.fromNamespaceAndPath("convert_probe", path));
    }

    private static void checkIndexes() {
        var remote = CACHE.getAllSlotAdapters().stream().filter(e -> e.getKey().getNamespace().equals("tacz")).toList();
        if (remote.size() != 5 || !remote.stream().allMatch(e -> TimelessAPI.getClientSlotAdapterIndex(e.getKey()).isPresent()))
            throw new AssertionError("Default slot adapters missing after extended sync: " + remote);
        if (TimelessAPI.getAllClientGunIndex().stream().filter(e -> e.getKey().getNamespace().equals("tacz")).count() != 70)
            throw new AssertionError("Incomplete 1.2 client gun catalog");
        if (com.tacz.guns.api.item.gun.AbstractGunItem.fillItemCategory(com.tacz.guns.api.item.GunTabType.DMR).isEmpty())
            throw new AssertionError("DMR tab empty");
    }

    private static void checkConvertedTable() {
        var shells = table("shells");
        if (shells == null || table("off") != null || table("unsupported") != null) throw new AssertionError("Wrong reloaded table set");
        shells.getResult().init();
        var stack = shells.getResult().getResult();
        var custom = com.tacz.guns.util.ItemNbtUtils.getTag(stack);
        if (!(stack.getItem() instanceof com.tacz.guns.api.item.IAmmo ammo) || !ammo.getAmmoId(stack).equals(Identifier.parse("tacz:12g"))
                || stack.getCount() != 5 || custom.getByteOr("reloaded", (byte) 0) != 7 || !custom.getCompoundOrEmpty("AttachmentSCOPE").contains("components"))
            throw new AssertionError("Lost builder ID, count or typed/nested DFU data");
        var stick = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        var input = shells.getMaterials().getFirst();
        if (input.getCount() != 2 || input.getIngredientOrThrow().test(stick)) throw new AssertionError("Widened converted predicate");
        com.tacz.guns.util.ItemNbtUtils.updateTag(stick, tag -> tag.putByte("marked", (byte) 1));
        if (!input.getIngredientOrThrow().test(stick)) throw new AssertionError("Marked ingredient rejected");
    }

    private static boolean online(Minecraft mc) {
        return mc.level != null && mc.player != null && mc.getSingleplayerServer() == null && mc.getConnection() != null
                && !mc.getConnection().getConnection().isMemoryConnection();
    }

    static void requireProduct() throws IOException {
        var loader = FabricLoader.getInstance();
        var origins = loader.getModContainer("tacz").orElseThrow().getOrigin().getPaths();
        if (loader.isDevelopmentEnvironment() || !"intermediary".equals(loader.getMappingResolver().getCurrentRuntimeNamespace())
                || origins.size() != 1 || !Files.isSameFile(origins.getFirst(), Path.of(System.getProperty("tacz.probe.jar")))) {
            throw new AssertionError("Not running the audited intermediary JAR: " + origins);
        }
    }

    static void connect(ClientGameTestContext ctx) {
        String addr = "127.0.0.1:" + Integer.parseInt(System.getProperty("tacz.probe.port"));
        ctx.runOnClient(mc -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(addr),
                new ServerData("Pack probe", addr, ServerData.Type.OTHER), false, null));
    }

    static void disconnect(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            mc.level.disconnect(Component.literal("pack sync test"));
            mc.disconnectWithSavingScreen();
        });
        ctx.waitFor(mc -> mc.level == null);
        ctx.setScreen(TitleScreen::new);
    }
}
