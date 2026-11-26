package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.bedrock.*;
import com.tacz.guns.client.render.ViewmodelFlash;
import com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import java.util.ArrayList;
import java.util.List;

/** Native/Iris contract for actual frozen body tasks, material boundaries, UV2 and reflected gain. */
public final class BodyMaterialContractSmoke {
    private record Seen(String id,int light,float red,VertexConsumer consumer) {}
    private static final class Part extends BedrockPart {
        Part(String name){super(name);}
        Part child(Part child){addChild(child);child.parent=this;return child;}
    }
    private record Cube(String id,List<Seen> seen) implements BedrockCube {
        public BedrockPolygon[] getPolygons(){return new BedrockPolygon[0];}
        public void compile(PoseStack.Pose pose,VertexConsumer out,int light,int overlay,float r,float g,float b,float a){seen.add(new Seen(id,light,r,out));}
    }
    private static final class Collector extends SubmitNodeStorage {
        final List<RenderType> types=new ArrayList<>();final List<Runnable> tasks=new ArrayList<>();
        // Storage.order has a concrete covariant return type in 1.21.11. Intercept the
        // collector interface instead of overriding it with the wider 26.x return type.
        final SubmitNodeCollector proxy=(SubmitNodeCollector)java.lang.reflect.Proxy.newProxyInstance(
                SubmitNodeCollector.class.getClassLoader(),new Class<?>[]{SubmitNodeCollector.class},
                (target,method,args)->method.getParameterCount()==1 && method.getParameterTypes()[0]==int.class
                        && net.minecraft.client.renderer.OrderedSubmitNodeCollector.class.isAssignableFrom(method.getReturnType())
                        ?target:method.invoke(this,args));
        @Override public void submitCustomGeometry(PoseStack pose,RenderType type,CustomGeometryRenderer draw){types.add(type);var frozen=pose.last().copy();tasks.add(()->draw.render(frozen,null));}
        void flush(){tasks.forEach(Runnable::run);}
    }
    @SuppressWarnings("unchecked")
    public static void verify() {
        var seen=new ArrayList<Seen>();var root=new Part("root");root.cubes.add(new Cube("body",seen));
        var glow=root.child(new Part("lamp_illuminated"));glow.illuminated=true;glow.cubes.add(new Cube("lamp",seen));
        require(new com.tacz.guns.client.model.FunctionalBedrockPart(null,glow).illuminated,"Functional wrapper lost authored illuminated bit");
        glow.child(new Part("lamp_child")).cubes.add(new Cube("inherited",seen));
        root.child(new Part("body2")).cubes.add(new Cube("body2",seen));
        int ambient=(3<<20)|(2<<4);boolean previous=RenderConfig.ILLUMINATED_REAL_SKY.get();
        try {
            RenderConfig.ILLUMINATED_REAL_SKY.set(true);
            var snapshot=BedrockRenderSnapshot.captureSubtree(root,new PoseStack(),ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ambient,0,1,1,1,1);
            var texture=Identifier.parse("tacz:textures/entity/default_bullet.png");
            var body=RenderTypes.entityCutout(texture);var illuminated=IlluminatedMaterials.forBody(body,true);
            require(illuminated!=body,"Known body material did not acquire an unshaded variant");
            var collector=new Collector();snapshot.submitGeometry(collector.proxy,body,true);
            root.visible=false;glow.illuminated=false;collector.flush(); // all decisions had to freeze before this mutation
            require(collector.types.equals(List.of(body,illuminated,body)),"Body/illuminated material ranges were lost or reordered");
            var scoped=new Collector();snapshot.submitMaterials(scoped.proxy,body,true,ScopeRenderTypes::apertureCopy);
            require(scoped.types.stream().map(ScopeRenderTypes::unwrapDepthCopy).toList().equals(List.of(body,illuminated,body)),
                    "Scope aperture wrapper hid material identity before the illuminated partition");
            int expected=IrisCompat.isUsingRenderPack()?((3<<20)|(15<<4)):15728880;
            require(seen.size()==4 && seen.get(0).light==ambient && seen.get(1).light==expected && seen.get(2).light==expected && seen.get(3).light==ambient,
                    "Frozen illuminated subtree did not preserve real-sky UV2 or polluted ordinary body light: "+seen);
            var unknown=new Collector();var swirl=RenderTypes.energySwirl(texture,0,0);snapshot.submitMaterials(unknown.proxy,swirl,true);
            require(unknown.types.equals(List.of(swirl)),"Specialized material was replaced by guessing its albedo");
            var field=ViewmodelFlash.class.getDeclaredField("GAIN");field.setAccessible(true);
            var gain=(ThreadLocal<Float>)field.get(null);Float saved=gain.get();
            try {
                gain.set(.5f);seen.clear();var reflected=new Collector();snapshot.submitGeometry(reflected.proxy,body,true);
                gain.set(0f);reflected.flush();
                require(reflected.types.size()==4,"Viewmodel reflection did not add exactly one neutral replay");
                var extra=seen.subList(4,seen.size());
                require(extra.size()==2 && extra.stream().allMatch(v->v.red==.5f && v.light==ambient)
                        && extra.stream().noneMatch(v->v.id.equals("lamp")||v.id.equals("inherited")),
                        "Reflected gain was read late, modified ambient light or double-lit an emitter: "+extra);
            } finally {if(saved==null)gain.remove();else gain.set(saved);}
            root.visible=true;glow.illuminated=true;seen.clear();
            var lit=(VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                    new Class<?>[]{VertexConsumer.class},(proxy,method,args)->proxy);
            root.render(new PoseStack(),ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,null,lit,ambient,0,1,1,1,1);
            require(seen.size()==4 && seen.get(0).consumer==null && seen.get(1).consumer==lit
                    && seen.get(2).consumer==lit && seen.get(3).consumer==null && seen.get(1).light==expected,
                    "Immediate two-buffer routing lost inherited material or real-sky light");
            var calls=new int[1];
            var functional=new com.tacz.guns.client.model.FunctionalBedrockPart(p->(pose,out,context,light,overlay)->{
                require(out==lit && light==expected,"Functional renderer lost emitter buffer/light");calls[0]++;
            },"hook_illuminated");
            functional.illuminated=true;functional.visible=false;
            functional.render(new PoseStack(),ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,null,lit,ambient,0,1,1,1,1);
            require(calls[0]==1,"Invisible functional provider no longer executes");
            System.out.println("PORT_BODY_MATERIAL: actual frozen body/unshaded/body submissions, inherited real-sky UV2, unknown-material preservation and neutral reflection without emitter double-light passed; iris="+IrisCompat.isUsingRenderPack());
        } catch(ReflectiveOperationException e){throw new AssertionError(e);}
        finally {RenderConfig.ILLUMINATED_REAL_SKY.set(previous);}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private BodyMaterialContractSmoke(){}
}
