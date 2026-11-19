package com.tacz.guns.client.renderer.snapshot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.FunctionalBedrockPart;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.IFunctionalSubmitter;
import com.tacz.guns.client.model.IMirrorGeometry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.bedrock.UnshadedVertices;
import com.tacz.guns.util.IlluminatedLights;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable per-submit snapshot of a mutable BedrockModel.
 *
 * <p>The old renderer could mutate a shared model and immediately upload its vertices. In 26.2
 * geometry is consumed after extraction, so retaining BedrockPart references would allow later
 * cleanAnimationTransform() calls or another entity submission to overwrite this frame's pose.
 * This class resolves every part matrix during extraction and retains only immutable matrices and
 * static cube geometry for the delayed custom-geometry callback.</p>
 */
public final class BedrockRenderSnapshot {
    private final List<DrawCommand> drawCommands;
    private final List<IFunctionalSubmitter.SubmitTask> functionalTasks;
    private final int skippedFunctionalNodeCount;

    private BedrockRenderSnapshot(List<DrawCommand> drawCommands,
                                  List<IFunctionalSubmitter.SubmitTask> functionalTasks,
                                  int skippedFunctionalNodeCount) {
        this.drawCommands = List.copyOf(drawCommands);
        this.functionalTasks = List.copyOf(functionalTasks);
        this.skippedFunctionalNodeCount = skippedFunctionalNodeCount;
    }

    public static BedrockRenderSnapshot capture(BedrockModel model,
                                                 PoseStack rootPose,
                                                 ItemDisplayContext displayContext,
                                                 int light,
                                                 int overlay,
                                                 float red,
                                                 float green,
                                                 float blue,
                                                 float alpha) {
        PoseStack working = copyOf(rootPose);
        Builder builder = new Builder(displayContext, overlay, red, green, blue, alpha);
        for (BedrockPart part : model.getShouldRender()) {
            builder.capturePart(part, working, light, false);
        }
        return new BedrockRenderSnapshot(builder.commands, builder.functionalTasks, builder.skippedFunctionalNodes);
    }

    /**
     * 以单个节点为根做一次几何快照（不遍历整个模型的 shouldRender 列表）。
     *
     * <p>用于瞄具的 ocular / division / scope_body 等<b>不在主渲染列表里</b>、
     * 需要单独按顺序绘制的部件（见 {@code BedrockAttachmentModel#submitTempPart}）。</p>
     *
     * <p>注意：{@code rootPose} 应当<b>已经</b>套用了该节点自身及其父级链的变换，
     * 因此这里不再对根节点重复套用，只在递归子节点时套用。</p>
     */
    public static BedrockRenderSnapshot captureSubtree(BedrockPart root,
                                                       PoseStack rootPose,
                                                       ItemDisplayContext displayContext,
                                                       int light,
                                                       int overlay,
                                                       float red,
                                                       float green,
                                                       float blue,
                                                       float alpha) {
        PoseStack working = copyOf(rootPose);
        Builder builder = new Builder(displayContext, overlay, red, green, blue, alpha);
        boolean glowing = false;
        for (BedrockPart p = root; p != null; p = p.getParent()) glowing |= p.illuminated;
        builder.captureGeometry(root, working, glowing ? IlluminatedLights.resolve(light) : light, glowing);
        return new BedrockRenderSnapshot(builder.commands, builder.functionalTasks, builder.skippedFunctionalNodes);
    }

    public boolean isEmpty() {
        return this.drawCommands.isEmpty();
    }

    /** Number of old functional nodes intentionally deferred to the A3 collector migration. */
    public int skippedFunctionalNodeCount() {
        return this.skippedFunctionalNodeCount;
    }

    public void submitFunctionalTasks(SubmitNodeCollector collector) {
        for (IFunctionalSubmitter.SubmitTask task : this.functionalTasks) {
            task.submit(collector);
        }
    }

    public void write(VertexConsumer consumer) {
        PoseStack poseStack = new PoseStack();
        VertexConsumer unshaded = null;
        for (DrawCommand command : this.drawCommands) {
            PoseStack.Pose pose = poseStack.last();
            pose.pose().set(command.pose());
            pose.normal().set(command.normal());
            if (command.unshaded() && unshaded == null) unshaded = UnshadedVertices.wrap(consumer);
            for (BedrockCube cube : command.cubes()) {
                cube.compile(pose, command.unshaded() ? unshaded : consumer, command.light(), command.overlay(),
                        command.red(), command.green(), command.blue(), command.alpha());
            }
        }
    }

