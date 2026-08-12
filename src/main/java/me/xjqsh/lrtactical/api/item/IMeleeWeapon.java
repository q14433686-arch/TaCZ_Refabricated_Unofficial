package me.xjqsh.lrtactical.api.item;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.api.melee.MeleeAction;
import me.xjqsh.lrtactical.item.index.MeleeWeaponIndex;
import me.xjqsh.lrtactical.item.melee.CombatData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * 近战武器 —— 用「同一个物品 id + NBT」承载多把不同的刀。
 *
 * <p>身份读写、索引查询、目标收集、服务端伤害结算、暴击/附魔与耐久消耗均在本接口中
 * 提供默认实现；{@code MeleeItem} 不需要用空方法冒充已实现行为。</p>
 *
 * <h2>26.1.2 移植要点</h2>
 * NBT 读取 API 全面变化（与 {@code IThrowable} 同源，字节码确认）：
 * <ul>
 *   <li>{@code CompoundTag#contains(String, int)} 带类型 id 的重载<b>已移除</b>；</li>
 *   <li>{@code getString(String)} 返回 {@link Optional}，旧行为要用
 *       {@code getStringOr(String, String)}。</li>
 * </ul>
 * 因此上游的 {@code contains(TAG, Tag.TAG_STRING)} + {@code getString(TAG)}
 * 两步写法，在此合并为一次 {@code getStringOr} 并对空串判断。
 */
public interface IMeleeWeapon extends ICustomItem {
    String ID_TAG = "MeleeWeaponId";
    String OVERRIDE_DISPLAY_ID = "DisplayId";
    Identifier EMPTY = Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "empty");

    @Nullable
    static IMeleeWeapon of(ItemStack stack) {
        if (stack.getItem() instanceof IMeleeWeapon item) {
            return item;
        }
        return null;
    }

    @Override
    default Identifier getId(ItemStack stack) {
        return readId(stack, ID_TAG).orElse(EMPTY);
    }

    @Override
    default Identifier getDisplayId(ItemStack stack) {
        return readId(stack, OVERRIDE_DISPLAY_ID).orElseGet(() -> getId(stack));
    }

    /**
     * 从 {@code CUSTOM_DATA} 读一个 Identifier 字段。
     *
     * @return 字段缺失或不是合法 Identifier 时返回 {@link Optional#empty()}
     */
    private static Optional<Identifier> readId(ItemStack stack, String key) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return Optional.empty();
        }
        CompoundTag nbt = customData.copyTag();
        String raw = nbt.getStringOr(key, "");
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(Identifier.tryParse(raw));
    }

    @Override
    default void setId(ItemStack stack, Identifier id) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> nbt.putString(ID_TAG, id.toString()));
    }

    @Override
    default boolean isSame(ItemStack i, ItemStack j) {
        IMeleeWeapon a = IMeleeWeapon.of(i);
        IMeleeWeapon b = IMeleeWeapon.of(j);
        if (a != null && b != null) {
            return Objects.equals(a.getId(i), b.getId(j));
        }
        if (i.isEmpty() || j.isEmpty()) {
            return i.isEmpty() && j.isEmpty();
        }
        return false;
    }

    @Override
    default int getDrawTime(ItemStack stack) {
        return getMeleeIndex(stack).map(index -> index.getData().getDrawTime()).orElse(0);
    }

    @Override
    default int getPutAwayTime(ItemStack stack) {
        return getMeleeIndex(stack).map(index -> index.getData().getPutAwayTime()).orElse(0);
    }

    /**
     * 取该物品对应的近战武器索引。
     *
     * <p>查不到时返回 empty，调用方据此表现为「可持有但无近战能力」，不会崩溃。
     */
    default Optional<MeleeWeaponIndex<?>> getMeleeIndex(ItemStack stack) {
        return me.xjqsh.lrtactical.api.LrTacticalAPI.getMeleeIndex(stack);
    }

    /** 本次动作的冷却（tick）。 */
    default int getAttackCoolDown(ItemStack stack, MeleeAction action) {
        return getMeleeIndex(stack)
                .map(index -> index.getData().getAttackInfo().getAttackInfo(action))
                .map(CombatData.MeleeAttackInfo::getCooldown)
                .orElse(0);
    }

    /** 从按下到判定的延迟（tick），用于对齐挥击动画。 */
    default int getAttackDelay(ItemStack stack, MeleeAction action) {
        return getMeleeIndex(stack)
                .map(index -> index.getData().getAttackInfo().getAttackInfo(action))
                .map(CombatData.MeleeAttackInfo::getDelay)
                .orElse(0);
    }

    /**
     * 本次动作的前冲位移配置，没配则为 {@code null}。
     *
     * <p>位移只在<b>客户端</b>施加（见 {@code CombatProperties} 类注释）：
     * 移动由客户端主导，服务端硬推会与客户端预测打架、表现为拉扯。
     */
    @Nullable
    default CombatData.MeleeMovement getAttackMovement(ItemStack stack, MeleeAction action) {
        return getMeleeIndex(stack)
                .map(index -> index.getData().getAttackInfo().getAttackInfo(action))
                .map(CombatData.MeleeAttackInfo::getMovement)
                .orElse(null);
    }

    /** 本次动作是否有配置（没配就不该响应该按键）。 */
    default boolean canAttack(ItemStack stack, MeleeAction action) {
        return getMeleeIndex(stack)
                .map(index -> index.getData().getAttackInfo().getAttackInfo(action) != null)
                .orElse(false);
    }

    /**
     * 执行一次完整的近战攻击（索敌 + 结算）。
     *
     * <h2>【重要】与上游的分工不同 —— 全部在服务端完成</h2>
     * 上游的设计是「<b>客户端</b>索敌 → 把目标列表用 C2S 包发给服务端 → 服务端结算」。
     * 本移植改为<b>服务端一次做完</b>，理由有二：
     * <ol>
     *   <li><b>26.2 的 {@code Entity#hurt} 返回 {@code void}</b>（字节码确认），
     *       真正能拿到「是否造成伤害」的入口是
     *       {@code hurtServer(ServerLevel, DamageSource, float) -> boolean}，
     *       而它<b>只能在服务端调用</b>。上游靠 {@code hurt} 的返回值决定
     *       要不要施加击退/暴击特效，那套写法在 26.2 已无法照搬；</li>
     *   <li>客户端索敌意味着<b>信任客户端提交的目标列表</b>，
     *       上游为此还要加 {@code MELEE_MAX_TARGET_PER_PACKET} 之类的限流。
     *       服务端索敌从根上没有这个问题。</li>
     * </ol>
     * 代价是「挥空/命中」的客户端预测反馈会略滞后一个 RTT，
     * 对近战手感影响很小，换来的是判定权威且实现简单得多。
     *
     * @param origin    索敌起点（攻击者眼睛位置）
     * @param direction 索敌方向（单位向量）
     * @return 实际命中的目标数
     */
    default int performAttack(ServerPlayer attacker, ItemStack stack, MeleeAction action,
                              Vec3 origin, Vec3 direction) {
        MeleeWeaponIndex<?> index = getMeleeIndex(stack).orElse(null);
        if (index == null) {
            return 0;
        }
        CombatData.MeleeAttackInfo info = index.getData().getAttackInfo().getAttackInfo(action);
        if (info == null) {
            return 0;
        }
        ServerLevel level = attacker.level() instanceof ServerLevel sl ? sl : null;
        if (level == null) {
            return 0;
        }

        // 基础伤害 = 攻击者的 ATTACK_DAMAGE 属性（已含武器的属性修饰）× 本段倍率
        float base = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) * info.getFactor();

        int hitCount = 0;
        for (Entity target : info.getHitbox().filterTargets(attacker, origin, direction)) {
            if (hurtSingle(attacker, target, level, base, info.getKnockback())) {
                hitCount++;
            }
        }

        if (hitCount > 0) {
            // 只有真的打中才扣耐久 —— 与原版一致，挥空不磨损
            int durabilityDamage = info.getDurabilityDamage();
            if (durabilityDamage > 0) {
                stack.hurtAndBreak(durabilityDamage, attacker, EquipmentSlot.MAINHAND);
            }
        }
        attacker.causeFoodExhaustion(0.1F);
        return hitCount;
    }

    /**
     * 对单个目标结算。
     *
     * @return 是否真的造成了伤害
     */
    private static boolean hurtSingle(ServerPlayer attacker, Entity target, ServerLevel level,
                                      float base, float knockback) {
        if (target == attacker || !target.isAlive() || !target.isAttackable()
                || target.skipAttackInteraction(attacker)) {
            return false;
        }

        float damage = base;
        // 【暴击】26.2 已有现成的 Player#canCriticalAttack(Entity)（字节码确认），
        // 它封装了全部条件：fallDistance>0 && !onGround && !onClimbable && !isInWater
        // && !isMobilityRestricted && !isPassenger && target instanceof LivingEntity && !isSprinting。
        // 直接用它，而不是照上游那样手写一遍条件 ——
        // 上游依赖 NeoForge 的 CriticalHitEvent（Fabric 无），
        // 但原版判定本身在 26.2 是公开可用的，行为与原版完全一致而非「近似」。
        boolean critical = attacker.canCriticalAttack(target);
        if (critical) {
            damage *= 1.5F;   // 与原版 Player#attack 中的倍率一致（字节码确认常量 1.5）
        }

        // 附魔加成（锋利等）：26.2 由 EnchantmentHelper.modifyDamage 统一处理，
        // 签名 (ServerLevel, ItemStack, Entity, DamageSource, float) -> float
        DamageSource source = attacker.damageSources().playerAttack(attacker);
        damage = EnchantmentHelper.modifyDamage(level, attacker.getMainHandItem(), target, source, damage);

        // 清无敌帧：近战连招的间隔常常短于原版 10 tick 的无敌时间，
        // 不清的话第二段会被静默吞掉。与本仓库 ExplodeUtil 的处理同源。
        target.invulnerableTime = 0;

        // 26.2: Entity#hurt 返回 void，服务端判定入口是 hurtServer -> boolean（字节码确认）
        boolean hurt = target.hurtServer(level, source, damage);
        if (!hurt) {
            return false;
        }

        if (knockback > 0) {
            double yRot = Math.toRadians(attacker.getYRot());
            target.push(-Math.sin(yRot) * knockback, 0, Math.cos(yRot) * knockback);
        }
        if (critical) {
            // 原版的暴击特效（粒子 + 音效），公开方法
            attacker.crit(target);
        }
        // 附魔命中后效（火焰附加的点燃、抢夺的战利品等）
        EnchantmentHelper.doPostAttackEffects(level, target, source);
        return true;
    }
}
