package com.tacz.guns.test.mixin;

import com.tacz.guns.client.tooltip.ClientAttachmentItemTooltip;
import com.tacz.guns.test.TooltipSmoke;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(value=ClientAttachmentItemTooltip.class,remap=false)
abstract class AttachmentTooltipObserver {
    @Shadow @Final private List<Component> components;
    @Shadow @Final private Identifier attachmentId;
    @Inject(method="extractText",at=@At("HEAD")) private void lines(CallbackInfo ci) { TooltipSmoke.drawn(attachmentId,components); }
}
