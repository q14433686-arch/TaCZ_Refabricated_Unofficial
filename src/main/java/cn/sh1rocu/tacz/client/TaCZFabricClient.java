package cn.sh1rocu.tacz.client;

import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import cn.sh1rocu.simplebedrockmodel.api.event.RenderTickEvent;
import cn.sh1rocu.tacz.api.event.*;
import cn.sh1rocu.tacz.api.extension.IItem;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.api.client.event.RenderItemInHandBobEvent;
import com.tacz.guns.api.client.event.SwapItemWithOffHand;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.event.*;
import com.tacz.guns.client.init.ClientSetupEvent;
import com.tacz.guns.client.init.ModContainerScreen;
import com.tacz.guns.client.init.ModEntitiesRender;
import com.tacz.guns.client.init.ParticleFactories;
import com.tacz.guns.client.renderer.block.GunSmithTableRenderer;
import com.tacz.guns.client.renderer.block.StatueRenderer;
import com.tacz.guns.client.renderer.block.TargetRenderer;
import com.tacz.guns.client.renderer.feature.TaczFeatureRenderers;
import com.tacz.guns.client.renderer.item.AmmoBoxStatueProperty;
import com.tacz.guns.client.renderer.item.TaczDynamicItemModel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties;
import com.tacz.guns.client.input.*;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.init.CommonRegistry;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.network.NetworkHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import net.minecraft.core.registries.BuiltInRegistries;

