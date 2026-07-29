#!/usr/bin/env python3
"""
第 14 轮补丁：修复「除高爆弹外所有工作台配方不可见」+「材料数量文字不显示」

根因 1（配方全灭）：
  GunSmithTableIngredientSerializer 在 Gson 反序列化<b>当场</b>调用 Ingredient.CODEC.parse。
  26.2 的 item tag 要等 MinecraftServer#reloadResources 里
  updateComponentsAndStaticRegistryTags() -> PendingTags::apply 才绑定，
  而我们的配方加载器 CommonDataManager 是 reload listener，跑在那之前。
  于是任何含 "#c:xxx" 的材料都拿到 "Missing tag" -> 抛 JsonParseException
  -> JsonDataManager#apply catch 住只打日志 -> 整条配方被静默丢弃。
  172 个默认配方中仅 attachments/ammo_mod_he.json 不含 #tag，故只有高爆弹幸存。
  已用运行时实验证实：#c:ingots/copper -> FAIL: Missing tag。
  修法：材料改为「延迟解析」，首次取用时才解析（那时 tag 已绑定）。

根因 2（数量文字不显示）：
  renderIngredient 里 color 用了 6 位 0xFFFFFF / 0xFF0000，alpha=0。
  26.2 GuiGraphicsExtractor#text 开头 if (ARGB.alpha(color) != 0) 静默丢弃。
  修法：补 0xFF 前缀。

用法：python3 patch_r14.py [repo_root]
"""
import sys, os, io

ROOT = sys.argv[1] if len(sys.argv) > 1 else "/home/user/repo"


def rd(p):
    with io.open(os.path.join(ROOT, p), encoding="utf-8") as f:
        return f.read()


def wr(p, s):
    with io.open(os.path.join(ROOT, p), "w", encoding="utf-8") as f:
        f.write(s)


def sub(p, old, new, desc):
    s = rd(p)
    if new in s and old not in s:
        print("  [skip] %s (already applied)" % desc)
        return
    assert old in s, "PATTERN NOT FOUND in %s: %s" % (p, desc)
    wr(p, s.replace(old, new, 1))
    print("  [ok]   %s" % desc)


