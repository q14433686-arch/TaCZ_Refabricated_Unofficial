package cn.sh1rocu.tacz.compat.meshloader.mixin;

import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSupport;
import cn.sh1rocu.tacz.compat.meshloader.model.TaczPolyMeshGunModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import com.tacz.guns.client.resource.pojo.display.gun.GunLod;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
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
 * <p>主模型：{@code GunModelTypeManager} 已按 model_type 构造了
 * {@link TaczPolyMeshGunModel} 实例（见 {@code TaczPolyMeshGunModel#register}），
 * 这里只负责在模型就位后灌入 geo 数据。LOD 模型没有 model_type 通道，
 * 按「旁边有同名 geo 就替换」的上游约定处理。</p>
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
                polyModel.loadPolyMesh(PolyMeshSupport.toGeoPath(modelId));
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
        if (!PolyMeshSupport.hasGeoModel(lodModelId)) {
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
        polyLodModel.loadPolyMesh(PolyMeshSupport.toGeoPath(lodModelId));
        this.lodModel = Pair.of(polyLodModel, this.lodModel.getRight());
    }
}
