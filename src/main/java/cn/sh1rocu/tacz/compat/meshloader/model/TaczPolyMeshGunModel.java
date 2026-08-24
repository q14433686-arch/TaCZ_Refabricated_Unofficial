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
 * 支持 poly_mesh 的枪械模型。枪包 display 里写 {@code "model_type": "mesh"} 即启用。
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
    /** 当前烘焙进 UV2 的量化光照档位；-1 = 尚未烘焙。 */
    private int bakedLightKey = -1;
    private long lastRebakeMs = 0L;
    private boolean gpuBaked = false;
    private boolean loggedFirstSubmit = false;
    private boolean loggedGuiVertexCap = false;

    public TaczPolyMeshGunModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
    }

    /**
     * 7 参只是转发。真正的第一/第三人称入口是下面的 8 参
     * （{@code GunItemRendererWrapper} 会传入枪身贴图做镜内裁剪）。
     * 上一版只覆写了 7 参，结果高模路径从未执行 —— 枪体全空。
     */
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

        // GUI/预览顶点闸门：JEI 一屏几十个高模图标与第一人称同价，超限只画立方体。
        if (transformType == ItemDisplayContext.GUI
                || transformType == ItemDisplayContext.FIXED
                || transformType == ItemDisplayContext.HEAD) {
            if (!previewWithinVertexBudget()) {
                return;
            }
        }

        boolean handPass = transformType != null && transformType.firstPerson();
        boolean gpuActive = PolyMeshGpuRenderer.isGpuPathUsable()
                && ensureBaked(texture, light);
        if (!loggedFirstSubmit) {
            loggedFirstSubmit = true;
            LOGGER.info("[TacZMeshLoader] poly submit: bones={} verts={} gpu={} firstPerson={} texture={}",
                    polyMeshModel.getMeshBoneCount(),
                    polyMeshModel.getTotalVertexCount(),
                    gpuActive,
                    handPass,
                    texture);
        }
        if (gpuActive) {
            polyMeshModel.visitBones(poseStack, true, (boneName, bonePose) -> {
                if (polyMeshModel.isTranslucentBone(boneName)) {
                    return true;
                }
                PolyMeshGpuRenderer.BakedBone baked = bakedBones.get(boneName);
                if (baked != null) {
                    // 只提交 poseStack 里已经包含的物品+骨骼矩阵，不再乘 modelView。
                    // 世界 pass 在世界那次 renderAllFeatures 边界绘制，彼时主 target
                    // 深度含地形/实体，遮挡正确；光照用的是烘焙档位（近似）。
                    PolyMeshGpuRenderer.submitBone(
                            new Matrix4f(bonePose.last().pose()), texture, baked, handPass);
                }
                return true;
            });
            PolyMeshSnapshot translucentOnly = polyMeshModel.capture(poseStack, light, this::isGpuBone);
            submitPolyMeshTranslucent(translucentOnly, collector, texture, overlay);
        } else {
            submitPolyMesh(polyMeshModel.capture(poseStack, light), collector, texture, overlay);
        }

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

    private boolean isGpuBone(String boneName) {
        return bakedBones.containsKey(boneName) && !polyMeshModel.isTranslucentBone(boneName);
    }

    /**
     * GUI/FIXED 预览的顶点数闸门：高模在 JEI 列表、创造背包这类一屏多个物品的场合
     * 每帧全量重建，代价与第一人称相同。超过 {@code MeshGuiMaxVertices} 时预览
     * 只画立方体（纯 mesh 枪会不可见），日志提示一次。0 = 不限制。
     */
    private boolean previewWithinVertexBudget() {
        int cap = MeshyConfig.GUI_MAX_VERTICES.get();
        if (cap <= 0) {
            return true;
        }
        int total = polyMeshModel == null ? 0 : polyMeshModel.getTotalVertexCount();
        if (total <= cap) {
            return true;
        }
        if (!loggedGuiVertexCap) {
            loggedGuiVertexCap = true;
            LOGGER.info("[TacZMeshLoader] poly preview suppressed in GUI contexts: {} vertices exceeds "
                    + "MeshGuiMaxVertices={} (adjust in tacz-client.toml [mesh_loader]; 0 = unlimited)",
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

    /**
     * 确保 GPU 烘焙就绪，并按需按光照档位重烘焙。
     *
     * <p>顶点 UV2 里烘焙的是量化光照（sky/block 各 4 级一档）。档位变了理论上要
     * 重写一遍顶点；为了不在光照边界抖动时每帧重写，档位变化后至少间隔
     * {@code REBAKE_MIN_INTERVAL_MS} 才真正重烘焙，期间沿用旧档（光照偏差最多
     * 4 级，肉眼近似不可辨）。</p>
     */
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
                // 沿用旧档位，等节流窗口过去
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
        // 同一 geo 常被多个 display 复用（实机日志里每把枪恰好加载两遍），
        // 日志按 geo 去重；解析本身已由 PolyMeshSupport 的缓存去重。
        if (!PolyMeshSupport.markGeoLogged(modelLocation)) {
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
