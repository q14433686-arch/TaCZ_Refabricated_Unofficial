package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.render.scope.ScopePipRenderer;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.UUID;

/** Real XL50 against a striped backdrop. No test shader, fake mask, glReadPixels or glowing flag. */
final class ScopeLensSmoke {
    private static final Identifier SIGHT = Identifier.parse("tacz:scope_xl50");
    private record Scene(UUID cow, BlockPos aim, BlockPos wall) {}

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        Scene scene = world.getServer().computeOnServer(server -> {
            var p = world.getConnection().getServerPlayer();
            p.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(Identifier.parse("tacz:qr_24"))
                    .putAttachment(AttachmentType.SCOPE, SIGHT).setAmmoCount(3).build());
            var level = p.level();
            var aim = p.blockPosition().north(40).above(16);
            for (int i = 1; i <= 44; i++) {
                var at = BlockPos.containing(p.getEyePosition().lerp(Vec3.atCenterOf(aim), i/40.0));
                for (int x=-1; x<=1; x++) for (int y=-1; y<=1; y++) level.removeBlock(at.offset(x,y,0), false);
            }
            var cow = new Cow(EntityTypes.COW, level);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.snapTo(Vec3.atBottomCenterOf(aim));
            level.addFreshEntity(cow);
            for (int x=-8; x<=8; x++) for (int y=-7; y<=7; y++) level.setBlock(aim.offset(x,y,-4),
                    (x == 0 ? Blocks.WOOL.red() : Blocks.WOOL.blue()).defaultBlockState(), Block.UPDATE_ALL);
            return new Scene(cow.getUUID(), aim, BlockPos.containing(p.getEyePosition().lerp(Vec3.atCenterOf(aim), .5)));
        });
        var prior = ctx.computeOnClient(mc -> new boolean[]{RenderConfig.EXPERIMENTAL_SCOPE_LENS.get(),
                RenderConfig.SCOPE_THERMAL_ENABLE.get(), RenderConfig.SCOPE_PIP_ENABLE.get(), RenderConfig.SCOPE_PIP_RERENDER.get()});
        try {
            ctx.waitFor(mc -> mc.level.getEntity(scene.cow) != null && mc.player.getMainHandItem().getItem() instanceof IGun g
                    && SIGHT.equals(g.getAttachmentId(mc.player.getMainHandItem(), AttachmentType.SCOPE)));
            ctx.getInput().lookAt(scene.aim);
            ctx.getInput().holdKey(AimKey.AIM_KEY);
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1) > .99f);
            world.getConnection().waitForChunksRender();
            flags(ctx, false, false, false, false);
            var bare = capture(ctx, "classic_bare");
            flags(ctx, true, false, false, false);
            var optical = capture(ctx, "classic_optical");
            assertOptics(bare, optical);
            flags(ctx, true, true, false, false);
            var hot = capture(ctx, "classic_hot");
            wall(ctx, world, scene, true);
            var behind = capture(ctx, "classic_wall");
            wall(ctx, world, scene, false);
            var restored = capture(ctx, "classic_restored");

            flags(ctx, false, false, true, false);
            ctx.waitFor(mc -> ScopePipRenderer.suppressesWorldFovZoom());
            var pipBare = capture(ctx, "pip_bare");
            flags(ctx, true, false, true, false);
            var pipOptics = capture(ctx, "pip_optical");
            assertOptics(pipBare, pipOptics);
            flags(ctx, true, true, true, false);
            var pipHot = capture(ctx, "pip_hot");
            wall(ctx, world, scene, true);
            var pipWall = capture(ctx, "pip_wall");
            wall(ctx, world, scene, false);
            var pipRestored = capture(ctx, "pip_restored");
            blacklistCow(ctx, world);
            var pipVeto = capture(ctx, "pip_tagged");
            flags(ctx, true, true, false, false);
            var veto = capture(ctx, "classic_tagged");
            int classic = heatPixels(hot, behind, restored, veto);
            int pip = heatPixels(pipHot, pipWall, pipRestored, pipVeto);
            GunMod.LOGGER.info("PORT_LENS: XL50 restored {} classic / {} PIP heat pixels; wall and live tag veto cold", classic, pip);
        } finally {
            ctx.getInput().releaseKey(AimKey.AIM_KEY);
            flags(ctx, prior[0], prior[1], prior[2], prior[3]);
            world.getServer().runOnServer(s -> { var e = world.getConnection().getServerPlayer().level().getEntity(scene.cow); if (e != null) e.discard(); });
        }
    }

    private static void flags(ClientGameTestContext ctx, boolean lens, boolean heat, boolean pip, boolean rerender) {
        ctx.runOnClient(mc -> {
            RenderConfig.EXPERIMENTAL_SCOPE_LENS.set(lens);
            RenderConfig.SCOPE_THERMAL_ENABLE.set(heat);
            RenderConfig.SCOPE_PIP_ENABLE.set(pip);
            RenderConfig.SCOPE_PIP_RERENDER.set(rerender);
        });
    }

    private static void wall(ClientGameTestContext ctx, TestSingleplayerContext world, Scene scene, boolean place) {
        world.getServer().runOnServer(s -> {
            var level = world.getConnection().getServerPlayer().level();
            for (int x=-2; x<=2; x++) for (int y=-2; y<=2; y++) level.setBlock(scene.wall.offset(x,y,0),
                    (place ? Blocks.STONE : Blocks.AIR).defaultBlockState(), Block.UPDATE_ALL);
        });
        ctx.waitFor(mc -> mc.level.getBlockState(scene.wall).is(place ? Blocks.STONE : Blocks.AIR));
        world.getConnection().waitForChunksRender();
    }

    private static void blacklistCow(ClientGameTestContext ctx, TestSingleplayerContext world) {
        // This is Loom's disposable test gameDir. Use the same real external-pack reload as a player;
        // put the cow on BOTH lists to prove that the veto wins (not cached entity classification).
        var reloaded = world.getServer().computeOnServer(s -> {
            var pack = Files.createTempDirectory(FabricLoader.getInstance().getGameDir().resolve("tacz"), "lens-smoke-");
            Files.writeString(pack.resolve("gunpack.meta.json"), "{\"namespace\":\"lens_smoke\",\"dependencies\":{\"minecraft\":\"[26.3]\"}}");
            var tags = Files.createDirectories(pack.resolve("data/tacz/tags/entity_type/thermal_imaging"));
            for (String list : new String[]{"whitelist", "blacklist"}) Files.writeString(tags.resolve(list+".json"), "{\"values\":[\"minecraft:cow\"]}");
            var packs = s.getPackRepository();
            packs.reload();
            return s.reloadResources(packs.getSelectedIds());
        });
        ctx.waitFor(mc -> reloaded.isDone(), 600);
        reloaded.join();
        ctx.waitFor(mc -> {
            var type = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.COW);
            return type.is(TagKey.create(Registries.ENTITY_TYPE, Identifier.parse("tacz:thermal_imaging/blacklist")))
                    && type.is(TagKey.create(Registries.ENTITY_TYPE, Identifier.parse("tacz:thermal_imaging/whitelist")));
        }, 600);
    }

    private static BufferedImage capture(ClientGameTestContext ctx, String name) {
        ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1) > .99f);
        ctx.waitTicks(12);
        try { return ImageIO.read(ctx.takeScreenshot("tacz_lens_" + name).toFile()); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static void assertOptics(BufferedImage bare, BufferedImage optical) {
        int w=bare.getWidth(), h=bare.getHeight(), radius=h/8;
        int red=0, kept=0, darkened=0, outside=0, unchanged=0;
        for (int y=h/2-radius; y<h/2+radius; y++) for (int x=0; x<w; x++) {
            int a=bare.getRGB(x,y), b=optical.getRGB(x,y);
            if (Math.abs(x-w/2) < radius) {
                if (stripe(a)) { red++; if (stripe(b)) kept++; }
                if (max(a)-min(a)>24 && max(a)-max(b)>6 && max(b)>max(a)*.5) darkened++;
            } else if (Math.abs(x-w/2)>w/3 && max(a)-min(a)>20) {
                outside++;
                if (Math.abs(r(a)-r(b))+Math.abs(g(a)-g(b))+Math.abs(b(a)-b(b))<12) unchanged++;
            }
        }
        if (red<20 || kept<.88*red || darkened<150 || outside<200 || unchanged<.88*outside)
            throw new AssertionError("XL50 optics: stripe="+kept+"/"+red+", darkened="+darkened+", exterior="+unchanged+"/"+outside);
    }

    private static int heatPixels(BufferedImage hot, BufferedImage wall, BufferedImage restored, BufferedImage veto) {
        int w=hot.getWidth(), h=hot.getHeight(), radius=h/9, n=0, leaked=0;
        for (int y=h/2-radius; y<h/2+radius; y++) for (int x=w/2-radius; x<w/2+radius; x++) {
            int a=hot.getRGB(x,y), d=veto.getRGB(x,y);
            if (bright(a) && bright(restored.getRGB(x,y)) && cold(d)) {
                n++;
                if (!cold(wall.getRGB(x,y))) leaked++;
            }
        }
        if (n < 40 || leaked > .02*n) throw new AssertionError("XL50 geometry did not survive/occlude: restored="+n+", not cold behind wall="+leaked);
        return n;
    }

    private static int r(int p) { return p>>16 & 255; }
    private static int g(int p) { return p>>8 & 255; }
    private static int b(int p) { return p & 255; }
    private static int max(int p) { return Math.max(r(p),Math.max(g(p),b(p))); }
    private static int min(int p) { return Math.min(r(p),Math.min(g(p),b(p))); }
    private static boolean bright(int p) { return min(p)>180 && max(p)-min(p)<4; }
    private static boolean cold(int p) { return max(p)<96 && max(p)-min(p)<4; }
    private static boolean stripe(int p) { return r(p)>45 && r(p)>2*g(p) && r(p)>2*b(p); }
}
