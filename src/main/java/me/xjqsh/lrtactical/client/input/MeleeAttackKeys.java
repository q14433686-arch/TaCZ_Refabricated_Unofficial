package me.xjqsh.lrtactical.client.input;

import cn.sh1rocu.tacz.api.event.InputEvent;
import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import me.xjqsh.lrtactical.api.melee.MeleeAction;
import me.xjqsh.lrtactical.init.ModCapabilities;
import me.xjqsh.lrtactical.network.ClientMessagePrepareMeleeAttack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

/**
 * 近战攻击的客户端按键入口。
 *
 * <h2>为什么需要它（第 3 步的两个限制都由此解决）</h2>
 * 第 3 步只挂了 {@code AttackEntityCallback}，它<b>只在左键点到实体时</b>触发，
 * 于是留下两个缺口：
 * <ul>
 *   <li><b>挥空不触发</b> → 锥形/OBB 的 AOE 必须先命中一个目标才能带出周围的；</li>
 *   <li><b>右键完全没接</b> → 右键在原版是「使用物品」语义，不走攻击回调。</li>
 * </ul>
 * 直接监听鼠标按键即可绕开这两点：<b>挥空也会发包</b>，服务端照常索敌，
 * 因此 AOE 不再依赖「先命中一个」。
 *
 * <h2>为什么不新建 KeyMapping</h2>
 * 上游为左右键各注册了一个 {@code KeyMapping}（绑定到鼠标左/右键）。
 * 本移植<b>直接复用原版的攻击键/使用键</b>，理由：
 * <ol>
 *   <li>新建绑定到同一物理按键的 KeyMapping 会与原版<b>冲突</b>，
 *       在按键设置界面显示成红色，且玩家改键后行为会分裂；</li>
 *   <li>本仓库 {@code TaCZKeyCategory} 记录过一个相关的坑 ——
 *       26.2 的按键分类标题从 {@code Identifier} 推导，
 *       写错命名空间会导致界面显示原始键名。新增分类等于多踩一次；</li>
 *   <li>近战攻击本来就该跟随玩家自定义的攻击键，而不是硬编码鼠标左键。</li>
 * </ol>
 * <p>当前直接按<b>物理鼠标左右键</b>判定（与上游一致，上游那两个 KeyMapping
 * 也是硬绑到鼠标左右键的）。若日后要支持自定义键位，
 * 应改为比对 {@code Minecraft#options} 的 {@code keyAttack}/{@code keyUse}，
 * 而不是新建 KeyMapping。
 *
 * <h2>与 {@code AttackEntityCallback} 的关系</h2>
 * 两条路<b>会同时触发</b>（点到实体时）。为避免同一次点击结算两遍，
 * 第 3 步的 {@code MeleeAttackHandler} 已改为<b>只拦截、不结算</b>，
 * 真正的结算统一走本类 → C2S 包 → {@code CombatProperties#preAttack}。
 * 服务端 {@code preAttack} 自带冷却校验，即便两条路都发了包也只会生效一次。
 */
@Environment(EnvType.CLIENT)
public final class MeleeAttackKeys {
    private MeleeAttackKeys() {
    }

    /** 鼠标按下时触发（左键=轻击，右键=重击）。 */
    public static void onMousePress(InputEvent.MouseButton.Post event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        // 必须用本仓库既有的 isInGame()，不能自己写 mc.screen != null ——
        // 26.2 的 GUI 重组已把 Minecraft#screen 移到 Hud#screen()（字节码确认
        // Minecraft 上已无 screen 字段），该工具类的注释里正记录了这次变更。
        // 它还一并检查了「加载覆盖层 / 鼠标是否被捕获 / 窗口是否激活」，
        // 比只判断一个 screen 更严谨（否则 alt-tab 出去点击也会挥刀）。
        if (!isInGame()) {
            return;
        }
        int button = event.getButton();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            tryAttack(MeleeAction.LEFT);
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            tryAttack(MeleeAction.RIGHT);
        }
    }

    /**
     * 发起一次攻击。
     *
     * <p>客户端先自行走一遍 {@code preAttack}：
     * 一来做本地冷却判断（避免冷却中疯狂发包），
     * 二来触发前冲位移与挥手动画。真实伤害由服务端结算。
     */
    private static void tryAttack(MeleeAction action) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator() || mc.gameMode == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IMeleeWeapon weapon)) {
            return;
        }
        if (!weapon.canAttack(stack, action)) {
            return;
        }

        var combat = ModCapabilities.combatProperties(player);
        if (combat.getCoolDownTick() > 0) {
            return;
        }

        var origin = player.getEyePosition();
        var direction = player.getLookAngle().normalize();

        // 客户端本地状态机：进入冷却 + 排前冲位移
        if (!combat.preAttack(action, origin, direction)) {
            return;
        }
        // 先触发 LRTactical 内容包里的攻击动画。
        //
        // 对照上游 1.21.1 的 AttackKeys：preAttack 成功后会立即
        // renderer.triggerAnimation(stack, "attack_left"/"attack_right")，再 swing 手臂。
        // 本移植此前只做了冷却/位移/发包/vanilla swing，漏掉了这一句；所以 draw、inspect
        // 这类由其它输入触发的动画正常，唯独左右键攻击没有内容包动作。
        triggerAttackAnimation(stack, action);
        // 通知服务端做权威结算
        ClientPlayNetworking.send(new ClientMessagePrepareMeleeAttack(action, origin, direction));
        // 挥手动画（纯表现）
        player.swing(InteractionHand.MAIN_HAND);
    }

    private static void triggerAttackAnimation(ItemStack stack, MeleeAction action) {
        var renderer = cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry.INSTANCE.get(stack.getItem());
        if (renderer instanceof com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer<?, ?> animated) {
            animated.triggerAnimation(stack, action.getId());
        }
    }
}
