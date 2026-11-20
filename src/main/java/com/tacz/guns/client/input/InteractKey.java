package com.tacz.guns.client.input;

import com.tacz.guns.block.AmmoStorageBlock;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.client.gui.overlay.DisplayCaseLockHint;
import com.tacz.guns.network.message.ClientMessageAmmoStorageInteract;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.util.InteractKeyConfigRead;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

@Environment(EnvType.CLIENT)
public class InteractKey {
    public static final KeyMapping INTERACT_KEY = new KeyMapping("key.tacz.interact.desc",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_K, // O opens vanilla 26.3's friends screen before block use can run.
            TaCZKeyCategory.TACZ);

    public static boolean onInteractControllerPress(boolean isPress) {
        return isPress && isInGame() && doInteractLogic(true);
    }

    public static boolean sharesUseBinding(KeyMapping use) {
        return !use.isUnbound() && KeyMappingHelper.getBoundKeyOf(INTERACT_KEY).equals(KeyMappingHelper.getBoundKeyOf(use));
    }

    // Only for the synchronous, allowlisted startUseItem call below. A pad (or a mouse rebind)
    // need not have set the keyboard mapping's down flag when Minecraft fires its use event.
    private static boolean dispatchingUse;
    public static boolean isDispatchingUse() { return dispatchingUse; }
    private static void use(Minecraft mc) {
        boolean previous = dispatchingUse;
        dispatchingUse = true;
        try { mc.startUseItem(); } finally { dispatchingUse = previous; }
    }

    static boolean doInteractLogic(boolean controller) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator() || !IGun.mainHandHoldGun(player)) return false;
        boolean vanillaUse = !controller && sharesUseBinding(mc.options.keyUse);
        if (mc.hitResult instanceof BlockHitResult blockHitResult && blockHitResult.getType() == HitResult.Type.BLOCK) {
            interactBlock(blockHitResult, player, mc, vanillaUse);
            return true;
        }
        if (mc.hitResult instanceof EntityHitResult entityHitResult) {
            if (!vanillaUse && InteractKeyConfigRead.canInteractEntity(entityHitResult.getEntity())) use(mc);
            return true;
        }
        return false;
    }

    private static void interactBlock(BlockHitResult blockHitResult, LocalPlayer player, Minecraft mc, boolean vanillaUse) {
        BlockPos blockPos = blockHitResult.getBlockPos();
        BlockState block = player.level().getBlockState(blockPos);
        if (block.getBlock() instanceof GunDisplayCaseBlock && block.getValue(GunDisplayCaseBlock.LOCKED)) {
            DisplayCaseLockHint.show(mc, true);
        }
        if (block.getBlock() instanceof AmmoStorageBlock) {
            if (ClientPlayNetworking.canSend(ClientMessageAmmoStorageInteract.TYPE))
                ClientPlayNetworking.send(new ClientMessageAmmoStorageInteract(blockPos));
            return;
        }
        if (!vanillaUse && InteractKeyConfigRead.canInteractBlock(block)) {
            use(mc);
        }
    }
}
