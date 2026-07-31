package com.tacz.guns.api.item.operation;

import com.tacz.guns.api.item.component.FeedDeviceData;
import com.tacz.guns.api.item.component.GunStateData;
import com.tacz.guns.api.item.component.LoadedRound;
import com.tacz.guns.api.item.enums.FeedSystemType;
import com.tacz.guns.api.item.enums.MalfunctionType;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * 供弹操作原语接口。
 * <p>
 * 这是一组纯粹的、无状态的数据操作函数，每个只做一件最小的事。
 * 状态机只负责调度这些原语，不直接操作弹药数据。
 * <p>
 * <b>为什么每种供弹具的原语实现不同</b>：
 * <ul>
 *   <li>BoxMagazineData.stripNextRound() → 取 list 尾部（LIFO），检查 springFatigue 决定是否供弹成功</li>
 *   <li>CylinderData.stripNextRound() → 不"取出"，而是旋转 alignedChamberIndex 到下一格，读取那格状态</li>
 *   <li>EnBlocClipData.stripNextRound() → 取一发，若取完则触发 autoEject 标记（Garand 那声"叮"）</li>
 *   <li>BeltData.stripNextRound() → 取队首，若是可散式弹链则产生一个"废弹链链节"掉落物</li>
 *   <li>TubularMagazineData.stripNextRound() → 取队首（FIFO），尖头弹警告</li>
 *   <li>StripperClipData.stripNextRound() → 取一发，全部取出后标记为 isConsumed</li>
 *   <li>DrumMagazineData.stripNextRound() → 取尾部（LIFO），检查 springFatigue + windingTension</li>
 * </ul>
 * <p>
 * 对应设计文档：P1 供弹操作原语
 */
public final class FeedOperation {

    private static final Random RANDOM = new Random();

    private FeedOperation() {}

    // ====== 1. stripNextRound — 从供弹具取出下一发 ======

    /**
     * 从供弹具取出下一发弹药。
     * <p>
     * 不改枪膛，只从供弹具中取出。
     * 每种供弹具的物理差异（LIFO/FIFO/旋转/整体弹出/弹链链节）都收敛在此实现中。
     *
     * @param device              供弹具数据
     * @param reliabilityModifier 综合可靠性修正（0.0~1.0），来自公差/磨损/保养等
     * @return StripResult：取出的弹药 + 修改后的供弹具数据
     */
    public static FeedResult.StripResult stripNextRound(FeedDeviceData device, float reliabilityModifier) {
        if (device == null || device.isEmpty()) {
            return FeedResult.StripResult.empty();
        }

        return switch (device) {
            case com.tacz.guns.api.item.component.BoxMagazineData mag -> stripBoxMagazine(mag, reliabilityModifier);
            case com.tacz.guns.api.item.component.TubularMagazineData mag -> stripTubularMagazine(mag, reliabilityModifier);
            case com.tacz.guns.api.item.component.CylinderData cyl -> stripCylinder(cyl, reliabilityModifier);
            case com.tacz.guns.api.item.component.BeltData belt -> stripBelt(belt, reliabilityModifier);
            case com.tacz.guns.api.item.component.StripperClipData clip -> stripStripperClip(clip, reliabilityModifier);
            case com.tacz.guns.api.item.component.EnBlocClipData clip -> stripEnBlocClip(clip, reliabilityModifier);
            case com.tacz.guns.api.item.component.DrumMagazineData drum -> stripDrumMagazine(drum, reliabilityModifier);
        };
    }

    // ====== 2. chamberRound — 推弹入膛 ======

    /**
     * 将弹药推入枪膛。
     *
     * @param state 当前枪械状态
     * @param round  要入膛的弹药
     * @return ChamberResult：更新后的 GunStateData
     */
    public static FeedResult.ChamberResult chamberRound(GunStateData state, LoadedRound round) {
        if (state == null || round == null) {
            return FeedResult.ChamberResult.fail();
        }
        // 膛内已有弹（闭膛待击的枪不应该有这种情况，但双重进弹可以）
        if (state.hasChamberedRound()) {
            return FeedResult.ChamberResult.fail();
        }
        GunStateData newState = state.withChamberedRound(round);
        return FeedResult.ChamberResult.ok(newState);
    }

    // ====== 3. extractFromChamber — 从枪膛抽壳 ======

