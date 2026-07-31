package cn.sh1rocu.tacz.industry.loader;

import cn.sh1rocu.tacz.industry.api.bullet.BulletType;
import cn.sh1rocu.tacz.industry.api.cartridge.CartridgeType;
import cn.sh1rocu.tacz.industry.api.heat.CoolingCurve;
import cn.sh1rocu.tacz.industry.api.material.MaterialType;
import cn.sh1rocu.tacz.industry.api.process.WorkProcessType;
import cn.sh1rocu.tacz.industry.api.tolerance.AssemblyWeights;
import cn.sh1rocu.tacz.industry.api.tolerance.MachineTsWindow;
import cn.sh1rocu.tacz.industry.api.tolerance.TsGrade;
import cn.sh1rocu.tacz.industry.registry.BulletRegistry;
import cn.sh1rocu.tacz.industry.registry.CartridgeRegistry;
import cn.sh1rocu.tacz.industry.registry.CoolingCurveRegistry;
import cn.sh1rocu.tacz.industry.registry.MaterialRegistry;
import cn.sh1rocu.tacz.industry.registry.ToleranceTables;
import cn.sh1rocu.tacz.industry.registry.WorkProcessRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier;
import net.minecraft.server.packs.resources.PreparableReloadListener.SharedState;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * TACZ-INDUSTRIAL 全部数据驱动注册表的资源重载入口（数据驱动承诺的执行层，P1 重构为通用多目录模式）。
 *
 * <p><b>目录约定（一类实体一目录，注册名 = 数据包命名空间:文件名）：</b></p>
 * <ul>
 *   <li>{@code cartridge/}                → CartridgeRegistry（P0，代码内置默认+JSON 覆盖）</li>
 *   <li>{@code bullet/}                   → BulletRegistry（P0）</li>
 *   <li>{@code material/}                 → MaterialRegistry（P1，A-1 材料树）</li>
 *   <li>{@code process/}                  → WorkProcessRegistry（P1，A-2 工序）</li>
 *   <li>{@code cooling_curve/}            → CoolingCurveRegistry（P1，A-2 冷却介质）</li>
 *   <li>{@code tolerance/machine_ts/}     → ToleranceTables.machines（P1，A-8a）</li>
 *   <li>{@code tolerance/grade_band/}     → ToleranceTables.grades（P1，A-8d）</li>
 *   <li>{@code tolerance/weights/}        → ToleranceTables.weights（P1，A-8c）</li>
 * </ul>
 *
 * <p><b>健壮性立场（P0 沿袭 + 21 章）：</b>单条文件解析失败记 error 并跳过——
 * 一个坏 JSON 绝不允许掀翻对应注册表，更不允许波及其他目录。</p>
 *
 * <p>线程模型：读取与解析在 backgroundExecutor；注册表重建在 gameExecutor（barrier 后串行点）。</p>
 */
public class IndustryDataLoader implements IdentifiableResourceReloadListener {
    public static final IndustryDataLoader INSTANCE = new IndustryDataLoader();

    private static final Gson GSON = new Gson();
    private static final Identifier LISTENER_ID = Identifier.fromNamespaceAndPath("taczind", "data_loader");

    private IndustryDataLoader() {
    }

    @Override
    public Identifier getFabricId() {
        return LISTENER_ID;
    }

    /**
     * 一类数据实体的装载说明：目录 + 单条解析函数 + 汇总后的落点。
     * sink 只在主线程串行点被调用（见 reload），参数为不可变 Map。
     */
    private record DirSpec<T>(String dir,
                              BiFunction<Identifier, JsonObject, T> parser,
                              Consumer<Map<Identifier, T>> sink) {
    }

    private static final List<DirSpec<?>> SPECS = new ArrayList<>();

    static {
        SPECS.add(new DirSpec<>("cartridge", CartridgeRegistry::fromJson, CartridgeRegistry::rebuild));
        SPECS.add(new DirSpec<>("bullet", BulletType::fromJson, BulletRegistry::rebuild));
        SPECS.add(new DirSpec<>("material", MaterialType::fromJson, MaterialRegistry::rebuild));
        SPECS.add(new DirSpec<>("process", WorkProcessType::fromJson, WorkProcessRegistry::rebuild));
        SPECS.add(new DirSpec<>("cooling_curve", CoolingCurve::fromJson, CoolingCurveRegistry::rebuild));
        SPECS.add(new DirSpec<>("tolerance/machine_ts", MachineTsWindow::fromJson, ToleranceTables::rebuildMachines));
        SPECS.add(new DirSpec<>("tolerance/grade_band", TsGrade::fromJson, ToleranceTables::rebuildGrades));
        SPECS.add(new DirSpec<>("tolerance/weights", AssemblyWeights::fromJson, ToleranceTables::rebuildWeights));
    }

    @Override
    public CompletableFuture<Void> reload(SharedState sharedState, Executor backgroundExecutor,
                                          PreparationBarrier barrier, Executor gameExecutor) {
        return CompletableFuture
                .supplyAsync(() -> {
                    ResourceManager rm = sharedState.resourceManager();
                    List<ParsedDir<?>> parsed = new ArrayList<>(SPECS.size());
                    for (DirSpec<?> spec : SPECS) {
                        parsed.add(new ParsedDir<>(spec, parseDir(rm, spec)));
                    }
                    return parsed;
                }, backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(parsed -> {
                    for (ParsedDir<?> p : parsed) {
                        p.apply();
                    }
                }, gameExecutor);
    }

    private record ParsedDir<T>(DirSpec<T> spec, Map<Identifier, T> entries) {
        void apply() {
            spec.sink().accept(entries);
        }
    }

    private static <T> Map<Identifier, T> parseDir(ResourceManager resourceManager, DirSpec<T> spec) {
        Map<Identifier, T> entries = new LinkedHashMap<>();
        resourceManager.listResources(spec.dir(), path -> path.getPath().endsWith(".json"))
                .forEach((fileId, resource) -> {
                    Identifier id = toEntryId(fileId, spec.dir());
                    try (InputStreamReader reader = openReader(resource)) {
                        JsonObject json = GSON.fromJson(reader, JsonObject.class);
                        entries.put(id, spec.parser().apply(id, json));
                    } catch (Exception e) {
                        GunMod.LOGGER.error("[taczind] Failed to load {} json {}", spec.dir(), fileId, e);
                    }
                });
        return entries;
    }

    private static InputStreamReader openReader(Resource resource) throws IOException {
        return new InputStreamReader(resource.open(), StandardCharsets.UTF_8);
    }

    private static Identifier toEntryId(Identifier fileId, String dir) {
        String path = fileId.getPath();
        String name = path.substring(dir.length() + 1, path.length() - ".json".length());
        return Identifier.fromNamespaceAndPath(fileId.getNamespace(), name);
    }
}
