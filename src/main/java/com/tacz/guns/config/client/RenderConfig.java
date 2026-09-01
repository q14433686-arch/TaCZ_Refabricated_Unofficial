package com.tacz.guns.config.client;

import com.tacz.guns.client.renderer.crosshair.CrosshairType;
import net.minecraftforge.common.ForgeConfigSpec;

public class RenderConfig {
    public static ForgeConfigSpec.BooleanValue ENABLE_LASER_FADE_OUT;
    public static ForgeConfigSpec.IntValue GUN_LOD_RENDER_DISTANCE;
    public static ForgeConfigSpec.IntValue BULLET_HOLE_PARTICLE_LIFE;
    public static ForgeConfigSpec.DoubleValue BULLET_HOLE_PARTICLE_FADE_THRESHOLD;
    public static ForgeConfigSpec.EnumValue<CrosshairType> CROSSHAIR_TYPE;
    public static ForgeConfigSpec.DoubleValue HIT_MARKET_START_POSITION;
    public static ForgeConfigSpec.BooleanValue HEAD_SHOT_DEBUG_HITBOX;
    /** 瞄准镜镜内裁剪（目镜掩码）总开关。默认<b>开启</b>。 */
    public static ForgeConfigSpec.BooleanValue SCOPE_MASK_ENABLE;
    public static ForgeConfigSpec.BooleanValue GUN_HUD_ENABLE;
    public static ForgeConfigSpec.BooleanValue KILL_AMOUNT_ENABLE;
    public static ForgeConfigSpec.DoubleValue KILL_AMOUNT_DURATION_SECOND;
    public static ForgeConfigSpec.IntValue TARGET_RENDER_DISTANCE;
    public static ForgeConfigSpec.BooleanValue FIRST_PERSON_BULLET_TRACER_ENABLE;
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
     * 再由目镜孔径把这张图贴进镜片 —— 默认（{@code WorldZoomShare=0}）镜外保持 1×、
     * 只有镜片里是放大的；调高 share 会让镜外也承担部分倍率。</p>
     *
     * <p>代价是镜内画面来自主画面中心裁切区的放大，高倍镜（6× 以上）会明显变软。
     * 因为这属于观感取舍而非性能取舍，默认关闭、由玩家自己选。</p>
     */
    public static ForgeConfigSpec.BooleanValue SCOPE_PIP_ENABLE;
    /** 开镜进度低于该值时不做 PIP（此时孔径几乎闭合，拷贝纯属浪费）。 */
    public static ForgeConfigSpec.DoubleValue SCOPE_PIP_MIN_AIMING_PROGRESS;
    /**
     * 瞄具倍率低于该值时不做 PIP，改走原来的整屏变焦。
     *
     * <p>低倍镜（2×/3×）的整屏变焦本来就自然，PIP 却照付全屏拷贝成本并让镜内变软；
     * 高倍镜才是 PIP 的目标场景。组合镜按<b>当前档位</b>判定，切到低倍档自动回整屏变焦，
     * 切回高倍档自动回 PIP。</p>
     */
    public static ForgeConfigSpec.DoubleValue SCOPE_PIP_MIN_MAGNIFICATION;
    /**
     * 瞄具倍率里有多大一份由<b>世界</b>承担（0 = 全归镜内，纯 PIP；1 = 全归世界，等于关掉 PIP）。
     *
     * <p>镜内画面是主画面中心区按倍率放大来的，放大倍数<b>就是</b>瞄具倍率。
     * 倍率是相乘的，按 {@code 世界 = Z^share、镜内 = Z^(1-share)} 拆分后总倍率恒为 Z，
     * 而镜内拿到的真实像素多 {@code Z^share} 倍。这是唯一能真正增加镜内分辨率的旋钮
     * （锐化只提高主观锐度）。</p>
     *
     * <p>默认 0 = 镜外全程 1×、镜内承受全部放大；调高会牺牲「镜外纯净」换取镜内清晰。</p>
     */
    public static ForgeConfigSpec.DoubleValue SCOPE_PIP_WORLD_ZOOM_SHARE;
    /**
     * 镜内锐化强度（0 = 关）。
     *
     * <p>镜内画面是主画面中心区按倍率放大来的，必然变软。锐化不能凭空造出细节，
     * 但能显著挽回主观锐度；实际强度按倍率线性加权（1× 不锐化，6× 及以上取满），
     * 所以低倍镜不会被过度处理。</p>
     */
    public static ForgeConfigSpec.DoubleValue SCOPE_PIP_SHARPNESS;
    /**
     * 是否允许在 Iris 光影包开启时运行 PIP。
     *
     * <p>无光影时我们在手部绘制<b>之前</b>抓一份干净的世界色；有光影时这份抓取点不再成立
     * （世界画进 Iris 自己的 gbuffer/composite 链，主 target 此时还不是成品）。适配原则是
     * 改在 {@code IrisRenderingPipeline#finalizeLevelRendering()} 之后抓<b>最终成品帧</b>：
     * 镜身已经在孔径内被裁剪，孔径区域本来就是干净的 1× 世界，直接在成品帧上做屏幕空间
     * 重投影即可，镜内外颜色必然一致。</p>
     *
     * <p>代价：成品帧里镜内可用的真实像素上限仍是「屏幕 ÷ 倍率」，且要在 Iris 所有
     * composite/final pass 之后再开一张全屏 pass，高倍镜会更软、开销多一小截。所以默认
     * <b>关闭</b>（保持旧的整屏变焦），由玩家按需开启。</p>
     */
    public static ForgeConfigSpec.BooleanValue SCOPE_PIP_ALLOW_SHADER_PACKS;
    /**
     * 【实验】用窄 FOV 把世界<b>二次渲染</b>一遍作为镜内画面，取代屏幕空间重投影。
     *
     * <p>重投影路径的镜内分辨率被锁死为「屏幕分辨率 ÷ 倍率」，高倍镜必然变软；
     * 二次渲染用窄 FOV 真画一遍，镜内是<b>原生分辨率</b>，代价是每帧多跑一遍世界渲染。
     * 与 26.2 的 {@code ScopePipRerender} 同名同义。</p>
     *
     * <p><b>注意</b>：本分支当前只实现无光影（vanilla）路径；Iris 光影下仍走现有
     * 屏幕空间成品帧合成，此开关暂不生效（见配置 tooltip）。默认关闭。</p>
     */
    public static ForgeConfigSpec.BooleanValue SCOPE_PIP_RERENDER;
    /**
     * 二次渲染模式下，镜内那一遍世界每 N 帧才真正渲染一次，其余帧复用上一帧的镜内画面。
     *
     * <p>二次渲染的代价是一整个额外世界渲染；「每两帧才跑第二遍」把这份开销直接减半
     * （N=4 则减到 1/4）。代价：转动视角时镜内<b>内容</b>滞后 N-1 帧（N=2 时一帧，
     * 接近难以察觉）；镜外主画面永远满帧率，掩码/合成/准星层次逐帧照常。
     * 默认 1 = 每帧都渲染（即关闭隔帧复用）。与 26.2 的
     * {@code ScopePipRerenderInterval} 同名同义同默认；仅当
     * {@link #SCOPE_PIP_RERENDER} 开启时生效。窗口缩放/格式变化会使复用失效
     * （离屏画布代数守卫，见 {@code ScopePipRenderState} 的 generation）。</p>
     */
    public static ForgeConfigSpec.IntValue SCOPE_PIP_RERENDER_INTERVAL;
    /**
     * 光影下二次渲染时，是否给镜内那一遍配<b>独立的 Iris 管线</b>（时域隔离）。
     *
     * <p>Iris 的所有「上一帧」值（previous 矩阵、previous 相机位置）都是<b>读一次推进一次</b>，
     * 一帧跑两遍世界渲染会让主画面拿到镜内那遍的矩阵做时域重投影——实测三症状同源：
     * 整屏拖影、体积云噪点闪烁、开镜时镜外整屏发糙。隔离=借 Iris 按维度缓存管线的机制
     * 给镜内一遍单独一套 colortex/程序/previous 族（26.2 {@code ScopePipIsolatePipeline}
     * 同名同默认）。代价：多一套光影缓冲（高分辨率下数百 MB 显存）+ 首次开镜一次性着色器编译。</p>
     */
    public static ForgeConfigSpec.BooleanValue SCOPE_PIP_ISOLATE_PIPELINE;
    /**
     * 镜内那一遍的阴影贴图分辨率比例（1.0 = 与光影包自身一致）。开销按面积走：
     * 0.5 = 镜内那遍的阴影只花 1/4 的代价，画质损失只落在镜内。
     * 仅当 {@link #SCOPE_PIP_RERENDER}+{@link #SCOPE_PIP_ISOLATE_PIPELINE}+光影齐备时生效；
     * 阴影分辨率在瞄具管线构造时一次定死，改动后由预热逻辑销毁重建热生效。
     */
    public static ForgeConfigSpec.DoubleValue SCOPE_PIP_SHADOW_SCALE;
    /**
     * 【实验】不开镜连续 {@link #SCOPE_PIP_IDLE_RELEASE_DELAY_FRAMES} 帧后销毁瞄具专用
     * Iris 管线释放其全部 GPU 资源，下次开镜由预热重建（带一次着色器编译成本）。
     * 26.2 的 FPS 衰减调查线：光影下「开镜帧率自首次 ADS 起持续衰减、重进存档重置」
     * 强烈指向瞄具管线保留 GPU 状态里的逐 pass 累积，空闲销毁即清零。
     */
    public static ForgeConfigSpec.BooleanValue SCOPE_PIP_RELEASE_IDLE_PIPELINE;
    /** 空闲释放前需连续空闲的帧数（默认 120 ≈ 60fps 下 2 秒，避免进出镜抖动式重建）。 */
    public static ForgeConfigSpec.IntValue SCOPE_PIP_IDLE_RELEASE_DELAY_FRAMES;
    /**
     * 二次渲染模式下，镜内那遍的渲染分辨率（1.0 = 原生分辨率）。
     *
     * <p>调低可显著减少第二遍世界渲染的 GPU 开销（0.5 = 25% 像素），代价是镜内更软。
     * 仅当 {@link #SCOPE_PIP_RERENDER} 开启时生效。与 26.2 的
     * {@code ScopePipResolutionScale} 同名同义。</p>
     */
    public static ForgeConfigSpec.DoubleValue SCOPE_PIP_RESOLUTION_SCALE;
    /**
     * 【诊断】抓取照常进行，但<b>不做合成</b>。
     *
     * <p>用来区分「放大画面溢出到镜外」的两种成因：什么都不画仍溢出 → 抓取/离屏路径漏到主画面；
     * 镜片为空 → 溢出来自合成/掩码。默认关闭。</p>
     */
    public static ForgeConfigSpec.BooleanValue SCOPE_PIP_DEBUG_NO_COMPOSITE;
    /**
     * 【诊断】把 PIP 合成实际覆盖到的区域涂成纯品红。
     *
     * <p>整屏变品红 = 合成没被目镜孔径约束住；只有镜片变品红 = 合成范围正确，
     * 溢出/缺图来自别处。默认关闭。</p>
     */
    public static ForgeConfigSpec.BooleanValue SCOPE_PIP_DEBUG_PAINT_LENS;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("render");

