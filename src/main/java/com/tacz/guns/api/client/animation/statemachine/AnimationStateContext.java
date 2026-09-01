package com.tacz.guns.api.client.animation.statemachine;

import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.DiscreteTrackArray;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.List;

public class AnimationStateContext {
    private boolean shouldHideCrossHair = false;
    private @Nullable AnimationStateMachine<?> stateMachine;
    private final DiscreteTrackArray trackArray = new DiscreteTrackArray();

    /**
     * 状态机脚本不要调用此方法。
     *
     * @return 上下文绑定的状态机。
     */
    public @Nullable AnimationStateMachine<?> getStateMachine() {
        return stateMachine;
    }

    /**
     * 状态机脚本不要调用此方法。
     *
     * @return 上下文的离散轨道序列。
     */
    public DiscreteTrackArray getTrackArray() {
        return trackArray;
    }

    /**
     * 分配一个新的轨道行，返回新的轨道行下标。
     *
     * @return 新的轨道行下标
     * @throws TrackArrayMismatchException 当状态机对应的 track array 不是当前 context 指定的实例，抛出此异常。
     */
    public int addTrackLine() {
        checkTrackArray();
        return getTrackArray().addTrackLine();
    }

    /**
     * 确保轨道行的数量
     *
     * @param size 需要确保的轨道行数量。
     * @throws TrackArrayMismatchException 当状态机对应的 track array 不是当前 context 指定的实例，抛出此异常。
     */
    public void ensureTrackLineSize(int size) {
        checkTrackArray();
        getTrackArray().ensureCapacity(size);
    }

    /**
     * 获取轨道行的数量
     *
     * @return 轨道行的数量
     * @throws TrackArrayMismatchException 当状态机对应的 track array 不是当前 context 指定的实例，抛出此异常。
     */
    public int getTrackLineSize() {
        checkTrackArray();
        return getTrackArray().getTrackLineSize();
    }

    /**
     * 为指定轨道行分配一个新的轨道，返回新的轨道的下标
     *
     * @param index 轨道行的下标
     * @return 新的轨道下标
     * @throws TrackArrayMismatchException 当状态机对应的 track array 不是当前 context 指定的实例，抛出此异常。
     */
    public int assignNewTrack(int index) {
        checkTrackArray();
        return getTrackArray().assignNewTrack(index);
    }

    /**
     * 优先返回轨道行中的空闲轨道，如果没有空闲轨道则会开辟一个新的轨道
     *
     * @param index            轨道行的下标
     * @param interruptHolding 是否将处于 holding 状态的轨道视为空闲轨道
     * @return 轨道在控制器中的指针
     * @throws TrackArrayMismatchException 当状态机对应的 track array 不是当前 context 指定的实例，抛出此异常。
     * @see AnimationStateContext#assignNewTrack(int)
     */
    public int findIdleTrack(int index, boolean interruptHolding) {
        var stateMachine = checkStateMachine();
        checkTrackArray();
        DiscreteTrackArray trackArray = getTrackArray();
        List<Integer> trackList = trackArray.getByIndex(index);
        AnimationController controller = stateMachine.getAnimationController();
        for (int track : trackList) {
            ObjectAnimationRunner animation = controller.getAnimation(track);
            if (animation == null || animation.isStopped() || (interruptHolding && animation.isHolding())) {
                return track;
            }
        }
        return trackArray.assignNewTrack(index);
    }

    /**
     * 保证指定的轨道行有足够的轨道数量
     *
     * @param index  轨道行下标
     * @param amount 需要的轨道数量
     */
    public void ensureTracksAmount(int index, int amount) {
        checkTrackArray();
        getTrackArray().ensureTrackAmount(index, amount);
    }

    /**
     * 获取轨道指针
     *
     * @param trackLineIndex 轨道行的下标
     * @param trackIndex     轨道的下标
     * @return 轨道在控制器中的指针，或者 -1 当轨道不存在
     */
    public int getTrack(int trackLineIndex, int trackIndex) {
        checkTrackArray();
        DiscreteTrackArray trackArray = getTrackArray();
        if (trackLineIndex >= trackArray.getTrackLineSize()) {
            return -1;
        }
        List<Integer> tracks = trackArray.getByIndex(trackLineIndex);
        if (trackIndex >= tracks.size()) {
            return -1;
        }
        return tracks.get(trackIndex);
    }

