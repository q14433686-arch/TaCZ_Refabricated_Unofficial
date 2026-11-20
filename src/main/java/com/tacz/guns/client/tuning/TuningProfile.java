package com.tacz.guns.client.tuning;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** One account: XYZ is shared by its guns. Each HUD override is a complete style, not a bag of deltas. */
public final class TuningProfile {
    private final TuningValues.Draft base = new TuningValues.Draft();
    private final Map<String, TuningValues.Draft> types = new TreeMap<>(), guns = new TreeMap<>();

    public TuningValues.Draft base() { return base; }
    public Set<String> typeKeys() { return Set.copyOf(types.keySet()); }
    public Set<String> gunKeys() { return Set.copyOf(guns.keySet()); }

    public TuningValues hud(@Nullable String type, @Nullable String gun) {
        if (gun != null && guns.containsKey(gun)) return guns.get(gun);
        return type == null ? base : types.getOrDefault(type, base);
    }

    public boolean overrides(@Nullable String type, @Nullable String gun) {
        return gun != null ? guns.containsKey(gun) : type != null && types.containsKey(type);
    }

    public void inherit(@Nullable String type, @Nullable String gun) {
        if (gun != null) guns.remove(gun); else if (type != null) types.remove(type);
    }

    public void setHud(@Nullable String type, @Nullable String gun, TuningValues style) {
        var target = new TuningValues.Draft(base); // overrides contain HUD only; XYZ always reads from this account
        require(TuningShare.CROSSHAIR.decode(TuningShare.CROSSHAIR.encode(style), target));
        if (gun != null) guns.put(gun, target); else if (type != null) types.put(type, target); else base.merge(target);
    }

    public static TuningProfile seeded(TuningValues seed) {
        var result = new TuningProfile();
        for (var group : TuningShare.values()) require(group.decode(group.encode(seed), result.base));
        return result;
    }

    public TuningProfile copy() {
        var result = new TuningProfile();
        result.base.merge(base);
        types.forEach((k,v) -> result.copyRule(result.types, k, v));
        guns.forEach((k,v) -> result.copyRule(result.guns, k, v));
        return result;
    }

    private void copyRule(Map<String, TuningValues.Draft> map, String key, TuningValues.Draft value) {
        var draft = new TuningValues.Draft(base);
        draft.merge(value);
        map.put(key, draft);
    }

    JsonObject json() {
        var obj = new JsonObject();
        obj.addProperty("version", 1);
        obj.addProperty("position", TuningShare.POSITION.encode(base));
        obj.addProperty("default", TuningShare.CROSSHAIR.encode(base));
        obj.add("types", encodeRules(types));
        obj.add("guns", encodeRules(guns));
        return obj;
    }

    private static JsonObject encodeRules(Map<String, TuningValues.Draft> rules) {
        var map = new JsonObject();
        rules.forEach((k,v) -> map.addProperty(k, TuningShare.CROSSHAIR.encode(v)));
        return map;
    }

    static TuningProfile parse(JsonElement element) {
        var o = element.getAsJsonObject();
        require(o.has("version") && "1".equals(o.get("version").toString()));
        var p = new TuningProfile();
        require(TuningShare.POSITION.decode(text(o.get("position")), p.base));
        require(TuningShare.CROSSHAIR.decode(text(o.get("default")), p.base));
        p.readRules(o.get("types"), p.types, false);
        p.readRules(o.get("guns"), p.guns, true);
        return p;
    }

    private void readRules(JsonElement element, Map<String, TuningValues.Draft> rules, boolean gun) {
        require(element != null && element.isJsonObject());
        for (var entry : element.getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            require(!key.isBlank() && (!gun || Identifier.tryParse(key) != null));
            var draft = new TuningValues.Draft(base);
            require(TuningShare.CROSSHAIR.decode(text(entry.getValue()), draft));
            rules.put(key, draft);
        }
    }

    private static String text(JsonElement e) {
        require(e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString());
        return e.getAsString();
    }
    private static void require(boolean ok) { if (!ok) throw new IllegalArgumentException("Invalid tuning profile"); }
}
