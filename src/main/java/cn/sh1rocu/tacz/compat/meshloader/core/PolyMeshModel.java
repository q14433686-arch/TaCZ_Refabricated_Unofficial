package cn.sh1rocu.tacz.compat.meshloader.core;

import cn.sh1rocu.tacz.compat.meshloader.api.IPolyMeshBone;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * poly_mesh 模型：骨骼树 + 每骨骼网格列表 + 渲染快照采集。
 *
 * <p>26.2 版不直接渲染：{@link #capture} 在 submit 时把每根骨骼的矩阵、
 * 可见性与光照冻结成 {@link PolyMeshSnapshot}，延迟回调只写顶点 ——
 * 与移植版 {@code BedrockRenderSnapshot} 完全同构，从架构上避免
 * 「延迟回调读共享 BedrockPart 被后续动画改写」的竞态。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)，
 * 删除了 VBO 缓存/ShaderStateTracker/GUI 检测等 26.2 不再需要的机制。</p>
 */
@Environment(EnvType.CLIENT)
public class PolyMeshModel {

    /** 满亮度（发光骨骼使用）。 */
    public static final int FULL_BRIGHT = 15728880;

    private final IPolyMeshBone root;
    private final Map<String, List<PolyMesh>> meshMap = new HashMap<>();
    private final Set<String> translucentBones = new HashSet<>();
    private final boolean hasTranslucent;
    /** 网格骨头的全部祖先（含自身），用于渲染时快速剪枝。 */
    private final Set<String> meshAncestorBones = new HashSet<>();
    /** poly_mesh 且需要满亮度绘制的骨骼名（含祖先 illuminated 传播）。 */
    private final Set<String> illuminatedBones = new HashSet<>();
    /** 从主渲染中排除的子树的根骨骼名（additional_magazine 用）。 */
    private String excludeSubtreeRoot = null;
    /** excludeSubtreeRoot 下的全部骨骼名缓存。 */
    private final Set<String> excludedBones = new HashSet<>();

    public PolyMeshModel(IPolyMeshBone root, JsonObject rawJson) {
        this.root = root;
        parsePolyMeshes(rawJson);
        for (String name : meshMap.keySet()) {
            if (name.toLowerCase().contains("translucent")) {
                translucentBones.add(name);
            }
        }
        this.hasTranslucent = !translucentBones.isEmpty();
        buildMeshAncestors(this.root, new ArrayDeque<>());
        buildIlluminatedBones(this.root, false);
    }

    public boolean hasTranslucentMeshes() {
        return hasTranslucent;
    }

    // =========================================================================
    // 树构建
    // =========================================================================

    private boolean buildMeshAncestors(IPolyMeshBone bone, Deque<String> path) {
        String name = bone.getName();
        path.addLast(name);
        boolean has = meshMap.containsKey(name);
        for (IPolyMeshBone child : bone.getChildren()) {
            if (buildMeshAncestors(child, path)) has = true;
        }
        if (has) {
            meshAncestorBones.addAll(path);
        }
        path.removeLast();
        return has;
    }

    /** illuminated 标记从祖先向子节点传播，并把「带 mesh 且 illuminated」的骨骼登记进 illuminatedBones。 */
    private void buildIlluminatedBones(IPolyMeshBone bone, boolean parentIlluminated) {
        boolean illuminated = parentIlluminated || bone.isIlluminated();
        if (illuminated && meshMap.containsKey(bone.getName())) {
            illuminatedBones.add(bone.getName());
        }
        for (IPolyMeshBone child : bone.getChildren()) {
            buildIlluminatedBones(child, illuminated);
        }
    }

    private void parsePolyMeshes(JsonObject rawJson) {
        JsonArray geometries = rawJson.has("minecraft:geometry") ? rawJson.getAsJsonArray("minecraft:geometry") : null;
        if (geometries == null || geometries.isEmpty()) return;
        JsonObject geo = geometries.get(0).getAsJsonObject();
        if (!geo.has("description") || !geo.getAsJsonObject("description").has("texture_width")) return;
        float texW = geo.getAsJsonObject("description").get("texture_width").getAsFloat();
        float texH = geo.getAsJsonObject("description").get("texture_height").getAsFloat();
        JsonArray bones = geo.getAsJsonArray("bones");
        if (bones == null) return;
        for (JsonElement boneElem : bones) {
            JsonObject boneObj = boneElem.getAsJsonObject();
            if (!boneObj.has("poly_mesh") || !boneObj.has("name")) continue;
            String name = boneObj.get("name").getAsString();
            float pX = 0, pY = 0, pZ = 0;
            if (boneObj.has("pivot")) {
                JsonArray p = boneObj.getAsJsonArray("pivot");
                pX = p.get(0).getAsFloat();
                pY = p.get(1).getAsFloat();
                pZ = p.get(2).getAsFloat();
            }
            PolyMesh mesh = new PolyMesh(boneObj.getAsJsonObject("poly_mesh"), texW, texH, new float[]{pX, pY, pZ});
            if (mesh.getVertexCount() > 0) {
                meshMap.computeIfAbsent(name, k -> new ArrayList<>()).add(mesh);
            }
        }
    }

