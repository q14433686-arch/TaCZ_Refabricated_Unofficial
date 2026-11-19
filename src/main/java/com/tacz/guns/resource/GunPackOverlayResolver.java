package com.tacz.guns.resource;

import cn.sh1rocu.tacz.util.forge.PathPackResources;
import com.tacz.guns.GunMod;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackResources;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

/** TaCZ overlays are selected by Minecraft *version*, not vanilla pack format numbers. */
public final class GunPackOverlayResolver {
    private GunPackOverlayResolver() {}

    public static List<String> resolve(PackMeta meta, Version minecraft) throws VersionParsingException {
        if (meta.getOverlays() == null) return List.of();
        var entries = meta.getOverlays().entries();
        if (entries == null) throw new IllegalArgumentException("overlays.entries must be an array");
        var seen = new HashSet<String>();
        var selected = new ArrayList<String>();
        for (var entry : entries) {
            String dir = entry == null ? null : entry.directory();
            if (dir == null || !dir.matches("[a-z0-9_-]+") || dir.equals("assets") || dir.equals("data") || !seen.add(dir)) {
                throw new IllegalArgumentException("Invalid or duplicate overlay directory: " + dir);
            }
            if (GunPackVersions.matches(entry.minecraft(), minecraft)) selected.add(dir);
        }
        return List.copyOf(selected);
    }

    /** Return layers highest-priority first. All paths pass through the same 26.3 recipe compatibility layer. */
    static List<PackResources> open(Path path, String name, List<String> selected) throws IOException {
        boolean directory = Files.isDirectory(path);
        if (!directory && !selected.isEmpty()) {
            // Validate the original entry names, before the ZIP filesystem normalizes paths.
            try (ZipFile zip = new ZipFile(path.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    String entry = entries.nextElement().getName();
                    int slash = entry.indexOf('/');
                    if (slash >= 0 && selected.contains(entry.substring(0, slash))) validateEntryPath(entry);
                }
            }
        }
        FileSystem archive = directory ? null : FileSystems.newFileSystem(path, Map.of());
        try {
            Path root = directory ? path.toRealPath() : archive.getPath("/");
            Component title = Component.literal(path.getFileName().toString());
            var layers = new ArrayList<PackResources>(selected.size() + 1);
            for (String dir : selected.reversed()) {
                Path overlay = root.resolve(dir);
                if (!Files.isDirectory(overlay)) throw new IOException("Missing overlay: " + dir);
                if (directory) {
                    try (var children = Files.walk(overlay)) {
                        for (var it = children.iterator(); it.hasNext();) {
                            Path child = it.next();
                            if (Files.isSymbolicLink(child) && (Files.isDirectory(child) || !child.toRealPath().startsWith(root))) {
                                throw new IOException("Linked overlay path outside layer: " + child);
                            }
                        }
                    }
                }
                layers.add(new PathPackResources(name, title, false, overlay));
            }
            // Exactly one close owner per mount. Opening metadata, another repository or a reload
            // must never borrow this mount or invalidate a supplier from another resource manager.
            layers.add(new PathPackResources(name, title, false, root) {
                @Override public void close() {
                    if (archive != null) {
                        try { archive.close(); }
                        catch (IOException e) { GunMod.LOGGER.warn("Closing gun pack {}", path, e); }
                    }
                }
            });
            return layers;
        } catch (IOException | RuntimeException | Error e) {
            if (archive != null) {
                try { archive.close(); } catch (IOException suppressed) { e.addSuppressed(suppressed); }
            }
            throw e;
        }
    }

    public static void validateEntryPath(String name) throws IOException {
        String relative = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        if (name.contains("\\") || name.contains(":")) throw new IOException("Invalid pack path: " + name);
        for (String segment : relative.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) throw new IOException("Invalid pack path: " + name);
        }
    }
}
