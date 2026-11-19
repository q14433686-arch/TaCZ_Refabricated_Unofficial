// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component;

/**
 * 粒子系统组件公共基接口。
 * <p>
 * {@link IEmitterComponent} 和 {@link IParticleComponent} 平级继承此接口。
 */
public interface IComponent {
    /**
     * 排序优先级。值越小越先执行。
     * <ul>
     *   <li>-1000 ~ -1：早期执行（如变量赋值、随机数初始化）</li>
     *   <li>0：形状/位置</li>
     *   <li>100~200：外观初始化</li>
     *   <li>300~400：运动和行为</li>
     *   <li>500：发射器核心（rate/lifetime）</li>
     *   <li>1000：默认</li>
     * </ul>
     */
    default int order() {
        return 1000;
    }
}
