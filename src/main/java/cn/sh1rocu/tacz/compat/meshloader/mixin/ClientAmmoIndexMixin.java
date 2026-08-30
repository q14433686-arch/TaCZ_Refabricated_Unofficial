package cn.sh1rocu.tacz.compat.meshloader.mixin;

import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSupport;
import cn.sh1rocu.tacz.compat.meshloader.model.TaczPolyMeshAmmoModel;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.client.resource.pojo.display.ammo.AmmoDisplay;
import com.tacz.guns.client.resource.pojo.display.ammo.AmmoEntityDisplay;
import com.tacz.guns.client.resource.pojo.display.ammo.ShellDisplay;
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
 * 弹药模型替换（物品/掉落实体/抛壳三个通道）。
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
@Mixin(ClientAmmoIndex.class)
public class ClientAmmoIndexMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("TacZMeshLoader");

    @Inject(method = "checkTextureAndModel", at = @At("TAIL"))
    private static void meshyloader$afterCheckTextureAndModel(AmmoDisplay display, ClientAmmoIndex index, CallbackInfo ci) {
        replaceAmmoModel(display.getModelLocation(), display.getModelTexture(), index, "ammoModel");
    }

    @Inject(method = "checkAmmoEntity", at = @At("TAIL"))
    private static void meshyloader$afterCheckAmmoEntity(AmmoDisplay display, ClientAmmoIndex index, CallbackInfo ci) {
        AmmoEntityDisplay entityDisplay = display.getAmmoEntity();
        if (entityDisplay == null) {
            return;
        }
        replaceAmmoModel(entityDisplay.getModelLocation(), entityDisplay.getModelTexture(), index, "ammoEntityModel");
    }

    @Inject(method = "checkShell", at = @At("TAIL"))
    private static void meshyloader$afterCheckShell(AmmoDisplay display, ClientAmmoIndex index, CallbackInfo ci) {
        ShellDisplay shellDisplay = display.getShellDisplay();
        if (shellDisplay == null) {
            return;
        }
        replaceAmmoModel(shellDisplay.getModelLocation(), shellDisplay.getModelTexture(), index, "shellModel");
    }

    private static void replaceAmmoModel(Identifier modelId, Identifier texture,
                                         ClientAmmoIndex index, String fieldName) {
        if (modelId == null || !PolyMeshSupport.hasGeoModel(modelId)) {
            return;
        }
        BedrockModelPOJO pojo = ClientAssetsManager.INSTANCE.getBedrockModelPOJO(modelId);
        if (pojo == null) {
            return;
        }
        BedrockVersion version = BedrockVersion.isLegacyVersion(pojo) ? BedrockVersion.LEGACY : BedrockVersion.NEW;
        TaczPolyMeshAmmoModel polyModel = new TaczPolyMeshAmmoModel(pojo, version);
        polyModel.loadPolyMesh(PolyMeshSupport.toGeoPath(modelId), texture);
        try {
            Field field = ClientAmmoIndex.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(index, polyModel);
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] Failed to inject ammo poly_mesh model into '{}'", fieldName, e);
        }
    }
}
