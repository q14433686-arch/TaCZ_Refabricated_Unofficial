package com.tacz.guns.client.resource.serialize;

import com.google.gson.*;
import com.tacz.guns.api.client.animation.*;
import com.tacz.guns.client.resource.pojo.animation.bedrock.ParticleEffectKeyframes;
import net.minecraft.resources.Identifier;
import java.lang.reflect.Type;
import java.util.*;

/** Bedrock string/object/array forms. Invalid input is diagnosed by the asset loader, not coerced. */
public final class ParticleEffectKeyframesSerializer implements JsonDeserializer<ParticleEffectKeyframes> {
    @Override public ParticleEffectKeyframes deserialize(JsonElement json,Type type,JsonDeserializationContext ctx){
        if(!json.isJsonObject())throw new JsonParseException("particle_effects must be an object");
        List<ObjectAnimationParticleChannel.Keyframe> frames=new ArrayList<>();
        for(var e:json.getAsJsonObject().entrySet()){
            double time;
            try{time=Double.parseDouble(e.getKey());}catch(NumberFormatException ex){throw new JsonParseException("Invalid particle keyframe time",ex);}
            if(!Double.isFinite(time)||time<0)throw new JsonParseException("Particle time must be finite and nonnegative");
            List<AnimationParticleEvent> events=new ArrayList<>();
            if(e.getValue().isJsonArray())e.getValue().getAsJsonArray().forEach(v->events.add(parse(v)));
            else events.add(parse(e.getValue()));
            if(frames.size()>=4096||events.size()>256)throw new JsonParseException("Particle keyframe budget exceeded");
            frames.add(new ObjectAnimationParticleChannel.Keyframe(time,events));
        }
        return new ParticleEffectKeyframes(new ObjectAnimationParticleChannel(frames));
    }
    private static AnimationParticleEvent parse(JsonElement json){
        String id,locator=null,script=null;
        if(json.isJsonPrimitive()&&json.getAsJsonPrimitive().isString())id=json.getAsString();
        else if(json.isJsonObject()){
            var o=json.getAsJsonObject();
            if(!o.has("effect")||!o.get("effect").isJsonPrimitive()||!o.getAsJsonPrimitive("effect").isString())throw new JsonParseException("Particle effect ID missing");
            id=o.get("effect").getAsString();
            if(o.has("locator"))locator=o.get("locator").getAsString();
            if(o.has("pre_effect_script"))script=o.get("pre_effect_script").getAsString();
        }else throw new JsonParseException("Invalid particle request");
        var effect=Identifier.tryParse(id);if(effect==null)throw new JsonParseException("Invalid particle ID "+id);
        return new AnimationParticleEvent(effect,locator,script);
    }
}
