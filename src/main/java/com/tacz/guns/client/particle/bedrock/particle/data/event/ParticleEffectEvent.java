// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.event;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import org.jetbrains.annotations.Nullable;

/**
 * 粒子效果事件节点。触发时创建子发射器。
 *
 * @param effect              子粒子效果标识符
 * @param type                发射器类型
 * @param preEffectExpression 创建子发射器前执行的 Molang 表达式（原始字符串，调试用）
 * @param compiledPreEffect   预编译的 pre_effect_expression
 */
public record ParticleEffectEvent(
        String effect,
        Type type,
        @Nullable String preEffectExpression,
        @Nullable MolangExpression compiledPreEffect
) implements IEventNode {

    public enum Type {
        EMITTER,
        EMITTER_BOUND,
        PARTICLE,
        PARTICLE_WITH_VELOCITY;

        public static Type fromString(String str) {
            return switch (str.toLowerCase()) {
                case "emitter_bound" -> EMITTER_BOUND;
                case "particle" -> PARTICLE;
                case "particle_with_velocity" -> PARTICLE_WITH_VELOCITY;
                default -> EMITTER;
            };
        }
    }

    public static ParticleEffectEvent of(String effect, Type type, @Nullable String preExpr, ParticleMolangEnvironment molang) {
        MolangExpression compiled = (preExpr != null && !preExpr.isEmpty())
                ? molang.compile(preExpr) : null;
        return new ParticleEffectEvent(effect, type, preExpr, compiled);
    }
}
