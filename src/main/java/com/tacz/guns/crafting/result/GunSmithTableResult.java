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
            // 容错边界：本方法会在「收集全部配方 → 逐条 init()」的循环里被调用
            // （JEI GunModPlugin、REI REIClientPlugin、GunSmithTableScreen、GunSmithTableMenu）。
            // 若此处因 JSON 格式错误抛出（例如内层键误写成 "id" 而非 "item"），
            // 一条坏配方就会中断整个循环，导致全部工作台配方从 JEI/REI 消失。
            // raw 路径（RawGunTableResult.init）对坏数据一向降级为 EMPTY + 空页签而非抛出，
            // 这里对齐同样语义：归入 EMPTY_GROUP 后，各处的页签过滤会自然剔除本条配方。
            try {
                this.result = CraftingHelper.getItemStack(rawCustomItem, true);
            } catch (RuntimeException e) {
                GunMod.LOGGER.error("Failed to parse custom gun smith table result item: {}", rawCustomItem, e);
                this.result = ItemStack.EMPTY;
                this.group = EMPTY_GROUP;
            }
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
