package me.xjqsh.lrtactical.client.resource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.tacz.guns.GunMod;
import me.xjqsh.lrtactical.client.resource.display.MeleeDisplayInstance;
import me.xjqsh.lrtactical.client.resource.display.ThrowableDisplayInstance;
import me.xjqsh.lrtactical.client.resource.manager.MeleeDisplayManager;
import me.xjqsh.lrtactical.client.resource.manager.ThrowableDisplayManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * LRTactical 的<b>客户端</b>资源管理器：所有 display 数据缓存在此。
 *
 * <p>结构对齐 TACZ 的 {@code ClientAssetsManager}，但只管 LRTactical 自己的
 * {@code display/melee} 与 {@code display/throwable} 两类。
 * 模型（{@code geo_models}）、动画（{@code animations}）、Lua 脚本（{@code scripts}）
 * <b>刻意不重复加载</b> —— 直接复用 TACZ 的管理器，理由见下。
 *
 * <h2>为什么复用 TACZ 的模型/动画/脚本加载器</h2>
 * {@code MeleeDisplayInstance#create} 里调的是
 * {@code ClientAssetsManager.INSTANCE.getBedrockModelPOJO/getBedrockAnimations/getScript}。
 * 这三者在 TACZ 侧是<b>全命名空间扫描</b>的 —— {@code LazyJsonDataManager} 的
 * eager 谓词（{@code id -> "tacz".equals(id.getNamespace())}）只决定「是否在重载时
 * 立刻解析」，<b>不</b>限制「是否收录」（见其 {@code prepare}：非 eager 的一律
 * {@code preparedEntries.put(id, PreparedEntry.lazy(...))}）。
 * 因此 {@code lrtactical:} 命名空间下的 geo/animation/lua 会被一并收录、按需懒加载。
 * 再建一套只会重复占内存，还会与 TACZ 的缓存不一致。
 *
 * <h2>顺序依赖：必须排在 TACZ 之后（本移植的关键约束）</h2>
 * 上游用 NeoForge 的 {@code @SubscribeEvent(priority = EventPriority.LOW)} 保证
 * 「LRTactical 的 display 在 TACZ 的资源之后加载」，因为 {@code create()} 时
 * <b>同步地</b>要去 TACZ 那里取模型和动画 —— 顺序反了就会满屏
 * 「no corresponding model found」。
 *
 * <p>Fabric 没有 EventPriority，但 {@code IdentifiableResourceReloadListener} 提供了
 * <b>更精确</b>的机制：{@code getFabricDependencies()} 声明「我必须在这些 listener 之后跑」
 * （已核对 26.2 分支源码，该默认方法仍在，返回 {@code Collection<Identifier>}；
 * 接口本身已标 {@code @Deprecated}，官方推荐改用
 * {@code ResourceLoader#addListenerOrdering}，但<b>本仓库 TACZ 侧全线仍用这套</b>，
 * 混用两套排序机制反而无法互相约束，故保持一致）。
 *
 * <p><b>注意</b>：Fabric 的依赖声明是「软」的 —— 依赖项不存在时不报错，只是不产生约束。
 * 因此 id 拼错不会崩，而是<b>静默退化成不保证顺序</b>，表现为偶发的加载失败。
 * 故这里不手写字符串字面量，而是在 {@link #taczListenerId} 里复刻 TACZ
 * {@code JsonDataManager} 的 id 生成规则。
 */
@Environment(EnvType.CLIENT)
public enum LrClientAssetsManager {
    INSTANCE;

    /**
     * display 专用 Gson。
     *
     * <p>与上游的差异：<b>不</b>注册 {@code ItemTransforms} / {@code ItemTransform} 适配器。
     * 26.2 上这两个 {@code Deserializer} 的构造器已降为包级私有（字节码确认），
     * 外部包无法实例化；transforms 改由 {@code BlockTransformParser} 在
     * {@code create()} 阶段解析，POJO 里存原始 {@code JsonObject}。
     * 详见 {@link MeleeDisplayInstance} 的类注释。
     */
    public static final Gson GSON = new GsonBuilder()
            .setStrictness(com.google.gson.Strictness.LENIENT)
            .registerTypeAdapter(Identifier.class,
                    (com.google.gson.JsonDeserializer<Identifier>) (json, type, ctx) ->
                            Identifier.tryParse(json.getAsString()))
            .registerTypeAdapter(Identifier.class,
                    (com.google.gson.JsonSerializer<Identifier>) (src, type, ctx) ->
                            new JsonPrimitive(src.toString()))
            .create();

    /**
     * TACZ 侧三个 listener 的 marker 名，取自 {@code ClientAssetsManager#reloadAndRegister}：
     * <pre>
     * bedrockModel     = new LazyJsonDataManager&lt;&gt;(..., "BedrockModelLoader", ...)
     * bedrockAnimation = new LazyJsonDataManager&lt;&gt;(..., "BedrockAnimationLoader", ...)
     * scriptManager    = new ScriptManager(...)
     * </pre>
     * 前两者的 Fabric id = {@code tacz:<marker 全小写>}；
     * {@code ScriptManager} <b>不走这条规则</b>，它自建了
     * {@code public static final Identifier ID = ...("script_manager")}，见 {@link #TACZ_SCRIPT_ID}。
     */
    private static final String[] TACZ_ASSET_MARKERS = {
            "BedrockModelLoader",
            "BedrockAnimationLoader"
    };

    /**
     * {@code ScriptManager} 的 id。
     *
     * <p>直接引用其 {@code public static final ID} 常量，而不是重新拼字符串 ——
     * 这样 TACZ 侧一旦改名，这里会<b>编译期</b>暴露，而不是运行时静默失序。
     */
    private static final Identifier TACZ_SCRIPT_ID =
            com.tacz.guns.resource.manager.ScriptManager.ID;

    @Nullable
    private ThrowableDisplayManager throwableDisplay;
    @Nullable
    private MeleeDisplayManager meleeDisplay;

    /**
     * 建立并注册两个 display listener。
     *
     * <p>调用点在 {@code TaCZFabricClient}，必须与 TACZ 自己的
     * {@code ClientAssetsManager.INSTANCE.reloadAndRegister} 用<b>同一个</b>
     * {@code ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)} ——
     * 跨 helper 的依赖声明不产生约束。
     *
     * <p>manager 只建一次（与 TACZ 的 {@code listeners == null} 守卫同理）：
     * 重复 new 会让已经缓存的 display 全部丢失，而资源重载本身会调用
     * {@code apply} 重新填充，无需换实例。
     */
    public void reloadAndRegister(Consumer<IdentifiableResourceReloadListener> register) {
        if (throwableDisplay == null) {
            throwableDisplay = new ThrowableDisplayManager(GSON);
            meleeDisplay = new MeleeDisplayManager(GSON);
        }
        register.accept(throwableDisplay);
        register.accept(meleeDisplay);
    }

    @Nullable
    public ThrowableDisplayInstance getThrowableDisplay(Identifier id) {
        if (throwableDisplay == null) {
            return null;
        }
        ThrowableDisplayInstance exact = throwableDisplay.getData(id);
        return exact != null ? exact : findUniqueThrowableDisplayByPath(id);
    }

    @Nullable
    public MeleeDisplayInstance getMeleeDisplay(Identifier id) {
        if (meleeDisplay == null) {
            return null;
        }
        MeleeDisplayInstance exact = meleeDisplay.getData(id);
        return exact != null ? exact : findUniqueMeleeDisplayByPath(id);
    }

    /**
     * 有些组合枪包的服务端 index id 与客户端 display id 只在命名空间上不同
     * （例如 {@code data/<pack_ns>/index/throwable/foo.json} 对应
     * {@code assets/<other_ns>/display/throwable/foo.json}）。精确 id 查不到时，
     * 只在 path 全局唯一的情况下回退，避免多个包都叫 m67/dagger 时串资源。
     */
    @Nullable
    private ThrowableDisplayInstance findUniqueThrowableDisplayByPath(Identifier id) {
        if (throwableDisplay == null) {
            return null;
        }
        ThrowableDisplayInstance match = null;
        for (var entry : throwableDisplay.getAllData().entrySet()) {
            if (!entry.getKey().getPath().equals(id.getPath())) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = entry.getValue();
        }
        return match;
    }

    /** 近战 display 的 path-only 唯一匹配回退，规则同投掷物。 */
    @Nullable
    private MeleeDisplayInstance findUniqueMeleeDisplayByPath(Identifier id) {
        if (meleeDisplay == null) {
            return null;
        }
        MeleeDisplayInstance match = null;
        for (var entry : meleeDisplay.getAllData().entrySet()) {
            if (!entry.getKey().getPath().equals(id.getPath())) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = entry.getValue();
        }
        return match;
    }

    /**
     * 「必须排在这些 listener 之后」的完整清单，供两个 display manager 的
     * {@code getFabricDependencies()} 直接返回。
     *
     * <p>集中在此定义，避免两个 manager 各写一份、日后只改其中一份而另一份静默失序。
     */
    public static Collection<Identifier> taczAssetDependencies() {
        List<Identifier> deps = new ArrayList<>(TACZ_ASSET_MARKERS.length + 1);
        for (String marker : TACZ_ASSET_MARKERS) {
            deps.add(taczListenerId(marker));
        }
        deps.add(TACZ_SCRIPT_ID);
        return deps;
    }

    /**
     * 复刻 {@code JsonDataManager} / {@code LazyJsonDataManager} 的 id 生成规则：
     * {@code Identifier.fromNamespaceAndPath(GunMod.MOD_ID, marker.toLowerCase(Locale.ROOT))}。
     *
     * @see com.tacz.guns.resource.manager.JsonDataManager
     */
    private static Identifier taczListenerId(String marker) {
        return Identifier.fromNamespaceAndPath(GunMod.MOD_ID, marker.toLowerCase(Locale.ROOT));
    }
}
