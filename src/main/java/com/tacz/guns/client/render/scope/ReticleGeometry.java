package com.tacz.guns.client.render.scope;

import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot;
import java.util.ArrayDeque;
import java.util.List;

/** Replay the authored, active division once. Only the small luminous fallback is safe without an aperture. */
final class ReticleGeometry {
    private ReticleGeometry() {}

    static void submit(IReticleRenderer.Context ctx, ScopeNodeSet nodes) {
        float alpha = Math.clamp((ctx.aimingProgress() - .35f) / .65f, 0f, 1f);
        if (alpha == 0) return;
        var divisions = ctx.maskActive() ? nodes.etchedReticle() : List.<BedrockPart>of();
        for (var part : divisions) {
            capture(ctx, part, alpha).submitLayers(ctx::submit);
        }
        // Some packs use a standalone luminous root. Do not replay nested markers twice.
        for (var part : nodes.illuminatedReticle()) {
            if (divisions.contains(part) || belowAny(part, divisions) || belowAny(part, nodes.illuminatedReticle())) continue;
            var snapshot = capture(ctx, part, alpha);
            if (!snapshot.isEmpty()) ctx.submit(snapshot, true);
        }
    }

    private static boolean belowAny(BedrockPart part, List<BedrockPart> roots) {
        for (var p=part.getParent(); p!=null; p=p.getParent()) if (roots.contains(p)) return true;
        return false;
    }

    private static BedrockRenderSnapshot capture(IReticleRenderer.Context ctx, BedrockPart part, float alpha) {
        var pose = ctx.poseStack();
        var chain = new ArrayDeque<BedrockPart>();
        for (var p=part; p!=null; p=p.getParent()) chain.push(p);
        // Only the root being captured must be visible. Applying its parent matrices doesn't
        // require unhiding their geometry; inactive sibling divisions stay hidden.
        boolean visible = part.visible;
        pose.pushPose();
        try {
            part.visible = true;
            for (var p : chain) p.translateAndRotateAndScale(pose);
            return BedrockRenderSnapshot.captureSubtree(part, pose, ctx.displayContext(), ctx.light(), ctx.overlay(), 1, 1, 1, alpha);
        } finally { part.visible=visible; pose.popPose(); }
    }
}
