package cn.sh1rocu.tacz.industry.api.gun;

import cn.sh1rocu.tacz.industry.api.ammo.LoadedRound;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * 枪械运行时状态数据（挂载在每把枪物品上的 DataComponent）。
 *
 * <p><b>本次改动（任务要求 5）：</b>枪膛是否上膛的表示，从简单布尔升级
 * 为 {@code Optional<LoadedRound> chamberedRound}——膛内状态可追溯到"具体这
 * 一发"的完整个体数据，为后续系统提供判定基础：</p>
 * <ul>
 *   <li>瞎火/哑弹：按该发底火类型/装药偏差掷骰，而不是全局概率</li>
 *   <li>Squib 续射炸膛：枪膛实体存在 + {@code barrelObstruction} 标记共同进 F 章判定</li>
 *   <li>腐蚀倒计时：该发底火腐蚀性决定射击后何时必须做溶剂清洁</li>
 * </ul>
 *
 * <p><b>职责边界：</b>本组件只存"与单发弹药/枪膛相关的运行时状态"；
 * 磨损轨在 PartsData（P3），温度在 ThermalData（P4）。供弹具数据在供弹具物品上，
 * 不在枪上——枪内固定仓类机构（管仓/内仓/漏夹）通过独立的"枪内 feed 槽"
 * 机制引用一份 FeedDeviceData 副本（P2/N-1 落地时定义，Q-12）。</p>
 *
 * <p><b>与 TACZ 原生字段的关系：</b>TACZ 的 {@code HasBulletInBarrel}(boolean) 保留为
 * 显示/动画镜像；写入规范是"任何 chamberedRound 变更必须同步镜像布尔"——
 * 权威状态以本组件为准（写入规范见实现记录 impl-log/P0）。</p>
 *
 * @param chamberedRound    膛内弹药个体数据；empty = 空膛（原布尔 false 的唯一等价语义）
 * @param barrelObstruction 枪管异物标记（Squib 留膛/泥沙/水；与 chamberedRound 独立——
 *                          枪管是枪管，弹膛是弹膛，真实事故链路需要分开记录）
 * @param obstructionKnown  异物是否已被"检查枪管"动作揭示（隐藏惩罚设计，E-3.5）
 */
public record GunStateData(
        Optional<LoadedRound> chamberedRound,
        boolean barrelObstruction,
        boolean obstructionKnown
) {
    public static final MapCodec<GunStateData> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    LoadedRound.CODEC.optionalFieldOf("chambered_round").forGetter(GunStateData::chamberedRound),
                    Codec.BOOL.optionalFieldOf("barrel_obstruction", false).forGetter(GunStateData::barrelObstruction),
                    Codec.BOOL.optionalFieldOf("obstruction_known", false).forGetter(GunStateData::obstructionKnown)
            ).apply(instance, GunStateData::new)
    );

    public static final GunStateData EMPTY = new GunStateData(Optional.empty(), false, false);

    /**
     * 语义守卫：这一方法名就是"原布尔字段"的唯一合法读法。
     */
    public boolean hasChamberedRound() {
        return chamberedRound.isPresent();
    }

    public GunStateData withChamberedRound(LoadedRound round) {
        return new GunStateData(Optional.of(round), barrelObstruction, obstructionKnown);
    }

    public GunStateData clearChamber() {
        return new GunStateData(Optional.empty(), barrelObstruction, obstructionKnown);
    }

    public GunStateData withObstruction(boolean known) {
        return new GunStateData(chamberedRound, true, known);
    }

    public GunStateData clearObstruction() {
        return new GunStateData(chamberedRound, false, false);
    }

    public GunStateData revealObstruction() {
        return obstructionKnown ? this : new GunStateData(chamberedRound, barrelObstruction, true);
    }
}
