package com.tacz.guns.block.entity;

import com.mojang.authlib.GameProfile;
import com.tacz.guns.GunMod;
import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.config.common.OtherConfig;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.tacz.guns.block.TargetBlock.OUTPUT_POWER;
import static com.tacz.guns.block.TargetBlock.STAND;

public class TargetBlockEntity extends BlockEntity implements Nameable {
    public static final BlockEntityType<TargetBlockEntity> TYPE = new BlockEntityType<>(TargetBlockEntity::new, Set.of(ModBlocks.TARGET));
    /** 标靶复位时间，暂定为 5 秒。 */
    private static final int RESET_TIME = 5 * 20;
    private static final String OWNER_PROFILE_TAG = "OwnerProfile";
    private static final String LEGACY_OWNER_TAG = "Owner";
    private static final String OWNER_UUID_TAG = "owner_uuid";
    private static final String OWNER_NAME_TAG = "owner_name";
    private static final String CUSTOM_NAME_TAG = "CustomName";
    public float rot = 0;
    public float oRot = 0;
    private @Nullable ResolvableProfile owner;
    // Dynamic UUID profiles have an empty partial name; retain the legacy display name.
    private @Nullable String ownerNameHint;
    private @Nullable Component name;
    private boolean profileResolveAttempted;
    private long profileLookupGeneration;

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

    /** 保留原有 GameProfile 接口；名字未解析时返回原版生成的临时档案。 */
    @Nullable
    public GameProfile getOwner() {
        if (this.owner == null) {
            return null;
        }
        GameProfile partial = this.owner.partialProfile();
        if (partial.name().isEmpty() && this.ownerNameHint != null && !this.ownerNameHint.isEmpty()) {
            return new GameProfile(partial.id(), this.ownerNameHint, partial.properties());
        }
        return partial;
    }

    @Nullable
    public ResolvableProfile getOwnerProfile() {
        return this.owner;
    }

    public void setOwner(@Nullable GameProfile owner) {
        if (owner == null) {
            this.setOwnerProfile(null, null);
            return;
        }
        // 无纹理的档案仍须按 UUID 查询；createResolved 会认为其纹理已经完整。
        ResolvableProfile profile = owner.properties().containsKey("textures")
                ? ResolvableProfile.createResolved(owner)
                : ResolvableProfile.createUnresolved(owner.id());
        this.setOwnerProfile(profile, owner.name());
    }

    public void setOwnerName(@Nullable String ownerName) {
        this.setOwnerProfile(ownerName == null || ownerName.isEmpty()
                ? null : ResolvableProfile.createUnresolved(ownerName), ownerName);
    }

    private void setOwnerProfile(@Nullable ResolvableProfile owner, @Nullable String ownerNameHint) {
        this.owner = owner;
        this.ownerNameHint = owner == null ? null : ownerNameHint;
        this.profileLookupGeneration++;
        this.profileResolveAttempted = false;
        this.refresh();
        this.resolveOwnerProfile();
    }

    @Override
    public void setLevel(Level level) {
        if (this.level != level) {
            this.profileLookupGeneration++;
            this.profileResolveAttempted = false;
        }
        super.setLevel(level);
        this.resolveOwnerProfile();
    }

    @Override
    public void setRemoved() {
        this.profileLookupGeneration++;
        this.profileResolveAttempted = false;
        super.setRemoved();
    }

