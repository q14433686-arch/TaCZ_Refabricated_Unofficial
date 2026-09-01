package cn.sh1rocu.tacz.compat.meshloader.model;

import cn.sh1rocu.tacz.compat.meshloader.api.IPolyMeshBone;
import cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig;
import cn.sh1rocu.tacz.compat.meshloader.config.PolyRenderPolicy;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshModel;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSnapshot;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 支持 poly_mesh 的配件模型。mesh 目镜的镜内裁剪不支持（与上游 TML 相同的限制：
 * ocular 物体必须用立方体），poly 部分按普通几何提交。
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
public class TaczPolyMeshAttachmentModel extends BedrockAttachmentModel {

    private static final Logger LOGGER = LoggerFactory.getLogger("TacZMeshLoader");

    private PolyMeshModel polyMeshModel;
    private Identifier cachedTexture = null;
    private List<IPolyMeshBone> cachedRootChildren = null;

    public TaczPolyMeshAttachmentModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
    }

    @Override
    public void submit(@Nullable ItemStack attachmentItem, ItemStack currentGunItem, PoseStack poseStack,
                       ItemDisplayContext transformType, SubmitNodeCollector collector,
                       RenderType renderType, @Nullable Identifier texture,
                       int light, int overlay) {
        super.submit(attachmentItem, currentGunItem, poseStack, transformType, collector,
                renderType, texture, light, overlay);
        if (!hasPolyMesh() || !PolyRenderPolicy.shouldRenderPoly(transformType, poseStack)) {
            return;
        }
        Identifier tex = texture != null ? texture : resolveTexture(attachmentItem);
        if (tex == null) {
            return;
        }
        PolyMeshSnapshot snapshot = polyMeshModel.capture(poseStack, light);
        if (snapshot.isEmpty()) {
            return;
        }
        collector.submitCustomGeometry(new PoseStack(), RenderTypes.entityCutout(tex),
                (entryPose, consumer) -> snapshot.writeCutout(consumer, overlay));
        if (snapshot.hasTranslucent()) {
            collector.submitCustomGeometry(new PoseStack(), RenderTypes.entityTranslucent(tex),
                    (entryPose, consumer) -> snapshot.writeTranslucent(consumer, overlay));
        }
    }

    @Nullable
    private Identifier resolveTexture(@Nullable ItemStack attachmentItem) {
        if (cachedTexture != null) {
            return cachedTexture;
        }
        if (attachmentItem == null || attachmentItem.isEmpty()) {
            return null;
        }
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (iAttachment != null) {
            Identifier attachmentId = iAttachment.getAttachmentId(attachmentItem);
            TimelessAPI.getClientAttachmentIndex(attachmentId)
                    .ifPresent(index -> cachedTexture = index.getModelTexture());
        }
        return cachedTexture;
    }

    public void loadPolyMesh(Identifier geoPath) {
        try {
            this.cachedRootChildren = null;
            this.polyMeshModel = PolyMeshSupport.load(geoPath, () -> {
                if (cachedRootChildren != null) {
                    return cachedRootChildren;
                }
                cachedRootChildren = PolyMeshSupport.adaptShouldRender(this);
                return cachedRootChildren;
            });
            this.cachedTexture = null;
            if (this.polyMeshModel != null && MeshyConfig.LOG_STATS.get()
                    && PolyMeshSupport.markGeoLogged(geoPath)) {
                LOGGER.info("[TacZMeshLoader] attachment poly_mesh stats for {}: {} bones, {} vertices",
                        geoPath, polyMeshModel.getMeshBoneCount(), polyMeshModel.getTotalVertexCount());
            }
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] Failed to load attachment poly_mesh: {}", geoPath, e);
        }
    }

    public boolean hasPolyMesh() {
        return polyMeshModel != null;
    }
}
