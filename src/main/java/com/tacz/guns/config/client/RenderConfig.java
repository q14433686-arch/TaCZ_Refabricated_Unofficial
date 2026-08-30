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
        SCOPE_PIP_WORLD_ZOOM_SHARE = builder.comment(
                        "How much of the scope zoom is applied to the world (0.0 = pure PIP, 1.0 = old "
                                + "whole-screen zoom). World = Z^share and lens = Z^(1-share).")
                .defineInRange("ScopePipWorldZoomShare", 0.0, 0.0, 1.0);
        SCOPE_PIP_SHARPNESS = builder.comment(
                        "Lens sharpening strength (0 = off). Weighted by zoom: negligible at 1x, full at 6x+.")
                .defineInRange("ScopePipSharpness", 0.0, 0.0, 1.0);
        SCOPE_PIP_DEBUG_PAINT_LENS = builder.comment(
                        "Diagnostic: paint the PIP composite coverage solid magenta (lens only when the "
                                + "mask is correct, the whole screen when the composite leaks).")
                .define("ScopePipDebugPaintLens", false);

        builder.pop();
    }
}
