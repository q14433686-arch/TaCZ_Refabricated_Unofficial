package com.tacz.guns.test;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.gui.ViewTuningScreen;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.input.GunInputHandler;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.client.tuning.TuningValues;
import com.tacz.guns.client.tuning.ViewmodelPosition;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.client.ViewTuningConfig;
import com.tacz.guns.config.client.ViewTuningConfig.Axis;
import com.tacz.guns.config.client.ViewTuningConfig.Measure;
import com.tacz.guns.config.client.ViewTuningConfig.Mode;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;

/** Paused live hand, native widgets, disk readback and final HUD pixels with both Mesa and Iris. */
final class ViewTuningSmoke {
    private static final String UI = "gui.tacz.view_tuning.";

    static void run(ClientGameTestContext ctx) {
        var key = GunInputHandler.VIEW_TUNING_KEY;
        var binding = ctx.computeOnClient(mc -> KeyMappingHelper.getBoundKeyOf(key));
        var camera = ctx.computeOnClient(mc -> mc.options.getCameraType());
        var previous = ctx.computeOnClient(mc -> {
            var backup = new TuningValues.Draft();
            for (Axis axis : Axis.values()) remember(backup, axis.option);
            for (Measure measure : Measure.values()) remember(backup, measure.option);
            for (var option : List.of(ViewTuningConfig.MODE, ViewTuningConfig.RGB, ViewTuningConfig.DOT,
                    ViewTuningConfig.OUTLINE, RenderConfig.CROSSHAIR_TYPE)) remember(backup, option);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            key.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_J));
            KeyMapping.resetMapping();
            return backup;
        });
        try {
            ctx.setScreen(() -> new PauseScreen(true));
            ctx.clickScreenButton(UI + "title");
            ctx.waitFor(mc -> mc.isPaused() && mc.gui.screen() instanceof ViewTuningScreen);
            BufferedImage idle = frame(ctx, "idle");
            Vector3f before = muzzle(ctx);
            ctx.runOnClient(mc -> {
                var x = field(mc, "axis.x");
                slider(mc, "axis.x", .75); // use the real slider's click path; next, exercise typed precision
                check(x.getValue().equals("0.500"), "X field did not follow slider: " + x.getValue());
                for (String bad : List.of("-", "NaN", "1.001")) {
                    x.setValue(bad);
                    check(!saveButton(mc).active, "Save enabled with invalid axis: " + bad);
                }
                x.setValue("0.237"); field(mc, "axis.y").setValue("-.113"); field(mc, "axis.z").setValue("-.330");
                check(saveButton(mc).active && Axis.X.option.get() == 0d, "Live preview wrote through to config");
                // Halfway contains half the offset; overshooting either full ADS or full refit leaves the exact authored matrix.
                for (float[] state : new float[][]{{.5f, 0, .5f}, {0, .5f, .5f}, {1.01f, 0, 0}, {0, 1f, 0}}) {
                    Matrix4f authored = new Matrix4f().translate(.2f, -.1f, .4f).rotateXYZ(.1f, .3f, .6f);
                    Matrix4f result = new Matrix4f(authored);
                    ViewmodelPosition.apply(result, state[0], state[1]);
                    authored.m30(authored.m30() - .237f * state[2]).m31(authored.m31() + .113f * state[2])
                            .m32(authored.m32() - .330f * state[2]);
                    check(authored.equals(result, .00001f), "Wrong hip/ADS/refit interpolation");
                }
            });
            BufferedImage moved = frame(ctx, "moved");
            Vector3f delta = muzzle(ctx).sub(before);
            check(Math.abs(delta.x - .237) < .035 && Math.abs(delta.y + .113) < .035 && delta.z < -.08f,
                    "Actual hand muzzle did not follow right/down/away in paused editor: " + delta);
            int handPixels = changedInHand(idle, moved);
            check(handPixels > 150, "Only the editor changed, not the held gun: " + handPixels);
            ctx.clickScreenButton("gui.cancel"); ctx.waitForScreen(PauseScreen.class);
            ctx.runOnClient(mc -> check(TuningValues.visible().get(Axis.X.option) == 0d, "Cancelled draft leaked"));
            ctx.clickScreenButton(UI + "title");
            ctx.runOnClient(mc -> field(mc, "axis.x").setValue(".237"));
            ctx.clickScreenButton(UI + "crosshair");
            ctx.clickScreenButton(UI + "mode.preset");
            ctx.runOnClient(mc -> {
                var rgb = field(mc, "rgb"); rgb.setValue("#XY0101");
                check(!saveButton(mc).active, "Save enabled for malformed RGB");
                rgb.setValue("#FF00FF");
                slider(mc, "opacity", 1); slider(mc, "thickness", 2d / 7);
            });
            frame(ctx, "designer");
            ctx.clickScreenButton(UI + "save"); ctx.waitForScreen(PauseScreen.class);
            ctx.runOnClient(mc -> {
                var path = FabricLoader.getInstance().getConfigDir().resolve("tacz-client.toml");
                try (var file = CommentedFileConfig.builder(path).build()) {
                    file.load();
                    check(Objects.equals(file.get(Axis.X.option.getPath()), .237d)
                            && Objects.equals(file.get(ViewTuningConfig.MODE.getPath()), "DESIGNER")
                            && Objects.equals(file.get(ViewTuningConfig.RGB.getPath()), 0xFF00FF)
                            && Objects.equals(file.get(Measure.THICKNESS.option.getPath()), 3), "Tuning not persisted to " + path);
                }
            });
            ctx.setScreen(() -> null); ctx.waitTicks(8);
            int scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
            // 4 arms of length 4, thickness 3, plus 3x3 center. Bounding box = 3 + 2*(4+2).
            int designer = assertHud(frame(ctx, "hip"), scale, 57, 15, 15);
            ctx.getInput().holdKey(AimKey.AIM_KEY);
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1) > .99f);
            assertHud(frame(ctx, "ads"), scale, 0, 0, 0);
            ctx.getInput().releaseKey(AimKey.AIM_KEY);
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1) < .01f);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            assertHud(frame(ctx, "third_person"), scale, 0, 0, 0);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            ctx.getInput().pressKey(key); ctx.waitForScreen(ViewTuningScreen.class); // rebound, not the Cloth config key
            ctx.clickScreenButton(UI + "crosshair"); ctx.clickScreenButton(UI + "mode.designer");
            ctx.runOnClient(mc -> slider(mc, "size", (32d - 4) / (64 - 4)));
            ctx.clickScreenButton(UI + "save"); ctx.waitForScreen(null);
            // The test resource pack overrides the slot with a 32x32 texture, six opaque centered columns.
            int png = assertHud(frame(ctx, "png"), scale, 6 * 32, 6, 32);
            ctx.getInput().pressKey(key); ctx.waitForScreen(ViewTuningScreen.class);
            ctx.clickScreenButton(UI + "crosshair"); ctx.clickScreenButton(UI + "reset");
            ctx.runOnClient(mc -> check(TuningValues.visible().get(ViewTuningConfig.MODE) == Mode.PRESET
                    && TuningValues.visible().get(Axis.X.option) == .237d, "Reset affected wrong page"));
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE); ctx.waitForScreen(null);
            ctx.runOnClient(mc -> check(ViewTuningConfig.MODE.get() == Mode.PNG, "Escape saved the reset"));
            GunMod.LOGGER.info("PORT_VIEW_TUNING: live muzzle delta={}, changed hand pixels={}, HUD designer/PNG={}/{} px at {}x, ADS and F5=0; disk, reset, cancel and rebound key passed",
                    delta, handPixels, designer, png, scale);
        } finally {
            ctx.getInput().releaseKey(AimKey.AIM_KEY);
            ctx.setScreen(() -> null);
            ctx.runOnClient(mc -> {
                previous.save(); mc.options.setCameraType(camera); key.setKey(binding); KeyMapping.resetMapping();
            });
        }
    }

    private static <T> void remember(TuningValues.Draft backup, ConfigValue<T> option) {
        backup.set(option, option.get());
        option.set(option.getDefault());
    }

    private static EditBox field(Minecraft mc, String key) {
        return mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(b -> b.getMessage().equals(Component.translatable(UI + key))).findFirst().orElseThrow();
    }

    private static Button saveButton(Minecraft mc) {
        // Vanilla supplies a differently styled Component when inactive. Match the visible label as Fabric does.
        String label = Component.translatable(UI + "save").getString();
        return mc.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }

    private static void slider(Minecraft mc, String key, double fraction) {
        var label = Component.translatable(UI + key).getString();
        var slider = mc.gui.screen().children().stream().filter(AbstractSliderButton.class::isInstance)
                .map(AbstractSliderButton.class::cast).filter(s -> s.getMessage().getString().startsWith(label)).findFirst().orElseThrow();
        slider.onClick(new MouseButtonEvent(slider.getX() + 4 + fraction * (slider.getWidth() - 8), slider.getY() + 5,
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
    }

    private static Vector3f muzzle(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> new Vector3f(GunItemRendererWrapper.muzzleRenderOffset));
    }

    private static BufferedImage frame(ClientGameTestContext ctx, String name) {
        return SnowstormMaterialSmoke.read(ctx.takeScreenshot("tacz_view_tuning_" + name));
    }

    private static int changedInHand(BufferedImage a, BufferedImage b) {
        int count = 0;
        for (int y = a.getHeight()/2; y < a.getHeight() - 20; y++) for (int x = a.getWidth()/2; x < a.getWidth(); x++) {
            int p = a.getRGB(x, y), q = b.getRGB(x, y);
            if (Math.abs((p >> 16 & 255) - (q >> 16 & 255)) + Math.abs((p >> 8 & 255) - (q >> 8 & 255))
                    + Math.abs((p & 255) - (q & 255)) > 65) count++;
        }
        return count;
    }

    private static int assertHud(BufferedImage image, int scale, int area, int width, int height) {
        int cx = image.getWidth()/2, cy = image.getHeight()/2, r = 38 * scale;
        int count = 0, x0 = cx+r, y0 = cy+r, x1 = cx-r, y1 = cy-r;
        for (int y = cy-r; y <= cy+r; y++) for (int x = cx-r; x <= cx+r; x++) {
            int p = image.getRGB(x, y);
            if ((p >> 16 & 255) > 210 && (p >> 8 & 255) < 45 && (p & 255) > 210) {
                count++; x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
            }
        }
        if (area == 0) check(count == 0, "HUD reticle escaped its visibility gate: " + count);
        else {
            int expected = area * scale * scale;
            check(Math.abs(count-expected) <= Math.max(2, expected/12) && Math.abs(x1-x0+1-width*scale) <= 1
                    && Math.abs(y1-y0+1-height*scale) <= 1 && Math.abs(x0+x1-2*cx) <= 3*scale
                    && Math.abs(y0+y1-2*cy) <= 3*scale, "Wrong centered reticle: " + count + "/" + expected
                    + " px, [" + x0 + "," + y0 + ".." + x1 + "," + y1 + "] at " + scale + "x");
        }
        return count;
    }

    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
