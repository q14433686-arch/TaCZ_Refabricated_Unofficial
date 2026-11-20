package com.tacz.guns.test;

import cn.sh1rocu.tacz.TaCZFabric;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.tuning.TuningProfiles;
import com.tacz.guns.client.tuning.TuningValues;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.gui.ViewTuningScreen;
import com.tacz.guns.client.input.ShootKey;
import com.tacz.guns.config.client.ViewTuningConfig;
import com.tacz.guns.config.client.ViewTuningConfig.Dimension;
import com.tacz.guns.config.client.ViewTuningConfig.Measure;
import com.tacz.guns.config.client.ViewTuningConfig.Mode;
import com.tacz.guns.util.time.GunTime;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.tacz.guns.test.ViewTuningSmoke.*;

/** Regress the reported axis error and distinguish a real firing cue from any change of stance/spread. */
final class HudPrecisionSmoke {
    private static final String UI = "gui.tacz.view_tuning.";
    private static final Identifier BURST = Identifier.parse("tacz:b93r");
    private static volatile UUID watched;
    private static boolean cancel; // local pre-event and test mutations on the render thread
    private static final AtomicInteger client = new AtomicInteger(), server = new AtomicInteger(), cancelled = new AtomicInteger();
    private static float lastPeak;
    static {
        GunFireEvent.CALLBACK.register(TaCZFabric.LOWEST, e -> {
            if (e.getLogicalSide().isClient() && e.getShooter().getUUID().equals(watched) && cancel) {
                e.setCanceled(true); cancelled.incrementAndGet();
            }
        });
        GunFirePostEvent.EVENT.register(e -> {
            if (!e.getShooter().getUUID().equals(watched)) return;
            if (e.getLogicalSide().isServer()) server.incrementAndGet();
            else {
                lastPeak = op(Minecraft.getInstance()).getDataHolder().hudShotEnvelope.sample(GunTime.nowMillis() + 65);
                client.incrementAndGet();
            }
        });
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world, KeyMapping key, int scale) {
        open(ctx, key); ctx.clickScreenButton(UI + "page.share");
        paste(ctx, "TACZ-HUD1:PRESET,DOT_1,CROSS,ORIGINAL,5,FF00FF,16,4,2,1,100,0,255,100");
        saveHud(ctx, Mode.PRESET);
        // Native texture (no replacement dot) + the four one-unit arms, on even and odd render targets.
        axes(frame(ctx, "native_axes", false), scale);
        axes(frame(ctx, "native_axes_odd", true), scale);
        var reported = reportedViewport(ctx);
        open(ctx, key); ctx.clickScreenButton(UI + "mode.preset");
        for (String shape : List.of("cross", "t", "sides", "box")) ctx.clickScreenButton(UI + "shape." + shape);
        ctx.runOnClient(mc -> {
            field(mc, "kick").setValue("7.25"); field(mc, "recovery").setValue("1500");
            field(mc, "spread_scale").setValue("0");
        });
        ctx.clickScreenButton(UI + "preview_shot");
        ctx.runOnClient(mc -> check(op(mc).getDataHolder().hudShotEnvelope.sample(GunTime.nowMillis()) == 0, "Preview fired the held gun"));
        ctx.clickScreenButton(UI + "page.geometry");
        ctx.runOnClient(mc -> {
            field(mc, "radius").setValue("9,37"); field(mc, "thickness").setValue("1.75"); field(mc, "dot_size").setValue("2.99");
        });
        frame(ctx, "ring_editor", false);
        ctx.clickScreenButton(UI + "page.share");
        String code = copy(ctx);
        check(code.equals("TACZ-HUD2:DESIGNER,DOT_1,RING,ORIGINAL,5,FF00FF,16.00,4.00,2.00,1.75,100,2.99,255,0,9.37,7.25,1500"), "Fractional profile: " + code);
        ctx.clickScreenButton(UI + "reset_hud"); paste(ctx, code); check(code.equals(copy(ctx)), "Ring round trip");
        saveHud(ctx, Mode.DESIGNER);
        ctx.runOnClient(mc -> check(fromDisk().base().get(Dimension.RADIUS.option) == 9.37 && fromDisk().base().get(Dimension.THICKNESS.option) == 1.75
                && fromDisk().base().get(Dimension.KICK.option) == 7.25, "Disk reload lost hundredths"));
        ring(frame(ctx, "ring_rest", false), scale, 1, true);
        ctx.runOnClient(mc -> { setBase(Measure.ASPECT.option, 150); setBase(ViewTuningConfig.DOT, false); setBase(ViewTuningConfig.OUTLINE, true); });
        ring(frame(ctx, "ring_ellipse", false), scale, 1.5, false);
        ctx.runOnClient(mc -> { setBase(Measure.ASPECT.option, 100); setBase(ViewTuningConfig.DOT, true); setBase(ViewTuningConfig.OUTLINE, false); });
        // On odd physical dimensions the middle pixel is ON the axis. Only .02 GUI units apart:
        // integer GUI geometry would produce identical dot coverage at both settings.
        int[] counts = new int[2];
        for (int i = 0; i < 2; i++) {
            double d = 6d / scale + (i == 0 ? -.01 : .01);
            ctx.runOnClient(mc -> setBase(Dimension.DOT_SIZE.option, d));
            counts[i] = pixels(frame(ctx, "dot_hundredth_" + i, true), 4*scale, (x,y) -> true).n;
        }
        check(counts[0] == 25 && counts[1] == 49, "Hundredths did not reach the raster: " + counts[0] + "/" + counts[1]);
        ctx.runOnClient(mc -> setBase(Dimension.DOT_SIZE.option, 2.99));
        GunMod.LOGGER.info("PORT_HUD_PRECISION: native axes even/odd + 2560x1417 at 4x {}; circle, open ellipse and dot subpixel boundary {}/{} framebuffer pixels", reported, counts[0], counts[1]);
        realBurst(ctx, world, scale);
    }

