package cn.sh1rocu.tacz.compat.meshloader.model;

import cn.sh1rocu.tacz.compat.meshloader.api.IPolyMeshBone;
import cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig;
import cn.sh1rocu.tacz.compat.meshloader.config.PolyRenderPolicy;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMesh;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshModel;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSnapshot;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSupport;
import cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.other.GunModelTypeManager;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.GunModelConstant;
import com.tacz.guns.client.model.IMirrorGeometry;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import com.tacz.guns.util.RenderDistance;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 支持 poly_mesh 的枪械模型。
 *
 * <h2>弹匣（关 PR #70 的教训）</h2>
 * 上游 TML 在 {@code loadPolyMesh} 之后把 {@code additional_magazine} 的
 * FunctionalRenderer 包一层：立方体走原 {@code IMirrorGeometry}，poly 在
 * {@code additional_magazine.visible} 时按该节点变换再画一遍 {@code magazine}。
 * 关 PR #70 没接这条链路，纯 mesh 枪的弹匣会丢。
 *
 * <p>26.2 快照遍历器认 {@link IMirrorGeometry} 画立方体镜像。本类保留那条路径，
 * 再按原版 TML 在主 submit 里补 poly：
 * <ol>
 *   <li>exclude {@code additional_magazine} 子树，避免主遍历把它画在错误位置；</li>
 *   <li>{@code super.submit} 照常走立方体 + {@code IMirrorGeometry}；</li>
 *   <li>主 poly（含 {@code magazine}）走 GPU（第一人称手部 pass、无光影）
 *       或 collector（其余一切场景）；</li>
 *   <li>{@code additional_magazine.visible} 时在该节点变换下再提交 magazine /
 *       additional_magazine 的 poly（与上游 TML 的 {@code renderSubtreeDirect} 同构）。</li>
 * </ol>
 *
 * <h2>GPU 路径（O(顶点)→O(骨骼)，见 {@link PolyMeshGpuRenderer}）</h2>
 * <p>{@code shouldSubmitGpu}=true 时顶点常驻 GPU（骨骼本地系 + light 烘进顶点），
 * 每帧只登记「骨骼矩阵 + 纹理 + VBO」，绘制发生在手部 executeSolid 之后。
 * translucent 骨骼不烘（混合顺序交给 collector 的排序），换弹 additional_magazine
 * 恒走 collector（矩阵语义不同且不是顶点热点）。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
public class TaczPolyMeshGunModel extends BedrockGunModel {

    private static final Logger LOGGER = LoggerFactory.getLogger("TacZMeshLoader");

    private PolyMeshModel polyMeshModel;
    private Identifier cachedTexture = null;
    private Identifier overrideTexture = null;
    private List<IPolyMeshBone> cachedRootChildren = null;
    private boolean cachedHasMagMesh = false;
    private boolean cachedHasAdditionalMagMesh = false;
    private List<BedrockPart> cachedAdditionalMagazinePath = null;
    private final Map<String, PolyMeshGpuRenderer.BakedBone> bakedBones = new HashMap<>();
    private int bakedLightKey = -1;
    private int bakedGeneration = -1;
    private long lastRebakeMs = 0L;
    private boolean gpuBaked = false;
    /**
     * 世界语境的烘焙缓存：量化光照档 → 该档的整套骨骼 VBO。
     *
     * <p>access-order 的 {@link LinkedHashMap} 当 LRU 用（上游 TML 的
     * {@code PolyMesh#vboCache} 同构，它按未量化 light 缓存 8 档；我们先量化
     * 再缓存，{@code MeshGpuLightCacheSize} 档就够覆盖同屏光照差异）。
     * 逐出的 VBO 走 {@link PolyMeshGpuRenderer#releaseDeferred}（下一帧才 close）
     * —— 本帧可能已有 WORLD_DRAWS 条目引用它，当场 close 是悬空引用。</p>
     *
     * <p>第一人称的 {@link #bakedBones} 单档缓存保持原样不动：手上只有一把枪、
     * 光照单值，单档 + 1 秒节流已实测 PASS，没必要合并进 LRU 添变数。</p>
     */
    private final java.util.LinkedHashMap<Integer, Map<String, PolyMeshGpuRenderer.BakedBone>> worldBakedByLight =
            new java.util.LinkedHashMap<>(8, 0.75f, true);
    private int worldBakedGeneration = -1;
    private boolean loggedFirstSubmit = false;
    private boolean loggedGuiVertexCap = false;
    private boolean loggedWorldVertexCap = false;
    private boolean loggedDenseModel = false;

