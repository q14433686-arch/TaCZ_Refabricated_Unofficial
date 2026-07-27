package com.tacz.guns.compat.playeranimator.pal;

import com.tacz.guns.GunMod;
import com.tacz.guns.util.TacPathVisitor;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Preserves TACZ's legacy player_animator/&lt;file&gt;.json grouping on PAL 1.2.5. */
public final class PalAssetManager extends SimplePreparableReloadListener<Map<Identifier, Map<String, Animation>>>
        implements IdentifiableResourceReloadListener {
    public static final PalAssetManager INSTANCE = new PalAssetManager();
    public static final Identifier ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pal_asset_manager");

    private static final Pattern ZIP_PATTERN = Pattern.compile("^(\\w+)/player_animator/([\\w/]+)\\.json$");
    private final FileToIdConverter converter = new FileToIdConverter("player_animator", ".json");
    private final Map<Identifier, Map<String, Animation>> animations = new HashMap<>();

    private PalAssetManager() {
    }

    public synchronized boolean contains(Identifier fileId) {
        return animations.containsKey(fileId);
    }

    public synchronized Optional<Animation> get(Identifier fileId, String animationName) {
        Map<String, Animation> file = animations.get(fileId);
        return file == null ? Optional.empty() : Optional.ofNullable(file.get(normalize(animationName)));
    }

    public synchronized void clearAll() {
        animations.clear();
    }

    public synchronized void put(Identifier fileId, InputStream input) throws IOException {
        animations.put(fileId, normalize(UniversalAnimLoader.loadAnimations(input)));
    }

    public boolean load(ZipFile zipFile, String zipPath) {
        Matcher matcher = ZIP_PATTERN.matcher(zipPath);
        if (!matcher.find()) {
            return false;
        }
        ZipEntry entry = zipFile.getEntry(zipPath);
        if (entry == null) {
            return false;
        }
        Identifier id = Identifier.fromNamespaceAndPath(matcher.group(1), matcher.group(2));
        try (InputStream input = zipFile.getInputStream(entry)) {
            put(id, input);
            return true;
        } catch (IOException | RuntimeException exception) {
            GunMod.LOGGER.warn("Failed to load PAL animation {} from {}", zipPath, zipFile, exception);
            return false;
        }
    }

    public void load(File root) {
        Path animatorPath = root.toPath().resolve("player_animator");
        if (!Files.isDirectory(animatorPath)) {
            return;
        }
        TacPathVisitor visitor = new TacPathVisitor(animatorPath.toFile(), root.getName(), ".json", (id, file) -> {
            try (InputStream input = Files.newInputStream(file)) {
                put(id, input);
            } catch (IOException | RuntimeException exception) {
                GunMod.LOGGER.warn("Failed to load PAL animation file {}", file, exception);
            }
        });
        try {
            Files.walkFileTree(animatorPath, visitor);
        } catch (IOException exception) {
            GunMod.LOGGER.warn("Failed to scan PAL animation directory {}", animatorPath, exception);
        }
    }

    @Override
    protected @NotNull Map<Identifier, Map<String, Animation>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, Map<String, Animation>> output = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : converter.listMatchingResources(manager).entrySet()) {
            Identifier fileId = converter.fileToId(entry.getKey());
            try (InputStream input = entry.getValue().open()) {
                output.put(fileId, normalize(UniversalAnimLoader.loadAnimations(input)));
            } catch (IOException | RuntimeException exception) {
                GunMod.LOGGER.warn("Failed to load PAL animation resource {}", entry.getKey(), exception);
            }
        }
        return output;
    }

    @Override
    protected synchronized void apply(Map<Identifier, Map<String, Animation>> prepared,
                                      ResourceManager manager,
                                      ProfilerFiller profiler) {
        animations.clear();
        animations.putAll(prepared);
    }

    private static Map<String, Animation> normalize(Map<String, Animation> source) {
        Map<String, Animation> normalized = new HashMap<>();
        source.forEach((name, animation) -> normalized.put(normalize(name), animation));
        return normalized;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
