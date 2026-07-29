package me.xjqsh.lrtactical.client.audio;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 能按<b>名字</b>提供音效 id 的 display 数据。
 *
 * <p>内容包在 {@code display/*.json} 的 {@code sounds} 段里写
 * {@code {"crit": "minecraft:entity.player.attack.crit"}} 这样的映射，
 * 代码侧则用固定的键名（如 {@code "crit"}）取用，从而做到<b>音效可被内容包替换</b>。
 *
 * <p>26.2 变更：{@code ResourceLocation} → {@link Identifier}，其余不变。
 */
public interface ICustomSoundSupplier {
    Map<String, Identifier> getSounds();

    /**
     * @return 内容包未定义该音效名时返回 {@code null}（调用方应视为「不播」，而非崩溃）
     */
    @Nullable
    default Identifier getSound(String key) {
        return getSounds().get(key);
    }
}
