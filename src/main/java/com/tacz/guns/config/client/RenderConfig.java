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
    public static ForgeConfigSpec.ConfigValue<String> SHELL_EJECTION_DEBUG_GUN;
    public static ForgeConfigSpec.IntValue SHELL_EJECTION_DEBUG_INTERVAL_MS;
    public static ForgeConfigSpec.BooleanValue RECOIL_DEBUG;
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

        RECOIL_DEBUG = builder
                .comment("[DEBUG] Log camera recoil diagnostics: per-shot recoil spline envelopes and per-frame",
                        "pitch/yaw deltas applied to the player rotation with facing + iris state, plus the level/item",
                        "camera-animation quaternions. Used to trace direction-bias of recoil feedback vs facing. Default off.")
                .define("RecoilDebug", false);

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
