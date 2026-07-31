package cn.sh1rocu.tacz.industry.registry;

import cn.sh1rocu.tacz.industry.api.tolerance.AssemblyWeights;
import cn.sh1rocu.tacz.industry.api.tolerance.MachineTsWindow;
import cn.sh1rocu.tacz.industry.api.tolerance.TsGrade;
import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A-8 公差系统三张数据表的总注册处：
 * <ul>
 *   <li>机器基础 TS 窗口表（A-8a）—— key 为机器/工艺标识</li>
 *   <li>TS 分级带表（A-8d）—— 低→高排序后供 ToleranceRules.gradeOf 线性查询
 *       （量级 ≤10 条，线性查找比预构建区间树更合适，21 章性能立场）</li>
 *   <li>装配权重表（A-8c）—— 多套方案并存</li>
 * </ul>
 *
 * <p>空表语义：机器窗口/权重缺失 → 相关生产交互拒绝（规则层判空）；
 * 分级带缺失 → TS 属性映射取"最低档语义兜底"（见 {@link #gradeOrLowest}）——
 * 宁可让产品全部"土造"也不让存档因缺数据包而不可计算。</p>
 */
public final class ToleranceTables {
    private static final Map<Identifier, MachineTsWindow> MACHINES = new LinkedHashMap<>();
    private static final List<TsGrade> GRADES = new ArrayList<>();
    private static final Map<Identifier, AssemblyWeights> WEIGHTS = new LinkedHashMap<>();

    /** 默认枪械装配方案 id（数据包可覆盖同 id 或注册多方案；规则层与 GunData 增量字段引用它）。 */
    public static final Identifier DEFAULT_GUN_WEIGHTS = Identifier.fromNamespaceAndPath(IndustryIds.MOD_ID, "default_gun");

    private ToleranceTables() {
    }

    public static synchronized void rebuildMachines(Map<Identifier, MachineTsWindow> entries) {
        MACHINES.clear();
        MACHINES.putAll(entries);
        GunMod.LOGGER.info("[taczind] ToleranceTables.machines rebuilt: {} entries", MACHINES.size());
    }

    public static synchronized void rebuildGrades(Map<Identifier, TsGrade> entries) {
        GRADES.clear();
        GRADES.addAll(entries.values());
        GRADES.sort(Comparator.comparingInt(TsGrade::minTs));
        GunMod.LOGGER.info("[taczind] ToleranceTables.grades rebuilt: {} bands", GRADES.size());
    }

    public static synchronized void rebuildWeights(Map<Identifier, AssemblyWeights> entries) {
        WEIGHTS.clear();
        WEIGHTS.putAll(entries);
        GunMod.LOGGER.info("[taczind] ToleranceTables.weights rebuilt: {} entries", WEIGHTS.size());
    }

    public static synchronized void clearAll() {
        MACHINES.clear();
        GRADES.clear();
        WEIGHTS.clear();
    }

    @Nullable
    public static MachineTsWindow machine(@Nullable Identifier id) {
        if (id == null) {
            return null;
        }
        return MACHINES.get(id);
    }

    /** 分级带全表（已按 minTs 升序）。 */
    public static List<TsGrade> gradeBands() {
        return Collections.unmodifiableList(GRADES);
    }

    /**
     * TS → 分级带，空表/未命中时硬兜底：人为构造"最低档语义"的临时带
     * （散布×1.6 故障×4 耐久×0.6 初速±6%，即设计 A-8d 的 Crude 档语义）。
     * 兜底对象为瞬态产物，不进注册表。
     */
    public static TsGrade gradeOrLowest(int ts) {
        for (TsGrade band : GRADES) {
            if (band.contains(ts)) {
                return band;
            }
        }
        return new TsGrade(Identifier.fromNamespaceAndPath(IndustryIds.MOD_ID, "fallback_crude"),
                0, 101, "fallback_crude", 1.6f, 4.0f, 0.6f, 0.06f);
    }

    @Nullable
    public static AssemblyWeights weights(@Nullable Identifier id) {
        if (id == null) {
            return null;
        }
        return WEIGHTS.get(id);
    }

    /** 默认装配权重：查不到时返回 null（规则层拒绝装配，提示数据包缺失——装配是显式交互，不适用静默兜底）。 */
    @Nullable
    public static AssemblyWeights defaultGunWeights() {
        return WEIGHTS.get(DEFAULT_GUN_WEIGHTS);
    }
}
