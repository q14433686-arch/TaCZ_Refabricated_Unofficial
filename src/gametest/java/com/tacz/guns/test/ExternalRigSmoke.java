package com.tacz.guns.test;

import cn.sh1rocu.tacz.compat.meshloader.model.TaczPolyMeshGunModel;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;

/** Independent poses and real TML dispatch, including the FIXED renderer's selected model on the wall. */
final class ExternalRigSmoke {
    static final Identifier MESH = Identifier.parse("port_test:mesh_probe");
    private static final Identifier GUN = Identifier.parse("tacz:ak47");

    static GunDisplayInstance check(ClientGameTestContext context) {
        var display = context.computeOnClient(mc -> TimelessAPI.getGunDisplay(MESH, GUN).orElseThrow());
        context.waitFor(mc -> {
            var near = display.getExternalModel(false);
            var far = display.getExternalModel(true);
            return near != null && far != null && !near.getRight().equals(far.getRight());
        });
        context.runOnClient(mc -> {
            var near = display.getExternalModel(false);
            var far = display.getExternalModel(true);
            var hand = display.getGunModel();
            if (!(hand instanceof TaczPolyMeshGunModel a) || !a.hasPolyMesh()
                    || !(near.getLeft() instanceof TaczPolyMeshGunModel b) || !b.hasPolyMesh()
                    || !(far.getLeft() instanceof TaczPolyMeshGunModel c) || !c.hasPolyMesh())
                throw new AssertionError("Lost a mesh in the main / external / LOD factory");
            if (a.getRootNode() == b.getRootNode() || b.getRootNode() == c.getRootNode() || a.getRootNode() == c.getRootNode())
                throw new AssertionError("External rigs share a mutable bone");
            if (!near.getRight().equals(display.getModelTexture()) || far.getLeft() != display.getLodModel().getLeft())
                throw new AssertionError("External selection used the wrong texture or duplicated the LOD bake");
        });
        // Only a real FIXED submission can have populated currentGunItem on this synthetic mesh.
        context.waitFor(mc -> display.getExternalModel(true).getLeft().getCurrentGunItem() != null
                || display.getExternalModel(false).getLeft().getCurrentGunItem() != null);
        GunMod.LOGGER.info("PORT_SMOKE_CLIENT: independent hand, near and far TML rigs; FIXED mesh submitted");
        return display;
    }

    static void afterReload(ClientGameTestContext context, GunDisplayInstance old) {
        context.runOnClient(mc -> {
            if (TimelessAPI.getGunDisplay(MESH, GUN).orElseThrow() == old || old.getExternalModel(false) != null || old.getExternalModel(true) != null)
                throw new AssertionError("An external rig survived its index revision");
        });
    }
}
