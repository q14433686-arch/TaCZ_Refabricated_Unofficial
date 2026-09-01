package com.tacz.guns.util;

import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.LightCoordsUtil;

/**
 * {@code _illuminated} 骨骼的光照取值 —— 立方体层与 poly_mesh 层共用。
 *
 * <h2>为什么不能永远 0xF000F0（下游 1.21.11 分支审查 A10 续集，2026-08-31 采纳）</h2>
 * 全亮常量把 block 与 sky <b>两列</b>都拉满。无光影下必须如此 —— 原版光照图是
 * 两列相乘，缺一列就不够亮。但光影包把 sky 列读成「这个表面看得见天空」：
 * sky=15 被翻译成「太阳/月亮永远照得到」，于是夜里的发光准星/氚光继承天空亮度、
 * 高模枪身「遮不住太阳」。
 *
 * <p>修法：光影激活时 block 仍 15（自发光的本义），sky 换成<b>环境真值</b>
 * （取自该部件本来会拿到的 packed light 的 sky 列）。无光影时行为不变。</p>
 *
 * <p>按下游建议收进 {@code ClientConfig}（{@code RenderConfig.ILLUMINATED_REAL_SKY}）
 * 且<b>两层一起改</b> —— 只改 poly 层的话，一把枪的发光准星（立方体）与发光
 * 枪身件（poly）会一个跟天空走一个不跟。</p>
 */
@Environment(EnvType.CLIENT)
public final class IlluminatedLights {

    public static final int FULL_BRIGHT = 0xF000F0;

    private IlluminatedLights() {
    }

    /**
     * @param environmentLight 该部件不发光时本会拿到的 packed light（sky 列的来源）
     * @return 发光部件应使用的 packed light
     */
    public static int resolve(int environmentLight) {
        if (!readToggle() || !IrisCompat.isUsingRenderPack()) {
            return FULL_BRIGHT;
        }
        int sky = (environmentLight >>> 20) & 0xF;
        return LightCoordsUtil.pack(15, sky);
    }

    private static boolean readToggle() {
        try {
            return RenderConfig.ILLUMINATED_REAL_SKY.get();
        } catch (Throwable t) {
            // 配置尚未加载（理论上不发生）：退回原版全亮行为。
            return false;
        }
    }
}
