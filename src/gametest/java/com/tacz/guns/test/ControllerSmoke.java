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
import org.lwjgl.sdl.*;
import org.lwjgl.system.MemoryStack;
import java.util.concurrent.atomic.AtomicInteger;
import static org.lwjgl.sdl.SDLGamepad.*;
import static org.lwjgl.sdl.SDLJoystick.*;

/** SDL virtual joystick → signed Controllable polling and binding registry → normal TaCZ actions.
 * The return path is SDL's native rumble callback, not the user's physical motors. */
final class ControllerSmoke {
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var names = ctx.computeOnClient(mc -> Controllable.getBindingRegistry().getRegisteredBindings().stream()
                .filter(b -> b.getCategory().equals("key.category.tacz")).map(b -> b.getDescription()).distinct().toList());
        if (names.size() != 9) throw new AssertionError("Missing TaCZ controller bindings: " + names);
        var hold = ctx.computeOnClient(mc -> KeyConfig.HOLD_TO_AIM.get());
        var saved = world.getServer().computeOnServer(s -> world.getConnection().getServerPlayer().getMainHandItem().copy());
        var motors = new AtomicInteger();
        var rumble = SDL_VirtualJoystickDescRumbleCallback.create((userdata, low, high) -> {
            if (low != 0 && high != 0) motors.incrementAndGet();
            return true;
        });
        int pad = 0;
        try {
            equip(world, GunItemBuilder.create().setId(Identifier.parse("tacz:ak47")).setFireMode(FireMode.SEMI).setAmmoInBarrel(true).setAmmoCount(3).build());
            ctx.waitTicks(40);
            pad = ctx.computeOnClient(mc -> {
                try (var stack = MemoryStack.stackPush()) {
                    var desc = SDL_VirtualJoystickDesc.calloc(stack).version(SDL_VirtualJoystickDesc.SIZEOF)
                            .type((short)SDL_JOYSTICK_TYPE_GAMEPAD).nbuttons((short)15).naxes((short)6)
                            .button_mask(0x7fff).axis_mask(0x3f).name(stack.UTF8("TaCZ test pad")).Rumble(rumble);
                    int device = SDL_AttachVirtualJoystick(desc);
                    if (device == 0 || !SDL_IsGamepad(device)) throw new AssertionError("SDL test pad not recognized: " + SDLError.SDL_GetError());
                    return device;
                }
            });
            final int id = pad;
            ctx.waitFor(mc -> Controllable.getController() != null && Controllable.getController().getJid().intValue() == id);
            ctx.runOnClient(mc -> KeyConfig.HOLD_TO_AIM.set(false));
            for (boolean expected : new boolean[]{true,false}) {
                trigger(ctx, id, SDL_GAMEPAD_AXIS_LEFT_TRIGGER, true);
                ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim() == expected);
                trigger(ctx, id, SDL_GAMEPAD_AXIS_LEFT_TRIGGER, false); ctx.waitTicks(3);
                if (ctx.computeOnClient(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim()) != expected)
                    throw new AssertionError("Releasing toggle changed ADS");
            }
            ctx.runOnClient(mc -> KeyConfig.HOLD_TO_AIM.set(true));
            trigger(ctx, id, SDL_GAMEPAD_AXIS_LEFT_TRIGGER, true);
            ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim());
            ctx.getInput().holdKey(AimKey.AIM_KEY);
            trigger(ctx, id, SDL_GAMEPAD_AXIS_LEFT_TRIGGER, false); ctx.waitTicks(3);
            if (!ctx.computeOnClient(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim())) throw new AssertionError("Pad release overrode held mouse");
            ctx.getInput().releaseKey(AimKey.AIM_KEY); ctx.waitFor(mc -> !IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim());
            int before = motors.get();
            trigger(ctx, id, SDL_GAMEPAD_AXIS_RIGHT_TRIGGER, true);
            world.getServer().waitFor(s -> {var stack=world.getConnection().getServerPlayer().getMainHandItem();return ((IGun)stack.getItem()).getCurrentAmmoCount(stack)==2;});
            ctx.waitFor(mc -> motors.get() > before); ctx.waitTicks(6);
            trigger(ctx, id, SDL_GAMEPAD_AXIS_RIGHT_TRIGGER, false);
            world.getServer().runOnServer(s -> { var stack = world.getConnection().getServerPlayer().getMainHandItem();
                if (((IGun)stack.getItem()).getCurrentAmmoCount(stack) != 2) throw new AssertionError("Semi-auto trigger repeated while held"); });
            equip(world, ItemStack.EMPTY); ctx.waitFor(mc -> mc.player.getMainHandItem().isEmpty());
            trigger(ctx, id, SDL_GAMEPAD_AXIS_LEFT_TRIGGER, true); ctx.waitTicks(4);
            if (ctx.computeOnClient(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim())) throw new AssertionError("Empty hand stole trigger");
            trigger(ctx, id, SDL_GAMEPAD_AXIS_LEFT_TRIGGER, false);
            equip(world,saved.copy()); ctx.waitTicks(40);
            trigger(ctx,id, SDL_GAMEPAD_AXIS_LEFT_TRIGGER,true); ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim());
            ctx.runOnClient(mc -> { if (!SDL_DetachVirtualJoystick(id)) throw new AssertionError(SDLError.SDL_GetError()); }); pad = 0;
            ctx.waitFor(mc -> Controllable.getController() == null && !IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim());
            GunMod.LOGGER.info("PORT_CONTROLLER: installed mod polled an SDL gamepad: toggle, mixed holds, authoritative single shot, rumble callback, empty hand and detach passed");
        } finally {
            final int cleanup = pad;
            ctx.runOnClient(mc -> { if (cleanup != 0) SDL_DetachVirtualJoystick(cleanup); KeyConfig.HOLD_TO_AIM.set(hold); });
            ctx.waitTicks(2); rumble.free(); ctx.getInput().releaseKey(AimKey.AIM_KEY); equip(world,saved);
        }
    }
    private static void trigger(ClientGameTestContext ctx,int id,int axis,boolean down) {
        ctx.runOnClient(mc -> {
            if (!SDL_SetJoystickVirtualAxis(SDL_GetJoystickFromID(id), axis, down ? Short.MAX_VALUE : Short.MIN_VALUE)) throw new AssertionError(SDLError.SDL_GetError());
            SDL_UpdateJoysticks();
        });
    }
    private static void equip(TestSingleplayerContext world,ItemStack stack) {
        world.getServer().runOnServer(s -> {var p=world.getConnection().getServerPlayer();p.setItemInHand(InteractionHand.MAIN_HAND,stack);p.inventoryMenu.broadcastChanges();});
    }
}
