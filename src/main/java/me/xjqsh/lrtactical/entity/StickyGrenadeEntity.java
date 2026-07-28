package me.xjqsh.lrtactical.entity;

import me.xjqsh.lrtactical.EquipmentMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 粘性手雷 —— 命中后不弹跳，直接粘在方块或实体上，到时引爆。
 *
 * <p>继承 {@link GrenadeEntity}：爆炸逻辑、数据字段（伤害/半径/连锁引爆）
 * 全部复用，本类只负责「粘附」。数据也共用 {@code ExplodeThrowableData}。
 *
 * <h2>26.2 移植要点（均经字节码确认）</h2>
 * <ol>
 *   <li><b>{@code Rotations} 已变成 {@code record}</b>：
 *       访问器由 {@code getX()/getY()/getZ()} 改为 <b>{@code x()/y()/z()}</b>。
 *       构造器 {@code (float,float,float)} 未变。</li>
 *   <li><b>{@code Entity#lerpTo(...)} 已移除</b>。上游覆写它来「粘在实体上时
 *       忽略服务端位置插值，防止抖动」。26.2 的对应方法是
 *       {@code lerpPositionAndRotationStep(IDDDDD)}（字节码确认）。
 *       <p>但本类<b>不覆写它</b> —— 见下方「与上游的差异」。</li>
 *   <li><b>{@code Block#getSoundType(state,level,pos,entity)} 已移除</b>，
 *       只剩 {@code state.getSoundType()}（无参，在 {@code BlockBehaviour} 上）。
 *       与基类 {@code ThrowableItemEntity#onHit} 里已处理的是同一处变更。</li>
 *   <li><b>{@code EntityType.Builder}</b>：{@code setShouldReceiveVelocityUpdates}
 *       已移除；{@code setTrackingRange/setUpdateInterval} →
 *       {@code clientTrackingRange/updateInterval}；{@code build(String)} →
 *       {@code build(ResourceKey)}。沿用 {@link GrenadeEntity} 的既有写法。</li>
 *   <li><b>音效</b>：上游还会播 {@code ModSounds.GRENADE_BOUNCE}（原作受限素材，
 *       本移植不打包），已删除，只保留方块本身的脚步声 ——
 *       与基类对同一音效的处理一致。</li>
 * </ol>
 *
 * <h2>与上游的差异：不覆写位置插值</h2>
 * 上游覆写 {@code lerpTo} 让「粘在实体上」时忽略服务端位置同步。
 * 本移植改用更简单也更可靠的做法：<b>粘附位置每 tick 由两端各自算出</b>
 * （{@code STUCK_ENTITY_ID} / {@code STUCK_OFFSET} / {@code STUCK_ROTATION}
 * 都是同步数据，客户端拿得到宿主实体与偏移量，能自行推出正确位置）。
 * 既然两端算的是同一个结果，服务端的位置包本就不会造成偏差，
 * 也就无需拦截插值 —— 少覆写一个签名已变的方法，少一处风险。
 */
public class StickyGrenadeEntity extends GrenadeEntity {
    public static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "sticky_grenade"));

    public static final EntityType<StickyGrenadeEntity> TYPE = EntityType.Builder
            .<StickyGrenadeEntity>of(StickyGrenadeEntity::new, MobCategory.MISC)
            .noSave().noSummon().fireImmune()
            .sized(0.3F, 0.3F)
            .clientTrackingRange(64).updateInterval(1)
            .build(KEY);

    private static final EntityDataAccessor<Boolean> STICKED =
            SynchedEntityData.defineId(StickyGrenadeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> STUCK_ENTITY_ID =
            SynchedEntityData.defineId(StickyGrenadeEntity.class, EntityDataSerializers.INT);
    /** 相对宿主实体的偏移（借用 Rotations 存三个 float，与上游一致）。 */
    private static final EntityDataAccessor<Rotations> STUCK_OFFSET =
            SynchedEntityData.defineId(StickyGrenadeEntity.class, EntityDataSerializers.ROTATIONS);
    /** 相对宿主实体的朝向：x=pitch, y=yaw。 */
    private static final EntityDataAccessor<Rotations> STUCK_ROTATION =
            SynchedEntityData.defineId(StickyGrenadeEntity.class, EntityDataSerializers.ROTATIONS);

    @Nullable
    private BlockPos stuckBlockPos;
    @Nullable
    private UUID stuckEntityUUID;

    public StickyGrenadeEntity(LivingEntity owner, Level level, int lifeTime) {
        super(TYPE, owner, level, lifeTime);
    }

    public StickyGrenadeEntity(EntityType<? extends StickyGrenadeEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STICKED, false);
        builder.define(STUCK_ENTITY_ID, -1);
        builder.define(STUCK_OFFSET, new Rotations(0, 0, 0));
        builder.define(STUCK_ROTATION, new Rotations(0, 0, 0));
    }

    public boolean isSticked() {
        return this.entityData.get(STICKED);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isSticked()) {
            return;
        }
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);

        // 粘在方块上：方块没了就掉下来
        if (!this.level().isClientSide() && this.stuckBlockPos != null) {
            BlockState state = this.level().getBlockState(this.stuckBlockPos);
            if (state.isAir()) {
                this.detach();
                return;
            }
        }

        // 粘在实体上：每 tick 跟随宿主。两端各自计算，结果一致。
        int entityId = this.entityData.get(STUCK_ENTITY_ID);
        if (entityId == -1) {
            return;
        }
        Entity host = this.level().getEntity(entityId);
        // 服务端在实体 id 失效时用 UUID 兜底（跨区块重载后 id 可能变）
        if (host == null && this.level() instanceof ServerLevel serverLevel && this.stuckEntityUUID != null) {
            host = serverLevel.getEntity(this.stuckEntityUUID);
            if (host != null) {
                this.entityData.set(STUCK_ENTITY_ID, host.getId());
            }
        }
        if (host == null || !host.isAlive()) {
            if (!this.level().isClientSide()) {
                this.detach();
            }
            return;
        }

        Rotations offsetRot = this.entityData.get(STUCK_OFFSET);
        // 26.2: Rotations 是 record，访问器是 x()/y()/z() 而非 getX()/getY()/getZ()
        Vec3 offset = new Vec3(offsetRot.x(), offsetRot.y(), offsetRot.z());
        float hostYRot = getEntityRotation(host);
        Vec3 rotatedOffset = offset.yRot(-hostYRot * Mth.DEG_TO_RAD);
        this.setPos(host.position().add(0, host.getBbHeight() * 0.5, 0).add(rotatedOffset));

        Rotations relativeRot = this.entityData.get(STUCK_ROTATION);
        this.setYRot(hostYRot + relativeRot.y());
        this.setXRot(relativeRot.x());
    }

    /** 粘住后不再按飞行速度更新朝向，否则会不停打转。 */
    @Override
    protected void updateRotation() {
        if (this.isSticked()) {
            return;
        }
        super.updateRotation();
    }

    /** 粘住后不再做碰撞推进。 */
    @Override
    public BounceResult doMultiBounce(Vec3 deltaMovement) {
        if (this.isSticked()) {
            return new BounceResult(this.position(), Vec3.ZERO);
        }
        Vec3 start = this.position();
        Vec3 end = start.add(deltaMovement);
        HitResult hitResult = this.getHitResult(start, end, deltaMovement, this::canHitEntity, this.level());

        if (hitResult.getType() != HitResult.Type.MISS) {
            // 只在服务端处理命中：粘附状态靠同步数据下发，客户端不自行判定
            if (!this.level().isClientSide()) {
                this.onHit(hitResult);
            }
            return new BounceResult(this.position(), Vec3.ZERO);
        }
        return new BounceResult(end, deltaMovement);
    }

    /**
     * 命中即粘附。
     *
     * <p><b>刻意不调用 {@code super.onHit}</b>：基类的实现里
     * {@code if (!shouldBounce || landedFlat) onDeath(result)} ——
     * 而粘性雷正是「不弹跳」的，走基类会<b>一碰就爆</b>，
     * 完全失去「粘住、等引信烧完再炸」的意义。
     */
    @Override
    protected void onHit(@NotNull HitResult result) {
        if (this.isSticked() || result.getType() == HitResult.Type.MISS) {
            return;
        }
        if (result.getType() == HitResult.Type.BLOCK) {
            stickToBlock((BlockHitResult) result);
        } else if (result.getType() == HitResult.Type.ENTITY) {
            stickToEntity((EntityHitResult) result);
        }
    }

    private void stickToBlock(BlockHitResult blockResult) {
        BlockPos resultPos = blockResult.getBlockPos();
        BlockState state = this.level().getBlockState(resultPos);
        // 26.2: getSoundType 只剩无参版（在 BlockBehaviour 上），与基类同一处变更。
        SoundEvent event = state.getSoundType().getStepSound();
        Vec3 loc = blockResult.getLocation();
        this.level().playSound(null, loc.x, loc.y, loc.z, event, SoundSource.AMBIENT, 2.0F, 1.0F);
        // 上游此处还会播 ModSounds.GRENADE_BOUNCE（原作受限音效），本移植不打包。

        this.entityData.set(STICKED, true);
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        // 稍微嵌进表面一点，避免悬空感
        this.setPos(loc.subtract(
                -blockResult.getDirection().getStepX() * 0.15,
                -blockResult.getDirection().getStepY() * 0.15 + 0.15,
                -blockResult.getDirection().getStepZ() * 0.15));

        this.stuckBlockPos = resultPos;
        alignToVec(Vec3.atLowerCornerOf(blockResult.getDirection().getUnitVec3i()));
    }

    private void stickToEntity(EntityHitResult entityResult) {
        Entity host = entityResult.getEntity();
        if (host == this.getOwner() || host == this.getVehicle()) {
            return;
        }
        // 26.2: Entity#hurt 返回 void；服务端判定入口是 hurtServer。
        // 这里只需要「造成撞击伤害」这个副作用，不关心是否真的打中，
        // 且 doMultiBounce 已保证只在服务端调用，故用 hurtServer。
        if (this.level() instanceof ServerLevel serverLevel) {
            host.hurtServer(serverLevel, host.damageSources().thrown(this, this.getOwner()), this.getHitDamage());
        }

        this.entityData.set(STICKED, true);
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);

        this.stuckEntityUUID = host.getUUID();
        this.entityData.set(STUCK_ENTITY_ID, host.getId());

        // 把命中点换算成宿主本地坐标系下的偏移，这样宿主转身时手雷会跟着转
        Vec3 globalOffset = entityResult.getLocation()
                .subtract(host.position().add(0, host.getBbHeight() * 0.5, 0));
        alignToVec(globalOffset);
        float hostYRot = getEntityRotation(host);
        Vec3 localOffset = globalOffset.yRot(hostYRot * Mth.DEG_TO_RAD).scale(0.75F);

        this.entityData.set(STUCK_OFFSET,
                new Rotations((float) localOffset.x, (float) localOffset.y, (float) localOffset.z));
        this.entityData.set(STUCK_ROTATION,
                new Rotations(this.getXRot(), this.getYRot() - hostYRot, 0));
    }

    /** 脱落：恢复重力与自由飞行。 */
    private void detach() {
        this.entityData.set(STICKED, false);
        this.entityData.set(STUCK_ENTITY_ID, -1);
        this.setNoGravity(false);
        this.stuckBlockPos = null;
        this.stuckEntityUUID = null;
    }

    /** 让模型朝向给定方向（贴合表面法线 / 命中方向）。 */
    private void alignToVec(Vec3 vec) {
        if (vec.lengthSqr() < 1.0E-7D) {
            return;
        }
        Vec3 n = vec.normalize();
        this.setYRot((float) (Mth.atan2(-n.x, n.z) * Mth.RAD_TO_DEG));
        this.setXRot((float) (-Mth.atan2(n.y, Math.sqrt(n.x * n.x + n.z * n.z)) * Mth.RAD_TO_DEG));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    /** 生物用身体朝向（而非头部），否则粘在背上的雷会随视线乱转。 */
    private static float getEntityRotation(Entity entity) {
        return entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
    }
}