    /**
     * 用于只需要一个轨道的轨道行，如果目标轨道行没有轨道，则会分配一个轨道，
     * 如果已经有多个轨道，多余的轨道不会舍弃，会返回其中的第一个轨道。
     *
     * @param index 轨道行的下标
     * @return 轨道的下标
     * @throws TrackArrayMismatchException 当状态机对应的 track array 不是当前 context 指定的实例，抛出此异常。
     */
    public int getAsSingletonTrack(int index) {
        checkTrackArray();
        DiscreteTrackArray trackArray = getTrackArray();
        List<Integer> trackList = trackArray.getByIndex(index);
        if (trackList.isEmpty()) {
            return trackArray.assignNewTrack(index);
        } else {
            return trackList.get(0);
        }
    }

    /**
     * 在指定轨道上运行动画。如果轨道已经有动画在运行，将会打断，并根据输入的过渡时间开始过渡。
     * 新动画在播放的瞬间就开始运行，并不会因为过渡而停止。旧动画则在播放开始的瞬间停止。
     *
     * @param name           动画的名称
     * @param track          轨道在控制器中的指针
     * @param blending       动画是否向下混合
     * @param playType       动画的播放状态，为枚举的 ordinal 值。
     * @param transitionTime 过渡时长
     * @see AnimationConstant
     */
    public void runAnimation(String name, int track, boolean blending, int playType, float transitionTime) {
        var stateMachine = checkStateMachine();
        ObjectAnimation.PlayType pt = ObjectAnimation.PlayType.values()[playType];
        stateMachine.getAnimationController().runAnimation(track, name, pt, transitionTime);
        stateMachine.getAnimationController().setBlending(track, blending);
    }

    /**
     * 将动画停止。停止后的动画关键帧不会再影响模型。
     *
     * <h3>过渡链上的动画何时连坐（2026-08-30 两轮修复的完整结论）</h3>
     * {@code runAnimation} 带过渡时长启动动画时，新 runner 是挂在旧 runner 的
     * {@code transitionTo} 上的 —— 过渡完成前（常见 0.2 秒），
     * {@code getAnimation(track)} 返回的仍是<b>旧</b> runner。
     * 于是「stop 该不该波及 transitionTo」出现两个方向相反的实锤案例：
     *
     * <p><b>案例一（必须连坐）：开镜检视不可打断。</b>脚本 {@code inspect.transition}
     * 的「{@code aimingProgress > 0} 就 stop + 回 idle」分支，会被每 tick 的移动类
     * 输入在检视启动后 ≤50ms 内触发 —— 必然落在过渡窗口内。此时轨道上的当前
     * runner 是<b>早已停止的旧残骸</b>（上一个 draw/换弹的尸体），检视本体在
     * {@code transitionTo} 上。只停当前 runner = 停了个寂寞，检视成了无主僵尸，
     * 状态机已回 idle、所有挂在 inspect 态上的打断手段全部失联，动画播完全程。</p>
     *
     * <p><b>案例二（必须豁免）：检视中换弹，换弹动画被误杀。</b>
     * {@code AnimationStateMachine#trigger} 的调用顺序是先 {@code transition()}
     * 后 {@code exitAction()}：换弹输入先在 transition 里
     * {@code runAnimation("reload", ..., 0.2)} —— 新 runner 恰好也挂在
     * 检视 runner 的 {@code transitionTo} 上 —— 随后 {@code inspect.exit} 的
     * stopAnimation 才执行。一刀切停 transitionTo 会把刚启动的换弹当场杀掉：
     * 检视被打断了，换弹却不播（第一轮修复的回归，用户实测）。</p>
     *
     * <p><b>判据：出生序号。</b>{@code AnimationStateMachine#trigger} 在进入状态
     * 转移前快照 runner 发号器（{@code getTriggerSpawnFloor}），
     * 序号大于快照的 runner 必然是<b>本次 trigger 里刚启动的</b>后继动画 ——
     * 正是案例二要豁免的那一个；序号不大于快照的（案例一的检视：上一次 trigger
     * 启动的）照常连坐。两个案例由此完美分野，且不依赖「当前 runner 恰好是残骸」
     * 这类可能被别的时序破坏的间接特征。不在 trigger 里调用时（快照 = -1）
     * 无从区分新旧，保持连坐 —— 案例一的语义是默认语义。</p>
     *
     * <p>与 {@link #isStopped(int)} 的「有 transitionTo 就看 transitionTo」语义自洽：
     * 案例一停完后 transitionTo.isStopped() = true（轨道判空闲 ✓）；
     * 案例二停完后 transitionTo 是活的换弹（轨道判忙 ✓，
     * {@code findIdleTrack} 不会把它当空闲轨道覆写）。</p>
     *
     * @param track 轨道在控制器中的指针
     */
    public void stopAnimation(int track) {
        var stateMachine = checkStateMachine();
        ObjectAnimationRunner runner = stateMachine.getAnimationController().getAnimation(track);
        if (runner != null) {
            ObjectAnimationRunner transitionTo = runner.getTransitionTo();
            if (transitionTo != null) {
                long floor = stateMachine.getTriggerSpawnFloor();
                boolean bornThisTrigger = floor >= 0 && transitionTo.getSpawnOrdinal() > floor;
                if (!bornThisTrigger) {
                    transitionTo.stop();
                }
            }
            runner.stop();
        }
    }

