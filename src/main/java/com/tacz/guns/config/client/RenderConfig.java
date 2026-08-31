package com.tacz.guns.config.client;

import com.tacz.guns.client.renderer.crosshair.CrosshairType;
import net.minecraftforge.common.ForgeConfigSpec;

public class RenderConfig {
    public static ForgeConfigSpec.BooleanValue ENABLE_LASER_FADE_OUT;
    public static ForgeConfigSpec.BooleanValue ILLUMINATED_REAL_SKY;
    public static ForgeConfigSpec.IntValue GUN_LOD_RENDER_DISTANCE;
    public static ForgeConfigSpec.IntValue BULLET_HOLE_PARTICLE_LIFE;
    public static ForgeConfigSpec.DoubleValue BULLET_HOLE_PARTICLE_FADE_THRESHOLD;
    public static ForgeConfigSpec.EnumValue<CrosshairType> CROSSHAIR_TYPE;
    public static ForgeConfigSpec.DoubleValue HIT_MARKET_START_POSITION;
    public static ForgeConfigSpec.BooleanValue HEAD_SHOT_DEBUG_HITBOX;
    /** 瞄准镜镜内裁剪（目镜掩码）总开关。默认<b>开启</b>。 */
    public static ForgeConfigSpec.BooleanValue SCOPE_MASK_ENABLE;
    /** 【调试】把瞄具目镜掩码贴图画到屏幕左上角，用于排查离屏渲染链路。默认关闭。 */
    public static ForgeConfigSpec.BooleanValue SCOPE_MASK_DEBUG;
    public static ForgeConfigSpec.BooleanValue SCOPE_MASK_HULL_FILL;
    /** 开镜掩码激活时，物理目镜框 {@code ocular_ring} 摘除主提交并以未裁剪 RenderType 重画（上游 stencil-ALWAYS 语义）。默认<b>开启</b>。 */
    public static ForgeConfigSpec.BooleanValue SCOPE_OCULAR_RING_FIX;
    /** 低倍/红点通道（含组合镜低倍组）激活时，镜身不做目镜掩码裁剪（上游 renderSight 无条件绘制镜身）。默认<b>开启</b>。 */
    public static ForgeConfigSpec.BooleanValue SCOPE_SIGHT_CLIP_FIX;
    /**
     * 低倍/红点通道激活时，<b>准星仍然</b>被约束在目镜投影内（reticle-only mask）。默认<b>开启</b>。
     *
     * <p>与 {@link #SCOPE_SIGHT_CLIP_FIX} 是两件独立的事：上游 {@code renderSight}
     * 不裁镜身，但<b>照样</b>调 {@code renderOcularStencil} + {@code renderDivisionOnly}
     * （{@code stencilFunc(GL_EQUAL, i+1)}）把分划限制在目镜区域内。
     * 关掉本开关 = 回到「低倍通道整帧不建掩码、准星可溢出镜片」的旧行为。</p>
     */
    public static ForgeConfigSpec.BooleanValue SCOPE_SIGHT_RETICLE_CLIP;
    public static ForgeConfigSpec.BooleanValue GUN_HUD_ENABLE;
    public static ForgeConfigSpec.BooleanValue KILL_AMOUNT_ENABLE;
    public static ForgeConfigSpec.DoubleValue KILL_AMOUNT_DURATION_SECOND;
    public static ForgeConfigSpec.IntValue TARGET_RENDER_DISTANCE;
    public static ForgeConfigSpec.BooleanValue FIRST_PERSON_BULLET_TRACER_ENABLE;
    public static ForgeConfigSpec.BooleanValue TRACER_DEBUG;
    public static ForgeConfigSpec.ConfigValue<String> TRACER_DEBUG_GUN;
    public static ForgeConfigSpec.IntValue TRACER_DEBUG_INTERVAL_MS;
    public static ForgeConfigSpec.IntValue TRACER_DEBUG_FIRST_TICKS;
    public static ForgeConfigSpec.BooleanValue SHELL_EJECTION_DEBUG;
    public static ForgeConfigSpec.BooleanValue LASER_DEBUG;
    public static ForgeConfigSpec.ConfigValue<String> SHELL_EJECTION_DEBUG_GUN;
    public static ForgeConfigSpec.IntValue SHELL_EJECTION_DEBUG_INTERVAL_MS;
    public static ForgeConfigSpec.BooleanValue RECOIL_DEBUG;
    /** 【RecoilDebug 隔离】运行时关闭枪口火光渲染（定位斜向侧偏视觉载体用）。默认 false=正常渲染。 */
    public static ForgeConfigSpec.BooleanValue DEBUG_DISABLE_MUZZLE_FLASH;
    /** 【RecoilDebug 隔离】运行时关闭第一人称抛壳。 */
    public static ForgeConfigSpec.BooleanValue DEBUG_DISABLE_SHELL;
    /** 【RecoilDebug 隔离】运行时关闭曳光弹道实体渲染。 */
    public static ForgeConfigSpec.BooleanValue DEBUG_DISABLE_TRACER;
    /** 【RecoilDebug 隔离】运行时关闭摄像机动画（镜头摇动）的两处消费（世界叠加 + 手部旋转），含数据清理。 */
    public static ForgeConfigSpec.BooleanValue DEBUG_DISABLE_CAMERA_ANIM;
    /**
     * 【案例⑧ · 第 28~30 轮实验开关】第一人称手部锁视角强制（renderFirstPerson 捕获基座后
     * 左乘 (B·MV)⁻¹）。第 30 轮裁决：modelView 在手部 pass 不受控（第 26 轮枪口归一化实证 +
     * 第 29 轮缩放污染探针）+ 用户实测「没解决」⇒ 理论证伪，默认关闭；保留开关供 A/B。
     */
    public static ForgeConfigSpec.BooleanValue HAND_VIEW_LOCK_FIX;
    /**
     * 【案例⑧ · 第 30 轮 A/B 开关，第 32 轮起废弃（代码零读取）】
     * 原「false 强制落 mode 0」legacy 映射被现场证明是配置陷阱：用户 A/B 期间
     * 把本键留在 false，导致其显式设置的 ConstraintCompensateMode=2 被静默否决，
     * 实测跑的其实是 mode 0。自第 32 轮起档位只认 {@link #CONSTRAINT_COMPENSATE_MODE}；
     * 本布尔仅保留注册，让旧配置文件原样加载不报错。
     */
    public static ForgeConfigSpec.BooleanValue CONSTRAINT_BASE_COMPENSATE;
    /**
     * 【案例⑧ · 第 31~35 轮】约束位移写入形态：
     * 0 = plain（修复前原版 diag·v0；秒回退档——用户两次实测本案症状全消，
     *     已知代价：四方向斜向后坐力侧漏原样保留）；
     * 1 = Bᵀ·diag·Bᵀ 三明治（第 31 轮在体否决：本案病灶注入源；归档勿用）；
     * 2 = 姿态帧共轭 P_post·diag·P_preᵀ·v0（第 33 轮在体否决：斜向漏消除但
     *     「整体随朝向转」复现 + 手感不自然；归档勿用）；
     * 3 = 【本仓库默认 · 第 36 轮起】从 26.1.2 移植线在体验证形态转写：
     *     v = Ŵ·D·Wᵀ·v0，Ŵ=Q·W·Q。邻链关键认识：写回 (−x,−y,+z) 藏了
     *     Q=diag(−1,−1,+1)，真正 authored 系数是 C=Q·D；Q 与旋转不可交换，
     *     必须连 Q 一起共轭。26.1.2 与 26.2 双线在体验证通过
     *     （第 36 轮用户答卷「不转 / 不漏 / 自然」三项全过，案例⑧ 结案）。
     */
    public static ForgeConfigSpec.IntValue CONSTRAINT_COMPENSATE_MODE;
    /**
     * 【案例⑧ · 第 29 轮并行开关】锁视角读取 modelView 时剔除 3x3 缩放分量：
     * v5 日志实测该读取在部分帧上携带 0.9933~1.0068 的均匀缩放（旋转分部逐位不变），
     * 缩放会经 (B·MV)⁻¹ 烙进基座。默认开启；置 false 可单独回退本步而不动第 28 轮主修复。
     */
    public static ForgeConfigSpec.BooleanValue HAND_VIEW_LOCK_NORMALIZE;
    /**
     * 【光影枪身闪烁 · 修复】Iris 26.x 的 {@code HandRenderer} 一帧跑两遍手部
     * （renderSolid + renderTranslucent），Iris 自己会在半透明遍里取消实心物品
     * （{@code iris$skipTranslucentHands}），但 TACZ 用 WrapOperation 替换了
     * {@code submitArmWithItem} 调用点，该取消对 TACZ 视模永远不生效 ⇒ 枪身
     * （entityCutout）被提交进两遍（gbuffers_hand + gbuffers_hand_water）、动画状态机
     * 一帧推进两次。labPBR/SEUS PBR 光影对 hand water 遍的照明与实心遍不同，
     * 两层叠加即表现为枪身反射光源时的整块明暗闪烁。开启后视模只提交实心遍，
     * 复刻 Iris 对普通实心物品的语义。默认开启；2026-08-29 用户在体 A/B 验证 PASS
     * （Complementary + labPBR/SEUS PBR 下枪身闪烁消失），关回 false 可秒回退。
     */
    public static ForgeConfigSpec.BooleanValue IRIS_HAND_PHASE_SPLIT_FIX;
    public static ForgeConfigSpec.BooleanValue DISABLE_INTERACT_HUD_TEXT;
    public static ForgeConfigSpec.BooleanValue AUTO_SELECT_GUN_SMITH_TABLE_FILTER;
    public static ForgeConfigSpec.IntValue DAMAGE_COUNTER_RESET_TIME;
    public static ForgeConfigSpec.BooleanValue DISABLE_MOVEMENT_ATTRIBUTE_FOV;
    public static ForgeConfigSpec.BooleanValue ENABLE_TACZ_ID_IN_TOOLTIP;
    public static ForgeConfigSpec.BooleanValue BLOCK_ENTITY_TRANSLUCENT;
    /**
     * 瞄准镜「镜内画中画（PIP）」总开关。默认<b>关闭</b>。
     *
     * <p>开启后世界 FOV <b>不再</b>整屏变焦（{@code CameraSetupEvent#applyScopeMagnification}
     * 对装了倍镜的枪整体让位），改为把已画好的世界拷一份、按倍率在屏幕空间重投影，
     * 再由目镜掩码把这张图贴进镜片孔径 —— 于是镜外保持 1×、只有镜片里是放大的。
     *
     * <p>代价是镜内画面来自主画面中心裁切区的放大，高倍镜（6× 以上）会明显变软。
     * 因为这属于观感取舍而非性能取舍，默认关闭、由玩家自己选。
     */
    public static ForgeConfigSpec.BooleanValue SCOPE_PIP_ENABLE;
    /** 开镜进度低于该值时不做 PIP（此时孔径几乎闭合，拷贝纯属浪费）。 */
    public static ForgeConfigSpec.DoubleValue SCOPE_PIP_MIN_AIMING_PROGRESS;
    /**
     * 瞄具倍率低于该值时不做 PIP，改走原来的整屏变焦。
     *
     * <p>PIP 对低倍镜是笔亏本买卖：2×/3× 下整屏变焦的观感本来就自然
     * （视野收窄不明显），PIP 却照付全套成本 —— 每帧一次全屏拷贝，
     * 二次渲染模式下更是整遍世界重画。高倍镜才是 PIP 的目标场景
     * （8× 整屏变焦会把周边视野压没）。组合镜按<b>当前档位</b>判定，
     * 切到低倍档自动回整屏变焦，切回高倍档自动回 PIP。
     */
    public static ForgeConfigSpec.DoubleValue SCOPE_PIP_MIN_MAGNIFICATION;
    /**
     * 镜内锐化强度（0 = 关）。
     *
     * <p>镜内画面是主画面中心区按倍率放大来的，放大倍数<b>就是</b>瞄具倍率 ——
     * 6 倍镜就是 6× 放大，必然变软。锐化不能凭空造出细节，但能显著挽回主观锐度。
     * 实际强度按倍率线性加权（1× 不锐化，6× 及以上取满），所以低倍镜不会被过度处理。
     */
    public static ForgeConfigSpec.DoubleValue SCOPE_PIP_SHARPNESS;
    /**
     * 瞄具倍率里有多大一份由<b>世界</b>承担（0 = 全归镜内，纯 PIP；1 = 全归世界，等于关掉 PIP）。
     *
     * <p>镜内清晰度的硬上限是「屏幕分辨率 ÷ 镜内放大倍数」。倍率是相乘的，
     * 按 {@code 世界 = Z^share、镜内 = Z^(1-share)} 拆分后总倍率恒为 Z，
     * 而镜内拿到的真实像素<b>多 Z^share 倍</b>。
     * 这是唯一能真正增加镜内分辨率（而非仅提升主观锐度）的旋钮。
     *
     * <p>刻意用<b>比例</b>而不是绝对倍率上限：后者会被 Z 夹住，
     * 于是任何 ≥ Z 的取值都让镜内倍率退化成 1（PIP 名存实亡），
     * 且那个临界点随瞄具倍率漂移 —— 实测中玩家正是撞上了这条。
     */
    public static ForgeConfigSpec.DoubleValue SCOPE_PIP_WORLD_ZOOM_SHARE;
    /**
     * 开镜时持枪晃动的强度倍数（{@code 1.0} = 与改动前一致）。
     *
     * <p>晃动本身是「枪跟不上视角转动」的滞后量，腰射与开镜原本一视同仁。
     * 但开镜后视野被瞄具收窄、镜内还被放大 Z 倍，同样的角度抖动在镜内会被放大同样的倍数
     * —— 现实里高倍镜正是「越放大越难稳住」。本项让开镜那一档单独可调。
     *
     * <p>按开镜进度插值：腰射恒为 1，满开镜取到本值。{@code 0} = 满开镜时完全不晃。
     */
    public static ForgeConfigSpec.DoubleValue AIMING_SWAY_INTENSITY;
    /**
     * 允许在光影包启用时也跑 PIP。默认<b>关闭</b>。
     *
     * <p>关闭是保守默认，不是「已知不兼容」—— 见 {@code ScopePipRenderer} 里的说明。
     */
    public static ForgeConfigSpec.BooleanValue SCOPE_PIP_ALLOW_SHADER_PACKS;
    /**
     * 镜内画面改用「窄 FOV 把世界再画一遍」，而不是重投影主画面。默认<b>关闭</b>。
     *
     * <p>重投影的镜内分辨率上限是「屏幕分辨率 ÷ 倍率」—— 8 倍镜下惨不忍睹。
     * 本模式的镜内像素是真画出来的，没有那个上限；代价是每帧多跑一遍完整世界渲染。
     *
     * <p>已知：与 Sodium 的地形投影快照需要同步（{@code SodiumCompat} 负责）；
     * 早前的实现还出现过「镜外实体消失」，尚未定位，所以默认关闭、按需自测。
     */
    public static ForgeConfigSpec.BooleanValue SCOPE_PIP_RERENDER;
    /**
     * 二次渲染（非光影模式）下镜内画面的渲染分辨率比例（1.0 = 屏幕原生分辨率）。
     * 默认 0.75。开销按面积走（0.75x 相当于仅渲染 ~56% 像素），显著减轻二次渲染开销。
     *
     * <p>【作用域必读】只有「二次渲染 + 无光影」这一种组合消费它：
     * <ul>
     *   <li>重投影模式（默认）下无效 —— 镜内是已画好的主画面的放大采样，
     *       根本不存在可以降分辨率的第二遍渲染，降采样拷贝只糊不省；</li>
     *   <li>光影下强制 1.0 —— Iris 画进自己那套 colortex，我们的离屏 target
     *       只是成品的拷贝目的地，缩小它省不掉 Iris 那遍的任何真实开销。
     *       光影下的开销杠杆是 {@link #SCOPE_PIP_SHADOW_SCALE} 与
     *       {@link #SCOPE_PIP_RERENDER_INTERVAL}。</li>
     * </ul>
     * 两条不消费它的路径都会打一次性日志明说（见 ScopePipRenderer）。
     */
    public static ForgeConfigSpec.DoubleValue SCOPE_PIP_RESOLUTION_SCALE;
    /**
     * 二次渲染模式下，镜内那一遍世界每 N 帧才真正渲染一次，其余帧复用上一帧的镜内画面。
     *
     * <p>这是<b>光影下唯一能砍到大头的杠杆</b>：光影 + 二次渲染的帧率对半，根因是
     * 整条 Iris 管线（阴影贴图、gbuffer、composite 链）每帧跑两遍 —— 降低离屏
     * target 分辨率救不了它（见 {@link #SCOPE_PIP_RESOLUTION_SCALE} 的作用域说明），
     * 但「每两帧才跑第二遍」能把那份额外开销直接减半。
     *
     * <p>代价：转动视角时镜内画面滞后 N-1 帧（N=2 时为一帧，接近难以察觉）。
     * 镜外主画面永远满帧率。掩码/剪裁是逐帧的，只有镜内<b>内容</b>滞后。
     */
    public static ForgeConfigSpec.IntValue SCOPE_PIP_RERENDER_INTERVAL;
    /**
     * 二次渲染 + 光影时，是否给镜内那一遍配一套独立的 Iris 管线。
     *
     * <p>不隔离的话，Iris 那一整族「上一帧」uniform 会被一帧推进两次，
     * 主画面的时域效果（TAA、体积云、SSGI）全部失准 —— 表现为拖影、云噪点，
     * 以及<b>开镜时镜外整屏发糙</b>。隔离的代价是多一套 colortex（显存）。
     */
    /** 镜内那一遍的阴影贴图分辨率比例（1.0 = 与主画面相同）。开销按面积走。 */
    public static ForgeConfigSpec.DoubleValue SCOPE_PIP_SHADOW_SCALE;
    public static ForgeConfigSpec.BooleanValue SCOPE_PIP_ISOLATE_PIPELINE;
    /**
     * 【诊断】跑完镜内那一遍，但<b>不做合成</b>。
     *
     * <p>用来一刀切开「放大画面溢出到镜外」这个症状的两种可能：
     * <ul>
     *   <li>画面干净（只有正常世界、镜片里什么都没有）→ 二次渲染是<b>关在离屏 target 里</b>的，
     *       溢出来自合成/掩码；</li>
     *   <li>放大画面照样溢出 → 二次渲染<b>漏到主画面</b>了，与合成无关。</li>
     * </ul>
     * 两种情况的修法完全不同，靠肉眼看成品是分不出来的。
     */
    public static ForgeConfigSpec.BooleanValue SCOPE_PIP_DEBUG_NO_COMPOSITE;
    /**
     * 【诊断】把镜内那一遍期间的渲染目标解析顺序打进日志（只记前几帧）。
     * 见 {@code ScopePipTrace} 的类注释 —— 这个症状靠静态分析已经连错四次。
     */
    public static ForgeConfigSpec.BooleanValue SCOPE_PIP_DEBUG_TRACE;
    /**
     * 【诊断】把合成实际覆盖到的区域涂成纯品红。
     * 整屏变品红 = 合成没被掩码约束住；只有镜片变品红 = 合成是对的，溢出来自别处。
     */
    public static ForgeConfigSpec.BooleanValue SCOPE_PIP_DEBUG_PAINT_LENS;
    /**
     * 【实验 · 光影下开镜帧率衰减】空闲时销毁瞄具那套 Iris 管线（释放其全部 GPU 资源），
     * 玩家重新开镜时再重建。用于验证「衰减随 scope pass 次数累积、重进存档重置」的累积源
     * 是否在瞄具管线的保留 GPU 状态里。开启后每次重新开镜会付一次管线重建（shaderpack
     * 编译）成本。默认关；见 ScopePipRenderer#prewarmShaderPipelineIfNeeded 与
     * IrisScopePipelineCompat#releaseScopePipelineIfPresent。
     */
    public static ForgeConfigSpec.BooleanValue SCOPE_PIP_RELEASE_IDLE_PIPELINE;
    /**
     * 【实验配套】空闲释放前要连续空闲多少帧（默认 120 ≈ 2 秒 @60fps），
     * 防开镜/收镜过渡噪声把管线反复拆建。
     */
    public static ForgeConfigSpec.IntValue SCOPE_PIP_IDLE_RELEASE_DELAY_FRAMES;
    /**
     * 【诊断】每 600 帧打一次瞄具/主管线各自的 GPU 纹理字节数与 scope pass 累计数，
     * 量化「衰减是否随 scope pass 次数在显存侧累积」。默认关。
     */
    public static ForgeConfigSpec.BooleanValue SCOPE_PIP_DEBUG_GPU_MEM;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("render");

