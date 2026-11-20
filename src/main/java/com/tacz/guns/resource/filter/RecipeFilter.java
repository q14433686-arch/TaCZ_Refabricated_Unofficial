package com.tacz.guns.resource.filter;

import com.google.gson.*;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.CommonAssetsManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.PatternSyntaxException;

public class RecipeFilter {
    private final List<IFilter<Identifier>> whitelist = new ArrayList<>();
    private final List<IFilter<Identifier>> blacklist = new ArrayList<>();

    public void merge(RecipeFilter other) {
        this.whitelist.addAll(other.whitelist);
        this.blacklist.addAll(other.blacklist);
    }

    /** Same additive rules as RecipeFilterManager for ordinary data-pack stacks; blacklists still win. */
    public static InputStream mergeLayers(Identifier id, List<IoSupplier<InputStream>> layers) throws IOException {
        RecipeFilter merged = null;
        for (var layer : layers) {
            try (var reader = new InputStreamReader(layer.get(), StandardCharsets.UTF_8)) {
                RecipeFilter filter = CommonAssetsManager.GSON.fromJson(reader, RecipeFilter.class);
                if (filter == null) throw new JsonParseException("Empty filter");
                if (merged == null) merged = filter;
                else merged.merge(filter);
            } catch (IOException | RuntimeException ex) {
                GunMod.LOGGER.warn("Ignoring invalid workbench filter layer {}: {}", id, ex.toString());
            }
        }
        if (merged == null) throw new IOException("No readable workbench filter for " + id);
        return new ByteArrayInputStream(CommonAssetsManager.GSON.toJson(merged).getBytes(StandardCharsets.UTF_8));
    }

    public boolean contains(Identifier location) {
        boolean allowed = whitelist.isEmpty();
        for (IFilter<Identifier> filter : this.whitelist) {
            if (filter.test(location)) {
                allowed = true;
                break;
            }
        }
        for (IFilter<Identifier> filter : this.blacklist) {
            if (filter.test(location)) {
                allowed = false;
                break;
            }
        }
        return allowed;
    }

    public List<Identifier> filter(List<Identifier> input) {
        List<Identifier> output = new ArrayList<>();
        for (Identifier location : input) {
            if (contains(location)) {
                output.add(location);
            }
        }
        return output;
    }

    public <T> List<T> filter(List<T> input, Function<T, Identifier> getter) {
        List<T> output = new ArrayList<>();
        for (T entry : input) {
            if (contains(getter.apply(entry))) {
                output.add(entry);
            }
        }
        return output;
    }


    public static class Deserializer implements JsonDeserializer<RecipeFilter>, JsonSerializer<RecipeFilter> {
        @Override
        public RecipeFilter deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            RecipeFilter filter = new RecipeFilter();
            JsonArray wl = json.getAsJsonObject().getAsJsonArray("whitelist");
            if (wl != null) {
                loadFilters(wl, filter.whitelist);
            }
            JsonArray bl = json.getAsJsonObject().getAsJsonArray("blacklist");
            if (bl != null) {
                loadFilters(bl, filter.blacklist);
            }
            return filter;
        }

        private void loadFilters(JsonArray array, @NotNull List<IFilter<Identifier>> list) {
            // A missing or empty whitelist means unrestricted (before the blacklist). The wire
            // serializer writes [] even if the author omitted the key. An empty LiteralFilter
            // here would make whitelist.isEmpty() false and reject every recipe on remote clients.
            if (array.isEmpty()) return;
            LiteralFilter.Builder<Identifier> builder = new LiteralFilter.Builder<>();
            for (JsonElement element : array) {
                if (element.isJsonPrimitive()) {
                    String entry = element.getAsString();
                    if (entry.startsWith("^")) {
                        try {
                            list.add(new RegexFilter<>(entry));
                        } catch (PatternSyntaxException e) {
                            GunMod.LOGGER.error("Failed to parse regex filter: {}", entry, e);
                        }
                    } else {
                        Identifier rl = Identifier.tryParse(entry);
                        if (rl != null) {
                            builder.add(rl);
                        }
                    }
                } else {
                    throw new JsonParseException("Invalid recipe filter entry: " + element);
                }
            }
            list.add(builder.build());
        }

        @Override
        public JsonElement serialize(RecipeFilter filter, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject json = new JsonObject();

            json.add("whitelist", context.serialize(toList(filter.whitelist)));
            json.add("blacklist", context.serialize(toList(filter.blacklist)));
            return json;
        }

        private List<String> toList(List<IFilter<Identifier>> filter) {
            List<String> list = new ArrayList<>();
            for (var f : filter) {
                if (f instanceof LiteralFilter<Identifier> literalFilter) {
                    list.addAll(literalFilter.getSet().stream().map(Identifier::toString).toList());
                } else if (f instanceof RegexFilter<Identifier> regexFilter) {
                    list.add(regexFilter.getPattern().pattern());
                }
            }
            // A *nonempty*, wholly malformed list matched nothing before serialization. Do not
            // turn it into an unrestricted whitelist when transferred or merged across packs.
            return !filter.isEmpty() && list.isEmpty() ? List.of("^(?!)$") : list;
        }
    }
}
