package com.tacz.guns.crafting.result;

import cn.sh1rocu.tacz.util.forge.CraftingHelper;
import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;
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
        // CUSTOM result must be parsed lazily. During resource reload, 26.2 item components may not be
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
    }

    public ItemStack getResult() {
        return result;
    }

    public Identifier getGroup() {
        return group;
    }
}
