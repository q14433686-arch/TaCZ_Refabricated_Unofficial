package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-mod-only readback for the user-environment suite (real Iris + a real shader pack + the
 * unmodified default-pack optic). Production state is read, never changed, except for the Iris
 * redirect flag that {@link #formatContract()} toggles and restores in {@code finally}.
 */
public final class UserEnvReadback {
    private UserEnvReadback() {}

    /** Every gate between an authored lens and a composited pixel, so a failure names its stage. */
    public static String state() {
        var s = new StringBuilder();
        var expected = ScopeLensFrame.expected;
        s.append("expected=").append(expected == null ? "null" : expected.getVisionMode());
        s.append(" observed==expected=").append(field(ScopeLensFrame.class, "observed") == expected);
        s.append(" measured=").append(field(ScopeLensFrame.class, "measured"));
        s.append(" current=").append(ScopeLensFrame.current() == null ? "null" : ScopeLensFrame.current().getVisionMode());
        s.append(" bounds=").append(Arrays.toString(ScopeLensFrame.currentBounds()));
        s.append(" needsHeat=").append(ScopeLensFrame.needsHeat());
        s.append(" heatReady=").append(ScopeThermalCapture.current() != null);
        for (String name : new String[]{"active", "ready", "failed", "shaderPack", "submittedModels", "texturedModels"})
            s.append(" heat.").append(name).append('=').append(field(ScopeThermalCapture.class, name));
        s.append(" pip.sceneCaptured=").append(field(ScopePipRenderState.class, "sceneCaptured"));
        s.append(" pip.failed=").append(field(ScopePipRenderState.class, "failed"));
        s.append(" maskThisFrame=").append(ScopeDepthCopyState.hasMaskCycleThisFrame());
        s.append(" maskValid=").append(ScopeDepthCopyState.isMaskCycleValid());
        s.append(" pack=").append(IrisCompat.isUsingRenderPack());
        s.append(" finalHook=").append(IrisCompat.supportsFinalScopeOverlay());
        return s.toString();
    }

    /**
     * Regression for the 2026-11-26 crash: with Iris' getVertexFormat() redirect LIVE, a clone must
     * still be declared with the vanilla format, and must report the vanilla format again once the
     * redirect is off. Fails (instead of passing vacuously) when the redirect cannot be observed.
     */
    public static String formatContract() {
        try {
            var flag = Class.forName("net.irisshaders.iris.vertices.ImmediateState").getField("isRenderingLevel");
            boolean old = flag.getBoolean(null);
            try {
                flag.setBoolean(null, true);
                VertexFormat redirected = RenderPipelines.ENTITY_SOLID.getVertexFormat();
                if (redirected == DefaultVertexFormat.NEW_ENTITY)
                    throw new AssertionError("Iris getVertexFormat redirect is not live; the contract would be vacuous");
                RenderPipeline clone = ScopeRenderTypes.clonePipeline(RenderPipelines.ENTITY_SOLID,
                        Identifier.parse("tacz_probe:pipeline/format_contract")).build();
                VertexFormat declared = ScopeRenderTypes.declaredVertexFormat(clone);
                if (declared != DefaultVertexFormat.NEW_ENTITY)
                    throw new AssertionError("Clone baked the redirected format " + declared.getElementAttributeNames());
                flag.setBoolean(null, false);
                if (clone.getVertexFormat() != DefaultVertexFormat.NEW_ENTITY)
                    throw new AssertionError("Clone kept an extended format after the redirect ended: "
                            + clone.getVertexFormat().getElementAttributeNames());
                return "redirected=" + redirected.getElementAttributeNames() + " cloneDeclared=NEW_ENTITY afterRedirect=NEW_ENTITY";
            } finally {
                flag.setBoolean(null, old);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Iris ImmediateState not reachable", e);
        }
    }

    /** After the pack is off, no TaCZ pipeline may still expose an Iris-only vertex attribute. */
    public static String scanPipelines() {
        Map<RenderPipeline, String> all = new IdentityHashMap<>();
        for (var p : RenderPipelines.getStaticPipelines())
            if ("tacz".equals(p.getLocation().getNamespace())) all.put(p, "static");
        int illuminated = addMap(all, "com.tacz.guns.client.model.bedrock.IlluminatedMaterials");
        int flash = addMap(all, "com.tacz.guns.client.render.FlashMaterials");
        int snow = addMap(all, "com.tacz.guns.client.particle.bedrock.particle.world.SnowstormMaterials");
        int thermal = 0;
        for (var depth : ScopeThermalMaterial.Depth.values()) {
            var p = (RenderPipeline) field(ScopeThermalMaterial.Depth.class, depth, "pipeline");
            all.put(p, "thermal");
            thermal++;
        }
        List<String> offenders = new ArrayList<>();
        for (var e : all.entrySet()) {
            var names = e.getKey().getVertexFormat().getElementAttributeNames();
            if (names.stream().anyMatch(n -> n.startsWith("mc_") || n.startsWith("at_") || n.startsWith("iris_")))
                offenders.add(e.getKey().getLocation() + "(" + e.getValue() + ")=" + names);
        }
        if (!offenders.isEmpty()) throw new AssertionError("Pipelines kept Iris vertex attributes after the pack was disabled: " + offenders);
        return "scanned=" + all.size() + " illuminated=" + illuminated + " flash=" + flash + " snowstorm=" + snow + " thermal=" + thermal;
    }

    private static int addMap(Map<RenderPipeline, String> all, String owner) {
        try {
            var f = Class.forName(owner).getDeclaredField("PIPELINES");
            f.setAccessible(true);
            var map = (Map<?, ?>) f.get(null);
            for (Object p : map.values()) all.put((RenderPipeline) p, owner.substring(owner.lastIndexOf('.') + 1));
            return map.size();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object field(Class<?> owner, String name) { return field(owner, null, name); }

    private static Object field(Class<?> owner, Object instance, String name) {
        try {
            var f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(instance);
        } catch (ReflectiveOperationException e) {
            return "<" + e.getClass().getSimpleName() + ">";
        }
    }
}
