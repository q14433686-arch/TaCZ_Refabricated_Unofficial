package cn.sh1rocu.tacz.industry.registry;

import cn.sh1rocu.tacz.industry.api.material.MaterialType;
import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MaterialType 注册表（A-1 材料树，纯数据包驱动）。
 *
 * <p><b>与 CartridgeRegistry 的差异（有意为之）：</b>材料树是本模组自有内容，
 * 不存在旧枪包兼容包袱——因此不设代码内置默认，全部条目来自数据包
 * （本模组 resources/data/taczind/material/ 就是内置数据包，整合包可覆盖）。
 * 空注册表时一切查询返回 null，规则层一律按"无材料"拒绝，不崩档。</p>
 *
 * <p>生命周期与线程模型同 P0 注册表：服务端数据重载整体重建，运行期只读。</p>
 */
public final class MaterialRegistry {
    private static final Map<Identifier, MaterialType> REGISTRY = new LinkedHashMap<>();

    private MaterialRegistry() {
    }

    public static synchronized void rebuild(Map<Identifier, MaterialType> datapackEntries) {
        REGISTRY.clear();
        REGISTRY.putAll(datapackEntries);
        GunMod.LOGGER.info("[taczind] MaterialRegistry rebuilt: {} entries", REGISTRY.size());
    }

    public static synchronized void clear() {
        REGISTRY.clear();
    }

    @Nullable
    public static MaterialType get(@Nullable Identifier id) {
        if (id == null) {
            return null;
        }
        return REGISTRY.get(id);
    }

    public static boolean contains(Identifier id) {
        return REGISTRY.containsKey(id);
    }

    public static Collection<MaterialType> values() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /** 按层查（手册/配方推导用）。 */
    public static int tierOf(Identifier id) {
        MaterialType t = get(id);
        return t == null ? -1 : t.tier();
    }
}