        builder.comment("Whether or not apply fadeout effect on the laser beam. Close this may improve laser performance under some shaders.");
        ENABLE_LASER_FADE_OUT = builder.define("EnableLaserFadeOut", true);

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
        SCOPE_MASK_ENABLE = builder
                .comment("Whether to open the first-person scope body with the ocular depth aperture.")
                .define("ScopeMaskEnable", true);

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

        builder.comment("Disable the interact hud text in center of the screen");
        DISABLE_INTERACT_HUD_TEXT = builder.define("DisableInteractHudText", false);

        builder.comment("Whether or not to automatically select the gun smith table's held item filter when opening it with a gun, attachment or ammo in main hand");
        AUTO_SELECT_GUN_SMITH_TABLE_FILTER = builder.define("AutoSelectGunSmithTableFilter", true);

        builder.comment("Max time the damage counter will reset");
        DAMAGE_COUNTER_RESET_TIME = builder.defineInRange("DamageCounterResetTime", 2000, 10, Integer.MAX_VALUE);

        builder.comment("Disable the fov effect from the movement speed attribute while holding a gun");
        DISABLE_MOVEMENT_ATTRIBUTE_FOV = builder.define("DisableMovementAttributeFov", true);

        builder.comment("Enable the display of the TACZ ID in the tooltip when Advanced Tooltip is enabled");
        ENABLE_TACZ_ID_IN_TOOLTIP = builder.define("EnableTaczIdInTooltip", true);