public class TaCZFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 26.2 client item JSONs use the custom tacz:dynamic_item ItemModel type.
        TaczDynamicItemModel.registerType();
        // 弹药盒外观变体属性（tacz:ammo_statue），供 items/ammo_box.json 的 select 使用。
        // 必须在客户端物品 JSON 解码之前注册，否则 select 会因为找不到属性类型而报错。
        SelectItemModelProperties.ID_MAPPER.put(AmmoBoxStatueProperty.ID, AmmoBoxStatueProperty.TYPE);
        NetworkHandler.registerS2CPackets();
        ClientSetupEvent.init();
        ModContainerScreen.registerScreens();
        ModEntitiesRender.registerEntityRenderers();
        ParticleFactories.registerParticles();
        // All three blocks return RenderShape.INVISIBLE, so registration is mandatory:
        // without these renderers, placed tables/targets/statues are functional but invisible.
        BlockEntityRendererRegistry.register(ModBlocks.GUN_SMITH_TABLE_BE, GunSmithTableRenderer::new);
        BlockEntityRendererRegistry.register(ModBlocks.TARGET_BE, TargetRenderer::new);
        BlockEntityRendererRegistry.register(ModBlocks.STATUE_BE, StatueRenderer::new);
        // getCustomRenderer() 允许返回 null —— 表示「该物品走原版模型渲染」。
        // 弹药盒就是这种情况（改用 items/ammo_box.json 的 select + 9 个变体模型）。
        // 若把 null 塞进注册表，TaczSpecialRenderer 会拿到 null 渲染器而什么都不画。
        BuiltInRegistries.ITEM.stream().filter(item -> item instanceof IItem).forEach(clientEx -> {
            BuiltinItemRendererRegistry.DynamicItemRenderer renderer = ((IItem) clientEx).getCustomRenderer();
            if (renderer != null) {
                BuiltinItemRendererRegistry.INSTANCE.register(clientEx, renderer);
            }
        });
        // 26.2 Feature Rendering: 注册 TACZ 自定义 FeatureRenderers
        TaczFeatureRenderers.register();
        subscribeEvents();
    }

    private void subscribeEvents() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> CommonRegistry.onLoadComplete());

        RenderTickEvent.EVENT.register(RefitTransform::tickInterpolation);

        ViewportEvent.CAMERA.register(CameraSetupEvent::applyLevelCameraAnimation);
        BeforeRenderHandEvent.CALLBACK.register(CameraSetupEvent::applyItemInHandCameraAnimation);
        ViewportEvent.FOV.register(CameraSetupEvent::applyScopeMagnification);
        ViewportEvent.FOV.register(CameraSetupEvent::applyGunModelFovModifying);
        GunFireEvent.CALLBACK.register(CameraSetupEvent::initialCameraRecoil);
        ViewportEvent.CAMERA.register(CameraSetupEvent::applyCameraRecoil);
        ComputeFovModifierEvent.CALLBACK.register(CameraSetupEvent::onComputeMovementFov);

        EntityHurtByGunEvent.POST.register(ClientHitMark::onEntityHurt);
        EntityKillByGunEvent.CALLBACK.register(ClientHitMark::onEntityKill);

        InputEvent.InteractionKeyMappingTriggered.EVENT.register(ClientPreventGunClick::onClickInput);

        ClientPlayConnectionEvents.DISCONNECT.register(CommonNetworkCacheEvent::onClientPlayerLoggingIn);

        // 26.2: 第一人称入口不再走 SimpleBedrockModel 的 RenderHandEvent。
        // 现在统一由客户端 ItemModel（tacz:dynamic_item）-> AnimateGeoItemRenderer#render 的
        // mode.firstPerson() 分支进入，与其它 display context 完全一致。
        // 原先被注释掉的 FirstPersonRenderEvent 注册与其 RenderHandEvent stub 均已删除，
        // 以免再有人把它误当作第一人称链路来排查。

        RenderItemInHandBobEvent.VIEW.register(FirstPersonRenderGunEvent::cancelItemInHandViewBobbing);
        GunFireEvent.CALLBACK.register(FirstPersonRenderGunEvent::onGunFire);

        ClientTickEvents.START_CLIENT_TICK.register(client -> InventoryEvent.onPlayerChangeSelect(client, false));
        ClientTickEvents.END_CLIENT_TICK.register(client -> InventoryEvent.onPlayerChangeSelect(client, true));
        SwapItemWithOffHand.CALLBACK.register(InventoryEvent::onPlayerSwapMainHand);
        ClientPlayerNetworkEvent.LOGGING_OUT.register(InventoryEvent::onPlayerLoggedOut);

        PlayerEvent.LOGGED_IN.register(PlayerEnterWorld::onPlayerEnterWorld);

        EntityHurtByGunEvent.POST.register(PlayerHurtByGunEvent::onPlayerHurtByGun);

        // 【r42】原来这里还注册了 ClientPlayerNetworkEvent.CLONE ->
        // RefreshClonePlayerDataEvent::onClientPlayerClone，但该事件在 26.2 永远发不出来
        // （唯一发射点 ClientHooks#firePlayerRespawn 依赖的 ClientPacketListenerMixin
        //   注入点 ClientLevel#addPlayer 已不存在）。
        // 重生/换维度后的配件缓存刷新改由下面这个 tick 回调内部检测玩家实例变化来触发。
        ClientTickEvents.START_CLIENT_TICK.register(RefreshClonePlayerDataEvent::onClientTick);

        TextureStitchEvent.POST.register(ReloadResourceEvent::onTextureStitchEventPost);

        RenderTickEvent.EVENT.register(RenderCrosshairEvent::onRenderTick);

        RenderLivingEvent.POST.register(RenderHeadShotAABB::onRenderEntity);

        ClientTickEvents.START_CLIENT_TICK.register(TickAnimationEvent::tickAnimation);
        ClientTickEvents.END_CLIENT_TICK.register(TickAnimationEvent::tickAnimation);
        RenderTickEvent.EVENT.register(TickAnimationEvent::tickAnimation);

        ItemTooltipCallback.EVENT.register((stack, tooltipContext, flag, lines) -> TooltipEvent.onTooltip(stack, flag, lines));

        InputEvent.MouseButton.Post.EVENT.register(AimKey::onAimPress);
        ClientTickEvents.END_CLIENT_TICK.register(AimKey::cancelAim);
        ClientTickEvents.START_CLIENT_TICK.register(AimKey::onAimHoldingPreInput);
        ClientTickEvents.END_CLIENT_TICK.register(AimKey::onAimHoldingPreInput);

        InputEvent.Key.EVENT.register(ConfigKey::onOpenConfig);
        InputEvent.Key.EVENT.register(CrawlKey::onCrawlPress);

        InputEvent.Key.EVENT.register(FireSelectKey::onFireSelectKeyPress);
        InputEvent.MouseButton.Post.EVENT.register(FireSelectKey::onFireSelectMousePress);

        InputEvent.Key.EVENT.register(InspectKey::onInspectPress);

        InputEvent.Key.EVENT.register(InteractKey::onInteractKeyPress);
        InputEvent.MouseButton.Post.EVENT.register(InteractKey::onInteractMousePress);

        InputEvent.Key.EVENT.register(MeleeKey::onMeleeKeyPress);
        InputEvent.MouseButton.Post.EVENT.register(MeleeKey::onMeleeMousePress);

        InputEvent.Key.EVENT.register(RefitKey::onRefitPress);

        InputEvent.Key.EVENT.register(ReloadKey::onReloadPress);
        PlayerTickEvent.START.register(ReloadKey::autoReload);

        ClientTickEvents.START_CLIENT_TICK.register(mc -> ShootKey.autoShoot(mc, false));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> ShootKey.autoShoot(mc, true));

        InputEvent.Key.EVENT.register(ZoomKey::onZoomKeyPress);
        InputEvent.MouseButton.Post.EVENT.register(ZoomKey::onZoomMousePress);

        ClientTickEvents.END_CLIENT_TICK.register(SoundPlayManager::onClientTick);
    }
}
