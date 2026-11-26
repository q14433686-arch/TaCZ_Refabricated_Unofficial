package com.tacz.guns.mixin.client;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla's tab cache key does not contain gun-pack revisions. Clear it on client index reload. */
@Mixin(CreativeModeTabs.class)
public interface CreativeModeTabsAccessor {
    @Accessor("CACHED_PARAMETERS")
    static void tacz$setCachedParameters(CreativeModeTab.ItemDisplayParameters parameters) {
        throw new AssertionError("Mixin accessor not applied");
    }
}
