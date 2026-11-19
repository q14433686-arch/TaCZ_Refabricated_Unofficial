package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopePipRenderer;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Read-only probes in the same world collection as the captured PNG. In particular we can
 * distinguish a shader ignoring UV2 from the actual receiver not getting a light at all. */
public final class ReceiverInputs {
    private static final Map<String,int[]> frames = new HashMap<>();
    private static Vec3[] entities;
    private static BlockPos statue,board,cabinet;
    private static int[] current;
    static void scene(Vec3 cow, Vec3 stand, Vec3 drop, BlockPos base, BlockPos normal, BlockPos lit) {
        entities=new Vec3[]{cow,stand,drop};statue=base;board=normal;cabinet=lit;frames.clear();
    }
    static void arm(String label) {
        current=null;
        if (!label.startsWith("objects_")) return;
        current=new int[6];Arrays.fill(current,-1);frames.put(label,current);
    }
    static void collected() { current=null; }
    private static boolean observing() {
        return current!=null && SurfaceLightFrames.triggered() && !ScopePipRenderer.isInsideScopeLevelRender() && !IrisCompat.isRenderShadow();
    }
    /** Vanilla clears both lists BEFORE Fabric COLLECT_SUBMITS. Observe from inside
     * submitFeatures as vanilla begins submitting entities, with the scoped light applied. */
    public static void submit(LevelRenderState state) {
        if (!observing()) return;
        for (var e:state.entityRenderStates) {
            int i=e.entityType==EntityTypes.COW ? 0 : e.entityType==EntityTypes.ARMOR_STAND ? 1 : e.entityType==EntityTypes.ITEM ? 2 : -1;
            if (i>=0 && entities[i].distanceToSqr(e.x,e.y,e.z)<.09) current[i]=e.lightCoords;
        }
        for (var b:state.blockEntityRenderStates) if (statue.equals(b.blockPos)) current[3]=b.lightCoords;
    }
    public static void rack(BlockPos pos,int light) {
        if (!observing()) return;
        if (board.equals(pos)) current[4]=light;
        if (cabinet.equals(pos)) current[5]=light;
    }
    static void verify() {
        var off=frames.get("objects_off");var on=frames.get("objects_on");var expired=frames.get("objects_expired");
        var base=frames.get("objects_blocked_rest"); var wall=frames.get("objects_blocked");
        for(int i=0;i<6;i++) {
            if(off[i]<0 || on[i]<0 || off[i]!=expired[i] || base[i]!=wall[i] || wall[i]<0 ||
                    (i==5 ? off[i]!=LightCoordsUtil.FULL_BRIGHT || on[i]!=off[i] : LightCoordsUtil.block(on[i])-LightCoordsUtil.block(off[i])<3
                            || LightCoordsUtil.sky(off[i])!=LightCoordsUtil.sky(on[i])))
                throw new AssertionError("World UV2 sample "+i+": "+Arrays.toString(off)+" -> "+Arrays.toString(on)+", expired="+Arrays.toString(expired)+", blocked="+Arrays.toString(base)+"/"+Arrays.toString(wall));
        }
        GunMod.LOGGER.info("PORT_RECEIVER: UV2 cow/stand/drop/statue/board slot/fullbright case OFF={} ON={}; no sky change, expiry or wall leak",Arrays.toString(off),Arrays.toString(on));
    }
    static void clear() { frames.clear();current=null;entities=null;statue=board=cabinet=null; }
    private ReceiverInputs() {}
}
