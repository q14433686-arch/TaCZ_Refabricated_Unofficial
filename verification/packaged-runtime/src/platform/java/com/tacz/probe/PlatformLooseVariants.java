package com.tacz.probe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.client.renderer.item.AttachmentItemRenderer;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.VariantHelper;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Actual loose item renderer resolves tagged variants from synchronized common + client indexes. */
final class PlatformLooseVariants {
    static void verify(ClientGameTestContext ctx) {
        ctx.runOnClient(mc->{
            var stack=AttachmentItemBuilder.create().setId(Identifier.parse("tacz:scope_acog_ta31")).build();
            var variant=TimelessAPI.getClientAttachmentVariantIndex(Identifier.parse("tacz_lang_probe:replace")).orElseThrow();
            if(variant.getAttachmentModel()==null)throw new AssertionError("Variant model fixture not loaded");
            var expected=RenderTypes.entityCutout(variant.getModelTexture());
            var base=render(stack,ItemDisplayContext.FIXED,false);
            if(base.isEmpty() || base.contains(expected))throw new AssertionError("Invalid base material control");
            VariantHelper.setVariant(stack,Identifier.parse("tacz_lang_probe:replace"));
            if(TimelessAPI.getClientAttachmentVariantIndex(stack).isEmpty())throw new AssertionError("Synchronized variant target was rejected");
            var replaced=render(stack,ItemDisplayContext.FIXED,false);
            if(!replaced.contains(expected) || replaced.stream().anyMatch(base::contains))throw new AssertionError("Loose replacement retained the base model/material");
            int distance=RenderConfig.GUN_LOD_RENDER_DISTANCE.get();
            try {
                RenderConfig.GUN_LOD_RENDER_DISTANCE.set(1);
                if(!render(stack,ItemDisplayContext.GROUND,true).contains(expected))throw new AssertionError("Base LOD overwrote replacement variant");
            } finally {RenderConfig.GUN_LOD_RENDER_DISTANCE.set(distance);}
            VariantHelper.setVariant(stack,Identifier.parse("tacz_lang_probe:overlay"));
            var overlay=render(stack,ItemDisplayContext.FIXED,false);
            if(!overlay.contains(expected) || !overlay.containsAll(base))throw new AssertionError("Loose overlay did not add to the base model");
            var gui=render(stack,ItemDisplayContext.GUI,false);
            if(gui.contains(expected) || gui.isEmpty())throw new AssertionError("Variant replaced the GUI slot icon");
            VariantHelper.setVariant(stack,Identifier.parse("tacz_lang_probe:missing"));
            if(!render(stack,ItemDisplayContext.FIXED,false).equals(base))throw new AssertionError("Unknown loose variant lost base fallback");
        });
        System.out.println("PORT_PLATFORM_LOOSE_VARIANTS: real loose attachment FIXED replace/overlay and tagged lookup; replacement survived far GROUND LOD, GUI slot icon unchanged, missing variant fell back");
    }
    private static List<RenderType> render(ItemStack stack,ItemDisplayContext context,boolean far) {
        var types=new ArrayList<RenderType>();
        var storage=new SubmitNodeStorage(){@Override public void submitCustomGeometry(PoseStack pose,RenderType type,CustomGeometryRenderer draw){types.add(type);}};
        var collector=(SubmitNodeCollector)Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),new Class<?>[]{SubmitNodeCollector.class},(proxy,method,args)->
                method.getParameterCount()==1 && method.getParameterTypes()[0]==int.class && net.minecraft.client.renderer.OrderedSubmitNodeCollector.class.isAssignableFrom(method.getReturnType())?proxy:method.invoke(storage,args));
        var pose=new PoseStack();if(far)pose.translate(1000,0,0);
        AttachmentItemRenderer.INSTANCE.get().renderByItem(stack,context,pose,collector,15728880,0);
        return types;
    }
    private PlatformLooseVariants(){}
}
