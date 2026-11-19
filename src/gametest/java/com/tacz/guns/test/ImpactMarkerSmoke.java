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
import net.minecraft.world.entity.EntityTypes;
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

    public static boolean onSubmit(ImpactMarkerRender marker, PoseStack root) {
        if (trace) {
            var mc = Minecraft.getInstance();
            Matrix4f hud = new Matrix4f();
            ((GameRendererProjectionAccessor)(Object)mc.gameRenderer).tacz$getHudProjection().getMatrix(hud);
            hud.mul(RenderSystem.getModelViewMatrixCopy());
            root.pushPose();
            try {
                for (var part : marker.path()) part.translateAndRotateAndScale(root);
                hud.mul(root.last().pose());
                diagnostic = "clip=" + hud.m30() + "," + hud.m31() + "," + hud.m33()
                        + ", camera=" + mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.initialized;
            } finally { root.popPose(); }
        }
        return hide;
    }

    public static void onSample(double slope) { lastSlope = slope; }
    public static void atDraw() { draws++; }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var sight = Identifier.parse("tacz:scope_xm157");
        var target = world.getServer().computeOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(Identifier.parse("tacz:qr_24"))
                    .putAttachment(AttachmentType.SCOPE, sight).setAmmoCount(3).build());
            var cow = new Cow(EntityTypes.COW, player.level());
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
                var player = world.getConnection().getServerPlayer();
                BlockPos center = BlockPos.containing(player.getEyePosition().lerp(Vec3.atCenterOf(look), .5));
                for (int x=-1; x<=1; x++) for (int y=-1; y<=1; y++) player.level().setBlock(center.offset(x,y,0), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                return center;
            });
            ctx.waitFor(mc -> mc.level.getBlockState(wall).is(Blocks.STONE));
            ctx.waitTicks(6); // beyond the actual XM157 refresh interval
            assertNoDraws(ctx, "behind a solid block");
            int pixels = checkRing(on, off, again, ctx.takeScreenshot("tacz_xm157_occluded"));
            GunMod.LOGGER.info("PORT_OPTIC: XM157 showed {} restored yellow pixels on entity, zero draws behind wall", pixels);
        } finally {
            ctx.getInput().releaseKey(AimKey.AIM_KEY);
            ctx.runOnClient(mc -> { hide = false; trace = false; });
            world.getServer().runOnServer(s -> {
                var cow = world.getConnection().getServerPlayer().level().getEntity(target);
                if (cow != null) cow.discard();
            });
        }
    }

    private static void assertNoDraws(ClientGameTestContext ctx, String condition) {
        ctx.runOnClient(mc -> draws = 0);
        ctx.waitTicks(8);
        ctx.runOnClient(mc -> { if (draws != 0) throw new AssertionError("XM157 marker persisted " + condition); });
    }

    private static boolean nearYellow(BufferedImage image,int x,int y){
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)
            if(yellow(image.getRGB(Math.clamp(x+dx,0,image.getWidth()-1),Math.clamp(y+dy,0,image.getHeight()-1))))return true;
        return false;
    }

    private static int checkRing(Path on, Path control, Path restored, Path occluded) {
        try {
            var a = ImageIO.read(on.toFile());
            var b = ImageIO.read(control.toFile());
            var c = ImageIO.read(restored.toFile());
            var d = ImageIO.read(occluded.toFile());
            if (a == null) throw new AssertionError("Unreadable XM157 frame: " + on);
            int w = a.getWidth(), h = a.getHeight();
            for (var frame : new BufferedImage[]{b, c, d}) {
                if (frame == null || frame.getWidth() != w || frame.getHeight() != h)
                    throw new AssertionError("Incomparable XM157 frames");
            }
            // The range text is yellow too and can shift by a pixel. Only inspect the centre
            // of the fixed scene (±15px at 854x480); don't count text, HUD or advancement toast.
            int radius = Math.min(w, h) / 32;
            int n = 0, x0 = w, x1 = -1, y0 = h, y1 = -1;
            for (int y = h/2-radius; y <= h/2+radius; y++) {
                for (int x = w/2-radius; x <= w/2+radius; x++) {
                    // The restored display layer can legitimately place yellow range text or
                    // an etched reticle here. Measure *additional* yellow against both controls;
                    // behind-wall marker suppression is independently asserted by zero draws.
                    if (yellow(a.getRGB(x, y)) && yellow(c.getRGB(x, y))
                            && !nearYellow(b,x,y) && !nearYellow(d,x,y)) {
                        x0 = Math.min(x0,x); x1 = Math.max(x1,x);
                        y0 = Math.min(y0,y); y1 = Math.max(y1,y);
                        n++;
                    }
                }
            }
            if (n < 6 || x1-x0 < 2 || y1-y0 < 2 || (yellow(a.getRGB((x0+x1)/2, (y0+y1)/2)) && !nearYellow(b,(x0+x1)/2,(y0+y1)/2))
                    || (yellow(c.getRGB((x0+x1)/2, (y0+y1)/2)) && !nearYellow(b,(x0+x1)/2,(y0+y1)/2)))
                throw new AssertionError("No restored hollow XM157 marker: " + n + " pixels in " + x0 + "," + y0 + ".." + x1 + "," + y1);
            return n;
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }

    private static boolean yellow(int rgb) {
        int r = rgb >>> 16 & 255, g = rgb >>> 8 & 255, b = rgb & 255;
        return r > 110 && g > 110 && Math.min(r,g) > 2*b && Math.abs(r-g) < 48;
    }
}
