package com.tacz.guns.client.resource;

import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSupport;
import cn.sh1rocu.tacz.compat.meshloader.model.TaczPolyMeshGunModel;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.other.GunModelTypeManager;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

/** Common model/texture resolution for the animated rig and independent external rigs. No GPU work. */
final class GunRigBaker {
    private GunRigBaker() {}

    static @Nullable Pair<BedrockGunModel, Identifier> bake(GunDisplay display, boolean lod, boolean external) {
        var low = display.getGunLod();
        if (lod && (low == null || low.getModelLocation() == null || low.getModelTexture() == null)) return null;
        var id = lod ? low.getModelLocation() : display.getModelLocation();
        var texture = lod ? low.getModelTexture() : display.getModelTexture();
        var pojo = id == null ? null : ClientAssetsManager.INSTANCE.getBedrockModelPOJO(id);
        // Preserve the missing-high fallback and use the *selected* geo and texture for mesh models too.
        if (!lod && pojo == null && low != null && low.getModelLocation() != null) {
            GunMod.LOGGER.warn("Missing high-detail model {}; trying {}", id, low.getModelLocation());
            id = low.getModelLocation();
            pojo = ClientAssetsManager.INSTANCE.getBedrockModelPOJO(id);
            if (low.getModelTexture() != null) texture = low.getModelTexture();
        }
        if (pojo == null || texture == null) throw new IllegalArgumentException("Missing model or texture for " + id);
        BedrockVersion version;
        if (BedrockVersion.isLegacyVersion(pojo) && pojo.getGeometryModelLegacy() != null) version = BedrockVersion.LEGACY;
        else if (BedrockVersion.isNewVersion(pojo) && pojo.getGeometryModelNew() != null) version = BedrockVersion.NEW;
        else throw new IllegalArgumentException("No supported geometry in " + id);

        // Main models honor registered model_type factories. LOD assets retain the bundled TML
        // loader's geo-based dispatch (formerly injected after checkLod). Mesh overrides live on
        // the new instance; do not substitute a plain Bedrock model just because this is FIXED.
        BedrockGunModel rig;
        if (lod) rig = PolyMeshSupport.hasGeoModel(id) ? new TaczPolyMeshGunModel(pojo, version) : new BedrockGunModel(pojo, version);
        else rig = GunModelTypeManager.getModelInstanceConstructor(display.getModelType()).apply(pojo, version);
        if (rig instanceof TaczPolyMeshGunModel mesh) {
            mesh.setOverrideTexture(texture);
            mesh.loadPolyMesh(PolyMeshSupport.toGeoPath(id));
        }
        if (external) {
            rig.setRenderHand(false);
            rig.cleanAnimationTransform(); // also hide the reload-only additional magazine
            if (display.getTextShows() != null) display.getTextShows().keySet().forEach(name ->
                    rig.setFunctionalRenderer(name, part -> { part.visible = false; return null; }));
        }
        return Pair.of(rig, texture);
    }
}
