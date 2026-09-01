package cn.sh1rocu.tacz.util.forge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 上游 TACZ <b>1.21.1+ 的 {@code tacz:nbt}</b> 自定义材料类型 —— 跨包合成 bug 的真凶补齐。
 *
 * <p>本类是 <b>26.2 线 {@code 61345c5}</b> 的同形移植（26.1.2 与 26.2 的 Fabric
 * CustomIngredient 接口面一致，逐字搬；已核对 {@link PartialNBTIngredient} 在本分支
 * 使用同一套 {@code CustomIngredient}/{@code CustomIngredientSerializer} 签名）。</p>
 *
 * <h2>证据链（26.2 线 2026-09-01 实机日志 + TaCZPackUpgrader 源码实读）</h2>
 * <ol>
 *   <li>维护者实机日志：{@code {"type":"tacz:nbt","nbt":{...},"partial":true,
 *       "items":"tacz:attachment"}} 解析失败 —— Fabric 的 CODEC 里没有
 *       {@code tacz:nbt} 这个 serializer；</li>
 *   <li>社区枪包升级工具 TaCZPackUpgrader（Upgrader.kt {@code upgradeIngredient}）
 *       把旧包批量转换：{@code forge:nbt → tacz:nbt + partial=false}、
 *       {@code forge:partial_nbt → tacz:nbt + partial=true}，且 {@code items}
 *       写成<b>单个字符串</b>而非数组 —— 与日志逐字吻合；</li>
 *   <li>上游 TACZ 1.21.1+ 注册了该类型（NeoForge 侧 ICustomIngredient），
 *       renov（NeoForge 家族）从上游新代码继承自然无病；本仓移植自 1.20.1 线，
 *       只带了 {@code forge:nbt}/{@code forge:partial_nbt} 两个旧类型。</li>
 * </ol>
 * 「附属包要默认包的枪不行、要自己包的枪行」的不对称由此解释：同一个包里
 * 混着两个时代的配方文件，旧写法（我们认识）能解析，被 upgrader 升级成
 * {@code tacz:nbt} 的那批全灭。
 *
 * <h2>语义</h2>
 * {@code partial=true} = 宽松子集匹配（{@code CustomData.matchedBy}，等价
 * {@link PartialNBTIngredient}）；{@code partial=false} = 严格全等
 * （等价 {@link StrictNBTIngredient} 的 tag 部分）。JSON 归一化
 * （{@code items} 字符串→数组、{@code fabric:type} 判别键）由
 * {@code GunSmithTableIngredient.normalizeLegacy} 负责，本类 codec 只认数组。
 *
 * <p><b>证据级别（AGENTS §2）</b>：机制与证据全部来自 26.2 线的实机日志与上游源码；
 * 本分支这一份是<b>同形移植、编译门通过、实机未验</b>（本沙箱无运行环境）。</p>
 */
public class TaczNbtIngredient implements CustomIngredient {
    private final Set<Item> items;
    private final CompoundTag nbt;
    private final boolean partial;

    protected TaczNbtIngredient(Set<Item> items, CompoundTag nbt, boolean partial) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Cannot create a TaczNbtIngredient with no items");
        }
        this.items = Collections.unmodifiableSet(items);
        this.nbt = nbt;
        this.partial = partial;
    }

    @Override
    public boolean test(@Nullable ItemStack input) {
        if (input == null || !items.contains(input.getItem())) {
            return false;
        }
        CustomData customData = input.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }
        if (partial) {
            return customData.matchedBy(nbt);
        }
        return customData.copyTag().equals(nbt);
    }

    @Override
    public Stream<Holder<Item>> items() {
        return items.stream().map(BuiltInRegistries.ITEM::wrapAsHolder);
    }

    /**
     * 材料格显示带上要求 NBT 的物品（枪/配件按 GunId/AttachmentId 正确渲染），
     * 而不是裸基础物品 —— 与 {@link PartialNBTIngredient#display()} 同一理由。
     */
    @Override
    public SlotDisplay display() {
        return new SlotDisplay.Composite(items.stream()
                .map(item -> {
                    ItemStack stack = new ItemStack(item);
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt.copy()));
                    return (SlotDisplay) new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(stack));
                })
                .toList());
    }

    @Override
    public boolean requiresTesting() {
        return true;
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    public static final Identifier ID = Identifier.fromNamespaceAndPath("tacz", "nbt");

    public static class Serializer implements CustomIngredientSerializer<TaczNbtIngredient> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public Identifier getIdentifier() {
            return ID;
        }

        @Override
        public MapCodec<TaczNbtIngredient> getCodec() {
            return RecordCodecBuilder.mapCodec(codec -> codec.group(
                    BuiltInRegistries.ITEM.holderByNameCodec().listOf().fieldOf("items").forGetter(ing ->
                            ing.items.stream().map(BuiltInRegistries.ITEM::wrapAsHolder).toList()),
                    CustomData.COMPOUND_TAG_CODEC.fieldOf("nbt").forGetter(ing -> ing.nbt),
                    Codec.BOOL.optionalFieldOf("partial", false).forGetter(ing -> ing.partial)
            ).apply(codec, (holders, tag, partial) -> new TaczNbtIngredient(
                    holders.stream().map(Holder::value).collect(Collectors.toSet()), tag, partial)));
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, TaczNbtIngredient> getStreamCodec() {
            return StreamCodec.composite(
                    ByteBufCodecs.holderRegistry(Registries.ITEM).apply(ByteBufCodecs.list()),
                    ing -> ing.items.stream().map(BuiltInRegistries.ITEM::wrapAsHolder).toList(),
                    ByteBufCodecs.TRUSTED_COMPOUND_TAG,
                    ing -> ing.nbt,
                    ByteBufCodecs.BOOL,
                    ing -> ing.partial,
                    (holders, tag, partial) -> new TaczNbtIngredient(
                            holders.stream().map(Holder::value).collect(Collectors.toSet()), tag, partial)
            );
        }
    }
}
