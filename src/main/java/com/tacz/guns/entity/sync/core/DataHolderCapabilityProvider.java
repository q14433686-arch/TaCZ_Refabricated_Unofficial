package com.tacz.guns.entity.sync.core;

import cn.sh1rocu.tacz.util.forge.LazyOptional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * 26.2: CCA 5.2.3 与 Mojang 映射不兼容，改用 WeakHashMap 存储实体数据
 */
public class DataHolderCapabilityProvider {
    // Integrated client and server threads share this static map. A plain WeakHashMap can corrupt
    // itself under concurrent access, so all lifecycle operations must be synchronized.
    private static final Map<Entity, DataHolderCapabilityProvider> INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final DataHolder holder = new DataHolder();
    private final LazyOptional<DataHolder> optional = LazyOptional.of(() -> this.holder);

    public static DataHolderCapabilityProvider get(Entity entity) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(entity, e -> new DataHolderCapabilityProvider());
        }
    }

    public static Optional<DataHolderCapabilityProvider> maybeGet(Entity entity) {
        synchronized (INSTANCES) {
            return Optional.ofNullable(INSTANCES.get(entity));
        }
    }

    public static void remove(Entity entity) {
        synchronized (INSTANCES) {
            INSTANCES.remove(entity);
        }
    }

    public void invalidate() {
        this.optional.invalidate();
    }

    public Optional<DataHolder> getDataHolder() {
        return optional.resolve();
    }

    private ListTag serializeNBT() {
        ListTag list = new ListTag();
        this.holder.dataMap.forEach((key, entry) -> {
            if (key.save()) {
                CompoundTag keyTag = new CompoundTag();
                keyTag.putString("ClassKey", key.classKey().id().toString());
                keyTag.putString("DataKey", key.id().toString());
                keyTag.put("Value", entry.writeValue());
                list.add(keyTag);
            }
        });
        return list;
    }

    private void deserializeNBT(ListTag listTag) {
        this.holder.dataMap.clear();
        listTag.forEach(entryTag -> {
            CompoundTag keyTag = (CompoundTag) entryTag;
            Identifier classKey = Identifier.tryParse(keyTag.getStringOr("ClassKey", ""));
            Identifier dataKey = Identifier.tryParse(keyTag.getStringOr("DataKey", ""));
            Tag value = keyTag.get("Value");
            SyncedClassKey<?> syncedClassKey = SyncedEntityData.instance().getClassKey(classKey);
            if (syncedClassKey == null) {
                return;
            }
            SyncedDataKey<?, ?> syncedDataKey = SyncedEntityData.instance().getKey(syncedClassKey, dataKey);
            if (syncedDataKey == null || !syncedDataKey.save()) {
                return;
            }
            DataEntry<?, ?> entry = new DataEntry<>(syncedDataKey);
            entry.readValue(value);
            this.holder.dataMap.put(syncedDataKey, entry);
        });
    }

    public void readFromNbt(CompoundTag tag) {
        deserializeNBT(tag.getListOrEmpty("DataHolder"));
    }

    public void writeToNbt(CompoundTag tag) {
        ListTag listTag = serializeNBT();
        tag.put("DataHolder", listTag);
    }
}
