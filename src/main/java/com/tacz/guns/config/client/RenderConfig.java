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
    /** 【调试】把瞄具目镜掩码贴图画到屏幕左上角，用于排查离屏渲染链路。默认关闭。 */
    public static ForgeConfigSpec.BooleanValue SCOPE_MASK_DEBUG;
    public static ForgeConfigSpec.BooleanValue SCOPE_MASK_HULL_FILL;
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
    public static ForgeConfigSpec.BooleanValue DISABLE_INTERACT_HUD_TEXT;
    public static ForgeConfigSpec.BooleanValue AUTO_SELECT_GUN_SMITH_TABLE_FILTER;
    public static ForgeConfigSpec.IntValue DAMAGE_COUNTER_RESET_TIME;
    public static ForgeConfigSpec.BooleanValue DISABLE_MOVEMENT_ATTRIBUTE_FOV;
    public static ForgeConfigSpec.BooleanValue ENABLE_TACZ_ID_IN_TOOLTIP;
    public static ForgeConfigSpec.BooleanValue BLOCK_ENTITY_TRANSLUCENT;
    /** 第 18 轮：PIP 镜内渲染 P1 验证开关，默认关闭。 */

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

        builder.comment("Disable the interact hud text in center of the screen");
        DISABLE_INTERACT_HUD_TEXT = builder.define("DisableInteractHudText", false);

        builder.comment("Whether or not to automatically select the gun smith table's held item filter when opening it with a gun, attachment or ammo in main hand");
        AUTO_SELECT_GUN_SMITH_TABLE_FILTER = builder.define("AutoSelectGunSmithTableFilter", true);

        builder.comment("[DEBUG] Enable the scope picture-in-picture (PIP) stage-1 verification. "
                + "Only creates an off-screen render target and verifies output redirection works; "
                + "does not yet render the world inside the scope. Default off.");

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
