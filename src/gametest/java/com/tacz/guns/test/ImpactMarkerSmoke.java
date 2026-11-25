package com.tacz.guns.test;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.model.functional.ImpactMarkerRender;
import com.tacz.guns.mixin.client.GameRendererProjectionAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** Compare the real XM157 frame with the same stationary scene minus just its four marker quads. */
public final class ImpactMarkerSmoke {
    private static int draws;
    private static boolean hide;

    private static String diagnostic = "no marker submission";
    private static boolean trace;
    private static double lastSlope;

    public static boolean onSubmit(ImpactMarkerRender marker, PoseStack root, ImpactMarkerRender.Layer layer) {
        if (trace) {
            var mc = Minecraft.getInstance();
            Matrix4f hud = new Matrix4f();
            ((GameRendererProjectionAccessor)(Object)mc.gameRenderer).tacz$getHudProjection().getMatrix(hud);
            hud.mul(RenderSystem.getModelViewMatrix());
            root.pushPose();
            try {
                for (var part : marker.path()) part.translateAndRotateAndScale(root);
                hud.mul(root.last().pose());
                diagnostic = layer + " clip=" + hud.m30() + "," + hud.m31() + "," + hud.m33()
                        + ", camera=" + mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.initialized;
            } finally { root.popPose(); }
        }
        return hide;
    }

