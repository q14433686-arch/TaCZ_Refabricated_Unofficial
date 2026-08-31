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
     * 解析瞄具 / 枪模文字（display json 里的 {@code text_show}）：先查语言表，再替换
     * {@code %ammo_count%} 这类占位符。
     *
     * <h2>为什么必须是纯查表，不能是 {@code I18n.get}（2026-09-01 修，对齐 26.2 的 {@code ec51f556}）</h2>
     * {@code I18n.get(key, args...)} 是<b>格式化</b>接口：它先查表，再对查到的串跑
     * {@code String.format}，失败时返回 {@code "Format error: " + 原文}。而枪包的 {@code textKey}
     * 往往不是语言键而是<b>直接内联的显示串</b> —— MK5HD 用的就是 {@code "%ammo_count%"}：查表落空
     * 原样返回之后，{@code String.format} 会把 {@code %a...} 当格式说明符解析 ⇒
     * {@code IllegalFormatException} ⇒ 返回 {@code "Format error: %ammo_count%"}；随后占位符替换又在
     * 这串尾部拼上真实弹药数，于是镜内看到的是「一长串 Format error + 末尾一个数字」。语言文件里
     * 若真有含 {@code %} 的译文（例如 {@code "%s发"}）同样会炸。
     *
     * <p>{@code Language#getInstance().getOrDefault(key)} 是同一个查表入口但<b>不格式化</b>，
     * 键不存在时原样返回键 —— 也就是上游 1.20.1 的语义（上游原文写的就是
     * {@code I18n.language.getOrDefault(textKey)}）。成员存在性由本轮 CI 的 javap 探针核实
     * （TEMP 块在 {@code build.gradle}，输出进 {@code build-reports/compile-java.log}）。</p>
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
