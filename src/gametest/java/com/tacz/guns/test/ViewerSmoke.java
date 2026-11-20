package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.init.ModItems;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

/** Each lane installs one real viewer. Never call the TACZ registration methods directly. */
interface ViewerSmoke {
    Identifier EXISTING = Identifier.parse("tacz:ak47");
    Identifier NEW = Identifier.parse("reload_smoke:creative_probe");

    boolean forward(Identifier id);
    boolean reverse(Identifier id);
    Set<Identifier> workbenchRecipes(Identifier table, @Nullable ItemStack outputFocus);
    void open(Identifier gun);
    boolean isOpen(Screen screen);

    static boolean isGun(ItemStack stack, Identifier id) {
        return stack.getItem() instanceof IGun gun && id.equals(gun.getGunId(stack));
    }

    /** A real output-focused recipe lookup, not the similarly named gun→ammo/attachment usages. */
    private static void checkWorkbenches(ClientGameTestContext ctx, ViewerSmoke viewer, boolean reloaded) {
        record Sample(String table, String prefix, int count, String product, ItemStack focus) {}
        var gunTable = Identifier.parse("tacz:gun_smith_table");
        ctx.waitFor(mc -> viewer.workbenchRecipes(gunTable, null).contains(Identifier.parse("tacz:gun/ak47")), 1200);
        ctx.runOnClient(mc -> {
            var cache = CommonNetworkCache.INSTANCE;
            var samples = List.of(
                    new Sample("gun_smith_table", "gun/", 70, "ak47", GunItemBuilder.create().setId(EXISTING).build()),
                    new Sample("attachment_workbench", "attachments/", 107, "scope_acog_ta31", AttachmentItemBuilder.create().setId(Identifier.parse("tacz:scope_acog_ta31")).build()),
                    new Sample("ammo_workbench", "ammo/", 25, "9mm", AmmoItemBuilder.create().setId(Identifier.parse("tacz:9mm")).build()));
            for (var sample : samples) {
                var tableId = Identifier.parse("tacz:" + sample.table());
                var table = cache.getBlockIndex(tableId);
                if (table == null || table.getData() != cache.getBlockData(tableId) || table.getFilter() != cache.getRecipeFilter(Identifier.parse("tacz:default")))
                    throw new AssertionError("Workbench is not bound to the received data/filter: " + tableId);
                var expected = cache.getAllTableRecipes().stream().map(Map.Entry::getKey)
                        .filter(id -> id.getNamespace().equals("tacz") && id.getPath().startsWith(sample.prefix())).collect(Collectors.toSet());
                if (expected.size() != sample.count()) throw new AssertionError("Wire count for " + sample.prefix() + ": " + expected.size());
                // The gun table's misc tab also has the base mod's painting (not one of the 70 pack guns).
                if (tableId.equals(gunTable)) expected.add(Identifier.parse("tacz:misc/blood_strike_1"));
                var displayed = viewer.workbenchRecipes(tableId, null).stream().filter(id -> id.getNamespace().equals("tacz")).collect(Collectors.toSet());
                if (!expected.equals(displayed)) {
                    var missing = new HashSet<>(expected);
                    missing.removeAll(displayed);
                    displayed.removeAll(expected);
                    throw new AssertionError(tableId + " missing=" + missing + " unexpected=" + displayed);
                }
                var recipeId = Identifier.parse("tacz:" + sample.prefix() + sample.product());
                var focused = viewer.workbenchRecipes(tableId, sample.focus()).stream()
                        .filter(id -> id.getNamespace().equals("tacz")).collect(Collectors.toSet());
                if (!focused.equals(Set.of(recipeId)))
                    throw new AssertionError("Output search for " + recipeId + " on " + tableId + " returned " + focused);
            }
            var ammoTable = Identifier.parse("tacz:ammo_workbench");
            var shell = AmmoItemBuilder.create().setId(Identifier.parse("tacz:12g")).build();
            var specialId = Identifier.parse("port_test:condition_true");
            var defaults = viewer.workbenchRecipes(ammoTable, shell);
            var specialist = viewer.workbenchRecipes(Identifier.parse("port_test:specialist"), shell);
            if (cache.getTableRecipe(specialId) == null || defaults.contains(specialId) || !specialist.contains(specialId)
                    || !defaults.contains(Identifier.parse("tacz:ammo/12g")) || specialist.contains(Identifier.parse("tacz:ammo/12g"))
                    || defaults.contains(Identifier.parse("reload_smoke:shells")) != reloaded)
                throw new AssertionError("Third-party or blacklisted recipe misclassified: default=" + defaults + ", specialist=" + specialist);
            GunMod.LOGGER.info("PORT_WORKBENCH_VIEWER: 70 gun, 107 attachment, 25 ammo recipes match wire IDs; three output searches and specialist blacklist passed (reloaded={})", reloaded);
        });
    }

