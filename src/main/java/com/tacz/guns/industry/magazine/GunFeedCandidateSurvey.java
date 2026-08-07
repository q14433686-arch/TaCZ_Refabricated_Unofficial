package com.tacz.guns.industry.magazine;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.ICommonResourceProvider;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only survey of legacy TACZ reload data.
 *
 * <p>This deliberately produces <em>review categories</em>, never a live
 * {@link GunFeedDefinition}. Historical gun packs overload FeedType.MAGAZINE
 * for detachable magazines, fixed boxes, tubes, cylinders, clips and custom
 * scripts. The signals here can rule out several unsafe candidates and make
 * human review faster, but cannot prove that a model has a real magazine well
 * or that two same-calibre guns share a physical carrier.</p>
 */
public final class GunFeedCandidateSurvey {
    private GunFeedCandidateSurvey() {
    }

    public enum Classification {
        /** An inline or sidecar declaration has already been server-validated. */
        VALIDATED,
        /** Fuel/inventory/non-magazine APIs are not generic removable-carrier candidates. */
        EXCLUDED_NON_MAGAZINE,
        EXCLUDED_INFINITE,
        EXCLUDED_LOW_CAPACITY,
        /** The observed script owns per-round or clip-batch events. */
        INCREMENTAL_OR_CLIP,
        /** Open-bolt + semi-only resembles a revolver, but remains non-authoritative. */
        ACTION_AMBIGUOUS,
        /** One-shot reload timing may support a swap, but it could still be a fixed internal box. */
        REVIEW_SINGLE_SWAP;

        public String serializedName() {
            return switch (this) {
                case VALIDATED -> "validated";
                case EXCLUDED_NON_MAGAZINE -> "excluded_non_magazine";
                case EXCLUDED_INFINITE -> "excluded_infinite";
                case EXCLUDED_LOW_CAPACITY -> "excluded_low_capacity";
                case INCREMENTAL_OR_CLIP -> "incremental_or_clip";
                case ACTION_AMBIGUOUS -> "action_ambiguous";
                case REVIEW_SINGLE_SWAP -> "review_external_or_fixed";
            };
        }

        public boolean needsHumanReview() {
            return this == ACTION_AMBIGUOUS || this == REVIEW_SINGLE_SWAP;
        }
    }

    public record Candidate(
            Identifier gunId,
            String gunClass,
            Classification classification,
            String provisionalPrivateFamily,
            List<String> signals
    ) {
        public Candidate {
            gunClass = gunClass == null || gunClass.isBlank() ? "unknown" : gunClass;
            provisionalPrivateFamily = provisionalPrivateFamily == null ? "" : provisionalPrivateFamily;
            signals = signals == null ? List.of() : List.copyOf(signals);
        }
    }

    public record Summary(int total, int validated, int review, int incrementalOrClip, int excluded) {
    }

    public static Candidate analyze(Identifier gunId, @Nullable CommonGunIndex index,
                                    @Nullable GunFeedDefinition validatedDefinition) {
        List<String> signals = new ArrayList<>();
        if (validatedDefinition != null) {
            signals.add("validated_mechanism=" + validatedDefinition.getMechanism().serializedName());
            signals.add("validated_family=" + validatedDefinition.getMagazineFamily());
            return new Candidate(gunId, index == null ? "unknown" : safeClass(index.getType()),
                    Classification.VALIDATED, "", signals);
        }
        if (gunId == null || index == null || index.getGunData() == null) {
            return new Candidate(gunId, "unknown", Classification.EXCLUDED_NON_MAGAZINE, "",
                    List.of("loaded_gun_index_or_data_absent"));
        }

        GunData data = index.getGunData();
        String gunClass = safeClass(index.getType());
        signals.add("gun_class=" + gunClass);
        signals.add(classReviewHint(gunClass));
        FeedType feedType = data.getReloadData() == null ? null : data.getReloadData().getType();
        signals.add("reload_type=" + (feedType == null ? "absent" : feedType.name().toLowerCase(Locale.ROOT)));
        signals.add("ammo=" + (data.getAmmoId() == null ? "absent" : data.getAmmoId()));
        signals.add("base_capacity=" + data.getAmmoAmount());
        if (data.getScript() != null) {
            signals.add("script=" + data.getScript());
        } else {
            signals.add("script=default_java");
        }
        if (data.getBolt() != null) {
            signals.add("bolt=" + data.getBolt().name().toLowerCase(Locale.ROOT));
        }

        if (feedType != FeedType.MAGAZINE) {
            return new Candidate(gunId, gunClass, Classification.EXCLUDED_NON_MAGAZINE, "", signals);
        }
        if (data.getReloadData().isInfinite()) {
            signals.add("infinite_reload=true");
            return new Candidate(gunId, gunClass, Classification.EXCLUDED_INFINITE, "", signals);
        }
        if (data.getAmmoAmount() <= 2) {
            signals.add("capacity_lte_2=true");
            return new Candidate(gunId, gunClass, Classification.EXCLUDED_LOW_CAPACITY, "", signals);
        }

        List<String> incrementalSignals = incrementalScriptSignals(data.getScriptParam());
        if (!incrementalSignals.isEmpty()) {
            signals.addAll(incrementalSignals);
            return new Candidate(gunId, gunClass, Classification.INCREMENTAL_OR_CLIP, "", signals);
        }

        if (data.getBolt() == Bolt.OPEN_BOLT && !hasRapidFireMode(data.getFireModeSet())) {
            signals.add("open_bolt_semi_only=true");
            return new Candidate(gunId, gunClass, Classification.ACTION_AMBIGUOUS,
                    privateFamilyProposal(gunId), signals);
        }

        if (data.getAllowAttachments() != null
                && data.getAllowAttachments().contains(AttachmentType.EXTENDED_MAG)) {
            signals.add("extended_mag_slot=true");
        }
        var reloadTime = data.getReloadData().getFeed();
        if (reloadTime == null) {
            signals.add("reload_feed_timing=absent");
        } else if (reloadTime.getEmptyTime() < 1.0F || reloadTime.getTacticalTime() < 1.0F) {
            signals.add("short_reload_timing=true");
        } else {
            signals.add("single_finish_timing_candidate=true");
        }
        signals.add("requires_mechanism_and_family_confirmation=true");
        return new Candidate(gunId, gunClass, Classification.REVIEW_SINGLE_SWAP,
                privateFamilyProposal(gunId), signals);
    }

