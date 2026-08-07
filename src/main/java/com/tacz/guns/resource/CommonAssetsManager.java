package com.tacz.guns.resource;

import cn.sh1rocu.tacz.TaCZFabric;
import cn.sh1rocu.tacz.api.event.AddReloadListenerEvent;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tacz.guns.api.vmlib.LuaGunLogicConstant;
import com.tacz.guns.api.vmlib.LuaLibrary;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.init.ModRecipe;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageSyncGunPack;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.manager.*;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.block.BlockData;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.Ignite;
import com.tacz.guns.industry.maintenance.IndustryMaintenanceProfile;
import com.tacz.guns.industry.maintenance.IndustryMaintenanceProfileManager;
import com.tacz.guns.industry.magazine.GunFeedDefinition;
import com.tacz.guns.industry.recipe.CartridgeAssemblyDefinition;
import com.tacz.guns.industry.recipe.CartridgeAssemblyRecipeManager;
import com.tacz.guns.industry.recipe.IndustryAssemblyDefinition;
import com.tacz.guns.industry.recipe.IndustryProcessDefinition;
import com.tacz.guns.industry.recipe.IndustryProcessManager;
import com.tacz.guns.industry.reference.IndustryIdentityAlias;
import com.tacz.guns.industry.reference.IndustryIdentityAliasManager;
import com.tacz.guns.industry.reference.IndustryReferenceProfile;
import com.tacz.guns.industry.reference.IndustryReferenceProfileManager;
import com.tacz.guns.resource.pojo.data.loot.LootTableInjection;
import com.tacz.guns.resource.serialize.*;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.LuaTable;

import java.util.*;
import java.util.function.Consumer;

public class CommonAssetsManager implements ICommonResourceProvider {
    private static CommonAssetsManager INSTANCE;
    public static final Gson GSON = new GsonBuilder()
            // Gun packs intentionally support JSON5-style comments and trailing commas.
            .setStrictness(com.google.gson.Strictness.LENIENT)
            .registerTypeAdapter(Identifier.class, new IdentifierSerializer())
            .registerTypeAdapter(Pair.class, new PairSerializer())
            .registerTypeAdapter(GunSmithTableIngredient.class, new GunSmithTableIngredientSerializer())
            .registerTypeAdapter(GunSmithTableResult.class, new GunSmithTableResultSerializer())
            .registerTypeAdapter(ExtraDamage.DistanceDamagePair.class, new DistanceDamagePairSerializer())
            .registerTypeAdapter(Vec3.class, new Vec3Serializer())
            .registerTypeAdapter(Ignite.class, new IgniteSerializer())
            .registerTypeAdapter(RecipeFilter.class, new RecipeFilter.Deserializer())
            .registerTypeAdapter(CommonGunIndex.class, new CommonGunIndexSerializer())
            .registerTypeAdapter(CommonAmmoIndex.class, new CommonAmmoIndexSerializer())
            .registerTypeAdapter(CommonAttachmentIndex.class, new CommonAttachmentIndexSerializer())
            .registerTypeAdapter(CommonBlockIndex.class, new CommonBlockIndexSerializer())
            .registerTypeAdapter(TabConfig.class, new TabConfig.Deserializer())
            .registerTypeAdapter(IndustryProcessDefinition.class, new IndustryProcessDefinition.Deserializer())
            .create();

    private final List<INetworkCacheReloadListener> listeners = new ArrayList<>();
    private CommonDataManager<GunData> gunData;
    private CommonDataManager<AttachmentData> attachmentData;
    private CommonDataManager<BlockData> blockData;
    private CommonDataManager<CommonAmmoIndex> ammoIndex;
    private CommonDataManager<CommonGunIndex> gunIndex;
    private CommonDataManager<CommonAttachmentIndex> attachmentIndex;
    private CommonDataManager<CommonBlockIndex> blockIndex;
    /** 第 12 轮：枪械工作台配方，需同步到客户端供 GunSmithTableScreen 使用。 */
    private TableRecipeManager tableRecipe;
    /**
     * Data-driven real-feed declarations.  They intentionally sit outside the
     * original gun pack's GunData files so the ND-licensed default pack is not
     * modified and third-party packs can opt in independently.
     */
    private CommonDataManager<GunFeedDefinition> gunFeed;
    /** Create recipe projection synchronised for the built-in REI bridge. */
    private CommonDataManager<IndustryProcessDefinition> industryProcess;
    /** Server-authoritative dedicated cartridge-assembly definitions, synced for UI/REI. */
    private CommonDataManager<CartridgeAssemblyDefinition> cartridgeAssembly;
    /** Explicit result-id repairs must load before TableRecipeManager applies industrial transforms. */
    private IndustryIdentityAliasManager industryIdentityAliases;
    /** Factual action/feed/ammunition profiles plus the post-table-reload audit snapshot. */
    private IndustryReferenceProfileManager industryReferenceProfiles;
    /** Data-driven phase-A condition/fouling baselines, synchronized for client status rendering. */
    private IndustryMaintenanceProfileManager industryMaintenanceProfiles;
    private RecipeFilterManager recipeFilterManager;
    private LootInjectionManager lootInjectionManager;

