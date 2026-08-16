package cn.sh1rocu.tacz.compat.rei.category;

import cn.sh1rocu.tacz.compat.rei.REIClientPlugin;
import cn.sh1rocu.tacz.compat.rei.display.AmmoQueryDisplay;
import com.tacz.guns.compat.recipeviewer.AmmoQueryEntry;
import com.tacz.guns.init.ModCreativeTabs;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** REI counterpart of the built-in ammunition compatibility query. */
public final class AmmoQueryCategory implements DisplayCategory<AmmoQueryDisplay> {
    private static final Component TITLE = Component.translatable("jei.tacz.ammo_query.title");

    private final Renderer icon = EntryStacks.of(ModCreativeTabs.AMMO_TAB.getIconItem());

    @Override
    public List<Widget> setupDisplay(AmmoQueryDisplay display, Rectangle bounds) {
        List<EntryIngredient> inputs = display.getInputEntries();
        List<EntryIngredient> outputs = display.getOutputEntries();
        List<Widget> widgets = new ArrayList<>();

        int startX = bounds.x + 5;
        int startY = bounds.y + 5;
        widgets.add(Widgets.createRecipeBase(bounds));
        widgets.add(Widgets.createSlot(new Point(startX + 72, startY))
                .entries(outputs.getFirst())
                .markOutput());

        int xOffset = 0;
        int yOffset = 20;
        int visibleCount = Math.min(inputs.size(), AmmoQueryEntry.MAX_GUN_SHOW_COUNT);
        for (int i = 0; i < visibleCount; i++) {
            int column = i % 9;
            int row = i / 9;
            xOffset = column * 18;
            yOffset = 20 + row * 18;
            widgets.add(Widgets.createSlot(new Point(startX + xOffset, startY + yOffset))
                    .entries(inputs.get(i))
                    .markInput());
        }

        if (inputs.size() > AmmoQueryEntry.MAX_GUN_SHOW_COUNT) {
            Font font = Minecraft.getInstance().font;
            widgets.add(Widgets.createDrawableWidget((guiGraphics, mouseX, mouseY, delta) ->
                    guiGraphics.drawString(font, Component.translatable("jei.tacz.ammo_query.more"),
                            startX + 128, startY + 134, 0x555555, false)));
            widgets.add(Widgets.createSlot(new Point(startX + xOffset + 18, startY + yOffset))
                    .entries(inputs.getLast())
                    .markInput());
        }

        return widgets;
    }

    @Override
    public CategoryIdentifier<? extends AmmoQueryDisplay> getCategoryIdentifier() {
        return REIClientPlugin.AMMO_QUERY;
    }

    @Override
    public Component getTitle() {
        return TITLE;
    }

    @Override
    public Renderer getIcon() {
        return icon;
    }

    @Override
    public int getDisplayHeight() {
        return 145;
    }

    @Override
    public int getDisplayWidth(AmmoQueryDisplay display) {
        return 170;
    }
}
