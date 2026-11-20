package com.tacz.guns.test;

import com.mrcrayfish.controllable.Controllable;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.config.client.KeyConfig;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import com.mrcrayfish.controllable_sdl.api.joystick.SDL_JoystickID;
import com.mrcrayfish.controllable_sdl.api.joystick.virtual.SDL_VirtualJoystickDesc;
import com.mrcrayfish.controllable_sdl.api.error.SdlError;
import static com.mrcrayfish.controllable_sdl.api.gamecontroller.SDL_GameControllerAxis.*;
import static com.mrcrayfish.controllable_sdl.api.gamecontroller.SdlGamecontroller.SDL_IsGameController;
import static com.mrcrayfish.controllable_sdl.api.joystick.SdlJoystick.*;
import static com.mrcrayfish.controllable_sdl.api.joystick.SdlJoystickConst.SDL_VIRTUAL_JOYSTICK_DESC_VERSION;
import static com.mrcrayfish.controllable_sdl.api.joystick.SDL_JoystickType.SDL_JOYSTICK_TYPE_GAMECONTROLLER;
import java.util.concurrent.atomic.AtomicInteger;

/** SDL virtual joystick → signed Controllable polling and binding registry → normal TaCZ actions.
 * The return path is SDL's native rumble callback, not the user's physical motors. */
