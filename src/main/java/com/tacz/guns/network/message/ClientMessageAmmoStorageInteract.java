package com.tacz.guns.network.message;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import cn.sh1rocu.tacz.api.extension.IEntityPersistentData;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.util.AmmoStorageHelper;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ClientMessageAmmoStorageInteract implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientMessageAmmoStorageInteract> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("tacz", "client_message_ammo_storage_interact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientMessageAmmoStorageInteract> STREAM_CODEC =
            StreamCodec.of((buf, message) -> encode(message, buf), ClientMessageAmmoStorageInteract::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static final String LAST_PICKUP_TAG = "TacAmmoStorageLastPickup";
    private static final String LAST_ACCUMULATE_TAG = "TacAmmoStorageLastAccumulate";
    private static final String ACCUMULATE_AMMO_TAG = "TacAmmoStorageAccumulateAmmo";
    private static final String ACCUMULATE_COUNT_TAG = "TacAmmoStorageAccumulateCount";
    private static final long PICKUP_COOLDOWN_TICKS = 5L;
    private static final long ACCUMULATE_WINDOW_TICKS = 20L;

    private final BlockPos pos;

    public ClientMessageAmmoStorageInteract(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(ClientMessageAmmoStorageInteract message, RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(message.pos);
    }

    public static ClientMessageAmmoStorageInteract decode(RegistryFriendlyByteBuf buf) {
        return new ClientMessageAmmoStorageInteract(buf.readBlockPos());
    }

    public static void handle(ClientMessageAmmoStorageInteract message, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            if (player == null || !player.isAlive() || player.isSpectator()) {
                return;
            }
            long gameTime = player.level().getGameTime();
            long lastPickup = ((IEntityPersistentData) player).tacz$getPersistentData().getLongOr(LAST_PICKUP_TAG, -1);
            if (lastPickup >= 0 && lastPickup <= gameTime && gameTime - lastPickup < PICKUP_COOLDOWN_TICKS) {
                return;
            }
            if (!player.isWithinBlockInteractionRange(message.pos, 0) || !player.level().hasChunkAt(message.pos)
                    || !(player.pick(player.blockInteractionRange(), 1F, false) instanceof BlockHitResult target)
                    || target.getType() != HitResult.Type.BLOCK || !target.getBlockPos().equals(message.pos)) {
                return;
            }
            ItemStack gunStack = player.getMainHandItem();
            if (!(gunStack.getItem() instanceof IGun gun)) {
                return;
            }
            Identifier ammoId = TimelessAPI.getCommonGunIndex(gun.getGunId(gunStack))
                    .map(index -> index.getGunData().getAmmoId())
                    .orElse(null);
            if (ammoId == null) {
                return;
            }
            BlockEntity blockEntity = player.level().getBlockEntity(message.pos);
            if (!(blockEntity instanceof AmmoStorageBlockEntity storage)) {
                return;
            }
            int amount = AmmoStorageHelper.getStackSize(AmmoStorageHelper.createAmmoStack(ammoId, 1));
            int count = 0;
            for (var stock : storage.getContents()) {
                if (ammoId.equals(AmmoStorageHelper.getAmmoId(stock.sample()))) {
                    count = storage.moveAmmoToInventory(player.getInventory(), 36, stock.sample(), Math.min(amount, stock.sample().getMaxStackSize()));
                    if (count > 0) break;
                }
            }
            ((IEntityPersistentData) player).tacz$getPersistentData().putLong(LAST_PICKUP_TAG, gameTime);
            if (count == 0) {
                player.sendOverlayMessage(Component.translatable("message.tacz.ammo_storage.no_gun_stock"));
                return;
            }
            int accumulatedCount = getAccumulatedCount(player, ammoId, count, gameTime);
            player.sendOverlayMessage(Component.translatable("message.tacz.ammo_storage.picked", AmmoStorageHelper.createAmmoStack(ammoId, 1).getHoverName(), accumulatedCount));
            player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
        });
    }

    private static int getAccumulatedCount(ServerPlayer player, Identifier ammoId, int count, long gameTime) {
        String ammoKey = ammoId.toString();
        long lastAccumulate = ((IEntityPersistentData) player).tacz$getPersistentData().getLongOr(LAST_ACCUMULATE_TAG, -1);
        String lastAmmoKey = ((IEntityPersistentData) player).tacz$getPersistentData().getStringOr(ACCUMULATE_AMMO_TAG, "");
        int accumulated = count;
        if (lastAccumulate >= 0 && lastAccumulate <= gameTime && gameTime - lastAccumulate <= ACCUMULATE_WINDOW_TICKS && ammoKey.equals(lastAmmoKey)) {
            accumulated = (int) Math.min(Integer.MAX_VALUE, (long) count + Math.max(0, ((IEntityPersistentData) player).tacz$getPersistentData().getIntOr(ACCUMULATE_COUNT_TAG, 0)));
        }
        ((IEntityPersistentData) player).tacz$getPersistentData().putLong(LAST_ACCUMULATE_TAG, gameTime);
        ((IEntityPersistentData) player).tacz$getPersistentData().putString(ACCUMULATE_AMMO_TAG, ammoKey);
        ((IEntityPersistentData) player).tacz$getPersistentData().putInt(ACCUMULATE_COUNT_TAG, accumulated);
        return accumulated;
    }
}
