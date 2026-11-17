package com.tacz.guns.resource;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;

/** Native Fabric predicates for existing packs, plus the Maven intervals used in universal packs. */
final class GunPackVersions {
    private static final Pattern UNION = Pattern.compile("(?<=[\\])]),\\s*(?=[\\[(])");
    private static final Pattern INTERVAL = Pattern.compile("([\\[(])\\s*([^,\\[\\]()\\s]*)\\s*(?:,\\s*([^,\\[\\]()\\s]*)\\s*)?([\\])])");

    private GunPackVersions() {}

    static boolean matches(String spec, Version version) throws VersionParsingException {
        if (spec == null || spec.isBlank()) throw new IllegalArgumentException("Missing version range");
        String range = spec.trim();
        if (!range.startsWith("[") && !range.startsWith("(")) {
            return VersionPredicate.parse(range).test(version); // keep bare/exact, *, >=, ~, ^ and conjunctions
        }
        boolean matches = false;
        for (String part : UNION.split(range, -1)) {
            var m = INTERVAL.matcher(part.trim());
            if (!m.matches()) throw new IllegalArgumentException("Malformed version range: " + spec);
            String lower = m.group(2), upper = m.group(3);
            var terms = new ArrayList<String>(2);
            if (upper == null) {
                if (!m.group(1).equals("[") || !m.group(4).equals("]") || lower.isEmpty()) {
                    throw new IllegalArgumentException("Malformed exact version: " + part);
                }
                terms.add("=" + lower);
            } else {
                if (!lower.isEmpty()) terms.add((m.group(1).equals("[") ? ">=" : ">") + lower);
                if (!upper.isEmpty()) terms.add((m.group(4).equals("]") ? "<=" : "<") + upper);
            }
            // Validate every alternative, even after a match. Joining alternatives with spaces would mean AND.
            matches |= VersionPredicate.parse(terms.isEmpty() ? "*" : String.join(" ", terms)).test(version);
        }
        return matches;
    }

    static boolean dependenciesMatch(Map<String, String> dependencies, boolean advisoryMinecraft) throws VersionParsingException {
        if (dependencies == null) return true;
        for (var entry : dependencies.entrySet()) {
            String id = entry.getKey();
            if (advisoryMinecraft && id.equals("minecraft")) continue;
            // This is a bundled port, not a second mod container with TaCZ's version.
            Version version = id.equals("lrtactical") ? Version.parse("0.3.0") : FabricLoader.getInstance()
                    .getModContainer(id).map(mod -> mod.getMetadata().getVersion()).orElse(null);
            if (version == null || !matches(entry.getValue(), version)) return false;
        }
        return true;
    }
}
