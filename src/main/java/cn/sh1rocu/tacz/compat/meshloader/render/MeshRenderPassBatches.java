package cn.sh1rocu.tacz.compat.meshloader.render;

import java.util.List;

/** Render-pass boundaries, not VBO baking or draw-call batching. */
final class MeshRenderPassBatches {
    private MeshRenderPassBatches() {
    }

    /**
     * Iris caches the normal/inverse model-view matrices and albedo/PBR setup until pass close.
     * Each bone therefore needs its own pass, even when adjacent bones share a texture.
     * Vanilla can keep all draws in one pass, using a DynamicTransforms slice per bone.
     */
    static <T> List<List<T>> partition(List<T> draws, boolean irisFlush) {
        if (draws.isEmpty()) {
            return List.of();
        }
        return irisFlush ? draws.stream().map(List::of).toList() : List.of(draws);
    }
}
