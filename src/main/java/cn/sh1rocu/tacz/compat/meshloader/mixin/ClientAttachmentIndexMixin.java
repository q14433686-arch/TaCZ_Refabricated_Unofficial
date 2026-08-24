package cn.sh1rocu.tacz.compat.meshloader.mixin;

import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSupport;
import cn.sh1rocu.tacz.compat.meshloader.model.TaczPolyMeshAttachmentModel;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentLod;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * 配件模型替换。移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。
 */
@Mixin(ClientAttachmentIndex.class)
public class ClientAttachmentIndexMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("TacZMeshLoader");

    @Inject(method = "checkTextureAndModel", at = @At("TAIL"))
    private static void meshyloader$afterCheckTextureAndModel(AttachmentDisplay display, ClientAttachmentIndex index, CallbackInfo ci) {
        Identifier modelId = display.getModel();
        if (modelId == null || !PolyMeshSupport.hasGeoModel(modelId)) {
            return;
        }
        BedrockModelPOJO pojo = ClientAssetsManager.INSTANCE.getBedrockModelPOJO(modelId);
        if (pojo == null) {
            return;
        }
        BedrockVersion version = BedrockVersion.isLegacyVersion(pojo) ? BedrockVersion.LEGACY : BedrockVersion.NEW;
        TaczPolyMeshAttachmentModel polyModel = new TaczPolyMeshAttachmentModel(pojo, version);
        polyModel.setIsScope(display.isScope());
        polyModel.setIsSight(display.isSight());
        polyModel.loadPolyMesh(PolyMeshSupport.toGeoPath(modelId));
        try {
            Field field = ClientAttachmentIndex.class.getDeclaredField("attachmentModel");
            field.setAccessible(true);
            field.set(index, polyModel);
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] Failed to inject attachment poly_mesh model", e);
        }
    }

    @Inject(method = "checkLod", at = @At("TAIL"))
    private static void meshyloader$afterCheckLod(AttachmentDisplay display, ClientAttachmentIndex index, CallbackInfo ci) {
        Pair<BedrockAttachmentModel, Identifier> currentLod;
        try {
            Field lodField = ClientAttachmentIndex.class.getDeclaredField("lodModel");
            lodField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Pair<BedrockAttachmentModel, Identifier> lod =
                    (Pair<BedrockAttachmentModel, Identifier>) lodField.get(index);
            currentLod = lod;
        } catch (Exception e) {
            return;
        }
        if (currentLod == null) {
            return;
        }
        AttachmentLod attachmentLod = display.getAttachmentLod();
        if (attachmentLod == null || attachmentLod.getModelLocation() == null) {
            return;
        }
        Identifier lodModelId = attachmentLod.getModelLocation();
        if (!PolyMeshSupport.hasGeoModel(lodModelId)) {
            return;
        }
        BedrockModelPOJO pojo = ClientAssetsManager.INSTANCE.getBedrockModelPOJO(lodModelId);
        if (pojo == null) {
            return;
        }
        BedrockVersion version = BedrockVersion.isLegacyVersion(pojo) ? BedrockVersion.LEGACY : BedrockVersion.NEW;
        TaczPolyMeshAttachmentModel polyLodModel = new TaczPolyMeshAttachmentModel(pojo, version);
        polyLodModel.setIsScope(display.isScope());
        polyLodModel.setIsSight(display.isSight());
        polyLodModel.loadPolyMesh(PolyMeshSupport.toGeoPath(lodModelId));
        try {
            Field lodField = ClientAttachmentIndex.class.getDeclaredField("lodModel");
            lodField.setAccessible(true);
            lodField.set(index, Pair.of(polyLodModel, currentLod.getRight()));
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] Failed to inject LOD poly_mesh model for attachment: {}", lodModelId, e);
        }
    }
}
