package me.xjqsh.lrtactical.api.item;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.item.index.ThrowableIndex;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * 可投掷物（手雷类）。
 *
 * <h2>26.2 移植要点</h2>
 * <b>NBT 读取 API 全面变化</b>（字节码确认）：
 * <ul>
 *   <li>{@code CompoundTag#contains(String, int)}（带类型 id 的重载）<b>已移除</b>，
 *       只剩 {@code contains(String)}；</li>
 *   <li>{@code CompoundTag#getString(String)} 现在返回 {@link Optional}，
 *       想要旧行为需用 {@code getStringOr(String, String)}。</li>
 * </ul>
 * 因此上游的 {@code nbt.contains(ID_TAG, Tag.TAG_STRING)} + {@code nbt.getString(...)}
 * 两步写法，在此合并为一次 {@code getStringOr}，并对空串做判断 ——
 * 与本仓库 {@code GunItemDataAccessor} 的既有写法保持一致。
 */
public interface IThrowable extends ICustomItem {
    String ID_TAG = "ThrowableId";
    String OVERRIDE_DISPLAY_ID = "DisplayId";
    Identifier EMPTY = Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "empty");

    @Nullable
    static IThrowable of(ItemStack stack) {
        if (stack.getItem() instanceof IThrowable item) {
            return item;
        }
        return null;
    }

    @Override
    default Identifier getId(ItemStack stack) {
        return readId(stack, ID_TAG).orElse(EMPTY);
    }

    @Override
    default Identifier getDisplayId(ItemStack stack) {
        return readId(stack, OVERRIDE_DISPLAY_ID).orElseGet(() -> getId(stack));
    }

    /**
     * 从 {@code CUSTOM_DATA} 里读一个 Identifier 字段。
     *
     * @return 字段缺失或不是合法 Identifier 时返回 {@link Optional#empty()}
     */
    private static Optional<Identifier> readId(ItemStack stack, String key) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return Optional.empty();
        }
        CompoundTag nbt = customData.copyTag();
        String raw = nbt.getStringOr(key, "");
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        // tryParse 对非法字符串返回 null，此时视为「没有」而不是抛异常
        return Optional.ofNullable(Identifier.tryParse(raw));
    }

    @Override
    default void setId(ItemStack stack, Identifier id) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> nbt.putString(ID_TAG, id.toString()));
        applyMaxStackSize(stack);
    }

    /**
     * 按数据包定义写入 {@code minecraft:max_stack_size} 组件 —— 修复「手雷无法堆叠」。
     *
     * <h2>问题根因（26.2 破坏性变更）</h2>
     * 上游靠覆写 {@code Item#getMaxStackSize(ItemStack)} 让「同一个物品、不同 NBT」
     * 各有各的堆叠上限。<b>26.2 的 {@code Item} 已经没有这个重载</b>（字节码确认：
     * 只剩 {@code getDefaultMaxStackSize()I}），移植时该覆写被删掉了，
     * 但没有任何东西接手它的职责 —— 于是所有手雷永远停在
     * {@code ThrowableItem} 构造器里的 {@code stacksTo(1)}。
     *
     * <p>26.2 里真正决定上限的是 {@code DataComponents.MAX_STACK_SIZE} 组件
     * （字节码确认 {@code ItemInstance#getMaxStackSize} 就是
     * {@code getOrDefault(MAX_STACK_SIZE, 1)}）。
     *
     * <h2>为什么写在 {@code setId} 里</h2>
     * {@code setId} 是投掷物物品堆<b>获得身份的唯一入口</b>
     * （{@code ThrowableIndex#createItemStack}、创造栏、未来的合成/给予都经由此处），
     * 因此覆盖面等价于上游的物品级覆写。本仓库 TACZ 侧的
     * {@code AmmoItemDataAccessor#applyMaxStackSize} 解决的是<b>同一个问题</b>，
     * 这里刻意<b>照抄那套已验证的做法</b>，而不是另创一种。
     *
     * <h2>为什么必须夹到 [1, 99]</h2>
     * {@code max_stack_size} 组件的 codec 是 {@code ExtraCodecs.intRange(1, 99)}
     * （字节码确认，上界与 {@code Item.ABSOLUTE_MAX_STACK_SIZE}=99 一致）。
     * 数据包若写了超过 99 的 {@code stack_size}，直接 set 会在<b>序列化/网络同步</b>时
     * 被 codec 拒绝，表现为物品异常甚至断线。宁可少堆，不能崩。
     */
    static void applyMaxStackSize(ItemStack stack) {
        // getThrowableIndex 自身已做 instanceof IThrowable 判断，此处不再重复
        me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableIndex(stack)
                .map(index -> Math.clamp(index.getMaxStackSize(), 1, Item.ABSOLUTE_MAX_STACK_SIZE))
                // 值没变就不写：inventoryTick 每游戏刻都会调到这里，而
                // PatchedDataComponentMap#set 会先 ensureMapOwnership()（可能复制整张 patch 表）。
                // 先比对 ItemInstance#getMaxStackSize（字节码确认它就是读该组件），
                // 把稳态下的开销降为一次读取。
                .filter(size -> size != stack.getMaxStackSize())
                .ifPresent(size -> stack.set(DataComponents.MAX_STACK_SIZE, size));
    }

    @Override
    default boolean isSame(ItemStack i, ItemStack j) {
        IThrowable a = IThrowable.of(i);
        IThrowable b = IThrowable.of(j);
        if (a != null && b != null) {
            return Objects.equals(a.getId(i), b.getId(j));
        }
        if (i.isEmpty() || j.isEmpty()) {
            return i.isEmpty() && j.isEmpty();
        }
        return false;
    }

    @Override
    default Optional<Identifier> getCoolDownId(ItemStack stack) {
        return getThrowableIndex(stack).map(index -> index.getData().getCooldownCategory());
    }

    @Override
    default int getMaxUsingTick(ItemStack stack) {
        return getThrowableIndex(stack).map(index -> index.getData().getPrepareTime()).orElse(0);
    }

    /**
     * 取该物品对应的投掷物索引。
     *
     * <p>查不到（物品没带 id、或数据包里没有该定义）时返回 {@link Optional#empty()}，
     * 调用方据此表现为「可持有但不可投掷」，不会崩溃。
     */
    default Optional<ThrowableIndex<?, ?>> getThrowableIndex(ItemStack stack) {
        return me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableIndex(stack);
    }
}
