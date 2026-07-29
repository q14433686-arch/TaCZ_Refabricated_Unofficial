package com.tacz.guns.client.resource;

import com.tacz.guns.util.TaczThreads;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 资源后台预热器。
 *
 * <p>这个池本来就是 daemon（唯一一个），改用统一工厂只为口径一致 ——
 * 三个池各写各的 daemon 逻辑正是上一轮漏掉两个的原因。
 * 为什么必须 daemon 见 {@link TaczThreads}。
 */
public final class ClientAssetLoadDispatcher {
    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(TaczThreads.daemonFactory("tacz-client-asset-preload"));

    private ClientAssetLoadDispatcher() {
    }

    public static ExecutorService executor() {
        return EXECUTOR;
    }
}
