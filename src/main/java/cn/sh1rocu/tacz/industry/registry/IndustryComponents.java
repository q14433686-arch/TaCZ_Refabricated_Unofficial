package cn.sh1rocu.tacz.industry.registry;

import cn.sh1rocu.tacz.industry.api.ammo.LoadedRound;
import cn.sh1rocu.tacz.industry.api.feed.FeedDeviceData;
import cn.sh1rocu.tacz.industry.api.gun.GunStateData;
import cn.sh1rocu.tacz.industry.api.heat.HeatWorkData;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * TACZ-INDUSTRIAL 全部 DataComponentType 注册（任务要求 4 落地处）。
 *
 * <p>26.2 的 ItemStack 布局为 {id, count, components}，typed component 是
 * 本模组一切"物品个体数据"的唯一载体（取代旧 NBT bag 惯例）。</p>
 */
public final class IndustryComponents {
    private IndustryComponents() {
    }

    /**
     * 供弹具数据：挂载在弹匣/桥夹/漏夹/弹链等待增物品上（物品必须不可堆叠，
     * 见 {@code FeedItemRules}）。多态 Codec 按 "feed_system" 键分派六机构。
     */
    public static final DataComponentType<FeedDeviceData> FEED_DEVICE_DATA =
            register("feed_device_data", FeedDeviceData.CODEC);

    /**
     * 枪械运行时状态：chamberedRound(枪膛个体弹药) + 枪管异物标记。
     * 替代原"是否已上膛"布尔（原布尔转为镜像，见 GunStateData 注释）。
     */
    public static final DataComponentType<GunStateData> GUN_STATE_DATA =
            register("gun_state_data", GunStateData.CODEC.codec());

    /**
     * 单发弹药个体数据模板：供"工厂弹/手装弹"弹药物品携带其基准个体参数，
     * 装填时被复制进供弹具队列（装填时掷骰引入批次偏差）。
     */
    public static final DataComponentType<LoadedRound> LOADED_ROUND =
            register("loaded_round", LoadedRound.CODEC);

    /**
     * 工件数据（A-2）：热加工半成品物品上的"热+工序进度+材料形态"运行时组件。
     * 工件物品同样必须不可堆叠（同供弹具原则）。
     */
    public static final DataComponentType<HeatWorkData> WORKPIECE =
            register("workpiece", HeatWorkData.CODEC);

    private static <T> DataComponentType<T> register(String name, Codec<T> codec) {
        return Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(IndustryIds.MOD_ID, name),
                DataComponentType.<T>builder().persistent(codec).build()
        );
    }

    /**
     * 模块入口显式调用（Fabric 无自动注册扫描，静态字段惰性加载）。
     */
    public static void init() {
        // 触发静态字段初始化即完成注册
    }
}
