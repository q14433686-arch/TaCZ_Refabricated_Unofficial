package me.xjqsh.lrtactical.client.init;

import me.xjqsh.lrtactical.entity.GrenadeEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import me.xjqsh.lrtactical.client.renderer.entity.ThrowableEntityRenderer;

/**
 * 实体渲染器注册。
 *
 * <h2>为什么必须有</h2>
 * 实体类型注册了但<b>没有对应渲染器</b>时，客户端不会「不画它」，
 * 而是在 {@code EntityRenderDispatcher#shouldRender} 处直接
 * <b>抛 NullPointerException 导致游戏崩溃</b>
 * （{@code Cannot invoke "EntityRenderer.shouldRender(...)" because "renderer" is null}）。
 *
 * <p>第 5 步遗漏了这一步，表现为「手雷一扔出去就崩」——
 * 实体本身已成功生成，崩在客户端渲染阶段。
 *
 * <h2>【动画层补完后的更新】改用自建的 {@code ThrowableEntityRenderer}</h2>
 * 原先这里用原版 {@code ThrownItemRenderer}，理由是「本移植不打包美术资源，
 * 用原版渲染器即可」。补完动画层后这个理由不再成立 —— 装了内容包时，
 * 原版渲染器会把手雷画成<b>永远正对镜头的平面贴片</b>，
 * 既丢失飞行姿态，也无法隐藏 {@code entity_hide}（拉环/保险销）组。
 *
 * <p>{@code ThrowableEntityRenderer} 内部仍走
 * {@code ItemModelResolver#updateForTopItem}，因此<b>没装内容包时行为不变</b>
 * （照样是原版物品模型），只是多了朝向旋转。
 */
@Environment(EnvType.CLIENT)
public final class ModEntitiesRender {
    private ModEntitiesRender() {
    }

    public static void registerEntityRenderers() {
        // 【必须】每新增一种投掷物实体都要在这里加一行，否则一进视野就 NPE 崩溃。
        // 五种投掷物实体都继承 ThrowableItemEntity，故共用同一个渲染器。
        EntityRendererRegistry.register(GrenadeEntity.TYPE, ThrowableEntityRenderer::new);
        EntityRendererRegistry.register(
                me.xjqsh.lrtactical.entity.StickyGrenadeEntity.TYPE, ThrowableEntityRenderer::new);
        EntityRendererRegistry.register(
                me.xjqsh.lrtactical.entity.SmokeGrenadeEntity.TYPE, ThrowableEntityRenderer::new);
        EntityRendererRegistry.register(
                me.xjqsh.lrtactical.entity.EffectCloudGrenadeEntity.TYPE, ThrowableEntityRenderer::new);
        EntityRendererRegistry.register(
                me.xjqsh.lrtactical.entity.StunGrenadeEntity.TYPE, ThrowableEntityRenderer::new);
        // 效果云本体用 NoopRenderer：云自身【不绘制任何模型】，视觉完全由粒子构成。
        // 原版 AreaEffectCloud 用的正是它（EntityRenderers 常量池确认引用了
        // NoopRenderer.<init>(EntityRendererProvider$Context)）。
        // 注意它同样【必须注册】—— 没有渲染器一进视野就 NPE 崩溃，
        // 「不需要画」和「不注册」是两回事。
        EntityRendererRegistry.register(
                me.xjqsh.lrtactical.entity.sp.SpEffectCloudEntity.TYPE,
                net.minecraft.client.renderer.entity.NoopRenderer::new);
    }

    /**
     * 粒子工厂注册。
     *
     * <p>用带 {@code PendingParticleProvider} 的重载 —— 它会在贴图图集就绪后
     * 回调并给出 {@code SpriteSet}（sprite 来自
     * {@code assets/<ns>/particles/<name>.json}，与原版规则一致）。
     * 另一个不带 SpriteSet 的重载适用于自绘贴图的粒子
     * （本仓库 {@code BulletHoleParticle} 走的是那条）。
     */
    public static void registerParticles() {
        net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance()
                .register(me.xjqsh.lrtactical.init.ModParticleTypes.SMOKE_CLOUD,
                        me.xjqsh.lrtactical.client.particle.SmokeCloudParticle.Provider::new);
    }

