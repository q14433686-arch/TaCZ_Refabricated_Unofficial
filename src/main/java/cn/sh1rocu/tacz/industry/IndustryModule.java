package cn.sh1rocu.tacz.industry;

import cn.sh1rocu.tacz.industry.loader.IndustryDataLoader;
import cn.sh1rocu.tacz.industry.registry.BulletRegistry;
import cn.sh1rocu.tacz.industry.registry.CartridgeRegistry;
import cn.sh1rocu.tacz.industry.registry.IndustryComponents;
import com.tacz.guns.GunMod;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;

/**
 * TACZ-INDUSTRIAL 子系统总入口。
 *
 * <p>从 TaCZFabric.onInitialize 显式调用（Fabric 无自动注册扫描）。
 * 当前覆盖范围（P0 补充数据层）：CartridgeType/BulletType 注册表、
 * FeedDeviceData 六机构数据形状、GunStateData、DataComponent 注册、
 * 数据包重载 loader。物品/方块/GUI 均按计划后置（先抽象与规则，后物品）。</p>
 */
public final class IndustryModule {
    private static boolean initialized = false;

    private IndustryModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        // 顺序敏感：先注册组件（无副作用），再初始化内置注册表，最后挂数据重载
        IndustryComponents.init();
        CartridgeRegistry.init();
        BulletRegistry.init();
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(IndustryDataLoader.INSTANCE);
        GunMod.LOGGER.info("[taczind] Industry module initialized (P0 data layer: cartridge/bullet registries, feed device data, gun state data)");
    }
}
