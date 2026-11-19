package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.awt.image.BufferedImage;
import java.util.HashMap;

/** Play an authored animation keyframe at a real first-person muzzle locator. Unlike a world
 * emitter in front of the camera, these definitions have local position and to_world=false. */
final class HandMaterialSmoke {
    private static final Identifier DISPLAY = Identifier.parse("port_test:hand_materials");

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world, String backing) {
        var rt = WorldEmitterManager.getInstance();
        var previous = world.getServer().computeOnServer(s -> {
            var p = world.getConnection().getServerPlayer();
            var old = p.getMainHandItem().copy();
            var stack = GunItemBuilder.create().setId(Identifier.parse("tacz:ak47")).setAmmoCount(3).build();
            ((IGun)stack.getItem()).setGunDisplayId(stack, DISPLAY);
            p.setItemInHand(InteractionHand.MAIN_HAND, stack); p.inventoryMenu.broadcastChanges();
            return old;
        });
        try {
            ctx.waitFor(mc -> mc.player.getMainHandItem().getItem() instanceof IGun g && DISPLAY.equals(g.getGunDisplayId(mc.player.getMainHandItem())));
            ctx.waitTicks(40);
            var machine = ctx.computeOnClient(mc -> TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).orElseThrow().getAnimationStateMachine());
            ctx.waitFor(mc -> machine.getContext() != null);
            var controller = machine.getAnimationController();
            // The LUA state context owns its DiscreteTrackArray by identity. Allocate through
            // the same API as a pack script, never substitute a copied updating-track iterable.
            int track = ctx.computeOnClient(mc -> {
                var context = machine.getContext();
                return context.assignNewTrack(context.addTrackLine());
            });
            try {
                String prefix = "tacz_hand_material_" + backing + "_";
                var control = SnowstormMaterialSmoke.read(ctx.takeScreenshot(prefix + "control"));
                var samples = new HashMap<String, Strength>();
                for (String name : SnowstormMaterialSmoke.MATERIALS) {
                    long before = ctx.computeOnClient(mc -> {
                        if (!controller.containPrototype(name)) throw new AssertionError("Display did not load animation " + name);
                        long count = rt.rendered();
                        controller.runAnimation(track, name, ObjectAnimation.PlayType.PLAY_ONCE_STOP, 0);
                        return count;
                    });
                    try { ctx.waitFor(mc -> rt.particleCount() == 1 && rt.rendered() > before); }
                    catch (AssertionError e) { throw new AssertionError(prefix + name + ": " + ctx.computeOnClient(mc ->
                            rt.emitterCount() + " emitters, " + rt.particleCount() + " particles, " + (rt.rendered()-before) + " submits"), e); }
                    var frame = SnowstormMaterialSmoke.read(ctx.takeScreenshot(prefix + name));
                    samples.put(name, strength(control, frame, prefix + name));
                    ctx.runOnClient(mc -> { controller.removeAnimation(track); rt.clear(); });
                    ctx.waitTicks(2);
                }
                var cutout = samples.get("particles_opaque");
                var alpha = samples.get("particles_alpha");
                var blend = samples.get("particles_blend");
                double between = alpha.distance(blend), toCutout = Math.min(alpha.distance(cutout), blend.distance(cutout));
                if (Math.max(alpha.bandRatio(), blend.bandRatio()) > cutout.bandRatio() - .12 || between > toCutout / 3)
                    throw new AssertionError(prefix + " q10/q95 cutout/alpha/blend=" + cutout.bandRatio() + "/" + alpha.bandRatio() + "/" + blend.bandRatio()
                            + "; alpha/blend distance=" + between + " vs cutout=" + toCutout);
                GunMod.LOGGER.info("PORT_HAND_MATERIALS: {} six visible muzzle-local effects; q10/q95 cutout/alpha/blend={}/{}/{}; color distances {}/{}",
                        backing, cutout.bandRatio(), alpha.bandRatio(), blend.bandRatio(), between, toCutout);
            } finally {
                ctx.runOnClient(mc -> { controller.removeAnimation(track); rt.clear(); });
            }
        } finally {
            world.getServer().runOnServer(s -> {
                var p = world.getConnection().getServerPlayer(); p.setItemInHand(InteractionHand.MAIN_HAND, previous); p.inventoryMenu.broadcastChanges();
            });
            ctx.waitFor(mc -> ItemStack.matches(mc.player.getMainHandItem(), previous));
        }
    }

    // The hand can breathe between frames: compare the distribution of color *inside* the
    // injected quad, not a whole-screen diff that would count moving geometry or the chat/HUD.
    private record Strength(int[] histogram, int count) {
        int quantile(double q) {
            int sum = 0;
            for (int i = 0; i < histogram.length; i++) if ((sum += histogram[i]) >= count * q) return i;
            throw new AssertionError("Empty color distribution");
        }
        double bandRatio() { return quantile(.1) / (double)quantile(.95); }
        // Compare all observed levels, not two individual percentile bins. A moving hand can
        // occlude 14% vs 16% of the bright stripe; q85 then jumps to the next *identical* color.
        double distance(Strength other) {
            int a = 0, b = 0; double distance = 0;
            for (int i = 0; i < histogram.length; i++) {
                a += histogram[i]; b += other.histogram[i];
                distance += Math.abs(a / (double)count - b / (double)other.count);
            }
            return distance;
        }
    }
    private static Strength strength(BufferedImage base, BufferedImage frame, String name) {
        int[] histogram = new int[256]; int count = 0;
        for (int y = frame.getHeight()/6; y < frame.getHeight()*4/5; y++)
            for (int x = frame.getWidth()/5; x < frame.getWidth()*4/5; x++) {
                int value = chroma(frame.getRGB(x,y));
                if (value > 12 && value - chroma(base.getRGB(x,y)) > 12) { histogram[value]++; count++; }
            }
        if (count < 60) throw new AssertionError(name + " added only " + count + " pink pixels");
        return new Strength(histogram, count);
    }
    private static int chroma(int c) { return Math.min(c >> 16 & 255, c & 255) - (c >> 8 & 255); }
}
