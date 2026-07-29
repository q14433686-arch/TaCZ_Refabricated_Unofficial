package me.xjqsh.lrtactical.client.resource.display;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * display JSON 里的<b>简写路径</b> → 实际资源路径。
 *
 * <p>内容包写 {@code "texture": "lrtactical:melee/dagger_uv"}，
 * 实际文件在 {@code assets/lrtactical/textures/melee/dagger_uv.png}。
 * 这个「补 {@code textures/} 前缀与 {@code .png} 后缀」的转换在上游是
 * 每个 DisplayInstance 各自内联一遍字符串拼接，这里收成一处。
 *
 * <p>转换规则与 TACZ 的 {@code IDisplay#converter}
 * （{@code new FileToIdConverter("textures", ".png")}）完全等价 ——
 * 之所以不直接用那个 converter，是因为它是 {@code IDisplay} 接口上的字段，
 * 而 LRTactical 的 display 并未实现该接口（它们的加载通道不同）。
 */
public final class DisplayPaths {
    private DisplayPaths() {
    }

    /**
     * @param raw display JSON 中的简写贴图 id，可为 {@code null}
     * @return {@code <ns>:textures/<path>.png}；入参为 {@code null} 时返回 {@code null}
     */
    @Nullable
    @Contract("null -> null; !null -> !null")
    public static Identifier toTexturePath(@Nullable Identifier raw) {
        if (raw == null) {
            return null;
        }
        return Identifier.fromNamespaceAndPath(raw.getNamespace(), "textures/" + raw.getPath() + ".png");
    }
}
