package me.xjqsh.lrtactical.entity;

import cn.sh1rocu.tacz.api.extension.IEntityAdditionalSpawnData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * 可投掷物实体基类（手雷等）—— 会弹跳、有寿命、落地/超时后触发 {@link #onDeath}。
 *
 * <h2>26.2 / Fabric 移植要点</h2>
 *
 * <b>1. {@code IEntityWithComplexSpawn} → {@link IEntityAdditionalSpawnData}</b><br>
 * 前者是 NeoForge 专有接口。本仓库已有等价的 Fabric 实现
 * （{@code cn.sh1rocu.tacz.api.extension}，TACZ 的子弹实体在用），直接复用。
 * 注意其方法签名是 {@link FriendlyByteBuf} 而非 {@code RegistryFriendlyByteBuf}
 * —— 这直接影响下面第 4 点。
 *
 * <b>2. 实体存档已从 {@code CompoundTag} 换成 {@code ValueInput}/{@code ValueOutput}</b><br>
 * 26.2 字节码确认签名为
 * {@code addAdditionalSaveData(ValueOutput)} / {@code readAdditionalSaveData(ValueInput)}。
 * 沿用旧的 {@code CompoundTag} 签名会「静默地不覆写父类方法」——
 * 编译器只报 {@code @Override} 错误，真正的危害是<b>存档读写整个失效</b>。
 * 本仓库的方块实体（{@code GunSmithTableBlockEntity} 等）已在用新 API，此处保持一致。
 *
 * <b>3. 复合数据改用 codec 存取</b><br>
 * {@code ItemStack#save/parse} 已移除；{@code ValueOutput} 提供
 * {@code store(key, codec, value)}，{@code ValueInput} 提供 {@code read(key, codec)}。
 * <b>必须用 OPTIONAL_CODEC</b>：普通 {@code CODEC} 的 count 取值范围是 {@code [1,99]}，
 * 对 {@code ItemStack.EMPTY} 会直接抛异常 —— 这正是本项目
 * 「卸除配件复制物品」与「联机全服踢线」两个严重 bug 的同源坑
 * （详见 PORTING_NOTES 6.2）。
 *
 * <b>4. 尾迹粒子的同步方式改为「按注册名」</b><br>
 * 上游用 {@code ParticleTypes.STREAM_CODEC} 编解码，但该 codec 的签名是
 * {@code StreamCodec<RegistryFriendlyByteBuf, ParticleOptions>}（字节码确认），
 * <b>需要带注册表的 buf</b>，而第 1 点里的接口只给普通 {@code FriendlyByteBuf}。
 *
 * <p>解法：只同步<b>粒子类型的注册名</b>。这样做是安全的，因为本类的尾迹粒子
 * 全部来自数据驱动配置、且都是<b>无额外参数</b>的 {@link SimpleParticleType}
 * （字节码确认 {@code SimpleParticleType implements ParticleOptions}，
 * 即类型本身就是一个合法的 options 实例）。
 * <b>不</b>把它硬转成需要参数的粒子 —— 若将来真需要带参粒子，
 * 应改走带注册表的通道，而不是在这里猜参数。
 */
public abstract class ThrowableItemEntity extends Projectile
        implements IEntityAdditionalSpawnData, net.minecraft.world.entity.projectile.ItemSupplier {
    private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK =
            SynchedEntityData.defineId(ThrowableItemEntity.class, EntityDataSerializers.ITEM_STACK);

    private int life = 100;
    private float gravity = 0.07f;
    private double bounceFactor = 0.75;
    private boolean shouldBounce = true;
    private boolean brokeOnGround = false;
    private float hitDamage = 1.0f;
    @Nullable
    private ParticleOptions tailParticle = null;

    public ThrowableItemEntity(EntityType<? extends Projectile> type, LivingEntity shooter, Level level, int lifeTime) {
        super(type, level);
        this.setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
        this.setOwner(shooter);
        this.life = lifeTime;
    }

    public ThrowableItemEntity(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket(@NotNull ServerEntity entity) {
        return IEntityAdditionalSpawnData.getEntitySpawningPacket(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM_STACK, ItemStack.EMPTY);
    }

    @Override
    public void addAdditionalSaveData(@NotNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Life", this.life);
        ItemStack stack = this.getItemRaw();
        if (!stack.isEmpty()) {
            // 见类注释第 3 点：必须用 OPTIONAL_CODEC。
            output.store("Item", ItemStack.OPTIONAL_CODEC, stack);
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.life = input.getIntOr("Life", this.life);
        this.setItem(input.read("Item", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }

    protected ItemStack getItemRaw() {
        return this.getEntityData().get(DATA_ITEM_STACK);
    }

    /**
     * 实现 {@code ItemSupplier}，从而可直接复用原版的 {@code ThrownItemRenderer}
     * （雪球/末影珍珠等用的就是它）。
     *
     * <p>不实现该接口的话客户端<b>没有渲染器</b>，实体一进入视野就会
     * 在 {@code EntityRenderDispatcher#shouldRender} 抛 NPE 导致游戏崩溃。
     */
    @Override
    public ItemStack getItem() {
        ItemStack stack = this.getItemRaw();
        return stack.isEmpty() ? new ItemStack(this.getDefaultItem()) : stack;
    }

    public void setItem(ItemStack stack) {
        if (!stack.is(this.getDefaultItem()) || !stack.getComponentsPatch().isEmpty()) {
            this.getEntityData().set(DATA_ITEM_STACK, stack.copyWithCount(1));
        }
    }

    /**
     * 默认物品。保持为抽象，让每个实体类型显式声明自己的回退栈；当前投掷物子类
     * 都返回已注册的 {@code ModItems.THROWABLE}，不会依赖错误的通用占位物。
     */
    protected abstract Item getDefaultItem();

    @Override
    protected void onHit(@NotNull HitResult result) {
        if (result.getType() != HitResult.Type.MISS) {
            boolean landedFlat = this.brokeOnGround
                    && result instanceof BlockHitResult blockHitResult
                    && blockHitResult.getDirection() == Direction.UP;
            if (!this.shouldBounce || landedFlat) {
                this.onDeath(result);
                return;
            }
        }
        switch (result.getType()) {
            case BLOCK -> {
                BlockHitResult blockResult = (BlockHitResult) result;
                BlockPos resultPos = blockResult.getBlockPos();
                BlockState state = this.level().getBlockState(resultPos);
                // 26.2: getSoundType 只剩 (BlockState) 一个重载（字节码确认，且已移到
                // BlockBehaviour 上）。1.21.1 的 (state, level, pos, entity) 四参版本已移除，
                // 意味着方块无法再按位置/实体返回不同音效 —— 对本用途无影响。
                SoundEvent event = state.getSoundType().getStepSound();
                double speed = this.getDeltaMovement().length();
                if (speed > 0.1) {
                    this.level().playSound(null, result.getLocation().x, result.getLocation().y, result.getLocation().z,
                            event, SoundSource.AMBIENT, 2.0F, 1.0F);
                    // Audited limitation: upstream plays its ARR grenade-bounce asset here; that
                    // sound is intentionally not redistributed by this code-only LRTactical port.
                    //  音效资源属原作 All Rights Reserved 素材，本移植不打包，
                    //  故只保留方块本身的脚步声。内容包可自行提供弹跳音效。
                }
                state.onProjectileHit(this.level(), state, blockResult, this);
            }
            case ENTITY -> {
                EntityHitResult entityResult = (EntityHitResult) result;
                Entity entity = entityResult.getEntity();
                if (entity == this.getOwner() || entity == this.getVehicle()) {
                    return;
                }
                if (this.getDeltaMovement().length() > 0.1) {
                    entity.hurt(entity.damageSources().thrown(this, this.getOwner()), this.getHitDamage());
                }
            }
            default -> {
            }
        }
    }

    /** 实体最终处于的点和速度。 */
    public record BounceResult(Vec3 location, Vec3 deltaMovement) {
    }

    /**
     * 一 tick 内做多次碰撞检测，返回最终落点与下一 tick 起始速度。
     * 最多反弹 3 次，超过则判定为卡住并停下。
     */
    public BounceResult doMultiBounce(Vec3 deltaMovement) {
        Vec3 start = this.position();
        Vec3 end = start.add(deltaMovement);
        Vec3 endVecOffset = new Vec3(deltaMovement.x, deltaMovement.y, deltaMovement.z);
        for (int i = 0; i < 3; i++) {
            HitResult hitResult = this.getHitResult(start, end, endVecOffset, this::canHitEntity, this.level());
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockResult = (BlockHitResult) hitResult;
                Vec3 hit = blockResult.getLocation();
                if (blockResult.getDirection() == Direction.UP && start.y() - hit.y() < 0.01) {
                    hit = new Vec3(hit.x(), start.y(), hit.z());
                }
                if (i < 2) {
                    // 起点设为碰撞点稍前处，避免粘在方块上
                    start = start.lerp(hit, 0.8);
                    Vec3 rest = end.subtract(start);
                    endVecOffset = this.bounce(blockResult.getDirection(), rest);
                    end = start.add(endVecOffset);
                    deltaMovement = this.bounce(blockResult.getDirection(), deltaMovement);
                } else {
                    // 一 tick 内连撞三次，判定为卡住
                    end = start.lerp(hit, 0.8);
                    deltaMovement = Vec3.ZERO;
                }
            } else if (hitResult.getType() == HitResult.Type.ENTITY) {
                EntityHitResult entityResult = (EntityHitResult) hitResult;
                Entity entity = entityResult.getEntity();
                if (entity == this.getOwner() || entity == this.getVehicle()) {
                    break;
                }
                // 26.2: Direction.getNearest 的 (double,double,double) 重载已改名为
                // getApproximateNearest；现存的 getNearest 只剩 (int,int,int,Direction)
                // 与 (Vec3i,Direction) 两个整数版本，直接传 double 会编译失败。
                Direction direction = Direction.getApproximateNearest(
                        endVecOffset.x(), endVecOffset.y(), endVecOffset.z()).getOpposite();
                Vec3 hit = hitResult.getLocation();
                start = start.lerp(hit, 0.8);
                Vec3 rest = end.subtract(start);
                endVecOffset = this.bounce(direction, rest);
                end = start.add(endVecOffset);
                deltaMovement = this.bounce(direction, deltaMovement);
            } else {
                break;
            }
            this.onHit(hitResult);
        }
        return new BounceResult(end, deltaMovement);
    }

    public Vec3 bounce(Direction direction, Vec3 deltaMovement) {
        double factor = this.getBounceFactor();
        return switch (direction.getAxis()) {
            case X -> deltaMovement.multiply(-factor / 1.5, factor, factor);
            case Y -> {
                Vec3 newVec = deltaMovement.multiply(factor, -factor / 2.5, factor);
                if (newVec.y() < this.getThrowableGravity()) {
                    newVec = newVec.multiply(1, 0, 1);
                }
                yield newVec;
            }
            case Z -> deltaMovement.multiply(factor, factor, -factor / 1.5);
        };
    }

    public HitResult getHitResult(Vec3 start, Vec3 end, Vec3 endVecOffset, Predicate<Entity> filter, Level level) {
        HitResult hitResult = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hitResult.getType() != HitResult.Type.MISS) {
            end = hitResult.getLocation();
        }
        HitResult entityHit = getEntityHitResult(level, this, start, end,
                this.getBoundingBox().expandTowards(endVecOffset).inflate(1.0D), filter);
        return entityHit != null ? entityHit : hitResult;
    }

    @Nullable
    public static EntityHitResult getEntityHitResult(Level level, Entity projectile, Vec3 startVec, Vec3 endVec,
                                                     AABB boundingBox, Predicate<Entity> filter) {
        double nearest = Double.MAX_VALUE;
        Entity hitEntity = null;
        Vec3 hitPos = null;
        for (Entity candidate : level.getEntities(projectile, boundingBox, filter)) {
            AABB aabb = candidate.getBoundingBox().inflate(0.3);
            Optional<Vec3> clip = aabb.clip(startVec, endVec);
            if (clip.isPresent()) {
                double distance = startVec.distanceToSqr(clip.get());
                if (distance < nearest) {
                    hitEntity = candidate;
                    nearest = distance;
                    hitPos = clip.get();
                }
            }
        }
        return hitEntity == null ? null : new EntityHitResult(hitEntity, hitPos);
    }

    @Override
    public void tick() {
        super.tick();
        BounceResult result = this.doMultiBounce(this.getDeltaMovement());

        // 26.2: Entity#checkInsideBlocks() 的无参重载已移除（现只剩带
        // InsideBlockEffectApplier 的内部重载）。原版 ThrowableProjectile#tick
        // 改调 applyEffectsFromBlocks()（字节码确认），它在 Entity 上是 protected 无参版，
        // 语义等价：让实体受所在方块影响（如凋灵玫瑰、火焰）。
        this.applyEffectsFromBlocks();

        Vec3 motion = result.deltaMovement();
        double x = result.location().x();
        double y = result.location().y();
        double z = result.location().z();

        this.setDeltaMovement(motion);
        this.updateRotation();

        float drag;
        if (this.isInWater()) {
            for (int i = 0; i < 4; ++i) {
                this.level().addParticle(ParticleTypes.BUBBLE,
                        x - motion.x * 0.25D, y - motion.y * 0.25D, z - motion.z * 0.25D,
                        motion.x, motion.y, motion.z);
            }
            drag = 0.8F;
        } else {
            drag = 0.99F;
        }

        this.setDeltaMovement(motion.scale(drag));
        if (!this.isNoGravity()) {
            Vec3 current = this.getDeltaMovement();
            this.setDeltaMovement(current.x, current.y - this.getThrowableGravity(), current.z);
        }

        this.setPos(x, y, z);

        if (this.tickCount >= life && life > 0 && !this.level().isClientSide()) {
            this.onDeath(null);
        }

        if (this.level().isClientSide()) {
            this.renderTailParticle();
        }
    }

    public void renderTailParticle() {
        if (this.getTailParticle() != null) {
            // 26.2: addParticle 的单 boolean 重载已移除，现为
            // (particle, overrideLimiter, alwaysShow, x,y,z, xd,yd,zd)
            // —— 参数名取自 ClientLevel 的 LocalVariableTable。
            // 上游那个 true 对应 overrideLimiter（无视粒子数量限制）；
            // alwaysShow（无视距离/粒子设置强制显示）保持 false，维持原行为。
            this.level().addParticle(this.getTailParticle(), true, false,
                    this.getX(), this.getY() + 0.35, this.getZ(), 0.0D, 0.01D, 0.0D);
        }
    }

    /**
     * 生命周期结束时调用。
     *
     * @param hitResult 由撞击导致时为碰撞结果，超时则为 {@code null}
     */
    public void onDeath(@Nullable HitResult hitResult) {
        this.discard();
        // Audited limitation: upstream broadcasts an ARR death-sound asset here. The index network
        // layer is complete, but this code-only port deliberately neither redistributes nor requests that asset.
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double size = this.getBoundingBox().getSize() * 4.0D;
        if (Double.isNaN(size)) {
            size = 4.0D;
        }
        size *= 64.0D;
        return distance < size * size;
    }

    // ---------------- 属性存取 ----------------

    /**
     * 一次性套用数据包里的实体属性。
     *
     * <p>由各 {@code ThrowableType} 在创建实体时调用，避免逐字段 set。
     */
    public void setBaseData(me.xjqsh.lrtactical.item.throwable.EntityData data) {
        this.setThrowableGravity(data.getGravity());
        this.setBounceFactor(data.getBounceFactor());
        this.setShouldBounce(data.isShouldBounce());
        this.setHitDamage(data.getHitDamage());
        this.setBrokeOnGround(data.isBrokeOnGround());
        this.setTailParticle(data.getTailParticles());
    }

    public float getThrowableGravity() {
        return gravity;
    }

    public void setThrowableGravity(float gravity) {
        this.gravity = gravity;
    }

    public double getBounceFactor() {
        return bounceFactor;
    }

    public void setBounceFactor(double bounceFactor) {
        this.bounceFactor = bounceFactor;
    }

    public boolean shouldBounce() {
        return shouldBounce;
    }

    public void setShouldBounce(boolean shouldBounce) {
        this.shouldBounce = shouldBounce;
    }

    public int getLife() {
        return life;
    }

    public void setLife(int life) {
        this.life = life;
    }

    public float getHitDamage() {
        return hitDamage;
    }

    public void setHitDamage(float hitDamage) {
        this.hitDamage = hitDamage;
    }

    public boolean isBrokeOnGround() {
        return brokeOnGround;
    }

    public void setBrokeOnGround(boolean brokeOnGround) {
        this.brokeOnGround = brokeOnGround;
    }

    @Nullable
    public ParticleOptions getTailParticle() {
        return tailParticle;
    }

    public void setTailParticle(@Nullable ParticleOptions tailParticle) {
        this.tailParticle = tailParticle;
    }

    // ---------------- 生成数据同步 ----------------

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeInt(life);
        buffer.writeFloat(gravity);
        buffer.writeDouble(bounceFactor);
        buffer.writeBoolean(shouldBounce);
        buffer.writeBoolean(brokeOnGround);
        // 见类注释第 4 点：只同步注册名，避免依赖 RegistryFriendlyByteBuf。
        if (tailParticle != null) {
            Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(tailParticle.getType());
            if (id != null) {
                buffer.writeBoolean(true);
                buffer.writeIdentifier(id);
                return;
            }
        }
        buffer.writeBoolean(false);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        life = buffer.readInt();
        gravity = buffer.readFloat();
        bounceFactor = buffer.readDouble();
        shouldBounce = buffer.readBoolean();
        brokeOnGround = buffer.readBoolean();
        if (buffer.readBoolean()) {
            Identifier id = buffer.readIdentifier();
            // 只还原无参粒子（SimpleParticleType 自身即 ParticleOptions）。
            // 带参粒子无法仅凭注册名重建，此时保持 null 而不是猜一个默认值。
            tailParticle = BuiltInRegistries.PARTICLE_TYPE.getValue(id) instanceof SimpleParticleType simple
                    ? simple
                    : null;
        } else {
            tailParticle = null;
        }
    }
}
