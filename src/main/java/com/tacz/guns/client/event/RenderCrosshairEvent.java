package com.tacz.guns.client.event;

import cn.sh1rocu.simplebedrockmodel.api.event.RenderTickEvent;
import com.mojang.blaze3d.platform.Window;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateContext;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.renderer.crosshair.CrosshairType;
import com.tacz.guns.compat.shouldersurfing.ShoulderSurfingCompat;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;


@Environment(EnvType.CLIENT)
public class RenderCrosshairEvent {
    private static final Identifier HIT_ICON = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/crosshair/hit/hit_marker.png");
    private static final long KEEP_TIME = 300;
    private static boolean isRefitScreen = false;
    private static long hitTimestamp = -1L;
    private static long killTimestamp = -1L;
    private static long headShotTimestamp = -1L;

    /**
     * 当玩家手上拿着枪时，播放特定动画、或瞄准时需要隐藏准心
     */
    public static void onRenderOverlay(GuiGraphicsExtractor guiGraphics, Window window) {
        // 【掩码调试叠加层】ScopeMaskDebug 打开时，把掩码 target 按着色器采样方向
        // 画到左上角。截图与镜筒一对比就能直接看出掩码是黑、是翻、还是位置错 ——
        // 这是把不可见的 GPU 掩码变成可见像素的唯一手段。
        if (RenderConfig.SCOPE_MASK_DEBUG != null && RenderConfig.SCOPE_MASK_DEBUG.get()) {
            renderMaskDebug(guiGraphics, window);
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (!IGun.mainHandHoldGun(player)) {
            return;
        }

        // 击中显示
        renderHitMarker(guiGraphics, window);
        // 换弹进行时取消准心渲染
        ReloadState reloadState = IGunOperator.fromLivingEntity(player).getSynReloadState();
        if (reloadState.getStateType().isReloading()) {
            return;
        }
        // 打开枪械改装界面的时候，取消准心渲染
        if (isRefitScreen) {
            return;
        }
        // 播放的动画需要隐藏准心时，取消准心渲染
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun)) {
            return;
        }

