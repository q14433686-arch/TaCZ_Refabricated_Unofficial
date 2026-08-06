package com.tacz.guns.industry.blueprint;

import cn.sh1rocu.tacz.api.extension.IEntityPersistentData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.Set;

/** Server-authoritative per-player record of industrial platforms already studied. */
public final class BlueprintKnowledge {
    private static final String ROOT = "TaczBlueprintKnowledge";
    private static final String PLATFORMS = "Platforms";

    private BlueprintKnowledge() {
    }

    public static boolean knows(ServerPlayer player, String platform) {
        return !platform.isBlank() && platforms(player).contains(platform);
    }

    /** @return true only when this call newly records the platform. */
    public static boolean learn(ServerPlayer player, String platform) {
        if (platform.isBlank()) {
            return false;
        }
        Set<String> known = platforms(player);
        if (!known.add(platform)) {
            return false;
        }
        write(player, known);
        return true;
    }

    public static Set<String> platforms(ServerPlayer player) {
        CompoundTag root = ((IEntityPersistentData) player).tacz$getPersistentData();
        CompoundTag knowledge = root.getCompoundOrEmpty(ROOT);
        Set<String> result = new LinkedHashSet<>();
        for (Tag raw : knowledge.getListOrEmpty(PLATFORMS)) {
            if (raw instanceof StringTag string && !string.getAsString().isBlank()) {
                result.add(string.getAsString());
            }
        }
        return result;
    }

    private static void write(ServerPlayer player, Set<String> platforms) {
        CompoundTag knowledge = new CompoundTag();
        ListTag values = new ListTag();
        platforms.stream().sorted().map(StringTag::valueOf).forEach(values::add);
        knowledge.put(PLATFORMS, values);
        ((IEntityPersistentData) player).tacz$getPersistentData().put(ROOT, knowledge);
    }
}
