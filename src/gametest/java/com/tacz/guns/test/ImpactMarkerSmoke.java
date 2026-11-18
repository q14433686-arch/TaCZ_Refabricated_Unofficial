package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.input.AimKey;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** Compare the real XM157 frame with the same stationary scene minus just its four marker quads. */
public final class ImpactMarkerSmoke {
    private static int draws;
    private static boolean hide;

    private static String diagnostic = "no marker submission";
    private static boolean trace;
    public static void observe(com.tacz.guns.client.model.functional.ImpactMarkerRender marker, net.minecraft.world.item.ItemStack held,
                               com.mojang.blaze3d.vertex.PoseStack root) {
        if (!trace) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        root.pushPose();
        try {
            for (var part : marker.path()) part.translateAndRotateAndScale(root);
            var hud = new org.joml.Matrix4f();
            ((com.tacz.guns.mixin.client.GameRendererProjectionAccessor)(Object)mc.gameRenderer).tacz$getHudProjection().getMatrix(hud);
            hud.mul(root.last().pose());
            diagnostic = "slope=" + com.tacz.guns.client.model.papi.RangefinderReadout.impactSlope(held, marker.settings())
                    + ", clip=" + hud.m30() + "," + hud.m31() + "," + hud.m33() + ", up=" + hud.m11()
                    + ", camera=" + mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.initialized;
        } finally { root.popPose(); }
    }

    public static boolean atDraw() { draws++; return hide; }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var sight = Identifier.parse("tacz:scope_xm157");
        var target = world.getServer().computeOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(Identifier.parse("tacz:qr_24"))
                    .putAttachment(AttachmentType.SCOPE, sight).setAmmoCount(3).build());
            var cow = new Cow(EntityTypes.COW, player.level());
            cow.setNoAi(true);
            cow.setNoGravity(true);
            BlockPos targetPos = player.blockPosition().above(12).south(24);
            cow.snapTo(Vec3.atBottomCenterOf(targetPos));
            // A random spawn can be on a hillside or beside a tree. Explicitly clear the sight line.
            for (int i = 1; i <= 30; i++) {
                var center = BlockPos.containing(player.getEyePosition().lerp(Vec3.atCenterOf(targetPos.above()), i / 30.0));
                for (int y = -1; y <= 1; y++) player.level().removeBlock(center.above(y), false);
            }
            player.level().addFreshEntity(cow);
            return cow.getUUID();
        });
        try {
            ctx.waitFor(mc -> mc.level.getEntity(target) != null && mc.player.getMainHandItem().getItem() instanceof IGun g
                    && sight.equals(g.getAttachmentId(mc.player.getMainHandItem(), AttachmentType.SCOPE)));
            BlockPos look = ctx.computeOnClient(mc -> mc.level.getEntity(target).blockPosition().above());
            ctx.getInput().lookAt(look);
            ctx.getInput().holdKey(AimKey.AIM_KEY);
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1) >= .99f);
            ctx.runOnClient(mc -> { draws = 0; trace = true; });
            try { ctx.waitFor(mc -> draws > 0); }
            catch (AssertionError e) {
                ctx.takeScreenshot("tacz_xm157_missing");
                throw new AssertionError(ctx.computeOnClient(mc -> "No XM157 vertices: " + diagnostic + "; readout=" + RangefinderSmoke.lastReading()), e);
            }
            ctx.waitTicks(12);
            Path on = ctx.takeScreenshot("tacz_xm157_entity");
            ctx.runOnClient(mc -> hide = true);
            ctx.waitTicks(6);
            Path off = ctx.takeScreenshot("tacz_xm157_control");
            ctx.runOnClient(mc -> hide = false);
            ctx.waitTicks(6);
            Path again = ctx.takeScreenshot("tacz_xm157_restored");
            int pixels = restoredYellow(on, off, again);
            if (pixels < 2) throw new AssertionError("XM157 draw callback ran but no restored yellow marker pixels: " + pixels);
            var wall = world.getServer().computeOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                BlockPos center = BlockPos.containing(player.getEyePosition().lerp(Vec3.atCenterOf(look), .5));
                for (int x=-1; x<=1; x++) for (int y=-1; y<=1; y++) player.level().setBlock(center.offset(x,y,0), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                return center;
            });
            ctx.waitFor(mc -> mc.level.getBlockState(wall).is(Blocks.STONE));
            ctx.waitTicks(6); // beyond the actual XM157 refresh interval
            ctx.runOnClient(mc -> draws = 0);
            ctx.waitTicks(8);
            ctx.runOnClient(mc -> { if (draws != 0) throw new AssertionError("Marker persisted behind a solid block"); });
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

    private static int restoredYellow(Path on, Path control, Path restored) {
        try {
            var a = ImageIO.read(on.toFile()); var b = ImageIO.read(control.toFile()); var c = ImageIO.read(restored.toFile());
            int n = 0;
            for (int x = a.getWidth()*3/8; x < a.getWidth()*5/8; x++)
                for (int y = a.getHeight()*3/8; y < a.getHeight()*5/8; y++)
                    if (yellow(a.getRGB(x,y)) && !yellow(b.getRGB(x,y)) && yellow(c.getRGB(x,y))) n++;
            return n;
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
    private static boolean yellow(int rgb) {
        int r = rgb >>> 16 & 255, g = rgb >>> 8 & 255, b = rgb & 255;
        return r > 110 && g > 110 && Math.min(r,g) > 2*b && Math.abs(r-g) < 48;
    }
}
