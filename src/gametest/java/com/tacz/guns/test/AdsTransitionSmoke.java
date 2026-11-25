package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.render.scope.ScopeDepthCopyState;
import com.tacz.guns.client.render.scope.ScopeLensFrame;
import com.tacz.guns.client.render.scope.ScopePipRenderState;
import com.tacz.guns.client.render.scope.ScopePipRerender;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/** Sample an actual moving optic against two colours of terrain. Compare immediately before
 * the lens draw, after that draw, and after the physical rim/reticle on the SAME frame. */
public final class AdsTransitionSmoke {
    public static boolean running;
    private static boolean shader;
    private static Shot pending;

    private static final class Shot {
        final String name;
        final float lo, hi;
        float progress, zoom;
        double worldShare;
        float[] bounds;
        boolean[] mask;
        CompletableFuture<BufferedImage> before, lens, display;
        Shot(String name, float lo, float hi) { this.name = name; this.lo = lo; this.hi = hi; }
    }

    public static void beforeLens(Minecraft mc, boolean irisFinal) {
        var s = pending;
        if (s == null || s.before != null || irisFinal != shader || ScopePipRerender.isInsideScopeLevelRender()) return;
        float p = IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if (p < s.lo || p > s.hi) return;
        var main = mc.getMainRenderTarget();
        s.progress = p;
        s.zoom = ScopePipRenderState.currentZoom();
        s.worldShare = RenderConfig.SCOPE_PIP_WORLD_ZOOM_SHARE.get();
        s.bounds = ScopeLensFrame.currentBounds();
        if (ScopeDepthCopyState.hasMaskCycleThisFrame())
            s.mask = ApertureReadback.rectangle(0, 0, main.width, main.height, main.height, main.width);
        s.before = SensorLayers.readColor(main);
    }

    public static void afterLens() {
        var s = pending;
        if (s != null && s.before != null && s.lens == null)
            s.lens = SensorLayers.readColor(Minecraft.getInstance().getMainRenderTarget());
    }

    public static void afterInk() {
        var s = pending;
        if (s != null && s.lens != null && s.display == null)
            s.display = SensorLayers.readColor(Minecraft.getInstance().getMainRenderTarget());
    }

    private record Center(double x, double y) {}
    private enum Expect { REPROJECT, PICTURE, CLASSIC, BELOW_FLOOR, NO_MASK }

