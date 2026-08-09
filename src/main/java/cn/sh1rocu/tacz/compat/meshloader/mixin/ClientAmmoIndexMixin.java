package cn.sh1rocu.tacz.compat.meshloader.mixin;

import cn.sh1rocu.tacz.compat.meshloader.model.TaczPolyMeshAmmoModel;
import com.tacz.guns.client.model.BedrockAmmoModel;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.client.resource.pojo.display.ammo.AmmoDisplay;
import com.tacz.guns.client.resource.pojo.display.ammo.AmmoEntityDisplay;
import com.tacz.guns.client.resource.pojo.display.ammo.ShellDisplay;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * 弹药模型替换：模型旁存在 {@code geo_models/<模型名>.json} 时，
 * 把 ammo / ammo_entity / shell 三个模型替换为 {@link TaczPolyMeshAmmoModel}。
 *
 * <p>注入点与上游 1.21.1_fabric 一致（static 方法 TAIL，字段经反射写入，
 * 26.2 字段类型为 {@code BedrockAmmoModel}，其余仅类型适配）。</p>
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
        if (modelId == null) {
            return;
        }
        Identifier geoPath = toGeoPath(modelId);
        if (Minecraft.getInstance().getResourceManager().getResource(geoPath).isEmpty()) {
            return;
        }
        BedrockModelPOJO pojo = ClientAssetsManager.INSTANCE.getBedrockModelPOJO(modelId);
        if (pojo == null) {
            return;
        }
        BedrockVersion version = BedrockVersion.isLegacyVersion(pojo) ? BedrockVersion.LEGACY : BedrockVersion.NEW;
        TaczPolyMeshAmmoModel polyModel = new TaczPolyMeshAmmoModel(pojo, version);
        polyModel.loadPolyMesh(geoPath, texture);
        try {
            Field field = findField(ClientAmmoIndex.class, fieldName);
            field.setAccessible(true);
            field.set(index, polyModel);
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] Failed to inject ammo poly_mesh model into '{}'", fieldName, e);
        }
    }

    /** 模型 id → geo.json 资源路径（与 TACZ 的 geo_models 目录约定一致）。 */
    private static Identifier toGeoPath(Identifier modelId) {
        return Identifier.fromNamespaceAndPath(modelId.getNamespace(), "geo_models/" + modelId.getPath() + ".json");
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name + " not found in " + clazz.getName());
    }
}
