package me.xjqsh.lrtactical.client.resource.manager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.manager.JsonDataManager;
import me.xjqsh.lrtactical.client.resource.display.MeleeDisplayInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Collection;
import java.util.Map;

/**
 * 加载 {@code assets/<ns>/display/melee/*.json}。
 *
 * <p>沿用 TACZ 的 {@link JsonDataManager}（它在 26.2 已实现 Fabric 的
 * {@code IdentifiableResourceReloadListener}，可直接注册进资源重载流程）。
 *
 * <h2>为什么不用 {@code DisplayManager} 而是直接继承 {@code JsonDataManager}</h2>
 * TACZ 的 {@code DisplayManager<T extends IDisplay>} 要求数据类实现 {@code IDisplay}
 * 并有无参可反序列化的形态；而 LRTactical 的 display 是「先反序列化成 POJO record，
 * 再经 {@code create()} 校验并解析出模型/动画/状态机」的两段式，
 * 二者形状不同。上游也是直接继承 {@code JsonDataManager} 并覆写 {@code apply}，此处照搬。
 *
 * <p>{@code dataClass} 传 {@code null} 是刻意的：本类不走基类的
 * {@code parseJson(element) -> gson.fromJson(element, dataClass)} 路径，
 * 而是在 {@code apply} 里显式指定 POJO 类型。
 *
 * <h2>单个文件解析失败不影响其它文件</h2>
 * {@code create()} 用 {@code Preconditions} 抛 {@code IllegalArgumentException}
 * 来表达「这个 display 缺字段/引用了不存在的模型」。这里逐个 catch 并记日志 ——
 * 一个内容包写错一把刀，不该让整个资源重载失败。
 */
public class MeleeDisplayManager extends JsonDataManager<MeleeDisplayInstance> {
    public MeleeDisplayManager(Gson pGson) {
        super(null, pGson, "display/melee", "LrMeleeDisplay");
    }

    /**
     * 声明「必须在 TACZ 的模型/动画/脚本加载完之后再跑」。
     *
     * <p><b>这是必需的、不是优化</b>：{@code MeleeDisplayInstance#create} 会<b>同步</b>去
     * {@code ClientAssetsManager} 取 geo 模型、bedrock 动画与 Lua 脚本。
     * 顺序反了就会全数取到 {@code null}，表现为「no corresponding model found」——
     * 而且因为资源重载是并行调度的，这种失败是<b>偶发</b>的，极难复现和排查。
     *
     * @see me.xjqsh.lrtactical.client.resource.LrClientAssetsManager#taczAssetDependencies()
     */
    @Override
    public Collection<Identifier> getFabricDependencies() {
        return me.xjqsh.lrtactical.client.resource.LrClientAssetsManager.taczAssetDependencies();
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        dataMap.clear();
        for (Map.Entry<Identifier, JsonElement> entry : pObject.entrySet()) {
            Identifier id = entry.getKey();
            try {
                var pojo = getGson().fromJson(entry.getValue(), MeleeDisplayInstance.MeleeDisplay.class);
                dataMap.put(id, MeleeDisplayInstance.create(pojo, id));
            } catch (JsonParseException | IllegalArgumentException e) {
                GunMod.LOGGER.error(getMarker(), "Failed to load display file {}", id, e);
            }
        }
    }
}
