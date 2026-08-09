package cn.sh1rocu.tacz.compat.meshloader.core;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 一次 submit 的 poly_mesh 冻结快照。
 *
 * <p>每根骨骼的模型矩阵/法线矩阵/光照在采集时（submit 阶段）冻结成
 * 不可变 {@link Command}，延迟回调只写顶点 —— 与移植版
 * {@code BedrockRenderSnapshot} 相同的 26.2 延迟提交模式，
 * 避免回调里读共享 {@code BedrockPart} 被 {@code cleanAnimationTransform()}
 * 或其它实体提交改写的竞态。</p>
 *
 * <p>命令列表在构造后不再被外部修改（{@code PolyMeshModel} 内部新建即交），
 * 因此直接持有引用而不做防御拷贝，减少每帧分配。</p>
 */
public final class PolyMeshSnapshot {

    public record Command(Matrix4f pose, Matrix3f normal, List<PolyMesh> meshes, int light) {
        public Command {
            // 防御拷贝：采集时 poseStack 后续会被 popPose/复用改写
            pose = new Matrix4f(pose);
            normal = new Matrix3f(normal);
        }
    }

    private final List<Command> cutoutCommands;
    private final List<Command> translucentCommands;

    PolyMeshSnapshot(List<Command> cutoutCommands, List<Command> translucentCommands) {
        this.cutoutCommands = cutoutCommands;
        this.translucentCommands = translucentCommands;
    }

    public boolean isEmpty() {
        return cutoutCommands.isEmpty() && translucentCommands.isEmpty();
    }

    public boolean hasTranslucent() {
        return !translucentCommands.isEmpty();
    }

    /** 把不透明网格写入 consumer（在 collector 的延迟回调中调用）。 */
    public void writeCutout(VertexConsumer consumer, int overlay) {
        write(cutoutCommands, consumer, overlay, 1f, 1f, 1f, 1f);
    }

    /** 把不透明网格以指定颜色写入 consumer（弹药染色/曳光弹用）。 */
    public void writeCutout(VertexConsumer consumer, int overlay, float red, float green, float blue, float alpha) {
        write(cutoutCommands, consumer, overlay, red, green, blue, alpha);
    }

    /** 把半透明网格写入 consumer（在 collector 的延迟回调中调用）。 */
    public void writeTranslucent(VertexConsumer consumer, int overlay) {
        write(translucentCommands, consumer, overlay, 1f, 1f, 1f, 1f);
    }

    /** 把半透明网格以指定颜色写入 consumer。 */
    public void writeTranslucent(VertexConsumer consumer, int overlay, float red, float green, float blue, float alpha) {
        write(translucentCommands, consumer, overlay, red, green, blue, alpha);
    }

    private void write(List<Command> commands, VertexConsumer consumer, int overlay,
                       float red, float green, float blue, float alpha) {
        PoseStack scratch = new PoseStack();
        for (Command command : commands) {
            PoseStack.Pose pose = scratch.last();
            pose.pose().set(command.pose());
            pose.normal().set(command.normal());
            for (PolyMesh mesh : command.meshes()) {
                mesh.compile(pose, consumer, command.light(), overlay, red, green, blue, alpha);
            }
        }
    }
}
