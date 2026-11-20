// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component;

/**
 * 粒子组件定义接口。定义层组件来自 JSON 反序列化，存放于 {@code ParticlePreset}，不可变。
 *
 * @see IParticleComponent 运行时接口
 */
public interface IParticleComponentDefinition extends IComponent {
    /**
     * 是否需要每帧调用运行时的 {@link IParticleComponent#update}。
     * 在 Preset 构建时调用一次。
     */
    default boolean requireUpdate() { return false; }

    /**
     * 创建此定义组件的运行时实例。
     * <p>
     * 无状态组件可返回 {@code this}（需同时实现 {@link IParticleComponent}）。
     * 有状态组件必须返回独立的 Runtime 实例。
     */
    default IParticleComponent createRuntime() {
        return (IParticleComponent) this;
    }

    /** 排序优先级，值越小越先执行。默认 1000。 */
    @Override
    default int order() { return 1000; }
}