    private static PoseStack copyOf(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }

    public record DrawCommand(Matrix4f pose,
                              Matrix3f normal,
                              List<BedrockCube> cubes,
                              int light,
                              int overlay,
                              float red,
                              float green,
                              float blue,
                              float alpha,
                              boolean unshaded) {
        public DrawCommand(Matrix4f pose, Matrix3f normal, List<BedrockCube> cubes, int light, int overlay,
                           float red, float green, float blue, float alpha) {
            this(pose, normal, cubes, light, overlay, red, green, blue, alpha, false);
        }
        public DrawCommand {
            pose = new Matrix4f(pose);
            normal = new Matrix3f(normal);
            cubes = List.copyOf(cubes);
        }
    }

    private static final class Builder {
        private final ItemDisplayContext displayContext;
        private final int overlay;
        private final float red;
        private final float green;
        private final float blue;
        private final float alpha;
        private final List<DrawCommand> commands = new ArrayList<>();
        private final List<IFunctionalSubmitter.SubmitTask> functionalTasks = new ArrayList<>();
        private int skippedFunctionalNodes;

        private Builder(ItemDisplayContext displayContext,
                        int overlay,
                        float red,
                        float green,
                        float blue,
                        float alpha) {
            this.displayContext = displayContext;
            this.overlay = overlay;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
        }

        /**
         * 在<b>当前矩阵</b>下采集 part 自身与其子树的几何。
         * 与 capturePart 的区别：不对 part 自身再套一次 translateAndRotateAndScale
         * （调用点已经套过），仅对子节点递归时套用。
         */
        private void captureGeometry(BedrockPart part, PoseStack poseStack, int partLight, boolean unshaded) {
            if (!part.visible) {
                return;
            }
            if (!part.cubes.isEmpty()) {
                PoseStack.Pose current = poseStack.last();
                this.commands.add(new DrawCommand(
                        current.pose(), current.normal(), part.cubes,
                        partLight, this.overlay, this.red, this.green, this.blue, this.alpha, unshaded));
            }
            for (BedrockPart child : part.children) {
                capturePart(child, poseStack, partLight, unshaded);
            }
        }

        private void capturePart(BedrockPart part, PoseStack poseStack, int inheritedLight, boolean inheritedUnshaded) {
            boolean unshaded = inheritedUnshaded || part.illuminated;
            int partLight = part.illuminated ? IlluminatedLights.resolve(inheritedLight) : inheritedLight;

            // FunctionalBedrockPart always evaluates its provider, even if visible is false. Providers
            // returning null are visibility/state hooks and can be snapshotted normally. Providers
            // returning a renderer need a collector-aware A3 implementation and are not executed here.
            IFunctionalRenderer legacyFunctionalRenderer = null;
            if (part instanceof FunctionalBedrockPart functional && functional.functionalRenderer != null) {
                legacyFunctionalRenderer = functional.functionalRenderer.apply(part);
            }

            poseStack.pushPose();
            part.translateAndRotateAndScale(poseStack);

            if (legacyFunctionalRenderer != null) {
                if (legacyFunctionalRenderer instanceof IFunctionalSubmitter submitter) {
                    submitter.extract(new IFunctionalSubmitter.ExtractionContext(
                            poseStack,
                            this.displayContext,
                            partLight,
                            this.overlay,
                            this.functionalTasks::add
                    ));
                    poseStack.popPose();
                    return;
                } else if (legacyFunctionalRenderer instanceof IMirrorGeometry mirror) {
                    // 在本节点的变换下，先画自己（含子树），再把被镜像的节点也画一遍。
                    // 用于 additional_magazine：枪身上那一个弹匣与跟手的那一个共用同一份网格。
                    // 走这条路径可与枪身共用 RenderType / DrawCommand 批次，保证材质与顺序正确。
                    if (part.visible) {
                        captureGeometry(part, poseStack, partLight, unshaded);
                        BedrockPart mirrored = mirror.getMirroredPart();
                        if (mirrored != null && mirrored.visible) {
                            captureGeometry(mirrored, poseStack,
                                    mirrored.illuminated ? IlluminatedLights.resolve(partLight) : partLight,
                                    unshaded || mirrored.illuminated);
                        }
                    }
                    poseStack.popPose();
                    return;
                } else {
                    this.skippedFunctionalNodes++;
                    poseStack.popPose();
                    return;
                }
            }

            captureGeometry(part, poseStack, partLight, unshaded);
            poseStack.popPose();
        }
    }
}
