package cn.sh1rocu.tacz.util.forge;

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
    /** 1.21.11 vanilla data-pack directory. */
    private static final String RECIPE_DIRECTORY = "recipe";
    /** Pre-1.21 directory still used by a large number of TACZ gun packs. */
    private static final String LEGACY_RECIPE_DIRECTORY = "recipes";

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

    /**
     * Lists current {@code data/<namespace>/recipe/} resources and also exposes old
     * {@code data/<namespace>/recipes/} resources under the current path.
     *
     * <p>The latter is intentionally implemented at the gun-pack aggregate boundary rather than
     * in {@code TableRecipeManager}: a custom table's <em>own construction recipe</em> is a normal
     * vanilla shaped recipe, so only vanilla's recipe loader can make it craftable. Previously we
     * accepted old plural-directory TACZ table recipes in the custom table UI, while vanilla still
     * silently skipped the corresponding recipe that creates the table item itself.
     */
    @Override
    public void listResources(PackType type, String resourceNamespace, String paths, ResourceOutput resourceOutput) {
        String legacyPath = getLegacyRecipePath(type, paths);
        for (PackResources delegate : this.delegates) {
            if (legacyPath == null) {
                delegate.listResources(type, resourceNamespace, paths,
                        (location, supplier) -> resourceOutput.accept(location,
                                migrateRecipeResultIfNeeded(type, location, supplier)));
                continue;
            }

            // A pack may already have migrated a particular file. Its current singular-path form
            // must win over the same relative path left in recipes/.
            Set<Identifier> currentLocations = new HashSet<>();
            delegate.listResources(type, resourceNamespace, paths, (location, supplier) -> {
                currentLocations.add(location);
                resourceOutput.accept(location, migrateRecipeResultIfNeeded(type, location, supplier));
            });
            delegate.listResources(type, resourceNamespace, legacyPath, (legacyLocation, supplier) -> {
                Identifier modernLocation = toModernRecipeLocation(legacyLocation);
                if (modernLocation == null || currentLocations.contains(modernLocation)) {
                    return;
                }
                // recipes/ also contains TACZ's own legacy table recipes. Those remain in the
                // TableRecipeManager-only legacy path; only actual vanilla recipes may be aliased
                // into recipe/ for Minecraft's recipe loader.
                IoSupplier<InputStream> migrated =
                        LegacyGunPackRecipeMigrator.migrateLegacyVanillaRecipe(modernLocation, supplier);
                if (migrated != null) {
                    currentLocations.add(modernLocation);
                    resourceOutput.accept(modernLocation, migrated);
                }
            });
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
        // Root resources do not make sense here
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        Identifier legacyLocation = toLegacyRecipeLocation(type, location);
        for (PackResources pack : getCandidatePacks(type, location)) {
            // Prefer the current path in each pack, then transparently fall back to the old plural
            // path. Doing this per pack preserves the aggregate pack's existing priority order.
            IoSupplier<InputStream> currentSupplier = pack.getResource(type, location);
            if (currentSupplier != null) {
                return migrateRecipeResultIfNeeded(type, location, currentSupplier);
            }
            if (legacyLocation != null) {
                IoSupplier<InputStream> legacySupplier = pack.getResource(type, legacyLocation);
                if (legacySupplier != null) {
                    IoSupplier<InputStream> migrated =
                            LegacyGunPackRecipeMigrator.migrateLegacyVanillaRecipe(location, legacySupplier);
                    if (migrated != null) {
                        return migrated;
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    public Collection<PackResources> getChildren() {
        return delegates;
    }

    @Nullable
    private static String getLegacyRecipePath(PackType type, String path) {
        if (type != PackType.SERVER_DATA) {
            return null;
        }
        if (RECIPE_DIRECTORY.equals(path)) {
            return LEGACY_RECIPE_DIRECTORY;
        }
        if (path.startsWith(RECIPE_DIRECTORY + "/")) {
            return LEGACY_RECIPE_DIRECTORY + path.substring(RECIPE_DIRECTORY.length());
        }
        return null;
    }

    @Nullable
    private static Identifier toLegacyRecipeLocation(PackType type, Identifier location) {
        if (type != PackType.SERVER_DATA) {
            return null;
        }
        String path = location.getPath();
        if (!path.startsWith(RECIPE_DIRECTORY + "/")) {
            return null;
        }
        return Identifier.fromNamespaceAndPath(location.getNamespace(),
                LEGACY_RECIPE_DIRECTORY + path.substring(RECIPE_DIRECTORY.length()));
    }

    @Nullable
    private static Identifier toModernRecipeLocation(Identifier legacyLocation) {
        String path = legacyLocation.getPath();
        if (!path.startsWith(LEGACY_RECIPE_DIRECTORY + "/")) {
            return null;
        }
        return Identifier.fromNamespaceAndPath(legacyLocation.getNamespace(),
                RECIPE_DIRECTORY + path.substring(LEGACY_RECIPE_DIRECTORY.length()));
    }

    private static IoSupplier<InputStream> migrateRecipeResultIfNeeded(PackType type,
                                                                         Identifier location,
                                                                         IoSupplier<InputStream> supplier) {
        if (type == PackType.SERVER_DATA && location.getPath().startsWith(RECIPE_DIRECTORY + "/")) {
            return LegacyGunPackRecipeMigrator.migrate(location, supplier);
        }
        return supplier;
    }

    private List<PackResources> getCandidatePacks(PackType type, Identifier location) {
        Map<String, List<PackResources>> map = type == PackType.CLIENT_RESOURCES ? namespacesAssets : namespacesData;
        List<PackResources> packsWithNamespace = map.get(location.getNamespace());
        return packsWithNamespace == null ? Collections.emptyList() : packsWithNamespace;
    }
}