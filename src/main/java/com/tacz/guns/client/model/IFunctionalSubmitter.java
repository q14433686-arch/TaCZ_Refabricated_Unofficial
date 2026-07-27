package com.tacz.guns.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.function.Consumer;

/**
 * Collector-aware replacement for legacy IFunctionalRenderer.
 *
 * <p>Implementations run during extraction and emit immutable tasks. They must not retain the
 * mutable PoseStack or read mutable BedrockPart/GunDisplayInstance state when the task executes.</p>
 */
public interface IFunctionalSubmitter extends IFunctionalRenderer {
    void extract(ExtractionContext context);

    /** Prevent accidental use from the old same-buffer immediate rendering path. */
    @Override
    default void render(PoseStack poseStack,
                        VertexConsumer vertexBuffer,
                        ItemDisplayContext transformType,
                        int light,
                        int overlay) {
        // Collector-only implementation.
    }

    @FunctionalInterface
    interface SubmitTask {
        void submit(SubmitNodeCollector collector);
    }

    record ExtractionContext(PoseStack poseStack,
                             ItemDisplayContext displayContext,
                             int light,
                             int overlay,
                             Consumer<SubmitTask> output) {
        public ExtractionContext {
            PoseStack frozen = new PoseStack();
            frozen.last().pose().set(poseStack.last().pose());
            frozen.last().normal().set(poseStack.last().normal());
            poseStack = frozen;
        }

        public void add(SubmitTask task) {
            output.accept(task);
        }
    }
}
