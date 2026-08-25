package cn.sh1rocu.tacz.compat.meshloader.model;

import cn.sh1rocu.tacz.compat.meshloader.api.IPolyMeshBone;
import cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig;
import cn.sh1rocu.tacz.compat.meshloader.config.PolyRenderPolicy;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMesh;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshModel;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSnapshot;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSupport;
import cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.other.GunModelTypeManager;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.GunModelConstant;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 支持 poly_mesh 的枪械模型。
 *
 * <h2>弹匣（相对关 PR 的重写）</h2>
 * 上游 TML 在 {@code loadPolyMesh} 之后把 {@code additional_magazine} 的
 * FunctionalRenderer 包一层：立方体走原 {@code IMirrorGeometry}，poly 在
 * {@code additional_magazine.visible} 时按该节点变换再画一遍 {@code magazine}。
 * 关 PR 把这段改成 submit 之后的 collector 补画，纯 mesh 枪的弹匣会丢。
 *
 * <p>26.2 快照遍历器认 {@link IMirrorGeometry} 画立方体镜像。本类保留那条路径，
 * 再按原版 TML 在 FunctionalRenderer 里补 poly —— 但 26.2 的延迟提交不能在
 * 旧 {@code render()} 回调里写 VertexConsumer。做法：主 submit 里
 * <ol>
 *   <li>exclude {@code additional_magazine} 子树，避免主遍历把它画在错误位置；</li>
 *   <li>{@code super.submit} 照常走立方体 + {@code IMirrorGeometry}；</li>
 *   <li>主 poly（含 {@code magazine}）走 GPU 或 collector；</li>
 *   <li>{@code additional_magazine.visible} 时在该节点变换下再提交 magazine /
 *       additional_magazine 的 poly（与上游 TML 的 {@code renderSubtreeDirect} 同构）。</li>
 * </ol>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
public class TaczPolyMeshGunModel extends BedrockGunModel {

    private static final Logger LOGGER = LoggerFactory.getLogger("TacZMeshLoader");

    private PolyMeshModel polyMeshModel;
    private Identifier cachedTexture = null;
    private Identifier overrideTexture = null;
    private List<IPolyMeshBone> cachedRootChildren = null;
    private boolean cachedHasMagMesh = false;
    private boolean cachedHasAdditionalMagMesh = false;
    private List<BedrockPart> cachedAdditionalMagazinePath = null;
    private final Map<String, PolyMeshGpuRenderer.BakedBone> bakedBones = new HashMap<>();
    private int bakedLightKey = -1;
    private long lastRebakeMs = 0L;
    private boolean gpuBaked = false;
    private boolean loggedFirstSubmit = false;
    private boolean loggedGuiVertexCap = false;
    private boolean loggedWorldVertexCap = false;
    private boolean loggedDenseModel = false;

