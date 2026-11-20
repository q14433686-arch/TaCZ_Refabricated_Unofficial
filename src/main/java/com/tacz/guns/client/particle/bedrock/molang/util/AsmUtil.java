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
package com.tacz.guns.client.particle.bedrock.molang.util;

import com.tacz.guns.client.particle.bedrock.molang.runtime.TypeCastException;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import static java.util.Objects.requireNonNull;

public final class AsmUtil {

    private AsmUtil() {}

    /**
     * Returns the internal name (slash-separated) for a Java class.
     */
    public static @NotNull String internalName(final @NotNull Class<?> clazz) {
        return Type.getInternalName(clazz);
    }

    /**
     * Returns the type descriptor for a Java class.
     */
    public static @NotNull String descriptor(final @NotNull Class<?> clazz) {
        return Type.getDescriptor(clazz);
    }

    /**
     * Emits a type cast from {@code from} to {@code to} on the given MethodVisitor.
     */
    public static void addCast(final @NotNull MethodVisitor mv, final @NotNull Type from, final @NotNull Type to) {
        requireNonNull(mv, "mv");
        requireNonNull(from, "from");
        requireNonNull(to, "to");

        if (from.equals(to)) {
            return;
        }

        if (from.equals(Type.VOID_TYPE) || to.equals(Type.VOID_TYPE)) {
            throw new IllegalArgumentException("Cannot cast to or from void");
        }

        int fromSort = from.getSort();
        int toSort = to.getSort();

        if (fromSort <= Type.DOUBLE && toSort <= Type.DOUBLE) {
            // primitive to primitive
            addPrimitiveCast(mv, from, to);
        } else if (fromSort <= Type.DOUBLE && toSort == Type.OBJECT) {
            // primitive to wrapper (boxing)
            addBoxing(mv, from);
        } else if (fromSort == Type.OBJECT && toSort <= Type.DOUBLE) {
            // wrapper to primitive (unboxing)
            addUnboxing(mv, to);
        } else {
            // object to object
            mv.visitTypeInsn(Opcodes.CHECKCAST, to.getInternalName());
        }
    }

    private static void addPrimitiveCast(final @NotNull MethodVisitor mv, final @NotNull Type from, final @NotNull Type to) {
        int f = from.getSort();
        int t = to.getSort();

        // Normalize small int types to INT for casting purposes
        if (f == Type.BOOLEAN || f == Type.CHAR || f == Type.BYTE || f == Type.SHORT) {
            f = Type.INT;
        }

        if (f == t) return;

        switch (f) {
            case Type.INT:
                addCastIntTo(mv, to);
                break;
            case Type.LONG:
                addCastLongTo(mv, to);
                break;
            case Type.FLOAT:
                addCastFloatTo(mv, to);
                break;
            case Type.DOUBLE:
                addCastDoubleTo(mv, to);
                break;
            default:
                throw new TypeCastException("Cannot cast unknown primitive type: " + from);
        }
    }

