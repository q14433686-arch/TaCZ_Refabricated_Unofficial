package com.tacz.guns.init;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.item.AmmoBoxItem;
import com.tacz.guns.item.AmmoItem;
import com.tacz.guns.item.AttachmentItem;
import com.tacz.guns.item.GunSmithTableItem;
import com.tacz.guns.item.MagazineItem;
import com.tacz.guns.industry.item.IndustryItemBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;

@SuppressWarnings("all")
public class ModCreativeTabs {
    public static void init() {

    }

    public static CreativeModeTab OTHER_TAB = regiser("other", CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup.tab.tacz.other"))
            .icon(() -> ModItems.GUN_SMITH_TABLE.getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.acceptAll(GunSmithTableItem.fillItemCategory());
                output.accept(ModItems.TARGET);
                output.accept(ModItems.STATUE);
                output.accept(ModItems.TARGET_MINECART);
                AmmoBoxItem.fillItemCategory(output);
                output.accept(ModItems.CARBON_DUST);
                output.accept(ModItems.SULFUR_DUST);
                output.accept(ModItems.CINNABAR_DUST);
                output.accept(ModItems.PIG_IRON_INGOT);
                output.accept(ModItems.HIGH_CARBON_STEEL_INGOT);
                output.accept(ModItems.HIGH_CARBON_STEEL_PLATE);
                output.accept(ModItems.INDUSTRIAL_PROPELLANT);
                output.accept(ModItems.CARTRIDGE_CASE);
                output.accept(ModItems.PRIMER);
                output.accept(ModItems.PROJECTILE_CORE);
                // Representative samples make the generic NBT-backed items
                // discoverable in creative without exposing every platform.
                output.accept(IndustryItemBuilder.blueprint()
                        .platform("ak").kind("blueprint")
                        .displayNameKey("item.tacz.gun_blueprint.ak").build());
                output.accept(IndustryItemBuilder.component()
                        .platform("ak").kind("receiver")
                        .displayNameKey("item.tacz.gun_component.ak_receiver").build());
            }).build());

    public static CreativeModeTab AMMO_TAB = regiser("ammo", CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup.tab.tacz.ammo"))
            .icon(() -> AmmoItemBuilder.create().setId(DefaultAssets.DEFAULT_AMMO_ID).build())
            .displayItems((parameters, output) -> {
                output.acceptAll(AmmoItem.fillItemCategory());
                MagazineItem.fillItemCategory(output);
            }).build());

    public static CreativeModeTab ATTACHMENT_SCOPE_TAB = regiser("scope", CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("tacz.type.scope.name"))
            .icon(() -> AttachmentItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "scope_acog_ta31")).build())
            .displayItems((parameters, output) -> output.acceptAll(AttachmentItem.fillItemCategory(AttachmentType.SCOPE))).build());

    public static CreativeModeTab ATTACHMENT_MUZZLE_TAB = regiser("muzzle", CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("tacz.type.muzzle.name"))
            .icon(() -> AttachmentItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "muzzle_compensator_trident")).build())
            .displayItems((parameters, output) -> output.acceptAll(AttachmentItem.fillItemCategory(AttachmentType.MUZZLE))).build());

    public static CreativeModeTab ATTACHMENT_STOCK_TAB = regiser("stock", CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("tacz.type.stock.name"))
            .icon(() -> AttachmentItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "stock_militech_b5")).build())
            .displayItems((parameters, output) -> output.acceptAll(AttachmentItem.fillItemCategory(AttachmentType.STOCK))).build());

    public static CreativeModeTab ATTACHMENT_GRIP_TAB = regiser("grip", CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("tacz.type.grip.name"))
            .icon(() -> AttachmentItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "grip_magpul_afg_2")).build())
            .displayItems((parameters, output) -> output.acceptAll(AttachmentItem.fillItemCategory(AttachmentType.GRIP))).build());

    public static CreativeModeTab ATTACHMENT_EXTENDED_MAG_TAB = regiser("extended_mag", CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("tacz.type.extended_mag.name"))
            .icon(() -> AttachmentItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "extended_mag_3")).build())
            .displayItems((parameters, output) -> output.acceptAll(AttachmentItem.fillItemCategory(AttachmentType.EXTENDED_MAG))).build());

    public static CreativeModeTab ATTACHMENT_LASER_TAB = regiser("laser", CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("tacz.type.laser.name"))
            .icon(() -> AttachmentItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "laser_compact")).build())
            .displayItems((parameters, output) -> output.acceptAll(AttachmentItem.fillItemCategory(AttachmentType.LASER))).build());

    public static CreativeModeTab GUN_PISTOL_TAB = regiser("pistol", CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("tacz.type.pistol.name"))
            .icon(() -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "glock_17")).build())
            .displayItems((parameters, output) -> output.acceptAll(AbstractGunItem.fillItemCategory(GunTabType.PISTOL))).build());

    public static CreativeModeTab GUN_SNIPER_TAB = regiser("sniper", CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("tacz.type.sniper.name"))
            .icon(() -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "ai_awp")).build())
            .displayItems((parameters, output) -> output.acceptAll(AbstractGunItem.fillItemCategory(GunTabType.SNIPER))).build());

    public static CreativeModeTab GUN_RIFLE_TAB = regiser("rifle", CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("tacz.type.rifle.name"))
            .icon(() -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "ak47")).build())
            .displayItems((parameters, output) -> output.acceptAll(AbstractGunItem.fillItemCategory(GunTabType.RIFLE))).build());

    public static CreativeModeTab GUN_SHOTGUN_TAB = regiser("shotgun", CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("tacz.type.shotgun.name"))
            .icon(() -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "db_short")).build())
            .displayItems((parameters, output) -> output.acceptAll(AbstractGunItem.fillItemCategory(GunTabType.SHOTGUN))).build());

    public static CreativeModeTab GUN_SMG_TAB = regiser("smg", CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("tacz.type.smg.name"))
            .icon(() -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "hk_mp5a5")).build())
            .displayItems((parameters, output) -> output.acceptAll(AbstractGunItem.fillItemCategory(GunTabType.SMG))).build());

    public static CreativeModeTab GUN_RPG_TAB = regiser("rpg", CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("tacz.type.rpg.name"))
            .icon(() -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "rpg7")).build())
            .displayItems((parameters, output) -> output.acceptAll(AbstractGunItem.fillItemCategory(GunTabType.RPG))).build());

    public static CreativeModeTab GUN_MG_TAB = regiser("mg", CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("tacz.type.mg.name"))
            .icon(() -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "m249")).build())
            .displayItems((parameters, output) -> output.acceptAll(AbstractGunItem.fillItemCategory(GunTabType.MG))).build());

    private static CreativeModeTab regiser(String name, CreativeModeTab tab) {
        return Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, name), tab);
    }
}
