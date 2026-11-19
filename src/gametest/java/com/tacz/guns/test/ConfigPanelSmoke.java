package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.compat.cloth.MenuIntegration;
import com.tacz.guns.config.ConfigPersist;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import java.nio.file.Files;
import java.util.*;

/** Real Cloth construction/defaults/save consumers, plus FCAP disk save/read (not process restart). */
final class ConfigPanelSmoke {
    static void run(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            var root=MenuIntegration.getConfigBuilder();
            var category=root.getOrCreateCategory(Component.translatable("config.tacz.client.render"));
            Map<String,Object> expected=Map.of(
                "disable_third_person_offhand_and_hotbar_gun_render",false,
                "gun_hurt_bob_multiplier",.1D,"muzzle_smoke",true,
                "dropped_gun_sprite_distance",RenderConfig.DEFAULT_DROPPED_GUN_SPRITE_DISTANCE,
                "muzzle_surface_light",false,"legacy_muzzle_light",false,"explosion_surface_light",false);
            int checked=0;
            for(Object candidate:category.getEntries()) {
                if(!(candidate instanceof AbstractConfigListEntry<?> entry))continue;
                for(var e:expected.entrySet()) {
                    var label=Component.translatable("config.tacz.client.render."+e.getKey());
                    if(entry.getFieldName().equals(label)) {
                        if(!entry.getDefaultValue().orElseThrow().equals(e.getValue()))throw new AssertionError("Incorrect Cloth default "+e.getKey());
                        entry.save();checked++;
                    }
                }
            }
            if(checked!=expected.size())throw new AssertionError("Missing or duplicate new render settings: "+checked);
            var ammo=root.getOrCreateCategory(Component.translatable("config.tacz.common.ammo"));
            Map<String,Object> rules=Map.of(
                    "destroy_blocks",com.tacz.guns.config.common.AmmoConfig.DEFAULT_DESTROY_BLOCKS,
                    "destroy_block_hardness_threshold",1D,"ignore_team_friendly_fire",false,
                    "enable_bullet_damage_ignore_list",false,
                    "bullet_damage_ignore_list",com.tacz.guns.config.common.AmmoConfig.DEFAULT_BULLET_DAMAGE_IGNORE_LIST,
                    "explosive_ammo_destroys_blocks",true);
            int ruleCount=0;
            for(Object candidate:ammo.getEntries()) {
                if(!(candidate instanceof AbstractConfigListEntry<?> entry))continue;
                for(var rule:rules.entrySet()) {
                    if(entry.getFieldName().equals(Component.translatable("config.tacz.common.ammo."+rule.getKey()))) {
                        if(!entry.getDefaultValue().orElseThrow().equals(rule.getValue()))throw new AssertionError("Incorrect ammo reset default "+rule.getKey());
                        entry.save();ruleCount++;
                    }
                }
            }
            if(ruleCount!=6)throw new AssertionError("Missing ammo rule controls: "+ruleCount);
            root.build(); // build the actual menu, not just isolated entries
            boolean hide=RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.get();
            boolean smoke=RenderConfig.RENDER_MUZZLE_SMOKE.get();
            double tilt=RenderConfig.GUN_HURT_BOB_MULTIPLIER.get();
            var common=com.tacz.guns.config.common.AmmoConfig.IGNORE_TEAM_FRIENDLY_FIRE;
            boolean team=common.get();
            var hardness=com.tacz.guns.config.common.AmmoConfig.DESTROY_BLOCK_HARDNESS_THRESHOLD;
            double threshold=hardness.get();
            var blockList=com.tacz.guns.config.common.AmmoConfig.DESTROY_BLOCKS;
            var blocks=List.copyOf(blockList.get());
            try {
                RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.set(!hide);
                RenderConfig.RENDER_MUZZLE_SMOKE.set(!smoke);
                RenderConfig.GUN_HURT_BOB_MULTIPLIER.set(.37D);
                common.set(!team);hardness.set(.75D);blockList.set(List.of("minecraft:glass","#minecraft:planks"));
                ConfigPersist.saveAll();
                var commonPath=FabricLoader.getInstance().getConfigDir().resolve("tacz-common.toml");
                try(var file=com.electronwill.nightconfig.core.file.CommentedFileConfig.builder(commonPath).build()) {
                    file.load();
                    if(!Objects.equals(file.get(common.getPath()),!team)
                            || !Objects.equals(file.get(hardness.getPath()),.75D)
                            || !Objects.equals(file.get(blockList.getPath()),List.of("minecraft:glass","#minecraft:planks")))
                        throw new AssertionError("Common ammo rules did not persist to TOML");
                }
                var path=FabricLoader.getInstance().getConfigDir().resolve("tacz-client.toml");
                try(var file=com.electronwill.nightconfig.core.file.CommentedFileConfig.builder(path).build()) {
                    file.load();
                    if(!Objects.equals(file.get(RenderConfig.GUN_HURT_BOB_MULTIPLIER.getPath()),.37D)
                        || !Objects.equals(file.get(RenderConfig.RENDER_MUZZLE_SMOKE.getPath()),!smoke)
                        || !Objects.equals(file.get(RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.getPath()),!hide))
                        throw new AssertionError("New client controls did not persist to TOML");
                }
            } finally {
                RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.set(hide);
                RenderConfig.RENDER_MUZZLE_SMOKE.set(smoke);
                RenderConfig.GUN_HURT_BOB_MULTIPLIER.set(tilt);
                common.set(team);hardness.set(threshold);blockList.set(blocks);
                ConfigPersist.saveAll();
            }
            GunMod.LOGGER.info("PORT_CONFIG: 14 Cloth render/ammo controls built with correct defaults and save consumers; client/common FCAP TOML persistence passed");
        });
    }
}