# ---------------------------------------------------------------- 1. 延迟解析载体
INGREDIENT = r'''package com.tacz.guns.crafting;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.GunMod;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

/**
 * 工作台配方的一项材料。
 *
 * <h2>第 14 轮：为什么这里要「延迟解析」</h2>
 *
 * 原先 {@code GunSmithTableIngredientSerializer} 在 Gson 反序列化的当场就调用
 * {@code Ingredient.CODEC.parse(...)}。这在 26.2 上是<b>错误时机</b>，原因经反编译逐级确认：
 *
 * <ol>
 *   <li>{@code Ingredient.CODEC = ExtraCodecs.nonEmptyHolderSet(NON_AIR_HOLDER_SET_CODEC)}，
 *       而 {@code NON_AIR_HOLDER_SET_CODEC = HolderSetCodec.create(Registries.ITEM, ...)}；</li>
 *   <li>{@code HolderSetCodec#decode} 碰到 {@code "#c:ingots/copper"} 这种 tag 写法时，
 *       会走 {@code lookupTag(registry, tag)}；该方法在 tag 尚未绑定时直接返回
 *       {@code DataResult.error("Missing tag: ...")}；</li>
 *   <li>{@code MappedRegistry#get(TagKey)} 读的是 {@code allTags}，而 {@code allTags}
 *       要等 {@code Registry.PendingTags#apply()} 之后才有内容；</li>
 *   <li>{@code ReloadableServerResources#loadResources} 把 {@code postponedTags} 存起来，
 *       真正的 {@code updateComponentsAndStaticRegistryTags()}
 *       （内部 {@code postponedTags.forEach(PendingTags::apply)}）
 *       是在 {@code MinecraftServer#reloadResources} 的 {@code thenAcceptAsync} 里执行的，
 *       <b>晚于所有 reload listener 跑完</b>。</li>
 * </ol>
 *
 * 我们的 {@code CommonDataManager}（配方加载器）正是一个 reload listener，
 * 所以它 {@code apply()} 的时候 item tag 一律查不到 → 每个含 {@code #tag} 的材料都抛异常 →
 * {@code JsonDataManager#apply} 把 {@code JsonParseException} catch 住只打一行 error →
 * <b>整条配方被静默丢弃</b>。
 *
 * <p>实测 172 个默认配方里只有 {@code attachments/ammo_mod_he.json}（高爆弹）不含 {@code #tag}，
 * 于是「有且仅有高爆弹能被查到」——与用户观察完全吻合。运行时实验亦已证实：
 * {@code "#c:ingots/copper"} → {@code FAIL: Missing tag: 'c:ingots/copper' in 'minecraft:item'}，
 * 而 {@code "minecraft:crying_obsidian"} → OK。
 *
 * <p>对照上游 1.21.1：上游同样在反序列化当场解析，但上游配方走 vanilla {@code RecipeManager} 通道，
 * 其 ops 来自 {@code ReloadableServerResources} 的
 * {@code loadingContext = fullRegistries.lookupWithUpdatedTags()}——
 * <b>那是一份已经带上新 tag 的 lookup</b>，所以上游不触发本问题。
 * 我们在第 12 轮把配方改走自己的 {@code DataType.RECIPES} 同步通道后就失去了这份 lookup，
 * 必须自己把解析推迟到 tag 绑定之后。
 */
public class GunSmithTableIngredient {
    private final int count;

    @Nullable
    private Ingredient ingredient;
    /** 尚未解析的 {@code "item"} 字段原文；解析成功后置空。 */
    @Nullable
    private JsonElement rawItem;
    /** 只记录「是否已打过日志」，不缓存失败结果——避免过早的一次调用把材料永久毒化。 */
    private boolean loggedFailure;

    public GunSmithTableIngredient(Ingredient ingredient, int count) {
        this.ingredient = ingredient;
        this.count = count;
    }

    /** 延迟解析用的构造器，见类注释。 */
    public GunSmithTableIngredient(JsonElement rawItem, int count) {
        this.rawItem = rawItem;
        this.count = count;
    }

    /**
     * @return 解析后的 {@link Ingredient}；若原始 JSON 至今仍无法解析（例如 tag 真的不存在）则返回 {@code null}。
     *         调用方必须判空——这正是「配方整条消失」与「材料格空着但配方还在」的区别。
     */
    @Nullable
    public Ingredient getIngredient() {
        if (this.ingredient == null && this.rawItem != null) {
            JsonElement raw = this.rawItem;
            try {
                this.ingredient = Ingredient.CODEC.parse(
                        RegistryOps.create(JsonOps.INSTANCE,
                                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)),
                        raw
                ).getOrThrow();
                this.rawItem = null;
            } catch (RuntimeException e) {
                if (!this.loggedFailure) {
                    this.loggedFailure = true;
                    GunMod.LOGGER.error("Failed to resolve gun smith table ingredient {}", raw, e);
                }
            }
        }
        return this.ingredient;
    }

    /** 供必须拿到非空值的场合（如网络编码）使用。 */
    public Ingredient getIngredientOrThrow() {
        Ingredient resolved = this.getIngredient();
        if (resolved == null) {
            throw new IllegalStateException("Unresolved gun smith table ingredient: " + this.rawItem);
        }
        return resolved;
    }

    /** 材料是否可用（tag 已绑定且解析成功）。 */
    public boolean isResolved() {
        return this.getIngredient() != null;
    }

    public int getCount() {
        return count;
    }
}
'''

