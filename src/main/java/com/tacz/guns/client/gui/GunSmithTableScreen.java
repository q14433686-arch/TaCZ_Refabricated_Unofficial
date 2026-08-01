package com.tacz.guns.client.gui;

import cn.sh1rocu.tacz.mixin.accessor.ScreenAccessor;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.components.FlatColorButton;
import com.tacz.guns.client.gui.components.GunPackList;
import com.tacz.guns.client.gui.components.smith.ResultButton;
import com.tacz.guns.client.gui.components.smith.TypeButton;
import com.tacz.guns.client.gui.preview.GunPreviewRenderState;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.network.message.ClientMessageCraft;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import com.tacz.guns.util.RenderDistance;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3x2fStack;

import javax.annotation.Nullable;
import java.util.*;

public class GunSmithTableScreen extends AbstractContainerScreen<GunSmithTableMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/gui/gun_smith_table.png");
    private static final Identifier SIDE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/gui/gun_smith_table_side.png");

    private final LinkedHashMap<Identifier, TabConfig> recipeKeys = Maps.newLinkedHashMap();
    private final Map<Identifier, List<Identifier>> recipes = Maps.newLinkedHashMap();

    private int typePage;
    private Identifier selectedType = null;
    private List<Identifier> selectedRecipeList = new ArrayList<>();

    private int indexPage;
    private @Nullable GunSmithTableRecipe selectedRecipe;
    private @Nullable Int2IntArrayMap playerIngredientCount;

    private int scale = 70;
    private boolean filterEnabled = false;
    private GunPackList filterList;
    private boolean autoByHandFilterApplied = false;

    public GunSmithTableScreen(GunSmithTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 344, 186);
        this.classifyRecipes();
        this.typePage = 0;
        this.indexPage = 0;
        this.selectedRecipe = this.getSelectedRecipe(selectedRecipeList != null && !this.selectedRecipeList.isEmpty() ? this.selectedRecipeList.get(0) : null);
        this.getPlayerIngredientCount(this.selectedRecipe);
    }

    public static void drawModCenteredString(GuiGraphicsExtractor gui, Font font, Component component, int pX, int pY, int color) {
        FormattedCharSequence text = component.getVisualOrderText();
        gui.text(font, text, pX - font.width(text) / 2, pY, color, false);
    }

    private void classifyRecipes() {
        this.recipes.clear();
        this.recipeKeys.clear();
        Identifier blockId = menu.getBlockId();
        if (blockId == null) {
            return;
        }
        Map<Identifier, List<Identifier>> recipes = Maps.newLinkedHashMap();
        Map<Identifier, TabConfig> recipeKeys = Maps.newLinkedHashMap();

        TimelessAPI.getCommonBlockIndex(blockId).ifPresent(blockIndex -> {
            var tabs = blockIndex.getData().getTabs();
            if (DefaultAssets.DEFAULT_BLOCK_ID.equals(blockId) && !SyncConfig.ENABLE_TABLE_FILTER.get()) {
                tabs = TabConfig.DEFAULT_TABS;
            }
            for (TabConfig tab : tabs) {
                recipes.put(tab.id(), Lists.newArrayList());
                recipeKeys.put(tab.id(), tab);
            }
        });

        List<Pair<Identifier, Identifier>> recipeIds = Lists.newArrayList();

        // 第 12 轮修复：改用 CommonAssetsManager.get()。
        //
        // 之前这里用的是 getInstance()，那是<b>纯服务端</b>实例（recipeManager 只在
        // AddReloadListenerEvent 里由 event.getServerResources() 赋值）。
        // 多人客户端上 getInstance() 恒为 null -> 配方列表必然为空，
        // 表现就是"工作台打得开、分类页签也在，但一条配方都没有"。
        //
        // get() 会在客户端回退到 CommonNetworkCache（与本方法下面取 blockIndex 的方式一致，
        // 此前同一个方法里两种取法混用，正是 bug 来源）。
        //
        // 注意不能照抄上游的 recipeManager.getAllRecipesFor(...)：26.2 客户端<b>没有</b>
        // 完整配方表（ClientLevel#recipeAccess 只有 propertySet/stonecutterRecipes），
        // 因此配方改由 mod 自己经 DataType.RECIPES 通道同步过来。
        Set<String> namespaces = filterList != null ? filterList.namespaceList() : null;
        for (Map.Entry<Identifier, TableRecipe> entry : CommonAssetsManager.get().getAllTableRecipes()) {
            Identifier id = entry.getKey();
            TableRecipe pojo = entry.getValue();
            if (pojo == null || pojo.getResult() == null) {
                continue;
            }
            if (namespaces != null && !namespaces.contains(id.getNamespace())) {
                continue;
            }
            GunSmithTableRecipe recipe = new GunSmithTableRecipe(id, pojo);
            // 必须调用 init()：Gson 反序列化出来的 result 里只有 raw 数据，
            // 真正的 ItemStack 与 group 要靠 RawGunTableResult.init 解析出来。
            // 少了这一步，getResult() 恒为 EMPTY、getGroup() 为 null，
            // 所有配方都会在下面的 recipeKeys.containsKey(groupName) 处被过滤掉
            // —— 这就是第 12 轮"所有配方都看不见"的原因。
            recipe.init();
            if (!isSuitableForMainHand(recipe)) {
                continue;
            }
            if (!isNameMatch(recipe)) {
                continue;
            }
            Identifier groupName = recipe.getResult().getGroup();
            if (recipeKeys.containsKey(groupName)) {
                recipeIds.add(Pair.of(groupName, id));
            }
        }

        TimelessAPI.getCommonBlockIndex(menu.getBlockId()).map(blockIndex -> {
            if (menu.getBlockId().equals(DefaultAssets.DEFAULT_BLOCK_ID) && !SyncConfig.ENABLE_TABLE_FILTER.get()) {
                return null;
            }
            RecipeFilter filter = blockIndex.getFilter();
            if (filter != null) {
                return filter.filter(recipeIds, Pair::value);
            }
            return null;
        }).orElse(recipeIds).forEach(entry -> {
            Identifier groupName = entry.key();
            if (recipeKeys.containsKey(groupName)) {
                recipes.computeIfAbsent(groupName, g -> Lists.newArrayList()).add(entry.value());
            }
        });

        for (var entry : recipes.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                this.recipes.put(entry.getKey(), entry.getValue());
                this.recipeKeys.put(entry.getKey(), recipeKeys.get(entry.getKey()));
            }
        }

        if (!this.recipeKeys.containsKey(selectedType)) {
            selectedType = null;
            selectedRecipeList = null;
            indexPage = 0;
        }

        if (!this.recipeKeys.keySet().isEmpty()) {
            if (selectedType == null) {
                selectedType = this.recipeKeys.keySet().iterator().next();
            }
        }

        if (selectedType != null) {
            selectedRecipeList = this.recipes.get(selectedType);
        }
    }

    private boolean isNameMatch(GunSmithTableRecipe recipe) {
        if (filterList != null && StringUtils.isNotBlank(filterList.getSearchText())) {
            String searchText = filterList.getSearchText().toLowerCase();
            Component name = recipe.getResult().getResult().getHoverName();
            return name.getString().toLowerCase().contains(searchText);
        }
        return true;
    }

    private boolean isSuitableForMainHand(GunSmithTableRecipe recipe) {
        if (filterList != null && filterList.isByHandSelected()) {
            ItemStack result = recipe.getResult().getResult();

            Minecraft minecraft = Minecraft.getInstance();
            ItemStack stack = minecraft.player != null ? minecraft.player.getMainHandItem() : ItemStack.EMPTY;
            if (stack.getItem() instanceof IGun igun) {
                if (result.getItem() instanceof IAmmo iAmmo) {
                    return iAmmo.isAmmoOfGun(stack, result);
                }
                if (result.getItem() instanceof IAttachment) {
                    return igun.allowAttachment(stack, result);
                }
                return false;
            }
            if (stack.getItem() instanceof IAttachment) {
                if (result.getItem() instanceof IGun iGun) {
                    return iGun.allowAttachment(result, stack);
                }
                return false;
            }
            if (stack.getItem() instanceof IAmmo iAmmo) {
                if (result.getItem() instanceof IGun) {
                    return iAmmo.isAmmoOfGun(result, stack);
                }
                return false;
            }
        }
        return true;
    }

    private boolean shouldFilterByMainHand() {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack stack = minecraft.player != null ? minecraft.player.getMainHandItem() : ItemStack.EMPTY;
        Item item = stack.getItem();
        return item instanceof IGun || item instanceof IAttachment || item instanceof IAmmo;
    }

    private void updateSelectedRecipeAfterFiltering() {
        if (selectedRecipeList == null || selectedRecipeList.isEmpty()) {
            this.selectedRecipe = null;
            this.playerIngredientCount = null;
            return;
        }
        boolean selectedRecipeExists = this.selectedRecipe != null && selectedRecipeList.contains(this.selectedRecipe.getId());
        if (!selectedRecipeExists) {
            this.selectedRecipe = this.getSelectedRecipe(selectedRecipeList.get(0));
        }
        this.getPlayerIngredientCount(this.selectedRecipe);
    }

    public void setIndexPage(int indexPage) {
        this.indexPage = indexPage;
    }

    @Nullable
    private GunSmithTableRecipe getSelectedRecipe(Identifier recipeId) {
        // 第 12 轮：同上，改走客户端可用的同步数据。
        if (recipeId != null) {
            TableRecipe pojo = CommonAssetsManager.get().getTableRecipe(recipeId);
            if (pojo != null && pojo.getResult() != null) {
                GunSmithTableRecipe recipe = new GunSmithTableRecipe(recipeId, pojo);
                recipe.init();   // 同上，必须解析 raw result
                return recipe;
            }
        }
        return null;
    }

    private void getPlayerIngredientCount(GunSmithTableRecipe recipe) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || recipe == null) {
            return;
        }
        List<GunSmithTableIngredient> ingredients = recipe.getInputs();
        int size = ingredients.size();
        this.playerIngredientCount = new Int2IntArrayMap(size);
        for (int i = 0; i < size; i++) {
            GunSmithTableIngredient ingredient = ingredients.get(i);
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
            }
            playerIngredientCount.put(i, count);
        }
    }

    public void updateIngredientCount() {
        if (this.selectedRecipe != null) {
            this.getPlayerIngredientCount(selectedRecipe);
        }
        this.init();
    }

    @Override
    public void init() {
        super.init();
        if (this.filterList == null) {
            this.filterList = new GunPackList(this.minecraft, 134, this.imageHeight, topPos, topPos + imageHeight + 1, 15, recipes, this);
        }
        if (!this.autoByHandFilterApplied && RenderConfig.AUTO_SELECT_GUN_SMITH_TABLE_FILTER.get()) {
            this.filterList.setByHandSelected(this.shouldFilterByMainHand());
            this.autoByHandFilterApplied = true;
        }
        this.filterList.updateSizeAndPosition(134, this.imageHeight, topPos, topPos + imageHeight + 1);
        this.filterList.setPosition(leftPos, topPos);

        this.classifyRecipes();
        this.updateSelectedRecipeAfterFiltering();
        this.clearWidgets();

        this.addTypePageButtons();
        this.addTypeButtons();
        this.addIndexPageButtons();
        this.addIndexButtons();
        this.addRenderableWidget(new FlatColorButton(leftPos - 10, topPos, 9, 9, Component.literal("F"), b -> {
            this.filterEnabled = !this.filterEnabled;
            this.init();
        }).setTooltips("gui.tacz.gun_smith_table.filter"));
        if (this.filterEnabled) {
            this.addRenderableWidget(this.filterList);
        } else {
            this.addScaleButtons();
            this.addUrlButton();
        }
        this.addCraftButton();
    }

    private void addCraftButton() {
        this.addRenderableWidget(Button.builder(Component.translatable("gui.tacz.gun_smith_table.craft"), b -> {
            if (this.selectedRecipe != null && playerIngredientCount != null) {
                List<GunSmithTableIngredient> inputs = selectedRecipe.getInputs();
                int size = inputs.size();
                for (int i = 0; i < size; i++) {
                    if (i >= playerIngredientCount.size()) {
                        return;
                    }
                    int hasCount = playerIngredientCount.get(i);
                    int needCount = inputs.get(i).getCount();
                    boolean isCreative = Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
                    if (hasCount < needCount && !isCreative) {
                        return;
                    }
                }
                ClientPlayNetworking.send(new ClientMessageCraft(this.selectedRecipe.getId(), this.menu.containerId));
            }
        }).bounds(leftPos + 289, topPos + 162, 48, 18).build());
    }

    private void addUrlButton() {
        this.addRenderableWidget(Button.builder(Component.literal("URL"), b -> {
            if (this.selectedRecipe != null) {
                ItemStack output = selectedRecipe.getOutput();
                Item item = output.getItem();
                Identifier id;
                if (item instanceof IGun iGun) {
                    id = iGun.getGunId(output);
                } else if (item instanceof IAttachment iAttachment) {
                    id = iAttachment.getAttachmentId(output);
                } else if (item instanceof IAmmo iAmmo) {
                    id = iAmmo.getAmmoId(output);
                } else {
                    return;
                }

                PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(id);
                if (packInfo == null) {
                    return;
                }
                String url = packInfo.getUrl();
                if (StringUtils.isNotBlank(url) && minecraft != null) {
                    minecraft.setScreenAndShow(new ConfirmLinkScreen(yes -> {
                        if (yes) {
                            Util.getPlatform().openUri(url);
                        }
                        minecraft.setScreenAndShow(this);
                    }, url, false));
                }
            }
        }).bounds(leftPos + 112, topPos + 164, 18, 18).build());
    }

    private void addIndexButtons() {
        if (selectedRecipeList == null || selectedRecipeList.isEmpty()) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            int finalIndex = i + indexPage * 6;
            if (finalIndex >= selectedRecipeList.size()) {
                break;
            }
            int yOffset = topPos + 66 + 17 * i;
            Identifier recipeId = selectedRecipeList.get(finalIndex);
            GunSmithTableRecipe recipe = getSelectedRecipe(recipeId);
            if (recipe == null) {
                continue;
            }
            ResultButton button = addRenderableWidget(new ResultButton(leftPos + 144, yOffset, recipe.getOutput(), b -> {
                this.selectedRecipe = recipe;
                this.getPlayerIngredientCount(this.selectedRecipe);
                this.init();
            }));
            if (this.selectedRecipe != null && recipe.getId().equals(this.selectedRecipe.getId())) {
                button.setSelected(true);
            }
        }
    }

    private void addTypeButtons() {
        var list = Arrays.asList(recipeKeys.values().toArray(new TabConfig[0]));
        for (int i = 0; i < 7; i++) {
            int typeIndex = typePage * 7 + i;
            if (typeIndex >= recipes.size()) {
                return;
            }
            TabConfig tabConfig = list.get(typeIndex);
            Identifier type = tabConfig.id();
            int xOffset = leftPos + 157 + 24 * i;

            ItemStack icon = tabConfig.icon().get();

            TypeButton typeButton = new TypeButton(xOffset, topPos + 2, icon, b -> {
                this.selectedType = type;
                this.selectedRecipeList = recipes.get(type);
                this.indexPage = 0;
                this.selectedRecipe = getSelectedRecipe(this.selectedRecipeList.isEmpty() ? null : this.selectedRecipeList.get(0));
                this.getPlayerIngredientCount(this.selectedRecipe);
                this.init();
            });
            typeButton.setTooltip(Tooltip.create(tabConfig.getName(), tabConfig.getName()));
            if (this.selectedType.equals(type)) {
                typeButton.setSelected(true);
            }
            this.addRenderableWidget(typeButton);
        }
    }

    private void addIndexPageButtons() {
        this.addRenderableWidget(Button.builder(Component.literal("^"), b -> {
            if (this.indexPage > 0) {
                this.indexPage--;
                this.init();
            }
        }).bounds(leftPos + 143, topPos + 56, 96, 6).build());
        this.addRenderableWidget(Button.builder(Component.literal("v"), b -> {
            if (selectedRecipeList != null && !selectedRecipeList.isEmpty()) {
                int maxIndexPage = (selectedRecipeList.size() - 1) / 6;
                if (this.indexPage < maxIndexPage) {
                    this.indexPage++;
                    this.init();
                }
            }
        }).bounds(leftPos + 143, topPos + 171, 96, 6).build());
    }

    private void addTypePageButtons() {
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (this.typePage > 0) {
                this.typePage--;
                this.init();
            }
        }).bounds(leftPos + 136, topPos + 4, 18, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            int maxIndexPage = (recipes.size() - 1) / 7;
            if (this.typePage < maxIndexPage) {
                this.typePage++;
                this.init();
            }
        }).bounds(leftPos + 327, topPos + 4, 18, 20).build());
    }

    private void addScaleButtons() {
        this.addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            this.scale = Math.min(this.scale + 20, 200);
        }).bounds(leftPos + 5, topPos + 5, 10, 10).build());
        this.addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            this.scale = Math.max(this.scale - 20, 10);
        }).bounds(leftPos + 17, topPos + 5, 10, 10).build());
        this.addRenderableWidget(Button.builder(Component.literal("R"), b -> {
            this.scale = 70;
        }).bounds(leftPos + 29, topPos + 5, 10, 10).build());
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollDeltaX, double pScrollDeltaY) {
        if (pMouseX > leftPos + 143 && pMouseX < leftPos + 143 + 94 && pMouseY > topPos + 66 && pMouseY < topPos + 66 + 85) {
            if (pScrollDeltaY > 0) {
                this.indexPage = Math.max(0, this.indexPage - 1);
            } else {
                int maxIndexPage = (selectedRecipeList.size() - 1) / 6;
                this.indexPage = Math.min(maxIndexPage, this.indexPage + 1);
            }
            this.init();
            return true;
        }
        return super.mouseScrolled(pMouseX, pMouseY, pScrollDeltaX, pScrollDeltaY);
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawModCenteredString(graphics, font, Component.translatable("gui.tacz.gun_smith_table.preview"), leftPos + 108, topPos + 5, 0xFF555555);
        if (selectedType != null) {
            var config = recipeKeys.get(selectedType);
            if (config != null) {
                graphics.text(font, config.getName(), leftPos + 150, topPos + 32, 0xFF555555, false);
            }
        }
        graphics.text(font, Component.translatable("gui.tacz.gun_smith_table.ingredient"), leftPos + 254, topPos + 50, 0xFF555555, false);
        // 第 15 轮修复：这里原本又手动画了一遍 "制造"，而下方 addCraftButton() 创建的
        // Button 自身也会绘制自己的标签，两者位置几乎重合 -> 文字叠印成乱码
        // （截图里的 "Cmilaft" 就是 "Craft" 和 "制造" 叠在一起）。
        // 正确做法是让 Button 用本地化文本，这里不再重复绘制。
        if (!this.filterEnabled && this.selectedRecipe != null) {
            this.renderLeftModel(graphics, this.selectedRecipe);
            this.renderPackInfo(graphics, this.selectedRecipe);
            graphics.text(font, Component.translatable("gui.tacz.gun_smith_table.count", this.selectedRecipe.getResult().getResult().getCount()), leftPos + 254, topPos + 140, 0xFF555555, false);
        }
        if (selectedRecipeList != null && !selectedRecipeList.isEmpty()) {
            renderIngredient(graphics);
        }

        ((ScreenAccessor) this).tacz$getRenderables().stream().filter(w -> w instanceof ResultButton)
                .forEach(w -> ((ResultButton) w).renderTooltips(stack -> graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)));
    }

    private void renderPackInfo(GuiGraphicsExtractor gui, GunSmithTableRecipe recipe) {
        ItemStack output = recipe.getOutput();
        Item item = output.getItem();
        Identifier id;
        if (item instanceof IGun iGun) {
            id = iGun.getGunId(output);
        } else if (item instanceof IAttachment iAttachment) {
            id = iAttachment.getAttachmentId(output);
        } else if (item instanceof IAmmo iAmmo) {
            id = iAmmo.getAmmoId(output);
        } else {
            return;
        }

        PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(id);
        Matrix3x2fStack poseStack = gui.pose();
        if (packInfo != null) {
            poseStack.pushMatrix();
            poseStack.scale(0.75f, 0.75f);
            Component nameText = Component.translatable(packInfo.getName());
            gui.text(font, nameText, (int) ((leftPos + 6) / 0.75f), (int) ((topPos + 122) / 0.75f), 0xFF555555, false);
            poseStack.popMatrix();

            poseStack.pushMatrix();
            poseStack.scale(0.5f, 0.5f);

            int offsetX = (leftPos + 6) * 2;
            int offsetY = (topPos + 123) * 2;
            int nameWidth = font.width(nameText);
            Component ver = Component.literal("v" + packInfo.getVersion()).withStyle(style -> style.withUnderlined(true));
            gui.text(font, ver, (int) (offsetX + nameWidth * 0.75f / 0.5f + 5), offsetY, 0xFF555555, false);
            offsetY += 14;

            String descKey = packInfo.getDescription();
            if (StringUtils.isNoneBlank(descKey)) {
                Component desc = Component.translatable(descKey);
                List<FormattedCharSequence> split = font.split(desc, 245);
                for (FormattedCharSequence charSequence : split) {
                    gui.text(font, charSequence, offsetX, offsetY, 0xFF555555, false);
                    offsetY += font.lineHeight;
                }
                offsetY += 3;
            }

            gui.text(font, Component.translatable("gui.tacz.gun_smith_table.license")
                            .append(Component.literal(packInfo.getLicense()).withStyle(style -> style.withColor(0xFF555555))),
                    offsetX, offsetY, 0xFF555555, false);
            offsetY += 12;

            List<String> authors = packInfo.getAuthors();
            if (!authors.isEmpty()) {
                gui.text(font, Component.translatable("gui.tacz.gun_smith_table.authors")
                                .append(Component.literal(StringUtils.join(authors, ", ")).withStyle(style -> style.withColor(0xFF555555))),
                        offsetX, offsetY, 0xFF555555, false);
                offsetY += 12;
            }

            gui.text(font, Component.translatable("gui.tacz.gun_smith_table.date")
                            .append(Component.literal(packInfo.getDate()).withStyle(style -> style.withColor(0xFF555555))),
                    offsetX, offsetY, 0xFF555555, false);

            poseStack.popMatrix();
        } else {
            Identifier recipeId = recipe.getId();
            gui.text(font, Component.translatable("gui.tacz.gun_smith_table.error").withStyle(style -> style.withColor(0xFFAA0000)), leftPos + 6, topPos + 122, 0xFFAF0000, false);
            gui.text(font, Component.translatable("gui.tacz.gun_smith_table.error.id", recipeId.toString()).withStyle(style -> style.withColor(0xFFAA0000)), leftPos + 6, topPos + 134, 0xFFFFFFFF, false);
            PackInfo errorPackInfo = ClientAssetsManager.INSTANCE.getPackInfo(id);
            if (errorPackInfo != null) {
                gui.text(font, Component.translatable(errorPackInfo.getName()).withStyle(style -> style.withColor(0xFFAA0000)), leftPos + 6, topPos + 146, 0xFFAF0000, false);
            }
        }
    }

    private void renderIngredient(GuiGraphicsExtractor gui) {
        if (this.selectedRecipe == null) {
            return;
        }
        List<GunSmithTableIngredient> inputs = this.selectedRecipe.getInputs();
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 2; j++) {
                int index = i * 2 + j;
                if (index >= inputs.size()) {
                    return;
                }
                int offsetX = leftPos + 254 + 45 * j;
                int offsetY = topPos + 62 + 17 * i;

                GunSmithTableIngredient smithTableIngredient = inputs.get(index);
                // 第 14 轮：材料改为延迟解析，可能尚未（或无法）解析出来，必须判空。
                Ingredient ingredient = smithTableIngredient.getIngredient();

                ItemStack[] items = ingredient == null
                        ? new ItemStack[0]
                        : ingredient.display().resolveForStacks(SlotDisplayContext.fromLevel(Minecraft.getInstance().level)).toArray(ItemStack[]::new);
                int itemIndex = ((int) (System.currentTimeMillis() / 1_000)) % Math.max(1, items.length);
                ItemStack item = items.length > 0 ? items[itemIndex] : ItemStack.EMPTY;

                gui.fakeItem(item, offsetX, offsetY);

                Matrix3x2fStack poseStack = gui.pose();
                poseStack.pushMatrix();

                poseStack.translate(0, 0);
                poseStack.scale(0.5f, 0.5f);
                int count = smithTableIngredient.getCount();
                if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative()) {
                    gui.text(font, String.format("%d/∞", count), (offsetX + 17) * 2, (offsetY + 10) * 2, 0xFFFFFFFF, false);
                } else {
                    int hasCount = 0;
                    if (playerIngredientCount != null && index < playerIngredientCount.size()) {
                        hasCount = playerIngredientCount.get(index);
                    }
                    // 第 14 轮修复：这两个色值原本是 6 位（0xFFFFFF / 0xFF0000），alpha 分量为 0。
                    // 26.2 的 GuiGraphicsExtractor#text 开头就是 if (ARGB.alpha(color) != 0)，
                    // alpha=0 的文字会被<b>静默丢弃</b>（1.21.x 的 drawString 会自动补不透明，26.2 不会）。
                    // 结果：材料数量 "x/y" 在两种情况下都画不出来 —— 这正是用户看到的
                    // 「仅在持有所需物品时才显示个数」（那时走的是上面创造模式 0xFFFFFFFF 分支）。
                    int color = count <= hasCount ? 0xFFFFFFFF : 0xFFFF0000;
                    gui.text(font, String.format("%d/%d", count, hasCount), (offsetX + 17) * 2, (offsetY + 10) * 2, color, false);
                }

                poseStack.popMatrix();
            }
        }
    }

    /**
     * 左侧「旋转预览模型」。
     *
     * <h2>本轮修复：缩放/旋转按钮此前完全无效</h2>
     *
     * <p>旧实现只有一句 {@code graphics.item(result, x, y)} —— 画的是 16×16 的<b>物品栏图标</b>，
     * 既不旋转也不缩放；{@code scale} 字段与 {@code +/-/R} 三个按钮从头到尾没有被读过。
     * 这正是玩家反馈的「工作台里的模型没法缩放」。</p>
     *
     * <p>26.2 的 GUI 是「extract → 统一绘制」两段式，1.21.1 那套
     * {@code RenderSystem.getModelViewStack()} + {@code renderStatic} + {@code endBatch}
     * 已全部不存在。唯一能在 GUI 内做带自定义变换的 3D 绘制的官方通道是
     * {@code PictureInPictureRenderer}（vanilla 的实体预览、超框物品都走它），
     * 因此这里改为提交一个 {@link GunPreviewRenderState}，
     * 由 {@link com.tacz.guns.client.gui.preview.GunPreviewRenderer} 渲染到离屏纹理再合回。</p>
     *
     * <p>几何参数逐项照抄上游（{@code renderLeftModel}）：预览框
     * {@code (leftPos+3, topPos+16) 128×99}、自转周期 8 秒、俯角 15°、
     * 缩放基准 70（{@code +/-} 步进 20，范围 10..200）。
     * 上游用 {@code RenderSystem.enableScissor} 限定的可视框，
     * 在 26.2 里由 PIP 的 {@code scissorArea} 承担。</p>
     *
     * <p>用 {@code updateForTopItem} 而非 {@code updateForLiving}：后者会把
     * {@code displayContext.ordinal()} 混进 seed，而这里要的是与上游一致的
     * {@code FIXED} 上下文 + 固定 seed 0。</p>
     */
    private void renderLeftModel(GuiGraphicsExtractor graphics, GunSmithTableRecipe recipe) {
        // 先标记一下，渲染高模（与上游同序：LOD 判定依赖它）
        RenderDistance.markGuiRenderTimestamp();
        if (recipe == null) {
            return;
        }
        ItemStack result = recipe.getOutput();
        if (result.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ItemModelResolver resolver = mc.getItemModelResolver();
        if (resolver == null) {
            return;
        }

        final float rotationPeriodMs = 8000f;
        final float rotPitch = 15f;
        int startX = leftPos + 3;
        int startY = topPos + 16;
        int width = 128;
        int height = 99;

        float yaw = (System.currentTimeMillis() % (long) rotationPeriodMs) * (360f / rotationPeriodMs);

        ItemStackRenderState renderState = new ItemStackRenderState();
        resolver.updateForTopItem(renderState, result, ItemDisplayContext.FIXED, mc.level, mc.player, 0);

        // 上游用 RenderSystem.enableScissor 把预览限制在面板可视框内；26.2 交给 PIP 的 scissorArea。
        // 若外层已有 scissor（例如被别的容器裁剪），取交集，语义与 vanilla 一致。
        ScreenRectangle preview = new ScreenRectangle(startX, startY, width, height);
        ScreenRectangle outer = graphics.scissorStack.peek();
        ScreenRectangle scissor = outer == null ? preview : preview.intersection(outer);

        graphics.guiRenderState.addPicturesInPictureState(new GunPreviewRenderState(
                renderState,
                startX, startY, startX + width, startY + height,
                this.scale, rotPitch, yaw,
                // 上游模型原点 (leftPos+60+8, topPos+50+8) 相对预览框中心 (leftPos+67, topPos+65.5) 的偏移
                1.0f, -7.5f,
                scissor
        ));
    }

    @Override
    protected void extractLabels(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY) {
    }

    /**
     * 26.2 对齐说明（与反编译的 net.minecraft.client.gui.screens.inventory.AbstractContainerScreen 对齐）：
     * <p>
     * 1.21.1 的 {@code renderBg} 在未平移的坐标系里执行，所以旧代码直接用 {@code leftPos/topPos} 画背景是正确的。
     * 26.2 把它拆成了两个阶段：
     * <ul>
     *   <li>{@code Screen#extractRenderStateWithTooltipAndSubtitles} 先调用 {@code extractBackground}（<b>未平移</b>）；</li>
     *   <li>{@code AbstractContainerScreen#extractContents} 内部执行
     *       {@code graphics.pose().pushMatrix(); graphics.pose().translate(leftPos, topPos); ...}，
     *       即该方法体内所有绘制都<b>已经</b>带了 (leftPos, topPos) 偏移。</li>
     * </ul>
     * 因此在 {@code extractContents} 里再加一次 {@code leftPos/topPos} 会让背景整体偏移一倍，
     * 正是“工作方块 UI 与槽位错位”的直接原因。所有原版容器界面（ContainerScreen、CraftingScreen、
     * AbstractFurnaceScreen、ItemCombinerScreen…）无一例外都把背景放在 {@code extractBackground} 中绘制，
     * 这里与之对齐。
     */
    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(gui, mouseX, mouseY, partialTick);
        // 【贴图尺寸必须是 256×256】
        //
        // 26.2 的 blit 多了末尾两个「贴图总尺寸」参数，而 1.21.1 的
        //     gui.blit(TEXTURE, x, y, u, v, w, h)
        // 走的是 256×256 的默认重载。移植时 TEXTURE 这一行被填成了 512 ——
        // 但 gun_smith_table.png 实际就是 256×256（已核实两张图都是）。
        //
        // blit 内部按 u / texWidth 计算 UV，宽度填 512 等于把水平 UV【整体减半】：
        // 主面板只采样到贴图左半边、再横向拉伸到 208px 宽，
        // 于是边框、分隔线、槽位底纹全部错位变形 —— 正是原版(45)与我们版(11)
        // 那些「说不上来但就是不对」的细节差异。
        //
        // 三个工作台共用本 Screen，所以这一处修好，三个界面一起恢复。
        gui.blit(RenderPipelines.GUI_TEXTURED, SIDE, leftPos, topPos, 0, 0, 134, 187, 256, 256);
        gui.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + 136, topPos + 27, 0, 0, 208, 160, 256, 256);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
