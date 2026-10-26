package com.tacz.guns.block.entity;

import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.config.common.OtherConfig;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.init.ModSounds;
import com.tacz.guns.util.TargetOwnerProfiles;
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
import net.minecraft.world.item.component.ResolvableProfile;
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
    public static final BlockEntityType<TargetBlockEntity> TYPE = net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(TargetBlockEntity::new, ModBlocks.TARGET).build();
    /**
     * 标靶复位时间，暂定为 5 秒
     */
    private static final int RESET_TIME = 5 * 20;
    /**
     * 标靶主人名（头像皮肤所属玩家）。<b>只存名字这一个键</b>。
     *
     * <p><b>历史坑（26.2 线 2026-10-26 发现；本线代码在修前除 {@code TYPE} 的构造写法外与之逐字等价）</b>：上游把主人整个写成
     * {@code Owner} 复合标签；移植时 {@code saveAdditional} 改成了扁平键 {@code owner_uuid} /
     * {@code owner_name}，{@code loadAdditional} 的前置判据却仍写着 {@code Owner} ——
     * 这个键从那一刻起再没有任何地方写入，判据恒为假，读取整段被跳过。
     * 又因为客户端的方块实体也是靠 {@code loadAdditional} 吃更新包的，客户端同样永远读不到主人，
     * 头像根本画不出来。现在写入与读取用同一个键，{@code scripts/verify_block_entity_persistence.py}
     * 会在 CI 里守住「判据键必须被同一个类写入、读写键对称」。</p>
     *
     * <p>兼容：旧版本写下的 {@code owner_uuid} 会被忽略（UUID 由客户端按名字重新解析），
     * {@code owner_name} 照常读取；26.2 线存档里的 {@code owner_uuid} + {@code owner_name}
     * 同理，本线写出的只有 {@code owner_name} 的存档在 26.2 线也读得回来（名字-only）。</p>
     */
    private static final String OWNER_NAME_TAG = "owner_name";
    private static final String CUSTOM_NAME_TAG = "CustomName";
    public float rot = 0;
    public float oRot = 0;
    /**
     * 头像档案。类型是原版的 {@link ResolvableProfile}（可以只有名字），<b>不是</b> {@code GameProfile}：
     * 本线的 authlib 7 里 {@code GameProfile} 不接受 {@code null} UUID，
     * 「只有名字」的档案根本造不出来，见 {@link TargetOwnerProfiles}。
     */
    private @Nullable ResolvableProfile owner;
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
    public ResolvableProfile getOwner() {
        return owner;
    }

    public void setOwner(@Nullable ResolvableProfile owner) {
        this.owner = owner;
        // 服务端不解析：UUID 与皮肤由客户端渲染时经 PlayerSkinRenderCache 异步解析（同原版头颅）。
        this.refresh();
    }

    @Override
    public void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        // 与 saveAdditional 写的键保持一致；空串 → null，即没有主人（不再有「判据键从不被写入」的死分支）。
        this.owner = TargetOwnerProfiles.fromName(input.getStringOr(OWNER_NAME_TAG, ""));
        if (!input.getStringOr(CUSTOM_NAME_TAG, "").isEmpty()) {
            this.name = net.minecraft.network.chat.ComponentSerialization.CODEC
                    .parse(com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(input.getStringOr(CUSTOM_NAME_TAG, "")))
                    .result().orElse(Component.empty());
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        if (owner != null) {
            // 只有「按名字创建」的档案才有名字可存（TargetOwnerProfiles 只造这一种）。
            owner.name().ifPresent(ownerName -> output.putString(OWNER_NAME_TAG, ownerName));
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
