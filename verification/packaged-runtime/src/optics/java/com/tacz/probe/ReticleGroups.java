package com.tacz.probe;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.render.scope.ScopeNodeSet;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.List;

/** Inspect only mod-owned members (stable names in intermediary), without widening production API. */
final class ReticleGroups {
    static void verify() {
        try {
            var nodesField = BedrockAttachmentModel.class.getDeclaredField("reticleNodes"); nodesField.setAccessible(true);
            var groupField = BedrockAttachmentModel.class.getDeclaredField("activeViewGroup"); groupField.setAccessible(true);
            var select = BedrockAttachmentModel.class.getDeclaredMethod("filterReticleByActiveView", ScopeNodeSet.class); select.setAccessible(true);
            for (String name : List.of("scope_hamr", "scope_vudu")) {
                var model = TimelessAPI.getClientAttachmentIndex(Identifier.parse("tacz:" + name)).orElseThrow().getAttachmentModel();
                int previous = groupField.getInt(model);
                try {
                    var all = (ScopeNodeSet) nodesField.get(model);
                    model.setActiveViewGroup(1); var first = (ScopeNodeSet) select.invoke(model, all);
                    model.setActiveViewGroup(2); var second = (ScopeNodeSet) select.invoke(model, all);
                    var a = new HashSet<BedrockPart>(first.etchedReticle()); a.addAll(first.illuminatedReticle());
                    var b = new HashSet<BedrockPart>(second.etchedReticle()); b.addAll(second.illuminatedReticle());
                    if (a.isEmpty() || b.isEmpty() || a.stream().anyMatch(b::contains))
                        throw new AssertionError(name + ": active groups empty or overlap: " + a.size() + "/" + b.size());
                } finally { model.setActiveViewGroup(previous); }
            }
            var ta31 = TimelessAPI.getClientAttachmentIndex(Identifier.parse("tacz:scope_acog_ta31")).orElseThrow().getAttachmentModel();
            var nodes = (ScopeNodeSet) nodesField.get(ta31);
            var housing = ta31.getNode("scope_body");
            for (var part : nodes.illuminatedReticle()) {
                if (below(part, housing) && nodes.etchedReticle().stream().noneMatch(root -> below(part, root)))
                    throw new AssertionError("Housing decoration incorrectly selected as a reticle: " + part.name);
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError("Target mod reticle contract changed", e); }
    }
    private static boolean below(BedrockPart part, BedrockPart root) {
        if (root == null) return false;
        for (var p = part; p != null; p = p.getParent()) if (p == root) return true;
        return false;
    }
    private ReticleGroups() {}
}
