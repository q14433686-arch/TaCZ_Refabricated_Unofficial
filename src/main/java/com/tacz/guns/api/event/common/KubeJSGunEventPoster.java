package com.tacz.guns.api.event.common;

import cn.sh1rocu.tacz.api.event.BaseEvent;

/**
 * Stable event-poster facade while KubeJS has no Fabric 26.1.2 release (its 26.1.2 builds are
 * NeoForge-only). Native TACZ callbacks still run; only the optional KubeJS mirror is disabled.
 */
public interface KubeJSGunEventPoster<E extends BaseEvent> {
    default void postEventToKubeJS(E event) {
    }

    // 客户端事件应调用此方法
    default void postClientEventToKubeJS(E event) {
    }

    // 服务端事件应调用此方法
    default void postServerEventToKubeJS(E event) {
    }
}
