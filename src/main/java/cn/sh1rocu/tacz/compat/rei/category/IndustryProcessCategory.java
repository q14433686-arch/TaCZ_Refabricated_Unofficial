package cn.sh1rocu.tacz.compat.rei.category;

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

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight REI category used when Create Fly's published 26.2 build omits
 * its own REI source set. It intentionally shows a readable process graph
 * rather than trying to reproduce Create's animated machine renderers.
 *
 * <p>The grid is deliberately wide enough for the large Basin recipes and the
 * serial gun-assembly bill of materials. In particular, a sequenced-assembly
 * display lists all stations for planning purposes, but its footer states the
 * physical rule: only the first workpiece ever sits on the Depot/belt.</p>
 */
public final class IndustryProcessCategory implements DisplayCategory<IndustryProcessDisplay> {
    private static final int INPUT_COLUMNS = 5;
    private static final int OUTPUT_COLUMNS = 2;
    private static final int SLOT_SPACING = 19;
    private static final int INPUT_X = 8;
    private static final int INPUT_Y = 10;
    private static final int ARROW_X = 108;
    private static final int OUTPUT_X = 137;

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
            int x = bounds.x + INPUT_X + (index % INPUT_COLUMNS) * SLOT_SPACING;
            int y = bounds.y + INPUT_Y + (index / INPUT_COLUMNS) * SLOT_SPACING;
            widgets.add(Widgets.createSlot(new Point(x, y)).entries(inputs.get(index)).markInput());
            if (display.getProcess().isInputReusable(index)) {
                final int markerX = x + 11;
                final int markerY = y - 4;
                widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) ->
                        graphics.text(Minecraft.getInstance().font, "∞", markerX, markerY, 0xFF55FFFF, false)));
            }
        }

        List<EntryIngredient> outputs = display.getOutputEntries();
        for (int index = 0; index < outputs.size(); index++) {
            int x = bounds.x + OUTPUT_X + (index % OUTPUT_COLUMNS) * SLOT_SPACING;
            int y = bounds.y + 19 + (index / OUTPUT_COLUMNS) * SLOT_SPACING;
            widgets.add(Widgets.createSlot(new Point(x, y)).entries(outputs.get(index)).markOutput());
        }

        widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) -> {
            var font = Minecraft.getInstance().font;
            String arrow = display.getProcess().isSequencedAssembly() ? "⇒" : "→";
            graphics.text(font, arrow, bounds.x + ARROW_X, bounds.y + 28, 0xFFFFFFFF, false);

            int ticks = display.getProcess().getProcessingTime();
            if (ticks > 0) {
                graphics.text(font, Component.translatable("rei.tacz.industry.processing_time", ticks),
                        bounds.x + ARROW_X - 12, bounds.y + 48, 0xFFAAAAAA, false);
            }

            Component footer = null;
            if (display.getProcess().isSequencedAssembly()) {
                footer = Component.translatable("rei.tacz.industry.sequenced_assembly.one_workpiece");
            } else if (machine == IndustryProcessMachine.DEPLOYING) {
                footer = Component.translatable("rei.tacz.industry.deploying.one_workpiece");
            } else if (machine == IndustryProcessMachine.COMPACTING) {
                footer = Component.translatable("rei.tacz.industry.compacting.basin");
            }
            if (footer != null) {
                graphics.text(font, footer, bounds.x + 8, bounds.y + 70, 0xFFAAAAAA, false);
            }
        }));
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
        // Three rows cover the current 15-input 12G loading line. The footer
        // below them makes Depot-vs-Basin semantics visible in every recipe.
        return 84;
    }

    @Override
    public int getDisplayWidth(IndustryProcessDisplay display) {
        return 184;
    }
}
