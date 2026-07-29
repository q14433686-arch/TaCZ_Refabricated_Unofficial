package com.tacz.guns.client.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.bedrock.ModelRendererWrapper;
import com.tacz.guns.client.model.functional.BeamRenderer;
import com.tacz.guns.client.render.scope.IReticleRenderer;
import com.tacz.guns.client.render.scope.ReticleRendererRegistry;
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
    protected @Nullable List<List<BedrockPart>> laserBeamPaths;

    private @Nullable ItemStack currentGunItem;
    private @Nullable ItemStack attachmentItem;

    private boolean isScope = false;
    private boolean isSight = false;

    public BedrockAttachmentModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
        scopeViewPaths = new ArrayList<>();
        laserBeamPaths = new ArrayList<>();
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

        boolean maskable = transformType != null && transformType.firstPerson()
                && !ocularParts.isEmpty()
                && currentAimingProgress() > AIM_CLIP_START;

        // Capture ocular snapshots for stencil writing
        List<com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot> ocularSnapshots = new ArrayList<>();
        if (maskable) {
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
                    ocularSnapshots.add(com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot.captureSubtree(
                            ocular, ocularPose, transformType, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F
                    ));
                }
            }
        }

        // Capture body snapshot (hide non-blackout oculars first)
        List<BedrockPart> hiddenOculars = new ArrayList<>();
        if (transformType != null && transformType.firstPerson()) {
            for (BedrockPart ocular : ocularParts) {
                if (ocular.visible && !shouldDrawOcularBlackout(ocular)) {
                    ocular.visible = false;
                    hiddenOculars.add(ocular);
                }
            }
        }
        com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot bodySnapshot;
        try {
            bodySnapshot = com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot.capture(
                    this, poseStack, transformType, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F
            );
        } finally {
            for (BedrockPart ocular : hiddenOculars) {
                ocular.visible = true;
            }
        }

        // Render Ocular + Body using Stencil
        if (maskable && !ocularSnapshots.isEmpty() && !bodySnapshot.isEmpty()) {
            PoseStack identity = new PoseStack();
            // 1. Submit Ocular to write stencil
            collector.submitCustomGeometry(identity, renderType, (entryPose, consumer) -> {
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_STENCIL_TEST);
                org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_STENCIL_BUFFER_BIT);
                org.lwjgl.opengl.GL11.glStencilOp(org.lwjgl.opengl.GL11.GL_KEEP, org.lwjgl.opengl.GL11.GL_KEEP, org.lwjgl.opengl.GL11.GL_REPLACE);
                org.lwjgl.opengl.GL11.glStencilFunc(org.lwjgl.opengl.GL11.GL_ALWAYS, 1, 0xFF);
                com.mojang.blaze3d.systems.RenderSystem.colorMask(false, false, false, false);
                com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
                
                for (com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot ocularSnap : ocularSnapshots) {
                    ocularSnap.write(consumer);
                }
                
                com.mojang.blaze3d.systems.RenderSystem.colorMask(true, true, true, true);
                com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
                org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_STENCIL_TEST);
            });

            // 2. Submit Body (drawn only outside ocular)
            collector.submitCustomGeometry(identity, renderType, (entryPose, consumer) -> {
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_STENCIL_TEST);
                org.lwjgl.opengl.GL11.glStencilFunc(org.lwjgl.opengl.GL11.GL_EQUAL, 0, 0xFF);
                bodySnapshot.write(consumer);
                org.lwjgl.opengl.GL11.glStencilFunc(org.lwjgl.opengl.GL11.GL_ALWAYS, 0, 0xFF);
                org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_STENCIL_TEST);
            });
        } else {
            // Unmaskable: normal rendering via custom geometry
            if (!bodySnapshot.isEmpty()) {
                PoseStack identity = new PoseStack();
                collector.submitCustomGeometry(identity, renderType, (entryPose, consumer) -> bodySnapshot.write(consumer));
            }
        }

        // Render Reticle
        if (transformType != null && transformType.firstPerson() && !reticleNodes.isEmpty()) {
            ScopeNodeSet active = filterReticleByActiveView(reticleNodes);
            IReticleRenderer reticle = ReticleRendererRegistry.select(active);
            if (reticle != null && !active.isEmpty()) {
                RenderType baseReticleType = resolveReticleRenderType(renderType, texture, false);
                RenderType baseIlluminatedType = resolveIlluminatedReticleRenderType(renderType, texture, false);
                
                boolean maskActive = maskable;
                SubmitNodeCollector wrappedCollector = new SubmitNodeCollector() {
                    @Override
                    public net.minecraft.client.renderer.OrderedSubmitNodeCollector order(int value) {
                        return collector.order(value);
                    }

                    @Override
                    public void submitCustomGeometry(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometry customGeometry) {
                        collector.submitCustomGeometry(pose, type, (entryPose, consumer) -> {
                            if (maskActive) {
                                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_STENCIL_TEST);
                                org.lwjgl.opengl.GL11.glStencilFunc(org.lwjgl.opengl.GL11.GL_EQUAL, 1, 0xFF);
                            }
                            customGeometry.draw(entryPose, consumer);
                            if (maskActive) {
                                org.lwjgl.opengl.GL11.glStencilFunc(org.lwjgl.opengl.GL11.GL_ALWAYS, 0, 0xFF);
                                org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_STENCIL_TEST);
                            }
                        });
                    }
                };

                reticle.submitReticle(new IReticleRenderer.Context(
                        poseStack, wrappedCollector, transformType, baseReticleType, baseIlluminatedType,
                        light, overlay, currentAimingProgress(), maskActive), active);
            }
        }

        if (laserBeamPaths != null) {
            for (var entry : laserBeamPaths) {
                BeamRenderer.renderLaserBeam(attachmentItem, poseStack, transformType, entry, collector);
            }
        }
    }

    private RenderType resolveBodyRenderType(RenderType original, @Nullable Identifier texture, boolean maskable) {
        return original;
    }

    private RenderType resolveReticleRenderType(RenderType original, @Nullable Identifier texture, boolean maskable) {
        return original;
    }

    private RenderType resolveIlluminatedReticleRenderType(RenderType original, @Nullable Identifier texture, boolean maskable) {
        if (texture == null) {
            return original;
        }
        return net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(texture);
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
