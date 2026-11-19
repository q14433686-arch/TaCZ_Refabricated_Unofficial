package com.tacz.guns.client.gui;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.tuning.TuningProfile;
import com.tacz.guns.client.tuning.TuningProfiles;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** A stable picker for the lifetime of one editor. Includes rules for temporarily missing packs so they can be removed. */
final class HudAssignments {
    enum Scope { DEFAULT, TYPE, GUN }
    Scope scope = Scope.DEFAULT;
    private final List<String> types, guns;
    private final String heldGun, heldType;
    private String type, gun;

    HudAssignments(TuningProfile profile, ItemStack stack) {
        var typeSet = new TreeSet<>(profile.typeKeys());
        var gunSet = new TreeSet<>(profile.gunKeys());
        TimelessAPI.getAllClientGunIndex().forEach(e -> { gunSet.add(e.getKey().toString()); typeSet.add(e.getValue().getType()); });
        Identifier id = stack.getItem() instanceof IGun g ? g.getGunId(stack) : null;
        heldGun = id == null ? null : id.toString();
        heldType = id == null ? null : TuningProfiles.type(id);
        if (heldGun != null) gunSet.add(heldGun);
        guns = List.copyOf(gunSet); types = List.copyOf(typeSet);
        gun = heldGun != null ? heldGun : guns.isEmpty() ? null : guns.getFirst();
        type = heldType != null ? heldType : types.isEmpty() ? null : types.getFirst();
        if (heldGun != null && profile.overrides(heldType, heldGun)) scope = Scope.GUN;
        else if (heldType != null && profile.overrides(heldType, null)) scope = Scope.TYPE;
    }

    @Nullable String type() { return scope == Scope.TYPE ? type : scope == Scope.GUN ? TuningProfiles.type(Identifier.parse(gun)) : null; }
    @Nullable String gun() { return scope == Scope.GUN ? gun : null; }
    String key() { return scope == Scope.TYPE ? type : gun; }
    boolean available(Scope s) { return s == Scope.DEFAULT || !(s == Scope.TYPE ? types : guns).isEmpty(); }
    @Nullable String held() { return scope == Scope.TYPE ? heldType : heldGun; }
    void select(String key) { if (scope == Scope.TYPE) type = key; else gun = key; }
    private List<String> choices() { return scope == Scope.TYPE ? types : guns; }
    String step(int delta) { var list = choices(); return list.get(Math.floorMod(list.indexOf(key()) + delta, list.size())); }

    Component name(String key) {
        if (scope == Scope.TYPE) return Component.translatableWithFallback("tacz.type." + key + ".name", key);
        return TimelessAPI.getClientGunIndex(Identifier.parse(key)).<Component>map(i -> Component.translatableWithFallback(i.getName(), key))
                .orElseGet(() -> Component.literal(key));
    }

    @Nullable String find(String text) {
        String query = text.strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return null;
        for (String key : choices()) if (key.equalsIgnoreCase(query)) return key;
        for (String key : choices()) if (key.toLowerCase(Locale.ROOT).contains(query) || name(key).getString().toLowerCase(Locale.ROOT).contains(query)) return key;
        return null;
    }
}
