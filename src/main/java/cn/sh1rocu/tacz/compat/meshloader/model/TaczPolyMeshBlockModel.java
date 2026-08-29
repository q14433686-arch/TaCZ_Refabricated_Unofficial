package cn.sh1rocu.tacz.compat.meshloader.model;

import cn.sh1rocu.tacz.compat.meshloader.api.IPolyMeshBone;
import cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig;
import cn.sh1rocu.tacz.compat.meshloader.config.PolyRenderPolicy;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshModel;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSnapshot;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 支持 poly_mesh 的方块模型（自定义工作台等）。
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
public class TaczPolyMeshBlockModel extends BedrockModel {

    private static final Logger LOGGER = LoggerFactory.getLogger("TacZMeshLoader");

    private PolyMeshModel polyMeshModel;
    private Identifier texture;
    private List<IPolyMeshBone> cachedRootChildren = null;

    public TaczPolyMeshBlockModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
    }

    @Override
    public void submit(PoseStack poseStack, ItemDisplayContext transformType,
                       SubmitNodeCollector collector, RenderType renderType,
                       int light, int overlay,
                       float red, float green, float blue, float alpha) {
        super.submit(poseStack, transformType, collector, renderType, light, overlay, red, green, blue, alpha);
        if (!PolyRenderPolicy.shouldRenderPoly(transformType, poseStack)
                || polyMeshModel == null || texture == null) {
            return;
        }
        PolyMeshSnapshot snapshot = polyMeshModel.capture(poseStack, light);
        if (snapshot.isEmpty()) {
            return;
        }
        collector.submitCustomGeometry(new PoseStack(), RenderTypes.entityCutout(texture),
                (entryPose, consumer) -> snapshot.writeCutout(consumer, overlay, red, green, blue, alpha));
        if (snapshot.hasTranslucent()) {
            collector.submitCustomGeometry(new PoseStack(), RenderTypes.entityTranslucent(texture),
                    (entryPose, consumer) -> snapshot.writeTranslucent(consumer, overlay, red, green, blue, alpha));
        }
    }

    public void loadPolyMesh(Identifier geoPath, Identifier textureLocation) {
        try {
            this.cachedRootChildren = null;
            this.polyMeshModel = PolyMeshSupport.load(geoPath, () -> {
                if (cachedRootChildren != null) {
                    return cachedRootChildren;
                }
                cachedRootChildren = PolyMeshSupport.adaptShouldRender(this);
                return cachedRootChildren;
            });
            this.texture = textureLocation;
            if (this.polyMeshModel != null && MeshyConfig.LOG_STATS.get()
                    && PolyMeshSupport.markGeoLogged(geoPath)) {
                LOGGER.info("[TacZMeshLoader] block poly_mesh stats for {}: {} bones, {} vertices",
                        geoPath, polyMeshModel.getMeshBoneCount(), polyMeshModel.getTotalVertexCount());
            }
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] Failed to load block poly_mesh: {}", geoPath, e);
        }
    }

    public boolean hasPolyMesh() {
        return polyMeshModel != null;
    }
}
