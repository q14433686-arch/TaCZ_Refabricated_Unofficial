package cn.sh1rocu.tacz.compat.meshloader.mixin;

import cn.sh1rocu.tacz.compat.meshloader.model.TaczPolyMeshBlockModel;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.index.ClientBlockIndex;
import com.tacz.guns.client.resource.pojo.display.block.BlockDisplay;
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
 * 方块模型替换：模型旁存在 {@code geo_models/<模型名>.json} 时，
 * 把方块模型替换为 {@link TaczPolyMeshBlockModel}。
 *
 * <p>注入点与上游 1.21.1_fabric 一致（{@code checkModel} 的 TAIL，
 * 字段经反射写入）。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
@Mixin(ClientBlockIndex.class)
public class ClientBlockIndexMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("TacZMeshLoader");

    @Inject(method = "checkModel", at = @At("TAIL"))
    private static void meshyloader$afterCheckModel(BlockDisplay display, ClientBlockIndex index, CallbackInfo ci) {
        Identifier modelId = display.getModelLocation();
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
        TaczPolyMeshBlockModel polyModel = new TaczPolyMeshBlockModel(pojo, version);
        polyModel.loadPolyMesh(geoPath, display.getModelTexture());
        try {
            Field field = findField(ClientBlockIndex.class, "model");
            field.setAccessible(true);
            field.set(index, polyModel);
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] Failed to inject block poly_mesh model", e);
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
