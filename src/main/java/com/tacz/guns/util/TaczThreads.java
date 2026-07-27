package com.tacz.guns.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本模组所有后台线程的统一工厂 —— <b>一律 daemon</b>。
 *
 * <h2>为什么必须是 daemon</h2>
 * JVM 只在<b>最后一个非 daemon 线程</b>结束后才退出。26.2 的客户端为此加了看门狗：
 * {@code Main#main} 在 {@code Minecraft#run} 与 {@code exitWorldAndClose} 返回后调用
 * {@code ClientShutdownWatchdog#startShutdownWatchdog}，它睡
 * {@code CRASH_REPORT_PRELOAD_LOAD}（字节码确认 = 15 秒）后，若进程还活着就
 * dump 全部线程、写一份 "Client shutdown from ..." 崩溃报告，再 {@code System.exit}。
 *
 * <p>也就是说：<b>任何一个残留的非 daemon 线程，都会让玩家在关闭游戏 15 秒后
 * 收到一份崩溃报告</b>。游戏本身已经玩完了，但报告照发，观感上就是"退出即崩溃"。
 *
 * <h2>本模组踩的坑</h2>
 * 修复前有三个线程池，只有一个是 daemon：
 * <table border="1">
 *   <caption>修复前的线程池状况</caption>
 *   <tr><th>池</th><th>工厂</th><th>daemon?</th></tr>
 *   <tr><td>{@code ClientAssetLoadDispatcher.EXECUTOR}</td>
 *       <td>自建，显式 {@code setDaemon(true)}</td><td>是</td></tr>
 *   <tr><td>{@code LocalPlayerDataHolder.SCHEDULED_EXECUTOR_SERVICE}</td>
 *       <td>{@code Executors.defaultThreadFactory()}</td>
 *       <td><b>否</b> —— 该工厂<b>无条件</b> {@code setDaemon(false)}</td></tr>
 *   <tr><td>{@code SecondOrderDynamics.executorService}</td>
 *       <td>{@code Thread::new}</td>
 *       <td><b>看运气</b> —— {@code new Thread(Runnable)} 继承<b>创建者线程</b>的
 *           daemon 属性，而这里是静态初始化块，谁先碰到这个类就随谁</td></tr>
 * </table>
 *
 * <p>后两者都没有任何 {@code shutdown()} 调用（全仓 grep 零命中），
 * 所以它们的线程会一直活到进程被强杀。
 *
 * <p>{@code SecondOrderDynamics} 尤其严重：它的 {@code update()} 是
 * {@code while (!stop)} 死循环 + {@code Thread.sleep(6)}，而 {@code stop()}
 * <b>全仓从未被调用过</b>；同时有 5 个常驻实例
 * （{@code WORLD_FOV} / {@code ITEM_MODEL_FOV} / {@code AIMING} /
 * {@code REFIT_OPENING} / {@code JUMPING}），等于 5 个永不退出的线程。
 * 只要它们恰好是非 daemon，关闭游戏必然触发看门狗。
 *
 * <h2>为什么用 daemon 而不是「退出时 shutdown 线程池」</h2>
 * 两者都能解决，但 daemon 是<b>兜底</b>而非<b>约定</b>：
 * 它不依赖任何一处退出钩子被正确注册和执行，也不受关闭顺序影响。
 * 这几个池干的都是纯客户端表现层的活（FOV 平滑、音效定时、模型预热），
 * 进程退出时直接丢弃没有任何副作用 —— 没有需要落盘的状态。
 *
 * <p>顺带给线程起了名字。修复前 {@code SecondOrderDynamics} 那 15 个线程
 * 在 jstack 里全叫 {@code Thread-N}，正是因为这样，用户贴来的线程 dump 里
 * 根本认不出它们是本模组的。
 */
public final class TaczThreads {
    private TaczThreads() {
    }

    /**
     * 造一个只产出 daemon 线程的工厂。
     *
     * @param poolName 线程名前缀，最终形如 {@code tacz-fov-smoothing-1}
     */
    public static ThreadFactory daemonFactory(String poolName) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, poolName + "-" + counter.getAndIncrement());
            // 关键：绝不阻塞 JVM 退出。
            thread.setDaemon(true);
            // 显式压到普通优先级 —— new Thread 会继承创建者的优先级，
            // 而这些池常在渲染线程(优先级偏高)上被首次触碰。
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        };
    }
}
