package com.tacz.guns.client.render.scope;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders ocular geometry into the offscreen RGBA8 mask FBO at the phase boundary.
 *
 * <p>The mask is a binary image: R=1 where the ocular lens covers the pixel, R=0 elsewhere.
 * The green channel carries the ADS aiming progress for edge softening.
 * Scope body and reticle shaders sample this texture for per-pixel discard.</p>
 *
 * <h2>Why raw OpenGL instead of RenderType</h2>
 * The mask renders to a custom FBO that is NOT the main framebuffer.
 * Vanilla's {@code RenderType} system always renders to whatever FBO is currently bound,
 * and we cannot easily redirect it. Raw GL gives us full control over the FBO target.</p>
 *
 * <h2>Coordinate space</h2>
 * Vertices are collected during model submission with the ModelView already baked in
 * (identical to how 26.2's ScopeMaskRenderer works). At render time, only the vanilla
 * Projection matrix is needed — ModelView is identity.
 */
@Environment(EnvType.CLIENT)
public final class ScopeMaskRenderer {

    // ── Collected geometry ─────────────────────────────────────────────
    public record Entry(Matrix4f pose, List<BedrockCube> cubes) {
        public Entry {
            pose = new Matrix4f(pose);       // defensive copy
            cubes = List.copyOf(cubes);
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    // ── GPU resources for the mask draw ────────────────────────────────
    private static int shaderProgram;
    private static int vao;
    private static int vbo;
    private static boolean initialized;
    private static boolean failed;
    private static boolean loggedSuccess;
    private static boolean loggedEmpty;

    // ── Hand pass tracking ─────────────────────────────────────────────
    private static boolean inHandPass;

    // ── Inline shaders (position-only, RGBA output) ────────────────────
    private static final String VERTEX_SHADER = """
            #version 330 core
            uniform mat4 ProjMat;
            in vec3 Position;
            void main() {
                gl_Position = ProjMat * vec4(Position, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core
            uniform vec4 ColorModulator;
            out vec4 fragColor;
            void main() {
                fragColor = ColorModulator;
            }
            """;

    private ScopeMaskRenderer() {}

    // ── Public API ─────────────────────────────────────────────────────

    public static void setInHandPass(boolean value) { inHandPass = value; }
    public static boolean isInHandPass() { return inHandPass; }

    /** Register ocular geometry for this frame. Called during model submission. */
    public static void addEntry(Matrix4f pose, List<BedrockCube> cubes) {
        if (cubes != null && !cubes.isEmpty()) {
            ENTRIES.add(new Entry(pose, cubes));
            if (!loggedSuccess) {
                GunMod.LOGGER.info("[TACZ Scope] addEntry: {} cubes registered", cubes.size());
            }
        }
    }

    /**
     * Called at the phase boundary (before executeSolid / solid batch draw).
     * Renders all collected ocular geometry into the mask FBO.
     */
    public static void renderAtPhaseBoundary() {
        if (!inHandPass) return;

        if (!RenderConfig.SCOPE_MASK_ENABLE.get()) {
            ENTRIES.clear();
            return;
        }

        GunMod.LOGGER.info("[TACZ Scope] renderAtPhaseBoundary: {} entries, maskEnable={}",
                ENTRIES.size(), RenderConfig.SCOPE_MASK_ENABLE.get());

        try {
            if (failed) { ENTRIES.clear(); return; }
            if (!ScopeMaskTarget.ensureReady()) { ENTRIES.clear(); return; }
            if (!ensureInitialized()) { ENTRIES.clear(); return; }

            if (ENTRIES.isEmpty()) {
                if (!loggedEmpty) {
                    loggedEmpty = true;
                    GunMod.LOGGER.warn("[TACZ Scope] Mask enabled but no ocular geometry registered this frame.");
                }
                clearMask();
            } else {
                renderMask();
            }
        } catch (Exception e) {
            failed = true;
            GunMod.LOGGER.error("[TACZ Scope] Mask render failed; feature disabled.", e);
        } finally {
            ENTRIES.clear();
        }
    }

    // ── Implementation ─────────────────────────────────────────────────

    private static boolean ensureInitialized() {
        if (initialized) return true;

        // Compile shader
        shaderProgram = compileProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (shaderProgram <= 0) { failed = true; return false; }

        // Create VAO + VBO
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        // Position attribute (location 0, vec3)
        int posLoc = GL20.glGetAttribLocation(shaderProgram, "Position");
        if (posLoc >= 0) {
            GL20.glEnableVertexAttribArray(posLoc);
            GL20.glVertexAttribPointer(posLoc, 3, GL11.GL_FLOAT, false, 12, 0);
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

        initialized = true;
        return true;
    }

    private static void clearMask() {
        int prevFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevViewport[] = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ScopeMaskTarget.fbo());
        GL11.glViewport(0, 0, ScopeMaskTarget.width(), ScopeMaskTarget.height());
        GL11.glClearColor(0, 0, 0, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GL20.glUseProgram(prevProgram);
        GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
    }

    private static void renderMask() {
        // Build vertex data
        float[] vertices = buildVertexData();
        if (vertices == null || vertices.length == 0) {
            clearMask();
            return;
        }

        // Save GL state
        int prevFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevViewport[] = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        try {
            // Bind mask FBO and clear
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ScopeMaskTarget.fbo());
            GL11.glViewport(0, 0, ScopeMaskTarget.width(), ScopeMaskTarget.height());
            GL11.glClearColor(0, 0, 0, 1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            // Set up shader
            GL20.glUseProgram(shaderProgram);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);

            // Set uniforms
            // ProjMat: vanilla's current projection matrix.
            // Vertices are already in camera space (ModelView baked in during submission),
            // so only the projection matrix is needed.
            // 26.1.2's RenderSystem has no public getProjectionMatrix(); access internal field.
            int projLoc = GL20.glGetUniformLocation(shaderProgram, "ProjMat");
            if (projLoc >= 0) {
                float[] proj = getProjectionMatrix();
                java.nio.FloatBuffer fb = java.nio.ByteBuffer.allocateDirect(16 * 4)
                        .order(java.nio.ByteOrder.nativeOrder())
                        .asFloatBuffer();
                fb.put(proj);
                fb.rewind();
                GL20.glUniformMatrix4fv(projLoc, false, fb);
            }

            // ColorModulator: R=1 (mask), G=aimingProgress, B=1, A=1
            float progress = ScopeMaskState.currentAimingProgress();
            int colorLoc = GL20.glGetUniformLocation(shaderProgram, "ColorModulator");
            if (colorLoc >= 0) {
                GL20.glUniform4f(colorLoc, 1.0f, progress, 1.0f, 1.0f);
            }

            // Upload and draw
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            ByteBuffer buffer = ByteBuffer.allocateDirect(vertices.length * 4)
                    .order(ByteOrder.nativeOrder());
            FloatBuffer floatBuffer = buffer.asFloatBuffer();
            floatBuffer.put(vertices);
            floatBuffer.flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, floatBuffer, GL15.GL_STREAM_DRAW);

            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertices.length / 3);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(0);

            if (!loggedSuccess) {
                loggedSuccess = true;
                GunMod.LOGGER.info("[TACZ Scope] Mask rendered: {} vertices from {} entries.",
                        vertices.length / 3, ENTRIES.size());
            }
        } finally {
            // Restore GL state
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            GL20.glUseProgram(prevProgram);
            GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
            if (prevDepthTest) GL11.glEnable(GL11.GL_DEPTH_TEST);
            if (prevBlend) GL11.glEnable(GL11.GL_BLEND);
            if (prevCull) GL11.glEnable(GL11.GL_CULL_FACE);
        }
    }

    /**
     * Builds a flat float array of triangle vertices from all collected entries.
     * Each vertex is 3 floats (x, y, z). Quads are expanded to two triangles.
     */
    private static float[] buildVertexData() {
        // First pass: count vertices
        int totalVertices = 0;
        for (Entry entry : ENTRIES) {
            for (BedrockCube cube : entry.cubes()) {
                for (var polygon : cube.getPolygons()) {
                    if (polygon != null) totalVertices += polygon.vertices.length;
                }
            }
        }
        if (totalVertices == 0) return null;

        // Quads (4 verts) → triangles (6 verts)
        // Each polygon has 4 vertices (quad), expanded to 2 triangles = 6 vertices
        int totalFloats = 0;
        for (Entry entry : ENTRIES) {
            for (BedrockCube cube : entry.cubes()) {
                for (var polygon : cube.getPolygons()) {
                    if (polygon != null && polygon.vertices.length == 4) {
                        totalFloats += 6 * 3; // 6 vertices * 3 floats
                    }
                }
            }
        }

        float[] data = new float[totalFloats];
        int idx = 0;
        for (Entry entry : ENTRIES) {
            Matrix4f pose = entry.pose();
            for (BedrockCube cube : entry.cubes()) {
                for (var polygon : cube.getPolygons()) {
                    if (polygon == null || polygon.vertices.length != 4) continue;
                    // Transform all 4 vertices
                    float[] x = new float[4], y = new float[4], z = new float[4];
                    for (int i = 0; i < 4; i++) {
                        var v = polygon.vertices[i];
                        float px = v.pos.x() / 16.0f;
                        float py = v.pos.y() / 16.0f;
                        float pz = v.pos.z() / 16.0f;
                        org.joml.Vector4f vec = new org.joml.Vector4f(px, py, pz, 1.0f);
                        vec.mul(pose);
                        x[i] = vec.x(); y[i] = vec.y(); z[i] = vec.z();
                    }
                    // Quad → two triangles: (0,1,2) and (0,2,3)
                    // Triangle 1
                    data[idx++] = x[0]; data[idx++] = y[0]; data[idx++] = z[0];
                    data[idx++] = x[1]; data[idx++] = y[1]; data[idx++] = z[1];
                    data[idx++] = x[2]; data[idx++] = y[2]; data[idx++] = z[2];
                    // Triangle 2
                    data[idx++] = x[0]; data[idx++] = y[0]; data[idx++] = z[0];
                    data[idx++] = x[2]; data[idx++] = y[2]; data[idx++] = z[2];
                    data[idx++] = x[3]; data[idx++] = y[3]; data[idx++] = z[3];
                }
            }
        }
        return data;
    }

    // ── Shader compilation ─────────────────────────────────────────────

    /**
     * Retrieves the current projection matrix from RenderSystem via reflection.
     * 26.1.2's RenderSystem stores it as a private static Matrix4fStack field "projectionMatrix".
     */
    private static float[] getProjectionMatrix() {
        try {
            java.lang.reflect.Field f = com.mojang.blaze3d.systems.RenderSystem.class
                    .getDeclaredField("projectionMatrix");
            f.setAccessible(true);
            Object mat = f.get(null);
            // Matrix4fStack extends Matrix4f — call get(FloatBuffer) via reflection
            java.nio.FloatBuffer fb = java.nio.ByteBuffer.allocateDirect(16 * 4)
                    .order(java.nio.ByteOrder.nativeOrder())
                    .asFloatBuffer();
            java.lang.reflect.Method m = mat.getClass().getMethod("get", java.nio.FloatBuffer.class);
            m.invoke(mat, fb);
            fb.rewind();
            float[] arr = new float[16];
            fb.get(arr);
            return arr;
        } catch (Exception e) {
            GunMod.LOGGER.warn("[TACZ Scope] Could not read projection matrix, using identity", e);
            return new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
        }
    }


    private static int compileProgram(String vertexSource, String fragmentSource) {
        int vs = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
        if (vs <= 0) return 0;
        int fs = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
        if (fs <= 0) { GL20.glDeleteShader(vs); return 0; }

        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        // Bind Position to location 0 before linking
        GL20.glBindAttribLocation(program, 0, "Position");
        GL20.glLinkProgram(program);

        int[] status = new int[1];
        GL20.glGetProgramiv(program, GL20.GL_LINK_STATUS, status);
        if (status[0] == 0) {
            String log = GL20.glGetProgramInfoLog(program);
            GunMod.LOGGER.error("[TACZ Scope] Mask shader link failed:\n{}", log);
            GL20.glDeleteProgram(program);
            program = 0;
        }

        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        int[] status = new int[1];
        GL20.glGetShaderiv(shader, GL20.GL_COMPILE_STATUS, status);
        if (status[0] == 0) {
            String log = GL20.glGetShaderInfoLog(shader);
            String typeName = type == GL20.GL_VERTEX_SHADER ? "vertex" : "fragment";
            GunMod.LOGGER.error("[TACZ Scope] Mask {} shader compile failed:\n{}", typeName, log);
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }
}
