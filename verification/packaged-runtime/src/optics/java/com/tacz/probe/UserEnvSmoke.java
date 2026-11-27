package com.tacz.probe;

import com.google.gson.Gson;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.render.scope.ThermalReadback;
import com.tacz.guns.client.render.scope.UserEnvReadback;
import com.tacz.guns.client.resource.pojo.display.attachment.ScopeLensConfig;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Reproduces the 2026-11-26 user report on the production JAR, in the reported combination:
 * Iris 1.10.7 + Sodium 0.8.12 + Complementary Unbound r5.8.1, default client config, and the
 * UNMODIFIED default-pack {@code scope_xl50} (authored THERMAL_WHITE_HOT lens).
 *
 * <ol>
 *   <li>Iris on: authored thermal must reach the final frame; authored lens edge (vignette) must
 *       darken pixels relative to the same lens with edge effects removed.</li>
 *   <li>Iris getVertexFormat redirect live: TaCZ clones must keep the declared vanilla format.</li>
 *   <li>Shader pack switched off WHILE aiming (the reported crash): the client must keep rendering,
 *       and no cached TaCZ pipeline may still carry Iris-only attributes.</li>
 *   <li>Iris off: the same thermal and edge checks on the vanilla path.</li>
 * </ol>
 * Every stage is attempted and reported; the suite fails at the end if any stage failed.
 */
public final class UserEnvSmoke extends PlayerProbe {
    private static final Identifier OPTIC = Identifier.parse("tacz:scope_xl50");
    private static final Identifier NORMAL_LENS_SOURCE = Identifier.parse("tacz:scope_c79");

