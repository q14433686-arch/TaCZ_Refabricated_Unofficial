package cn.sh1rocu.tacz.mixin.common;

import cn.sh1rocu.tacz.util.forge.CraftingHelper;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 【第 42 轮：确认为永久废弃 —— 功能已被 26.2 原版取代】
 *
 * <p>本 mixin 的目的是让合成配方的 {@code result} 支持 {@code nbt} 字段
 * （1.21.1 原版只认 {@code item} + {@code count}，写 NBT 需要 Forge 的
 * {@code CraftingHelper}，这是从 Forge 侧移植过来的补丁）。</p>
 *
 * <h2>为什么不能注册</h2>
 * 注入点 {@code ShapedRecipe#itemStackFromJson} 在 26.2 <b>不存在</b>。
 * 配方体系已全面 codec 化，不再有任何 JSON 手工解析入口
 * （{@code ShapedRecipe} 现在只有 {@code MAP_CODEC} / {@code STREAM_CODEC}
 * 与 {@code SERIALIZER}）。
 *
 * <h2>为什么也不需要重写</h2>
 * 26.2 的配方结果类型已从裸 {@code ItemStack} 换成
 * {@link net.minecraft.world.item.ItemStackTemplate}，而它是个 record，
 * 字段为：
 * <pre>
 *   item       : Holder&lt;Item&gt;
 *   count      : int
 *   components : DataComponentPatch   ← 原生支持
 * </pre>
 * 也就是说「在配方结果里附带自定义数据」这件事，
 * <b>原版 codec 已经原生支持</b>，用 {@code components} 字段即可，
 * 不再需要 {@code nbt} 字段与本 mixin。
 *
 * <p>注意：数据包里旧的 {@code "nbt": ...} 写法在 26.2 不再被识别，
 * 需要迁移成 {@code "components": {...}}。这属于枪包/数据包的迁移问题，
 * 与本 mixin 无关（TACZ 自己的枪械配方走的是
 * {@code GunSmithTableResultSerializer}，不经过原版 ShapedRecipe）。</p>
 *
 * <p>保留源码仅作历史参考。</p>
 */
@Mixin(ShapedRecipe.class)
public class ShapedRecipeMixin {
    @Inject(method = "itemStackFromJson", at = @At("HEAD"), cancellable = true)
    private static void tacz$$itemStackFromJson(JsonObject json, CallbackInfoReturnable<ItemStack> cir) {
        if (json.has("nbt")) {
            cir.setReturnValue(CraftingHelper.getItemStack(json, true, true));
        }
    }
}
