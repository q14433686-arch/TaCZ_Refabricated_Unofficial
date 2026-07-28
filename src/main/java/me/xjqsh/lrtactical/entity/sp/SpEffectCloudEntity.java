package me.xjqsh.lrtactical.entity.sp;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.entity.SmokeGrenadeEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 增强版效果云 —— 在原版 {@link AreaEffectCloud} 之上加两件事：
 * <ol>
 *   <li><b>点燃</b>：范围内的生物持续着火（燃烧云）；</li>
 *   <li><b>被烟雾扑灭</b>：附近有烟雾弹时自行消散，
 *       构成「燃烧云 vs 烟雾弹」的对抗玩法。</li>
 * </ol>
 *
 * <h2>26.2 移植要点</h2>
 * <ul>
 *   <li>{@code AreaEffectCloud} 在 26.2 <b>仍可继承</b>（非 final，字节码确认），
 *       且 {@code setRadius/setDuration/setWaitTime/setRadiusPerTick/setOwner/addEffect}
 *       全部健在；</li>
 *   <li><b>{@code setParticle(...)} 已改名 {@code setCustomParticle(ParticleOptions)}</b>
 *       （字节码确认，另有 {@code DATA_PARTICLE} 与 {@code getParticle}）；</li>
 *   <li>{@code EntityType.Builder} 三处变更，沿用本模块既有写法；</li>
 *   <li>{@code Entity#igniteForSeconds} 参数在 26.2 是 <b>float</b>（原为 int）。</li>
 * </ul>
 *
 * <p>类名保留上游的 {@code Sp} 前缀（special），便于与上游源码对照。
 */
public class SpEffectCloudEntity extends AreaEffectCloud {
    public static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "sp_effect_cloud"));

    public static final EntityType<SpEffectCloudEntity> TYPE = EntityType.Builder
            .<SpEffectCloudEntity>of(SpEffectCloudEntity::new, MobCategory.MISC)
            .noSave().noSummon().fireImmune()
            .sized(6.0F, 0.5F)
            // 云本身几乎不动，用极长的更新间隔省带宽（半径变化靠 SynchedEntityData 同步）
            .clientTrackingRange(10).updateInterval(Integer.MAX_VALUE)
            .build(KEY);

    /** 每隔多少 tick 检查一次范围内实体（逐 tick 太费）。 */
    private static final int CHECK_INTERVAL = 10;
    /** 烟雾弹在此距离内（平方值）可扑灭本云。 */
    private static final double EXTINGUISH_DISTANCE_SQR = 25.0;

    private boolean ignite = false;
    private int igniteTime = 2;
    private boolean extinguishBySmoke = false;

    public SpEffectCloudEntity(EntityType<? extends AreaEffectCloud> type, Level level) {
        super(type, level);
    }

    public SpEffectCloudEntity(Level level, double x, double y, double z) {
        this(TYPE, level);
        this.setPos(x, y, z);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() || this.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        // 纵向多查 2 格：站在云边缘的生物脚部可能略高于云的包围盒
        List<Entity> nearby = this.level()
                .getEntitiesOfClass(Entity.class, this.getBoundingBox().inflate(0, 2, 0));
        for (Entity entity : nearby) {
            if (this.extinguishBySmoke && entity instanceof SmokeGrenadeEntity smoke) {
                if (shouldBeExtinguished(smoke)) {
                    this.discard();
                    return;
                }
                continue;
            }
            if (this.ignite && entity instanceof LivingEntity && !entity.fireImmune()
                    && this.getBoundingBox().intersects(entity.getBoundingBox())) {
                // 26.2: igniteForSeconds 参数是 float（字节码确认）
                entity.igniteForSeconds(this.igniteTime);
            }
        }
    }

    /** 只有<b>已经开始释放</b>烟雾的弹体才能扑灭（与烟雾弹自身的 40 tick 引信一致）。 */
    private boolean shouldBeExtinguished(SmokeGrenadeEntity smoke) {
        return smoke.tickCount >= 40
                && smoke.position().distanceToSqr(this.position()) < EXTINGUISH_DISTANCE_SQR;
    }

    /** 包围盒随半径变化，否则云长大了判定范围却不变。 */
    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose) {
        return EntityDimensions.scalable(this.getRadius() * 2.0F, 0.5F);
    }

    public boolean isIgnite() {
        return ignite;
    }

    public void setIgnite(boolean ignite) {
        this.ignite = ignite;
    }

    public int getIgniteTime() {
        return igniteTime;
    }

    public void setIgniteTime(int igniteTime) {
        this.igniteTime = igniteTime;
    }

    public boolean isExtinguishBySmoke() {
        return extinguishBySmoke;
    }

    public void setExtinguishBySmoke(boolean extinguishBySmoke) {
        this.extinguishBySmoke = extinguishBySmoke;
    }
}
