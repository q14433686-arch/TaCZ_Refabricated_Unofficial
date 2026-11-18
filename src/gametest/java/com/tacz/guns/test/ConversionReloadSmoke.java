package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.resource.convert.UniversalPackConverter;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.util.ItemNbtUtils;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

/** End to end, independent of the dedicated test's working directory. Only installs into Loom's disposable game dir. */
final class ConversionReloadSmoke {
    private ConversionReloadSmoke() {}

    static void run(ClientGameTestContext context, TestSingleplayerContext world) throws IOException {
        var root = FabricLoader.getInstance().getGameDir();
        var scratch = Files.createTempDirectory(root, "converter-test-");
        var source = Files.createDirectory(scratch.resolve("source"));
        Files.writeString(source.resolve("gunpack.meta.json"), "{\"namespace\":\"reload_smoke\",\"dependencies\":{\"minecraft\":\"[1.20.1]\"}}");
        var recipes = Files.createDirectories(source.resolve("data/reload_smoke/recipes"));
        String table = """
                {"type":"tacz:gun_smith_table_crafting","materials":[{"item":{"item":"minecraft:stick"}}],
                 "result":{"type":"ammo","id":"tacz:12g","count":5,"nbt":"{reloaded:7b}"}}
                """;
        Files.writeString(recipes.resolve("shells.json"), table);
        Files.writeString(recipes.resolve("off.json"), table.replace("\"materials\"", "\"conditions\":[{\"type\":\"forge:false\"}],\"materials\""));
        Files.writeString(recipes.resolve("custom.json"), """
                {"type":"tacz:gun_smith_table_crafting","materials":[{"item":{"item":"minecraft:dirt"}}],
                 "result":{"type":"custom","group":"misc","item":{"item":"minecraft:stick","count":2,"nbt":"{reloaded:9b}"}}}
                """);
        Files.writeString(recipes.resolve("missing.json"), """
                {"type":"minecraft:crafting_shapeless","conditions":[{"type":"forge:mod_loaded","modid":"absent"}],
                 "ingredients":[{"item":"absent:part"}],"result":{"item":"minecraft:stick"}}
                """);
        Files.writeString(recipes.resolve("unsupported.json"), "{\"type\":\"absent:unsupported\"}");
        // The IDs below must appear *because of this reload*, not because the mod includes a static converted resource.
        var id = Identifier.parse("reload_smoke:shells");
        context.runOnClient(mc -> {
            if (CommonNetworkCache.INSTANCE.getTableRecipe(id) != null) throw new AssertionError("Stale conversion fixture loaded before export");
        });
        var reloaded = world.getServer().computeOnServer(server -> {
            var converter = new UniversalPackConverter(server.reloadableRegistries().lookup(), Map.of());
            var export = converter.convert(source, scratch.resolve("output"), true);
            if (export.packPath() == null || export.report().skippedRecipes != 1) throw new AssertionError(export.report().diagnostics);
            Files.copy(export.packPath(), root.resolve("tacz/reload-smoke.zip"));
            var packs = server.getPackRepository();
            packs.reload();
            return server.reloadResources(packs.getSelectedIds());
        });
        // Never block either game thread waiting for reload apply or for network sync.
        context.waitFor(mc -> reloaded.isDone(), 600);
        reloaded.join();
        world.getServer().runOnServer(server -> {
            var ids = server.getRecipeManager().getRecipes().stream().map(h -> h.id().identifier()).filter(i -> i.getNamespace().equals("reload_smoke")).collect(java.util.stream.Collectors.toSet());
            if (!ids.equals(java.util.Set.of(id, Identifier.parse("reload_smoke:custom")))) throw new AssertionError("Wrong reloaded registry recipes: " + ids);
        });
        context.waitFor(mc -> CommonNetworkCache.INSTANCE.getTableRecipe(id) != null, 600);
        context.runOnClient(mc -> {
            var cache = CommonNetworkCache.INSTANCE;
            var custom = cache.getTableRecipe(Identifier.parse("reload_smoke:custom")).getResult();
            custom.init();
            var shells = cache.getTableRecipe(id).getResult();
            shells.init();
            var stack = shells.getResult();
            if (cache.getTableRecipe(Identifier.parse("reload_smoke:off")) != null
                    || !(stack.getItem() instanceof IAmmo a) || !a.getAmmoId(stack).equals(Identifier.parse("tacz:12g"))
                    || stack.getCount() != 5 || ItemNbtUtils.getTag(stack).getByteOr("reloaded", (byte) 0) != 7
                    || !custom.getResult().is(Items.STICK) || custom.getResult().getCount() != 2
                    || ItemNbtUtils.getTag(custom.getResult()).getByteOr("reloaded", (byte) 0) != 9)
                throw new AssertionError("Wrong converted results after actual pack reload and sync");
        });
        GunMod.LOGGER.info("PORT_SMOKE_CLIENT: exported ZIP loaded through /reload path; registry, conditions and synced component stacks agree");
    }
}
