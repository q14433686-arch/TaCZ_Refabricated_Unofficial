package com.tacz.guns.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 清除「受击无敌间隔」，让同一 tick 内的多段伤害都能落实。
 *
 * <h2>为什么需要这个工具类（26.3 的机制变更）</h2>
 * <p>霰弹枪一次击发是<b>多颗弹丸各自结算</b>，连发武器的相邻两发也可能落在同一
 * tick 内。原版默认有受击无敌间隔，不清除的话第一发之后的伤害会被静默吞掉，
 * 表现就是「霰弹枪伤害完全不对」「连发时对方一次只受击一次」。</p>
 *
 * <p>26.2 及以前，这道闸门是 {@code Entity#invulnerableTime}，所以各处直接写
 * {@code entity.invulnerableTime = 0}。<b>26.3 把 LivingEntity 的伤害闸门整个换掉了</b>：
 * {@code LivingEntity#hurtServer}（LE:1219）判的是
 * <pre>if (this.damageCooldownTime &gt; 10.0F &amp;&amp; !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
 *     if (damage &lt;= this.lastHurt) return false;      // 伤害不比上一次大 -&gt; 整发被吞
 *     actuallyHurt(level, source, damage - this.lastHurt);  // 否则只补差值
 *     this.lastHurt = damage;
 * }</pre>
 * 整个 {@code LivingEntity} 里<b>再也没有出现过 {@code invulnerableTime}</b>
 * （已全文 grep 确认）——它只剩非生物实体还在用。
 *
 * <p>于是 26.3 下 {@code setInvulnerableTime(0)} 对生物<b>完全是空操作</b>，
 * 闸门照旧关着。这解释了用户报告的两个现象，而且能解释得很精确：</p>
 * <ul>
 *   <li><b>霰弹枪伤害「完全不对」而不是「只中一发」</b> —— 不是简单地被吞：
 *       第二颗弹丸若伤害大于 {@code lastHurt} 会走 {@code damage - lastHurt}
 *       只补差值，小于等于就整发丢弃。多颗弹丸伤害相近时，总伤害约等于
 *       「其中最大的那一发」，而不是累加。</li>
 *   <li><b>连发「一次只受击一次」</b> —— {@code damageCooldownTime} 一次受击设为 20，
 *       每 tick 减 1，&gt;10 的那 10 tick 内后续伤害都要过上面那道差值闸。</li>
 * </ul>
 *
 * <h2>做法</h2>
 * <p>两个字段都清零，而不是二选一：</p>
 * <ul>
 *   <li>{@code damageCooldownTime = 0} 让 {@code hurtServer} 走 else 分支，
 *       即「完整结算本次伤害」；</li>
 *   <li>{@code lastHurt = 0} 是配套的 —— 即便别处又把 cooldown 顶起来，
 *       差值闸也退化为「按全额算」，不会再吃掉一部分伤害；</li>
 *   <li>{@code setInvulnerableTime(0)} 对非生物实体（盔甲架的部件、载具等）
 *       仍然必要，保留。</li>
 * </ul>
 *
 * <p>{@code damageCooldownTime} 在 26.3 是 <b>public</b> 字段（LE:236），
 * 不需要反射也不需要 accessor mixin。{@code lastHurt} 是 protected，
 * 通过 {@link com.tacz.guns.mixin.common.LivingEntityDamageCooldownAccessor} 访问。</p>
 *
 * <p><b>没有改成加 BYPASSES_COOLDOWN 标签</b>：那会让我们的伤害绕过所有冷却语义
 * （包括别的 mod 依赖这道闸门做的平衡），影响面比「清零本次冷却」大得多，
 * 且需要改动 damage type 数据文件。清零是与 26.2 行为等价的最小改动。</p>
 */
public final class DamageCooldownUtil {
    private DamageCooldownUtil() {
    }

    /**
     * 清掉目标当前的受击无敌间隔，使紧接着的一次 {@code hurt}/{@code hurtServer}
     * 能够完整结算。
     *
     * <p>每一段伤害之前都要调一次 —— 一次 {@code hurt} 成功后 vanilla 会立刻把
     * {@code damageCooldownTime} 重新设回 20（LE:1229），所以多段伤害必须逐段清。</p>
     */
    public static void clear(Entity entity) {
        if (entity == null) {
            return;
        }
        // 非生物实体仍走老字段。
        entity.setInvulnerableTime(0);
        if (entity instanceof LivingEntity living) {
            living.damageCooldownTime = 0;
            ((com.tacz.guns.mixin.common.LivingEntityDamageCooldownAccessor) living).tacz$setLastHurt(0.0F);
        }
    }
}