        builder.comment("Whether or not apply fadeout effect on the laser beam. Close this may improve laser performance under some shaders.");
        ENABLE_LASER_FADE_OUT = builder.define("EnableLaserFadeOut", true);

        builder.comment("'_illuminated' bones (glowing sights, tritium dots) are forced to full",
                "brightness 0xF000F0 - both the block AND sky light columns maxed. Vanilla",
                "needs both, but shader packs read sky=15 as 'this surface can see the sky',",
                "so glowing parts inherit sun/moon lighting at night. true = when a shader",
                "pack is active, keep block=15 but use the real environment sky light.",
                "Applies to both the cube layer and the poly_mesh layer so the two halves",
                "of one gun stay consistent. No effect without a shader pack.");
        ILLUMINATED_REAL_SKY = builder.define("IlluminatedRealSky", true);

        builder.comment("How far to display the lod model, 0 means always display");
        GUN_LOD_RENDER_DISTANCE = builder.defineInRange("GunLodRenderDistance", 0, 0, Integer.MAX_VALUE);

        builder.comment("The existence time of bullet hole particles, in tick");
        BULLET_HOLE_PARTICLE_LIFE = builder.defineInRange("BulletHoleParticleLife", 400, 0, Integer.MAX_VALUE);

        builder.comment("The threshold for fading out when rendering bullet hole particles");
        BULLET_HOLE_PARTICLE_FADE_THRESHOLD = builder.defineInRange("BulletHoleParticleFadeThreshold", 0.98, 0, 1);