    private AttachmentsTagManager attachmentsTagManager;
    List<LuaLibrary> libList = List.of(new LuaGunLogicConstant());
    private final ScriptManager scriptManager = new ScriptManager(new FileToIdConverter("scripts", ".lua"), libList);

    public void reloadAndRegister(Consumer<PreparableReloadListener> register) {
        // 这里会顺序重载，所以需要把index这种依赖data的放在后面
        gunData = register(new CommonDataManager<>(DataType.GUN_DATA, GunData.class, GSON, "data/guns", "GunDataLoader"));
        gunFeed = register(new CommonDataManager<>(DataType.GUN_FEED, GunFeedDefinition.class, GSON,
                "industry/gun_feed", "GunFeedLoader"));
        attachmentData = register(new AttachmentDataManager());
        attachmentsTagManager = register(new AttachmentsTagManager());
        recipeFilterManager = register(new RecipeFilterManager());
        lootInjectionManager = new LootInjectionManager();
        register.accept(lootInjectionManager);
        blockData = register(new CommonDataManager<>(DataType.BLOCK_DATA, BlockData.class, GSON, "data/blocks", "BlockDataLoader"));
        register.accept(scriptManager);

        ammoIndex = register(new CommonDataManager<>(DataType.AMMO_INDEX, CommonAmmoIndex.class, GSON, "index/ammo", "AmmoIndexLoader"));
        gunIndex = register(new CommonDataManager<>(DataType.GUN_INDEX, CommonGunIndex.class, GSON, "index/guns", "GunIndexLoader"));
        attachmentIndex = register(new CommonDataManager<>(DataType.ATTACHMENT_INDEX, CommonAttachmentIndex.class, GSON, "index/attachments", "AttachmentIndexLoader"));
        blockIndex = register(new CommonDataManager<>(DataType.BLOCK_INDEX, CommonBlockIndex.class, GSON, "index/blocks", "BlockIndexLoader"));
        // Aliases depend on the loaded indexes and must be ready before table
        // recipes are parsed/replaced. They are explicit compatibility data,
        // never filename guesses.
        industryIdentityAliases = register(new IndustryIdentityAliasManager());
        // 第 12 轮：把工作台配方也纳入同步。目录与 vanilla 数据包配方一致（data/<ns>/recipe），
        // 这样客户端无需 RecipeManager 也能列出配方（26.2 客户端已无完整配方表）。
        //
        // 必须用 TableRecipeManager 而非裸的 CommonDataManager：该目录里混着原版
        // 与其他模组的配方（实测原版 1585 条），不按 "type" 过滤会全部灌进
        // TableRecipe 的解析器刷屏，并被原样打进同步包。详见该类的注释。
        tableRecipe = register(new TableRecipeManager());
        // This manager validates curated factual profiles after the complete
        // table-recipe snapshot exists, then emits the safe/alias/unresolved
        // audit used by future runtime industrial generation.
        industryReferenceProfiles = register(new IndustryReferenceProfileManager());
        industryMaintenanceProfiles = register(new IndustryMaintenanceProfileManager());
        industryProcess = register(new IndustryProcessManager());
        cartridgeAssembly = register(new CartridgeAssemblyRecipeManager());

        listeners.forEach(register);
        register.accept((sharedState, backgroundExecutor, barrier, gameExecutor) -> {
            return barrier
                    .wait(null)
                    .thenRunAsync(AllowAttachmentTagMatcher::resetCache, gameExecutor);
        });
    }

    private <T extends INetworkCacheReloadListener> T register(T listener) {
        listeners.add(listener);
        return listener;
    }

    public Map<DataType, Map<Identifier, String>> getNetworkCache() {
        ImmutableMap.Builder<DataType, Map<Identifier, String>> builder = ImmutableMap.builder();
        for (INetworkCacheReloadListener listener : listeners) {
            builder.put(listener.getType(), listener.getNetworkCache());
        }
        return builder.build();
    }

    @Nullable
    @Override
    public GunData getGunData(Identifier id) {
        return gunData.getData(id);
    }

    @Nullable
    @Override
    public AttachmentData getAttachmentData(Identifier id) {
        return attachmentData.getData(id);
    }

    @Nullable
    @Override
    public BlockData getBlockData(Identifier id) {
        return blockData.getData(id);
    }