    /**
     * 动画/渲染层的客户端注册。
     *
     * <p><b>必须在客户端物品 JSON 解码之前调用</b> —— 两个注册项都是「JSON 里会引用的类型」，
     * 注册晚了会在解码 {@code items/*.json} 时报「未知类型」，
     * 表现为物品完全没有模型（而不是回退到默认模型）。
     * 调用点因此与 TACZ 的 {@code TaczDynamicItemModel.registerType()} 并排，
     * 放在 {@code onInitializeClient} 的最前面。
     */
    public static void registerItemModels() {
        // 客户端物品模型类型 lrtactical:dynamic_item
        me.xjqsh.lrtactical.client.renderer.item.LrDynamicItemModel.registerType();
        // 条件属性 lrtactical:has_custom_display（用于「有无内容包」的模型分流）
        net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties.ID_MAPPER.put(
                me.xjqsh.lrtactical.client.renderer.item.HasCustomDisplayProperty.ID,
                me.xjqsh.lrtactical.client.renderer.item.HasCustomDisplayProperty.MAP_CODEC);
    }

    /**
     * 注册 display 资源加载器（{@code assets/lrtactical/display/**}）。
     *
     * <p><b>必须与 TACZ 用同一个 {@code ResourceManagerHelper}</b>，
     * 否则 {@code getFabricDependencies()} 声明的「排在 TACZ 模型/动画之后」不生效。
     * 详见 {@code LrClientAssetsManager} 的类注释。
     */
    public static void registerReloadListeners() {
        me.xjqsh.lrtactical.client.resource.LrClientAssetsManager.INSTANCE.reloadAndRegister(
                net.fabricmc.fabric.api.resource.ResourceManagerHelper
                        .get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
                        ::registerReloadListener);
    }

    /**
     * 把本模块的物品与其自定义渲染器登记进 {@code BuiltinItemRendererRegistry}。
     *
     * <p>TACZ 侧在 {@code TaCZFabricClient} 里是遍历整个物品注册表、挑出
     * {@code instanceof IItem} 的来注册。LRTactical 的两个物品同样实现了
     * {@code IItem}，因此<b>会被那段遍历一并覆盖</b> —— 本方法只是把这层依赖
     * 显式化，便于日后 TACZ 侧改写遍历逻辑时不至于静默失效。
     * 重复注册是幂等的（底层是 {@code IdentityHashMap#put}）。
     */
    public static void registerItemRenderers() {
        register(me.xjqsh.lrtactical.init.ModItems.MELEE);
        register(me.xjqsh.lrtactical.init.ModItems.THROWABLE);
    }

    private static void register(net.minecraft.world.item.Item item) {
        if (item instanceof cn.sh1rocu.tacz.api.extension.IItem ext) {
            var renderer = ext.getCustomRenderer();
            if (renderer != null) {
                cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry.INSTANCE.register(item, renderer);
            }
        }
    }

    /**
     * HUD 覆盖层注册。先登记使用/预燃/近战进度，最后登记致盲。
     *
     * <p>用 {@code addLast} 让致盲遮罩画在<b>所有 HUD 元素之上</b> ——
     * 被闪到时物品栏、血条也应该一起被白屏盖住，
     * 否则玩家仍能靠 HUD 读信息，失去「致盲」的意义。
     *
     * <p>写法照抄本仓库 {@code ClientSetupEvent} 里 5 个 overlay 的既有注册
     * （26.2 用 {@code HudElementRegistry} + {@code GuiGraphicsExtractor}，
     * 与 1.21.1 的 {@code GuiGraphics} 不同）。
     */
    public static void registerHudOverlays() {
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        me.xjqsh.lrtactical.EquipmentMod.MOD_ID, "using_progress"),
                (graphics, deltaTracker) ->
                        me.xjqsh.lrtactical.client.overlay.UsingProgressOverlay.render(
                                graphics, deltaTracker.getRealtimeDeltaTicks()));
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        me.xjqsh.lrtactical.EquipmentMod.MOD_ID, "blindness"),
                (graphics, deltaTracker) ->
                        me.xjqsh.lrtactical.client.overlay.BlindnessOverlay.render(
                                graphics, deltaTracker.getRealtimeDeltaTicks()));
    }
}
