package cn.sh1rocu.tacz.compat.meshloader.model;

import cn.sh1rocu.tacz.compat.meshloader.api.IPolyMeshBone;
import cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig;
import cn.sh1rocu.tacz.compat.meshloader.config.PolyRenderPolicy;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMesh;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshModel;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSnapshot;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSupport;
import cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer;
import cn.sh1rocu.tacz.compat.meshloader.render.ScreenRenderTracker;
import cn.sh1rocu.tacz.compat.meshloader.render.ShaderStateTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.other.GunModelTypeManager;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.GunModelConstant;
import com.tacz.guns.client.model.IMirrorGeometry;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 支持 poly_mesh 的枪械模型。
 *
 * <h2>GPU 路径（第 1 步：无光影第一人称，见 {@link PolyMeshGpuRenderer}）</h2>
 * <p>{@code shouldSubmitGpu}=true 时 cutout 顶点常驻 GPU（骨骼本地系 + light 烘进顶点），
 * 每帧只登记「骨骼矩阵 + 纹理 + VBO」，绘制发生在手部 pass 的 RETURN 点。
 * translucent 骨骼不烘（混合顺序交给 collector），换弹 additional_magazine
 * 恒走 collector（矩阵语义不同且不是顶点热点）。</p>
 *
 * <h2>弹匣（关 PR #70 的教训）</h2>
 * 上游 TML 在 {@code loadPolyMesh} 之后把 {@code additional_magazine} 的
 * FunctionalRenderer 包一层：立方体走原 {@code IMirrorGeometry}，poly 在
 * {@code additional_magazine.visible} 时按该节点变换再画一遍 {@code magazine}。
 * 关 PR #70 没接这条链路，纯 mesh 枪的弹匣会丢。
 *
 * <p>本仓快照遍历器认 {@link IMirrorGeometry} 画立方体镜像。本类保留那条路径，
 * 再按原版 TML 在主 submit 里补 poly：
 * <ol>
 *   <li>exclude {@code additional_magazine} 子树，避免主遍历把它画在错误位置；</li>
 *   <li>{@code super.submit} 照常走立方体 + {@code IMirrorGeometry}；</li>
 *   <li>主 poly（含 {@code magazine}）走 collector；</li>
 *   <li>{@code additional_magazine.visible} 时在该节点变换下再提交 magazine /
 *       additional_magazine 的 poly（与上游 TML 的 {@code renderSubtreeDirect} 同构）。</li>
 * </ol>
 *
 * <h2>性能边界（如实声明，见 docs/MESH_LOADER.md）</h2>
 * <p>collector 路径每帧对每个可见 poly 顶点做一次 CPU 变换 + 逐顶点
 * VertexConsumer 调用。36 万顶点级高模在第一人称<b>仍然有帧率成本</b> ——
 * 第 0 步内置只保证「能用、不劣化无 mesh 场景、GUI/世界有预算闸门」；
 * O(顶点)→O(骨骼) 的 GPU 静态烘焙是第 1 步，见
 * docs/TML_GPU_FEASIBILITY_1211_20260831.md §5。</p>
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
    /**
     * 这批 VBO 烘焙时用的顶点格式（{@link PolyMeshGpuRenderer#bakeFormat()}）。
     * 光影激活时它是 Iris 的扩展实体格式，与 NEW_ENTITY 的 stride 不同，格式一变必须重烘。
     */
    private VertexFormat bakedFormat = null;
    private long lastRebakeMs = 0L;
    private boolean gpuBaked = false;
    /**
     * 世界语境的烘焙缓存：量化光照档 → 该档的整套骨骼 VBO（access-order 当 LRU 用）。
     *
     * <p>第一人称的单档缓存 {@link #bakedBones} 保持原样：手上只有一把枪、光照单值，
     * 单档 + 1 秒节流已实测 PASS，没必要合并进 LRU 添变数。世界里同屏可能有几十把
     * 不同光照的枪，单档缓存会互相挤掉、逐帧重烘（比 collector 还慢），所以必须多档共存。</p>
     *
     * <p>逐出的 VBO 走 {@link PolyMeshGpuRenderer#releaseDeferred}（下一帧才 close）——
     * 本帧的 WORLD_DRAWS 可能已经引用它（同型号两把枪一先一后 submit，前者登记、
     * 后者触发逐出）。</p>
     */
    private final java.util.LinkedHashMap<Integer, Map<String, PolyMeshGpuRenderer.BakedBone>> worldBakedByLight =
            new java.util.LinkedHashMap<>(8, 0.75f, true);
    private static String worldSkipReason = null;
    private static int worldSkipCount = 0;

    private int worldBakedGeneration = -1;
    private VertexFormat worldBakedFormat = null;
    /** 世界烘焙日志只在前两次用 info（光照档来回切时逐次 info 会刷屏）。 */
    private int worldBakeLogCount = 0;
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
                       @Nullable Identifier gunTexture, int light, int overlay) {
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
        // 世界语境（第三人称手持 / 掉落物 / 展示框 / 展示台）的 GPU 路径。两级闸门：
        // ① isWorldGpuContext —— 按 transformType 排除 GUI 系语境（热栏图标以 GUI 语境在
        //    HUD 里提取，<b>没有 Screen</b>，ScreenRenderTracker 拦不住，只能按语境挡；
        //    GUI 的 pose 带正交投影，落进世界表必画错）；
        // ② PolyMeshGpuRenderer.shouldSubmitGpuWorld —— 手部/Screen 提取/镜内/阴影/
        //    光影未放行 逐个拒收，并要求世界 flush 钩子的存活证明。
        Map<String, PolyMeshGpuRenderer.BakedBone> worldBaked = null;
        if (!gpu && isWorldGpuContext(transformType)) {
            if (PolyMeshGpuRenderer.shouldSubmitGpuWorld()) {
                worldBaked = ensureWorldBaked(light);
                if (worldBaked == null && worldBakedByLight.isEmpty()) {
                    noteWorldSkip("bake refused: per-frame bake budget exhausted, or the bake failed"
                            + " for the current vertex format (see the earlier [TacZMeshLoader] lines)");
                }
            } else if (worldBakedByLight.isEmpty()) {
                // 门闸拒收本来是静默的（正确行为，但现场不留痕迹），这里补一行原因。
                String blocker = PolyMeshGpuRenderer.worldSubmitBlocker();
                noteWorldSkip(blocker != null ? blocker : "world gate closed");
            }
        }

        // 顶点预算只保护【collector 路径】—— 它防的是 O(顶点) 的 CPU 提交成本。GPU 路径
        // 每帧只传 O(骨骼) 个矩阵，预算对它没有保护对象；反过来，若在这里拦掉 GPU，
        // 「多人一堆高模枪」根本没解决。烘焙失败/额度耗尽回退 collector 时预算照常生效
        // （那才是它该保护的场景）。第一人称从不吃预算（原语义不变）。
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
            // 世界 GPU：与手部同构 —— 每骨骼一条「pose + VBO」进世界表，帧末在世界那次
            // feature flush 后统一绘制。同型号多把枪共享同一模型实例 = 共享同一套 VBO，
            // 各自登记各自的骨骼 pose（pose 已含相机相对平移，见 EntityRenderDispatcher）。
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
     * 这个 transformType 是不是「世界 GPU 表」可以收的语境。
     *
     * <ul>
     *   <li>{@code GUI} / {@code GUI_LEFTHAND} / {@code GUI_RIGHTHAND} / {@code FIXED_GUI}*
     *       一律不收 —— 它们带正交投影的 pose；</li>
     *   <li>{@code FIRST_PERSON} 系列永远轮不到这里（手部 pass 的 submit 由
     *       {@code shouldSubmitGpuWorld} 的 inHandPass 闸门拒收），显式排除是防「第三人称
     *       视角下手里那把枪」被两个语境各登记一次；</li>
     *   <li>{@code FIXED} / {@code HEAD} 是双面语境（世界展示框/雕像 vs 枪匠桌 GUI 预览）：
     *       Screen 内的预览已被 {@code ScreenRenderTracker} 拦，这里再按
     *       {@code com.tacz.guns.util.RenderDistance.isGuiRender()}（枪匠桌的 100ms
     *       标记）补一道 ——
     *       代价只是「枪匠桌开着的瞬间世界雕像回退 collector」，比反向泄漏便宜得多。</li>
     * </ul>
     */
    private static boolean isWorldGpuContext(ItemDisplayContext transformType) {
        if (transformType == null || transformType == ItemDisplayContext.GUI) {
            return false;
        }
        String name = transformType.name();
        if (name.startsWith("GUI") || name.startsWith("FIXED_GUI")) {
            return false;
        }
        if (transformType == ItemDisplayContext.FIXED || transformType == ItemDisplayContext.HEAD) {
            return !com.tacz.guns.util.RenderDistance.isGuiRender();
        }
        return !transformType.firstPerson();
    }

    /** 该骨骼的 cutout 是否已由 GPU 覆盖（capture 时跳过，避免画两遍）。 */
    private boolean isGpuBone(String boneName) {
        return bakedBones.containsKey(boneName) && !polyMeshModel.isTranslucentBone(boneName);
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
     * 跨档才重烘，且有 1 秒节流。illuminated 骨骼的光照值走
     * {@link PolyRenderPolicy#illuminatedLight(int)}，与 collector 语义一致：无光影时恒
     * FULL_BRIGHT，装光影包时 sky 换成环境真值（光影状态翻转本来就会让烘焙世代失效重烘，
     * 见 {@code ShaderStateTracker}，所以这里不需要额外失效逻辑）。</p>
     */
    private boolean ensureBaked(Identifier texture, int currentLight) {
        if (polyMeshModel == null) {
            return false;
        }
        int lightKey = PolyMeshGpuRenderer.quantizeLight(currentLight);
        int generation = PolyMeshGpuRenderer.getBakeGeneration();
        VertexFormat bakeFormat = PolyMeshGpuRenderer.bakeFormat();
        if (gpuBaked) {
            if (generation != bakedGeneration || bakeFormat != bakedFormat) {
                // 光影包开关翻转、或 pass 实际消费的顶点格式变了（Iris 会在光影激活时把
                // NEW_ENTITY 换成扩展实体格式）：旧 VBO 按新 stride 解读就是模型拉伸，
                // 必须立即重烘，不受 1 秒光照节流约束。
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
            // 与 collector 同源：装了光影包时自发光骨骼的 sky 用环境真值
            // （PolyRenderPolicy#illuminatedLight），否则光影包会读成「永远晒得到太阳月亮」
            int boneLight = polyMeshModel.isIlluminatedBone(boneName)
                    ? PolyRenderPolicy.illuminatedLight(lightKey) : lightKey;
            PolyMeshGpuRenderer.BakedBone baked = PolyMeshGpuRenderer.bakeBone(entry.getValue(), boneLight, bakeFormat);
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
            bakedFormat = bakeFormat;
            lastRebakeMs = System.currentTimeMillis();
            LOGGER.info("[TacZMeshLoader] GPU-baked {} bones ({} vertices) for {} at quantized light {}",
                    bakedBones.size(), polyMeshModel.getTotalVertexCount(), texture,
                    Integer.toHexString(lightKey));
        } else {
            // 部分失败宁可整体回 collector：半 GPU 半 collector 的 cutout 集合难以对账。
            releaseBaked();
        }
        return gpuBaked;
    }

    /**
     * 世界语境版 {@link #ensureBaked}：按量化光照档 LRU 缓存整套骨骼 VBO。
     *
     * <p>与第一人称单档缓存的三点差异：</p>
     * <ol>
     *   <li><b>多档共存</b>：同屏的掉落枪 / 其他玩家光照各不相同，单档会互相挤掉并逐帧
     *       重烘；LRU 容量 {@code MeshGpuLightCacheSize}（默认 4），量化到 4 级步进后
     *       同屏超过 4 档的场景极罕见；</li>
     *   <li><b>烘焙额度</b>：{@link PolyMeshGpuRenderer#tryReserveBake} 限制每帧新烘焙次数
     *       —— 光照档数超过 LRU 容量的病理场景下，宁可让部分枪回退 collector 一帧，
     *       也不许「逐出-重烘」逐帧打摆；</li>
     *   <li><b>延迟释放</b>：逐出的 VBO 交 {@link PolyMeshGpuRenderer#releaseDeferred}
     *       下一帧才 close（本帧 WORLD_DRAWS 可能已引用它）。</li>
     * </ol>
     *
     * @return 该光照档的骨骼 VBO 表；无法就绪（额度耗尽 / 烘焙失败）返回 null，调用方回退 collector。
     */
    @Nullable
    /**
     * 「世界 GPU 被拒」的一次性原因日志：同一原因只记一次，原因切换时补一行累计次数。
     *
     * <p>为什么要有：{@code shouldSubmitGpuWorld()} 静默回退 collector 是<b>正确</b>的
     * （宁可不优化也不能画错），但它让「光影下世界路径怎么没生效」这种问题在 latest.log 里
     * 一个字都不留，只能靠加日志复现。放在静态字段上 = 全模型共用一条，不会每把枪各刷一行。
     * 级别用 INFO：既不该被当成错误（多半是配置/语境使然），又默认可见。</p>
     */
    private static void noteWorldSkip(String reason) {
        if (reason.equals(worldSkipReason)) {
            worldSkipCount++;
            return;
        }
        if (worldSkipReason != null) {
            LOGGER.info("[TacZMeshLoader] previous GPU world-submit refusal \"{}\" lasted {} submission(s)",
                    worldSkipReason, worldSkipCount);
        }
        worldSkipReason = reason;
        worldSkipCount = 1;
        LOGGER.info("[TacZMeshLoader] GPU world submit refused: {} (mesh guns keep the collector path)"
                + " -- not an error unless you expected the world GPU path", reason);
    }

    private Map<String, PolyMeshGpuRenderer.BakedBone> ensureWorldBaked(int currentLight) {
        if (polyMeshModel == null) {
            return null;
        }
        int generation = PolyMeshGpuRenderer.getBakeGeneration();
        VertexFormat bakeFormat = PolyMeshGpuRenderer.bakeFormat();
        if (generation != worldBakedGeneration || bakeFormat != worldBakedFormat) {
            // 光影包开关翻转、或 pass 消费的顶点格式变了：所有档位的 buffer 布局一起作废，
            // 与第一人称 bakedGeneration 同一根因（按错 stride 解读就是模型拉伸）。
            releaseWorldBaked();
            worldBakedGeneration = generation;
            worldBakedFormat = bakeFormat;
        }
        int lightKey = PolyMeshGpuRenderer.quantizeLight(currentLight);
        Map<String, PolyMeshGpuRenderer.BakedBone> cached = worldBakedByLight.get(lightKey);
        if (cached != null) {
            return cached;
        }
        int cap = Math.max(1, MeshyConfig.GPU_LIGHT_CACHE_SIZE.get());
        if (!PolyMeshGpuRenderer.tryReserveBake(cap)) {
            return null;
        }
        Map<String, PolyMeshGpuRenderer.BakedBone> bones = new HashMap<>();
        boolean allOk = true;
        for (Map.Entry<String, List<PolyMesh>> entry : polyMeshModel.getMeshMap().entrySet()) {
            String boneName = entry.getKey();
            if (polyMeshModel.isTranslucentBone(boneName)) {
                continue;
            }
            // 与 collector 同源：装了光影包时自发光骨骼的 sky 用环境真值
            // （PolyRenderPolicy#illuminatedLight），否则光影包会读成「永远晒得到太阳月亮」
            int boneLight = polyMeshModel.isIlluminatedBone(boneName)
                    ? PolyRenderPolicy.illuminatedLight(lightKey) : lightKey;
            PolyMeshGpuRenderer.BakedBone baked =
                    PolyMeshGpuRenderer.bakeBone(entry.getValue(), boneLight, bakeFormat);
            if (baked == null) {
                allOk = false;
                break;
            }
            bones.put(boneName, baked);
        }
        if (!allOk || bones.isEmpty()) {
            // 半套缓存没有意义（哪根骨骼该谁画对不上账），全释放、本档回 collector。
            for (PolyMeshGpuRenderer.BakedBone baked : bones.values()) {
                PolyMeshGpuRenderer.releaseDeferred(baked);
            }
            return null;
        }
        worldBakedByLight.put(lightKey, bones);
        while (worldBakedByLight.size() > cap) {
            // access-order LinkedHashMap：迭代器首位即最久未访问档。
            var it = worldBakedByLight.entrySet().iterator();
            Map<String, PolyMeshGpuRenderer.BakedBone> evicted = it.next().getValue();
            it.remove();
            for (PolyMeshGpuRenderer.BakedBone baked : evicted.values()) {
                PolyMeshGpuRenderer.releaseDeferred(baked);
            }
        }
        if (worldBakeLogCount < 2) {
            worldBakeLogCount++;
            LOGGER.info("[TacZMeshLoader] GPU world-baked {} bones ({} vertices) at quantized light {}"
                            + " ({} level(s) cached, format={})",
                    bones.size(), polyMeshModel.getTotalVertexCount(), Integer.toHexString(lightKey),
                    worldBakedByLight.size(), bakeFormat);
        } else {
            LOGGER.debug("[TacZMeshLoader] GPU world-baked {} bones at quantized light {}",
                    bones.size(), Integer.toHexString(lightKey));
        }
        return bones;
    }

    private void releaseWorldBaked() {
        for (Map<String, PolyMeshGpuRenderer.BakedBone> level : worldBakedByLight.values()) {
            for (PolyMeshGpuRenderer.BakedBone baked : level.values()) {
                PolyMeshGpuRenderer.releaseDeferred(baked);
            }
        }
        worldBakedByLight.clear();
        worldBakedFormat = null;
    }

    private void releaseBaked() {
        for (PolyMeshGpuRenderer.BakedBone baked : bakedBones.values()) {
            baked.close();
        }
        bakedBones.clear();
        gpuBaked = false;
        bakedLightKey = -1;
        bakedGeneration = -1;
        bakedFormat = null;
    }

    /**
     * 换弹时留在枪上的那份弹匣。立方体已由 {@link IMirrorGeometry} 处理；
     * 这里只补 poly，且仅在 {@code additional_magazine.visible} 时画。
     *
     * <p>captureSubtree 的矩阵语义与上游 TML {@code renderSubtreeDirect} 一致：
     * 该节点及其祖先的变换先由调用方乘进 pose，根骨骼自身不再套变换。</p>
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

    private boolean withinContextBudget(ItemDisplayContext transformType, PoseStack poseStack) {
        if (transformType == ItemDisplayContext.GUI
                || transformType == ItemDisplayContext.FIXED
                || transformType == ItemDisplayContext.HEAD) {
            // FIXED/HEAD 是双面语境：既出现在枪匠桌 GUI 预览，也出现在世界里的
            // 展示台雕像/物品展示框/背枪。只有世界侧允许按相机距离豁免——展示台上
            // 的高模枪正是走 FIXED 被 GUI 预算拦掉的。这里用
            // {@code ScreenRenderTracker.isRenderingScreen()} 精确判定「正在画 GUI
            // 画面」，而非「菜单开着」——避免菜单开着时世界内无关 FIXED/HEAD 渲染
            // 被误判进 GUI 预算档。
            if (transformType != ItemDisplayContext.GUI
                    && !ScreenRenderTracker.isRenderingScreen()
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
        // 世界表走延迟释放池：本帧可能已有条目引用这些 buffer。
        releaseBaked();
        releaseWorldBaked();
        worldBakedGeneration = -1;
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
            // 注册给 ShaderStateTracker（弱引用）：光影包开关翻转时失效 VBO。
            // 第 0 步无 GPU 烘焙缓存，invalidateVboCache 为空操作；第 1 步 GPU
            // 落地后这里就是 VBO 失效的完整链路。
            ShaderStateTracker.registerModel(this.polyMeshModel);
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
