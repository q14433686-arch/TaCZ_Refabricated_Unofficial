package cn.sh1rocu.tacz.util.forge;

import com.google.common.collect.ImmutableList;
import com.tacz.guns.industry.reference.IndustryGunPackPreflight;
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
    private final List<PackResources> delegates;
    private final Map<String, List<PackResources>> namespacesAssets;
    private final Map<String, List<PackResources>> namespacesData;

    public DelegatingPackResources(String packId, boolean isBuiltin, PackMetadataSection packMeta, List<? extends PackResources> packs) {
        super(new PackLocationInfo(packId, Component.literal(packId), PackSource.DEFAULT, Optional.empty()));
        this.packMeta = packMeta;
        this.delegates = ImmutableList.copyOf(packs);
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
    public void listResources(PackType type, String resourceNamespace, String paths, ResourceOutput resourceOutput) {
        boolean isRecipe = type == PackType.SERVER_DATA && "recipe".equals(paths);
        String legacyPath = type == PackType.SERVER_DATA ? cn.sh1rocu.tacz.util.RecipeCompat.getLegacyForCurrent(paths) : null;

        if (isRecipe) {
            ResourceOutput transformingOutput = (location, supplier) -> {
                var wrapped = cn.sh1rocu.tacz.util.RecipeCompat.wrapSupplierForRecipe(location, supplier);
                resourceOutput.accept(location, wrapped);
            };
            listDelegateResourcesWithExtensionAliases(type, resourceNamespace, paths, transformingOutput, true);
            if (legacyPath != null) {
                ResourceOutput legacyOutput = (location, supplier) -> {
                    var remapped = cn.sh1rocu.tacz.util.RecipeCompat.remapLegacyToCurrent(location);
                    try {
                        try (var in = supplier.get()) {
                            byte[] bytes = in.readAllBytes();
                            String txt = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim();
                            if (!txt.isEmpty()) {
                                try {
                                    var je = com.google.gson.JsonParser.parseString(txt);
                                    if (je.isJsonObject()) {
                                        var obj = je.getAsJsonObject();
                                        if (!cn.sh1rocu.tacz.util.RecipeCompat.isVanillaRecipeType(obj)) {
                                            return;
                                        }
                                    }
                                } catch (Exception ignoreParse) {}
                            }
                            IoSupplier<InputStream> fixedSupplier = () -> {
                                try (var in2 = supplier.get()) {
                                    return cn.sh1rocu.tacz.util.RecipeCompat.transformStreamIfNeeded(in2);
                                }
                            };
                            resourceOutput.accept(remapped, fixedSupplier);
                        }
                    } catch (Exception e) {
                        var wrapped = cn.sh1rocu.tacz.util.RecipeCompat.wrapSupplierForRecipe(remapped, supplier);
                        resourceOutput.accept(remapped, wrapped);
                    }
                };
                for (PackResources delegate : this.delegates) {
                    try {
                        delegate.listResources(type, resourceNamespace, legacyPath, legacyOutput);
                    } catch (Exception ignored) {}
                }
            }
        } else if (legacyPath != null && type == PackType.SERVER_DATA) {
            // 非 recipe 的兼容路径（如 loot_table, tags/block）
            for (PackResources delegate : this.delegates) {
                try {
                    delegate.listResources(type, resourceNamespace, paths, resourceOutput);
                } catch (Exception ignored) {}
            }
            ResourceOutput legacyOutput = (location, supplier) -> {
                var remapped = cn.sh1rocu.tacz.util.RecipeCompat.remapLegacyToCurrent(location);
                resourceOutput.accept(remapped, supplier);
            };
            for (PackResources delegate : this.delegates) {
                try {
                    delegate.listResources(type, resourceNamespace, legacyPath, legacyOutput);
                } catch (Exception ignored) {}
            }
        } else {
            listDelegateResourcesWithExtensionAliases(type, resourceNamespace, paths, resourceOutput, false);
        }
    }

    /**
     * FileToIdConverter only accepts physical *.json paths. Legacy gun packs
     * occasionally omit that suffix while still storing a JSON object. Expose
     * a virtual sibling ending in .json only for the narrow TACZ data families
     * classified by IndustryGunPackPreflight; never rename arbitrary pack data.
     */
    private void listDelegateResourcesWithExtensionAliases(PackType type, String namespace, String paths,
                                                           ResourceOutput output, boolean suppressDelegateFailures) {
        List<ListedResource> listed = new ArrayList<>();
        ResourceOutput collector = (location, supplier) -> listed.add(new ListedResource(location, supplier));
        for (PackResources delegate : this.delegates) {
            if (!suppressDelegateFailures) {
                delegate.listResources(type, namespace, paths, collector);
                continue;
            }
            try {
                delegate.listResources(type, namespace, paths, collector);
            } catch (Exception ignored) {
                // The recipe compatibility branch historically isolated a
                // malformed optional delegate; preserve that narrow behaviour.
            }
        }
        Set<Identifier> physicalLocations = new HashSet<>();
        for (ListedResource resource : listed) {
            physicalLocations.add(resource.location());
            output.accept(resource.location(), resource.supplier());
        }
        if (type != PackType.SERVER_DATA) {
            return;
        }
        Set<Identifier> emittedAliases = new HashSet<>();
        for (ListedResource resource : listed) {
            Identifier alias = IndustryGunPackPreflight.normalizedJsonLocation(resource.location());
            if (alias == null || physicalLocations.contains(alias) || !emittedAliases.add(alias)
                    || !IndustryGunPackPreflight.looksLikeJson(resource.supplier())) {
                continue;
            }
            output.accept(alias, resource.supplier());
        }
    }

    private record ListedResource(Identifier location, IoSupplier<InputStream> supplier) {
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
        for (PackResources pack : getCandidatePacks(type, location)) {
            IoSupplier<InputStream> ioSupplier = pack.getResource(type, location);
            if (ioSupplier != null) {
                return wrapRecipeSupplier(type, location, ioSupplier);
            }
        }
        IoSupplier<InputStream> extensionless = getExtensionlessJsonAlias(type, location);
        if (extensionless != null) {
            return wrapRecipeSupplier(type, location, extensionless);
        }

        if (type == PackType.SERVER_DATA) {
            Identifier legacy = cn.sh1rocu.tacz.util.RecipeCompat.remapCurrentToLegacy(location);
            if (!legacy.equals(location)) {
                for (PackResources pack : getCandidatePacks(type, legacy)) {
                    IoSupplier<InputStream> ioSupplier = pack.getResource(type, legacy);
                    if (ioSupplier != null) {
                        return wrapRecipeSupplier(type, location, ioSupplier);
                    }
                }
                IoSupplier<InputStream> legacyExtensionless = getExtensionlessJsonAlias(type, legacy);
                if (legacyExtensionless != null) {
                    return wrapRecipeSupplier(type, location, legacyExtensionless);
                }
                for (PackResources pack : this.delegates) {
                    IoSupplier<InputStream> ioSupplier = pack.getResource(type, legacy);
                    if (ioSupplier != null) {
                        return wrapRecipeSupplier(type, location, ioSupplier);
                    }
                }
            }
        }

        return null;
    }

    private IoSupplier<InputStream> wrapRecipeSupplier(PackType type, Identifier requested,
                                                        IoSupplier<InputStream> supplier) {
        return type == PackType.SERVER_DATA && cn.sh1rocu.tacz.util.RecipeCompat.isRecipePath(requested)
                ? cn.sh1rocu.tacz.util.RecipeCompat.wrapSupplierForRecipe(requested, supplier) : supplier;
    }

    @Nullable
    private IoSupplier<InputStream> getExtensionlessJsonAlias(PackType type, Identifier requestedJsonLocation) {
        if (type != PackType.SERVER_DATA || !requestedJsonLocation.getPath().endsWith(".json")) {
            return null;
        }
        Identifier source = requestedJsonLocation.withPath(
                requestedJsonLocation.getPath().substring(0, requestedJsonLocation.getPath().length() - ".json".length())
        );
        Identifier normalized = IndustryGunPackPreflight.normalizedJsonLocation(source);
        if (!requestedJsonLocation.equals(normalized)) {
            return null;
        }
        for (PackResources pack : getCandidatePacks(type, source)) {
            IoSupplier<InputStream> supplier = pack.getResource(type, source);
            if (supplier != null && IndustryGunPackPreflight.looksLikeJson(supplier)) {
                return supplier;
            }
        }
        return null;
    }

    @Nullable
    public Collection<PackResources> getChildren() {
        return delegates;
    }

    private List<PackResources> getCandidatePacks(PackType type, Identifier location) {
        Map<String, List<PackResources>> map = type == PackType.CLIENT_RESOURCES ? namespacesAssets : namespacesData;
        List<PackResources> packsWithNamespace = map.get(location.getNamespace());
        return packsWithNamespace == null ? Collections.emptyList() : packsWithNamespace;
    }
}
