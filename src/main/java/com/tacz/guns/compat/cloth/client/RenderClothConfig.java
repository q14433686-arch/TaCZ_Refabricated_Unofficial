package com.tacz.guns.compat.cloth.client;

import cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig;
import com.tacz.guns.client.renderer.crosshair.CrosshairType;
import com.tacz.guns.client.tuning.TuningProfiles;
import com.tacz.guns.compat.cloth.widget.CrosshairDropdown;
import com.tacz.guns.config.client.RenderConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

public class RenderClothConfig {
    public static void init(ConfigBuilder root, ConfigEntryBuilder entryBuilder) {
        ConfigCategory render = root.getOrCreateCategory(Component.translatable("config.tacz.client.render"));

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.disable_third_person_offhand_and_hotbar_gun_render"), RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.disable_third_person_offhand_and_hotbar_gun_render.desc"))
                .setSaveConsumer(RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER::set).build());

        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.gun_hurt_bob_multiplier"), RenderConfig.GUN_HURT_BOB_MULTIPLIER.get())
                .setMin(0).setMax(1).setDefaultValue(0.1).setTooltip(Component.translatable("config.tacz.client.render.gun_hurt_bob_multiplier.desc"))
                .setSaveConsumer(RenderConfig.GUN_HURT_BOB_MULTIPLIER::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.muzzle_surface_light"), RenderConfig.MUZZLE_SURFACE_LIGHT.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.muzzle_surface_light.desc"))
                .setSaveConsumer(RenderConfig.MUZZLE_SURFACE_LIGHT::set).build());
        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.legacy_muzzle_light"), RenderConfig.LEGACY_MUZZLE_LIGHT.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.legacy_muzzle_light.desc"))
                .setSaveConsumer(RenderConfig.LEGACY_MUZZLE_LIGHT::set).build());
        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.explosion_surface_light"), RenderConfig.EXPLOSION_SURFACE_LIGHT.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.explosion_surface_light.desc"))
                .setSaveConsumer(RenderConfig.EXPLOSION_SURFACE_LIGHT::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.muzzle_smoke"), RenderConfig.RENDER_MUZZLE_SMOKE.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.muzzle_smoke.desc"))
                .setSaveConsumer(RenderConfig.RENDER_MUZZLE_SMOKE::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.ocular_inflate_enable"), RenderConfig.ENABLE_OCULAR_INFLATE.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.ocular_inflate_enable.desc"))
                .setSaveConsumer(RenderConfig.ENABLE_OCULAR_INFLATE::set).build());
        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.ocular_inflate"), RenderConfig.OCULAR_INFLATE.get())
                .setMin(0.0).setMax((double) Float.MAX_VALUE).setDefaultValue(0.0005)
                .setTooltip(Component.translatable("config.tacz.client.render.ocular_inflate.desc"))
                .setSaveConsumer(RenderConfig.OCULAR_INFLATE::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.laser_fadeout"), RenderConfig.ENABLE_LASER_FADE_OUT.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.laser_fadeout.desc"))
                .setSaveConsumer(RenderConfig.ENABLE_LASER_FADE_OUT::set).build());


        render.addEntry(entryBuilder.startIntField(Component.translatable("config.tacz.client.render.gun_lod_render_distance"), RenderConfig.GUN_LOD_RENDER_DISTANCE.get())
                .setMin(0).setMax(Integer.MAX_VALUE).setDefaultValue(0).setTooltip(Component.translatable("config.tacz.client.render.gun_lod_render_distance.desc"))
                .setSaveConsumer(RenderConfig.GUN_LOD_RENDER_DISTANCE::set).build());

        render.addEntry(entryBuilder.startIntField(Component.translatable("config.tacz.client.render.dropped_gun_sprite_distance"), RenderConfig.DROPPED_GUN_SPRITE_DISTANCE.get())
                .setMin(-1).setMax(Integer.MAX_VALUE).setDefaultValue(RenderConfig.DEFAULT_DROPPED_GUN_SPRITE_DISTANCE)
                .setTooltip(Component.translatable("config.tacz.client.render.dropped_gun_sprite_distance.desc"))
                .setSaveConsumer(RenderConfig.DROPPED_GUN_SPRITE_DISTANCE::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.hit_particle_enable"), RenderConfig.HIT_PARTICLE_ENABLE.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.hit_particle_enable.desc"))
                .setSaveConsumer(RenderConfig.HIT_PARTICLE_ENABLE::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.hit_extra_particle_enable"), RenderConfig.HIT_EXTRA_PARTICLE_ENABLE.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.hit_extra_particle_enable.desc"))
                .setSaveConsumer(RenderConfig.HIT_EXTRA_PARTICLE_ENABLE::set).build());

        render.addEntry(entryBuilder.startIntField(Component.translatable("config.tacz.client.render.hit_particle_max_distance"), RenderConfig.HIT_PARTICLE_MAX_DISTANCE.get())
                .setMin(0).setMax(Integer.MAX_VALUE).setDefaultValue(64).setTooltip(Component.translatable("config.tacz.client.render.hit_particle_max_distance.desc"))
                .setSaveConsumer(RenderConfig.HIT_PARTICLE_MAX_DISTANCE::set).build());

        render.addEntry(entryBuilder.startIntField(Component.translatable("config.tacz.client.render.bullet_hole_particle_life"), RenderConfig.BULLET_HOLE_PARTICLE_LIFE.get())
                .setMin(0).setMax(Integer.MAX_VALUE).setDefaultValue(400).setTooltip(Component.translatable("config.tacz.client.render.bullet_hole_particle_life.desc"))
                .setSaveConsumer(RenderConfig.BULLET_HOLE_PARTICLE_LIFE::set).build());

        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.bullet_hole_particle_fade_threshold"), RenderConfig.BULLET_HOLE_PARTICLE_FADE_THRESHOLD.get())
                .setMin(0).setMax(1).setDefaultValue(0.98).setTooltip(Component.translatable("config.tacz.client.render.bullet_hole_particle_fade_threshold.desc"))
                .setSaveConsumer(RenderConfig.BULLET_HOLE_PARTICLE_FADE_THRESHOLD::set).build());

        var tuningOwner = TuningProfiles.account();
        render.addEntry(entryBuilder.startDropdownMenu(Component.translatable("config.tacz.client.render.crosshair_type"),
                        CrosshairDropdown.of(TuningProfiles.current().base().get(RenderConfig.CROSSHAIR_TYPE)), CrosshairDropdown.of())
                .setSelections(Arrays.stream(CrosshairType.values()).sorted().sorted(Comparator.comparing(CrosshairType::name)).collect(Collectors.toCollection(LinkedHashSet::new)))
                .setDefaultValue(CrosshairType.DOT_1).setTooltip(Component.translatable("config.tacz.client.render.crosshair_type.desc"))
                .setSaveConsumer(value -> {
                    if (!tuningOwner.equals(TuningProfiles.account())) return;
                    var profile = TuningProfiles.current().copy();
                    if (value == profile.base().get(RenderConfig.CROSSHAIR_TYPE)) return;
                    profile.base().set(RenderConfig.CROSSHAIR_TYPE, value);
                    TuningProfiles.save(tuningOwner, profile);
                }).build());

        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.hit_market_start_position"), RenderConfig.HIT_MARKET_START_POSITION.get())
                .setMin(-1024).setMax(1024).setDefaultValue(4).setTooltip(Component.translatable("config.tacz.client.render.hit_market_start_position.desc"))
                .setSaveConsumer(RenderConfig.HIT_MARKET_START_POSITION::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.head_shot_debug_hitbox"), RenderConfig.HEAD_SHOT_DEBUG_HITBOX.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.head_shot_debug_hitbox.desc"))
                .setSaveConsumer(RenderConfig.HEAD_SHOT_DEBUG_HITBOX::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.gun_hud_enable"), RenderConfig.GUN_HUD_ENABLE.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.gun_hud_enable.desc"))
                .setSaveConsumer(RenderConfig.GUN_HUD_ENABLE::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.scope_mask_enable"), RenderConfig.SCOPE_MASK_ENABLE.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.scope_mask_enable.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_MASK_ENABLE::set).build());
        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.scope_lens_enable"), RenderConfig.EXPERIMENTAL_SCOPE_LENS.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.scope_lens_enable.desc"))
                .setSaveConsumer(RenderConfig.EXPERIMENTAL_SCOPE_LENS::set).build());
        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.scope_thermal_enable"), RenderConfig.SCOPE_THERMAL_ENABLE.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.scope_thermal_enable.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_THERMAL_ENABLE::set).build());
        render.addEntry(entryBuilder.startEnumSelector(Component.translatable("config.tacz.client.render.iris_scope_mask_injection"), RenderConfig.IrisScopeMaskInjection.class, RenderConfig.IRIS_SCOPE_MASK_INJECTION.get())
                .setDefaultValue(RenderConfig.IrisScopeMaskInjection.HAND_ONLY).setTooltip(Component.translatable("config.tacz.client.render.iris_scope_mask_injection.desc"))
                .setSaveConsumer(RenderConfig.IRIS_SCOPE_MASK_INJECTION::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.kill_amount_enable"), RenderConfig.KILL_AMOUNT_ENABLE.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.kill_amount_enable.desc"))
                .setSaveConsumer(RenderConfig.KILL_AMOUNT_ENABLE::set).build());

        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.kill_amount_duration_second"), RenderConfig.KILL_AMOUNT_DURATION_SECOND.get())
                .setMin(0).setMax(Double.MAX_VALUE).setDefaultValue(3).setTooltip(Component.translatable("config.tacz.client.render.kill_amount_duration_second.desc"))
                .setSaveConsumer(RenderConfig.KILL_AMOUNT_DURATION_SECOND::set).build());

        render.addEntry(entryBuilder.startIntField(Component.translatable("config.tacz.client.render.target_render_distance"), RenderConfig.TARGET_RENDER_DISTANCE.get())
                .setMin(0).setMax(Integer.MAX_VALUE).setDefaultValue(128).setTooltip(Component.translatable("config.tacz.client.render.target_render_distance.desc"))
                .setSaveConsumer(RenderConfig.TARGET_RENDER_DISTANCE::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.first_person_bullet_tracer_enable"), RenderConfig.FIRST_PERSON_BULLET_TRACER_ENABLE.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.first_person_bullet_tracer_enable.desc"))
                .setSaveConsumer(RenderConfig.FIRST_PERSON_BULLET_TRACER_ENABLE::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.disable_interact_hud_text"), RenderConfig.DISABLE_INTERACT_HUD_TEXT.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.disable_interact_hud_text.desc"))
                .setSaveConsumer(RenderConfig.DISABLE_INTERACT_HUD_TEXT::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.creative_pack_sidebar"), RenderConfig.RENDER_CREATIVE_PACK_SIDEBAR.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.creative_pack_sidebar.desc"))
                .setSaveConsumer(RenderConfig.RENDER_CREATIVE_PACK_SIDEBAR::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.auto_select_gun_smith_table_filter"), RenderConfig.AUTO_SELECT_GUN_SMITH_TABLE_FILTER.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.auto_select_gun_smith_table_filter.desc"))
                .setSaveConsumer(RenderConfig.AUTO_SELECT_GUN_SMITH_TABLE_FILTER::set).build());

        render.addEntry(entryBuilder.startIntField(Component.translatable("config.tacz.client.render.damage_counter_reset_time"), RenderConfig.DAMAGE_COUNTER_RESET_TIME.get())
                .setMin(10).setMax(Integer.MAX_VALUE).setDefaultValue(2000).setTooltip(Component.translatable("config.tacz.client.render.damage_counter_reset_time.desc"))
                .setSaveConsumer(RenderConfig.DAMAGE_COUNTER_RESET_TIME::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.disable_movement_fov"), RenderConfig.DISABLE_MOVEMENT_ATTRIBUTE_FOV.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.disable_movement_fov.desc"))
                .setSaveConsumer(RenderConfig.DISABLE_MOVEMENT_ATTRIBUTE_FOV::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.enable_tooltip_id"), RenderConfig.ENABLE_TACZ_ID_IN_TOOLTIP.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.enable_tooltip_id.desc"))
                .setSaveConsumer(RenderConfig.ENABLE_TACZ_ID_IN_TOOLTIP::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.enable_translucent"), RenderConfig.BLOCK_ENTITY_TRANSLUCENT.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.enable_translucent.desc"))
                .setSaveConsumer(RenderConfig.BLOCK_ENTITY_TRANSLUCENT::set).build());

        // ================= Scope PIP 画中画配置 =================
        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.scope_pip_enable"), RenderConfig.SCOPE_PIP_ENABLE.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.scope_pip_enable.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_ENABLE::set).build());

        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.scope_pip_min_magnification"), RenderConfig.SCOPE_PIP_MIN_MAGNIFICATION.get())
                .setMin(1.0).setMax(100.0).setDefaultValue(4.0).setTooltip(Component.translatable("config.tacz.client.render.scope_pip_min_magnification.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_MIN_MAGNIFICATION::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.scope_pip_rerender"), RenderConfig.SCOPE_PIP_RERENDER.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.scope_pip_rerender.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_RERENDER::set).build());

        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.scope_pip_resolution_scale"), RenderConfig.SCOPE_PIP_RESOLUTION_SCALE.get())
                .setMin(0.25).setMax(1.0).setDefaultValue(0.75).setTooltip(Component.translatable("config.tacz.client.render.scope_pip_resolution_scale.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_RESOLUTION_SCALE::set).build());

        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.scope_pip_shadow_scale"), RenderConfig.SCOPE_PIP_SHADOW_SCALE.get())
                .setMin(0.25).setMax(1.0).setDefaultValue(0.5).setTooltip(Component.translatable("config.tacz.client.render.scope_pip_shadow_scale.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_SHADOW_SCALE::set).build());

        render.addEntry(entryBuilder.startIntField(Component.translatable("config.tacz.client.render.scope_pip_rerender_interval"), RenderConfig.SCOPE_PIP_RERENDER_INTERVAL.get())
                .setMin(1).setMax(4).setDefaultValue(1).setTooltip(Component.translatable("config.tacz.client.render.scope_pip_rerender_interval.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_RERENDER_INTERVAL::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.scope_pip_allow_shader_packs"), RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.scope_pip_allow_shader_packs.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.scope_pip_isolate_pipeline"), RenderConfig.SCOPE_PIP_ISOLATE_PIPELINE.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.scope_pip_isolate_pipeline.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_ISOLATE_PIPELINE::set).build());

        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.scope_pip_world_zoom_share"), RenderConfig.SCOPE_PIP_WORLD_ZOOM_SHARE.get())
                .setMin(0.0).setMax(1.0).setDefaultValue(0.0).setTooltip(Component.translatable("config.tacz.client.render.scope_pip_world_zoom_share.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_WORLD_ZOOM_SHARE::set).build());

        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.scope_pip_sharpness"), RenderConfig.SCOPE_PIP_SHARPNESS.get())
                .setMin(0.0).setMax(1.0).setDefaultValue(0.5).setTooltip(Component.translatable("config.tacz.client.render.scope_pip_sharpness.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_SHARPNESS::set).build());

        // ================= Mesh Loader（poly_mesh）配置 =================
        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.mesh_gpu_baking"), MeshyConfig.GPU_BAKING.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.mesh_gpu_baking.desc"))
                .setSaveConsumer(MeshyConfig.GPU_BAKING::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.mesh_gpu_world"), MeshyConfig.GPU_WORLD.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.mesh_gpu_world.desc"))
                .setSaveConsumer(MeshyConfig.GPU_WORLD::set).build());

        render.addEntry(entryBuilder.startIntField(Component.translatable("config.tacz.client.render.mesh_gpu_light_cache_size"), MeshyConfig.GPU_LIGHT_CACHE_SIZE.get())
                .setMin(1).setMax(16).setDefaultValue(4).setTooltip(Component.translatable("config.tacz.client.render.mesh_gpu_light_cache_size.desc"))
                .setSaveConsumer(MeshyConfig.GPU_LIGHT_CACHE_SIZE::set).build());

        render.addEntry(entryBuilder.startIntField(Component.translatable("config.tacz.client.render.mesh_gpu_bake_budget"), MeshyConfig.GPU_BAKE_BUDGET_PER_FRAME.get())
                .setMin(1).setMax(64).setDefaultValue(4).setTooltip(Component.translatable("config.tacz.client.render.mesh_gpu_bake_budget.desc"))
                .setSaveConsumer(MeshyConfig.GPU_BAKE_BUDGET_PER_FRAME::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.mesh_poly_mirror_reverse_winding"), MeshyConfig.POLY_MIRROR_REVERSE_WINDING.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.mesh_poly_mirror_reverse_winding.desc"))
                .setSaveConsumer(MeshyConfig.POLY_MIRROR_REVERSE_WINDING::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.mesh_poly_prefer_pack_normals"), MeshyConfig.POLY_PREFER_PACK_NORMALS.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.mesh_poly_prefer_pack_normals.desc"))
                .setSaveConsumer(MeshyConfig.POLY_PREFER_PACK_NORMALS::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.mesh_poly_invert_normals"), MeshyConfig.POLY_INVERT_NORMALS.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.mesh_poly_invert_normals.desc"))
                .setSaveConsumer(MeshyConfig.POLY_INVERT_NORMALS::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.illuminated_real_sky"), RenderConfig.ILLUMINATED_REAL_SKY.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.illuminated_real_sky.desc"))
                .setSaveConsumer(RenderConfig.ILLUMINATED_REAL_SKY::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.mesh_gpu_under_shaders"), MeshyConfig.GPU_UNDER_SHADERS.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.mesh_gpu_under_shaders.desc"))
                .setSaveConsumer(MeshyConfig.GPU_UNDER_SHADERS::set).build());

        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.mesh_world_full_detail_distance"), MeshyConfig.WORLD_FULL_DETAIL_DISTANCE.get())
                .setMin(0.0).setMax(1024.0).setDefaultValue(16.0).setTooltip(Component.translatable("config.tacz.client.render.mesh_world_full_detail_distance.desc"))
                .setSaveConsumer(MeshyConfig.WORLD_FULL_DETAIL_DISTANCE::set).build());
    }
}
