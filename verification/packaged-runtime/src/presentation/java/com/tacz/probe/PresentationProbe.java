package com.tacz.probe;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.gui.ViewTuningScreen;
import com.tacz.guns.client.input.ViewTuningKey;
import com.tacz.guns.client.tuning.TuningProfiles;
import com.tacz.guns.client.tuning.TuningShare;
import com.tacz.guns.util.InputExtraCheck;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/** One real launcher identity per production JVM. Only files from the game's profile directory travel to the next run. */
public final class PresentationProbe implements FabricClientGameTest {
    private static final String UI = "gui.tacz.view_tuning.";
    private static final String TYPE = "TACZ-HUD2:DESIGNER,DOT_1,RING,ORIGINAL,1,FF00FF,16.00,8.00,3.00,1.00,100,2.00,255,0,6.00,3.00,300";
    private static final String EXACT = TYPE.replace(",RING,", ",LADDER,");
    private static final String ZERO = "TACZ-POS1:0.000,0.000,0.000", POS_A = "TACZ-POS1:0.237,-0.113,-0.330", POS_B = "TACZ-POS1:-0.500,0.000,0.250";

    @Override public void runTest(ClientGameTestContext ctx) {
        try { PackagedConnectionProbe.requireProduct(); } catch (IOException e) { throw new UncheckedIOException(e); }
        for (String id : new String[]{"cloth-config", "jei", "roughlyenoughitems", "iris", "sodium", "controllable", "player_animation_library"})
            check(!FabricLoader.getInstance().isModLoaded(id), "Unexpected optional mod " + id);
        String phase = System.getProperty("tacz.probe.presentation");
        boolean mouse = phase.equals("b");
        try (var world = ctx.worldBuilder().create()) {
            ctx.waitFor(mc -> mc.player != null && mc.getSingleplayerServer() != null && InputExtraCheck.isInGame(), 1200);
            ctx.runOnClient(mc -> {
                check(TuningProfiles.account().equals(UUID.fromString(System.getProperty("tacz.probe.owner"))), "Wrong launcher profile");
                mc.options.hideGui = false;
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                ViewTuningKey.KEY.setKey(mouse ? InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4)
                        : InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J));
                KeyMapping.resetMapping();
            });
            equip(ctx, world, "m320");
            switch (phase) {
                case "a", "b" -> {
                    ctx.runOnClient(mc -> check(TuningProfiles.current().gunKeys().isEmpty() && TuningProfiles.current().typeKeys().isEmpty(), "Inherited another account's rules"));
                    // Opening from Pause must keep its parent. Invalid imports must be all-or-nothing, and Cancel must not write.
                    ctx.setScreen(() -> new PauseScreen(true)); ctx.clickScreenButton(UI + "title");
                    ctx.waitFor(mc -> mc.isPaused() && mc.screen instanceof ViewTuningScreen);
                    check(copy(ctx).equals(ZERO), "Unexpected seed position");
                    paste(ctx, POS_A); paste(ctx, "TACZ-POS1:0.500,1.001,0.000");
                    check(copy(ctx).equals(POS_A), "Partial import of invalid position");
                    ctx.clickScreenButton("gui.cancel"); ctx.waitForScreen(PauseScreen.class); ctx.setScreen(() -> null);
                    open(ctx, mouse); check(copy(ctx).equals(ZERO), "Cancelled position leaked");
                    paste(ctx, mouse ? POS_B : POS_A);
                    if (!mouse) {
                        // Assign the type through the picker, then change the inherited ring to a ladder on this gun only.
                        button(ctx, "crosshair"); button(ctx, "page.rules"); button(ctx, "scope.type"); button(ctx, "page.share");
                        paste(ctx, TYPE); check(copy(ctx).equals(TYPE), "Type style didn't import");
                        button(ctx, "page.rules"); button(ctx, "scope.gun"); button(ctx, "page.style"); button(ctx, "shape.ring");
                        button(ctx, "page.share"); check(copy(ctx).equals(EXACT), "Exact rule didn't start from type");
                    }
                    button(ctx, "save"); ctx.waitForScreen(null);
                }
                case "a-return" -> {
                    open(ctx, false); check(copy(ctx).equals(POS_A), "B overwrote A's position");
                    button(ctx, "crosshair"); button(ctx, "page.rules"); button(ctx, "inherit");
                    button(ctx, "page.share"); check(copy(ctx).equals(TYPE), "Exact rule didn't reveal its parent");
                    ctx.clickScreenButton("gui.cancel"); ctx.waitForScreen(null);
                    ctx.runOnClient(mc -> check(EXACT.equals(TuningShare.CROSSHAIR.encode(TuningProfiles.forStack(mc.player.getMainHandItem()))), "Cancel deleted the persisted rule"));
                    verifyShapes(ctx, world);
                    verifyHandPreview(ctx);
                }
                default -> throw new AssertionError(phase);
            }
            System.out.println("PORT_PROFILE: " + phase + " launcher=" + ctx.computeOnClient(mc -> TuningProfiles.account())
                    + " position=" + ctx.computeOnClient(mc -> TuningShare.POSITION.encode(TuningProfiles.current().base())));
        }
    }

    private static void open(ClientGameTestContext ctx, boolean mouse) {
        ctx.waitFor(mc -> InputExtraCheck.isInGame() && !mc.isPaused());
        if (mouse) ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_4); else ctx.getInput().pressKey(ViewTuningKey.KEY);
        ctx.waitForScreen(ViewTuningScreen.class);
    }
    private static void equip(ClientGameTestContext ctx, TestSingleplayerContext world, String name) {
        var id = Identifier.fromNamespaceAndPath("tacz", name);
        world.getServer().runOnServer(s -> {
            var p = s.getPlayerList().getPlayers().getFirst();
            p.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(id).build());
            p.inventoryMenu.broadcastChanges();
        });
        ctx.waitFor(mc -> mc.player.getMainHandItem().getItem() instanceof IGun g && g.getGunId(mc.player.getMainHandItem()).equals(id));
        ctx.waitTicks(35);
    }
    private static void verifyShapes(ClientGameTestContext ctx, TestSingleplayerContext world) {
        ctx.waitFor(mc -> InputExtraCheck.isInGame()); ctx.waitTicks(12);
        int scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        var exact = frame(ctx, "exact");
        for (int i = 1; i <= 3; ++i) {
            int got = width(exact, scale, 4 + 8*i);
            check(Math.abs(got - (4 + 4*i)*scale) <= 1, "Exact HUD rung " + i + " width=" + got + " at scale " + scale);
        }
        // A registered gun with a visible hip ladder and no forced ADS crosshair. Exercise the bound action.
        ctx.runOnClient(mc -> check(!com.tacz.guns.api.TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).orElseThrow().isShowCrosshair(), "ADS control has a forced crosshair"));
        var aim = com.tacz.guns.client.input.AimKey.AIM_KEY;
        ctx.getInput().holdKey(aim);
        try {
            ctx.waitFor(mc -> com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1) > .99f);
            ctx.waitTicks(15);
            var ads = frame(ctx, "ads");
            for (int i = 1; i <= 3; i++) check(width(ads, scale, 4 + 8*i) == 0, "Hip HUD leaked into full ADS");
            check(handPixels(exact, ads) > 150, "ADS didn't change the held-item region");
        } finally { ctx.getInput().releaseKey(aim); }
        ctx.waitFor(mc -> com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1) < .01f);
        ctx.waitTicks(12);
        check(Math.abs(width(frame(ctx, "ads_exit"), scale, 12) - 8*scale) <= 1, "Hip ladder not restored on ADS exit");
        equip(ctx, world, "rpg7");
        var sibling = frame(ctx, "type");
        check(width(sibling, scale, 12) == 0 && width(sibling, scale, 0) >= 3*scale, "Type ring missing or used exact HUD");
        equip(ctx, world, "ak47");
        var base = frame(ctx, "default");
        check(width(base, scale, 0) == 0 && width(base, scale, 12) == 0, "Unrelated type inherited a HUD rule");
        System.out.println("PORT_HUD_FRAME: 3 exact rungs, sibling ring, unrelated default; GUI scale=" + scale);
    }
    private static void verifyHandPreview(ClientGameTestContext ctx) {
        // AK47 has a muzzle locator and a settled visible hand. Freeze the world (including animation and bob),
        // not the renderer; compare the untouched half of the actual framebuffer beside the editor panel.
        ctx.waitFor(mc -> !com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder().clientStateLock
                && com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(mc.player).getSynDrawCoolDown() == 0);
        ctx.setScreen(() -> new PauseScreen(true)); ctx.clickScreenButton(UI + "title");
        ctx.waitFor(mc -> mc.isPaused() && mc.screen instanceof ViewTuningScreen);
        check(copy(ctx).equals(POS_A), "Hand comparison did not start with A's saved XYZ");
        var moved = frame(ctx, "hand_saved");
        var at = ctx.computeOnClient(mc -> new org.joml.Vector3f(com.tacz.guns.client.renderer.item.GunItemRendererWrapper.muzzleRenderOffset));
        paste(ctx, ZERO); ctx.waitTicks(3);
        var idle = frame(ctx, "hand_zero_draft");
        var delta = ctx.computeOnClient(mc -> at.sub(com.tacz.guns.client.renderer.item.GunItemRendererWrapper.muzzleRenderOffset));
        int changed = handPixels(idle, moved);
        check(Math.abs(delta.x - .237) < .035 && Math.abs(delta.y + .113) < .035 && delta.z < -.08f && changed > 150,
                "Paused hand frame/locator did not follow XYZ: delta=" + delta + ", changed=" + changed);
        ctx.clickScreenButton("gui.cancel"); ctx.waitForScreen(PauseScreen.class); ctx.setScreen(() -> null);
        System.out.println("PORT_HAND_FRAME: paused AK47 saved/zero draft: delta=" + delta + ", changed=" + changed + "; real full ADS and HUD return captured");
    }
    private static int handPixels(BufferedImage a, BufferedImage b) {
        int changed = 0;
        for (int y = a.getHeight()/2; y < a.getHeight()-20; y++) for (int x = a.getWidth()/2; x < a.getWidth(); x++) {
            int p = a.getRGB(x,y), q = b.getRGB(x,y);
            if (Math.abs((p>>16 & 255)-(q>>16 & 255)) + Math.abs((p>>8 & 255)-(q>>8 & 255)) + Math.abs((p & 255)-(q & 255)) > 65) changed++;
        }
        return changed;
    }
    private static BufferedImage frame(ClientGameTestContext ctx, String name) {
        try { return ImageIO.read(ctx.takeScreenshot("profile_" + name).toFile()); } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private static int width(BufferedImage im, int scale, int dy) {
        int n = 0;
        for (int x = im.getWidth()/2 - 35*scale; x < im.getWidth()/2 + 35*scale; ++x) {
            int rgb = im.getRGB(x, im.getHeight()/2 + dy*scale);
            if ((rgb >> 16 & 255) > 210 && (rgb >> 8 & 255) < 45 && (rgb & 255) > 210) n++;
        }
        return n;
    }
    private static void button(ClientGameTestContext ctx, String key) { ctx.clickScreenButton(UI + key); }
    private static void paste(ClientGameTestContext ctx, String value) {
        ctx.runOnClient(mc -> mc.keyboardHandler.setClipboard(value)); button(ctx, "paste");
    }
    private static String copy(ClientGameTestContext ctx) {
        button(ctx, "copy"); return ctx.computeOnClient(mc -> mc.keyboardHandler.getClipboard());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
