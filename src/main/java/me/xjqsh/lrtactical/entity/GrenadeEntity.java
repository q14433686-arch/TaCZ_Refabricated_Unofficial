package me.xjqsh.lrtactical.entity;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.util.ParticleUtil;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 爆炸手雷 —— 落地/超时后产生爆炸。
 *
 * <h2>26.2 / Fabric 移植要点</h2>
 *
 * <b>1. 不移植 {@code CustomExplosion}（185 行），改用本仓库已验证的做法</b><br>
 * 上游自建 {@code CustomExplosion extends Explosion}，但 26.2 的
 * {@code Explosion} <b>已变成接口</b>（字节码确认 {@code is_interface}），
 * 真正的实现类是新增的 {@code ServerExplosion}，且 {@code getToBlow()} 等 API 已移除。
 *
 * <p>本仓库为 TACZ 的 RPG/榴弹解决过同一问题，最终结论写在 {@code ExplodeUtil}：
 * <b>直接用原版 {@code Level#explode}</b>，由它统一处理客户端爆炸粒子/音效
 * （{@code ClientboundExplodePacket}）、方块破坏与击退；
 * 再自行按距离线性衰减补一份自定义伤害。
 * 这里沿用同一套 {@link me.xjqsh.lrtactical.util.ExplodeUtil}，
 * 而不是把上游那 185 行逐行硬翻 —— 后者要重写整个方块破坏循环，
 * 收益为零且极易出错。
 *
 * <p><b>由此丢失的能力</b>（如实记录，不假装等价）：
 * 上游的 {@code screenShakeTime} / {@code screenShakeAmplitude}（爆炸屏幕震动）
 * 依赖尚未实现的专用 {@code SShakeScreenMessage}（索引与近战网络层已经完成），
 * 故这两个字段<b>保留但暂不生效</b>；{@code destroyMultiplier}（破坏力倍率）
 * 同理，原版 {@code explode} 不支持该参数。
 *
 * <b>2. {@code Entity#hurt} 在 26.2 返回 {@code void}，无法照上游那样覆写</b><br>
 * 字节码：{@code hurt(DamageSource,float)V}，而上游覆写的是返回 {@code boolean} 的版本。
 * 26.2 可覆写的服务端伤害入口是
 * {@code hurtServer(ServerLevel, DamageSource, float) -> boolean}。
 * 「被其他爆炸引爆」的逻辑改挂到该方法上。
 *
 * <b>3. {@code EntityType.Builder} 三处变更</b><br>
 * {@code setTrackingRange/setUpdateInterval/setShouldReceiveVelocityUpdates} 均已移除；
 * {@code build(String)} 改为 {@code build(ResourceKey)}。
 * 对应关系取自本仓库 {@code EntityKineticBullet} 的既有写法：
 * {@code clientTrackingRange(int)} / {@code updateInterval(int)} /
 * {@code build(ResourceKey.create(Registries.ENTITY_TYPE, id))}。
 */
public class GrenadeEntity extends ThrowableItemEntity {
    public static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "explode_grenade"));

    public static final EntityType<GrenadeEntity> TYPE = EntityType.Builder
            .<GrenadeEntity>of(GrenadeEntity::new, MobCategory.MISC)
            .noSave().noSummon().fireImmune()
            .sized(0.3F, 0.3F)
            .clientTrackingRange(64).updateInterval(1)
            .build(KEY);

    private double damage = 18.0;
    private float radius = 4.5f;
    private boolean destroyBlocks = false;
    private float destroyMultiplier = 1.0f;
    private double screenShakeTime = 20;
    private double screenShakeAmplitude = 50;
    private boolean triggerOnExplode = false;
    /** 是否只能由投掷者的遥控起爆器引爆（C4/遥控雷）。 */
    private boolean remoteDetonation = false;
    /** 防止爆炸连锁时自己把自己再次引爆。 */
    private boolean exploded = false;

    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, LivingEntity owner, Level level, int lifeTime) {
        super(type, owner, level, lifeTime);
    }

    public GrenadeEntity(LivingEntity owner, Level level, int lifeTime) {
        super(TYPE, owner, level, lifeTime);
    }

    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) {
        super(type, level);
    }

    /**
     * 默认物品 —— 实体上没带 ItemStack 时的回退显示。
     *
     * <p>第 3 步曾临时用 {@code Items.SNOWBALL} 兜底，本步物品层已就位，
     * 改为真正的 {@link me.xjqsh.lrtactical.init.ModItems#THROWABLE}。
     */
    @Override
    protected Item getDefaultItem() {
        return me.xjqsh.lrtactical.init.ModItems.THROWABLE;
    }

    @Override
    public void onDeath(@Nullable HitResult hitResult) {
        this.exploded = true;
        Vec3 pos = hitResult == null ? this.position() : this.position().lerp(hitResult.getLocation(), 0.8);
        if (this.level() instanceof ServerLevel serverLevel) {
            // 见类注释第 1 点：走本仓库已验证的 ExplodeUtil，而非上游的 CustomExplosion。
            me.xjqsh.lrtactical.util.ExplodeUtil.createExplosion(
                    this.getOwner(), this,
                    (float) this.damage, this.radius,
                    this.destroyBlocks, pos);

            // 26.2: ParticleTypes.FLASH 不再是 SimpleParticleType，而是
            // ParticleType<ColorParticleOption>（字节码泛型签名确认），
            // 即它本身【不是】一个可直接使用的 ParticleOptions，必须先带上颜色构造。
            // 原版 FireworkParticles$Starter#tick 用的正是
            // ColorParticleOption.create(ParticleTypes.FLASH, color)。
            // 此处取白色（0xFFFFFF）—— 爆炸闪光本就是白光，且与旧版 FLASH 的观感一致。
            ParticleUtil.sendParticle(serverLevel,
                    ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFF),
                    pos.x(), pos.y() + 0.5, pos.z(), 50, 0.2, 0.2, 0.2, 20, true);
            ParticleUtil.sendParticle(serverLevel, ParticleTypes.EXPLOSION_EMITTER,
                    pos.x(), pos.y() + 1, pos.z(), 5, 0.7, 0.7, 0.7, 1, true);
        }
        super.onDeath(hitResult);
    }

    /**
     * 被其他爆炸波及时提前引爆（连锁爆炸）。
     *
     * <p>见类注释第 2 点：26.2 的 {@code hurt} 返回 {@code void} 且不作为覆写点，
     * 服务端伤害入口是 {@code hurtServer}。
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.triggerOnExplode && !this.exploded && source.is(DamageTypeTags.IS_EXPLOSION)) {
            // 3 tick 后引爆，形成连锁而非同帧递归
            this.setLife(this.tickCount + 3);
            return true;
        }
        return super.hurtServer(level, source, amount);
    }

    // ---------------- 属性存取 ----------------

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = damage;
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    public boolean isDestroyBlocks() {
        return destroyBlocks;
    }

    public void setDestroyBlocks(boolean destroyBlocks) {
        this.destroyBlocks = destroyBlocks;
    }

    /** 暂未生效，见类注释第 1 点（依赖未移植的网络层）。 */
    public double getScreenShakeTime() {
        return screenShakeTime;
    }

    public void setScreenShakeTime(double screenShakeTime) {
        this.screenShakeTime = screenShakeTime;
    }

    /** 暂未生效，见类注释第 1 点（依赖未移植的网络层）。 */
    public double getScreenShakeAmplitude() {
        return screenShakeAmplitude;
    }

    public void setScreenShakeAmplitude(double screenShakeAmplitude) {
        this.screenShakeAmplitude = screenShakeAmplitude;
    }

    /** 暂未生效，见类注释第 1 点（原版 explode 不支持破坏力倍率）。 */
    public float getDestroyMultiplier() {
        return destroyMultiplier;
    }

    public void setExplodeDestroyMultiplier(float destroyMultiplier) {
        this.destroyMultiplier = destroyMultiplier;
    }

    public boolean isTriggerOnExplode() {
        return triggerOnExplode;
    }

    public void setTriggerOnExplode(boolean triggerOnExplode) {
        this.triggerOnExplode = triggerOnExplode;
    }

    public boolean isRemoteDetonation() {
        return remoteDetonation;
    }

    public void setRemoteDetonation(boolean remoteDetonation) {
        this.remoteDetonation = remoteDetonation;
    }
}
