package com.tacz.guns.client.model.papi;

import com.google.common.collect.Maps;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.locale.Language;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.function.Function;

@Environment(EnvType.CLIENT)
public final class PapiManager {
    private static final Map<String, Function<ItemStack, String>> PAPI = Maps.newHashMap();

    // 注册，不知道放哪里，先放这
    static {
        addPapi(PlayerNamePapi.NAME, new PlayerNamePapi());
        addPapi(AmmoCountPapi.NAME, new AmmoCountPapi());
    }

    public static void addPapi(String textKey, Function<ItemStack, String> function) {
        textKey = "%" + textKey + "%";
        PAPI.put(textKey, function);
    }

    /**
     * 解析瞄具/枪模文字（display json 的 {@code text_show}）：
     * 先查语言表，再替换 {@code %ammo_count%} 等占位符。
     *
     * <h3>为什么必须用 {@code Language.getOrDefault} 而不是 {@code I18n.get}（2026-08-30 修复）</h3>
     * 上游 1.20.1 原文就是 {@code I18n.language.getOrDefault(textKey)} ——
     * 纯查表，键不存在时原样返回键本身。移植 26.2 时该字段没了，曾误换成
     * {@code I18n.get(textKey)}，但那是【格式化】接口（字节码实读）：
     * <pre>
     * String s = Language.getInstance().getOrDefault(key);
     * try { return String.format(Locale.ROOT, s, args); }
     * catch (IllegalFormatException e) { return "Format error: " + s; }
     * </pre>
     * 枪包的 textKey 常常不是语言键而是【直接内联的显示串】，例如 MK5HD 的
     * {@code "%ammo_count%"}：查表落空原样返回后，{@code String.format} 把
     * {@code %a...} 当格式说明符解析 → {@code IllegalFormatException} →
     * 返回 <b>"Format error: %ammo_count%"</b>。于是镜内文字先冒出一长串
     * "Format error: ..."，占位符替换后才在末尾剩下真正的弹药数
     * （用户实测，MK5HD + 光影下裁剪修复后暴露）。
     * 语言文件里若真有含 % 的译文（如 "%s发"）同样会炸。
     * 这里回到上游语义：只查表，不格式化。
     */
    public static String getTextShow(String textKey, ItemStack stack) {
        String text = Language.getInstance().getOrDefault(textKey);
        for (var entry : PAPI.entrySet()) {
            String placeholder = entry.getKey();
            String data = entry.getValue().apply(stack);
            text = text.replace(placeholder, data);
        }
        return text;
    }
}
