package com.tacz.guns.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tacz.guns.GunMod;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nullable;
import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class GetJarResources {
    /**
     * 打包时间会影响压缩包的哈希值，故直接指定时间
     * <p>
     * 此时间为 TaCZ 第一笔提交时间
     */
    private static final Instant BACKUP_TIME = Instant.parse("2024-02-26T12:28:08.000Z");
    private static final DateTimeFormatter BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());
    private static final int MAX_BACKUP_COUNT = 10;
    private static final String EXPORT_STATE_FILE_NAME = ".export-state.json";
    private static final int EXPORT_STATE_VERSION = 1;
    private static final Gson EXPORT_STATE_GSON = new GsonBuilder().setPrettyPrinting().create();

    private GetJarResources() {
    }

    /**
     * 复制本模组的文件到指定文件夹。将强行覆盖原文件。
     *
     * @param srcPath jar 中的源文件地址
     * @param root    想要复制到的根目录
     * @param path    复制后的路径
     */
    public static void copyModFile(String srcPath, Path root, String path) {
        URL url = GunMod.class.getResource(srcPath);
        try {
            if (url != null) {
                FileUtils.copyURLToFile(url, root.resolve(path).toFile());
            }
        } catch (IOException e) {
            GunMod.LOGGER.error("Failed to export resource {} to {}", srcPath, root.resolve(path), e);
        }
    }

    /**
     * 复制本模组的文件夹到指定文件夹。将强行覆盖原文件夹。
     *
     * @param srcPath jar 中的源文件地址
     * @param root    想要复制到的根目录
     * @param path    复制后的路径
     */
    public static void copyModDirectory(Class<?> resourceClass, String srcPath, Path root, String path) {
        URL url = resourceClass.getResource(srcPath);
        try {
            if (url != null) {
                exportFolderIfChanged(resourceClass, srcPath, url, root, path);
            }
        } catch (IOException e) {
            GunMod.LOGGER.error("Failed to export resource {} to {}", srcPath, root.resolve(path), e);
        }
    }

    /**
     * 复制本模组的文件夹到指定文件夹。将强行覆盖原文件夹。
     *
     * @param srcPath jar 中的源文件地址
     * @param root    想要复制到的根目录
     * @param path    复制后的路径
     */
    public static void copyModDirectory(String srcPath, Path root, String path) {
        copyModDirectory(GunMod.class, srcPath, root, path);
    }

    @Nullable
    public static InputStream readModFile(String filePath) {
        URL url = GunMod.class.getResource(filePath);
        try {
            if (url != null) {
                return url.openStream();
            }
        } catch (IOException e) {
            GunMod.LOGGER.error("Failed to read resource {}", filePath, e);
        }
        return null;
    }

    private static void exportFolderIfChanged(Class<?> resourceClass, String srcPath, URL sourceUrl, Path root, String path) throws IOException {
        String stateKey = getExportStateKey(resourceClass, srcPath, path);
        String sourceFingerprint = calculateSourceFingerprint(sourceUrl, FabricLoader.getInstance()
                .getModContainer(GunMod.MOD_ID).orElseThrow().getMetadata().getVersion().getFriendlyString());
        ExportStateFile stateFile = readExportState(root);
        Path targetPath = root.resolve(path);
        String previousFingerprint = stateFile.entries.get(stateKey);
        if (Files.isDirectory(targetPath, LinkOption.NOFOLLOW_LINKS) && sourceFingerprint.equals(previousFingerprint)) {
            GunMod.LOGGER.debug("Skipping unchanged exported resource {}", targetPath);
            return;
        }

        GunMod.LOGGER.info("Exporting resource pack {} to {}", srcPath, targetPath);
        copyFolder(sourceUrl, targetPath);
        stateFile.version = EXPORT_STATE_VERSION;
        stateFile.entries.put(stateKey, sourceFingerprint);
        writeExportState(root, stateFile);
    }

    private static String getExportStateKey(Class<?> resourceClass, String srcPath, String path) {
        return resourceClass.getName() + "|" + srcPath + "|" + path;
    }

    private static ExportStateFile readExportState(Path root) {
        Path statePath = root.resolve(EXPORT_STATE_FILE_NAME);
        if (!Files.isRegularFile(statePath)) {
            return new ExportStateFile();
        }
        try (Reader reader = Files.newBufferedReader(statePath, StandardCharsets.UTF_8)) {
            ExportStateFile state = EXPORT_STATE_GSON.fromJson(reader, ExportStateFile.class);
            if (state == null || state.version != EXPORT_STATE_VERSION || state.entries == null) {
                return new ExportStateFile();
            }
            return state;
        } catch (Exception exception) {
            GunMod.LOGGER.warn("Failed to read export state from {}, forcing full export", statePath, exception);
            return new ExportStateFile();
        }
    }

    private static void writeExportState(Path root, ExportStateFile stateFile) throws IOException {
        Files.createDirectories(root);
        Path statePath = root.resolve(EXPORT_STATE_FILE_NAME);
        try (Writer writer = Files.newBufferedWriter(statePath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            EXPORT_STATE_GSON.toJson(stateFile, writer);
        }
    }

    static String calculateSourceFingerprint(URL sourceUrl, String modVersion) throws IOException {
        List<String> lines = "jar".equals(sourceUrl.getProtocol())
                ? collectJarFingerprintLines(sourceUrl)
                : collectPathFingerprintLines(resolveSourcePath(sourceUrl));
        lines.add("mod-version|" + modVersion);
        Collections.sort(lines);
        return Md5Utils.md5Hex(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> collectPathFingerprintLines(Path sourceRoot) throws IOException {
        List<String> lines = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(sourceRoot, Integer.MAX_VALUE)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                try {
                    BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                    String relativePath = sourceRoot.relativize(path).toString().replace('\\', '/');
                    // Same format as JarEntry below, even for Loom/Fabric's path-backed JARs.
                    // Timestamps alone both invalidate unchanged sources and miss same-size edits.
                    var crc = new java.util.zip.CRC32();
                    try (var in = new java.util.zip.CheckedInputStream(Files.newInputStream(path), crc)) {
                        in.transferTo(OutputStream.nullOutputStream());
                    }
                    lines.add(relativePath + "|" + attributes.size() + "|" + crc.getValue());
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
        return lines;
    }

    private static List<String> collectJarFingerprintLines(URL sourceUrl) throws IOException {
        JarURLConnection connection = (JarURLConnection) sourceUrl.openConnection();
        connection.setUseCaches(false);
        String rootEntry = normalizeDirectoryEntryName(connection.getEntryName());
        List<String> lines = new ArrayList<>();
        try (JarFile jarFile = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(rootEntry)) {
                    continue;
                }
                String relativePath = entry.getName().substring(rootEntry.length());
                if (relativePath.isEmpty()) {
                    continue;
                }
                lines.add(relativePath + "|" + entry.getSize() + "|" + entry.getCrc());
            }
        }
        return lines;
    }

    private static void copyFolder(URL sourceUrl, Path targetPath) throws IOException {
        Path backup = FabricLoader.getInstance().getGameDir().resolve("tacz_backup").resolve(targetPath.getFileName());
        replaceExport(sourceUrl, targetPath, backup);
    }

    static void replaceExport(URL sourceUrl, Path targetPath, Path backup) throws IOException {
        boolean replacing = Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS);
        if (replacing && !Files.isDirectory(targetPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Expected a pack directory; keeping existing path: " + targetPath);
        }
        Files.createDirectories(targetPath.getParent());
        // Keep staging on the target filesystem. Its top-level directory has no pack metadata.
        Path stage = Files.createTempDirectory(targetPath.getParent(), ".tacz-export-");
        Path next = stage.resolve("next"), previous = stage.resolve("previous");
        try {
            if ("jar".equals(sourceUrl.getProtocol())) copyJarProtocolFolder(sourceUrl, next);
            else copyPathBackedFolder(resolveSourcePath(sourceUrl), next);
            if (replacing) {
                backupFiles(targetPath, backup); // a failed or partial backup cannot authorize removal
                Files.move(targetPath, previous);
            }
            try {
                Files.move(next, targetPath);
            } catch (IOException installFailure) {
                if (replacing) Files.move(previous, targetPath);
                throw installFailure;
            }
            if (replacing) deleteFiles(previous);
        } finally {
            // If rollback itself failed, keep previous as well as the completed ZIP.
            if (!Files.exists(previous, LinkOption.NOFOLLOW_LINKS)) deleteFiles(stage);
        }
    }

    private static void copyPathBackedFolder(Path sourceRoot, Path targetPath) throws IOException {
        Files.createDirectories(targetPath);
        try (Stream<Path> stream = Files.walk(sourceRoot, Integer.MAX_VALUE)) {
            stream.forEach(source -> {
                Path target = targetPath.resolve(sourceRoot.relativize(source).toString());
                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    private static void copyJarProtocolFolder(URL sourceUrl, Path targetPath) throws IOException {
        JarURLConnection connection = (JarURLConnection) sourceUrl.openConnection();
        connection.setUseCaches(false);
        String rootEntry = normalizeDirectoryEntryName(connection.getEntryName());
        Files.createDirectories(targetPath);
        try (JarFile jarFile = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().startsWith(rootEntry)) {
                    continue;
                }
                String relativePath = entry.getName().substring(rootEntry.length());
                if (relativePath.isEmpty()) {
                    continue;
                }
                Path target = targetPath.resolve(relativePath);
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (InputStream inputStream = jarFile.getInputStream(entry)) {
                    Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static String normalizeDirectoryEntryName(String entryName) {
        if (entryName == null || entryName.isEmpty()) {
            return "";
        }
        return entryName.endsWith("/") ? entryName : entryName + "/";
    }

    private static Path resolveSourcePath(URL sourceUrl) throws IOException {
        try {
            return Paths.get(sourceUrl.toURI());
        } catch (Exception exception) {
            throw new IOException("Failed to resolve source path " + sourceUrl, exception);
        }
    }

    /** Never let a partial ZIP authorize deletion of the live export. Stage the complete
     * archive on the backup filesystem before rotating files created by this exporter. */
    static void backupFiles(Path targetPath, Path backupPath) throws IOException {
        Files.createDirectories(backupPath);
        Path temp = Files.createTempFile(backupPath, ".tacz-backup-", ".tmp");
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(temp)); var paths = Files.walk(targetPath)) {
                for (var it = paths.sorted().iterator(); it.hasNext();) {
                    Path path = it.next();
                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Cannot back up a link or special file; keeping original export: " + path);
                    }
                    ZipEntry entry = new ZipEntry(targetPath.relativize(path).toString().replace(File.separatorChar, '/'));
                    entry.setLastModifiedTime(FileTime.from(BACKUP_TIME));
                    zip.putNextEntry(entry);
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
            String digest;
            try (var in = Files.newInputStream(temp)) { digest = Md5Utils.md5Hex(in); }
            List<Path> archived;
            try (var paths = Files.list(backupPath)) {
                archived = paths.filter(path -> path.getFileName().toString().matches("backup-[0-9]{8}-[0-9]{6}-[0-9a-f]{32}\\.zip")
                                && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), Comparator.reverseOrder())).toList();
            }
            // A matching filename alone isn't proof the existing backup is intact.
            for (var copy : archived) {
                if (copy.getFileName().toString().endsWith("-" + digest + ".zip") && Files.mismatch(temp, copy) == -1) return;
            }
            Files.move(temp, backupPath.resolve("backup-" + BACKUP_DATE_FORMAT.format(Instant.now()) + "-" + digest + ".zip"));
            for (int i = MAX_BACKUP_COUNT - 1; i < archived.size(); i++) Files.delete(archived.get(i));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void deleteFiles(Path targetPath) throws IOException {
        Files.walkFileTree(targetPath, new SimpleFileVisitor<>() {
            // 先去遍历删除文件
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            // 再去遍历删除目录
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static final class ExportStateFile {
        private int version = EXPORT_STATE_VERSION;
        private Map<String, String> entries = new HashMap<>();
    }
}
