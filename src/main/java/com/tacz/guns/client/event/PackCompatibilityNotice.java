package com.tacz.guns.client.event;

import com.tacz.guns.config.ConfigPersist;
import com.tacz.guns.config.common.OtherConfig;
import com.tacz.guns.resource.GunPackLoader;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Client-owned preference: a remote server cannot alter local TOML or dismiss for everyone. */
@Environment(EnvType.CLIENT)
public final class PackCompatibilityNotice {
    private static final Set<String> pending = new LinkedHashSet<>();
    private static final Set<String> announced = new HashSet<>();
    private static int delay;

    private PackCompatibilityNotice() {}

    /** All callers run on the client thread; names are displayed as plain text, never commands. */
    public static void accept(Collection<String> names) {
        for (String name : names) pending.add(name.replaceAll("[\\p{Cntrl}§]", ""));
    }

    public static Set<String> announcedPacks() { return Set.copyOf(announced); }

    public static void reset() {
        pending.clear();
        announced.clear();
        delay = 0;
    }

    public static void register() {
        // INIT also covers teardown tasks discarded when switching connections.
        ClientPlayConnectionEvents.INIT.register((handler, client) -> reset());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            delay = 20;
            accept(GunPackLoader.INSTANCE.getPacksNeedingConversion(PackType.CLIENT_RESOURCES));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || (delay > 0 && --delay > 0)) return;
            if (OtherConfig.HIDE_GUN_PACK_COMPATIBILITY_WARNING.get()) return;
            var fresh = pending.stream().filter(name -> !announced.contains(name)).toList();
            if (fresh.isEmpty()) return;
            announced.addAll(fresh);
            var guide = Component.translatable("message.tacz.pack_compatibility.guide").withStyle(style -> style
                    .withColor(ChatFormatting.GREEN).withUnderlined(true)
                    .withClickEvent(new ClickEvent.RunCommand("/tacz_pack_compat guide")));
            var dismiss = Component.translatable("message.tacz.pack_compatibility.dismiss").withStyle(style -> style
                    .withColor(ChatFormatting.GRAY).withClickEvent(new ClickEvent.RunCommand("/tacz_pack_compat dismiss")));
            // LocalPlayer has no sendSystemMessage in 1.21.11; this message belongs only to the local chat.
            client.gui.getChat().addMessage(Component.translatable("message.tacz.pack_compatibility.notice", String.join(", ", fresh))
                    .withStyle(ChatFormatting.YELLOW).append(" ").append(guide).append(" ").append(dismiss));
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(literal("tacz_pack_compat")
                .then(literal("guide").executes(ctx -> {
                    ctx.getSource().sendFeedback(Component.translatable("message.tacz.pack_compatibility.help"));
                    return 1;
                }))
                .then(literal("dismiss").executes(ctx -> {
                    OtherConfig.HIDE_GUN_PACK_COMPATIBILITY_WARNING.set(true);
                    ConfigPersist.saveAll();
                    ctx.getSource().sendFeedback(Component.translatable("message.tacz.pack_compatibility.hidden"));
                    return 1;
                }))));
    }
}
