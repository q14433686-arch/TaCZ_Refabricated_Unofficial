package com.tacz.probe;

import com.tacz.guns.compat.cloth.MenuIntegration;
import com.tacz.guns.config.ConfigPersist;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.config.common.OtherConfig;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.AbstractConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Native Cloth entries and their real save callbacks, not a parallel settings model. */
final class PlatformClothSettings {
    private record Setting(String category,String key,ConfigValue<?> value) {}
    private static Setting render(String key,ConfigValue<?> value){return new Setting("client.render",key,value);}
    private static Setting gun(String key,ConfigValue<?> value){return new Setting("common.gun",key,value);}
    static void verify(ClientGameTestContext ctx) {
        ctx.runOnClient(mc->{
            var settings=List.of(
                    render("muzzle_surface_light",RenderConfig.MUZZLE_SURFACE_LIGHT),
                    render("legacy_muzzle_light",RenderConfig.LEGACY_MUZZLE_LIGHT),
                    render("explosion_surface_light",RenderConfig.EXPLOSION_SURFACE_LIGHT),
                    render("muzzle_smoke",RenderConfig.RENDER_MUZZLE_SMOKE),
                    render("dropped_gun_sprite_distance",RenderConfig.DROPPED_GUN_SPRITE_DISTANCE),
                    render("hit_particle_enable",RenderConfig.HIT_PARTICLE_ENABLE),
                    render("hit_extra_particle_enable",RenderConfig.HIT_EXTRA_PARTICLE_ENABLE),
                    render("hit_particle_max_distance",RenderConfig.HIT_PARTICLE_MAX_DISTANCE),
                    render("scope_lens_enable",RenderConfig.EXPERIMENTAL_SCOPE_LENS),
                    render("scope_thermal_enable",RenderConfig.SCOPE_THERMAL_ENABLE),
                    render("scope_pip_rerender_interval",RenderConfig.SCOPE_PIP_RERENDER_INTERVAL),
                    render("scope_pip_isolate_pipeline",RenderConfig.SCOPE_PIP_ISOLATE_PIPELINE),
                    render("scope_pip_shadow_scale",RenderConfig.SCOPE_PIP_SHADOW_SCALE),
                    render("scope_pip_release_idle_pipeline",RenderConfig.SCOPE_PIP_RELEASE_IDLE_PIPELINE),
                    render("scope_pip_idle_release_delay_frames",RenderConfig.SCOPE_PIP_IDLE_RELEASE_DELAY_FRAMES),
                    render("creative_pack_sidebar",RenderConfig.RENDER_CREATIVE_PACK_SIDEBAR),
                    render("illuminated_real_sky",RenderConfig.ILLUMINATED_REAL_SKY),
                    gun("enable_gun_sound_anger",GunConfig.ENABLE_GUN_SOUND_ANGER),
                    gun("default_gun_sound_neutral_mob_anger_distance",GunConfig.DEFAULT_GUN_SOUND_NEUTRAL_MOB_ANGER_DISTANCE),
                    gun("default_gun_sound_hostile_mob_anger_distance",GunConfig.DEFAULT_GUN_SOUND_HOSTILE_MOB_ANGER_DISTANCE),
                    new Setting("common.other","hide_gun_pack_compatibility_warning",OtherConfig.HIDE_GUN_PACK_COMPATIBILITY_WARNING));
            Map<ConfigValue<?>,Object> originals=new LinkedHashMap<>();
            var previous=mc.screen;
            try {
                var builder=MenuIntegration.getConfigBuilder();
                var screen=builder.setParentScreen(previous).build();mc.setScreen(screen);
                int toggled=0;
                for(var setting:settings){
                    var entry=entry(builder,setting);
                    if(!entry.getValue().equals(setting.value.get()) || !entry.getDefaultValue().orElseThrow().equals(setting.value.getDefault()))
                        throw new AssertionError("Cloth current/reset binding mismatch: "+setting.key);
                    if(entry instanceof BooleanListEntry bool){
                        originals.put(setting.value,setting.value.get());
                        ((Button)bool.children().getFirst()).onPress(new KeyEvent(257,0,0));
                        if(bool.getValue().equals(setting.value.get()))throw new AssertionError("Boolean widget did not edit: "+setting.key);
                        toggled++;
                    }
                }
                if(toggled!=14)throw new AssertionError("Unexpected boolean settings fixture: "+toggled);
                ((AbstractConfigScreen)screen).saveAll(false);
                for(var setting:settings)if(originals.containsKey(setting.value) && originals.get(setting.value).equals(setting.value.get()))
                    throw new AssertionError("Cloth save consumer not bound: "+setting.key);
                var configDir=FabricLoader.getInstance().getConfigDir();
                for(var setting:settings)if(originals.containsKey(setting.value)){
                    var file=configDir.resolve(setting.category.startsWith("client")?"tacz-client.toml":"tacz-common.toml");
                    var path=setting.value.getPath();var key=path.getLast();
                    if(!Pattern.compile("(?m)^\\s*"+Pattern.quote(key)+"\\s*=\\s*"+setting.value.get()+"\\s*(?:#.*)?$").matcher(Files.readString(file)).find())
                        throw new AssertionError("Cloth save hook did not persist "+key+" to "+file);
                }
                // A newly opened editor sees the saved values; unsaved widget edits must not leak.
                var reopened=MenuIntegration.getConfigBuilder();mc.setScreen(reopened.setParentScreen(previous).build());
                var setting=settings.getFirst();var bool=(BooleanListEntry)entry(reopened,setting);
                if(!bool.getValue().equals(setting.value.get()))throw new AssertionError("Reopened editor lost saved setting");
                var saved=setting.value.get();((Button)bool.children().getFirst()).onPress(new KeyEvent(257,0,0));
                mc.screen.keyPressed(new KeyEvent(256,0,0));
                var discard=mc.screen.children().stream().filter(e->e instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals("text.cloth-config.quit_discard")).findFirst().orElseThrow(()->new AssertionError("No native discard confirmation"));
                ((Button)discard).onPress(new KeyEvent(257,0,0));
                if(mc.screen!=previous || !saved.equals(setting.value.get()))throw new AssertionError("Cancelled Cloth edit mutated configuration");
            } catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
            finally {originals.forEach(PlatformClothSettings::restore);ConfigPersist.saveAll();mc.setScreen(previous);}
        });
        System.out.println("PORT_PLATFORM_CLOTH_SETTINGS: 21 native entries current/reset bindings; 14 boolean widgets/save consumers and actual client/common TOML save hook; reopen/cancel isolation passed (not a process-restart claim)");
    }
    private static AbstractConfigListEntry<?> entry(ConfigBuilder builder,Setting setting){
        String key="config.tacz."+setting.category+"."+setting.key;
        var entries=builder.getOrCreateCategory(Component.translatable("config.tacz."+setting.category)).getEntries().stream()
                .filter(e->e instanceof AbstractConfigListEntry<?> entry && entry.getFieldName().getContents() instanceof TranslatableContents text && text.getKey().equals(key)).toList();
        if(entries.size()!=1)throw new AssertionError("Missing/duplicate Cloth setting "+key+": "+entries.size());
        return (AbstractConfigListEntry<?>)entries.getFirst();
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static void restore(ConfigValue value,Object old){value.set(old);}
    private PlatformClothSettings(){}
}
