// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
/*
 * This file is part of mocha, licensed under the MIT license
 *
 * Copyright (c) 2021-2025 Unnamed Team
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.tacz.guns.client.particle.bedrock.molang.runtime;

import com.tacz.guns.client.particle.bedrock.molang.parser.ast.Expression;
import com.tacz.guns.client.particle.bedrock.molang.runtime.binding.Entity;
import com.tacz.guns.client.particle.bedrock.molang.runtime.compiled.MochaCompiledFunction;
import com.tacz.guns.client.particle.bedrock.molang.runtime.compiled.Named;
import com.tacz.guns.client.particle.bedrock.molang.util.AsmUtil;
import com.tacz.guns.client.particle.bedrock.molang.util.CaseInsensitiveStringHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.*;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

@ApiStatus.Internal
public final class MolangCompiler {
    private static final Random RANDOM = new Random();

    private final Object entity;
    private final ClassLoader classLoader;
    private final Scope scope;
    private Consumer<byte @NotNull []> postCompile;

    public MolangCompiler(final @Nullable Object entity, final @NotNull ClassLoader classLoader, final @NotNull Scope scope) {
        this.entity = entity;
        this.classLoader = requireNonNull(classLoader, "classLoader");
        this.scope = requireNonNull(scope, "scope");
    }

    public @Nullable Object entity() {
        return entity;
    }

    public void postCompile(final @Nullable Consumer<byte @NotNull []> postCompile) {
        this.postCompile = postCompile;
    }

    public <T extends MochaCompiledFunction> @NotNull T compile(final @NotNull List<Expression> expressions, final @NotNull Class<T> clazz) {
        requireNonNull(expressions, "expressions");
        requireNonNull(clazz, "clazz");

        if (clazz == MochaFunction.class && expressions.isEmpty()) {
            return clazz.cast(MochaFunction.nop());
        }

        return compileBytecode(expressions, clazz);
    }

    private <T extends MochaCompiledFunction> @NotNull T compileBytecode(final @NotNull List<Expression> expressions, final @NotNull Class<T> clazz) {

        if (!clazz.isInterface()) {
            throw new IllegalArgumentException("Target type must be an interface: " + clazz.getName());
        }

        Method implementedMethod = null;
        for (final Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.isDefault()) {
                continue;
            }
            if (implementedMethod != null) {
                throw new IllegalArgumentException("Target type must have only one method: " + clazz.getName());
            }
            implementedMethod = method;
        }

        if (implementedMethod == null) {
            throw new IllegalArgumentException("Target type must have a method to implement: " + clazz.getName());
        }

        final Map<String, Integer> argumentParameterIndexes = new CaseInsensitiveStringHashMap<>();
        int entityParameterLoadIndex = -1;
        Class<?> entityParameterType = null;

        // check method parameter types and compute load indexes
        final Parameter[] parameters = implementedMethod.getParameters();
        {
            int loadIndex = 1; // slot 0 = this
            for (int i = 0; i < parameters.length; ++i) {
                final Parameter parameter = parameters[i];

                if (parameter.isAnnotationPresent(Entity.class)) {
                    entityParameterLoadIndex = loadIndex;
                    entityParameterType = parameter.getType();
                } else {
                    final Named named = parameter.getDeclaredAnnotation(Named.class);
                    final String name;

                    if (named != null) {
                        name = named.value();
                    } else if (parameter.isNamePresent()) {
                        name = parameter.getName();
                    } else {
                        throw new IllegalArgumentException("Parameter " + parameter.getName() + " (index " + i
                                + ") must be annotated with @Named and specify a name");
                    }

                    argumentParameterIndexes.put(name, i);
                }

                final Class<?> paramType = parameter.getType();
                if (paramType.equals(double.class) || paramType.equals(long.class)) {
                    loadIndex += 2;
                } else {
                    loadIndex += 1;
                }
            }
        }

        final String interfaceInternalName = Type.getInternalName(clazz);
        final String scriptClassName = getClass().getPackage().getName().replace('.', '/') + "/MolangFunctionImpl_" + clazz.getSimpleName() + "_" + implementedMethod.getName()
                + "_" + Long.toHexString(System.currentTimeMillis()) + "_" + Integer.toHexString(RANDOM.nextInt(2024));

        final Class<?> returnType = implementedMethod.getReturnType();
        final Type returnAsmType = Type.getType(returnType);

        // Build parameter ASM types
        final Type[] paramAsmTypes = new Type[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            paramAsmTypes[i] = Type.getType(parameters[i].getType());
        }

        final String methodDescriptor = Type.getMethodDescriptor(returnAsmType, paramAsmTypes);

        // Create ClassWriter with COMPUTE_FRAMES to auto-compute stack map and max stack/locals
        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                // In mod environment, we may not be able to load all classes from the default classloader.
                // Fall back to "java/lang/Object" to avoid ClassNotFoundException.
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (Exception e) {
                    return "java/lang/Object";
                }
            }
        };

        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                scriptClassName, null, "java/lang/Object",
                new String[]{interfaceInternalName});

        // We'll collect requirements during compilation, then add fields + constructor after
        // First, generate the main method
        final MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                implementedMethod.getName(),
                methodDescriptor,
                null, null);
        mv.visitCode();

        final FunctionCompileState compileState = new FunctionCompileState(
                this, scriptClassName, mv, implementedMethod, scope,
                argumentParameterIndexes, entityParameterLoadIndex, entityParameterType);

        // compute initial max locals
        {
            int maxLocals = 1; // 1: this
            for (final Type paramType : paramAsmTypes) {
                maxLocals += paramType.getSize();
            }
            compileState.maxLocals(maxLocals);
        }

        if (expressions.isEmpty()) {
            AsmUtil.addConstZero(mv, returnAsmType);
            AsmUtil.addReturn(mv, returnAsmType);
        } else {
            final MolangCompilingVisitor compiler = new MolangCompilingVisitor(compileState);
            CompileVisitResult lastVisitResult = null;

            final ExpressionInliner inliner = new ExpressionInliner(new ExpressionInterpreter<>(null, scope), scope);

            for (final Expression expression : expressions) {
                lastVisitResult = expression.visit(inliner).visit(compiler);
            }

            if (lastVisitResult == null || !lastVisitResult.returned()) {
                if (lastVisitResult == null || !returnAsmType.equals(lastVisitResult.lastPushedType())) {
                    AsmUtil.addCast(
                            mv,
                            lastVisitResult == null ? Type.DOUBLE_TYPE : lastVisitResult.lastPushedType(),
                            returnAsmType
                    );
                }

                compiler.endVisit();
            }
        }

        mv.visitMaxs(0, 0); // COMPUTE_FRAMES will calculate actual values
        mv.visitEnd();

        final Map<String, Object> requirements = compileState.requirements();

        // Add fields for requirements
        for (final Map.Entry<String, Object> entry : requirements.entrySet()) {
            final String fieldName = entry.getKey();
            final Object fieldValue = entry.getValue();
            final String fieldDescriptor = Type.getDescriptor(fieldValue.getClass());
            cw.visitField(Opcodes.ACC_PRIVATE, fieldName, fieldDescriptor, null, null).visitEnd();
        }

        // Add constructor that takes requirements and initializes them
        {
            final StringBuilder constructorDesc = new StringBuilder("(");
            for (final Object value : requirements.values()) {
                constructorDesc.append(Type.getDescriptor(value.getClass()));
            }
            constructorDesc.append(")V");

            final MethodVisitor ctorMv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", constructorDesc.toString(), null, null);
            ctorMv.visitCode();
            ctorMv.visitVarInsn(Opcodes.ALOAD, 0); // load this
            ctorMv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); // super()

            int parameterIndex = 1;
            for (final Map.Entry<String, Object> entry : requirements.entrySet()) {
                final String fieldName = entry.getKey();
                final Object fieldValue = entry.getValue();
                final String fieldDescriptor = Type.getDescriptor(fieldValue.getClass());

                ctorMv.visitVarInsn(Opcodes.ALOAD, 0); // load this
                ctorMv.visitVarInsn(Opcodes.ALOAD, parameterIndex); // load parameter
                ctorMv.visitFieldInsn(Opcodes.PUTFIELD, scriptClassName, fieldName, fieldDescriptor);
                parameterIndex++;
            }

            ctorMv.visitInsn(Opcodes.RETURN);
            ctorMv.visitMaxs(0, 0); // COMPUTE_FRAMES
            ctorMv.visitEnd();
        }

        cw.visitEnd();

        final byte[] bytecode = cw.toByteArray();

        if (postCompile != null) {
            postCompile.accept(bytecode);
        }

        // Load the class
        final MolangClassLoader loader = new MolangClassLoader(classLoader);
        final Class<?> compiledClass = loader.define(scriptClassName.replace('/', '.'), bytecode);

        // Find the constructor with the requirements
        final Class<?>[] constructorParameterTypes = new Class[requirements.size()];
        final Object[] constructorArguments = new Object[requirements.size()];
        int i = 0;
        for (final Object requirement : requirements.values()) {
            constructorParameterTypes[i] = requirement.getClass();
            constructorArguments[i] = requirement;
            ++i;
        }

        final Constructor<?> constructor;
        try {
            constructor = compiledClass.getDeclaredConstructor(constructorParameterTypes);
        } catch (final NoSuchMethodException e) {
            throw new IllegalStateException("Couldn't find constructor with parameters " + requirements.keySet(), e);
        }
        final Object instance;
        try {
            instance = constructor.newInstance(constructorArguments);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Couldn't instantiate script class", e);
        }
        return clazz.cast(instance);
    }

    /**
     * Custom ClassLoader for loading generated Molang classes.
     */
    private static final class MolangClassLoader extends ClassLoader {
        MolangClassLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
