package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.gui.ViewTuningScreen;
import com.tacz.guns.client.tuning.*;
import com.tacz.guns.config.client.ViewTuningConfig;
import com.tacz.guns.config.client.ViewTuningConfig.Axis;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.UUID;

import static com.tacz.guns.test.ViewTuningSmoke.*;

/** Change category and exact-ID bindings through the real editor; don't infer the silhouette from a config value. */
final class AccountHudSmoke {
    private static final String UI = "gui.tacz.view_tuning.";
    private static final String M320 = "tacz:m320", RPG7 = "tacz:rpg7";
    private static final String TYPE = "TACZ-HUD2:DESIGNER,DOT_1,RING,ORIGINAL,1,FF00FF,16.00,8.00,3.00,1.00,100,2.00,255,0,6.00,3.00,300";
    private static final String EXACT = TYPE.replace(",RING,", ",LADDER,");

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world, KeyMapping key, int scale) {
        var before = world.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getMainHandItem().copy());
        var baseCode = ctx.computeOnClient(mc -> TuningShare.CROSSHAIR.encode(TuningProfiles.current().base()));
        try {
            ctx.runOnClient(mc -> twoAccounts());
            equip(ctx, world, M320); open(ctx, key);
            ctx.clickScreenButton(UI + "scope.type");
            ctx.clickScreenButton(UI + "page.share"); paste(ctx, TYPE);
            ctx.clickScreenButton(UI + "page.rules"); ctx.clickScreenButton(UI + "scope.gun");
            ctx.runOnClient(mc -> field(mc, "search_hint").setValue(M320)); ctx.clickScreenButton(UI + "find");
            ctx.clickScreenButton(UI + "page.style"); ctx.clickScreenButton(UI + "shape.ring");
            ctx.clickScreenButton(UI + "page.share"); check(EXACT.equals(copy(ctx)), "Ladder not selectable or inherited the wrong category");
            ctx.clickScreenButton(UI + "page.rules"); ctx.takeScreenshot("tacz_view_tuning_m320_rule");
            ctx.clickScreenButton(UI + "save"); ctx.waitForScreen(null);
            ctx.waitFor(mc -> !mc.isPaused()); ctx.waitTicks(12);
            ctx.runOnClient(mc -> {
                var disk = fromDisk();
                check(code(disk.hud("rpg", M320)).equals(EXACT) && code(disk.hud("rpg", RPG7)).equals(TYPE)
                        && code(disk.hud("rifle", "tacz:ak47")).equals(baseCode) && disk.hud("rpg", M320).get(Axis.X.option) == .237,
                        "Base/type/gun isolation or shared account XYZ lost on disk");
            });
            var ladder = ClientProbe.read(ctx.takeScreenshot("tacz_view_tuning_m320_ladder"));
            for (int i = 1; i <= 3; i++) {
                int w = rowWidth(ladder, scale, 4 + 8 * i);
                check(Math.abs(w - (4 + 4*i)*scale) <= 1, "M320 ladder rung " + i + " width=" + w);
            }
            check(rowWidth(ladder, scale, -12) == 0 && rowWidth(ladder, scale, 16) == scale, "Missing ladder stem or spurious upper rung");
            equip(ctx, world, RPG7);
            var sibling = ClientProbe.read(ctx.takeScreenshot("tacz_view_tuning_rpg7_type"));
            check(rowWidth(sibling, scale, 12) == 0 && rowWidth(sibling, scale, 0) >= 3*scale,
                    "Sibling with same pack type incorrectly used the single-gun rule");
            equip(ctx, world, M320); open(ctx, key);
            ctx.clickScreenButton(UI + "inherit"); ctx.clickScreenButton("gui.cancel"); ctx.waitForScreen(null);
            ctx.runOnClient(mc -> check(code(TuningProfiles.forStack(mc.player.getMainHandItem())).equals(EXACT), "Cancel removed a saved rule"));
            open(ctx, key); ctx.clickScreenButton(UI + "inherit");
            ctx.clickScreenButton(UI + "page.share"); check(TYPE.equals(copy(ctx)), "Deleting the gun rule didn't reveal its parent");
            ctx.clickScreenButton(UI + "page.rules"); ctx.clickScreenButton(UI + "scope.type"); ctx.clickScreenButton(UI + "inherit");
            ctx.clickScreenButton(UI + "save"); ctx.waitForScreen(null);
            ctx.runOnClient(mc -> check(code(TuningProfiles.forStack(mc.player.getMainHandItem())).equals(baseCode) && fromDisk().gunKeys().isEmpty()
                    && fromDisk().typeKeys().isEmpty(), "Removing both overrides failed to restore base"));
            GunMod.LOGGER.info("PORT_ACCOUNT_HUD: A/B/A disk isolation, RPG class / M320 exact / inherit / Cancel, three graduated HUD rungs passed at {}x", scale);
        } finally { world.getServer().runOnServer(s -> s.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, before)); }
    }

    private static void open(ClientGameTestContext ctx, KeyMapping key) {
        ctx.getInput().pressKey(key); ctx.waitForScreen(ViewTuningScreen.class);
        ctx.clickScreenButton(UI + "crosshair"); ctx.clickScreenButton(UI + "page.rules");
    }
    private static void equip(ClientGameTestContext ctx, TestSingleplayerContext world, String id) {
        world.getServer().runOnServer(s -> {
            ItemStack stack = GunItemBuilder.create().setId(Identifier.parse(id)).build();
            var p = s.getPlayerList().getPlayers().getFirst(); p.setItemInHand(InteractionHand.MAIN_HAND, stack); p.inventoryMenu.broadcastChanges();
        });
        ctx.waitFor(mc -> mc.player.getMainHandItem().getItem() instanceof IGun g && id.equals(g.getGunId(mc.player.getMainHandItem()).toString()));
        ctx.waitTicks(35);
    }
    private static int rowWidth(BufferedImage im, int scale, int dy) {
        int count = 0, y = im.getHeight()/2 + dy*scale;
        for (int x = im.getWidth()/2 - 35*scale; x < im.getWidth()/2 + 35*scale; x++) {
            int color = im.getRGB(x,y);
            if ((color >> 16 & 255) > 210 && (color >> 8 & 255) < 45 && (color & 255) > 210) count++;
        }
        return count;
    }

    private static String code(TuningValues v) { return TuningShare.CROSSHAIR.encode(v); }
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }

    private static void twoAccounts() {
        try {
            var dir = Files.createTempDirectory("tacz-account-smoke");
            var legacy = new TuningValues.Draft(); legacy.set(Axis.Z.option, -.375);
            var store = new TuningProfileStore(dir, legacy);
            var a = UUID.randomUUID(); var b = UUID.randomUUID();
            var first = store.get(a).copy(); first.base().set(Axis.Z.option, -.75);
            check(TuningShare.CROSSHAIR.decode(TYPE, first.base()), "Bad HUD fixture");
            var exact = new TuningValues.Draft(); check(TuningShare.CROSSHAIR.decode(EXACT, exact), "Bad ladder fixture");
            first.setHud("rpg", M320, exact); check(store.save(a, first), "Account A failed to save");
            var second = store.get(b).copy();
            check(second.gunKeys().isEmpty() && second.base().get(Axis.Z.option) == -.375 && !code(second.base()).equals(TYPE), "A bled into B");
            second.base().set(Axis.Z.option, .25);
            check(!store.save(a, first) && store.save(b, second), "Stale owner accepted or B failed to save");
            var restart = new TuningProfileStore(dir, TuningValues.DEFAULTS);
            var a2 = restart.get(a);
            check(a2.base().get(Axis.Z.option) == -.75 && code(a2.hud("rpg", M320)).equals(EXACT)
                    && restart.get(b).base().get(Axis.Z.option) == .25 && restart.get(b).gunKeys().isEmpty(), "A/B/A roundtrip lost data");
            String damaged = "{\"version\":42}"; Files.writeString(dir.resolve(a + ".json"), damaged);
            restart.get(a); check(!restart.save(a, second) && Files.readString(dir.resolve(a + ".json")).equals(damaged), "Unknown profile overwritten");
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
