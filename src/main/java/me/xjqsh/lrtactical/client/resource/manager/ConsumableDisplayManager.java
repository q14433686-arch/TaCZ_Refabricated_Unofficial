package me.xjqsh.lrtactical.client.resource.manager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.manager.JsonDataManager;
import me.xjqsh.lrtactical.client.resource.display.ConsumableDisplayInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Collection;
import java.util.Map;

/**
 * 加载 {@code assets/<ns>/display/consumable/*.json}。
 *
 * <p>设计与 {@link MeleeDisplayManager} 完全一致，说明见该类注释。
 */
public class ConsumableDisplayManager extends JsonDataManager<ConsumableDisplayInstance> {
    public ConsumableDisplayManager(Gson pGson) {
        super(null, pGson, "display/consumable", "LrConsumableDisplay");
    }

    /**
     * 声明「必须在 TACZ 的模型/动画/脚本加载完之后再跑」。
     *
     * <p><b>Fabric 侧必需，不能照抄 NeoForge 版把这个覆写省掉</b>：
     * NeoForge 的资源重载是顺序的，Fabric 则并行调度，
     * 缺了依赖声明时 {@code ConsumableDisplayInstance#create} 同步去
     * {@code ClientAssetsManager} 取模型/动画/脚本会<b>偶发</b>取到 null，
     * 表现为随机的 "no corresponding model found"。
     *
     * @see MeleeDisplayManager#getFabricDependencies()
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
                var pojo = getGson().fromJson(entry.getValue(), ConsumableDisplayInstance.ConsumableDisplay.class);
                dataMap.put(id, ConsumableDisplayInstance.create(pojo, id));
            } catch (JsonParseException | IllegalArgumentException e) {
                GunMod.LOGGER.error(getMarker(), "Failed to load display file {}", id, e);
            }
        }
    }
}
