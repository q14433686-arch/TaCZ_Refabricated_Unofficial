package com.tacz.probe;

import com.google.gson.Gson;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import com.tacz.guns.client.tooltip.ClientAttachmentItemTooltip;
import com.tacz.guns.inventory.tooltip.AttachmentItemTooltip;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import java.util.List;

final class PlatformAttachmentTips {
    private static final String PREFIX="tooltip.tacz.attachment.";
    static void verify(ClientGameTestContext ctx) {
        ctx.runOnClient(mc->{
            var entry=TimelessAPI.getAllClientAttachmentIndex().stream().filter(e->e.getValue().isScope()).findFirst().orElseThrow();
            var index=entry.getValue();var pojo=TimelessAPI.getCommonAttachmentIndex(entry.getKey()).orElseThrow().getPojo();
            try {
                var displayField=ClientAttachmentIndex.class.getDeclaredField("display");displayField.setAccessible(true);var display=displayField.get(index);
                var dataField=ClientAttachmentIndex.class.getDeclaredField("data");dataField.setAccessible(true);var data=dataField.get(index);
                var builtinField=pojo.getClass().getDeclaredField("builtinAttachment");builtinField.setAccessible(true);boolean builtin=builtinField.getBoolean(pojo);
                try {
                    dataField.set(index,new Gson().fromJson("{flash_hider:0.5,smoke_hider:2}",AttachmentData.class));
                    displayField.set(index,display("THERMAL_BLACK_HOT",true,true));builtinField.setBoolean(pojo,true);
                    var positive=tips(entry.getKey(),AttachmentType.SCOPE);
                    for(String key:List.of("builtin","flash_hider.decrease","smoke_hider.increase","rangefinder","fire_control","ammo_counter","thermal_imaging.black_hot")) {
                        require(count(positive,key)==1,"Missing or duplicate attachment capability "+key);
                        require(!Language.getInstance().getOrDefault(PREFIX+key).equals(PREFIX+key),"New tooltip key lacks a loaded translation: "+key);
                    }
                    for(String key:List.of("flash_hider.increase","smoke_hider.decrease","thermal_imaging.white_hot"))require(count(positive,key)==0,"Wrong sign/sensor "+key);
                    var nonscope=tips(entry.getKey(),AttachmentType.MUZZLE);
                    require(count(nonscope,"rangefinder")==1 && count(nonscope,"fire_control")==1
                            && count(nonscope,"ammo_counter")==0 && count(nonscope,"thermal_imaging.black_hot")==0,
                            "Scope-only capabilities leaked to other attachment categories");
                    displayField.set(index,display("NORMAL",false,false));
                    dataField.set(index,new Gson().fromJson("{flash_hider:1,smoke_hider:1}",AttachmentData.class));builtinField.setBoolean(pojo,false);
                    var neutral=tips(entry.getKey(),AttachmentType.SCOPE);
                    require(count(neutral,"rangefinder")==1 && count(neutral,"fire_control")==0 && count(neutral,"ammo_counter")==0,
                            "Capability flags remained stale after a changed display");
                    for(String key:List.of("builtin","flash_hider.decrease","flash_hider.increase","smoke_hider.decrease","smoke_hider.increase","thermal_imaging.black_hot"))
                        require(count(neutral,key)==0,"Neutral/absent feature produced a tooltip: "+key);
                    dataField.set(index,new Gson().fromJson("{flash_hider:2,smoke_hider:0.5}",AttachmentData.class));
                    var inverse=tips(entry.getKey(),AttachmentType.MUZZLE);
                    require(count(inverse,"flash_hider.increase")==1 && count(inverse,"smoke_hider.decrease")==1,"Inverse muzzle modifiers lost their tooltips");
                } finally {displayField.set(index,display);dataField.set(index,data);builtinField.setBoolean(pojo,builtin);}
            } catch(ReflectiveOperationException e){throw new AssertionError(e);}
        });
        System.out.println("PORT_PLATFORM_ATTACHMENT_TIPS: actual native tooltip construction: builtin, signed flash/smoke, thermal sensor, range/impact/ammo capabilities, scope-only and neutral/changed-display negatives, loaded translations");
    }
    private static AttachmentDisplay display(String vision,boolean impact,boolean counter) {
        var display=ClientAssetsManager.GSON.fromJson("{scope_lens:{vision_mode:'"+vision+"'},text_show:{range:{rangefinder:{show_impact_prediction:"+impact+"}},counter:{text:'"+(counter?"Ammo %ammo_count%":"No counter")+"'}}}",AttachmentDisplay.class);
        display.init();return display;
    }
    @SuppressWarnings("unchecked")
    private static List<Component> tips(Identifier id,AttachmentType type) throws ReflectiveOperationException {
        var tooltip=new ClientAttachmentItemTooltip(new AttachmentItemTooltip(id,type,AttachmentItemBuilder.create().setId(id).build()));
        var field=ClientAttachmentItemTooltip.class.getDeclaredField("components");field.setAccessible(true);return (List<Component>)field.get(tooltip);
    }
    private static long count(List<Component> components,String suffix){return components.stream().filter(c->c.getContents() instanceof TranslatableContents t && t.getKey().equals(PREFIX+suffix)).count();}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private PlatformAttachmentTips(){}
}
