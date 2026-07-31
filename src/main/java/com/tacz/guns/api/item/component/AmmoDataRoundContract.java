package com.tacz.guns.api.item.component;

import com.tacz.guns.api.item.enums.BulletType;
import com.tacz.guns.api.item.enums.CaseCondition;
import com.tacz.guns.api.item.enums.CaseMaterial;
import com.tacz.guns.api.item.enums.PowderType;
import com.tacz.guns.api.item.enums.PrimerType;
import net.minecraft.resources.Identifier;

/**
 * AmmoData ↔ LoadedRound 同步契约。
 * <p>
 * 此类定义了 {@link AmmoData}（堆叠级模板）和 {@link LoadedRound}（单发级实例）
 * 之间的字段同步规则，确保两者不会因字段增删改而出现不一致。
 * <p>
 * <b>铁律：AmmoData 是唯一的"事实来源"(Source of Truth)。</b>
 * <ul>
 *   <li>任何字段的增删改，先改 AmmoData，再同步 LoadedRound</li>
 *   <li>LoadedRound 永远是从 AmmoData 实例化出来的快照</li>
 *   <li>LoadedRound 独有的字段（cartridgeType, powderChargeDeviation）是单发级数据，
 *       不需要同步回 AmmoData</li>
 * </ul>
 * <p>
 * <b>往返一致性契约</b>：
 * <pre>
 * // 对于 AmmoData 中与 LoadedRound 重叠的 7 个字段，以下断言必须成立：
 * LoadedRound round = LoadedRound.fromAmmoData(cartridgeType, ammoData);
 * AmmoData roundTripped = round.toAmmoData();
 * assert roundTripped.caseMaterial() == ammoData.caseMaterial();
 * assert roundTripped.primerType() == ammoData.primerType();
 * assert roundTripped.powderType() == ammoData.powderType();
 * assert Float.compare(roundTripped.powderCharge(), ammoData.powderCharge()) == 0;
 * assert roundTripped.bulletType() == ammoData.bulletType();
 * assert roundTripped.reloadCount() == ammoData.reloadCount();
 * assert roundTripped.caseCondition() == ammoData.caseCondition();
 * </pre>
 * <p>
 * <b>实现者注意事项</b>：
 * <ol>
 *   <li>在 AmmoData 中新增字段时，必须同步在 LoadedRound 中添加相同字段</li>
 *   <li>在 AmmoData 中新增字段时，必须更新 {@link LoadedRound#fromAmmoData} 和
 *       {@link LoadedRound#toAmmoData()} 方法</li>
 *   <li>在 AmmoData 中新增字段时，必须更新此契约中的断言列表</li>
 *   <li>如果新字段是"单发级"数据（如每发子弹都不同的随机值），则仅加在 LoadedRound 中，
 *       不加在 AmmoData 中</li>
 * </ol>
 * <p>
 * 此类不包含运行时逻辑，仅作为文档和编译期检查的契约声明。
 */
public final class AmmoDataRoundContract {

    /**
     * 重叠字段数量：AmmoData 和 LoadedRound 共享的字段数。
     * <p>
     * 当前值：7（caseMaterial, primerType, powderType, powderCharge, bulletType, reloadCount, caseCondition）
     * <p>
     * 如果此值与实际不一致，说明某次字段增删改时漏改了一边。
     */
    public static final int OVERLAP_FIELD_COUNT = 7;

    /**
     * AmmoData 独有字段：cartridgeType（口径类型）。
     * <p>
     * 注意：AmmoData 的 cartridgeType 是堆叠级数据，而 LoadedRound 的 cartridgeType
     * 是单发级数据。在 fromAmmoData() 转换时，AmmoData 的 cartridgeType 作为参数传入。
     * 在 toAmmoData() 反向转换时，cartridgeType 被保留。
     */
    public static final String AMMO_DATA_ONLY_FIELD = "cartridgeType (also in LoadedRound, but populated differently)";

    /**
     * LoadedRound 独有字段：powderChargeDeviation（装药偏差）。
     * <p>
     * 这是单发级数据，每发子弹的装药偏差可能不同，因此不属于 AmmoData。
     */
    public static final String LOADED_ROUND_ONLY_FIELD = "powderChargeDeviation";

    private AmmoDataRoundContract() {}

    /**
     * 验证往返一致性契约。
     * <p>
     * 此方法可用于单元测试或断言，确保 AmmoData → LoadedRound → AmmoData 的
     * 往返转换不会丢失任何重叠字段的数据。
     *
     * @param ammoData 原始 AmmoData
     * @param cartridgeType 用于转换的口径类型
     * @return 如果往返一致性成立则返回 true
     * @throws AssertionError 如果任何重叠字段不一致
     */
    public static boolean verifyRoundTrip(AmmoData ammoData, Identifier cartridgeType) {
        LoadedRound round = LoadedRound.fromAmmoData(cartridgeType, ammoData);
        AmmoData roundTripped = round.toAmmoData();

        // 注意：AmmoData 的 cartridgeType 在 toAmmoData() 中会被保留
        // 但 fromAmmoData() 的 cartridgeType 参数可能与原始 AmmoData 的不同
        // 因此 cartridgeType 不参与往返一致性检查

        assert roundTripped.caseMaterial() == ammoData.caseMaterial()
                : "caseMaterial mismatch after round-trip";
        assert roundTripped.primerType() == ammoData.primerType()
                : "primerType mismatch after round-trip";
        assert roundTripped.powderType() == ammoData.powderType()
                : "powderType mismatch after round-trip";
        assert Float.compare(roundTripped.powderCharge(), ammoData.powderCharge()) == 0
                : "powderCharge mismatch after round-trip";
        assert roundTripped.bulletType() == ammoData.bulletType()
                : "bulletType mismatch after round-trip";
        assert roundTripped.reloadCount() == ammoData.reloadCount()
                : "reloadCount mismatch after round-trip";
        assert roundTripped.caseCondition() == ammoData.caseCondition()
                : "caseCondition mismatch after round-trip";

        return true;
    }
}
