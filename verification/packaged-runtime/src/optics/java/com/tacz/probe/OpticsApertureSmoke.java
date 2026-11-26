package com.tacz.probe;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.render.scope.ScopeDepthCopyState;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.opengl.GL45C;

/**
 * Vanilla (no Iris) aperture-mask probe for the 1.21.11 production JAR.
 *
 * <p>Ports the non-shader part of 26.1.2's {@code ApertureReadback}: after a real ADS raise the
 * ocular aperture depth copy must be valid for this frame, must differ from the world depth
 * only inside a bounded central region, and must disappear again after release. The moving
 * PIP reprojection and Iris cases are separate suites and are not claimed here.</p>
 */
public final class OpticsApertureSmoke extends PlayerProbe {
    private record Mask(boolean valid, int width, int height, int area, double cx, double cy,
                        int minX, int minY, int maxX, int maxY, float progress, String background) {
    }

    @Override void exercise(ClientGameTestContext ctx, TestSingleplayerContext world) {
        ctx.runOnClient(mc -> {
            com.tacz.guns.client.render.scope.ReticleContractSmoke.verify();
            ReticleGroups.verify();
            int program = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
            try {
                org.lwjgl.opengl.GL20.glUseProgram(0);
                ScopeDepthCopyState.begin(ScopeDepthCopyState.Operation.MASK);
                if (ScopeDepthCopyState.beforeDraw()) throw new AssertionError("Unsafe interior draw without mask program");
                ScopeDepthCopyState.end();
                ScopeDepthCopyState.begin(ScopeDepthCopyState.Operation.MASK_OUTSIDE);
                if (!ScopeDepthCopyState.beforeDraw()) throw new AssertionError("Outside FX lost their unmasked fallback");
            } finally {
                ScopeDepthCopyState.end();
                org.lwjgl.opengl.GL20.glUseProgram(program);
            }
        });
        System.out.println("PORT_RETICLE_CONTRACT: hybrid materials, inherited light, dedup, frozen pose, visibility, ADS, safe fallback, draw veto, HAMR/Vudu active groups and TA31 housing exclusion passed in intermediary");
        var optic = Identifier.parse("tacz:scope_standard_8x");
        var server = world.getServer();
        var base = server.computeOnServer(s -> {
            var p = s.getPlayerList().getPlayers().getFirst();
            var at = new BlockPos(p.blockPosition().getX(), 240, p.blockPosition().getZ());
            p.level().setBlock(at.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            for (int x = -46; x <= 46; x++) for (int y = -29; y <= 29; y++)
                p.level().setBlock(at.offset(x, y, 40), ((x + y) % 2 == 0 ? Blocks.ORANGE_CONCRETE : Blocks.LIGHT_BLUE_CONCRETE).defaultBlockState(), Block.UPDATE_CLIENTS);
            p.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(Identifier.parse("tacz:m700"))
                    .putAttachment(AttachmentType.SCOPE, optic).setAmmoCount(3).build());
            return at;
        });
        server.runCommand("time set 6000");
        server.runCommand("weather clear");
        server.runCommand("tp @a " + (base.getX() + .5) + " " + base.getY() + " " + (base.getZ() + .5));
        ctx.waitFor(mc -> mc.player.onGround() && mc.player.blockPosition().equals(base)
                && mc.player.getMainHandItem().getItem() instanceof IGun gun && optic.equals(gun.getAttachmentId(mc.player.getMainHandItem(), AttachmentType.SCOPE)));
        turn(ctx, 0, 0);
        world.getClientWorld().waitForChunksRender();
        ctx.waitTicks(30);

        var errors = new java.util.ArrayList<String>();
        try {
            ctx.getInput().holdKey(AimKey.AIM_KEY);
            // Raise: sample inside the transition; the probe only claims the aperture exists there.
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(0) >= .2f);
            var raise = sample(ctx, "raise");
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(0) > .999f);
            ctx.waitTicks(6);
            var steady = sample(ctx, "steady");
            ReticleFrames.verify(ctx, optic);
            // Minecraft releases key mappings when the paused material-ablation screen opens.
            // Establish a fresh real ADS state; never interpret a hip frame as missing heat.
            ctx.getInput().releaseKey(AimKey.AIM_KEY);
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(0) < .001f);
            ctx.getInput().holdKey(AimKey.AIM_KEY);
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(0) > .999f);
            ThermalFrames.verify(ctx, world, base, optic);
            AdsTransitionSmoke.run(ctx,world);
            WideSceneSmoke.verify(ctx,world,base);
            ReceiverLightSmoke.verify(ctx,world,base);
            ctx.getInput().holdKey(AimKey.AIM_KEY);
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(0) > .999f);
            ctx.getInput().releaseKey(AimKey.AIM_KEY);
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(0) < .001f);
            ctx.waitTicks(6);
            var hip = sample(ctx, "hip");

            check(raise, errors, "raise", false);
            check(steady, errors, "steady", true);
            if (hip.valid && hip.area != 0) errors.add("hip: aperture persisted after release, area=" + hip.area);
            // Iris is probed separately: the vanilla suite must not have a pack, the Iris suite must have one.
            boolean iris = "optics-iris".equals(System.getProperty("tacz.probe.player"));
            boolean active = ctx.computeOnClient(mc -> com.tacz.guns.compat.iris.IrisCompat.isUsingRenderPack());
            GunMod.LOGGER.info("PORT_IRIS: suite={} pack-active={} at steady ADS", iris ? "optics-iris" : "vanilla", active);
            if (iris != active) errors.add("shader pack state " + active + " does not match suite " + (iris ? "optics-iris" : "vanilla"));
        } finally {
            ctx.getInput().releaseKey(AimKey.AIM_KEY);
        }
        if (!errors.isEmpty()) throw new AssertionError("Vanilla aperture mask: " + errors);
    }

    private static Mask sample(ClientGameTestContext ctx, String name) {
        var mask = ctx.computeOnClient(OpticsApertureSmoke::readMask);
        String state = ctx.computeOnClient(OpticsApertureSmoke::state);
        GunMod.LOGGER.info("PORT_OPTIC_STATE: {} {}", name, state);
        ctx.takeScreenshot("tacz_optic_" + name);
        GunMod.LOGGER.info("PORT_OPTIC: {} p={} valid={} background={} area={} centre={}/{} box={},{}-{},{} size={}x{}",
                name, mask.progress, mask.valid, mask.background, mask.area, mask.cx, mask.cy, mask.minX, mask.minY, mask.maxX, mask.maxY, mask.width, mask.height);
        return mask;
    }

    private static void check(Mask m, java.util.List<String> errors, String name, boolean steady) {
        if (!m.valid) { errors.add(name + ": no aperture copy from this frame"); return; }
        int pixels = m.width * m.height;
        if (m.area < 30) errors.add(name + ": aperture area too small " + m.area);
        if (steady) {
            if (m.area > pixels * .6) errors.add(name + ": aperture covers most of the frame " + m.area + "/" + pixels);
            if (Math.hypot(m.cx - m.width / 2.0, m.cy - m.height / 2.0) > .15 * m.height) errors.add(name + ": aperture centre off axis " + m.cx + "/" + m.cy);
            if (m.minX <= 0 || m.minY <= 0 || m.maxX >= m.width - 1 || m.maxY >= m.height - 1) errors.add(name + ": aperture touches the frame border");
        }
    }

    /** Diagnostic only: the four conditions readMask needs, so a failed copy is named rather than guessed. */
    private static String state(net.minecraft.client.Minecraft mc) {
        var main = mc.getMainRenderTarget();
        var a = ScopeDepthCopyState.apertureDepthTarget();
        var w = ScopeDepthCopyState.worldDepthTarget();
        return "cycleThisFrame=" + ScopeDepthCopyState.hasMaskCycleThisFrame() + " maskValid=" + ScopeDepthCopyState.isMaskCycleValid()
                + " aperture=" + a.available() + "/" + a.width() + "x" + a.height() + "/fmt=" + a.internalFormat()
                + " world=" + w.available() + "/" + w.width() + "x" + w.height() + "/fmt=" + w.internalFormat()
                + " main=" + main.width + "x" + main.height;
    }

    /**
     * Reads the live ocular aperture copy. The background is the private world copy when the mod has one
     * (vanilla). Under Iris the mod reads Iris's own depthtex2, which is not exposed, so the background is
     * the median of the outer 2% border ring of the aperture itself; this is labelled as such in the receipt.
     */
    private static Mask readMask(net.minecraft.client.Minecraft mc) {
        var main = mc.getMainRenderTarget();
        float progress = IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(0);
        int w = main.width, h = main.height;
        var aperture = ScopeDepthCopyState.apertureDepthTarget();
        var world = ScopeDepthCopyState.worldDepthTarget();
        if (!ScopeDepthCopyState.hasMaskCycleThisFrame() || !aperture.available()
                || aperture.width() != w || aperture.height() != h) {
            return new Mask(false, w, h, 0, 0, 0, 0, 0, 0, 0, progress, "none");
        }
        boolean privateWorld = world.available() && world.width() == w && world.height() == h;
        int[] keys = {GL45C.GL_PACK_SKIP_PIXELS, GL45C.GL_PACK_SKIP_ROWS, GL45C.GL_PACK_ROW_LENGTH, GL45C.GL_PACK_ALIGNMENT};
        int[] old = java.util.Arrays.stream(keys).map(GL45C::glGetInteger).toArray();
        try {
            for (int i = 0; i < keys.length; i++) GL45C.glPixelStorei(keys[i], i == 3 ? 4 : 0);
            float[] a = depth(aperture.textureId(), w, h);
            float[] b = privateWorld ? depth(world.textureId(), w, h) : null;
            float background = 0;
            if (!privateWorld) {
                // Inside the wall plane there is no aperture; the ring is wall too for this scene.
                float[] ring = new float[w * h];
                int n = 0, bx = Math.max(1, w / 50), by = Math.max(1, h / 50);
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
                    if (x < bx || y < by || x >= w - bx || y >= h - by) ring[n++] = a[y * w + x];
                java.util.Arrays.sort(ring, 0, n);
                background = ring[n / 2];
            }
            int area = 0, minX = w, minY = h, maxX = -1, maxY = -1;
            double sx = 0, sy = 0;
            for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
                // GL origin is bottom-left; both textures are read identically, so the mask is flipped only once.
                int src = (h - row - 1) * w + col;
                float bg = privateWorld ? b[src] : background;
                if (a[src] < bg - 1e-6f) {
                    area++; sx += col + .5; sy += row + .5;
                    minX = Math.min(minX, col); maxX = Math.max(maxX, col);
                    minY = Math.min(minY, row); maxY = Math.max(maxY, row);
                }
            }
            String source = privateWorld ? "private-world-copy" : "border-ring-median";
            if (area == 0) return new Mask(true, w, h, 0, 0, 0, 0, 0, 0, 0, progress, source);
            return new Mask(true, w, h, area, sx / area, sy / area, minX, minY, maxX, maxY, progress, source);
        } finally {
            for (int i = 0; i < keys.length; i++) GL45C.glPixelStorei(keys[i], old[i]);
        }
    }

    private static float[] depth(int texture, int w, int h) {
        float[] buffer = new float[w * h];
        GL45C.glGetTextureSubImage(texture, 0, 0, 0, 0, w, h, 1, GL45C.GL_DEPTH_COMPONENT, GL45C.GL_FLOAT, buffer);
        return buffer;
    }
}
