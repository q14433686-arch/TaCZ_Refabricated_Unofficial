package com.tacz.guns.client.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.pojo.model.*;
import com.tacz.guns.config.ClientConfig;
import com.tacz.guns.config.client.RenderConfig;
import java.util.ArrayList;
import java.util.List;

/** Exercises the real attachment constructor and all supported cube assembly branches. */
public final class OcularBakeContractSmoke {
    private static final Gson GSON=new GsonBuilder().registerTypeAdapter(CubesItem.class,new CubesItem.Deserializer()).create();
    public static void verify() {
        require(ClientConfig.isLoaded(),"Client config must be loaded before the runtime bake contract");
        boolean enabled=RenderConfig.ENABLE_OCULAR_INFLATE.get();double amount=RenderConfig.OCULAR_INFLATE.get();
        try {
            for(var version:BedrockVersion.values()) {
                RenderConfig.ENABLE_OCULAR_INFLATE.set(false);RenderConfig.OCULAR_INFLATE.set(.125);
                var disabled=bake(version);
                RenderConfig.ENABLE_OCULAR_INFLATE.set(true);RenderConfig.OCULAR_INFLATE.set(0.0);
                var zero=bake(version);
                RenderConfig.OCULAR_INFLATE.set(.125);var expanded=bake(version);
                for(String node:List.of("ocular_scope_2","glass","ocular_ring","division")) {
                    var base=cubes(disabled.getNode(node));var off=cubes(zero.getNode(node));var on=cubes(expanded.getNode(node));
                    require(base.size()==on.size() && base.size()==off.size() && !base.isEmpty(),"Cube assembly lost a branch: "+node);
                    boolean ocular=node.equals("ocular_scope_2")||node.equals("glass");
                    for(int i=0;i<base.size();i++) {
                        close(width(base.get(i)),2.5f);close(width(off.get(i)),2.5f);
                        close(width(on.get(i)),ocular?2.75f:2.5f);
                    }
                }
                // Baking another generation must not mutate a model already used by a queued frame.
                close(width(cubes(disabled.getNode("glass")).getFirst()),2.5f);
                if(version==BedrockVersion.NEW) require(cubes(expanded.getNode("ocular_scope_2")).size()==5,
                        "Modern test must cover box/per-face, rotated box/per-face, and the descendant");
            }
            System.out.println("PORT_OCULAR_BAKE: real legacy/modern attachment bakes; disabled/zero/enabled, authored inflate plus exact ocular/descendant bounds, box/per-face/rotated branches, rim/reticle exclusion and old generation unchanged");
        } finally {RenderConfig.ENABLE_OCULAR_INFLATE.set(enabled);RenderConfig.OCULAR_INFLATE.set(amount);}
    }
    private static BedrockAttachmentModel bake(BedrockVersion version) {
        String box="{origin:[0,0,0],size:[2,2,2],inflate:0.25,uv:[0,0]}";
        String face=box.replace("uv:[0,0]","uv:{south:{uv:[0,0],uv_size:[2,2]}}");
        String rotatedBox=box.replace("origin:","pivot:[0,0,0],rotation:[0,20,0],origin:");
        String rotatedFace=face.replace("origin:","pivot:[0,0,0],rotation:[0,20,0],origin:");
        String ocularCubes=version==BedrockVersion.NEW?String.join(",",box,face,rotatedBox,rotatedFace):box;
        String bones="[{name:'root',pivot:[0,0,0]},"
                +bone("ocular_scope_2","root",ocularCubes)+","+bone("glass","ocular_scope_2",box)+","
                +bone("ocular_ring","root",box)+","+bone("division","root",box)+"]";
        String json=version==BedrockVersion.NEW
                ?"{format_version:'1.12.0','minecraft:geometry':[{description:{texture_width:16,texture_height:16,visible_bounds_width:4,visible_bounds_height:4,visible_bounds_offset:[0,0,0]},bones:"+bones+"}]}"
                :"{format_version:'1.10.0','geometry.model':{texturewidth:16,textureheight:16,visible_bounds_width:4,visible_bounds_height:4,visible_bounds_offset:[0,0,0],bones:"+bones+"}}";
        return new BedrockAttachmentModel(GSON.fromJson(json,BedrockModelPOJO.class),version);
    }
    private static String bone(String name,String parent,String cubes){return "{name:'"+name+"',parent:'"+parent+"',pivot:[0,0,0],cubes:["+cubes+"]}";}
    private static List<BedrockCube> cubes(BedrockPart part){var result=new ArrayList<>(part.cubes);for(var child:part.children)result.addAll(cubes(child));return result;}
    private static float width(BedrockCube cube){float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;for(var face:cube.getPolygons())if(face!=null && face.isPresent())for(var v:face.vertices){min=Math.min(min,v.pos.x);max=Math.max(max,v.pos.x);}return max-min;}
    private static void close(float actual,float expected){require(Math.abs(actual-expected)<.00001f,"Baked ocular width "+actual+" != "+expected);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private OcularBakeContractSmoke(){}
}
