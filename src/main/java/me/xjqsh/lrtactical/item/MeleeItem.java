package me.xjqsh.lrtactical.item;

import com.tacz.guns.api.item.IAnimationItem;
import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 近战武器物品 —— 一个物品承载所有刀，具体是哪一把由 NBT 决定。
 *
 * <h2>26.2 移植要点：上游的四个覆写<b>全部删除</b>（方法已不存在）</h2>
 * 按 PORTING_NOTES 9.1 的检查项，每删一个都必须回答「原职责现在谁来干」：
 *
 * <table border="1">
 *   <tr><th>上游覆写</th><th>26.2 状况</th><th>现由谁承担</th></tr>
 *   <tr><td>{@code isEnchantable(ItemStack)}</td><td><b>已不存在</b></td>
 *       <td>{@code DataComponents.ENCHANTABLE} 组件</td></tr>
 *   <tr><td>{@code getEnchantmentValue(ItemStack)}</td><td><b>已不存在</b></td>
 *       <td>同上（{@code Enchantable(int value)} 的 value）</td></tr>
 *   <tr><td>{@code getMaxDamage(ItemStack)}</td><td><b>已不存在</b></td>
 *       <td>{@code DataComponents.MAX_DAMAGE} 组件</td></tr>
 *   <tr><td>{@code isDamageable(ItemStack)}</td><td><b>已不存在</b></td>
 *       <td>由「有无 MAX_DAMAGE 组件」隐式决定</td></tr>
 *   <tr><td>{@code getAttributeModifiers(...)}</td><td><b>已不存在</b>
 *       （且上游自己就注释掉了）</td>
 *       <td>{@code DataComponents.ATTRIBUTE_MODIFIERS} 组件</td></tr>
 * </table>
 *
 * 这些组件全部在 {@code MeleeWeaponIndex#createItemStack} 里按数据包写入，
 * 所以职责有明确接手方，不是静默丢失。
 *
 * <p><b>{@code getDescriptionId(ItemStack)} 同样已不存在</b>，
 * 改为覆写 {@link #getName(ItemStack)} —— 与 {@code ThrowableItem}
 * 和 TACZ 的 {@code AbstractGunItem} 做法一致。
 *
 * <h2>为什么实现 {@code IAnimationItem}</h2>
 * 这是 TACZ 的接口，26.2 上<b>只要求一个 {@code isSame(ItemStack, ItemStack)}</b>
 * （字节码确认），用于判断「换手时要不要重置动画状态机」。
 * 它<b>与渲染层无耦合</b>，因此即便本移植跳过自定义渲染器也应当实现 ——
 * 否则 TACZ 的动画状态机会把「切换到另一把刀」误判为同一把。
 */
public class MeleeItem extends Item implements IAnimationItem, IMeleeWeapon, cn.sh1rocu.tacz.api.extension.IItem {
    public MeleeItem(Properties properties) {
        // 【26.2】不再有 setNoRepair() —— 该方法已随「修复语义反转」一起移除。
        //
        // 1.20/1.21：默认可用同种材料在铁砧修复，要禁止得显式 setNoRepair()。
        // 26.2    ：改由 DataComponents.REPAIRABLE 组件描述「能用哪些物品修复」
        //           （字节码确认 Properties#repairable(Item) 的实现就是
        //            component(REPAIRABLE, new Repairable(HolderSet.direct(...)))）。
        //           这是一张【白名单】—— 不写该组件就意味着本来就不可修复。
        //
        // 因此这里【什么都不做】才是正确的移植：上游 setNoRepair() 的职责
        // 已由「不写 REPAIRABLE 组件」这一默认行为承担，不存在静默失效
        // （按 PORTING_NOTES 9.1 的检查项：方法没了，必须交代谁接手）。
        super(properties.stacksTo(1));
    }

    @Override
    public boolean isSame(ItemStack stack1, ItemStack stack2) {
        return IMeleeWeapon.super.isSame(stack1, stack2);
    }

    /**
     * 为配方/旧存档产生的“裸栈”补写攻击力、攻速、耐久和工具组件。
     *
     * <p>真实 LRTactical 刀包的工作台配方通常只写 {@code MeleeWeaponId}，
     * 不可能提前知道 26.2 需要的 {@code ATTRIBUTE_MODIFIERS}/{@code TOOL} 等组件。
     * 因此必须像 TACZ 子弹和 LRTactical 手雷那样，在服务端 inventory tick 做一次自愈。</p>
     */
    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull ServerLevel level,
                              @NotNull Entity entity, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        this.getMeleeIndex(stack).ifPresent(index -> index.applyDataComponents(stack, false));
    }

    /**
     * 让每把刀显示各自的名字。
     *
     * <p>见类注释：26.2 已无 {@code getDescriptionId(ItemStack)} 重载。
     */
    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return this.getMeleeIndex(stack)
                .<Component>map(index -> Component.translatable(index.getDescriptionId()))
                .orElseGet(() -> super.getName(stack));
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return this.getMeleeIndex(stack).isPresent()
                ? Optional.of(new me.xjqsh.lrtactical.inventory.tooltip.MeleeTooltip(stack))
                : Optional.empty();
    }

    /**
     * 自定义渲染器：接入 TACZ 的 Bedrock 模型 + Lua 动画状态机管线。
     *
     * <p>{@code IItem} 是本仓库为 Fabric 补的扩展接口（NeoForge 侧对应
     * {@code IClientItemExtensions#getCustomRenderer}）。返回的实例会在
     * {@code TaCZFabricClient} 里被登记进 {@code BuiltinItemRendererRegistry}，
     * 再由客户端物品模型 {@code lrtactical:dynamic_item} 的 SpecialModelRenderer 调用。
     *
     * <p><b>没装内容包时不会走到这里</b> —— {@code items/melee.json} 用
     * {@code minecraft:condition} + {@code lrtactical:has_custom_display} 做了分流，
     * 条件为假时直接用原版占位模型。详见 {@code HasCustomDisplayProperty}。
     */
    @Override
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    public cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry.DynamicItemRenderer getCustomRenderer() {
        return me.xjqsh.lrtactical.client.renderer.item.MeleeItemRenderer.INSTANCE.get();
    }

    /**
     * 阻止玩家手臂挥动 —— 挥砍由 Lua 动画状态机负责，vanilla 的摆手会与之打架。
     *
     * <p>与 TACZ 的 {@code AbstractGunItem#tacz$onEntitySwing} 同样处理。
     */
    @Override
    public boolean tacz$onEntitySwing(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        return true;
    }
}
