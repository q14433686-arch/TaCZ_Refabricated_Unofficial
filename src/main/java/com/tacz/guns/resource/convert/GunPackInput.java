package com.tacz.guns.resource.convert;

import com.tacz.guns.resource.GunPackOverlayResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;

/** A sorted, streaming source view; ZIPs are never extracted to disk. */
final class GunPackInput implements AutoCloseable {
    interface Opener { InputStream open() throws IOException; }
    final Map<String, Opener> files = new TreeMap<>();
    static final int MAX_FILES = 32_768;
    static final long MAX_BYTES = 1024L * 1024 * 1024;
    private int entries;
    private long bytes;
    private void account(long size) throws IOException {
        if (++entries > MAX_FILES || size < 0 || size > MAX_BYTES - bytes) throw new IOException("Pack exceeds conversion size limit");
        bytes += size;
    }
    private ZipFile zip;

    GunPackInput(Path source) throws IOException {
        try {
            if (Files.isDirectory(source)) {
                Path root = source.toRealPath();
                try (var stream = Files.walk(source)) {
                    for (var it = stream.iterator(); it.hasNext();) {
                        Path file = it.next();
                        if (Files.isSymbolicLink(file)) throw new IOException("Linked pack entries are not supported: " + file);
                        account(Files.isRegularFile(file) ? Files.size(file) : 0);
                        if (Files.isRegularFile(file)) {
                            String name = source.relativize(file).toString().replace('\\', '/');
                            GunPackOverlayResolver.validateEntryPath(name);
                            files.put(name, () -> {
                                if (!file.toRealPath().startsWith(root)) throw new IOException("Pack path escaped source during conversion");
                                return Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS);
                            });
                        }
                    }
                }
            } else {
                zip = new ZipFile(source.toFile());
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    account(entry.getSize());
                    GunPackOverlayResolver.validateEntryPath(entry.getName());
                    if (!entry.isDirectory() && files.putIfAbsent(entry.getName(), () -> zip.getInputStream(entry)) != null) {
                        throw new IOException("Duplicate ZIP entry: " + entry.getName());
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            close();
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        if (zip != null) zip.close();
    }
}