SERIALIZER = r'''package com.tacz.guns.resource.serialize;

import com.google.gson.*;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import net.minecraft.util.GsonHelper;

import java.lang.reflect.Type;

/**
 * 第 14 轮：不再在此处调用 {@code Ingredient.CODEC.parse}。
 *
 * <p>本序列化器由 {@code CommonDataManager}（一个 server reload listener）驱动，
 * 而 26.2 的 item tag 要等 {@code MinecraftServer#reloadResources} 里的
 * {@code updateComponentsAndStaticRegistryTags()} 才绑定，<b>晚于所有 reload listener</b>。
 * 在这里解析 {@code "#c:ingots/copper"} 必然拿到 {@code Missing tag}，
 * 异常再被 {@code JsonDataManager#apply} 吞掉，导致整条配方静默消失。
 * 详见 {@link GunSmithTableIngredient} 的类注释。
 *
 * <p>因此这里只做结构校验 + 原样保存 JSON，真正解析推迟到首次取用时。
 */
public class GunSmithTableIngredientSerializer implements JsonDeserializer<GunSmithTableIngredient> {
    @Override
    public GunSmithTableIngredient deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            if (!jsonObject.has("item")) {
                throw new JsonSyntaxException("Expected " + jsonObject + " must has a item member");
            }
            int count = 1;
            if (jsonObject.has("count")) {
                count = Math.max(GsonHelper.getAsInt(jsonObject, "count"), 1);
            }
            // 延迟解析：此刻 tag 尚未绑定，存原文即可。
            return new GunSmithTableIngredient(jsonObject.get("item"), count);
        } else {
            throw new JsonSyntaxException("Expected " + json + " to be a Pair because it's not an object");
        }
    }
}
'''

print("[1/6] 重写 GunSmithTableIngredient / GunSmithTableIngredientSerializer")
wr("src/main/java/com/tacz/guns/crafting/GunSmithTableIngredient.java", INGREDIENT)
print("  [ok]   GunSmithTableIngredient (延迟解析)")
wr("src/main/java/com/tacz/guns/resource/serialize/GunSmithTableIngredientSerializer.java", SERIALIZER)
print("  [ok]   GunSmithTableIngredientSerializer (只存原文)")

# ---------------------------------------------------------------- 2. GUI 渲染判空
print("[2/6] GunSmithTableScreen#renderIngredient 判空")
SCREEN = "src/main/java/com/tacz/guns/client/gui/GunSmithTableScreen.java"
sub(SCREEN,
    """                GunSmithTableIngredient smithTableIngredient = inputs.get(index);
                Ingredient ingredient = smithTableIngredient.getIngredient();

                ItemStack[] items = ingredient.display().resolveForStacks(SlotDisplayContext.fromLevel(Minecraft.getInstance().level)).toArray(ItemStack[]::new);""",
    """                GunSmithTableIngredient smithTableIngredient = inputs.get(index);
                // 第 14 轮：材料改为延迟解析，可能尚未（或无法）解析出来，必须判空。
                Ingredient ingredient = smithTableIngredient.getIngredient();

                ItemStack[] items = ingredient == null
                        ? new ItemStack[0]
                        : ingredient.display().resolveForStacks(SlotDisplayContext.fromLevel(Minecraft.getInstance().level)).toArray(ItemStack[]::new);""",
    "renderIngredient 判空")

# ---------------------------------------------------------------- 3. alpha 修复
print("[3/6] 材料数量文字 alpha 修复")
sub(SCREEN,
    """                    int color = count <= hasCount ? 0xFFFFFF : 0xFF0000;""",
    """                    // 第 14 轮修复：这两个色值原本是 6 位（0xFFFFFF / 0xFF0000），alpha 分量为 0。
                    // 26.2 的 GuiGraphicsExtractor#text 开头就是 if (ARGB.alpha(color) != 0)，
                    // alpha=0 的文字会被<b>静默丢弃</b>（1.21.x 的 drawString 会自动补不透明，26.2 不会）。
                    // 结果：材料数量 "x/y" 在两种情况下都画不出来 —— 这正是用户看到的
                    // 「仅在持有所需物品时才显示个数」（那时走的是上面创造模式 0xFFFFFFFF 分支）。
                    int color = count <= hasCount ? 0xFFFFFFFF : 0xFFFF0000;""",
    "材料数量 alpha 补 0xFF")

# ---------------------------------------------------------------- 4. 背包计数判空
print("[4/6] getPlayerIngredientCount 判空")
sub(SCREEN,
    """            GunSmithTableIngredient ingredient = ingredients.get(i);
            Inventory inventory = player.getInventory();
            int count = 0;
            for (ItemStack stack : inventory.getNonEquipmentItems()) {
                if (!stack.isEmpty() && ingredient.getIngredient().test(stack)) {
                    count = count + stack.getCount();
                }
            }""",
    """            GunSmithTableIngredient ingredient = ingredients.get(i);
            Inventory inventory = player.getInventory();
            int count = 0;
            // 第 14 轮：延迟解析后可能为 null，此时按「一个都没有」处理。
            Ingredient resolved = ingredient.getIngredient();
            if (resolved != null) {
                for (ItemStack stack : inventory.getNonEquipmentItems()) {
                    if (!stack.isEmpty() && resolved.test(stack)) {
                        count = count + stack.getCount();
                    }
                }
            }""",
    "getPlayerIngredientCount 判空")