    /** The original report used a 2560 x 1417 framebuffer with 4-pixel-thick one-GUI-unit arms. */
    private static Axes reportedViewport(ClientGameTestContext ctx) {
        int[] original = ctx.computeOnClient(mc -> new int[]{mc.getWindow().getWidth(), mc.getWindow().getHeight(), mc.getWindow().getGuiScale()});
        try {
            setTarget(ctx, 2560, 1417, 4);
            var shot = ctx.takeScreenshot(TestScreenshotOptions.of("tacz_view_tuning_native_axes_reported").withSize(2560, 1417));
            BufferedImage im = ClientProbe.read(shot);
            check(im.getWidth() == 2560 && im.getHeight() == 1417, "Unexpected render target");
            var axes = axes(im, 4);
            // Also keep a lossless, unscaled crop for frame review; the full target is in the smoke artifact.
            var crop = im.getSubimage(im.getWidth()/2 - 128, im.getHeight()/2 - 128, 256, 257);
            ImageIO.write(crop, "png", shot.resolveSibling("tacz_view_tuning_reported_crop.png").toFile());
            return axes;
        } catch (IOException e) { throw new UncheckedIOException(e); }
        finally { setTarget(ctx, original[0], original[1], original[2]); }
    }

    private static void setTarget(ClientGameTestContext ctx, int w, int h, int scale) {
        ctx.runOnClient(mc -> {
            var window = mc.getWindow();
            window.setWidth(w); window.setHeight(h); window.setGuiScale(scale); mc.gameRenderer.resize(w, h);
        });
    }

    private static void open(ClientGameTestContext ctx, KeyMapping key) {
        ctx.getInput().pressKey(key); ctx.waitForScreen(ViewTuningScreen.class); ctx.clickScreenButton(UI + "crosshair");
    }

