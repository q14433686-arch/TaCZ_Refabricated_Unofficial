package com.tacz.guns.client.init;

import com.tacz.guns.init.ModBlocks;
import net.fabricmc.fabric.api.blockgetter.v2.FabricBlockGetter;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class GunPegboardColors {
    private GunPegboardColors() {}
    public static void register() {
        BlockColorRegistry.register(List.of(new BlockTintSource() {
            @Override public int color(BlockState state) { return -1; }
            @Override public int colorInWorld(BlockState state, BlockAndTintGetter view, BlockPos pos) {
                var data = ((FabricBlockGetter) view).getBlockEntityRenderData(pos);
                return data instanceof Integer rgb && rgb >= 0 ? 0xff000000 | rgb : -1;
            }
        }), ModBlocks.GUN_PEGBOARD, ModBlocks.GLOW_GUN_PEGBOARD);
    }
}
