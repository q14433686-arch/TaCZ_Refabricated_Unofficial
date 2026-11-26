package cn.sh1rocu.tacz.util.forge;

import com.tacz.guns.resource.filter.RecipeFilter;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.Component;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class DelegatingPackResources extends AbstractPackResources {
    private final PackMetadataSection packMeta;
    private final List<List<PackResources>> gunPacks;
    private final List<PackResources> delegates;
    private final Map<String, List<PackResources>> namespacesAssets;
    private final Map<String, List<PackResources>> namespacesData;

    public DelegatingPackResources(String packId, boolean isBuiltin, PackMetadataSection packMeta, List<? extends PackResources> packs) {
        this(packId, packMeta, packs.stream().map(p -> List.<PackResources>of(p)).toList());
    }

    /** Each gun pack contributes its highest matching overlay, then its base if no overlay supplies the file. */
    public DelegatingPackResources(String packId, PackMetadataSection packMeta, List<List<PackResources>> gunPacks) {
        super(new PackLocationInfo(packId, Component.literal(packId), PackSource.DEFAULT, Optional.empty()));
        this.packMeta = packMeta;
        this.gunPacks = gunPacks.stream().map(List::copyOf).toList();
        this.delegates = this.gunPacks.stream().flatMap(List::stream).toList();
        this.namespacesAssets = this.buildNamespaceMap(PackType.CLIENT_RESOURCES, delegates);
        this.namespacesData = this.buildNamespaceMap(PackType.SERVER_DATA, delegates);
    }

    private Map<String, List<PackResources>> buildNamespaceMap(PackType type, List<PackResources> packList) {
        Map<String, List<PackResources>> map = new HashMap<>();
        for (PackResources pack : packList) {
            for (String namespace : pack.getNamespaces(type)) {
                map.computeIfAbsent(namespace, k -> new ArrayList<>()).add(pack);
            }
        }
        map.replaceAll((k, list) -> ImmutableList.copyOf(list));
        return ImmutableMap.copyOf(map);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionType<T> deserializer) throws IOException {
        return deserializer.name().equals("pack") ? (T) this.packMeta : null;
    }

    @Override
    public void listResources(PackType type, String resourceNamespace, String paths, PackResources.ResourceOutput resourceOutput) {
        // Same winner for list and direct lookup, including current vs legacy recipe aliases.
        // All disk/zip layers now go through PathPackResources, which already normalizes recipes.
        Set<Identifier> seen = new HashSet<>();
        ResourceOutput emitOnce = (id, supplier) -> {
            if (seen.add(id)) resourceOutput.accept(id, (isAttachmentTag(type, id) || isRecipeFilter(type, id))
                    ? getResource(type, id) : supplier);
        };
        for (PackResources pack : getCandidatePacks(type, resourceNamespace)) {
            pack.listResources(type, resourceNamespace, paths, emitOnce);
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? namespacesAssets.keySet() : namespacesData.keySet();
    }

    @Override
    public void close() {
        for (PackResources pack : delegates) {
            pack.close();
        }
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        if (isRecipeFilter(type, location)) {
            // RecipeFilterManager normally merges a resource stack. The repository exposes all
            // gun packs as ONE pack, so return one effective filter per contributing gun pack.
            List<IoSupplier<InputStream>> parts = new ArrayList<>();
            for (var pack : gunPacks) {
                for (var layer : pack) {
                    var part = layer.getResource(type, location);
                    if (part != null) {
                        parts.add(part);
                        break;
                    }
                }
            }
            if (parts.isEmpty()) return null;
            return parts.size() == 1 ? parts.getFirst() : () -> RecipeFilter.mergeLayers(location, parts);
        }
        if (isAttachmentTag(type, location)) {
            List<IoSupplier<InputStream>> parts = new ArrayList<>();
            for (PackResources pack : getCandidatePacks(type, location.getNamespace())) {
                var part = pack.getResource(type, location);
                if (part != null) parts.add(part);
            }
            if (parts.isEmpty()) return null;
            // A composite pack hides its children from listMatchingResourceStacks. These tags
            // are additive arrays, not winner-takes-all indexes; preserve all child values.
            return () -> com.tacz.guns.util.AttachmentTagLayers.merge(location.toString(), parts);
        }
        for (PackResources pack : getCandidatePacks(type, location.getNamespace())) {
            var supplier = pack.getResource(type, location);
            if (supplier != null) {
                return supplier;
            }
        }
        return null;
    }

    private static boolean isRecipeFilter(PackType type, Identifier id) {
        return type == PackType.SERVER_DATA && id.getPath().startsWith("recipe_filters/") && id.getPath().endsWith(".json");
    }

    private static boolean isAttachmentTag(PackType type, Identifier id) {
        return type == PackType.SERVER_DATA && id.getPath().startsWith("tacz_tags/attachments/")
                && id.getPath().endsWith(".json");
    }

    @Nullable
    public Collection<PackResources> getChildren() {
        return delegates;
    }

    private List<PackResources> getCandidatePacks(PackType type, String namespace) {
        Map<String, List<PackResources>> map = type == PackType.CLIENT_RESOURCES ? namespacesAssets : namespacesData;
        List<PackResources> packsWithNamespace = map.get(namespace);
        return packsWithNamespace == null ? Collections.emptyList() : packsWithNamespace;
    }
}
