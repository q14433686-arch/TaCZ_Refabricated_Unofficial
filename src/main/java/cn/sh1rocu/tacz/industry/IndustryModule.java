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
 * 当前覆盖范围：P0 供弹具数据层（Cartridge/Bullet/FeedDeviceData/GunStateData）
 * + P1 制造地基层（Material/WorkProcess/CoolingCurve/ToleranceTables 注册表、
 * HeatWorkData 工件组件、HeatRules/ProcessRules/QualityRules/ToleranceRules 规则层）。
 * 物品/方块/GUI 均按计划后置（先抽象与规则，后物品）。</p>
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
        // 顺序敏感：先注册组件（无副作用），再初始化带代码默认的注册表，最后挂数据重载
        // （P1 的 Material/Process/CoolingCurve/ToleranceTables 纯数据包驱动，首次 PLAYER_DATA
        // 重载前为空表——空表语义由各注册表/规则层兜底，见各自 javadoc）
        IndustryComponents.init();
        CartridgeRegistry.init();
        BulletRegistry.init();
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(IndustryDataLoader.INSTANCE);
        GunMod.LOGGER.info("[taczind] Industry module initialized (P0 feed/ammo layer + P1 manufacturing foundation: materials, heat processes, tolerance tables)");
    }
}
