package com.tacz.guns.init;

import cn.sh1rocu.tacz.api.event.AddPackFindersEvent;
import com.tacz.guns.GunMod;
import com.tacz.guns.advancement.GunRefitTrigger;
import com.tacz.guns.advancement.TargetHitTrigger;
import com.tacz.guns.entity.sync.ModSyncedEntityData;
import net.minecraft.core.Registry;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import com.tacz.guns.network.HandshakeNetworking;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.resource.GunPackLoader;

public final class CommonRegistry {
    private static boolean LOAD_COMPLETE = false;

    public static void onSetupEvent() {
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "target_hit"), TargetHitTrigger.INSTANCE);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "gun_refit"), GunRefitTrigger.INSTANCE);
        AddPackFindersEvent.CALLBACK.register(CommonRegistry::onAddPackFinders);
        NetworkHandler.registerC2SPackets();
        HandshakeNetworking.init();
        ModSyncedEntityData.init();
    }

    /**
     * Upstream 1.21.1 ({@code CommonRegistry} @ 356bdf9) lets a water cauldron wash the dye off both
     * pegboards. 26.3 does it through the {@code minecraft:cauldron_can_remove_dye} item tag, which
     * only exists from 26.1 on; 1.21.11 still dispatches per item and its wash handler is private.
     * Reuse the exact handler vanilla mapped for leather boots: it checks {@code #minecraft:dyeable}
     * (both pegboards are listed) plus {@code DYED_COLOR}, clears the colour, awards the stat and
     * lowers the water level. Must run after {@link ModItems} registration.
     */
    public static void registerCauldronInteractions() {
        var water = CauldronInteraction.WATER.map();
        CauldronInteraction wash = water.get(Items.LEATHER_BOOTS);
        if (wash == null) {
            GunMod.LOGGER.warn("[TaCZ] Vanilla leather-armour cauldron wash not found; pegboards cannot be washed");
            return;
        }
        water.put(ModItems.GUN_PEGBOARD, wash);
        water.put(ModItems.GLOW_GUN_PEGBOARD, wash);
    }

    public static void onLoadComplete() {
        LOAD_COMPLETE = true;
    }

    public static boolean isLoadComplete() {
        return LOAD_COMPLETE;
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        event.addRepositorySource(GunPackLoader.forType(event.getPackType()));
    }
}
