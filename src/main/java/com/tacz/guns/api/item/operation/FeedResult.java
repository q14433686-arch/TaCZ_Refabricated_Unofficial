package com.tacz.guns.api.item.operation;

import com.tacz.guns.api.item.component.FeedDeviceData;
import com.tacz.guns.api.item.component.GunStateData;
import com.tacz.guns.api.item.component.LoadedRound;
import com.tacz.guns.api.item.enums.MalfunctionType;
import org.jetbrains.annotations.Nullable;

/**
 * 供弹操作原语结果。
 * <p>
 * 所有原语操作返回统一的 Result 类型，携带成功/失败状态和副作用数据。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>成功时：result.isSuccess() == true，data 包含操作后的新状态</li>
 *   <li>失败时：result.isSuccess() == false，malfunctionType 指明故障类型</li>
 *   <li>不可变记录：所有字段 final，每次操作返回新实例</li>
 * </ul>
 */
public sealed interface FeedResult {

    /**
     * 操作是否成功
     */
    boolean isSuccess();

    /**
     * 操作失败时的故障类型（成功时为 null）
     */
    @Nullable MalfunctionType malfunctionType();

    // ====== 从供弹具取出下一发 ======

    /**
     * stripNextRound 的结果。
     * <p>
     * 成功：取出的弹药 + 修改后的供弹具数据
     * 失败：进弹失败 (FAILURE_TO_FEED) 或 双重进弹 (DOUBLE_FEED)
     */
    record StripResult(
            boolean success,
            @Nullable LoadedRound strippedRound,
            @Nullable FeedDeviceData updatedDevice,
            @Nullable MalfunctionType malfunctionType
    ) implements FeedResult {
        /** 成功：取出弹药 */
        public static StripResult ok(LoadedRound round, FeedDeviceData updatedDevice) {
            return new StripResult(true, round, updatedDevice, null);
        }

        /** 失败：进弹失败 */
        public static StripResult failFeed() {
            return new StripResult(false, null, null, MalfunctionType.FAILURE_TO_FEED);
        }

        /** 失败：双重进弹 */
        public static StripResult failDoubleFeed() {
            return new StripResult(false, null, null, MalfunctionType.DOUBLE_FEED);
        }

        /** 失败：供弹具为空（不是故障，只是没有弹药） */
        public static StripResult empty() {
            return new StripResult(false, null, null, null);
        }
    }

    // ====== 推弹入膛 ======

    /**
     * chamberRound 的结果。
     * <p>
     * 成功：更新后的 GunStateData（chamberedRound 已设置）
     * 失败：进弹失败（弹头卡在进弹坡上）
     */
    record ChamberResult(
            boolean success,
            @Nullable GunStateData updatedState,
            @Nullable MalfunctionType malfunctionType
    ) implements FeedResult {
        /** 成功：弹药入膛 */
        public static ChamberResult ok(GunStateData state) {
            return new ChamberResult(true, state, null);
        }

        /** 失败：进弹失败 */
        public static ChamberResult fail() {
            return new ChamberResult(false, null, MalfunctionType.FAILURE_TO_FEED);
        }
    }

    // ====== 从枪膛抽壳 ======

    /**
     * extractFromChamber 的结果。
     * <p>
     * 成功：抽出的弹壳 + 更新后的 GunStateData（chamberedRound 已清空）
     * 失败：抽壳失败 (FAILURE_TO_EXTRACT)
     */
    record ExtractResult(
            boolean success,
            @Nullable LoadedRound extractedCase,
            @Nullable GunStateData updatedState,
            @Nullable MalfunctionType malfunctionType
    ) implements FeedResult {
        /** 成功：抽出弹壳 */
        public static ExtractResult ok(LoadedRound spentCase, GunStateData state) {
            return new ExtractResult(true, spentCase, state, null);
        }

        /** 失败：抽壳失败（弹壳留在膛内） */
        public static ExtractResult fail() {
            return new ExtractResult(false, null, null, MalfunctionType.FAILURE_TO_EXTRACT);
        }

        /** 膛内无弹（不是故障，只是没有弹壳可抽） */
        public static ExtractResult empty() {
            return new ExtractResult(false, null, null, null);
        }
    }

    // ====== 抛壳 ======

    /**
     * ejectCase 的结果。
     * <p>
     * 成功：抛出的弹壳（可以生成掉落物或丢弃）
     * 失败：抛壳失败/烟囱 (FAILURE_TO_EJECT)
     */
    record EjectResult(
            boolean success,
            @Nullable LoadedRound ejectedCase,
            @Nullable MalfunctionType malfunctionType
    ) implements FeedResult {
        /** 成功：弹壳抛出 */
        public static EjectResult ok(LoadedRound ejectedCase) {
            return new EjectResult(true, ejectedCase, null);
        }

        /** 失败：抛壳失败/烟囱 */
        public static EjectResult fail() {
            return new EjectResult(false, null, MalfunctionType.FAILURE_TO_EJECT);
        }
    }

    // ====== 压入供弹具 ======

    /**
     * insertRound 的结果。
     * <p>
     * 成功：更新后的供弹具数据
     * 失败：供弹具已满 / 口径不兼容
     */
    record InsertResult(
            boolean success,
            @Nullable FeedDeviceData updatedDevice,
            @Nullable String failureReason
    ) implements FeedResult {
        /** 成功：弹药已装入 */
        public static InsertResult ok(FeedDeviceData updatedDevice) {
            return new InsertResult(true, updatedDevice, null);
        }

        /** 失败：供弹具已满 */
        public static InsertResult full() {
            return new InsertResult(false, null, "Feed device is full");
        }

        /** 失败：口径不兼容 */
        public static InsertResult incompatible() {
            return new InsertResult(false, null, "Cartridge type incompatible");
        }
    }
}
