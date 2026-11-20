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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Locale;

/** The test-mod observer sees the text resolved during extraction, not a second call to the distance helper. */
public final class RangefinderSmoke {
    private static String observed;
    static String lastReading() { return observed; }
    public static void observe(String text) { observed = text; }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var sight = Identifier.parse("tacz:sight_uh1");
        var ceiling = world.getServer().computeOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var held = GunItemBuilder.create().setId(Identifier.parse("tacz:qr_24"))
                    .putAttachment(AttachmentType.SCOPE, sight).setAmmoCount(3).build();
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            var pos = player.blockPosition().above(12);
            for (int i = 1; i <= 10; i++) player.level().removeBlock(pos.below(i), false);
            player.level().setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            return pos;
        });
        context.waitFor(mc -> mc.level.getBlockState(ceiling).is(Blocks.STONE)
                && mc.player.getMainHandItem().getItem() instanceof IGun gun && sight.equals(gun.getAttachmentId(mc.player.getMainHandItem(), AttachmentType.SCOPE)));
        context.getInput().lookAt(ceiling);
        context.runOnClient(mc -> observed = null);
        context.getInput().holdKey(AimKey.AIM_KEY);
        try {
            context.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1) >= .9f);
            checkReading(context, ceiling);
            var nearer = ceiling.below(5);
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().level()
                    .setBlock(nearer, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL));
            context.waitFor(mc -> mc.level.getBlockState(nearer).is(Blocks.STONE));
            checkReading(context, nearer);
            context.takeScreenshot("tacz_range_readout");
            GunMod.LOGGER.info("PORT_SMOKE_CLIENT: held UH-1 readout changed with the world while aimed");
        } finally {
            context.getInput().releaseKey(AimKey.AIM_KEY);
        }
    }

    private static void checkReading(ClientGameTestContext context, BlockPos pos) {
        String expected = context.computeOnClient(mc -> String.format(Locale.ROOT, "%03d m",
                Math.round((pos.getY() - mc.player.getEyeY()) / mc.player.getViewVector(1f).y)));
        try { context.waitFor(mc -> expected.equals(observed)); }
        catch (AssertionError e) { throw new AssertionError("Expected held range text " + expected + ", saw " + context.computeOnClient(mc -> observed), e); }
    }
}
