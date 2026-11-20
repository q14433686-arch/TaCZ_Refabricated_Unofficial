package com.tacz.guns.client.gui.components;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.*;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

/**
 * A screen-local content pack filter; the registered creative tabs keep their full contents.
 */
public class CreativePackFilters {
    private static final Identifier TAB_SPRITE = Identifier.withDefaultNamespace("recipe_book/tab");
    private static final Identifier SELECTED_TAB_SPRITE = Identifier.withDefaultNamespace("recipe_book/tab_selected");
    private static final Identifier ALL_PACKS_ICON = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/gui/creative_pack_all.png");
    private static final int VISIBLE_PACKS = 3;
    private static final int TAB_HEIGHT = 26;
    private static final int TAB_SPACING = 29;

    private final CreativeModeInventoryScreen screen;
    private final Runnable resetScroll;
    private final Map<CreativeModeTab, String> selections = new IdentityHashMap<>();
    private final List<PackFilterButton> buttons = new ArrayList<>();
    private final List<FilterOption> options = new ArrayList<>();
    private Button previousButton;
    private Button nextButton;
    private CreativeModeTab currentTab;
    private Collection<ItemStack> sourceItems;
    private long packInfoVersion = -1;
    private int packScroll;

    public CreativePackFilters(CreativeModeInventoryScreen screen, Runnable resetScroll) {
        this.screen = screen;
        this.resetScroll = resetScroll;
    }

    private int left, top;

    public void init(Consumer<AbstractWidget> add, CreativeModeTab tab, int left, int top) {
        this.left = left;
        this.top = top;
        buttons.clear();
        for (int i = 0; i <= VISIBLE_PACKS; i++) {
            PackFilterButton button = new PackFilterButton(left - 28,
                    top + 11 + TAB_SPACING * i);
            buttons.add(button);
            add.accept(button);
        }
        previousButton = Button.builder(Component.literal("▲"), button -> scrollPacks(-1))
                .bounds(left - 24, top - 3, 20, 12)
                .tooltip(Tooltip.create(Component.translatable("gui.tacz.creative_pack_filter.previous"))).build();
        nextButton = Button.builder(Component.literal("▼"), button -> scrollPacks(1))
                .bounds(left - 24, top + 127, 20, 12)
                .tooltip(Tooltip.create(Component.translatable("gui.tacz.creative_pack_filter.next"))).build();
        add.accept(previousButton);
        add.accept(nextButton);
        updateTab(tab, true);
    }

    public void updateTab(CreativeModeTab tab, boolean force) {
        if (!RenderConfig.RENDER_CREATIVE_PACK_SIDEBAR.get()) {
            if (currentTab == tab) {
                showItems(tab.getDisplayItems());
            }
            selections.clear();
            hideWidgets();
            return;
        }
        Identifier tabId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        if (tabId == null || !GunMod.MOD_ID.equals(tabId.getNamespace()) || tab.getType() != CreativeModeTab.Type.CATEGORY) {
            hideWidgets();
            return;
        }

        Collection<ItemStack> displayItems = tab.getDisplayItems();
        long reloadVersion = ClientAssetsManager.INSTANCE.getPackInfoReloadVersion();
        if (!force && currentTab == tab && sourceItems == displayItems && packInfoVersion == reloadVersion) {
            return;
        }
        if (currentTab != tab) {
            packScroll = 0;
        }
        currentTab = tab;
        sourceItems = displayItems;
        packInfoVersion = reloadVersion;
        options.clear();

        // Keep one read-only view of each source stack. Only the screen's temporary menu gets copies.
        List<ItemStack> allItems = List.copyOf(displayItems);
        options.add(new FilterOption(null, Component.translatable("gui.tacz.creative_pack_filter.all"),
                ItemStack.EMPTY, ALL_PACKS_ICON, allItems));
        Map<String, List<ItemStack>> groupedItems = groupItems(displayItems);
        groupedItems.forEach((namespace, items) -> {
            PackInfo info = ClientAssetsManager.INSTANCE.getPackInfo(namespace);
            Component name = info == null || info.getName() == null || info.getName().isBlank()
                    || "custom.tacz.error.no_name".equals(info.getName())
                    ? Component.literal(namespace) : Component.translatable(info.getName());
            Identifier logo = info == null ? null : info.getLogo(namespace);
            if (logo != null && Minecraft.getInstance().getResourceManager().getResource(logo).isEmpty()) {
                logo = null;
            }
            FilterOption option = new FilterOption(namespace, name, items.get(0).copy(), logo, items);
            if (GunMod.MOD_ID.equals(namespace)) {
                options.add(1, option);
            } else {
                options.add(option);
            }
        });
        String selected = selections.get(tab);
        if (selected != null && !groupedItems.containsKey(selected)) {
            selections.remove(tab);
        }
        if (selected != null) {
            // Returning to a tab/resize: the selected pack must stay within the visible window.
            for (int i = 1; i < options.size(); i++) if (selected.equals(options.get(i).namespace())) {
                packScroll = Mth.clamp(packScroll, Math.max(0, i - VISIBLE_PACKS), i - 1);
                break;
            }
        }
        packScroll = Mth.clamp(packScroll, 0, maxScroll());
        updateButtons();
        applyFilter();
    }

