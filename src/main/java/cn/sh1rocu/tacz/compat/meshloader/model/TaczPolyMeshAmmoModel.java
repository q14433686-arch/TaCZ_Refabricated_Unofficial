package cn.sh1rocu.tacz.compat.meshloader.model;

import cn.sh1rocu.tacz.compat.meshloader.api.IPolyMeshBone;
import cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig;
import cn.sh1rocu.tacz.compat.meshloader.config.PolyRenderPolicy;
import cn.sh1rocu.tacz.compat.meshloader.core.BedrockPartBoneAdapter;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshModel;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSnapshot;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockAmmoModel;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 支持 poly_mesh 的弹药模型，替代 ammo（物品显示）/ ammo_entity（子弹实体）/
 * shell（弹壳）三种 {@link BedrockAmmoModel}。
 * 没有对应 geo.json 时行为与父类完全一致。
 *
 * <p>26.2 版：重写 {@code BedrockModel#submit} 的 10 参版本（6 参由父类委托），
 * 立方体走 {@code super.submit}，poly_mesh 经 {@code submitCustomGeometry} 提交。
 * 支持颜色参数（曳光弹染色等）。</p>
 *
 * <p>注入：{@code ClientAmmoIndex#checkTextureAndModel} /
 * {@code checkAmmoEntity} / {@code checkShell} 的 TAIL（见 mixin）。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
public class TaczPolyMeshAmmoModel extends BedrockAmmoModel {

    private static final Logger LOGGER = LoggerFactory.getLogger("TacZMeshLoader");

    private PolyMeshModel polyMeshModel;
    private Identifier texture;
    private List<IPolyMeshBone> cachedRootChildren = null;

    public TaczPolyMeshAmmoModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
    }

    // =========================================================================
    // 26.2 submit 路径（6 参由 BedrockModel 委托到本 10 参）
    // =========================================================================

    @Override
    public void submit(PoseStack poseStack, ItemDisplayContext transformType,
                       SubmitNodeCollector collector, RenderType renderType,
                       int light, int overlay,
                       float red, float green, float blue, float alpha) {
        // 立方体层正常提交
        super.submit(poseStack, transformType, collector, renderType, light, overlay, red, green, blue, alpha);

        // poly 层渲染策略（阴影 pass 跳过 / 距离裁剪 / 预览开关）
        if (!PolyRenderPolicy.shouldRenderPoly(transformType, poseStack)) {
            return;
        }

        // poly_mesh 层
        if (polyMeshModel == null || texture == null) {
            return;
        }
        PolyMeshSnapshot snapshot = polyMeshModel.capture(poseStack, light);
        if (snapshot.isEmpty()) {
            return;
        }
        PoseStack identity = new PoseStack();
        collector.submitCustomGeometry(identity, RenderTypes.entityCutout(texture),
                (entryPose, consumer) -> snapshot.writeCutout(consumer, overlay, red, green, blue, alpha));
        if (snapshot.hasTranslucent()) {
            PoseStack identityTranslucent = new PoseStack();
            collector.submitCustomGeometry(identityTranslucent, RenderTypes.entityTranslucent(texture),
                    (entryPose, consumer) -> snapshot.writeTranslucent(consumer, overlay, red, green, blue, alpha));
        }
    }

    // =========================================================================
    // poly_mesh 加载
    // =========================================================================

    public void loadPolyMesh(Identifier modelLocation, Identifier textureLocation) {
        try {
            Optional<net.minecraft.server.packs.resources.Resource> resource =
                    Minecraft.getInstance().getResourceManager().getResource(modelLocation);
            if (!resource.isPresent()) {
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
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
                this.texture = textureLocation;

                if (MeshyConfig.LOG_STATS.get()) {
                    LOGGER.info("[TacZMeshLoader] ammo poly_mesh stats for {}: {} bones, {} vertices"
                                    + " (translucent={}, illuminated={})",
                            modelLocation,
                            polyMeshModel.getMeshBoneCount(),
                            polyMeshModel.getTotalVertexCount(),
                            polyMeshModel.getTranslucentBoneCount(),
                            polyMeshModel.getIlluminatedBoneCount());
                }
            }
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] Failed to load ammo poly_mesh: {}", modelLocation, e);
        }
    }

    public boolean hasPolyMesh() {
        return polyMeshModel != null;
    }
}
