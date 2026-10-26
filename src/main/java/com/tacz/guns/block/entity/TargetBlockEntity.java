package com.tacz.guns.block.entity;

import com.google.gson.JsonObject;
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
import net.minecraft.sounds.SoundSource;
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
    /**
     * 标靶复位时间，暂定为 5 秒
     */
    private static final int RESET_TIME = 5 * 20;
    /**
     * 标靶主人档案的存档键。
     *
     * <p>刻意与<b>原版头颅</b>（{@code SkullBlockEntity}）一模一样：同一个键
     * {@code "profile"}、同一个 {@code ResolvableProfile.CODEC}。这样存档格式、跨版本兼容、
     * 以及「能不能被原版工具读懂」全部跟原版对齐，我们不再维护自己的档案格式。</p>
     *
     * <h2>历史坑（2026-10-27 修复，第二轮）</h2>
     * <p>第一轮修复（PR #115）把主人拆成 {@code owner_uuid} / {@code owner_name} 两个字符串存，
     * 并保留了 {@code Owner} 键的读取兼容。那套东西本质上是在手写「档案序列化 + 服务端补全 +
     * 客户端查皮肤」的半套协议，于是踩了两个必现的运行期崩溃与一类静默失效：</p>
     * <ul>
     *   <li>{@code TargetBlock#setPlacedBy} 里 {@code new GameProfile(null, 名字)} ——
     *       authlib 9 的 {@code GameProfile} 是 record，id/name/properties 都
     *       {@code Objects.requireNonNull}，传 null 必抛 NPE。抛在 {@code BlockItem#place}
     *       中途，于是<b>物品不被消耗</b>、方块却已经放下去了。</li>
     *   <li>{@code TargetMinecart#getGameProfile} 同款写法，但它在<b>客户端每帧</b>被渲染线程调用
     *       ⇒ 改名过的标靶矿车直接把渲染线程崩掉。</li>
     *   <li>服务端「补全 UUID」那一套（{@code nameToIdCache} + 后台线程 + 一次性标志）在离线服 /
     *       拼错名字时只能降级成默认皮肤，还要我们自己维护日志与重试。</li>
     * </ul>
     * <p>现在改成原版的做法：方块实体只存一个 {@code ResolvableProfile}（可以只有名字、也可以
     * 只有 UUID、或两者都有），<b>服务端不解析</b>；客户端通过
     * {@code PlayerSkinRenderCache}（原版头颅用的同一个缓存）异步解析，解析期间与解析失败都
     * 自动用默认皮肤，不需要我们做任何兜底。见 {@code TargetRenderer}。</p>
     */
    private static final String PROFILE_TAG = "profile";
    /** 本分支 26.2 早期版本写下的扁平键（PR #115 之前）；只读，不再写。 */
    private static final String LEGACY_OWNER_UUID_TAG = "owner_uuid";
    private static final String LEGACY_OWNER_NAME_TAG = "owner_name";
    /** 上游 1.20.1 写的 GameProfile 复合标签 {@code {Name:"x", Id:"uuid", Properties:{...}}}；只读，不再写。 */
    private static final String LEGACY_OWNER_TAG = "Owner";
    private static final String CUSTOM_NAME_TAG = "CustomName";
    public float rot = 0;
    public float oRot = 0;
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

    /**
     * 标靶主人档案（可能只有名字，也可能只有 UUID）。
     *
     * <p>返回的是<b>未解析</b>的原版 {@link ResolvableProfile}：服务端不查会话服务，
     * 客户端渲染时由 {@code PlayerSkinRenderCache} 异步解析。没有主人时返回 {@code null}。</p>
     */
    @Nullable
    public ResolvableProfile getOwnerProfile() {
        return this.owner;
    }

    /**
     * 设置标靶主人。
     *
     * <p>调用方必须传 {@link ResolvableProfile}（原版允许「只有名字」），
     * 不要再构造 {@code GameProfile}：authlib 9 的 {@code GameProfile} 不允许 null id/name，
     * 这正是 {@code setPlacedBy} NPE 的根因。</p>
     */
    public void setOwner(@Nullable ResolvableProfile owner) {
        this.owner = owner;
        this.refresh();
    }

    @Override
    public void loadAdditional(ValueInput input) {
        // 主路径：与原版头颅同一个键、同一个 CODEC，兼容「只写名字」「只写 UUID」「两者都有」。
        ResolvableProfile loaded = input.read(PROFILE_TAG, ResolvableProfile.CODEC).orElse(null);
        if (loaded == null) {
            // 老存档兼容：本分支早期的 owner_uuid/owner_name，以及上游 1.20.1 的 Owner 复合标签。
            // 读到之后下一次保存就会以原版 profile 键覆盖，属于一次性迁移。
            loaded = readLegacyOwner(input);
        }
        this.owner = loaded;
        this.name = readCustomName(input);
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
    private static @Nullable ResolvableProfile readLegacyOwner(ValueInput input) {
        String name = getStringOrEmpty(input, LEGACY_OWNER_NAME_TAG);
        String uuidText = getStringOrEmpty(input, LEGACY_OWNER_UUID_TAG);
        Optional<ValueInput> legacyOwner = input.child(LEGACY_OWNER_TAG);
        if (legacyOwner.isPresent()) {
            ValueInput child = legacyOwner.get();
            if (name.isEmpty()) {
                name = getStringOrEmpty(child, "Name");
            }
            if (uuidText.isEmpty()) {
                uuidText = getStringOrEmpty(child, "Id");
            }
        }
        return createLegacyProfile(name, parseUuid(uuidText));
    }

    /**
     * 把「名字 + 可选 UUID」拼成 {@link ResolvableProfile}。
     *
     * <p>两者都有时走 {@code ResolvableProfile.CODEC}（它接受 {@code {name, id, properties}} 形态），
     * 这样 UUID 与名字都能保留、离线也能解析；CODEC 不认（字段缺失等）时退化成只用名字、
     * 再退化成只用 UUID —— 全都失败返回 {@code null}，绝不抛异常。</p>
     */
    private static @Nullable ResolvableProfile createLegacyProfile(String name, @Nullable UUID id) {
        if (!name.isEmpty() && id != null) {
            JsonObject json = new JsonObject();
            json.addProperty("name", name);
            json.addProperty("id", id.toString());
            Optional<ResolvableProfile> parsed = ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, json).result();
            if (parsed.isPresent()) {
                return parsed.get();
            }
        }
        if (!name.isEmpty()) {
            return ResolvableProfile.createUnresolved(name);
        }
        if (id != null) {
            return ResolvableProfile.createUnresolved(id);
        }
        return null;
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
        if (this.owner != null) {
            output.store(PROFILE_TAG, ResolvableProfile.CODEC, this.owner);
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
