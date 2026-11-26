package com.tacz.guns.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.GunPackOverlayResolver;
import com.tacz.guns.resource.PackMeta;
import com.tacz.guns.resource.convert.UniversalPackConverter;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import com.tacz.guns.util.ItemNbtUtils;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.Version;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

public class PackConversionSmoke {
    @GameTest
    public void checkExportAndValidateWithBoundRegistries(GameTestHelper helper) throws Exception {
        var temp = Files.createTempDirectory("tacz-conversion-smoke-");
        try {
            Path input = temp.resolve("pack"), output = temp.resolve("report");
            var recipes = Files.createDirectories(input.resolve("data/demo/recipes"));
            String meta = """
                    {"namespace":"demo","dependencies":{"minecraft":"[1.20.1],[1.21.1]"},
                     "overlays":{"entries":[{"directory":"compat_1_21_1","minecraft":"[1.21.1]"}]}}
                    """;
            Files.writeString(input.resolve("gunpack.meta.json"), meta);
            Files.createDirectories(input.resolve("compat_1_21_1/assets/demo/lang"));
            Files.writeString(input.resolve("compat_1_21_1/assets/demo/lang/en_us.json"), "{}");
            String table = """
                    {"type":"tacz:gun_smith_table_crafting",
                     "materials":[{"item":{"type":"forge:partial_nbt","item":"minecraft:stick","nbt":"{marked:1b}"},"count":2}],
                     "result":{"type":"ammo","id":"tacz:12g","nbt":"{named:3b,AttachmentSCOPE:{id:'tacz:attachment',Count:1b,tag:{AttachmentId:'demo:sight'}}}"}}
                    """;
            Files.writeString(recipes.resolve("table.json"), table);
            Files.writeString(recipes.resolve("modded.json"), """
                    {"type":"minecraft:crafting_shapeless","conditions":[{"type":"forge:mod_loaded","modid":"absent"}],
                     "ingredients":[{"item":"absent:part"}],"result":{"item":"minecraft:stick","count":2}}
                    """);
            Files.writeString(recipes.resolve("chisel.json"), """
                    {"type":"minecraft:stonecutting","ingredient":{"item":"minecraft:stone"},"count":3,"result":"minecraft:stone_slab"}
                    """);
            Files.writeString(recipes.resolve("empty_tag.json"), """
                    {"type":"minecraft:crafting_shapeless","conditions":[{"type":"forge:tag_empty","tag":"demo:empty"}],
                     "ingredients":[{"item":"minecraft:stone"}],"result":{"item":"minecraft:stick"}}
                    """);
            Files.writeString(recipes.resolve("unsupported.json"), "{\"type\":\"absent:unknown_recipe\"}");
            var loot = Files.createDirectories(input.resolve("data/demo/loot_tables"));
            Files.writeString(loot.resolve("roll.json"), """
                    {"type":"minecraft:block","pools":[{"rolls":1,"conditions":[{"condition":"minecraft:survives_explosion"}],
                     "entries":[{"type":"minecraft:item","name":"minecraft:stick","functions":[{"function":"minecraft:set_nbt","tag":"{marked:1b}"}]}]}]}
                    """);
            var converter = new UniversalPackConverter(helper.getLevel().registryAccess(), Map.of());
            var checked = converter.convert(input, output, false);
            helper.assertTrue(checked.report().targetFormatValidated && !checked.report().gameValidated && checked.packPath() == null
                    && checked.report().skippedRecipes == 2 && checked.report().missingItemIds.contains("absent:part"), "Unexpected check: " + checked.report().diagnostics);
            var result = converter.convert(input, output, true);
            helper.assertTrue(result.packPath() != null && !result.reportPath().equals(checked.reportPath()), "Export failed: " + result.report().diagnostics);
            try (var zip = new ZipFile(result.packPath().toFile())) {
                helper.assertTrue(text(zip, "data/demo/recipes/table.json").equals(table) && text(zip, "compat_1_21_1/assets/demo/lang/en_us.json").equals("{}"), "Export changed base/existing overlay bytes");
                var outputMeta = CommonAssetsManager.GSON.fromJson(text(zip, "gunpack.meta.json"), PackMeta.class);
                for (var version : List.of("1.20.1", "1.21.1", "1.21.11")) {
                    var layers = GunPackOverlayResolver.resolve(outputMeta, Version.parse(version));
                    helper.assertTrue(layers.equals(switch (version) { case "1.21.11" -> List.of("compat_1_21_11"); case "1.21.1" -> List.of("compat_1_21_1"); default -> List.of(); }), "Wrong overlay for " + version);
                }
                String base = "compat_1_21_11/data/demo/";
                var convertedJson = json(zip, base + "recipe/table.json");
                var ops = helper.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
                var registryRecipe = com.tacz.guns.crafting.GunSmithTableSerializer.CODEC.codec().parse(ops, convertedJson).getOrThrow();
                registryRecipe.getResult().init();
                helper.assertTrue(ItemNbtUtils.getTag(registryRecipe.getResult().getResult()).getByteOr("named", (byte) 0) == 3,
                        "Native recipe-registry codec lost converted result components");
                var reencoded = com.tacz.guns.crafting.GunSmithTableSerializer.CODEC.codec().encodeStart(ops, registryRecipe).getOrThrow().getAsJsonObject();
                helper.assertTrue(reencoded.get("result").equals(convertedJson.get("result")), "Registry re-encode lost the source result specification");
                var recipe = CommonAssetsManager.GSON.fromJson(text(zip, base + "recipe/table.json"), TableRecipe.class);
                recipe.getResult().init();
                var stack = recipe.getResult().getResult();
                var custom = ItemNbtUtils.getTag(stack);
                helper.assertTrue(stack.getItem() instanceof IAmmo ammo && ammo.getAmmoId(stack).equals(Identifier.parse("tacz:12g"))
                        && custom.getByteOr("named", (byte) 0) == 3 && custom.getCompoundOrEmpty("AttachmentSCOPE").contains("components"), "Typed recipe lost id, custom data or nested attachment");
                var stick = new ItemStack(Items.STICK);
                var ingredient = recipe.getMaterials().getFirst().getIngredient();
                helper.assertTrue(ingredient != null && !ingredient.test(stick), "Partial-NBT ingredient widened to any stick");
                ItemNbtUtils.updateTag(stick, n -> n.putByte("marked", (byte) 1));
                helper.assertTrue(ingredient.test(stick), "Partial-NBT ingredient no longer matches");
                helper.assertTrue(json(zip, base + "recipe/chisel.json").getAsJsonObject("result").get("count").getAsInt() == 3,
                        "Stonecutting count defaulted to 1");
                var modded = json(zip, base + "recipe/modded.json");
                helper.assertTrue(modded.has("fabric:load_conditions") && modded.getAsJsonArray("ingredients").get(0).getAsString().equals("absent:part"), "Validation placeholder leaked into export");
                var disabled = json(zip, base + "recipe/unsupported.json").get("fabric:load_conditions");
                var condition = net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition.CONDITION_CODEC.parse(ops, disabled).getOrThrow();
                helper.assertTrue(!condition.test(null), "Skipped base recipe can reactivate");
                var pool = json(zip, base + "loot_table/roll.json").getAsJsonArray("pools").get(0).getAsJsonObject();
                helper.assertTrue(pool.getAsJsonArray("conditions").get(0).getAsJsonObject().get("condition").getAsString().equals("minecraft:survives_explosion")
                        && pool.getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonArray("functions").get(0).getAsJsonObject().get("function").getAsString().equals("minecraft:set_custom_data"), "Legacy loot fields silently ignored");
            }
            helper.assertTrue(Files.readString(input.resolve("gunpack.meta.json")).equals(meta) && converter.convert(input, output, true).report().hasErrors(), "Export overwrote source or an existing ZIP");
            helper.assertTrue(converter.convert(result.packPath(), output, false).report().isSkipped(), "ZIP target already has an overlay");
        } finally {
            try (var files = Files.walk(temp)) { for (var p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); }
        }
        com.tacz.guns.GunMod.LOGGER.info("PORT_RECEIVER_SERVER: conversion codec and export assertions passed");
        helper.succeed();
    }

    private static JsonObject json(ZipFile zip, String path) throws Exception { return JsonParser.parseString(text(zip, path)).getAsJsonObject(); }
    private static String text(ZipFile zip, String path) throws Exception {
        try (var stream = zip.getInputStream(zip.getEntry(path))) { return new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
    }
}
