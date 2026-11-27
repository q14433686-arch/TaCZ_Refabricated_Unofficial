package com.tacz.guns.resource.manager;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.util.ResourceScanner;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.*;


public class AttachmentsTagManager extends SimplePreparableReloadListener<Map<Identifier, List<JsonElement>>> implements INetworkCacheReloadListener {
    private final Map<Identifier, Set<String>> tags = Maps.newHashMap();
    private final Map<Identifier, Set<String>> allow_attachments = Maps.newHashMap();

    private final Gson gson;
    private final Marker marker;

    private final FileToIdConverter fileToIdConverter;
    protected Map<Identifier, String> networkCache;

    public AttachmentsTagManager() {
        this.gson = CommonAssetsManager.GSON;
        this.marker = MarkerFactory.getMarker("AllowTagManager");
        this.fileToIdConverter = FileToIdConverter.json("tacz_tags/attachments");
    }

    @NotNull
    @Override
    protected Map<Identifier, List<JsonElement>> prepare(ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        return ResourceScanner.scanDirectoryAll(pResourceManager, this.fileToIdConverter, this.gson);
    }

    @Override
    protected void apply(Map<Identifier, List<JsonElement>> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        tags.clear();
        allow_attachments.clear();
        ImmutableMap.Builder<Identifier, String> builder = ImmutableMap.builder();

        for (Map.Entry<Identifier, List<JsonElement>> entry : pObject.entrySet()) {
            Identifier id = entry.getKey();

            // Upstream 1.2.0: merge every pack's file for this id in pack order, drop duplicates,
            // and publish the same immutable ordered set to the server and to the network cache
            // (CommonNetworkCache#resolveAttachmentTag rebuilds the identical set on the client).
            LinkedHashSet<String> merged = new LinkedHashSet<>();
            for (JsonElement element : entry.getValue()) {
                try {
                    List<String> data = parseJson(element);
                    if (data != null) {
                        if (data.stream().anyMatch(Objects::isNull)) {
                            throw new JsonParseException("Null value found in JSON data");
                        } else {
                            merged.addAll(data);
                        }
                    }
                } catch (JsonParseException e) {
                    GunMod.LOGGER.error(marker, "Failed to parse data file {}", id, e);
                }
            }

            Set<String> ordered = Collections.unmodifiableSet(new LinkedHashSet<>(merged));
            if (id.getPath().startsWith("allow_attachments/") && id.getPath().length() > 18) {
                Identifier gunId = id.withPath(id.getPath().substring(18));
                allow_attachments.put(gunId, ordered);
            } else {
                tags.put(id, ordered);
            }

            builder.put(entry.getKey(), gson.toJson(ordered));
        }

        this.networkCache = builder.build();
    }

    private List<String> parseJson(JsonElement element) {
        return gson.fromJson(element, new TypeToken<>() {
        });
    }

    @Override
    public Map<Identifier, String> getNetworkCache() {
        return networkCache;
    }

    @Override
    public DataType getType() {
        return DataType.ATTACHMENT_TAGS;
    }

    public Set<String> getAttachmentTags(Identifier registryName) {
        return tags.get(registryName);
    }

    public Set<String> getAllowAttachmentTags(Identifier registryName) {
        return allow_attachments.get(registryName);
    }

    public static final Identifier ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "attachments_tag_manager");

    @Override
    public Identifier getFabricId() {
        return null;
    }
}
