package me.xjqsh.lrtactical.client.renderer.item;

import com.mojang.serialization.MapCodec;
import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.api.LrTacticalAPI;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * 客户端物品模型条件属性 {@code lrtactical:has_custom_display}：
 * <b>当前内容包是否为这个物品堆提供了 Bedrock display</b>。
 *
 * <p>供 {@code assets/lrtactical/items/*.json} 用 {@code minecraft:condition} 分流：
 * <pre>
 * {
 *   "model": {
 *     "type": "minecraft:condition",
 *     "property": "lrtactical:has_custom_display",
 *     "on_true":  { "type": "lrtactical:dynamic_item", ... },   // 内容包模型 + 动画
 *     "on_false": { "type": "minecraft:model", "model": "lrtactical:item/melee" }   // 原版占位模型
 *   }
 * }
 * </pre>
 *
 * <h2>为什么需要它（这是本次移植的核心设计取舍）</h2>
 * 本移植<b>刻意不打包任何美术资源</b>（上游为 {@code Art Assets: All Rights Reserved}），
 * 所以必须同时满足两种互斥的场景：
 * <ul>
 *   <li><b>没装内容包</b>（绝大多数用户的默认状态）：应当沿用现在的原版物品模型，
 *       与移植当前的行为完全一致，<b>不能有任何退化</b>；</li>
 *   <li><b>装了内容包</b>：内容包自带的 geo/animation 要真正生效。</li>
 * </ul>
 *
 * <p>若不做这个分流，而是像 TACZ 那样把物品 JSON 直接写死成动态模型类型，
 * 那么没装内容包时 {@code getModel()} 返回 {@code null}，
 * 渲染器要么什么都不画（物品<b>隐形</b>）、要么画 missing texture（<b>紫黑块</b>）——
 * 两者都是明显的功能退化。TACZ 自己不存在这个问题，是因为它<b>内置了默认枪包</b>。
 *
 * <h2>为什么不能用原版内置的条件属性代替</h2>
 * 26.2 内置的 {@code condition} 属性共 12 个（字节码确认常量池：
 * {@code custom_model_data / using_item / broken / damaged / fishing_rod/cast /
 * has_component / bundle/has_selected_item / selected / carried / extended_view /
 * keybind_down / view_entity}）。其中唯一能读物品数据的是 {@code has_component}，
 * 它只能判断「某个组件存不存在」。
 *
 * <p>而这里要判断的是「<b>资源包</b>里有没有对应的 display 文件」——
 * 这个信息根本不在 ItemStack 上，而在客户端的 {@code LrClientAssetsManager} 缓存里，
 * 且随资源重载动态变化。原版属性无一能表达，因此必须自建。
 * （本仓库为完全同类的需求已经写过 {@code AmmoBoxStatueProperty}，
 * 那是 {@code select} 版；本类是 {@code condition} 版，注册入口不同，见下。）
 *
 * <h2>注册入口</h2>
 * {@code ConditionalItemModelProperties.ID_MAPPER}，必须在<b>客户端物品 JSON 解码之前</b>
 * 完成（与 {@code TaczDynamicItemModel.registerType()} 同一时机），
 * 否则解码时找不到该属性类型会直接报错。
 */
public record HasCustomDisplayProperty() implements ConditionalItemModelProperty {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "has_custom_display");

    public static final MapCodec<HasCustomDisplayProperty> MAP_CODEC =
            MapCodec.unit(new HasCustomDisplayProperty());

    @Override
    public boolean get(ItemStack stack,
                       @Nullable ClientLevel level,
                       @Nullable LivingEntity entity,
                       int seed,
                       ItemDisplayContext displayContext) {
        // 三类物品各查各的通道；都查不到就返回 false → 走原版占位模型
        return LrTacticalAPI.getMeleeDisplay(stack).isPresent()
                || LrTacticalAPI.getThrowableDisplay(stack).isPresent()
                || LrTacticalAPI.getConsumableDisplay(stack).isPresent();
    }

    @Override
    public MapCodec<? extends ConditionalItemModelProperty> type() {
        return MAP_CODEC;
    }
}
