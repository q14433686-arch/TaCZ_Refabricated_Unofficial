package com.tacz.guns.api.client.other;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 用来在收物品时，让其保持一段时间渲染的接口
 */
public interface KeepingItemRenderer {
    /**
     * 物品保持渲染的时间
     *
     * @param itemStack 保持的物品
     * @param timeMs    时间，单位毫秒
     */
    void keep(ItemStack itemStack, long timeMs);

    /**
     * 获取当前主手正在渲染的物品
     */
    ItemStack getCurrentItem();

    /**
     * {@code FirstPersonHandsAndItems} 通过 Mixin 的方式实现了此接口。
     *
     * <p><b>26.3 迁移</b>：26.2 及以前，持有 {@code mainHandItem} 的是
     * {@code ItemInHandRenderer}，实例挂在 {@code EntityRenderDispatcher} 上。
     * 26.3 把状态侧拆成了 {@code net.minecraft.client.player.FirstPersonHandsAndItems}，
     * 实例改为<b>每个 {@code LocalPlayer} 自己持有</b>，取法是
     * {@code player.firstPersonHandsAndItems()}（已对照 vanilla 反编译源码核实：
     * {@code LocalPlayer} 的私有字段 {@code firstPersonHandsAndItems} + 同名 public 访问器）。</p>
     *
     * @return 当前本地玩家的 {@code FirstPersonHandsAndItems}；玩家尚未就绪时返回 {@code null}
     */
    @Nullable
    static KeepingItemRenderer getRenderer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        return (KeepingItemRenderer) (Object) player.firstPersonHandsAndItems();
    }

    /**
     * {@link #getRenderer()} + {@link #getCurrentItem()} 的空安全组合。
     *
     * <p>26.3 起 {@link #getRenderer()} 依赖本地玩家存在（实例由 {@code LocalPlayer} 持有），
     * 因此可能返回 {@code null}。绝大多数调用点只是想知道"这一帧该画哪把枪"，
     * 玩家不在时答案就是"什么都不画"，用本方法即可。</p>
     *
     * @return 当前主手正在渲染的物品；玩家尚未就绪时返回 {@link ItemStack#EMPTY}
     */
    static ItemStack getCurrentRenderItem() {
        KeepingItemRenderer renderer = getRenderer();
        return renderer == null ? ItemStack.EMPTY : renderer.getCurrentItem();
    }
}
