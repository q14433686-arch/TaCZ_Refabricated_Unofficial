package com.tacz.guns.client.event;

import com.tacz.guns.config.common.OtherConfig;
import com.tacz.guns.config.ConfigPersist;
import com.tacz.guns.resource.GunPackLoader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import net.minecraft.server.packs.PackType;
import java.util.*;

/** Client-owned hide preference: a remote server cannot change local TOML or dismiss for everyone. */
public final class PackCompatibilityNotice {
    private static final Set<String> pending=new LinkedHashSet<>(), announced=new HashSet<>();
    private static int delay;
    private PackCompatibilityNotice(){}
    public static void accept(Collection<String> names){for(String name:names)pending.add(name.replaceAll("[\\p{Cntrl}§]",""));}
    public static Set<String> announcedPacks(){return Set.copyOf(announced);}
    public static void reset(){pending.clear();announced.clear();delay=0;}
    public static void register(){
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->{delay=20;accept(GunPackLoader.INSTANCE.getPacksNeedingConversion(PackType.CLIENT_RESOURCES));});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->reset());
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.player==null || (delay>0 && --delay>0))return;
            if(OtherConfig.HIDE_GUN_PACK_COMPATIBILITY_WARNING.get())return;
            var fresh=pending.stream().filter(n->!announced.contains(n)).toList();
            if(fresh.isEmpty())return;
            announced.addAll(fresh);
            var guide=Component.translatable("message.tacz.pack_compatibility.guide").withStyle(s->s.withColor(ChatFormatting.GREEN).withUnderlined(true)
                    .withClickEvent(new ClickEvent.RunCommand("/tacz_pack_compat guide")));
            var dismiss=Component.translatable("message.tacz.pack_compatibility.dismiss").withStyle(s->s.withColor(ChatFormatting.GRAY)
                    .withClickEvent(new ClickEvent.RunCommand("/tacz_pack_compat dismiss")));
            client.player.sendSystemMessage(Component.translatable("message.tacz.pack_compatibility.notice",String.join(", ",fresh))
                    .withStyle(ChatFormatting.YELLOW).append(" ").append(guide).append(" ").append(dismiss));
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(literal("tacz_pack_compat")
            .then(literal("guide").executes(ctx->{ctx.getSource().sendFeedback(Component.translatable("message.tacz.pack_compatibility.help"));return 1;}))
            .then(literal("dismiss").executes(ctx->{OtherConfig.HIDE_GUN_PACK_COMPATIBILITY_WARNING.set(true);ConfigPersist.saveAll();
                ctx.getSource().sendFeedback(Component.translatable("message.tacz.pack_compatibility.hidden"));return 1;}))));
    }
}
