// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data;

import com.tacz.guns.client.particle.bedrock.particle.data.component.*;
import com.tacz.guns.client.particle.bedrock.particle.data.component.lifetime.LifetimeComponent;
import com.tacz.guns.client.particle.bedrock.particle.data.component.rate.RateComponent;
import com.tacz.guns.client.particle.bedrock.particle.data.component.shape.*;

import java.util.*;

/**
 * 发射器组件预设。从 {@link ParticleEffectDefinition} 构建，不可变。
 * <p>
 * 存储 {@link IEmitterComponentDefinition}，按 {@code order()} 排序。
 */
public final class EmitterPreset {

    private final List<IEmitterComponentDefinition> components;
    private final List<IEmitterComponentDefinition> updateComponents;
    private final Map<Class<? extends IComponent>, IComponent> componentMap;

    private final boolean localPosition;
    private final boolean localRotation;
    private final boolean localVelocity;

    public EmitterPreset(List<IEmitterComponentDefinition> components) {
        validateNoDuplicates(components);

        List<IEmitterComponentDefinition> sorted = new ArrayList<>(components);
        sorted.sort(Comparator.comparingInt(IEmitterComponentDefinition::order));
        this.components = Collections.unmodifiableList(sorted);

        this.updateComponents = this.components.stream()
                .filter(IEmitterComponentDefinition::requireUpdate)
                .toList();

        Map<Class<? extends IComponent>, IComponent> map = new HashMap<>();
        for (IEmitterComponentDefinition c : this.components) {
            map.put(c.getClass(), c);
            for (Class<?> iface : c.getClass().getInterfaces()) {
                if (IComponent.class.isAssignableFrom(iface)
                        && iface != IComponent.class
                        && iface != IParticleComponent.class
                        && iface != IEmitterComponent.class
                        && iface != IParticleComponentDefinition.class
                        && iface != IEmitterComponentDefinition.class) {
                    @SuppressWarnings("unchecked")
                    Class<? extends IComponent> parentType = (Class<? extends IComponent>) iface;
                    map.putIfAbsent(parentType, c);
                }
            }
        }
        this.componentMap = Collections.unmodifiableMap(map);

        EmitterLocalSpace ls = find(EmitterLocalSpace.class);
        this.localPosition = ls != null && ls.position();
        this.localRotation = ls != null && ls.position() && ls.rotation();
        this.localVelocity = ls != null && ls.velocity();
    }

    private static void validateNoDuplicates(List<IEmitterComponentDefinition> components) {
        boolean hasRate = false, hasLifetime = false, hasShape = false;
        for (IEmitterComponentDefinition c : components) {
            if (c instanceof RateComponent) {
                if (hasRate) throw new IllegalArgumentException("Duplicate rate component");
                hasRate = true;
            }
            if (c instanceof LifetimeComponent) {
                if (hasLifetime) throw new IllegalArgumentException("Duplicate lifetime component");
                hasLifetime = true;
            }
            if (c instanceof EmitterShape) {
                if (hasShape) throw new IllegalArgumentException("Duplicate shape component");
                hasShape = true;
            }
        }
    }

    // ===== 公共 API =====

    /** 全部发射器定义组件（已排序，不可变） */
    public List<IEmitterComponentDefinition> components() {
        return components;
    }

    /** requireUpdate()=true 的定义组件子集 */
    public List<IEmitterComponentDefinition> updateComponents() {
        return updateComponents;
    }

    @SuppressWarnings("unchecked")
    public <T extends IComponent> T find(Class<T> type) {
        return (T) componentMap.get(type);
    }

    public boolean localPosition() {
        return localPosition;
    }

    public boolean localRotation() {
        return localRotation;
    }

    public boolean localVelocity() {
        return localVelocity;
    }
}
