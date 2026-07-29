package me.xjqsh.lrtactical.entity;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.init.ModEffects;
import me.xjqsh.lrtactical.item.throwable.flash.StunThrowableData;
import me.xjqsh.lrtactical.util.ParticleUtil;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 闪光弹 —— 按距离与视线夹角施加致盲 / 耳鸣。
 *
 * <h2>效果本身在哪实现</h2>
 * 本类只负责<b>判定谁该被闪到、闪多久</b>，然后挂上
 * {@code ModEffects.BLIND} / {@code DEAFENED} 两个状态。
 * 这两个 {@code MobEffect} 是<b>空壳</b>，真正的表现在客户端：
 * <ul>
 *   <li>致盲 → {@code BlindnessOverlay}（屏幕糊白）；</li>
 *   <li>耳鸣 → {@code DeafenSoundHandler}（压低所有音量）。</li>
 * </ul>
 *
 * <h2>判定规则（与上游一致）</h2>
 * <ul>
 *   <li><b>耳鸣</b>只看距离 —— 背对、闭眼都没用；</li>
 *   <li><b>致盲</b>还要满足两个条件：视线夹角在 {@code max_angle} 内，
 *       且爆点与眼睛之间<b>没有方块阻挡</b>（隔墙不闪）。</li>
 * </ul>
 *
 * <h2>26.2 移植要点</h2>
 * <ul>
 *   <li><b>不移植上游的 {@code SightTraceUtil}</b>（140 行自定义射线追踪）——
 *       它做的就是「两点间有无不透明方块」，而原版 {@code Level#clip} +
 *       {@code ClipContext.Block.COLLIDER} 已经能做到，本模块
 *       {@code ITargetFilter#clip} 也是同一写法。少 140 行，行为等价。</li>
 *   <li>{@code ParticleTypes.FLASH} 是 {@code ParticleType<ColorParticleOption>}，
 *       须用 {@code ColorParticleOption.create(...)} 构造 —— 与爆炸雷同一处理。</li>
 *   <li>{@code MobEffectInstance} 构造接收 {@code Holder<MobEffect>}，
 *       故 {@code ModEffects} 直接存 Holder。</li>
 * </ul>
 */
public class StunGrenadeEntity extends ThrowableItemEntity {
    public static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "stun_grenade"));

    public static final EntityType<StunGrenadeEntity> TYPE = EntityType.Builder
            .<StunGrenadeEntity>of(StunGrenadeEntity::new, MobCategory.MISC)
            .noSave().noSummon().fireImmune()
            .sized(0.3F, 0.3F)
            .clientTrackingRange(64).updateInterval(1)
            .build(KEY);

    private StunThrowableData.StunData data = new StunThrowableData.StunData();

    public StunGrenadeEntity(LivingEntity owner, Level level, int lifeTime) {
        super(TYPE, owner, level, lifeTime);
    }

    public StunGrenadeEntity(EntityType<? extends StunGrenadeEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDefaultItem() {
        return me.xjqsh.lrtactical.init.ModItems.THROWABLE;
    }

    public StunThrowableData.StunData getData() {
        return data;
    }

    public void setStunData(StunThrowableData.StunData data) {
        this.data = data;
    }

    @Override
    public void onDeath(@Nullable HitResult hitResult) {
        if (this.level() instanceof ServerLevel serverLevel) {
            double radius = this.data.getRadius();
            AABB area = this.getBoundingBox().inflate(radius);
            for (Entity entity : this.level().getEntities(this, area, EntitySelector.NO_SPECTATORS)) {
                if (entity instanceof LivingEntity living) {
                    applyStun(this, living, this.data);
                }
            }
            // 爆闪：FLASH 在 26.2 是 ParticleType<ColorParticleOption>，须带颜色构造
            Vec3 pos = this.position();
            ParticleUtil.sendParticle(serverLevel,
                    ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFF),
                    pos.x(), pos.y() + 0.5, pos.z(), 12, 0.2, 0.2, 0.2, 8, true);
        }
        super.onDeath(hitResult);
    }

    /**
     * 对单个目标结算致盲与耳鸣。
     *
     * <p>公开为 static，便于将来的防爆盾（{@code FlashShieldItem}）复用同一套判定。
     */
    public static void applyStun(Entity source, LivingEntity target, StunThrowableData.StunData data) {
        // 爆点略微抬高，避免贴地时被地面挡住
        Vec3 origin = source.position().add(0.0, 1.0, 0.0);
        Vec3 eyes = target.getEyePosition(1.0F);
        Vec3 eyeToOrigin = origin.subtract(eyes);

        double distance = eyeToOrigin.length();
        if (distance > data.getRadius()) {
            return;
        }

        // 致盲：还要看角度与遮挡
        double angle = Math.toDegrees(Math.acos(
                target.getViewVector(1.0F).dot(eyeToOrigin.normalize())));
        if (angle > 0 && angle < data.getBlind().getMaxAngle() && hasLineOfSight(target, eyes, origin)) {
            int blindDuration = data.calcBlindDuration(distance, angle);
            if (blindDuration > 0) {
                // ambient=false, visible=false —— 不在 HUD 上显示图标/粒子，
                // 否则玩家能靠图标知道「我被闪了」，削弱临场感（与上游一致）
                target.addEffect(new MobEffectInstance(
                        ModEffects.BLIND, blindDuration, 0, false, false));
            }
        }

        // 耳鸣：只按距离，背对也照样生效
        int deafDuration = data.calcDeafenedDuration(distance);
        if (deafDuration > 0) {
            target.addEffect(new MobEffectInstance(
                    ModEffects.DEAFENED, deafDuration, 0, false, false));
        }
    }

    /**
     * 两点之间是否没有方块阻挡。
     *
     * <p>见类注释：用原版 {@code Level#clip} 代替上游的自定义射线追踪。
     */
    private static boolean hasLineOfSight(LivingEntity target, Vec3 eyes, Vec3 origin) {
        return target.level().clip(new ClipContext(
                eyes, origin, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target)
        ).getType() == HitResult.Type.MISS;
    }
}
