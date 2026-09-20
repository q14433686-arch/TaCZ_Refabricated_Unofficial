package com.tacz.guns.resource;

import cn.sh1rocu.tacz.util.forge.DelegatingPackResources;
import cn.sh1rocu.tacz.util.forge.PathPackResources;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.resource.ResourceManager;
import com.tacz.guns.config.PreLoadConfig;
import com.tacz.guns.util.GetJarResources;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.minecraft.SharedConstants;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.util.InclusiveRange;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public enum GunPackLoader implements RepositorySource {
    INSTANCE;
    private static final Marker MARKER = MarkerFactory.getMarker("GunPackFinder");
    /**
     * 26.3 起 {@code Pack.ResourcesSupplier#openResources} 需要一个 {@link Pack.Metadata}。
     * 枪包的 zip 走的是"直接打开主 pack"这条路，不读 pack.mcmeta、也不使用 overlay，
     * 所以这里给一个空描述、标记为兼容、无附加 feature flag、无 overlay 的占位值。
     * 它只影响 {@code openResources} 内部要不要再去叠加 overlay 子 pack（我们不需要）。
     */
    private static final Pack.Metadata EMPTY_PACK_METADATA = new Pack.Metadata(
            Component.empty(), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());
    public PackType packType;
    private boolean firstLoad = true;


    @Override
    public void loadPacks(Consumer<Pack> pOnLoad) {
        Pack extensionsPack = discoverExtensions();
        if (extensionsPack != null) {
            pOnLoad.accept(extensionsPack);
        }
    }

    public Pack discoverExtensions() {
        Path resourcePacksPath = FabricLoader.getInstance().getGameDir().resolve("tacz");
        File folder = resourcePacksPath.toFile();
        if (!folder.isDirectory()) {
            try {
                Files.createDirectories(folder.toPath());
            } catch (Exception e) {
                GunMod.LOGGER.warn(MARKER, "Failed to init tacz resource directory...", e);
                return null;
            }
        }

        // 确保配置文件加载，这个阶段将比标准的forge配置文件加载早
        PreLoadConfig.load(resourcePacksPath);

        // 仅在第一次加载时复制默认资源包
        if (firstLoad) {
            if (!PreLoadConfig.override.get()) {
                for (ResourceManager.ExtraEntry entry : ResourceManager.EXTRA_ENTRIES) {
                    GetJarResources.copyModDirectory(entry.modMainClass(), entry.srcPath(), resourcePacksPath, entry.extraDirName());
                }
            }
            firstLoad = false;
        }

        GunMod.LOGGER.info(MARKER, "Start scanning for gun packs in {}", resourcePacksPath);
        List<GunPack> gunPacks = scanExtensions(resourcePacksPath);
        GunMod.LOGGER.info(MARKER, "Found {} possible gunpack(s) and added them to resource set.", gunPacks.size());
        List<PackResources> extensionPacks = new ArrayList<>();

        for (GunPack gunPack : gunPacks) {
            PackResources packResources;
            if (Files.isDirectory(gunPack.path)) {
                packResources = new PathPackResources(gunPack.name, false, gunPack.path) {
                    @Override
                    @NotNull
                    protected Path resolve(String... paths) {
                        if (paths.length < 1) {
                            throw new IllegalArgumentException("Missing path");
                        } else {
                            return gunPack.path.resolve(String.join("/", paths));
                        }
                    }
                };
            } else {
                // 26.3: Pack.ResourcesSupplier 的 openPrimary/openFull 换成了
                // openMetadata(location) / openResources(location, metadata)，后者返回 Stream。
                // 这里要的就是"主 pack 本体"，取 openResources 的第一个元素即可
                // （overlay 由 metadata.overlays() 驱动，枪包不使用）。
                PackLocationInfo zipLocation = new PackLocationInfo(gunPack.name, Component.literal(gunPack.name), PackSource.DEFAULT, Optional.empty());
                packResources = new FilePackResources.FileResourcesSupplier(gunPack.path)
                        .openResources(zipLocation, EMPTY_PACK_METADATA)
                        .findFirst()
                        .orElse(null);
                if (packResources == null) {
                    GunMod.LOGGER.warn(MARKER, "Failed to open gun pack archive {}, skipped.", gunPack.path);
                    continue;
                }
            }
            extensionPacks.add(packResources);
        }


        PackLocationInfo location = new PackLocationInfo("tacz_resources", Component.literal("TACZ Resources"), PackSource.BUILT_IN, Optional.empty());
        // 26.3: ResourcesSupplier 从 openPrimary/openFull 改成 openMetadata/openResources。
        // openMetadata 只用来读 pack.mcmeta（返回 PackMetadataResources），
        // openResources 返回真正参与资源查找的 pack 流。两者都交给同一个
        // DelegatingPackResources 实例工厂，行为与 26.2 的 openPrimary 一致。
        Pack.ResourcesSupplier resourcesSupplier = new Pack.ResourcesSupplier() {
            private PackResources open(PackLocationInfo locationInfo) {
                return new DelegatingPackResources(locationInfo.id(), false, new PackMetadataSection(Component.translatable("tacz.resources.modresources"),
                        new InclusiveRange<>(SharedConstants.getCurrentVersion().packVersion(packType))), extensionPacks) {
                    @Override
                    public IoSupplier<InputStream> getRootResource(String... paths) {
                        if (paths.length == 1 && paths[0].equals("pack.png")) {
                            Path logoPath = getModIcon("tacz");
                            if (logoPath != null) {
                                return IoSupplier.create(logoPath);
                            }
                        }
                        return null;
                    }
                };
            }

            @Override
            public PackMetadataResources openMetadata(PackLocationInfo locationInfo) {
                return open(locationInfo);
            }

            @Override
            public Stream<PackResources> openResources(PackLocationInfo locationInfo, Pack.Metadata metadata) {
                return Stream.of(open(locationInfo));
            }
        };
        return Pack.readMetaAndCreate(location, resourcesSupplier, packType, new PackSelectionConfig(true, Pack.Position.BOTTOM, false));
    }

    public static @Nullable Path getModIcon(String modId) {
        Optional<ModContainer> m = FabricLoader.getInstance().getModContainer(modId);
        if (m.isPresent()) {
            Optional<Path> logoPath = m.get().findPath("icon.png");
            if (logoPath.isPresent()) {
                if (Files.exists(logoPath.get())) {
                    return logoPath.get();
                }
            }
        }

        return null;
    }

    // 检查路径中的config.json
    // 应该不会在用这个了，先保留
//    private static RepositoryConfig checkConfig(Path resourcePacksPath) {
//        Path configPath = resourcePacksPath.resolve("config.json");
//        if (Files.exists(configPath)) {
//            try (InputStream stream = Files.newInputStream(configPath)) {
//                return GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), RepositoryConfig.class);
//            } catch (IOException | JsonSyntaxException | JsonIOException e) {
//                GunMod.LOGGER.warn(MARKER, "Failed to read config json: {}", configPath);
//            }
//        }
//        // 不存在或者出问题了，新建一个
//        RepositoryConfig config = new RepositoryConfig(true);
//        // 使用Gson写文件
//        try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
//            GSON.toJson(config, writer);
//        } catch (IOException e) {
//            GunMod.LOGGER.warn(MARKER, "Failed to init config json: {}", configPath);
//        }
//        return config;
//    }

    private static GunPack fromDirPath(Path path) throws IOException {
        Path packInfoFilePath = path.resolve("gunpack.meta.json");
        try (InputStream stream = Files.newInputStream(packInfoFilePath)) {
            PackMeta info = CommonAssetsManager.GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), PackMeta.class);

            if (info == null) {
                GunMod.LOGGER.warn(MARKER, "Failed to read info json: {}", packInfoFilePath.getFileName());
                return null;
            }

            if (info.getDependencies() != null && !modVersionAllMatch(info)) {
                GunMod.LOGGER.warn(MARKER, "Mod version mismatch: {}", packInfoFilePath.getFileName());
                return null;
            }

            return new GunPack(path, info.getName());
        } catch (IOException | JsonSyntaxException | JsonIOException | VersionParsingException exception) {
            GunMod.LOGGER.warn(MARKER, "Failed to read info json: {}", packInfoFilePath.getFileName());
            GunMod.LOGGER.warn(exception.getMessage());
        }
        return null;
    }

    private static GunPack fromZipPath(Path path) {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            ZipEntry extDescriptorEntry = zipFile.getEntry("gunpack.meta.json");
            if (extDescriptorEntry == null) {
                GunMod.LOGGER.error(MARKER, "Failed to load extension from ZIP {}. Error: {}", path.getFileName(), "No gunpack.meta.json found");
                return null;
            }

            try (InputStream stream = zipFile.getInputStream(extDescriptorEntry)) {
                PackMeta info = CommonAssetsManager.GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), PackMeta.class);

                if (info == null) {
                    GunMod.LOGGER.warn(MARKER, "Failed to read info json: {}", path.getFileName());
                    return null;
                }

                if (info.getDependencies() != null && !modVersionAllMatch(info)) {
                    GunMod.LOGGER.warn(MARKER, "Mod version mismatch: {}", path.getFileName());
                    return null;
                }

                return new GunPack(path, info.getName());
            } catch (IOException | JsonSyntaxException | JsonIOException | VersionParsingException e) {
                GunMod.LOGGER.error(MARKER, "Failed to load extension from ZIP {}. Error: {}", path.getFileName(), e);
                return null;
            }
        } catch (IOException e) {
            GunMod.LOGGER.error(MARKER, "Failed to load extension from ZIP {}. Error: {}", path.getFileName(), e);
            return null;
        }
    }

    private static List<GunPack> scanExtensions(Path extensionsPath) {
        List<GunPack> gunPacks = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(extensionsPath)) {
            for (Path entry : stream) {
                GunPack gunPack = null;
                if (Files.isDirectory(entry)) {
                    gunPack = fromDirPath(entry);
                } else if (entry.toString().endsWith(".zip")) {
                    gunPack = fromZipPath(entry);
                }
                if (gunPack != null) {
                    GunMod.LOGGER.info(MARKER, "- {}, Main namespace: {}", gunPack.path.getFileName(), gunPack.name);
                    gunPacks.add(gunPack);
                }
            }
        } catch (IOException e) {
            GunMod.LOGGER.error(MARKER, "Failed to scan extensions from {}. Error: {}", extensionsPath, e);
        }

        return gunPacks;
    }

    private static boolean modVersionAllMatch(PackMeta info) throws VersionParsingException {
        HashMap<String, String> dependencies = info.getDependencies();
        for (String modId : dependencies.keySet()) {
            if (!modVersionMatch(modId, dependencies.get(modId))) {
                return false;
            }
        }
        return true;
    }

    private static boolean modVersionMatch(String modId, String version) throws VersionParsingException {
        VersionPredicate versionRange = VersionPredicate.parse(version);
        if ("lrtactical".equals(modId)) {
            // LRTactical is bundled into this 26.2 test build, not shipped as a separate Fabric mod jar.
            // Gun packs copied from the original add-on still declare a dependency on mod id "lrtactical";
            // satisfy that check with the upstream LRTactical version we ported from.
            return versionRange.test(Version.parse("0.3.0"));
        }
        return FabricLoader.getInstance().getModContainer(modId).map(mod -> {
            Version modVersion = mod.getMetadata().getVersion();
            return versionRange.test(modVersion);
        }).orElse(false);
    }


    public record GunPack(Path path, String name) {
    }
}
