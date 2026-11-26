package com.tacz.guns.init;

import cn.sh1rocu.tacz.api.event.AddPackFindersEvent;
import com.tacz.guns.GunMod;
import com.tacz.guns.advancement.GunRefitTrigger;
import com.tacz.guns.advancement.TargetHitTrigger;
import com.tacz.guns.entity.sync.ModSyncedEntityData;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
