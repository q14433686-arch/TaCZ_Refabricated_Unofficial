// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.molang.runtime;

import com.tacz.guns.client.particle.bedrock.molang.runtime.binding.Entity;
import com.tacz.guns.client.particle.bedrock.molang.runtime.compiled.MochaCompiledFunction;

/**
 * 带上下文参数的 Molang 编译函数。
 * <p>
 * 与 {@link com.tacz.guns.client.particle.bedrock.molang.runtime.MochaFunction MochaFunction}
 * 不同，此接口的 evaluate 方法接收 {@link MolangContext} 参数，
 * 使得同一个编译后的表达式可以对不同 entity/上下文复用。
 */
@FunctionalInterface
public interface MolangExpression extends MochaCompiledFunction {
    double evaluate(@Entity MolangContext<?> context);

    /**
     * 返回一个始终求值为 0 的空表达式。
     */
    static MolangExpression zero() {
        return ctx -> 0D;
    }

    /**
     * 返回一个始终求值为指定常量的表达式。
     */
    static MolangExpression constant(double value) {
        return ctx -> value;
    }
}
