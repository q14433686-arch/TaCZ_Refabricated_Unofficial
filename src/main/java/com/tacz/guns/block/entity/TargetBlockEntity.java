package com.tacz.guns.block.entity;

import com.mojang.authlib.GameProfile;
import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.config.common.OtherConfig;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.Set;

import static com.tacz.guns.block.TargetBlock.OUTPUT_POWER;
import static com.tacz.guns.block.TargetBlock.STAND;

public class TargetBlockEntity extends BlockEntity implements Nameable {
    public static final BlockEntityType<TargetBlockEntity> TYPE = new BlockEntityType<>(TargetBlockEntity::new, Set.of(ModBlocks.TARGET));
    /**
     * 标靶复位时间，暂定为 5 秒
     */
    private static final int RESET_TIME = 5 * 20;
    private static final String OWNER_TAG = "Owner";
    private static final String CUSTOM_NAME_TAG = "CustomName";
    public float rot = 0;
    public float oRot = 0;
    private @Nullable GameProfile owner;
    private @Nullable Component name;

    public TargetBlockEntity(BlockPos pos, BlockState blockState) {
        super(TYPE, pos, blockState);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TargetBlockEntity pBlockEntity) {
        pBlockEntity.oRot = pBlockEntity.rot;
        if (state.getValue(STAND)) {
            pBlockEntity.rot = Math.max(pBlockEntity.rot - 18, 0);
        } else {
            pBlockEntity.rot = Math.min(pBlockEntity.rot + 45, 90);
        }
    }

    @Nullable
    public GameProfile getOwner() {
        return owner;
    }

    public void setOwner(@Nullable GameProfile owner) {
        this.owner = owner;
        // 26.2: SkullBlockEntity.updateGameprofile removed. Just refresh directly.
        this.refresh();
    }

    @Override
    public void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        if (!input.getStringOr(OWNER_TAG, "").isEmpty()) {
            this.owner = new com.mojang.authlib.GameProfile(java.util.UUID.fromString(input.getStringOr("owner_uuid","")), input.getStringOr("owner_name",""));
        }
        if (!input.getStringOr(CUSTOM_NAME_TAG, "").isEmpty()) {
            this.name = net.minecraft.network.chat.ComponentSerialization.CODEC
                    .parse(com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(input.getStringOr(CUSTOM_NAME_TAG, "")))
                    .result().orElse(Component.empty());
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
                if (owner != null) {
            output.putString("owner_uuid", owner.id()!=null?owner.id().toString():"");
            output.putString("owner_name", owner.name()!=null?owner.name():"");
        }
        if (this.name != null) {
            output.putString(CUSTOM_NAME_TAG, net.minecraft.network.chat.ComponentSerialization.CODEC
                    .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, this.name)
                    .result().map(Object::toString).orElse(""));
        }
    }

    @Override
    public Component getName() {
        return this.name != null ? this.name : Component.empty();
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return this.name;
    }

    public void setCustomName(Component name) {
        this.name = name;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }

    public void refresh() {
        this.setChanged();
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    // In Minecraft 26.2+, getRenderBoundingBox() is handled differently
    // Rendering bounds are now typically managed through BlockEntityRenderers

    public void hit(Level level, BlockState state, BlockHitResult hit, boolean isUpperBlock) {
        if (this.level != null && state.getValue(STAND)) {
            BlockPos blockPos = hit.getBlockPos();
            // 如果是击中上方，把状态移动到下方处理
            if (isUpperBlock) {
                blockPos = blockPos.below();
                state = level.getBlockState(blockPos);
            }
            int redstoneStrength = TargetBlock.getRedstoneStrength(hit, isUpperBlock);
            level.setBlock(blockPos, state.setValue(STAND, false).setValue(OUTPUT_POWER, redstoneStrength), Block.UPDATE_ALL);
            level.scheduleTick(blockPos, state.getBlock(), RESET_TIME);
            // 原版的声音传播距离由 volume 决定
            // 当声音大于 1 时，距离为 = 16 * volume
            float volume = OtherConfig.TARGET_SOUND_DISTANCE.get() / 16.0f;
            volume = Math.max(volume, 0);
            level.playSound(null, blockPos, ModSounds.TARGET_HIT, SoundSource.BLOCKS, volume, this.level.getRandom().nextFloat() * 0.1F + 0.9F);
        }
    }
}
