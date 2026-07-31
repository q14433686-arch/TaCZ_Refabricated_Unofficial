package cn.sh1rocu.tacz.industry.api.feed;

import cn.sh1rocu.tacz.industry.api.internal.IndustryCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * 供弹机构类型（N 章 N-1 的核心枚举）。
 *
 * <p>每个枚举值持有对应数据结构的 {@link MapCodec}，由 {@link FeedDeviceData#CODEC}
 * 按 "feed_system" 键做多态分派。新增供弹机构 = 新枚举值 + 新 device record + permits 登记。</p>
 */
public enum FeedSystemType {
    /** 可拆盒式弹匣（LIFO 栈 + 弹簧疲劳 + 供弹口损伤） */
    BOX_MAGAZINE("box_magazine", BoxMagazineData.CODEC),
    /** 管状弹仓（严格 FIFO 队列） */
    TUBULAR("tubular", TubularMagazineData.CODEC),
    /** 转轮弹巢（固定长度槽位数组 + 对齐格位索引） */
    CYLINDER("cylinder", CylinderData.CODEC),
    /** 弹链（FIFO + 链节类型 + 弹药箱对接标记） */
    BELT("belt", BeltData.CODEC),
    /** 桥夹（固定容量 + 一次性消耗） */
    STRIPPER_CLIP("stripper_clip", StripperClipData.CODEC),
    /** 漏夹（固定容量 + 强制整体弹出） */
    EN_BLOC("en_bloc", EnBlocClipData.CODEC);

    public static final Codec<FeedSystemType> CODEC = IndustryCodecs.enumByName(FeedSystemType.class, values(), FeedSystemType::getSerializedName);

    private final String serializedName;
    private final MapCodec<? extends FeedDeviceData> dataCodec;

    FeedSystemType(String serializedName, MapCodec<? extends FeedDeviceData> dataCodec) {
        this.serializedName = serializedName;
        this.dataCodec = dataCodec;
    }

    public String getSerializedName() {
        return serializedName;
    }

    public MapCodec<? extends FeedDeviceData> dataCodec() {
        return dataCodec;
    }
}