        builder.comment("The crosshair when holding a gun");
        CROSSHAIR_TYPE = builder.defineEnum("CrosshairType", CrosshairType.DOT_1);

        builder.comment("The starting position of the hit marker");
        HIT_MARKET_START_POSITION = builder.defineInRange("HitMarketStartPosition", 4d, -1024d, 1024d);

        builder.comment("Whether or not to display the head shot's hitbox");
        HEAD_SHOT_DEBUG_HITBOX = builder.define("HeadShotDebugHitbox", false);
        // 特性与调试分离：
        // 早前两者共用 ScopeMaskDebug 一个开关，导致「关掉左上角预览」会连镜内裁剪
        // 一起关掉。现在拆开——功能默认开，预览默认关。
        SCOPE_MASK_ENABLE = builder
                .comment("Whether to clip the scope body/reticle inside the ocular (see-through scope).")
                .define("ScopeMaskEnable", true);
        SCOPE_MASK_DEBUG = builder
                .comment("Debug: draw the scope ocular mask texture at the top-left corner.")
                .define("ScopeMaskDebug", false);
        // 【案例③ 第二轮：凸包填充模式】
        // 「几何投影」掩码（默认枪包实测覆盖 33 款）在两类瞄具上失真：
        //   - 全玻璃板目镜（红点/全息）：掩码≈孔径，表现正确；
        //   - 板条拼玻璃的目镜（AUG 3 条十字、elcan 8 片竖板）：掩码只有板条区域,
        //     孔径内的镜身内壁网格漏裁 = 镜片里残留灰块（AUG 最明显）;
        //   - 高倍筒镜若玻璃板大于真实孔径, 又会把镜框内圈啃掉一圈 (黑边被裁)。
        // 凸包模式：把当帧目镜几何投影的【2D 凸包】整体涂进掩码 —
        // 板条展开的跨度正好勾勒出孔径内切多边形，掩码从「形状描摹」升级为「孔径近似」。
        // 严格比板条掩码覆盖更大 → 漏裁类残块必消；镜框内圈是否在部分镜种被啃,
        // 由用户对照截图裁决（开 ScopeMaskDebug 可见掩码本体）。
        SCOPE_MASK_HULL_FILL = builder
                .comment("Scope mask shape: true = fill the convex hull of the ocular projection (recommended;",
                        "fixes sparse-sliver oculars like AUG leaving scope-body fragments inside the sight picture).",
                        "false = legacy raw ocular geometry projection, kept as an instant fallback.")
                .define("ScopeMaskHullFill", true);

