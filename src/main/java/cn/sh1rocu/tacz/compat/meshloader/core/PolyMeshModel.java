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
 * poly_mesh 模型：骨骼树 + 每骨骼网格 + 快照采集。
 *
 * <p>骨骼树遍历只下潜「子树里有网格」的分支（{@code meshAncestorBones} 预计算），
 * 高骨骼数模型的每帧遍历成本因此与网格骨骼数相关而非总骨骼数。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
@Environment(EnvType.CLIENT)
public class PolyMeshModel {

    public static final int FULL_BRIGHT = 15728880;

    private final IPolyMeshBone root;
    private final Map<String, List<PolyMesh>> meshMap = new HashMap<>();
    private final Set<String> translucentBones = new HashSet<>();
    private final boolean hasTranslucent;
    private final Set<String> meshAncestorBones = new HashSet<>();
    private final Set<String> illuminatedBones = new HashSet<>();
    private String excludeSubtreeRoot = null;
    private final Set<String> excludedBones = new HashSet<>();

    public PolyMeshModel(IPolyMeshBone root, Map<String, List<PolyMesh>> sharedMeshMap) {
        this.root = root;
        this.meshMap.putAll(sharedMeshMap);
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

    private boolean buildMeshAncestors(IPolyMeshBone bone, Deque<String> path) {
        String name = bone.getName();
        path.addLast(name);
        boolean has = meshMap.containsKey(name);
        for (IPolyMeshBone child : bone.getChildren()) {
            if (buildMeshAncestors(child, path)) {
                has = true;
            }
        }
        if (has) {
            meshAncestorBones.addAll(path);
        }
        path.removeLast();
        return has;
    }

    private void buildIlluminatedBones(IPolyMeshBone bone, boolean parentIlluminated) {
        boolean illuminated = parentIlluminated || bone.isIlluminated();
        if (illuminated && meshMap.containsKey(bone.getName())) {
            illuminatedBones.add(bone.getName());
        }
        for (IPolyMeshBone child : bone.getChildren()) {
            buildIlluminatedBones(child, illuminated);
        }
    }

    public static Map<String, List<PolyMesh>> parseMeshMapFromJson(JsonObject rawJson) {
        Map<String, List<PolyMesh>> result = new HashMap<>();
        JsonArray geometries = rawJson.has("minecraft:geometry") ? rawJson.getAsJsonArray("minecraft:geometry") : null;
        if (geometries == null || geometries.isEmpty()) {
            return result;
        }
        JsonObject geo = geometries.get(0).getAsJsonObject();
        if (!geo.has("description") || !geo.getAsJsonObject("description").has("texture_width")) {
            return result;
        }
        float texW = geo.getAsJsonObject("description").get("texture_width").getAsFloat();
        float texH = geo.getAsJsonObject("description").get("texture_height").getAsFloat();
        JsonArray bones = geo.getAsJsonArray("bones");
        if (bones == null) {
            return result;
        }
        for (JsonElement boneElem : bones) {
            JsonObject boneObj = boneElem.getAsJsonObject();
            if (!boneObj.has("poly_mesh") || !boneObj.has("name")) {
                continue;
            }
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
                result.computeIfAbsent(name, k -> new ArrayList<>()).add(mesh);
            }
        }
        return result;
    }