    // =========================================================================
    // 快照采集（26.2 submit 路径）
    // =========================================================================

    /**
     * 从整棵树采集快照。排除规则（{@link #setExcludeSubtree}）生效。
     * 应在 submit 时调用：此刻骨骼变换是当帧动画后的实时值。
     */
    public PolyMeshSnapshot capture(PoseStack rootPose, int light) {
        return capture(rootPose, light, null);
    }

    /**
     * 带骨骼过滤的采集：{@code skipBones} 命中的骨骼不写入快照
     * （GPU 烘焙路径下由 {@code PolyMeshGpuRenderer} 负责绘制），
     * 其余骨骼照常。用于 GPU 路径下把 translucent 骨骼留在 consumer、
     * cutout 骨骼交给 GPU 的分流。
     */
    public PolyMeshSnapshot capture(PoseStack rootPose, int light, java.util.function.Predicate<String> skipBones) {
        List<PolyMeshSnapshot.Command> cutout = new ArrayList<>();
        List<PolyMeshSnapshot.Command> translucent = new ArrayList<>();
        captureBone(root, rootPose, light, true, skipBones, cutout, translucent);
        return new PolyMeshSnapshot(cutout, translucent);
    }

    /**
     * 从指定骨骼采集子树快照（additional_magazine 镜像副本 pass 用）。
     * 不受排除规则影响（该子树正是被排除的那棵）。
     *
     * @param mirrorRoot 是否为镜像模式。与移植版 {@code IMirrorGeometry} 的
     *                   {@code captureGeometry} 行为一致：
     *                   <ul>
     *                   <li>{@code true}：<b>不</b>套用根骨骼自身的变换
     *                   （调用方应已把 additional_magazine 的完整变换压进
     *                   {@code rootPose}），但根骨骼自身的网格照画、子树正常
     *                   递归 —— 这样 magazine 副本不会跟着换弹动画跑；</li>
     *                   <li>{@code false}：正常套用根骨骼变换。</li>
     *                   </ul>
     */
    public PolyMeshSnapshot captureSubtree(String rootBoneName, PoseStack rootPose, int light, boolean mirrorRoot) {
        List<PolyMeshSnapshot.Command> cutout = new ArrayList<>();
        List<PolyMeshSnapshot.Command> translucent = new ArrayList<>();
        IPolyMeshBone bone = findBone(this.root, rootBoneName);
        if (bone == null) {
            return new PolyMeshSnapshot(cutout, translucent);
        }
        if (mirrorRoot) {
            captureBoneMirrored(bone, rootPose, light, cutout, translucent);
        } else {
            captureBone(bone, rootPose, light, false, null, cutout, translucent);
        }
        return new PolyMeshSnapshot(cutout, translucent);
    }

    /** 镜像模式：根骨骼变换不套用（已由调用方压入），根网格照画，子树正常递归。 */
    private void captureBoneMirrored(IPolyMeshBone bone, PoseStack poseStack, int light,
                                     List<PolyMeshSnapshot.Command> cutout, List<PolyMeshSnapshot.Command> translucent) {
        if (!bone.isVisible()) return;
        if (!meshAncestorBones.contains(bone.getName())) return;
        drawBoneMeshes(bone, poseStack, light, cutout, translucent);
        for (IPolyMeshBone child : bone.getChildren()) {
            captureBone(child, poseStack, light, false, null, cutout, translucent);
        }
    }

    private void captureBone(IPolyMeshBone bone, PoseStack poseStack, int light, boolean checkExcluded,
                             java.util.function.Predicate<String> skipBones,
                             List<PolyMeshSnapshot.Command> cutout, List<PolyMeshSnapshot.Command> translucent) {
        if (!bone.isVisible()) return;
        if (!meshAncestorBones.contains(bone.getName())) return;
        if (checkExcluded && !excludedBones.isEmpty() && excludedBones.contains(bone.getName())) return;
        if (skipBones != null && skipBones.test(bone.getName())) return;

        poseStack.pushPose();
        bone.applyTransform(poseStack);
        drawBoneMeshes(bone, poseStack, light, cutout, translucent);
        for (IPolyMeshBone child : bone.getChildren()) {
            captureBone(child, poseStack, light, checkExcluded, skipBones, cutout, translucent);
        }
        poseStack.popPose();
    }

    /**
     * 遍历骨骼树（GPU 烘焙路径的矩阵收集用）。
     * 回调在骨骼变换已压入 {@code poseStack} 后触发；
     * 返回 false 可剪掉整棵子树。
     */
    public void visitBones(PoseStack poseStack, int light,
                           java.util.function.Predicate<String> skipBones,
                           java.util.function.BiPredicate<String, PoseStack> visitor) {
        visitBone(root, poseStack, light, true, skipBones, visitor);
    }

