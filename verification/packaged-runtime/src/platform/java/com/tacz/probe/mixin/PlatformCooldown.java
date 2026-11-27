package com.tacz.probe.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiGraphics.class)
public interface PlatformCooldown {
    @Invoker("renderItemCooldown") void platform$cooldown(ItemStack stack,int x,int y);
}