    static void run(ClientGameTestContext ctx, String viewer) {
        var loader = FabricLoader.getInstance();
        if (loader.isModLoaded("jei") != viewer.equals("jei") || loader.isModLoaded("roughlyenoughitems") != viewer.equals("rei"))
            throw new AssertionError("Expected only the " + viewer + " runtime");
        ViewerSmoke api = switch (viewer) {
            case "jei" -> new JeiViewerSmoke();
            case "rei" -> new ReiViewerSmoke();
            default -> throw new IllegalArgumentException(viewer);
        };
        try (var world = ctx.worldBuilder().create()) {
            ctx.waitFor(mc -> CommonNetworkCache.INSTANCE.getGunIndex(EXISTING) != null && api.forward(EXISTING) && api.reverse(EXISTING), 1200);
            ctx.runOnClient(mc -> {
                if (api.forward(NEW) || api.reverse(NEW)) throw new AssertionError("New gun already in viewer before conversion");
            });
            // Prove discovery on the vanilla recipe-book channel, not just a custom viewer category.
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().getInventory()
                    .add(new ItemStack(Items.IRON_BLOCK)));
            ctx.waitFor(mc -> mc.player.getRecipeBook().getCollections().stream().flatMap(c -> c.getRecipes().stream())
                    .flatMap(e -> e.display().result().resolveForStacks(SlotDisplayContext.fromLevel(mc.level)).stream())
                    .anyMatch(stack -> stack.is(ModItems.GUN_SMITH_TABLE)), 300);
            if (api instanceof ReiViewerSmoke rei) rei.checkLateSync(ctx, false);
            checkWorkbenches(ctx, api, false);
            ConversionReloadSmoke.run(ctx, world);
            world.getClientLevel().waitForChunksRender();
            ctx.waitFor(mc -> mc.gui.screen() == null && mc.gui.overlay() == null && api.forward(NEW) && api.reverse(NEW), 1200);
            if (api instanceof ReiViewerSmoke rei) rei.checkLateSync(ctx, true);
            checkWorkbenches(ctx, api, true);
            Path background = ctx.takeScreenshot("tacz_world_before_" + viewer);
            // Match the normal player entry point, not an in-flight join/loading screen.
            ctx.getInput().pressKey(options -> options.keyInventory);
            ctx.waitForScreen(InventoryScreen.class);
            ctx.runOnClient(mc -> api.open(NEW));
            ctx.waitFor(mc -> api.isOpen(mc.gui.screen()));
            ctx.getInput().setCursorPos(2, 2); // keep the tooltip off the recipe
            ctx.waitTicks(4);
            requireVisibleScreen(ctx, api, viewer);
            var page = ctx.takeScreenshot("tacz_viewer_" + viewer);
            requireVisibleScreen(ctx, api, viewer);
            requirePanelPixels(background, page);
            ctx.setScreen(() -> null);
            GunMod.LOGGER.info("PORT_VIEWER: {} showed the reloaded gun; both query directions changed", viewer);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void requireVisibleScreen(ClientGameTestContext ctx, ViewerSmoke api, String viewer) {
        ctx.runOnClient(mc -> {
            if (!api.isOpen(mc.gui.screen()) || mc.gui.overlay() != null)
                throw new AssertionError(viewer + " recipe page lost during capture: screen=" + mc.gui.screen() + ", overlay=" + mc.gui.overlay());
        });
    }

    /** Both pinned default GUIs draw a large neutral panel. A nonempty PNG of sky/HUD is not a recipe screenshot. */
    private static void requirePanelPixels(Path world, Path page) throws IOException {
        var a = ImageIO.read(world.toFile());
        var b = ImageIO.read(page.toFile());
        if (a == null || b == null || a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight())
            throw new AssertionError("Incomparable viewer screenshots");
        int panel = 0, samples = 0;
        for (int y = b.getHeight()/4; y < 3*b.getHeight()/4; y += 2) {
            for (int x = b.getWidth()/3; x < 2*b.getWidth()/3; x += 2) {
                int rgb = b.getRGB(x, y), before = a.getRGB(x, y);
                int r = rgb >>> 16 & 255, g = rgb >>> 8 & 255, blue = rgb & 255;
                int min = Math.min(r, Math.min(g, blue)), max = Math.max(r, Math.max(g, blue));
                int delta = Math.abs(r - (before >>> 16 & 255)) + Math.abs(g - (before >>> 8 & 255)) + Math.abs(blue - (before & 255));
                if (min >= 24 && max <= 240 && max-min <= 6 && delta >= 72) panel++;
                samples++;
            }
        }
        if (samples == 0 || panel * 8 < samples) throw new AssertionError("Viewer panel not visible: " + panel + "/" + samples);
        GunMod.LOGGER.info("PORT_VIEWER: {} of {} central samples show the recipe panel against the world control", panel, samples);
    }
}
