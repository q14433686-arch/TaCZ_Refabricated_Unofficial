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
        playLower(player, display, limbSwingAmount);
        playUpper(player, display, limbSwingAmount);
        playNamed(player, display, PlayerAnimatorCompat.ROTATION_ANIMATION, AnimationName.EMPTY, true);
    }

    public static void stopAll(AbstractClientPlayer player, int fadeTicks) {
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
        if (animation == null || controller == null || controller.getCurrentAnimationInstance() == animation) {
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
        // 【第 37 轮】去重：本 controller 的淡出只请求一次。
        //
        // 为什么必须去重（PAL 源码 AnimationController#replaceAnimationWithFade L434-446）：
        //     if (fadeFromNothing || this.isActive()) {
        //         ...
        //         addModifierLast(fadeModifier);      // ← 无条件【追加】，不是替换
        //     }
        //     this.triggerAnimation(newAnimation);
        // 它每调用一次就往 modifier 链上<b>挂一个新的</b> fade modifier，从不移除旧的。
        //
        // 而 stopAllAnimation 是从 InnerThirdPersonManager 里每帧调的
        // （手里不是枪 -> 每一帧都走 stopAllAnimation）。原来只有 isActive() 这一道门禁，
        // 但淡出期间 isActive() 仍为 true，于是每帧都会再挂一个 fade ——
        // 几十帧后 modifier 链上堆满淡出层，链首那个 modifier 把后续动画的输出层层削掉。
        // 表现就是用户报告的：收枪后走/跑/游泳<b>没有对应动作</b>，
        // 而开枪/换弹走 ONCE_UPPER 的 triggerAnimation（会重置 modifier 链）能短暂恢复，
        // 随后又被新一轮堆积压回去。
        Set<Identifier> marks = FADING_OUT.computeIfAbsent(
                player, p -> Collections.synchronizedSet(new HashSet<>()));
        if (!marks.add(controllerId)) {
            return;
        }
        controller.replaceAnimationWithFade(
                AbstractFadeModifier.standardFadeOut(fadeTicks, EasingType.EASE_IN_OUT_SINE),
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
            stopAll(player, 8);
        }
    }
}
