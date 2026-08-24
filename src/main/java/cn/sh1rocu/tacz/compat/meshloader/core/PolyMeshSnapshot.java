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
 * {@code BedrockRenderSnapshot} 相同的 26.2 延迟提交模式。</p>
 */
public final class PolyMeshSnapshot {

    public record Command(Matrix4f pose, Matrix3f normal, List<PolyMesh> meshes, int light) {
        public Command {
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

    public void writeCutout(VertexConsumer consumer, int overlay) {
        write(cutoutCommands, consumer, overlay, 1f, 1f, 1f, 1f);
    }

    public void writeCutout(VertexConsumer consumer, int overlay, float red, float green, float blue, float alpha) {
        write(cutoutCommands, consumer, overlay, red, green, blue, alpha);
    }

    public void writeTranslucent(VertexConsumer consumer, int overlay) {
        write(translucentCommands, consumer, overlay, 1f, 1f, 1f, 1f);
    }

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
