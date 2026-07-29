package me.xjqsh.lrtactical.client.renderer.item;

import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.api.item.ICustomItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * LRTactical 版的动态物品模型桥接（客户端 {@code ItemModel} 类型 {@code lrtactical:dynamic_item}）。
 *
 * <p>作用与 TACZ 的 {@code TaczDynamicItemModel} 完全相同：把 26.2 的
 * {@code ItemModel → SpecialModelRenderer} 管线接到本模块的
 * {@link MeleeItemRenderer} / {@link ThrowableItemRendererWrapper} 上。
 *
 * <h2>为什么<b>不能</b>直接复用 {@code tacz:dynamic_item}</h2>
 * 两者的 {@code SpecialModelRenderer} 逻辑一字不差（都是从
 * {@code BuiltinItemRendererRegistry} 按 {@code Item} 取渲染器再调 {@code render}），
 * 唯一的差别在 <b>GUI 图标缓存键</b>。
 *
 * <p>{@code GuiItemAtlas#getOrUpdate} 用 {@code getModelIdentity()}（一个 List）
 * 作为 key 去分配/复用图标槽位。TACZ 的 {@code identityKeyOf} 是这样取内容 id 的：
 * <pre>
 * if (item instanceof IGun g)            contentId = g.getGunId(stack);
 * else if (item instanceof IAttachment a) ...
 * else if (item instanceof IAmmo a)       ...
 * else                                    contentId = null;   // → 落到 ""
 * </pre>
 * LRTactical 的物品<b>一个都不匹配</b>这些接口 —— 所有手雷共用
 * {@code lrtactical:throwable} 这一个 {@code Item}，靠 NBT 区分种类。
 * 于是 M67、闪光弹、烟雾弹会算出<b>完全相同</b>的 identity
 * {@code [throwableItem, GUI, ""]}，在物品栏里<b>共用同一个图标槽位</b> ——
 * 表现为「几种手雷图标串味/全都长一样」。
 *
 * <p>本类把内容 id 换成 {@link ICustomItem#getDisplayId}（这正是决定外观的那个 id），
 * 从根本上解决该冲突。<b>这是必须新建一个类型、而不是复用 TACZ 那个的唯一原因</b>，
 * 其余部分（extents 取值、identity 的值语义要求、快照注意事项）
 * 均与 {@code TaczDynamicItemModel} 一致，那里有完整论证，此处只做要点提示。
 */
public final class LrDynamicItemModel implements ItemModel {
    public static final Identifier TYPE_ID =
            Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "dynamic_item");

    private static final LrSpecialRenderer SPECIAL_RENDERER = new LrSpecialRenderer();

    /**
     * 模型包围盒角点。
     *
     * <p>取值与 {@code TaczDynamicItemModel.EXTENTS} <b>完全一致</b>，两条约束缺一不可
     * （该类有逐条的字节码论证，这里只记结论）：
     * <ul>
     *   <li><b>每条边长必须 ≤ 1.0</b>：否则 {@code calculateOversizedItemBounds} 判定为
     *       oversized，强制走 {@code OversizedItemRenderer} 的离屏渲染路径，
     *       结果是<b>物品栏图标一片空白</b>；</li>
     *   <li><b>{@code minY} 必须为 0</b>：{@code ItemEntityRenderer#submit} 直接用
     *       {@code -aabb.minY()} 决定掉落物抬升高度，取 -0.5 会让掉落物<b>凭空浮高半格</b>。</li>
     * </ul>
     * 故 XZ 用对称的 ±0.5（旋转以模型原点为中心），Y 用 [0, 1]。
     */
    private static final Supplier<Vector3fc[]> EXTENTS = () -> new Vector3fc[]{
            new Vector3f(-0.5F, 0.0F, -0.5F),
            new Vector3f(-0.5F, 0.0F, 0.5F),
            new Vector3f(-0.5F, 1.0F, -0.5F),
            new Vector3f(-0.5F, 1.0F, 0.5F),
            new Vector3f(0.5F, 0.0F, -0.5F),
            new Vector3f(0.5F, 0.0F, 0.5F),
            new Vector3f(0.5F, 1.0F, -0.5F),
            new Vector3f(0.5F, 1.0F, 0.5F)
    };

    private final ModelRenderProperties properties;
    private final Matrix4fc transformation;

    private LrDynamicItemModel(ModelRenderProperties properties, Matrix4fc transformation) {
        this.properties = properties;
        this.transformation = new Matrix4f(transformation);
    }

    /** 必须在客户端物品 JSON 解码<b>之前</b>调用。 */
    public static void registerType() {
        ItemModels.ID_MAPPER.put(TYPE_ID, Unbaked.MAP_CODEC);
    }

    @Override
    public void update(ItemStackRenderState state,
                       ItemStack stack,
                       ItemModelResolver resolver,
                       ItemDisplayContext displayContext,
                       ClientLevel level,
                       ItemOwner owner,
                       int seed) {
        state.appendModelIdentityElement(this);

        ItemStackRenderState.LayerRenderState layer = state.newLayer();
        if (stack.hasFoil()) {
            layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
            state.setAnimated();
        }

        RenderArgument argument = new RenderArgument(stack.copy(), displayContext);
        layer.setExtents(EXTENTS);
        layer.setLocalTransform(this.transformation);
        layer.setupSpecialModel(SPECIAL_RENDERER, argument);
        this.properties.applyToLayer(layer, displayContext);

        state.appendModelIdentityElement(identityKeyOf(stack, displayContext));
    }

    /**
     * 生成具备<b>值语义</b>的 identity key。
     *
     * <p>两条硬性要求（违反任一都会导致物品栏图标异常，且症状与原因看不出关联）：
     * <ol>
     *   <li><b>绝不能放入 {@code ItemStack} 本身</b> —— 26.2 的 {@code ItemStack}
     *       没有覆写 {@code equals/hashCode}，走对象身份比较；而上面是 {@code stack.copy()}，
     *       每帧都是新对象 → identity 每帧不等 → atlas 每帧重新分配槽位 →
     *       <b>图标一片空白</b>；</li>
     *   <li><b>不放高频变化但不影响图标的量</b>（如手雷剩余数量）—— 同样会让 identity 每帧失效。</li>
     * </ol>
     *
     * <p>{@code displayId} 用的是 {@link ICustomItem#getDisplayId}，
     * 它正是 display 查询所用的 id（允许「同种手雷不同外观」），
     * 与实际渲染结果一一对应，是这里唯一正确的选择。
     */
    private static Object identityKeyOf(ItemStack stack, ItemDisplayContext displayContext) {
        Identifier contentId = null;
        if (stack.getItem() instanceof ICustomItem customItem) {
            contentId = customItem.getDisplayId(stack);
        }
        return List.of(
                stack.getItem(),
                displayContext,
                contentId == null ? "" : contentId
        );
    }

    public record RenderArgument(ItemStack stack, ItemDisplayContext displayContext) {
    }

    private static final class LrSpecialRenderer implements SpecialModelRenderer<RenderArgument> {
        @Override
        public void submit(RenderArgument argument,
                           PoseStack poseStack,
                           SubmitNodeCollector collector,
                           int light,
                           int overlay,
                           boolean hasFoil,
                           int outlineColor) {
            BuiltinItemRendererRegistry.DynamicItemRenderer renderer =
                    BuiltinItemRendererRegistry.INSTANCE.get(argument.stack().getItem());
            if (renderer != null) {
                renderer.render(argument.stack(), argument.displayContext(), poseStack, collector, light, overlay);
            }
        }

        @Override
        public void getExtents(Consumer<Vector3fc> output) {
            for (Vector3fc extent : EXTENTS.get()) {
                output.accept(extent);
            }
        }

        @Override
        public RenderArgument extractArgument(ItemStack stack) {
            // 真正的 display context 由 update() 经 setupSpecialModel 提供，这里只是兜底
            return new RenderArgument(stack.copy(), ItemDisplayContext.NONE);
        }
    }

    public record Unbaked(Identifier base, Optional<Transformation> transformation) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("base").forGetter(Unbaked::base),
                Transformation.EXTENDED_CODEC.optionalFieldOf("transformation").forGetter(Unbaked::transformation)
        ).apply(instance, Unbaked::new));

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(this.base);
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc inheritedTransform) {
            Matrix4fc composedTransform = Transformation.compose(inheritedTransform, this.transformation);
            ModelBaker baker = context.blockModelBaker();
            ResolvedModel resolved = baker.getModel(this.base);
            TextureSlots slots = resolved.getTopTextureSlots();
            ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, resolved, slots);
            return new LrDynamicItemModel(properties, composedTransform);
        }

        @Override
        public MapCodec<? extends ItemModel.Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
