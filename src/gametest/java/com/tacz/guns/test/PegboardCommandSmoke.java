package com.tacz.guns.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionSet;
import java.util.HashSet;

public class PegboardCommandSmoke {
    @GameTest
    public void boundedModeratorScene(GameTestHelper helper) throws CommandSyntaxException {
        var level = helper.getLevel();
        var pos = helper.absolutePos(BlockPos.ZERO);
        var server = level.getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
        var root = dispatcher.getRoot().getChild("tacz");
        helper.assertTrue(!root.canUse(source.withPermission(PermissionSet.NO_PERMISSIONS)), "Unprivileged command source can create walls");
        var suffix = " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        for (int invalid : new int[]{0, 16385}) {
            try { dispatcher.execute("tacz debug pegboard " + invalid + " same" + suffix, source); throw new AssertionError("Accepted count " + invalid); }
            catch (CommandSyntaxException expected) { /* rejected by Brigadier before the handler */ }
        }
        for (String mode : new String[]{"same", "different"}) {
            var command = "tacz debug pegboard 4 " + mode + suffix;
            helper.assertTrue(dispatcher.execute(command, source) == 4, "Wrong sample size");
            var board = (GunPegboardBlockEntity)level.getBlockEntity(pos);
            try {
                var ids = new HashSet<Identifier>();
                int count = 0;
                for (int i=0; i<board.slotCount(); ++i) {
                    var stack = board.getItem(i);
                    if (stack.getItem() instanceof IGun gun) { count += stack.getCount(); ids.add(gun.getGunId(stack)); }
                }
                helper.assertTrue(count == 4 && ids.size() == (mode.equals("same") ? 1 : 4), "Wrong stress sample: " + ids);
                helper.assertTrue(dispatcher.execute(command, source) == 0 && level.getBlockEntity(pos) == board, "Overwrote an occupied cell");
            } finally { board.clearContent(); level.removeBlock(pos,false); }
        }
        helper.succeed();
    }
}