    /**
     * 从枪膛抽出弹壳。
     * <p>
     * 可能失败：抽壳失败（弹壳留在膛内）。
     * 抽壳失败概率受弹壳状态（变形/锈蚀）和枪机状态影响。
     *
     * @param state              当前枪械状态
     * @param reliabilityModifier 综合可靠性修正
     * @return ExtractResult：抽出的弹壳 + 更新后的 GunStateData
     */
    public static FeedResult.ExtractResult extractFromChamber(GunStateData state, float reliabilityModifier) {
        if (state == null || !state.hasChamberedRound()) {
            return FeedResult.ExtractResult.empty();
        }

        LoadedRound chambered = state.chamberedRound();

        // 抽壳失败概率计算
        float extractFailChance = 0.0f;

        // 弹壳状态影响
        if (chambered.caseCondition() == com.tacz.guns.api.item.enums.CaseCondition.CRACKED) {
            extractFailChance += 0.15f;  // 裂纹弹壳卡在膛内
        } else if (chambered.caseCondition() == com.tacz.guns.api.item.enums.CaseCondition.DEPORMED) {
            extractFailChance += 0.25f;  // 变形弹壳更难抽出
        } else if (chambered.caseCondition() == com.tacz.guns.api.item.enums.CaseCondition.CORRODED) {
            extractFailChance += 0.20f;  // 锈蚀弹壳卡在膛内
        }

        // 腐蚀性弹药残留影响
        if (chambered.isCorrosive()) {
            extractFailChance += 0.05f;
        }

        // 可靠性修正
        extractFailChance *= (1.0f - reliabilityModifier);

        // 抽壳失败判定
        if (RANDOM.nextFloat() < extractFailChance) {
            return FeedResult.ExtractResult.fail();
        }

        // 抽壳成功：弹壳留在结果中，枪膛清空
        GunStateData newState = state.withChamberedRoundFired();
        return FeedResult.ExtractResult.ok(chambered, newState);
    }

    // ====== 4. ejectCase — 抛壳 ======

    /**
     * 抛出弹壳。
     * <p>
     * 可能失败：抛壳失败/烟囱（弹壳卡在抛壳口竖直方向）。
     * 抛壳失败概率受枪机速度和弹壳状态影响。
     *
     * @param spentCase          待抛出的弹壳
     * @param reliabilityModifier 综合可靠性修正
     * @return EjectResult：抛出的弹壳（可生成掉落物）
     */
    public static FeedResult.EjectResult ejectCase(LoadedRound spentCase, float reliabilityModifier) {
        if (spentCase == null) {
            return FeedResult.EjectResult.ok(null);
        }

        // 抛壳失败概率计算
        float ejectFailChance = 0.005f;  // 基础 0.5%

        // 弹壳状态影响
        if (spentCase.caseCondition() == com.tacz.guns.api.item.enums.CaseCondition.DEPORMED) {
            ejectFailChance += 0.08f;  // 变形弹壳容易卡在抛壳口
        }

        // 可靠性修正
        ejectFailChance *= (1.0f - reliabilityModifier);

        // 抛壳失败判定
        if (RANDOM.nextFloat() < ejectFailChance) {
            return FeedResult.EjectResult.fail();
        }

        return FeedResult.EjectResult.ok(spentCase);
    }

    // ====== 5. insertRound — 压入供弹具 ======

    /**
     * 向供弹具压入一发弹药。
     * <p>
     * 用于装填/复装场景。
     *
     * @param device 供弹具数据
     * @param round  要压入的弹药
     * @return InsertResult：更新后的供弹具数据
     */
    public static FeedResult.InsertResult insertRound(FeedDeviceData device, LoadedRound round) {
        if (device == null || round == null) {
            return FeedResult.InsertResult.incompatible();
        }

        // 口径兼容性检查
        if (!device.isCartridgeCompatible(round.cartridgeType())) {
            return FeedResult.InsertResult.incompatible();
        }

        // 供弹具已满
        if (device.isFull()) {
            return FeedResult.InsertResult.full();
        }

        FeedDeviceData updated = switch (device) {
            case com.tacz.guns.api.item.component.BoxMagazineData mag -> mag.loadRound(round);
            case com.tacz.guns.api.item.component.TubularMagazineData mag -> mag.loadRound(round);
            case com.tacz.guns.api.item.component.CylinderData cyl -> cyl.loadNextEmpty(round);
            case com.tacz.guns.api.item.component.BeltData belt -> belt.loadRound(round);
            case com.tacz.guns.api.item.component.StripperClipData clip -> clip.loadRound(round);
            case com.tacz.guns.api.item.component.EnBlocClipData clip -> clip.loadRound(round);
            case com.tacz.guns.api.item.component.DrumMagazineData drum -> drum.loadRound(round);
        };

        return FeedResult.InsertResult.ok(updated);
    }

    // ====== 各供弹具的 stripNextRound 实现 ======

    private static FeedResult.StripResult stripBoxMagazine(
            com.tacz.guns.api.item.component.BoxMagazineData mag, float reliabilityModifier) {
        if (mag.isEmpty()) return FeedResult.StripResult.empty();

        // 弹簧疲劳供弹可靠性判定
        float successChance = mag.getOverallReliability() * reliabilityModifier;
        if (RANDOM.nextFloat() > successChance) {
            return FeedResult.StripResult.failFeed();
        }

        // 取出弹药（LIFO：取末尾）
        LoadedRound round = mag.feedRound();
        if (round == null) return FeedResult.StripResult.empty();

        // 弹簧疲劳度增加（每次供弹循环+0.001）
        com.tacz.guns.api.item.component.BoxMagazineData updatedMag =
                mag.withSpringFatigueAdded(0.001f);

        return FeedResult.StripResult.ok(round, updatedMag);
    }

