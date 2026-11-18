// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.molang.runtime;

import com.tacz.guns.client.particle.bedrock.molang.runtime.binding.QueryBinding;
import com.tacz.guns.client.particle.bedrock.molang.runtime.value.MutableObjectBinding;
import com.maydaymemory.mae.basic.IEvaluationContext;

import org.jetbrains.annotations.Nullable;

/**
 * Molang 求值上下文。持有关联对象的引用、独立的 variable 存储。
 *
 * @param <T> 关联对象的类型
 */
public class MolangContext<T> implements IEvaluationContext {
    private @Nullable T entity;
    private final MutableObjectBinding variableStorage;
    private double animTime;

    public MolangContext() {
        this(null, new MutableObjectBinding());
    }

    public MolangContext(@Nullable T entity) {
        this(entity, new MutableObjectBinding());
    }

    public MolangContext(@Nullable T entity, MutableObjectBinding variableStorage) {
        this.entity = entity;
        this.variableStorage = variableStorage;
    }

    @Nullable
    public T getEntity() {
        return entity;
    }

    public void setEntity(@Nullable T entity) {
        this.entity = entity;
    }

    // === IEvaluationContext ===

    @Override
    public float getTimeS() {
        return (float) animTime;
    }

    @Override
    public void prepareEvaluation(float timeS) {
        this.animTime = timeS;
    }

    // === @QueryBinding 属性 ===

    /**
     * 当前动画播放时间（秒），对应 Molang {@code query.anim_time}。
     */
    @QueryBinding("anim_time")
    public double queryAnimTime() {
        return animTime;
    }

    // === Variable 存储 ===
    /**
     * 获取此 context 独立的 variable 存储。
     * 对应 Molang 中的 variable / v 命名空间。
     */
    public MutableObjectBinding getVariableStorage() {
        return variableStorage;
    }
}
