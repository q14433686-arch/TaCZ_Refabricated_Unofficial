package cn.sh1rocu.tacz.compat.meshloader.mixin;

import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSupport;
import cn.sh1rocu.tacz.compat.meshloader.model.TaczPolyMeshBlockModel;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.index.ClientBlockIndex;
import com.tacz.guns.client.resource.pojo.display.block.BlockDisplay;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * 方块模型替换（自定义工作台等）。
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
@Mixin(ClientBlockIndex.class)
public class ClientBlockIndexMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("TacZMeshLoader");

    @Inject(method = "checkModel", at = @At("TAIL"))
    private static void meshyloader$afterCheckModel(BlockDisplay display, ClientBlockIndex index, CallbackInfo ci) {
        Identifier modelId = display.getModelLocation();
        if (modelId == null || !PolyMeshSupport.hasGeoModel(modelId)) {
            return;
        }
        BedrockModelPOJO pojo = ClientAssetsManager.INSTANCE.getBedrockModelPOJO(modelId);
        if (pojo == null) {
            return;
        }
        BedrockVersion version = BedrockVersion.isLegacyVersion(pojo) ? BedrockVersion.LEGACY : BedrockVersion.NEW;
        TaczPolyMeshBlockModel polyModel = new TaczPolyMeshBlockModel(pojo, version);
        polyModel.loadPolyMesh(PolyMeshSupport.toGeoPath(modelId), display.getModelTexture());
        try {
            Field field = ClientBlockIndex.class.getDeclaredField("model");
            field.setAccessible(true);
            field.set(index, polyModel);
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] Failed to inject block poly_mesh model", e);
        }
    }
}
