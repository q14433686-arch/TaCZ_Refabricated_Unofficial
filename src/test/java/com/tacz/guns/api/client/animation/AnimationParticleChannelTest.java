package com.tacz.guns.api.client.animation;

import com.google.gson.*;
import com.tacz.guns.client.resource.pojo.animation.bedrock.ParticleEffectKeyframes;
import com.tacz.guns.client.resource.serialize.ParticleEffectKeyframesSerializer;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnimationParticleChannelTest {
    final AnimationParticleEvent a=new AnimationParticleEvent(Identifier.parse("minecraft:smoke"),null,null);
    final AnimationParticleEvent b=new AnimationParticleEvent(Identifier.parse("tacz:muzzle_flash"),"muzzle_flash",null);
    final ObjectAnimationParticleChannel channel=new ObjectAnimationParticleChannel(List.of(
            new ObjectAnimationParticleChannel.Keyframe(.5,List.of(b)),new ObjectAnimationParticleChannel.Keyframe(0,List.of(a))));
    @Test void initialZeroAndHalfOpenBoundaries(){
        var out=new ArrayList<AnimationParticleEvent>();channel.collect(0,.5,1,false,true,out);
        assertEquals(List.of(a,b),out);out.clear();channel.collect(.5,.5,1,false,false,out);assertTrue(out.isEmpty());
    }
    @Test void wrapsChronologicallyAndDoesNotReplayZeroOnOrdinaryFrame(){
        var out=new ArrayList<AnimationParticleEvent>();channel.collect(.25,1,1,true,false,out);
        assertEquals(List.of(b,a),out);out.clear();channel.collect(0,0,1,true,false,out);assertTrue(out.isEmpty());
    }
    @Test void boundedAfterLongStall(){
        var out=new ArrayList<AnimationParticleEvent>();channel.collect(0,1e12,1,true,true,out);assertEquals(256,out.size());
    }
    @Test void parseArraysScriptsAndInvalidTimes(){
        Gson gson=new GsonBuilder().registerTypeAdapter(ParticleEffectKeyframes.class,new ParticleEffectKeyframesSerializer()).create();
        var parsed=gson.fromJson("{\"0\":[\"minecraft:smoke\",{\"effect\":\"tacz:muzzle_flash\",\"locator\":\"root\",\"pre_effect_script\":\"v.size=1;\"}]}",ParticleEffectKeyframes.class);
        assertEquals(2,parsed.channel().frames().getFirst().events().size());
        assertEquals("v.size=1;",parsed.channel().frames().getFirst().events().get(1).preEffectScript());
        for(String time:List.of("NaN","Infinity","-1","bad"))
            assertThrows(JsonParseException.class,()->gson.fromJson("{\""+time+"\":\"minecraft:smoke\"}",ParticleEffectKeyframes.class));
    }
}
