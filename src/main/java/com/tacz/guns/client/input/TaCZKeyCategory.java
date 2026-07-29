package com.tacz.guns.client.input;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Shared KeyMapping category for all TACZ key bindings.
 *
 * <h2>为什么必须显式指定 {@code tacz} 命名空间</h2>
 * 26.2 的分类标题是<b>从 Identifier 推导</b>出来的，不再是自己写死的字符串：
 * <pre>
 * KeyMapping.Category#label():
 *     return Component.translatable(this.id.toLanguageKey("key.category"));
 * Identifier#toLanguageKey(String prefix):
 *     return prefix + "." + namespace + "." + path;
 * </pre>
 * （两处均为字节码确认。）
 *
 * <p>原先写的是 {@code Identifier.parse("tacz")} —— 不带冒号时会套用<b>默认命名空间
 * {@code minecraft}</b>，于是推出来的键是 {@code key.category.minecraft.tacz}，
 * 而语言文件里根本没有这个键，界面上就直接显示出原始键名
 * （用户实测：按键绑定里标题是一串 {@code key.category.mincraft.tacz}）。
 *
 * <p>改为 {@code tacz:tacz} 后推导出 {@code key.category.tacz.tacz}，
 * 并已在全部 21 个语言文件里补上该键。
 * 对照 vanilla：{@code Category.register("movement")} 内部走
 * {@code Identifier.withDefaultNamespace}，得到的正是
 * {@code key.category.minecraft.movement} —— 同一套规则。
 *
 * <p>旧键 {@code key.category.tacz} 予以保留：Controllable 兼容层
 * （{@code ControllableInner}）仍在用它做手柄按键分类，那是另一套 API。
 */
public final class TaCZKeyCategory {
    public static final KeyMapping.Category TACZ =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("tacz", "tacz"));

    private TaCZKeyCategory() {
    }
}
