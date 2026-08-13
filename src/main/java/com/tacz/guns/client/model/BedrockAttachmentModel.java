package com.tacz.guns.client.model;

import com.mojang.blaze3d.vertex.*;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.bedrock.ModelRendererWrapper;
import com.tacz.guns.client.model.functional.BeamRenderer;
import com.tacz.guns.client.render.scope.IReticleRenderer;
import com.tacz.guns.client.render.scope.ReticleRendererRegistry;
import com.tacz.guns.client.render.scope.ScopeFinalOverlayState;
import com.tacz.guns.client.render.scope.ScopeLateReticleState;
import com.tacz.guns.client.render.scope.ScopeNodeSet;
import com.tacz.guns.client.render.scope.ScopeRenderTypes;
import com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.client.model.functional.TextShowRender;
import com.tacz.guns.client.resource.pojo.display.gun.TextShow;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class BedrockAttachmentModel extends BedrockAnimatedModel {
    private static final String SCOPE_VIEW_NODE = "scope_view";
    private static final String DIVISION_NODE = "division";
    private static final String OCULAR_RING_NODE = "ocular_ring";
    private static final String OCULAR_NODE = "ocular";
    private static final String OCULAR_SIGHT_NODE = "ocular_sight";
    private static final String OCULAR_SCOPE_NODE = "ocular_scope";
    private static final Pattern LASER_BEAM_PATTERN = Pattern.compile("^laser_beam(_(\\d+))?$");

    /**
     * 开始裁剪的开镜进度阈值。低于此值完全不裁剪。
     *
     * <p>取一个很小的正数而非 0：{@code aimingProgress} 是插值出来的浮点，
     * 收枪结束时可能停在 0.001 这类残值上，用 {@code > 0} 判据会让镜身
     * 一直挂着一个几乎不可见但确实存在的洞。
     */
    private static final float AIM_CLIP_START = 0.02f;

    /**
     * 瞄具文字开始显示的开镜进度。与 {@code IlluminatedReticleRenderer.FADE_IN_START}
     * 取同一值，让文字与准星同时出现，观感统一。详见 {@link #setTextShowList}。
     */
    private static final float TEXT_SHOW_AIM_START = 0.35f;

    // SubmitNodeStorage renders order keys in ascending order (Int2ObjectAVLTreeMap). Custom geometry inside
    // one order is grouped by HashMap<RenderType, ...>, so distinct RenderTypes alone do NOT guarantee
    // aperture -> body -> exact depth restore -> gun(default 0) -> filtered reticle.
    private static final int SCOPE_APERTURE_ORDER = -3;
    private static final int SCOPE_BODY_ORDER = -2;
    private static final int SCOPE_DEPTH_CLEANUP_ORDER = -1;
    private static final int SCOPE_RETICLE_ORDER = 1;
    /**
     * Physical ocular rim: after depth cleanup so the aperture cannot punch holes in it, and
     * <b>after the reticle</b> so the opaque rim covers any reticle fragment that spills past
     * the ocular edge.
     * <p>
     * 【准星溢出镜框的修复 / 2026-08-13 实机反馈】原先 rim=1、reticle=2，准星画在镜框【之后】。
     * 准星的镜内判据用的是 {@code APERTURE_TARGET}，而它是在 order -2（body 绘制边界）就
     * 快照好的 —— 那时 rim 根本还没画，掩码里没有镜框的任何信息，于是压在镜框上的准星像素
     * 通过了判据，表现为准星"漏"出目镜、贴到镜框上（有无光影都会出现，因为这与深度测试
     * 函数、与 Iris 都无关，纯粹是绘制顺序问题）。
     * <p>
     * 上游 1.21.1 的顺序本来就是「先准星、后 ocular_ring」，用不透明的镜框盖住溢出部分；
     * 移植时把两者调换了。这里改回上游顺序即可，无需调整掩码 epsilon
     * （盲目放大 epsilon 会连镜内准星一起裁掉，是更糟的做法）。
     */
    private static final int SCOPE_OCULAR_RING_ORDER = 2;

    /**
     * 发光准星节点。凡是名字以 {@code _illuminated} 结尾的，
     * {@code BedrockModel} 构造时都会把 {@code illuminated=true}，
     * 快照阶段自动给满亮度(15728880)。
     *
     * <p>但并非所有 {@code *_illuminated} 都是准星 —— 激光/手电/镜片高光也用这个后缀。
     * 实测默认枪包出现过：{@code division_illuminated}、{@code dot_illuminated}、
     * {@code crosshair_illuminated}、{@code cross_illuminated}、{@code red_illuminated}、
     * {@code sight_division_illuminated}、{@code scope_division_illuminated}、
     * 以及 {@code laser_illuminated} / {@code flashlight_illuminated} / {@code lens_illuminated}（<b>非</b>准星）。
     * 因此这里用白名单式匹配，只认「分划/点/十字」这几类词根。</p>
     */
    private static final Pattern RETICLE_ILLUMINATED_PATTERN = Pattern.compile(
            "^(.*_)?(division|divisions|dot|cross|crosshair|reticle|red)(_\\d+)?_illuminated\\d*$");

    /** 蚀刻分划节点（不发光）；没有安全 inside mask 时由 EtchedReticleRenderer 主动跳过。 */
    private static final Pattern RETICLE_ETCHED_PATTERN = Pattern.compile(
            "^(division|divisions)(_(\\d+))?$");

    protected List<List<BedrockPart>> scopeViewPaths;
    /** 第 22 轮：准星（分划）节点集合，构造时解析一次，供 IReticleRenderer 使用。 */
    protected ScopeNodeSet reticleNodes = ScopeNodeSet.empty();

    /**
     * 目镜编号 → 该目镜是否属于<b>筒镜</b>分系统（{@code ocular_scope*}）。
     *
     * <h2>为什么必须按编号存，而不是按名字前缀判断</h2>
     * 上游 {@code BedrockAttachmentModel} 构造时用的是
     * <pre>
     * TreeMap&lt;Integer, OcularWrapper&gt; map;
     * int num = matcher.group(3) == null ? 1 : parseInt(matcher.group(3));
     * map.put(num, new OcularWrapper(renderer, OCULAR_SCOPE_NODE.equals(type)));
     * </pre>
     * 也就是说 <b>{@code ocular_xxx_N} 里的 N 才是它的序号</b>，
     * 而 {@code isScopeOcular} 只是挂在该序号上的一个布尔标记。
     *
     * <p>随后 {@code renderOcularAndDivision} 严格按<b>同一个序号</b>
     * 把目镜与分划配对：{@code ocularNodePaths.get(i)} ↔ {@code divisionNodePaths.get(i)}。
     *
     * <h2>此前按前缀分组为什么必然出错</h2>
     * 早前的 {@code isOcularInActiveGroup}/{@code filterReticleByActiveView} 假定
     * 「{@code sight_} 前缀 = 红点组（views 值 1）、{@code scope_} 前缀 = 筒镜组（views 值 2）」。
     * 但 {@code scope_standard_8x} 的命名是：
     * <pre>
     * ocular_scope     -> 无后缀，序号 1
     * ocular_sight_2   -> 后缀 2，序号 2
     * </pre>
     * 即<b>序号 1 反而是筒镜、序号 2 才是红点</b>，与 hamr/vudu
     * （{@code ocular_sight} = 1、{@code ocular_scope_2} = 2）正好相反。
     * 按前缀映射到 views 值，在这个模型上必然反选。
     *
     * <p>而 {@code scope_vudu} 的分划节点叫 {@code division_illuminated} /
     * {@code division_2_illuminated}，压根没有 {@code sight_}/{@code scope_} 前缀 ——
     * 旧代码在这里退回「全集」，于是两组准星同时画出来，正是用户实测到的现象。
     * 改用序号后，{@code division}(1) / {@code division_2}(2) 天然可配对，不再需要退化分支。
     */
    protected final java.util.NavigableMap<Integer, BedrockPart> ocularByIndex = new java.util.TreeMap<>();
    /** 目镜序号 → 是否为筒镜分系统。与 {@link #ocularByIndex} 同键。 */
    protected final java.util.Map<Integer, Boolean> ocularIsScopeByIndex = new java.util.HashMap<>();
    /** 分划序号 → 该分划子树的根节点。序号语义与 {@link #ocularByIndex} 一致。 */
    protected final java.util.NavigableMap<Integer, BedrockPart> divisionByIndex = new java.util.TreeMap<>();
    /**
     * 目镜节点。未开镜时可作为镜片绘制；开镜后活动节点从 body 快照移出，
     * 单独作为 invisible depth-aperture 几何。它的屏幕投影决定镜身被深度挡掉的区域。
     */
    protected final List<BedrockPart> ocularParts = new ArrayList<>();
    /**
     * Physical inner rim around the lens. Upstream 1.21.1 renders this before any stencil clipping;
     * it is not aperture/blackout geometry and must never be removed by the ocular mask.
     */
    protected final @Nullable BedrockPart ocularRingPart;
    protected @Nullable List<List<BedrockPart>> laserBeamPaths;

    private @Nullable ItemStack currentGunItem;
    private @Nullable ItemStack attachmentItem;

    private boolean isScope = false;
    private boolean isSight = false;

    public BedrockAttachmentModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
        scopeViewPaths = new ArrayList<>();
        laserBeamPaths = new ArrayList<>();
        ModelRendererWrapper ocularRingWrapper = modelMap.get(OCULAR_RING_NODE);
        ocularRingPart = ocularRingWrapper == null ? null : ocularRingWrapper.getModelRenderer();
        // 初始化 view 的 node path
        List<BedrockPart> path = getPath(modelMap.get(SCOPE_VIEW_NODE));
        int i = 2;
        while (path != null) {
            scopeViewPaths.add(path);
            path = getPath(modelMap.get(SCOPE_VIEW_NODE + '_' + i++));
        }
        // 收集目镜几何和激光束。目镜在未开镜时仍可作为黑色镜片显示；开镜后，
        // 活动目镜会从 body 快照中移除并单独进入 invisible depth-aperture 批次。不能在这里永久
        // visible=false，因为 BedrockPart 是跨帧、跨显示上下文共享的。
        // 三种命名都要收：ocular / ocular_sight / ocular_scope（组合镜两组各一个）。
        Pattern ocularPattern = Pattern.compile(
                "^(" + OCULAR_NODE + "|" + OCULAR_SIGHT_NODE + "|" + OCULAR_SCOPE_NODE + ")(_(\\d+))?$");
        for (Map.Entry<String, ModelRendererWrapper> entry : modelMap.entrySet()) {
            String name = entry.getKey();
            java.util.regex.Matcher ocularMatcher = name == null ? null : ocularPattern.matcher(name);
            if (ocularMatcher != null && ocularMatcher.matches()) {
                BedrockPart part = entry.getValue().getModelRenderer();
                if (part != null) {
                    // 【按上游语义登记序号】名字尾部的 _N 就是序号，无后缀视为 1。
                    // 这与上游构造函数里的 TreeMap<Integer, OcularWrapper> 逐字对应，
                    // 是后面「目镜 ↔ 分划」配对与「当前镜组」判定的唯一依据。
                    String numStr = ocularMatcher.group(3);
                    int num = numStr == null ? 1 : Integer.parseInt(numStr);
                    ocularByIndex.put(num, part);
                    ocularIsScopeByIndex.put(num, OCULAR_SCOPE_NODE.equals(ocularMatcher.group(1)));
                    // 【只登记，不隐藏】—— 目镜是【要画出来】的可见几何。
                    //
                    // 上游 renderOcularAndDivision 里那两行写得很直白：
                    //     // 渲染目镜黑色遮罩
                    //     stencilFunc(GL_EQUAL, i + 1);
                    //     renderTempPart(... ocularNodePaths.get(i));
                    // 目镜是一块【不透明的黑色镜片】，只是被 stencil 裁在圆内而已。
                    //
                    // 早前这里写了 part.visible = false（当时以为上游"从不画目镜"，
                    // 那个判断只对了 renderOcularStencil 那一步 —— 那一步确实只写模板，
                    // 但后面还有专门画它的一步）。后果是目镜【永久消失】：
                    // 不开镜时镜筒里就是个洞，能直接看到物镜和镜身内壁
                    // —— 正是用户实测到的第 1 个问题（elcan_4x / hamr 等）。
                    //
                    // 现在改为正常渲染：开镜时由掩码裁剪，不开镜时它就是一块实心镜片。
                    ocularParts.add(part);
                }
            }
            if (LASER_BEAM_PATTERN.matcher(name).find()) {
                laserBeamPaths.add(getPath(entry.getValue()));
            }
        }
        // 初始化 division 的 node path。
        //
        // 上游这段循环同时干两件事：把 division 隐藏（不让它走主渲染列表），
        // 并按 division、division_2、division_3… 的顺序压进 divisionNodePaths。
        // 那个 List 下标 i 与 ocularNodePaths 的下标 i 一一对应 ——
        // 也就是说 division 的序号规则与目镜完全相同（无后缀 = 1）。
        // 这里额外把它记进 divisionByIndex，好让准星能按序号跟目镜配对。
        ModelRendererWrapper divisionModel = modelMap.get(DIVISION_NODE);
        path = getPath(modelMap.get(DIVISION_NODE));
        i = 2;
        while (path != null) {
            divisionModel.setHidden(true);
            BedrockPart divisionPart = divisionModel.getModelRenderer();
            if (divisionPart != null) {
                divisionByIndex.put(i - 1, divisionPart);
            }
            divisionModel = modelMap.get(DIVISION_NODE + '_' + i++);
            path = getPath(divisionModel);
        }
        // 第 22 轮：解析准星（分划）节点集合，供 IReticleRenderer 策略使用。
        this.reticleNodes = resolveReticleNodes();
    }

    /**
     * 扫描模型树，把准星节点分成「发光」与「蚀刻」两类。
     *
     * <h2>为什么必须单独扫描，而不能复用 divisionNodePaths</h2>
     * 上面那段初始化把 {@code division} 整个 {@code setHidden(true)} 了，
     * 而实测（默认枪包 33 个瞄具）发现 <b>{@code division_illuminated} 是
     * {@code division} 的子节点</b>：
     * <pre>
     *   scope_acog_ta31:  division(5 cubes)  ← 黑色蚀刻线 + 遮光板
     *                     └─ division_illuminated(1 cube)  ← 发光竖线
     *   sight_exp3:       division(0 cubes)
     *                     └─ division_illuminated(1 cube)  ← 全息红点
     * </pre>
     * 而快照遍历器 {@code BedrockRenderSnapshot#capturePart} 遇到
     * {@code visible == false} 会<b>直接 return、连子节点都不遍历</b>。
     * 于是父级一被隐藏，发光准星也跟着永远画不出来 ——
     * 这正是「镜片掏空后什么都看不见」的直接原因。
     *
     * <p>因此这里<b>绕过父子关系</b>，直接把发光节点单独收集出来，
     * 由 {@code IlluminatedReticleRenderer} 在 submit 阶段临时置为可见并单独提交。</p>
     */
    private ScopeNodeSet resolveReticleNodes() {
        List<BedrockPart> illuminated = new ArrayList<>();
        List<BedrockPart> etched = new ArrayList<>();
        for (Map.Entry<String, ModelRendererWrapper> entry : modelMap.entrySet()) {
            String name = entry.getKey();
            if (name == null) {
                continue;
            }
            BedrockPart part = entry.getValue().getModelRenderer();
            if (part == null) {
                continue;
            }
            if (RETICLE_ILLUMINATED_PATTERN.matcher(name).matches()) {
                illuminated.add(part);
            } else if (RETICLE_ETCHED_PATTERN.matcher(name).matches()) {
                etched.add(part);
            }
        }
        if (illuminated.isEmpty() && etched.isEmpty()) {
            return ScopeNodeSet.empty();
        }
        return new ScopeNodeSet(etched, illuminated);
    }

    /**
     * 【第 35 轮】组合镜（both）当前激活的镜组：{@code 1} = 红点/全息分系统，
     * {@code 2} = 筒镜分系统。非组合镜恒为 {@code 0}（表示"不适用，不做过滤"）。
     *
     * <p>由 {@code FirstPersonRenderGunEvent} 在计算定位时顺带写入 ——
     * 那里本来就要按 {@code views[zoomNumber]} 选 {@code scope_view} 定位组，
     * 是全流程里唯一知道"现在用的是哪一组"的地方。</p>
     */
    private int activeViewGroup = 0;

    /** 由渲染事件在每帧定位阶段写入，见 {@link #activeViewGroup}。 */
    public void setActiveViewGroup(int group) {
        this.activeViewGroup = group;
    }

    /**
     * 按当前激活镜组过滤准星节点。
     *
     * <p>只对<b>组合镜</b>生效：单一形态的瞄具（纯红点或纯筒镜）不存在两组准星，
     * 直接原样返回，零开销、零行为变化。</p>
     *
     * <p>命名判据（实测默认枪包 3 个 both 型模型全部遵循）：
     * 节点名以 {@code sight_} 开头属红点组、以 {@code scope_} 开头属筒镜组。
     * <b>不带前缀的节点（如 {@code division_illuminated}、{@code dot_illuminated}）
     * 一律保留</b> —— 例如 {@code scope_vudu} 用的是 {@code division_illuminated} /
     * {@code division_2_illuminated} 这种无前缀命名，无法按前缀归组，
     * 此时宁可全画（维持现状）也不要误删成空准星。</p>
     */
    private ScopeNodeSet filterReticleByActiveView(ScopeNodeSet all) {
        if (!(isScope && isSight) || activeViewGroup == 0) {
            return all;
        }
        Integer activeIndex = activeOcularIndex();
        if (activeIndex == null) {
            return all;
        }
        BedrockPart activeDivision = divisionByIndex.get(activeIndex);
        if (activeDivision == null) {
            // 该模型的分划没有按序号建组，无从判断 —— 维持现状（全画），
            // 与目镜侧的「无从判断就保留」保持同一原则。
            return all;
        }
        // 只保留挂在【当前序号那棵 division 子树】下的准星节点。
        //
        // 这条判据取代了旧的名字前缀匹配。前缀法在 scope_vudu 上直接失效
        // （它的准星叫 division_illuminated / division_2_illuminated，无前缀），
        // 旧代码于是退回全集，两组准星同时画出 —— 正是用户实测到的现象。
        // 而按子树归属判断对所有组合镜都成立，因为分划树本身就是按组切分的：
        //   division   -> sight_division_illuminated / division_2_illuminated
        //   division_2 -> scope_division_illuminated / division_illuminated
        List<BedrockPart> illuminated = filterByAncestor(all.illuminatedReticle(), activeDivision);
        List<BedrockPart> etched = filterByAncestor(all.etchedReticle(), activeDivision);
        if (illuminated.isEmpty() && etched.isEmpty()) {
            return all;
        }
        return new ScopeNodeSet(etched, illuminated);
    }

    /**
     * 当前激活镜组对应的<b>目镜序号</b>。
     *
     * <p>{@code activeViewGroup} 取自 display json 的 {@code views[]}，
     * 约定 {@code 1} = 红点分系统、{@code 2} = 筒镜分系统。这里把它翻译成
     * 本模型内部的目镜序号 —— 两者<b>不能划等号</b>：
     * {@code scope_standard_8x} 的筒镜是序号 1（{@code ocular_scope}）、
     * 红点是序号 2（{@code ocular_sight_2}），与 hamr/vudu 正好相反。
     * 因此必须查 {@link #ocularIsScopeByIndex} 这张构造时建好的表，
     * 而不能假设「序号 = views 值」或「前缀 = views 值」。
     *
     * @return 匹配的目镜序号；找不到（非组合镜或命名不含分组信息）返回 {@code null}
     */
    @Nullable
    private Integer activeOcularIndex() {
        boolean wantScope = activeViewGroup == 2;
        for (Map.Entry<Integer, Boolean> entry : ocularIsScopeByIndex.entrySet()) {
            if (entry.getValue() == wantScope) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** {@code part} 自身或其任一祖先是否为 {@code ancestor}。 */
    private static boolean hasAncestor(BedrockPart part, BedrockPart ancestor) {
        for (BedrockPart p = part; p != null; p = p.getParent()) {
            if (p == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static List<BedrockPart> filterByAncestor(List<BedrockPart> src, BedrockPart ancestor) {
        List<BedrockPart> out = new ArrayList<>();
        for (BedrockPart part : src) {
            if (hasAncestor(part, ancestor)) {
                out.add(part);
            }
        }
        return out;
    }

    /**
     * 当前开镜进度（0 = 未开镜，1 = 完全开镜）。
     *
     * <p>高于 {@link #AIM_CLIP_START} 后，活动 ocular 从可见 body 中移到 invisible depth writer；
     * 其深度负责阻止后方镜身像素写入，而世界颜色保持不变。</p>
     */
    private static float currentAimingProgress() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0f;
        }
        return IClientPlayerGunOperator.fromLocalPlayer(player)
                .getClientAimingProgress(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }



    @Nullable
    public List<BedrockPart> getScopeViewPath(int viewSwitchCount) {
        if (scopeViewPaths.isEmpty()) {
            return null;
        }
        if (viewSwitchCount >= scopeViewPaths.size()) {
            return scopeViewPaths.get(0);
        }
        return scopeViewPaths.get(viewSwitchCount);
    }

    public void setIsScope(boolean isScope) {
        this.isScope = isScope;
    }

    public void setIsSight(boolean isSight) {
        this.isSight = isSight;
    }

    public boolean isScope() {
        return isScope;
    }

    public boolean isSight() {
        return isSight;
    }

    /**
     * 添加枪械自定义的文本显示。
     *
     * <h2>为什么这里要判断开镜进度</h2>
     * 瞄具上的文字（如 MK5HD 的弹药计数与 "AMMO" 标签）走
     * {@code SubmitNodeCollector#submitText} 的 <b>vanilla 字体管线</b>，不是本类可包装的
     * custom-geometry RenderType，因此不会进入瞄具的专用几何阶段。
     *
     * <p>实测 MK5HD 的两个文字节点位于世界坐标 {@code y=22.375}，
     * 而其筒镜目镜 {@code ocular_scope_2} 在 {@code y=21.875} ——
     * 文字比目镜中心高 0.5、且 X 偏左 0.75，正好落在目镜边缘附近，
     * 开镜后就露到圆孔外面（用户实测：「文字不像准星那样只在镜内出现，而是会溢出」）。
     *
     * <h2>上游是什么行为</h2>
     * 上游 {@code renderScope} 的顺序是
     * <pre>
     * stencilFunc(GL_ALWAYS, 0);          // 先【关掉】裁剪
     * disableItemEntityStencilTest();
     * super.render(...);                  // 文字在这里才画
     * </pre>
     * 即<b>上游同样不裁剪这些文字</b>。所以严格说这不是移植缺陷，
     * 但上游圆孔与镜身严丝合缝，溢出不明显；迁移后的字体批次仍不参与镜内裁剪。
     *
     * <h2>做法</h2>
     * 与准星保持一致：<b>只在开镜时显示</b>。
     * 复用 {@link IlluminatedReticleRenderer} 那条 {@code FADE_IN_START = 0.35}
     * 的判据 —— 未开镜时本就看不到镜内，文字自然也不该出现；
     * 开镜后视线对准光轴，文字落在圆孔内，不会溢出。
     *
     * <p>这是<b>保守做法</b>：不碰字体管线、不碰掩码链路，
     * 只在提交前加一道门禁。代价是腰射时看不到瞄具上的弹药计数 ——
     * 但那本来也是「凑到镜前才看得清」的信息，符合直觉。
     *
     * <p>注意只对<b>瞄具</b>生效：{@code BedrockGunModel} 里同名方法不加此门禁，
     * 枪身上的文字（如弹匣计数）本就该常显。
     */
    public void setTextShowList(Map<String, TextShow> textShowList) {
        textShowList.forEach((name, textShow) -> this.setFunctionalRenderer(name,
                bedrockPart -> {
                    // 未开镜（或刚开始开镜）时不提交，避免文字溢出到镜孔之外。
                    if (currentAimingProgress() <= TEXT_SHOW_AIM_START) {
                        return null;
                    }
                    return new TextShowRender(this, textShow, currentGunItem);
                }));
    }

    /**
     * Captures one named part with its complete parent transform while preserving its shared
     * visibility flag. This mirrors upstream renderTempPart(), but produces immutable geometry for
     * the 26.1.2 delayed collector instead of drawing immediately.
     */
    private static BedrockRenderSnapshot captureStandalonePart(BedrockPart part,
                                                                PoseStack rootPose,
                                                                ItemDisplayContext transformType,
                                                                int light,
                                                                int overlay) {
        PoseStack partPose = new PoseStack();
        partPose.last().pose().set(rootPose.last().pose());
        partPose.last().normal().set(rootPose.last().normal());
        List<BedrockPart> parents = new ArrayList<>();
        for (BedrockPart parent = part.getParent(); parent != null; parent = parent.getParent()) {
            parents.add(0, parent);
        }
        for (BedrockPart parent : parents) {
            parent.translateAndRotateAndScale(partPose);
        }
        part.translateAndRotateAndScale(partPose);

        boolean originallyVisible = part.visible;
        part.visible = true;
        try {
            return BedrockRenderSnapshot.captureSubtree(
                    part, partPose, transformType, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            part.visible = originallyVisible;
        }
    }


    /**
     * 兼容重载：不带贴图。此时<b>不做镜内裁剪</b>，行为与 Step 2 之前完全一致。
     *
     * <p>供物品栏预览（{@code AttachmentItemRenderer}）等场景使用 ——
     * 那些场景本就不是第一人称开镜，不需要裁剪。
     */
    public void submit(@Nullable ItemStack attachmentItem,
                       ItemStack currentGunItem,
                       PoseStack poseStack,
                       ItemDisplayContext transformType,
                       SubmitNodeCollector collector,
                       RenderType renderType,
                       int light,
                       int overlay) {
        submit(attachmentItem, currentGunItem, poseStack, transformType, collector,
                renderType, (Identifier) null, light, overlay);
    }

    /**
     * Collector path with an invisible depth aperture for first-person scopes.
     *
     * @param texture 该瞄具的贴图。
     *                传 {@code null} 表示调用方不关心裁剪，一律走原始 RenderType。
     */
    public void submit(@Nullable ItemStack attachmentItem,
                       ItemStack currentGunItem,
                       PoseStack poseStack,
                       ItemDisplayContext transformType,
                       SubmitNodeCollector collector,
                       RenderType renderType,
                       @Nullable Identifier texture,
                       int light,
                       int overlay) {
        this.currentGunItem = currentGunItem;
        this.attachmentItem = attachmentItem;

        boolean scopeMaskEnabled = RenderConfig.SCOPE_MASK_ENABLE == null || RenderConfig.SCOPE_MASK_ENABLE.get();
        boolean apertureActive = scopeMaskEnabled
                && transformType != null && transformType.firstPerson()
                && !ocularParts.isEmpty()
                && currentAimingProgress() > AIM_CLIP_START;

        // ocular_ring is the physical black rim, not the aperture. Upstream draws it with stencil
        // disabled. Freeze it separately so the depth writer cannot clip it out of the body batch.
        BedrockRenderSnapshot ocularRingSnapshot = apertureActive && texture != null && ocularRingPart != null
                ? captureStandalonePart(ocularRingPart, poseStack, transformType, light, overlay)
                : null;

        // Capture ocular snapshots for invisible depth writing
        List<BedrockRenderSnapshot> ocularSnapshots = new ArrayList<>();
        if (apertureActive) {
            for (BedrockPart ocular : ocularParts) {
                if (ocular.visible && isOcularInActiveGroup(ocular)) {
                    PoseStack ocularPose = new PoseStack();
                    ocularPose.last().pose().set(poseStack.last().pose());
                    ocularPose.last().normal().set(poseStack.last().normal());
                    List<BedrockPart> path = new ArrayList<>();
                    for (BedrockPart p = ocular.getParent(); p != null; p = p.getParent()) {
                        path.add(0, p);
                    }
                    for (BedrockPart p : path) {
                        p.translateAndRotateAndScale(ocularPose);
                    }
                    // captureSubtree assumes rootPose already contains the root part's own transform.
                    // Applying only the parents writes the aperture at the parent origin: the visible ocular is
                    // then removed from bodySnapshot, but the scope body is not blocked and the correctly
                    // positioned reticle no longer aligns with the lens. This exactly produces a transparent red-dot
                    // window with no dot and a fully black magnified scope.
                    ocular.translateAndRotateAndScale(ocularPose);
                    ocularSnapshots.add(BedrockRenderSnapshot.captureSubtree(
                            ocular, ocularPose, transformType, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F
                    ));
                }
            }
        }

        // The active ocular is depth-aperture geometry, not visible body geometry. Keeping it in bodySnapshot
        // would draw the opaque lens after the invisible writer and cover the opening again.
        List<BedrockPart> hiddenParts = new ArrayList<>();
        if (ocularRingSnapshot != null && ocularRingPart != null && ocularRingPart.visible) {
            ocularRingPart.visible = false;
            hiddenParts.add(ocularRingPart);
        }
        if (transformType != null && transformType.firstPerson()) {
            for (BedrockPart ocular : ocularParts) {
                boolean hideForAperture = apertureActive && isOcularInActiveGroup(ocular);
                boolean hideByNormalVisibilityRule = !shouldDrawOcularBlackout(ocular);
                if (ocular.visible && (hideForAperture || hideByNormalVisibilityRule)) {
                    ocular.visible = false;
                    hiddenParts.add(ocular);
                }
            }
        }
        com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot bodySnapshot;
        try {
            bodySnapshot = com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot.capture(
                    this, poseStack, transformType, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F
            );
        } finally {
            for (BedrockPart hiddenPart : hiddenParts) {
                hiddenPart.visible = true;
            }
        }

        // Submit the aperture/body/cleanup sequence in HAND_SOLID. Under an active Iris shader
        // pack, only reticle color and its physical ocular rim move to the later HAND_TRANSLUCENT
        // boundary; all 3D snapshots below retain the original ADS/recoil/view-bob transform.
        boolean orderedScopeSequence = apertureActive
                && texture != null
                && !ocularSnapshots.isEmpty()
                && !bodySnapshot.isEmpty();
        boolean deferReticleToIrisFinalOverlay = orderedScopeSequence
                && IrisCompat.isRenderingSolidHandPass()
                && IrisCompat.supportsFinalScopeOverlay();
        // Keep the R8/R9 hand-translucent path as a fallback for Iris versions whose final hook
        // was not bytecode-audited. The verified 1.10.7 path goes past final composite instead.
        boolean deferReticleToIrisTranslucent = orderedScopeSequence
                && IrisCompat.isRenderingSolidHandPass()
                && !deferReticleToIrisFinalOverlay;

        if (orderedScopeSequence) {
            PoseStack identity = new PoseStack();
            RenderType apertureWriter = ScopeRenderTypes.depthAperture(texture);
            OrderedSubmitNodeCollector apertureCollector = collector.order(SCOPE_APERTURE_ORDER);
            apertureCollector.submitCustomGeometry(identity, apertureWriter, (entryPose, consumer) -> {
                for (BedrockRenderSnapshot ocularSnap : ocularSnapshots) {
                    ocularSnap.write(consumer);
                }
            });

            // Step 3 of the ocular screen-space mask happens at this draw boundary: the wrapped
            // body type first copies the aperture depth (world depth plus only the ocular
            // differences) into the mask texture, and only then does the ordinary scope body draw.
            // Body fragments behind the invisible ocular still fail their normal depth test, so
            // neither stencil nor framebuffer attachment changes are involved.
            RenderType bodyWithApertureCopy = ScopeRenderTypes.apertureCopy(renderType);
            collector.order(SCOPE_BODY_ORDER).submitCustomGeometry(identity, bodyWithApertureCopy,
                    (entryPose, consumer) -> bodySnapshot.write(consumer));

            // Restore the aperture pixels from the exact pre-ocular world-depth backup. Iris renders water,
            // fog, particles and volumetric clouds after its solid-hand pass and needs the original depth.
            RenderType depthCleanup = ScopeRenderTypes.depthCleanup(texture);
            collector.order(SCOPE_DEPTH_CLEANUP_ORDER).submitCustomGeometry(identity, depthCleanup,
                    (entryPose, consumer) -> {
                        for (BedrockRenderSnapshot ocularSnap : ocularSnapshots) {
                            ocularSnap.write(consumer);
                        }
                    });

            // Without Iris deferral, keep the established order: reticle(1) then opaque rim(2).
            // With deferral, hold the rim until we know a reticle snapshot was actually queued;
            // it will then follow that snapshot in HAND_TRANSLUCENT and still cover edge spill.
            if (!deferReticleToIrisTranslucent && !deferReticleToIrisFinalOverlay
                    && ocularRingSnapshot != null && !ocularRingSnapshot.isEmpty()) {
                collector.order(SCOPE_OCULAR_RING_ORDER).submitCustomGeometry(identity, renderType,
                        (entryPose, consumer) -> ocularRingSnapshot.write(consumer));
            }
        } else if (!bodySnapshot.isEmpty()) {
            PoseStack identity = new PoseStack();
            collector.submitCustomGeometry(identity, renderType,
                    (entryPose, consumer) -> bodySnapshot.write(consumer));
        }

        // Render Reticle. Iris HAND_SOLID freezes only immutable snapshots here. Iris 1.10.7's
        // verified final-overlay path submits them after all shader-pack composites; older Iris
        // versions retain the R8/R9 HAND_TRANSLUCENT fallback. Vanilla remains immediate.
        int lateReticlesBefore = ScopeLateReticleState.pendingReticleCount();
        int finalReticlesBefore = ScopeFinalOverlayState.pendingReticleCount();
        if (transformType != null && transformType.firstPerson() && !reticleNodes.isEmpty()) {
            ScopeNodeSet active = filterReticleByActiveView(reticleNodes);
            IReticleRenderer reticle = ReticleRendererRegistry.select(active);
            if (reticle != null && !active.isEmpty()) {
                boolean etchedOnly = active.hasEtched() && !active.hasIlluminated() && texture != null;
                RenderType baseReticleType;
                if (etchedOnly) {
                    if (deferReticleToIrisFinalOverlay) {
                        baseReticleType = ScopeRenderTypes.finalEtchedReticle(texture);
                    } else if (deferReticleToIrisTranslucent) {
                        baseReticleType = ScopeRenderTypes.lateEtchedReticle(texture);
                    } else {
                        baseReticleType = ScopeRenderTypes.etchedReticle(texture);
                    }
                } else {
                    baseReticleType = renderType;
                }
                RenderType baseIlluminatedType;
                if (texture == null) {
                    baseIlluminatedType = renderType;
                } else if (deferReticleToIrisFinalOverlay) {
                    baseIlluminatedType = ScopeRenderTypes.finalVisibleReticle(texture);
                } else if (deferReticleToIrisTranslucent) {
                    baseIlluminatedType = ScopeRenderTypes.lateVisibleReticle(texture);
                } else {
                    baseIlluminatedType = ScopeRenderTypes.visibleReticle(texture);
                }

                // Pure etched trees are CPU-filtered to retain thin marks and discard large blackout panels.
                // Both renderers sample world/aperture depth per pixel and only retain the ocular interior.
                reticle.submitReticle(new IReticleRenderer.Context(
                        poseStack, collector.order(SCOPE_RETICLE_ORDER),
                        transformType, baseReticleType, baseIlluminatedType,
                        light, overlay, currentAimingProgress(), etchedOnly,
                        deferReticleToIrisTranslucent, deferReticleToIrisFinalOverlay), active);
            }
        }

        if ((deferReticleToIrisTranslucent || deferReticleToIrisFinalOverlay)
                && ocularRingSnapshot != null && !ocularRingSnapshot.isEmpty()) {
            boolean queuedFinal = deferReticleToIrisFinalOverlay
                    && ScopeFinalOverlayState.pendingReticleCount() > finalReticlesBefore;
            boolean queuedLate = deferReticleToIrisTranslucent
                    && ScopeLateReticleState.pendingReticleCount() > lateReticlesBefore;
            if (queuedFinal) {
                // The final rim is deliberately after final reticle geometry, preserving physical
                // lens-edge occlusion without giving Complementary another fog/composite pass.
                ScopeFinalOverlayState.queueOcularRing(
                        ocularRingSnapshot, ScopeRenderTypes.finalOcularRing(texture));
            } else if (queuedLate) {
                ScopeLateReticleState.queueOcularRing(
                        ocularRingSnapshot, ScopeRenderTypes.lateOcularRing(texture));
            } else {
                // No visible reticle this frame (for example during fade-in): preserve the normal
                // solid-pass rim rather than forcing an otherwise unnecessary deferred pass.
                collector.order(SCOPE_OCULAR_RING_ORDER).submitCustomGeometry(new PoseStack(), renderType,
                        (entryPose, consumer) -> ocularRingSnapshot.write(consumer));
            }
        }

        if (laserBeamPaths != null) {
            for (var entry : laserBeamPaths) {
                BeamRenderer.renderLaserBeam(attachmentItem, poseStack, transformType, entry, collector);
            }
        }
    }

    private boolean isOcularInActiveGroup(BedrockPart ocular) {
        if (activeViewGroup == 0 || !(isScope && isSight)) {
            return true;
        }
        Integer activeIndex = activeOcularIndex();
        if (activeIndex == null) {
            return true;
        }
        return ocularByIndex.get(activeIndex) == ocular;
    }

    private boolean shouldDrawOcularBlackout(BedrockPart ocular) {
        if (isSight && !isScope) {
            return false;
        }
        if (!(isScope && isSight)) {
            return true;
        }
        for (Map.Entry<Integer, BedrockPart> entry : ocularByIndex.entrySet()) {
            if (entry.getValue() == ocular) {
                return Boolean.TRUE.equals(ocularIsScopeByIndex.get(entry.getKey()));
            }
        }
        return true;
    }
}