        builder.comment("Enable translucent while render block entity or not. Enable this option will result in ADDITIONAL PERFORMANCE OVERHEAD.");
        BLOCK_ENTITY_TRANSLUCENT = builder.define("EnableBlockEntityTranslucent", false);

        // ===== 镜内画中画（Scope PIP）=====
        // 这些键名对齐 26.2(main) 的既有命名，避免旧配置文件/模组菜单来回改名丢值。
        // 功能默认全部关闭或保持旧行为，避免玩家升级后看到画面/手感突变。
        SCOPE_PIP_ENABLE = builder.comment(
                        "Enable the first-person scope picture-in-picture (PIP). When on, the world outside "
                                + "the lens keeps 1x by default and only the lens shows the magnified scene; "
                                + "ScopePipWorldZoomShare can move part of the zoom back to the world.")
                .define("ScopePipEnable", false);
        SCOPE_PIP_MIN_AIMING_PROGRESS = builder.comment(
                        "Do not run PIP below this aiming progress; the aperture is nearly closed anyway.")
                .defineInRange("ScopePipMinAimingProgress", 0.05, 0.0, 1.0);
        SCOPE_PIP_MIN_MAGNIFICATION = builder.comment(
                        "Scopes below this magnification keep the classic whole-screen zoom because "
                                + "PIP's softness/cost is not worth it at low power.")
                .defineInRange("ScopePipMinMagnification", 4.0, 1.0, 100.0);
        SCOPE_PIP_WORLD_ZOOM_SHARE = builder.comment(
                        "How much of the scope zoom is applied to the world (0.0 = pure PIP, 1.0 = old "
                                + "whole-screen zoom). World = Z^share and lens = Z^(1-share).")
                .defineInRange("ScopePipWorldZoomShare", 0.0, 0.0, 1.0);
        SCOPE_PIP_SHARPNESS = builder.comment(
                        "Lens sharpening strength (0 = off). Weighted by zoom: negligible at 1x, full at 6x+.")
                .defineInRange("ScopePipSharpness", 0.5, 0.0, 1.0);
        SCOPE_PIP_ALLOW_SHADER_PACKS = builder.comment(
                        "Allow the scope picture-in-picture to run while an Iris shader pack is active. "
                                + "Off by default because the capture point moves to after Iris' final composite, "
                                + "so the lens is a screen-space reprojection of the finished frame (slightly softer "
                                + "under high magnification). Turn it on to test with your pack.")
                .define("ScopePipAllowShaderPacks", false);
        SCOPE_PIP_RERENDER = builder.comment(
                        "Draw the scope image by rendering the world a SECOND time with a narrow FOV, "
                                + "instead of reprojecting the already-rendered frame. The lens then has native "
                                + "resolution (the reprojection path is capped at screen resolution / zoom). "
                                + "Costs a full extra world render every frame. Experimental; default off. "
                                + "Works without a shader pack, and under an Iris pack once "
                                + "ScopePipAllowShaderPacks is on (the scope pass then runs on its own "
                                + "Iris pipeline when ScopePipIsolatePipeline is on).")
                .define("ScopePipRerender", false);
        SCOPE_PIP_RERENDER_INTERVAL = builder.comment(
                        "Rerender mode: truly render the narrow-FOV scope world only every N frames; ",
                        "other frames reuse the previous lens image. Halves (N=2) or quarters (N=4) the ",
                        "cost of the second world render; the lens CONTENT lags N-1 frames while the ",
                        "main view stays full-rate. Default 1 = render every frame (no reuse).")
                .defineInRange("ScopePipRerenderInterval", 1, 1, 4);
        SCOPE_PIP_ISOLATE_PIPELINE = builder.comment(
                        "Rerender mode under a shader pack: give the scope pass its own Iris pipeline, ",
                        "so its temporal state cannot corrupt the main view. Iris advances every 'previous ",
                        "frame' value when it is READ, so rendering the world twice would otherwise leave ",
                        "the main view reprojecting against the scope pass's matrices (ghosting, ",
                        "shimmering clouds, grainy screen outside the scope while aiming).",
                        "",
                        "Costs an extra set of shader buffers (up to a few hundred MB of VRAM at high ",
                        "resolutions) and a one-time shader compile the first time you aim. Turn this ",
                        "off if VRAM is tight, and the artifacts above come back.")
                .define("ScopePipIsolatePipeline", true);
        SCOPE_PIP_SHADOW_SCALE = builder.comment(
                        "Shadow map resolution for the scope pass, as a fraction of the pack's own.",
                        "Only used with ScopePipRerender + ScopePipIsolatePipeline + a shader pack.",
                        "",
                        "Iris renders shadows once per world render, so rendering the world twice",
                        "doubles that cost; it scales with area, so 0.5 cuts the scope pass' shadow",
                        "work to about 25%. Only the lens is affected. Takes effect when the scope",
                        "pipeline is (re)built; the pipeline is rebuilt automatically on change.")
                .defineInRange("ScopePipShadowScale", 0.5d, 0.25d, 1.0d);
        SCOPE_PIP_RELEASE_IDLE_PIPELINE = builder.comment(
                        "[EXPERIMENT] Destroy the scope pass' isolated Iris pipeline while not aiming, to",
                        "release its full GPU resources; it is rebuilt (with a shaderpack compile cost) on",
                        "the next aim. Tests whether the shader-pack aiming FPS decay that accumulates",
                        "since the first ADS and resets on world rejoin lives in the scope pipeline's",
                        "retained GPU state.")
                .define("ScopePipReleaseIdlePipeline", false);
        SCOPE_PIP_IDLE_RELEASE_DELAY_FRAMES = builder.comment(
                        "[EXPERIMENT] Consecutive idle frames before the idle scope pipeline is released",
                        "(default 120 ~ 2s at 60fps; keeps aim transitions from thrashing the pipeline).")
                .defineInRange("ScopePipIdleReleaseDelayFrames", 120, 1, Integer.MAX_VALUE);
        SCOPE_PIP_RESOLUTION_SCALE = builder.comment(
                        "Render resolution scale for the scope pass in rerender mode (1.0 = native). "
                                + "Lower values reduce the GPU cost of the second world render at the price "
                                + "of a softer lens. Only used when ScopePipRerender is on.")
                .defineInRange("ScopePipResolutionScale", 0.75d, 0.25d, 1.0d);
        SCOPE_PIP_DEBUG_NO_COMPOSITE = builder.comment(
                        "Diagnostic: still capture, but skip the composite. If the magnified image still "
                                + "overflows, the leak is in the capture/offscreen path, not the composite.")
                .define("ScopePipDebugNoComposite", false);
        SCOPE_PIP_DEBUG_PAINT_LENS = builder.comment(
                        "Diagnostic: paint the PIP composite coverage solid magenta (lens only when the "
                                + "mask is correct, the whole screen when the composite leaks).")
                .define("ScopePipDebugPaintLens", false);

        builder.pop();
    }
}
