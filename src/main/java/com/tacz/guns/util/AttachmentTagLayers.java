package com.tacz.guns.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import net.minecraft.server.packs.resources.IoSupplier;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Preserve additive attachment tags when several disk/zip packs share one PackResources. */
public final class AttachmentTagLayers {
    private AttachmentTagLayers() {}
    public static InputStream merge(String id, List<IoSupplier<InputStream>> layers) {
        Set<String> values = new LinkedHashSet<>();
        for (var layer : layers) {
            try (var input = layer.get(); var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                var array = JsonParser.parseReader(reader).getAsJsonArray();
                List<String> valid = new ArrayList<>();
                for (var value : array) {
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                        throw new JsonParseException("Attachment tag entries must be strings");
                    valid.add(value.getAsString());
                }
                values.addAll(valid);
            } catch (IOException | RuntimeException ex) {
                GunMod.LOGGER.warn("Ignoring invalid attachment tag layer {}: {}", id, ex.toString());
            }
        }
        JsonArray result = new JsonArray();
        values.forEach(result::add);
        return new ByteArrayInputStream(result.toString().getBytes(StandardCharsets.UTF_8));
    }
}
