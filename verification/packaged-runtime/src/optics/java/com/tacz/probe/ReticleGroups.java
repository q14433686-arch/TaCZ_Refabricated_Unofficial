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
            var ocularIndex = BedrockAttachmentModel.class.getDeclaredMethod("activeOcularIndex"); ocularIndex.setAccessible(true);
            var scopeRole = BedrockAttachmentModel.class.getDeclaredMethod("activeGroupIsScope"); scopeRole.setAccessible(true);
            var ocularsField = BedrockAttachmentModel.class.getDeclaredField("ocularByIndex"); ocularsField.setAccessible(true);
            var divisionsField = BedrockAttachmentModel.class.getDeclaredField("divisionByIndex"); divisionsField.setAccessible(true);
            var activeOcular = BedrockAttachmentModel.class.getDeclaredMethod("isOcularInActiveGroup", BedrockPart.class); activeOcular.setAccessible(true);
            for (String name : List.of("scope_hamr", "scope_vudu", "scope_amg_10")) {
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
                    @SuppressWarnings("unchecked") var oculars=(java.util.Map<Integer,BedrockPart>)ocularsField.get(model);
                    @SuppressWarnings("unchecked") var divisions=(java.util.Map<Integer,BedrockPart>)divisionsField.get(model);
                    for(int view:new int[]{1,2}) {
                        model.setActiveViewGroup(view);
                        int expected=name.equals("scope_amg_10") ? 3-view : view;
                        if(!Integer.valueOf(expected).equals(ocularIndex.invoke(model)) || !Boolean.valueOf(expected==2).equals(scopeRole.invoke(model)))
                            throw new AssertionError(name+": authored view "+view+" did not select ocular "+expected+" and its actual scope role");
                        for(var ocular:oculars.entrySet())if(!Boolean.valueOf(ocular.getKey()==expected).equals(activeOcular.invoke(model,ocular.getValue())))
                            throw new AssertionError(name+": active aperture mismatched view "+view);
                        var selected=view==1?a:b;
                        for(var part:selected)if(!below(part,divisions.get(expected)))throw new AssertionError(name+": reticle belonged to the other optical axis");
                    }
                } finally { model.setActiveViewGroup(previous); }
            }
            var standard = TimelessAPI.getClientAttachmentIndex(Identifier.parse("tacz:scope_standard_8x")).orElseThrow().getAttachmentModel();
            int previous=groupField.getInt(standard);
            try {
                standard.setActiveViewGroup(1);
                if(!Integer.valueOf(1).equals(ocularIndex.invoke(standard)) || !Boolean.TRUE.equals(scopeRole.invoke(standard)))
                    throw new AssertionError("Standard 8x view 1 lost its magnified optical channel");
            } finally {standard.setActiveViewGroup(previous);}
            var ta31 = TimelessAPI.getClientAttachmentIndex(Identifier.parse("tacz:scope_acog_ta31")).orElseThrow().getAttachmentModel();
            var nodes = (ScopeNodeSet) nodesField.get(ta31);
            var housing = ta31.getNode("scope_body");
            for (var part : nodes.illuminatedReticle()) {
                if (below(part, housing) && nodes.etchedReticle().stream().noneMatch(root -> below(part, root)))
                    throw new AssertionError("Housing decoration incorrectly selected as a reticle: " + part.name);
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError("Target mod reticle contract changed", e); }
        System.out.println("PORT_RETICLE_GROUPS: real HAMR/Vudu/AMG10 rest-pose view-axis to ocular/division mapping, both active apertures and scope-lens roles; Standard 8x view 1 and TA31 housing exclusion passed");
    }
    private static boolean below(BedrockPart part, BedrockPart root) {
        if (root == null) return false;
        for (var p = part; p != null; p = p.getParent()) if (p == root) return true;
        return false;
    }
    private ReticleGroups() {}
}
