#!/usr/bin/env python3
"""
TaczDynamicItemModel / LrDynamicItemModel: 26.1 -> 1.21.11.

Three verified interface differences (javap on both merged jars):

1. SpecialModelRenderer#submit gains an ItemDisplayContext parameter in 1.21.11:
     26.1.2 : submit(T, PoseStack, SubmitNodeCollector, int, int, boolean, int)
     1.21.11: submit(T, ItemDisplayContext, PoseStack, SubmitNodeCollector, int, int, boolean, int)
   That is strictly better here: the display context no longer has to be smuggled
   through RenderArgument, so we take it from the parameter.

2. ItemModel.Unbaked#bake loses the inherited-transform parameter:
     26.1.2 : bake(BakingContext, Matrix4fc)
     1.21.11: bake(BakingContext)

3. ItemStackRenderState.LayerRenderState#setLocalTransform(Matrix4fc) does not
   exist in 1.21.11 (26.1 added it together with the localTransform field).
   The model's own transformation therefore has to be applied by hand around the
   delegated draw, inside submit(), where we do have the PoseStack.
"""
import re, sys

FILES = {
    'src/main/java/com/tacz/guns/client/renderer/item/TaczDynamicItemModel.java':
        ('TaczDynamicItemModel', 'TaczSpecialRenderer'),
    'src/main/java/me/xjqsh/lrtactical/client/renderer/item/LrDynamicItemModel.java':
        ('LrDynamicItemModel', 'LrSpecialRenderer'),
}


def migrate(path, cls, renderer_cls):
    s = open(path, encoding='utf-8').read()
    orig = s

    # --- 1. RenderArgument carries the model transform instead of setLocalTransform ---
    s = s.replace(
        "    public record RenderArgument(ItemStack stack, ItemDisplayContext displayContext) {\n    }",
        "    /**\n"
        "     * 1.21.11 的 LayerRenderState 没有 setLocalTransform，模型自带的 transformation\n"
        "     * 只能在 submit() 里手动套到 PoseStack 上，因此随参数一起传下去。\n"
        "     * displayContext 在 1.21.11 由 submit 形参直接提供，这里保留字段仅为\n"
        "     * extractArgument 的兜底路径服务。\n"
        "     */\n"
        "    public record RenderArgument(ItemStack stack,\n"
        "                                 ItemDisplayContext displayContext,\n"
        "                                 @Nullable Matrix4fc localTransform) {\n"
        "    }", 1)

    # --- 2. update(): drop setLocalTransform, pass the matrix through the argument ---
    s = s.replace(
        "        RenderArgument argument = new RenderArgument(stack.copy(), displayContext);\n"
        "        layer.setExtents(EXTENTS);\n"
        "        layer.setLocalTransform(this.transformation);\n"
        "        layer.setupSpecialModel(SPECIAL_RENDERER, argument);",
        "        // 1.21.11: layer.setLocalTransform(...) 不存在，改由 submit() 施加（见 RenderArgument）。\n"
        "        RenderArgument argument = new RenderArgument(stack.copy(), displayContext, this.transformation);\n"
        "        layer.setExtents(EXTENTS);\n"
        "        layer.setupSpecialModel(SPECIAL_RENDERER, argument);", 1)

    # --- 3. submit(): new ItemDisplayContext parameter + apply the transform ---
    s = s.replace(
        "        public void submit(RenderArgument argument,\n"
        "                           PoseStack poseStack,\n"
        "                           SubmitNodeCollector collector,\n"
        "                           int light,\n"
        "                           int overlay,\n"
        "                           boolean hasFoil,\n"
        "                           int outlineColor) {\n"
        "            BuiltinItemRendererRegistry.DynamicItemRenderer renderer =\n"
        "                    BuiltinItemRendererRegistry.INSTANCE.get(argument.stack().getItem());\n"
        "            if (renderer != null) {\n"
        "                renderer.render(argument.stack(), argument.displayContext(), poseStack, collector, light, overlay);\n"
        "            }\n"
        "        }",
        "        public void submit(RenderArgument argument,\n"
        "                           // 1.21.11 新增：display context 直接由调用方给出，\n"
        "                           // 不必再依赖 RenderArgument 里 setupSpecialModel 时记下的那份。\n"
        "                           ItemDisplayContext displayContext,\n"
        "                           PoseStack poseStack,\n"
        "                           SubmitNodeCollector collector,\n"
        "                           int light,\n"
        "                           int overlay,\n"
        "                           boolean hasFoil,\n"
        "                           int outlineColor) {\n"
        "            BuiltinItemRendererRegistry.DynamicItemRenderer renderer =\n"
        "                    BuiltinItemRendererRegistry.INSTANCE.get(argument.stack().getItem());\n"
        "            if (renderer == null) {\n"
        "                return;\n"
        "            }\n"
        "            Matrix4fc localTransform = argument.localTransform();\n"
        "            if (localTransform == null) {\n"
        "                renderer.render(argument.stack(), displayContext, poseStack, collector, light, overlay);\n"
        "                return;\n"
        "            }\n"
        "            // 代替 26.1 的 LayerRenderState#setLocalTransform。\n"
        "            poseStack.pushPose();\n"
        "            poseStack.last().pose().mul(localTransform);\n"
        "            try {\n"
        "                renderer.render(argument.stack(), displayContext, poseStack, collector, light, overlay);\n"
        "            } finally {\n"
        "                poseStack.popPose();\n"
        "            }\n"
        "        }", 1)

    # --- 4. extractArgument fallback gains the null transform ---
    s = re.sub(r'return new RenderArgument\(stack\.copy\(\), ItemDisplayContext\.NONE\);',
               'return new RenderArgument(stack.copy(), ItemDisplayContext.NONE, null);', s)

    # --- 5. bake() loses the Matrix4fc parameter ---
    s = s.replace(
        "        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc inheritedTransform) {\n"
        "            Matrix4fc composedTransform = Transformation.compose(inheritedTransform, this.transformation);",
        "        // 1.21.11: ItemModel.Unbaked#bake 没有 inheritedTransform 形参（26.1 才加的），\n"
        "        // 因此这里只用本模型自己声明的 transformation。\n"
        "        public ItemModel bake(ItemModel.BakingContext context) {\n"
        "            Matrix4fc composedTransform = this.transformation\n"
        "                    .map(Transformation::getMatrix)\n"
        "                    .orElse(new Matrix4f());", 1)

    # --- imports ---
    if 'import org.joml.Matrix4f;' not in s:
        s = s.replace('import org.joml.Matrix4fc;', 'import org.joml.Matrix4f;\nimport org.joml.Matrix4fc;', 1)
    if 'import org.jetbrains.annotations.Nullable;' not in s:
        s = s.replace('import org.joml.Matrix4f;', 'import org.jetbrains.annotations.Nullable;\nimport org.joml.Matrix4f;', 1)

    if s == orig:
        print(f'!! no change: {path}')
        return False
    open(path, 'w', encoding='utf-8').write(s)
    print(f'migrated {path}')
    return True


if __name__ == '__main__':
    for p, (c, r) in FILES.items():
        migrate(p, c, r)
