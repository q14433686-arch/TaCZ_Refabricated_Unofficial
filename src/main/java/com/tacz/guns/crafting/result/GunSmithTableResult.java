package com.tacz.guns.crafting.result;

import cn.sh1rocu.tacz.util.forge.CraftingHelper;
import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GunSmithTableResult {
    private static final Identifier EMPTY_GROUP = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "empty");
    public static final String GUN = "gun";
    public static final String AMMO = "ammo";
    public static final String ATTACHMENT = "attachment";
    public static final String CUSTOM = "custom";

    private ItemStack result = ItemStack.EMPTY;
    private Identifier group = null;

    @Nullable
    private RawGunTableResult raw = null;
    @Nullable
    private JsonObject rawCustomItem = null;

    public GunSmithTableResult(ItemStack result, @Nullable Identifier group) {
        this.result = result;
        this.group = group == null ? EMPTY_GROUP : group;
    }

    public GunSmithTableResult(@NotNull JsonObject rawCustomItem, @Nullable Identifier group) {
        // CUSTOM result must be parsed lazily. During resource reload, 26.1.2 item components may not be
        // bound yet; constructing ItemStack immediately can throw "Components not bound yet".
        this.rawCustomItem = rawCustomItem;
        this.group = group == null ? EMPTY_GROUP : group;
    }

    public GunSmithTableResult(@NotNull RawGunTableResult raw) {
        this.raw = raw;
    }

    public GunSmithTableResult(@NotNull RawGunTableResult raw, @Nullable Identifier group) {
        this.raw = raw;
        this.group = group == null ? EMPTY_GROUP : group;
    }

    @Nullable
    private JsonObject extraComponents;
    @Nullable private JsonObject sourceJson;

    public void setSourceJson(JsonObject spec) { sourceJson = spec.deepCopy(); }

    public Identifier fallbackRecipeId() {
        if (sourceJson != null && sourceJson.has("id")) return Identifier.parse(sourceJson.get("id").getAsString());
        return Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "empty");
    }

    public com.google.gson.JsonElement encodeSpec() {
        if (sourceJson != null) return sourceJson.deepCopy();
        JsonObject obj = new JsonObject();
        obj.addProperty("type", CUSTOM);
        obj.add("item", CraftingHelper.encodeItemStack(result));
        if (group != null) obj.addProperty("group", group.toString());
        return obj;
    }

    public void setComponents(JsonObject components) { this.extraComponents = components.deepCopy(); }

    public void init() {
        if (raw != null) {
            GunSmithTableResult result = RawGunTableResult.init(raw);
            this.result = result.getResult();
            if (group == null || group.equals(EMPTY_GROUP)) {
                this.group = result.getGroup();
            }
            this.raw = null;
        }
        if (rawCustomItem != null) {
            // 第三方枪包可能写 26.x 风格 {"id": ...} 或字符串简写 —— 序列化器已尽量归一,
            // 但对意外形状绝不能炸:这里一旦抛出,开合成台界面时会把玩家以「网络协议错误」
            // 踢出(实际崩溃点 CraftingHelper.getItemStack 的 "Missing item"),JEI/REI 的
            // 收集循环也会被第一条坏配方整个打断(所有工作台配方一起消失)。
            // 失败降级为 EMPTY + 空页签组:等价于该条配方不显示,其余配方不受影响;
            // group 若不重置,坏配方会带着原页签(如 tacz:misc)混进页签过滤,
            // 在 JEI/REI/界面里显示成一个没有结果的空条目。
            try {
                this.result = CraftingHelper.getItemStack(rawCustomItem, true);
            } catch (RuntimeException e) {
                GunMod.LOGGER.error("Failed to parse custom gun smith table result item: {}", rawCustomItem, e);
                this.result = ItemStack.EMPTY;
                this.group = EMPTY_GROUP;
            }
            this.rawCustomItem = null;
        }
        if (extraComponents != null) {
            try {
                if (!result.isEmpty()) applyComponents(result, CraftingHelper.parseComponents(extraComponents));
            } catch (RuntimeException e) {
                GunMod.LOGGER.error("Invalid component patch on workbench result {}", extraComponents, e);
                result = ItemStack.EMPTY;
                group = EMPTY_GROUP;
            }
            extraComponents = null;
        }
    }

    private static void applyComponents(ItemStack stack, DataComponentPatch patch) {
        var original = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        stack.applyComponents(patch);
        // A patch that explicitly removes custom data must stay removed. Otherwise merge into the
        // builder-generated id/attachments, rather than replacing them with only a user-supplied flag.
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && !original.isEmpty()) stack.set(DataComponents.CUSTOM_DATA, CustomData.of(original.merge(data.copyTag())));
    }

    public ItemStack getResult() {
        return result;
    }

    public Identifier getGroup() {
        return group;
    }
}
