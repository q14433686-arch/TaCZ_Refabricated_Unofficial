package com.tacz.guns.compat.carryon;

import com.tacz.guns.GunMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Reflection bridge for the optional Carry On 2.9.x API.
 *
 * <p>Carry On is deliberately not a compile-time dependency. All callers are additionally gated
 * by the optional mixin config, so these lookups only run when the mod is installed.</p>
 */
public final class CarryOnReflection {
    private static final String DATA_MANAGER_CLASS = "tschipp.carryon.common.carry.CarryOnDataManager";
    private static final String DATA_CLASS = "tschipp.carryon.common.carry.CarryOnData";

    private static volatile boolean resolved;
    private static Method getCarryData;
    private static Method getBlock;
    private static Method getBlockEntity;

    private CarryOnReflection() {
    }

    @Nullable
    public static BlockState getCarriedBlock(Player player) {
        Object data = getData(player);
        if (data == null) {
            return null;
        }
        try {
            return (BlockState) getBlock.invoke(data);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // CarryOnData#getBlock throws while an entity (rather than a block) is being carried.
            return null;
        }
    }

    @Nullable
    public static BlockEntity getCarriedBlockEntity(Player player, BlockPos pos, HolderLookup.Provider lookup) {
        Object data = getData(player);
        if (data == null) {
            return null;
        }
        try {
            return (BlockEntity) getBlockEntity.invoke(data, pos, lookup);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static Object getData(Player player) {
        resolve();
        if (getCarryData == null) {
            return null;
        }
        try {
            return getCarryData.invoke(null, player);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        synchronized (CarryOnReflection.class) {
            if (resolved) {
                return;
            }
            try {
                Class<?> managerClass = Class.forName(DATA_MANAGER_CLASS);
                Class<?> dataClass = Class.forName(DATA_CLASS);
                getCarryData = managerClass.getMethod("getCarryData", Player.class);
                getBlock = dataClass.getMethod("getBlock");
                getBlockEntity = dataClass.getMethod("getBlockEntity", BlockPos.class, HolderLookup.Provider.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                GunMod.LOGGER.warn("Carry On compatibility could not resolve the audited 2.9.x data API", e);
            } finally {
                resolved = true;
            }
        }
    }
}
