// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.runtime;

import com.tacz.guns.client.particle.bedrock.particle.data.component.IParticleComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 单个粒子实例。所有坐标都在局部坐标系中。
 */
public class ParticleInstance {
    // 位置（局部坐标）
    public float x, y, z;
    /** Shape-space birth position used by parametric relative_position. */
    public float originX,originY,originZ;
    // 速度
    public float vx, vy, vz;
    // 颜色
    public float r = 1f, g = 1f, b = 1f, a = 1f;
    // 尺寸
    public float width = 0.1f, height = 0.1f;
    // 旋转（度）
    public float rotation;
    public float rotationRate;
    // 生命周期
    public float age;
    public float maxLifetime;
    // 随机种子（用于 Molang variable.particle_random_N）
    public float random1, random2, random3, random4;
    // UV
    public float u0, v0, u1, v1;
    // 发射时的骨骼缩放快照
    public float spawnScale = 1f;
    // 是否存活
    public boolean alive = true;
    // 标记该粒子是否在世界空间中（emitter_local_space.position=false 时为 true）
    public boolean worldSpace = false;
    // 标记该粒子是否在第一人称脱离模式（发射后脱离定位器，但不投放到世界）
    public boolean fpDetached = false;
    // KillPlane 符号追踪
    public boolean insideKillPlane = false;

    /** 所属发射器（用于事件触发和 Molang 上下文访问） */
    @Nullable
    public ParticleEmitterInstance emitter;

    /** 粒子运行时组件列表 */
    public List<IParticleComponent> updateComponents = List.of();

    public ParticleInstance() {
    }

    /**
     * 重置粒子状态以便复用（对象池）。
     */
    public void reset() {
        x = y = z = 0;
        originX=originY=originZ=0;
        vx = vy = vz = 0;
        r = g = b = a = 1f;
        width = height = 0.1f;
        rotation = 0;
        rotationRate = 0;
        age = 0;
        maxLifetime = 1;
        random1 = random2 = random3 = random4 = 0;
        u0 = v0 = 0;
        u1 = v1 = 1;
        spawnScale = 1f;
        alive = true;
        worldSpace = false;
        fpDetached = false;
        insideKillPlane = false;
    }

    /**
     * 更新粒子位置和年龄。
     * @param dt 时间步长（秒）
     */
    public void tick(float dt) {
        x += vx * dt;
        y += vy * dt;
        z += vz * dt;
        rotation += rotationRate * dt;
        age += dt;
        if (age >= maxLifetime) {
            alive = false;
        }
    }
}
