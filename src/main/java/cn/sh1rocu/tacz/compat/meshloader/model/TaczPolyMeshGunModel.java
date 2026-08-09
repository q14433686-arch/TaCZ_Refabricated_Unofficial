package cn.sh1rocu.tacz.compat.meshloader.model;

import cn.sh1rocu.tacz.compat.meshloader.api.IPolyMeshBone;
import cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig;
import cn.sh1rocu.tacz.compat.meshloader.config.PolyRenderPolicy;
import cn.sh1rocu.tacz.compat.meshloader.core.BedrockPartBoneAdapter;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshModel;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSnapshot;
import cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 支持 poly_mesh 的枪械模型。枪包 display 里写 {@code "model_type": "mesh"} 即启用。
 *
 * <p>26.2 移植版要点：</p>
 * <ul>
 *   <li>重写 {@link #submit} 而非已废弃的 {@code render(...)}：立方体部分走
 *       {@code super.submit}（scope/laser/AR 等现有行为全部保留），
 *       poly_mesh 部分在 submit 时冻结快照、经
 *       {@code collector.submitCustomGeometry} 提交，回调只写顶点；</li>
 *   <li>additional_magazine 处理与移植版 {@code IMirrorGeometry} 语义一致：
 *       主 pass 排除 additional_magazine 子树；动画期间
 *       （{@code additionalMagazineNode.visible == true}）在 additional_magazine
 *       变换下补画 magazine / additional_magazine 子树（magazine 根变换不套用，
 *       与立方体镜像行为一致，否则副本会跟着换弹动画跑）；</li>
 *   <li>无需 VBO / ShaderStateTracker / GUI 检测等上游配套机制
 *       （26.2 渲染管线已不需要它们）。</li>
 * </ul>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
public class TaczPolyMeshGunModel extends BedrockGunModel {

    private static final Logger LOGGER = LoggerFactory.getLogger("TacZMeshLoader");

    private PolyMeshModel polyMeshModel;
    private Identifier cachedTexture = null;
    /** LOD 模型等场景下固定贴图用（优先于 display 贴图）。 */
    private Identifier overrideTexture = null;
    private List<IPolyMeshBone> cachedRootChildren = null;
    /** MAG_NORMAL_NODE 子树是否带 poly_mesh（loadPolyMesh 时确定）。 */
    private boolean cachedHasMagMesh = false;
    /** MAG_ADDITIONAL_NODE 子树是否带 poly_mesh。 */
    private boolean cachedHasAdditionalMagMesh = false;
    /** additional_magazine 骨骼到根的路径（模型加载后固定，缓存避免每帧重建）。 */
    private List<BedrockPart> cachedAdditionalMagazinePath = null;
    /** GPU 烘焙：骨骼名 → 常驻 GPU 几何（烘焙完成后填充）。 */
    private final Map<String, PolyMeshGpuRenderer.BakedBone> bakedBones = new HashMap<>();
    /** GPU 烘焙是否已完成（烘焙失败则保持 false，永久走 consumer）。 */
    private boolean gpuBaked = false;

    public TaczPolyMeshGunModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
    }

    // =========================================================================
    // 26.2 submit 路径
    // =========================================================================

    @Override
    public void submit(PoseStack poseStack, ItemStack gunItem, ItemDisplayContext transformType,
                       SubmitNodeCollector collector, RenderType renderType, int light, int overlay) {
        if (!hasPolyMesh()) {
            super.submit(poseStack, gunItem, transformType, collector, renderType, light, overlay);
            return;
        }

        // 主 pass 排除 additional_magazine 子树（由下面的镜像副本 pass 负责）。
        if (cachedHasAdditionalMagMesh) {
            polyMeshModel.setExcludeSubtree(GunModelConstant.MAG_ADDITIONAL_NODE);
        } else {
            polyMeshModel.clearExcludeSubtree();
        }

        // 立方体部分（含 scope 提交、laser、AR 兼容、muzzle flash 等）完全走原路径。
        super.submit(poseStack, gunItem, transformType, collector, renderType, light, overlay);

        // poly 层渲染策略：第一人称全量；阴影 pass 默认跳过（立方体阴影足够）；
        // GUI/FIXED 预览与距离裁剪由配置控制 —— 见 PolyRenderPolicy。
        if (!PolyRenderPolicy.shouldRenderPoly(transformType, poseStack)) {
            return;
        }

        Identifier texture = resolveTexture(gunItem);
        if (texture == null) {
            return;
        }

        // GPU 烘焙路径：仅第一人称开启（第三人称/掉落物等数量少且距离可裁剪，
        // 保持 consumer 路径；Iris 光影或烘焙未就绪时自动回退 consumer）。
        boolean gpuActive = transformType.firstPerson()
                && PolyMeshGpuRenderer.isGpuPathUsable()
                && ensureBaked(texture);
        if (gpuActive) {
            // 收集 cutout 骨骼的当帧变换并登记；translucent 骨骼留给 consumer。
            polyMeshModel.visitBones(poseStack, light,
                    this::isGpuBone,
                    (boneName, bonePose) -> {
                        if (polyMeshModel.isTranslucentBone(boneName)) {
                            return false; // translucent 骨骼不登记 GPU
                        }
                        Matrix4f frozen = new Matrix4f(RenderSystem.getModelViewMatrixCopy())
                                .mul(bonePose.last().pose());
                        Matrix3f frozenNormal = new Matrix3f(bonePose.last().normal());
                        PolyMeshGpuRenderer.BakedBone baked = bakedBones.get(boneName);
                        if (baked != null) {
                            PolyMeshGpuRenderer.submitBone(frozen, frozenNormal, baked);
                        }
                        return true;
                    });
            // translucent 骨骼走 consumer（需排序）；cutout 骨骼已被 GPU 登记，跳过。
            PolyMeshSnapshot translucentOnly = polyMeshModel.capture(poseStack, light, this::isGpuBone);
            submitPolyMeshTranslucent(translucentOnly, collector, texture, overlay);
        } else {
            // 主 pass：整棵 mesh 树（additional_magazine 已排除）。
            submitPolyMesh(polyMeshModel.capture(poseStack, light), collector, texture, overlay);
        }

        // 镜像副本 pass：换弹/检视等动画期间 additional_magazine.visible=true，
        // 在 additional_magazine 的变换下补画 magazine / additional_magazine 子树。
        // 与移植版 renderAdditionalMagazine（IMirrorGeometry）的 captureGeometry
        // 语义一致：把 additional_magazine 的完整变换压进 pose 后以镜像模式采集
        // （根骨骼自身变换不再套用，否则副本会跟着换弹动画跑）。
        BedrockPart additionalMagazine = getAdditionalMagazineNode();
        if (additionalMagazine != null && additionalMagazine.visible
                && (cachedHasMagMesh || cachedHasAdditionalMagMesh)) {
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
    }

    /** GPU 骨骼判定：已烘焙且非半透明。 */
    private boolean isGpuBone(String boneName) {
        return bakedBones.containsKey(boneName) && !polyMeshModel.isTranslucentBone(boneName);
    }

    /** 只提交快照的 translucent 部分（GPU 路径下 cutout 已由 GPU 绘制）。 */
    private void submitPolyMeshTranslucent(PolyMeshSnapshot snapshot, SubmitNodeCollector collector,
                                           Identifier texture, int overlay) {
        if (!snapshot.hasTranslucent()) {
            return;
        }
        PoseStack identity = new PoseStack();
        collector.submitCustomGeometry(identity, RenderTypes.entityTranslucent(texture),
                (entryPose, consumer) -> snapshot.writeTranslucent(consumer, overlay));
    }

    /**
     * 首次 GPU 使用时把全部骨骼烘焙成常驻缓冲（渲染线程）。
     * 烘焙失败会静默回退 consumer 路径（gpuBaked 保持 false）。
     */
    private boolean ensureBaked(Identifier texture) {
        if (gpuBaked) {
            return true;
        }
        if (polyMeshModel == null) {
            return false;
        }
        boolean allOk = true;
        for (Map.Entry<String, List<cn.sh1rocu.tacz.compat.meshloader.core.PolyMesh>> entry
                : polyMeshModel.getMeshMap().entrySet()) {
            String boneName = entry.getKey();
            if (polyMeshModel.isTranslucentBone(boneName)) {
                continue; // translucent 骨骼保持 consumer（半透明排序）
            }
            PolyMeshGpuRenderer.BakedBone baked =
                    PolyMeshGpuRenderer.bakeBone(entry.getValue(), texture);
            if (baked == null) {
                allOk = false;
                continue;
            }
            bakedBones.put(boneName, baked);
        }
        gpuBaked = allOk && !bakedBones.isEmpty();
        if (gpuBaked) {
            GunMod.LOGGER.info("[TacZMeshLoader] GPU-baked {} bones ({} vertices) for {}",
                    bakedBones.size(), polyMeshModel.getTotalVertexCount(), texture);
        } else {
            // 释放已烘焙的（若有），永久回退 consumer
            releaseBaked();
        }
        return gpuBaked;
    }

    /** 释放 GPU 烘焙资源（模型重载/资源重载时调用）。 */
    private void releaseBaked() {
        for (PolyMeshGpuRenderer.BakedBone baked : bakedBones.values()) {
            baked.close();
        }
        bakedBones.clear();
        gpuBaked = false;
    }

    /**
     * 把一份 poly 快照提交给 collector。
     * 快照矩阵已包含完整根变换，因此与 {@code BedrockModel#submit} 相同，
     * 从 identity 栈提交，避免根变换被套用两次。
     */
    private void submitPolyMesh(PolyMeshSnapshot snapshot, SubmitNodeCollector collector,
                                Identifier texture, int overlay) {
        if (snapshot.isEmpty()) {
            return;
        }
        PoseStack identity = new PoseStack();
        collector.submitCustomGeometry(identity, RenderTypes.entityCutout(texture),
                (entryPose, consumer) -> snapshot.writeCutout(consumer, overlay));
        if (snapshot.hasTranslucent()) {
            PoseStack identityTranslucent = new PoseStack();
            collector.submitCustomGeometry(identityTranslucent, RenderTypes.entityTranslucent(texture),
                    (entryPose, consumer) -> snapshot.writeTranslucent(consumer, overlay));
        }
    }

    // =========================================================================
    // 贴图解析
    // =========================================================================

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

    /** LOD 模型用：固定 poly_mesh 使用的贴图。 */
    public void setOverrideTexture(Identifier texture) {
        this.overrideTexture = texture;
        this.cachedTexture = null;
    }

    // =========================================================================
    // poly_mesh 加载
    // =========================================================================

    /**
     * 读取 geo.json 并把 poly_mesh 骨骼注册进 {@link PolyMeshModel}。
     * 调用时机：{@code GunDisplayInstance#checkTextureAndModel} / {@code checkLod}
     * 的 TAIL（由 mixin 触发），此时骨骼树已由构造器建好。
     */
    public void loadPolyMesh(Identifier modelLocation) {
        // 释放上一份 GPU 烘焙 + 清贴图 view 缓存（资源重载/模型替换时旧 view 失效）
        releaseBaked();
        PolyMeshGpuRenderer.clearTextureViewCache();
        try {
            Optional<net.minecraft.server.packs.resources.Resource> resource =
                    Minecraft.getInstance().getResourceManager().getResource(modelLocation);
            if (!resource.isPresent()) {
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(
                    resource.get().open(), java.nio.charset.StandardCharsets.UTF_8)) {
                JsonObject rawJson = JsonParser.parseReader(reader).getAsJsonObject();

                this.cachedRootChildren = null;
                IPolyMeshBone adaptedRoot = new IPolyMeshBone() {
                    @Override public String getName()    { return "meshy_dummy_root"; }
                    @Override public float getPivotX()   { return 0; }
                    @Override public float getPivotY()   { return 0; }
                    @Override public float getPivotZ()   { return 0; }
                    @Override public float getRotX()     { return 0; }
                    @Override public float getRotY()     { return 0; }
                    @Override public float getRotZ()     { return 0; }
                    @Override public boolean isVisible() { return true; }
                    @Override public void applyTransform(PoseStack ps) { }
                    @Override
                    public List<? extends IPolyMeshBone> getChildren() {
                        if (cachedRootChildren != null) return cachedRootChildren;
                        cachedRootChildren = getShouldRender().stream()
                                .map(BedrockPartBoneAdapter::new).collect(Collectors.toList());
                        return cachedRootChildren;
                    }
                };

                this.polyMeshModel = new PolyMeshModel(adaptedRoot, rawJson);
                this.cachedTexture = null;
                this.cachedHasMagMesh = this.polyMeshModel.hasMeshInSubtree(GunModelConstant.MAG_NORMAL_NODE);
                this.cachedHasAdditionalMagMesh = this.polyMeshModel.hasMeshInSubtree(GunModelConstant.MAG_ADDITIONAL_NODE);

                logStats(modelLocation);
            }
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] Failed to load poly_mesh: {}", modelLocation, e);
        }
    }

    public boolean hasPolyMesh() {
        return polyMeshModel != null;
    }

    /** 加载统计日志（骨骼/顶点/半透明/发光），便于排查性能。 */
    private void logStats(Identifier modelLocation) {
        if (!MeshyConfig.LOG_STATS.get()) {
            return;
        }
        LOGGER.info("[TacZMeshLoader] poly_mesh stats for {}: {} bones, {} vertices"
                        + " (translucent={}, illuminated={}, mag={}, additionalMag={})",
                modelLocation,
                polyMeshModel.getMeshBoneCount(),
                polyMeshModel.getTotalVertexCount(),
                polyMeshModel.getTranslucentBoneCount(),
                polyMeshModel.getIlluminatedBoneCount(),
                cachedHasMagMesh,
                cachedHasAdditionalMagMesh);
    }

    // =========================================================================
    // 注册
    // =========================================================================

    public static void register() {
        GunModelTypeManager.registerModelType("mesh", TaczPolyMeshGunModel::new);
        LOGGER.info("[TacZMeshLoader] Registered TACZ gun model type: mesh");
    }

    // =========================================================================
    // 内部工具
    // =========================================================================

    /** 从根到指定骨骼的路径（根在前）。 */
    private static List<BedrockPart> pathToRoot(BedrockPart part) {
        List<BedrockPart> path = new ArrayList<>();
        for (BedrockPart current = part; current != null; current = current.getParent()) {
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }

}
