package cn.sh1rocu.tacz.industry.api.heat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cn.sh1rocu.tacz.industry.api.internal.IndustryCodecs;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * 工件数据（A-2 半成品物品上承载的运行时状态，对应设计文档 `taczind:workpiece` 组件）。
 *
 * <p>承载在"热加工中的半成品"物品上：一块被加热的生铁坯、一根锻打到一半的枪管毛坯。
 * 工件物品同样必须不可堆叠（与 FeedDeviceData 同原则：栈内每个物品独立记录热与进度）。</p>
 *
 * @param heat        当前炉温单位（0–1000，{@link HeatUnits}）
 * @param processId   正在进行的工序（→ WorkProcessRegistry 查表；empty=未开始工序的热坯）
 * @param progress    工序完成度 0–1（锤击推进；1.0 时由规则层做收锤质量判定）
 * @param qualitySeed 收锤质量判定的随机种子（A-2 防刷设计：重进存档结果不变，
 *                    首次开工时由规则层从物品起源信息派生写入）
 * @param material    工件当前材料形态（→ MaterialRegistry；工序会改写它，如 生铁→熟铁）
 */
public record HeatWorkData(
        int heat,
        Optional<Identifier> processId,
        float progress,
        long qualitySeed,
        Identifier material
) {
    // Q-21 铁律：RecordCodecBuilder 一律两步式（先直接赋值锚定 O，再链 validate 等后续操作）。
    private static final MapCodec<HeatWorkData> SHAPE_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.INT.optionalFieldOf("heat", HeatUnits.AMBIENT).forGetter(HeatWorkData::heat),
                    IndustryCodecs.IDENTIFIER.optionalFieldOf("process_id").forGetter(HeatWorkData::processId),
                    Codec.FLOAT.optionalFieldOf("progress", 0f).forGetter(HeatWorkData::progress),
                    Codec.LONG.optionalFieldOf("quality_seed", 0L).forGetter(HeatWorkData::qualitySeed),
                    IndustryCodecs.IDENTIFIER.fieldOf("material").forGetter(HeatWorkData::material)
            ).apply(instance, HeatWorkData::new)
    );

    public static final Codec<HeatWorkData> CODEC = SHAPE_CODEC.codec();
    public static final MapCodec<HeatWorkData> MAP_CODEC = SHAPE_CODEC;

    /** 一块刚出炉（或刚入工序）、未选工序的工件。 */
    public static HeatWorkData of(Identifier material, int heat, long seed) {
        return new HeatWorkData(HeatUnits.clamp(heat), Optional.empty(), 0f, seed, material);
    }

    public HeatWorkData withHeat(int newHeat) {
        return new HeatWorkData(HeatUnits.clamp(newHeat), processId, progress, qualitySeed, material);
    }

    public HeatWorkData withProcess(Identifier process) {
        return new HeatWorkData(heat, Optional.of(process), progress, qualitySeed, material);
    }

    public HeatWorkData withProgress(float newProgress) {
        float p = newProgress < 0f ? 0f : Math.min(newProgress, 1f);
        return new HeatWorkData(heat, processId, p, qualitySeed, material);
    }

    public HeatWorkData withMaterial(Identifier newMaterial) {
        return new HeatWorkData(heat, processId, progress, qualitySeed, newMaterial);
    }

    public boolean isComplete() {
        return progress >= 1f;
    }
}