        // 【案例⑨ · 26.1.2 邻链回流适配】ocular_ring 物理目镜框：上游 1.21.1 以
        // stencilFunc(ALWAYS) 无裁剪独立绘制（实体件，非孔径/遮光几何；默认枪包 14 个
        // 中高倍镜全有）。26.1.2 在体实证：混入会被 ocular 区域杀掉的批 ⇒ 内环被啃。
        // 26.2 掩码架构同源病灶 = 案例③ 遗留「镜框内圈被 hull 掩码啃掉」。处置 =
        // 开镜掩码激活时摘除主提交、事后以未裁剪原版 RenderType 重画（含子树）。
        // false = 秒回退到旧行为（随主提交走裁剪版）。
        SCOPE_OCULAR_RING_FIX = builder
                .comment("[FIX] Draw the physical ocular_ring (scope eyepiece rim) unclipped while aiming;",
                        "mirrors upstream 1.21.1 stencil-ALWAYS handling (verified in-body on the 26.1.2 port).",
                        "Without this, the oculus mask nibbles the ring's inner rim on all mid/high-zoom scopes",
                        "that contain the bone. Set false to revert instantly. Default on.")
                .define("ScopeOcularRingFix", true);

        // 【案例⑨ 第二轮】sight（低倍/红点）通道不留掩码裁剪：上游 renderSight 的
        // scope_body 无条件绘制（无圆形 INVERT 模板；组合镜只对筒镜组走筒镜逻辑）。
        // 掩码对 sight 激活帧裁剪镜身 ⇒ 瞄具自己的内框/边缘在镜片投影内被啃缺口
        // （用户报告的慢性低倍镜病灶）。sight 目镜本就恒隐藏（恒掏空=透视窗），
        // 故撤裁后观感与上游一致。false = 旧行为（sight 也裁）。
        SCOPE_SIGHT_CLIP_FIX = builder
                .comment("[FIX] Skip the ocular-mask body clip while aiming through a low-power/red-dot",
                        "sight (including the low-power side of combo scopes). Upstream 1.21.1 renderSight",
                        "draws the sight body unconditionally; our clip nibbled the sight's own inner frame.",
                        "Default on; set false to restore legacy clipping on sights too.")
                .define("ScopeSightClipFix", true);

