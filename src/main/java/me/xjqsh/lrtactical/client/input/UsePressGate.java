package me.xjqsh.lrtactical.client.input;

import me.xjqsh.lrtactical.api.item.ICustomItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 一次按压只允许触发一次 LRTactical 物品的使用。
 *
 * <p>原版在右键持续按住、玩家又刚好不再处于 using 状态时，会自动再次调用
 * {@code Minecraft#startUseItem()}。对食物这是特性；对 LR 的投掷物/消耗品则会造成
 * 「读空条」「姿势卡住」之类的客户端假使用状态。</p>
 */
@Environment(EnvType.CLIENT)
public final class UsePressGate {
    private static boolean wasUsing;
    private static ItemStack lastUsedStack = ItemStack.EMPTY;
    private static boolean consumedThisPress;
    private static LocalPlayer trackedPlayer;

    private UsePressGate() {
    }

    /** 在 {@code ClientTickEvents.END_CLIENT_TICK} 调用。 */
    public static void onClientTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != trackedPlayer) {
            trackedPlayer = player;
            reset();
            return;
        }
        if (player == null) {
            return;
        }

        boolean keyDown = mc.options.keyUse.isDown();
        if (player.isUsingItem()) {
            wasUsing = true;
            lastUsedStack = player.getUseItem();
        } else if (wasUsing) {
            wasUsing = false;
            Item usedItem = lastUsedStack.getItem();
            consumedThisPress = keyDown
                    && usedItem instanceof ICustomItem
                    && (player.getMainHandItem().getItem() == usedItem
                        || player.getOffhandItem().getItem() == usedItem);
            lastUsedStack = ItemStack.EMPTY;
        }

        if (!keyDown) {
            consumedThisPress = false;
        }
    }

    public static boolean shouldBlockRestart() {
        if (!consumedThisPress) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc.options != null && mc.options.keyUse.isDown();
    }

    private static void reset() {
        wasUsing = false;
        consumedThisPress = false;
        lastUsedStack = ItemStack.EMPTY;
    }
}
