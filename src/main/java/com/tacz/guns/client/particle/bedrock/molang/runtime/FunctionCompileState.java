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

import com.tacz.guns.client.particle.bedrock.molang.util.CaseInsensitiveStringHashMap;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.MethodVisitor;

import java.lang.reflect.Method;
import java.util.Map;

import static java.util.Objects.requireNonNull;

final class FunctionCompileState {
    private final MolangCompiler compiler;

    private final String className; // internal name (slash-separated)
    private final MethodVisitor mv;
    private final Method method;

    private final Map<String, Object> requirements = new CaseInsensitiveStringHashMap<>();
    private final Scope scope;
    private final Map<String, Integer> argumentParameterIndexes;
    private final int entityParameterLoadIndex;
    private final Class<?> entityParameterType;
    private int maxLocals = 0;

    FunctionCompileState(
            MolangCompiler compiler,
            String className,
            MethodVisitor mv,
            Method method,
            Scope scope,
            Map<String, Integer> argumentParameterIndexes,
            int entityParameterLoadIndex,
            Class<?> entityParameterType
    ) {
        this.compiler = requireNonNull(compiler, "compiler");
        this.className = requireNonNull(className, "className");
        this.mv = requireNonNull(mv, "mv");
        this.method = requireNonNull(method, "method");
        this.scope = requireNonNull(scope, "scope");
        this.argumentParameterIndexes = requireNonNull(argumentParameterIndexes, "argumentParameterIndexes");
        this.entityParameterLoadIndex = entityParameterLoadIndex;
        this.entityParameterType = entityParameterType;
    }

    public @NotNull MolangCompiler compiler() {
        return compiler;
    }

    public @NotNull String className() {
        return className;
    }

    public @NotNull MethodVisitor mv() {
        return mv;
    }

    public @NotNull Method method() {
        return method;
    }

    public @NotNull Map<String, Object> requirements() {
        return requirements;
    }

    public @NotNull Scope scope() {
        return scope;
    }

    public @NotNull Map<String, Integer> argumentParameterIndexes() {
        return argumentParameterIndexes;
    }

    public int maxLocals() {
        return maxLocals;
    }

    public void maxLocals(int maxLocals) {
        this.maxLocals = maxLocals;
    }

    public int entityParameterLoadIndex() {
        return entityParameterLoadIndex;
    }

    public Class<?> entityParameterType() {
        return entityParameterType;
    }
}