    public static void addCastIntTo(final @NotNull MethodVisitor mv, final @NotNull Type to) {
        int t = to.getSort();
        switch (t) {
            case Type.INT:
                break;
            case Type.BYTE:
                mv.visitInsn(Opcodes.I2B);
                break;
            case Type.BOOLEAN:
                // int to boolean: 0 → 0, nonzero → 1
                org.objectweb.asm.Label falseLabel = new org.objectweb.asm.Label();
                org.objectweb.asm.Label endLabel = new org.objectweb.asm.Label();
                mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);
                mv.visitInsn(Opcodes.ICONST_1);
                mv.visitJumpInsn(Opcodes.GOTO, endLabel);
                mv.visitLabel(falseLabel);
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitLabel(endLabel);
                break;
            case Type.SHORT:
                mv.visitInsn(Opcodes.I2S);
                break;
            case Type.CHAR:
                mv.visitInsn(Opcodes.I2C);
                break;
            case Type.LONG:
                mv.visitInsn(Opcodes.I2L);
                break;
            case Type.FLOAT:
                mv.visitInsn(Opcodes.I2F);
                break;
            case Type.DOUBLE:
                mv.visitInsn(Opcodes.I2D);
                break;
            default:
                throw new TypeCastException("Cannot cast int to unknown type: " + to);
        }
    }

    public static void addCastDoubleTo(final @NotNull MethodVisitor mv, final @NotNull Type to) {
        int t = to.getSort();
        switch (t) {
            case Type.DOUBLE:
                break;
            case Type.INT:
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.SHORT:
            case Type.CHAR:
                mv.visitInsn(Opcodes.D2I);
                if (t != Type.INT) {
                    addCastIntTo(mv, to);
                }
                break;
            case Type.LONG:
                mv.visitInsn(Opcodes.D2L);
                break;
            case Type.FLOAT:
                mv.visitInsn(Opcodes.D2F);
                break;
            default:
                throw new TypeCastException("Cannot cast double to unknown type: " + to);
        }
    }

    public static void addCastLongTo(final @NotNull MethodVisitor mv, final @NotNull Type to) {
        int t = to.getSort();
        switch (t) {
            case Type.LONG:
                break;
            case Type.INT:
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.SHORT:
            case Type.CHAR:
                mv.visitInsn(Opcodes.L2I);
                if (t != Type.INT) {
                    addCastIntTo(mv, to);
                }
                break;
            case Type.DOUBLE:
                mv.visitInsn(Opcodes.L2D);
                break;
            case Type.FLOAT:
                mv.visitInsn(Opcodes.L2F);
                break;
            default:
                throw new TypeCastException("Cannot cast long to unknown type: " + to);
        }
    }

    public static void addCastFloatTo(final @NotNull MethodVisitor mv, final @NotNull Type to) {
        int t = to.getSort();
        switch (t) {
            case Type.FLOAT:
                break;
            case Type.INT:
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.SHORT:
            case Type.CHAR:
                mv.visitInsn(Opcodes.F2I);
                if (t != Type.INT) {
                    addCastIntTo(mv, to);
                }
                break;
            case Type.DOUBLE:
                mv.visitInsn(Opcodes.F2D);
                break;
            case Type.LONG:
                mv.visitInsn(Opcodes.F2L);
                break;
            default:
                throw new TypeCastException("Cannot cast float to unknown type: " + to);
        }
    }

    /**
     * Emits a boxing instruction (primitive → wrapper).
     */
    private static void addBoxing(final @NotNull MethodVisitor mv, final @NotNull Type primitiveType) {
        String wrapperInternal;
        String valueOfDesc;
        switch (primitiveType.getSort()) {
            case Type.BOOLEAN:
                wrapperInternal = "java/lang/Boolean";
                valueOfDesc = "(Z)Ljava/lang/Boolean;";
                break;
            case Type.BYTE:
                wrapperInternal = "java/lang/Byte";
                valueOfDesc = "(B)Ljava/lang/Byte;";
                break;
            case Type.CHAR:
                wrapperInternal = "java/lang/Character";
                valueOfDesc = "(C)Ljava/lang/Character;";
                break;
            case Type.SHORT:
                wrapperInternal = "java/lang/Short";
                valueOfDesc = "(S)Ljava/lang/Short;";
                break;
            case Type.INT:
                wrapperInternal = "java/lang/Integer";
                valueOfDesc = "(I)Ljava/lang/Integer;";
                break;
            case Type.LONG:
                wrapperInternal = "java/lang/Long";
                valueOfDesc = "(J)Ljava/lang/Long;";
                break;
            case Type.FLOAT:
                wrapperInternal = "java/lang/Float";
                valueOfDesc = "(F)Ljava/lang/Float;";
                break;
            case Type.DOUBLE:
                wrapperInternal = "java/lang/Double";
                valueOfDesc = "(D)Ljava/lang/Double;";
                break;
            default:
                throw new TypeCastException("Cannot box type: " + primitiveType);
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, wrapperInternal, "valueOf", valueOfDesc, false);
    }

    /**
     * Emits an unboxing instruction (wrapper → primitive).
     */
    private static void addUnboxing(final @NotNull MethodVisitor mv, final @NotNull Type primitiveType) {
        String wrapperInternal;
        String methodName;
        String methodDesc;
        switch (primitiveType.getSort()) {
            case Type.BOOLEAN:
                wrapperInternal = "java/lang/Boolean";
                methodName = "booleanValue";
                methodDesc = "()Z";
                break;
            case Type.BYTE:
                wrapperInternal = "java/lang/Byte";
                methodName = "byteValue";
                methodDesc = "()B";
                break;
            case Type.CHAR:
                wrapperInternal = "java/lang/Character";
                methodName = "charValue";
                methodDesc = "()C";
                break;
            case Type.SHORT:
                wrapperInternal = "java/lang/Short";
                methodName = "shortValue";
                methodDesc = "()S";
                break;
            case Type.INT:
                wrapperInternal = "java/lang/Integer";
                methodName = "intValue";
                methodDesc = "()I";
                break;
            case Type.LONG:
                wrapperInternal = "java/lang/Long";
                methodName = "longValue";
                methodDesc = "()J";
                break;
            case Type.FLOAT:
                wrapperInternal = "java/lang/Float";
                methodName = "floatValue";
                methodDesc = "()F";
                break;
            case Type.DOUBLE:
                wrapperInternal = "java/lang/Double";
                methodName = "doubleValue";
                methodDesc = "()D";
                break;
            default:
                throw new TypeCastException("Cannot unbox to type: " + primitiveType);
        }
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, wrapperInternal, methodName, methodDesc, false);
    }

    /**
     * Pushes a zero/null constant for the given type.
     */
    public static void addConstZero(final @NotNull MethodVisitor mv, final @NotNull Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                mv.visitInsn(Opcodes.ICONST_0);
                break;
            case Type.LONG:
                mv.visitInsn(Opcodes.LCONST_0);
                break;
            case Type.FLOAT:
                mv.visitInsn(Opcodes.FCONST_0);
                break;
            case Type.DOUBLE:
                mv.visitInsn(Opcodes.DCONST_0);
                break;
            case Type.VOID:
                // nothing to push
                break;
            default:
                // object type → push null
                mv.visitInsn(Opcodes.ACONST_NULL);
                break;
        }
    }

    /**
     * Emits a return instruction for the given type.
     */
    public static void addReturn(final @NotNull MethodVisitor mv, final @NotNull Type type) {
        switch (type.getSort()) {
            case Type.VOID:
                mv.visitInsn(Opcodes.RETURN);
                break;
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                mv.visitInsn(Opcodes.IRETURN);
                break;
            case Type.LONG:
                mv.visitInsn(Opcodes.LRETURN);
                break;
            case Type.FLOAT:
                mv.visitInsn(Opcodes.FRETURN);
                break;
            case Type.DOUBLE:
                mv.visitInsn(Opcodes.DRETURN);
                break;
            default:
                mv.visitInsn(Opcodes.ARETURN);
                break;
        }
    }

    /**
     * Emits a load instruction for the given type and local variable index.
     */
    public static void addLoad(final @NotNull MethodVisitor mv, int index, final @NotNull Type type) {
        mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), index);
    }

    /**
     * Emits a store instruction for the given type and local variable index.
     */
    public static void addStore(final @NotNull MethodVisitor mv, int index, final @NotNull Type type) {
        mv.visitVarInsn(type.getOpcode(Opcodes.ISTORE), index);
    }

    /**
     * Returns the size of a type in local variable slots (1 or 2).
     */
    public static int typeSize(final @NotNull Type type) {
        return type.getSize();
    }

    /**
     * Builds a method descriptor string from return type and parameter types.
     */
    public static @NotNull String methodDescriptor(final @NotNull Class<?> returnType, final @NotNull Class<?>... paramTypes) {
        Type[] asmParamTypes = new Type[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            asmParamTypes[i] = Type.getType(paramTypes[i]);
        }
        return Type.getMethodDescriptor(Type.getType(returnType), asmParamTypes);
    }
}