    /**
     * 将动画进度拖至动画末尾并挂起。挂起的动画将定格在动画的最后一帧。
     *
     * @param track 轨道在控制器中的指针
     */
    public void holdAnimation(int track) {
        var stateMachine = checkStateMachine();
        ObjectAnimationRunner runner = stateMachine.getAnimationController().getAnimation(track);
        if (runner != null) {
            runner.hold();
        }
    }

    /**
     * 暂停动画。动画将会定格，关键帧仍然影响模型。
     *
     * @param track 轨道在控制器中的指针
     */
    public void pauseAnimation(int track) {
        var stateMachine = checkStateMachine();
        ObjectAnimationRunner runner = stateMachine.getAnimationController().getAnimation(track);
        if (runner != null) {
            runner.pause();
        }
    }

    /**
     * 恢复动画运行。如果动画已经在运行，则什么都不会发生
     *
     * @param track 轨道在控制器中的指针
     */
    public void resumeAnimation(int track) {
        var stateMachine = checkStateMachine();
        ObjectAnimationRunner runner = stateMachine.getAnimationController().getAnimation(track);
        if (runner != null) {
            runner.run();
        }
    }

    /**
     * 设置动画播放的绝对进度。
     * 如果启用归一化 (normalization 设为 true)，则 progress 可取值 0 ~ 1，0 代表动画开头，1 代表动画结尾。
     * 否则，progress 代表时长，单位：秒
     *
     * @param track         轨道在控制器中的指针
     * @param progress      动画的绝对进度，如果 normalization 为 true，则可取值 0 ~ 1，0 代表动画开头，1 代表动画结尾。否则代表时长，单位为秒
     * @param normalization 是否启用归一化
     */
    public void setAnimationProgress(int track, float progress, boolean normalization) {
        var stateMachine = checkStateMachine();
        ObjectAnimationRunner runner = stateMachine.getAnimationController().getAnimation(track);
        if (runner != null) {
            if (runner.isRunning() || runner.isPausing()) {
                if (normalization) {
                    progress = runner.getAnimation().getMaxEndTimeS() * progress;
                }
                runner.setProgressNs((long) (progress * 1e9));
                return;
            }
            ObjectAnimationRunner runner1 = runner.getTransitionTo();
            if (runner1 != null) {
                if (normalization) {
                    progress = runner1.getAnimation().getMaxEndTimeS() * progress;
                }
                runner1.setProgressNs((long) (progress * 1e9));
            }
        }
    }

    /**
     * 在当前动画进度的基础上移动一段进度，比如前进 10s、后退 10s。
     * 如果启用归一化 (normalization 设为 true)，则 progress 可取值 -1 ~ 1，-1 代表后退动画全长，1 代表前进动画全长。
     * 否则，progress 代表时长，单位：秒
     *
     * @param track         轨道在控制器中的指针
     * @param progress      相对进度，可为负值。如果 normalization 为 true，则可取值 -1 ~ 1，-1 代表后退动画全长，1 代表前进动画全长。
     *                      否则代表时长，单位为秒。
     * @param normalization 是否启用归一化
     */
    public void adjustAnimationProgress(int track, float progress, boolean normalization) {
        var stateMachine = checkStateMachine();
        ObjectAnimationRunner runner = stateMachine.getAnimationController().getAnimation(track);
        if (runner != null) {
            if (runner.isRunning()) {
                if (normalization) {
                    progress = runner.getAnimation().getMaxEndTimeS() * progress;
                }
                runner.setProgressNs(runner.getProgressNs() + (long) (progress * 1e9));
                return;
            }
            ObjectAnimationRunner runner1 = runner.getTransitionTo();
            if (runner1 != null) {
                if (normalization) {
                    progress = runner1.getAnimation().getMaxEndTimeS() * progress;
                }
                runner1.setProgressNs(runner1.getProgressNs() + (long) (progress * 1e9));
            }
        }
    }