# ---------------------------------------------------------------- 5. 服务端合成 fail-closed
print("[5/6] GunSmithTableMenu#doCraft fail-closed")
sub("src/main/java/com/tacz/guns/inventory/GunSmithTableMenu.java",
    """                for (GunSmithTableIngredient ingredient : ingredients) {
                    int count = 0;
                    for (int slotIndex = 0; slotIndex < handler.getSlots(); slotIndex++) {
                        ItemStack stack = handler.getStackInSlot(slotIndex);
                        int stackCount = stack.getCount();
                        if (!stack.isEmpty() && ingredient.getIngredient().test(stack)) {""",
    """                for (GunSmithTableIngredient ingredient : ingredients) {
                    int count = 0;
                    // 第 14 轮：材料延迟解析。若解析不出来（tag 缺失等），
                    // 必须<b>拒绝合成</b>而不是跳过该材料 —— 否则玩家能白嫖成品。
                    net.minecraft.world.item.crafting.Ingredient resolved = ingredient.getIngredient();
                    if (resolved == null) {
                        return;
                    }
                    for (int slotIndex = 0; slotIndex < handler.getSlots(); slotIndex++) {
                        ItemStack stack = handler.getStackInSlot(slotIndex);
                        int stackCount = stack.getCount();
                        if (!stack.isEmpty() && resolved.test(stack)) {""",
    "doCraft 无法解析则拒绝合成")

# ---------------------------------------------------------------- 6. JEI / REI / 网络
print("[6/6] JEI / REI / 网络编码")
sub("src/main/java/com/tacz/guns/compat/jei/category/GunSmithTableCategory.java",
    """            GunSmithTableIngredient ingredient = inputs.get(index);
            return ingredient.getIngredient().display()
                    .resolveForStacks(SlotDisplayContext.fromLevel(Minecraft.getInstance().level))""",
    """            GunSmithTableIngredient ingredient = inputs.get(index);
            // 第 14 轮：材料延迟解析，可能尚未解析成功。
            net.minecraft.world.item.crafting.Ingredient resolved = ingredient.getIngredient();
            if (resolved == null) {
                return Collections.singletonList(ItemStack.EMPTY);
            }
            return resolved.display()
                    .resolveForStacks(SlotDisplayContext.fromLevel(Minecraft.getInstance().level))""",
    "JEI getInput 判空")

sub("src/main/java/cn/sh1rocu/tacz/compat/rei/category/GunSmithTableCategory.java",
    """            GunSmithTableIngredient ingredient = inputs.get(index);
            return ingredient.getIngredient().display()
                    .resolveForStacks(EntryIngredients.slotDisplayContext())""",
    """            GunSmithTableIngredient ingredient = inputs.get(index);
            // 第 14 轮：材料延迟解析，可能尚未解析成功。
            net.minecraft.world.item.crafting.Ingredient resolved = ingredient.getIngredient();
            if (resolved == null) {
                return Collections.singletonList(EntryStack.of(VanillaEntryTypes.ITEM, ItemStack.EMPTY));
            }
            return resolved.display()
                    .resolveForStacks(EntryIngredients.slotDisplayContext())""",
    "REI getInput 判空")

SER = "src/main/java/com/tacz/guns/crafting/GunSmithTableSerializer.java"
sub(SER,
    "Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient.getIngredient());",
    "Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient.getIngredientOrThrow());",
    "STREAM_CODEC encode 用 OrThrow")
sub(SER,
    'Ingredient.CODEC.fieldOf("item").forGetter(GunSmithTableIngredient::getIngredient),',
    'Ingredient.CODEC.fieldOf("item").forGetter(GunSmithTableIngredient::getIngredientOrThrow),',
    "MapCodec getter 用 OrThrow")

print("\n第 14 轮补丁应用完毕。")
