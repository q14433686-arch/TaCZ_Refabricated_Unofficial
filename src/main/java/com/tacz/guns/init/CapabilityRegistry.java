package com.tacz.guns.init;

import cn.sh1rocu.tacz.api.event.EntityRemoveEvent;
import com.tacz.guns.entity.sync.core.DataHolderCapabilityProvider;
import net.minecraft.server.level.ServerPlayer;

/**
 * 26.2: CCA 已移除，改用 DataHolderCapabilityProvider 内置的 WeakHashMap 存储
 */
public class CapabilityRegistry {
    public static void init() {
        EntityRemoveEvent.EVENT.register(event -> {
            var entity = event.getEntity();
            if (!(entity instanceof ServerPlayer)) {
                DataHolderCapabilityProvider.maybeGet(entity).ifPresent(DataHolderCapabilityProvider::invalidate);
                DataHolderCapabilityProvider.remove(entity);
            }
        });
    }
}