    private void hideWidgets() {
        currentTab = null;
        sourceItems = null;
        buttons.forEach(button -> button.setOption(null));
        previousButton.visible = nextButton.visible = false;
        previousButton.active = nextButton.active = false;
    }

    /**
     * Keeps every stack, including multiple NBT variants of the same registered item.
     */
    static Map<String, List<ItemStack>> groupItems(Collection<ItemStack> items) {
        Map<String, List<ItemStack>> groups = new LinkedHashMap<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                groups.computeIfAbsent(getContentId(stack).getNamespace(), key -> new ArrayList<>()).add(stack);
            }
        }
        return groups;
    }

    static Identifier getContentId(ItemStack stack) {
        if (stack.getItem() instanceof IGun gun) {
            return gun.getGunId(stack);
        }
        if (stack.getItem() instanceof IAmmo ammo) {
            return ammo.getAmmoId(stack);
        }
        if (stack.getItem() instanceof IAttachment attachment) {
            return attachment.getAttachmentId(stack);
        }
        if (stack.getItem() instanceof IBlock block) {
            return block.getBlockId(stack);
        }
        if (stack.getItem() instanceof IAmmoBox box) {
            Identifier ammoId = box.getAmmoId(stack);
            if (!DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
                return ammoId;
            }
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private void select(FilterOption option) {
        if (option.namespace() == null) {
            selections.remove(currentTab);
        } else {
            selections.put(currentTab, option.namespace());
        }
        applyFilter();
    }

    private void applyFilter() {
        String selected = selections.get(currentTab);
        FilterOption option = options.stream().filter(entry -> Objects.equals(entry.namespace(), selected))
                .findFirst().orElse(options.get(0));
        showItems(option.items());
    }

    private void showItems(Collection<ItemStack> items) {
        screen.getMenu().items.clear();
        items.forEach(stack -> screen.getMenu().items.add(stack.copy()));
        resetScroll.run();
        screen.getMenu().scrollTo(0);
    }

    private int maxScroll() {
        return Math.max(0, options.size() - 1 - VISIBLE_PACKS);
    }

    private void scrollPacks(int delta) {
        packScroll = Mth.clamp(packScroll + delta, 0, maxScroll());
        updateButtons();
    }

    private void updateButtons() {
        buttons.get(0).setOption(options.get(0));
        for (int i = 1; i < buttons.size(); i++) {
            int optionIndex = packScroll + i;
            buttons.get(i).setOption(optionIndex < options.size() ? options.get(optionIndex) : null);
        }
        previousButton.visible = nextButton.visible = maxScroll() > 0;
        previousButton.active = packScroll > 0;
        nextButton.active = packScroll < maxScroll();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!RenderConfig.RENDER_CREATIVE_PACK_SIDEBAR.get() || currentTab == null || scrollY == 0) {
            return false;
        }
        int x = left - 28;
        int y = top + 11;
        if (mouseX >= x && mouseX < x + 32 && mouseY >= y && mouseY < y + TAB_SPACING * VISIBLE_PACKS + TAB_HEIGHT) {
            scrollPacks(scrollY > 0 ? -1 : 1);
            return true;
        }
        return false;
    }

    private record FilterOption(@Nullable String namespace, Component name, ItemStack icon,
                                @Nullable Identifier logo, List<ItemStack> items) {
    }

    private class PackFilterButton extends Button {
        @Nullable
        private FilterOption option;

        private PackFilterButton(int x, int y) {
            super(x, y, 32, TAB_HEIGHT, Component.empty(), button -> {
                FilterOption selected = ((PackFilterButton) button).option;
                if (selected != null) {
                    select(selected);
                }
            }, DEFAULT_NARRATION);
        }

        private void setOption(@Nullable FilterOption option) {
            this.option = option;
            visible = active = option != null;
            setMessage(option == null ? Component.empty() : option.name());
            setTooltip(option == null ? null : Tooltip.create(option.name()));
        }

        @Override
        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (option == null) {
                return;
            }
            boolean selected = Objects.equals(option.namespace(), selections.get(currentTab));
            Identifier background = selected ? SELECTED_TAB_SPRITE : TAB_SPRITE;
            int backgroundWidth = selected ? 31 : 28;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, background, getX(), getY(), backgroundWidth, TAB_HEIGHT);
            if (option.logo() != null) {
                // UVs cover the full image, independently of its pixel dimensions.
                graphics.blit(RenderPipelines.GUI_TEXTURED, option.logo(), getX() + 8, getY() + 5, 0, 0, 16, 16, 16, 16);
            } else {
                graphics.renderItem(option.icon(), getX() + 8, getY() + 5);
            }
            if (isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard()) {
                graphics.renderOutline(getX() + 5, getY() + 3, 22, 20, 0xFFFFFFFF);
            }
        }
    }
}
