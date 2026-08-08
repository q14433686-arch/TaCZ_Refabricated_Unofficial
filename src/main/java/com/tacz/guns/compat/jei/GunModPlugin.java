package com.tacz.guns.compat.jei;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.api.item.gun.GunItemManager;
import com.tacz.guns.compat.jei.category.AttachmentQueryCategory;
import com.tacz.guns.compat.jei.category.CartridgeAssemblyCategory;
import com.tacz.guns.compat.jei.category.GunSmithTableCategory;
import com.tacz.guns.compat.jei.category.GunFeedReferenceCategory;
import com.tacz.guns.compat.jei.category.IndustryProcessCategory;
import com.tacz.guns.compat.jei.entry.AttachmentQueryEntry;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.industry.magazine.GunFeedReferenceEntry;
import com.tacz.guns.industry.recipe.IndustryProcessDefinition;
import com.tacz.guns.industry.recipe.IndustryProcessMachine;
import com.tacz.guns.init.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JeiPlugin
public class GunModPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "jei");
    private static volatile GunModPlugin instance;

    private final Map<Identifier, IRecipeType<GunSmithTableRecipe>> recipeTypeMap = new HashMap<>();
    private final Map<IndustryProcessMachine, IRecipeType<IndustryProcessDefinition>> industryRecipeTypeMap =
            new EnumMap<>(IndustryProcessMachine.class);
    /** Runtime JEI recipes are added after TACZ's server data cache arrives. */
    private final Set<Identifier> syncedIndustryRecipeIds = new HashSet<>();
    /** One feed reference per gun; dynamic additions are needed after remote cache sync. */
    private final Set<Identifier> syncedGunFeedReferenceIds = new HashSet<>();
    private IJeiRuntime runtime;

    public GunModPlugin() {
        instance = this;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        recipeTypeMap.clear();
        industryRecipeTypeMap.clear();
        for (IndustryProcessMachine machine : IndustryProcessMachine.values()) {
            IRecipeType<IndustryProcessDefinition> type = IRecipeType.create(
                    Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "industry/" + machine.id()),
                    IndustryProcessDefinition.class
            );
            registration.addRecipeCategories(new IndustryProcessCategory(
                    registration.getJeiHelpers().getGuiHelper(), machine, type
            ));
            industryRecipeTypeMap.put(machine, type);
        }
        var map = TimelessAPI.getAllCommonBlockIndex();
        for (var entry : map) {
            BlockItem item = entry.getValue().getBlock();
            ItemStack icon = BlockItemBuilder.create(item).setId(entry.getKey()).build();
            IRecipeType<GunSmithTableRecipe> type = IRecipeType.create(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "gun_smith_table/" + entry.getKey().toString().replace(':', '_')), GunSmithTableRecipe.class);
            registration.addRecipeCategories(new GunSmithTableCategory(registration.getJeiHelpers().getGuiHelper(), icon, type, item.getName(icon)));
            recipeTypeMap.put(entry.getKey(), type);
        }
        registration.addRecipeCategories(new AttachmentQueryCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new GunFeedReferenceCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new CartridgeAssemblyCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (Minecraft.getInstance().level == null) return;
        // 第 12 轮：与 GunSmithTableScreen 一致，改用 CommonAssetsManager.get()。
        // getInstance() 是纯服务端实例，在多人客户端恒为 null —— JEI 在单人能用
        // 只是因为同 JVM 共享了服务端实例，连专用服务器时同样会空。
        List<GunSmithTableRecipe> recipes = new java.util.ArrayList<>();
        for (var e : com.tacz.guns.resource.CommonAssetsManager.get().getAllTableRecipes()) {
            if (e.getValue() != null && e.getValue().getResult() != null) {
                GunSmithTableRecipe r = new GunSmithTableRecipe(e.getKey(), e.getValue());
                try {
                    r.init();   // 解析 raw result，否则 getResult() 恒为 EMPTY
                } catch (RuntimeException ex) {
                    // 一条坏配方绝不能中断整个循环 —— 否则全部工作台配方都会从 JEI 消失。
                    GunMod.LOGGER.error("Failed to init gun smith table recipe {} for JEI, skipping it", e.getKey(), ex);
                    continue;
                }
                recipes.add(r);
            }
        }

        for (var entry : recipeTypeMap.entrySet()) {
            TimelessAPI.getCommonBlockIndex(entry.getKey()).ifPresent(blockIndex -> {
                List<GunSmithTableRecipe> recipeList = blockIndex.getFilter().filter(recipes, GunSmithTableRecipe::getId);
                recipeList.removeIf(recipe -> !blockIndex.getData().supportsTab(recipe.getResult().getGroup()));
                registration.addRecipes(entry.getValue(), recipeList);
            });
        }

        registerInitialIndustryRecipes(registration);
        registerInitialGunFeedReferences(registration);
        registration.addRecipes(AttachmentQueryCategory.ATTACHMENT_QUERY, AttachmentQueryEntry.getAllAttachmentQueryEntries());
        registration.addRecipes(CartridgeAssemblyCategory.TYPE,
                com.tacz.guns.resource.CommonAssetsManager.get().getAllCartridgeAssemblyRecipes().stream()
                        .map(java.util.Map.Entry::getValue)
                        .filter(java.util.Objects::nonNull)
                        .toList());
    }

    /**
     * JEI commonly initializes its plugin before a remote server has delivered
     * TACZ's synchronized gun-pack cache. Registering only in the normal
     * startup callback therefore made every dynamic component/die process
     * disappear permanently. Keep the ids already handed to JEI so the runtime
     * sync path can add exactly the missing process definitions once data exists.
     */
    private synchronized Map<IRecipeType<IndustryProcessDefinition>, List<IndustryProcessDefinition>>
    takeUnregisteredIndustryProcesses() {
        Map<IRecipeType<IndustryProcessDefinition>, List<IndustryProcessDefinition>> grouped = new HashMap<>();
        for (Map.Entry<Identifier, IndustryProcessDefinition> entry :
                com.tacz.guns.resource.CommonAssetsManager.get().getAllIndustryProcesses()) {
            IndustryProcessDefinition process = entry.getValue();
            if (process == null) {
                continue;
            }
            IRecipeType<IndustryProcessDefinition> type = industryRecipeTypeMap.get(process.getMachine());
            if (type == null || !syncedIndustryRecipeIds.add(entry.getKey())) {
                continue;
            }
            grouped.computeIfAbsent(type, ignored -> new java.util.ArrayList<>()).add(process);
        }
        return grouped;
    }

    private void registerInitialIndustryRecipes(IRecipeRegistration registration) {
        for (Map.Entry<IRecipeType<IndustryProcessDefinition>, List<IndustryProcessDefinition>> entry :
                takeUnregisteredIndustryProcesses().entrySet()) {
            registration.addRecipes(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Gun/feed relationships are synchronized data, just like the industry
     * process graph. JEI can initialize before a dedicated server has supplied
     * that cache, so retain only the gun ids already registered and add the
     * missing references once the client receives the normal sync packet.
     */
    private synchronized List<GunFeedReferenceEntry> takeUnregisteredGunFeedReferences() {
        List<GunFeedReferenceEntry> references = new java.util.ArrayList<>();
        for (GunFeedReferenceEntry entry : GunFeedReferenceEntry.getAll()) {
            if (syncedGunFeedReferenceIds.add(entry.getGunId())) {
                references.add(entry);
            }
        }
        return references;
    }

    private void registerInitialGunFeedReferences(IRecipeRegistration registration) {
        List<GunFeedReferenceEntry> references = takeUnregisteredGunFeedReferences();
        if (!references.isEmpty()) {
            registration.addRecipes(GunFeedReferenceCategory.TYPE, references);
        }
    }

    private void refreshSynchronizedViewerRecipes() {
        IJeiRuntime activeRuntime = runtime;
        if (activeRuntime == null) {
            return;
        }
        int addedProcesses = 0;
        for (Map.Entry<IRecipeType<IndustryProcessDefinition>, List<IndustryProcessDefinition>> entry :
                takeUnregisteredIndustryProcesses().entrySet()) {
            activeRuntime.getRecipeManager().addRecipes(entry.getKey(), entry.getValue());
            addedProcesses += entry.getValue().size();
        }
        List<GunFeedReferenceEntry> references = takeUnregisteredGunFeedReferences();
        if (!references.isEmpty()) {
            activeRuntime.getRecipeManager().addRecipes(GunFeedReferenceCategory.TYPE, references);
        }
        if (addedProcesses > 0 || !references.isEmpty()) {
            GunMod.LOGGER.info("Added {} synchronized TACZ Create process(es) and {} gun-feed reference entry/entries to JEI.",
                    addedProcesses, references.size());
        }
    }

    /** Called on the client thread after {@code ServerMessageSyncGunPack} refreshes viewer data. */
    public static void onIndustryProcessesSynchronized() {
        GunModPlugin plugin = instance;
        if (plugin != null) {
            plugin.refreshSynchronizedViewerRecipes();
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        this.runtime = jeiRuntime;
        refreshSynchronizedViewerRecipes();
    }

    @Override
    public void onRuntimeUnavailable() {
        this.runtime = null;
        this.syncedIndustryRecipeIds.clear();
        this.syncedGunFeedReferenceIds.clear();
    }

    /**
     * The Gunsmith Table has no input-grid slots to fill. Registering a normal
     * JEI transfer handler lets the familiar + button lock the selected
     * synchronized recipe in the open table instead, while crafting remains
     * server-authoritative through the existing table packet.
     */
    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        for (Map.Entry<Identifier, IRecipeType<GunSmithTableRecipe>> entry : recipeTypeMap.entrySet()) {
            registration.addRecipeTransferHandler(
                    new GunSmithTableRecipeTransferHandler(entry.getKey(), entry.getValue(), registration.getTransferHelper()),
                    entry.getValue()
            );
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (var entry : recipeTypeMap.entrySet()) {
            TimelessAPI.getCommonBlockIndex(entry.getKey()).ifPresent(blockIndex -> {
                ItemStack stack = BlockItemBuilder.create(blockIndex.getBlock()).setId(entry.getKey()).build();
                registration.addCraftingStation(entry.getValue(), stack);
            });

        }
        registration.addCraftingStation(CartridgeAssemblyCategory.TYPE, ModItems.CARTRIDGE_ASSEMBLY_MACHINE.getDefaultInstance());
        for (Map.Entry<IndustryProcessMachine, IRecipeType<IndustryProcessDefinition>> entry : industryRecipeTypeMap.entrySet()) {
            registration.addCraftingStation(entry.getValue(), entry.getKey().workstationStack());
        }

    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(ModItems.AMMO, GunModSubtype.getAmmoSubtype());
        registration.registerSubtypeInterpreter(ModItems.ATTACHMENT, GunModSubtype.getAttachmentSubtype());
        registration.registerSubtypeInterpreter(ModItems.AMMO_BOX, GunModSubtype.getAmmoBoxSubtype());
        registration.registerSubtypeInterpreter(ModItems.MAGAZINE, GunModSubtype.getMagazineSubtype());
        registration.registerSubtypeInterpreter(ModItems.GUN_COMPONENT, GunModSubtype.getIndustrySubtype());
        registration.registerSubtypeInterpreter(ModItems.GUN_COMPONENT_BLANK, GunModSubtype.getIndustrySubtype());
        registration.registerSubtypeInterpreter(ModItems.GUN_BLUEPRINT, GunModSubtype.getIndustrySubtype());
        registration.registerSubtypeInterpreter(ModItems.CARTRIDGE_CASE_BLANK, GunModSubtype.getIndustrySubtype());
        registration.registerSubtypeInterpreter(ModItems.CARTRIDGE_CASE, GunModSubtype.getIndustrySubtype());
        registration.registerSubtypeInterpreter(ModItems.PROJECTILE_BLANK, GunModSubtype.getIndustrySubtype());
        registration.registerSubtypeInterpreter(ModItems.PROJECTILE_CORE, GunModSubtype.getIndustrySubtype());
        registration.registerSubtypeInterpreter(ModItems.PRESS_DIE, GunModSubtype.getIndustrySubtype());
        registration.registerSubtypeInterpreter(ModItems.WORKBENCH_111, GunModSubtype.getTableSubType());
        registration.registerSubtypeInterpreter(ModItems.WORKBENCH_121, GunModSubtype.getTableSubType());
        registration.registerSubtypeInterpreter(ModItems.WORKBENCH_211, GunModSubtype.getTableSubType());
        GunItemManager.getAllGunItems().forEach(item -> registration.registerSubtypeInterpreter(item, GunModSubtype.getGunSubtype()));
    }

    @Override
    public Identifier getPluginUid() {
        return UID;
    }
}
