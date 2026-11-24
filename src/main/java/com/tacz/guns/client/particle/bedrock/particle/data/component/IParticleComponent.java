// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component;

import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;

/**
 * 粒子运行时组件接口。每个 particle 持有独立实例，可持有运行时状态。
 * <p>
 * 定义层请使用 {@link IParticleComponentDefinition}。
 */
public interface IParticleComponent extends IComponent {
    /**
     * 粒子 spawn 时调用一次。
     */
    default void apply(ParticleInstance particle) {}

    /**
     * 粒子每 tick 调用。
     */
    default void update(ParticleInstance particle) {}
}
