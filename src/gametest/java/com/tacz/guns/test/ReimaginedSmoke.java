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
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.tacz.guns.client.model.bedrock.IlluminatedMaterials;
import com.mojang.blaze3d.systems.RenderSystem;
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
    public static boolean shadowReference, pulse, bodyOnly, omitCaster;
    public static RenderType oldMaterial, svdBody;
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
            boolean populated=false;
            for(float v:a) if(Float.isFinite(v) && v!=a[0]) {populated=true;break;}
            if(!populated) throw new AssertionError("Empty shadow depth readback: " + id);
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
            for(int x=-12;x<=12;x++) for(int z=-4;z<=30;z++) level.setBlock(pos.offset(x,-1,z), net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:white_concrete")).defaultBlockState(),Block.UPDATE_ALL);
            return pos;});
        ctx.getInput().lookAt(0,18);
        world.getConnection().waitForChunksRender(); ctx.waitTicks(12);
        ctx.runOnClient(mc->{ requirePack(); mc.options.bobView().set(false); });
        var failures=new StringBuilder();
        try {
            frame(ctx,"absent");
            equip(ctx,world,SVD);
            shadow(ctx,"held",failures);
            equip(ctx,world,null);
            // Third screenshot: the unselected first slot is NOT an absent-gun control.
            // The shipped SVD display has hotbar_show[0], so its external rig can still cast.
            server.runOnServer(s->{var inv=world.getConnection().getServerPlayer().getInventory(); inv.setSelectedSlot(1); inv.setItem(0,gun(SVD)); world.getConnection().getServerPlayer().inventoryMenu.broadcastChanges();});
            ctx.runOnClient(mc->mc.player.getInventory().setSelectedSlot(1));
            ctx.waitFor(mc->mc.player.getMainHandItem().isEmpty() && mc.player.getInventory().getItem(0).getItem() instanceof IGun);
            shadow(ctx,"stowed",failures);
            server.runOnServer(s->{var p=world.getConnection().getServerPlayer();p.getInventory().setItem(0,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();});
            ctx.waitFor(mc->mc.player.getInventory().getItem(0).isEmpty());
            var dropped=server.computeOnServer(s->{var level=world.getConnection().getServerPlayer().level();
                var e=new ItemEntity(level,base.getX()+.5,base.getY()+.8,base.getZ()+4.5,gun(SVD),0,0,0); e.setNoGravity(true); e.setPickUpDelay(32767);level.addFreshEntity(e);return e.getUUID();});
            ctx.waitFor(mc->mc.level.getEntity(dropped)!=null);
            shadow(ctx,"drop",failures);
            server.runOnServer(s->world.getConnection().getServerPlayer().level().getEntity(dropped).snapTo(Vec3.atCenterOf(base.offset(3,0,5))));
            ctx.waitFor(mc->mc.level.getEntity(dropped).getX()>base.getX()+2);
            shadow(ctx,"moved",failures);
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
        } finally { ctx.runOnClient(mc->{tile=0;pulse=bodyOnly=shadowReference=omitCaster=false;targets=oldMaterial=svdBody=null;}); }
    }
    private static float[] renderDepth(Minecraft mc) {
        // Four complete renders on the SAME client tick. No advancing item bob/spin or actor
        // animation between the original-material reference and production footage.
        mc.gameRenderer.update(DeltaTracker.ONE); mc.gameRenderer.extract(DeltaTracker.ONE,true);
        mc.gameRenderer.render(); RenderSystem.getDevice().createCommandEncoder().submit();
        return depth();
    }
    private static void shadow(ClientGameTestContext ctx, String label, StringBuilder failures) {
        frame(ctx,label);
        ctx.runOnClient(mc->{
            // A null shadow mapping was the suspected regression. Use the original visible
            // (non-shadow) illuminated type as a deliberate bad caster for sensitivity.
            svdBody=RenderTypes.entityCutout(Identifier.parse("tacz:textures/gun/uv/svd.png"));
            var unmapped=IlluminatedMaterials.forBody(svdBody,false);
            shadowBodies=0; omitCaster=true; var absent=renderDepth(mc); omitCaster=false;
            shadowReference=true; var ideal=renderDepth(mc); shadowReference=false;
            var actual=renderDepth(mc);
            oldMaterial=unmapped; var bad=renderDepth(mc); oldMaterial=null;
            int n=(int)Math.sqrt(actual.length), width=0, near=0, agree=0,spurious=0,badSpurious=0;
            // Static nearby terrain, but waving leaves/foliage may change a few individual
            // distant texels even within a tick. Count long contiguous runs as well as area.
            for(int y=0;y<n;y++) {int run=0;for(int x=0;x<n;x++) {
                int i=x+y*n; boolean ref=Math.abs(ideal[i]-absent[i])>.00015f, got=Math.abs(actual[i]-absent[i])>.00015f;
                if(ref)near++; if(ref && Math.abs(actual[i]-ideal[i])<.00015f)agree++;
                if(!ref && got)spurious++;
                if(!ref && Math.abs(bad[i]-absent[i])>.00015f){badSpurious++;width=Math.max(width,++run);}else run=0;
            }}
            GunMod.LOGGER.info("PORT_REIMAGINED: shadow {} reference={} same-depth={} extra={} unmapped-extra={} unmapped-longest-row={} selects={}",label,near,agree,spurious,badSpurious,width,shadowBodies);
            if(shadowBodies==0 || near<3 || agree<near*.70 || spurious>Math.max(12,near*.15)) failures.append(' ').append(label).append("-shadow");
        });
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
        for (int[] rect:new int[][]{{62,71,65,74},{49,67,51,70}}){
            int[] a=new int[3],b=new int[3];int n=0;
            for(int y=off.getHeight()*rect[1]/100;y<off.getHeight()*rect[3]/100;y++)for(int x=off.getWidth()*rect[0]/100;x<off.getWidth()*rect[2]/100;x++){
                for(int i=0;i<3;i++){a[i]+=off.getRGB(x,y)>>(16-8*i)&255;b[i]+=on.getRGB(x,y)>>(16-8*i)&255;}n++;}
            for(int i=0;i<3;i++){a[i]/=n;b[i]/=n;}msg.append(Arrays.toString(a)).append(" -> ").append(Arrays.toString(b)).append(';');
        }return msg.toString();
    }
}
