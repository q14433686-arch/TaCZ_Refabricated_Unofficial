package com.tacz.guns.crafting.result;

import cn.sh1rocu.tacz.util.forge.CraftingHelper;
import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GunSmithTableResult {
    private static final Identifier EMPTY_GROUP = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "empty");
    public static final String GUN = "gun";
    public static final String AMMO = "ammo";
    public static final String ATTACHMENT = "attachment";
    public static final String CUSTOM = "custom";

    private ItemStack result = ItemStack.EMPTY;
    private Identifier group = null;

    @Nullable
    private RawGunTableResult raw = null;
    @Nullable
    private JsonObject rawCustomItem = null;

    public GunSmithTableResult(ItemStack result, @Nullable Identifier group) {
        this.result = result;
        this.group = group == null ? EMPTY_GROUP : group;
    }

    public GunSmithTableResult(@NotNull JsonObject rawCustomItem, @Nullable Identifier group) {
        // CUSTOM result must be parsed lazily. During resource reload, 26.2 item components may not be
        // bound yet; constructing ItemStack immediately can throw "Components not bound yet".
        this.rawCustomItem = rawCustomItem;
        this.group = group == null ? EMPTY_GROUP : group;
    }

    public GunSmithTableResult(@NotNull RawGunTableResult raw) {
        this.raw = raw;
    }

    public GunSmithTableResult(@NotNull RawGunTableResult raw, @Nullable Identifier group) {
        this.raw = raw;
        this.group = group == null ? EMPTY_GROUP : group;
    }

    public void init() {
        if (raw != null) {
            GunSmithTableResult result = RawGunTableResult.init(raw);
            this.result = result.getResult();
            if (group == null || group.equals(EMPTY_GROUP)) {
                this.group = result.getGroup();
            }
            this.raw = null;
        }
        if (rawCustomItem != null) {
            this.result = CraftingHelper.getItemStack(rawCustomItem, true);
            this.rawCustomItem = null;
        }
    }

    public ItemStack getResult() {
        return result;
    }

    public Identifier getGroup() {
        return group;
    }
}
