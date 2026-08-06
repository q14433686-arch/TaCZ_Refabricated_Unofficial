package com.tacz.guns.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tacz.guns.industry.reference.IndustryReferenceProfile;
import com.tacz.guns.industry.reference.IndustryRuntimeAudit;
import com.tacz.guns.resource.CommonAssetsManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Moderator-facing inspection of the runtime factual reference table. */
public final class IndustryReferenceCommand {
    private static final String ROOT = "industry";
    private static final String AUDIT = "audit";
    private static final String REFERENCE = "reference";
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
                                .executes(IndustryReferenceCommand::reference)));
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
        return Command.SINGLE_SUCCESS;
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