    private boolean visitBone(IPolyMeshBone bone, PoseStack poseStack, int light, boolean checkExcluded,
                              java.util.function.Predicate<String> skipBones,
                              java.util.function.BiPredicate<String, PoseStack> visitor) {
        if (!bone.isVisible()) return false;
        if (!meshAncestorBones.contains(bone.getName())) return false;
        if (checkExcluded && !excludedBones.isEmpty() && excludedBones.contains(bone.getName())) return false;
        if (skipBones != null && skipBones.test(bone.getName())) return false;

        poseStack.pushPose();
        bone.applyTransform(poseStack);
        boolean descend = visitor.test(bone.getName(), poseStack);
        if (descend) {
            for (IPolyMeshBone child : bone.getChildren()) {
                visitBone(child, poseStack, light, checkExcluded, skipBones, visitor);
            }
        }
        poseStack.popPose();
        return true;
    }

    /** 把当前骨骼自身的网格按当前矩阵采集为命令（不含子骨骼）。 */
    private void drawBoneMeshes(IPolyMeshBone bone, PoseStack poseStack, int light,
                                List<PolyMeshSnapshot.Command> cutout, List<PolyMeshSnapshot.Command> translucent) {
        List<PolyMesh> meshes = meshMap.get(bone.getName());
        if (meshes == null || meshes.isEmpty()) {
            return;
        }
        int actualLight = (bone.isIlluminated() || illuminatedBones.contains(bone.getName())) ? FULL_BRIGHT : light;
        PolyMeshSnapshot.Command command = new PolyMeshSnapshot.Command(
                new Matrix4f(poseStack.last().pose()),
                new Matrix3f(poseStack.last().normal()),
                meshes,
                actualLight);
        if (translucentBones.contains(bone.getName())) {
            translucent.add(command);
        } else {
            cutout.add(command);
        }
    }

    // =========================================================================
    // 排除控制（additional_magazine）
    // =========================================================================

    /** 把指定骨骼及其子树从主渲染中排除。 */
    public void setExcludeSubtree(String rootBoneName) {
        if (rootBoneName.equals(excludeSubtreeRoot)) return;
        excludeSubtreeRoot = rootBoneName;
        excludedBones.clear();
        IPolyMeshBone bone = findBone(this.root, rootBoneName);
        if (bone != null) {
            collectSubtreeBones(bone, excludedBones);
        }
    }

    public void clearExcludeSubtree() {
        excludeSubtreeRoot = null;
        excludedBones.clear();
    }

    private void collectSubtreeBones(IPolyMeshBone bone, Set<String> result) {
        result.add(bone.getName());
        for (IPolyMeshBone child : bone.getChildren()) {
            collectSubtreeBones(child, result);
        }
    }

    // =========================================================================
    // 查询
    // =========================================================================

    /** 骨骼名 → 网格列表（GPU 烘焙遍历用）。 */
    public Map<String, List<PolyMesh>> getMeshMap() {
        return meshMap;
    }

    /** 该骨骼是否半透明（骨骼名含 "translucent"）。 */
    public boolean isTranslucentBone(String boneName) {
        return translucentBones.contains(boneName);
    }

    /** 全部 poly 网格的顶点总数（含半透明/发光），用于加载统计与性能排查。 */
    public int getTotalVertexCount() {
        int total = 0;
        for (List<PolyMesh> meshes : meshMap.values()) {
            for (PolyMesh mesh : meshes) {
                total += mesh.getVertexCount();
            }
        }
        return total;
    }

    /** 带 poly_mesh 的骨骼数。 */
    public int getMeshBoneCount() {
        return meshMap.size();
    }

    /** 半透明骨骼数（骨骼名含 "translucent"）。 */
    public int getTranslucentBoneCount() {
        return translucentBones.size();
    }

    /** 发光骨骼数（自身或祖先 illuminated）。 */
    public int getIlluminatedBoneCount() {
        return illuminatedBones.size();
    }

    /** 指定骨骼（或其子树）是否带 poly_mesh。 */
    public boolean hasMeshInSubtree(String boneName) {
        IPolyMeshBone bone = findBone(this.root, boneName);
        if (bone == null) return false;
        return hasMeshInSubtreeInternal(bone);
    }

    private boolean hasMeshInSubtreeInternal(IPolyMeshBone bone) {
        if (meshMap.containsKey(bone.getName())) return true;
        for (IPolyMeshBone child : bone.getChildren()) {
            if (hasMeshInSubtreeInternal(child)) return true;
        }
        return false;
    }

    private IPolyMeshBone findBone(IPolyMeshBone bone, String name) {
        if (name.equals(bone.getName())) return bone;
        for (IPolyMeshBone child : bone.getChildren()) {
            IPolyMeshBone found = findBone(child, name);
            if (found != null) return found;
        }
        return null;
    }
}
