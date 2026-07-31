package cn.sh1rocu.tacz.industry.loader;

import cn.sh1rocu.tacz.industry.api.bullet.BulletType;
import cn.sh1rocu.tacz.industry.api.cartridge.CartridgeType;
import cn.sh1rocu.tacz.industry.registry.BulletRegistry;
import cn.sh1rocu.tacz.industry.registry.CartridgeRegistry;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 口径/弹头数据包的资源重载入口（数据驱动承诺的执行层）。
 *
 * <p>路径约定（与原版 tag/recipe 同级习惯）：</p>
 * <ul>
 *   <li>{@code data/<ns>/cartridge/<name>.json} → CartridgeRegistry</li>
 *   <li>{@code data/<ns>/bullet/<name>.json}    → BulletRegistry</li>
 * </ul>
 *
 * <p>注册名 = 数据包命名空间:文件名（不含扩展名）；与内置默认同名则覆盖。
 * 解析失败的单条文件记 error 并跳过——一个坏 JSON 绝不允许掀翻整个注册表。</p>
 *
 * <p>接口选择说明：实现 {@link IdentifiableResourceReloadListener} 并以 26.2 最新
 * {@code reload(SharedState, Executor, PreparationBarrier, Executor)} 签名接入
 * （与 ClientAssetsManager 中仓库自验的匿名实现同模式），规避
 * SimpleSynchronousResourceReloadListener 在本版本 fabric-api 中可用性的不确定。</p>
 */
public class IndustryDataLoader implements IdentifiableResourceReloadListener {
    public static final IndustryDataLoader INSTANCE = new IndustryDataLoader();

    private static final Gson GSON = new Gson();
    private static final Identifier LISTENER_ID = Identifier.fromNamespaceAndPath("taczind", "data_loader");
    private static final String CARTRIDGE_DIR = "cartridge";
    private static final String BULLET_DIR = "bullet";

    private IndustryDataLoader() {
    }

    @Override
    public Identifier getFabricId() {
        return LISTENER_ID;
    }

    @Override
    public CompletableFuture<Void> reload(SharedState sharedState, Executor backgroundExecutor,
                                          PreparationBarrier barrier, Executor gameExecutor) {
        // 数据读取与解析放后台线程；注册表重建（final 状态切换）放主线程串行点
        return CompletableFuture
                .supplyAsync(() -> parseAll(sharedState.resourceManager()), backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(parsed -> {
                    CartridgeRegistry.rebuild(parsed.cartridges);
                    BulletRegistry.rebuild(parsed.bullets);
                }, gameExecutor);
    }

    private record ParsedRegistries(Map<Identifier, CartridgeType> cartridges,
                                    Map<Identifier, BulletType> bullets) {
    }

    private static ParsedRegistries parseAll(ResourceManager resourceManager) {
        Map<Identifier, CartridgeType> cartridges = new LinkedHashMap<>();
        resourceManager.listResources(CARTRIDGE_DIR, path -> path.getPath().endsWith(".json"))
                .forEach((fileId, resource) -> {
                    Identifier id = toEntryId(fileId, CARTRIDGE_DIR);
                    try (InputStreamReader reader = openReader(resource)) {
                        JsonObject json = GSON.fromJson(reader, JsonObject.class);
                        cartridges.put(id, CartridgeRegistry.fromJson(id, json));
                    } catch (Exception e) {
                        GunMod.LOGGER.error("[taczind] Failed to load cartridge json {}", fileId, e);
                    }
                });

        Map<Identifier, BulletType> bullets = new LinkedHashMap<>();
        resourceManager.listResources(BULLET_DIR, path -> path.getPath().endsWith(".json"))
                .forEach((fileId, resource) -> {
                    Identifier id = toEntryId(fileId, BULLET_DIR);
                    try (InputStreamReader reader = openReader(resource)) {
                        JsonObject json = GSON.fromJson(reader, JsonObject.class);
                        bullets.put(id, BulletType.fromJson(id, json));
                    } catch (Exception e) {
                        GunMod.LOGGER.error("[taczind] Failed to load bullet json {}", fileId, e);
                    }
                });
        return new ParsedRegistries(cartridges, bullets);
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
