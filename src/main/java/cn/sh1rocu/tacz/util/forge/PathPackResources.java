package cn.sh1rocu.tacz.util.forge;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FileUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PathPackResources extends AbstractPackResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Path source;

    public PathPackResources(String packId, boolean isBuiltin, final Path source) {
        super(new PackLocationInfo(packId, Component.literal(packId), PackSource.DEFAULT, Optional.empty()));
        this.source = source;
    }

    public Path getSource() {
        return this.source;
    }

    protected Path resolve(String... paths) {
        Path path = getSource();
        for (String name : paths)
            path = path.resolve(name);
        return path;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        final Path path = resolve(paths);
        if (!Files.exists(path))
            return null;

        return IoSupplier.create(path);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput resourceOutput) {
        // 26.2 单复数兼容：recipe/loot_table/tags 等
        if (type == PackType.SERVER_DATA) {
            String legacy = cn.sh1rocu.tacz.util.RecipeCompat.getLegacyForCurrent(path);
            boolean isRecipe = "recipe".equals(path);
            if (isRecipe) {
                // recipe 需要转换包装
                ResourceOutput transformingOutput = (location, supplier) -> {
                    var wrapped = cn.sh1rocu.tacz.util.RecipeCompat.wrapSupplierForRecipe(location, supplier);
                    resourceOutput.accept(location, wrapped);
                };
                FileUtil.decomposePath(path).result().ifPresent(parts ->
                        net.minecraft.server.packs.PathPackResources.listPath(namespace, resolve(type.getDirectory(), namespace).toAbsolutePath(), parts, transformingOutput));
                if (legacy != null) {
                    FileUtil.decomposePath(legacy).result().ifPresent(legacyParts -> {
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
                                        } catch (Exception ignore) {}
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
                        try {
                            net.minecraft.server.packs.PathPackResources.listPath(namespace, resolve(type.getDirectory(), namespace).toAbsolutePath(), legacyParts, legacyOutput);
                        } catch (Exception ignored) {}
                    });
                }
                return;
            } else if (legacy != null) {
                // 非 recipe 的 data 路径（如 loot_table, tags/block），直接映射
                FileUtil.decomposePath(path).result().ifPresent(parts ->
                        net.minecraft.server.packs.PathPackResources.listPath(namespace, resolve(type.getDirectory(), namespace).toAbsolutePath(), parts, resourceOutput));
                FileUtil.decomposePath(legacy).result().ifPresent(legacyParts -> {
                    ResourceOutput legacyOutput = (location, supplier) -> {
                        var remapped = cn.sh1rocu.tacz.util.RecipeCompat.remapLegacyToCurrent(location);
                        resourceOutput.accept(remapped, supplier);
                    };
                    try {
                        net.minecraft.server.packs.PathPackResources.listPath(namespace, resolve(type.getDirectory(), namespace).toAbsolutePath(), legacyParts, legacyOutput);
                    } catch (Exception ignored) {}
                });
                return;
            }
        }
        FileUtil.decomposePath(path).result().ifPresent(parts ->
                net.minecraft.server.packs.PathPackResources.listPath(namespace, resolve(type.getDirectory(), namespace).toAbsolutePath(), parts, resourceOutput));
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return getNamespacesFromDisk(type);
    }

    @NotNull
    private Set<String> getNamespacesFromDisk(final PackType type) {
        try {
            Path root = resolve(type.getDirectory());
            try (Stream<Path> walker = Files.walk(root, 1)) {
                return walker
                        .filter(Files::isDirectory)
                        .map(root::relativize)
                        .filter(p -> p.getNameCount() > 0)
                        .map(p -> p.toString().replaceAll("/$", ""))
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
            }
        } catch (IOException e) {
            if (type == PackType.SERVER_DATA) {
                return this.getNamespaces(PackType.CLIENT_RESOURCES);
            } else {
                return Collections.emptySet();
            }
        }
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        IoSupplier<InputStream> sup = this.getRootResource(getPathFromLocation(location.getPath().startsWith("lang/") ? PackType.CLIENT_RESOURCES : type, location));
        if (sup == null && type == PackType.SERVER_DATA) {
            // 尝试旧路径回退（recipe/loot_table/tags 等）
            Identifier legacy = cn.sh1rocu.tacz.util.RecipeCompat.remapCurrentToLegacy(location);
            if (!legacy.equals(location)) {
                sup = this.getRootResource(getPathFromLocation(type, legacy));
            }
        }
        if (sup != null && type == PackType.SERVER_DATA && cn.sh1rocu.tacz.util.RecipeCompat.isRecipePath(location)) {
            return cn.sh1rocu.tacz.util.RecipeCompat.wrapSupplierForRecipe(location, sup);
        }
        return sup;
    }

    private static String[] getPathFromLocation(PackType type, Identifier location) {
        String[] parts = location.getPath().split("/");
        String[] result = new String[parts.length + 2];
        result[0] = type.getDirectory();
        result[1] = location.getNamespace();
        System.arraycopy(parts, 0, result, 2, parts.length);
        return result;
    }

    @Override
    public void close() {
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s: %s (%s)", getClass().getName(), this.packId(), getSource());
    }
}
