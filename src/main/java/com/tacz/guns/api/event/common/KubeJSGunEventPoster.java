package com.tacz.guns.api.event.common;

import cn.sh1rocu.tacz.api.event.BaseEvent;

/**
 * 26.2: KubeJS compat disabled - TimelessXEvents classes not available.
 * TODO: Re-implement when KubeJS supports 26.2
 */
public interface KubeJSGunEventPoster<E extends BaseEvent> {
    default void postEventToKubeJS(E event) {
        // 26.2: no-op
    }

    //客户端事件应调用此方法
    default void postClientEventToKubeJS(E event) {
        // 26.2: no-op
    }

    //服务端事件应调用此方法
    default void postServerEventToKubeJS(E event) {
        // 26.2: no-op
    }
}
