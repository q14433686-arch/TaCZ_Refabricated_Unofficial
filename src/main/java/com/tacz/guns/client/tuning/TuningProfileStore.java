package com.tacz.guns.client.tuning;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.tacz.guns.GunMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/** Client-thread owned. Cache only the active launcher identity; display names and server UUIDs are not file names. */
public final class TuningProfileStore {
    private static final int LIMIT = 8 * 1024 * 1024;
    private final Path directory;
    private final TuningValues legacy;
    private UUID active;
    private TuningProfile saved;
    private boolean readFailed;

    public TuningProfileStore(Path directory, TuningValues legacy) {
        this.directory = directory;
        this.legacy = legacy;
    }

    private Path path(UUID id) { return directory.resolve(id + ".json"); }

    public TuningProfile get(UUID id) {
        if (id.equals(active)) return saved;
        readFailed = false;
        try {
            var file = path(id);
            if (Files.size(file) > LIMIT) throw new IOException("Oversized tuning profile");
            try (var in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                saved = TuningProfile.parse(JsonParser.parseReader(in));
            }
        } catch (NoSuchFileException missing) {
            // The old shared TOML is a read-only seed, never the destination of a personal edit.
            saved = TuningProfile.seeded(legacy);
        } catch (IOException | RuntimeException e) {
            readFailed = true;
            saved = TuningProfile.seeded(TuningValues.DEFAULTS);
            GunMod.LOGGER.warn("Could not read the local TaCZ tuning profile; using defaults and protecting the original file.", e);
        }
        active = id;
        return saved;
    }

    public boolean save(UUID owner, TuningProfile edited) {
        if (!owner.equals(active) || readFailed) return false;
        try {
            byte[] data = (new GsonBuilder().setPrettyPrinting().create().toJson(edited.json()) + "\n").getBytes(StandardCharsets.UTF_8);
            if (data.length > LIMIT) throw new IOException("Oversized tuning profile");
            Files.createDirectories(directory);
            Path tmp = Files.createTempFile(directory, owner + ".", ".tmp");
            try {
                Files.write(tmp, data);
                try { Files.move(tmp, path(owner), ATOMIC_MOVE, REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(tmp, path(owner), REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(tmp); }
            saved = edited.copy();
            return true;
        } catch (IOException | RuntimeException e) {
            GunMod.LOGGER.warn("Could not save the local TaCZ tuning profile.", e);
            return false;
        }
    }
}
