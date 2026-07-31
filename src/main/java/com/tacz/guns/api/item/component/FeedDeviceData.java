package com.tacz.guns.api.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.api.item.enums.FeedSystemType;
import com.tacz.guns.api.item.cartridge.CartridgeTypeManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 供弹具数据密封接口。
 * <p>
 * 针对已有的 {@link FeedSystemType} 枚举中的每一种供弹机制，
 * 实现对应的独立数据结构。不能用单一通用结构覆盖所有类型，
 * 因为它们的物理数据形状本质不同：
 * <ul>
 *   <li>{@link BoxMagazineData} — 盒式弹匣（有序列表+LIFO+弹簧疲劳度+供弹口损伤度）</li>
 *   <li>{@link TubularMagazineData} — 管状弹仓（严格FIFO队列）</li>
 *   <li>{@link CylinderData} — 转轮弹巢（固定长度数组，每格独立状态+对齐索引）</li>
 *   <li>{@link BeltData} — 弹链（FIFO+链节类型+可对接标记）</li>
 *   <li>{@link StripperClipData} — 桥夹（固定容量+一次性消耗标记）</li>
 *   <li>{@link EnBlocClipData} — 漏夹（固定容量+强制整体弹出标记）</li>
 *   <li>{@link DrumMagazineData} — 弹鼓（大容量LIFO+弹簧疲劳+发条张力）</li>
 * </ul>
 * <p>
 * 对应设计文档：N.1 供弹具机构差异化系统 & L.3 弹药存储
 */
public sealed interface FeedDeviceData
        permits BoxMagazineData, TubularMagazineData, CylinderData,
        BeltData, StripperClipData, EnBlocClipData, DrumMagazineData {

    // ====== 通用接口 ======

    /** 获取供弹机制类型 */
    FeedSystemType getFeedSystemType();

    /** 获取最大容量 */
    int getCapacity();

    /** 获取当前弹药数量 */
    int getCurrentRoundCount();

    /** 是否为空 */
    default boolean isEmpty() {
        return getCurrentRoundCount() == 0;
    }

    /** 是否已满 */
    default boolean isFull() {
        return getCurrentRoundCount() >= getCapacity();
    }

    /** 获取此供弹具的口径规格（从第一发弹药或指定口径获取） */
    @Nullable Identifier getCartridgeType();

    // ====== 分派 Codec ======

    /**
     * 类型键 → Codec 映射。
     * 使用 FeedSystemType 的序列化名称作为分派键。
     */
    Map<String, Codec<? extends FeedDeviceData>> TYPE_CODECS = Map.of(
            "detachable_magazine", BoxMagazineData.CODEC,
            "tubular_magazine", TubularMagazineData.CODEC,
            "cylinder", CylinderData.CODEC,
            "belt", BeltData.CODEC,
            "stripper_clip", StripperClipData.CODEC,
            "en_bloc", EnBlocClipData.CODEC,
            "drum", DrumMagazineData.CODEC
    );

    /**
     * 序列化名称 → FeedSystemType 映射。
     */
    Map<String, FeedSystemType> TYPE_KEY_MAP = Map.of(
            "detachable_magazine", FeedSystemType.DETACHABLE_MAGAZINE,
            "tubular_magazine", FeedSystemType.TUBULAR_MAGAZINE,
            "cylinder", FeedSystemType.CYLINDER,
            "belt", FeedSystemType.BELT,
            "stripper_clip", FeedSystemType.STRIPPER_CLIP,
            "en_bloc", FeedSystemType.EN_BLOC,
            "drum", FeedSystemType.DRUM
    );

    /**
     * FeedSystemType → 序列化名称映射。
     */
    Map<FeedSystemType, String> TYPE_SERIALIZED_NAME_MAP = Map.of(
            FeedSystemType.DETACHABLE_MAGAZINE, "detachable_magazine",
            FeedSystemType.TUBULAR_MAGAZINE, "tubular_magazine",
            FeedSystemType.CYLINDER, "cylinder",
            FeedSystemType.BELT, "belt",
            FeedSystemType.STRIPPER_CLIP, "stripper_clip",
            FeedSystemType.EN_BLOC, "en_bloc",
            FeedSystemType.DRUM, "drum"
    );

    /**
     * 分派 Codec：根据 "feed_system_type" 字段选择对应的子类型 Codec 进行序列化/反序列化。
     */
    Codec<FeedDeviceData> CODEC = Codec.STRING.dispatch(
            "feed_system_type",
            data -> TYPE_SERIALIZED_NAME_MAP.getOrDefault(data.getFeedSystemType(), "detachable_magazine"),
            key -> TYPE_CODECS.getOrDefault(key, BoxMagazineData.CODEC)
    );

    /**
     * 分派 StreamCodec：先写入类型字节，再写入对应子类型数据。
     */
    StreamCodec<ByteBuf, FeedDeviceData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FeedDeviceData decode(ByteBuf buf) {
            int typeOrdinal = buf.readInt();
            FeedSystemType type = FeedSystemType.BY_ID.apply(typeOrdinal);
            return switch (type) {
                case DETACHABLE_MAGAZINE -> BoxMagazineData.STREAM_CODEC.decode(buf);
                case TUBULAR_MAGAZINE -> TubularMagazineData.STREAM_CODEC.decode(buf);
                case CYLINDER -> CylinderData.STREAM_CODEC.decode(buf);
                case BELT -> BeltData.STREAM_CODEC.decode(buf);
                case STRIPPER_CLIP -> StripperClipData.STREAM_CODEC.decode(buf);
                case EN_BLOC -> EnBlocClipData.STREAM_CODEC.decode(buf);
                case DRUM -> DrumMagazineData.STREAM_CODEC.decode(buf);
            };
        }

        @Override
        public void encode(ByteBuf buf, FeedDeviceData data) {
            buf.writeInt(data.getFeedSystemType().ordinal());
            switch (data) {
                case BoxMagazineData d -> BoxMagazineData.STREAM_CODEC.encode(buf, d);
                case TubularMagazineData d -> TubularMagazineData.STREAM_CODEC.encode(buf, d);
                case CylinderData d -> CylinderData.STREAM_CODEC.encode(buf, d);
                case BeltData d -> BeltData.STREAM_CODEC.encode(buf, d);
                case StripperClipData d -> StripperClipData.STREAM_CODEC.encode(buf, d);
                case EnBlocClipData d -> EnBlocClipData.STREAM_CODEC.encode(buf, d);
                case DrumMagazineData d -> DrumMagazineData.STREAM_CODEC.encode(buf, d);
            }
        }
    };

    // ====== 兼容性检查 ======

    /**
     * 检查此供弹具是否与指定口径兼容。
     * <p>
     * 如果供弹具为空，则接受任何口径（首次装填时确定口径）。
     * 如果供弹具已有弹药，则新弹药的口径必须与已有弹药口径兼容。
     */
    default boolean isCartridgeCompatible(Identifier cartridgeType) {
        Identifier myCartridge = getCartridgeType();
        if (myCartridge == null) return true;  // 空供弹具接受任何口径
        return CartridgeTypeManager.isCompatible(myCartridge, cartridgeType);
    }
}
