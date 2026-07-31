package cn.sh1rocu.tacz.industry.api.feed.rule;

import cn.sh1rocu.tacz.industry.api.ammo.LoadedRound;
import cn.sh1rocu.tacz.industry.api.feed.FeedDeviceData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 口径/供弹具兼容判定规则层（任务要求 6 的判定函数落地处）。
 *
 * <p><b>分层定位：</b>GunData 保持纯 POJO（只加字段，不加逻辑）；
 * 一切"能不能装"的判定收敛在本类。后续"装弹交互"（换弹匣/压桥夹/装漏夹/
 * 转轮装填）全部以本类为准入守卫。</p>
 *
 * <p><b>判定优先级（先物理后型号）：</b></p>
 * <ol>
 *   <li>口径兼容：弹药口径 == 枪膛口径规格（chamberedCartridge 缺省时回退枪的弹药 id）</li>
 *   <li>供弹具口径兼容：供弹具口径 == 口径兼容判定的同一规格</li>
 *   <li>型号兼容：供弹具型号标签 ∈ 枪的 compatibleFeedDeviceTag（通过外部 tag 解析，
 *       物品阶段接入；本层只判纯数据）</li>
 * </ol>
 */
public final class FeedCompatibility {
    private FeedCompatibility() {
    }

    /**
     * 解析枪的膛内口径规格：GunData 显式声明优先，缺省回退为其弹药 id
     * （旧枪包零迁移：弹药 id 本身即口径语义，CartridgeRegistry 内置同名兜底条目）。
     */
    public static Identifier resolveChamberCartridge(GunData gunData) {
        Identifier explicit = gunData.getChamberedCartridge();
        if (explicit != null) {
            return explicit;
        }
        // 回退：TACZ 原生弹药 id（CommonGunIndex 保证非空时才走到这；null 代表异常枪包）
        @Nullable Identifier ammoId = gunData.getAmmoId();
        return ammoId != null ? ammoId : Identifier.fromNamespaceAndPath("taczind", "unknown");
    }

    /**
     * 弹药个体能否上膛。
     */
    public static boolean canChamber(GunData gunData, LoadedRound round) {
        return canChamber(gunData, round.cartridge());
    }

    /**
     * 口径 id 能否上膛。
     */
    public static boolean canChamber(GunData gunData, Identifier cartridgeId) {
        return resolveChamberCartridge(gunData).equals(cartridgeId);
    }

    /**
     * 供弹具与枪的口径层兼容（型号标签判定见物品阶段；此处只判物理口径）。
     */
    public static boolean acceptsFeedDeviceCartridge(GunData gunData, FeedDeviceData device) {
        return canChamber(gunData, device.cartridge());
    }

    /**
     * 供弹具型号标签判定：GunData 未声明标签 = 全兼容（旧枪包语义）；
     * 声明后必须精确命中。物品阶段的 ItemStack 级判定（TagKey 解析）由
     * 供弹具物品实现调用并传入解析结果，本层保持纯数据、无世界依赖。
     */
    public static boolean acceptsFeedDeviceTag(GunData gunData, Identifier feedDeviceTagId) {
        @Nullable Identifier required = gunData.getCompatibleFeedDeviceTag();
        if (required == null) {
            return true;
        }
        return required.equals(feedDeviceTagId);
    }

    /**
     * 组合判定（一条路径）：弹、具、枪三者全通过才允许进入装填交互。
     */
    public static boolean canLoadFromDevice(GunData gunData, FeedDeviceData device, @Nullable Identifier feedDeviceTagId) {
        if (!acceptsFeedDeviceCartridge(gunData, device)) {
            return false;
        }
        return feedDeviceTagId == null || acceptsFeedDeviceTag(gunData, feedDeviceTagId);
    }
}
