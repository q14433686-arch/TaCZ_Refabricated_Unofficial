package me.xjqsh.lrtactical.capability;

import me.xjqsh.lrtactical.api.item.ICustomItem;
import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import me.xjqsh.lrtactical.api.melee.MeleeAction;
import me.xjqsh.lrtactical.item.melee.CombatData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 近战状态机 —— 冷却、切枪、攻击前摇与位移。
 *
 * <h2>与上游的关键差异：攻击判定不再由客户端提交目标</h2>
 * 上游的 {@code preAttack} 在客户端会创建一个 {@code DelayAttack} 延时任务，
 * 到点后<b>在客户端索敌</b>并用 {@code CMeleeAttackRequest} 把目标 id 列表发给服务端。
 *
 * <p>本移植第 3 步已确定改为<b>服务端索敌 + 结算</b>
 * （理由见 {@link IMeleeWeapon#performAttack}：26.2 的 {@code Entity#hurt} 返回 void，
 * 判定入口 {@code hurtServer} 只能在服务端调；且信任客户端目标列表需要额外限流）。
 *
 * <p>因此这里的分工是：
 * <ul>
 *   <li><b>客户端</b>：只负责「按键 → 发一个 {@code CPrepareMeleeAttack} 请求」
 *       以及本地的挥手动画、前冲位移（纯表现，不影响判定）；</li>
 *   <li><b>服务端</b>：收到请求后校验冷却，起一个<b>延时任务</b>，
 *       到点自行索敌并结算。前摇延迟因此也在服务端计时。</li>
 * </ul>
 * 由此<b>不需要移植 {@code CMeleeAttackRequest}</b> —— 那个包的唯一用途
 * 就是传客户端算好的目标列表，而这条路已被弃用。少一个包也少一处可被伪造的输入。
 *
 * <h2>为什么延时任务两端都要跑</h2>
 * 服务端跑的是<b>真实结算</b>；客户端跑的是<b>前冲位移</b>
 * （{@code MeleeMovement}）—— 位移必须在客户端本地施加，
 * 否则会与客户端预测打架、表现为拉扯。这与原版「移动由客户端主导」一致。
 */
public class CombatProperties {
    private final Player entity;

    private final List<DelayTask> delayedActions = new ArrayList<>();
    private ItemStack lastItem = ItemStack.EMPTY;
    private int coolDownTick = 0;
    private int lastMaxTick = 0;
    private int lastSelected = 0;
    private int drawingTick = 0;
    private final Map<MeleeAction, Integer> actionCounts = new EnumMap<>(MeleeAction.class);

    public CombatProperties(Player entity) {
        this.entity = entity;
    }

    /** 当前剩余冷却（tick）。两端各自计时，可能有一两 tick 偏差。 */
    public int getCoolDownTick() {
        return coolDownTick;
    }

    /** 本次冷却的总时长，用于 HUD 画进度条。 */
    public int getLastMaxTick() {
        return lastMaxTick;
    }

    public boolean isDrawing() {
        return drawingTick > 0;
    }

    public void tick() {
        ItemStack mainHand = entity.getMainHandItem();

        // 换手 / 换物品时重置状态（切入需要时间）
        if (mainHand.getItem() instanceof ICustomItem customItem) {
            // 26.2: Inventory#selected 字段仍在，但有公开的 getSelectedSlot()（字节码确认），
            // 优先用方法而非直接读字段
            int selected = entity.getInventory().getSelectedSlot();
            if (lastSelected != selected) {
                lastSelected = selected;
                reset(customItem, lastItem);
            } else if (!customItem.isSame(lastItem, mainHand)) {
                reset(customItem, lastItem);
            }
        } else if (!ItemStack.matches(lastItem, mainHand)) {
            lastItem = mainHand.copy();
        }

        if (coolDownTick > 0) {
            coolDownTick--;
        }
        if (drawingTick > 0) {
            drawingTick--;
        }

        // 延时任务：服务端跑结算，客户端跑位移（见类注释）
        if (!delayedActions.isEmpty()) {
            for (DelayTask task : delayedActions) {
                if (task.tick()) {
                    task.perform(entity);
                }
            }
            delayedActions.removeIf(DelayTask::expired);
        }
    }

    /** 切换武器：进入「收起 + 举起」的空窗期。 */
    public void reset(ICustomItem customItem, ItemStack last) {
        lastItem = entity.getMainHandItem().copy();
        int newCoolDown = customItem.getDrawTime(entity.getMainHandItem());
        if (last.getItem() instanceof ICustomItem previous) {
            newCoolDown += previous.getPutAwayTime(last);
        }
        coolDownTick = newCoolDown;
        lastMaxTick = newCoolDown;
        drawingTick = newCoolDown;
        delayedActions.clear();
        actionCounts.clear();
    }

    /** 某类近战动作已连续执行的次数，用于内容包 Lua 选择连击动画。 */
    public int getActionCount(MeleeAction action) {
        return actionCounts.getOrDefault(action, 0);
    }

    /**
     * 发起一次攻击。
     *
     * <p><b>两端都会调用</b>，但行为不同：
     * 服务端排一个 {@link DelayAttack} 做真实结算，客户端只排前冲位移。
     *
     * @return 是否成功发起（冷却中或该动作没配置时返回 false）
     */
    public boolean preAttack(MeleeAction action, Vec3 origin, Vec3 direction) {
        ItemStack stack = entity.getMainHandItem();
        if (!(stack.getItem() instanceof IMeleeWeapon weapon)) {
            return false;
        }
        if (coolDownTick > 0 || !weapon.canAttack(stack, action)) {
            return false;
        }

        int actionCount = actionCounts.getOrDefault(action, 0);
        actionCounts.put(action, actionCount + 1);

        coolDownTick = weapon.getAttackCoolDown(stack, action);
        lastMaxTick = coolDownTick;

        int delay = weapon.getAttackDelay(stack, action);

        if (entity instanceof ServerPlayer) {
            // 服务端宽限 1 tick，平衡网络延迟造成的「客户端已好、服务端还差一点」
            this.coolDownTick = Math.max(0, coolDownTick - 1);
            DelayAttack attack = new DelayAttack(delay, stack, action, origin, direction);
            if (delay <= 0) {
                attack.perform(entity);
            } else {
                delayedActions.add(attack);
            }
        } else {
            // 客户端：只做前冲位移（纯表现）。挥手动画由调用方触发。
            CombatData.MeleeMovement moveInfo = weapon.getAttackMovement(stack, action);
            if (moveInfo != null) {
                DelayMove move = new DelayMove(moveInfo.getDelay(), moveInfo.getSpeed(), stack);
                if (moveInfo.getDelay() <= 0) {
                    move.perform(entity);
                } else {
                    delayedActions.add(move);
                }
            }
            playAttackSound(action, origin);
        }
        return true;
    }

    /**
     * 播放内容包为该动作配置的挥击音效（{@code display/melee/*.json} 的 {@code sounds} 段）。
     *
     * <h2>为什么只在客户端播、且用 {@code playLocalSound}</h2>
     * 音效 id 来自 <b>display</b>，而 display 属于资源包 —— <b>服务端根本没有这份数据</b>
     * （专用服务器上连 display 类都加载不到）。因此上游也是在客户端本地播放：
     * {@code level().playLocalSound(...)} 只在本机出声、不发包。
     *
     * <p>代价是「别人听不到你的挥刀声」。这与上游行为一致，也是数据放在资源包里的必然结果 ——
     * 内容包若希望全场可闻，应当改用 {@code index}（数据包）里的音效字段，
     * 由服务端 {@code level.playSound(null, ...)} 广播。<b>此处刻意不自行加广播</b>：
     * 那需要新增一个 S2C 包与数据通道，属于超出「动画层补完」范围的设计变更。
     *
     * <h2>26.2 签名核对</h2>
     * {@code Level#playLocalSound(double,double,double,SoundEvent,SoundSource,float,float,boolean)}
     * 与 {@code SoundEvent#createVariableRangeEvent(Identifier)} 均<b>原样存在</b>（字节码确认），
     * 仅 {@code ResourceLocation} → {@code Identifier}。
     */
    private void playAttackSound(MeleeAction action, Vec3 origin) {
        // 【必须显式判断 isClientSide，不能只依赖「走到 else 分支 = 客户端」】
        //
        // LrTacticalAPI#getMeleeDisplay 标了 @Environment(CLIENT) —— Fabric 会在
        // 专用服务器上把该方法【整个剥离】，此时调用它抛的是 NoSuchMethodError，
        // 而不是 ClassNotFoundException，堆栈里也看不出与「客户端专有」有任何关系。
        //
        // 上面的 else 分支条件是 !(entity instanceof ServerPlayer)，
        // 在专用服务器上遇到假玩家（FakePlayer 之类）时同样会落进来 —— 那就会崩服。
        // 多这一行判断，把「只在客户端存在的 API」与「只在客户端执行」严格对齐。
        if (!entity.level().isClientSide()) {
            return;
        }
        me.xjqsh.lrtactical.api.LrTacticalAPI.getMeleeDisplay(entity.getMainHandItem())
                .map(display -> display.getSound(action.getId()))
                .ifPresent(soundId -> entity.level().playLocalSound(
                        origin.x, origin.y, origin.z,
                        net.minecraft.sounds.SoundEvent.createVariableRangeEvent(soundId),
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        1.0F, 1.0F, false));
    }

    // ---------------- 延时任务 ----------------

    /** 前冲位移（客户端）。 */
    public static class DelayMove extends DelayTask {
        private final ItemStack stack;
        private final double speed;

        DelayMove(int delay, double speed, ItemStack stack) {
            super(delay);
            this.stack = stack;
            this.speed = speed;
        }

        @Override
        public void perform(Player player) {
            // 期间换了武器就取消 —— 否则会出现「切枪后突然前冲」
            if (stack.getItem() instanceof IMeleeWeapon weapon
                    && weapon.isSame(stack, player.getMainHandItem())) {
                double factor = player.onGround() ? 1.0 : 0.5;
                Vec3 motion = player.getLookAngle().multiply(1, 0, 1).normalize().scale(factor * speed);
                player.addDeltaMovement(motion);
            }
        }
    }

    /** 真实攻击结算（服务端）。 */
    public static class DelayAttack extends DelayTask {
        private final ItemStack stack;
        private final MeleeAction action;
        private final Vec3 origin;
        private final Vec3 direction;

        DelayAttack(int delay, ItemStack stack, MeleeAction action, Vec3 origin, Vec3 direction) {
            super(delay);
            this.stack = stack;
            this.action = action;
            this.origin = origin;
            this.direction = direction;
        }

        @Override
        public void perform(Player player) {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            // 前摇期间换了武器则本次作废
            if (!(stack.getItem() instanceof IMeleeWeapon weapon)
                    || !weapon.isSame(stack, player.getMainHandItem())) {
                return;
            }
            // 用「结算这一刻」的实际视线，而不是按下时记录的那个 ——
            // 前摇期间玩家可能已经转身，用旧向量会打到背后的空气。
            // origin/direction 仅作为客户端上报的参考，这里以服务端权威状态为准。
            Vec3 realOrigin = serverPlayer.getEyePosition();
            Vec3 realDirection = serverPlayer.getLookAngle().normalize();
            weapon.performAttack(serverPlayer, player.getMainHandItem(), action, realOrigin, realDirection);
        }
    }

    public abstract static class DelayTask {
        protected int delay;

        protected DelayTask(int delay) {
            this.delay = delay;
        }

        public abstract void perform(Player player);

        /** @return 本 tick 是否到点 */
        public boolean tick() {
            delay--;
            return delay <= 0;
        }

        public boolean expired() {
            return delay <= 0;
        }

        public int getDelay() {
            return delay;
        }
    }
}
