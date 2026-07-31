package com.tacz.guns.api.item.ballistics;

import com.tacz.guns.api.item.component.GunMaintenanceData;
import com.tacz.guns.api.item.enums.ContaminationType;

/**
 * 保养交互逻辑。
 * <p>
 * 对应设计文档：H.2.5 保养操作流程设计
 * <p>
 * 采用"进度条+自动完成"模式，而非小游戏式交互。
 * <p>
 * 保养操作流程：
 * <ol>
 *   <li>擦拭（清洁刷+溶剂）→ 积碳-30</li>
 *   <li>润滑（枪械润滑油）→ 润滑+100</li>
 *   <li>除锈（钢丝刷+溶剂）→ 锈蚀-20（仅限轻度锈蚀）</li>
 *   <li>退出枪管异物（通条）→ contaminationType = NONE</li>
 * </ol>
 * <p>
 * 此类为纯计算工具，无状态，线程安全。
 */
public final class MaintenanceInteraction {

    /** 清洁操作减少的积碳量 */
    public static final float CLEANING_CARBON_REDUCTION = 30.0f;

    /** 润滑操作增加的润滑量 */
    public static final float LUBRICATION_AMOUNT = 100.0f;

    /** 除锈操作减少的锈蚀量 */
    public static final float RUST_REMOVAL_AMOUNT = 20.0f;

    /** 除锈操作的锈蚀上限（超过此值无法除锈，需要更换部件） */
    public static final float RUST_REMOVAL_MAX = 50.0f;

    private MaintenanceInteraction() {}

    /**
     * 执行清洁操作。
     * <p>
     * 需要工具：清洁刷 + 清洁溶剂
     * <p>
     * 效果：积碳-30
     *
     * @param maintenanceData 当前保养数据
     * @return 更新后的保养数据
     */
    public static GunMaintenanceData performCleaning(GunMaintenanceData maintenanceData) {
        return maintenanceData.withCleaning(CLEANING_CARBON_REDUCTION);
    }

    /**
     * 执行润滑操作。
     * <p>
     * 需要工具：枪械润滑油
     * <p>
     * 效果：润滑+100
     *
     * @param maintenanceData 当前保养数据
     * @return 更新后的保养数据
     */
    public static GunMaintenanceData performLubrication(GunMaintenanceData maintenanceData) {
        return maintenanceData.withLubrication(LUBRICATION_AMOUNT);
    }

    /**
     * 执行除锈操作。
     * <p>
     * 需要工具：钢丝刷 + 清洁溶剂
     * <p>
     * 效果：锈蚀-20（仅限锈蚀 < 50% 的武器）
     * <p>
     * 如果锈蚀超过50%，无法通过除锈修复，需要更换部件。
     *
     * @param maintenanceData 当前保养数据
     * @return 操作结果
     */
    public static RustRemovalResult performRustRemoval(GunMaintenanceData maintenanceData) {
        if (maintenanceData.corrosionLevel() >= RUST_REMOVAL_MAX) {
            return new RustRemovalResult(maintenanceData, false, "锈蚀过于严重，需要更换部件");
        }
        GunMaintenanceData updated = maintenanceData.withCorrosionAdded(-RUST_REMOVAL_AMOUNT);
        return new RustRemovalResult(updated, true, "除锈成功");
    }

    /**
     * 执行退出枪管异物操作。
     * <p>
     * 需要工具：通条
     * <p>
     * 效果：contaminationType = NONE
     *
     * @param maintenanceData 当前保养数据
     * @return 更新后的保养数据
     */
    public static GunMaintenanceData performBarrelClearing(GunMaintenanceData maintenanceData) {
        return maintenanceData.withContamination(ContaminationType.NONE);
    }

    /**
     * 执行完整保养操作（清洁+润滑）。
     * <p>
     * 需要工具：枪械保养套装（包含清洁刷+溶剂+润滑油）
     * <p>
     * 效果：积碳-30 + 润滑+100
     *
     * @param maintenanceData 当前保养数据
     * @return 更新后的保养数据
     */
    public static GunMaintenanceData performFullMaintenance(GunMaintenanceData maintenanceData) {
        GunMaintenanceData cleaned = performCleaning(maintenanceData);
        return performLubrication(cleaned);
    }

    /**
     * 判断是否需要保养（基于积碳/锈蚀/润滑状态）。
     *
     * @param maintenanceData 保养数据
     * @return 是否需要保养
     */
    public static boolean needsMaintenance(GunMaintenanceData maintenanceData) {
        return maintenanceData.carbonFoulingLevel() > 30.0f
                || maintenanceData.corrosionLevel() > 25.0f
                || maintenanceData.lubricationLevel() < 20.0f;
    }

    /**
     * 获取保养建议（用于UI提示）。
     *
     * @param maintenanceData 保养数据
     * @return 保养建议列表（0=无建议，1=需要清洁，2=需要润滑，3=需要除锈，4=需要退出异物）
     */
    public static int getMaintenanceSuggestion(GunMaintenanceData maintenanceData) {
        int suggestion = 0;
        if (maintenanceData.carbonFoulingLevel() > 30.0f) suggestion |= 1;  // 需要清洁
        if (maintenanceData.lubricationLevel() < 20.0f) suggestion |= 2;    // 需要润滑
        if (maintenanceData.corrosionLevel() > 25.0f) suggestion |= 4;      // 需要除锈
        if (maintenanceData.contaminationType() != ContaminationType.NONE) suggestion |= 8; // 需要退出异物
        return suggestion;
    }

    /**
     * 除锈操作结果。
     */
    public record RustRemovalResult(
            /** 更新后的保养数据 */
            GunMaintenanceData maintenanceData,
            /** 操作是否成功 */
            boolean success,
            /** 操作结果描述 */
            String message
    ) {}
}
