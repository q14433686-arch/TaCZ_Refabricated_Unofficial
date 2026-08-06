package com.tacz.guns.industry.reference;

import com.tacz.guns.GunMod;
import com.tacz.guns.resource.GunPackLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Early archive-layout diagnostics and safe extensionless-JSON classification
 * for legacy third-party gun packs.
 *
 * <p>Vanilla's {@code FileToIdConverter.json(...)} correctly ignores a file
 * without a {@code .json} suffix. Several old packs nevertheless store JSON
 * payloads under extensionless recipe/index paths, which otherwise looks like
 * an arbitrary missing gun or ammo at runtime. {@code DelegatingPackResources}
 * uses the public classifier below to expose a virtual {@code .json} alias
 * without mutating the archive; this preflight also reports the exact source
 * paths for diagnostics.</p>
 */
public final class IndustryGunPackPreflight {
    private static final int MAX_PATHS_PER_PACK = 8;
    private static final int PREFIX_BYTES = 4096;

    private IndustryGunPackPreflight() {
    }

    public static void inspect(List<GunPackLoader.GunPack> packs) {
        if (packs == null || packs.isEmpty()) {
            return;
        }
        for (GunPackLoader.GunPack pack : packs) {
            if (pack == null || pack.path() == null) {
                continue;
            }
            try {
                List<String> candidates = Files.isDirectory(pack.path())
                        ? inspectDirectory(pack.path()) : inspectZip(pack.path());
                if (!candidates.isEmpty()) {
                    int hidden = Math.max(0, candidates.size() - MAX_PATHS_PER_PACK);
                    List<String> shown = candidates.subList(0, Math.min(MAX_PATHS_PER_PACK, candidates.size()));
                    GunMod.LOGGER.info(
                            "TACZ industry preflight: gun pack {} ({}) exposes {} JSON-looking extensionless data resource(s) through virtual .json aliases. Source path(s): {}{}",
                            pack.path().getFileName(), pack.name(), candidates.size(), shown,
                            hidden == 0 ? "" : " (and " + hidden + " more)"
                    );
                }
            } catch (IOException exception) {
                GunMod.LOGGER.warn("TACZ industry preflight could not inspect gun pack {}", pack.path(), exception);
            }
        }
    }

    /**
     * Return the virtual .json location for one resource-pack identifier, or
     * {@code null} when the path is not one of the TACZ data families we can
     * safely normalize. The caller must still verify the source bytes look
     * like JSON before exposing the alias.
     */
    @Nullable
    public static Identifier normalizedJsonLocation(Identifier location) {
        if (location == null || location.getPath().endsWith(".json") || !relevantResourcePath(location.getPath())) {
            return null;
        }
        return location.withPath(location.getPath() + ".json");
    }

    public static boolean looksLikeJson(IoSupplier<InputStream> supplier) {
        if (supplier == null) {
            return false;
        }
        try (InputStream input = supplier.get()) {
            return looksLikeJson(input);
        } catch (IOException exception) {
            return false;
        }
    }

    private static List<String> inspectZip(Path path) throws IOException {
        List<String> candidates = new ArrayList<>();
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !relevantExtensionlessArchivePath(entry.getName())) {
                    continue;
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    if (looksLikeJson(input)) {
                        candidates.add(entry.getName());
                    }
                }
            }
        }
        return candidates;
    }

    private static List<String> inspectDirectory(Path root) throws IOException {
        List<String> candidates = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (!relevantExtensionlessArchivePath(relative)) {
                    continue;
                }
                try (InputStream input = Files.newInputStream(path)) {
                    if (looksLikeJson(input)) {
                        candidates.add(relative);
                    }
                }
            }
        }
        return candidates;
    }

    private static boolean relevantExtensionlessArchivePath(String archivePath) {
        if (archivePath == null || archivePath.endsWith(".json") || !archivePath.startsWith("data/")) {
            return false;
        }
        String[] parts = archivePath.split("/", 3);
        return parts.length == 3 && relevantResourcePath(parts[2]);
    }

    /** Resource location path, without the leading data/<namespace>/. */
    private static boolean relevantResourcePath(String resourcePath) {
        if (resourcePath == null) {
            return false;
        }
        return resourcePath.startsWith("recipes/")
                || resourcePath.startsWith("recipe/")
                || resourcePath.startsWith("index/guns/")
                || resourcePath.startsWith("index/ammo/")
                || resourcePath.startsWith("index/attachments/")
                || resourcePath.startsWith("data/guns/")
                || resourcePath.startsWith("data/attachments/");
    }

    private static boolean looksLikeJson(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(PREFIX_BYTES);
        int index = 0;
        // UTF-8 BOM
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            index = 3;
        }
        // Gun packs support JSON5-style comments. A leading comment must not
        // make an otherwise valid extensionless JSON resource disappear.
        while (index < bytes.length) {
            while (index < bytes.length && Character.isWhitespace((char) bytes[index])) {
                index++;
            }
            if (index + 1 < bytes.length && bytes[index] == '/' && bytes[index + 1] == '/') {
                index += 2;
                while (index < bytes.length && bytes[index] != '\n' && bytes[index] != '\r') {
                    index++;
                }
                continue;
            }
            if (index + 1 < bytes.length && bytes[index] == '/' && bytes[index + 1] == '*') {
                index += 2;
                while (index + 1 < bytes.length && !(bytes[index] == '*' && bytes[index + 1] == '/')) {
                    index++;
                }
                index = Math.min(bytes.length, index + 2);
                continue;
            }
            break;
        }
        return index < bytes.length && (bytes[index] == '{' || bytes[index] == '[');
    }
}