    private static Center sample(ClientGameTestContext ctx, String name, float lo, float hi, Expect expect, ArrayList<String> errors) {
        var s = new Shot(name, lo, hi);
        ctx.runOnClient(mc -> pending = s);
        ctx.waitFor(mc -> s.display != null && s.display.isDone() && s.lens.isDone() && s.before.isDone(), 80);
        ctx.runOnClient(mc -> pending = null);
        var pre = s.before.join(); var post = s.lens.join(); var finalFrame = s.display.join();
        var dir = ctx.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots/ads-transition"));
        try {
            Files.createDirectories(dir);
            ImageIO.write(finalFrame, "png", dir.resolve(name + ".png").toFile());
        } catch (IOException e) { throw new UncheckedIOException(e); }
        int w = pre.getWidth(), h = pre.getHeight();
        int area=0, leak=0, swapped=0, visible=0, changed=0, matched=0, candidates=0, coated=0;
        double sx=0, sy=0;
        float cx=s.bounds == null ? 0 : (.5f+.25f*(s.bounds[0]+s.bounds[2]))*w;
        float cy=s.bounds == null ? 0 : (.5f-.25f*(s.bounds[1]+s.bounds[3]))*h;
        // The centred, steady image retains the camera optical axis (including authored sight offsets).
        if (s.progress == 1) { cx=w*.5f; cy=h*.5f; }
        double z=(1+(s.zoom-1)*s.progress) / (1+(Math.pow(s.zoom,s.worldShare)-1)*s.progress);
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) {
            int a=pre.getRGB(x,y), b=post.getRGB(x,y), c=finalFrame.getRGB(x,y);
            if (s.mask == null || !s.mask[y*w+x]) { if (a!=b) leak++; continue; }
            area++; sx+=x+.5; sy+=y+.5;
            if (a!=b) changed++;
            // Opposite wall tiles, not tint, depth, diagnostic paint, or a reticle draw.
            if (tile(a)*tile(b)<0) { swapped++; if (tile(c)==tile(b)) visible++; }
            // Classic = no local zoom; real scene colours survive the authored tint and final ink.
            if (tile(a)!=0 && tile(a)==tile(b) && tile(c)==tile(b) && Math.abs((a>>>16&255)-(b>>>16&255))>=4) coated++;
            if (expect != Expect.REPROJECT || s.bounds==null) continue;
            // Compare unambiguous bilinear source footprints inside the live ocular. Demand agreement
            // on pixels where an identity paste would give the *wrong* world tile.
            int u=(int)Math.floor(cx+(x+.5-cx)/z-.5), v=(int)Math.floor(cy+(y+.5-cy)/z-.5);
            if (u<0 || v<0 || u+1>=w || v+1>=h) continue;
            int t=tile(pre.getRGB(u,v));
            if (t*tile(a)>=0) continue;
            boolean clean=true;
            for (int j=0;j<=1;j++) for (int i=0;i<=1;i++)
                clean &= s.mask[(v+j)*w+u+i] && tile(pre.getRGB(u+i,v+j))==t;
            if (clean) { candidates++; if (tile(b)==t) matched++; }
        }
        var center=new Center(sx/Math.max(1,area), sy/Math.max(1,area));
        GunMod.LOGGER.info("PORT_OPTIC: {} p={} depth={} center={}/{} share={} changed={} switchedTiles={} stillVisible={} movingUV={}/{} coated={} outside={}",
                name,s.progress,area,center.x,center.y,s.worldShare,changed,swapped,visible,matched,candidates,coated,leak);
        boolean ok = switch (expect) {
            case NO_MASK -> s.mask==null && changed==0;
            case BELOW_FLOOR -> area>=30 && changed==0;
            case CLASSIC -> area>=30 && coated>=Math.max(8,area/20);
            case PICTURE, REPROJECT -> area>=30 && swapped>=8 && visible>=Math.max(8,swapped/10)
                    && (expect!=Expect.REPROJECT || candidates>=8 && matched>=.90*candidates);
        };
        if (leak!=0 || !ok) errors.add(name+" (see frame counts)");
        return center;
    }

    private static int tile(int rgb) {
        int d=(rgb>>>16 & 255)-(rgb & 255);
        return d>32 ? 1 : d< -32 ? -1 : 0;
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var optic=Identifier.parse("tacz:scope_standard_8x");
        var server=world.getServer();
        var base=server.computeOnServer(s -> {
            var p=s.getPlayerList().getPlayers().getFirst();
            var at=new BlockPos(p.blockPosition().getX(), 240, p.blockPosition().getZ());
            p.level().setBlock(at.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            for (int x=-46;x<=46;x++) for (int y=-29;y<=29;y++)
                p.level().setBlock(at.offset(x,y,-40), ((x+y)%2==0 ? Blocks.ORANGE_CONCRETE : Blocks.LIGHT_BLUE_CONCRETE).defaultBlockState(),Block.UPDATE_CLIENTS);
            p.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(Identifier.parse("tacz:m700"))
                    .putAttachment(AttachmentType.SCOPE,optic).setAmmoCount(3).build());
            return at;
        });
        server.runCommand("time set 6000"); server.runCommand("weather clear");
        server.runCommand("tp @a "+(base.getX()+.5)+" "+base.getY()+" "+(base.getZ()+.5));
        ctx.waitFor(mc -> mc.player.onGround() && mc.player.blockPosition().equals(base) && mc.player.getMainHandItem().getItem() instanceof IGun gun
                && optic.equals(gun.getAttachmentId(mc.player.getMainHandItem(),AttachmentType.SCOPE)));
        ctx.getInput().lookAt(180,0); world.getClientLevel().waitForChunksRender(); ctx.waitTicks(30);
        var old=ctx.computeOnClient(mc -> new boolean[]{RenderConfig.SCOPE_PIP_ENABLE.get(),RenderConfig.SCOPE_PIP_RERENDER.get(),
                RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.get(),RenderConfig.EXPERIMENTAL_SCOPE_LENS.get(),mc.options.bobView().get(), RenderConfig.SCOPE_MASK_ENABLE.get()});
        double floor=ctx.computeOnClient(mc -> RenderConfig.SCOPE_PIP_MIN_AIMING_PROGRESS.get());
        double share=ctx.computeOnClient(mc -> RenderConfig.SCOPE_PIP_WORLD_ZOOM_SHARE.get());
        var errors=new ArrayList<String>();
        try {
            ctx.runOnClient(mc -> {
                running=true; shader=IrisCompat.isUsingRenderPack(); mc.options.bobView().set(false);
                RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.set(true); RenderConfig.SCOPE_PIP_ENABLE.set(true);
            });
            for (boolean narrow : new boolean[]{false,true}) {
                ctx.runOnClient(mc -> { RenderConfig.SCOPE_PIP_RERENDER.set(narrow); RenderConfig.EXPERIMENTAL_SCOPE_LENS.set(narrow); });
                String mode=narrow ? "narrow_" : "reproject_";
                ctx.getInput().holdKey(AimKey.AIM_KEY);
                var expect=narrow ? Expect.PICTURE : Expect.REPROJECT;
                var up=sample(ctx,mode+"raise",.18f,.34f,expect,errors);
                sample(ctx,mode+"mid",.53f,.78f,Expect.PICTURE,errors);
                ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(0)==1); ctx.waitTicks(6);
                var full=sample(ctx,mode+"steady",1,1,expect,errors);
                // Leave the colour target allocated at full ADS but withhold this frame's aperture.
                ctx.runOnClient(mc -> RenderConfig.SCOPE_MASK_ENABLE.set(false)); ctx.waitTicks(2);
                sample(ctx,mode+"missing_mask",1,1,Expect.NO_MASK,errors);
                ctx.runOnClient(mc -> { RenderConfig.SCOPE_MASK_ENABLE.set(true); RenderConfig.SCOPE_PIP_MIN_AIMING_PROGRESS.set(.85); });
                ctx.waitTicks(2);
                ctx.getInput().releaseKey(AimKey.AIM_KEY);
                sample(ctx,mode+"below_floor",.60f,.78f,Expect.BELOW_FLOOR,errors);
                ctx.runOnClient(mc -> {
                    RenderConfig.SCOPE_PIP_MIN_AIMING_PROGRESS.set(floor);
                    if (!narrow) RenderConfig.SCOPE_PIP_WORLD_ZOOM_SHARE.set(.5); // check split zoom on the way down
                });
                var down=sample(ctx,mode+"lower",.15f,.25f,expect,errors);
                sample(ctx,mode+"hip",0,0,Expect.NO_MASK,errors);
                ctx.runOnClient(mc -> RenderConfig.SCOPE_PIP_WORLD_ZOOM_SHARE.set(share));
                if (Math.hypot(up.x-full.x,up.y-full.y)<5 || Math.hypot(down.x-full.x,down.y-full.y)<5)
                    errors.add(mode+"ocular did not move");
            }
            // With PIP disabled there is no second zoom: an authored classic lens must keep
            // its separate prediction/identity gate, but needn't wait for completed ADS either.
            ctx.runOnClient(mc -> { RenderConfig.SCOPE_PIP_ENABLE.set(false); RenderConfig.SCOPE_PIP_RERENDER.set(false); });
            ctx.getInput().holdKey(AimKey.AIM_KEY);
            sample(ctx,"classic_raise",.18f,.34f,Expect.CLASSIC,errors);
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(0)==1);
            ctx.getInput().releaseKey(AimKey.AIM_KEY);
            sample(ctx,"classic_lower",.18f,.34f,Expect.CLASSIC,errors);
        } finally {
            ctx.getInput().releaseKey(AimKey.AIM_KEY);
            ctx.runOnClient(mc -> {
                running=false; pending=null;
                RenderConfig.SCOPE_PIP_ENABLE.set(old[0]); RenderConfig.SCOPE_PIP_RERENDER.set(old[1]);
                RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.set(old[2]); RenderConfig.EXPERIMENTAL_SCOPE_LENS.set(old[3]); mc.options.bobView().set(old[4]);
                RenderConfig.SCOPE_MASK_ENABLE.set(old[5]); RenderConfig.SCOPE_PIP_MIN_AIMING_PROGRESS.set(floor);
                RenderConfig.SCOPE_PIP_WORLD_ZOOM_SHARE.set(share);
            });
        }
        if (!errors.isEmpty()) throw new AssertionError("Moving depth-aperture picture: "+errors);
    }
    private AdsTransitionSmoke() {}
}
