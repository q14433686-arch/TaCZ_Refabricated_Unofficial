package cn.sh1rocu.tacz.api.extension;


import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public interface IItem {
    // 【第 39 轮】移除 tacz$getMaxStackSize(ItemStack)。
    //
    // 它是 1.21.1 时代为「每种弹药各自的堆叠上限」自建的扩展点，靠
    // compat/tweakeroo/ItemMixin 注入 Item#getMaxStackSize(ItemStack) 生效。
    // 但在 26.2 下这条路已彻底断掉：
    //   1. Item 上【没有】getMaxStackSize(ItemStack) 这个重载（字节码确认，
    //      只剩无参的 getDefaultMaxStackSize()）—— 该 mixin 一旦注册就会因
    //      找不到目标而崩溃，这也是它长期未被注册的真正原因；
    //   2. 真正的上限现在由 DataComponents.MAX_STACK_SIZE 组件决定
    //      （ItemInstance#getMaxStackSize 的实现就是
    //       getOrDefault(MAX_STACK_SIZE, 1)），第 34 轮已改为在
    //      AmmoItemDataAccessor#applyMaxStackSize 与 AmmoItem#inventoryTick
    //      里写该组件，不再需要任何 mixin。
    //
    // 删除 ItemMixin 后本方法失去唯一调用方，留着只会误导后来者以为它还有用。

    default boolean tacz$onEntitySwing(ItemStack stack, LivingEntity entity) {
        return false;
    }

    @Environment(EnvType.CLIENT)
    BuiltinItemRendererRegistry.DynamicItemRenderer getCustomRenderer();
}
