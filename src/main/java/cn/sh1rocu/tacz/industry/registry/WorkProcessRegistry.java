package cn.sh1rocu.tacz.industry.registry;

import cn.sh1rocu.tacz.industry.api.process.WorkProcessType;
import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WorkProcessType 注册表（A-2 工序，纯数据包驱动；空表=无可用工序，交互层拒绝开工）。
 *
 * <p>装载校验原则（P0 惯例）：解析失败的单条记 error 跳过，绝不让一个坏 JSON 掀翻整个表；
 * 工序的 inputMaterial 悬空引用（材料未注册）在装载期仅告警——工序 JSON 与材料 JSON 的
 * 先后耦合不允许成为数据包地雷（21 章健壮性立场）。</p>
 */
public final class WorkProcessRegistry {
    private static final Map<Identifier, WorkProcessType> REGISTRY = new LinkedHashMap<>();

    private WorkProcessRegistry() {
    }

    public static synchronized void rebuild(Map<Identifier, WorkProcessType> datapackEntries) {
        REGISTRY.clear();
        REGISTRY.putAll(datapackEntries);
        // 交叉引用的装载期告警（拒载会误伤模块化数据包组合；仅提示给整合包作者修）
        for (WorkProcessType p : REGISTRY.values()) {
            if (!MaterialRegistry.contains(p.inputMaterial())) {
                GunMod.LOGGER.warn("[taczind] process {} 的 input_material {} 未注册 —— 该工序在实际办理前会被规则层拒绝",
                        p.id(), p.inputMaterial());
            }
        }
        GunMod.LOGGER.info("[taczind] WorkProcessRegistry rebuilt: {} entries", REGISTRY.size());
    }

    public static synchronized void clear() {
        REGISTRY.clear();
    }

    @Nullable
    public static WorkProcessType get(@Nullable Identifier id) {
        if (id == null) {
            return null;
        }
        return REGISTRY.get(id);
    }

    public static Collection<WorkProcessType> values() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }
}
