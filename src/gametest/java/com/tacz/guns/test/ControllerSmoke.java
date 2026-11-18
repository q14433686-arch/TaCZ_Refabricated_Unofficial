package com.tacz.guns.test;

import com.mrcrayfish.controllable.Controllable;
import com.mrcrayfish.controllable.client.binding.ButtonBinding;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.config.client.KeyConfig;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.stream.Collectors;

/** Real Controllable binding registry and handler, simulated press; the CI host has no physical pad. */
final class ControllerSmoke {
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        ctx.waitTicks(30);
        Map<String,ButtonBinding> bindings=ctx.computeOnClient(mc -> Controllable.getBindingRegistry().getRegisteredBindings().stream()
                .filter(b -> b.getCategory().equals("key.category.tacz")).collect(Collectors.toMap(ButtonBinding::getDescription,b -> b)));
        if(bindings.size()!=9)throw new AssertionError("Missing/duplicate TaCZ controller bindings: "+bindings.keySet());
        var aim=bindings.get("key.tacz.aim.desc");var shoot=bindings.get("key.tacz.shoot.desc");
        var saved=ctx.computeOnClient(mc -> KeyConfig.HOLD_TO_AIM.get());
        try {
            ctx.runOnClient(mc -> {KeyConfig.HOLD_TO_AIM.set(false); var op=IClientPlayerGunOperator.fromLocalPlayer(mc.player);op.aim(false);
                if(!Controllable.getInputHandler().handleBindingPressed(null,aim,false) || !op.isAim())throw new AssertionError("Registered trigger did not aim");
                aim.resetPressedState();
                if(!Controllable.getInputHandler().handleBindingPressed(null,aim,false) || op.isAim())throw new AssertionError("Second trigger did not exit toggle ADS");
                aim.resetPressedState(); });
            world.getServer().runOnServer(s->{var p=world.getConnection().getServerPlayer();p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();});
            ctx.waitFor(mc->mc.player.getMainHandItem().isEmpty());
            ctx.runOnClient(mc->{if(Controllable.getInputHandler().handleBindingPressed(null,shoot,false)) throw new AssertionError("Empty hand stole vanilla trigger");});
        } finally {ctx.runOnClient(mc->{KeyConfig.HOLD_TO_AIM.set(saved);aim.resetPressedState();shoot.resetPressedState();});}
        GunMod.LOGGER.info("PORT_CONTROLLER: 9 registered bindings, simulated toggle edges and empty-hand pass-through on real Controllable");
    }
}
