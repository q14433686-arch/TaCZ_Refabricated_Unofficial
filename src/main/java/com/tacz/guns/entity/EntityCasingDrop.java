package com.tacz.guns.entity;

import com.tacz.guns.api.item.enums.CaseMaterial;
import com.tacz.guns.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 弹壳掉落实体。
 * <p>
 * 射击后弹壳从枪械抛壳口弹出，以物理方式掉落。
 * 玩家可以拾取弹壳，用于复装系统（P4制造链）。
 * <p>
 * 弹壳实体携带以下信息：
 * <ul>
 *   <li>口径类型标识符（如 tacz:9mm）</li>
 *   <li>弹壳材质（黄铜/钢/铝/聚合物）</li>
 *   <li>弹壳状态（击发后状态变化）</li>
 *   <li>是否已被拾取</li>
 * </ul>
 * <p>
 * 对应设计文档：B.2.5 复装系统完整流程 - 步骤1：回收空弹壳
 * <p>
 * 性能考虑：
 * - 弹壳实体使用较小的碰撞箱（0.125×0.125），不进行实体碰撞检测
 * - 5秒后自动转化为ItemEntity（如果未被拾取）
 * - 客户端追踪范围小（3格），更新间隔大（10tick）
 */
public class EntityCasingDrop extends Entity {

    public static final EntityType<EntityCasingDrop> TYPE = EntityType.Builder
            .<EntityCasingDrop>of(EntityCasingDrop::new, MobCategory.MISC)
            .noSummon().noSave()
            .sized(0.125F, 0.125F)
            .clientTrackingRange(3).updateInterval(10)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("tacz", "casing_drop")));

    /** 弹壳存活时间（tick），5秒后自动转化为ItemEntity */
    private static final int LIFETIME = 100;

    /** 口径类型标识符 */
    @Nullable
    private Identifier cartridgeType;
    /** 弹壳材质 */
    private CaseMaterial caseMaterial = CaseMaterial.BRASS;
    /** 弹壳状态（击发后状态） */
    private String caseCondition = "good";
    /** 存活计时器 */
    private int age = 0;

    public EntityCasingDrop(EntityType<? extends Entity> type, Level level) {
        super(type, level);
    }

    public EntityCasingDrop(Level level, double x, double y, double z,
                            @Nullable Identifier cartridgeType,
                            CaseMaterial caseMaterial,
                            String caseCondition) {
        this(TYPE, level);
        this.setPos(x, y, z);
        this.cartridgeType = cartridgeType;
        this.caseMaterial = caseMaterial;
        this.caseCondition = caseCondition;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        // 不需要同步数据，弹壳实体是短暂存在的
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;

        // 物理运动：重力 + 阻力
        Vec3 movement = this.getDeltaMovement();
        this.setDeltaMovement(movement.x * 0.98, movement.y - 0.04, movement.z * 0.98);

        // 碰撞检测：与方块碰撞
        Vec3 pos = this.position();
        Vec3 nextPos = pos.add(this.getDeltaMovement());
        if (this.level().getBlockState(BlockPos.containing(nextPos)).isSolid()) {
            // 碰到方块，反弹（简化）
            this.setDeltaMovement(movement.x * -0.3, movement.y * -0.3, movement.z * -0.3);
        } else {
            this.setPos(nextPos);
        }

        // 超时：转化为ItemEntity
        if (this.age >= LIFETIME) {
            convertToItemEntity();
            return;
        }

        // 检测附近玩家拾取
        if (!this.level().isClientSide()) {
            AABB pickupBox = this.getBoundingBox().inflate(0.5);
            for (Player player : this.level().getEntitiesOfClass(Player.class, pickupBox)) {
                if (tryPickup(player)) {
                    this.discard();
                    return;
                }
            }
        }
    }

    /**
     * 尝试让玩家拾取弹壳。
     * <p>
     * 拾取后，弹壳转化为弹药物品（用于复装）。
     * 如果弹壳不可复装（钢/铝/聚合物），则拾取为废金属。
     *
     * @param player 拾取玩家
     * @return 是否成功拾取
     */
    private boolean tryPickup(Player player) {
        // TODO: P4制造链实现后，此处应创建弹壳物品
        // 目前简化为：拾取后直接给予玩家一个通用的"空弹壳"物品
        // 拾取逻辑将在P4制造链中完善
        return true;
    }

    /**
     * 超时后将弹壳实体转化为ItemEntity。
     */
    private void convertToItemEntity() {
        if (this.level().isClientSide()) {
            this.discard();
            return;
        }
        // TODO: P4制造链实现后，此处应创建弹壳物品的ItemStack
        // 目前简化为直接消失
        this.discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("CartridgeType")) {
            this.cartridgeType = Identifier.parse(tag.getString("CartridgeType"));
        }
        if (tag.contains("CaseMaterial")) {
            try {
                this.caseMaterial = CaseMaterial.valueOf(tag.getString("CaseMaterial"));
            } catch (IllegalArgumentException ignored) {
                this.caseMaterial = CaseMaterial.BRASS;
            }
        }
        if (tag.contains("CaseCondition")) {
            this.caseCondition = tag.getString("CaseCondition");
        }
        this.age = tag.getInt("Age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.cartridgeType != null) {
            tag.putString("CartridgeType", this.cartridgeType.toString());
        }
        tag.putString("CaseMaterial", this.caseMaterial.name());
        tag.putString("CaseCondition", this.caseCondition);
        tag.putInt("Age", this.age);
    }

    // ====== Getter ======

    @Nullable
    public Identifier getCartridgeType() {
        return cartridgeType;
    }

    public CaseMaterial getCaseMaterial() {
        return caseMaterial;
    }

    public String getCaseCondition() {
        return caseCondition;
    }

    public int getAge() {
        return age;
    }

    // ====== 设置弹壳弹出方向 ======

    /**
     * 设置弹壳抛出方向和速度。
     * <p>
     * 模拟真实抛壳：弹壳从枪械右侧上方弹出，带有旋转。
     *
     * @param shooterYaw   射手朝向（度）
     * @param ejectSide    抛壳方向（1=右侧，-1=左侧）
     * @param ejectSpeed   抛壳速度
     */
    public void setEjectDirection(float shooterYaw, int ejectSide, float ejectSpeed) {
        double rad = Math.toRadians(shooterYaw);
        // 向侧面和上方弹出
        double x = -Math.sin(rad) * ejectSide * ejectSpeed;
        double y = ejectSpeed * 0.5; // 向上
        double z = Math.cos(rad) * ejectSide * ejectSpeed;
        this.setDeltaMovement(x, y, z);
    }
}
