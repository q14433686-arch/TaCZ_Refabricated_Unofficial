package com.tacz.guns.client.init;

import com.tacz.guns.init.ModBlocks;
import net.fabricmc.fabric.api.blockview.v2.FabricBlockView;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;

public final class GunPegboardColors {
    private GunPegboardColors() {}

    public static void register() {
        ColorProviderRegistry.BLOCK.register((state, view, pos, tint) -> {
            if (view == null || pos == null) return -1;
            var data = ((FabricBlockView) view).getBlockEntityRenderData(pos);
            return data instanceof Integer rgb && rgb >= 0 ? 0xff000000 | rgb : -1;
        }, ModBlocks.GUN_PEGBOARD, ModBlocks.GLOW_GUN_PEGBOARD);
    }
}
