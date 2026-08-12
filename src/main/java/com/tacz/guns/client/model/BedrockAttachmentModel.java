package com.tacz.guns.client.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.bedrock.ModelRendererWrapper;
import com.tacz.guns.client.model.functional.BeamRenderer;
import com.tacz.guns.client.render.scope.IReticleRenderer;
import com.tacz.guns.client.render.scope.ReticleRendererRegistry;
import com.tacz.guns.client.render.scope.ScopeBodyRenderTypes;
import com.tacz.guns.client.render.scope.ScopeMaskGeometry;
import com.tacz.guns.client.render.scope.ScopeMaskTextureHandle;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.client.render.scope.ScopeNodeSet;
import com.tacz.guns.client.model.functional.TextShowRender;
import com.tacz.guns.client.resource.pojo.display.gun.TextShow;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class BedrockAttachmentModel extends BedrockAnimatedModel {
    private static final String SCOPE_VIEW_NODE = "scope_view";
    private static final String DIVISION_NODE = "division";
    private static final String OCULAR_NODE = "ocular";
    private static final String OCULAR_SIGHT_NODE = "ocular_sight";
    private static final String OCULAR_SCOPE_NODE = "ocular_scope";
    private static final String OCULAR_RING_NODE = "ocular_ring";
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

    /** 蚀刻分划节点（不发光）。P1 暂不绘制，留给 P2 的蚀刻策略。 */
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
     * 目镜节点。主画面<b>不画</b>它们（构造时已 visible=false），
     * 但要用它们的屏幕投影生成镜内掩码 —— 这正是上游 stencil 裁剪区域的来源。
     */
    protected final List<BedrockPart> ocularParts = new ArrayList<>();
    /**
     * 物理目镜框（镜口黑色实体内圈）。上游 1.21.1 始终以 {@code stencilFunc(ALWAYS)}
     * 无裁剪独立绘制它——它是实体件，<b>不是</b>孔径/遮光几何；默认枪包 14 个中高倍镜
     * 全部包含该骨骼（上游弃用旧命名 {@code oculus_ring} 另见邻链审计）。
     * 26.1.2 在体实证：把它混进「会被 ocular 区域杀掉的批」会逐镜统一啃掉内环
     * （26.2 掩码架构的同源病灶 = 案例③ 遗留的「镜框内圈边缘被 hull 掩码啃掉」）。
     * 只在构造时收集引用；摘除/重画与否由开关与开镜状态在 submit 里决定。
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
        // 【案例⑨ · 邻链回流】ocular_ring = 物理目镜框（实体件），上游单独无裁剪绘制。
        // 只收集引用、不隐藏：腰射 / 第三人称 / 未开镜时它必须随镜身完整出现；
        // 开镜掩码激活时才摘除主提交、事后用未裁剪 RenderType 重画（见 submit）。
        ModelRendererWrapper ocularRingWrapper = modelMap.get(OCULAR_RING_NODE);
        ocularRingPart = ocularRingWrapper == null ? null : ocularRingWrapper.getModelRenderer();
        // 初始化 view 的 node path
        List<BedrockPart> path = getPath(modelMap.get(SCOPE_VIEW_NODE));
        int i = 2;
        while (path != null) {
            scopeViewPaths.add(path);
            path = getPath(modelMap.get(SCOPE_VIEW_NODE + '_' + i++));
        }
        // 隐藏目镜（镜片）节点，并收集激光束节点。
        //
        // 【为什么目镜必须一个像素都不画】
        // 上游 renderOcularStencil 的第一行就是：
        //     RenderSystem.colorMask(false, false, false, false);
        //     RenderSystem.depthMask(false);
        // 目镜几何的<b>唯一</b>用途是往模板缓冲写值，好让镜身知道哪块区域属于镜内；
        // 它自身既不写颜色也不写深度。我们移植时把 colorMask 那行注释掉了
        // （26.2 无该 API），于是这块本该隐形的几何被实打实画成了不透明镜片 ——
        // 这就是用户实测反馈的「开镜后有镜片在遮挡」。
        //
        // 【为什么是 visible=false 而不是「隐形 RenderType」】
        // r52 曾建过一条 ColorTargetState.WRITE_NONE 的专用管线去提交目镜，
        // 想「保留几何、只是不可见」。那次实测崩在
        //     IllegalStateException: Missing sampler Sampler0
        //         at VulkanRenderPass.pushDescriptors
        // —— 26.2 的 RenderSetup 必须为管线声明的每个 sampler 绑定贴图，
        // 而那条管线基于 ENTITY_SNIPPET（需要 Sampler0），RenderSetup 里却一张都没绑。
        //
        // 但更根本的问题是：<b>那次提交本身就是多余的</b>。
        // 既然 26.2 没有模板缓冲，目镜写模板这个唯一用途已经不存在；
        // 一份「不写颜色、不写深度」的几何对画面的贡献严格为零，
        // 提交它只是在为一个不存在的下游消费者付出顶点与管线成本。
        // 因此正确做法不是修那条管线，而是让这条路径整个消失。
        //
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
     * 第 16 轮：当前的开镜进度（0 = 完全没开镜，1 = 完全开镜）。
     *
     * <p>用于决定是否绘制目镜的<b>不透明黑色遮罩</b>。上游 1.21.1 靠 stencil 把这块遮罩
     * 裁掉，26.2 已移除 stencil（第 9/10 轮已逐项确认），遮罩就原样画了出来 ——
     * 表现为「镜片永远是一块黑色贴图」。
     *
     * <p>观察 TACZ 官方宣传图可以确认：<b>不开镜时官方也不渲染镜片</b>，
     * 镜框里是直接透出背景的。所以在没有 stencil 的情况下，
     * 「不开镜就不画遮罩」既贴近官方观感，也是当前最合理的降级策略。
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
     * {@code SubmitNodeCollector#submitText} 的 <b>vanilla 字体管线</b>，
     * 用不了我们给镜身/准星写的 {@code scope_body.fsh}
     * （那是 {@code entityCutout} 的变体，靠 {@code SCOPE_MASK_INVERT}
     * 采样掩码做 discard）。也就是说<b>无法把它裁进镜内</b>。
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
     * 但上游靠 stencil 时圆孔与镜身严丝合缝，溢出不明显；
     * 我们的掩码是屏幕空间的，边界更"硬"，一露出来就很扎眼。
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
     * Backend-neutral collector path. Advanced scope stencil behavior intentionally degrades to
     * normal model geometry until the dedicated scope/PIP milestone.
     *
     * @param texture 该瞄具的贴图。传入后镜身才可能启用「目镜掩码裁剪」；
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

        // 【第 45 轮重构】此前这里有两段「靠切 visible 模拟 stencil」的逻辑，已整体移除：
        //   ① 目镜遮罩（shouldDrawOcularMask）—— 按开镜进度决定 ocular 画不画
        //   ② 镜身剔除（cullScopeBody）—— 开镜时把整根 scope_body 隐藏
        //
        // 移除依据（逐行核对上游 1.21.1 后确认，见 SCOPE_UPSTREAM_TRUTH_2026-07-27.md）：
        //
        // <b>上游从来没有「画一块黑色目镜」这回事。</b>
        // renderOcularStencil 的第一行就是 colorMask(false,false,false,false) ——
        // 目镜<b>只写模板值、完全不写颜色</b>。我们当年把 colorMask 注释掉之后，
        // 那块本该隐形的几何被实打实画成了黑片，于是又反过来加一个「遮罩开关」去补救；
        // 补救本身建立在对上游的误读上。
        //
        // <b>「镜身剔除」同理，是被 stencil 语义误导出来的概念。</b>
        // 上游 renderScope 第 4 步 stencilFunc(EQUAL,0) 的含义是
        // 「镜身只在目镜圆【之外】绘制」—— 这是一次<b>屏幕空间的区域二分</b>，
        // 不是「把镜身整根删掉」。我们没有 stencil，就退化成了全局布尔开关，
        // 结果连累红点/组合镜（r34、r35 两轮都在给这个错误概念打补丁）。
        //
        // 既然这两段的共同前提（stencil 区域裁剪）在 26.2 不存在，
        // 正确做法就不是继续调参，而是<b>让它们消失</b>：
        // 所有几何都按模型原样提交，交给 super.submit 统一处理。
        //
        // 代价与收益：
        //   - 代价：镜内会看到镜筒内壁（无区域裁剪能力，这是 26.2 的硬约束）；
        //   - 收益：不再有任何「按形态分类的可见性开关」，红点/筒镜/组合镜
        //           走<b>完全相同</b>的一条路径，自带瞄具出问题时无旁路可藏。
        //
        // 真正的解法是屏幕空间圆形裁剪（shader discard），参数已在
        // SCOPE_UPSTREAM_TRUTH §6.3 列出，但需先单独验证圆心坐标算法 —— 未做。

        // 【Step 2】登记目镜几何，供阶段边界的掩码 pass 使用。
        //
        // 只在第一人称登记：镜内裁剪只对开镜的本人有意义，
        // 第三人称看别人的枪不需要（也没有「镜内」可言）。
        //
        // 【顺序要求】必须在 super.submit 之【前】——
        // 下面选 RenderType 时要看「本帧有没有登记到目镜」来决定用不用裁剪版。
        //
        // 【开镜门禁】只在真正开镜时裁剪。不开镜时镜身必须完整 ——
        // 否则腰射状态下瞄具中间就是个洞，这不是上游的行为。
        // 上游同样把裁剪半径乘上 aimingProgress（renderOcularAndDivision:
        // `rad *= getClientAimingProgress(...)`），进度为 0 时半径归零 = 不裁剪。
        boolean maskable = transformType != null && transformType.firstPerson()
                && !ocularParts.isEmpty()
                && currentAimingProgress() > AIM_CLIP_START;
        // 【案例⑨ 第二轮 · sight（低倍/红点通道）激活时镜身不做掩码裁剪】
        // 上游事实（SCOPE_UPSTREAM_TRUTH §4，逐行核对 renderSight / renderBoth）：
        // 低倍链【没有】圆形 INVERT 模板、scope_body 无条件绘制；组合镜只对筒镜组
        // 走筒镜逻辑。我们的掩码若在 sight 通道激活时裁剪镜身，瞄具自己的内框/边缘
        // （普通镜身几何，红点类没有 ocular_ring 骨骼可摘）会在镜片投影内被啃出
        // 缺口——用户报告的慢性病灶「低倍镜（含组合镜低倍组）的边的内部某些部分
        // 被错误裁切」即此。sight 组目镜本就被 shouldDrawOcularBlackout 恒隐藏
        // （恒掏空 = 透视窗），撤掉镜身裁剪后观感 = 上游 r34 语义：窗内是世界、
        // 窗框完整，两者都不依赖掩码。开关 false = 旧行为（sight 也裁）。
        if (maskable && RenderConfig.SCOPE_SIGHT_CLIP_FIX != null
                && RenderConfig.SCOPE_SIGHT_CLIP_FIX.get()
                && !activeGroupIsScope()) {
            maskable = false;
        }
        // 光影开启时禁用 26.2 的离屏掩码裁剪，退回普通瞄具几何。
        //
        // 现象：Iris/Sulkan 类光影包会接管主世界的若干 pass（发光实体、天气/雾、后处理等），
        // 而我们的 scope mask/clip 是一套独立的自定义 RenderPipeline + 离屏 target。
        // 两者同时启用时，镜内会缺失经验球、蜘蛛/末影人眼睛等自发光层与雾效，甚至镜身本身被裁没。
        // 这不是崩溃类问题，但比“镜内见到镜筒内壁”的降级更糟，所以有光影时先走安全回退。
        boolean shaderMaskUnsafe = IrisCompat.shouldDisableScopeMaskUnderShaderPack();
        if (maskable && !shaderMaskUnsafe) {
            registerOcularMaskGeometry(poseStack);
        } else if (shaderMaskUnsafe) {
            maskable = false;
        }

        // 目镜（ocular*）参与 super.submit 时，要按上游规则决定<b>画不画黑片</b>。
        //
        // 上游只在 renderOcularAndDivision 里画目镜，而纯红点镜（renderSight）
        // 压根不调用它、组合镜（selective=true）也只给筒镜组画。
        // 详见 shouldDrawOcularBlackout 的注释。
        //
        // 实现方式是临时把不该画的目镜 visible=false，submit 完在 finally 里还原 ——
        // BedrockPart 是跨帧共享对象，不还原会污染第三人称与物品栏预览。
        //
        // 【Step 3】镜身改用「会被目镜掩码裁剪」的 RenderType，
        // 复刻上游 scope_body 的 stencilFunc(GL_EQUAL, 0)：只在目镜没盖到处画镜身。
        //
        // 任何一环不满足就原样退回 renderType（= RenderTypes.entityCutout），
        // 也就是当前已 PASS 的行为。最坏情况只是「镜内仍见镜筒内壁」，不会更糟。
        // 【案例③ 首判修正 · 回退记录】曾把黑片从主提交摘除并单独用反向裁剪版
        // RenderType 提交（认为黑环贴图中心透明、被自己的投影自裁）。
        // 实测（用户截图 + 全部瞄具贴图 alpha 取证）推翻前提：
        // ocular 板材采样的是【实心深色纹素】（alpha=255，非透明中心），
        // 「透视镜片」恰恰依赖它跟着镜身一起被自己的掩码投影 discard 才成立 ——
        // 单独重画等于把不透明深色玻璃拍回镜片上，表现为整片黑盘、甚至溢出镜框。
        // 因此恢复原路径：黑片依旧随 super.submit 走同一裁剪版 RenderType。
        // 案例③（黑环边缘被啃）的真因指向「几何投影掩码略大于真实通光孔径、
        // 吃掉 scope_body 内圈边缘」（上游用固定半径圆，见 PORTING_NOTES §3.5），
        // 留待拿到对照截图+掩码预览后再修。
        // 【案例⑨ · 26.1.2 → 26.2 邻链回流适配】ocular_ring 物理目镜框的独立路径：
        // 邻链在体实证（commit 0b7c4cd，含上游语义取证）——它是实体件而非孔径/遮光几何，
        // 上游 1.21.1 以 stencilFunc(ALWAYS) 无裁剪绘制；混入「会被 ocular 区域杀掉的批」
        // 会在全部 14 个含该骨骼的镜子上统一啃掉内环。本架构的同源形态 = 这里若让它
        // 随镜身走裁剪版 RenderType，开镜时内环被 hull 掩码啃掉（案例③ 遗留条目由此定因）。
        // 等价处置（本架构无深度 order 概念，且掩码只在 shader 端杀片段、无深度写入者）：
        // 开镜掩码激活时把它从主提交摘除（visible=false 会连子树一起摘除，
        // 见 BedrockRenderSnapshot.capturePart 的剪枝语义），主提交后用未裁剪的原版
        // RenderType 重画；普通 opaque cutout 天然回写深度（邻链的「重写入镜框深度
        // 防水/雾/透明粒子覆盖」语义在本架构免费成立，无需额外步骤）。
        // 开关 ScopeOcularRingFix = false 即整体回到旧行为（摘除+重画全部跳过）。
        // 无 ocular_ring 骨骼的第三方模型、腰射、第三人称、Iris 回退路径（maskable=false）
        // 均不受影响——此时 detaching=false，走原来的主提交。
        boolean detachOcularRing = maskable && ocularRingPart != null && ocularRingPart.visible
                && RenderConfig.SCOPE_OCULAR_RING_FIX != null && RenderConfig.SCOPE_OCULAR_RING_FIX.get();
        List<BedrockPart> hiddenOculars = new ArrayList<>();
        if (transformType != null && transformType.firstPerson()) {
            for (BedrockPart ocular : ocularParts) {
                if (ocular.visible && !shouldDrawOcularBlackout(ocular)) {
                    ocular.visible = false;
                    hiddenOculars.add(ocular);
                }
            }
        }
        if (detachOcularRing) {
            ocularRingPart.visible = false; // 主提交摘除（finally 里必还原，anti-lock 跨帧共享对象）
        }
        try {
            super.submit(poseStack, transformType, collector,
                    resolveBodyRenderType(renderType, texture, maskable), light, overlay);
        } finally {
            if (detachOcularRing) {
                ocularRingPart.visible = true;
            }
            for (BedrockPart ocular : hiddenOculars) {
                ocular.visible = true;
            }
        }
        // 重画物理目镜框（先于下面的准星提交，等价于邻链 ring=order 1 / reticle=order 2 的语义；
        // 本架构按提交顺序消费，且目镜框是 opaque cutout，深度测试下顺序本来就不敏感）。
        if (detachOcularRing) {
            submitOcularRingPlain(poseStack, collector, renderType, transformType, light, overlay);
        }

        if (transformType != null && transformType.firstPerson() && !reticleNodes.isEmpty()) {
            ScopeNodeSet active = filterReticleByActiveView(reticleNodes);
            IReticleRenderer reticle = ReticleRendererRegistry.select(active);
            if (reticle != null && !active.isEmpty()) {
                // 准星用【反向裁剪】版：只在目镜盖到处绘制。
                //
                // 这是上游 renderDivisionOnly 的等价物：
                //     stencilFunc(GL_EQUAL, i + 1)   // 模板值 i+1 = 第 i 个目镜的投影区
                // 也就是说准星【被约束在目镜投影内】。
                //
                // 少了这层约束，准星就会溢出镜筒、像贴纸一样固定在屏幕上，
                // 不随镜框缩放 —— 正是用户实测到的第 2 个问题。
                //
                // 注意方向别搞反：镜身是「盖到就 discard」，准星是「没盖到才 discard」。
                // 两者共用同一张掩码、同一份 shader，靠 SCOPE_MASK_INVERT 区分。
                RenderType reticleType = resolveReticleRenderType(renderType, texture, maskable);
                RenderType illuminatedReticleType = resolveIlluminatedReticleRenderType(renderType, texture, maskable);
                // maskActive：本帧准星是否真的走了反向裁剪。
                // EtchedReticleRenderer 依赖它决定敢不敢画 division（内含大块遮光板）。
                boolean maskActive = reticleType != renderType;
                reticle.submitReticle(new IReticleRenderer.Context(
                        poseStack, collector, transformType, reticleType, illuminatedReticleType,
                        light, overlay, currentAimingProgress(), maskActive), active);
            }
        }

        if (laserBeamPaths != null) {
            for (var entry : laserBeamPaths) {
                BeamRenderer.renderLaserBeam(attachmentItem, poseStack, transformType, entry, collector);
            }
        }
    }

    /**
     * 【案例⑨】以未裁剪的原版 RenderType 重画物理目镜框（含子树）。
     *
     * <p>父链遍历-套用写法与 {@link #registerOcularMaskGeometry} 同构：两者都复刻
     * {@code BedrockRenderSnapshot.captureSubtree} 的调用约定——rootPose 必须已经
     * 套用根节点自身及其全部父级变换（自底向上收集祖先 → 自顶向下逐个
     * {@code translateAndRotateAndScale}），captureSubtree 内部不再对根节点重复套用。
     * 该约定与主渲染产出的模型矩阵完全一致（registerOcularMaskGeometry 的注释链
     * 已多轮实测验证）。
     *
     * @param renderType 调用点收到的<b>原始</b> RenderType（未走 {@link #resolveBodyRenderType}
     *                   的裁剪分支），即该配件的正常材质——上游 stencilFunc(ALWAYS) 的等价物。
     */
    private void submitOcularRingPlain(PoseStack poseStack, SubmitNodeCollector collector,
                                       RenderType renderType, ItemDisplayContext transformType,
                                       int light, int overlay) {
        if (ocularRingPart == null) {
            return;
        }
        PoseStack ringPose = new PoseStack();
        ringPose.last().pose().set(poseStack.last().pose());
        ringPose.last().normal().set(poseStack.last().normal());
        java.util.List<BedrockPart> ringParents = new java.util.ArrayList<>();
        for (BedrockPart parent = ocularRingPart.getParent(); parent != null; parent = parent.getParent()) {
            ringParents.add(0, parent);
        }
        for (BedrockPart parent : ringParents) {
            parent.translateAndRotateAndScale(ringPose);
        }
        ocularRingPart.translateAndRotateAndScale(ringPose);
        com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot ringSnapshot =
                com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot.captureSubtree(
                        ocularRingPart, ringPose, transformType, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        if (!ringSnapshot.isEmpty()) {
            collector.submitCustomGeometry(new PoseStack(), renderType,
                    (entryPose, consumer) -> ringSnapshot.write(consumer));
        }
    }

    /**
     * 把目镜几何登记进 {@link ScopeMaskGeometry}，供阶段边界的掩码 pass 绘制。
     *
     * <h2>为什么必须自己走一遍父级链</h2>
     * 目镜在构造时被置为 {@code visible = false}，主渲染路径根本不会遍历到它，
     * 所以拿不到现成的矩阵。这里复刻
     * {@code BedrockRenderSnapshot#captureSubtree} 的调用约定：
     * 自底向上收集祖先链 → 自顶向下逐个 {@code translateAndRotateAndScale}，
     * 得到与主渲染<b>完全一致</b>的模型矩阵。
     *
     * <p>与 {@code IlluminatedReticleRenderer#submitOne} 同构 —— 那条路径已被
     * 多轮实测验证过（准星位置正确），这里沿用同一套写法，不另起炉灶。
     *
     * <h2>为什么不用改 visible</h2>
     * 准星那边要临时打开 {@code visible} 是因为它走
     * {@code captureSubtree}，而遍历器遇 {@code visible=false} 会直接 return。
     * 这里<b>不走遍历器</b>，直接取 {@code part.cubes}，
     * 因此不必碰 {@code visible} —— 少一处跨帧共享状态的改动，就少一个隐患。
     */
    private void registerOcularMaskGeometry(PoseStack poseStack) {
        for (BedrockPart ocular : ocularParts) {
            if (!isOcularInActiveGroup(ocular)) {
                // 组合镜：只有【当前正在用的】那一组目镜参与掩码。
                // 否则另一组的镜片也会把镜身挖穿 —— 用低倍红点时，
                // 高倍筒镜那一圈会莫名变透明（用户实测到的第 3 个问题）。
                continue;
            }
            java.util.Deque<BedrockPart> chain = new java.util.ArrayDeque<>();
            for (BedrockPart p = ocular; p != null; p = p.getParent()) {
                chain.push(p);
            }
            poseStack.pushPose();
            try {
                // 先套用祖先链（不含 ocular 自身），再交给递归 —— 递归会对每个
                // 节点（含 ocular）自己套一次变换，与 BedrockPart#render 的结构一致。
                for (BedrockPart p : chain) {
                    if (p != ocular) {
                        p.translateAndRotateAndScale(poseStack);
                    }
                }
                // 【不在这里做开镜过渡】——
                // 掩码几何始终按目镜的真实姿态登记，过渡在【屏幕空间】完成
                // （见 ScopeMaskRenderer 的 maskProgress 与 scope_body.fsh）。
                //
                // 早前这里按进度缩放过 3D 几何，实测效果是镜内区域从画面外
                // 「飞」进来，而不是原地由小变大。原因：透视投影下，缩放 3D 物体
                // 会同时改变它的投影【位置】，不只是大小。
                //
                // 上游不是这么做的 —— 它的圆心固定在目镜投影中心，只让半径随进度长：
                //     centerX/centerY = getBedrockPartCenter(...)   // 固定
                //     rad = 80 * modifier * aimingProgress          // 只有半径在变
                // 那是纯粹的二维操作。我们对应的二维操作就是在 shader 里
                // 按屏幕距离收缩掩码，几何本身不动。
                collectMaskGeometry(ocular, poseStack);
            } finally {
                poseStack.popPose();
            }
        }
    }

    /**
     * 决定镜身用哪个 RenderType：带掩码裁剪的，还是原样。
     *
     * <h2>为什么要这么多前置条件</h2>
     * 这条路径上任何一环出问题，最坏也只能退回「镜内看得见镜筒内壁」——
     * 那是<b>已经验证 PASS 的状态</b>。绝不能因为裁剪特性坏掉就让瞄具整个不可用。
     * 所以每个条件都是「不满足就退回」，而不是抛异常。
     *
     * @param original 调用方给的原始 RenderType（{@code RenderTypes.entityCutout(贴图)}）
     * @param texture  该瞄具的贴图；为 {@code null} 时无法构造等价的裁剪版，直接退回
     * @param maskable 本帧是否登记了目镜几何（第一人称 + 该瞄具确实有目镜）
     */
    private RenderType resolveBodyRenderType(RenderType original,
                                             @Nullable Identifier texture,
                                             boolean maskable) {
        if (!maskable) {
            // 第三人称，或这个配件压根没有目镜（如握把、消音器）—— 不需要裁剪。
            return original;
        }
        if (texture == null) {
            // 拿不到贴图就没法建等价的裁剪版 RenderType。宁可不裁剪，也不能画错贴图。
            return original;
        }
        if (!RenderConfig.SCOPE_MASK_ENABLE.get()) {
            // 特性总开关。Step 3 期间仍挂在调试开关下，默认关闭：
            // 万一有兼容问题，玩家关掉开关就能回到已知可用的状态，无需回滚版本。
            return original;
        }
        // 把掩码 target 的纹理挂到 TextureManager 上，供 shader 采样。
        // 掩码没画成（target 建不出来 / 绘制失败）时返回 false，此时必须退回，
        // 否则 shader 会采样到一张陈旧甚至无效的纹理。
        if (!ScopeMaskTextureHandle.syncToMaskTarget()) {
            return original;
        }
        return ScopeBodyRenderTypes.clipped(texture);
    }

    /**
     * 决定准星用哪个 RenderType：被约束在镜内的，还是原样。
     *
     * <p>前置条件与 {@link #resolveBodyRenderType} <b>完全相同</b>，只是最终
     * 换成反向裁剪的那一版。两者必须同进同退：若镜身裁了而准星没裁，
     * 准星会飘在镜外；若准星裁了而镜身没裁，镜内又会被镜身糊住。
     */
    private RenderType resolveReticleRenderType(RenderType original,
                                                @Nullable Identifier texture,
                                                boolean maskable) {
        if (!maskable || texture == null || !RenderConfig.SCOPE_MASK_ENABLE.get()) {
            return original;
        }
        if (!ScopeMaskTextureHandle.syncToMaskTarget()) {
            return original;
        }
        return ScopeBodyRenderTypes.reticle(texture);
    }

    /** 发光准星使用不受方向光影响的专用 RenderType，避免随玩家朝向变亮/变暗。 */
    private RenderType resolveIlluminatedReticleRenderType(RenderType original,
                                                          @Nullable Identifier texture,
                                                          boolean maskable) {
        if (texture == null) {
            return original;
        }
        if (!maskable || !RenderConfig.SCOPE_MASK_ENABLE.get()) {
            return ScopeBodyRenderTypes.emissive(texture);
        }
        if (!ScopeMaskTextureHandle.syncToMaskTarget()) {
            return ScopeBodyRenderTypes.emissive(texture);
        }
        return ScopeBodyRenderTypes.reticleEmissive(texture);
    }

    /**
     * 该目镜是否属于<b>当前激活</b>的镜组。
     *
     * <h2>为什么需要它</h2>
     * 组合镜（如 {@code scope_hamr}、{@code scope_mk5hd}）同时有两个目镜：
     * {@code ocular_sight}（红点分系统）与 {@code ocular_scope}（筒镜分系统）。
     * 但玩家一次只用其中一组。
     *
     * <p>若两组目镜都写进掩码，没在用的那组也会把镜身挖穿 ——
     * 表现为「某些瞄具的部分效果缺失」（用户实测：低倍红点状态下，
     * 高倍筒镜那一圈镜身莫名变透明）。
     *
     * <h2>判据可靠性</h2>
     * 实测默认枪包 4 个组合镜的目镜命名<b>全部</b>带前缀：
     * <pre>
     * scope_hamr         ocular_sight / ocular_scope_2
     * scope_mk5hd        ocular_sight / ocular_scope_2
     * scope_standard_8x  ocular_sight_2 / ocular_scope
     * scope_vudu         ocular_sight / ocular_scope_2
     * </pre>
     * 这一点比准星那边可靠得多 —— 准星有 {@code scope_vudu} 这种无前缀命名
     * （{@code division_illuminated}），只能退回全集；目镜没有这个问题。
     *
     * <p>{@code views[]} 的取值约定（{@code FirstPersonRenderGunEvent} 写入）：
     * {@code 1} = 红点组、{@code 2} = 筒镜组，与 {@code sight}/{@code scope}
     * 前缀一一对应（display json 实测：hamr/vudu 均为 {@code views=[2,1]}）。
     */
    private boolean isOcularInActiveGroup(BedrockPart ocular) {
        if (activeViewGroup == 0 || !(isScope && isSight)) {
            // 非组合镜（单一形态），不存在分组问题 —— 全都算数。
            return true;
        }
        Integer activeIndex = activeOcularIndex();
        if (activeIndex == null) {
            // 该模型的目镜没有分组信息 —— 无从判断，保留。
            // 与准星过滤同一原则：宁可多画，也不要误删成空掩码。
            return true;
        }
        return ocularByIndex.get(activeIndex) == ocular;
    }

    /**
     * 【案例⑨ 第三轮】当前激活的目镜通道是否为「筒镜（高倍）」——决定镜身要不要走
     * 掩码裁剪。判别输入与上游三分支（renderScope / renderSight / renderBoth）同源：
     * display json 的 {@code scope}/{@code sight} 双 flag + {@code views[]} 当前通道值。
     *
     * <h2>第二轮判别器为何误伤（教训：命名≠物理属性）</h2>
     * 当时直接读 {@code ocularIsScopeByIndex} —— 它的语义是「该节点名带不带
     * {@code ocular_scope} 前缀」，纯倍镜（AUG 默认镜 / ACOG / lpvo 等）的目镜名
     * 是普通 {@code ocular}，映射恒 false ⇒ 被误判为「非筒镜」而误关裁剪
     * （用户实测：倍镜组合高倍组与 AUG 默认镜失去一切裁剪痕迹）。
     * 还有 standard_8x（views=[1,1] 双 zoom 全组 1）、mk5hd（views=[2,2,1]）这类
     * 「flag 与命名不同构」的模型，只有 flag+通道才是稳态判据。
     *
     * <pre>
     * 纯筒镜（scope=true, sight=false）  → 恒 true（镜身恒裁，含 8x/lpvo/elcan 的多档 zoom）
     * 纯红点/全息（sight=true, scope=false）→ 恒 false（上游 renderSight 无条件画镜身）
     * 组合镜（双 flag）                   → 看当前通道：通道查不到/映射缺失时回退 true
     *                                     （保守维持旧行为，与「宁可多画」原则一致）
     * </pre>
     */
    private boolean activeGroupIsScope() {
        if (isSight && !isScope) {
            return false; // 纯红点/全息：上游 renderSight 从不裁镜身
        }
        if (isScope && !isSight) {
            return true;  // 纯筒镜：目镜无论叫什么名都是物理高倍通道
        }
        // 组合镜（hamr/vudu/mk5hd）：views[zoom] 选出当前通道；通道组内没有
        // 「名符其实」的筒镜目镜（如纯 plain-ocular 命名）时回退 true = 恒裁旧行为。
        Integer activeIndex = activeViewGroup == 0 ? null : activeOcularIndex();
        if (activeIndex == null) {
            return true;
        }
        return Boolean.TRUE.equals(ocularIsScopeByIndex.get(activeIndex));
    }

    /**
     * 当前是否应当把目镜画成<b>不透明黑色镜片</b>。
     *
     * <h2>上游的规则（逐行核对 renderSight / renderScope / renderBoth）</h2>
     * 「画黑片」这一步<b>只存在于 {@code renderOcularAndDivision} 里</b>，
     * 而三条渲染路径对它的调用情况是：
     * <pre>
     * renderScope (纯筒镜)   -> renderOcularAndDivision(selective=false)  画黑片
     * renderBoth  (组合镜)   -> renderOcularAndDivision(selective=true)   仅【筒镜组】画黑片
     * renderSight (纯红点)   -> 【根本不调用】                            从不画黑片
     * </pre>
     * {@code selective=true} 分支里那段 if/else 说得很清楚：
     * <pre>
     * if (selective &amp;&amp; !isScopeOcular.get(i)) {
     *     // 非筒镜组：只画分划，【跳过目镜黑片】
     *     renderTempPart(... divisionNodePaths.get(i));
     * } else {
     *     // 渲染目镜黑色遮罩
     *     renderTempPart(... ocularNodePaths.get(i));
     *     ...
     * }
     * </pre>
     *
     * <h2>为什么这正是用户报的第 3 个问题</h2>
     * 现实中红点/全息镜的镜片是<b>透光</b>的，本就不该有黑底；
     * 只有高倍筒镜因为光路封闭，未对准光轴时才是一片黑。
     * 我们此前无差别地把所有 {@code ocular*} 都交给 {@code super.submit} 正常渲染，
     * 于是低倍红点镜（{@code sight_*}）和组合镜的红点组在<b>未开镜</b>时
     * 都糊着一层本不该有的黑色遮罩。
     *
     * <p>注意判据是<b>该目镜是否属于筒镜分系统</b>，而不是「瞄具整体是不是 scope」：
     * 组合镜的两个目镜要分别判断，红点那个永远不画黑片。
     */
    private boolean shouldDrawOcularBlackout(BedrockPart ocular) {
        if (isSight && !isScope) {
            // 纯红点/全息镜：上游 renderSight 从不画黑片。
            return false;
        }
        if (!(isScope && isSight)) {
            // 纯筒镜：renderScope 用 selective=false，全部画黑片。
            return true;
        }
        // 组合镜：只有筒镜分系统的那个目镜画黑片。
        for (Map.Entry<Integer, BedrockPart> entry : ocularByIndex.entrySet()) {
            if (entry.getValue() == ocular) {
                return Boolean.TRUE.equals(ocularIsScopeByIndex.get(entry.getKey()));
            }
        }
        return true;
    }

    /**
     * 递归采集一个节点及其<b>所有子节点</b>的立方体。
     *
     * <h2>为什么必须递归（第一版就栽在这）</h2>
     * {@code BedrockModel#loadNewModel} 里有一条容易被忽略的分支：
     * <b>带 {@code rotation} 的 cube 不会进 {@code bone.cubes}</b>，
     * 而是被包进一个新建的 {@code BedrockPart} 作为<b>子节点</b>挂上去
     * （因为单个 cube 的自转必须有自己的变换原点）。
     *
     * <p>实测默认枪包：161 个目镜立方体里 <b>101 个带 rotation</b>（63%）。
     * 第一版只读 {@code ocular.cubes}，等于把这 63% 全漏了 ——
     * 再叠加 {@code instanceof BedrockCubeBox} 把剩下 37% 也滤光，
     * 最终一个顶点都没登记，掩码自然全黑、日志也不会打印任何东西
     * （因为 {@code isEmpty()} 提前 return 了）。
     *
     * <p>这里的遍历结构刻意与 {@link BedrockPart#render} 对齐：
     * 每个节点先 {@code translateAndRotateAndScale}，再收自己的 cubes，
     * 然后递归子节点。<b>不检查 {@code visible}</b> ——
     * 目镜自身就是 {@code visible=false}（主画面不画它），
     * 若按可见性过滤就又会一个都收不到。
     */
    private void collectMaskGeometry(BedrockPart part, PoseStack poseStack) {
        poseStack.pushPose();
        try {
            part.translateAndRotateAndScale(poseStack);
            if (!part.cubes.isEmpty()) {
                // Entry 的构造函数会拷贝矩阵 —— 必须如此：
                // poseStack 马上就 popPose 了，而这份数据要活到阶段边界才被消费。
                //
                // 关键：把“提交手持物这一刻”的 RenderSystem ModelView 烘焙进掩码矩阵。
                // 实际瞄具几何在延迟绘制时等价于 Projection * ModelView(submit) * itemPose * vertex；
                // 若掩码 pass 到阶段边界后再取当前 ModelView，Iris hand path 下会拿到与提交时
                // 不一致的矩阵，表现为裁剪区域固定在 world north。这里提前合成，mask pass 里
                // 使用 identity ModelView，保证采样区域跟随提交时的第一人称瞄具。
                Matrix4f bakedPose = new Matrix4f(RenderSystem.getModelViewMatrixCopy()).mul(poseStack.last().pose());
                ScopeMaskGeometry.add(bakedPose, part.cubes);
            }
            for (BedrockPart child : part.children) {
                collectMaskGeometry(child, poseStack);
            }
        } finally {
            poseStack.popPose();
        }
    }
}