    private static void realBurst(ClientGameTestContext ctx, TestSingleplayerContext world, int scale) {
        var previous = world.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getMainHandItem().copy());
        var previousProfile = ctx.computeOnClient(mc -> TuningProfiles.current().copy());
        var rotation = ctx.computeOnClient(mc -> new float[]{mc.player.getYRot(), mc.player.getXRot()});
        try {
            equip(world, GunItemBuilder.create().setId(BURST).setAmmoCount(20).setAmmoInBarrel(true).setFireMode(FireMode.BURST).build());
            ctx.waitFor(mc -> mc.player.getMainHandItem().getItem() instanceof IGun g && g.getGunId(mc.player.getMainHandItem()).equals(BURST));
            ctx.waitTicks(25); ctx.getInput().lookAt(90, -70);
            ctx.waitFor(mc -> mc.player.onGround() && !op(mc).getDataHolder().clientStateLock && IGunOperator.fromLivingEntity(mc.player).getSynDrawCoolDown() == 0);
            ctx.runOnClient(mc -> {
                // Re-enable the physical term, but keep every modified stance exactly zero for the entire shot.
                setBase(Measure.SPREAD_SCALE.option, 100);
                // Only the exact gun is dynamic. Reading the shared TOML, account default or editor
                // preview in the post-fire callback cannot open the envelope in this test.
                setBase(ViewTuningConfig.DYNAMIC, false);
                setBurstDynamic(true);
                var cache = IGunOperator.fromLivingEntity(mc.player).getCacheProperty();
                var zero = new EnumMap<>(cache.getCache(GunProperties.INACCURACY)); zero.replaceAll((k,v) -> 0f);
                cache.setCache(GunProperties.INACCURACY, zero);
                watched = mc.player.getUUID(); client.set(0); server.set(0); cancelled.set(0);
            });
            var rest = ring(frame(ctx, "burst_rest", false), scale, 1, true);
            // One actual input produces three subshots. No synthetic fire event or modified clock.
            trigger(ctx, 3);
            ctx.waitFor(mc -> op(mc).getDataHolder().hudShotEnvelope.sample(GunTime.nowMillis()) > 1.1f);
            var opened = pixels(frame(ctx, "burst_open", false), 50*scale, (x,y) -> true);
            check(lastPeak > 1.5f && opened.width() > rest.width() + 5*scale, "Zero-spread burst did not open: " + lastPeak + "; widths " + rest.width() + "/" + opened.width());
            ctx.waitFor(mc -> op(mc).getDataHolder().hudShotEnvelope.sample(GunTime.nowMillis()) == 0);
            var settled = ring(frame(ctx, "burst_return", false), scale, 1, true);
            check(settled.width() == rest.width() && settled.n == rest.n, "Shot cue did not return to rest");
            ctx.runOnClient(mc -> {
                check(IGunOperator.fromLivingEntity(mc.player).getCacheProperty().getCache(GunProperties.INACCURACY).values().stream().allMatch(v -> v == 0f), "Test spread changed");
                setBurstDynamic(false);
            });
            trigger(ctx, 6);
            check(lastPeak == 0f, "Static mode recorded an impulse");
            ring(frame(ctx, "burst_static", false), scale, 1, true);
            ctx.runOnClient(mc -> { setBurstDynamic(true); cancel = true; });
            ctx.getInput().holdKey(ShootKey.SHOOT_KEY);
            try { ctx.waitFor(mc -> cancelled.get() == 3); } finally { ctx.getInput().releaseKey(ShootKey.SHOOT_KEY); }
            check(client.get() == 6, "Cancelled fire emitted a post");
            ctx.runOnClient(mc -> check(op(mc).getDataHolder().hudShotEnvelope.sample(GunTime.nowMillis() + 65) == 0, "Cancelled fire opened the HUD"));
            // Empty hand magazine and chamber on the authority, not a fake mouse event. No post/impulse.
            equip(world, GunItemBuilder.create().setId(BURST).setAmmoCount(0).setAmmoInBarrel(false).setFireMode(FireMode.BURST).build());
            ctx.waitFor(mc -> mc.player.getMainHandItem().getItem() instanceof IGun g && g.getCurrentAmmoCount(mc.player.getMainHandItem()) == 0);
            ctx.waitTicks(20);
            ctx.runOnClient(mc -> {
                cancel = false;
                check(op(mc).shoot() == ShootResult.NO_AMMO && op(mc).getDataHolder().hudShotEnvelope.sample(GunTime.nowMillis() + 65) == 0, "Dry fire moved the HUD");
            });
            GunMod.LOGGER.info("PORT_HUD_SHOTS: zero modified spread, 3 local burst posts; ring widths rest/open/returned={}/{}/{} framebuffer px; static, cancelled and dry controls passed", rest.width(), opened.width(), settled.width());
        } finally {
            watched = null; ctx.runOnClient(mc -> cancel = false); ctx.getInput().releaseKey(ShootKey.SHOOT_KEY);
            ctx.runOnClient(mc -> check(TuningProfiles.save(TuningProfiles.account(), previousProfile), "Could not restore base profile"));
            equip(world, previous); ctx.getInput().lookAt(rotation[0], rotation[1]);
        }
    }

    private static void setBurstDynamic(boolean enabled) {
        var p = TuningProfiles.current().copy();
        String type = TuningProfiles.type(BURST);
        var exact = new TuningValues.Draft(p.hud(type, BURST.toString()));
        exact.set(ViewTuningConfig.DYNAMIC, enabled); p.setHud(type, BURST.toString(), exact);
        check(TuningProfiles.save(TuningProfiles.account(), p), "Could not save exact-gun firing fixture");
    }

    private static void trigger(ClientGameTestContext ctx, int count) {
        ctx.getInput().holdKey(ShootKey.SHOOT_KEY);
        try { ctx.waitFor(mc -> client.get() >= count && server.get() >= count); }
        finally { ctx.getInput().releaseKey(ShootKey.SHOOT_KEY); }
        check(client.get() == count && server.get() == count, "Duplicate/missing subshot: " + client + "/" + server);
    }

    private static void equip(TestSingleplayerContext world, ItemStack stack) {
        world.getServer().runOnServer(s -> {
            var p = s.getPlayerList().getPlayers().getFirst(); p.setItemInHand(InteractionHand.MAIN_HAND, stack); p.inventoryMenu.broadcastChanges();
        });
    }
    private static IClientPlayerGunOperator op(Minecraft mc) { return IClientPlayerGunOperator.fromLocalPlayer(mc.player); }
    private static BufferedImage frame(ClientGameTestContext ctx, String name, boolean odd) {
        var options = TestScreenshotOptions.of("tacz_view_tuning_" + name);
        return ClientProbe.read(ctx.takeScreenshot(odd ? options.withSize(853, 481) : options));
    }

    private interface Region { boolean contains(double x, double y); }
    private static final class Ink {
        int n, sumX, sumY, x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
        void add(int x, int y) { n++; sumX += x; sumY += y; x0 = Math.min(x0,x); x1 = Math.max(x1,x); y0 = Math.min(y0,y); y1 = Math.max(y1,y); }
        int width() { return x1 - x0 + 1; } int height() { return y1 - y0 + 1; }
        double x() { return sumX / (double)n; } double y() { return sumY / (double)n; }
    }
    private static Ink pixels(BufferedImage im, int r, Region region) {
        double cx = (im.getWidth() - 1) / 2d, cy = (im.getHeight() - 1) / 2d;
        var data = new Ink();
        for (int y = (int)cy-r; y <= cy+r; y++) for (int x = (int)cx-r; x <= cx+r; x++) {
            int rgb = im.getRGB(x,y);
            if ((rgb >> 16 & 255) > 210 && (rgb >> 8 & 255) < 45 && (rgb & 255) > 210 && region.contains(x-cx,y-cy)) data.add(x,y);
        }
        return data;
    }
    private record Axes(double dotX, double dotY, double verticalX, double horizontalY) {}
    private static Axes axes(BufferedImage im, int scale) {
        int r = 40*scale, c = 3*scale;
        var dot = pixels(im, c, (x,y) -> true);
        Ink l = pixels(im,r,(x,y) -> x < -c), right = pixels(im,r,(x,y) -> x > c);
        Ink top = pixels(im,r,(x,y) -> y < -c), b = pixels(im,r,(x,y) -> y > c);
        // Compare EACH arm's perpendicular axis and the midpoint of the opposed arms to the actual framebuffer.
        double cx = (im.getWidth()-1)/2d, cy = (im.getHeight()-1)/2d;
        for (double x : new double[]{dot.x(), top.x(), b.x(), (l.x()+right.x())/2}) check(Math.abs(x-cx) <= .55, "Horizontal center " + x + " vs " + cx);
        for (double y : new double[]{dot.y(), l.y(), right.y(), (top.y()+b.y())/2}) check(Math.abs(y-cy) <= .55, "Vertical center " + y + " vs " + cy);
        return new Axes(dot.x(), dot.y(), (top.x()+b.x())/2, (l.y()+right.y())/2);
    }
    private static Ink ring(BufferedImage im, int scale, double aspect, boolean dot) {
        var all = pixels(im, 38*scale, (x,y) -> true);
        double ix = 9.37, iy = ix*aspect, ox = ix + 1.75, oy = iy + 1.75;
        double area = Math.PI*(ox*oy-ix*iy) * scale*scale;
        var middle = pixels(im, 4*scale, (x,y) -> true);
        check(Math.abs(all.width()-2*ox*scale) <= 1.1 && Math.abs(all.height()-2*oy*scale) <= 1.1 && Math.abs(all.n-middle.n-area) < area*.09
                && (dot ? middle.n > 0 && middle.width() <= 3*scale && middle.height() <= 3*scale : middle.n == 0),
                "Wrong ring: " + all.width() + "x" + all.height() + "/" + all.n + " pixels; center=" + middle.n);
        return all;
    }
    private static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }
}
