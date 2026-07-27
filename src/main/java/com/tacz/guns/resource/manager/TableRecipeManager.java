package com.tacz.guns.resource.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import com.tacz.guns.init.ModRecipe;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import com.tacz.guns.util.ResourceScanner;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 枪械工作台配方加载器。
 *
 * <h2>为什么需要这个子类，而不是直接用 {@code CommonDataManager}</h2>
 * 工作台配方和原版配方<b>共用同一个目录</b>：{@code data/<ns>/recipe/**.json}。
 * 这是上游的既定布局（上游靠原版 {@code RecipeManager} 按 {@code "type"} 分发，
 * 天然只会拿到自己那一份），我们不能改目录，否则所有现存枪包都会失效。
 *
 * <p>但 26.2 客户端已经<b>没有</b>完整配方表（{@code ClientLevel#recipeAccess}
 * 只剩 {@code propertySet} 与 {@code stonecutterRecipes}），所以工作台配方改由
 * mod 自己走 {@code DataType.RECIPES} 通道同步。这条自建通道没有原版的类型分发，
 * 于是 {@code FileToIdConverter.json("recipe")} 会把<b>所有</b>命名空间下的
 * 所有配方一网打尽 —— 实测：原版 1585 条 + 本模组 20 条原版格式配方
 * （{@code crafting_shaped}/{@code crafting_shapeless}），全都被喂给了
 * {@code TableRecipe} 的 Gson 解析器。
 *
 * <p>它们没有 {@code result.type} 字段，于是每一条都在
 * {@code GunSmithTableResultSerializer} 第 24 行
 * （{@code GsonHelper.getAsString(jsonObject, "type")}）抛
 * {@code JsonSyntaxException}，刷满整个日志。更糟的是这堆无关 JSON
 * 还会被<b>原样序列化进网络包</b>发给每个进服的客户端，客户端再解析一遍、再刷一遍日志
 * （崩溃日志里那串 {@code CommonNetworkCache.parse} 调用栈就是这么来的）。
 *
 * <h2>做法</h2>
 * 在解析<b>之前</b>按顶层 {@code "type"} 过滤，只留下
 * {@code tacz:gun_smith_table_crafting}。过滤后的结果同时决定 {@code dataMap} 与
 * {@code networkCache}（父类的 {@code apply} 用同一份入参构造两者），
 * 因此日志噪声和网络包体积一起解决。
 *
 * <p>类型 id 不写字面量，而是从注册表反查 {@link ModRecipe#GUN_SMITH_TABLE_CRAFTING}，
 * 这样将来若改注册名，这里不会悄悄失配。
 */
public class TableRecipeManager extends CommonDataManager<TableRecipe> {
    /**
     * 工作台配方的 {@code "type"} 值。取自注册表而非硬编码字符串
     * —— 见类注释末段。回退值仅用于注册表异常时的兜底，正常路径走不到。
     */
    private static final String RECIPE_TYPE_ID = resolveRecipeTypeId();

    /**
     * 旧版枪包（1.20 及以前）使用的<b>复数</b>配方目录。
     *
     * <p>26.2 把原版数据包目录统一改成了单数 {@code recipe/}，但<b>工作台配方并不走
     * 原版数据包加载器</b> —— 它走的是本 mod 自建的 {@link DataType#RECIPES} 通道
     * （见类注释）。既然解析与同步全由我们自己负责，就<b>没有</b>必须单数的约束，
     * 完全可以同时接纳两种历史布局。</p>
     *
     * <p>这正是「旧枪包装上后，枪械/配件都在，唯独扩展物品在工作台和 JEI/REI 里
     * 一个都搜不到」的根因：那些包的配方躺在 {@code data/<ns>/recipes/} 下，
     * 而我们只扫了 {@code recipe/}，于是<b>整包配方被静默忽略</b>
     * （没有任何报错，因为对加载器而言那就是一批不存在的文件）。</p>
     */
    private static final String LEGACY_RECIPE_DIRECTORY = "recipes";

    /** 旧目录的扫描器。与父类那个 {@code recipe} 扫描器<b>并列</b>使用，不是替代。 */
    private static final FileToIdConverter LEGACY_CONVERTER = FileToIdConverter.json(LEGACY_RECIPE_DIRECTORY);

    public TableRecipeManager() {
        // 目录与原版数据包配方一致（data/<ns>/recipe），这是上游的既定布局，不能改。
        // 旧枪包的 data/<ns>/recipes（复数）由下面的 prepare() 覆写额外扫描。
        super(DataType.RECIPES, TableRecipe.class, CommonAssetsManager.GSON, "recipe", "TableRecipeLoader");
    }

    /**
     * 同时扫描 {@code recipe/}（当前布局）与 {@code recipes/}（旧枪包布局）。
     *
     * <h2>为什么在 prepare 阶段合并，而不是加载后再补</h2>
     * {@code prepare} 是唯一能决定「有哪些候选文件」的地方；
     * 后续的 {@link #apply} 只能在既有集合上过滤。旧目录若不在这里加进来，
     * 之后任何环节都无从补救。
     *
     * <h2>冲突处理</h2>
     * 两个目录的文件会映射到<b>相同格式</b>的 Identifier（命名空间 + 相对路径），
     * 因此同一个包若两处都放了同名配方，会发生键冲突。
     * 这里让<b>新目录优先</b>：先放旧的，再用新的覆盖。
     * 理由是若枪包作者已经做了 26.2 适配（写进 {@code recipe/}），
     * 那份显然比遗留的旧文件更可信。
     */
    @NotNull
    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        Map<Identifier, JsonElement> legacy =
                ResourceScanner.scanDirectory(pResourceManager, LEGACY_CONVERTER, CommonAssetsManager.GSON);
        Map<Identifier, JsonElement> current = super.prepare(pResourceManager, pProfiler);
        if (legacy.isEmpty()) {
            return current;
        }
        // 新目录优先覆盖同名项，见方法注释。
        Map<Identifier, JsonElement> merged = new LinkedHashMap<>(legacy);
        merged.putAll(current);
        GunMod.LOGGER.info(getMarker(),
                "Found {} recipe file(s) in legacy 'recipes/' directory (old gun pack layout), {} in 'recipe/'.",
                legacy.size(), current.size());
        return merged;
    }

    private static String resolveRecipeTypeId() {
        Identifier id = BuiltInRegistries.RECIPE_TYPE.getKey(ModRecipe.GUN_SMITH_TABLE_CRAFTING);
        return id != null ? id.toString() : GunMod.MOD_ID + ":gun_smith_table_crafting";
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        // LinkedHashMap 保序，便于复现问题时对照日志顺序。
        Map<Identifier, JsonElement> ours = new LinkedHashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : pObject.entrySet()) {
            if (isGunSmithTableRecipe(entry.getValue())) {
                ours.put(entry.getKey(), entry.getValue());
            }
        }
        GunMod.LOGGER.debug(getMarker(), "Gun smith table recipes: {} accepted, {} foreign recipe files skipped",
                ours.size(), pObject.size() - ours.size());
        // 父类会用这一份（且仅这一份）同时构建 dataMap 与 networkCache。
        super.apply(ours, pResourceManager, pProfiler);
    }

    private static boolean isGunSmithTableRecipe(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return false;
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement type = object.get("type");
        // 只认字符串型的顶层 type；非字符串（数组/对象）一律当外来文件跳过，
        // 不抛异常 —— 这条路径上每帧都可能遇到别的模组的私有格式。
        if (type == null || !type.isJsonPrimitive() || !type.getAsJsonPrimitive().isString()) {
            return false;
        }
        return RECIPE_TYPE_ID.equals(type.getAsString());
    }
}
