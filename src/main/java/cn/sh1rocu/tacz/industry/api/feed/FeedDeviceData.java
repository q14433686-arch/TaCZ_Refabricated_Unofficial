package cn.sh1rocu.tacz.industry.api.feed;

import cn.sh1rocu.tacz.industry.api.ammo.LoadedRound;
import cn.sh1rocu.tacz.industry.api.feed.device.BeltData;
import cn.sh1rocu.tacz.industry.api.feed.device.BoxMagazineData;
import cn.sh1rocu.tacz.industry.api.feed.device.CylinderData;
import cn.sh1rocu.tacz.industry.api.feed.device.EnBlocClipData;
import cn.sh1rocu.tacz.industry.api.feed.device.StripperClipData;
import cn.sh1rocu.tacz.industry.api.feed.device.TubularMagazineData;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * 供弹具数据（N 章 N-1）：弹匣/桥夹/漏夹/弹巢/弹链等物品上承载的
 * "内部装填状态 + 机构磨损"的密封多态数据。
 *
 * <p><b>为什么必须是 sealed 而不是通用结构：</b>六种机构的物理数据形状本质不同——
 * 弹匣是 LIFO 栈加两条磨损轨，转轮是定长槽位数组加对齐索引，弹链是可对接队列……
 * 任何"万能结构"都必然让 80% 字段对某一机构无意义。密封接口 + 每机构独立 record
 * 是唯一能同时保证存档安全（多态 Codec 分派）与语义清晰的做法。</p>
 *
 * <p><b>物品规则（对应任务要求 4）：</b>承载本组件的物品必须 maxStackSize=1——
 * 每个物品实体独立记录装填状态与磨损，堆叠必然导致状态串扰。
 * 注册物品时由 {@code FeedItemRules#requireUnstackable} 断言兜底。</p>
 *
 * <p><b>不可变约定：</b>所有实现均为 record，读改写（ItemStack 组件的标准模式）：
 * 读组件 → 以方法返回的新实例写回。不允许就地修改。</p>
 */
public sealed interface FeedDeviceData
        permits BoxMagazineData, TubularMagazineData, CylinderData, BeltData, StripperClipData, EnBlocClipData {

    /**
     * 多态解码入口。存档键 "feed_system" 决定具体结构。
     */
    Codec<FeedDeviceData> CODEC = FeedSystemType.CODEC.dispatch(
            "feed_system",
            FeedDeviceData::feedSystem,
            FeedSystemType::dataCodec
    );

    /**
     * 机构类型。
     */
    FeedSystemType feedSystem();

    /**
     * 该供弹具的口径规格（制造时固化；供弹具与弹药的第一层物理兼容）。
     */
    Identifier cartridge();

    /**
     * 容弹上限（转轮=槽位数，管仓=管长档，弹链=链节数）。
     */
    int capacity();

    /**
     * 当前容弹数。注意部分机构（转轮）"有壳未抛"不等于"有弹可发"，
     * 该值只统计可击发弹药，具体格位语义见各实现。
     */
    int loadedCount();

    default boolean isEmpty() {
        return loadedCount() <= 0;
    }

    /**
     * 按该机构的物理供弹顺序，查看下一发将入膛的弹药（不移除）。
     */
    Optional<LoadedRound> peekNext();

    /**
     * 执行一次供弹动作：按机构规则把"下一发"从供弹队列中移除并返回新数据。
     * 不同机构的差异（转轮留空壳前进格位、漏夹打空自动弹出等）在实现内完成。
     * 空供弹具调用应返回 this（调用方先用 isEmpty 守卫）。
     */
    FeedDeviceData ejectNext();

    /**
     * 尝试装入一发弹药。统一校验（口径匹配、容量、弹壳可装状态）由实现内的
     * 嵌套规则完成；失败返回 Optional.empty()，不改变原数据。
     */
    Optional<FeedDeviceData> tryLoad(LoadedRound round);

    /**
     * 统一嵌套校验：口径匹配 + 弹壳状态可装。供所有实现的 tryLoad 复用，
     * 避免六份拷贝的规则漂移。
     */
    default boolean rejectsLoad(LoadedRound round) {
        return !round.cartridge().equals(cartridge()) || !round.caseState().isLoadable();
    }
}
