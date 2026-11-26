package com.tacz.guns.block.entity;

import com.mojang.authlib.GameProfile;
import com.tacz.guns.GunMod;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.config.common.OtherConfig;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
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
import java.util.concurrent.CompletableFuture;

import static com.tacz.guns.block.TargetBlock.OUTPUT_POWER;
import static com.tacz.guns.block.TargetBlock.STAND;

public class TargetBlockEntity extends BlockEntity implements Nameable {
    public static final BlockEntityType<TargetBlockEntity> TYPE = net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(TargetBlockEntity::new, ModBlocks.TARGET).build();
    /**
     * 标靶复位时间，暂定为 5 秒
     */
    private static final int RESET_TIME = 5 * 20;
    /** Keep the 26.2 / vanilla skull profile format. Server identity lookup must not
     * freeze a texture snapshot: clients still resolve dynamic UUIDs in their skin cache. */
    private static final String PROFILE_TAG = "profile";
    private static final String ALTERNATE_PROFILE_TAG = "OwnerProfile"; // tacz:read-only-key:OwnerProfile->profile
    /** Historical flat keys; owner_name can also carry a name hint for a UUID-only profile. */
    private static final String LEGACY_OWNER_UUID_TAG = "owner_uuid";
    private static final String LEGACY_OWNER_NAME_TAG = "owner_name";
    /** 上游 1.20.1 写的 GameProfile 复合标签 {@code {Name:"x", Id:"uuid", Properties:{...}}}；只读，不再写。 */
    private static final String LEGACY_OWNER_TAG = "Owner";
    private static final String CUSTOM_NAME_TAG = "CustomName";
    public float rot = 0;
    public float oRot = 0;
    private @Nullable ResolvableProfile owner;
    private String ownerNameHint = "";
    private long lookupGeneration;
    private boolean lookupAttempted;
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
    public ResolvableProfile getOwnerProfile() { return owner; }

    /** Preserve the native public getter, including skin patches and unresolved names. */
    public @Nullable ResolvableProfile getOwner() { return owner; }

    /** Human-readable partial profile; partialProfile() always supplies a non-null UUID. */
    public @Nullable GameProfile getGameProfile() {
        if (owner == null) return null;
        var partial = owner.partialProfile();
        return partial.name().isEmpty() && !ownerNameHint.isEmpty()
                ? new GameProfile(partial.id(), ownerNameHint, partial.properties()) : partial;
    }

    /** A placement/anvil name is not a texture snapshot or an authoritative UUID. */
    public void setOwnerName(@Nullable String name) {
        setOwner(name == null || name.isEmpty() ? null : ResolvableProfile.createUnresolved(name));
    }

    /** Native 26.2 API, including skin patches and the unambiguous setOwner(null). */
    public void setOwner(@Nullable ResolvableProfile owner) {
        replaceOwner(owner, owner == null ? "" : owner.partialProfile().name());
    }

    /** For callers with authlib profiles: only an explicit texture is a finished skin. */
    public void setGameProfile(@Nullable GameProfile profile) {
        if (profile == null) { setOwner(null); return; }
        replaceOwner(profile.properties().containsKey("textures") ? ResolvableProfile.createResolved(profile)
                : ResolvableProfile.createUnresolved(profile.id()), profile.name());
    }

    private void replaceOwner(@Nullable ResolvableProfile owner, String nameHint) {
        this.owner = owner;
        this.ownerNameHint = nameHint;
        invalidateLookup();
        refresh();
        resolveOwnerIdentity();
    }

    @Override public void setLevel(Level level) {
        if (this.level != level) invalidateLookup();
        super.setLevel(level);
        resolveOwnerIdentity();
    }

    @Override public void setRemoved() {
        invalidateLookup();
        super.setRemoved();
    }

    private void invalidateLookup() { lookupGeneration++; lookupAttempted = false; }

    // A rename must not silently give a saved target a different skin. Resolve the account
    // off-thread and keep its UUID, not the texture returned by the server. Explicit skins
    // and patches remain under their author's ownership. No network call from placement/render.
    private void resolveOwnerIdentity() {
        if (lookupAttempted || isRemoved() || !(level instanceof ServerLevel world)
                || !(owner instanceof ResolvableProfile.Dynamic requested)
                || !PlayerSkin.Patch.EMPTY.equals(requested.skinPatch())) return;
        String name = requested.name().orElse(null);
        if (name != null && !StringUtil.isValidPlayerName(name)) return;
        lookupAttempted = true;
        long generation = lookupGeneration;
        lookup(world, name, requested.partialProfile().id()).whenCompleteAsync((result, error) -> {
            if (lookupGeneration != generation || level != world || isRemoved() || owner != requested) return;
            if (error != null) {
                lookupAttempted = false; // a later world attachment may retry, never spin on a tick
                GunMod.LOGGER.warn("Could not resolve target owner {}; keeping original profile", requested.partialProfile(), error);
                return;
            }
            result.ifPresent(found -> {
                if (name == null && found.name().equals(ownerNameHint) && found.id().equals(requested.partialProfile().id())) return;
                owner = ResolvableProfile.createUnresolved(found.id());
                ownerNameHint = found.name();
                refresh();
            });
        }, world.getServer());
    }

    CompletableFuture<Optional<GameProfile>> lookup(ServerLevel world, @Nullable String name, UUID id) {
        var resolver = world.getServer().services().profileResolver();
        return CompletableFuture.supplyAsync(() -> name != null ? resolver.fetchByName(name) : resolver.fetchById(id), Util.nonCriticalIoPool());
    }

