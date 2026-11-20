package com.tacz.guns.resource;

import cn.sh1rocu.tacz.util.forge.DelegatingPackResources;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.resource.ResourceManager;
import com.tacz.guns.config.PreLoadConfig;
import com.tacz.guns.util.GetJarResources;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.InclusiveRange;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

public enum GunPackLoader implements RepositorySource {
    INSTANCE;
    public PackType packType;
    private boolean firstLoad = true;
    private volatile List<String> serverWarnings = List.of(), clientWarnings = List.of();
    public List<String> getPacksNeedingConversion(PackType type) {
        return type == PackType.SERVER_DATA ? serverWarnings : clientWarnings;
    }
    public static boolean needsConversion(String required, Version minecraft, List<String> overlays) throws VersionParsingException {
        return required != null && !GunPackVersions.matches(required, minecraft) && overlays.isEmpty();
    }

    /** Capture the repository's type; the two repositories coexist on an integrated server. */
    public static RepositorySource forType(PackType type) {
        return output -> INSTANCE.loadPacks(output, type);
    }

    @Override
    public void loadPacks(Consumer<Pack> output) {
        loadPacks(output, packType);
    }

    private void loadPacks(Consumer<Pack> output, PackType type) {
        Pack pack = discoverExtensions(type);
        if (pack != null) output.accept(pack);
    }

    public Pack discoverExtensions() {
        return discoverExtensions(packType);
    }

    private synchronized void prepareDirectory(Path root) throws IOException {
        Files.createDirectories(root);
        PreLoadConfig.load(root);
        if (firstLoad && !PreLoadConfig.override.get()) {
            for (ResourceManager.ExtraEntry entry : ResourceManager.EXTRA_ENTRIES) {
                GetJarResources.copyModDirectory(entry.modMainClass(), entry.srcPath(), root, entry.extraDirName());
            }
        }
        firstLoad = false;
    }

    private Pack discoverExtensions(PackType type) {
        Path root = FabricLoader.getInstance().getGameDir().resolve("tacz");
        List<GunPack> packs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            prepareDirectory(root);
            Version minecraft = Version.parse(SharedConstants.getCurrentVersion().name());
            try (var paths = Files.list(root)) {
                for (Path path : paths.sorted().toList()) {
                    if (!Files.isDirectory(path) && !path.toString().endsWith(".zip")) continue;
                    try {
                        var meta = readMeta(path);
                        if (meta == null || meta.getName() == null || meta.getName().isBlank()) throw new IOException("Missing namespace");
                        if (!GunPackVersions.dependenciesMatch(meta.getDependencies(), true)) {
                            GunMod.LOGGER.warn("Skipping gun pack {}: mod dependency mismatch", path.getFileName());
                            continue;
                        }
                        var requiredMc = meta.getDependencies() == null ? null : meta.getDependencies().get("minecraft");
                        if (requiredMc != null && !GunPackVersions.matches(requiredMc, minecraft)) {
                            GunMod.LOGGER.warn("Gun pack {} does not declare support for Minecraft {} ({}); trying its base and matching overlays",
                                    path.getFileName(), minecraft, requiredMc);
                        }
                        var overlays = GunPackOverlayResolver.resolve(meta, minecraft);
                        if (needsConversion(requiredMc, minecraft, overlays)) warnings.add(path.getFileName().toString());
                        packs.add(new GunPack(path, meta.getName(), overlays));
                    } catch (IOException | JsonParseException | VersionParsingException | IllegalArgumentException e) {
                        GunMod.LOGGER.warn("Skipping gun pack {}: {}", path, e.toString());
                    }
                }
            }
        } catch (IOException | VersionParsingException e) {
            GunMod.LOGGER.error("Failed to scan gun packs at {}", root, e);
            return null;
        }
        if (type == PackType.SERVER_DATA) serverWarnings = List.copyOf(warnings);
        else clientWarnings = List.copyOf(warnings);
        GunMod.LOGGER.info("Found {} possible gunpack(s) in {}", packs.size(), root);
        var location = new PackLocationInfo("tacz_resources", Component.literal("TACZ Resources"), PackSource.BUILT_IN, Optional.empty());
        var meta = new PackMetadataSection(Component.translatable("tacz.resources.modresources"),
                new InclusiveRange<>(SharedConstants.getCurrentVersion().packVersion(type)));
        return Pack.readMetaAndCreate(location, supplier(meta, packs), type, new PackSelectionConfig(true, Pack.Position.BOTTOM, false));
    }

    static Pack.ResourcesSupplier supplier(PackMetadataSection meta, List<GunPack> packs) {
        return new Pack.ResourcesSupplier() {
            private DelegatingPackResources resources(PackLocationInfo info, List<List<PackResources>> packs) {
                return new DelegatingPackResources(info.id(), meta, packs) {
                    @Override public IoSupplier<InputStream> getRootResource(String... paths) {
                        if (paths.length == 1 && "pack.png".equals(paths[0])) {
                            Path icon = getModIcon(GunMod.MOD_ID);
                            if (icon != null) return IoSupplier.create(icon);
                        }
                        return null;
                    }
                };
            }

            @Override public PackResources openPrimary(PackLocationInfo info) {
                return resources(info, List.of()); // Pack.readMetaAndCreate closes this immediately
            }

            @Override public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                List<List<PackResources>> layers = new ArrayList<>();
                try {
                    for (GunPack pack : packs) {
                        try {
                            layers.add(GunPackOverlayResolver.open(pack.path, pack.name, pack.overlays));
                        } catch (IOException | RuntimeException e) {
                            GunMod.LOGGER.warn("Skipping unreadable gun pack {}: {}", pack.path, e.toString());
                        }
                    }
                    return resources(info, layers);
                } catch (RuntimeException | Error e) {
                    layers.forEach(pack -> pack.forEach(PackResources::close));
                    throw e;
                }
            }
        };
    }

    static PackMeta readMeta(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var reader = Files.newBufferedReader(path.resolve("gunpack.meta.json"))) {
                return CommonAssetsManager.GSON.fromJson(reader, PackMeta.class);
            }
        }
        try (var zip = new ZipFile(path.toFile())) {
            var entry = zip.getEntry("gunpack.meta.json");
            if (entry == null) throw new IOException("No gunpack.meta.json at archive root");
            try (var reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                return CommonAssetsManager.GSON.fromJson(reader, PackMeta.class);
            }
        }
    }

    public static @Nullable Path getModIcon(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).flatMap(m -> m.findPath("icon.png")).orElse(null);
    }

    public record GunPack(Path path, String name, List<String> overlays) {
        public GunPack(Path path, String name) { this(path, name, List.of()); }
    }
}
