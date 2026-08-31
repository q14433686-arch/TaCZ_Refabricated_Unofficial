package cn.sh1rocu.tacz.compat.meshloader.core;

import cn.sh1rocu.tacz.compat.meshloader.api.IPolyMeshBone;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import javax.annotation.Nullable;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * geo.json 路径约定、解析缓存。
 *
 * <p>解析缓存修复用户 2026-08-25 实机 log 实证的「每枪 geo JSON 双遍解析」
 * （GunDisplayInstance 主模型 + LOD 各触发一次，多枪同 geo 时更多）。
 * 缓存以 geo 路径为键存共享网格数据（float 数组只读），资源重载时由
 * {@code TaczMeshyIntegration} 注册的监听器整体失效。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
public final class PolyMeshSupport {

    private PolyMeshSupport() {
    }

    public static Identifier toGeoPath(Identifier modelId) {
        return Identifier.fromNamespaceAndPath(modelId.getNamespace(), "geo_models/" + modelId.getPath() + ".json");
    }

    public static boolean hasGeoModel(Identifier modelId) {
        return Minecraft.getInstance().getResourceManager().getResource(toGeoPath(modelId)).isPresent();
    }

    private static final Map<Identifier, Map<String, List<PolyMesh>>> PARSE_CACHE = new ConcurrentHashMap<>();
    private static final Set<Identifier> LOGGED_GEO = ConcurrentHashMap.newKeySet();

    public static void invalidateParseCache() {
        PARSE_CACHE.clear();
        LOGGED_GEO.clear();
    }

    /** @return true 表示这一 geo 首次记录（用于按 geo 去重的统计日志）。 */
    public static boolean markGeoLogged(Identifier geoPath) {
        return LOGGED_GEO.add(geoPath);
    }

    @Nullable
    public static PolyMeshModel load(Identifier geoPath, Supplier<List<IPolyMeshBone>> rootChildren) {
        Map<String, List<PolyMesh>> meshMap = PARSE_CACHE.get(geoPath);
        if (meshMap == null) {
            meshMap = parseMeshMap(geoPath);
            if (meshMap == null) {
                return null;
            }
            PARSE_CACHE.put(geoPath, meshMap);
        }
        return new PolyMeshModel(dummyRoot(rootChildren), meshMap);
    }

    @Nullable
    private static Map<String, List<PolyMesh>> parseMeshMap(Identifier geoPath) {
        var resourceOpt = Minecraft.getInstance().getResourceManager().getResource(geoPath);
        if (resourceOpt.isEmpty()) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(resourceOpt.get().open(), StandardCharsets.UTF_8)) {
            JsonObject rawJson = JsonParser.parseReader(reader).getAsJsonObject();
            return PolyMeshModel.parseMeshMapFromJson(rawJson);
        } catch (Exception e) {
            GunMod.LOGGER.error("[TacZMeshLoader] Failed to parse poly_mesh geo: {}", geoPath, e);
            return null;
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
