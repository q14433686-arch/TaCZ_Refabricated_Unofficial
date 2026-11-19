package com.tacz.guns.test;

import cn.sh1rocu.tacz.api.LogicalSide;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.particle.NativeMuzzleEffects;
import com.tacz.guns.client.particle.bedrock.particle.resource.ParticleDefinitionLoader;
import com.tacz.guns.client.particle.bedrock.particle.world.SnowstormMaterials;
import com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.test.mixin.ReimaginedShotControl;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL45C;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

/** A separate integrated game with the pinned, unmodified user shader, NOT the small Iris optical fixture.
 * Read final colour and Iris's actual shadowtex0. Never feed any readback into the product renderer. */
public final class ReimaginedSmoke {
    private static final Identifier SVD = Identifier.parse("tacz:svd"), AK = Identifier.parse("tacz:ak47");
    public static boolean shadowReference, pulse, bodyOnly;
    public static int shadowBodies;
    private static int tile, pulses;
    private static Object targets;

    public static void refreshPulse() {
        if (!pulse) return;
        var player = Minecraft.getInstance().player;
        ReimaginedShotControl.taczTest$shot(new GunFirePostEvent(player, player.getMainHandItem(), LogicalSide.CLIENT));
        pulses++;
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private static Object call(Object owner, String name) throws ReflectiveOperationException {
        var m = owner.getClass().getMethod(name); m.setAccessible(true); return m.invoke(owner);
    }
    private static void requirePack() {
        try {
            var iris = Class.forName("net.irisshaders.iris.Iris");
            var name = iris.getMethod("getCurrentPackName").invoke(null);
            if (!IrisCompat.isUsingRenderPack() || !"ComplementaryReimagined_r5.9.3.zip".equals(name)) throw new AssertionError("Not rendering Reimagined: " + name);
            var pipeline = call(iris.getMethod("getPipelineManager").invoke(null), "getPipelineNullable");
            var pack = ((Optional<?>)iris.getMethod("getCurrentPack").invoke(null)).orElseThrow();
            var values = call(call(pack,"getShaderPackOptions"),"getOptionValues");
            var setting = values.getClass().getMethod("getStringValueOrDefault",String.class).invoke(values,"COLORED_LIGHTING");
            boolean floodfill = false;
            for (Object img : (Collection<?>)field(pipeline,"customImages")) if ("floodfill_img".equals(call(img,"getName"))) floodfill = true;
            if (!System.getProperty("tacz.test.irisColor").equals(setting) || floodfill != "128".equals(setting)) throw new AssertionError("ACT config/runtime mismatch: " + setting + "/"+floodfill);
            targets = field(pipeline,"shadowRenderTargets");
            if (targets == null) throw new AssertionError("No real shadow map");
            var api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            GunMod.LOGGER.info("PORT_REIMAGINED: {} COLORED_LIGHTING={}, floodfill image={}, shadowtex0={}, Iris API 0.{}",name,setting,floodfill,call(targets,"getResolution"),api.getMethod("getMinorApiRevision").invoke(api.getMethod("getInstance").invoke(null)));
        } catch (ReflectiveOperationException e) { throw new AssertionError("Pinned Iris probe", e); }
    }
    private static float[] depth() {
        try {
            int n = (int)call(targets,"getResolution"); int id = (int)call(call(targets,"getDepthTexture"),"iris$getGlId");
            float[] a = new float[n*n];
            GL45C.glGetTextureImage(id,0,GL45C.GL_DEPTH_COMPONENT,GL45C.GL_FLOAT,a);
            if (!(a[0] >= 0 && a[0] <= 1) || a[a.length/2]==a[0] && a[n*n/2+n/2]==a[0] && a[n*n-1]==a[0]) {
                boolean ok=false; for(float v:a) if(v!=a[0] && Float.isFinite(v)){ok=true;break;}
                if(!ok) throw new AssertionError("Empty shadow depth readback: " + id);
            }
            return a;
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static BufferedImage frame(ClientGameTestContext ctx, String name) {
        ctx.waitTicks(4);
        return SnowstormMaterialSmoke.read(ctx.takeScreenshot("tacz_reimagined_"+name));
    }
    private static ItemStack gun(Identifier id) { return GunItemBuilder.create().setId(id).setAmmoCount(6).build(); }
    private static void equip(ClientGameTestContext ctx, TestSingleplayerContext world, Identifier id) {
        world.getServer().runOnServer(s -> { var p=world.getConnection().getServerPlayer(); p.setItemInHand(InteractionHand.MAIN_HAND,id==null?ItemStack.EMPTY:gun(id)); p.inventoryMenu.broadcastChanges(); });
        ctx.waitFor(mc -> {var held=mc.player.getMainHandItem(); return id==null?held.isEmpty():held.getItem() instanceof IGun g && id.equals(g.getGunId(held));});
        ctx.waitTicks(15);
    }
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var server=world.getServer();
        server.runCommand("gamerule advance_time false"); server.runCommand("time set 4000"); server.runCommand("weather clear");
        server.runCommand("gamerule spawn_mobs false"); server.runCommand("kill @e[type=!minecraft:player]");
        var base=server.computeOnServer(s->{var p=world.getConnection().getServerPlayer(); var pos=p.blockPosition(); var level=p.level();
            for(int x=-12;x<=12;x++) for(int z=-4;z<=30;z++) level.setBlock(pos.offset(x,-1,z), Blocks.WHITE_CONCRETE.defaultBlockState(),Block.UPDATE_ALL);
            return pos;});
        ctx.getInput().lookAt(0,18);
        world.getConnection().waitForChunksRender(); ctx.waitTicks(12);
        ctx.runOnClient(mc->{ requirePack(); mc.options.bobView().set(false); });
        var failures=new StringBuilder();
        try {
            frame(ctx,"absent"); var empty=ctx.computeOnClient(mc->depth());
            equip(ctx,world,SVD);
            shadow(ctx,"held",empty,failures);
            equip(ctx,world,null);
            var dropped=server.computeOnServer(s->{var level=world.getConnection().getServerPlayer().level();
                var e=new ItemEntity(level,base.getX()+.5,base.getY()+.8,base.getZ()+4.5,gun(SVD),0,0,0); e.setNoGravity(true); e.setPickUpDelay(32767);level.addFreshEntity(e);return e.getUUID();});
            ctx.waitFor(mc->mc.level.getEntity(dropped)!=null);
            shadow(ctx,"drop",empty,failures);
            server.runOnServer(s->world.getConnection().getServerPlayer().level().getEntity(dropped).snapTo(Vec3.atCenterOf(base.offset(3,0,5))));
            ctx.waitFor(mc->mc.level.getEntity(dropped).getX()>base.getX()+2);
            shadow(ctx,"moved",empty,failures);
            server.runOnServer(s->world.getConnection().getServerPlayer().level().getEntity(dropped).discard());
            ctx.waitFor(mc->mc.level.getEntity(dropped)==null); frame(ctx,"removed");
            server.runCommand("time set 18000");
            // Dim blue ACT light behind the camera. Keep the first person mesh and its base ambient material.
            server.runOnServer(s->world.getConnection().getServerPlayer().level().setBlock(base.offset(0,0,-6),Blocks.SOUL_LANTERN.defaultBlockState(),Block.UPDATE_ALL));
            world.getConnection().waitForChunksRender(); ctx.getInput().lookAt(0,0); ctx.waitTicks(20);
            equip(ctx,world,AK);
            ctx.runOnClient(mc->{ registerTile(); RenderConfig.RENDER_MUZZLE_SMOKE.set(false); });
            var off=frame(ctx,"night_off");
            ctx.runOnClient(mc->{pulse=true;bodyOnly=true;});
            var body=frame(ctx,"night_body");
            ctx.runOnClient(mc->bodyOnly=false); frame(ctx,"night_stock");
            ctx.runOnClient(mc->{pulse=false;NativeMuzzleEffects.clear();WorldEmitterManager.getInstance().clear();});
            GunMod.LOGGER.info("PORT_REIMAGINED: {} held shot extractions; off/body samples (gun and skin) {}",pulses,regions(off,body));
            if(pulses==0)throw new AssertionError("No real hand submits");
            equip(ctx,world,null);
            ctx.runOnClient(mc->tile=1); var strip=rgb(frame(ctx,"night_strip"));
            ctx.runOnClient(mc->tile=2); var emitted=rgb(frame(ctx,"night_reference"));
            GunMod.LOGGER.info("PORT_REIMAGINED: same stock white strip texel RGB={} emissive reference={}",Arrays.toString(strip),Arrays.toString(emitted));
            for(int i=0;i<3;i++) if(emitted[i]<110 || Math.abs(strip[i]-emitted[i])>14){failures.append(" strip!=emissive");break;}
            if(!failures.isEmpty()) throw new AssertionError("Reimagined: "+failures);
            GunMod.LOGGER.info("PORT_REIMAGINED: complete");
        } finally { ctx.runOnClient(mc->{tile=0;pulse=bodyOnly=shadowReference=false;targets=null;}); }
    }
    private static void shadow(ClientGameTestContext ctx, String label, float[] empty, StringBuilder failures) {
        ctx.runOnClient(mc->{shadowBodies=0;shadowReference=true;});
        frame(ctx,label+"_reference"); var ideal=ctx.computeOnClient(mc->depth());
        ctx.runOnClient(mc->shadowReference=false); frame(ctx,label); var actual=ctx.computeOnClient(mc->depth());
        // Compare against the SAME SVD with the original cutout material for shadow depth only.
        // An animated body/turning item can move within that local silhouette, not halfway across the map.
        int n=(int)Math.sqrt(empty.length), x0=n,y0=n,x1=-1,y1=-1,near=0,far=0;
        for(int i=0;i<empty.length;i++) if(Math.abs(empty[i]-ideal[i])>.0001f) {int x=i%n,y=i/n;x0=Math.min(x0,x);x1=Math.max(x1,x);y0=Math.min(y0,y);y1=Math.max(y1,y);near++;}
        int pad=n/24;
        for(int i=0;i<empty.length;i++) if(Math.abs(empty[i]-actual[i])>.0001f){int x=i%n,y=i/n;if(x<x0-pad||x>x1+pad||y<y0-pad||y>y1+pad)far++;}
        GunMod.LOGGER.info("PORT_REIMAGINED: shadow {} reference changed={} bounds=({},{})..({},{}) outliers={} shadow body selects={}",label,near,x0,y0,x1,y1,far,shadowBodies);
        if(near<4 || shadowBodies==0 || far>4) failures.append(' ').append(label).append("-shadow(").append(near).append('/').append(far).append('/').append(shadowBodies).append(')');
    }
    // Sample the shipped flash strip through the production material factory against the pack's
    // emissive program. Same opaque white texel, camera, dark scene, full UV2 and vertex colour.
    private static void registerTile() {
        LevelRenderEvents.COLLECT_SUBMITS.register(c->{
            if(tile==0 || IrisCompat.isRenderShadow())return;
            var desc=ParticleDefinitionLoader.getInstance().getDefinition(Identifier.parse("tacz:muzzle_flash")).getDescription();
            var type=tile==1?SnowstormMaterials.type(desc,false,false):RenderTypes.eyes(desc.getTexture());
            var pose=new PoseStack();pose.translate(0,0,2);
            c.submitNodeCollector().submitCustomGeometry(pose,type,(p,b)->{
                for(int[] v:new int[][]{{-1,-1},{-1,1},{1,1},{1,-1}}) b.addVertex(p.pose(),v[0]*.18f,v[1]*.18f,0).setColor(220,220,220,255)
                        .setUv(312.5f/1152,40.5f/64).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0,0,-1);
            });
        });
    }
    private static int[] rgb(BufferedImage image) {
        int[] s=new int[3]; int n=0;
        for(int y=image.getHeight()/2-18;y<image.getHeight()/2+18;y++) for(int x=image.getWidth()/2-18;x<image.getWidth()/2+18;x++){
            if(Math.abs(x-image.getWidth()/2)<7&&Math.abs(y-image.getHeight()/2)<7)continue;
            int c=image.getRGB(x,y);for(int i=0;i<3;i++)s[i]+=(c>>(16-8*i))&255;n++;
        }
        for(int i=0;i<3;i++)s[i]/=n;return s;
    }
    private static String regions(BufferedImage off,BufferedImage on){
        var msg=new StringBuilder();
        for (int[] rect:new int[][]{{54,70,61,79},{64,79,74,88}}){
            int[] a=new int[3],b=new int[3];int n=0;
            for(int y=off.getHeight()*rect[1]/100;y<off.getHeight()*rect[3]/100;y++)for(int x=off.getWidth()*rect[0]/100;x<off.getWidth()*rect[2]/100;x++){
                for(int i=0;i<3;i++){a[i]+=off.getRGB(x,y)>>(16-8*i)&255;b[i]+=on.getRGB(x,y)>>(16-8*i)&255;}n++;}
            for(int i=0;i<3;i++){a[i]/=n;b[i]/=n;}msg.append(Arrays.toString(a)).append(" -> ").append(Arrays.toString(b)).append(';');
        }return msg.toString();
    }
}