    @Override
    public void loadAdditional(ValueInput input) {
        // 主路径：与原版头颅同一个键、同一个 CODEC，兼容「只写名字」「只写 UUID」「两者都有」。
        super.loadAdditional(input);
        invalidateLookup();
        ownerNameHint = input.getString(LEGACY_OWNER_NAME_TAG).orElse("");
        ResolvableProfile loaded = input.read(PROFILE_TAG, ResolvableProfile.CODEC)
                .or(() -> input.read(ALTERNATE_PROFILE_TAG, ResolvableProfile.CODEC)).orElse(null); // tacz:legacy-key: migrate to profile
        if (loaded == null) {
            // 老存档兼容：本分支早期的 owner_uuid/owner_name，以及上游 1.20.1 的 Owner 复合标签。
            // 读到之后下一次保存就会以原版 profile 键覆盖，属于一次性迁移。
            loaded = readLegacyOwner(input);
        }
        this.owner = loaded;
        this.name = readCustomName(input);
        resolveOwnerIdentity();
    }

    /**
     * 读老版本的主人写法。只读不写 —— 保存时统一写 {@link #PROFILE_TAG}。
     *
     * <p>三种历史格式：</p>
     * <ol>
     *   <li>{@code Owner} 复合标签（上游 1.20.1，{@code NbtUtils.writeGameProfile}）；
     *       内含 {@code Name} 与 {@code Id}。</li>
     *   <li>{@code owner_uuid} / {@code owner_name} 两个字符串（本分支 26.2 早期，PR #115）。</li>
     *   <li>两者都没有：没有主人。</li>
     * </ol>
     * <p>解析不出来（名字拼错之类）不算错误：档案仍然是合法的「未解析档案」，
     * 客户端解析不到就退回默认皮肤，与原版头颅一致。</p>
     */
    private @Nullable ResolvableProfile readLegacyOwner(ValueInput input) {
        String name = ownerNameHint;
        String uuidText = getStringOrEmpty(input, LEGACY_OWNER_UUID_TAG);
        UUID id = parseUuid(uuidText);
        Optional<ValueInput> legacyOwner = input.child(LEGACY_OWNER_TAG);
        if (legacyOwner.isPresent()) {
            ValueInput child = legacyOwner.get();
            if (name.isEmpty()) {
                name = getStringOrEmpty(child, "Name");
            }
            if (id == null) {
                id = child.getIntArray("Id").filter(array -> array.length == 4)
                        .map(net.minecraft.core.UUIDUtil::uuidFromIntArray)
                        .orElseGet(() -> parseUuid(getStringOrEmpty(child, "Id")));
            }
        }
        ownerNameHint = name;
        // A name+UUID object decoded without textures becomes a STATIC profile: no skin
        // fetch ever occurs. Keep the name for display and let the native cache resolve the ID.
        if (id != null) return ResolvableProfile.createUnresolved(id);
        return name.isEmpty() ? null : ResolvableProfile.createUnresolved(name);
    }

    /** {@code ValueInput#getString} 遇到类型不匹配的旧存档时可能抛异常，读不到就当空串。 */
    private static String getStringOrEmpty(ValueInput input, String key) {
        try {
            return input.getString(key).orElse("");
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static @Nullable UUID parseUuid(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 读自定义名。
     *
     * <p>主路径是 {@link ComponentSerialization#CODEC}（与 NBT 里的组件写法一致）。老存档是被
     * 「把组件用 JsonOps 编码成字符串再当字符串存」写下的，读回来会变成字面量
     * {@code {"text":"名字"}} —— 那会真的显示成一串 JSON。这里识别这种形态并重新解析，
     * 读不到就返回 {@code null}（无自定义名），不抛异常。</p>
     */
    private static @Nullable Component readCustomName(ValueInput input) {
        Optional<Component> parsed = input.read(CUSTOM_NAME_TAG, ComponentSerialization.CODEC);
        String raw = getStringOrEmpty(input, CUSTOM_NAME_TAG);
        if (raw.startsWith("{\"")) {
            try {
                Component legacy = ComponentSerialization.CODEC
                        .parse(JsonOps.INSTANCE, JsonParser.parseString(raw))
                        .result()
                        .orElse(null);
                // 只有「重解析后确实不同」才采纳：玩家真的把名字起成 {"text":"x"} 这种字面量时，
                // 解析结果与原文一模一样，说明它本来就是一段普通文本，必须原样保留。
                if (legacy != null && !raw.equals(legacy.getString())) {
                    return legacy;
                }
            } catch (RuntimeException ignored) {
                // 不是合法 JSON：按字面量处理，落到下面的 parsed。
            }
        }
        return parsed.orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (owner != null) {
            var name = owner.name();
            boolean canEncode = name.isEmpty() || StringUtil.isValidPlayerName(name.get());
            if (canEncode) output.store(PROFILE_TAG, ResolvableProfile.CODEC, owner);
            // Anvils accept names the profile codec rejects. Preserve those literally,
            // and preserve a known display name alongside an otherwise UUID-only profile.
            if (!canEncode || name.isEmpty() && !ownerNameHint.isEmpty())
                output.putString(LEGACY_OWNER_NAME_TAG, name.orElse(ownerNameHint));
        }
        if (this.name != null) {
            output.storeNullable(CUSTOM_NAME_TAG, ComponentSerialization.CODEC, this.name);
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
