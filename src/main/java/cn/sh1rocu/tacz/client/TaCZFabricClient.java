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
        // 附属模块 LRTactical 的客户端物品模型类型（lrtactical:dynamic_item）与
        // 条件属性（lrtactical:has_custom_display）。
        // 必须与上面一行并排、在任何客户端物品 JSON 解码之前完成注册，
        // 否则解码 items/*.json 时会因未知类型而报错，物品将完全没有模型。
        me.xjqsh.lrtactical.client.init.ModEntitiesRender.registerItemModels();
        // 弹药盒外观变体属性（tacz:ammo_statue），供 items/ammo_box.json 的 select 使用。
        // 必须在客户端物品 JSON 解码之前注册，否则 select 会因为找不到属性类型而报错。
        SelectItemModelProperties.ID_MAPPER.put(AmmoBoxStatueProperty.ID, AmmoBoxStatueProperty.TYPE);
        NetworkHandler.registerS2CPackets();
        ClientSetupEvent.init();
        // 内置附属：TacZ Mesh Loader（GPL-3.0 代码移植，见 docs/MESH_LOADER_INTEGRATION_PLAN.md）。
        // 注册 "mesh" 枪械模型类型；必须在任何枪械 display 资源加载之前完成。
        cn.sh1rocu.tacz.compat.meshloader.TaczMeshyIntegration.onClientSetup();
        ModContainerScreen.registerScreens();
        ModEntitiesRender.registerEntityRenderers();
        // 附属模块 LRTactical 的实体渲染器。
        // 必须注册：实体类型有了但没渲染器时，客户端会在
        // EntityRenderDispatcher#shouldRender 抛 NPE 直接崩溃，而非静默不画。
        me.xjqsh.lrtactical.client.init.ModEntitiesRender.registerEntityRenderers();
        // 附属模块 LRTactical 的 S2C 接收器。
        // 必须注册：索引只在服务端加载，联机时客机靠这个包才能拿到，
        // 否则创造栏里找不到手雷、名字也只显示通用名「投掷物」。
        me.xjqsh.lrtactical.client.init.ModEntitiesRender.registerParticles();
        // 致盲遮罩（闪光弹的实际效果所在）
        me.xjqsh.lrtactical.client.init.ModEntitiesRender.registerHudOverlays();
        // 附属模块 LRTactical 的 display 资源加载器（assets/lrtactical/display/**）。
        // 注意它内部声明了「必须排在 TACZ 的模型/动画/脚本之后」，
        // 而这种依赖只在【同一个 ResourceManagerHelper】内生效 ——
        // TACZ 侧的注册在 ClientSetupEvent.init() -> onClientResourceReload()，
        // 用的同样是 ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)。
        me.xjqsh.lrtactical.client.init.ModEntitiesRender.registerReloadListeners();
        // 耳鸣声的播放驱动（效果消失时由音效实例自行 stop）
        ClientTickEvents.END_CLIENT_TICK.register(
                me.xjqsh.lrtactical.client.audio.DeafenState::tick);
        me.xjqsh.lrtactical.network.LrNetworkHandler.registerS2CPackets();
        // 附属模块 LRTactical 的近战按键监听。
        // 直接复用原版左右键（不新建 KeyMapping，避免与原版冲突），
        // 挥空也会发包 -> 解决「AOE 必须先命中一个」与「右键没反应」。
        InputEvent.MouseButton.Post.EVENT.register(
                me.xjqsh.lrtactical.client.input.MeleeAttackKeys::onMousePress);
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
        // 枪械工作台左侧「旋转预览模型」的 PIP 渲染器。
        // 26.2 的 GUI 是 extract→绘制两段式，1.21.1 那套直接改 RenderSystem 模型视图矩阵
        // 再 renderStatic 的做法已不存在；带自定义变换的 GUI 3D 绘制只能走 PictureInPictureRenderer。
        // 不注册的话，GunSmithTableScreen 提交的 GunPreviewRenderState 找不到渲染器 -> 预览框空白。
        net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry.register(
                ctx -> new com.tacz.guns.client.gui.preview.GunPreviewRenderer());
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
        // 附属模块 LRTactical：客户端重生/换维度后丢弃陈旧的近战与冷却状态。
        // 客户端没有可用的重生事件（CLONE 在 26.2 永不触发，见 RefreshClonePlayerDataEvent），
        // 故照抄其「每 tick 比对 Minecraft#player 引用」的手法。
        ClientTickEvents.START_CLIENT_TICK.register(
                client -> me.xjqsh.lrtactical.init.ModCapabilities.onClientPlayerTick(client.player));

        // 附属模块 LRTactical：动画状态机的 idle/walk/run 推进。
        //
        // 【必须单独注册，不能指望 TACZ 的 TickAnimationEvent 顺带处理】——
        // 后者的入口写死了 TimelessAPI.getGunDisplay(...)，只认枪械 display。
        // 缺了这一步，近战/投掷物的动画会永远停在 draw 结束的那一帧
        // （模型和动画其实都加载成功了，看起来却像卡住）。
        //
        // 与 TACZ 一致地在 START 与 END 各注册一次：状态机的 trigger 是幂等的
        // （同一状态重复 trigger 不会重启动画），两端各调一次可减少输入延迟。
        ClientTickEvents.START_CLIENT_TICK.register(
                me.xjqsh.lrtactical.client.event.LrTickAnimationEvent::tickAnimation);
        ClientTickEvents.END_CLIENT_TICK.register(
                me.xjqsh.lrtactical.client.event.LrTickAnimationEvent::tickAnimation);
        // 第三人称的动画推进与音效（第一人称由 ItemInHandRendererMixin 每帧驱动）
        RenderTickEvent.EVENT.register(
                me.xjqsh.lrtactical.client.event.LrTickAnimationEvent::tickAnimation);

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
