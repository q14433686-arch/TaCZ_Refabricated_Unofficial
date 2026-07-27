package com.tacz.guns.resource.serialize;

import com.google.gson.*;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Type;

public class IdentifierSerializer implements JsonSerializer<Identifier>, JsonDeserializer<Identifier> {
    @Override
    public JsonElement serialize(Identifier src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
    }

    @Override
    public Identifier deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return Identifier.tryParse(json.getAsString());
    }
}
