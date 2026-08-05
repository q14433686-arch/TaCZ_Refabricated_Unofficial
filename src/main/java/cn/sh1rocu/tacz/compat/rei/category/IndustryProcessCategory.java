package cn.sh1rocu.tacz.compat.rei.category;

import cn.sh1rocu.tacz.compat.rei.REIClientPlugin;
import cn.sh1rocu.tacz.compat.rei.display.IndustryProcessDisplay;
import com.tacz.guns.industry.recipe.IndustryProcessMachine;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight REI category used when Create Fly's published 26.2 build omits
 * its own REI source set. It intentionally shows a readable process graph
 * rather than trying to reproduce Create's animated machine renderers.
 */
public final class IndustryProcessCategory implements DisplayCategory<IndustryProcessDisplay> {
    private final IndustryProcessMachine machine;
    private final CategoryIdentifier<IndustryProcessDisplay> id;
    private final Renderer icon;

    public IndustryProcessCategory(IndustryProcessMachine machine,
                                   CategoryIdentifier<IndustryProcessDisplay> id) {
        this.machine = machine;
        this.id = id;
        this.icon = EntryStacks.of(machine.workstationStack());
    }

    @Override
    public List<Widget> setupDisplay(IndustryProcessDisplay display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));

        List<EntryIngredient> inputs = display.getInputEntries();
        for (int index = 0; index < inputs.size(); index++) {
            int x = bounds.x + 8 + (index % 3) * 19;
            int y = bounds.y + 10 + (index / 3) * 19;
            widgets.add(Widgets.createSlot(new Point(x, y)).entries(inputs.get(index)).markInput());
        }

        widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) -> {
            var font = Minecraft.getInstance().font;
            graphics.text(font, "→", bounds.x + 75, bounds.y + 27, 0xFFFFFFFF, false);
            int ticks = display.getProcess().getProcessingTime();
            if (ticks > 0) {
                graphics.text(font, Component.translatable("rei.tacz.industry.processing_time", ticks),
                        bounds.x + 61, bounds.y + 48, 0xFFAAAAAA, false);
            }
            if (display.getProcess().keepsHeldItem() && inputs.size() > 1) {
                // In deploying recipes the second input is the Deployer-held
                // die/template. Keep-held is gameplay-critical, not cosmetic.
                graphics.text(font, "∞", bounds.x + 28, bounds.y + 4, 0xFF55FFFF, false);
            }
        }));

        List<EntryIngredient> outputs = display.getOutputEntries();
        for (int index = 0; index < outputs.size(); index++) {
            int x = bounds.x + 112 + (index % 2) * 19;
            int y = bounds.y + 19 + (index / 2) * 19;
            widgets.add(Widgets.createSlot(new Point(x, y)).entries(outputs.get(index)).markOutput());
        }
        return widgets;
    }

    @Override
    public CategoryIdentifier<? extends IndustryProcessDisplay> getCategoryIdentifier() {
        return id;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(machine.translationKey());
    }

    @Override
    public Renderer getIcon() {
        return icon;
    }

    @Override
    public int getDisplayHeight() {
        return 68;
    }

    @Override
    public int getDisplayWidth(IndustryProcessDisplay display) {
        return 160;
    }
}
