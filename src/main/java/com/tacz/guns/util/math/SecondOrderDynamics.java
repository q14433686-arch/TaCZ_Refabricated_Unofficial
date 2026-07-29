package com.tacz.guns.util.math;

import com.tacz.guns.util.TaczThreads;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class SecondOrderDynamics {
    /**
     * 每个实例占用一个线程跑 {@link #update()} 死循环，所以池容量必须 >= 实例数。
     *
     * <p><b>必须是 daemon 池</b>：{@code update()} 是 {@code while (!stop)} 死循环，
     * 而 {@code stop()} 全仓从未被调用；同时有 5 个常驻实例。
     * 原先用的 {@code Thread::new} 会<b>继承创建者线程</b>的 daemon 属性
     * —— 静态初始化块由谁先触发就随谁，等于把「关闭游戏会不会崩」交给运气。
     * 一旦摊上非 daemon，这 5 个线程就会卡住 JVM 退出，
     * 15 秒后 {@code ClientShutdownWatchdog} 发一份崩溃报告。
     * 详见 {@link TaczThreads}。
     */
    public static final ScheduledExecutorService executorService =
            Executors.newScheduledThreadPool(15, TaczThreads.daemonFactory("tacz-dynamics"));

    static {
        for (int i = 0; i < 15; i++) {
            executorService.execute(() -> {
            });
        }
    }

    private final float k1;
    private final float k2;
    private final float k3;

    private float py;
    private float pyd;
    private float px;

    private float target;

    private boolean stop = false;

    /**
     * @param f  Natural frequency
     * @param z  Damping coefficient
     * @param r  Initial velocity
     * @param x0 Initial position
     */
    public SecondOrderDynamics(float f, float z, float r, float x0) {
        k1 = (float) (z / (Math.PI * f));
        k2 = (float) (1 / ((2 * Math.PI * f) * (2 * Math.PI * f)));
        k3 = (float) (r * z / (2 * Math.PI * f));

        py = px = x0;
        pyd = 0;

        target = x0;

        executorService.execute(this::update);
    }

    /**
     * @return processed y value
     */
    public float update(float x) {
        target = x;
        return get();
    }

    public float get() {
        // 修正罕见的 NAN 错误
        if (Float.isNaN(py)) {
            py = 0;
        }
        if (Float.isNaN(pyd)) {
            pyd = 0;
        }
        return py + 0.05f * pyd;
    }

    public void stop() {
        this.stop = true;
    }

    private void update() {
        while (!stop) {
            // 修正罕见的 NAN 错误
            if (Float.isNaN(py)) {
                py = 0;
            }
            if (Float.isNaN(pyd)) {
                pyd = 0;
            }

            float t = 0.05f;
            float xd = (target - px) / t;
            float y = py + t * pyd;

            pyd = pyd + t * (px + k3 * xd - py - k1 * pyd) / k2;
            px = target;
            py = y;

            try {
                Thread.sleep(6);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
