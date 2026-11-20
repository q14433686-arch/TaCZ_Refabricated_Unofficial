package com.tacz.guns.entity.shooter;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.luaj.vm2.LuaError;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** Isolate a pack's Lua error to this entity and draw, without hiding unrelated Java errors. */
public final class GunScriptGuard {
    private GunScriptGuard() {
    }

    public static void run(ShooterDataHolder data, LivingEntity shooter, @Nullable Identifier gunId,
                           String operation, Runnable action) {
        run(data, shooter, gunId, operation, () -> {
            action.run();
            return null;
        }, null);
    }

    public static <T> T run(ShooterDataHolder data, LivingEntity shooter, @Nullable Identifier gunId,
                            String operation, Supplier<T> action, T fallback) {
        if (data.gunScriptFailed) {
            return fallback;
        }
        if (gunId == null || !gunId.equals(data.currentGunId)) {
            return fallback;
        }
        ItemStack current = data.currentGunItem == null ? ItemStack.EMPTY : data.currentGunItem.get();
        if (current != shooter.getMainHandItem() || !hasGunId(current, gunId)) {
            // The supplier may still point at the old slot even when both guns have the same ID.
            data.cancelGunScriptState();
            data.currentGunId = null;
            return fallback;
        }
        long generation = data.drawGeneration();
        try {
            T result = action.get();
            if (generation != data.drawGeneration()) return fallback;
            if (data.gunScriptFailed || data.currentGunId == null) {
                // A nested, immediately due cycle may have failed before the outer hook returned.
                data.cancelGunScriptState();
                return fallback;
            }
            return result;
        } catch (LuaError exception) {
            if (generation == data.drawGeneration()) {
                data.cancelGunScriptState();
                data.gunScriptFailed = true;
            }
            GunMod.LOGGER.error("Gun script failed for {} during {} (entity {}); paused until next draw",
                    gunId, operation, shooter.getUUID(), exception);
            return fallback;
        }
    }

    private static boolean hasGunId(@Nullable ItemStack stack, Identifier gunId) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        IGun gun = IGun.getIGunOrNull(stack);
        return gun != null && gunId.equals(gun.getGunId(stack));
    }
}
