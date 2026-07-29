package me.xjqsh.lrtactical.api.melee;

/**
 * 单次攻击对单个目标的结果。
 *
 * <p>用于让调用方区分「打空」与「打中」——
 * 例如只有真正命中才扣耐久、才播命中音效。
 */
public enum AttackResult {
    /** 未命中（目标不可攻击、被免疫、或伤害被完全挡下）。 */
    MISS,
    /** 命中。 */
    HIT,
    ;

    public boolean isHit() {
        return this == HIT;
    }
}
