package com.tacz.guns.client.event;

import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.util.HeadShotAABBConfigRead;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 爆头判定盒的调试绘制。
 *
 * <p>1.21.11 把实体碰撞箱从 {@code SubmitNodeCollector#submitHitbox} /
 * {@code ShapeRenderer#renderLineBox} 整段换成了 {@link Gizmos}。
 * 移植时用 {@code submitCustomGeometry(RenderTypes.lines(), ...)} 去补，
 * 会在「显示爆头范围 + F3+B 碰撞箱」同时开启时把 LINES 几何丢进实体
 * custom-geometry 管线，直接崩溃。</p>
 *
 * <p>正确入口是原版 {@code EntityHitboxDebugRenderer#showHitboxes}：
 * 那时 per-frame {@code GizmoCollector} 已经挂上，{@link Gizmos#cuboid}
 * 才会真正被收集并画出来。</p>
 */
@Environment(EnvType.CLIENT)
public class RenderHeadShotAABB {
    /** ARGB 不透明黄，与 26.2 {@code submitShapeOutline(..., 0xFFFFFF00, ...)} 一致。 */
    private static final int HEADSHOT_COLOR = 0xFFFFFF00;

    public static void emitGizmo(Entity entity, float partialTick, boolean inLocalServer) {
        // 单人世界里原版会对客户端实体和本地服务器实体各画一次碰撞箱。
        // 爆头范围只跟客户端判定可视化，避免叠两层黄盒。
        if (inLocalServer || !RenderConfig.HEAD_SHOT_DEBUG_HITBOX.get()) {
            return;
        }
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return;
        }
        Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
        AABB aabb = entityId != null ? HeadShotAABBConfigRead.getAABB(entityId) : null;
        if (aabb == null) {
            float width = living.getBbWidth();
            float eyeHeight = living.getEyeHeight();
            // 扩张 0.01，避免和原版显示重合
            aabb = new AABB(-width / 2.0, eyeHeight - 0.25, -width / 2.0, width / 2.0, eyeHeight + 0.25, width / 2.0)
                    .inflate(0.01);
        }
        Vec3 pos = living.getPosition(partialTick);
        Gizmos.cuboid(aabb.move(pos), GizmoStyle.stroke(HEADSHOT_COLOR));
    }
}
