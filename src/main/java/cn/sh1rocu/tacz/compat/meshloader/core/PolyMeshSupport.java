package cn.sh1rocu.tacz.compat.meshloader.core;

import cn.sh1rocu.tacz.compat.meshloader.api.IPolyMeshBone;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import javax.annotation.Nullable;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * geo.json 路径约定与加载共用逻辑。
 */
public final class PolyMeshSupport {

    private PolyMeshSupport() {
    }

    /** 模型 id → geo.json 资源路径（与 TACZ 的 {@code geo_models/} 目录约定一致）。 */
    public static Identifier toGeoPath(Identifier modelId) {
        return Identifier.fromNamespaceAndPath(modelId.getNamespace(), "geo_models/" + modelId.getPath() + ".json");
    }

    public static boolean hasGeoModel(Identifier modelId) {
        return Minecraft.getInstance().getResourceManager().getResource(toGeoPath(modelId)).isPresent();
    }

    @Nullable
    public static PolyMeshModel load(Identifier geoPath, Supplier<List<IPolyMeshBone>> rootChildren) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(geoPath);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonObject rawJson = JsonParser.parseReader(reader).getAsJsonObject();
            IPolyMeshBone adaptedRoot = dummyRoot(rootChildren);
            return new PolyMeshModel(adaptedRoot, rawJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse poly_mesh geo: " + geoPath, e);
        }
    }

    public static IPolyMeshBone dummyRoot(Supplier<List<IPolyMeshBone>> children) {
        return new IPolyMeshBone() {
            private List<IPolyMeshBone> cached;

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
                if (cached != null) {
                    return cached;
                }
                cached = children.get();
                return cached;
            }
        };
    }

    public static List<IPolyMeshBone> adaptShouldRender(BedrockModel model) {
        return model.getShouldRender().stream()
                .map(BedrockPartBoneAdapter::new)
                .collect(Collectors.toList());
    }
}
