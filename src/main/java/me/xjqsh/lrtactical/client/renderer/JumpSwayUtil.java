package me.xjqsh.lrtactical.client.renderer;

import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.util.math.SecondOrderDynamics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * 起跳/落地时手持物的上下摆动（sway）。
 *
 * <p>用二阶动力学系统做阻尼跟随，因此起跳有「甩上去再回落」的过冲感，
 * 落地有「压一下再弹回」的缓冲感，而不是生硬的线性位移。
 *
 * <h2>26.2 移植要点</h2>
 * <ul>
 *   <li><b>{@code Minecraft#getTimer()} 已改名 {@code getDeltaTracker()}</b>（字节码确认：
 *       {@code Minecraft} 上只有 {@code getDeltaTracker()Lnet/minecraft/client/DeltaTracker;}）。
 *       {@code DeltaTracker#getGameTimeDeltaPartialTick(boolean)} 签名未变。
 *       调用点改在 {@code MeleeItemRenderer#doExtraTransforms} 一侧，本类只收 float 参数。</li>
 *   <li>{@code Entity#yOld}、{@code Entity#getY()}、{@code Entity#onGround()} 三者
 *       在 26.2 均<b>原样存在</b>（字节码确认），故物理量计算逐行照搬。</li>
 * </ul>
 *
 * <h2>一处上游 bug 的修正</h2>
 * 上游写的是
 * <pre>
 * double posY = Mth.lerp(partialTicks, player.yOld, player.getY());
 * float velocityY = (float) (posY - player.yOld) / partialTicks;
 * </pre>
 * 展开后 {@code velocityY == (getY() - yOld)}，{@code partialTicks} 先乘后除被完全抵消 ——
 * 也就是说那个 {@code lerp} 与除法都是<b>无效运算</b>。但当 {@code partialTicks == 0} 时
 * （渲染恰好落在 tick 边界，实际会发生）会变成 {@code 0 / 0 = NaN}，
 * NaN 一旦进入 {@code jumpingSwayProgress} 就会污染后续所有帧（NaN 参与比较恒为 false，
 * 两个 clamp 分支都进不去），表现为手持物<b>永久偏移或抖动</b>。
 *
 * <p>这里直接用等价且无奇点的 {@code getY() - yOld}，行为与上游在 {@code partialTicks > 0}
 * 时<b>完全一致</b>，同时消除 NaN 风险。（注：{@code SecondOrderDynamics#get} 内部虽有
 * NaN 兜底，但那只保护它自己的状态量，救不了本类的 {@code jumpingSwayProgress}。）
 *
 * <p>另外把上游的「只截上界」改为 {@code Mth.clamp(v, 0, 1)}：上游在
 * 「贴地但速度向上」（走上台阶、被弹起的瞬间）时会算出<b>负的</b> progress，
 * 使模型向反方向偏移。截到 0 才符合「没有冲击就没有摆动」的语义。
 */
public class JumpSwayUtil {
    private static float jumpingSwayProgress = 0;
    private static boolean lastOnGround = false;
    private static long jumpingTimeStamp = -1;

    private static final SecondOrderDynamics JUMPING_DYNAMICS = new SecondOrderDynamics(0.28f, 1f, 0.65f, 0);
    private static final float JUMPING_Y_SWAY = -2f;
    private static final float JUMPING_SWAY_TIME = 0.3f;
    private static final float LANDING_SWAY_TIME = 0.15f;
    /** 玩家自然起跳的初速度（格/tick）。 */
    private static final float JUMP_VELOCITY = 0.42f;
    /** 落地冲击的归一化基准（格/tick），负号因为向下为负。 */
    private static final float LANDING_VELOCITY = -0.1f;

    private JumpSwayUtil() {
    }

    public static void applyJumpingSway(BedrockAnimatedModel model, float partialTicks) {
        if (jumpingTimeStamp == -1) {
            jumpingTimeStamp = System.currentTimeMillis();
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            // 见类注释：上游的 lerp + 除以 partialTicks 相互抵消，且在 partialTicks==0 时产生 NaN
            float velocityY = (float) (player.getY() - player.yOld);
            float elapsed = (System.currentTimeMillis() - jumpingTimeStamp) / 1000F;
            if (player.onGround()) {
                if (!lastOnGround) {
                    // 刚落地：按下落速度决定冲击强度
                    jumpingSwayProgress = Mth.clamp(velocityY / LANDING_VELOCITY, 0F, 1F);
                    lastOnGround = true;
                } else {
                    jumpingSwayProgress = Math.max(0F, jumpingSwayProgress - elapsed / LANDING_SWAY_TIME);
                }
            } else {
                if (lastOnGround) {
                    // 刚离地：按起跳速度决定甩动强度
                    jumpingSwayProgress = Mth.clamp(velocityY / JUMP_VELOCITY, 0F, 1F);
                    lastOnGround = false;
                } else {
                    jumpingSwayProgress = Math.max(0F, jumpingSwayProgress - elapsed / JUMPING_SWAY_TIME);
                }
            }
        }
        jumpingTimeStamp = System.currentTimeMillis();

        float ySway = JUMPING_DYNAMICS.update(JUMPING_Y_SWAY * jumpingSwayProgress);
        BedrockPart rootNode = model.getRootNode();
        if (rootNode != null) {
            // 基岩版模型 y 轴上下颠倒，sway 值取相反数
            rootNode.offsetY += -ySway / 16;
        }
    }
}
