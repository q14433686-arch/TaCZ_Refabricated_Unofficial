// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.runtime;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangContext;
import com.tacz.guns.client.particle.bedrock.common.molang.MolangEngineHelper;
import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.molang.MochaEngine;
import com.tacz.guns.client.particle.bedrock.molang.runtime.value.MutableObjectBinding;
import com.tacz.guns.client.particle.bedrock.molang.runtime.value.NumberValue;

/**
 * 粒子系统专用的 Molang 运行时环境。
 * <p>
 * 管理一个共享的 {@link MochaEngine}，并提供粒子/发射器相关的变量绑定。
 * 在每次求值前，通过 {@link #bindEmitter}/{@link #bindParticle} 更新当前上下文。
 */
public class ParticleMolangEnvironment {
    private final MochaEngine<?> engine;
    private final MolangContext<?> context;

    /** 创建独立的 Molang 环境 */
    public ParticleMolangEnvironment() {
        this.context = new MolangContext<>();
        this.engine = MolangEngineHelper.createEngine(context);
        registerVariables();
    }

    private void registerVariables() {
        MutableObjectBinding variableStorage = context.getVariableStorage();
        variableStorage.set("emitter_age", NumberValue.of(0));
        variableStorage.set("emitter_lifetime", NumberValue.of(0));
        variableStorage.set("emitter_random_1", NumberValue.of(0));
        variableStorage.set("emitter_random_2", NumberValue.of(0));
        variableStorage.set("emitter_random_3", NumberValue.of(0));
        variableStorage.set("emitter_random_4", NumberValue.of(0));

        variableStorage.set("particle_age", NumberValue.of(0));
        variableStorage.set("particle_lifetime", NumberValue.of(0));
        variableStorage.set("particle_random_1", NumberValue.of(0));
        variableStorage.set("particle_random_2", NumberValue.of(0));
        variableStorage.set("particle_random_3", NumberValue.of(0));
        variableStorage.set("particle_random_4", NumberValue.of(0));
    }

    /**
     * 绑定发射器状态到 Molang 变量。在发射器相关表达式求值前调用。
     */
    public void bindEmitter(float age, float lifetime, int r1, int r2, int r3, int r4) {
        MutableObjectBinding variableStorage = context.getVariableStorage();
        variableStorage.set("emitter_age", NumberValue.of(age));
        variableStorage.set("emitter_lifetime", NumberValue.of(lifetime));
        variableStorage.set("emitter_random_1", NumberValue.of(r1));
        variableStorage.set("emitter_random_2", NumberValue.of(r2));
        variableStorage.set("emitter_random_3", NumberValue.of(r3));
        variableStorage.set("emitter_random_4", NumberValue.of(r4));
    }

    /**
     * 绑定粒子状态到 Molang 变量。在粒子相关表达式求值前调用。
     */
    public void bindParticle(float age, float lifetime, float r1, float r2, float r3, float r4) {
        MutableObjectBinding variableStorage = context.getVariableStorage();
        variableStorage.set("particle_age", NumberValue.of(age));
        variableStorage.set("particle_lifetime", NumberValue.of(lifetime));
        variableStorage.set("particle_random_1", NumberValue.of(r1));
        variableStorage.set("particle_random_2", NumberValue.of(r2));
        variableStorage.set("particle_random_3", NumberValue.of(r3));
        variableStorage.set("particle_random_4", NumberValue.of(r4));
    }

    /**
     * 编译 Molang 表达式字符串为 {@link MolangExpression}。
     */
    public MolangExpression compile(String expression) {
        return MolangEngineHelper.compileExpression(engine, expression);
    }

    /**
     * 直接求值 Molang 表达式字符串。
     */
    public double eval(String expression) {
        return engine.eval(expression);
    }

    /**
     * 获取此环境的 MolangContext。
     */
    public MolangContext<?> getContext() {
        return context;
    }

    /**
     * 获取 variable 存储，允许外部设置自定义变量。
     */
    public MutableObjectBinding getVariableStorage() {
        return context.getVariableStorage();
    }

    public MochaEngine<?> getEngine() {
        return engine;
    }
}