    @Override
    @Nullable
    public RecipeFilter getRecipeFilter(Identifier id) {
        return recipeFilterManager.getFilter(id);
    }

    /**
     * 枪包声明过要注入的<b>目标战利品表 ID 集合</b>。
     *
     * <p>供 {@code LootTableInjectorModifier} 做「正查」用：26.2 无法由 LootTable 实例
     * 反查其注册 ID（RELOADABLE 层只给 HolderLookup，没有反查接口），
     * 因此改为拿这个候选集去逐个正查比对实例。默认枪包只有 1 个目标表。</p>
     */
    public Set<Identifier> getLootInjectionTargets() {
        if (lootInjectionManager == null) {
            return Set.of();
        }
        return lootInjectionManager.getInjectionTargets();
    }

    public List<LootTableInjection> getLootTableInjections(Identifier lootTable) {
        if (lootInjectionManager == null) {
            return List.of();
        }
        return lootInjectionManager.getInjections(lootTable);
    }

    @Nullable
    @Override
    public CommonGunIndex getGunIndex(Identifier gunId) {
        return gunIndex.getData(gunId);
    }

    @Override
    public Set<Map.Entry<Identifier, CommonGunIndex>> getAllGuns() {
        return gunIndex.getAllData().entrySet();
    }

    @Nullable
    @Override
    public CommonAmmoIndex getAmmoIndex(Identifier ammoId) {
        return ammoIndex.getData(ammoId);
    }

    @Override
    public Set<Map.Entry<Identifier, CommonAmmoIndex>> getAllAmmos() {
        return ammoIndex.getAllData().entrySet();
    }

    @Nullable
    @Override
    public CommonAttachmentIndex getAttachmentIndex(Identifier attachmentId) {
        return attachmentIndex.getData(attachmentId);
    }

    @Override
    public Set<Map.Entry<Identifier, CommonAttachmentIndex>> getAllAttachments() {
        return attachmentIndex.getAllData().entrySet();
    }

    @Override
    public LuaTable getScript(Identifier scriptId) {
        return scriptManager.getScript(scriptId);
    }

    @Nullable
    @Override
    public CommonBlockIndex getBlockIndex(Identifier blockId) {
        return blockIndex.getData(blockId);
    }

    @Override
    public TableRecipe getTableRecipe(Identifier recipeId) {
        return tableRecipe == null ? null : tableRecipe.getData(recipeId);
    }

    @Override
    public Set<Map.Entry<Identifier, TableRecipe>> getAllTableRecipes() {
        return tableRecipe == null ? java.util.Collections.emptySet() : tableRecipe.getAllData().entrySet();
    }

    /** Server-side high-fidelity terminal declaration used by the salvage station. */
    @Nullable
    public IndustryAssemblyDefinition getIndustryAssemblyForGun(Identifier gunId) {
        return tableRecipe == null ? null : tableRecipe.getIndustryAssemblyForGun(gunId);
    }

    public Set<Map.Entry<Identifier, CommonBlockIndex>> getAllBlocks() {
        return blockIndex.getAllData().entrySet();
    }

    @Override
    @Nullable
    public GunFeedDefinition getGunFeedDefinition(Identifier gunId) {
        return gunFeed == null ? null : gunFeed.getData(gunId);
    }

    @Override
    public Set<Map.Entry<Identifier, GunFeedDefinition>> getAllGunFeedDefinitions() {
        return gunFeed == null ? Collections.emptySet() : gunFeed.getAllData().entrySet();
    }

    @Override
    @Nullable
    public IndustryReferenceProfile getIndustryReferenceProfile(Identifier gunId) {
        return industryReferenceProfiles == null ? null : industryReferenceProfiles.getProfile(gunId);
    }

    @Override
    public Set<Map.Entry<Identifier, IndustryReferenceProfile>> getAllIndustryReferenceProfiles() {
        return industryReferenceProfiles == null ? Collections.emptySet()
                : industryReferenceProfiles.getValidProfiles().entrySet();
    }

    @Override
    @Nullable
    public IndustryMaintenanceProfile getIndustryMaintenanceProfile(Identifier gunId) {
        return industryMaintenanceProfiles == null ? null : industryMaintenanceProfiles.getProfile(gunId);
    }

    @Override
    public Set<Map.Entry<Identifier, IndustryMaintenanceProfile>> getAllIndustryMaintenanceProfiles() {
        return industryMaintenanceProfiles == null ? Collections.emptySet()
                : industryMaintenanceProfiles.getValidProfiles().entrySet();
    }

    @Override
    @Nullable
    public IndustryIdentityAlias getIndustryIdentityAlias(Identifier recipeId) {
        return industryIdentityAliases == null ? null : industryIdentityAliases.getAlias(recipeId);
    }

