package com.tacz.guns.test;

import cn.sh1rocu.simplebedrockmodel.api.event.RenderTickEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.model.bedrock.BedrockCubePerFace;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import com.tacz.guns.client.resource.pojo.model.FaceUVsItem;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Same tracked ItemEntities/camera for A and B. Count actual deferred vertex writes in a separate
 * replay of vanilla's item-entity renderer; never wrap or time that replay in the frame sample. */
final class DroppedGunSmoke {
    private static final Identifier AK = Identifier.parse("tacz:ak47"), DISPLAY = Identifier.parse("port_test:drop_no_lod");
    private static final Identifier NO_SLOT = Identifier.parse("port_test:hand_materials");
    private static final Identifier AUTHORED = Identifier.parse("tacz:ak47_display");
    private static final Identifier SLOT = Identifier.parse("port_test:textures/gun/rack_mesh.png");
    private static final int DEFAULT = RenderConfig.DEFAULT_DROPPED_GUN_SPRITE_DISTANCE;
    private static final int FRAMES = 32;
    private static long[] times;
    private static int samples;

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var opt = RenderConfig.DROPPED_GUN_SPRITE_DISTANCE;
        int previous = ctx.computeOnClient(mc -> opt.get());
        var facing = ctx.computeOnClient(mc -> new float[]{mc.player.getYRot(), mc.player.getXRot()});
        var held = world.getServer().computeOnServer(s -> {
            var player = world.getConnection().getServerPlayer();
            var old = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return old;
        });
        try {
            ctx.getInput().lookAt(0, 0);
            ctx.waitFor(mc -> mc.player.getMainHandItem().isEmpty());
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> {
                checkDisplays();
                RenderTickEvent.EVENT.register(e -> {
                    if (times != null && e.phase == RenderTickEvent.Phase.START && samples < times.length)
                        times[samples++] = mc.getFrameTimeNs(); // last completed renderFrame, excludes present
                });
            });
            // Warm high explicitly for the baseline; don't mistake the existing async placeholder for a fast model.
            ctx.waitFor(mc -> TimelessAPI.getGunDisplay(DISPLAY, AK).orElseThrow().getExternalModel(false) != null
                    && TimelessAPI.getGunDisplay(NO_SLOT, AK).orElseThrow().getExternalModel(false) != null
                    && TimelessAPI.getGunDisplay(AUTHORED, AK).orElseThrow().getExternalModel(true) != null);
            if (!Boolean.getBoolean("tacz.test.iris")) {
                scene(ctx, world, 3, 1);
                scene(ctx, world, 3, 16);
                scene(ctx, world, 12, 1);
            }
            scene(ctx, world, 12, 16);
        } finally {
            ctx.runOnClient(mc -> { times = null; opt.set(previous); });
            ctx.getInput().lookAt(facing[0], facing[1]);
            world.getServer().runOnServer(s -> world.getConnection().getServerPlayer().setItemInHand(InteractionHand.MAIN_HAND, held));
        }
    }

    private static void scene(ClientGameTestContext ctx, TestSingleplayerContext world, int distance, int count) {
        var eye = ctx.computeOnClient(mc -> mc.gameRenderer.mainCamera().position());
        var ids = world.getServer().computeOnServer(s -> {
            var level = world.getConnection().getServerPlayer().level();
            var result = new ArrayList<UUID>();
            for (int i = 0; i < count; i++) {
                var stack = GunItemBuilder.create().setId(AK).build();
                ((IGun)stack.getItem()).setGunDisplayId(stack, DISPLAY);
                var drop = new ItemEntity(level, eye.x + (count == 1 ? 0 : (i % 4 - 1.5) * .95),
                        eye.y - .8, eye.z + distance + i / 4 * .85, stack, 0, 0, 0);
                drop.setNoGravity(true);
                drop.setNeverPickUp();
                level.addFreshEntity(drop);
                result.add(drop.getUUID());
            }
            return result;
        });
        try {
            ctx.waitFor(mc -> ids.stream().allMatch(id -> mc.level.getEntity(id) instanceof ItemEntity drop
                    && drop.getItem().getItem() instanceof IGun g && DISPLAY.equals(g.getGunDisplayId(drop.getItem()))));
            BufferedImage full = null;
            for (int cutoff : distance == 3 ? List.of(-1, DEFAULT, 0) : List.of(-1, DEFAULT)) {
                ctx.runOnClient(mc -> RenderConfig.DROPPED_GUN_SPRITE_DISTANCE.set(cutoff));
                ctx.waitTicks(8);
                boolean icons = cutoff >= 0 && distance >= cutoff;
                var geometry = ctx.computeOnClient(mc -> count(mc, ids));
                if (icons ? geometry.sprites != count || geometry.nodes != count || geometry.vertices.n != 4 * count
                        : geometry.sprites != 0 || geometry.vertices.n < 1000 * count)
                    throw new AssertionError("Wrong drop path for " + distance + "/" + cutoff + ": " + geometry);
                if (distance == 3 && count == 1 && cutoff == 0) ctx.runOnClient(mc -> checkContexts(mc, (ItemEntity)mc.level.getEntity(ids.getFirst())));
                ctx.runOnClient(mc -> { samples = 0; times = new long[FRAMES]; });
                ctx.waitFor(mc -> samples == FRAMES);
                var sorted = ctx.computeOnClient(mc -> { var a = times; times = null; Arrays.sort(a); return a; });
                GunMod.LOGGER.info("PORT_DROPPED_GUN: {} drops at {} blocks, cutoff={}, {} geometry submissions / {} vertices; {} Minecraft renderFrame samples median={} us p95={} us",
                        count, distance, cutoff, geometry.nodes, geometry.vertices.n, FRAMES, sorted[FRAMES / 2] / 1000, sorted[FRAMES * 95 / 100] / 1000);
                if (distance == 12 && count == 16) {
                    var image = SnowstormMaterialSmoke.read(ctx.takeScreenshot("tacz_dropped_guns_" + (icons ? "on" : "off")));
                    if (icons) {
                        int pixels = newCyanPixels(full, image);
                        if (pixels < 50) throw new AssertionError("Distant proxies not visible in the real world frame: " + pixels);
                        GunMod.LOGGER.info("PORT_DROPPED_GUN: {} new cyan slot pixels in the pile (off/on, same camera)", pixels);
                    } else full = image;
                }
            }
        } finally {
            world.getServer().runOnServer(s -> {
                var level = world.getConnection().getServerPlayer().level();
                for (var id : ids) if (level.getEntity(id) instanceof ItemEntity drop) drop.discard();
            });
            ctx.waitFor(mc -> ids.stream().noneMatch(id -> mc.level.getEntity(id) != null));
        }
    }

    private static int newCyanPixels(BufferedImage before, BufferedImage after) {
        int n = 0;
        for (int y = after.getHeight() / 4; y < after.getHeight() * 3 / 4; y++)
            for (int x = after.getWidth() / 4; x < after.getWidth() * 3 / 4; x++) {
                int c = chroma(after.getRGB(x, y));
                if (c > 40 && c - chroma(before.getRGB(x, y)) > 35) n++;
            }
        return n;
    }
    private static int chroma(int rgb) { return Math.min(rgb >> 8 & 255, rgb & 255) - (rgb >> 16 & 255); }

    private static void checkDisplays() {
        // Explicit single-texel UV is valid, just like a nonzero UV. Only the four omitted
        // faces of this cube may be skipped; its public six-direction layout must not change.
        var faces = ClientAssetsManager.GSON.fromJson("""
                {"south":{"uv":[0,0],"uv_size":[0,0]},"up":{"uv":[0,0],"uv_size":[4,4]}}
                """, FaceUVsItem.class);
        var cube = new BedrockCubePerFace(1, 1, 1, 4, 4, 4, 0, 16, 16, faces);
        var counter = new Counter();
        cube.compile(new PoseStack().last(), counter, 0, 0, 1, 1, 1, 1);
        if (cube.getPolygons().length != 6 || counter.n != 8) throw new AssertionError("Per-face cube wrote " + counter.n + " vertices");
        if (!SLOT.equals(TimelessAPI.getGunDisplay(DISPLAY, AK).orElseThrow().getMissingLodDropTexture())
                || TimelessAPI.getGunDisplay(Identifier.parse("port_test:rack_mesh"), AK).orElseThrow().getMissingLodDropTexture() != null
                || TimelessAPI.getGunDisplay(NO_SLOT, AK).orElseThrow().getMissingLodDropTexture() != null)
            throw new AssertionError("Missing slot, no LOD and authored LOD must be distinct");
        for (String lod : List.of("{}", "{\"model\":\"port_test:missing\",\"texture\":\"port_test:gun/rack_mesh\"}",
                "{\"model\":\"port_test:rack_mesh\",\"texture\":\"port_test:missing\"}")) {
            for (String slot : List.of("gun/rack_mesh", "missing_slot")) {
                var pojo = ClientAssetsManager.GSON.fromJson("{\"lod\":" + lod + ",\"slot\":\"port_test:" + slot + "\"}", GunDisplay.class);
                pojo.init();
                var icon = GunDisplayInstance.create(DISPLAY, pojo).getMissingLodDropTexture();
                if (slot.equals("gun/rack_mesh") ? !SLOT.equals(icon) : icon != null)
                    throw new AssertionError("Wrong broken-resource fallback: " + lod + ", " + slot);
            }
        }
    }

    /** Install a client-only overlay in the disposable Loom run dir before an already scheduled
     * resource reload. Same display ID gains a real LOD; the old presence memo must not win. */
    static void acrossReload(ClientGameTestContext ctx, Runnable reload) {
        var old = ctx.computeOnClient(mc -> TimelessAPI.getGunDisplay(DISPLAY, AK).orElseThrow());
        ctx.runOnClient(mc -> {
            if (!SLOT.equals(old.getMissingLodDropTexture())) throw new AssertionError("Expected no-LOD display before reload");
        });
        try {
            // GunPackLoader searches directories lexically; this test-only pack precedes the fixture ZIP.
            var pack = Files.createTempDirectory(FabricLoader.getInstance().getGameDir().resolve("tacz"), "000-drop-reload-");
            Files.writeString(pack.resolve("gunpack.meta.json"), "{\"namespace\":\"port_test\",\"dependencies\":{\"minecraft\":\"[26.3]\"}}");
            Files.writeString(Files.createDirectories(pack.resolve("assets/port_test/display/guns")).resolve("drop_no_lod.json"), """
                    {"model":"tacz:gun/ak47_geo","texture":"tacz:gun/uv/ak47","slot":"port_test:gun/rack_mesh",
                     "lod":{"model":"tacz:gun/lod/ak47","texture":"tacz:gun/lod/ak47"}}
                    """);
        } catch (IOException e) { throw new UncheckedIOException(e); }
        reload.run();
        var low = Identifier.parse("tacz:textures/gun/lod/ak47.png");
        ctx.waitFor(mc -> {
            var fresh = TimelessAPI.getGunDisplay(DISPLAY, AK).orElseThrow();
            var rig = fresh.getExternalModel(true);
            return fresh != old && fresh.getMissingLodDropTexture() == null && rig != null && rig.getRight().equals(low);
        });
        GunMod.LOGGER.info("PORT_DROPPED_GUN: reloaded same display from no LOD to authored 3D LOD");
    }

    private static void checkContexts(Minecraft mc, ItemEntity drop) {
        // Same underlying gun at cutoff 0: the actual stack display decides, not a gun-ID cache.
        for (var id : List.of(DISPLAY, AUTHORED, NO_SLOT)) {
            var stack = drop.getItem().copy();
            ((IGun)stack.getItem()).setGunDisplayId(stack, id);
            var state = new ItemStackRenderState();
            mc.getItemModelResolver().updateForNonLiving(state, stack, ItemDisplayContext.GROUND, drop);
            var out = new CountingCollector();
            state.submit(new PoseStack(), out, 0x900070, OverlayTexture.NO_OVERLAY, 0);
            out.flush();
            if (id.equals(DISPLAY) ? out.sprites != 1 || out.vertices.n != 4 || out.vertices.light != 0x900070
                    || out.vertices.overlay != OverlayTexture.NO_OVERLAY : out.sprites != 0 || out.vertices.n <= 4)
                throw new AssertionError("Wrong display/light/overlay for " + id + ": " + out);
        }
        for (var context : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIXED,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.GROUND)) {
            var state = new ItemStackRenderState();
            // Including GROUND with no ItemEntity: previews/mod renderers may use that context too.
            mc.getItemModelResolver().updateForTopItem(state, drop.getItem(), context, mc.level,
                    context == ItemDisplayContext.GROUND ? null : drop, 1);
            var out = new CountingCollector();
            state.submit(new PoseStack(), out, 0x900070, OverlayTexture.NO_OVERLAY, 0);
            out.flush();
            if (out.sprites != 0 || (context == ItemDisplayContext.GUI ? out.vertices.n != 4 : out.vertices.n < 1000))
                throw new AssertionError("Drop fallback leaked into " + context + ": " + out);
        }
    }

    private static CountingCollector count(Minecraft mc, List<UUID> ids) {
        var out = new CountingCollector();
        var camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        for (var id : ids) {
            var drop = (ItemEntity)mc.level.getEntity(id);
            var renderer = (ItemEntityRenderer)(Object)mc.getEntityRenderDispatcher().getRenderer(drop);
            var state = renderer.createRenderState();
            renderer.extractRenderState(drop, state, 1);
            var pose = new PoseStack();
            pose.translate(drop.position().subtract(camera.pos));
            renderer.submit(state, pose, out, camera);
        }
        out.flush();
        return out;
    }

    private static final class CountingCollector extends SubmitNodeStorage {
        private final List<Runnable> callbacks = new ArrayList<>();
        private final Counter vertices = new Counter();
        private int nodes, sprites;
        @Override public void submitCustomGeometry(PoseStack pose, RenderType material, CustomGeometryRenderer draw) {
            nodes++;
            if (material == RenderTypes.entityCutout(SLOT)) sprites++;
            var snapshot = pose.last().copy();
            callbacks.add(() -> draw.render(snapshot, vertices));
        }
        void flush() { callbacks.forEach(Runnable::run); callbacks.clear(); }
        @Override public String toString() { return nodes + " nodes, " + sprites + " sprites, " + vertices.n + " vertices"; }
    }

    private static final class Counter implements VertexConsumer {
        int n, light, overlay;
        public VertexConsumer addVertex(float x, float y, float z) { n++; return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        public VertexConsumer setColor(int argb) { return this; }
        public VertexConsumer setUv(float u, float v) { return this; }
        public VertexConsumer setUv1(int u, int v) { overlay = v << 16 | u; return this; }
        public VertexConsumer setUv2(int u, int v) { light = v << 16 | u; return this; }
        public VertexConsumer setUv3(float u, float v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
        public VertexConsumer setLineWidth(float w) { return this; }
    }
}
