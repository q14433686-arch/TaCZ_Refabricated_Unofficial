package me.xjqsh.lrtactical.client.renderer.entity;

import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.xjqsh.lrtactical.client.renderer.item.ThrowableItemRendererWrapper;
import me.xjqsh.lrtactical.client.renderer.model.CustomBedrockModel;
import me.xjqsh.lrtactical.entity.ThrowableItemEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 飞行中的投掷物实体渲染。
 *
 * <p>比原版 {@code ThrownItemRenderer}（雪球那种「永远正对镜头的平面图标」）多两件事：
 * <ol>
 *   <li><b>按飞行朝向旋转</b> —— 手雷在空中是有姿态的，不是一张贴片；</li>
 *   <li><b>隐藏 {@code entity_hide} 组</b> —— 拉环、保险销这些「已经被拔掉」的部件
 *       不应该跟着手雷一起飞出去。</li>
 * </ol>
 *
 * <h2>26.2 移植：整个 EntityRenderer 契约变了</h2>
 * <table border="1">
 *   <tr><th>上游 1.21.1</th><th>26.2</th></tr>
 *   <tr><td>{@code EntityRenderer<T>}（一个泛型参）</td>
 *       <td>{@code EntityRenderer<T, S extends EntityRenderState>}（两个）</td></tr>
 *   <tr><td>{@code render(entity, yaw, pt, poseStack, bufferSource, light)}</td>
 *       <td><b>拆成两步</b>：{@code extractRenderState(entity, state, pt)} 先在主线程
 *           取快照，{@code submit(state, poseStack, collector, cameraState)} 再提交</td></tr>
 *   <tr><td>{@code getTextureLocation(entity)}</td>
 *       <td><b>已从基类移除</b> —— 上游那个返回 {@code null} 的覆写直接删掉</td></tr>
 *   <tr><td>{@code Minecraft.getInstance().getItemRenderer().renderStatic(...)}</td>
 *       <td>{@code ItemModelResolver#updateForTopItem} +
 *           {@code ItemStackRenderState#submit}</td></tr>
 *   <tr><td>{@code IClientItemExtensions.of(stack).getCustomRenderer()}</td>
 *       <td>{@code BuiltinItemRendererRegistry.INSTANCE.get(item)}</td></tr>
 * </table>
 *
 * <h2>{@code setEntityRendering} 为什么必须在 submit 阶段成对开关</h2>
 * 这个标志位挂在<b>共享的</b> {@code CustomBedrockModel} 实例上 ——
 * 同一种手雷，手里那个和天上飞的那些，用的是<b>同一份模型对象</b>。
 * 所以必须「提交前置 true、提交后立刻置回 false」，
 * 否则手里的手雷也会跟着丢掉拉环。
 *
 * <p>而且<b>不能</b>在 {@code extractRenderState} 里设 —— 那时几何还没被提取，
 * 到 {@code submit} 时早已被别的实体改回去了。
 * （TACZ 的 {@code MuzzleFlashRender.isSelf} 用的是同一套「共享模型 + 成对开关」手法。）
 */
public class ThrowableEntityRenderer
        extends EntityRenderer<ThrowableItemEntity, ThrowableEntityRenderer.ThrowableRenderState> {

    private final ItemModelResolver itemModelResolver;

    public ThrowableEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    /**
     * 渲染状态快照。
     *
     * <p>只放<b>值</b>（已插值好的角度、已解析好的物品渲染状态），
     * 不放实体引用 —— submit 可能在别的时刻执行，那时实体状态已经变了。
     * 唯一例外是 {@code stack}：它是 {@code getItem()} 返回的副本语义对象，
     * 且只用来查渲染器，不读可变状态。
     */
    public static class ThrowableRenderState extends EntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public ItemStack stack = ItemStack.EMPTY;
        public float yRot;
        public float xRot;
    }

    @Override
    public ThrowableRenderState createRenderState() {
        return new ThrowableRenderState();
    }

    @Override
    public void extractRenderState(ThrowableItemEntity entity, ThrowableRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
        state.xRot = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        state.stack = entity.getItem();
        // ItemDisplayContext.GROUND：与掉落物一致的语义，内容包的 transforms 里
        // "ground" 段正是为这个场景准备的
        this.itemModelResolver.updateForTopItem(
                state.item, state.stack, ItemDisplayContext.GROUND, entity.level(), null, 0);
    }

    @Override
    public void submit(ThrowableRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        poseStack.pushPose();

        // 与上游一致的姿态摆放：先抬一点，再按飞行朝向旋转，最后微调到手雷本体中心
        poseStack.translate(0, 0.15, 0);
        poseStack.mulPose(Axis.YN.rotationDegrees(state.yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot));
        poseStack.translate(0, 0.35, -0.15);

        // 见类注释：共享模型上的开关，必须在 submit 期间成对开合
        CustomBedrockModel model = resolveModel(state.stack);
        if (model != null) {
            model.setEntityRendering(true);
        }
        try {
            state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        } finally {
            // 用 finally 兜底：提交过程若抛异常，标志位不能留在 true，
            // 否则玩家手里的手雷会永久缺件（且看不出与异常有关）
            if (model != null) {
                model.setEntityRendering(false);
            }
        }

        poseStack.popPose();
        super.submit(state, poseStack, collector, cameraState);
    }

    /**
     * 取该物品当前使用的 Bedrock 模型；没装内容包时返回 {@code null}（走原版物品模型，无需开关）。
     */
    private static CustomBedrockModel resolveModel(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (BuiltinItemRendererRegistry.INSTANCE.get(stack.getItem())
                instanceof ThrowableItemRendererWrapper renderer) {
            return renderer.getModel(stack);
        }
        return null;
    }
}