    private static FeedResult.StripResult stripTubularMagazine(
            com.tacz.guns.api.item.component.TubularMagazineData mag, float reliabilityModifier) {
        if (mag.isEmpty()) return FeedResult.StripResult.empty();

        // 管状弹仓可靠性极高（机械结构简单）
        float successChance = 0.98f * reliabilityModifier;
        if (RANDOM.nextFloat() > successChance) {
            return FeedResult.StripResult.failFeed();
        }

        // 取出弹药（FIFO：取头部）
        LoadedRound round = mag.feedRound();
        if (round == null) return FeedResult.StripResult.empty();

        return FeedResult.StripResult.ok(round, mag);
    }

    private static FeedResult.StripResult stripCylinder(
            com.tacz.guns.api.item.component.CylinderData cyl, float reliabilityModifier) {
        // 转轮弹巢不"取出"弹药，而是旋转到下一格
        // 检查对齐的弹膛是否有实弹
        com.tacz.guns.api.item.component.CylinderChamber aligned = cyl.getAlignedChamber();
        if (aligned == null || !aligned.canFire()) {
            // 尝试旋转到下一格
            cyl = cyl.fireAndRotate();
            aligned = cyl.getAlignedChamber();
            if (aligned == null || !aligned.canFire()) {
                return FeedResult.StripResult.empty();
            }
        }

        // 转轮弹巢可靠性极高
        float successChance = 0.99f * reliabilityModifier;
        if (RANDOM.nextFloat() > successChance) {
            return FeedResult.StripResult.failFeed();
        }

        // 返回对齐弹膛的弹药（但不从弹巢中取出）
        // 转轮弹巢的弹药留在原位，击发后变为 SPENT
        return FeedResult.StripResult.ok(aligned.round(), cyl);
    }

    private static FeedResult.StripResult stripBelt(
            com.tacz.guns.api.item.component.BeltData belt, float reliabilityModifier) {
        if (belt.isEmpty()) return FeedResult.StripResult.empty();

        // 弹链供弹可靠性较低
        float linkReliability = belt.getLinkReliabilityModifier();
        float successChance = linkReliability * reliabilityModifier;
        if (RANDOM.nextFloat() > successChance) {
            return FeedResult.StripResult.failFeed();
        }

        // 取出弹药（FIFO：取头部）
        LoadedRound round = belt.feedRound();
        if (round == null) return FeedResult.StripResult.empty();

        return FeedResult.StripResult.ok(round, belt);
    }

    private static FeedResult.StripResult stripStripperClip(
            com.tacz.guns.api.item.component.StripperClipData clip, float reliabilityModifier) {
        if (clip.isEmpty() || clip.isConsumed()) return FeedResult.StripResult.empty();

        // 桥夹压入可靠性高（一次性操作）
        float successChance = 0.95f * reliabilityModifier;
        if (RANDOM.nextFloat() > successChance) {
            return FeedResult.StripResult.failFeed();
        }

        // 取出弹药（从头部取出，按顺序压入枪内弹仓）
        com.tacz.guns.api.item.component.StripperClipData.StripResult stripResult = clip.stripAll();
        if (stripResult.rounds().isEmpty()) return FeedResult.StripResult.empty();

        // 桥夹一次性全部取出，返回第一发 + 更新后的桥夹
        return FeedResult.StripResult.ok(stripResult.rounds().get(0), stripResult.remainingClip());
    }

    private static FeedResult.StripResult stripEnBlocClip(
            com.tacz.guns.api.item.component.EnBlocClipData clip, float reliabilityModifier) {
        if (clip.isEmpty()) return FeedResult.StripResult.empty();

        // 漏夹供弹可靠性中等
        float successChance = 0.90f * reliabilityModifier;
        if (RANDOM.nextFloat() > successChance) {
            return FeedResult.StripResult.failFeed();
        }

        // 取出弹药
        com.tacz.guns.api.item.component.EnBlocClipData.FeedResult feedResult = clip.feedRound();
        if (feedResult.round() == null) return FeedResult.StripResult.empty();

        return FeedResult.StripResult.ok(feedResult.round(), feedResult.remainingClip());
    }

    private static FeedResult.StripResult stripDrumMagazine(
            com.tacz.guns.api.item.component.DrumMagazineData drum, float reliabilityModifier) {
        if (drum.isEmpty()) return FeedResult.StripResult.empty();

        // 弹鼓综合可靠性（弹簧+供弹口+发条）
        float successChance = drum.getOverallReliability() * reliabilityModifier;
        if (RANDOM.nextFloat() > successChance) {
            return FeedResult.StripResult.failFeed();
        }

        // 取出弹药（LIFO：取末尾）
        LoadedRound round = drum.feedRound();
        if (round == null) return FeedResult.StripResult.empty();

        // 弹簧疲劳度增加 + 发条张力消耗
        com.tacz.guns.api.item.component.DrumMagazineData updatedDrum =
                drum.withSpringFatigueAdded(0.001f).withWindingTensionConsumed(0.005f);

        return FeedResult.StripResult.ok(round, updatedDrum);
    }
}
