package com.tacz.guns.test;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.render.scope.ScopePipRenderer;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.gui.ViewTuningScreen;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.input.GunInputHandler;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.client.tuning.TuningValues;
import com.tacz.guns.client.tuning.TuningProfile;
import com.tacz.guns.client.tuning.TuningProfileStore;
import com.tacz.guns.client.tuning.TuningProfiles;
import com.tacz.guns.client.tuning.ViewmodelPosition;
import com.tacz.guns.compat.shouldersurfing.ShoulderSurfingCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.client.ViewTuningConfig;
import com.tacz.guns.config.client.ViewTuningConfig.Axis;
import com.tacz.guns.config.client.ViewTuningConfig.Dimension;
import com.tacz.guns.config.client.ViewTuningConfig.Measure;
import com.tacz.guns.config.client.ViewTuningConfig.Mode;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import com.tacz.guns.util.InputExtraCheck;
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import fuzs.forgeconfigapiport.fabric.impl.config.ForgeConfigApiPortConfig;
import fuzs.forgeconfigapiport.fabric.impl.config.ModConfigValues;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
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
import net.minecraft.world.entity.Pose;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.neoforged.fml.config.ModConfig;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.nio.file.Files;

/** Paused live hand, native widgets, disk readback and final HUD pixels with both Mesa and Iris. */
final class ViewTuningSmoke {
    private static final String UI = "gui.tacz.view_tuning.";

    // Only legacy migration touches FCAP. A normal editor save must not wake its global watcher.
    private static volatile double watchedSize;
    static {
        ModConfigEvents.reloading(GunMod.MOD_ID).register(config -> {
            if (config.getType() == ModConfig.Type.CLIENT && Thread.currentThread() != Minecraft.getInstance().getRunningThread())
                watchedSize = Dimension.SIZE.option.get();
        });
    }

    static TuningProfile fromDisk() {
        var owner = TuningProfiles.account();
        check(Files.isRegularFile(TuningProfiles.DIRECTORY.resolve(owner + ".json")), "No account profile on disk");
        return new TuningProfileStore(TuningProfiles.DIRECTORY, TuningValues.DEFAULTS).get(owner);
    }

    private static void checkDisk(ClientGameTestContext ctx, Mode mode) {
        ctx.runOnClient(mc -> check(fromDisk().base().get(ViewTuningConfig.MODE) == mode
                && TuningProfiles.current().base().get(ViewTuningConfig.MODE) == mode, "Saved profile mode differs on disk"));
    }

