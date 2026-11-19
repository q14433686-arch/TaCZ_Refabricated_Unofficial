// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.common.molang;

import com.tacz.guns.client.particle.bedrock.molang.MochaEngine;
import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangContext;
import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.molang.runtime.binding.JavaObjectBinding;
import com.tacz.guns.client.particle.bedrock.molang.runtime.standard.MochaMath;
import com.tacz.guns.client.particle.bedrock.molang.runtime.value.MutableObjectBinding;

/**
 * 创建和管理 MolangEngine 的工具类。
 * <p>
 * {@code query.xxx} 通过 {@link com.tacz.guns.client.particle.bedrock.molang.runtime.binding.QueryBinding @QueryBinding}
 * 注解在 MolangContext 子类上声明，编译为直接方法调用。
 * <p>
 * 编译时可使用 {@link #createEngine(Class)}（只需类型信息），
 * 求值时再创建带实际对象的 context 实例传入。
 */
public final class MolangEngineHelper {
    private MolangEngineHelper() {}

    /**
     * 创建一个已绑定 {@link MolangContext} 实例的 MochaEngine。
     * 编译阶段需要 entity 实例时可使用。
     *
     * @param context 要绑定的 Molang 上下文
     * @return 配置完毕的引擎
     */
    public static MochaEngine<?> createEngine(MolangContext<?> context) {
        MochaEngine<?> engine = MochaEngine.create(context, builder -> {
            builder.set("math", JavaObjectBinding.of(MochaMath.class, null, new MochaMath()));
        });
        bindContext(engine, context);
        return engine;
    }

    /**
     * 根据 MolangContext 子类的类型创建 MochaEngine。
     * 不需要实体实例 — 编译器只需类型信息来扫描 {@code @QueryBinding} 注解方法。
     * <p>
     * 要求 context 子类有无参构造函数。
     * <pre>
     * // 编译阶段（加载动画时，实体尚未创建）
     * MochaEngine engine = MolangEngineHelper.createEngine(MolangEntityContext.class);
     * List&lt;BedrockAnimation&gt; anims = BedrockAnimation.createAnimation(file, model, engine);
     *
     * // 求值阶段（实体出现后）
     * MolangEntityContext&lt;Entity&gt; ctx = new MolangEntityContext&lt;&gt;(entity);
     * runner.evaluate(ctx);
     * </pre>
     *
     * @param contextClass MolangContext 子类
     * @return 配置完毕的引擎
     */
    public static MochaEngine<?> createEngine(Class<? extends MolangContext<?>> contextClass) {
        try {
            MolangContext<?> ctx = contextClass.getDeclaredConstructor().newInstance();
            return createEngine(ctx);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    "Cannot instantiate " + contextClass.getName() + ". Ensure it has a no-arg constructor.", e);
        }
    }

    /**
     * 创建一个标准的 MochaEngine，预配置 math 绑定。
     * 不含 entity 类型信息 —— query 编译回退到 scope 路径。
     */
    public static MochaEngine<?> createEngine() {
        return MochaEngine.create(null, builder -> {
            builder.set("math", JavaObjectBinding.of(MochaMath.class, null, new MochaMath()));
        });
    }

    /**
     * 将 MolangContext 的 variable 存储绑定到 engine scope。
     */
    public static void bindContext(MochaEngine<?> engine, MolangContext<?> context) {
        MutableObjectBinding vars = context.getVariableStorage();
        engine.scope().set("variable", vars);
        engine.scope().set("v", vars);
    }

    /**
     * 编译 Molang 表达式为 {@link MolangExpression}。
     * 编译后的表达式可以对同类型的不同 {@link MolangContext} 实例复用。
     */
    public static MolangExpression compileExpression(MochaEngine<?> engine, String expression) {
        return engine.compile(expression, MolangExpression.class);
    }
}
