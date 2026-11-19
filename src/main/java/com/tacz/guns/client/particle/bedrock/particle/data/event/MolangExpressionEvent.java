// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.event;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;

/**
 * Molang 表达式事件节点。触发时执行 Molang 表达式。
 *
 * @param expression         原始表达式字符串（调试用）
 * @param compiledExpression 预编译的表达式
 */
public record MolangExpressionEvent(String expression, MolangExpression compiledExpression) implements IEventNode {

    public static MolangExpressionEvent of(String expression, ParticleMolangEnvironment molang) {
        if (expression == null || expression.isEmpty()) {
            return new MolangExpressionEvent("", MolangExpression.constant(0));
        }
        return new MolangExpressionEvent(expression, molang.compile(expression));
    }
}
