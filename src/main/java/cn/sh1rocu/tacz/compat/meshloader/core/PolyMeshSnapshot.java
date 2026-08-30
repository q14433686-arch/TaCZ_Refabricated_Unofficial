package cn.sh1rocu.tacz.compat.meshloader.core;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 一次 submit 的 poly_mesh 冻结快照。延迟回调只写顶点。
 *
 * <p>{@code SubmitNodeCollector} 是「先收集、后绘制」的延迟提交：
 * 回调执行时动画系统可能已推进到下一帧，骨骼矩阵必须在 submit 当刻拷贝冻结
 * （与 {@code BedrockRenderSnapshot} 同一理由）。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
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
        // 【2026-08-30 撤回】这里曾按 isInsideScopeLevelRender() 在镜内那一遍早退，
        // 省掉重放时的第二遍 CPU 顶点变换。撤回理由（用户裁决）：
        //   1. GPU 烘焙路线落地后，mesh 枪的主流路径根本不走本方法的 CPU compile，
        //      这个优化保护的成本已经不存在；
        //   2. 它让镜内那一遍与主画面那一遍的内容出现分叉（那一遍缺 poly 部件），
        //      而「孔径内反正被 discard」的论证只覆盖掩码/合成全部正常的情况 ——
        //      多一个行为分叉点就多一类难排查的镜内异常。
        // 仍走 collector 的少数场景（GPU_BAKING=false / 会话降级）恢复为两遍照写：
        // 行为简单、两遍一致，性能损失只在开镜 + PIP 二次渲染的窗口内。
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
