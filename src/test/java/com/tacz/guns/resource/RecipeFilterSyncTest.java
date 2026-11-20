package com.tacz.guns.resource;

import com.google.gson.GsonBuilder;
import com.tacz.guns.resource.filter.RecipeFilter;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeFilterSyncTest {
    @Test void wireRoundTripPreservesEmptyAndInvalidWhitelists() {
        var gson = new GsonBuilder().registerTypeAdapter(RecipeFilter.class, new RecipeFilter.Deserializer()).create();
        var allowed = Identifier.parse("test:allowed");
        var blocked = Identifier.parse("test:blocked");
        var blacklistOnly = gson.fromJson("{\"blacklist\":[\"test:blocked\"]}", RecipeFilter.class);
        var synced = gson.fromJson(gson.toJson(blacklistOnly), RecipeFilter.class);
        assertTrue(synced.contains(allowed));
        assertFalse(synced.contains(blocked));
        var invalid = gson.fromJson("{\"whitelist\":[\"INVALID ID\"]}", RecipeFilter.class);
        assertFalse(gson.fromJson(gson.toJson(invalid), RecipeFilter.class).contains(allowed));
    }
}
