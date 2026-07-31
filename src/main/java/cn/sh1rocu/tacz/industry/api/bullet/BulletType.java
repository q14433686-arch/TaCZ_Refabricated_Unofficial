package cn.sh1rocu.tacz.industry.api.bullet;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

/**
 * 弹头类型（Bullet Type / Projectile Type）。
 *
 * <p><b>职责边界：</b>只描述弹头的"终点弹道效果"——伤害系数、穿甲、穿透等级、
 * 曳光、亚音速、扩张伤害。不描述物理口径兼容（那是 {@code CartridgeType}）。</p>
 *
 * <p>数据驱动：data/&lt;ns&gt;/bullet/&lt;name&gt;.json，由 {@code BulletRegistry} 持有。</p>
 *
 * @param id                   注册名（如 taczind:fmj）
 * @param massClass            质量档（C 章缠距匹配输入）
 * @param damageMultiplier     伤害系数（乘在枪械基础伤害上）
 * @param armorIgnoreBonus     护甲穿透加成（叠进 TACZ extra_damage.armor_ignore）
 * @param penClassBonus        方块穿透等级加减（C-4.1 表，-2..+1 合理区间）
 * @param tracerInterval       曳光间隔：0=非曳光；n&gt;0 每 n 发一曳（映射 TACZ tracer_count_interval）
 * @param subsonic             亚音速标记（K 章隐蔽判定、无音爆签名）
 * @param expansionDamageBonus 空尖扩张伤害（仅对无甲目标，命中结算层消费）
 */
public record BulletType(
        Identifier id,
        BulletMassClass massClass,
        float damageMultiplier,
        float armorIgnoreBonus,
        int penClassBonus,
        int tracerInterval,
        boolean subsonic,
        float expansionDamageBonus
) {
    public static BulletType fromJson(Identifier id, JsonObject json) {
        BulletMassClass mass = json.has("mass_class")
                ? BulletMassClass.valueOf(GsonHelper.getAsString(json, "mass_class").toUpperCase())
                : BulletMassClass.STD;
        float dmg = GsonHelper.getAsFloat(json, "damage_multiplier", 1.0f);
        float armorIgnore = GsonHelper.getAsFloat(json, "armor_ignore_bonus", 0.0f);
        int penBonus = GsonHelper.getAsInt(json, "pen_class_bonus", 0);
        int tracer = GsonHelper.getAsInt(json, "tracer_interval", 0);
        boolean subsonic = GsonHelper.getAsBoolean(json, "subsonic", false);
        float expansion = GsonHelper.getAsFloat(json, "expansion_damage_bonus", 0.0f);
        return new BulletType(id, mass, dmg, armorIgnore, penBonus, tracer, subsonic, expansion);
    }

    /**
     * 标准 FMJ 兜底弹头（旧数据未声明 bullet_type 时的默认引用目标 id）。
     */
    public static Identifier defaultId() {
        return Identifier.fromNamespaceAndPath("taczind", "fmj");
    }
}