    public PolyMeshSnapshot capture(PoseStack rootPose, int light) {
        List<PolyMeshSnapshot.Command> cutout = new ArrayList<>();
        List<PolyMeshSnapshot.Command> translucent = new ArrayList<>();
        captureBone(root, rootPose, light, true, cutout, translucent);
        return new PolyMeshSnapshot(cutout, translucent);
    }

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
            captureBone(bone, rootPose, light, false, cutout, translucent);
        }
        return new PolyMeshSnapshot(cutout, translucent);
    }

    /**
     * 与 {@link #captureBone} 的差别只有一处：<b>不</b>套用根骨骼自己的
     * pivot/rot 变换 —— 调用方（换弹留在枪上的弹匣）已把该节点及其祖先的
     * 变换乘进了 rootPose，这里再套一遍会双重变换。
     */
    private void captureBoneMirrored(IPolyMeshBone bone, PoseStack poseStack, int light,
                                     List<PolyMeshSnapshot.Command> cutout, List<PolyMeshSnapshot.Command> translucent) {
        if (!bone.isVisible()) {
            return;
        }
        if (!meshAncestorBones.contains(bone.getName())) {
            return;
        }
        drawBoneMeshes(bone, poseStack, light, cutout, translucent);
        for (IPolyMeshBone child : bone.getChildren()) {
            captureBone(child, poseStack, light, false, cutout, translucent);
        }
    }

    private void captureBone(IPolyMeshBone bone, PoseStack poseStack, int light, boolean checkExcluded,
                             List<PolyMeshSnapshot.Command> cutout, List<PolyMeshSnapshot.Command> translucent) {
        if (!bone.isVisible()) {
            return;
        }
        if (!meshAncestorBones.contains(bone.getName())) {
            return;
        }
        if (checkExcluded && !excludedBones.isEmpty() && excludedBones.contains(bone.getName())) {
            return;
        }

        poseStack.pushPose();
        bone.applyTransform(poseStack);
        drawBoneMeshes(bone, poseStack, light, cutout, translucent);
        for (IPolyMeshBone child : bone.getChildren()) {
            captureBone(child, poseStack, light, checkExcluded, cutout, translucent);
        }
        poseStack.popPose();
    }

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

    public void setExcludeSubtree(String rootBoneName) {
        if (rootBoneName.equals(excludeSubtreeRoot)) {
            return;
        }
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

    public Map<String, List<PolyMesh>> getMeshMap() {
        return meshMap;
    }

    public boolean isTranslucentBone(String boneName) {
        return translucentBones.contains(boneName);
    }

    public boolean isIlluminatedBone(String boneName) {
        return illuminatedBones.contains(boneName);
    }

    public int getTotalVertexCount() {
        int total = 0;
        for (List<PolyMesh> meshes : meshMap.values()) {
            for (PolyMesh mesh : meshes) {
                total += mesh.getVertexCount();
            }
        }
        return total;
    }

    public int getMeshBoneCount() {
        return meshMap.size();
    }

    public int getTranslucentBoneCount() {
        return translucentBones.size();
    }

    public int getIlluminatedBoneCount() {
        return illuminatedBones.size();
    }

    public boolean hasMeshInSubtree(String boneName) {
        IPolyMeshBone bone = findBone(this.root, boneName);
        return bone != null && hasMeshInSubtreeInternal(bone);
    }

    private boolean hasMeshInSubtreeInternal(IPolyMeshBone bone) {
        if (meshMap.containsKey(bone.getName())) {
            return true;
        }
        for (IPolyMeshBone child : bone.getChildren()) {
            if (hasMeshInSubtreeInternal(child)) {
                return true;
            }
        }
        return false;
    }

    private IPolyMeshBone findBone(IPolyMeshBone bone, String name) {
        if (name.equals(bone.getName())) {
            return bone;
        }
        for (IPolyMeshBone child : bone.getChildren()) {
            IPolyMeshBone found = findBone(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * {@code ShaderStateTracker} 的失效钩子：光影包开关翻转时被调用，
     * 释放本模型持有的 GPU 烘焙缓存（常驻 VBO 依赖当时的光影状态）。
     *
     * <p>第 0 步（纯 collector 路径）没有任何 GPU 烘焙缓存，此方法为空操作；
     * 第 1 步 GPU 静态烘焙落地时，这里接入对应模型的 VBO 释放逻辑。</p>
     */
    public void invalidateVboCache() {
        // 第 0 步：collector 路径无 VBO，无需处理。
    }
}
