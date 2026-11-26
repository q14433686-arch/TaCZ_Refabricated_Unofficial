package com.tacz.probe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.model.IFunctionalSubmitter;
import com.tacz.guns.client.model.functional.TextShowRender;
import com.tacz.guns.client.model.papi.PapiManager;
import com.tacz.guns.client.model.papi.RangefinderReadout;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.display.gun.TextShow;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import java.lang.reflect.Proxy;
import java.util.ArrayList;

/** Authored rangefinder settings must reach the real text extraction/submission consumer. */
final class PlatformRangefinderText {
    static void verify(ClientGameTestContext ctx) {
        ctx.runOnClient(mc->{
            var data=IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder();
            float progress=data.clientAimingProgress,old=LocalPlayerDataHolder.oldAimingProgress;
            int[] unused={0};PapiManager.addPapi("port_probe_unused",stack->{unused[0]++;return "unused";});
            try {
                data.clientAimingProgress=LocalPlayerDataHolder.oldAimingProgress=1;
                RangefinderReadout.clear();
                var definition=ClientAssetsManager.GSON.fromJson("{text:'range:%rangefinder%',rangefinder:{max_distance:0}}",TextShow.class);
                var gun=mc.player.getMainHandItem();
                if(!PapiManager.getTextShow(definition.getTextKey(),gun,definition.getRangefinder()).equals("range:---"))
                    throw new AssertionError("Authored rangefinder placeholder was not evaluated");
                if(!PapiManager.getTextShow("literal 50%",gun).equals("literal 50%") || !PapiManager.getTextShow("%rangefinder%",gun).isEmpty() || unused[0]!=0)
                    throw new AssertionError("Literal/no-config/unused placeholder compatibility regressed");
                var tasks=new ArrayList<IFunctionalSubmitter.SubmitTask>();
                new TextShowRender(null,definition,gun).extract(new IFunctionalSubmitter.ExtractionContext(new PoseStack(),ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,0,0,tasks::add));
                var output=new ArrayList<String>();
                var collector=(SubmitNodeCollector)Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),new Class<?>[]{SubmitNodeCollector.class},(proxy,method,args)->{
                    if(args!=null)for(Object arg:args)if(arg instanceof FormattedCharSequence sequence){var text=new StringBuilder();sequence.accept((index,style,codepoint)->{text.appendCodePoint(codepoint);return true;});output.add(text.toString());}
                    return null;
                });
                tasks.forEach(task->task.submit(collector));
                if(!output.equals(java.util.List.of("range:---")))throw new AssertionError("Actual TextShowRender dropped authored rangefinder: "+output);
            } finally {
                data.clientAimingProgress=progress;LocalPlayerDataHolder.oldAimingProgress=old;RangefinderReadout.clear();
                try {var field=PapiManager.class.getDeclaredField("PAPI");field.setAccessible(true);((java.util.Map<?,?>)field.get(null)).remove("%port_probe_unused%");}
                catch(ReflectiveOperationException e){throw new AssertionError(e);}
            }
        });
        System.out.println("PORT_PLATFORM_RANGEFINDER_TEXT: authored zero-range config reached native text task as range:---; no-config/literal-percent compatibility and unused-callback suppression passed");
    }
    private PlatformRangefinderText(){}
}