        // 【案例⑨ 第四轮】ScopeSightClipFix 只该关掉【镜身】裁剪，却顺带关掉了掩码本身，
        // 于是低倍/红点通道的准星失去目镜约束（上游 renderSight 仍然 renderOcularStencil
        // + renderDivisionOnly(stencilFunc EQUAL i+1)）。两个消费者拆开后，本开关单独控制
        // 「低倍通道是否仍建 reticle-only 掩码」。false = 旧行为（低倍准星可溢出镜片）。
        SCOPE_SIGHT_RETICLE_CLIP = builder
                .comment("[FIX] Keep constraining the reticle inside the ocular while aiming through a",
                        "low-power/red-dot sight (reticle-only mask, body left unclipped).",
                        "Upstream 1.21.1 renderSight still writes the ocular stencil and draws the",
                        "division with stencilFunc(GL_EQUAL, i+1); only the body clip is skipped there.",
                        "Default on; set false to restore the uncontained low-power reticle.")
                .define("ScopeSightReticleClip", true);

        builder.comment("Whether or not to display the gun's HUD");
        GUN_HUD_ENABLE = builder.define("GunHUDEnable", true);

        builder.comment("Whether or not to display the kill amount");
        KILL_AMOUNT_ENABLE = builder.define("KillAmountEnable", true);

        builder.comment("The duration of the kill amount, in second");
        KILL_AMOUNT_DURATION_SECOND = builder.defineInRange("KillAmountDurationSecond", 3, 0, Double.MAX_VALUE);

        builder.comment("The farthest render distance of the target, including minecarts type");
        TARGET_RENDER_DISTANCE = builder.defineInRange("TargetRenderDistance", 128, 0, Integer.MAX_VALUE);

        builder.comment("Whether or not to render first person bullet trail");
        FIRST_PERSON_BULLET_TRACER_ENABLE = builder.define("FirstPersonBulletTracerEnable", true);

        TRACER_DEBUG = builder
                .comment("[DEBUG] Log first-person tracer/bullet trail origin diagnostics. Default off.")
                .define("TracerDebug", false);
        TRACER_DEBUG_GUN = builder
                .comment("[DEBUG] Optional gun id filter for TracerDebug. Empty = all guns; accepts full id such as hamster:mp18 or path only such as mp18.")
                .define("TracerDebugGun", "");
        TRACER_DEBUG_INTERVAL_MS = builder
                .comment("[DEBUG] Minimum interval between tracer debug log lines after the first few bullet ticks, in milliseconds.")
                .defineInRange("TracerDebugIntervalMs", 500, 50, 10000);
        TRACER_DEBUG_FIRST_TICKS = builder
                .comment("[DEBUG] Always log at most once per bullet tick for the first N ticks. Set 0 to use only TracerDebugIntervalMs.")
                .defineInRange("TracerDebugFirstTicks", 3, 0, 20);

        SHELL_EJECTION_DEBUG = builder
                .comment("[DEBUG] Log first-person shell ejection capture/submit diagnostics. Useful for shader hand-pass issues. Default off.")
                .define("ShellEjectionDebug", false);
        SHELL_EJECTION_DEBUG_GUN = builder
                .comment("[DEBUG] Optional gun id filter for ShellEjectionDebug. Empty = all guns; accepts full id such as hamster:mp18 or path only such as mp18.")
                .define("ShellEjectionDebugGun", "");
        SHELL_EJECTION_DEBUG_INTERVAL_MS = builder
                .comment("[DEBUG] Minimum interval between shell ejection debug log lines, in milliseconds.")
                .defineInRange("ShellEjectionDebugIntervalMs", 250, 50, 10000);

        // 【LaserDebug · 第 28 轮：NVIDIA + Iris 下激光改颜色不生效 一案的二分探针】
        // 用户实测：N 卡 + 光影开启时改装界面里换激光颜色无效（N 卡关光影 / A 卡开关光影均正常）。
        // 激光颜色走【顶点色】提交（BeamRenderer.setColor），渲染类型用 vanilla
        // entityTranslucentEmissive。本探针在每次提交时记录「待写入的 RGB」，
        // 据此二分：若日志里颜色跟着改色变而画面不变 → 问题在 GL/Iris 管线侧；
        // 若日志里颜色也不变 → 问题在数据侧（NBT/同步/索引缓存）。
        LASER_DEBUG = builder
                .comment("[DEBUG] Log the laser beam vertex color at each submit (throttled 1s),",
                        "to bisect the 'laser recolor has no effect on NVIDIA + shader pack' issue. Default off.")
                .define("LaserDebug", false);

        RECOIL_DEBUG = builder
                .comment("[DEBUG] Log camera recoil diagnostics: per-shot recoil spline envelopes and per-frame",
                        "pitch/yaw deltas applied to the player rotation with facing + iris state, plus the level/item",
                        "camera-animation quaternions. Used to trace direction-bias of recoil feedback vs facing. Default off.")
                .define("RecoilDebug", false);

        // 【RecoilDebug · 第 27.4 轮：斜向"后坐力反馈固定侧偏"视觉载体隔离开关】
        // 数值取证已证明第一人称渲染管线全链干净（基座/绘制残差/后坐/镜头动画/枪根与枪口
        // 视图坐标在开枪的斜向帧上全部朝向无差异），但用户仍能稳定复现 sin(2θ) 镜像规律的
        // 侧偏 → 载体必为某个可视元素。以下四个运行时开关供逐项关闭定位：
        // 关掉哪一项后偏转消失，哪一项就是载体。
        DEBUG_DISABLE_MUZZLE_FLASH = builder
                .define("DebugDisableMuzzleFlash", false);
        DEBUG_DISABLE_SHELL = builder
                .define("DebugDisableShell", false);
        DEBUG_DISABLE_TRACER = builder
                .define("DebugDisableTracer", false);
        DEBUG_DISABLE_CAMERA_ANIM = builder
                .define("DebugDisableCameraAnim", false);

        // 【案例⑧主修复 · 第 28 轮】手部锁视角强制。
        // 取证链（20:25 日志, v3 探针逐帧相位对齐）：开镜换弹/开火期间
        // R=modelView×手部基座 ≡ 摄像机动画全量旋转（k=1.000, corr=0.998），
        // 轴为世界系固定轴（跨朝向世界系夹角中位 6.5° = 噪声底）——即手部链
        // 完全丢失摄像机动画的世界系补偿，整枪图像被该旋转整体甩动，
        // 且屏幕投影方向随玩家朝向旋转 ⇒「臂枪整体随朝向平移」「斜向最偏」
        // 「除北外后坐过分下压」。Iris 手部 pass 基座≈单位阵使 R 恒=I，
        // 与「开光影全部正常」的目击互洽 ⇒ 修复 = 把 vanilla 手部链也钉成 R=I。
        // 关闭本开关即可秒回退旧行为（掩码类改动并行开关铁律）。
        // 【第 30 轮裁决·默认回退】用户复测「没解决」+ 第 26 轮的既有实证
        // （26.2 手部 pass 的 RenderSystem modelView 仅为兼容保留、内容不受控——
        // 枪口归一化当时正是弃用它改为入口基座转置才定案）+ 第 29 轮探针抓到该读取
        // 携带合法缩放污染，三重证据表明：锁视角修复建立在错误的矩阵上。
        // 默认改回 false（=回到「四方向斜向修复」定版以来用户实测过的基线）；
        // 仍保留开关供你的 A/B 实验随时开回。
        HAND_VIEW_LOCK_FIX = builder
                .comment("[EXPERIMENT] Force-lock first-person hand view via the RenderSystem modelView matrix.",
                        "26.2 keeps that matrix compat-only for the hand pass, so this lock was reverted to",
                        "OFF by default after in-body rebuttal. Enable only for A/B experiments.")
                .define("HandViewLockFix", false);

