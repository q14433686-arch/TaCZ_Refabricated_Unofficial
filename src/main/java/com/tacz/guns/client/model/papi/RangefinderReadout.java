package com.tacz.guns.client.model.papi;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.pojo.display.gun.Rangefinder;
import com.tacz.guns.util.HitboxHelper;
import com.tacz.guns.util.block.BlockRayTrace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/** Client-thread-only, read-only world query. Collector callbacks receive text, not a live player. */
public final class RangefinderReadout {
    private record Sample(long tick, String text) {}
    // The definition, not its refresh interval, identifies a text node's settings.
    private static final Map<Rangefinder, Sample> samples = new WeakHashMap<>();
    private static LocalPlayer owner;
    private static ItemStack held = ItemStack.EMPTY;

    private RangefinderReadout() {}

    public static void clear() {
        samples.clear();
        owner = null;
        held = ItemStack.EMPTY;
    }

    static String read(ItemStack stack, @Nullable Rangefinder config) {
        if (config == null) return "";
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !player.isAlive() || stack == null || !(stack.getItem() instanceof IGun gun)) return Rangefinder.MISS;
        var hand = player.getMainHandItem();
        if (!(hand.getItem() instanceof IGun current) || hand != stack && (!gun.getGunId(stack).equals(current.getGunId(hand))
                || !gun.getGunDisplayId(stack).equals(current.getGunDisplayId(hand)))) return Rangefinder.MISS;
        if (owner != player || held != hand) {
            samples.clear();
            owner = player;
            held = hand;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (IClientPlayerGunOperator.fromLocalPlayer(player).getClientAimingProgress(partial) < .9f) {
            samples.remove(config);
            return Rangefinder.MISS;
        }
        long tick = player.level().getGameTime();
        Sample old = samples.get(config);
        if (old != null && tick >= old.tick && tick - old.tick < config.getRefreshTick()) return old.text;
        String text = config.format(measure(player, partial, config.getMaxDistance()));
        samples.put(config, new Sample(tick, text));
        return text;
    }

    private static double measure(LocalPlayer player, float partial, float range) {
        if (range == 0) return -1;
        var level = player.level();
        Vec3 eye = player.getEyePosition(partial), look = player.getViewVector(partial);
        var block = BlockRayTrace.rayTraceBlocks(level, new ClipContext(eye, eye.add(look.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        boolean miss = block.getType() == HitResult.Type.MISS;
        double endDistance = miss ? range : Math.min(range, eye.distanceTo(block.getLocation()));
        // Split the entity broad phase into short boxes instead of searching a view-distance-sized cuboid.
        // Clip to the first colliding block. The pick padding matches the upstream display convention.
        for (double d = 0; d < endDistance; d += 32) {
            Vec3 from = eye.add(look.scale(d)), to = eye.add(look.scale(Math.min(endDistance, d + 32)));
            var area = new AABB(from, to).inflate(.25, 8, .25);
            double closest = Double.POSITIVE_INFINITY;
            for (var entity : level.getEntities(player, area, e -> e.isAlive() && e.isPickable()
                    && !e.isSpectator() && !e.isPassengerOfSameVehicle(player))) {
                var box = HitboxHelper.getFixedBoundingBox(entity, player).inflate(.25, 8, .25);
                if (box.contains(from)) return d;
                var hit = box.clip(from, to);
                if (hit.isPresent()) closest = Math.min(closest, eye.distanceTo(hit.get()));
            }
            if (Double.isFinite(closest)) return Math.min(closest, endDistance);
        }
        return miss ? -1 : endDistance;
    }
}
