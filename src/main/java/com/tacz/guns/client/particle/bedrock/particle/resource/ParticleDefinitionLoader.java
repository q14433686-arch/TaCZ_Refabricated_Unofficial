package com.tacz.guns.client.particle.bedrock.particle.resource;

import com.google.gson.*;
import com.tacz.guns.client.particle.bedrock.particle.data.*;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.tacz.guns.resource.manager.JsonDataManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.*;

/** Uses the same pack scanner/precedence as gun assets, including external pack overlays. */
public final class ParticleDefinitionLoader extends JsonDataManager<ParticleEffectDefinition> {
    private static final ParticleDefinitionLoader INSTANCE = new ParticleDefinitionLoader();
    private Map<Identifier,ParticleEffectDefinition> identifiers = Map.of();
    private final ParticleMolangEnvironment compiler = new ParticleMolangEnvironment();
    private ParticleDefinitionLoader() { super(ParticleEffectDefinition.class,new Gson(),"particle_definitions","SnowstormDefinitions"); }
    public static ParticleDefinitionLoader getInstance() { return INSTANCE; }
    @Override protected ParticleEffectDefinition parseJson(JsonElement json) {
        return new ParticleEffectDeserializer().parse(json,compiler);
    }
    @Override protected void apply(Map<Identifier,JsonElement> input, ResourceManager manager, ProfilerFiller profiler) {
        com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager.getInstance().clear();
        super.apply(input,manager,profiler);
        var index=new HashMap<Identifier,ParticleEffectDefinition>();
        dataMap.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{
            var old=index.putIfAbsent(e.getValue().getIdentifier(),e.getValue());
            if(old!=null)throw new JsonParseException("Duplicate particle identifier: "+e.getValue().getIdentifier());
        });
        identifiers=Map.copyOf(index);
    }
    public ParticleEffectDefinition getDefinition(Identifier id) {
        var d=dataMap.get(id);return d!=null?d:identifiers.get(id);
    }
}
