package me.xjqsh.lrtactical.event;

import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 拦截原版攻击 —— 持近战武器时不让原版按「普通物品打人」结算。
 *
 * <h2>【第 4 步】职责已收窄：只拦截，不结算</h2>
 * 第 3 步时这里同时负责结算，但那条路只在<b>左键点到实体</b>时触发，
 * 导致「挥空不 AOE」「右键没反应」两个缺口。
 *
 * <p>第 4 步引入了 {@code MeleeAttackKeys}（直接监听鼠标按键）+
 * {@code ClientMessagePrepareMeleeAttack}（C2S）+ {@code CombatProperties}（状态机），
 * 攻击结算<b>统一走那条路</b>。本类若继续结算，点到实体时就会<b>结算两次</b>。
 *
 * <p>因此现在只做一件事：<b>返回非 PASS 把原版攻击挡掉</b>。
 *
 * <h2>为什么两端返回值不同</h2>
 * 已核对 Fabric API 26.2 分支的两个注入点：
 * <ul>
 *   <li><b>客户端</b> {@code MultiPlayerGameModeMixin#attackEntity}：
 *       {@code if (result != PASS) { if (result == SUCCESS) send(packet); info.cancel(); }}
 *       —— 这里要 {@code FAIL}：既挡掉原版逻辑，又<b>不发</b>原版攻击包
 *       （我们自己的 C2S 包已经发过了，再发一个原版包会让服务端多打一次）。</li>
 *   <li><b>服务端</b> {@code PlayerMixin#onPlayerInteractEntity}（注入 {@code Player#attack} HEAD）：
 *       {@code if (result != PASS) info.cancel();}
 *       —— 同样 {@code FAIL}，挡掉原版伤害。</li>
 * </ul>
 *
 * <p>注意这与第 3 步的结论<b>不矛盾</b>：当时客户端必须返回 {@code SUCCESS}，
 * 是因为结算依赖原版攻击包送达服务端；现在有了自己的 C2S 通道，
 * 就不再需要原版包了，反而要挡住它。<b>返回值该填什么，取决于调用方怎么用它</b> ——
 * 这正是上一轮记下的教训。
 */
public final class MeleeAttackHandler {
    private MeleeAttackHandler() {
    }

    /**
     * @return {@code FAIL} = 挡掉原版攻击且不发原版攻击包；{@code PASS} = 交还原版
     */
    public static InteractionResult onAttackEntity(Player player, Level level, InteractionHand hand,
                                                   Entity target, @Nullable EntityHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof IMeleeWeapon weapon)) {
            return InteractionResult.PASS;
        }
        // 拿的是近战武器但没配左键动作：交还原版，表现为普通物品打人
        if (!weapon.canAttack(stack, me.xjqsh.lrtactical.api.melee.MeleeAction.LEFT)) {
            return InteractionResult.PASS;
        }
        // 结算由 MeleeAttackKeys -> C2S -> CombatProperties 完成，这里只负责挡住原版
        return InteractionResult.FAIL;
    }
}
