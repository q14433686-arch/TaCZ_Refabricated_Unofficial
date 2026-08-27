package me.xjqsh.lrtactical.client.input;

import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.item.throwable.ThrowableData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 兜底：客户端若进入一个服务端并不存在的「持续使用」状态，
 * 在明显超过允许的最长预燃时间后自动本地 stop。
 */
@Environment(EnvType.CLIENT)
public final class StuckUseRecovery {
    private static final int LATENCY_MARGIN_TICKS = 20;

    private StuckUseRecovery() {
    }

    /** 在 {@code ClientTickEvents.END_CLIENT_TICK} 调用。 */
    public static void onClientTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || !player.isUsingItem()) {
            return;
        }

        ItemStack stack = player.getUseItem();
        if (!(stack.getItem() instanceof IThrowable throwable)) {
            return;
        }
        ThrowableData data = throwable.getThrowableIndex(stack)
                .map(index -> index.getData())
                .orElse(null);
        if (data == null || !data.isCookable()) {
            return;
        }

        int life = data.getEntityData().getLifeTime();
        if (life <= 0) {
            return;
        }

        int limit = data.getPrepareTime() + life + LATENCY_MARGIN_TICKS;
        if (player.getTicksUsingItem() > limit) {
            player.stopUsingItem();
        }
    }
}
