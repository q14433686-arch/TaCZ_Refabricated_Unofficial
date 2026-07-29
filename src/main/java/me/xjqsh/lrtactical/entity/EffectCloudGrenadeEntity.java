package me.xjqsh.lrtactical.entity;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.entity.sp.SpEffectCloudEntity;
import me.xjqsh.lrtactical.item.throwable.area.EffectCloudThrowableData;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.OptionalInt;

/**
 * 效果云手雷 —— 落地后释放药水效果。
 *
 * <p>两种形态由数据里的 {@code area_cloud} 决定：
 * <ul>
 *   <li><b>持续云</b>：生成一个 {@link SpEffectCloudEntity}（类似滞留药水）；</li>
 *   <li><b>一次性喷溅</b>：立即对范围内目标施加效果（类似喷溅药水）。</li>
 * </ul>
 *
 * <h2>26.2 移植要点</h2>
 * <ol>
 *   <li><b>{@code MobEffect#isInstantenous} 拼写修正为 {@code isInstantaneous}</b>，
 *       且 {@code applyInstantenousEffect} → <b>{@code applyInstantaneousEffect}</b>
 *       并<b>新增 {@code ServerLevel} 首参</b>（字节码确认）。
 *       这个拼写改动很隐蔽 —— 光看方法名相似很容易直接照抄过来编译失败。</li>
 *   <li><b>{@code PotionContents.getColor(List)} 这个静态重载已不存在</b>，
 *       26.2 是 {@code getColorOptional(Iterable)} 返回 {@code OptionalInt}
 *       （字节码确认）。</li>
 *   <li><b>不移植 {@code SSplashParticle} 网络包</b>。上游用它把喷溅粒子
 *       广播给附近玩家；26.2 的 {@code ServerLevel#sendParticles} 本身就是
 *       服务端广播（会自动发给追踪范围内的玩家），<b>不需要自建包</b>。
 *       少一个包也少一处要维护的协议。</li>
 *   <li>{@code ParticleTypes.ENTITY_EFFECT} 是
 *       {@code ParticleType<ColorParticleOption>}，须用
 *       {@code ColorParticleOption.create(type, color)} 构造 ——
 *       与手雷的 {@code FLASH} 同一个坑。</li>
 *   <li>{@code Entity#igniteForSeconds} 参数在 26.2 是 <b>float</b>。</li>
 * </ol>
 */
