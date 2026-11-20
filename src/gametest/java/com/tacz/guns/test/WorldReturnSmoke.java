package com.tacz.guns.test;

import cn.sh1rocu.tacz.api.LogicalSide;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.block.entity.GunDisplayCaseBlockEntity;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import com.tacz.guns.client.gui.AmmoStorageScreen;
import com.tacz.guns.client.input.ShootKey;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.inventory.AmmoStorageMenu;
import com.tacz.guns.network.message.ClientMessageAmmoStorageClick;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.util.time.GunTime;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Close the first integrated server, reopen the same on-disk save, and exercise the new connection. */
final class WorldReturnSmoke {
    private static final Identifier GUN = Identifier.parse("tacz:qr_24"), GRIP = Identifier.parse("tacz:grip_troy");
    private static final Component MARK = Component.literal("survived reopen");
    record Saved(TestWorldSave save, BlockPos board, BlockPos storage, CommonGunIndex index, AnimationStateMachine<?> machine, long epoch) {}

    static Saved prepare(ClientGameTestContext context, TestSingleplayerContext world, BlockPos board) {
        var storage = board.west();
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(GUN).setFireMode(FireMode.SEMI)
                    .setAmmoCount(2).setAmmoInBarrel(true).putAttachment(AttachmentType.GRIP, GRIP).build());
            var level = player.level();
            level.setBlock(storage, ModBlocks.AMMO_STORAGE_GOLD.defaultBlockState(), Block.UPDATE_ALL);
            var be = (AmmoStorageBlockEntity) level.getBlockEntity(storage);
            var sample = AmmoItemBuilder.create().setId(Identifier.parse("tacz:12g")).build();
            if (be.insertAmmo(sample.copyWithCount(3), false) != 3) throw new AssertionError("Plain insert failed");
            sample.set(DataComponents.CUSTOM_NAME, MARK);
            if (be.insertAmmo(sample.copyWithCount(17), false) != 17) throw new AssertionError("Named insert failed");
        });
        context.waitFor(mc -> !mc.player.isCreative() && persistedHand(mc.player.getMainHandItem()));
        var save = world.getWorldSave();
        return context.computeOnClient(mc -> {
            var sm = Objects.requireNonNull(TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).orElseThrow().getAnimationStateMachine());
            sm.setExitingTime(60_000);
            if (sm.getExitingTime() <= GunTime.nowMillis()) throw new AssertionError("Missing put-away deadline before logout");
            return new Saved(save, board, storage, Objects.requireNonNull(CommonNetworkCache.INSTANCE.getGunIndex(GUN)), sm, GunTime.epoch());
        });
    }

    static void reopen(ClientGameTestContext context, Saved saved) {
        context.runOnClient(mc -> {
            if (saved.epoch == GunTime.epoch() || saved.machine.getExitingTime() != -1) throw new AssertionError("Old put-away deadline survived logout: epoch="
                    + saved.epoch + " -> " + GunTime.epoch() + ", deadline=" + saved.machine.getExitingTime());
        });
        try (var world = saved.save.open()) {
            GunMod.LOGGER.info("PORT_SMOKE_CLIENT: rejoined saved world, waiting for new snapshot and persisted stacks");
            // Presence alone could be a left-over from the memory connection. Require a new parsed network index.
            context.waitFor(mc -> CommonNetworkCache.INSTANCE.getGunIndex(GUN) != null && CommonNetworkCache.INSTANCE.getGunIndex(GUN) != saved.index
                    && persistedHand(mc.player.getMainHandItem()) && TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).isPresent(), 600);
            context.waitFor(mc -> mc.level.getBlockEntity(saved.board) instanceof GunPegboardBlockEntity board && board.getColor() == 0x4088bb
                    && board.getRotation(6) == 1 && !board.getItem(6).isEmpty()
                    && mc.level.getBlockEntity(saved.board.east()) instanceof GunDisplayCaseBlockEntity cabinet
                    && cabinet.getItem(9).getItem() instanceof IGun g && ExternalRigSmoke.MESH.equals(g.getGunDisplayId(cabinet.getItem(9))));
            GunMod.LOGGER.info("PORT_SMOKE_CLIENT: rejoined with fresh snapshot, saved hand and display racks");
            checkOneShot(context);
            checkStock(context, world, saved.storage);
        }
        GunMod.LOGGER.info("PORT_SMOKE_CLIENT: reopened same save; new snapshot, one action, saved displays and survival storage transfer passed");
    }

    private static boolean persistedHand(ItemStack stack) {
        return stack.getItem() instanceof IGun gun && GUN.equals(gun.getGunId(stack)) && GRIP.equals(gun.getAttachmentId(stack, AttachmentType.GRIP))
                && gun.getFireMode(stack) == FireMode.SEMI && gun.getCurrentAmmoCount(stack) == 2 && gun.hasBulletInBarrel(stack);
    }

    private static volatile UUID observedPlayer;
    private static final AtomicInteger clientFire = new AtomicInteger(), serverFire = new AtomicInteger();
    static {
        GunFirePostEvent.EVENT.register(e -> {
            if (e.getShooter().getUUID().equals(observedPlayer))
                (e.getLogicalSide() == LogicalSide.CLIENT ? clientFire : serverFire).incrementAndGet();
        });
    }

    private static void checkOneShot(ClientGameTestContext context) {
        context.waitFor(mc -> mc.player.tickCount > 25 && IGunOperator.fromLivingEntity(mc.player).getSynDrawCoolDown() == 0
                && IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder().clientBaseTimestamp >= 0);
        clientFire.set(0); serverFire.set(0);
        observedPlayer = context.computeOnClient(mc -> mc.player.getUUID());
        context.getInput().lookAt(90, -70);
        try {
            context.getInput().holdKey(ShootKey.SHOOT_KEY);
            try {
                // Both sides must post; the locally predicted event alone is not a successful action.
                context.waitFor(mc -> clientFire.get() > 0 && serverFire.get() > 0);
            } catch (AssertionError e) {
                throw new AssertionError("Rejoined semi action: client/server posts = " + clientFire + "/" + serverFire
                        + context.computeOnClient(mc -> ", key=" + ShootKey.SHOOT_KEY.isDown() + ", input=" + com.tacz.guns.util.InputExtraCheck.isInGame()), e);
            } finally {
                context.getInput().releaseKey(ShootKey.SHOOT_KEY);
            }
            context.waitFor(mc -> mc.player.getMainHandItem().getItem() instanceof IGun g && g.getCurrentAmmoCount(mc.player.getMainHandItem()) == 1);
            context.waitTicks(4); // allow any erroneous echo to reach the local shooter
            if (clientFire.get() != 1 || serverFire.get() != 1) throw new AssertionError("Wrong post count: " + clientFire + "/" + serverFire);
        } finally {
            observedPlayer = null;
        }
    }

    private static void checkStock(ClientGameTestContext context, TestSingleplayerContext world, BlockPos storage) {
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var be = (AmmoStorageBlockEntity) player.level().getBlockEntity(storage);
            if (be == null || be.getContents().size() != 2 || !be.stillValid(player)) throw new AssertionError("Storage lost on disk or out of reach");
            player.openMenu(be);
        });
        context.waitForScreen(AmmoStorageScreen.class);
        context.waitFor(mc -> mc.player.containerMenu instanceof AmmoStorageMenu menu && menu.getEntries().size() == 2);
        context.waitTicks(3);
        PackClientSmoke.saveFrame(context, "tacz_ammo_storage_frame");
        context.runOnClient(mc -> {
            var menu = (AmmoStorageMenu) mc.player.containerMenu;
            var entry = menu.getEntries().stream().filter(e -> MARK.equals(e.sample().get(DataComponents.CUSTOM_NAME))).findFirst().orElseThrow();
            if (entry.count() != 17 || menu.getCapacityStacks() != 432) throw new AssertionError("Reloaded menu snapshot lost quantity or tier");
            ClientPlayNetworking.send(new ClientMessageAmmoStorageClick(menu.containerId, entry.id(), AmmoStorageMenu.Action.QUICK_MOVE));
        });
        context.waitFor(mc -> mc.player.containerMenu instanceof AmmoStorageMenu menu && menu.getEntries().size() == 1
                && menu.getEntries().getFirst().count() == 3 && namedCount(mc.player) == 17);
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var be = (AmmoStorageBlockEntity) player.level().getBlockEntity(storage);
            if (be.getContents().size() != 1 || be.getContents().getFirst().count() != 3 || namedCount(player) != 17)
                throw new AssertionError("Named stock not conserved across the actual transfer");
        });
    }

    private static int namedCount(Player player) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (MARK.equals(stack.get(DataComponents.CUSTOM_NAME))) total += stack.getCount();
        }
        return total;
    }
}