    public TaczPolyMeshGunModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
    }

    @Override
    public void submit(PoseStack poseStack, ItemStack gunItem, ItemDisplayContext transformType,
                       SubmitNodeCollector collector, RenderType renderType, int light, int overlay) {
        submit(poseStack, gunItem, transformType, collector, renderType, null, light, overlay);
    }

    @Override
    public void submit(PoseStack poseStack, ItemStack gunItem, ItemDisplayContext transformType,
                       SubmitNodeCollector collector, RenderType renderType,
                       @javax.annotation.Nullable Identifier gunTexture, int light, int overlay) {
        if (!hasPolyMesh()) {
            super.submit(poseStack, gunItem, transformType, collector, renderType, gunTexture, light, overlay);
            return;
        }

        // additional_magazine 由 IMirrorGeometry + submitAdditionalMagazinePoly 处理，
        // 主遍历必须排除，否则换弹中它会同时出现在两个位置。
        if (cachedHasAdditionalMagMesh) {
            polyMeshModel.setExcludeSubtree(GunModelConstant.MAG_ADDITIONAL_NODE);
        } else {
            polyMeshModel.clearExcludeSubtree();
        }

        super.submit(poseStack, gunItem, transformType, collector, renderType, gunTexture, light, overlay);

        if (!PolyRenderPolicy.shouldRenderPoly(transformType, poseStack)) {
            return;
        }

        Identifier texture = gunTexture != null ? gunTexture : resolveTexture(gunItem);
        if (texture == null) {
            if (!loggedFirstSubmit) {
                loggedFirstSubmit = true;
                LOGGER.warn("[TacZMeshLoader] poly submit skipped: no texture (firstPerson={})",
                        transformType != null && transformType.firstPerson());
            }
            return;
        }

        boolean gpu = PolyMeshGpuRenderer.shouldSubmitGpu() && ensureBaked(texture, light);
        // 世界语境（第三人称/掉落物/展示框/展示台）的 GPU 路径。两级闸门：
        // ① isWorldGpuContext —— 按 transformType 排除 GUI 系语境。热栏图标
        //    以 GUI 语境在 HUD 提取（没有 Screen！ScreenRenderTracker 拦不住），
        //    带着 GUI 投影的 pose 落进世界表就是关 PR #33 的「枪画进世界」；
        // ② shouldSubmitGpuWorld —— Screen 提取窗口/镜内/阴影/手部（提交侧防泄漏）。
        Map<String, PolyMeshGpuRenderer.BakedBone> worldBaked = null;
        if (!gpu && isWorldGpuContext(transformType) && PolyMeshGpuRenderer.shouldSubmitGpuWorld()) {
            worldBaked = ensureWorldBaked(light);
        }

        // 顶点预算只保护【collector 路径】—— 它防的是 O(顶点) 的 CPU 提交成本。
        // GPU 路径每帧只传 O(骨骼) 个矩阵，预算对它没有保护对象；反过来，
        // 预算若在这里拦掉 GPU 路径，16 格外的高模枪照旧整层消失，
        // 「多人一堆高模枪」的问题就没解决。烘焙失败/额度耗尽回退 collector 时，
        // 预算恢复生效（正是它该保护的场景）。第一人称从不受预算限制（原语义）。
        if (!gpu && worldBaked == null && !withinContextBudget(transformType, poseStack)) {
            return;
        }
        if (!loggedFirstSubmit) {
            loggedFirstSubmit = true;
            LOGGER.info("[TacZMeshLoader] poly submit: bones={} verts={} gpu={} gpuWorld={} firstPerson={} texture={}",
                    polyMeshModel.getMeshBoneCount(),
                    polyMeshModel.getTotalVertexCount(),
                    gpu,
                    worldBaked != null,
                    transformType != null && transformType.firstPerson(),
                    texture);
        }

        if (gpu) {
            // GPU：每骨骼登记一条「矩阵 + VBO」。visitor 返回 true 继续下潜 ——
            // 返回 false 是「剪掉子树」而不是「这根不画」（关 PR #33 的坑）。
            polyMeshModel.visitBones(poseStack, true, (boneName, bonePose) -> {
                if (polyMeshModel.isTranslucentBone(boneName)) {
                    return true;
                }
                PolyMeshGpuRenderer.BakedBone baked = bakedBones.get(boneName);
                if (baked != null) {
                    PolyMeshGpuRenderer.submitBone(new Matrix4f(bonePose.last().pose()), texture, baked);
                }
                return true;
            });
            // translucent 骨骼仍走 collector（排序混合），cutout 已由 GPU 覆盖。
            PolyMeshSnapshot translucentOnly = polyMeshModel.capture(poseStack, light, this::isGpuBone);
            submitPolyMeshTranslucent(translucentOnly, collector, texture, overlay);
        } else if (worldBaked != null) {
            // 世界 GPU：与手部同构 —— 每骨骼一条「矩阵 + VBO」进世界表，
            // 帧末在主世界那次 renderAllFeatures 的 executeSolid 之后统一绘制。
            // 同型号多把枪共享本模型实例 = 共享同一套 VBO，各自登记各自的矩阵。
            final Map<String, PolyMeshGpuRenderer.BakedBone> baked = worldBaked;
            polyMeshModel.visitBones(poseStack, true, (boneName, bonePose) -> {
                if (polyMeshModel.isTranslucentBone(boneName)) {
                    return true;
                }
                PolyMeshGpuRenderer.BakedBone bone = baked.get(boneName);
                if (bone != null) {
                    PolyMeshGpuRenderer.submitBoneWorld(new Matrix4f(bonePose.last().pose()), texture, bone);
                }
                return true;
            });
            PolyMeshSnapshot translucentOnly = polyMeshModel.capture(poseStack, light,
                    boneName -> baked.containsKey(boneName) && !polyMeshModel.isTranslucentBone(boneName));
            submitPolyMeshTranslucent(translucentOnly, collector, texture, overlay);
        } else {
            submitPolyMesh(polyMeshModel.capture(poseStack, light), collector, texture, overlay);
        }

        submitAdditionalMagazinePoly(poseStack, collector, texture, overlay, light);
    }

    /**
     * 换弹时留在枪上的那份弹匣。立方体已由 {@link IMirrorGeometry} 处理；
     * 这里只补 poly，且仅在 {@code additional_magazine.visible} 时画。
     *
     * <p>captureSubtree 的矩阵语义与上游 TML {@code renderSubtreeDirect} 一致：
     * 该节点及其祖先的变换先由调用方乘进 pose，根骨骼自身不再套变换。</p>
     *
     * <p><b>始终走 collector</b>：换弹弹匣不是 36 万顶点热点，且 mirrorRoot
     * 的矩阵语义与 visitBones 不同 —— 硬套 GPU 路径会把枪树变换乘两遍。</p>
     */
    private void submitAdditionalMagazinePoly(PoseStack poseStack, SubmitNodeCollector collector,
                                              Identifier texture, int overlay, int light) {
        BedrockPart additionalMagazine = getAdditionalMagazineNode();
        if (additionalMagazine == null || !additionalMagazine.visible
                || (!cachedHasMagMesh && !cachedHasAdditionalMagMesh)) {
            return;
        }
        PoseStack magazinePose = new PoseStack();
        magazinePose.last().pose().set(poseStack.last().pose());
        magazinePose.last().normal().set(poseStack.last().normal());
        List<BedrockPart> path = cachedAdditionalMagazinePath;
        if (path == null) {
            path = pathToRoot(additionalMagazine);
            cachedAdditionalMagazinePath = path;
        }
        for (BedrockPart part : path) {
            part.translateAndRotateAndScale(magazinePose);
        }
        if (cachedHasMagMesh) {
            submitPolyMesh(polyMeshModel.captureSubtree(
                    GunModelConstant.MAG_NORMAL_NODE, magazinePose, light, true),
                    collector, texture, overlay);
        }
        if (cachedHasAdditionalMagMesh) {
            submitPolyMesh(polyMeshModel.captureSubtree(
                    GunModelConstant.MAG_ADDITIONAL_NODE, magazinePose, light, true),
                    collector, texture, overlay);
        }
    }

    /** 该骨骼的 cutout 是否已由 GPU 覆盖（capture 时跳过，避免画两遍）。 */
    private boolean isGpuBone(String boneName) {
        return bakedBones.containsKey(boneName) && !polyMeshModel.isTranslucentBone(boneName);
    }

    /**
     * 这个 transformType 是不是「世界 GPU 表」可以收的语境。
     *
     * <ul>
     *   <li>{@code GUI} 一律不收 —— 热栏/背包图标在 HUD 提取阶段跑，
     *       <b>没有 Screen</b>，{@code ScreenRenderTracker} 拦不住它，
     *       只能按语境挡；GUI 的 pose 是正交投影，进世界表必画错；</li>
     *   <li>{@code FIXED}/{@code HEAD} 是双面语境（世界展示框/雕像 vs 枪匠桌
     *       GUI 预览）：Screen 内的预览已被 {@code ScreenRenderTracker} 拦，
     *       这里再按 {@code RenderDistance.isGuiRender()}（枪匠桌标记的 100ms
     *       时间戳）补一道 —— 代价只是「枪匠桌开着的瞬间世界雕像回退 collector」，
     *       比反过来（GUI 预览泄漏进世界表）便宜得多；</li>
     *   <li>第一人称语境永远轮不到这里 —— 手部 pass 里
     *       {@code shouldSubmitGpuWorld} 的 isInHandPass 闸门直接拒收。</li>
     * </ul>
     */
    private static boolean isWorldGpuContext(ItemDisplayContext transformType) {
        if (transformType == null || transformType == ItemDisplayContext.GUI) {
            return false;
        }
        if (transformType == ItemDisplayContext.FIXED || transformType == ItemDisplayContext.HEAD) {
            return !RenderDistance.isGuiRender();
        }
        return !transformType.firstPerson();
    }

    private boolean withinContextBudget(ItemDisplayContext transformType, PoseStack poseStack) {
        if (transformType == ItemDisplayContext.GUI
                || transformType == ItemDisplayContext.FIXED
                || transformType == ItemDisplayContext.HEAD) {
            // FIXED/HEAD 是双面语境：既出现在枪匠桌 GUI 预览（100ms 时间戳内），
            // 也出现在世界里的展示台雕像/物品展示框/背枪。只有世界侧允许按
            // 相机距离豁免——展示台上的高模枪正是走 FIXED 被 GUI 预算拦掉的。
            if (transformType != ItemDisplayContext.GUI
                    && !RenderDistance.isGuiRender()
                    && PolyRenderPolicy.withinFullDetailDistance(poseStack)) {
                return true;
            }
            return withinVertexBudget(MeshyConfig.GUI_MAX_VERTICES.get(), true);
        }
        if (transformType != null && !transformType.firstPerson()) {
            // 近距离全模豁免：眼前的第三人称/掉落物/展示台（NONE/GROUND/
            // THIRD_PERSON_*）不受顶点预算限制，否则无 LOD 的高模枪在玩家
            // 面前直接整层消失、只剩立方体。预算只保护远处/密集场景。
            if (PolyRenderPolicy.withinFullDetailDistance(poseStack)) {
                return true;
            }
            return withinVertexBudget(MeshyConfig.WORLD_MAX_VERTICES.get(), false);
        }
        return true;
    }

    private boolean withinVertexBudget(int cap, boolean gui) {
        if (cap <= 0) {
            return true;
        }
        int total = polyMeshModel == null ? 0 : polyMeshModel.getTotalVertexCount();
        if (total <= cap) {
            return true;
        }
        if (gui && !loggedGuiVertexCap) {
            loggedGuiVertexCap = true;
            LOGGER.info("[TacZMeshLoader] poly preview suppressed in GUI: {} vertices exceeds MeshGuiMaxVertices={}",
                    total, cap);
        } else if (!gui && !loggedWorldVertexCap) {
            loggedWorldVertexCap = true;
            LOGGER.info("[TacZMeshLoader] poly suppressed in world context: {} vertices exceeds MeshWorldMaxVertices={}",
                    total, cap);
        }
        return false;
    }

    private void submitPolyMeshTranslucent(PolyMeshSnapshot snapshot, SubmitNodeCollector collector,
                                           Identifier texture, int overlay) {
        if (!snapshot.hasTranslucent()) {
            return;
        }
        collector.submitCustomGeometry(new PoseStack(), RenderTypes.entityTranslucent(texture),
                (entryPose, consumer) -> snapshot.writeTranslucent(consumer, overlay));
    }

    /**
     * 确保当前光照档的骨骼 VBO 已就绪。
     *
     * <p>光照被 {@link PolyMeshGpuRenderer#quantizeLight} 量化成 4 级档位烘进顶点；
     * 跨档才重烘，且有 1 秒节流 —— 光照剧变（进出洞穴）最多滞后 1 秒，
     * 换来的是稳态零重烘。illuminated 骨骼恒烘 FULL_BRIGHT，与 collector 语义一致。</p>
     */
    private boolean ensureBaked(Identifier texture, int currentLight) {
        if (polyMeshModel == null) {
            return false;
        }
        int lightKey = PolyMeshGpuRenderer.quantizeLight(currentLight);
        int generation = PolyMeshGpuRenderer.getBakeGeneration();
        if (gpuBaked) {
            if (generation != bakedGeneration) {
                // 光影包开关翻转：Iris 激活与否改变实体顶点格式的写出布局，
                // 旧 VBO 在新管线下属性错位（模型拉伸）。立即重烘，
                // 不受下面的光照档 1 秒节流约束——旧 buffer 一帧都不能再用。
                releaseBaked();
            } else if (lightKey == bakedLightKey) {
                return true;
            } else {
                long now = System.currentTimeMillis();
                if (now - lastRebakeMs < 1000L) {
                    // 节流窗口内先用旧光照档画，避免闪烁边界上逐帧重烘。
                    return true;
                }
                releaseBaked();
            }
        }
        boolean allOk = true;
        for (Map.Entry<String, List<PolyMesh>> entry : polyMeshModel.getMeshMap().entrySet()) {
            String boneName = entry.getKey();
            if (polyMeshModel.isTranslucentBone(boneName)) {
                continue;
            }
            // 光影下发光骨骼 sky 列走环境真值（IlluminatedRealSky，两层同一开关）。
            // resolve 依赖 lightKey 的 sky 列 —— 烘焙缓存本就按 lightKey 分档，
            // 每档各烘各的，缓存正确性不受影响。
            int boneLight = polyMeshModel.isIlluminatedBone(boneName)
                    ? com.tacz.guns.util.IlluminatedLights.resolve(lightKey) : lightKey;
            PolyMeshGpuRenderer.BakedBone baked = PolyMeshGpuRenderer.bakeBone(entry.getValue(), boneLight);
            if (baked == null) {
                allOk = false;
                continue;
            }
            bakedBones.put(boneName, baked);
        }
        gpuBaked = allOk && !bakedBones.isEmpty();
        if (gpuBaked) {
            bakedLightKey = lightKey;
            bakedGeneration = generation;
            lastRebakeMs = System.currentTimeMillis();
            LOGGER.info("[TacZMeshLoader] GPU-baked {} bones ({} vertices) for {} at quantized light {}",
                    bakedBones.size(), polyMeshModel.getTotalVertexCount(), texture,
                    Integer.toHexString(lightKey));
        } else {
            // 部分失败宁可整体回 collector：半 GPU 半 collector 的 cutout 集合
            // 难以对账（哪根骨骼谁画的说不清），干脆二选一。
            releaseBaked();
        }
        return gpuBaked;
    }

    /**
     * 世界语境版 {@link #ensureBaked}：按量化光照档 LRU 缓存整套骨骼 VBO。
     *
     * <p>与第一人称单档缓存的三点差异：</p>
     * <ol>
     *   <li><b>多档共存</b>：同屏的掉落枪/其他玩家光照各不相同，单档会互相
     *       触发重烘打摆。LRU 容量 {@code MeshGpuLightCacheSize}（默认 4），
     *       量化(4 级步进 ⇒ block/sky 各 4 档)后同屏超过 4 档的场景极罕见；</li>
     *   <li><b>烘焙额度</b>：{@link PolyMeshGpuRenderer#tryReserveBake} 限制
     *       每帧新烘焙次数 —— 光照档数超过 LRU 容量的病理场景下宁可让部分枪
     *       回退 collector 一帧，也不许「逐出-重烘」逐帧打摆；</li>
     *   <li><b>延迟释放</b>：逐出的 VBO 交 {@link PolyMeshGpuRenderer#releaseDeferred}
     *       下一帧 close —— 本帧的 WORLD_DRAWS 可能已引用它（同型号两把枪
     *       一先一后 submit，前者登记、后者触发逐出）。</li>
     * </ol>
     *
     * @return 该光照档的骨骼 VBO 表；无法就绪（额度耗尽/烘焙失败）返回 null，
     *         调用方回退 collector。
     */
    @javax.annotation.Nullable
    private Map<String, PolyMeshGpuRenderer.BakedBone> ensureWorldBaked(int currentLight) {
        if (polyMeshModel == null) {
            return null;
        }
        int generation = PolyMeshGpuRenderer.getBakeGeneration();
        if (generation != worldBakedGeneration) {
            // 光影开关翻转：顶点布局随管线变化，全部档位一律作废
            // （与第一人称 bakedGeneration 同一根因，见 PolyMeshGpuRenderer#bakeGeneration）。
            releaseWorldBaked();
            worldBakedGeneration = generation;
        }
        int lightKey = PolyMeshGpuRenderer.quantizeLight(currentLight);
        Map<String, PolyMeshGpuRenderer.BakedBone> cached = worldBakedByLight.get(lightKey);
        if (cached != null) {
            return cached;
        }
        if (!PolyMeshGpuRenderer.tryReserveBake()) {
            return null;
        }
        Map<String, PolyMeshGpuRenderer.BakedBone> bones = new HashMap<>();
        boolean allOk = true;
        for (Map.Entry<String, List<PolyMesh>> entry : polyMeshModel.getMeshMap().entrySet()) {
            String boneName = entry.getKey();
            if (polyMeshModel.isTranslucentBone(boneName)) {
                continue;
            }
            // 光影下发光骨骼 sky 列走环境真值（IlluminatedRealSky，两层同一开关）。
            // resolve 依赖 lightKey 的 sky 列 —— 烘焙缓存本就按 lightKey 分档，
            // 每档各烘各的，缓存正确性不受影响。
            int boneLight = polyMeshModel.isIlluminatedBone(boneName)
                    ? com.tacz.guns.util.IlluminatedLights.resolve(lightKey) : lightKey;
            PolyMeshGpuRenderer.BakedBone baked = PolyMeshGpuRenderer.bakeBone(entry.getValue(), boneLight);
            if (baked == null) {
                allOk = false;
                break;
            }
            bones.put(boneName, baked);
        }
        if (!allOk || bones.isEmpty()) {
            // 半套缓存没有意义（哪根骨骼谁画的对不上账），全释放、本档回 collector。
            for (PolyMeshGpuRenderer.BakedBone baked : bones.values()) {
                PolyMeshGpuRenderer.releaseDeferred(baked);
            }
            return null;
        }
        worldBakedByLight.put(lightKey, bones);
        int cap = Math.max(1, MeshyConfig.GPU_LIGHT_CACHE_SIZE.get());
        while (worldBakedByLight.size() > cap) {
            // access-order LinkedHashMap：迭代器首位即最久未访问档。
            var it = worldBakedByLight.entrySet().iterator();
            Map<String, PolyMeshGpuRenderer.BakedBone> evicted = it.next().getValue();
            it.remove();
            for (PolyMeshGpuRenderer.BakedBone baked : evicted.values()) {
                PolyMeshGpuRenderer.releaseDeferred(baked);
            }
        }
        LOGGER.info("[TacZMeshLoader] GPU world-baked {} bones ({} vertices) at quantized light {} ({} levels cached)",
                bones.size(), polyMeshModel.getTotalVertexCount(),
                Integer.toHexString(lightKey), worldBakedByLight.size());
        return bones;
    }

    private void releaseWorldBaked() {
        for (Map<String, PolyMeshGpuRenderer.BakedBone> level : worldBakedByLight.values()) {
            for (PolyMeshGpuRenderer.BakedBone baked : level.values()) {
                PolyMeshGpuRenderer.releaseDeferred(baked);
            }
        }
        worldBakedByLight.clear();
    }

    private void releaseBaked() {
        for (PolyMeshGpuRenderer.BakedBone baked : bakedBones.values()) {
            baked.close();
        }
        bakedBones.clear();
        gpuBaked = false;
        bakedLightKey = -1;
        bakedGeneration = -1;
    }

    private void submitPolyMesh(PolyMeshSnapshot snapshot, SubmitNodeCollector collector,
                                Identifier texture, int overlay) {
        if (snapshot.isEmpty()) {
            return;
        }
        collector.submitCustomGeometry(new PoseStack(), RenderTypes.entityCutout(texture),
                (entryPose, consumer) -> snapshot.writeCutout(consumer, overlay));
        if (snapshot.hasTranslucent()) {
            collector.submitCustomGeometry(new PoseStack(), RenderTypes.entityTranslucent(texture),
                    (entryPose, consumer) -> snapshot.writeTranslucent(consumer, overlay));
        }
    }

    private Identifier resolveTexture(ItemStack gunItem) {
        if (overrideTexture != null) {
            return overrideTexture;
        }
        if (cachedTexture != null) {
            return cachedTexture;
        }
        Optional<GunDisplayInstance> display = TimelessAPI.getGunDisplay(gunItem);
        if (display.isPresent()) {
            cachedTexture = display.get().getModelTexture();
        }
        return cachedTexture;
    }

    public void setOverrideTexture(Identifier texture) {
        this.overrideTexture = texture;
        this.cachedTexture = null;
    }

    public void loadPolyMesh(Identifier geoPath) {
        // 换模型必须先放掉旧 VBO —— 资源重载会走到这里，不放就泄漏 GPU 内存。
        releaseBaked();
        releaseWorldBaked();
        try {
            this.cachedRootChildren = null;
            this.polyMeshModel = PolyMeshSupport.load(geoPath, () -> {
                if (cachedRootChildren != null) {
                    return cachedRootChildren;
                }
                cachedRootChildren = PolyMeshSupport.adaptShouldRender(this);
                return cachedRootChildren;
            });
            if (this.polyMeshModel == null) {
                return;
            }
            this.cachedTexture = null;
            this.cachedHasMagMesh = this.polyMeshModel.hasMeshInSubtree(GunModelConstant.MAG_NORMAL_NODE);
            this.cachedHasAdditionalMagMesh = this.polyMeshModel.hasMeshInSubtree(GunModelConstant.MAG_ADDITIONAL_NODE);
            logStats(geoPath);
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] Failed to load poly_mesh: {}", geoPath, e);
        }
    }

    public boolean hasPolyMesh() {
        return polyMeshModel != null;
    }

    private void logStats(Identifier geoPath) {
        if (!MeshyConfig.LOG_STATS.get()) {
            return;
        }
        if (!PolyMeshSupport.markGeoLogged(geoPath)) {
            return;
        }
        int verts = polyMeshModel.getTotalVertexCount();
        LOGGER.info("[TacZMeshLoader] poly_mesh stats for {}: {} bones, {} vertices"
                        + " (translucent={}, illuminated={}, mag={}, additionalMag={})",
                geoPath,
                polyMeshModel.getMeshBoneCount(),
                verts,
                polyMeshModel.getTranslucentBoneCount(),
                polyMeshModel.getIlluminatedBoneCount(),
                cachedHasMagMesh,
                cachedHasAdditionalMagMesh);
        int warnAt = MeshyConfig.MAX_MODEL_VERTICES.get();
        if (!loggedDenseModel && warnAt > 0 && verts > warnAt) {
            loggedDenseModel = true;
            LOGGER.warn("[TacZMeshLoader] {} has {} vertices (MeshMaxModelVertices={}). "
                            + "Expect first-person frame cost on the CPU path; GUI/world contexts are capped.",
                    geoPath, verts, warnAt);
        }
    }

    public static void register() {
        GunModelTypeManager.registerModelType("mesh", TaczPolyMeshGunModel::new);
        LOGGER.info("[TacZMeshLoader] Registered TACZ gun model type: mesh");
    }

    private static List<BedrockPart> pathToRoot(BedrockPart part) {
        List<BedrockPart> path = new ArrayList<>();
        for (BedrockPart current = part; current != null; current = current.getParent()) {
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }
}
