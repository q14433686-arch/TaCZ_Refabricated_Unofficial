package com.tacz.guns.test;

import com.mojang.authlib.GameProfile;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.compat.playeranimator.AnimationName;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import com.tacz.guns.compat.playeranimator.pal.PalAssetManager;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.UUID;

/** One integrated client, actual PAL controllers, one additional client-side avatar in its world.
 * The observing hook counts model setups, not ticks, so an offscreen interval cannot pass by
 * inadvertently continuing to animate in a second view. This is not a second network client. */
public final class PalRuntimeSmoke {
    public static final class Frames {
        public static int watched = Integer.MIN_VALUE, count;
    }

    static void run(ClientGameTestContext ctx) {
        if (!PlayerAnimatorCompat.isInstalled()) throw new AssertionError("PAL integration is not installed");
        var camera = ctx.computeOnClient(mc -> mc.options.getCameraType());
        var facing = ctx.computeOnClient(mc -> new float[]{mc.player.getYRot(), mc.player.getXRot()});
        ctx.getInput().lookAt(180, 0); // look away before constructing the player to the south
        var remote = ctx.computeOnClient(mc -> {
            var actor = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "PortPose"));
            actor.snapTo(mc.player.position().add(0, 0, 3));
            actor.setNoGravity(true);
            Frames.watched = actor.getId(); Frames.count = 0;
            mc.level.addEntity(actor);
            return actor;
        });
        try {
            ctx.waitTicks(5);
            var rifle = equip(ctx, remote, "ak47");
            await(ctx, remote, rifle, true, "offscreen initial rifle");
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> {
                var upper = layer(remote, PlayerAnimatorCompat.LOOP_UPPER_ANIMATION);
                if (upper.getAnimationTicks() <= 0 || Frames.count != 0) throw new AssertionError("Offscreen tick/control: model=" + Frames.count + ", time=" + upper.getAnimationTicks());
            });
            ctx.getInput().lookAt(0, 0);
            ctx.waitFor(mc -> Frames.count > 0);
            ctx.takeScreenshot("tacz_pal_remote_rifle");
            ctx.getInput().lookAt(180, 0); ctx.waitTicks(3);
            int before = ctx.computeOnClient(mc -> Frames.count);
            ctx.runOnClient(mc -> remote.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
            await(ctx, remote, rifle, false, "offscreen put-away");
            var pistol = equip(ctx, remote, "glock_17");
            if (rifle == pistol) throw new AssertionError("Fixture needs distinct hold clips");
            await(ctx, remote, pistol, true, "offscreen pistol");
            if (ctx.computeOnClient(mc -> Frames.count) != before) throw new AssertionError("Avatar was not out of view during switch");
            ctx.getInput().lookAt(0, 0);
            ctx.waitFor(mc -> Frames.count > before);
            await(ctx, remote, pistol, true, "visible pistol");
            ctx.takeScreenshot("tacz_pal_remote_pistol");

            var local = ctx.computeOnClient(mc -> mc.player);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            await(ctx, local, rifle, true, "local F5 rifle");
            ctx.runOnClient(mc -> {
                if (layer(local, PlayerAnimatorCompat.LOOP_UPPER_ANIMATION) == layer(remote, PlayerAnimatorCompat.LOOP_UPPER_ANIMATION))
                    throw new AssertionError("PAL controller shared across players");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            await(ctx, local, rifle, false, "first-person hand isolation");
            await(ctx, remote, pistol, true, "remote unaffected by F5");
            GunMod.LOGGER.info("PORT_PAL: offscreen rifle/empty/pistol, two visible re-entries, independent remote/local controllers and F5 hand isolation passed");
        } finally {
            ctx.runOnClient(mc -> { Frames.watched = Integer.MIN_VALUE; remote.discard(); mc.options.setCameraType(camera); });
            ctx.getInput().lookAt(facing[0], facing[1]);
        }
    }

    private static Animation equip(ClientGameTestContext ctx, AbstractClientPlayer actor, String id) {
        return ctx.computeOnClient(mc -> {
            var item = GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath("tacz", id)).build();
            actor.setItemInHand(InteractionHand.MAIN_HAND, item);
            var display = TimelessAPI.getGunDisplay(item).orElseThrow();
            for (var layerId : Set.of(PlayerAnimatorCompat.LOWER_ANIMATION, PlayerAnimatorCompat.LOOP_UPPER_ANIMATION,
                    PlayerAnimatorCompat.ONCE_UPPER_ANIMATION, PlayerAnimatorCompat.ROTATION_ANIMATION)) {
                if (layer(actor, layerId).getFirstPersonMode() != FirstPersonMode.DISABLED) throw new AssertionError("Layer leaked into hand: " + layerId);
            }
            return PalAssetManager.INSTANCE.get(display.getPlayerAnimator3rd(), AnimationName.HOLD_UPPER).orElseThrow();
        });
    }

    private static PlayerAnimationController layer(AbstractClientPlayer actor, Identifier id) {
        if (PlayerAnimationAccess.getPlayerAnimationLayer(actor, id) instanceof PlayerAnimationController c) return c;
        throw new AssertionError("Missing controller: " + id);
    }

    private static void await(ClientGameTestContext ctx, AbstractClientPlayer actor, Animation expected, boolean playing, String name) {
        try {
            ctx.waitFor(mc -> {
                var c = layer(actor, PlayerAnimatorCompat.LOOP_UPPER_ANIMATION);
                return playing ? c.isActive() && c.getCurrentAnimationInstance() == expected : !c.isActive();
            });
        } catch (AssertionError e) { throw new AssertionError("PAL " + name + ": " + ctx.computeOnClient(mc -> String.valueOf(layer(actor, PlayerAnimatorCompat.LOOP_UPPER_ANIMATION).getAnimationState())), e); }
    }
}
