package cn.sh1rocu.tacz.compat.meshloader.mixin;

import cn.sh1rocu.tacz.compat.meshloader.model.TaczPolyMeshGunModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import com.tacz.guns.client.resource.pojo.display.gun.GunLod;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在枪械 display 加载完成后，为 {@code model_type: "mesh"} 的枪加载 poly_mesh。
 *
 * <p>注入点与上游 1.21.1_fabric 版一致（{@code checkTextureAndModel} /
 * {@code checkLod} 的 TAIL），仅做了 26.2 的类型适配
 * （{@code ResourceLocation → Identifier}、直接赋值替代反射）。
 * LOD 模型额外固定 LOD 贴图（{@code setOverrideTexture}）。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
@Mixin(GunDisplayInstance.class)
public class GunDisplayInstanceMixin {

    @Shadow
    private BedrockGunModel gunModel;

    @Shadow
    private Pair<BedrockGunModel, Identifier> lodModel;

    @Inject(method = "checkTextureAndModel", at = @At("TAIL"))
    private void meshyloader$afterCheckTextureAndModel(GunDisplay display, CallbackInfo ci) {
        if (this.gunModel instanceof TaczPolyMeshGunModel polyModel) {
            Identifier modelId = display.getModelLocation();
            if (modelId != null) {
                // loadPolyMesh 内部会检查 geo 资源是否存在
                polyModel.loadPolyMesh(toGeoPath(modelId));
            }
        }
    }

    @Inject(method = "checkLod", at = @At("TAIL"))
    private void meshyloader$afterCheckLod(GunDisplay display, CallbackInfo ci) {
        if (this.lodModel == null) {
            return;
        }
        GunLod gunLod = display.getGunLod();
        if (gunLod == null || gunLod.getModelLocation() == null) {
            return;
        }
        Identifier lodModelId = gunLod.getModelLocation();
        Identifier geoPath = toGeoPath(lodModelId);
        if (!hasGeoModel(lodModelId)) {
            return;
        }
        BedrockModelPOJO modelPOJO = ClientAssetsManager.INSTANCE.getBedrockModelPOJO(lodModelId);
        if (modelPOJO == null) {
            return;
        }
        BedrockVersion version = BedrockVersion.isLegacyVersion(modelPOJO)
                ? BedrockVersion.LEGACY : BedrockVersion.NEW;
        TaczPolyMeshGunModel polyLodModel = new TaczPolyMeshGunModel(modelPOJO, version);
        polyLodModel.setOverrideTexture(this.lodModel.getRight());
        polyLodModel.loadPolyMesh(geoPath);
        this.lodModel = Pair.of(polyLodModel, this.lodModel.getRight());
    }

    /** 模型 id → geo.json 资源路径（与 TACZ 的 geo_models 目录约定一致）。 */
    private static Identifier toGeoPath(Identifier modelId) {
        return Identifier.fromNamespaceAndPath(modelId.getNamespace(), "geo_models/" + modelId.getPath() + ".json");
    }

    private static boolean hasGeoModel(Identifier modelId) {
        return Minecraft.getInstance().getResourceManager().getResource(toGeoPath(modelId)).isPresent();
    }
}
