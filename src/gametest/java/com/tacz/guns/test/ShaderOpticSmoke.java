package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.render.scope.ScopeLensFrame;
import com.tacz.guns.client.render.scope.ScopePipRenderState;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.VirtualOemAttachment;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

/** The two reported failures have different oracles. No shaderpack or edited optic assets in the fixture. */
public final class ShaderOpticSmoke {
    public static Identifier withoutIron;
    public static UUID poseTarget;
    public static float renderedYaw = Float.NaN;
    public static boolean withoutReticle, withoutCoating; // test mixin only
    private static final AttachmentType SLOT = AttachmentType.SCOPE;
    private static final Set<String> SMALL_MARKS = Set.of("scope_vudu",
            "sight_deltapoint_rifle", "sight_deltapoint_pistol", "sight_rmr_dot");
    private static final Set<String> ETCHED_SCALES = Set.of("scope_qmk152", "scope_elcan_4x", "scope_lpvo_1_6", "scope_standard_8x");
    private static final String PACK = "ComplementaryReimagined_r5.8.1.zip";
    private record Mount(Identifier optic, Identifier host, boolean builtin) {}
    private record View(int slot, float zoom, int locator) {}

    static void requirePack(String expected) {
        try {
            var iris = Class.forName("net.irisshaders.iris.Iris");
            var name = iris.getMethod("getCurrentPackName").invoke(null);
            if (!expected.equals(name) || !IrisCompat.hasActiveIrisPack()) throw new AssertionError("Inactive or wrong shader: " + name);
            GunMod.LOGGER.info("OPTIC_PACK: {} active, {}", name, com.mojang.blaze3d.systems.RenderSystem.getDevice());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static List<Mount> mounts(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(s -> {
            var guns = TimelessAPI.getAllCommonGunIndex().stream().map(Map.Entry::getKey)
                    .filter(k -> k.getNamespace().equals("tacz")).sorted(Comparator.comparing(k -> switch (k.getPath()) {
                        case "m249" -> "0"; case "m700" -> "1"; default -> "2" + k;
                    })).toList();
            var mounts = new ArrayList<Mount>();
            for (var e : TimelessAPI.getAllCommonAttachmentIndex()) {
                var id = e.getKey();
                if (!id.getNamespace().equals("tacz") || e.getValue().getType() != SLOT) continue;
                var part = AttachmentItemBuilder.create().setId(id).build();
                boolean builtin = e.getValue().getPojo().isBuiltinAttachment();
                var host = guns.stream().filter(k -> {
                    if (builtin) return VirtualOemAttachment.isAllowedVirtualOem(k, id, SLOT);
                    var stack = GunItemBuilder.create().setId(k).build();
                    var gun = (IGun)stack.getItem();
                    return gun.allowAttachmentType(stack, SLOT) && gun.allowAttachment(stack, part);
                }).findFirst().orElseThrow(() -> new AssertionError("No host for " + id));
                mounts.add(new Mount(id, host, builtin));
            }
            mounts.sort(Comparator.comparing(m -> m.optic.toString()));
            return mounts;
        });
    }

    private static List<View> views(ClientGameTestContext ctx, Mount m) {
        return ctx.computeOnClient(mc -> {
            var display = TimelessAPI.getClientAttachmentIndex(m.optic).orElseThrow();
            var result = new ArrayList<View>();
            var zoom = Objects.requireNonNull(display.getZoom());
            var views = display.getViews();
            for (int i=0; i<zoom.length; i++) result.add(new View(i, zoom[i], views[i % views.length]));
            return result;
        });
    }

    /** The actual held stack must have settled on both threads before sending aim input. */
    private static void equip(ClientGameTestContext ctx, TestSingleplayerContext world, Mount m, View v) {
        ctx.getInput().releaseKey(AimKey.AIM_KEY);
        ctx.waitTicks(3);
        world.getServer().runOnServer(s -> {
            var builder = GunItemBuilder.create().setId(m.host).setAmmoCount(3);
            if (m.builtin) {
                var virtual = new EnumMap<AttachmentType,Identifier>(AttachmentType.class);
                virtual.put(SLOT,m.optic);
                builder.putAllVirtualAttachment(virtual);
            } else builder.putAttachment(SLOT,m.optic);
            var stack=builder.build(); var gun=(IGun)stack.getItem();
            if (!m.optic.equals(gun.getAttachmentId(stack,SLOT))) throw new AssertionError("Server rejected " + m);
            var tag=gun.getAttachmentTag(stack,SLOT);
            AttachmentItemDataAccessor.setZoomNumberToTag(tag,v.slot);
            gun.setAttachmentTag(stack,SLOT,tag);
            s.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND,stack);
        });
        ctx.waitFor(mc -> {
            var stack = mc.player.getMainHandItem();
            return stack.getItem() instanceof IGun gun && m.host.equals(gun.getGunId(stack))
                    && m.optic.equals(gun.getAttachmentId(stack,SLOT))
                    && AttachmentItemDataAccessor.getZoomNumberFromTag(gun.getAttachmentTag(stack,SLOT))==v.slot;
        });
        ctx.waitTicks(26);
        ctx.getInput().holdKey(AimKey.AIM_KEY);
        ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1)>.995f);
        ctx.waitTicks(10);
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world, String suite) {
        ctx.runOnClient(mc -> { requirePack(PACK); mc.options.bobView().set(false); });
        if (suite.equals("readouts")) {
            world.getClientLevel().waitForChunksRender(); ctx.waitTicks(25);
            RangefinderSmoke.run(ctx, world); ImpactMarkerSmoke.run(ctx, world);
            GunMod.LOGGER.info("OPTIC_COMPLETE: readouts UH-1 and XM157");
            return;
        }
        var server=world.getServer(); // disposable game world; uniform blue wall makes an opaque red plate distinguishable from the scene
        server.runCommand("gamerule spawn_mobs false"); server.runCommand("kill @e[type=!minecraft:player]");
        server.runCommand("gamerule advance_time false"); server.runCommand("time set 6000"); server.runCommand("weather clear");
        var base=server.computeOnServer(s -> {
            var p=s.getPlayerList().getPlayers().getFirst(); var at=new BlockPos(p.blockPosition().getX(),240,p.blockPosition().getZ());
            var blue=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:light_blue_concrete"));
            p.level().setBlock(at.below(),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
            for (int x=-16;x<=16;x++) for(int y=-12;y<=12;y++) p.level().setBlock(at.offset(x,y,-45),blue.defaultBlockState(),Block.UPDATE_ALL);
            return at;
        });
        server.runCommand("tp @a " + (base.getX()+.5) + " " + base.getY() + " " + (base.getZ()+.5));
        ctx.waitFor(mc -> mc.player.blockPosition().equals(base)); ctx.getInput().lookAt(180,0);
        world.getClientLevel().waitForChunksRender();
        var all=mounts(world);
        if (!suite.startsWith("thermal")) backdrop(ctx, world, base, "light_blue");
        var failures=new ArrayList<String>(); int slots=0, count=0;
        try {
            boolean pipGallery = suite.equals("pip"), large = suite.startsWith("report-");
            if (pipGallery) ctx.runOnClient(mc -> {
                RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.set(true);
                RenderConfig.SCOPE_PIP_ENABLE.set(true);
            });
            if (large) ctx.getInput().resizeWindow(2560,1417);
            for (var m:all) {
                var views=views(ctx,m);
                slots+=views.size();
                String id=m.optic.getPath();
                boolean thermal=id.equals("scope_reap_ir") || id.equals("scope_xl50");
                boolean selected = switch (suite) {
                    case "thermal" -> thermal;
                    case "thermal-reap" -> id.equals("scope_reap_ir");
                    case "thermal-xl50", "thermal-narrow" -> id.equals("scope_xl50");
                    case "scopes" -> id.startsWith("scope_");
                    case "sights" -> !id.startsWith("scope_");
                    case "pip" -> true;
                    case "report-size" -> id.equals("scope_qmk152") || id.equals("scope_standard_8x") || id.equals("scope_lpvo_1_6");
                    case "report-marks" -> SMALL_MARKS.contains(id);
                    default -> throw new IllegalArgumentException("Unknown ADS suite " + suite);
                };
                if (!selected) continue;
                for (var v:views) {
                    if (large && id.equals("scope_lpvo_1_6") && v.zoom>2f) continue; // also verify its smallest etched scale
                    if (pipGallery && v.zoom < 4f) continue; // default PIP cutover
                    if (suite.equals("thermal-narrow") && v.slot != 0) continue;
                    count++; equip(ctx,world,m,v);
                    ctx.waitFor(mc -> Math.abs(ScopePipRenderState.currentZoom()-v.zoom)<.01);
                    if (pipGallery) ctx.waitFor(mc -> ScopePipRenderState.suppressesWorldFovZoom(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)));
                    if (suite.startsWith("thermal")) heat(ctx,world,base,m,v,failures,suite.equals("thermal-narrow"));
                    else gallery(ctx,world,base,m,v,thermal,large,failures);
                }
            }
            int expected = switch (suite) { case "scopes" -> 34; case "sights" -> 25; case "pip" -> 20; case "thermal", "report-size" -> 4; case "thermal-reap", "thermal-xl50" -> 2; case "report-marks" -> 5; case "thermal-narrow" -> 1; default -> throw new AssertionError(suite); };
            if (all.size()!=46 || slots!=59 || count!=expected) throw new AssertionError("Incomplete catalog "+all.size()+"/"+slots+"/"+count);
            if (!failures.isEmpty()) throw new AssertionError("ADS framebuffer failures: " + failures);
            GunMod.LOGGER.info("OPTIC_COMPLETE: {} {}/{} slots ({} indexed attachments)",suite,count,slots,all.size());
        } finally {
            ctx.runOnClient(mc -> { withoutCoating=withoutReticle=false; withoutIron=null; });
            ctx.getInput().releaseKey(AimKey.AIM_KEY);
        }
    }

    static BufferedImage frame(ClientGameTestContext ctx, String name) {
        try { return ImageIO.read(ctx.takeScreenshot("ads_"+name).toFile()); }
        catch(IOException e) { throw new UncheckedIOException(e); }
    }
    private static int r(int c) { return c>>>16&255; }
    private static int g(int c) { return c>>>8&255; }
    private static int b(int c) { return c&255; }
    private static boolean plate(int c) { return r(c)>g(c)+25 && r(c)>b(c)+15; }
    private static boolean hot(int c) { return Math.min(r(c),Math.min(g(c),b(c)))>180 && Math.max(r(c),Math.max(g(c),b(c)))-Math.min(r(c),Math.min(g(c),b(c)))<5; }
    private static boolean cold(int c) { return Math.max(r(c),Math.max(g(c),b(c)))<96 && Math.abs(r(c)-b(c))<5; }

    // Largest contiguous red square, not red pixel count: long legitimate red crosshairs must pass.
    private static int solidRed(BufferedImage img, int cx, int cy, int radius) {
        var line=new int[2*radius+1]; int maximum=0;
        for (int y=cy-radius;y<cy+radius;y++) {
            int diag=0;
            for(int i=1;i<line.length;i++) {
                int above=line[i]; line[i]=plate(img.getRGB(cx-radius+i-1,y)) ? 1+Math.min(diag,Math.min(above,line[i-1])) : 0;
                maximum=Math.max(maximum,line[i]); diag=above;
            }
        }
        return maximum;
    }
    private static void gallery(ClientGameTestContext ctx, TestSingleplayerContext world, BlockPos base, Mount m, View v, boolean thermal, boolean large, List<String> failures) {
        var key=m.optic.getPath()+"_"+v.slot;
        var bounds=ctx.computeOnClient(mc -> ScopeLensFrame.currentBounds());
        var img=frame(ctx,key);
        if (large && (img.getWidth()!=2560 || img.getHeight()!=1417)) throw new AssertionError("Wrong report-sized framebuffer");
        boolean iron=m.optic.getPath().startsWith("oem_sight_");
        ctx.runOnClient(mc -> { withoutReticle=true; withoutIron=iron?m.optic:null; }); ctx.waitTicks(3);
        var no=frame(ctx,key+(iron?"_no_iron":"_no_division"));
        BufferedImage bare=no;
        if (!iron && m.optic.getPath().startsWith("scope_")) {
            ctx.runOnClient(mc -> withoutCoating=true); ctx.waitTicks(3); bare=frame(ctx,key+"_no_division_or_coating");
        }
        ctx.runOnClient(mc -> { withoutReticle=withoutCoating=false; withoutIron=null; });
        BufferedImage alternate=null;
        if (iron) {
            // Mechanical posts/handles occupy real space, often over half the measuring square.
            // Change the actual scene instead of judging an iron sight as though it were glass.
            backdrop(ctx,world,base,"lime");
            try { alternate=frame(ctx,key+"_lime_background"); }
            finally { backdrop(ctx,world,base,"light_blue"); }
        }
        int w=img.getWidth(),h=img.getHeight(),cx=w/2,cy=h/2,rad=h/14;
        String failure="";
        if (bounds!=null) {
            cx=Math.round(w*(.5f+.25f*(bounds[0]+bounds[2]))); cy=Math.round(h*(.5f-.25f*(bounds[1]+bounds[3])));
            rad=Math.round(.17f*Math.min(w*(bounds[2]-bounds[0]),h*(bounds[3]-bounds[1])));
        }
        if (rad<3||cx-rad<0||cy-rad<0||cx+rad>w||cy+rad>h||(!iron && bounds==null)) failure="aperture";
        else {
            int changed=0,coating=0,background=0,material=0,scaleInk=0,colouredInk=0,ironScene=0,ironPost=0;
            for (int y=cy-rad;y<cy+rad;y++) for (int x=cx-rad;x<cx+rad;x++) {
                int a=img.getRGB(x,y),z=no.getRGB(x,y);
                if (Math.max(Math.abs(r(a)-r(z)),Math.max(Math.abs(g(a)-g(z)),Math.abs(b(a)-b(z))))>38) changed++;
                // A real chromatic stroke must introduce colour absent in the division-suppressed scene.
                int redChroma = (r(a)-g(a))-(r(z)-g(z)), greenChroma = (g(a)-b(a))-(g(z)-b(z));
                if ((r(a)>g(a)+12 && redChroma>50) || (g(a)>b(a)+12 && greenChroma>50)) colouredInk++;
                int d2=(x-cx)*(x-cx)+(y-cy)*(y-cy);
                if (d2>rad*rad*.04 && d2<rad*rad*.8 && b(z)-b(a)>45 && g(z)-g(a)>25 && !plate(a)) scaleInk++;
                if (alternate!=null) {
                    int green=alternate.getRGB(x,y);
                    if (Math.abs(x-cx)<=rad/2 && y>=cy-3*rad/4 && y<=cy+rad/4 && b(a)>r(a)+18
                            && g(green)>b(green)+18 && (g(green)-b(green))-(g(a)-b(a))>50) ironScene++;
                    if (Math.max(r(a),Math.max(g(a),b(a)))<100 && Math.max(r(green),Math.max(g(green),b(green)))<100
                            && b(z)>r(z)+18 && b(z)-b(a)>50) ironPost++;
                }
                int uncoated=bare.getRGB(x,y);
                if ((x-cx)*(x-cx)+(y-cy)*(y-cy)<rad*rad && Math.max(Math.abs(r(uncoated)-r(z)),
                        Math.max(Math.abs(g(uncoated)-g(z)),Math.abs(b(uncoated)-b(z))))>38) coating++;
                if (thermal ? cold(a) : b(a)>r(a)+18) background++;
                if (Math.max(r(a),Math.max(g(a),b(a)))<90) material++;
            }
            int red=solidRed(img,cx,cy,rad), without=solidRed(no,cx,cy,rad);
            if (red>Math.max(5,rad*.15)) {
                failure="red-plate";
                if (bounds != null) {
                    final int x0=cx-rad, y0=cy-rad, width=rad*2;
                    var mask=ctx.computeOnClient(mc -> ApertureReadback.region(x0,y0,width,h,w));
                    int inside=0, redInside=0, redOutside=0;
                    for (int y=0;y<width;y++) for (int x=0;x<width;x++) {
                        boolean through=mask[y*width+x]; if (through) inside++;
                        if (plate(img.getRGB(x0+x,y0+y))) { if (through) redInside++; else redOutside++; }
                    }
                    GunMod.LOGGER.info("OPTIC_TRACE: {} measured ocular={}/{} red inside/outside={}/{}; no division or coating red square={}",
                            key,inside,mask.length,redInside,redOutside,solidRed(bare,cx,cy,rad));
                }
            }
            if (iron) {
                if (ironScene<Math.max(25,rad*rad/6)) failure+=" no-scene-through-sightline";
                if (ironPost<15) failure+=" missing-physical-sight";
            } else if (background<rad*rad*2) failure+=" obstructed";
            if (coating>Math.max(8,.02*Math.PI*rad*rad)) failure+=" coating-in-aperture";
            if (ETCHED_SCALES.contains(m.optic.getPath()) && scaleInk<8) failure+=" missing-etched-scale";
            if (large && colouredInk==0) failure+=" missing-coloured-ink";
            if (iron ? changed<15 : changed<2) failure+=" missing-mark";
            GunMod.LOGGER.info("OPTIC_FRAME: {} {} {}x view={} centre={},{} r={} red={}/{} markDelta={} coatingDelta={} background={}/{} body={} scaleInk={} colouredInk={} ironScene={} ironPost={} {}",
                    key,m.host,v.zoom,v.locator,cx,cy,rad,red,without,changed,coating,background,4*rad*rad,material,scaleInk,colouredInk,ironScene,ironPost,failure.isEmpty()?"PASS":failure);
        }
        if (!failure.isEmpty()) failures.add(key+":"+failure);
    }

    private static void backdrop(ClientGameTestContext ctx, TestSingleplayerContext world, BlockPos base, String colour) {
        var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(Identifier.parse("minecraft:"+colour+"_concrete")).orElseThrow();
        // Keep the colour-swap control inside the nearest two chunks, even on the minimal
        // native client render distance. The thermal lane keeps its wall behind the 36-block target.
        var mid=base.north(16).above(1);
        world.getServer().runOnServer(s->{var level=s.getPlayerList().getPlayers().getFirst().level();
            for(int y=-6;y<=6;y++) for(int x=-6;x<=6;x++) level.setBlock(mid.offset(x,y,0),block.defaultBlockState(),Block.UPDATE_ALL);});
        ctx.waitFor(mc->mc.level.getBlockState(mid).is(block)); world.getClientLevel().waitForChunksRender(); ctx.waitTicks(6);
    }

    private static void heat(ClientGameTestContext ctx, TestSingleplayerContext world, BlockPos base, Mount m, View v, List<String> failures, boolean narrow) {
        var id=world.getServer().computeOnServer(s->{
            var level=s.getPlayerList().getPlayers().getFirst().level(); var slime=EntityType.SLIME.create(level,EntitySpawnReason.COMMAND);
            slime.setSize(3,true); slime.setNoAi(true); slime.setNoGravity(true); slime.snapTo(Vec3.atBottomCenterOf(base.north(16).above()));
            if (!level.addFreshEntity(slime)) throw new AssertionError("No slime"); return slime.getUUID();
        });
        ctx.waitFor(mc->mc.level.getEntity(id)!=null);
        try {
            for(boolean pip:narrow ? new boolean[]{true} : new boolean[]{false,true}) {
                ctx.runOnClient(mc -> { RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.set(true); RenderConfig.SCOPE_PIP_MIN_MAGNIFICATION.set(1d); RenderConfig.SCOPE_PIP_ENABLE.set(pip); RenderConfig.SCOPE_PIP_RERENDER.set(narrow); });
                ctx.waitFor(mc -> ScopePipRenderState.suppressesWorldFovZoom(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false))==pip);
                if (narrow) ctx.waitFor(mc -> com.tacz.guns.client.render.scope.ScopePipRerender.hasScene()
                        && Math.abs(mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.projectionMatrix.m11()
                                * Math.tan(Math.toRadians(mc.options.fov().get() * .5)) - 1) < .015, 600);
                for(int range:new int[]{16,36}) {
                    var at=base.north(range).above();
                    world.getServer().runOnServer(s->s.getPlayerList().getPlayers().getFirst().level().getEntity(id).snapTo(Vec3.atBottomCenterOf(at)));
                    ctx.getInput().lookAt(at); ctx.waitTicks(12);
                    var key=m.optic.getPath()+"_"+v.slot+(narrow?"_narrow_":pip?"_pip_":"_classic_")+range;
                    var wall=at.south(2);
                    // Both empty references have the *same* wall/background, camera, optic and pack
                    // as their populated counterpart. Never infer the target footprint from heat.
                    move(ctx,world,id,at.east(12)); setWall(ctx,world,wall,true);
                    var emptyWall=frame(ctx,key+"_wall_without_target");
                    var wallScene=SensorLayers.sample(ctx).sensor(); setWall(ctx,world,wall,false);
                    thermal(ctx,false); var emptyOptical=frame(ctx,key+"_optical_without_target");
                    move(ctx,world,id,at);
                    for(int angle:new int[]{0,45,90}) {
                        pose(ctx,world,id,angle);
                        var optical=frame(ctx,key+"_optical_"+angle);
                        thermal(ctx,true);
                        var image=frame(ctx,key+"_"+angle);
                        if (angle == 0) {
                            ctx.runOnClient(mc -> ScopeDepthProbe.next = key);
                            ctx.waitFor(mc -> ScopeDepthProbe.next == null);
                            GunMod.LOGGER.info("OPTIC_TRACE: {} exposed mask pixels={}", key, SensorLayers.sample(ctx).heatPixels());
                        }
                        float drawn=ctx.computeOnClient(mc->renderedYaw);
                        if (Math.abs(net.minecraft.util.Mth.wrapDegrees(drawn-angle))>1 || !Float.isFinite(drawn))
                            throw new AssertionError("Render state changed angle: "+drawn+" vs "+angle);
                        setWall(ctx,world,wall,true); var behind=frame(ctx,key+"_wall_"+angle);
                        var covered=SensorLayers.sample(ctx);
                        int h=image.getHeight(),w=image.getWidth();
                        int radius=Math.max(4,Math.min(h/40,(int)(h*.17*v.zoom/range)));
                        int n=0,lit=0,wallHot=0;
                        for(int y=h/2-radius;y<h/2+radius;y++) for(int x=w/2-radius;x<w/2+radius;x++) {
                            int b=behind.getRGB(x,y); if(hot(b))wallHot++; if(!cold(b))continue;
                            n++; if (hot(image.getRGB(x,y)))lit++;
                        }
                        var bounds=ctx.computeOnClient(mc->ScopeLensFrame.currentBounds());
                        // Compare the optically visible green body against the same empty blue scene.
                        // Exclude the physical rim/vignette and fixed display ink, NOT cold holes in the body.
                        int wr=Math.min(h/3,Math.round(.16f*Math.min(w*(bounds[2]-bounds[0]),h*(bounds[3]-bounds[1]))));
                        int visible=0,filled=0,leaks=0;
                        for(int y=h/2-wr; y<h/2+wr; y++) for(int x=w/2-wr; x<w/2+wr; x++) {
                            if (!cold(emptyWall.getRGB(x,y))) continue;
                            if (opticalSlime(optical,emptyOptical,x,y) && opticalSlime(optical,emptyOptical,x-1,y)
                                    && opticalSlime(optical,emptyOptical,x+1,y) && opticalSlime(optical,emptyOptical,x,y-1)
                                    && opticalSlime(optical,emptyOptical,x,y+1)) {
                                visible++; if (hot(image.getRGB(x,y))) filled++;
                            }
                            if (!hot(covered.sensor().getRGB(x,y))) continue;
                            boolean allCold=true;
                            for(int dy=-2;dy<=2 && allCold;dy++) for(int dx=-2;dx<=2;dx++)
                                if (!cold(wallScene.getRGB(x+dx,y+dy))) { allCold=false; break; }
                            if(allCold)leaks++;
                        }
                        boolean ok=n>=2*radius*radius && lit>=.83*n && visible>=2*radius*radius && filled>=.83*visible
                                && wallHot<=.16*4*radius*radius && leaks==0 && covered.heatPixels()==0;
                        GunMod.LOGGER.info("OPTIC_HEAT: {} @{} drawn={} face={}/{} opticalBody={}/{} wallBright={} wallLeak={} wallMask={} radius={} {}",
                                key,angle,drawn,lit,n,filled,visible,wallHot,leaks,covered.heatPixels(),radius,ok?"PASS":"FAIL");
                        if(!ok)failures.add(key+"@"+angle+":"+lit+"/"+n+" body="+filled+"/"+visible+" wallLeak="+leaks+" mask="+covered.heatPixels());
                        setWall(ctx,world,wall,false); thermal(ctx,false);
                    }
                    thermal(ctx,true);
                }
            }
            if (narrow) WideSceneSmoke.run(ctx, world, base, id);
        } finally {
            ctx.runOnClient(mc->{ poseTarget=null; RenderConfig.SCOPE_THERMAL_ENABLE.set(true); RenderConfig.SCOPE_PIP_ENABLE.set(false); RenderConfig.SCOPE_PIP_RERENDER.set(false); });
            world.getServer().runOnServer(s->s.getPlayerList().getPlayers().getFirst().level().getEntity(id).discard());
        }
    }
    private static boolean opticalSlime(BufferedImage target, BufferedImage empty, int x, int y) {
        int a=target.getRGB(x,y), z=empty.getRGB(x,y);
        return (g(a)-b(a))-(g(z)-b(z))>35;
    }
    private static void thermal(ClientGameTestContext ctx,boolean on) {
        ctx.runOnClient(mc->RenderConfig.SCOPE_THERMAL_ENABLE.set(on)); ctx.waitTicks(4);
    }
    private static void pose(ClientGameTestContext ctx, TestSingleplayerContext world, UUID id, int angle) {
        world.getServer().runOnServer(s->orient((LivingEntity)s.getPlayerList().getPlayers().getFirst().level().getEntity(id),angle));
        // No-AI mobs need not turn their body to their network head yaw while standing still.
        // Set both endpoints of the fixture pose and wait for the *rendered model*, not just the packet.
        ctx.runOnClient(mc->{ poseTarget=id; renderedYaw=Float.NaN; orient((LivingEntity)mc.level.getEntity(id),angle); });
        ctx.waitFor(mc->Float.isFinite(renderedYaw) && Math.abs(net.minecraft.util.Mth.wrapDegrees(renderedYaw-angle))<1);
        ctx.waitTicks(4);
    }
    private static void orient(LivingEntity e,int yaw) {
        e.setYRot(yaw); e.setYHeadRot(yaw); e.yHeadRotO=e.yHeadRot=e.yBodyRotO=e.yBodyRot=yaw;
    }
    static void move(ClientGameTestContext ctx,TestSingleplayerContext world,UUID id,BlockPos to) {
        world.getServer().runOnServer(s->s.getPlayerList().getPlayers().getFirst().level().getEntity(id).snapTo(Vec3.atBottomCenterOf(to)));
        ctx.waitFor(mc->mc.level.getEntity(id).position().distanceToSqr(Vec3.atBottomCenterOf(to))<.01);
        ctx.waitTicks(4);
    }
    private static void setWall(ClientGameTestContext ctx,TestSingleplayerContext world,BlockPos wall,boolean place) {
        world.getServer().runOnServer(s->{var level=s.getPlayerList().getPlayers().getFirst().level();
            for(int y=-2;y<=2;y++) for(int x=-2;x<=2;x++)level.setBlock(wall.offset(x,y,0),(place?Blocks.STONE:Blocks.AIR).defaultBlockState(),Block.UPDATE_ALL);});
        ctx.waitFor(mc->mc.level.getBlockState(wall).is(place?Blocks.STONE:Blocks.AIR));
        world.getClientLevel().waitForChunksRender(); ctx.waitTicks(4);
    }
}
