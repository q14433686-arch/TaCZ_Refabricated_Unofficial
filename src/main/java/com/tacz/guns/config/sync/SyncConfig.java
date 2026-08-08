package com.tacz.guns.config.sync;

import com.google.common.collect.Lists;
import com.tacz.guns.industry.IndustryProfile;
import com.tacz.guns.industry.maintenance.IndustryMaintenanceScope;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class SyncConfig {
    // 交互键的判断是在客户端执行的，但是需要服务端来控制
    public static ForgeConfigSpec.ConfigValue<List<String>> INTERACT_KEY_WHITELIST_BLOCKS;
    public static ForgeConfigSpec.ConfigValue<List<String>> INTERACT_KEY_WHITELIST_ENTITIES;
    public static ForgeConfigSpec.ConfigValue<List<String>> INTERACT_KEY_BLACKLIST_BLOCKS;
    public static ForgeConfigSpec.ConfigValue<List<String>> INTERACT_KEY_BLACKLIST_ENTITIES;
    public static ForgeConfigSpec.BooleanValue ENABLE_TABLE_FILTER;
    public static ForgeConfigSpec.BooleanValue SERVER_SHOOT_NETWORK_V;
    public static ForgeConfigSpec.BooleanValue SERVER_SHOOT_COOLDOWN_V;

    /** Server-selected industrial ruleset. Defaults to the Create Fly path. */
    public static ForgeConfigSpec.EnumValue<IndustryProfile> INDUSTRY_PROFILE;
    /** Enables physical external and internal feed ownership for gun-feed declarations in the active industrial profile. */
    public static ForgeConfigSpec.BooleanValue PHYSICAL_MAGAZINES;
    /** Scan uncurated gun-pack table recipes at reload and synthesize safe industrial fallback replacements. */
    public static ForgeConfigSpec.BooleanValue AUTO_DISCOVER_INDUSTRY_REPLACEMENTS;
    /** Safe default limits phase-A condition/fouling accounting to real industrial-origin gun stacks. */
    public static ForgeConfigSpec.EnumValue<IndustryMaintenanceScope> INDUSTRY_MAINTENANCE_SCOPE;
    /** C.3 global gate/scales layered on top of each maintenance profile's real native-heat stress. */
    public static ForgeConfigSpec.BooleanValue INDUSTRY_HEAT_STRESS_ENABLED;
    public static ForgeConfigSpec.DoubleValue INDUSTRY_HEAT_WEAR_SCALE;
    public static ForgeConfigSpec.DoubleValue INDUSTRY_HEAT_FOULING_SCALE;
    /** Per-physical-gun proficiency becomes real handling only when this server policy permits it. */
    public static ForgeConfigSpec.BooleanValue GUN_EXPERIENCE_HANDLING_ENABLED;
    public static ForgeConfigSpec.DoubleValue GUN_EXPERIENCE_AIM_TIME_REDUCTION;
    public static ForgeConfigSpec.DoubleValue GUN_EXPERIENCE_INACCURACY_REDUCTION;
    public static ForgeConfigSpec.DoubleValue GUN_EXPERIENCE_RECOIL_REDUCTION;

    // 三个全局系数，用于客户端枪械文本提示，需要同步
    public static ForgeConfigSpec.DoubleValue DAMAGE_BASE_MULTIPLIER;
    public static ForgeConfigSpec.DoubleValue ARMOR_IGNORE_BASE_MULTIPLIER;
    public static ForgeConfigSpec.DoubleValue HEAD_SHOT_BASE_MULTIPLIER;
    public static ForgeConfigSpec.DoubleValue WEIGHT_SPEED_MULTIPLIER;

    // 需要同步到客户端，方便客户端 debug 显示碰撞箱
    public static ForgeConfigSpec.ConfigValue<List<String>> HEAD_SHOT_AABB;
    // 子弹盒存储上限需要客户端显示支持
    public static ForgeConfigSpec.IntValue AMMO_BOX_STACK_SIZE;
    // 客户端需要下载的枪械包
    public static ForgeConfigSpec.ConfigValue<List<List<String>>> CLIENT_GUN_PACK_DOWNLOAD_URLS;
    // 禁用趴下战术动作
    public static ForgeConfigSpec.BooleanValue ENABLE_CRAWL;

    public static void init(ForgeConfigSpec.Builder builder) {
        interactKey(builder);
        baseMultiplier(builder);
        misc(builder);
    }

    public static void interactKey(ForgeConfigSpec.Builder builder) {
        builder.push("interact_key");

        builder.comment("These whitelist blocks can be interacted with when the interact key is pressed");
        INTERACT_KEY_WHITELIST_BLOCKS = builder.define("InteractKeyWhitelistBlocks", Lists.newArrayList());

        builder.comment("These whitelist entities can be interacted with when the interact key is pressed");
        INTERACT_KEY_WHITELIST_ENTITIES = builder.define("InteractKeyWhitelistEntities", Lists.newArrayList());

        builder.comment("These blacklist blocks can be interacted with when the interact key is pressed");
        INTERACT_KEY_BLACKLIST_BLOCKS = builder.define("InteractKeyBlacklistBlocks", Lists.newArrayList());

        builder.comment("These blacklist entities can be interacted with when the interact key is pressed");
        INTERACT_KEY_BLACKLIST_ENTITIES = builder.define("InteractKeyBlacklistEntities", Lists.newArrayList());

        builder.pop();
    }

    private static void baseMultiplier(ForgeConfigSpec.Builder builder) {
        builder.push("base_multiplier");

        builder.comment("All base damage number is multiplied by this factor");
        DAMAGE_BASE_MULTIPLIER = builder.defineInRange("DamageBaseMultiplier", 1, 0, Double.MAX_VALUE);

        builder.comment("All armor ignore damage number is multiplied by this factor");
        ARMOR_IGNORE_BASE_MULTIPLIER = builder.defineInRange("ArmorIgnoreBaseMultiplier", 1, 0, Double.MAX_VALUE);

        builder.comment("All head shot damage number is multiplied by this factor");
        HEAD_SHOT_BASE_MULTIPLIER = builder.defineInRange("HeadShotBaseMultiplier", 1, 0, Double.MAX_VALUE);

        builder.comment("The movement speed will decrease per kg of weight. 0.015 means 1.5% speed decrease per kg. Set a negative value to disable this feature");
        WEIGHT_SPEED_MULTIPLIER = builder.defineInRange("WeightSpeedMultiplier", 0.015, -1, Double.MAX_VALUE);

        builder.pop();
    }

    private static void misc(ForgeConfigSpec.Builder builder) {
        builder.push("misc");

        builder.comment("The entity's head hitbox during the headshot");
        builder.comment("Format: touhou_little_maid:maid [-0.5, 1.0, -0.5, 0.5, 1.5, 0.5]");
        HEAD_SHOT_AABB = builder.define("HeadShotAABB", Lists.newArrayList());

        builder.comment("The maximum stack size of ammo that the ammo box can hold");
        AMMO_BOX_STACK_SIZE = builder.defineInRange("AmmoBoxStackSize", 3, 1, Integer.MAX_VALUE);

        builder.comment("Deprecated. Use vanilla server resource pack");
        CLIENT_GUN_PACK_DOWNLOAD_URLS = builder.define("ClientGunPackDownloadUrls", Lists.newArrayList());

        builder.comment("Whether or not players are allowed to use the crawl feature");
        ENABLE_CRAWL = builder.define("EnableCrawl", true);

        builder.comment("Enable the recipe limit of default gunsmith table or not");
        ENABLE_TABLE_FILTER = builder.define("EnableDefaultGunSmithTableFilter", true);

        builder.comment("Manufacturing ruleset. CREATE_FLY requires Create Fly (mod id 'create') on server and clients; LEGACY preserves original TACZ recipes.");
        INDUSTRY_PROFILE = builder.defineEnum("IndustryProfile", IndustryProfile.CREATE_FLY);

        builder.comment("Use real external carriers and internal-feed ownership for guns that declare an industry/gun_feed definition. Requires an active CREATE_FLY profile.");
        PHYSICAL_MAGAZINES = builder.define("PhysicalMagazines", true);

        builder.comment("Automatically scan uncurated gun-pack table recipes and add an in-game industrial fallback material gate. Curated platform declarations always take priority.");
        AUTO_DISCOVER_INDUSTRY_REPLACEMENTS = builder.define("AutoDiscoverIndustryReplacements", true);

        builder.comment("Industrial maintenance eligibility. INDUSTRIAL_ASSEMBLY safely affects only guns with real industrial provenance; ALL_GUNS opts legacy guns in but migrates them full and clean.");
        INDUSTRY_MAINTENANCE_SCOPE = builder.defineEnum("IndustryMaintenanceScope", IndustryMaintenanceScope.INDUSTRIAL_ASSEMBLY);

        builder.comment("Use native GunHeatData/HeatAmount as an additional C.3 maintenance exposure. Per-gun maintenance profiles still set their own maximum heat stress.");
        INDUSTRY_HEAT_STRESS_ENABLED = builder.define("IndustryHeatStressEnabled", true);
        builder.comment("Scale only the extra heat-derived structural wear above 1.0. 0 disables the extra wear without disabling normal Condition accounting.");
        INDUSTRY_HEAT_WEAR_SCALE = builder.defineInRange("IndustryHeatWearScale", 1.0, 0.0, 16.0);
        builder.comment("Scale only the extra heat-derived Fouling above 1.0. 0 disables the extra fouling without disabling normal Fouling accounting.");
        INDUSTRY_HEAT_FOULING_SCALE = builder.defineInRange("IndustryHeatFoulingScale", 1.0, 0.0, 16.0);

        builder.comment("Enable real per-physical-gun proficiency handling bonuses. It never adds direct damage, armor penetration, or maintenance/fault bypasses.");
        GUN_EXPERIENCE_HANDLING_ENABLED = builder.define("GunExperienceHandlingEnabled", true);
        builder.comment("Maximum ADS-time reduction at gun proficiency level 10. 0 disables only the ADS bonus.");
        GUN_EXPERIENCE_AIM_TIME_REDUCTION = builder.defineInRange("GunExperienceMaxAimTimeReduction", 0.15, 0.0, 0.75);
        builder.comment("Maximum real server projectile-inaccuracy reduction at gun proficiency level 10. 0 disables only the accuracy bonus.");
        GUN_EXPERIENCE_INACCURACY_REDUCTION = builder.defineInRange("GunExperienceMaxInaccuracyReduction", 0.20, 0.0, 0.75);
        builder.comment("Maximum client recoil-camera reduction at gun proficiency level 10. 0 disables only the recoil bonus.");
        GUN_EXPERIENCE_RECOIL_REDUCTION = builder.defineInRange("GunExperienceMaxRecoilReduction", 0.15, 0.0, 0.75);

        builder.comment("[Debug Option] Do server-side network check while shooting or not");
        SERVER_SHOOT_NETWORK_V = builder.define("ServerShootNetworkCheck", true);

        builder.comment("[Debug Option] Do server-side shoot cooldown check or not." +
                " WARNING: Close this will disable the shoot cooldown check in server-side at all," +
                " which may lead to potential for cheating." +
                " Only consider to close this when you can't shoot at all sometimes.");
        SERVER_SHOOT_COOLDOWN_V = builder.define("ServerShootCooldownCheck", true);
        builder.pop();
    }
}
