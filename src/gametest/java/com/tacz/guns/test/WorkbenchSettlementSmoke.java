package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
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
public final class WorkbenchSettlementSmoke {
    @GameTest
    public void settleEveryStockTableProduct(GameTestHelper test) {
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
                var item = input.getIngredientOrThrow().items().findFirst().orElseThrow().value();
                var stack = new ItemStack(item);
                for (int left=input.getCount();left>0;) {
                    int n=Math.min(stack.getMaxStackSize(),left);
                    test.assertTrue(slot < 36,"Inputs do not fit in a survival inventory: "+id);
                    inv.setItem(slot++,stack.copyWithCount(n)); left-=n;
                }
            }
            int last = slot-1; var funded = inv.getItem(last).copy();
            inv.setItem(last,funded.copyWithCount(funded.getCount()-1));
            var before = new ItemStack[slot]; for (int i=0;i<slot;i++) before[i]=inv.getItem(i).copy();
            menu.doCraft(id,player);
            test.assertTrue(level.getEntitiesOfClass(ItemEntity.class,area).isEmpty(),"Unfunded product: "+id);
            for (int i=0;i<slot;i++) test.assertTrue(ItemStack.matches(before[i],inv.getItem(i)),"Partial debit on refusal: "+id);
            inv.setItem(last,funded);
            menu.doCraft(id,player);
            var output=level.getEntitiesOfClass(ItemEntity.class,area);
            test.assertTrue(inv.isEmpty() && output.size()==1 && ItemStack.matches(output.getFirst().getItem(),recipe.getOutput()),
                    "Wrong material balance or world product for "+id+" in "+menu.getBlockId());
            output.getFirst().discard(); counts.merge(group,1,Integer::sum);
        }
        test.assertTrue(counts.equals(Map.of("gun",70,"attachments",107,"ammo",25)),"Unexercised stock products: "+counts);
        GunMod.LOGGER.info("PORT_SETTLEMENT: 202 survival menu products {} with components/count and 202 one-short no-output/no-debit controls",counts);
        test.succeed();
    }
}
