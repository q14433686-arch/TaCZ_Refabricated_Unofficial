package com.tacz.probe;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.util.ItemNbtUtils;
import me.shedaniel.rei.api.client.ClientHelper;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import me.shedaniel.rei.impl.client.ClientHelperImpl;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Diagnostic of the installed REI entry action, not a replacement give implementation.
 * The console independently counts the authoritative server inventory after each phase. */
public final class ReiGiveProbe implements FabricClientGameTest {
    private static final Identifier AK = Identifier.parse("tacz:ak47");
    private static final BlockPos TABLE = new BlockPos(9, 201, 8);
    private static boolean ak(ItemStack stack) { return stack.getItem() instanceof IGun gun && AK.equals(gun.getGunId(stack)); }
    private static EntryStack<?> entry() {
        if (PluginManager.areAnyReloading()) return null;
        return EntryRegistry.getInstance().getEntryStacks().filter(e -> e.getType() == VanillaEntryTypes.ITEM && ak(e.castValue()))
                .findFirst().orElse(null);
    }
    private static boolean hasGiveCommand(Minecraft mc) {
        return mc.getConnection().getCommands().getRoot().getChild("give") != null;
    }
    // Model the pinned REI backend independently of the observed response. JSON round trips
    // custom_data numerics by value, not by their original NBT width. Never modify the offer.
    private static ItemStack wireRoundTrip(Minecraft mc, ItemStack offered) {
        var ops = mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var json = ItemStack.OPTIONAL_CODEC.encodeStart(ops, offered).getOrThrow();
        return ItemStack.OPTIONAL_CODEC.parse(ops, JsonParser.parseString(json.toString())).getOrThrow();
    }
    private static int count(Minecraft mc, boolean typed) {
        int total = 0;
        for (var stack : mc.player.getInventory().getNonEquipmentItems()) {
            if (stack.getItem() instanceof IGun && (!typed || ak(stack))) total += stack.getCount();
        }
        return total;
    }
    private static void close(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> mc.player.closeContainer());
        ctx.waitFor(mc -> mc.player.containerMenu == mc.player.inventoryMenu && mc.screen == null);
    }
    private static ItemStack request(ClientGameTestContext ctx, boolean op, boolean matched) {
        DedicatedCrafting.open(ctx, TABLE, CraftingMenu.class);
        // Opening the first REI-backed screen can itself start an entry reload.
        ctx.waitFor(mc -> entry() != null, 1200);
        var offered = ctx.computeOnClient(mc -> {
            var chosen = entry();
            if (chosen == null || mc.player.hasInfiniteMaterials() || hasGiveCommand(mc) != op
                    || ClientHelperImpl.getInstance().canUsePackets() != matched || !mc.player.containerMenu.getCarried().isEmpty())
                throw new AssertionError("REI preflight: entry=" + (chosen != null) + ", creative=" + mc.player.hasInfiniteMaterials()
                        + ", give in server command tree=" + hasGiveCommand(mc) + " (expected " + op + "), REI permission hint="
                        + ClientHelperImpl.getInstance().hasOperatorPermission() + ", channels=" + ClientHelperImpl.getInstance().canUsePackets()
                        + " (expected " + matched + "), cursor=" + mc.player.containerMenu.getCarried());
            ItemStack stack = chosen.castValue();
            if (stack.getCount() != 1 || !ak(stack) || !ClientHelper.getInstance().tryCheatingEntry(chosen))
                throw new AssertionError("Installed REI handler did not request the registered AK47");
            return stack.copy();
        });
        if (op && matched) {
            ctx.waitFor(mc -> !mc.player.containerMenu.getCarried().isEmpty(), 1200);
            ctx.runOnClient(mc -> {
                var cursor = mc.player.containerMenu.getCarried();
                if (!ItemStack.matches(wireRoundTrip(mc, offered), cursor))
                    throw new AssertionError("Matched cursor differs from selected entry's JSON codec: " + ItemNbtUtils.saveItemStack(cursor));
            });
        }
        close(ctx); // REI's default GRAB puts the result on the cursor; closing returns it on the SERVER.
        DedicatedCrafting.open(ctx, TABLE, CraftingMenu.class); // ordered server round trip, also for refusal
        close(ctx);
        return offered;
    }

    @Override public void runTest(ClientGameTestContext ctx) {
        try {
            PackagedConnectionProbe.requireProduct();
            var root = Path.of(System.getProperty("tacz.probe.ready"));
            String backend = System.getProperty("tacz.probe.give");
            boolean matched = "matched".equals(backend);
            PackagedConnectionProbe.connect(ctx);
            ctx.waitFor(mc -> mc.player != null && mc.getConnection().hasClientLoaded() && mc.screen == null, 1200);
            Files.createFile(root.resolve("joined"));
            ctx.waitFor(mc -> mc.player.getMainHandItem().is(Items.STICK) && mc.level.getBlockState(TABLE).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)
                    && entry() != null, 1200);
            request(ctx, false, matched);
            ctx.runOnClient(mc -> { if (count(mc, false) != 0) throw new AssertionError("Non-operator received a gun"); });
            Files.createFile(root.resolve("refused"));
            ctx.waitFor(ReiGiveProbe::hasGiveCommand, 1200);
            var offered = request(ctx, true, matched);
            ctx.waitFor(mc -> count(mc, false) == 1);
            var received = ctx.computeOnClient(mc -> mc.player.getInventory().getNonEquipmentItems().stream()
                    .filter(s -> s.getItem() instanceof IGun).findFirst().orElseThrow().copy());
            boolean full = ItemStack.matches(offered, received);
            // Complete the vanilla control even if the REI backend lost components. Count and
            // log the authoritative inventory first; a diagnostic is not a claim that REI worked.
            var expected = ctx.computeOnClient(mc -> matched ? wireRoundTrip(mc, offered) : new ItemStack(offered.getItem()));
            boolean backendMatch = ItemStack.matches(expected, received);
            int typed = ak(received) ? 1 : 0;
            Files.createFile(root.resolve("received-" + typed));
            ctx.waitFor(mc -> Files.isRegularFile(root.resolve("control-ready")), 1200);
            ctx.runOnClient(mc -> mc.getConnection().sendCommand("give @s tacz:modern_kinetic_gun[minecraft:custom_data={GunId:\"tacz:ak47\"}] 1"));
            ctx.waitFor(mc -> count(mc, false) == 2 && count(mc, true) == typed + 1);
            Files.createFile(root.resolve("control"));
            ctx.waitFor(mc -> Files.isRegularFile(root.resolve("verified")), 1200);
            System.out.println("PORT_REI_GIVE: " + backend + ", non-op refused, selected=" + ItemNbtUtils.saveItemStack(offered)
                    + ", received=" + ItemNbtUtils.saveItemStack(received) + ", fullComponentMatch=" + full + ", backendComponentMatch=" + backendMatch
                    + ", explicit /give added exactly one AK47");
            PackagedConnectionProbe.disconnect(ctx);
            if (!backendMatch || typed != (matched ? 1 : 0))
                throw new AssertionError("Stack does not match the expected REI backend or selected variant");
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