    @Override
    public Set<Map.Entry<Identifier, IndustryIdentityAlias>> getAllIndustryIdentityAliases() {
        return industryIdentityAliases == null ? Collections.emptySet()
                : industryIdentityAliases.getAliasesByRecipe().entrySet();
    }

    /** Internal reload ordering bridge for the post-table reference audit. */
    @Nullable
    public TableRecipeManager getTableRecipeManager() {
        return tableRecipe;
    }

    /** Internal alias resolver used before TableRecipeManager parses table outputs. */
    @Nullable
    public IndustryIdentityAliasManager getIndustryIdentityAliasManager() {
        return industryIdentityAliases;
    }

    @Nullable
    public IndustryReferenceProfileManager getIndustryReferenceProfileManager() {
        return industryReferenceProfiles;
    }

    @Override
    @Nullable
    public IndustryProcessDefinition getIndustryProcess(Identifier processId) {
        return industryProcess == null ? null : industryProcess.getData(processId);
    }

    @Override
    public Set<Map.Entry<Identifier, IndustryProcessDefinition>> getAllIndustryProcesses() {
        return industryProcess == null ? Collections.emptySet() : industryProcess.getAllData().entrySet();
    }

    @Override
    @Nullable
    public CartridgeAssemblyDefinition getCartridgeAssemblyRecipe(Identifier recipeId) {
        return cartridgeAssembly == null ? null : cartridgeAssembly.getData(recipeId);
    }

    @Override
    public Set<Map.Entry<Identifier, CartridgeAssemblyDefinition>> getAllCartridgeAssemblyRecipes() {
        return cartridgeAssembly == null ? Collections.emptySet() : cartridgeAssembly.getAllData().entrySet();
    }

    @Override
    public Set<String> getAttachmentTags(Identifier registryName) {
        return attachmentsTagManager.getAttachmentTags(registryName);
    }

    @Override
    public Set<String> getAllowAttachmentTags(Identifier registryName) {
        return attachmentsTagManager.getAllowAttachmentTags(registryName);
    }

    /**
     * 获取实例<br/>
     * 实例仅当内置服务器/专用服务器启动时才会被创建<br/>
     * 当客户端正连接到多人游戏时，该方法将返回 null
     *
     * @return CommonAssetsManger实例
     */
    @Nullable
    public static CommonAssetsManager getInstance() {
        return INSTANCE;
    }

    public static void clearInstance() {
        INSTANCE = null;
    }

    /**
     * 根据当前环境选择合适的缓存<br/>
     * 当前环境为单人游戏或多人游戏的服务端时，返回CommonAssetsManger实例<br/>
     * 当前环境为多人游戏的客户端时，返回CommonNetworkCache实例
     *
     * @return ICommonResourceProvider实例
     */
    public static ICommonResourceProvider get() {
        return INSTANCE == null ? CommonNetworkCache.INSTANCE : INSTANCE;
    }

    public static void onReload(AddReloadListenerEvent event) {
        var commonAssetsManager = new CommonAssetsManager();
        commonAssetsManager.reloadAndRegister(event::addListener);
        INSTANCE = commonAssetsManager;
        INSTANCE.recipeManager = event.getServerResources().getRecipeManager();
    }

    public RecipeManager recipeManager;

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }

    /**
     * 这个事件理论上会在server resource已经完成重载和传输到客户端之前触发<br/>
     * 尝试根据common data初始化延迟加载的配方
     */
    public static void onReload(RegistryAccess registries, boolean client) {
        if (!client) {
            if (getInstance() != null && getInstance().recipeManager != null) {
                List<GunSmithTableRecipe> recipes = getInstance().recipeManager.getRecipes().stream()
                        .map(net.minecraft.world.item.crafting.RecipeHolder::value)
                        .filter(recipe -> recipe.getType() == ModRecipe.GUN_SMITH_TABLE_CRAFTING)
                        .map(GunSmithTableRecipe.class::cast)
                        .toList();
                for (GunSmithTableRecipe recipe : recipes) {
                    recipe.init();
                }
            }
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        clearInstance();
    }

    public static void OnDatapackSync(ServerPlayer player, boolean joined) {
        if (getInstance() == null) {
            return;
        }
        ServerMessageSyncGunPack message = new ServerMessageSyncGunPack(getInstance().getNetworkCache());
        NetworkHandler.sendToClientPlayer(message, player);

    }

    public static void reloadAllPack() {
        var server = TaCZFabric.getServer();
        if (server == null) {
            return;
        }
        PackRepository packrepository = server.getPackRepository();
        packrepository.reload();

        Collection<String> collection = packrepository.getSelectedIds();
        server.reloadResources(collection);
    }
}


