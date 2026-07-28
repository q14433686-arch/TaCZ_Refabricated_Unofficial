package me.xjqsh.lrtactical.item.index;

import com.google.gson.JsonElement;
import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.api.index.ICustomItemIndex;
import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import me.xjqsh.lrtactical.item.melee.MeleeWeaponData;
import me.xjqsh.lrtactical.item.melee.MeleeWeaponType;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 一把具体近战武器的索引（如「战术匕首」）。
 *
 * <p>由数据包 {@code data/<ns>/index/melee/<name>.json} 反序列化而来。
 *
 * <h2>26.2 移植要点</h2>
 * <ol>
 *   <li><b>属性修饰不再用 {@code Multimap<Attribute, AttributeModifier>}</b>。
 *       上游靠覆写 {@code Item#getAttributeModifiers(EquipmentSlot, ItemStack)} 返回 Multimap，
 *       但 26.2 已改为 {@code DataComponents.ATTRIBUTE_MODIFIERS} 组件
 *       （类型 {@link ItemAttributeModifiers}，字节码确认）。
 *       故这里预先构建好组件值，在 {@link #createItemStack()} 时写入。
 *       <p><b>顺带说明</b>：上游那个覆写<b>本来就是注释掉的</b>（连同
 *       {@code ToolAction} 一起），也就是说上游 1.21.1 分支的近战武器属性
 *       其实并未生效。本移植把它接通了。</li>
 *   <li><b>{@code Attribute} 要用 {@link Holder} 包装</b>：26.2 的
 *       {@code ItemAttributeModifiers.Builder#add} 第一个参数是
 *       {@code Holder<Attribute>} 而非裸 {@code Attribute}（字节码确认）。
 *       取法为 {@code BuiltInRegistries.ATTRIBUTE.get(Identifier)}，
 *       返回 {@code Optional<Holder.Reference<Attribute>>}。</li>
 *   <li><b>附魔能力不需要反射</b>。上游用 {@code Class.forName("...Enchantable")}
 *       + 遍历候选组件的方式兜底，那是因为它要兼容多个子版本。
 *       26.2 上 {@code DataComponents.ENCHANTABLE} 与
 *       {@code net.minecraft.world.item.enchantment.Enchantable}
 *       <b>都确认存在</b>（字节码核实，{@code Enchantable} 是
 *       {@code record Enchantable(int value)}），因此直接强类型调用 ——
 *       反射兜底在这里只会掩盖错误。</li>
 * </ol>
 */
public class MeleeWeaponIndex<T extends MeleeWeaponData> implements ICustomItemIndex {
    private final MeleeWeaponType<T> type;
    private final Item baseItem;
    private final T data;
    private final Identifier id;
    private final String name;
    @Nullable
    private final String tooltip;
    private final ItemAttributeModifiers defaultModifiers;

    private MeleeWeaponIndex(MeleeWeaponType<T> type, T data, String name, @Nullable String tooltip,
                             Identifier id, Item baseItem) {
        this.type = type;
        this.baseItem = baseItem;
        this.data = data;
        this.id = id;
        this.name = name;
        this.tooltip = tooltip;
        this.defaultModifiers = buildModifiers(data, id);
    }

    private static ItemAttributeModifiers buildModifiers(MeleeWeaponData data, Identifier id) {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        boolean any = false;
        for (var entry : data.getRawAttributes().getAttributes()) {
            // 26.2: Registry#get(Identifier) 返回 Optional<Holder.Reference<T>>
            java.util.Optional<Holder.Reference<Attribute>> holder =
                    BuiltInRegistries.ATTRIBUTE.get(entry.id());
            if (holder.isEmpty()) {
                EquipmentMod.LOGGER.error("Unknown attribute {} for melee weapon {}", entry.id(), id);
                continue;
            }
            // 修饰符 id 必须唯一，否则同一属性的多条修饰会互相覆盖
            Identifier modifierId = Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID,
                    "melee_modifier_" + entry.id().getNamespace() + "_" + entry.id().getPath());
            builder.add(holder.get(),
                    new AttributeModifier(modifierId, entry.amount(), entry.operation()),
                    EquipmentSlotGroup.MAINHAND);
            any = true;
        }
        return any ? builder.build() : ItemAttributeModifiers.EMPTY;
    }

    @Nullable
    public static <T extends MeleeWeaponData> MeleeWeaponIndex<T> deserialize(
            @NotNull MeleeWeaponType<T> type, JsonElement data, String name,
            @Nullable String tooltip, Identifier id, Item baseItem) {
        T meleeData = type.serializer().parse(data);
        if (meleeData == null) {
            return null;
        }
        return new MeleeWeaponIndex<>(type, meleeData, name, tooltip, id, baseItem);
    }

    public ItemAttributeModifiers getDefaultModifiers() {
        return defaultModifiers;
    }

    public T getData() {
        return data;
    }

    public MeleeWeaponType<T> getType() {
        return type;
    }

    @Nullable
    public String getTooltip() {
        return tooltip;
    }

    @Override
    public int getMaxStackSize() {
        // 近战武器一律不可堆叠
        return 1;
    }

    @Override
    public String getDescriptionId() {
        return name;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Item getBaseItem() {
        return baseItem;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack stack = new ItemStack(baseItem);
        if (stack.getItem() instanceof IMeleeWeapon iMeleeWeapon) {
            iMeleeWeapon.setId(stack, this.getId());
        }
        applyDataComponents(stack, true);
        return stack;
    }

    /**
     * 把 26.2 物品组件写入物品栈。
     *
     * <p>创造栏走 {@link #createItemStack()}，但工作台配方的 {@code result.type=custom}
     * 是由 TACZ 的通用 {@code CraftingHelper#getItemStack} 构造的裸栈，只带
     * {@code CUSTOM_DATA}，不会经过本类。于是真实刀包合成出来的刀虽然有
     * {@code MeleeWeaponId}，却缺少攻击力/攻速/耐久/工具组件，表现为“所有刀伤害都一样”。
     * {@code MeleeItem#inventoryTick} 会用本方法为这些裸栈自愈。</p>
     *
     * @param freshStack 是否是刚创建的新栈；只有新栈才把 DAMAGE 初始化为 0，避免自愈时修复已损坏武器。
     */
    public void applyDataComponents(ItemStack stack, boolean freshStack) {
        if (defaultModifiers != ItemAttributeModifiers.EMPTY) {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, defaultModifiers);
        }
        if (data.getEnchantmentValue() > 0) {
            stack.set(DataComponents.ENCHANTABLE, new Enchantable(data.getEnchantmentValue()));
        }
        if (data.getMaxDurability() > 0) {
            stack.set(DataComponents.MAX_DAMAGE, data.getMaxDurability());
            if (freshStack || stack.get(DataComponents.DAMAGE) == null) {
                stack.set(DataComponents.DAMAGE, 0);
            }
        }
        data.getTool().applyTo(stack);
    }
}
