package me.xjqsh.lrtactical.api.melee;

/**
 * 近战动作类型 —— 左键轻击 / 右键重击。
 *
 * <p>{@code id} 同时是数据包里的字段名（{@code attack_left} / {@code attack_right}），
 * 由 {@code CombatData.Deserializer} 按此名查找对应的攻击配置。
 */
public enum MeleeAction {
    LEFT("attack_left"),
    RIGHT("attack_right"),
    ;

    public final String id;

    MeleeAction(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