    public static Summary summarize(ICommonResourceProvider provider) {
        int total = 0;
        int validated = 0;
        int review = 0;
        int incremental = 0;
        int excluded = 0;
        if (provider == null) {
            return new Summary(0, 0, 0, 0, 0);
        }
        for (Map.Entry<Identifier, CommonGunIndex> entry : provider.getAllGuns()) {
            Candidate candidate = analyze(entry.getKey(), entry.getValue(), provider.getGunFeedDefinition(entry.getKey()));
            total++;
            switch (candidate.classification()) {
                case VALIDATED -> validated++;
                case ACTION_AMBIGUOUS, REVIEW_SINGLE_SWAP -> review++;
                case INCREMENTAL_OR_CLIP -> incremental++;
                case EXCLUDED_NON_MAGAZINE, EXCLUDED_INFINITE, EXCLUDED_LOW_CAPACITY -> excluded++;
            }
        }
        return new Summary(total, validated, review, incremental, excluded);
    }

    /** Deterministic bounded queue for moderators; candidates remain inactive. */
    public static List<Candidate> reviewCandidates(ICommonResourceProvider provider, int limit) {
        if (provider == null || limit <= 0) {
            return List.of();
        }
        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<Identifier, CommonGunIndex> entry : provider.getAllGuns()) {
            Candidate candidate = analyze(entry.getKey(), entry.getValue(), provider.getGunFeedDefinition(entry.getKey()));
            if (candidate.classification().needsHumanReview()) {
                candidates.add(candidate);
            }
        }
        candidates.sort(Comparator.comparing((Candidate candidate) -> candidate.gunId().toString()));
        return List.copyOf(candidates.subList(0, Math.min(limit, candidates.size())));
    }

    private static List<String> incrementalScriptSignals(@Nullable Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return List.of();
        }
        List<String> signals = new ArrayList<>();
        for (String rawKey : parameters.keySet()) {
            if (rawKey == null) {
                continue;
            }
            String key = rawKey.toLowerCase(Locale.ROOT);
            if ("loop_feed".equals(key) || "clip_load_feed".equals(key)
                    || key.matches("round\\d+_feed")) {
                signals.add("script_param=" + key);
            }
        }
        return List.copyOf(signals);
    }

    private static boolean hasRapidFireMode(@Nullable List<FireMode> modes) {
        if (modes == null) {
            return false;
        }
        return modes.stream().anyMatch(mode -> mode == FireMode.AUTO || mode == FireMode.BURST);
    }

    private static String safeClass(@Nullable String gunClass) {
        return gunClass == null || gunClass.isBlank() ? "unknown" : gunClass.toLowerCase(Locale.ROOT);
    }

    private static String classReviewHint(String gunClass) {
        return switch (gunClass) {
            case "pistol", "smg", "rifle" -> "class_allows_external_but_is_not_proof";
            case "sniper" -> "class_can_be_fixed_or_detachable";
            case "shotgun" -> "class_can_be_tube_or_box";
            case "mg" -> "class_can_be_belt_box_or_internal";
            case "rpg" -> "class_requires_launcher_review";
            default -> "class_requires_manual_review";
        };
    }

    private static String privateFamilyProposal(Identifier gunId) {
        if (gunId == null) {
            return "";
        }
        String namespace = gunId.getNamespace().replaceAll("[^a-z0-9_]+", "_");
        String path = gunId.getPath().replaceAll("[^a-z0-9_]+", "_");
        return "surveyed_" + namespace + "_" + path;
    }
}