    public static void onSample(double slope) { lastSlope = slope; }
    public static void atDraw(PoseStack.Pose pose, float y, float half, float line) {
        draws++;
        var projection = com.tacz.guns.client.render.scope.ScopeProjection.current();
        if (!trace || projection == null || draws != 1) return;
        var clip = new Matrix4f(projection).mul(RenderSystem.getModelViewMatrix()).mul(pose.pose());
        var target = Minecraft.getInstance().getMainRenderTarget();
        var corners = new StringBuilder();
        for (float dx : new float[]{-half, half}) for (float dy : new float[]{-half, half}) {
            var p = clip.transformProject(new org.joml.Vector3f(dx, y + dy, 0));
            corners.append(' ').append((p.x()+1)*.5f*target.width).append(',').append((1-p.y())*.5f*target.height);
        }
        GunMod.LOGGER.info("PORT_XM157: projected corners (framebuffer px){}; local half={} stroke={} exposed slope={}", corners, half, line, lastSlope);
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        MarkerPixels.checkControls();
        var sight = Identifier.parse("tacz:scope_xm157");
        var target = world.getServer().computeOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(Identifier.parse("tacz:qr_24"))
                    .putAttachment(AttachmentType.SCOPE, sight).setAmmoCount(3).build());
            var cow = new Cow(EntityType.COW, player.level());
            cow.setNoAi(true);
            cow.setNoGravity(true);
            BlockPos targetPos = player.blockPosition().above(12).south(42).east(6);
            cow.snapTo(Vec3.atBottomCenterOf(targetPos));
            // A random spawn can be on a hillside or beside a tree. Explicitly clear the sight line.
            for (int i = 1; i <= 48; i++) {
                var center = BlockPos.containing(player.getEyePosition().lerp(Vec3.atCenterOf(targetPos), i / 48.0));
                for (int y = -1; y <= 1; y++) player.level().removeBlock(center.above(y), false);
            }
            player.level().addFreshEntity(cow);
            return cow.getUUID();
        });
        try {
            ctx.waitFor(mc -> mc.level.getEntity(target) != null && mc.player.getMainHandItem().getItem() instanceof IGun g
                    && sight.equals(g.getAttachmentId(mc.player.getMainHandItem(), AttachmentType.SCOPE)));
            BlockPos look = ctx.computeOnClient(mc -> mc.level.getEntity(target).blockPosition());
            ctx.getInput().lookAt(look);
            ctx.getInput().holdKey(AimKey.AIM_KEY);
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1) >= .99f);
            ctx.runOnClient(mc -> { draws = 0; trace = true; });
            try { ctx.waitFor(mc -> draws > 0 && lastSlope > 0); }
            catch (AssertionError e) {
                ctx.takeScreenshot("tacz_xm157_missing");
                throw new AssertionError(ctx.computeOnClient(mc -> "No projected XM157 marker: " + diagnostic + "; draws=" + draws + ", slope=" + lastSlope + ", readout=" + RangefinderSmoke.lastReading()), e);
            }
            ctx.runOnClient(mc -> trace = false);
            ctx.waitTicks(12);
            Path on = ctx.takeScreenshot("tacz_xm157_entity");
            ctx.runOnClient(mc -> hide = true);
            ctx.waitTicks(6);
            Path off = ctx.takeScreenshot("tacz_xm157_control");
            ctx.runOnClient(mc -> hide = false);
            ctx.waitTicks(6);
            Path again = ctx.takeScreenshot("tacz_xm157_restored");
            // The same entity remains visible to the player throughout this transition.
            ctx.getInput().releaseKey(AimKey.AIM_KEY);
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1) < .05f);
            assertNoDraws(ctx, "out of ADS");
            ctx.getInput().holdKey(AimKey.AIM_KEY);
            ctx.waitFor(mc -> draws > 0);
            var wall = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                BlockPos center = BlockPos.containing(player.getEyePosition().lerp(Vec3.atCenterOf(look), .5));
                for (int x=-1; x<=1; x++) for (int y=-1; y<=1; y++) player.level().setBlock(center.offset(x,y,0), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                return center;
            });
            ctx.waitFor(mc -> mc.level.getBlockState(wall).is(Blocks.STONE));
            ctx.waitTicks(6); // beyond the actual XM157 refresh interval
            assertNoDraws(ctx, "behind a solid block");
            Path blocked = ctx.takeScreenshot("tacz_xm157_occluded");
            ctx.runOnClient(mc -> hide = true);
            ctx.waitTicks(6);
            int pixels = checkRing(on, off, again, blocked, ctx.takeScreenshot("tacz_xm157_occluded_control"));
            GunMod.LOGGER.info("PORT_OPTIC: XM157 showed {} restored yellow pixels on entity, zero draws behind wall", pixels);
        } finally {
            ctx.getInput().releaseKey(AimKey.AIM_KEY);
            ctx.runOnClient(mc -> { hide = false; trace = false; });
            world.getServer().runOnServer(s -> {
                var cow = s.getPlayerList().getPlayers().getFirst().level().getEntity(target);
                if (cow != null) cow.discard();
            });
        }
    }

    private static void assertNoDraws(ClientGameTestContext ctx, String condition) {
        ctx.runOnClient(mc -> draws = 0);
        ctx.waitTicks(8);
        ctx.runOnClient(mc -> { if (draws != 0) throw new AssertionError("XM157 marker persisted " + condition); });
    }

    private static int checkRing(Path on, Path control, Path restored, Path occluded, Path occludedControl) {
        try {
            var a = ImageIO.read(on.toFile());
            var b = ImageIO.read(control.toFile());
            var c = ImageIO.read(restored.toFile());
            var d = ImageIO.read(occluded.toFile());
            var e = ImageIO.read(occludedControl.toFile());
            if (a == null) throw new AssertionError("Unreadable XM157 frame: " + on);
            int w = a.getWidth(), h = a.getHeight();
            for (var frame : new BufferedImage[]{b, c, d, e}) {
                if (frame == null || frame.getWidth() != w || frame.getHeight() != h)
                    throw new AssertionError("Incomparable XM157 frames");
            }
            // The range text is yellow too and can shift by a pixel. Only inspect the centre
            // of the fixed scene (±15px at 854x480); don't count text, HUD or advancement toast.
            int radius = Math.min(w, h) / 32;
            // Shader jitter can move a one-pixel line by a pixel between render ticks. Taking
            // a pixelwise intersection of two hollow outlines is not a test for a hollow outline:
            // a ring shifted down by one pixel intersects only the former vertical edges.
            // Require the entire box in EACH visible frame, in the same place to sub-stroke tolerance.
            // Each change in geometry needs its own hidden-marker control. A different wall
            // also changes the distance text and scene colour; it isn't a reference for the cow.
            var first = MarkerPixels.find(a, b, radius);
            var last = MarkerPixels.find(c, b, radius);
            var blocked = MarkerPixels.find(d, e, radius);
            if (first == null || last == null || Math.abs(first.cx()-last.cx()) > 1 || Math.abs(first.cy()-last.cy()) > 1 || blocked != null) {
                GunMod.LOGGER.error("PORT_XM157: entity on/off/restored (centre yellow rows): {} {} {}", map(a),map(b),map(c));
                GunMod.LOGGER.error("PORT_XM157: stone on/off: {} {}", map(d),map(e));
                throw new AssertionError("No restored hollow XM157 marker: " + first + " -> " + last + "; occluded=" + blocked + "; " + diagnostic);
            }
            GunMod.LOGGER.info("PORT_XM157: exposed {} restored {}; no enclosed marker behind stone", first, last);
            return Math.min(first.rim(), last.rim());
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }

    private static String map(BufferedImage image) {
        StringBuilder out=new StringBuilder();
        int cx=image.getWidth()/2,cy=image.getHeight()/2;
        for (int y=cy-6;y<=cy+8;y++) {
            int mask=0;
            for (int x=cx-7;x<=cx+7;x++) mask=(mask<<1)|(MarkerPixels.yellow(image.getRGB(x,y))?1:0);
            out.append(Integer.toHexString(mask)).append('/');
        }
        return out.toString();
    }

}
