package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.render.scope.ScopePipRenderer;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.UUID;

/** Real XL50 against a striped backdrop. No test shader, fake mask, glReadPixels or glowing flag. */
final class ScopeLensSmoke {
    private static final Identifier SIGHT = Identifier.parse("tacz:scope_xl50");
    private record Scene(UUID cow, BlockPos aim, BlockPos wall) {}

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        Scene scene = world.getServer().computeOnServer(server -> {
            var p = world.getConnection().getServerPlayer();
            // M700's default allow-list includes #tacz:scope. QR-24 does not accept the XL50.
            var gun = GunItemBuilder.create().setId(Identifier.parse("tacz:m700"))
                    .putAttachment(AttachmentType.SCOPE, SIGHT).setAmmoCount(3).build();
            if (!(gun.getItem() instanceof IGun g) || !SIGHT.equals(g.getAttachmentId(gun, AttachmentType.SCOPE)))
                throw new AssertionError("XL50 not installed on the server's M700");
            p.setItemInHand(InteractionHand.MAIN_HAND, gun);
            var level = p.level();
            var aim = p.blockPosition().north(40).above(16);
            for (int i = 1; i <= 44; i++) {
                var at = BlockPos.containing(p.getEyePosition().lerp(Vec3.atCenterOf(aim), i/40.0));
                for (int x=-1; x<=1; x++) for (int y=-1; y<=1; y++) level.removeBlock(at.offset(x,y,0), false);
            }
            var cow = new Cow(EntityTypes.COW, level);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.snapTo(Vec3.atBottomCenterOf(aim));
            level.addFreshEntity(cow);
            // Low-frequency, saturated vanilla surfaces: at 6x a slight subpixel FOV
            // transition on woven wool can change individual pixels even outside the scope.
            var red = BuiltInRegistries.BLOCK.getOptional(Identifier.parse("minecraft:red_concrete")).orElseThrow().defaultBlockState();
            var blue = BuiltInRegistries.BLOCK.getOptional(Identifier.parse("minecraft:blue_concrete")).orElseThrow().defaultBlockState();
            // Keep the measuring stripe beside (not behind) the cow: its moving head
            // and legs are legitimate different pixels, not evidence of optical zoom.
            for (int x=-8; x<=8; x++) for (int y=-7; y<=7; y++)
                level.setBlock(aim.offset(x,y,-4), x == -1 ? red : blue, Block.UPDATE_ALL);
            return new Scene(cow.getUUID(), aim, BlockPos.containing(p.getEyePosition().lerp(Vec3.atCenterOf(aim), .5)));
        });
        var prior = ctx.computeOnClient(mc -> new boolean[]{RenderConfig.EXPERIMENTAL_SCOPE_LENS.get(),
                RenderConfig.SCOPE_THERMAL_ENABLE.get(), RenderConfig.SCOPE_PIP_ENABLE.get(), RenderConfig.SCOPE_PIP_RERENDER.get(),
                RenderConfig.SCOPE_PIP_DEBUG_NO_COMPOSITE.get(), mc.options.bobView().get(), RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.get()});
        try {
            // The normal View Bobbing option also drives pack camera-node animation.
            // Hold the world camera still for this A/B pixel comparison, not the entity renderer.
            ctx.runOnClient(mc -> {
                mc.options.bobView().set(false);
                if (Boolean.getBoolean("tacz.test.iris")) RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.set(true);
            });
            ctx.waitFor(mc -> mc.level.getEntity(scene.cow) != null && mc.player.getMainHandItem().getItem() instanceof IGun g
                    && SIGHT.equals(g.getAttachmentId(mc.player.getMainHandItem(), AttachmentType.SCOPE)));
            ctx.getInput().lookAt(scene.aim);
            ctx.getInput().holdKey(AimKey.AIM_KEY);
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1) > .99f);
            world.getConnection().waitForChunksRender();
            flags(ctx, false, false, false, false);
            var bare = capture(ctx, "classic_bare");
            flags(ctx, true, false, false, false);
            var optical = capture(ctx, "classic_optical");
            assertOptics(bare, optical);
            flags(ctx, true, true, false, false);
            var hot = capture(ctx, "classic_hot");
            wall(ctx, world, scene, true);
            var behind = capture(ctx, "classic_wall");
            wall(ctx, world, scene, false);
            var restored = capture(ctx, "classic_restored");

            flags(ctx, false, false, true, false);
            ctx.waitFor(mc -> ScopePipRenderer.suppressesWorldFovZoom());
            // Same ADS, camera, hand and mask; suppress ONLY the PIP composite. At a
            // world share of zero this gives us a real 1x image to measure, not just a flag.
            float zoom = ctx.computeOnClient(mc -> {
                if (ScopePipRenderer.currentWorldZoom() != 1f) throw new AssertionError("Fixture requires pure PIP");
                RenderConfig.SCOPE_PIP_DEBUG_NO_COMPOSITE.set(true);
                return ScopePipRenderer.currentMagnification();
            });
            var wide = capture(ctx, "pip_1x");
            ctx.runOnClient(mc -> RenderConfig.SCOPE_PIP_DEBUG_NO_COMPOSITE.set(false));
            var pipBare = capture(ctx, "pip_bare");
            flags(ctx, true, false, true, false);
            var pipOptics = capture(ctx, "pip_optical");
            assertOptics(pipBare, pipOptics);
            assertZoom(zoom, wide, bare, optical, pipBare, pipOptics);
            flags(ctx, true, true, true, false);
            var pipHot = capture(ctx, "pip_hot");
            wall(ctx, world, scene, true);
            var pipWall = capture(ctx, "pip_wall");
            wall(ctx, world, scene, false);
            var pipRestored = capture(ctx, "pip_restored");
            BufferedImage afterToggle = null;
            if (Boolean.getBoolean("tacz.test.iris")) {
                toggleShaders(ctx, false);
                toggleShaders(ctx, true);
                afterToggle = capture(ctx, "pip_shader_restored");
            }
            blacklistCow(ctx, world);
            var pipVeto = capture(ctx, "pip_tagged");
            flags(ctx, true, true, false, false);
            var veto = capture(ctx, "classic_tagged");
            int classic = heatPixels(optical, hot, behind, restored, veto);
            int pip = heatPixels(pipOptics, pipHot, pipWall, pipRestored, pipVeto);
            if (afterToggle != null) {
                int toggled = heatPixels(pipOptics, afterToggle, pipWall, pipRestored, pipVeto);
                GunMod.LOGGER.info("PORT_IRIS: shader off/on restored {} heat pixels", toggled);
            }
            GunMod.LOGGER.info("PORT_LENS: XL50 restored {} classic / {} PIP heat pixels; wall and live tag veto cold", classic, pip);
        } finally {
            ctx.getInput().releaseKey(AimKey.AIM_KEY);
            ctx.runOnClient(mc -> {
                RenderConfig.SCOPE_PIP_DEBUG_NO_COMPOSITE.set(prior[4]);
                mc.options.bobView().set(prior[5]);
                RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.set(prior[6]);
            });
            flags(ctx, prior[0], prior[1], prior[2], prior[3]);
            world.getServer().runOnServer(s -> { var e = world.getConnection().getServerPlayer().level().getEntity(scene.cow); if (e != null) e.discard(); });
        }
    }

    private static void toggleShaders(ClientGameTestContext ctx, boolean enabled) {
        ctx.runOnClient(mc -> {
            try {
                Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
                Object config = iris.getMethod("getIrisConfig").invoke(null);
                config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, enabled);
                iris.getMethod("reload").invoke(null);
            } catch (ReflectiveOperationException e) { throw new AssertionError("Real Iris reload failed", e); }
        });
        ctx.waitTicks(5);
        ctx.waitFor(mc -> com.tacz.guns.compat.iris.IrisCompat.isUsingRenderPack() == enabled);
    }

    private static void flags(ClientGameTestContext ctx, boolean lens, boolean heat, boolean pip, boolean rerender) {
        ctx.runOnClient(mc -> {
            RenderConfig.EXPERIMENTAL_SCOPE_LENS.set(lens);
            RenderConfig.SCOPE_THERMAL_ENABLE.set(heat);
            RenderConfig.SCOPE_PIP_ENABLE.set(pip);
            RenderConfig.SCOPE_PIP_RERENDER.set(rerender);
        });
    }

    private static void wall(ClientGameTestContext ctx, TestSingleplayerContext world, Scene scene, boolean place) {
        world.getServer().runOnServer(s -> {
            var level = world.getConnection().getServerPlayer().level();
            for (int x=-2; x<=2; x++) for (int y=-2; y<=2; y++) level.setBlock(scene.wall.offset(x,y,0),
                    (place ? Blocks.STONE : Blocks.AIR).defaultBlockState(), Block.UPDATE_ALL);
        });
        ctx.waitFor(mc -> mc.level.getBlockState(scene.wall).is(place ? Blocks.STONE : Blocks.AIR));
        world.getConnection().waitForChunksRender();
    }

    private static void blacklistCow(ClientGameTestContext ctx, TestSingleplayerContext world) {
        // This is Loom's disposable test gameDir. Use the same real external-pack reload as a player;
        // put the cow on BOTH lists to prove that the veto wins (not cached entity classification).
        try {
            var pack = Files.createTempDirectory(FabricLoader.getInstance().getGameDir().resolve("tacz"), "lens-smoke-");
            Files.writeString(pack.resolve("gunpack.meta.json"), "{\"namespace\":\"lens_smoke\",\"dependencies\":{\"minecraft\":\"[26.3]\"}}");
            var tags = Files.createDirectories(pack.resolve("data/tacz/tags/entity_type/thermal_imaging"));
            for (String list : new String[]{"whitelist", "blacklist"}) Files.writeString(tags.resolve(list+".json"), "{\"values\":[\"minecraft:cow\"]}");
        } catch (IOException e) { throw new UncheckedIOException(e); }
        var reloaded = world.getServer().computeOnServer(s -> {
            var packs = s.getPackRepository();
            packs.reload();
            return s.reloadResources(packs.getSelectedIds());
        });
        ctx.waitFor(mc -> reloaded.isDone(), 600);
        reloaded.join();
        ctx.waitFor(mc -> {
            var type = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.COW);
            return type.is(TagKey.create(Registries.ENTITY_TYPE, Identifier.parse("tacz:thermal_imaging/blacklist")))
                    && type.is(TagKey.create(Registries.ENTITY_TYPE, Identifier.parse("tacz:thermal_imaging/whitelist")));
        }, 600);
    }

    private static BufferedImage capture(ClientGameTestContext ctx, String name) {
        ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1) > .99f);
        ctx.waitTicks(12);
        // ADS progress is not the world FOV: SecondOrderDynamics converges on its own
        // clock. Wait for the actual projection of this stationary, non-sprinting camera.
        double[] projection = new double[2];
        try {
            ctx.waitFor(mc -> {
                double worldZoom = RenderConfig.SCOPE_PIP_ENABLE.get() ? ScopePipRenderer.currentWorldZoom() : ScopePipRenderer.currentMagnification();
                projection[0] = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.projectionMatrix.m11();
                projection[1] = worldZoom / Math.tan(Math.toRadians(mc.options.fov().get() * .5));
                return Math.abs(projection[0] - projection[1]) < .0005 * projection[1];
            }, 200);
        } catch (AssertionError e) {
            throw new AssertionError(name + ": world projection m11=" + projection[0] + ", expected=" + projection[1], e);
        }
        try { return ImageIO.read(ctx.takeScreenshot("tacz_lens_" + name).toFile()); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    // At the centre the XL50 has zero geometric distortion. Use the upper quartile
    // of row widths: the cow, reticle and readout interrupt the stripe in some rows.
    private static int stripeWidth(BufferedImage img) {
        int x0 = img.getWidth() / 2, y0 = img.getHeight() / 2, rx = img.getHeight() / 5, ry = img.getHeight() / 8;
        int[] widths = new int[2 * ry];
        for (int row = 0; row < widths.length; row++) {
            for (int x = x0 - rx; x < x0 + rx; x++) {
                if (stripe(img.getRGB(x, y0 - ry + row))) widths[row]++;
            }
        }
        Arrays.sort(widths);
        return widths[3 * widths.length / 4];
    }

    private static void assertZoom(float zoom, BufferedImage wide, BufferedImage... zoomed) {
        int unit = stripeWidth(wide);
        int[] widths = Arrays.stream(zoomed).mapToInt(ScopeLensSmoke::stripeWidth).toArray();
        // One thresholded texel at 1x becomes a few pixels after reconstruction. Still
        // distinguish 1x, the pack's 6x, and applying that magnification a second time.
        float expected = unit * zoom;
        if (unit < 3 || zoom != 6f || Arrays.stream(widths).anyMatch(n -> Math.abs(n - expected) > .25f * expected))
            throw new AssertionError("XL50 stripe at 1x=" + unit + ", at " + zoom + "x=" + Arrays.toString(widths));
        GunMod.LOGGER.info("PORT_LENS_SCALE: stripe 1x={} px; classic bare/optical={}/{} px; PIP bare/optical={}/{} px",
                unit, widths[0], widths[1], widths[2], widths[3]);
    }

    private static void assertOptics(BufferedImage bare, BufferedImage optical) {
        int w=bare.getWidth(), h=bare.getHeight(), radius=h/8;
        int red=0, resultRed=0, kept=0, darkened=0;
        for (int y=h/2-radius; y<h/2+radius; y++) for (int x=w/2-radius+1; x<w/2+radius; x++) {
            int a=bare.getRGB(x,y), b=optical.getRGB(x,y);
            if (stripe(a)) red++;
            if (stripe(b)) resultRed++;
            if (stripe(a) && stripe(b)) kept++;
            if (max(a)-min(a)>24 && max(a)-max(b)>6 && max(b)>max(a)*.5) darkened++;
        }
        if (red<20 || kept<.88*Math.max(red, resultRed) || darkened<150)
            throw new AssertionError("XL50 optics: shared stripe="+kept+", bare="+red+", optical="+resultRed+", darkened="+darkened);
        assertExterior(bare, optical);
    }

    private static void assertExterior(BufferedImage a, BufferedImage b) {
        int w=a.getWidth(), h=a.getHeight(), samples=0, kept=0;
        for (int y=3*h/8; y<5*h/8; y++) for (int x=0; x<w; x+=2) {
            int u=a.getRGB(x,y), v=b.getRGB(x,y);
            if (Math.abs(x-w/2)<=w/3 || max(u)-min(u)<=20) continue;
            samples++;
            if (Math.abs(r(u)-r(v))+Math.abs(g(u)-g(v))+Math.abs(b(u)-b(v))<12) kept++;
        }
        if (samples<200 || kept<.88*samples) throw new AssertionError("XL50 changed the exterior: kept="+kept+"/"+samples);
    }

    private static int heatPixels(BufferedImage optical, BufferedImage hot, BufferedImage wall, BufferedImage restored, BufferedImage veto) {
        assertExterior(optical, hot);
        int w=hot.getWidth(), h=hot.getHeight(), radius=h/9, n=0, wallLeaks=0, tagLeaks=0, backdrop=0;
        for (int y=h/2-radius; y<h/2+radius; y++) for (int x=w/2-radius; x<w/2+radius; x++) {
            int a=hot.getRGB(x,y), o=optical.getRGB(x,y);
            if (cold(a) && max(o)-min(o)>24) backdrop++;
            // Identify coverage WITHOUT looking at either negative. Exclude white reticle/text
            // already bright with thermal disabled; don't credit a tag veto merely for being gray.
            if (bright(a) && bright(restored.getRGB(x,y)) && !bright(o)) {
                n++;
                if (!cold(wall.getRGB(x,y))) wallLeaks++;
                if (!cold(veto.getRGB(x,y))) tagLeaks++;
            }
        }
        if (n < 40 || backdrop < 200 || wallLeaks > .02*n || tagLeaks > .02*n)
            throw new AssertionError("XL50 restored="+n+", cold backdrop="+backdrop+", not cold: wall="+wallLeaks+", tag="+tagLeaks);
        return n;
    }

    private static int r(int p) { return p>>16 & 255; }
    private static int g(int p) { return p>>8 & 255; }
    private static int b(int p) { return p & 255; }
    private static int max(int p) { return Math.max(r(p),Math.max(g(p),b(p))); }
    private static int min(int p) { return Math.min(r(p),Math.min(g(p),b(p))); }
    private static boolean bright(int p) { return min(p)>180 && max(p)-min(p)<4; }
    private static boolean cold(int p) { return max(p)<96 && max(p)-min(p)<4; }
    private static boolean stripe(int p) { return r(p)>45 && r(p)>2*g(p) && r(p)>2*b(p); }
}
