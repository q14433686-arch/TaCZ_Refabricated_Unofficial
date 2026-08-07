package com.tacz.guns.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tacz.guns.industry.magazine.ExternalCarrierVariant;
import com.tacz.guns.industry.magazine.GunFeedCandidateSurvey;
import com.tacz.guns.industry.magazine.GunFeedDefinition;
import com.tacz.guns.industry.reference.IndustryReferenceProfile;
import com.tacz.guns.industry.reference.IndustryRuntimeAudit;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.TreeSet;

/** Moderator-facing inspection of the runtime factual reference table. */
public final class IndustryReferenceCommand {
    private static final String ROOT = "industry";
    private static final String AUDIT = "audit";
    private static final String REFERENCE = "reference";
    private static final String FEED = "feed";
    private static final String INSPECT = "inspect";
    private static final String CANDIDATES = "candidates";
    private static final String GUN = "gun";

    private IndustryReferenceCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        return Commands.literal(ROOT)
                .then(Commands.literal(AUDIT).executes(IndustryReferenceCommand::audit))
                .then(Commands.literal(REFERENCE)
                        // Resource ids contain ':' and may contain '/', while
                        // Brigadier's word() intentionally stops at ':'. This
                        // is the terminal argument, so greedyString remains
                        // safe and Identifier.tryParse performs validation.
                        .then(Commands.argument(GUN, StringArgumentType.greedyString())
                                .executes(IndustryReferenceCommand::reference)))
                .then(Commands.literal(FEED)
                        .then(Commands.literal(INSPECT)
                                .then(Commands.argument(GUN, StringArgumentType.greedyString())
                                        .executes(IndustryReferenceCommand::inspectFeed)))
                        .then(Commands.literal(CANDIDATES)
                                .executes(IndustryReferenceCommand::candidateSummary)));
    }

    private static int audit(CommandContext<CommandSourceStack> context) {
        CommonAssetsManager manager = CommonAssetsManager.getInstance();
        if (manager == null || manager.getIndustryReferenceProfileManager() == null) {
            context.getSource().sendSystemMessage(Component.translatable("commands.tacz.industry.unavailable"));
            return 0;
        }
        IndustryRuntimeAudit.Snapshot snapshot = manager.getIndustryReferenceProfileManager().getAuditSnapshot();
        context.getSource().sendSystemMessage(Component.translatable(
                "commands.tacz.industry.audit",
                snapshot.guns(), snapshot.ammo(), snapshot.attachments(), snapshot.direct(), snapshot.aliases(),
                snapshot.unresolved(), snapshot.profiledGuns(), snapshot.surveyedGunCandidates()
        ));
        var feedAudit = manager.getGunFeedAudit();
        context.getSource().sendSystemMessage(Component.translatable(
                "commands.tacz.industry.feed_audit", feedAudit.accepted(), feedAudit.dormant(), feedAudit.rejected()
        ));
        context.getSource().sendSystemMessage(Component.translatable(
                "commands.tacz.industry.feed_source_audit", feedAudit.sidecarAccepted(), feedAudit.inlineAccepted()
        ));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Aggregate the intentionally non-active reload-data survey. This gives an
     * administrator a bounded review queue without silently turning candidate
     * guns into physical magazines.
     */
    private static int candidateSummary(CommandContext<CommandSourceStack> context) {
        CommonAssetsManager manager = CommonAssetsManager.getInstance();
        if (manager == null) {
            context.getSource().sendSystemMessage(Component.translatable("commands.tacz.industry.unavailable"));
            return 0;
        }
        GunFeedCandidateSurvey.Summary summary = GunFeedCandidateSurvey.summarize(manager);
        context.getSource().sendSystemMessage(Component.translatable(
                "commands.tacz.industry.feed_candidates",
                summary.total(), summary.validated(), summary.review(), summary.incrementalOrClip(), summary.excluded()
        ));
        context.getSource().sendSystemMessage(Component.translatable(
                "commands.tacz.industry.feed_candidates_hint"
        ));
        List<GunFeedCandidateSurvey.Candidate> queue = GunFeedCandidateSurvey.reviewCandidates(manager, 12);
        if (!queue.isEmpty()) {
            context.getSource().sendSystemMessage(Component.translatable(
                    "commands.tacz.industry.feed_candidates_queue", formatSurveyQueue(queue)
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static String formatSurveyQueue(List<GunFeedCandidateSurvey.Candidate> candidates) {
        StringBuilder output = new StringBuilder();
        for (GunFeedCandidateSurvey.Candidate candidate : candidates) {
            if (!output.isEmpty()) {
                output.append(", ");
            }
            output.append(candidate.gunId()).append('[').append(candidate.gunClass()).append(']');
        }
        return output.toString();
    }

    /**
     * Prints only facts that the currently loaded GunData exposes. It is an
     * author/admin aid for creating a sidecar declaration, not an automatic
     * detachable-magazine classifier: historical packs use reload.type=magazine
     * for tubes, cylinders, fixed boxes and real removable magazines alike.
     */
    private static int inspectFeed(CommandContext<CommandSourceStack> context) {
        Identifier gunId = Identifier.tryParse(StringArgumentType.getString(context, GUN));
        CommonAssetsManager manager = CommonAssetsManager.getInstance();
        if (gunId == null) {
            context.getSource().sendSystemMessage(Component.translatable("commands.tacz.industry.invalid_id"));
            return 0;
        }
        if (manager == null) {
            context.getSource().sendSystemMessage(Component.translatable("commands.tacz.industry.unavailable"));
            return 0;
        }
        var index = manager.getGunIndex(gunId);
        if (index == null || index.getGunData() == null) {
            context.getSource().sendSystemMessage(Component.translatable(
                    "commands.tacz.industry.feed_inspect_missing", gunId
            ));
            return 0;
        }

        GunData data = index.getGunData();
        context.getSource().sendSystemMessage(Component.translatable(
                "commands.tacz.industry.feed_inspect_header", gunId
        ));
        context.getSource().sendSystemMessage(Component.translatable(
                "commands.tacz.industry.feed_inspect_facts",
                data.getAmmoId() == null ? "-" : data.getAmmoId(),
                data.getAmmoAmount(),
                formatCapacities(data.getExtendedMagAmmoAmount()),
                data.getReloadData() == null || data.getReloadData().getType() == null
                        ? "-" : data.getReloadData().getType().name().toLowerCase(java.util.Locale.ROOT),
                data.getBolt() == null ? "-" : data.getBolt().name().toLowerCase(java.util.Locale.ROOT),
                data.getScript() == null ? "-" : data.getScript(),
                data.getReloadData() != null && data.getReloadData().isInfinite()
        ));

        GunFeedDefinition definition = manager.getGunFeedDefinition(gunId);
        if (definition == null) {
            context.getSource().sendSystemMessage(Component.translatable(
                    "commands.tacz.industry.feed_inspect_legacy"
            ));
        } else if (definition.isValidExternalCarrierDefinition()) {
            context.getSource().sendSystemMessage(Component.translatable(
                    "commands.tacz.industry.feed_inspect_external",
                    definition.getMechanism().serializedName(), definition.getMagazineFamily(),
                    formatExternalCarrierVariants(definition)
            ));
        } else {
            context.getSource().sendSystemMessage(Component.translatable(
                    "commands.tacz.industry.feed_inspect_non_external",
                    definition.getMechanism().serializedName(), definition.getMagazineCapacity()
            ));
        }
        GunFeedCandidateSurvey.Candidate candidate = GunFeedCandidateSurvey.analyze(gunId, index, definition);
        context.getSource().sendSystemMessage(Component.translatable(
                "commands.tacz.industry.feed_candidate",
                candidate.classification().serializedName(), candidate.gunClass(),
                candidate.provisionalPrivateFamily().isBlank() ? "-" : candidate.provisionalPrivateFamily(),
                String.join(", ", candidate.signals())
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static String formatCapacities(int[] capacities) {
        if (capacities == null || capacities.length == 0) {
            return "-";
        }
        StringBuilder output = new StringBuilder("[");
        for (int index = 0; index < capacities.length; index++) {
            if (index > 0) {
                output.append(',');
            }
            output.append(capacities[index]);
        }
        return output.append(']').toString();
    }

    /**
     * Show every exact capacity declared by the same explicit family, not only
     * the current gun's base row. This makes shared standards visible in the
     * diagnostic: an M16A1 can report both its 20-round base and the 30-round
     * STANAG manufactured by M4A1/SCAR-L declarations.
     */
    private static String formatExternalCarrierVariants(GunFeedDefinition definition) {
        TreeSet<Integer> capacities = new TreeSet<>();
        for (var entry : CommonAssetsManager.get().getAllGunFeedDefinitions()) {
            GunFeedDefinition candidate = entry.getValue();
            if (candidate == null || !candidate.isValidExternalCarrierDefinition()
                    || candidate.getMechanism() != definition.getMechanism()
                    || !candidate.getMagazineFamily().equals(definition.getMagazineFamily())
                    || !candidate.getAmmoId().equals(definition.getAmmoId())) {
                continue;
            }
            for (ExternalCarrierVariant variant : candidate.getExternalCarrierVariants()) {
                capacities.add(variant.getCapacity());
            }
        }
        return capacities.toString();
    }

    private static int reference(CommandContext<CommandSourceStack> context) {
        Identifier gunId = Identifier.tryParse(StringArgumentType.getString(context, GUN));
        CommonAssetsManager manager = CommonAssetsManager.getInstance();
        if (gunId == null) {
            context.getSource().sendSystemMessage(Component.translatable("commands.tacz.industry.invalid_id"));
            return 0;
        }
        if (manager == null) {
            context.getSource().sendSystemMessage(Component.translatable("commands.tacz.industry.unavailable"));
            return 0;
        }
        IndustryReferenceProfile profile = manager.getIndustryReferenceProfile(gunId);
        if (profile == null) {
            context.getSource().sendSystemMessage(Component.translatable("commands.tacz.industry.reference_missing", gunId));
            return 0;
        }
        IndustryReferenceProfile.Feed feed = profile.getFeed();
        IndustryReferenceProfile.Ammunition ammunition = profile.getAmmunition();
        IndustryReferenceProfile.Manufacturing manufacturing = profile.getManufacturing();
        context.getSource().sendSystemMessage(Component.translatable(
                "commands.tacz.industry.reference_header", gunId, profile.getCanonicalModel(), profile.getConfidence()
        ));
        context.getSource().sendSystemMessage(Component.translatable(
                "commands.tacz.industry.reference_action", profile.getAction(), manufacturing.getProfile(), manufacturing.getTier()
        ));
        context.getSource().sendSystemMessage(Component.translatable(
                "commands.tacz.industry.reference_feed", feed.getDevice(), feed.getRuntimeMechanism(),
                feed.getCarrierBehaviour(), feed.getFamily().isBlank() ? "-" : feed.getFamily(), feed.getCapacity()
        ));
        context.getSource().sendSystemMessage(Component.translatable(
                "commands.tacz.industry.reference_ammo", ammunition.getKind(), ammunition.getNominal(),
                ammunition.getExpectedAmmo() == null ? "-" : ammunition.getExpectedAmmo()
        ));
        context.getSource().sendSystemMessage(Component.translatable(
                "commands.tacz.industry.reference_evidence", String.join("; ", profile.getEvidence())
        ));
        return Command.SINGLE_SUCCESS;
    }
}
