package com.tacz.guns.compat.cloth.client;

import cn.sh1rocu.tacz.compat.meshloader.config.MeshyConfig;
import com.tacz.guns.client.renderer.crosshair.CrosshairType;
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

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.laser_fadeout"), RenderConfig.ENABLE_LASER_FADE_OUT.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.laser_fadeout.desc"))
                .setSaveConsumer(RenderConfig.ENABLE_LASER_FADE_OUT::set).build());


        render.addEntry(entryBuilder.startIntField(Component.translatable("config.tacz.client.render.gun_lod_render_distance"), RenderConfig.GUN_LOD_RENDER_DISTANCE.get())
                .setMin(0).setMax(Integer.MAX_VALUE).setDefaultValue(0).setTooltip(Component.translatable("config.tacz.client.render.gun_lod_render_distance.desc"))
                .setSaveConsumer(RenderConfig.GUN_LOD_RENDER_DISTANCE::set).build());

        render.addEntry(entryBuilder.startIntField(Component.translatable("config.tacz.client.render.bullet_hole_particle_life"), RenderConfig.BULLET_HOLE_PARTICLE_LIFE.get())
                .setMin(0).setMax(Integer.MAX_VALUE).setDefaultValue(400).setTooltip(Component.translatable("config.tacz.client.render.bullet_hole_particle_life.desc"))
                .setSaveConsumer(RenderConfig.BULLET_HOLE_PARTICLE_LIFE::set).build());

        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.bullet_hole_particle_fade_threshold"), RenderConfig.BULLET_HOLE_PARTICLE_FADE_THRESHOLD.get())
                .setMin(0).setMax(1).setDefaultValue(0.98).setTooltip(Component.translatable("config.tacz.client.render.bullet_hole_particle_fade_threshold.desc"))
                .setSaveConsumer(RenderConfig.BULLET_HOLE_PARTICLE_FADE_THRESHOLD::set).build());

        render.addEntry(entryBuilder.startDropdownMenu(Component.translatable("config.tacz.client.render.crosshair_type"),
                        CrosshairDropdown.of(RenderConfig.CROSSHAIR_TYPE.get()), CrosshairDropdown.of())
                .setSelections(Arrays.stream(CrosshairType.values()).sorted().sorted(Comparator.comparing(CrosshairType::name)).collect(Collectors.toCollection(LinkedHashSet::new)))
                .setDefaultValue(CrosshairType.DOT_1).setTooltip(Component.translatable("config.tacz.client.render.crosshair_type.desc"))
                .setSaveConsumer(RenderConfig.CROSSHAIR_TYPE::set).build());

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

        // ===== Scope PIP 画中画配置 =====
        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.scope_pip_enable"), RenderConfig.SCOPE_PIP_ENABLE.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.scope_pip_enable.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_ENABLE::set).build());
        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.scope_pip_rerender"), RenderConfig.SCOPE_PIP_RERENDER.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.scope_pip_rerender.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_RERENDER::set).build());
        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.scope_pip_resolution_scale"), RenderConfig.SCOPE_PIP_RESOLUTION_SCALE.get())
                .setMin(0.25).setMax(1.0).setDefaultValue(0.75)
                .setTooltip(Component.translatable("config.tacz.client.render.scope_pip_resolution_scale.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_RESOLUTION_SCALE::set).build());
        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.scope_pip_min_aiming_progress"), RenderConfig.SCOPE_PIP_MIN_AIMING_PROGRESS.get())
                .setMin(0.0).setMax(1.0).setDefaultValue(0.05)
                .setTooltip(Component.translatable("config.tacz.client.render.scope_pip_min_aiming_progress.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_MIN_AIMING_PROGRESS::set).build());
        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.scope_pip_min_magnification"), RenderConfig.SCOPE_PIP_MIN_MAGNIFICATION.get())
                .setMin(1.0).setMax(100.0).setDefaultValue(4.0)
                .setTooltip(Component.translatable("config.tacz.client.render.scope_pip_min_magnification.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_MIN_MAGNIFICATION::set).build());
        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.scope_pip_world_zoom_share"), RenderConfig.SCOPE_PIP_WORLD_ZOOM_SHARE.get())
                .setMin(0.0).setMax(1.0).setDefaultValue(0.0)
                .setTooltip(Component.translatable("config.tacz.client.render.scope_pip_world_zoom_share.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_WORLD_ZOOM_SHARE::set).build());
        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.scope_pip_sharpness"), RenderConfig.SCOPE_PIP_SHARPNESS.get())
                .setMin(0.0).setMax(1.0).setDefaultValue(0.5)
                .setTooltip(Component.translatable("config.tacz.client.render.scope_pip_sharpness.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_SHARPNESS::set).build());
        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.scope_pip_allow_shader_packs"), RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.scope_pip_allow_shader_packs.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS::set).build());
        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.scope_pip_debug_no_composite"), RenderConfig.SCOPE_PIP_DEBUG_NO_COMPOSITE.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.scope_pip_debug_no_composite.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_DEBUG_NO_COMPOSITE::set).build());
        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.scope_pip_debug_paint_lens"), RenderConfig.SCOPE_PIP_DEBUG_PAINT_LENS.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.scope_pip_debug_paint_lens.desc"))
                .setSaveConsumer(RenderConfig.SCOPE_PIP_DEBUG_PAINT_LENS::set).build());

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

        // ================= Mesh Loader（poly_mesh）配置 =================
        // 全部 18 项都接进来了（R3 起的「胶水」轮次，法线三项与自发光 sky 那一项是同一天补的）：TOML 里能改的，局内也能改。
        // 每条的 setDefaultValue 与 MeshyConfig 的 define/defineInRange 默认值逐字对齐 ——
        // Cloth 的「重置为默认」读的是这里，不是 TOML，两边写歪就会出现「重置后行为变了」。
        // 范围同理取自 defineInRange：刻意不收窄成 UI 好看的区间，否则枪包作者需要的
        // 极端值（例如 1M 顶点预算）在局内根本设不进去。
        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.mesh_enable"), MeshyConfig.ENABLE_MESH.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.mesh_enable.desc"))
                .setSaveConsumer(MeshyConfig.ENABLE_MESH::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.mesh_poly_mirror_reverse_winding"), MeshyConfig.POLY_MIRROR_REVERSE_WINDING.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.mesh_poly_mirror_reverse_winding.desc"))
                .setSaveConsumer(MeshyConfig.POLY_MIRROR_REVERSE_WINDING::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.mesh_poly_invert_normals"), MeshyConfig.POLY_INVERT_NORMALS.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.mesh_poly_invert_normals.desc"))
                .setSaveConsumer(MeshyConfig.POLY_INVERT_NORMALS::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.mesh_poly_prefer_pack_normals"), MeshyConfig.POLY_PREFER_PACK_NORMALS.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.mesh_poly_prefer_pack_normals.desc"))
                .setSaveConsumer(MeshyConfig.POLY_PREFER_PACK_NORMALS::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.mesh_poly_illuminated_real_sky"), MeshyConfig.POLY_ILLUMINATED_REAL_SKY.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.mesh_poly_illuminated_real_sky.desc"))
                .setSaveConsumer(MeshyConfig.POLY_ILLUMINATED_REAL_SKY::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.mesh_poly_in_preview"), MeshyConfig.POLY_IN_PREVIEW.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.mesh_poly_in_preview.desc"))
                .setSaveConsumer(MeshyConfig.POLY_IN_PREVIEW::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.mesh_poly_in_shadow"), MeshyConfig.POLY_IN_SHADOW.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.mesh_poly_in_shadow.desc"))
                .setSaveConsumer(MeshyConfig.POLY_IN_SHADOW::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.mesh_log_stats"), MeshyConfig.LOG_STATS.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.mesh_log_stats.desc"))
                .setSaveConsumer(MeshyConfig.LOG_STATS::set).build());

        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.mesh_max_render_distance"), MeshyConfig.MAX_RENDER_DISTANCE.get())
                .setMin(0.0).setMax(1_000_000.0).setDefaultValue(48.0).setTooltip(Component.translatable("config.tacz.client.render.mesh_max_render_distance.desc"))
                .setSaveConsumer(MeshyConfig.MAX_RENDER_DISTANCE::set).build());

        render.addEntry(entryBuilder.startIntField(Component.translatable("config.tacz.client.render.mesh_gui_max_vertices"), MeshyConfig.GUI_MAX_VERTICES.get())
                .setMin(0).setMax(10_000_000).setDefaultValue(65536).setTooltip(Component.translatable("config.tacz.client.render.mesh_gui_max_vertices.desc"))
                .setSaveConsumer(MeshyConfig.GUI_MAX_VERTICES::set).build());

        render.addEntry(entryBuilder.startIntField(Component.translatable("config.tacz.client.render.mesh_world_max_vertices"), MeshyConfig.WORLD_MAX_VERTICES.get())
                .setMin(0).setMax(10_000_000).setDefaultValue(120000).setTooltip(Component.translatable("config.tacz.client.render.mesh_world_max_vertices.desc"))
                .setSaveConsumer(MeshyConfig.WORLD_MAX_VERTICES::set).build());

        render.addEntry(entryBuilder.startIntField(Component.translatable("config.tacz.client.render.mesh_max_model_vertices"), MeshyConfig.MAX_MODEL_VERTICES.get())
                .setMin(0).setMax(10_000_000).setDefaultValue(120000).setTooltip(Component.translatable("config.tacz.client.render.mesh_max_model_vertices.desc"))
                .setSaveConsumer(MeshyConfig.MAX_MODEL_VERTICES::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.mesh_gpu_baking"), MeshyConfig.GPU_BAKING.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.mesh_gpu_baking.desc"))
                .setSaveConsumer(MeshyConfig.GPU_BAKING::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.mesh_gpu_under_shaders"), MeshyConfig.GPU_UNDER_SHADERS.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.mesh_gpu_under_shaders.desc"))
                .setSaveConsumer(MeshyConfig.GPU_UNDER_SHADERS::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.mesh_gpu_world"), MeshyConfig.GPU_WORLD.get())
                .setDefaultValue(true).setTooltip(Component.translatable("config.tacz.client.render.mesh_gpu_world.desc"))
                .setSaveConsumer(MeshyConfig.GPU_WORLD::set).build());

        render.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.tacz.client.render.mesh_gpu_world_under_shaders"), MeshyConfig.GPU_WORLD_UNDER_SHADERS.get())
                .setDefaultValue(false).setTooltip(Component.translatable("config.tacz.client.render.mesh_gpu_world_under_shaders.desc"))
                .setSaveConsumer(MeshyConfig.GPU_WORLD_UNDER_SHADERS::set).build());

        render.addEntry(entryBuilder.startIntField(Component.translatable("config.tacz.client.render.mesh_gpu_light_cache_size"), MeshyConfig.GPU_LIGHT_CACHE_SIZE.get())
                .setMin(1).setMax(16).setDefaultValue(4).setTooltip(Component.translatable("config.tacz.client.render.mesh_gpu_light_cache_size.desc"))
                .setSaveConsumer(MeshyConfig.GPU_LIGHT_CACHE_SIZE::set).build());

        render.addEntry(entryBuilder.startDoubleField(Component.translatable("config.tacz.client.render.mesh_world_full_detail_distance"), MeshyConfig.WORLD_FULL_DETAIL_DISTANCE.get())
                .setMin(0.0).setMax(1024.0).setDefaultValue(16.0).setTooltip(Component.translatable("config.tacz.client.render.mesh_world_full_detail_distance.desc"))
                .setSaveConsumer(MeshyConfig.WORLD_FULL_DETAIL_DISTANCE::set).build());
    }
}