    static <T> void setBase(ConfigValue<T> option, T value) {
        var p = TuningProfiles.current().copy(); p.base().set(option, value);
        check(TuningProfiles.save(TuningProfiles.account(), p), "Could not save profile fixture");
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var key = GunInputHandler.VIEW_TUNING_KEY;
        var binding = ctx.computeOnClient(mc -> KeyMappingHelper.getBoundKeyOf(key));
        String clipboard = ctx.computeOnClient(mc -> mc.keyboardHandler.getClipboard());
        var camera = ctx.computeOnClient(mc -> mc.options.getCameraType());
        var previous = ctx.computeOnClient(mc -> {
            var backup = TuningProfiles.current().copy();
            check(TuningProfiles.save(TuningProfiles.account(), TuningProfile.seeded(TuningValues.DEFAULTS)), "Cannot prepare account profile");
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            key.setKey(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_J));
            KeyMapping.resetMapping();
            return backup;
        });
        try {
            // The preceding drop test restores the server's AK from an empty hand. Pausing
            // before equip/draw settles can freeze the model below the screen: its locator
            // still moves, but both screenshot ROIs are genuinely empty.
            ctx.waitFor(mc -> mc.player.getMainHandItem().getItem() instanceof IGun);
            ctx.waitTicks(25);
            ctx.waitFor(mc -> !IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder().clientStateLock
                    && IGunOperator.fromLivingEntity(mc.player).getSynDrawCoolDown()==0);
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
                check(saveButton(mc).active && TuningProfiles.current().base().get(Axis.X.option) == 0d, "Live preview wrote through to config");
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
            // The editor is paused, but extraction and presentation keep advancing. Read
            // the completed changed hand frame, not the last presented pre-edit frame.
            long editedFrame = ctx.computeOnClient(mc -> ScopePipRenderer.frameNumber());
            ctx.waitFor(mc -> ScopePipRenderer.frameNumber() >= editedFrame+2);
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
            String positionCode = copy(ctx);
            check(positionCode.equals("TACZ-POS1:0.237,0.000,0.000"), "Wrong position share: " + positionCode);
            ctx.clickScreenButton(UI + "crosshair");
            ctx.clickScreenButton(UI + "mode.preset");
            ctx.clickScreenButton(UI + "page.geometry");
            ctx.runOnClient(mc -> {
                slider(mc, "thickness", 1); var t = field(mc, "thickness");
                check(t.getValue().equals("32.00"), "Slider did not cover the widened range");
                for (String bad : List.of(".001", "NaN", "-1", "32.01")) {
                    t.setValue(bad); check(!saveButton(mc).active, "Invalid thickness accepted: " + bad);
                }
                t.setValue("3.00"); check(saveButton(mc).active, "Valid thickness rejected");
            });
            ctx.clickScreenButton(UI + "page.color");
            ctx.runOnClient(mc -> {
                var rgb = field(mc, "rgb"); rgb.setValue("#XY0101");
                check(!saveButton(mc).active, "Save enabled for malformed RGB");
                rgb.setValue("#FFFFFF");
                slider(mc, "hue", .5); slider(mc, "saturation", 1); slider(mc, "brightness", .5);
                check(rgb.getValue().equals("#008080"), "Hue lost on white or channels not synchronized: " + rgb.getValue());
                slider(mc, "opacity", 1);
            });
            ctx.clickScreenButton("color.minecraft.magenta");
            ctx.runOnClient(mc -> check(field(mc, "rgb").getValue().equals("#FF00FF"), "Color swatch did not reach RGB field"));
            frame(ctx, "colors");
            String hudCode = profileRoundTrips(ctx, positionCode);
            frame(ctx, "designer");

            ctx.clickScreenButton(UI + "save"); ctx.waitForScreen(PauseScreen.class);
            checkDisk(ctx, Mode.DESIGNER);
            ctx.runOnClient(mc -> {
                var saved = fromDisk().base();
                check(saved.get(Axis.X.option) == .237d && saved.get(ViewTuningConfig.RGB) == 0xFF00FF
                        && saved.get(Dimension.THICKNESS.option) == 3d, "Personal XYZ/HUD were not persisted");
                check(Axis.X.option.get() == 0d && ViewTuningConfig.MODE.get() == Mode.PRESET,
                        "Editor overwrote the shared TOML seed");
            });
            legacyMigration(ctx);
            ctx.setScreen(() -> null); ctx.waitTicks(8);
            int scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
            // 4 arms of length 4, thickness 3, plus 3x3 center. Bounding box = 3 + 2*(4+2).
            int designer = assertHud(frame(ctx, "hip"), scale, 57, 15, 15);
            ctx.getInput().holdKey(AimKey.AIM_KEY);
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1) > .99f);
            ctx.runOnClient(mc -> {
                var display = TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).orElseThrow();
                check(!display.isShowCrosshair() && !ShoulderSurfingCompat.showCrosshair(),
                        "ADS negative control requires a gun/camera without a forced crosshair");
            });
            assertHud(frame(ctx, "ads"), scale, 0, 0, 0);
            ctx.getInput().releaseKey(AimKey.AIM_KEY);
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1) < .01f);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            assertHud(frame(ctx, "third_person"), scale, 0, 0, 0);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            dynamicBox(ctx, scale, key);
            ctx.getInput().pressKey(key); ctx.waitForScreen(ViewTuningScreen.class); // rebound, not the Cloth config key
            ctx.clickScreenButton(UI + "crosshair"); ctx.clickScreenButton(UI + "page.share");
            paste(ctx, hudCode); // restore static and the original proportions before sampling the full-UV PNG
            ctx.clickScreenButton(UI + "page.style"); ctx.clickScreenButton(UI + "mode.designer");
            ctx.clickScreenButton(UI + "page.geometry");
            ctx.runOnClient(mc -> field(mc, "size").setValue("32"));

            ctx.clickScreenButton(UI + "save"); ctx.waitForScreen(null);
            checkDisk(ctx, Mode.PNG);
            // The test resource pack overrides the slot with a 32x32 texture, six opaque centered columns.
            int png = assertHud(frame(ctx, "png"), scale, 6 * 32, 6, 32);
            ctx.getInput().pressKey(key); ctx.waitForScreen(ViewTuningScreen.class);
            ctx.clickScreenButton(UI + "crosshair"); ctx.clickScreenButton(UI + "page.geometry");
            ctx.clickScreenButton(UI + "center.original"); ctx.clickScreenButton(UI + "center.clear");
            ctx.runOnClient(mc -> { field(mc, "aspect").setValue("150"); field(mc, "dot_size").setValue("4"); });

            ctx.clickScreenButton(UI + "save"); ctx.waitForScreen(null); checkDisk(ctx, Mode.PNG);
            // 32x48: six columns, except the middle 12 rows. The replacement center is 4x4, not stretched.
            assertHud(frame(ctx, "png_center"), scale, 6 * 36 + 16, 6, 48);
            ctx.getInput().pressKey(key); ctx.waitForScreen(ViewTuningScreen.class);
            ctx.clickScreenButton(UI + "crosshair"); ctx.clickScreenButton(UI + "reset_hud");
            ctx.runOnClient(mc -> check(TuningValues.visible().get(ViewTuningConfig.MODE) == Mode.PRESET
                    && TuningValues.visible().get(Axis.X.option) == .237d, "Reset affected wrong page"));
            ctx.clickScreenButton(UI + "page.share"); paste(ctx, hudCode);
            ctx.runOnClient(mc -> check(TuningValues.visible().get(ViewTuningConfig.MODE) == Mode.DESIGNER, "Imported code not previewed"));
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE); ctx.waitForScreen(null);
            ctx.runOnClient(mc -> check(TuningProfiles.current().base().get(ViewTuningConfig.MODE) == Mode.PNG && TuningProfiles.current().base().get(Measure.ASPECT.option) == 150, "Escape saved reset/import"));
            presetStyles(ctx, scale, key);
            HudPrecisionSmoke.run(ctx, world, key, scale);
            AccountHudSmoke.run(ctx, world, key, scale);
            GunMod.LOGGER.info("PORT_VIEW_TUNING: live muzzle delta={}, changed hand pixels={}, HUD designer/PNG={}/{} px at {}x, ADS and F5=0; disk, reset, cancel and rebound key passed",
                    delta, handPixels, designer, png, scale);
        } finally {
            ctx.getInput().releaseKey(AimKey.AIM_KEY);
            ctx.getInput().releaseKey(o -> o.keyShift);
            ctx.setScreen(() -> null);

            Mode restored = ctx.computeOnClient(mc -> {
                Mode mode = previous.base().get(ViewTuningConfig.MODE);
                check(TuningProfiles.save(TuningProfiles.account(), previous), "Profile restore failed"); mc.keyboardHandler.setClipboard(clipboard); mc.options.setCameraType(camera); key.setKey(binding); KeyMapping.resetMapping();
                return mode;
            });
            checkDisk(ctx, restored);
            ctx.waitFor(mc -> !mc.isPaused() && InputExtraCheck.isInGame());
        }
    }

    static void saveHud(ClientGameTestContext ctx, Mode mode) {

        ctx.clickScreenButton(UI + "save"); ctx.waitForScreen(null); checkDisk(ctx, mode);
        ctx.waitFor(mc -> !mc.isPaused() && InputExtraCheck.isInGame());
    }

    static String copy(ClientGameTestContext ctx) {
        ctx.clickScreenButton(UI + "copy");
        return ctx.computeOnClient(mc -> mc.keyboardHandler.getClipboard());
    }

    static void paste(ClientGameTestContext ctx, String code) {
        ctx.runOnClient(mc -> mc.keyboardHandler.setClipboard(code));
        ctx.clickScreenButton(UI + "paste");
    }

    private static String profileRoundTrips(ClientGameTestContext ctx, String position) {
        ctx.clickScreenButton(UI + "page.share");
        String hud = copy(ctx);
        check(hud.startsWith("TACZ-HUD2:DESIGNER,"), "Wrong HUD share: " + hud);
        ctx.clickScreenButton(UI + "reset_hud");
        String neutral = copy(ctx);
        for (String bad : List.of(position, hud.replace("HUD2", "HUD3"), hud + ",1", hud.replace(",3.00,100,", ",3.001,100,"), hud.substring(0, hud.lastIndexOf(',') + 1) + "1501", "x".repeat(257))) {
            paste(ctx, bad);
            check(copy(ctx).equals(neutral), "Invalid code partially applied to HUD: " + bad);
        }
        paste(ctx, "TACZ-HUD1:DESIGNER,DOT_1,BOX,ORIGINAL,5,FF00FF,16,4,2,3,150,5,255,100");
        check(copy(ctx).equals("TACZ-HUD2:DESIGNER,DOT_1,BOX,ORIGINAL,5,FF00FF,16.00,4.00,2.00,3.00,150,5.00,255,100,6.00,3.00,300"), "HUD1 migration lost values");
        paste(ctx, hud);
        check(copy(ctx).equals(hud), "HUD round trip lost values");
        ctx.clickScreenButton(UI + "model");
        check(copy(ctx).equals(position), "HUD import changed XYZ");
        ctx.runOnClient(mc -> field(mc, "axis.x").setValue("-.750"));
        String changed = copy(ctx);
        for (String bad : List.of(hud, "TACZ-POS1:0.125,0.2,NaN", "TACZ-POS1:0.125,2.0,0.3")) {
            paste(ctx, bad);
            check(copy(ctx).equals(changed), "Invalid code partially applied to XYZ");
        }
        paste(ctx, position);
        check(copy(ctx).equals(position), "Position round trip lost precision");
        ctx.clickScreenButton(UI + "crosshair");
        check(copy(ctx).equals(hud), "Position import changed HUD");
        ctx.runOnClient(mc -> check(TuningProfiles.current().base().get(Axis.X.option) == 0d && TuningProfiles.current().base().get(ViewTuningConfig.MODE) == Mode.PRESET,
                "Sharing bypassed Save"));
        ctx.clickScreenButton(UI + "page.geometry");
        return hud;
    }

    private static void dynamicBox(ClientGameTestContext ctx, int scale, KeyMapping key) {
        ctx.getInput().pressKey(key); ctx.waitForScreen(ViewTuningScreen.class);
        ctx.clickScreenButton(UI + "crosshair");
        for (String shape : List.of("cross", "t", "sides")) ctx.clickScreenButton(UI + "shape." + shape);
        ctx.clickScreenButton(UI + "motion.static");
        ctx.clickScreenButton(UI + "page.geometry");
        ctx.runOnClient(mc -> { field(mc, "aspect").setValue("150"); field(mc, "dot_size").setValue("5"); });
        ctx.clickScreenButton(UI + "page.share");
        String dynamic = copy(ctx);
        ctx.clickScreenButton(UI + "reset_hud"); paste(ctx, dynamic);
        check(copy(ctx).equals(dynamic), "Dynamic geometry round trip failed");

        ctx.clickScreenButton(UI + "save"); ctx.waitForScreen(null); checkDisk(ctx, Mode.DESIGNER);
        ctx.waitFor(mc -> mc.player.onGround() && mc.player.getPose() == Pose.STANDING && !mc.isPaused());
        double stand = boxFrame(ctx, "stand", scale, InaccuracyType.STAND);
        ctx.getInput().holdKey(o -> o.keyShift);
        ctx.waitFor(mc -> mc.player.getPose() == Pose.CROUCHING);
        double sneak = boxFrame(ctx, "sneak", scale, InaccuracyType.SNEAK);
        ctx.getInput().releaseKey(o -> o.keyShift);
        ctx.waitFor(mc -> mc.player.getPose() == Pose.STANDING);
        check(stand > sneak && sneak > 0, "Box did not contract with AK stance: " + stand + "/" + sneak);
        // Override ONLY the test client's modified stand entry. Base gun data and pose are unchanged;
        // a HUD driven by key inputs, a timer, or the unmodified pack spread cannot pass this frame.
        var old = ctx.computeOnClient(mc -> {
            var cache = IGunOperator.fromLivingEntity(mc.player).getCacheProperty();
            var saved = cache.getCache(GunProperties.INACCURACY);
            var perfect = new EnumMap<>(saved); perfect.put(InaccuracyType.STAND, 0f);
            cache.setCache(GunProperties.INACCURACY, perfect);
            return saved;
        });
        try {
            check(boxFrame(ctx, "zero_cache", scale, InaccuracyType.STAND) == 0, "Ignored zero modified spread");
        } finally {
            ctx.runOnClient(mc -> IGunOperator.fromLivingEntity(mc.player).getCacheProperty().setCache(GunProperties.INACCURACY, old));
        }
        GunMod.LOGGER.info("PORT_HUD_PROFILES: AK stand/sneak/zero-cache radii={}/{}/0 GUI px; box proportions, fixed dot, HSV and separate clipboard round trips passed", stand, sneak);
    }

    private static double boxFrame(ClientGameTestContext ctx, String name, int scale, InaccuracyType stance) {
        ctx.waitTicks(4);
        double r = projectedRadius(ctx, stance);
        // Base box 17x21, outline stroke 3 and a separate 5x5 center. Only the gap may grow.
        double w = 17 + 2 * r, h = 21 + 2 * r;
        assertHud(frame(ctx, "box_" + name), scale, 6 * (w + h - 6) + 25, w, h);
        return r;
    }

    private static double projectedRadius(ClientGameTestContext ctx, InaccuracyType stance) {
        return ctx.computeOnClient(mc -> {
            float uncertainty = IGunOperator.fromLivingEntity(mc.player).getCacheProperty().getCache(GunProperties.INACCURACY).get(stance);
            float projection = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.projectionMatrix.m11();
            return uncertainty * .0172275 * projection * mc.getWindow().getHeight() / (2 * mc.getWindow().getGuiScale());
        });
    }

    private static void presetStyles(ClientGameTestContext ctx, int scale, KeyMapping key) {
        // Keep the saved 32x48 image geometry and 4px replacement center. The basic dot must not
        // disappear or turn into a larger blob: only its four two-pixel spread ticks may move.
        ctx.getInput().pressKey(key); ctx.waitForScreen(ViewTuningScreen.class);
        ctx.clickScreenButton(UI + "crosshair"); ctx.clickScreenButton(UI + "mode.png");
        ctx.clickScreenButton(UI + "motion.static");

        ctx.clickScreenButton(UI + "save"); ctx.waitForScreen(null); checkDisk(ctx, Mode.PRESET);
        ctx.waitTicks(4);
        double r = projectedRadius(ctx, InaccuracyType.STAND);
        assertHud(frame(ctx, "dot_spread"), scale, 16 + 4 * 2, 8 + 2 * r, 8 + 2 * r);

        // Exercise a real shipped image, not a second procedural lookalike or just the custom PNG slot.
        ctx.getInput().pressKey(key); ctx.waitForScreen(ViewTuningScreen.class);
        ctx.clickScreenButton(UI + "crosshair"); ctx.clickScreenButton(UI + "motion.dynamic");
        for (int i = 0; i < 3; i++) ctx.clickScreenButton(">"); // DOT_1 -> CIRCLE_3

        ctx.clickScreenButton(UI + "save"); ctx.waitForScreen(null); checkDisk(ctx, Mode.PRESET);
        BufferedImage ring = frame(ctx, "preset_ring");
        Ink bounds = ink(ring, 38 * scale);
        int w = bounds.x1 - bounds.x0 + 1, h = bounds.y1 - bounds.y0 + 1;
        check(w > 22*scale && w < 29*scale && h > 1.4*w && h < 1.6*w && ink(ring, 5*scale).count == 16*scale*scale,
                "Legacy ring lost its 32x48 proportions or 4x4 center: " + bounds);
        GunMod.LOGGER.info("PORT_HUD_PRESETS: dynamic pinned dot and static customized CIRCLE_3 {}x{} framebuffer px passed", w, h);
    }

    private static void legacyMigration(ClientGameTestContext ctx) {
        if (ForgeConfigApiPortConfig.getConfigValue(ModConfigValues.DISABLE_CONFIG_WATCHER)) return;
        var path = FabricLoader.getInstance().getConfigDir().resolve("tacz-client.toml");
        double original = ctx.computeOnClient(mc -> Dimension.SIZE.option.get());
        try {
            watchedSize = -1;
            ctx.runOnClient(mc -> {
                try (var file = CommentedFileConfig.builder(path).build()) {
                    file.load(); file.set(Dimension.SIZE.option.getPath(), 37); file.save();
                }
            });
            ctx.waitFor(mc -> watchedSize == 37);
            ctx.runOnClient(mc -> {
                var fresh = new TuningProfileStore(TuningProfiles.DIRECTORY, ConfigValue::get).get(java.util.UUID.randomUUID());
                check(fresh.base().get(Dimension.SIZE.option) == 37d && TuningProfiles.current().base().get(Dimension.SIZE.option) != 37d,
                        "Legacy integer did not seed a new account independently of the saved one");
            });
        } finally {
            watchedSize = -1;
            ctx.runOnClient(mc -> {
                try (var file = CommentedFileConfig.builder(path).build()) {
                    file.load(); file.set(Dimension.SIZE.option.getPath(), original); file.save();
                }
            });
            ctx.waitFor(mc -> watchedSize == original);
        }
    }

    static EditBox field(Minecraft mc, String key) {
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
        return ClientProbe.read(ctx.takeScreenshot("tacz_view_tuning_" + name));
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

    private record Ink(int count, int x0, int y0, int x1, int y1) {}

    private static Ink ink(BufferedImage image, int r) {
        int cx = image.getWidth()/2, cy = image.getHeight()/2;
        int count = 0, x0 = cx+r, y0 = cy+r, x1 = cx-r, y1 = cy-r;
        for (int y = cy-r; y <= cy+r; y++) for (int x = cx-r; x <= cx+r; x++) {
            int p = image.getRGB(x, y);
            if ((p >> 16 & 255) > 210 && (p >> 8 & 255) < 45 && (p & 255) > 210) {
                count++; x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
            }
        }
        return new Ink(count, x0, y0, x1, y1);
    }

    private static int assertHud(BufferedImage image, int scale, double area, double width, double height) {
        int cx = image.getWidth()/2, cy = image.getHeight()/2;
        Ink ink = ink(image, 38 * scale);
        int count = ink.count, x0 = ink.x0, x1 = ink.x1, y0 = ink.y0, y1 = ink.y1;
        if (area == 0) check(count == 0, "HUD reticle escaped its visibility gate: " + count);
        else {
            double expected = area * scale * scale;
            check(Math.abs(count-expected) <= Math.max(2, expected/12) && Math.abs(x1-x0+1-width*scale) <= 1
                    && Math.abs(y1-y0+1-height*scale) <= 1 && Math.abs(x0+x1-2*cx) <= 3*scale
                    && Math.abs(y0+y1-2*cy) <= 3*scale, "Wrong centered reticle: " + count + "/" + expected
                    + " px, [" + x0 + "," + y0 + ".." + x1 + "," + y1 + "] at " + scale + "x");
        }
        return count;
    }

    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
