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
 * <p>设计与 {@link MeleeDisplayManager} 完全一致（含「单个文件解析失败不影响其它文件」），
 * 说明见该类注释。</p>
 *
 * <p><b>与姊妹仓 TaCZ_Renovated 26.2 的一处刻意不同</b>：那边的同名类没有
 * {@link #getFabricDependencies()}，因为 NeoForge 没有这个概念，重载顺序靠
 * {@code AddClientReloadListenersEvent} 的注册顺序弱保证。本仓是 Fabric，
 * 必须显式声明「排在 TACZ 的模型/动画/脚本之后」——
 * {@code ConsumableDisplayInstance#create} 会<b>同步</b>去 {@code ClientAssetsManager}
 * 取 geo 模型 / bedrock 动画 / Lua 脚本，顺序反了会全数取到 {@code null}，
 * 报「no corresponding model found」，且因为资源重载是并行调度的，这种失败是<b>偶发</b>的。
 */
public class ConsumableDisplayManager extends JsonDataManager<ConsumableDisplayInstance> {
    public ConsumableDisplayManager(Gson pGson) {
        super(null, pGson, "display/consumable", "LrConsumableDisplay");
    }

    /**
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
