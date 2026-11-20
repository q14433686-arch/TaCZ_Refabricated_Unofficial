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

import com.tacz.guns.client.particle.bedrock.molang.runtime.value.ObjectProperty;
import com.tacz.guns.client.particle.bedrock.molang.runtime.value.Value;
import com.tacz.guns.client.particle.bedrock.molang.util.CaseInsensitiveStringHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

final class ScopeImpl implements Scope {
    private final Map<String, ObjectProperty> bindings = new CaseInsensitiveStringHashMap<>();
    private boolean readOnly;

    @Override
    public @Nullable ObjectProperty getProperty(final @NotNull String name) {
        return bindings.get(name);
    }

    @Override
    public @NotNull Scope copy() {
        final ScopeImpl copy = new ScopeImpl();
        copy.bindings.putAll(this.bindings);
        return copy;
    }

    @Override
    public boolean set(final @NotNull String name, final @Nullable Value value) {
        if (readOnly) {
            return false;
        }
        if (value == null) {
            bindings.remove(name);
        } else {
            bindings.put(name, ObjectProperty.property(value, false));
        }
        return true;
    }

    @Override
    public void readOnly(final boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public boolean readOnly() {
        return readOnly;
    }

    @Override
    public @NotNull Map<String, ObjectProperty> entries() {
        return bindings;
    }

    static final class BuilderImpl implements Builder {
        private final Map<String, ObjectProperty> properties = new CaseInsensitiveStringHashMap<>();

        @Override
        public Builder set(@NotNull String name, @NotNull Value value) {
            properties.put(name, ObjectProperty.property(value, true));
            return this;
        }

        @Override
        public Scope build() {
            ScopeImpl impl = new ScopeImpl();
            impl.bindings.putAll(properties);
            return impl;
        }
    }
}
