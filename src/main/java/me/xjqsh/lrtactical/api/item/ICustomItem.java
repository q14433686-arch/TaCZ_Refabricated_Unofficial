package me.xjqsh.lrtactical.api.item;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * 自定义物品接口 —— 用「同一个物品 id + NBT」承载多种不同内容。
 *
 * <p>这是本模组数据驱动的基础：所有手雷共用 {@code lrtactical:throwable} 这一个物品，
 * 具体是哪一种由 NBT 里的 id 决定。
 *
 * <h2>26.2 移植说明</h2>
 * <ul>
 *   <li>{@code ResourceLocation} → {@link Identifier}；</li>
 *   <li>上游的 {@code shouldBlockAttack/Use/PickBlock} 三个方法依赖 NeoForge 的
 *       {@code InputEvent.InteractionKeyMappingTriggered}，Fabric 无对应事件，
 *       <b>暂不移植</b>（它们只影响「持该物品时屏蔽左右键默认行为」的手感，
 *       不影响核心功能）。待客户端输入层移植时再一并处理，
 *       <b>不</b>先留空方法占位 —— 空实现会让调用方误以为功能已生效。</li>
 *   <li>{@code getAttackCoolDown(stack, MeleeAction)} 属近战子系统，随该模块一并移植。</li>
 * </ul>
 */
public interface ICustomItem {
    /**
     * 物品的自定义 id（决定它「是什么」）。
     */
    Identifier getId(ItemStack stack);

    /**
     * 用于显示（模型/材质）的 id，默认与 {@link #getId} 相同。
     * 允许内容包让同一种手雷显示成不同外观。
     */
    default Identifier getDisplayId(ItemStack stack) {
        return getId(stack);
    }

    void setId(ItemStack stack, Identifier id);

    /**
     * 两个物品堆是否「同一种东西」—— 按自定义 id 比较，而非按物品类型。
     */
    boolean isSame(ItemStack stack1, ItemStack stack2);

    /**
     * 冷却分组 id。多种手雷可共享一个冷却，避免「换一种手雷就能立刻再扔」。
     *
     * @see me.xjqsh.lrtactical.capability.CustomItemCoolDowns
     */
    default Optional<Identifier> getCoolDownId(ItemStack stack) {
        return Optional.empty();
    }

    /**
     * 最大使用时长（tick），用于 HUD 上的使用进度条。
     */
    default int getMaxUsingTick(ItemStack stack) {
        return 0;
    }

    /**
     * 切入（举起）时间，此期间不可攻击或使用。
     */
    default int getDrawTime(ItemStack stack) {
        return 0;
    }

    default int getPutAwayTime(ItemStack stack) {
        return 0;
    }

    default boolean blockOffhandRendering(ItemStack stack) {
        return true;
    }
}