        // 【光影枪身闪烁 · 修复】证据链见字段声明处的完整注释。要点：
        // Iris HandRenderer 一帧两遍手部 pass（实心 + 半透明）；Iris 对实心物品在半透明遍
        // 有 submitArmWithItem HEAD 取消，但 TACZ 替换了该调用点，取消落空 ⇒ 枪身被画两遍
        // （gbuffers_hand + gbuffers_hand_water）。labPBR/SEUS PBR 下两遍照明不同 ⇒ 反射光源处
        // 整块明暗闪烁。开启 = 视模只走实心遍（枪口火光/抛壳随之只走实心遍，实心遍同样属于
        // HAND program，之前的水面层叠加只是重复绘制）。默认 true；2026-08-29 用户在体 A/B
        // 验证 PASS。若出现回归，关回 false 即秒回退旧行为。
        IRIS_HAND_PHASE_SPLIT_FIX = builder
                .comment("[FIX] Submit TACZ first-person viewmodels only to the Iris solid hand pass",
                        "(gbuffers_hand), skipping the translucent hand pass (gbuffers_hand_water) where",
                        "Iris's own solid-item cancellation never applies because TACZ replaces the",
                        "submitArmWithItem call. With labPBR/SEUS PBR shader packs the duplicate water-pass",
                        "copy is lit differently and shows up as whole-body brightness flicker on light",
                        "reflections. Default ON; verified in-body PASS (2026-08-29). Set false to revert.")
                .define("IrisHandPhaseSplitFix", true);

        // 第 32 轮起：本布尔已不再被任何代码读取（原 legacy 否决映射证明是配置陷阱，
        // 会把用户显式设置的 ConstraintCompensateMode 静默降级）。保留注册仅为
        // 兼容旧配置文件；档位一律用 ConstraintCompensateMode。
        CONSTRAINT_BASE_COMPENSATE = builder
                .comment("[DEPRECATED since round 32] No longer read by any code. Kept only so existing",
                        "config files load cleanly. Use ConstraintCompensateMode (0/1/2) instead.")
                .define("ConstraintBaseCompensate", true);

        // 第 33 轮：mode 2 首次真实生效即被用户在体否决（回报「①转 / ②不漏 / ③不自然」——
        // 姿态帧共轭确实消除了斜向侧漏，但「整枪随朝向转」复现、且手感不自然），
        // 与 mode 1（三明治）并列归档。默认自此回落 mode 0（plain）：
        // 用户两次全场实测确认的「本案症状全消」态；已知代价 = 8/10 前就存在的
        // 四方向斜向后坐力侧漏原样回归。
        // 第 35 轮追加 mode 3：26.1.2 线在体验证形态（写入向量 Ŵ·D·Wᵀ·v0，Ŵ=Q·W·Q，
        // 即把写回符号里藏的 Q=diag(-1,-1,+1) 一并纳入共轭）；第 36 轮 26.2 在体
        // 复现通过（不转/不漏/自然）⇒ 翻为默认，mode 0 留作秒回退档。
        CONSTRAINT_COMPENSATE_MODE = builder
                .comment("[FIX] Constraint-translation write form:",
                        "0 = plain (instant fallback; user-verified clean of case-8 main symptoms; known cost: diagonal ADS recoil leak),",
                        "1 = legacy B^T-diag-B^T sandwich (REJECTED in-body: facing-locked rotation; archived),",
                        "2 = pose-frame conjugate (REJECTED in-body: leak fixed but rotation returned + unnatural feel; archived),",
                        "3 = DEFAULT: conjugates the true authored coefficient C=diag(-1,-1,1)*D inside the live pose frame;",
                        "    verified in-body on both 26.1.2 and 26.2 (no rotation, no diagonal leak, natural feel).",
                        "The old ConstraintBaseCompensate boolean is deprecated and fully ignored.")
                .defineInRange("ConstraintCompensateMode", 3, 0, 3);

        // 第 29 轮：实测 modelView 顶部在部分帧携带 ~0.7% 均匀缩放（旋转不变），
        // 不剔除会被 (B·MV)⁻¹ 烙进基座与整条姿态链；本开关只控制归一化这一步。
        HAND_VIEW_LOCK_NORMALIZE = builder
                .comment("[FIX] Strip any 3x3 scale from the captured modelView before composing the",
                        "hand-view lock matrix (observed 0.9933~1.0068 uniform scale on some frames).",
                        "Only effective when HandViewLockFix is on. Default on.")
                .define("HandViewLockNormalize", true);

        builder.comment("Disable the interact hud text in center of the screen");
        DISABLE_INTERACT_HUD_TEXT = builder.define("DisableInteractHudText", false);

        builder.comment("Whether or not to automatically select the gun smith table's held item filter when opening it with a gun, attachment or ammo in main hand");
        AUTO_SELECT_GUN_SMITH_TABLE_FILTER = builder.define("AutoSelectGunSmithTableFilter", true);

