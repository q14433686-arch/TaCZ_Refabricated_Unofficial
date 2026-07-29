package com.tacz.guns.client.renderer.item;

import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.GunMod;
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

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Compile-level prototype for the 26.2 item model bridge.
 *
 * <p>This replaces the removed BuiltinItemRendererRegistry integration with a
 * custom ItemModel type. ItemModel.update still has the ItemDisplayContext, so
 * it can freeze both the stack and context into an immutable argument before
 * the delayed SpecialModelRenderer submit phase.</p>
 *
 * <p>Do not treat this class as a complete rendering migration. The existing
 * renderers still need to stop calling the deprecated no-op BedrockModel.render
 * path and must snapshot mutable Bedrock model state before deferred submission.</p>
 */
public final class TaczDynamicItemModel implements ItemModel {
    public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "dynamic_item");

    private static final TaczSpecialRenderer SPECIAL_RENDERER = new TaczSpecialRenderer();

    /**
     * 模型包围盒的角点，供 {@code ItemStackRenderState#visitExtents} 计算
     * {@code getModelBoundingBox()}。
     *
     * <p><b>为什么是 0.5 而不是 1.5</b></p>
     *
     * <p>26.2 的 GUI 物品渲染有两条路径，由 {@code GuiItemRenderState} 构造时决定：</p>
     * <pre>
     * oversizedItemBounds = itemStackRenderState.isOversizedInGui()
     *         ? calculateOversizedItemBounds() : null;
     * </pre>
     * 而 {@code calculateOversizedItemBounds()} 的判定是：
     * <pre>
     * AABB aabb = itemStackRenderState.getModelBoundingBox();   // 来自 visitExtents
     * int actualXSize = Mth.ceil(aabb.getXsize() * 16.0);
     * int actualYSize = Mth.ceil(aabb.getYsize() * 16.0);
     * if (actualXSize &lt;= 16 &amp;&amp; actualYSize &lt;= 16) return null;  // 走普通 GuiItemAtlas
     * else ... // 走 OversizedItemRenderer(PIP，离屏 RT)
     * </pre>
     *
     * <p>原先写死 ±1.5 →  包围盒边长 3.0 →  {@code 3.0 * 16 = 48 px} ≫ 16，
     * 于是所有 TACZ 物品都被判定为 "oversized"，强制走 {@code OversizedItemRenderer}
     * 这条 picture-in-picture 离屏渲染路径。该路径按 48px 的包围盒去布局和裁剪，
     * 而 TACZ 的 slot 贴图实际只有 1 格（16px），最终在 16×16 的槽位里被缩放/偏移到
     * 看不见的位置 —— 表现就是<b>工作台界面、物品栏里图标全是空白</b>。
     *
     * <p>改为 ±0.5（边长 1.0 →  正好 16 px）后判定为非 oversized，
     * 走与原版物品一致的 {@code GuiItemAtlas} 路径。这也与 TACZ 自身的
     * {@code renderSlotTexture} 语义吻合：它画的就是一个 1×1 格的四边形。</p>
     *
     * <p>注意 {@code items/*.json} 里的 {@code "oversized_in_gui": true} 只是允许
     * 超框绘制，真正决定走哪条路径的是这里的包围盒尺寸。</p>
     *
     * <h2>为什么 Y 是 [0, 1] 而不是对称的 [-0.5, +0.5]</h2>
     *
     * <p>这里同时受<b>两条互相冲突</b>的 26.2 约束，必须同时满足：</p>
     *
     * <p><b>约束一（GUI）</b>：如上所述，{@code calculateOversizedItemBounds} 按
     * {@code ceil(getXsize()*16) &gt; 16 || ceil(getYsize()*16) &gt; 16} 判定 oversized。
     * 因此包围盒每条<b>边长</b>都必须 ≤ 1.0。</p>
     *
     * <p><b>约束二（掉落物）</b>：{@code ItemEntityRenderer#submit} 里有
     * <pre>
     * AABB aabb = state.item.getModelBoundingBox();
     * poseStack.translate(0, -aabb.minY() * ... + 0.0625F, 0);
     * </pre>
     * 也就是掉落物的抬升高度<b>直接由 {@code minY} 决定</b>：
     * vanilla 用它把模型底面顶到地面上方 1/16 格。</p>
     *
     * <p>原先 Y 取 ±0.5 时 {@code minY = -0.5}，于是每个 TACZ 掉落物都被
     * <b>无条件额外抬高 0.5 格（8 像素）</b>，与模型实际大小无关 ——
     * 表现就是地上的枪、弹药盒等全都浮得过高。1.21.1 的
     * {@code ItemEntityRenderer} 没有这个基于包围盒的补偿（它用固定的
     * {@code 0.25F} 之类常量），所以这是一个纯粹由跨版本机制变化引入的回归，
     * 而不是哪个数值被写错了。</p>
     *
     * <p>把 Y 改成 {@code [0, 1]} 后：边长仍是 1.0（约束一继续满足，GUI 不变），
     * 而 {@code minY = 0} 让抬升量回落到 vanilla 的 {@code 0.0625}（约束二满足）。
     * XZ 保持 ±0.5 不动 —— 它们只参与约束一，且旋转时以模型原点为中心，
     * 对称范围才是对的。</p>
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

    private TaczDynamicItemModel(ModelRenderProperties properties, Matrix4fc transformation) {
        this.properties = properties;
        this.transformation = new Matrix4f(transformation);
    }

    /** Must run during client initialization, before client item JSON files are decoded. */
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

        // 26.2 修复：物品栏图标空白的根因。
        //
        // GuiItemAtlas#getOrUpdate 用 TrackingItemStackRenderState#getModelIdentity()（即
        // modelIdentityElements 这个 List）作为 key 去 DynamicAtlasAllocator 里分配/复用图标槽位，
        // 靠的是 List.equals -> 逐元素 equals。
        //
        // 原先这里直接把 RenderArgument 塞进 identity。record 的 equals 会逐字段比较，而
        // ItemStack 在 26.2 中<b>没有覆写 equals/hashCode</b>（javap 已确认，只有静态的
        // ItemStack.matches），走的是对象身份比较；上面又是 stack.copy() —— 每帧都是新对象。
        // 结果 identity 每帧都不相等：atlas 认为这是一个全新物品，不断重新分配槽位、
        // 反复 clear/重画，并很快耗尽/抖动，最终表现为<b>物品栏图标一片空白</b>。
        //
        // 正确做法是只把"真正影响外观"的、具备值语义的量放进 identity：
        //   - 物品本身（Item 是单例，可安全比较）
        //   - display context
        //   - 决定 TACZ 外观的 gun/attachment/ammo id 与关键组件
        // 这样同一把枪在相邻帧能命中同一槽位，图标得以正常绘制。
        state.appendModelIdentityElement(identityKeyOf(stack, displayContext));
    }

    /**
     * 生成具备<b>值语义</b>的 identity key（可安全参与 List.equals）。
     *
     * <p>只包含影响图标外观的信息；刻意<b>不</b>包含 ItemStack 本身（无 equals）与弹药数等
     * 高频变化字段 —— 后者不影响 GUI 图标（GUI 走的是 slot 贴图），若纳入会再次导致每帧失效。</p>
     */
    private static Object identityKeyOf(ItemStack stack, ItemDisplayContext displayContext) {
        Identifier contentId = null;
        var item = stack.getItem();
        if (item instanceof com.tacz.guns.api.item.IGun iGun) {
            contentId = iGun.getGunId(stack);
        } else if (item instanceof com.tacz.guns.api.item.IAttachment iAttachment) {
            contentId = iAttachment.getAttachmentId(stack);
        } else if (item instanceof com.tacz.guns.api.item.IAmmo iAmmo) {
            contentId = iAmmo.getAmmoId(stack);
        } else if (item instanceof com.tacz.guns.api.item.nbt.BlockItemDataAccessor accessor) {
            contentId = accessor.getBlockId(stack);
        }
        return java.util.List.of(
                item,
                displayContext,
                contentId == null ? "" : contentId
        );
    }

    public record RenderArgument(ItemStack stack, ItemDisplayContext displayContext) {
    }

    private static final class TaczSpecialRenderer implements SpecialModelRenderer<RenderArgument> {
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
            // The custom ItemModel supplies the real display context via setupSpecialModel.
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
            return new TaczDynamicItemModel(properties, composedTransform);
        }

        @Override
        public MapCodec<? extends ItemModel.Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
