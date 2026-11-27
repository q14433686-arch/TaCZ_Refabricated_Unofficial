package com.tacz.probe;

import com.google.gson.JsonParser;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.manager.AttachmentDataManager;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;

/** Exercise actual modifier parsing and the UI data supplied to the refit screen in production. */
final class PlatformModifiers {
    static void verify(ClientGameTestContext ctx) {
        ctx.runOnClient(mc->{
            var stack=mc.player.getMainHandItem().copy();var gun=(IGun)stack.getItem();gun.setFireMode(stack,FireMode.SEMI);
            var data=TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).orElseThrow().getGunData();
            var cache=new AttachmentCacheProperty();cache.eval(stack,data);
            double global=AmmoConfig.GLOBAL_BULLET_SPEED_MODIFIER.get();
            try {
                AmmoConfig.GLOBAL_BULLET_SPEED_MODIFIER.set(3.0);
                cache.setCache(GunProperties.AMMO_SPEED,123.25f);
                var speed=new AmmoSpeedModifier().getPropertyDiagramsData(stack,data,cache).getFirst();
                if(!speed.defaultString().equals("370m/s"))throw new AssertionError("Refit speed omitted actual global multiplier: "+speed);
                cache.setCache(GunProperties.EFFECTIVE_RANGE,37.25f);
                var range=new EffectiveRangeModifier().getPropertyDiagramsData(stack,data,cache).getFirst();
                if(!range.defaultString().equals(String.format("%.1fm",37.25f)))throw new AssertionError("Refit range displayed unmodified base: "+range);
                cache.setCache(GunProperties.HEADSHOT_MULTIPLIER,1.125f);
                var head=new HeadShotModifier().getPropertyDiagramsData(stack,data,cache).getFirst();
                if(!head.defaultString().equals(String.format("x%.2f",1.125f)))throw new AssertionError("Lost headshot precision: "+head);
                gun.setFireMode(stack,FireMode.BURST);cache.setCache(GunProperties.ROUNDS_PER_MINUTE,987);
                var rpm=new RpmModifier().getPropertyDiagramsData(stack,data,cache).getFirst();
                if(!rpm.defaultString().equals(String.format("%drpm",Math.max(1,data.getBurstData().getBpm()))) || rpm.modifier().intValue()!=0)
                    throw new AssertionError("Burst rate shown as intra-burst RPM: "+rpm);
                for(String field:new String[]{"aim_inaccuracy","sneak_inaccuracy","lie_inaccuracy"}) {
                    var parsed=AttachmentDataManager.parseData(JsonParser.parseString("{\""+field+"\":{\"addend\":0.125}}"),CommonAssetsManager.GSON);
                    if(!parsed.getModifier().containsKey(InaccuracyModifier.ID))throw new AssertionError("Standalone optional field lost by modifier registry: "+field);
                }
                var inaccuracy=new InaccuracyModifier();
                if(inaccuracy.getDiagramsDataSize()!=4)throw new AssertionError("Four accuracy rows advertised wrong UI size");
                var property=new DamageModifier().readJson("{\"damage\":{\"addend\":0.125}}");property.initComponents();
                if(property.getComponents().isEmpty())throw new AssertionError("Fractional damage improvement rounded out of tooltip");
            } finally {AmmoConfig.GLOBAL_BULLET_SPEED_MODIFIER.set(global);}
        });
        System.out.println("PORT_PLATFORM_MODIFIERS: actual refit speed/global gain, modified range, fractional headshot/damage, burst BPM and standalone accuracy-field registry parsing passed");
    }
    private PlatformModifiers(){}
}
