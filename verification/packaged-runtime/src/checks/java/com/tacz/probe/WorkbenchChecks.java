package com.tacz.probe;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import com.tacz.guns.compat.recipeviewer.GunAmmoQueryEntry;
import com.tacz.guns.compat.recipeviewer.AmmoQueryEntry;
import com.tacz.guns.compat.recipeviewer.AttachmentQueryData;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.TreeMap;

/** Catalog size, viewer registration and an item preview cannot establish that survival
 * materials actually settle and the expected product reaches the world. Test the menu which
 * handles the C2S request (not a mock of the recipe, and not 202 synthetic UI clicks). */
public final class WorkbenchChecks {
    public static void settleEveryStockTableProduct(GameTestHelper test) {
        var level = test.getLevel();
        var player = test.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(test.absolutePos(BlockPos.ZERO)));
        var inv = player.getInventory();
        var area = new AABB(player.position(),player.position().add(0,.5,0)).inflate(.2);
        var menus = Map.of("gun", "gun_smith_table", "ammo", "ammo_workbench", "attachments", "attachment_workbench");
        var counts = new TreeMap<String,Integer>();
        for (var entry : TimelessAPI.getAllRecipes().entrySet()) {
            var id = entry.getKey(); var recipe = entry.getValue();
            String group = id.getPath().split("/",2)[0];
            if (!id.getNamespace().equals("tacz") || !menus.containsKey(group)) continue;
            var menu = new GunSmithTableMenu(1,inv,Identifier.fromNamespaceAndPath("tacz",menus.get(group)));
            inv.clearContent();
            int slot = 0;
            for (var input : recipe.getInputs()) {
                var stack = input.getIngredientOrThrow().display().resolveForStacks(
                        net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(level)).getFirst();
                for (int left=input.getCount();left>0;) {
                    int n=Math.min(stack.getMaxStackSize(),left);
                    CatalogChecks.require(slot < 36,"Inputs do not fit in a survival inventory: "+id);
                    inv.setItem(slot++,stack.copyWithCount(n)); left-=n;
                }
            }
            int last = slot-1; var funded = inv.getItem(last).copy();
            inv.setItem(last,funded.copyWithCount(funded.getCount()-1));
            var before = new ItemStack[slot]; for (int i=0;i<slot;i++) before[i]=inv.getItem(i).copy();
            menu.doCraft(id,player);
            CatalogChecks.require(level.getEntitiesOfClass(ItemEntity.class,area).isEmpty(),"Unfunded product: "+id);
            for (int i=0;i<slot;i++) CatalogChecks.require(ItemStack.matches(before[i],inv.getItem(i)),"Partial debit on refusal: "+id);
            inv.setItem(last,funded);
            menu.doCraft(id,player);
            var output=level.getEntitiesOfClass(ItemEntity.class,area);
            CatalogChecks.require(inv.isEmpty() && output.size()==1 && ItemStack.matches(output.getFirst().getItem(),recipe.getOutput()),
                    "Wrong material balance or world product for "+id+" in "+menu.getBlockId());
            output.getFirst().discard(); counts.merge(group,1,Integer::sum);
        }
        CatalogChecks.require(counts.equals(Map.of("gun",70,"attachments",107,"ammo",25)),"Unexercised stock products: "+counts);
        GunMod.LOGGER.info("PORT_SETTLEMENT: 202 survival menu products {} with components/count and 202 one-short no-output/no-debit controls",counts);
        test.succeed();
    }
    public static void ordinaryTableCraftsWorkbench(GameTestHelper helper) {
        var level = helper.getLevel();
        var log = new ItemStack(Items.OAK_LOG);
        var iron = new ItemStack(Items.IRON_INGOT);
        var block = new ItemStack(Items.IRON_BLOCK);
        var input = CraftingInput.of(3, 3, java.util.List.of(log, log, log, iron, block, iron, iron, ItemStack.EMPTY, iron));
        var recipe = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
        CatalogChecks.require(recipe.id().identifier().equals(Identifier.parse("tacz:gun_smith_table"))
                && recipe.value().assemble(input, level.registryAccess()).is(com.tacz.guns.init.ModItems.GUN_SMITH_TABLE), "Server 3x3 crafting did not yield the workbench");
        helper.succeed();
    }

    public static void viewerSelectionHonorsTheOpenTable(GameTestHelper helper) {
        var inv = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL).getInventory();
        var gunId = Identifier.parse("tacz:gun/ak47");
        var menu = new com.tacz.guns.inventory.GunSmithTableMenu(1, inv, com.tacz.guns.api.DefaultAssets.DEFAULT_BLOCK_ID);
        CatalogChecks.require(menu.selectFromViewer(gunId, false) && menu.consumeRecipeSelection() == null, "Preview changed the menu");
        CatalogChecks.require(menu.selectFromViewer(gunId, true) && gunId.equals(menu.consumeRecipeSelection()) && menu.consumeRecipeSelection() == null,
                "Viewer selection must belong to this menu and be consumed once");
        var ammoTable = new com.tacz.guns.inventory.GunSmithTableMenu(2, inv, Identifier.parse("tacz:ammo_workbench"));
        CatalogChecks.require(!ammoTable.selectFromViewer(gunId, true) && ammoTable.consumeRecipeSelection() == null, "Viewer bypassed the table filter");
        helper.succeed();
    }

    public static void bothViewerDirectionsUseTheSameIndexedStacks(GameTestHelper helper) {
        var pairs = GunAmmoQueryEntry.getAllGunAmmoQueryEntries();
        var first = pairs.stream().filter(e -> Identifier.parse("tacz:ak47").equals(
                ((IGun) e.getGunStack().getItem()).getGunId(e.getGunStack()))).findFirst().orElseThrow();
        var pages = AmmoQueryEntry.getAllAmmoQueryEntries(java.util.Collections.nCopies(55, first));
        CatalogChecks.require(pages.size() == 2 && pages.getFirst().getGunStacks().size() == 54 && pages.getLast().getGunStacks().size() == 1
                && pages.getLast().getPageNumber() == 2 && pages.getLast().getTotalGunCount() == 55
                && pages.getLast().getAmmoStack() == first.getAmmoStack(), "Ammo reverse pages lost/duplicated entries");
        var parts = AttachmentQueryData.load();
        var reverse = parts.gunAttachmentQueries().getFirst();
        var part = reverse.getAttachmentStacks().getFirst();
        CatalogChecks.require(parts.attachmentQueries().stream().anyMatch(e -> e.getAttachmentStack() == part
                        && (e.getAllowGunStacks().contains(reverse.getGunStack()) || e.getExtraAllowGunStacks().contains(reverse.getGunStack()))),
                "Reverse attachment result missing its forward relation");
        CatalogChecks.require(parts.compatibilityChecks() <= (long) parts.attachmentCount() * parts.gunCount(), "Rescanned compatibility per direction");
        helper.succeed();
    }

}