    /**
     * 在 IO 线程补全真正带 textures 的档案，回到服务器线程后再同步。
     * 26.3 的 SkinManager 只读取已有纹理属性，单独补 UUID 不会加载玩家皮肤。
     * 客户端也可通过原版 PlayerSkinRenderCache 解析未完成的档案。
     */
    private void resolveOwnerProfile() {
        if (this.profileResolveAttempted || !(this.owner instanceof ResolvableProfile.Dynamic requested)
                || !(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        this.profileResolveAttempted = true;
        long generation = this.profileLookupGeneration;
        String requestedName = requested.name().orElse(null);
        UUID requestedId = requested.partialProfile().id();
        String label = requestedName != null ? requestedName : requestedId.toString();
        if (requestedName != null && !StringUtil.isValidPlayerName(requestedName)) {
            GunMod.LOGGER.warn("[TaCZ] Cannot resolve target owner profile '{}': invalid player name", label);
            return;
        }

        MinecraftServer server = serverLevel.getServer();
        var resolver = server.services().profileResolver();
        Util.nonCriticalIoPool().execute(() -> {
            Optional<GameProfile> resolved;
            try {
                resolved = requestedName != null ? resolver.fetchByName(requestedName) : resolver.fetchById(requestedId);
            } catch (RuntimeException exception) {
                server.execute(() -> {
                    if (this.isCurrentLookup(requested, serverLevel, generation)) {
                        this.profileResolveAttempted = false;
                        GunMod.LOGGER.warn("[TaCZ] Target owner profile lookup for '{}' failed", label, exception);
                    }
                });
                return;
            }
            server.execute(() -> {
                // 方块可能已被破坏、重载或改了主人，不能让旧异步结果覆盖新状态。
                if (!this.isCurrentLookup(requested, serverLevel, generation)) {
                    return;
                }
                if (resolved.isEmpty()) {
                    GunMod.LOGGER.warn("[TaCZ] Could not resolve target owner profile '{}'; leaving it unresolved", label);
                    return;
                }
                GameProfile complete = resolved.get();
                if (!complete.properties().containsKey("textures")) {
                    // 没有纹理可能是服务器离线或玩家使用默认皮肤；保留动态档案，让客户端也能尝试解析。
                    return;
                }
                this.owner = ResolvableProfile.createResolved(complete);
                this.ownerNameHint = complete.name();
                this.refresh();
            });
        });
    }

    private boolean isCurrentLookup(ResolvableProfile requested, ServerLevel serverLevel, long generation) {
        return !this.isRemoved() && this.level == serverLevel && this.owner == requested
                && this.profileLookupGeneration == generation;
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.ownerNameHint = input.getStringOr(OWNER_NAME_TAG, "");
        this.owner = input.read(OWNER_PROFILE_TAG, ResolvableProfile.CODEC)
                .orElseGet(() -> this.readLegacyOwner(input));
        this.profileLookupGeneration++;
        this.profileResolveAttempted = false;

        String customName = input.getStringOr(CUSTOM_NAME_TAG, "");
        this.name = null;
        if (!customName.isEmpty()) {
            try {
                this.name = net.minecraft.network.chat.ComponentSerialization.CODEC
                        .parse(com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(customName))
                        .result().orElse(Component.empty());
            } catch (RuntimeException exception) {
                GunMod.LOGGER.warn("[TaCZ] Failed to read target custom name", exception);
            }
        }
        // 已挂到世界的实体也可能被重新读取；正常区块加载则在 setLevel 中启动。
        this.resolveOwnerProfile();
    }

    @Nullable
    private ResolvableProfile readLegacyOwner(ValueInput input) {
        String ownerName = input.getStringOr(OWNER_NAME_TAG, "");
        UUID uuid = tryParseUuid(input.getStringOr(OWNER_UUID_TAG, ""));
        if (uuid != null) {
            return ResolvableProfile.createUnresolved(uuid);
        }
        if (!ownerName.isEmpty()) {
            return ResolvableProfile.createUnresolved(ownerName);
        }

        // 上游旧存档的 Owner 是复合标签（Name / Id），不是 UUID 字符串。
        Optional<ValueInput> oldOwner = input.child(LEGACY_OWNER_TAG);
        if (oldOwner.isPresent()) {
            ValueInput legacy = oldOwner.get();
            String legacyName = legacy.getStringOr("Name", "");
            this.ownerNameHint = legacyName;
            int[] id = legacy.getIntArray("Id").orElse(null);
            if (id != null && id.length == 4) {
                return ResolvableProfile.createUnresolved(UUIDUtil.uuidFromIntArray(id));
            }
            if (!legacyName.isEmpty()) {
                return ResolvableProfile.createUnresolved(legacyName);
            }
        }
        return null;
    }

    @Nullable
    private static UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.owner != null) {
            GameProfile partial = this.owner.partialProfile();
            // 同时保留旧版扁平键，以便读取旧存档及在无效玩家名无法编码时保留名字。
            String savedName = partial.name().isEmpty() && this.ownerNameHint != null
                    ? this.ownerNameHint : partial.name();
            output.putString(OWNER_NAME_TAG, savedName);
            output.putString(OWNER_UUID_TAG, this.owner instanceof ResolvableProfile.Dynamic && this.owner.name().isPresent()
                    ? "" : partial.id().toString());
            if ((this.owner instanceof ResolvableProfile.Dynamic && this.owner.name().isEmpty())
                    || StringUtil.isValidPlayerName(partial.name())) {
                output.store(OWNER_PROFILE_TAG, ResolvableProfile.CODEC, this.owner);
            }
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