    /**
     * 获取指定轨道是否被挂起
     *
     * @return 返回对应轨道的动画是否挂起。轨道为空时此方法返回 false，因为轨道没有动画的时候视为轨道停止，而非挂起。
     */
    public boolean isHolding(int track) {
        var stateMachine = checkStateMachine();
        ObjectAnimationRunner runner = stateMachine.getAnimationController().getAnimation(track);
        if (runner != null) {
            return (runner.getTransitionTo() != null ? runner.getTransitionTo().isHolding() : runner.isHolding());
        } else {
            return false;
        }
    }

    /**
     * 获取指定轨道是否停止
     *
     * @return 返回对应轨道的动画是否停止。轨道为空时此方法返回 true，因为轨道没有动画的时候视为轨道停止。
     */
    public boolean isStopped(int track) {
        var stateMachine = checkStateMachine();
        ObjectAnimationRunner runner = stateMachine.getAnimationController().getAnimation(track);
        if (runner != null) {
            return (runner.getTransitionTo() != null ? runner.getTransitionTo().isStopped() : runner.isStopped());
        } else {
            return true;
        }
    }

    /**
     * 获取指定轨道是否暂停
     *
     * @return 返回对应轨道的动画是否暂停。轨道为空时此方法返回 false，因为轨道没有动画的时候视为轨道停止，而非暂停。
     */
    public boolean isPause(int track) {
        var stateMachine = checkStateMachine();
        ObjectAnimationRunner runner = stateMachine.getAnimationController().getAnimation(track);
        if (runner != null) {
            return (runner.getTransitionTo() != null ? !runner.getTransitionTo().isPausing() : !runner.isPausing());
        } else {
            return false;
        }
    }

    /**
     * 获取动画文件中是否存在某个动画
     *
     * @param name 动画名称
     * @return 动画是否存在
     */
    public boolean hasAnimationPrototype(String name) {
        var stateMachine = checkStateMachine();
        AnimationController animationController = stateMachine.getAnimationController();
        return animationController.containPrototype(name);
    }

    /**
     * 手动触发一次状态转移
     *
     * @param input 状态转移的输入
     */
    public void trigger(String input) {
        var stateMachine = checkStateMachine();
        stateMachine.trigger(input);
    }

    /**
     * 动画有时会有剧烈的视角运动，因此可能需要隐藏准心减少眩晕感。
     *
     * @return 渲染时是否需要隐藏准心
     */
    public boolean shouldHideCrossHair() {
        return shouldHideCrossHair;
    }

    /**
     * 动画有时会有剧烈的视角运动，因此可能需要隐藏准心减少眩晕感。
     *
     * @param shouldHideCrossHair 渲染时是否需要隐藏准心
     */
    public void setShouldHideCrossHair(boolean shouldHideCrossHair) {
        this.shouldHideCrossHair = shouldHideCrossHair;
    }

    void setStateMachine(@Nullable AnimationStateMachine<?> stateMachine) {
        if (this.stateMachine != null) {
            this.stateMachine.getAnimationController().setUpdatingTrackArray(null);
        }
        if (stateMachine != null) {
            stateMachine.getAnimationController().setUpdatingTrackArray(trackArray);
        }
        this.stateMachine = stateMachine;
    }

    private void checkTrackArray() {
        if (stateMachine != null && stateMachine.getAnimationController().getUpdatingTrackArray() != trackArray) {
            throw new TrackArrayMismatchException();
        }
    }

    @Nonnull
    private AnimationStateMachine<?> checkStateMachine() {
        if (this.stateMachine == null) {
            throw new IllegalStateException("This context has not been bound to a state machine.");
        }
        return this.stateMachine;
    }
}
