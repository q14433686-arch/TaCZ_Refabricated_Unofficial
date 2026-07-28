package me.xjqsh.lrtactical.item.melee;

import com.google.gson.JsonElement;

/**
 * 「近战武器类型」—— 决定如何解析该武器的 {@code data} 段。
 *
 * <p>与投掷物的 {@code ThrowableType} 对称，但<b>没有实体工厂</b>：
 * 近战不生成实体，攻击是即时判定的。
 *
 * <p>类型注册在 {@code ModRegistries}（Fabric 上是普通 Map，理由见该类注释）。
 */
public record MeleeWeaponType<T extends MeleeWeaponData>(MeleeWeaponType.MeleeDataSerializer<T> serializer) {

    @FunctionalInterface
    public interface MeleeDataSerializer<T extends MeleeWeaponData> {
        T parse(JsonElement json);
    }
}