        SCOPE_PIP_ENABLE = builder
                .comment("Magnify only INSIDE the scope lens instead of zooming the whole screen",
                        "(picture-in-picture). The view around the scope tube stays at 1x.",
                        "Implemented by reprojecting a copy of the already-rendered frame, so it costs",
                        "one fullscreen copy and works with Sodium/other terrain renderers.",
                        "Tradeoff: the lens magnifies a centre crop of the frame, so high-power scopes",
                        "(6x and up) look noticeably softer than the surrounding view.",
                        "Requires ScopeMaskEnable and is skipped while a shader pack is active. Default off.")
                .define("ScopePipEnable", false);
        SCOPE_PIP_MIN_AIMING_PROGRESS = builder
                .comment("Skip the picture-in-picture work while the aiming progress is below this value",
                        "(the ocular aperture is still nearly closed down there).")
                .defineInRange("ScopePipMinAimingProgress", 0.05d, 0.0d, 1.0d);
        SCOPE_PIP_MIN_MAGNIFICATION = builder
                .comment("Only use picture-in-picture when the scope's CURRENT zoom level is at least",
                        "this value; weaker optics fall back to the classic full-screen zoom.",
                        "Low-power scopes (2x-3x) look fine with full-screen zoom and PIP costs a",
                        "fullscreen copy every frame (or a full world re-render in rerender mode),",
                        "so paying that price only for high-power optics is usually the better deal.",
                        "Variable scopes are judged by the zoom level currently selected.",
                        "1.0 = PIP for every scope (old behavior).")
                .defineInRange("ScopePipMinMagnification", 4.0d, 1.0d, 100.0d);
        SCOPE_PIP_SHARPNESS = builder
                .comment("Sharpening applied to the scope image (0 = off). The lens magnifies a centre crop",
                        "of the frame by exactly the scope's zoom factor, so high-power optics are soft;",
                        "sharpening cannot invent detail but recovers a lot of apparent crispness.",
                        "Strength is scaled by magnification, so low-power optics stay untouched.")
                .defineInRange("ScopePipSharpness", 0.5d, 0.0d, 1.0d);
        SCOPE_PIP_WORLD_ZOOM_SHARE = builder
                .comment("How much of the scope's magnification the WORLD takes, instead of the lens.",
                        "Trades the purity of PIP for real sharpness inside the lens.",
                        "",
                        "The lens magnifies a centre crop of the frame, so at Zx it only has 1/Z of the",
                        "screen's pixels to work with -- that is the whole reason high-power optics look",
                        "soft. Zoom factors multiply, so the split is:",
                        "    world = Z^share      lens = Z^(1-share)      world * lens = Z always",
                        "The lens gets 'world' times more real pixels to build the image from.",
                        "",
                        "  0.0 = the lens does all the work; outside stays 1x (purest PIP, softest)",
                        "  0.5 = split evenly; the lens image is built from sqrt(Z)x more real pixels",
                        "  1.0 = the world does all the work (identical to turning PIP off)",
                        "",
                        "Total magnification is always exactly the scope's zoom, at every setting --",
                        "this only moves where it comes from. Scale-independent, so the same value",
                        "behaves the same on a 2x and an 8x optic.",
                        "Ignored when ScopePipRerender is on -- that path already renders at native",
                        "resolution, so zooming the world would cost image quality for nothing.")
                .defineInRange("ScopePipWorldZoomShare", 0.0d, 0.0d, 1.0d);
        AIMING_SWAY_INTENSITY = builder
                .comment("How much the gun sways while aiming down sights, as a multiplier.",
                        "",
                        "Sway is the gun lagging behind your view when you turn -- it is what makes the",
                        "sight picture drift and settle. Hip fire is never affected by this setting; the",
                        "value is blended in by aiming progress, so it reaches full strength only when",
                        "fully scoped.",
                        "  0.0 = rock steady once fully aimed, no sway at all",
                        "  1.0 = the original amount, identical to before this option existed",
                        "  1.5 = default, noticeably more alive without being hard to aim",
                        "  3.0+ = heavy, deliberately difficult",
                        "Worth raising with high-power optics: a narrow field of view (and the PIP lens,",
                        "which magnifies by the scope's zoom on top) multiplies the same angular wobble,",
                        "the way real magnified optics get harder to hold steady.",
                        "The fast-turn safety clamp still applies, so the gun cannot swing off screen.")
                .defineInRange("AimingSwayIntensity", 1.0d, 0.0d, 5.0d);
        SCOPE_PIP_ALLOW_SHADER_PACKS = builder
                .comment("Allow the scope picture-in-picture to run while a shader pack is active.",
                        "Off by default as a precaution, NOT because it is known to be broken: under a",
                        "deferred shader pipeline the captured frame may not be fully shaded yet, and the",
                        "composite writes raw colour before tonemapping, so the lens could look flat or",
                        "blown out. Nothing can be corrupted by trying it - turn it on and look.")
                .define("ScopePipAllowShaderPacks", false);
        SCOPE_PIP_RERENDER = builder
                .comment("Draw the scope image by rendering the world a SECOND time with a narrow FOV,",
                        "instead of reprojecting the already-rendered frame. The lens then has native",
                        "resolution instead of being capped at screen resolution / zoom, which matters a",
                        "lot for 6x-8x optics. Costs a full extra world render every frame.",
                        "",
                        "This now works with shader packs too. The scope pass runs the shader pipeline",
                        "to completion first, its finished image is copied aside, and the normal frame",
                        "then renders over it -- two sequential frames as far as Iris is concerned, so",
                        "they reuse the same buffers and cost no extra VRAM.",
                        "Expect roughly HALF the frame rate with shaders on, since the whole pipeline",
                        "(shadow maps and composite chain included) runs twice. Temporal effects such",
                        "as TAA advance twice per frame as well, which can show up as ghosting or",
                        "shimmer; if that bothers you, use ScopePipWorldZoomShare instead.",
                        "",
                        "EXPERIMENTAL: an earlier attempt made entities vanish from the main view.",
                        "Default off.")
                .define("ScopePipRerender", false);
        SCOPE_PIP_RESOLUTION_SCALE = builder
                .comment("Render resolution scale for the scope pass in rerender mode (1.0 = native resolution).",
                        "Default 0.75 (~56% pixels of full frame), greatly reducing the GPU rendering cost of the scope view.",
                        "  1.0 = native resolution (sharpest, highest cost)",
                        "  0.75 = default (~56% pixels, high clarity with noticeable performance saving)",
                        "  0.5 = 50% resolution (25% pixels, maximum performance, softer image)",
                        "",
                        "SCOPE: only consumed by ScopePipRerender=true WITHOUT a shader pack.",
                        " - Reprojection mode (ScopePipRerender=false) ignores it: the lens is a resample",
                        "   of the already-rendered main frame; there is no second render to downscale.",
                        " - Under shader packs it is forced to 1.0: Iris renders into its own buffers at",
                        "   native size and our offscreen target is only a copy destination; shrinking it",
                        "   saves nothing. Use ScopePipShadowScale / ScopePipRerenderInterval instead.")
                .defineInRange("ScopePipResolutionScale", 0.75d, 0.25d, 1.0d);
        SCOPE_PIP_RERENDER_INTERVAL = builder
                .comment("In rerender mode, actually render the scope world pass only every N frames and",
                        "reuse the previous lens image in between. 1 = every frame (default).",
                        "",
                        "This is the lever that actually helps under shader packs: the half-rate cost",
                        "there comes from running the whole Iris pipeline (shadow map, gbuffers,",
                        "composites) twice per frame. Lowering the offscreen resolution cannot touch",
                        "that, but rendering the scope pass every 2nd frame halves the extra cost.",
                        "Trade-off: while turning the camera the lens content lags N-1 frames behind",
                        "(barely noticeable at 2). The main view always runs at full frame rate.",
                        "  1 = every frame (no saving)",
                        "  2 = half the scope-pass cost, ~1 frame of lens lag",
                        "  3-4 = bigger savings, visible lens stutter")
                .defineInRange("ScopePipRerenderInterval", 1, 1, 4);
        SCOPE_PIP_SHADOW_SCALE = builder
                .comment("Shadow map resolution for the scope pass, as a fraction of the pack's own.",
                        "Only used with ScopePipRerender + ScopePipIsolatePipeline + a shader pack.",
                        "",
                        "Iris renders shadows once per world render, so rendering the world twice draws",
                        "the whole shadow map twice per frame -- often one of the most expensive things",
                        "in a shader frame. Cost scales with AREA, so 0.5 cuts that pass' shadow work to",
                        "about a quarter. Rounded down to a power of two, minimum 256.",
                        "Only the lens is affected; the main view keeps the pack's full shadow map.",
                        "  1.0 = same as the main view (no saving)",
                        "  0.5 = default, ~1/4 the shadow cost for the scope pass",
                        "  0.25 = ~1/16, visibly blockier shadows in the lens",
                        "Takes effect when the scope pipeline is built, so restart or change dimension.")
                .defineInRange("ScopePipShadowScale", 0.5d, 0.25d, 1.0d);
        SCOPE_PIP_ISOLATE_PIPELINE = builder
                .comment("Give the scope pass its own shader pipeline, so its temporal state cannot",
                        "corrupt the main view. Only has any effect with ScopePipRerender on and a",
                        "shader pack active.",
                        "",
                        "Iris advances every 'previous frame' value when it is READ, not once per frame.",
                        "Rendering twice therefore leaves the main view reprojecting against the scope",
                        "pass's matrices, which breaks TAA, volumetric clouds and SSGI at once: ghosting,",
                        "shimmering clouds, and a grainy screen outside the scope while aiming (that",
                        "graininess is temporal accumulation failing, not sharpening).",
                        "Isolating the pass gives it separate buffers and separate uniforms, so both",
                        "views stay correct.",
                        "",
                        "Costs an extra set of shader buffers (a few hundred MB of VRAM at high",
                        "resolutions) and a one-time shader compile the first time you aim. Turn this",
                        "off if VRAM is tight, and the artifacts above come back.",
                        "Note: in dimensions other than the Overworld the lens may use the pack's",
                        "fallback shaders, since the pass uses its own dimension id.",
                        "",
                        "Voxy is handled alongside this: the scope pass is given its own Voxy viewport",
                        "too, the same way Voxy already separates the Iris shadow pass. Without that,",
                        "Voxy's per-view LOD state gets driven by two different projections in one frame",
                        "and its distant terrain corrupts permanently after the first time you aim.")
                .define("ScopePipIsolatePipeline", true);
        SCOPE_PIP_DEBUG_NO_COMPOSITE = builder
                .comment("[DEBUG] Run the scope pass but skip pasting it into the lens. Use this to tell",
                        "whether magnified imagery leaking outside the scope comes from the off-screen",
                        "pass escaping onto the screen, or from the composite/mask not confining it:",
                        "  clean screen, empty lens -> the off-screen pass is contained; blame the composite",
                        "  magnified imagery still leaks -> the pass itself is escaping to the main target")
                .define("ScopePipDebugNoComposite", false);
        SCOPE_PIP_DEBUG_TRACE = builder
                .comment("[DEBUG] Log which code resolves which render target during the scope pass,",
                        "for the first few frames only. Any line marked MAIN between SCOPE-PASS BEGIN",
                        "and SCOPE-PASS END is imagery escaping onto the screen; anything logged after",
                        "the vanilla clear means a renderer submits its draws late and cannot be",
                        "redirected at all.",
                        "",
                        "EXPENSIVE. While armed this walks the call stack on every render-target",
                        "resolve, and Sodium, Voxy and the frame graph all hit that path many times a",
                        "frame. Leave it off for normal play: it stalls the render thread enough that",
                        "terrain uploads pile up, and the resulting oversized GPU buffer request can",
                        "fail outright while exploring. It now disarms itself after a few hundred",
                        "frames regardless, but there is no reason to pay for it unless you are",
                        "chasing a scope render bug.")
                .define("ScopePipDebugTrace", false);
        SCOPE_PIP_DEBUG_PAINT_LENS = builder
                .comment("[DEBUG] Paint the area the scope composite actually covers in solid magenta.",
                        "  whole screen magenta -> the composite is NOT confined by the ocular mask",
                        "  only the lens magenta -> the composite is fine and the leak is elsewhere")
                .define("ScopePipDebugPaintLens", false);
        SCOPE_PIP_RELEASE_IDLE_PIPELINE = builder
                .comment("[EXPERIMENT] Destroy the scope pass' isolated Iris pipeline while not aiming, to",
                        "release its full GPU resources; it is rebuilt (with a shaderpack compile cost) on the",
                        "next aim. Tests whether the shader-pack aiming FPS decay that accumulates since the",
                        "first ADS and resets on world rejoin lives in the scope pipeline's retained GPU state.",
                        "See docs/SCOPE_PIP_FPS_DECAY_INVESTIGATION_2026_08_29.md.")
                .define("ScopePipReleaseIdlePipeline", false);
        SCOPE_PIP_IDLE_RELEASE_DELAY_FRAMES = builder
                .comment("[EXPERIMENT] Consecutive idle frames before the idle scope pipeline is released",
                        "(default 120 ~ 2s at 60fps; keeps aim transitions from thrashing the pipeline).")
                .defineInRange("ScopePipIdleReleaseDelayFrames", 120, 1, Integer.MAX_VALUE);
        SCOPE_PIP_DEBUG_GPU_MEM = builder
                .comment("[DEBUG] Every 600 frames log the scope/main Iris pipelines' retained GPU texture",
                        "bytes and the lifetime scope pass count. Quantifies whether the decay accumulates",
                        "on the GPU side (the CPU-side structure probes on the sister branch all came back flat).",
                        "If your MC has no GpuTexture#getMemorySize the byte fields log -1; also watch F3 VRAM.")
                .define("ScopePipDebugGpuMem", false);

        builder.comment("Max time the damage counter will reset");
        DAMAGE_COUNTER_RESET_TIME = builder.defineInRange("DamageCounterResetTime", 2000, 10, Integer.MAX_VALUE);

        builder.comment("Disable the fov effect from the movement speed attribute while holding a gun");
        DISABLE_MOVEMENT_ATTRIBUTE_FOV = builder.define("DisableMovementAttributeFov", true);

        builder.comment("Enable the display of the TACZ ID in the tooltip when Advanced Tooltip is enabled");
        ENABLE_TACZ_ID_IN_TOOLTIP = builder.define("EnableTaczIdInTooltip", true);

        builder.comment("Enable translucent while render block entity or not. Enable this option will result in ADDITIONAL PERFORMANCE OVERHEAD.");
        BLOCK_ENTITY_TRANSLUCENT = builder.define("EnableBlockEntityTranslucent", false);

        builder.pop();
    }
}