    @Override void exercise(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var server = world.getServer();
        var base = server.computeOnServer(s -> {
            var p = s.getPlayerList().getPlayers().getFirst();
            var at = new BlockPos(p.blockPosition().getX(), 240, p.blockPosition().getZ());
            p.level().setBlock(at.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            for (int x = -46; x <= 46; x++) for (int y = -29; y <= 29; y++)
                p.level().setBlock(at.offset(x, y, 40), ((x + y) % 2 == 0 ? Blocks.ORANGE_CONCRETE : Blocks.LIGHT_BLUE_CONCRETE).defaultBlockState(), Block.UPDATE_CLIENTS);
            p.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(Identifier.parse("tacz:m700"))
                    .putAttachment(AttachmentType.SCOPE, OPTIC).setAmmoCount(3).build());
            return at;
        });
        server.runCommand("time set 6000");
        server.runCommand("weather clear");
        server.runCommand("tp @a " + (base.getX() + .5) + " " + base.getY() + " " + (base.getZ() + .5));
        ctx.waitFor(mc -> mc.player.onGround() && mc.player.blockPosition().equals(base)
                && mc.player.getMainHandItem().getItem() instanceof IGun gun && OPTIC.equals(gun.getAttachmentId(mc.player.getMainHandItem(), AttachmentType.SCOPE)));
        turn(ctx, 0, 0);
        world.getClientWorld().waitForChunksRender();
        ctx.waitTicks(30);

        String env = ctx.computeOnClient(mc -> {
            var loader = FabricLoader.getInstance();
            String iris = loader.getModContainer("iris").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("none");
            String sodium = loader.getModContainer("sodium").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("none");
            String pack;
            try { pack = String.valueOf(Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName").invoke(null)); }
            catch (ReflectiveOperationException e) { pack = "<" + e.getClass().getSimpleName() + ">"; }
            var lens = TimelessAPI.getClientAttachmentIndex(OPTIC).orElseThrow().getScopeLens();
            return "iris=" + iris + " sodium=" + sodium + " pack=" + pack + " active=" + IrisCompat.isUsingRenderPack()
                    + " pip=" + RenderConfig.SCOPE_PIP_ENABLE.get() + " allowShaderPip=" + RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.get()
                    + " lensFeature=" + RenderConfig.EXPERIMENTAL_SCOPE_LENS.get() + " thermalFeature=" + RenderConfig.SCOPE_THERMAL_ENABLE.get()
                    + " mask=" + RenderConfig.SCOPE_MASK_ENABLE.get() + " optic=" + OPTIC + " authoredMode=" + lens.getVisionMode()
                    + " vignette=" + lens.getVignetteStrength() + "/" + lens.getVignetteWidth();
        });
        System.out.println("PORT_USER_ENV: " + env);
        if (!ctx.computeOnClient(mc -> IrisCompat.isUsingRenderPack())) throw new AssertionError("Shader pack is not active: " + env);

        List<String> errors = new ArrayList<>();
        try {
            String format = ctx.computeOnClient(mc -> UserEnvReadback.formatContract());
            System.out.println("PORT_USER_FORMAT: " + format);
        } catch (AssertionError | RuntimeException e) {
            errors.add("format: " + e.getMessage());
            System.out.println("PORT_USER_FORMAT: FAILED " + e.getMessage());
        }

        try {
            ctx.getInput().holdKey(AimKey.AIM_KEY);
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(0) > .999f);
            ctx.waitTicks(10);
            stage(errors, "iris", () -> lensChecks(ctx, world, base, "iris"));

            // The reported crash: switch the pack off through Iris itself while aiming.
            ctx.runOnClient(mc -> setShaders(false));
            ctx.waitTicks(40);
            ThermalFrames.frame(ctx, "user_after_toggle");
            boolean stillActive = ctx.computeOnClient(mc -> IrisCompat.isUsingRenderPack());
            if (stillActive) errors.add("toggle: pack still active after setShadersEnabledAndApply(false)");
            try {
                String scan = ctx.computeOnClient(mc -> UserEnvReadback.scanPipelines());
                System.out.println("PORT_USER_SHADER_TOGGLE: survived 40 ADS ticks after disabling the pack; " + scan);
            } catch (AssertionError | RuntimeException e) {
                errors.add("toggle: " + e.getMessage());
                System.out.println("PORT_USER_SHADER_TOGGLE: FAILED " + e.getMessage());
            }
            ctx.waitTicks(10);
            stage(errors, "vanilla", () -> lensChecks(ctx, world, base, "vanilla"));
        } finally {
            ctx.getInput().releaseKey(AimKey.AIM_KEY);
            ThermalFrames.kill(ctx, world);
        }
        if (!errors.isEmpty()) throw new AssertionError("User environment reproduction: " + errors);
    }

    private static void stage(List<String> errors, String label, Runnable body) {
        try { body.run(); }
        catch (AssertionError | RuntimeException e) { errors.add(label + ": " + e.getMessage()); }
    }

    /** Authored thermal on the final frame, then authored edge effects versus the same lens without them. */
    private static void lensChecks(ClientGameTestContext ctx, TestSingleplayerContext world, BlockPos base, String label) {
        var problems = new ArrayList<String>();
        ThermalFrames.kill(ctx, world);
        ctx.waitTicks(6);
        var empty = ThermalFrames.frame(ctx, "user_" + label + "_empty");
        ThermalFrames.summon(ctx, world, base, "slime", "Size:2,NoAI:1b,NoGravity:1b,Silent:1b");
        ctx.waitTicks(6);
        ctx.runOnClient(mc -> UserEnvReadback.startTrace());
        ctx.waitTicks(2);
        String trace = ctx.computeOnClient(mc -> UserEnvReadback.stopTrace()
                + " || " + UserEnvReadback.modelLens(TimelessAPI.getClientAttachmentIndex(OPTIC).orElseThrow()));
        System.out.println("PORT_USER_TRACE: " + label + " " + trace);
        if (!trace.contains("sameAsDisplay=true")) problems.add("rendering model lost the authored scope lens (" + trace.substring(trace.indexOf("||")) + ")");
        String state = ctx.computeOnClient(mc -> UserEnvReadback.state());
        GunMod.LOGGER.info("USER_DIAG {} {}", label, state);
        var hot = ThermalFrames.frame(ctx, "user_" + label + "_thermal");
        int white = ThermalFrames.white(hot), whiteEmpty = ThermalFrames.white(empty);
        String heat;
        try {
            var pixels = ThermalReadback.read(ctx);
            heat = "centralHot=" + pixels.centralHot();
            if (pixels.centralHot() < 20) problems.add("heat mask has no central body (" + pixels + ")");
        } catch (AssertionError | RuntimeException e) {
            heat = "unavailable";
            problems.add("heat attachment: " + e.getMessage());
        }
        if (white < 10 || white <= whiteEmpty + 5) problems.add("final frame shows no white-hot body: white=" + white + " empty=" + whiteEmpty);
        System.out.println("PORT_USER_THERMAL: " + label + " white=" + white + " emptyWhite=" + whiteEmpty + " " + heat
                + (problems.isEmpty() ? " passed" : " FAILED " + problems) + " state={" + state + "}");

        // Edge: the authored NORMAL lens of scope_c79 (vignette 1 / 0.35) on the same optic, versus that
        // lens with distortion, aberration and vignette removed. Only edge terms differ between frames.
        var edgeProblems = new ArrayList<String>();
        var model = ctx.computeOnClient(mc -> TimelessAPI.getClientAttachmentIndex(OPTIC).orElseThrow().getAttachmentModel());
        var authored = ctx.computeOnClient(mc -> TimelessAPI.getClientAttachmentIndex(OPTIC).orElseThrow().getScopeLens());
        var normal = ctx.computeOnClient(mc -> TimelessAPI.getClientAttachmentIndex(NORMAL_LENS_SOURCE).orElseThrow().getScopeLens());
        var tree = new Gson().toJsonTree(normal).getAsJsonObject();
        tree.addProperty("distortion", 0); tree.addProperty("aberration", 0); tree.addProperty("vignette_strength", 0);
        var flat = new Gson().fromJson(tree, ScopeLensConfig.class);
        flat.init();
        BufferedImage on, off;
        try {
            ctx.runOnClient(mc -> model.setScopeLens(normal));
            ctx.waitTicks(6);
            on = ThermalFrames.frame(ctx, "user_" + label + "_edge_on");
            ctx.runOnClient(mc -> model.setScopeLens(flat));
            ctx.waitTicks(6);
            off = ThermalFrames.frame(ctx, "user_" + label + "_edge_off");
        } finally {
            ctx.runOnClient(mc -> model.setScopeLens(authored));
            ctx.waitTicks(4);
        }
        int darker = 0, brighter = 0;
        for (int y = 0; y < on.getHeight(); y++) for (int x = 0; x < on.getWidth(); x++) {
            int d = luma(off.getRGB(x, y)) - luma(on.getRGB(x, y));
            if (d > 25) darker++;
            else if (d < -25) brighter++;
        }
        if (darker < 50 || darker < 3 * brighter) edgeProblems.add("no edge darkening: darker=" + darker + " brighter=" + brighter);
        System.out.println("PORT_USER_LENS_EDGE: " + label + " darker=" + darker + " brighter=" + brighter
                + (edgeProblems.isEmpty() ? " passed" : " FAILED " + edgeProblems));
        problems.addAll(edgeProblems);
        if (!problems.isEmpty()) throw new AssertionError(problems.toString());
    }

    private static int luma(int rgb) {
        return (int) (0.2126 * (rgb >> 16 & 255) + 0.7152 * (rgb >> 8 & 255) + 0.0722 * (rgb & 255));
    }

    private static void setShaders(boolean enabled) {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            Object config = api.getMethod("getConfig").invoke(instance);
            Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig")
                    .getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, enabled);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Iris API toggle unavailable", e);
        }
    }
}
