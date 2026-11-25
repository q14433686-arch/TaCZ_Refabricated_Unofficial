package com.tacz.guns.compat.iris;

/** Source-level hooks in the final transformed Iris fragment, before compile/link. No Iris linkage. */
public final class IrisScopeFragment {
    private IrisScopeFragment() {}

    public static String patch(String name, String source) {
        boolean hand = isHandProgram(name);
        // Iris 1.11 maps EMISSIVE_ENTITIES + the ENTITY format + ALPHA_CUTOUT to this key,
        // including when ShaderCreator synthesized the fragment in the absence of a pack program.
        boolean eyes = "entities_eyes_trans".equals(name);
        if ((!hand && !eyes) || source == null || source.contains("tacz_ScopeMaskMode")) {
            return source;
        }
        int main = source.indexOf("void main");
        if (main < 0) {
            return source;
        }
        int brace = source.indexOf('{', main);
        if (brace < 0) {
            return source;
        }

        if (eyes) {
            // A world-space local flash can be drawn before this frame's hand. Iris' current
            // depthtex2 need not correspond to the saved aperture (and FallbackShader need not
            // bind it at all). Use the *pair* from the same native cycle. No depth-restore
            // uniform or gl_FragDepth assignment in this shared spider-eyes program.
            return source.substring(0, main) + """
                    uniform int tacz_ScopeMaskMode;
                    uniform sampler2D tacz_WorldDepthSampler;
                    uniform sampler2D tacz_ApertureDepthSampler;
                    """ + source.substring(main, brace + 1) + """

                        if (tacz_ScopeMaskMode == 2) {
                            ivec2 tacz_pixel = ivec2(gl_FragCoord.xy);
                            float tacz_world = texelFetch(tacz_WorldDepthSampler, tacz_pixel, 0).r;
                            float tacz_aperture = texelFetch(tacz_ApertureDepthSampler, tacz_pixel, 0).r;
                            if (tacz_aperture < tacz_world - 1.0e-6) discard;
                        }
                    """ + source.substring(brace + 1);
        }
        // Iris copies world depth immediately before HAND_SOLID and publishes it as depthtex2. Reuse that
        // canonical sampler rather than copying the currently-bound hand FBO, whose depth can start cleared.
        String depthtex2Declaration = source.contains("depthtex2")
                ? ""
                : "uniform sampler2D depthtex2;\n";
        String declarations = "\n// TACZ ocular scope branches; dormant for ordinary hand draws\n"
                + "uniform int tacz_DepthRestoreMode;\n"
                + "uniform int tacz_ScopeMaskMode;\n"
                + "uniform sampler2D tacz_ApertureDepthSampler;\n"
                + "uniform sampler2D tacz_PostBodyDepthSampler;\n"
                + depthtex2Declaration;
        // Once a shader statically writes gl_FragDepth anywhere, OpenGL leaves the value undefined
        // on paths that do not write it. NVIDIA exposes this aggressively: ordinary hand draws can
        // poison the hand depth buffer, which then breaks water/fog/particles and produces odd lens
        // clipping. Preserve vanilla depth for every normal path; the cleanup branch overwrites it
        // with the sampled pre-hand world depth and returns.
        String restoreBranch = "\n    gl_FragDepth = gl_FragCoord.z;\n"
                + "    if (tacz_DepthRestoreMode != 0) {\n"
                + "        if (tacz_DepthRestoreMode == 2) {\n"
                + "            vec2 tacz_apertureUv = gl_FragCoord.xy / max(vec2(textureSize(tacz_ApertureDepthSampler, 0)), vec2(1.0));\n"
                + "            vec2 tacz_postBodyUv = gl_FragCoord.xy / max(vec2(textureSize(tacz_PostBodyDepthSampler, 0)), vec2(1.0));\n"
                + "            float tacz_apertureDepth = texture(tacz_ApertureDepthSampler, tacz_apertureUv).r;\n"
                + "            float tacz_postBodyDepth = texture(tacz_PostBodyDepthSampler, tacz_postBodyUv).r;\n"
                + "            if (tacz_postBodyDepth != tacz_apertureDepth) {\n"
                + "                discard;\n"
                + "            }\n"
                + "        }\n"
                + "        vec2 tacz_depthSize = max(vec2(textureSize(depthtex2, 0)), vec2(1.0));\n"
                + "        vec2 tacz_depthUv = gl_FragCoord.xy / tacz_depthSize;\n"
                + "        gl_FragDepth = texture(depthtex2, tacz_depthUv).r;\n"
                + "        return;\n"
                + "    }\n";
        // Mode 1 keeps reticles inside the ocular. Mode 2 keeps viewmodel FX outside it; this is
        // used by both muzzle-flash layers after the cleanup draw restores ordinary world depth.
        String maskBranch = "\n    if (tacz_ScopeMaskMode != 0) {\n"
                + "        vec2 tacz_maskWorldUv = gl_FragCoord.xy / max(vec2(textureSize(depthtex2, 0)), vec2(1.0));\n"
                + "        vec2 tacz_maskApertureUv = gl_FragCoord.xy / max(vec2(textureSize(tacz_ApertureDepthSampler, 0)), vec2(1.0));\n"
                + "        float tacz_maskWorldDepth = texture(depthtex2, tacz_maskWorldUv).r;\n"
                + "        float tacz_maskApertureDepth = texture(tacz_ApertureDepthSampler, tacz_maskApertureUv).r;\n"
                + "        bool tacz_insideOcular = tacz_maskApertureDepth < tacz_maskWorldDepth - 1.0e-6;\n"
                + "        if ((tacz_ScopeMaskMode == 1 && !tacz_insideOcular)\n"
                + "                || (tacz_ScopeMaskMode == 2 && tacz_insideOcular)) {\n"
                + "            discard;\n"
                + "        }\n"
                + "    }\n";

        // Insert immediately before main, not before a pack's #extension directives.
        return source.substring(0, main) + declarations + source.substring(main, brace + 1)
                + restoreBranch + maskBranch + source.substring(brace + 1);
    }

    private static boolean isHandProgram(String name) {
        if (name == null) {
            return false;
        }
        // ShaderCreator#createShader receives ShaderKey#getName() (for example
        // hand_cutout / hand_translucent / hand_water_bright), not only the underlying
        // shader-pack ProgramId source name (gbuffers_hand / gbuffers_hand_water).
        // These are the only keys eligible for depth restore.
        return name.equals("gbuffers_hand")
                || name.equals("gbuffers_hand_water")
                || name.endsWith("/gbuffers_hand")
                || name.endsWith("/gbuffers_hand_water")
                || name.equals("hand")
                || name.startsWith("hand_");
    }
}