public class EffectCloudGrenadeEntity extends ThrowableItemEntity {
    public static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "effect_cloud_grenade"));

    public static final EntityType<EffectCloudGrenadeEntity> TYPE = EntityType.Builder
            .<EffectCloudGrenadeEntity>of(EffectCloudGrenadeEntity::new, MobCategory.MISC)
            .noSave().noSummon().fireImmune()
            .sized(0.3F, 0.3F)
            .clientTrackingRange(64).updateInterval(1)
            .build(KEY);

    @Nullable
    private EffectCloudThrowableData.CloudData cloudData;

    public EffectCloudGrenadeEntity(LivingEntity owner, Level level, int lifeTime) {
        super(TYPE, owner, level, lifeTime);
    }

    public EffectCloudGrenadeEntity(EntityType<? extends EffectCloudGrenadeEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDefaultItem() {
        return me.xjqsh.lrtactical.init.ModItems.THROWABLE;
    }

    @Nullable
    public EffectCloudThrowableData.CloudData getCloudData() {
        return cloudData;
    }

    public void setCloudData(EffectCloudThrowableData.CloudData cloudData) {
        this.cloudData = cloudData;
    }

    @Override
    public void onDeath(@Nullable HitResult hitResult) {
        Vec3 pos = hitResult == null ? this.position() : hitResult.getLocation();
        if (this.level() instanceof ServerLevel serverLevel && this.cloudData != null) {
            if (this.cloudData.isAreaCloud()) {
                spawnEffectCloud(pos, this.cloudData);
            } else {
                Entity target = hitResult instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
                applySplash(this.cloudData, target);
                sendSplashParticles(serverLevel, pos, this.cloudData);
            }
        }
        super.onDeath(hitResult);
    }

    /** 生成持续存在的效果云。 */
    private void spawnEffectCloud(Vec3 pos, EffectCloudThrowableData.CloudData data) {
        SpEffectCloudEntity cloud = new SpEffectCloudEntity(this.level(), pos.x(), pos.y(), pos.z());
        cloud.setRadius(data.getRadius());
        cloud.setRadiusPerTick(data.getRadiusPerTick());
        cloud.setDuration(data.getDuration());
        cloud.setWaitTime(data.getWaitTime());
        // 26.2: setParticle -> setCustomParticle（字节码确认）
        cloud.setCustomParticle(data.getParticles());
        cloud.setIgnite(data.isIgnite());
        cloud.setIgniteTime(data.getIgniteTime());
        cloud.setExtinguishBySmoke(data.isExtinguishBySmoke());
        for (EffectCloudThrowableData.EffectData effect : data.getEffects()) {
            cloud.addEffect(effect.toInstance());
        }
        if (this.getOwner() instanceof LivingEntity owner) {
            cloud.setOwner(owner);
        }
        this.level().addFreshEntity(cloud);
    }

    /** 一次性喷溅：按距离衰减施加效果。 */
    private void applySplash(EffectCloudThrowableData.CloudData data, @Nullable Entity directTarget) {
        List<MobEffectInstance> effects = data.getEffectInstances();
        double radius = data.getRadius();
        if (effects.isEmpty() || radius <= 0.0D) {
            return;
        }
        AABB area = this.getBoundingBox().inflate(radius, 2.0D, radius);
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, area);
        if (targets.isEmpty()) {
            return;
        }
        Entity source = this.getEffectSource();
        for (LivingEntity target : targets) {
            if (!target.isAffectedByPotions()) {
                continue;
            }
            double distanceSqr = this.distanceToSqr(target);
            if (distanceSqr >= radius * radius) {
                continue;
            }
            // 直接命中的目标吃满效果，其余按距离线性衰减
            double factor = (target == directTarget) ? 1.0D : 1.0D - Math.sqrt(distanceSqr) / radius;
            applyAllEffects(effects, target, factor, source, data);
        }
    }

    private void applyAllEffects(List<MobEffectInstance> effects, LivingEntity target,
                                 double factor, Entity source, EffectCloudThrowableData.CloudData data) {
        for (MobEffectInstance effect : effects) {
            Holder<MobEffect> holder = effect.getEffect();
            // 26.1.2: isInstantenous (historical spelling)
            if (holder.value().isInstantenous()) {
                if (this.level() instanceof ServerLevel serverLevel) {
                    // 26.1.2: applyInstantenousEffect requires ServerLevel as first parameter (historical spelling)
                    holder.value().applyInstantenousEffect(
                            serverLevel, this, this.getOwner(), target, effect.getAmplifier(), factor);
                }
            } else {
                int duration = effect.mapDuration(d -> (int) (factor * d + 0.5D));
                MobEffectInstance scaled = new MobEffectInstance(
                        holder, duration, effect.getAmplifier(), effect.isAmbient(), effect.isVisible());
                // 太短的效果没意义，直接丢弃（与原版喷溅药水一致）
                if (!scaled.endsWithin(20)) {
                    target.addEffect(scaled, source);
                }
            }
        }
        if (data.isIgnite() && !target.fireImmune()) {
            // 26.2: igniteForSeconds 参数是 float
            target.igniteForSeconds(data.getIgniteTime());
        }
    }

    /**
     * 广播喷溅粒子。
     *
     * <p>见类注释第 3 点：不需要自建网络包，{@code ServerLevel#sendParticles}
     * 本身就会发给追踪范围内的所有客户端。
     */
    private void sendSplashParticles(ServerLevel level, Vec3 pos,
                                     EffectCloudThrowableData.CloudData data) {
        // 26.2: PotionContents.getColor(List) 已无，改用 getColorOptional(Iterable) -> OptionalInt
        OptionalInt color = PotionContents.getColorOptional(data.getEffectInstances());
        if (color.isEmpty()) {
            return;
        }
        // ENTITY_EFFECT 是 ParticleType<ColorParticleOption>，须显式构造带色的 options
        ColorParticleOption option = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, color.getAsInt());
        level.sendParticles(option, pos.x(), pos.y() + 0.5, pos.z(),
                40, 0.6, 0.4, 0.6, 0.0);
    }
}