    public TaczPolyMeshGunModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
    }

    @Override
    public void submit(PoseStack poseStack, ItemStack gunItem, ItemDisplayContext transformType,
                       SubmitNodeCollector collector, RenderType renderType, int light, int overlay) {
        submit(poseStack, gunItem, transformType, collector, renderType, null, light, overlay);
    }

    @Override
    public void submit(PoseStack poseStack, ItemStack gunItem, ItemDisplayContext transformType,
                       SubmitNodeCollector collector, RenderType renderType,
                       @javax.annotation.Nullable Identifier gunTexture, int light, int overlay) {
        if (!hasPolyMesh()) {
            super.submit(poseStack, gunItem, transformType, collector, renderType, gunTexture, light, overlay);
            return;
        }

        if (cachedHasAdditionalMagMesh) {
            polyMeshModel.setExcludeSubtree(GunModelConstant.MAG_ADDITIONAL_NODE);
        } else {
            polyMeshModel.clearExcludeSubtree();
        }

        super.submit(poseStack, gunItem, transformType, collector, renderType, gunTexture, light, overlay);

        if (!PolyRenderPolicy.shouldRenderPoly(transformType, poseStack)) {
            return;
        }

        Identifier texture = gunTexture != null ? gunTexture : resolveTexture(gunItem);
        if (texture == null) {
            if (!loggedFirstSubmit) {
                loggedFirstSubmit = true;
                LOGGER.warn("[TacZMeshLoader] poly submit skipped: no texture (firstPerson={})",
                        transformType != null && transformType.firstPerson());
            }
            return;
        }

        if (!withinContextBudget(transformType)) {
            return;
        }

        boolean gpu = PolyMeshGpuRenderer.shouldSubmitGpu() && ensureBaked(texture, light);
        if (!loggedFirstSubmit) {
            loggedFirstSubmit = true;
            LOGGER.info("[TacZMeshLoader] poly submit: bones={} verts={} gpu={} firstPerson={} texture={}",
                    polyMeshModel.getMeshBoneCount(),
                    polyMeshModel.getTotalVertexCount(),
                    gpu,
                    transformType != null && transformType.firstPerson(),
                    texture);
        }

        if (gpu) {
            polyMeshModel.visitBones(poseStack, true, (boneName, bonePose) -> {
                if (polyMeshModel.isTranslucentBone(boneName)) {
                    return true;
                }
                PolyMeshGpuRenderer.BakedBone baked = bakedBones.get(boneName);
                if (baked != null) {
                    PolyMeshGpuRenderer.submitBone(new Matrix4f(bonePose.last().pose()), texture, baked);
                }
                return true;
            });
            PolyMeshSnapshot translucentOnly = polyMeshModel.capture(poseStack, light, this::isGpuBone);
            submitPolyMeshTranslucent(translucentOnly, collector, texture, overlay);
        } else {
            submitPolyMesh(polyMeshModel.capture(poseStack, light), collector, texture, overlay);
        }

        submitAdditionalMagazinePoly(poseStack, collector, texture, overlay, light);
    }

    /**
     * 换弹时留在枪上的那份弹匣。立方体已由 {@link IMirrorGeometry} 处理；
     * 这里只补 poly，且仅在 {@code additional_magazine.visible} 时画。
     *
     * <p>始终走 collector：换弹弹匣不是 36 万顶点热点，captureSubtree 的矩阵语义
     * 与上游 TML {@code renderSubtreeDirect} 一致，避免再套一层 GPU visitBones
     * 把枪树变换乘进去。</p>
     */
    private void submitAdditionalMagazinePoly(PoseStack poseStack, SubmitNodeCollector collector,
                                              Identifier texture, int overlay, int light) {
        BedrockPart additionalMagazine = getAdditionalMagazineNode();
        if (additionalMagazine == null || !additionalMagazine.visible
                || (!cachedHasMagMesh && !cachedHasAdditionalMagMesh)) {
            return;
        }
        PoseStack magazinePose = new PoseStack();
        magazinePose.last().pose().set(poseStack.last().pose());
        magazinePose.last().normal().set(poseStack.last().normal());
        List<BedrockPart> path = cachedAdditionalMagazinePath;
        if (path == null) {
            path = pathToRoot(additionalMagazine);
            cachedAdditionalMagazinePath = path;
        }
        for (BedrockPart part : path) {
            part.translateAndRotateAndScale(magazinePose);
        }
        if (cachedHasMagMesh) {
            submitPolyMesh(polyMeshModel.captureSubtree(
                    GunModelConstant.MAG_NORMAL_NODE, magazinePose, light, true),
                    collector, texture, overlay);
        }
        if (cachedHasAdditionalMagMesh) {
            submitPolyMesh(polyMeshModel.captureSubtree(
                    GunModelConstant.MAG_ADDITIONAL_NODE, magazinePose, light, true),
                    collector, texture, overlay);
        }
    }

    private boolean isGpuBone(String boneName) {
        return bakedBones.containsKey(boneName) && !polyMeshModel.isTranslucentBone(boneName);
    }

    private boolean withinContextBudget(ItemDisplayContext transformType) {
        if (transformType == ItemDisplayContext.GUI
                || transformType == ItemDisplayContext.FIXED
                || transformType == ItemDisplayContext.HEAD) {
            return previewWithinVertexBudget(MeshyConfig.GUI_MAX_VERTICES.get(), true);
        }
        if (transformType != null && !transformType.firstPerson()) {
            return previewWithinVertexBudget(MeshyConfig.WORLD_MAX_VERTICES.get(), false);
        }
        return true;
    }

    private boolean previewWithinVertexBudget(int cap, boolean gui) {
        if (cap <= 0) {
            return true;
        }
        int total = polyMeshModel == null ? 0 : polyMeshModel.getTotalVertexCount();
        if (total <= cap) {
            return true;
        }
        if (gui && !loggedGuiVertexCap) {
            loggedGuiVertexCap = true;
            LOGGER.info("[TacZMeshLoader] poly preview suppressed in GUI: {} vertices exceeds MeshGuiMaxVertices={}",
                    total, cap);
        } else if (!gui && !loggedWorldVertexCap) {
            loggedWorldVertexCap = true;
            LOGGER.info("[TacZMeshLoader] poly suppressed in world context: {} vertices exceeds MeshWorldMaxVertices={}",
                    total, cap);
        }
        return false;
    }

    private void submitPolyMeshTranslucent(PolyMeshSnapshot snapshot, SubmitNodeCollector collector,
                                           Identifier texture, int overlay) {
        if (!snapshot.hasTranslucent()) {
            return;
        }
        collector.submitCustomGeometry(new PoseStack(), RenderTypes.entityTranslucent(texture),
                (entryPose, consumer) -> snapshot.writeTranslucent(consumer, overlay));
    }

    private boolean ensureBaked(Identifier texture, int currentLight) {
        if (polyMeshModel == null) {
            return false;
        }
        int lightKey = PolyMeshGpuRenderer.quantizeLight(currentLight);
        if (gpuBaked) {
            if (lightKey == bakedLightKey) {
                return true;
            }
            long now = System.currentTimeMillis();
            if (now - lastRebakeMs < 1000L) {
                return true;
            }
            releaseBaked();
        }
        boolean allOk = true;
        for (Map.Entry<String, List<PolyMesh>> entry : polyMeshModel.getMeshMap().entrySet()) {
            String boneName = entry.getKey();
            if (polyMeshModel.isTranslucentBone(boneName)) {
                continue;
            }
            int boneLight = polyMeshModel.isIlluminatedBone(boneName)
                    ? PolyMeshGpuRenderer.FULL_BRIGHT : lightKey;
            PolyMeshGpuRenderer.BakedBone baked = PolyMeshGpuRenderer.bakeBone(entry.getValue(), boneLight);
            if (baked == null) {
                allOk = false;
                continue;
            }
            bakedBones.put(boneName, baked);
        }
        gpuBaked = allOk && !bakedBones.isEmpty();
        if (gpuBaked) {
            bakedLightKey = lightKey;
            lastRebakeMs = System.currentTimeMillis();
            GunMod.LOGGER.info("[TacZMeshLoader] GPU-baked {} bones ({} vertices) for {} at quantized light {}",
                    bakedBones.size(), polyMeshModel.getTotalVertexCount(), texture,
                    Integer.toHexString(lightKey));
        } else {
            releaseBaked();
        }
        return gpuBaked;
    }

    private void releaseBaked() {
        for (PolyMeshGpuRenderer.BakedBone baked : bakedBones.values()) {
            baked.close();
        }
        bakedBones.clear();
        gpuBaked = false;
        bakedLightKey = -1;
    }

    private void submitPolyMesh(PolyMeshSnapshot snapshot, SubmitNodeCollector collector,
                                Identifier texture, int overlay) {
        if (snapshot.isEmpty()) {
            return;
        }
        collector.submitCustomGeometry(new PoseStack(), RenderTypes.entityCutout(texture),
                (entryPose, consumer) -> snapshot.writeCutout(consumer, overlay));
        if (snapshot.hasTranslucent()) {
            collector.submitCustomGeometry(new PoseStack(), RenderTypes.entityTranslucent(texture),
                    (entryPose, consumer) -> snapshot.writeTranslucent(consumer, overlay));
        }
    }

    private Identifier resolveTexture(ItemStack gunItem) {
        if (overrideTexture != null) {
            return overrideTexture;
        }
        if (cachedTexture != null) {
            return cachedTexture;
        }
        Optional<GunDisplayInstance> display = TimelessAPI.getGunDisplay(gunItem);
        if (display.isPresent()) {
            cachedTexture = display.get().getModelTexture();
        }
        return cachedTexture;
    }

    public void setOverrideTexture(Identifier texture) {
        this.overrideTexture = texture;
        this.cachedTexture = null;
    }

    public void loadPolyMesh(Identifier modelLocation) {
        releaseBaked();
        try {
            this.cachedRootChildren = null;
            this.polyMeshModel = PolyMeshSupport.load(modelLocation, () -> {
                if (cachedRootChildren != null) {
                    return cachedRootChildren;
                }
                cachedRootChildren = PolyMeshSupport.adaptShouldRender(this);
                return cachedRootChildren;
            });
            if (this.polyMeshModel == null) {
                return;
            }
            this.cachedTexture = null;
            this.cachedHasMagMesh = this.polyMeshModel.hasMeshInSubtree(GunModelConstant.MAG_NORMAL_NODE);
            this.cachedHasAdditionalMagMesh = this.polyMeshModel.hasMeshInSubtree(GunModelConstant.MAG_ADDITIONAL_NODE);
            logStats(modelLocation);
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] Failed to load poly_mesh: {}", modelLocation, e);
        }
    }

    public boolean hasPolyMesh() {
        return polyMeshModel != null;
    }

    private void logStats(Identifier modelLocation) {
        if (!MeshyConfig.LOG_STATS.get()) {
            return;
        }
        if (!PolyMeshSupport.markGeoLogged(modelLocation)) {
            return;
        }
        int verts = polyMeshModel.getTotalVertexCount();
        LOGGER.info("[TacZMeshLoader] poly_mesh stats for {}: {} bones, {} vertices"
                        + " (translucent={}, illuminated={}, mag={}, additionalMag={})",
                modelLocation,
                polyMeshModel.getMeshBoneCount(),
                verts,
                polyMeshModel.getTranslucentBoneCount(),
                polyMeshModel.getIlluminatedBoneCount(),
                cachedHasMagMesh,
                cachedHasAdditionalMagMesh);
        int warnAt = MeshyConfig.MAX_MODEL_VERTICES.get();
        if (!loggedDenseModel && warnAt > 0 && verts > warnAt) {
            loggedDenseModel = true;
            LOGGER.warn("[TacZMeshLoader] {} has {} vertices (MeshMaxModelVertices={}). "
                            + "First-person uses GPU baking when enabled; GUI/world are capped.",
                    modelLocation, verts, warnAt);
        }
    }

    public static void register() {
        GunModelTypeManager.registerModelType("mesh", TaczPolyMeshGunModel::new);
        LOGGER.info("[TacZMeshLoader] Registered TACZ gun model type: mesh");
    }

    private static List<BedrockPart> pathToRoot(BedrockPart part) {
        List<BedrockPart> path = new ArrayList<>();
        for (BedrockPart current = part; current != null; current = current.getParent()) {
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }
}