        IClientPlayerGunOperator playerGunOperator = IClientPlayerGunOperator.fromLocalPlayer(player);
        TimelessAPI.getGunDisplay(stack).ifPresent(gunIndex -> {
            // 瞄准快要完成时，取消准心渲染
            if (playerGunOperator.getClientAimingProgress(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true)) > 0.9) {
                // 枪包可以强制显示准星
                boolean forceShow = gunIndex.isShowCrosshair();
                // 越肩视角可以强制显示准星
                boolean shoulderSurfingForceShow = ShoulderSurfingCompat.showCrosshair();
                // 两个强制都没有时，那么才允许隐藏
                if (!forceShow && !shoulderSurfingForceShow) {
                    return;
                }
            }

            AnimationStateMachine<?> animationStateMachine = gunIndex.getAnimationStateMachine();
            if (animationStateMachine == null) {
                renderCrosshair(guiGraphics, window);
                return;
            }
            AnimationStateContext context = animationStateMachine.getContext();
            if (context == null || !context.shouldHideCrossHair()) {
                renderCrosshair(guiGraphics, window);
            }
        });
    }

    public static void onRenderTick(RenderTickEvent event) {
        // 奇迹的是，RenderGameOverlayEvent.PreLayer 事件中，screen 还未被赋值...
        isRefitScreen = Minecraft.getInstance().gui.screen() instanceof GunRefitScreen;
    }

    private static void renderCrosshair(GuiGraphicsExtractor graphics, Window window) {
        Options options = Minecraft.getInstance().options;
        // 越肩视角可以强制显示准星
        boolean shoulderSurfingForceShow = ShoulderSurfingCompat.showCrosshair();
        if (!options.getCameraType().isFirstPerson() && !shoulderSurfingForceShow) {
            return;
        }
        // 26.2: options.hideGui removed; GUI visibility now managed by Hud render pipeline
        MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
        if (gameMode == null) {
            return;
        }
        if (gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return;
        }
        int width = window.getGuiScaledWidth();
        int height = window.getGuiScaledHeight();

        Identifier location = CrosshairType.getTextureLocation(RenderConfig.CROSSHAIR_TYPE.get());

        float x = width / 2f - 8;
        float y = height / 2f - 8;
        // 26.2: Blending now handled by RenderPipeline; color via last int param (ARGB)
        graphics.blit(RenderPipelines.GUI_TEXTURED, location, (int) x, (int) y, 0, 0, 16, 16, 16, 16, 0xE6FFFFFF);
    }

    /** 见 {@link #onRenderOverlay}：把掩码 target 画到左上角供截图诊断。 */
    private static void renderMaskDebug(GuiGraphicsExtractor graphics, Window window) {
        var target = com.tacz.guns.client.render.scope.ScopeMaskTarget.current();
        if (target == null) {
            return;
        }
        var view = target.getColorTextureView();
        if (view == null) {
            return;
        }
        int W = Math.max(1, window.getGuiScaledWidth());
        int H = Math.max(1, window.getGuiScaledHeight());
        int dw = W / 3;
        int dh = (int) ((long) dw * H / W);
        int dx = 2;
        int dy = 2;
        var sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
                .getClampToEdge(com.mojang.renderpearl.api.textures.FilterMode.NEAREST);
        // v0=1 在顶、v1=0 在底：与 scope_body.fsh 的 scopeUv（NDC，y=0 在底）采样方向一致，
        // 叠加层与「着色器认为目镜在哪」同向，直接和镜筒对比即可判断方向/位置/黑屏。
        graphics.blit(view, sampler, dx, dy, dx + dw, dy + dh, 0f, 1f, 1f, 0f);
    }

    private static void renderHitMarker(GuiGraphicsExtractor graphics, Window window) {
        long remainHitTime = System.currentTimeMillis() - hitTimestamp;
        long remainKillTime = System.currentTimeMillis() - killTimestamp;
        long remainHeadShotTime = System.currentTimeMillis() - headShotTimestamp;
        float offset = RenderConfig.HIT_MARKET_START_POSITION.get().floatValue();
        float fadeTime;

        if (remainKillTime > KEEP_TIME) {
            if (remainHitTime > KEEP_TIME) {
                return;
            } else {
                fadeTime = remainHitTime;
            }
        } else {
            // 最大位移为 4 像素
            offset += (remainKillTime * 4f) / KEEP_TIME;
            fadeTime = remainKillTime;
        }

        int width = window.getGuiScaledWidth();
        int height = window.getGuiScaledHeight();
        float x = width / 2f - 8;
        float y = height / 2f - 8;

        // 26.2: Blending now handled by RenderPipeline; color via last int param (ARGB)
        int color;
        if (remainHeadShotTime > KEEP_TIME) {
            color = ((int) ((1 - fadeTime / KEEP_TIME) * 255) << 24) | 0x00FFFFFF;
        } else {
            color = ((int) ((1 - fadeTime / KEEP_TIME) * 255) << 24) | 0x00FF0000;
        }

        graphics.blit(RenderPipelines.GUI_TEXTURED, HIT_ICON, (int) (x - offset), (int) (y - offset), 0, 0, 8, 8, 16, 16, color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, HIT_ICON, (int) (x + 8 + offset), (int) (y - offset), 8, 0, 8, 8, 16, 16, color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, HIT_ICON, (int) (x - offset), (int) (y + 8 + offset), 0, 8, 8, 8, 16, 16, color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, HIT_ICON, (int) (x + 8 + offset), (int) (y + 8 + offset), 8, 8, 8, 8, 16, 16, color);
    }

    public static void markHitTimestamp() {
        RenderCrosshairEvent.hitTimestamp = System.currentTimeMillis();
    }

    public static void markKillTimestamp() {
        RenderCrosshairEvent.killTimestamp = System.currentTimeMillis();
    }

    public static void markHeadShotTimestamp() {
        RenderCrosshairEvent.headShotTimestamp = System.currentTimeMillis();
    }
}
