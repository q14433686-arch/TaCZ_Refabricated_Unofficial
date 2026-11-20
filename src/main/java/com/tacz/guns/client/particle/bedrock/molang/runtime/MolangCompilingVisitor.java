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

import com.tacz.guns.client.particle.bedrock.molang.parser.ast.*;
import com.tacz.guns.client.particle.bedrock.molang.runtime.binding.Entity;
import com.tacz.guns.client.particle.bedrock.molang.runtime.binding.JavaFieldBinding;
import com.tacz.guns.client.particle.bedrock.molang.runtime.binding.JavaFunction;
import com.tacz.guns.client.particle.bedrock.molang.runtime.binding.JavaObjectBinding;
import com.tacz.guns.client.particle.bedrock.molang.runtime.binding.QueryBinding;
import com.tacz.guns.client.particle.bedrock.molang.runtime.value.Function;
import com.tacz.guns.client.particle.bedrock.molang.runtime.value.NumberValue;
import com.tacz.guns.client.particle.bedrock.molang.runtime.value.ObjectValue;
import com.tacz.guns.client.particle.bedrock.molang.runtime.value.Value;
import com.tacz.guns.client.particle.bedrock.molang.util.AsmUtil;
import com.tacz.guns.client.particle.bedrock.molang.util.CaseInsensitiveStringHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class MolangCompilingVisitor implements ExpressionVisitor<CompileVisitResult> {

    private static final Type STRING_TYPE = Type.getType(String.class);
    private static final Type OBJECT_VALUE_TYPE = Type.getType(ObjectValue.class);
    private static final Type VALUE_TYPE = Type.getType(Value.class);
    private static final Type NUMBER_VALUE_TYPE = Type.getType(NumberValue.class);

    private final ExpressionInterpreter<?> interpreter;
    private final MethodVisitor mv;
    private final Method method;
    private final FunctionCompileState functionCompileState;
    private final Map<String, Object> requirements;
    private final Map<String, Integer> argumentParameterIndexes;
    private final Map<String, Integer> localsByName = new CaseInsensitiveStringHashMap<>();

    private final Type methodReturnType;
    private Type expectedType = null;

    MolangCompilingVisitor(final @NotNull FunctionCompileState compileState) {
        this.interpreter = new ExpressionInterpreter<>(null, compileState.scope());
        this.functionCompileState = compileState;
        this.mv = compileState.mv();
        this.method = compileState.method();
        this.requirements = compileState.requirements();
        this.argumentParameterIndexes = compileState.argumentParameterIndexes();
        this.methodReturnType = Type.getType(method.getReturnType());
        expectedType = methodReturnType;
    }

    /**
     * Helper: emit const_0 / const_1 for the current expectedType.
     */
    private void emitConst0() {
        if (expectedType == null || expectedType.equals(Type.DOUBLE_TYPE)) {
            mv.visitInsn(Opcodes.DCONST_0);
        } else if (expectedType.equals(Type.FLOAT_TYPE)) {
            mv.visitInsn(Opcodes.FCONST_0);
        } else if (expectedType.equals(Type.LONG_TYPE)) {
            mv.visitInsn(Opcodes.LCONST_0);
        } else {
            mv.visitInsn(Opcodes.ICONST_0);
        }
    }

    private void emitConst1() {
        if (expectedType == null || expectedType.equals(Type.DOUBLE_TYPE)) {
            mv.visitInsn(Opcodes.DCONST_1);
        } else if (expectedType.equals(Type.FLOAT_TYPE)) {
            mv.visitInsn(Opcodes.FCONST_1);
        } else if (expectedType.equals(Type.LONG_TYPE)) {
            mv.visitInsn(Opcodes.LCONST_1);
        } else {
            mv.visitInsn(Opcodes.ICONST_1);
        }
    }

    @Override
    public CompileVisitResult visitBinary(final @NotNull BinaryExpression expression) {
        final BinaryExpression.Op op = expression.op();

        if (op == BinaryExpression.Op.ASSIGN) {
            final Expression left = expression.left();
            if (left instanceof AccessExpression) {
                final Expression objectExpr = ((AccessExpression) left).object();
                if (objectExpr instanceof IdentifierExpression) {
                    final String name = ((IdentifierExpression) objectExpr).name();
                    final String property = ((AccessExpression) left).property();

                    if (name.equalsIgnoreCase("temp") || name.equalsIgnoreCase("t")) {
                        final Type previousExpectedType = expectedType;
                        expectedType = Type.DOUBLE_TYPE;
                        expression.right().visit(this);
                        expectedType = previousExpectedType;

                        final int localIndex = localsByName.computeIfAbsent(property, k -> {
                            int index = functionCompileState.maxLocals();
                            functionCompileState.maxLocals(index + 2);
                            return index;
                        });
                        mv.visitInsn(Opcodes.DUP2);
                        AsmUtil.addStore(mv, localIndex, Type.DOUBLE_TYPE);
                        if (previousExpectedType != null && !previousExpectedType.equals(Type.DOUBLE_TYPE)) {
                            AsmUtil.addCast(mv, Type.DOUBLE_TYPE, previousExpectedType);
                            return new CompileVisitResult(previousExpectedType);
                        }
                        return new CompileVisitResult(Type.DOUBLE_TYPE);
                    }

                    if (name.equalsIgnoreCase("variable") || name.equalsIgnoreCase("v")) {
                        final int entityLoadIndex = functionCompileState.entityParameterLoadIndex();
                        if (entityLoadIndex >= 0) {
                            final Type previousExpectedType = expectedType;
                            expectedType = Type.DOUBLE_TYPE;
                            expression.right().visit(this);
                            expectedType = previousExpectedType;

                            // dup2 the double value
                            mv.visitInsn(Opcodes.DUP2);

                            final String entityInternal = Type.getInternalName(functionCompileState.entityParameterType());

                            // NumberValue.of(double) -> NumberValue
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    NUMBER_VALUE_TYPE.getInternalName(), "of",
                                    Type.getMethodDescriptor(NUMBER_VALUE_TYPE, Type.DOUBLE_TYPE), false);

                            // Load entity parameter
                            mv.visitVarInsn(Opcodes.ALOAD, entityLoadIndex);

                            // Call context.getVariableStorage()
                            final Method getter;
                            try {
                                getter = functionCompileState.entityParameterType().getMethod("getVariableStorage");
                            } catch (NoSuchMethodException e) {
                                throw new IllegalStateException("Could not resolve getVariableStorage", e);
                            }
                            final Type getterReturnType = Type.getType(getter.getReturnType());
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, entityInternal, "getVariableStorage",
                                    Type.getMethodDescriptor(getterReturnType), false);

                            // Swap: [NumberValue, ObjectBinding] -> [ObjectBinding, NumberValue]
                            mv.visitInsn(Opcodes.SWAP);

                            // Load property name
                            mv.visitLdcInsn(property);

                            // Swap: [ObjectBinding, NumberValue, String] -> [ObjectBinding, String, NumberValue]
                            mv.visitInsn(Opcodes.SWAP);

                            // Call objectValue.set(String, Value) -> boolean
                            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                                    OBJECT_VALUE_TYPE.getInternalName(), "set",
                                    Type.getMethodDescriptor(Type.BOOLEAN_TYPE, STRING_TYPE, VALUE_TYPE), true);

                            // Pop the boolean return value
                            mv.visitInsn(Opcodes.POP);

                            expectedType = previousExpectedType;
                            return finishDoubleResult();
                        }
                    }
                }
            }
        }

        final Type currentExpectedType = expectedType;

        switch (op) {
            case AND: {
                expectedType = Type.BOOLEAN_TYPE;
                expression.left().visit(this);
                Label falseLabel = new Label();
                mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);
                expression.right().visit(this);
                Label endLabel = new Label();
                mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);
                expectedType = currentExpectedType;
                emitConst1();
                mv.visitJumpInsn(Opcodes.GOTO, endLabel);
                mv.visitLabel(falseLabel);
                emitConst0();
                mv.visitLabel(endLabel);
                return new CompileVisitResult(currentExpectedType);
            }
            case OR: {
                expectedType = Type.BOOLEAN_TYPE;
                expression.left().visit(this);
                Label trueLabel = new Label();
                mv.visitJumpInsn(Opcodes.IFNE, trueLabel);
                expression.right().visit(this);
                Label endLabel = new Label();
                mv.visitJumpInsn(Opcodes.IFEQ, endLabel);
                mv.visitLabel(trueLabel);
                expectedType = currentExpectedType;
                emitConst1();
                Label realEnd = new Label();
                mv.visitJumpInsn(Opcodes.GOTO, realEnd);
                mv.visitLabel(endLabel);
                emitConst0();
                mv.visitLabel(realEnd);
                return new CompileVisitResult(currentExpectedType);
            }
            case EQ:
            case NEQ:
            case LT:
            case LTE:
            case GT:
            case GTE: {
                expectedType = Type.DOUBLE_TYPE;
                expression.left().visit(this);
                expression.right().visit(this);
                expectedType = currentExpectedType;

                mv.visitInsn(Opcodes.DCMPL);

                Label trueLabel = new Label();
                Label endLabel = new Label();

                switch (op) {
                    case LT:  mv.visitJumpInsn(Opcodes.IFLT, trueLabel); break;
                    case LTE: mv.visitJumpInsn(Opcodes.IFLE, trueLabel); break;
                    case GT:  mv.visitJumpInsn(Opcodes.IFGT, trueLabel); break;
                    case GTE: mv.visitJumpInsn(Opcodes.IFGE, trueLabel); break;
                    case EQ:  mv.visitJumpInsn(Opcodes.IFEQ, trueLabel); break;
                    case NEQ: mv.visitJumpInsn(Opcodes.IFNE, trueLabel); break;
                }

                emitConst0();
                mv.visitJumpInsn(Opcodes.GOTO, endLabel);
                mv.visitLabel(trueLabel);
                emitConst1();
                mv.visitLabel(endLabel);
                return new CompileVisitResult(expectedType == null ? Type.BOOLEAN_TYPE : expectedType);
            }
            case ADD: {
                expectedType = Type.DOUBLE_TYPE;
                expression.left().visit(this);
                expression.right().visit(this);
                expectedType = currentExpectedType;
                mv.visitInsn(Opcodes.DADD);
                return new CompileVisitResult(Type.DOUBLE_TYPE);
            }
            case SUB: {
                expectedType = Type.DOUBLE_TYPE;
                expression.left().visit(this);
                expression.right().visit(this);
                expectedType = currentExpectedType;
                mv.visitInsn(Opcodes.DSUB);
                return new CompileVisitResult(Type.DOUBLE_TYPE);
            }
            case MUL: {
                expectedType = Type.DOUBLE_TYPE;
                expression.left().visit(this);
                expression.right().visit(this);
                expectedType = currentExpectedType;
                mv.visitInsn(Opcodes.DMUL);
                return new CompileVisitResult(Type.DOUBLE_TYPE);
            }
            case DIV: {
                expectedType = Type.DOUBLE_TYPE;
                expression.left().visit(this);
                expression.right().visit(this);
                expectedType = currentExpectedType;
                mv.visitInsn(Opcodes.DDIV);
                return new CompileVisitResult(Type.DOUBLE_TYPE);
            }
            case NULL_COALESCE: {
                expectedType = Type.DOUBLE_TYPE;
                expression.left().visit(this);
                expectedType = currentExpectedType;

                mv.visitInsn(Opcodes.DUP2);
                mv.visitInsn(Opcodes.DCONST_0);
                mv.visitInsn(Opcodes.DCMPL);

                Label falseLabel = new Label();
                Label endLabel = new Label();
                mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);

                finishDoubleResult();
                mv.visitJumpInsn(Opcodes.GOTO, endLabel);

                mv.visitLabel(falseLabel);
                mv.visitInsn(Opcodes.POP2);
                expectedType = Type.DOUBLE_TYPE;
                expression.right().visit(this);
                expectedType = currentExpectedType;
                finishDoubleResult();

                mv.visitLabel(endLabel);
                return new CompileVisitResult(currentExpectedType != null ? currentExpectedType : Type.DOUBLE_TYPE);
            }
            case CONDITIONAL: {
                expectedType = Type.BOOLEAN_TYPE;
                expression.left().visit(this);
                expectedType = currentExpectedType;

                Label falseLabel = new Label();
                Label endLabel = new Label();
                mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);

                expectedType = Type.DOUBLE_TYPE;
                expression.right().visit(this);
                expectedType = currentExpectedType;
                finishDoubleResult();
                mv.visitJumpInsn(Opcodes.GOTO, endLabel);

                mv.visitLabel(falseLabel);
                mv.visitInsn(Opcodes.DCONST_0);
                finishDoubleResult();

                mv.visitLabel(endLabel);
                return new CompileVisitResult(currentExpectedType != null ? currentExpectedType : Type.DOUBLE_TYPE);
            }
            case ARROW:
                break;
        }
        return null;
    }

    public void endVisit() {
        AsmUtil.addReturn(mv, methodReturnType);
    }

    @Override
    public @NotNull CompileVisitResult visitDouble(final @NotNull DoubleExpression expression) {
        final double value = expression.value();
        if (expectedType != null && expectedType.equals(Type.VOID_TYPE)) {
            return new CompileVisitResult(Type.VOID_TYPE);
        } else if (expectedType == null || expectedType.equals(Type.DOUBLE_TYPE)) {
            if (value == 1.0D) {
                mv.visitInsn(Opcodes.DCONST_1);
            } else if (value == 0.0D) {
                mv.visitInsn(Opcodes.DCONST_0);
            } else {
                mv.visitLdcInsn(value);
            }
            return new CompileVisitResult(Type.DOUBLE_TYPE);
        } else if (expectedType.equals(Type.BOOLEAN_TYPE)) {
            if (value != 0.0D) {
                mv.visitInsn(Opcodes.ICONST_1);
            } else {
                mv.visitInsn(Opcodes.ICONST_0);
            }
            return new CompileVisitResult(Type.BOOLEAN_TYPE);
        } else if (expectedType.equals(Type.INT_TYPE)) {
            mv.visitLdcInsn((int) value);
            return new CompileVisitResult(Type.INT_TYPE);
        } else if (expectedType.equals(Type.LONG_TYPE)) {
            mv.visitLdcInsn((long) value);
            return new CompileVisitResult(Type.LONG_TYPE);
        } else {
            System.err.println("[warning] expected type " + expectedType + " has no possible cast from double (" + expression + ")");
            AsmUtil.addConstZero(mv, expectedType);
            return new CompileVisitResult(expectedType);
        }
    }

    @Override
    public @NotNull CompileVisitResult visitString(final @NotNull StringExpression expression) {
        if (expectedType != null && expectedType.equals(Type.VOID_TYPE)) {
            return new CompileVisitResult(Type.VOID_TYPE);
        } else if (expectedType == null || expectedType.equals(STRING_TYPE)) {
            mv.visitLdcInsn(expression.value());
            return new CompileVisitResult(STRING_TYPE);
        } else {
            AsmUtil.addConstZero(mv, expectedType);
            return new CompileVisitResult(expectedType);
        }
    }

    @Override
    public @NotNull CompileVisitResult visitUnary(final @NotNull UnaryExpression expression) {
        switch (expression.op()) {
            case RETURN: {
                expectedType = methodReturnType;
                expression.expression().visit(this);
                expectedType = null;
                AsmUtil.addReturn(mv, methodReturnType);
                return new CompileVisitResult(methodReturnType, true);
            }
            case LOGICAL_NEGATION: {
                if (expectedType != null && expectedType.equals(Type.VOID_TYPE)) {
                    expression.expression().visit(this);
                    return new CompileVisitResult(Type.VOID_TYPE);
                }

                final Type currentExpectedType = expectedType;

                if (currentExpectedType != null && currentExpectedType.getSort() == Type.OBJECT) {
                    expectedType = Type.VOID_TYPE;
                    expression.expression().visit(this);
                    expectedType = currentExpectedType;
                    AsmUtil.addConstZero(mv, currentExpectedType);
                    return new CompileVisitResult(currentExpectedType);
                }

                expectedType = Type.BOOLEAN_TYPE;
                expression.expression().visit(this);
                expectedType = currentExpectedType;

                if (currentExpectedType != null && currentExpectedType.equals(Type.BOOLEAN_TYPE)) {
                    // For boolean, just leave the IFNE for branching
                    Label falseLabel = new Label();
                    Label endLabel = new Label();
                    mv.visitJumpInsn(Opcodes.IFNE, falseLabel);
                    mv.visitInsn(Opcodes.ICONST_1);
                    mv.visitJumpInsn(Opcodes.GOTO, endLabel);
                    mv.visitLabel(falseLabel);
                    mv.visitInsn(Opcodes.ICONST_0);
                    mv.visitLabel(endLabel);
                    return new CompileVisitResult(Type.BOOLEAN_TYPE);
                }

                Label falseLabel = new Label();
                Label endLabel = new Label();
                mv.visitJumpInsn(Opcodes.IFNE, falseLabel);
                // was false (0), so negation is true (1)
                emitConst1();
                mv.visitJumpInsn(Opcodes.GOTO, endLabel);
                mv.visitLabel(falseLabel);
                // was true (nonzero), so negation is false (0)
                emitConst0();
                mv.visitLabel(endLabel);
                return new CompileVisitResult(currentExpectedType);
            }
            case ARITHMETICAL_NEGATION: {
                final CompileVisitResult result = expression.expression().visit(this);
                if (result.is(Type.DOUBLE_TYPE)) {
                    mv.visitInsn(Opcodes.DNEG);
                } else if (result.is(Type.LONG_TYPE)) {
                    mv.visitInsn(Opcodes.LNEG);
                } else if (result.is(Type.FLOAT_TYPE)) {
                    mv.visitInsn(Opcodes.FNEG);
                } else if (result.is(Type.INT_TYPE)) {
                    mv.visitInsn(Opcodes.INEG);
                } else if (result.is(Type.BOOLEAN_TYPE)) {
                    mv.visitInsn(Opcodes.ICONST_1);
                    mv.visitInsn(Opcodes.IXOR);
                } else {
                    throw new IllegalStateException("Unsupported type for negation: " + result);
                }
                break;
            }
            default:
                throw new UnsupportedOperationException("Unsupported unary operator: " + expression.op());
        }
        return null;
    }

    @Override
    public @NotNull CompileVisitResult visitTernaryConditional(final @NotNull TernaryConditionalExpression expression) {
        final Expression trueExpr = expression.trueExpression();
        final Expression falseExpr = expression.falseExpression();

        final Type currentExpectedType = expectedType;
        expectedType = Type.BOOLEAN_TYPE;
        final CompileVisitResult conditionRes = expression.condition().visit(this);
        expectedType = currentExpectedType;

        if (conditionRes != null && conditionRes.lastPushedType() != null
                && !conditionRes.is(Type.BOOLEAN_TYPE) && !conditionRes.is(Type.INT_TYPE)) {
            AsmUtil.addConstZero(mv, conditionRes.lastPushedType());
            if (conditionRes.is(Type.DOUBLE_TYPE)) {
                mv.visitInsn(Opcodes.DCMPL);
            } else if (conditionRes.is(Type.FLOAT_TYPE)) {
                mv.visitInsn(Opcodes.FCMPL);
            } else if (conditionRes.is(Type.LONG_TYPE)) {
                mv.visitInsn(Opcodes.LCMP);
            } else {
                throw new IllegalStateException("Unsupported type for comparison: " + conditionRes);
            }
        }

        Label falseLabel = new Label();
        Label endLabel = new Label();
        mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);
        trueExpr.visit(this);
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        mv.visitLabel(falseLabel);
        falseExpr.visit(this);
        mv.visitLabel(endLabel);
        return new CompileVisitResult(currentExpectedType);
    }

    @Override
    public CompileVisitResult visitIdentifier(final @NotNull IdentifierExpression expression) {
        final String name = expression.name();
        final Integer paramIndex = argumentParameterIndexes.get(name);
        if (paramIndex == null) {
            throw new IllegalStateException("Unknown variable: " + name);
        }

        final Parameter[] parameters = method.getParameters();
        final Parameter parameter = parameters[paramIndex];
        int loadIndex = 1;
        for (int i = 0; i < paramIndex; i++) {
            final Class<?> paramType = parameters[i].getType();
            if (paramType.equals(double.class) || paramType.equals(long.class)) {
                loadIndex += 2;
            } else {
                loadIndex += 1;
            }
        }

        final Type parameterType = Type.getType(parameter.getType());
        AsmUtil.addLoad(mv, loadIndex, parameterType);

        if (expectedType == null) {
            return new CompileVisitResult(parameterType);
        }

        AsmUtil.addCast(mv, parameterType, expectedType);
        return new CompileVisitResult(expectedType);
    }

    private @NotNull CompileVisitResult finishDoubleResult() {
        if (expectedType != null && expectedType.equals(Type.VOID_TYPE)) {
            mv.visitInsn(Opcodes.POP2);
            return new CompileVisitResult(Type.VOID_TYPE);
        }
        if (expectedType != null && !expectedType.equals(Type.DOUBLE_TYPE)) {
            AsmUtil.addCast(mv, Type.DOUBLE_TYPE, expectedType);
            return new CompileVisitResult(expectedType);
        }
        return new CompileVisitResult(Type.DOUBLE_TYPE);
    }

    @Override
    public CompileVisitResult visitAccess(final @NotNull AccessExpression expression) {
        final Expression objectExpr = expression.object();
        final String property = expression.property();

        if (objectExpr instanceof IdentifierExpression) {
            final String name = ((IdentifierExpression) objectExpr).name();
            if (name.equalsIgnoreCase("temp") || name.equalsIgnoreCase("t")) {
                final Integer localIndex = localsByName.get(property);
                if (localIndex == null) {
                    mv.visitInsn(Opcodes.DCONST_0);
                } else {
                    AsmUtil.addLoad(mv, localIndex, Type.DOUBLE_TYPE);
                }
                return finishDoubleResult();
            }

            final int entityLoadIndex = functionCompileState.entityParameterLoadIndex();
            if (entityLoadIndex >= 0) {
                if (name.equalsIgnoreCase("variable") || name.equalsIgnoreCase("v")) {
                    final String entityInternal = Type.getInternalName(functionCompileState.entityParameterType());
                    final Method getter;
                    try {
                        getter = functionCompileState.entityParameterType().getMethod("getVariableStorage");
                    } catch (NoSuchMethodException e) {
                        throw new IllegalStateException("Could not resolve getVariableStorage", e);
                    }
                    final Type getterReturnType = Type.getType(getter.getReturnType());

                    mv.visitVarInsn(Opcodes.ALOAD, entityLoadIndex);
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, entityInternal, "getVariableStorage",
                            Type.getMethodDescriptor(getterReturnType), false);
                    mv.visitLdcInsn(property);
                    mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                            OBJECT_VALUE_TYPE.getInternalName(), "get",
                            Type.getMethodDescriptor(VALUE_TYPE, STRING_TYPE), true);
                    mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                            VALUE_TYPE.getInternalName(), "getAsNumber",
                            Type.getMethodDescriptor(Type.DOUBLE_TYPE), true);
                    return finishDoubleResult();
                }

                // 对于任意非 variable/temp 的命名空间，尝试 @QueryBinding
                final Method queryMethod = findQueryMethod(property, name);
                if (queryMethod != null) {
                    final Class<?> ownerClass = queryMethod.getDeclaringClass();
                    mv.visitVarInsn(Opcodes.ALOAD, entityLoadIndex);
                    if (!ownerClass.isAssignableFrom(functionCompileState.entityParameterType())) {
                        mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(ownerClass));
                    }
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                            Type.getInternalName(ownerClass),
                            queryMethod.getName(),
                            Type.getMethodDescriptor(Type.DOUBLE_TYPE), false);
                    return finishDoubleResult();
                }

                // @QueryBinding 未命中 → 回退到 scope 查找
            }
        }

        final Scope scope = functionCompileState.scope();
        final Value objectValue = objectExpr.visit(new ExpressionVisitor<Value>() {
            @Override
            public @NotNull Value visitIdentifier(final @NotNull IdentifierExpression expression) {
                return scope.get(expression.name());
            }

            @Override
            public @NotNull Value visitAccess(final @NotNull AccessExpression expression) {
                final Value object = expression.object().visit(this);
                if (object instanceof ObjectValue) {
                    return ((ObjectValue) object).get(expression.property());
                } else {
                    return NumberValue.zero();
                }
            }

            @Override
            public @NotNull Value visit(final @NotNull Expression expression) {
                return NumberValue.zero();
            }
        });

        if (objectValue instanceof ObjectValue) {
            final ObjectValue actualObjectValue = (ObjectValue) objectValue;

            // JavaObjectBinding: fields and bindings
            if (actualObjectValue instanceof JavaObjectBinding) {
                final JavaFieldBinding javaFieldBinding = ((JavaObjectBinding) actualObjectValue).getField(property);
                if (javaFieldBinding == null) {
                    mv.visitInsn(Opcodes.DCONST_0);
                } else if (javaFieldBinding.constant()) {
                    mv.visitLdcInsn(javaFieldBinding.get().getAsNumber());
                } else {
                    final Field field = javaFieldBinding.field();
                    if (Modifier.isStatic(field.getModifiers())) {
                        mv.visitFieldInsn(Opcodes.GETSTATIC,
                                Type.getInternalName(field.getDeclaringClass()),
                                field.getName(),
                                Type.getDescriptor(field.getType()));
                    } else {
                        final Object holder = javaFieldBinding.holder();
                        if (holder != null) {
                            final String fieldName = "holder_" + Integer.toHexString(System.identityHashCode(holder));
                            requirements.put(fieldName, holder);
                            final String holderInternal = Type.getInternalName(holder.getClass());
                            final String holderDesc = Type.getDescriptor(holder.getClass());
                            final Type fieldType = Type.getType(field.getType());

                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                            mv.visitFieldInsn(Opcodes.GETFIELD, functionCompileState.className(), fieldName, holderDesc);
                            mv.visitFieldInsn(Opcodes.GETFIELD,
                                    Type.getInternalName(field.getDeclaringClass()),
                                    field.getName(),
                                    Type.getDescriptor(field.getType()));
                            if (expectedType != null && !fieldType.equals(expectedType)) {
                                AsmUtil.addCast(mv, fieldType, expectedType);
                            }
                        } else {
                            mv.visitInsn(Opcodes.DCONST_0);
                        }
                    }
                }
                return new CompileVisitResult(expectedType != null ? expectedType : Type.DOUBLE_TYPE);
            }

            // Generic ObjectValue: dynamic property access at runtime
            {
                final String reqFieldName = "objval_" + Integer.toHexString(System.identityHashCode(actualObjectValue));
                requirements.put(reqFieldName, actualObjectValue);
                final String objValDesc = Type.getDescriptor(actualObjectValue.getClass());

                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitFieldInsn(Opcodes.GETFIELD, functionCompileState.className(), reqFieldName, objValDesc);
                mv.visitLdcInsn(property);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                        OBJECT_VALUE_TYPE.getInternalName(), "get",
                        Type.getMethodDescriptor(VALUE_TYPE, STRING_TYPE), true);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                        VALUE_TYPE.getInternalName(), "getAsNumber",
                        Type.getMethodDescriptor(Type.DOUBLE_TYPE), true);
                return finishDoubleResult();
            }
        }

        // namespace/property not found in scope either → 0
        mv.visitInsn(Opcodes.DCONST_0);
        return finishDoubleResult();
    }

    @Override
    public CompileVisitResult visitCall(final @NotNull CallExpression expression) {
        final Scope scope = functionCompileState.scope();
        final Expression functionExpr = expression.function();

        final Value functionValue = functionExpr.visit(new ExpressionVisitor<Value>() {
            @Override
            public @NotNull Value visitIdentifier(final @NotNull IdentifierExpression expression) {
                return scope.get(expression.name());
            }

            @Override
            public @NotNull Value visitAccess(final @NotNull AccessExpression expression) {
                final Value object = expression.object().visit(this);
                if (object instanceof ObjectValue) {
                    return ((ObjectValue) object).get(expression.property());
                } else {
                    return NumberValue.zero();
                }
            }

            @Override
            public @NotNull Value visit(final @NotNull Expression expression) {
                return NumberValue.zero();
            }
        });

        if (!(functionValue instanceof Function<?>)) {
            // scope 中没找到 → 尝试 @QueryBinding 带参方法
            if (functionExpr instanceof AccessExpression) {
                final AccessExpression accessExpr = (AccessExpression) functionExpr;
                if (accessExpr.object() instanceof IdentifierExpression) {
                    final String namespace = ((IdentifierExpression) accessExpr.object()).name();
                    final Method queryMethod = findQueryCallMethod(accessExpr.property(), namespace);
                    if (queryMethod != null) {
                        return compileQueryBindingCall(queryMethod, expression.arguments());
                    }
                }
            }
            mv.visitInsn(Opcodes.DCONST_0);
            return finishDoubleResult();
        }

        final Type callExpectedType = expectedType;
        final Function<?> function = (Function<?>) functionValue;

        if (function instanceof JavaFunction<?>) {
            final JavaFunction<?> javaFunction = (JavaFunction<?>) function;
            final Method nativeMethod = javaFunction.method();
            final Parameter[] parameters = nativeMethod.getParameters();
            final List<Expression> arguments = expression.arguments();

            final Type[] ctParameters = new Type[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                ctParameters[i] = Type.getType(parameters[i].getType());
            }

            final Object object = javaFunction.object();
            final boolean isStatic = Modifier.isStatic(nativeMethod.getModifiers());

            if (!isStatic) {
                if (object == null) {
                    throw new IllegalStateException("Non-static Java function has no receiver: " + nativeMethod);
                }

                final String fieldName = object.getClass().getSimpleName().toLowerCase() + Integer.toHexString(object.hashCode());
                requirements.put(fieldName, object);

                final String requirementDesc = Type.getDescriptor(object.getClass());
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitFieldInsn(Opcodes.GETFIELD, functionCompileState.className(), fieldName, requirementDesc);
            }

            // load arguments
            final Iterator<Expression> it = arguments.iterator();
            for (int i = 0; i < parameters.length; i++) {
                final Parameter parameter = parameters[i];

                if (parameter.isAnnotationPresent(Entity.class)) {
                    int entityLoadIndex = functionCompileState.entityParameterLoadIndex();
                    if (entityLoadIndex >= 0) {
                        mv.visitVarInsn(Opcodes.ALOAD, entityLoadIndex);
                        Class<?> entityParamType = functionCompileState.entityParameterType();
                        if (entityParamType != null && !parameter.getType().isAssignableFrom(entityParamType)) {
                            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(parameter.getType()));
                        }
                    } else {
                        Object entityObj = functionCompileState.compiler().entity();
                        if (entityObj == null || !parameter.getType().isInstance(entityObj)) {
                            AsmUtil.addConstZero(mv, ctParameters[i]);
                        } else {
                            requirements.put("__entity__", entityObj);
                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                            mv.visitFieldInsn(Opcodes.GETFIELD,
                                    functionCompileState.className(),
                                    "__entity__",
                                    ctParameters[i].getDescriptor());
                        }
                    }
                    continue;
                }

                if (!it.hasNext()) {
                    AsmUtil.addConstZero(mv, ctParameters[i]);
                    continue;
                }

                expectedType = ctParameters[i];
                it.next().visit(this);
                expectedType = callExpectedType;
            }

            expectedType = callExpectedType;

            final String nativeMethodOwner = Type.getInternalName(nativeMethod.getDeclaringClass());
            final Type ctReturnType = Type.getType(nativeMethod.getReturnType());
            final String nativeMethodDesc = Type.getMethodDescriptor(ctReturnType, ctParameters);

            if (isStatic) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, nativeMethodOwner,
                        nativeMethod.getName(), nativeMethodDesc, false);
            } else {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                        nativeMethodOwner, nativeMethod.getName(), nativeMethodDesc, false);
            }

            if (nativeMethod.getReturnType() == void.class) {
                if (callExpectedType != null && !callExpectedType.equals(Type.VOID_TYPE)) {
                    AsmUtil.addConstZero(mv, callExpectedType);
                    return new CompileVisitResult(callExpectedType);
                }
                return new CompileVisitResult(Type.VOID_TYPE);
            } else if (callExpectedType != null && !ctReturnType.equals(callExpectedType)) {
                AsmUtil.addCast(mv, ctReturnType, callExpectedType);
                return new CompileVisitResult(callExpectedType);
            }
            return new CompileVisitResult(ctReturnType);
        } else {
            throw new UnsupportedOperationException("Not supporting non-Java functions yet");
        }
    }

    @Override
    public CompileVisitResult visit(final @NotNull Expression expression) {
        throw new UnsupportedOperationException("Unsupported expression type: " + expression);
    }

    /**
     * 编译对 @QueryBinding 带参方法的调用。
     */
    private CompileVisitResult compileQueryBindingCall(final Method method, final List<Expression> arguments) {
        final Parameter[] parameters = method.getParameters();
        final int entityLoadIndex = functionCompileState.entityParameterLoadIndex();
        final Class<?> entityParamType = functionCompileState.entityParameterType();
        final Class<?> ownerClass = method.getDeclaringClass();

        // 加载 entity（含 CHECKCAST 如果需要）
        if (entityLoadIndex >= 0) {
            mv.visitVarInsn(Opcodes.ALOAD, entityLoadIndex);
            if (entityParamType != null && !ownerClass.isAssignableFrom(entityParamType)) {
                mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(ownerClass));
            }
        } else {
            // 无 @Entity 参数 → 从 requirements 加载
            final Object entityObj = functionCompileState.compiler().entity();
            if (entityObj != null && ownerClass.isInstance(entityObj)) {
                requirements.put("__entity__", entityObj);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitFieldInsn(Opcodes.GETFIELD,
                        functionCompileState.className(),
                        "__entity__",
                        Type.getDescriptor(entityObj.getClass()));
            } else {
                mv.visitInsn(Opcodes.ACONST_NULL);
            }
        }

        // 加载各参数
        final Iterator<Expression> it = arguments.iterator();
        for (int i = 0; i < parameters.length; i++) {
            final Parameter param = parameters[i];
            final Type paramType = Type.getType(param.getType());

            if (!it.hasNext()) {
                AsmUtil.addConstZero(mv, paramType);
                continue;
            }

            final Expression arg = it.next();
            final Type prevExpected = expectedType;
            if (paramType.getSort() <= Type.DOUBLE) {
                expectedType = Type.DOUBLE_TYPE;
            } else if (paramType.equals(STRING_TYPE)) {
                expectedType = STRING_TYPE;
            } else {
                expectedType = null;
            }
            final CompileVisitResult argResult = arg.visit(this);
            expectedType = prevExpected;

            if (argResult.lastPushedType() != null && !argResult.lastPushedType().equals(paramType)) {
                AsmUtil.addCast(mv, argResult.lastPushedType(), paramType);
            }
        }

        final Type returnType = Type.getType(method.getReturnType());
        final Type[] paramTypesList = new Type[parameters.length];
        for (int j = 0; j < parameters.length; j++) {
            paramTypesList[j] = Type.getType(parameters[j].getType());
        }
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                Type.getInternalName(ownerClass),
                method.getName(),
                Type.getMethodDescriptor(returnType, paramTypesList),
                false);

        final Type currentExpected = expectedType;
        if (currentExpected != null && !returnType.equals(currentExpected)) {
            AsmUtil.addCast(mv, returnType, currentExpected);
        }
        return new CompileVisitResult(currentExpected != null ? currentExpected : returnType);
    }

    /**
     * 在 entity 类型及其父类中查找带有指定命名空间和属性名的 {@link QueryBinding} 方法。
     */
    @Nullable
    private Method findQueryMethod(final String property, final String namespace) {
        final Class<?> declaredType = functionCompileState.entityParameterType();
        Method m = findQueryMethodOnType(declaredType, property, namespace);
        if (m != null) return m;
        final Object entity = functionCompileState.compiler().entity();
        if (entity != null) {
            m = findQueryMethodOnType(entity.getClass(), property, namespace);
        }
        return m;
    }

    @Nullable
    private static Method findQueryMethodOnType(final Class<?> type, final String property, final String namespace) {
        if (type == null) {
            return null;
        }
        for (final Method m : type.getMethods()) {
            final QueryBinding qb = m.getAnnotation(QueryBinding.class);
            if (qb != null && qb.value().equals(property)
                    && qb.namespace().equalsIgnoreCase(namespace)
                    && m.getReturnType() == double.class
                    && m.getParameterCount() == 0
                    && !Modifier.isStatic(m.getModifiers())) {
                return m;
            }
        }
        return null;
    }

    /**
     * 与 {@link #findQueryMethod} 相同，但不限制参数数量——用于函数调用路径。
     */
    @Nullable
    private Method findQueryCallMethod(final String property, final String namespace) {
        final Class<?> declaredType = functionCompileState.entityParameterType();
        Method m = findQueryCallMethodOnType(declaredType, property, namespace);
        if (m != null) return m;
        final Object entity = functionCompileState.compiler().entity();
        if (entity != null) {
            m = findQueryCallMethodOnType(entity.getClass(), property, namespace);
        }
        return m;
    }

    @Nullable
    private static Method findQueryCallMethodOnType(final Class<?> type, final String property, final String namespace) {
        if (type == null) {
            return null;
        }
        for (final Method m : type.getMethods()) {
            final QueryBinding qb = m.getAnnotation(QueryBinding.class);
            if (qb != null && qb.value().equals(property)
                    && qb.namespace().equalsIgnoreCase(namespace)
                    && m.getReturnType() == double.class
                    && !Modifier.isStatic(m.getModifiers())) {
                return m;
            }
        }
        return null;
    }
}
