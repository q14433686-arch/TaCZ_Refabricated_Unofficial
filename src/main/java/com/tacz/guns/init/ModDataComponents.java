package com.tacz.guns.init;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.component.AmmoData;
import com.tacz.guns.api.item.component.FeedDeviceData;
import com.tacz.guns.api.item.component.GunMaintenanceData;
import com.tacz.guns.api.item.component.GunStateData;
import com.tacz.guns.api.item.component.GunWearData;
import com.tacz.guns.api.item.component.ToleranceData;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponentType;

/**
 * 注册所有新增的 DataComponentType。
 * <p>
 * 在 Minecraft 26.2+ 中，自定义数据组件通过
 * {@code Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ...)} 注册。
 * <p>
 * 对应设计文档 P0 阶段：新增 DataComponent 体系
 */
public class ModDataComponents {

    public static final DataComponentType<AmmoData> AMMO_DATA = register("ammo_data",
            DataComponentType.<AmmoData>builder()
                    .persistent(AmmoData.CODEC)
                    .networkSynchronized(AmmoData.STREAM_CODEC)
                    .build());

    public static final DataComponentType<GunWearData> GUN_WEAR_DATA = register("gun_wear_data",
            DataComponentType.<GunWearData>builder()
                    .persistent(GunWearData.CODEC)
                    .networkSynchronized(GunWearData.STREAM_CODEC)
                    .build());

    public static final DataComponentType<GunMaintenanceData> GUN_MAINTENANCE_DATA = register("gun_maintenance_data",
            DataComponentType.<GunMaintenanceData>builder()
                    .persistent(GunMaintenanceData.CODEC)
                    .networkSynchronized(GunMaintenanceData.STREAM_CODEC)
                    .build());

    public static final DataComponentType<ToleranceData> TOLERANCE_DATA = register("tolerance_data",
            DataComponentType.<ToleranceData>builder()
                    .persistent(ToleranceData.CODEC)
                    .networkSynchronized(ToleranceData.STREAM_CODEC)
                    .build());

    public static final DataComponentType<GunStateData> GUN_STATE_DATA = register("gun_state_data",
            DataComponentType.<GunStateData>builder()
                    .persistent(GunStateData.CODEC)
                    .networkSynchronized(GunStateData.STREAM_CODEC)
                    .build());

    /**
     * P0补充：供弹具数据组件。
     * <p>
     * 挂载在弹匣/桥夹/弹链等供弹具物品上。
     * 供弹具物品必须设置为不可堆叠（每个实体独立记录内部装填状态与磨损）。
     * <p>
     * 使用密封接口分派模式，根据 FeedSystemType 选择对应的数据结构。
     */
    public static final DataComponentType<FeedDeviceData> FEED_DEVICE_DATA = register("feed_device_data",
            DataComponentType.<FeedDeviceData>builder()
                    .persistent(FeedDeviceData.CODEC)
                    .networkSynchronized(FeedDeviceData.STREAM_CODEC)
                    .build());

    public static void init() {
        // 触发类加载，确保所有组件注册
    }

    private static <T> DataComponentType<T> register(String name, DataComponentType<T> type) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(GunMod.MOD_ID, name), type);
    }
}
