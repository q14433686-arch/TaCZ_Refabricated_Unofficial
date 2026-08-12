package com.tacz.guns.compat.playeranimator.pal;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunDrawEvent;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.playeranimator.AnimationName;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** PAL 1.2.5 implementation of TACZ's four legacy third-person animation layers. */
public final class PalAnimationManager {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private PalAnimationManager() {
    }

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        registerController(PlayerAnimatorCompat.LOWER_ANIMATION, 93);
        registerController(PlayerAnimatorCompat.LOOP_UPPER_ANIMATION, 94);
        registerController(PlayerAnimatorCompat.ONCE_UPPER_ANIMATION, 95);
        registerController(PlayerAnimatorCompat.ROTATION_ANIMATION, 96);

        PalAnimationManager manager = new PalAnimationManager();
        GunShootEvent.CALLBACK.register(manager::onFire);
        GunReloadEvent.CALLBACK.register(manager::onReload);
        GunMeleeEvent.CALLBACK.register(manager::onMelee);
        GunDrawEvent.CALLBACK.register(manager::onDraw);
    }

    private static void registerController(Identifier id, int priority) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(id, priority, avatar -> {
            PlayerAnimationController controller = new PlayerAnimationController(
                    avatar, (animationController, data, setter) -> PlayState.STOP
            );
            // TACZ first-person gun and arm rendering is collector-based and independent.
            controller.setFirstPersonMode(FirstPersonMode.DISABLED);
            if (id.equals(PlayerAnimatorCompat.ROTATION_ANIMATION)) {
                controller.addModifierLast(new SafeAdjustmentModifier(new PalRotationAdjustment(avatar)));
            }
            return controller;
        });
    }

    public static boolean hasAnimations(GunDisplayInstance display) {
        Identifier fileId = display.getPlayerAnimator3rd();
        return fileId != null && PalAssetManager.INSTANCE.contains(fileId);
    }

    public static void play(AbstractClientPlayer player, GunDisplayInstance display, float limbSwingAmount) {
        discardProneTransitionOnStand(player);
        playLower(player, display, limbSwingAmount);
        playUpper(player, display, limbSwingAmount);
        playNamed(player, display, PlayerAnimatorCompat.ROTATION_ANIMATION, AnimationName.EMPTY, true);
    }

    public static void stopAll(AbstractClientPlayer player, int fadeTicks) {
        discardProneTransitionOnStand(player);
        stop(player, PlayerAnimatorCompat.LOWER_ANIMATION, fadeTicks);
        stop(player, PlayerAnimatorCompat.LOOP_UPPER_ANIMATION, fadeTicks);
        stop(player, PlayerAnimatorCompat.ONCE_UPPER_ANIMATION, fadeTicks);
        stop(player, PlayerAnimatorCompat.ROTATION_ANIMATION, fadeTicks);
    }

    private static void playLower(AbstractClientPlayer player, GunDisplayInstance display, float limbSwingAmount) {
        if (isPlayerLie(player)) {
            return;
        }
        String name;
        if (player.getVehicle() != null) {
            name = AnimationName.RIDE_LOWER;
        } else if (isFlying(player)) {
            name = AnimationName.HOLD_LOWER;
        } else if (player.isSprinting()) {
            name = player.getPose() == Pose.CROUCHING
                    ? AnimationName.CROUCH_WALK_LOWER : AnimationName.RUN_LOWER;
        } else if (limbSwingAmount > 0.05F) {
            name = player.getPose() == Pose.CROUCHING
                    ? AnimationName.CROUCH_WALK_LOWER : AnimationName.WALK_LOWER;
        } else {
            name = player.getPose() == Pose.CROUCHING
                    ? AnimationName.CROUCH_LOWER : AnimationName.HOLD_LOWER;
        }
        playNamed(player, display, PlayerAnimatorCompat.LOWER_ANIMATION, name, true);
    }

    private static void playUpper(AbstractClientPlayer player, GunDisplayInstance display, float limbSwingAmount) {
        float aimingProgress = IGunOperator.fromLivingEntity(player).getSynAimingProgress();
        String name;
        if (aimingProgress > 0) {
            if (isPlayerLie(player)) {
                name = !isFlying(player) && limbSwingAmount > 0.05F
                        ? AnimationName.LIE_MOVE : AnimationName.LIE_AIM;
            } else {
                name = AnimationName.AIM_UPPER;
            }
        } else if (!isFlying(player) && player.isSprinting()) {
            name = isPlayerLie(player) ? AnimationName.LIE_MOVE
                    : player.getPose() == Pose.CROUCHING
                    ? AnimationName.CROUCH_WALK_UPPER : AnimationName.RUN_UPPER;
        } else if (!isFlying(player) && limbSwingAmount > 0.05F) {
            name = isPlayerLie(player) ? AnimationName.LIE_MOVE
                    : player.getPose() == Pose.CROUCHING
                    ? AnimationName.CROUCH_WALK_UPPER : AnimationName.WALK_UPPER;
        } else {
            name = isPlayerLie(player) ? AnimationName.LIE : AnimationName.HOLD_UPPER;
        }
        playNamed(player, display, PlayerAnimatorCompat.LOOP_UPPER_ANIMATION, name, true);
    }

    private static void playNamed(AbstractClientPlayer player,
                                  GunDisplayInstance display,
                                  Identifier controllerId,
                                  String animationName,
                                  boolean replaceWithFade) {
        Identifier fileId = display.getPlayerAnimator3rd();
        if (fileId == null) {
            return;
        }
        Animation animation = PalAssetManager.INSTANCE.get(fileId, animationName).orElse(null);
        PlayerAnimationController controller = controller(player, controllerId);
        if (animation == null || controller == null) {
            return;
        }
        // getCurrentAnimationInstance() reports the last loaded clip even after the controller has
        // stopped. Only suppress an identical request while that clip is actually active; otherwise
        // a controller reset (and a finished one-shot such as fire/reload) could never restart it.
        if (controller.isActive() && controller.getCurrentAnimationInstance() == animation) {
            return;
        }
        // 重新开始播放 -> 允许下一次收枪再触发一次淡出。
        clearFadeMark(player, controllerId);
        if (replaceWithFade) {
            controller.replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(8, EasingType.EASE_IN_OUT_SINE), animation);
        } else if (!controller.isPlayingTriggeredAnimation()) {
            controller.triggerAnimation(animation);
        }
    }

    /**
     * 记录已经请求过淡出的 controller，避免每帧重复调用 {@code replaceAnimationWithFade}。
     *
     * <p>用 {@link java.util.WeakHashMap} 以玩家为键：玩家实体一被 GC（切世界/退出），
     * 记录自动消失，不会泄漏。</p>
     */
    private static final Map<AbstractClientPlayer, Set<Identifier>> FADING_OUT =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Last TACZ prone state observed while driving or stopping PAL for each player. */
    private static final Map<AbstractClientPlayer, Boolean> LAST_PRONE_STATE =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * PAL 1.2.5 represents each fade as a modifier containing a snapshot of the outgoing bone
     * transforms. That is normally useful, but a TACZ prone clip and a standing clip use different
     * body axes and substantially different arm offsets. Keeping the prone snapshot across the
     * {@code Pose.SWIMMING -> standing} boundary lets a later draw/fade use that snapshot as its
     * starting pose again. Repeating the cycle can therefore retain and compound the old Euler
     * rotations until an unrelated first-person render resets the model.
     *
     * <p>The boundary is also observed from {@link #stopAll}: switching to a non-gun clears TACZ's
     * forced swimming pose before there is another call to {@link #play}, so checking only the play
     * path would miss that exit. At the exact prone-to-standing edge we discard only fade modifiers
     * and controller playback state. The permanent rotation adjustment modifier is deliberately
     * retained, as are ordinary fade transitions that do not cross this incompatible pose edge.</p>
     */
    private static void discardProneTransitionOnStand(AbstractClientPlayer player) {
        boolean prone = isPlayerLie(player);
        Boolean wasProne = LAST_PRONE_STATE.put(player, prone);
        if (!Boolean.TRUE.equals(wasProne) || prone) {
            return;
        }

        resetAtProneExit(player, PlayerAnimatorCompat.LOWER_ANIMATION);
        resetAtProneExit(player, PlayerAnimatorCompat.LOOP_UPPER_ANIMATION);
        resetAtProneExit(player, PlayerAnimatorCompat.ONCE_UPPER_ANIMATION);
        resetAtProneExit(player, PlayerAnimatorCompat.ROTATION_ANIMATION);
        FADING_OUT.remove(player);
    }

    private static void resetAtProneExit(AbstractClientPlayer player, Identifier controllerId) {
        PlayerAnimationController controller = controller(player, controllerId);
        if (controller == null) {
            return;
        }
        // Do not use removeAllModifiers(): ROTATION owns SafeAdjustmentModifier for its lifetime.
        controller.removeModifierIf(AbstractFadeModifier.class::isInstance);
        controller.stopTriggeredAnimation();
        controller.stop();
        controller.forceAnimationReset();
    }

    private static void stop(AbstractClientPlayer player, Identifier controllerId, int fadeTicks) {
        PlayerAnimationController controller = controller(player, controllerId);
        if (controller == null || !controller.isActive()) {
            // 已经停下来了：清掉标记，下次重新播放后才能再次触发淡出。
            Set<Identifier> marks = FADING_OUT.get(player);
            if (marks != null) {
                marks.remove(controllerId);
            }
            return;
        }
        // 【第 38 轮】STOP 永远不要用 FADE_OUT —— PAL 1.2.5 的 fadeOut 会【永久哑掉】
        // 该 controller，这正是「装 PAL 后切枪一次、第三人称动画整局消失、小退/大退才恢复」
        // 的根因。源码级证据链（zigythebird/PlayerAnimationLibrary@main）：
        //
        //   1. AbstractFadeModifier#canRemove()：只有 FADE_IN 完成（progress>=1）才返回
        //      true；FADE_OUT 恒 false —— fadeOut 走完也【永远留在 modifier 链上】；
        //   2. AnimationController#tick() 逐帧只按 canRemove() 摘除 modifier —— 摘不掉它；
        //   3. AnimationController#get3DTransform(bone)：链非空即全权交给链首，靠
        //      super.get3DTransform 逐级内传。fadeOut 完成态 progress=0 → alpha=0 →
        //      输出 = bone（上游输入，链首即空 identity）＋ 下游动画 × 0
        //      —— 此后无论再 trigger/fadeIn 什么，骨骼输出恒为 identity；
        //   4. controller 只在 avatar 重建时重新生成 —— 所以大退/小退「治好」。
        //
        // 规避（零侵入 PAL）：改用 <b>FADE_IN-to-null</b>——
        // standardFadeIn(ticks) + triggerAnimation(null)。replaceAnimationWithFade 会
        // 把当前骨骼快照塞进 transitionAnimation，8 tick 内从旧姿势平滑滑入 identity
        // （=视觉上的淡出），其 progress>=1 后 canRemove()=true → PAL 下一 tick 自动
        // 摘除，链路恢复干净，后续播放/fade 一切如常。
        // 切枪时若新枪动画同帧 fadeIn 进来，两条 FADE_IN 链上短暂共存也是平滑叠加，
        // 互不压制（FADE_IN 从不把下游乘 0）。
        //
        // 注：then(null) 的路径经现网行为证实宽容（旧 fadeOut(null) 调用只哑不崩），
        // 本调用走同一 RawAnimation 路径，无新增风险。
        Set<Identifier> marks = FADING_OUT.computeIfAbsent(
                player, p -> Collections.synchronizedSet(new HashSet<>()));
        if (!marks.add(controllerId)) {
            return;
        }
        controller.replaceAnimationWithFade(
                AbstractFadeModifier.standardFadeIn(fadeTicks, EasingType.EASE_IN_OUT_SINE),
                (Animation) null
        );
    }

    /** 播放时清除淡出标记，使下一次收枪能重新触发淡出。 */
    private static void clearFadeMark(AbstractClientPlayer player, Identifier controllerId) {
        Set<Identifier> marks = FADING_OUT.get(player);
        if (marks != null) {
            marks.remove(controllerId);
        }
    }


    private static PlayerAnimationController controller(AbstractClientPlayer player, Identifier id) {
        var layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, id);
        return layer instanceof PlayerAnimationController controller ? controller : null;
    }

    private static boolean isFlying(AbstractClientPlayer player) {
        return !player.onGround() && player.getAbilities().flying;
    }

    private static boolean isPlayerLie(AbstractClientPlayer player) {
        return !player.isSwimming() && player.getPose() == Pose.SWIMMING;
    }

    private static boolean skipLocalFirstPerson(AbstractClientPlayer player) {
        return Minecraft.getInstance().player == player
                && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    private void onFire(GunShootEvent event) {
        if (event.getLogicalSide().isServer() || !(event.getShooter() instanceof AbstractClientPlayer player)
                || skipLocalFirstPerson(player)) {
            return;
        }
        ItemStack stack = event.getGunItemStack();
        if (!(stack.getItem() instanceof IGun)) {
            return;
        }
        TimelessAPI.getGunDisplay(stack).ifPresent(display -> {
            float aiming = IGunOperator.fromLivingEntity(player).getSynAimingProgress();
            String name = isPlayerLie(player)
                    ? aiming > 0 ? AnimationName.LIE_AIM_FIRE : AnimationName.LIE_NORMAL_FIRE
                    : aiming > 0 ? AnimationName.AIM_FIRE_UPPER : AnimationName.NORMAL_FIRE_UPPER;
            playNamed(player, display, PlayerAnimatorCompat.ONCE_UPPER_ANIMATION, name, false);
        });
    }

    private void onReload(GunReloadEvent event) {
        if (event.getLogicalSide().isServer() || !(event.getEntity() instanceof AbstractClientPlayer player)
                || skipLocalFirstPerson(player)) {
            return;
        }
        TimelessAPI.getGunDisplay(event.getGunItemStack()).ifPresent(display ->
                playNamed(player, display, PlayerAnimatorCompat.ONCE_UPPER_ANIMATION,
                        isPlayerLie(player) ? AnimationName.LIE_RELOAD : AnimationName.RELOAD_UPPER, false));
    }

    private void onMelee(GunMeleeEvent event) {
        if (event.getLogicalSide().isServer() || !(event.getShooter() instanceof AbstractClientPlayer player)
                || skipLocalFirstPerson(player)) {
            return;
        }
        String name = switch (player.getRandom().nextInt(3)) {
            case 0 -> AnimationName.MELEE_UPPER;
            case 1 -> AnimationName.MELEE_2_UPPER;
            default -> AnimationName.MELEE_3_UPPER;
        };
        TimelessAPI.getGunDisplay(event.getGunItemStack()).ifPresent(display ->
                playNamed(player, display, PlayerAnimatorCompat.ONCE_UPPER_ANIMATION, name, false));
    }

    private void onDraw(GunDrawEvent event) {
        if (event.getLogicalSide().isServer() || !(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        if (event.getCurrentGunItem().getItem() instanceof IGun
                && event.getPreviousGunItem().getItem() instanceof IGun) {
            discardProneTransitionOnStand(player);

            // 26.2-specific switch boundary: hard-reset the three authored animation layers
            // instead of fading the old gun to null.
            //
            // The 26.1.2 and 26.2 PalAnimationManager sources were byte-for-byte identical,
            // and both use PAL 1.2.5. The remaining behavioral difference is therefore not a
            // failed textual backport. The old fix depended on LAST_PRONE_STATE being observed
            // by the render-driven play()/stopAll() calls before this event, then called stop(),
            // which captures the controller's current activeBones into another fade snapshot.
            // Under 26.2's deferred PlayerModel submission/order that render observation can be
            // late, so the gun-to-gun transition itself re-snapshots the contaminated pose. The
            // steady held-gun loop is clean; only this eight-tick switch crossfade is dirty.
            //
            // GunDrawEvent is the authoritative boundary and occurs before the next model setup.
            // Removing all fade snapshots and stopping these layers here makes the new gun start
            // its normal 8-tick fade-in from identity on the next play() call. ROTATION is kept:
            // it is a permanent view adjustment, not part of the authored switch transition.
            hardResetSwitchLayer(player, PlayerAnimatorCompat.LOWER_ANIMATION);
            hardResetSwitchLayer(player, PlayerAnimatorCompat.LOOP_UPPER_ANIMATION);
            hardResetSwitchLayer(player, PlayerAnimatorCompat.ONCE_UPPER_ANIMATION);
            FADING_OUT.remove(player);
        }
    }

    private static void hardResetSwitchLayer(AbstractClientPlayer player, Identifier controllerId) {
        PlayerAnimationController controller = controller(player, controllerId);
        if (controller == null) {
            return;
        }
        controller.removeModifierIf(AbstractFadeModifier.class::isInstance);
        controller.stopTriggeredAnimation();
        controller.stop();
        controller.forceAnimationReset();
    }
}