final class ControllerSmoke {
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var names = ctx.computeOnClient(mc -> Controllable.getBindingRegistry().getRegisteredBindings().stream()
                .filter(b -> b.getCategory().equals("key.category.tacz")).map(b -> b.getDescription()).distinct().toList());
        if (names.size() != 9) throw new AssertionError("Missing TaCZ controller bindings: " + names);
        var hold = ctx.computeOnClient(mc -> KeyConfig.HOLD_TO_AIM.get());
        var saved = world.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getMainHandItem().copy());
        var motors = new AtomicInteger();
        // SDL2 uses a device index for attach/detach and a separate instance ID for input.
        // Keep the JNA callback reachable until the virtual controller has been removed.
        var desc = new SDL_VirtualJoystickDesc();
        desc.version = SDL_VIRTUAL_JOYSTICK_DESC_VERSION;
        desc.type = SDL_JOYSTICK_TYPE_GAMECONTROLLER;
        desc.nbuttons = 15;
        desc.naxes = 6;
        desc.buttonMask = 0x7fff;
        desc.axisMask = 0x3f;
        desc.name = "TaCZ test pad";
        desc.Rumble = (data, low, high) -> { if (low != 0 && high != 0) motors.incrementAndGet(); };
        int pad = -1;
        try {
            equip(world, GunItemBuilder.create().setId(Identifier.parse("tacz:ak47")).setFireMode(FireMode.SEMI).setAmmoInBarrel(true).setAmmoCount(3).build());
            ctx.waitTicks(40);
            pad = ctx.computeOnClient(mc -> {
                int device = SDL_JoystickAttachVirtualEx(desc);
                if (device < 0) throw new AssertionError("SDL2 virtual attach failed: " + SdlError.SDL_GetError());
                return device;
            });
            final int index = pad;
            final SDL_JoystickID id = ctx.computeOnClient(mc -> {
                if (!SDL_IsGameController(index)) throw new AssertionError("Virtual joystick has no game-controller mapping");
                return SDL_JoystickGetDeviceInstanceID(index);
            });
            ctx.waitFor(mc -> Controllable.getController() != null && Controllable.getController().getJid().intValue() == id.intValue());
            ctx.runOnClient(mc -> KeyConfig.HOLD_TO_AIM.set(false));
            for (boolean expected : new boolean[]{true,false}) {
                trigger(ctx, id, SDL_CONTROLLER_AXIS_TRIGGERLEFT, true);
                ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim() == expected);
                trigger(ctx, id, SDL_CONTROLLER_AXIS_TRIGGERLEFT, false); ctx.waitTicks(3);
                if (ctx.computeOnClient(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim()) != expected)
                    throw new AssertionError("Releasing toggle changed ADS");
            }
            ctx.runOnClient(mc -> KeyConfig.HOLD_TO_AIM.set(true));
            trigger(ctx, id, SDL_CONTROLLER_AXIS_TRIGGERLEFT, true);
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim());
            ctx.getInput().holdKey(AimKey.AIM_KEY);
            trigger(ctx, id, SDL_CONTROLLER_AXIS_TRIGGERLEFT, false); ctx.waitTicks(3);
            if (!ctx.computeOnClient(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim())) throw new AssertionError("Pad release overrode held mouse");
            ctx.getInput().releaseKey(AimKey.AIM_KEY); ctx.waitFor(mc -> !IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim());
            int before = motors.get();
            trigger(ctx, id, SDL_CONTROLLER_AXIS_TRIGGERRIGHT, true);
            ClientProbe.waitForServer(ctx, world, s -> {
                var stack = s.getPlayerList().getPlayers().getFirst().getMainHandItem();
                return stack.getItem() instanceof IGun gun && gun.getCurrentAmmoCount(stack) == 2;
            });
            ctx.waitFor(mc -> motors.get() > before); ctx.waitTicks(6);
            trigger(ctx, id, SDL_CONTROLLER_AXIS_TRIGGERRIGHT, false);
            world.getServer().runOnServer(s -> { var stack = s.getPlayerList().getPlayers().getFirst().getMainHandItem();
                if (((IGun)stack.getItem()).getCurrentAmmoCount(stack) != 2) throw new AssertionError("Semi-auto trigger repeated while held"); });
            equip(world, ItemStack.EMPTY); ctx.waitFor(mc -> mc.player.getMainHandItem().isEmpty());
            trigger(ctx, id, SDL_CONTROLLER_AXIS_TRIGGERLEFT, true); ctx.waitTicks(4);
            if (ctx.computeOnClient(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim())) throw new AssertionError("Empty hand stole trigger");
            trigger(ctx, id, SDL_CONTROLLER_AXIS_TRIGGERLEFT, false);
            equip(world,saved.copy()); ctx.waitTicks(40);
            trigger(ctx,id, SDL_CONTROLLER_AXIS_TRIGGERLEFT,true); ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim());
            ctx.runOnClient(mc -> { if (SDL_JoystickDetachVirtual(index) != 0) throw new AssertionError(SdlError.SDL_GetError()); }); pad = -1;
            ctx.waitFor(mc -> Controllable.getController() == null && !IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim());
            GunMod.LOGGER.info("PORT_CONTROLLER: installed mod polled an SDL2 virtual gamepad: toggle, mixed holds, authoritative single shot, rumble callback, empty hand and detach passed");
        } finally {
            final int cleanup = pad;
            ctx.runOnClient(mc -> { if (cleanup >= 0) SDL_JoystickDetachVirtual(cleanup); KeyConfig.HOLD_TO_AIM.set(hold); });
            ctx.waitTicks(2); java.lang.ref.Reference.reachabilityFence(desc); ctx.getInput().releaseKey(AimKey.AIM_KEY); equip(world,saved);
        }
    }
    private static void trigger(ClientGameTestContext ctx,SDL_JoystickID id,int axis,boolean down) {
        ctx.runOnClient(mc -> {
            if (SDL_JoystickSetVirtualAxis(SDL_JoystickFromInstanceID(id), axis, down ? Short.MAX_VALUE : Short.MIN_VALUE) != 0) throw new AssertionError(SdlError.SDL_GetError());
            SDL_JoystickUpdate();
        });
    }
    private static void equip(TestSingleplayerContext world,ItemStack stack) {
        world.getServer().runOnServer(s -> {var p=s.getPlayerList().getPlayers().getFirst();p.setItemInHand(InteractionHand.MAIN_HAND,stack);p.inventoryMenu.broadcastChanges();});
    }
}
