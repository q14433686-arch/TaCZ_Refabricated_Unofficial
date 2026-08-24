package cn.sh1rocu.tacz.compat.meshloader.core;

import cn.sh1rocu.tacz.compat.meshloader.api.IPolyMeshBone;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

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
 * geo.json 路径约定、解析与共用缓存。
 *
 * <h2>解析缓存（v3 新增，修实机日志里的重复解析）</h2>
 *
 * <p>用户 2026-08-25 日志里每把 mesh 枪的 geo JSON 都被完整解析了<b>两次</b>
 *（{@code ak_enact} 365,848 顶点 ×2，两次 stats 行相隔约 1 秒，都在
 * {@code tacz-client-asset-preload} 线程）。高模 geo JSON 有几十 MB，重复解析
 * 直接把资源重载拖长数秒。</p>
 *
 * <p>解析结果（骨骼名 → {@link PolyMesh} 列表）在资源代际内不可变，因此按
 * geoPath 缓存共享；{@code PolyMeshModel} 实例只保留自己的骨骼树与排除集。
 * 缓存在客户端资源重载时整体失效（见 {@code TaczMeshyIntegration} 注册的
 * 重载监听器）。</p>
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

    // =====================================================================
    // 解析缓存
    // =====================================================================

    /** geoPath → 解析出的骨骼网格表（PolyMesh 不可变，跨模型实例共享）。 */
    private static final Map<Identifier, Map<String, List<PolyMesh>>> PARSE_CACHE = new ConcurrentHashMap<>();

    /** 本资源代际内已经打过 stats 日志的 geoPath（避免同 geo 多 display 重复刷屏）。 */
    private static final Set<Identifier> LOGGED_GEO = ConcurrentHashMap.newKeySet();

    /** 客户端资源重载时调用：解析缓存与日志去重集一并失效。 */
    public static void invalidateParseCache() {
        PARSE_CACHE.clear();
        LOGGED_GEO.clear();
    }

    /**
     * 原子地标记「这个 geo 本代际已经打过日志」。
     *
     * @return 第一次标记返回 true（该打日志），之后返回 false。
     */
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

    /** 解析一个 geo JSON 的全部 poly_mesh。文件不存在返回 null；无 poly_mesh 返回空表。 */
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
