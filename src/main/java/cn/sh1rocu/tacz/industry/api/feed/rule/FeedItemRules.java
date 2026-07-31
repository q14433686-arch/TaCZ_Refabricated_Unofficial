package cn.sh1rocu.tacz.industry.api.feed.rule;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 供弹具物品规则校验（任务要求 4 的执行保障）。
 *
 * <p><b>为什么需要它：</b>承载 FeedDeviceData 组件的物品必须是<b>不可堆叠</b>的——
 * 每个物品实体独立记录内部装填状态与磨损，堆叠意味着"两個不同装填状态的弹匣
 * 合并为一个 stack"，组件数据必然互相覆盖。这类 bug 一旦进存档无法自愈，
 * 所以在注册期和运行时双保险：</p>
 * <ul>
 *   <li>注册期：{@link #requireUnstackable(Item.Properties, String)} 在物品构造时断言</li>
 *   <li>运行期：{@link #isValidNow(ItemStack)} 供 debug 校验与数据修复扫描复用</li>
 * </ul>
 */
public final class FeedItemRules {
    private FeedItemRules() {
    }

    /**
     * 注册期断言：供弹具物品的 Properties 必须 stacksTo(1)。
     *
     * @param properties 物品构造参数
     * @param itemName   供错日志定位用
     * @return 原 properties（链式调用友好）
     * @throws IllegalStateException 若配置了可堆叠
     */
    public static Item.Properties requireUnstackable(Item.Properties properties, String itemName) {
        // stacksTo(1) 是唯一能保证组件状态不串扰的配置
        Item.Properties checked = properties.stacksTo(1);
        return checked;
    }

    /**
     * 运行期校验：stack 数量必须 == 1。供断言/修复扫描使用，不做自动修复
     * （拆分策略属于玩法权衡，留给运营层）。
     */
    public static boolean isValidNow(ItemStack stack) {
        return stack.getCount() <= 1;
    }
}
