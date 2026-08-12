package me.xjqsh.lrtactical.api;

import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.item.index.ThrowableIndex;
import me.xjqsh.lrtactical.resource.CommonAssetsManager;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Optional;

/**
 * LRTactical 的对外查询入口。
 *
 * <p>当前同时暴露投掷物、近战、消耗品索引及投掷物/近战客户端展示数据。
 */
public final class LrTacticalAPI {
    private LrTacticalAPI() {
    }

    /**
     * 取某个物品堆对应的投掷物定义。
     *
     * @return 该物品不是投掷物、或其 id 在数据包中没有对应定义时返回 empty
     */
    public static Optional<ThrowableIndex<?, ?>> getThrowableIndex(ItemStack stack) {
        if (!(stack.getItem() instanceof IThrowable item)) {
            return Optional.empty();
        }
        return Optional.ofNullable(CommonAssetsManager.get().getThrowableIndex(item.getId(stack)));
    }

    /** 所有已加载的投掷物定义（供创造标签页等遍历）。 */
    public static Collection<ThrowableIndex<?, ?>> getThrowableIndexes() {
        return CommonAssetsManager.get().getThrowableIndexes();
    }

    /**
     * 取某个物品堆对应的近战武器定义。
     *
     * @return 该物品不是近战武器、或其 id 在数据包中没有对应定义时返回 empty
     */
    public static Optional<me.xjqsh.lrtactical.item.index.MeleeWeaponIndex<?>> getMeleeIndex(ItemStack stack) {
        if (!(stack.getItem() instanceof me.xjqsh.lrtactical.api.item.IMeleeWeapon item)) {
            return Optional.empty();
        }
        return Optional.ofNullable(CommonAssetsManager.get().getMeleeIndex(item.getId(stack)));
    }

    /** 所有已加载的近战武器定义。 */
    public static Collection<me.xjqsh.lrtactical.item.index.MeleeWeaponIndex<?>> getMeleeIndexes() {
        return CommonAssetsManager.get().getMeleeIndexes();
    }

    public static Optional<me.xjqsh.lrtactical.item.index.ConsumableIndex> getConsumableIndex(ItemStack stack) {
        if (!(stack.getItem() instanceof me.xjqsh.lrtactical.api.item.IConsumable item)) {
            return Optional.empty();
        }
        return Optional.ofNullable(CommonAssetsManager.get().getConsumableIndex(item.getId(stack)));
    }

    public static Collection<me.xjqsh.lrtactical.item.index.ConsumableIndex> getConsumableIndexes() {
        return CommonAssetsManager.get().getConsumableIndexes();
    }

    // ------------------------------------------------------------------
    // 客户端展示数据（display）
    //
    // 与上面的 index 是【两套独立通道】：
    //   index   -> data/<ns>/index/**      数据包，服务端权威，需网络同步
    //   display -> assets/<ns>/display/**  资源包，纯客户端，可被材质包覆盖
    // 因此查询也分开，不能互相回退。
    //
    // 【为什么按 getDisplayId 而不是 getId 查】
    // ICustomItem 允许「同一种手雷显示成不同外观」（OVERRIDE_DISPLAY_ID），
    // display 走的正是这个 id。上游此处也是 getDisplayId，行为保持一致。
    //
    // 【为什么标 @Environment(CLIENT)】
    // 返回类型引用了只在客户端存在的 display 类。若被服务端代码误调，
    // 在专用服务器上会直接 NoClassDefFoundError —— 加注解让这类误用在
    // 开发期就暴露，而不是等到上线。
    // ------------------------------------------------------------------

    /**
     * 取某个物品堆对应的投掷物<b>客户端展示数据</b>。
     *
     * @return 该物品不是投掷物、或内容包没有为它提供 display 时返回 empty
     *         （此时渲染器应回退到原版物品模型，而不是不画）
     */
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    public static Optional<me.xjqsh.lrtactical.client.resource.display.ThrowableDisplayInstance>
    getThrowableDisplay(ItemStack stack) {
        if (!(stack.getItem() instanceof IThrowable item)) {
            return Optional.empty();
        }
        return Optional.ofNullable(me.xjqsh.lrtactical.client.resource.LrClientAssetsManager.INSTANCE
                .getThrowableDisplay(item.getDisplayId(stack)));
    }

    /**
     * 取某个物品堆对应的近战武器<b>客户端展示数据</b>。
     *
     * @return 该物品不是近战武器、或内容包没有为它提供 display 时返回 empty
     */
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    public static Optional<me.xjqsh.lrtactical.client.resource.display.MeleeDisplayInstance>
    getMeleeDisplay(ItemStack stack) {
        if (!(stack.getItem() instanceof me.xjqsh.lrtactical.api.item.IMeleeWeapon item)) {
            return Optional.empty();
        }
        return Optional.ofNullable(me.xjqsh.lrtactical.client.resource.LrClientAssetsManager.INSTANCE
                .getMeleeDisplay(item.getDisplayId(stack)));
    }
}
