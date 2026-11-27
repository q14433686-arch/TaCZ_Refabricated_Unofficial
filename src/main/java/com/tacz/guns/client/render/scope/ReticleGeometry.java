package com.tacz.guns.client.render.scope;

import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.BiConsumer;

/** Replay each active authored division once. Only a small luminous fallback is safe
 * without an aperture. The draw-time mask guard, not a previous frame's CPU flag,
 * authorizes full geometry on 1.21.11. */
final class ReticleGeometry {
    private ReticleGeometry() {}

    static void submit(IReticleRenderer.Context ctx, ScopeNodeSet nodes) {
        extract(ctx, nodes, ctx::submit);
    }

    /** Same extraction for immediate, late-hand and final-overlay consumers. */
    static void extract(IReticleRenderer.Context ctx, ScopeNodeSet nodes,
                        BiConsumer<BedrockRenderSnapshot, Boolean> submit) {
        float alpha = Math.clamp((ctx.aimingProgress() - .35f) / .65f, 0f, 1f);
        if (alpha == 0) return;
        var divisions = ctx.maskActive() ? nodes.etchedReticle() : List.<BedrockPart>of();
        for (var part : divisions) {
            if (!belowAny(part, divisions)) capture(ctx, part, alpha).submitLayers(submit);
        }
        for (var part : nodes.illuminatedReticle()) {
            if (divisions.contains(part) || belowAny(part, divisions)
                    || belowAny(part, nodes.illuminatedReticle())) continue;
            var snapshot = capture(ctx, part, alpha);
            if (!ctx.maskActive()) snapshot = snapshot.filterCubes(ReticleMarkFilter::isThinMark);
            if (!snapshot.isEmpty()) submit.accept(snapshot, true);
        }
    }

    private static boolean belowAny(BedrockPart part, List<BedrockPart> roots) {
        for (var p = part.getParent(); p != null; p = p.getParent()) if (roots.contains(p)) return true;
        return false;
    }

    private static BedrockRenderSnapshot capture(IReticleRenderer.Context ctx, BedrockPart part, float alpha) {
        var pose = ctx.poseStack();
        var chain = new ArrayDeque<BedrockPart>();
        for (var p = part; p != null; p = p.getParent()) chain.push(p);
        // Ancestors supply transforms, not visibility. Never unhide inactive siblings.
        boolean visible = part.visible;
        pose.pushPose();
        try {
            part.visible = true;
            for (var p : chain) p.translateAndRotateAndScale(pose);
            return BedrockRenderSnapshot.captureSubtree(part, pose, ctx.displayContext(),
                    ctx.light(), ctx.overlay(), 1, 1, 1, alpha);
        } finally {
            part.visible = visible;
            pose.popPose();
        }
    }
}
