package com.tacz.guns.resource.pojo.data.block;

import cn.sh1rocu.tacz.util.forge.CraftingHelper;
import com.google.gson.*;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.init.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 26.2 修复：icon 改为懒加载 Supplier。
 * 原因：MC 26.2 中 new ItemStack(item, count) 需要 item 的 Holder.Reference.components 已 bind。
 * 枪包创造标签 icon 在资源重载(apply)阶段解析，此时组件尚未 bind，直接构造会抛
 * "Components not bound yet" 导致服务端/客户端进世界崩溃。
 * 现在解析阶段只捕获原始 JSON，ItemStack 构造推迟到 GUI 运行时（届时组件已 bind）。
 */
public record TabConfig(Identifier id, String name, Supplier<ItemStack> icon) {
    public static final Identifier TAB_AMMO = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "ammo");

    public static final Identifier TAB_PISTOL = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pistol");
    public static final Identifier TAB_SNIPER = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "sniper");
    public static final Identifier TAB_RIFLE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "rifle");
    public static final Identifier TAB_SHOTGUN = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "shotgun");
    public static final Identifier TAB_SMG = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "smg");
    public static final Identifier TAB_RPG = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "rpg");
    public static final Identifier TAB_MG = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "mg");

    public static final Identifier TAB_SCOPE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "scope");
    public static final Identifier TAB_MUZZLE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "muzzle");
    public static final Identifier TAB_STOCK = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "stock");
    public static final Identifier TAB_GRIP = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "grip");
    public static final Identifier TAB_EXTENDED_MAG = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "extended_mag");
    public static final Identifier TAB_LASER = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "laser");

    public static final Identifier TAB_MISC = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "misc");
    public static final Identifier TAB_EMPTY = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "empty");

    /**
     * Dedicated GPL-side pages for industrial compatibility work.  These are
     * intentionally separate from the original gun/ammo/attachment taxonomy:
     * a survey dossier or a physical carrier commission is not a pistol,
     * rifle, ordinary loose-ammo recipe, or miscellaneous item.
     */
    public static final Identifier TAB_INDUSTRY_ASSEMBLY = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "industry_assembly");
    public static final Identifier TAB_INDUSTRY_PLATFORM = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "industry_platform");
    public static final Identifier TAB_INDUSTRY_FEED = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "industry_feed");
    public static final Identifier TAB_INDUSTRY_CARTRIDGE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "industry_cartridge");

    private static final List<TabConfig> STANDARD_TABS = List.of(
            new TabConfig(TabConfig.TAB_AMMO, "tacz.type.ammo.name", () -> AmmoItemBuilder.create().setId(DefaultAssets.DEFAULT_AMMO_ID).build()),
            new TabConfig(TabConfig.TAB_PISTOL, "tacz.type.pistol.name", () -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "glock_17")).forceBuild()),
            new TabConfig(TabConfig.TAB_SNIPER, "tacz.type.sniper.name", () -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "ai_awp")).forceBuild()),
            new TabConfig(TabConfig.TAB_RIFLE, "tacz.type.rifle.name", () -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "ak47")).forceBuild()),
            new TabConfig(TabConfig.TAB_SHOTGUN, "tacz.type.shotgun.name", () -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "db_short")).forceBuild()),
            new TabConfig(TabConfig.TAB_SMG, "tacz.type.smg.name", () -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "hk_mp5a5")).forceBuild()),
            new TabConfig(TabConfig.TAB_RPG, "tacz.type.rpg.name", () -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "rpg7")).forceBuild()),
            new TabConfig(TabConfig.TAB_MG, "tacz.type.mg.name", () -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "m249")).forceBuild()),
            new TabConfig(TabConfig.TAB_SCOPE, "tacz.type.scope.name", () -> AttachmentItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "scope_acog_ta31")).build()),
            new TabConfig(TabConfig.TAB_MUZZLE, "tacz.type.muzzle.name", () -> AttachmentItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "muzzle_compensator_trident")).build()),
            new TabConfig(TabConfig.TAB_STOCK, "tacz.type.stock.name", () -> AttachmentItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "stock_militech_b5")).build()),
            new TabConfig(TabConfig.TAB_GRIP, "tacz.type.grip.name", () -> AttachmentItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "grip_magpul_afg_2")).build()),
            new TabConfig(TabConfig.TAB_EXTENDED_MAG, "tacz.type.extended_mag.name", () -> AttachmentItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "extended_mag_3")).build()),
            new TabConfig(TabConfig.TAB_LASER, "tacz.type.laser.name", () -> AttachmentItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "laser_compact")).build()),
            new TabConfig(TabConfig.TAB_MISC, "tacz.type.misc.name", () -> ModItems.GUN_SMITH_TABLE.getDefaultInstance())
    );

    public static final List<TabConfig> INDUSTRY_TABS = List.of(
            new TabConfig(TabConfig.TAB_INDUSTRY_ASSEMBLY, "tacz.type.industry_assembly.name", () -> ModItems.GUN_COMPONENT.getDefaultInstance()),
            new TabConfig(TabConfig.TAB_INDUSTRY_PLATFORM, "tacz.type.industry_platform.name", () -> ModItems.GUN_BLUEPRINT.getDefaultInstance()),
            new TabConfig(TabConfig.TAB_INDUSTRY_FEED, "tacz.type.industry_feed.name", () -> ModItems.MAGAZINE.getDefaultInstance()),
            new TabConfig(TabConfig.TAB_INDUSTRY_CARTRIDGE, "tacz.type.industry_cartridge.name", () -> ModItems.CARTRIDGE_CASE.getDefaultInstance())
    );

    /**
     * The standard TACZ pages plus the dedicated industry pages. Empty pages
     * are still removed by the screen, so adding these does not create blank
     * UI chrome on a table with no industrial operations.
     */
    public static final List<TabConfig> DEFAULT_TABS = mergeTabs(STANDARD_TABS, INDUSTRY_TABS);

    private static List<TabConfig> mergeTabs(List<TabConfig> first, List<TabConfig> second) {
        List<TabConfig> tabs = new ArrayList<>(first.size() + second.size());
        tabs.addAll(first);
        tabs.addAll(second);
        return List.copyOf(tabs);
    }

    /** Return only a code-owned TACZ tab; arbitrary third-party IDs stay data-pack owned. */
    @Nullable
    public static TabConfig findKnownTaczTab(@Nullable Identifier id) {
        if (id == null) {
            return null;
        }
        for (TabConfig tab : DEFAULT_TABS) {
            if (tab.id().equals(id)) {
                return tab;
            }
        }
        return null;
    }

    public static boolean isKnownTaczTab(@Nullable Identifier id) {
        return findKnownTaczTab(id) != null;
    }

    public static class Deserializer implements JsonDeserializer<TabConfig> {
        @Override
        public TabConfig deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (!json.isJsonObject()) {
                throw new JsonParseException("TabConfig must be a JSON object");
            }
            JsonObject object = json.getAsJsonObject();
            if (!object.has("id") || !object.get("id").isJsonPrimitive()) {
                throw new JsonParseException("TabConfig must have an id");
            }
            Identifier id = context.deserialize(object.get("id"), Identifier.class);
            // 只捕获原始 JSON，不在重载阶段构造 ItemStack（避免 "Components not bound yet"）
            final JsonObject iconObj = object.has("icon") && object.get("icon").isJsonObject()
                    ? object.getAsJsonObject("icon") : null;
            Supplier<ItemStack> icon = () -> {
                if (iconObj == null) {
                    return ItemStack.EMPTY;
                }
                try {
                    return CraftingHelper.getItemStack(iconObj, true);
                } catch (Exception e) {
                    // 运行时若仍失败（例如枪包 icon 引用了不存在的物品），回退空栈，避免崩 GUI
                    GunMod.LOGGER.error("Failed to build tab icon for {}", id, e);
                    return ItemStack.EMPTY;
                }
            };
            String name = GsonHelper.getAsString(object, "name", "tacz.type.unknown.name");
            return new TabConfig(id, name, icon);
        }
    }

    @NotNull
    public Component getName() {
        return Component.translatable(name == null ? "tacz.type.unknown.name" : name);
    }
}
