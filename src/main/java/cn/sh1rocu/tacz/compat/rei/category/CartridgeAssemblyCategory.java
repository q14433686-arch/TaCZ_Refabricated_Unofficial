package cn.sh1rocu.tacz.compat.rei.category;

import cn.sh1rocu.tacz.compat.rei.REIClientPlugin;
import cn.sh1rocu.tacz.compat.rei.display.CartridgeAssemblyDisplay;
import com.tacz.guns.init.ModItems;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Explicit four-slot view matching the dedicated cartridge assembler GUI. */
public final class CartridgeAssemblyCategory implements DisplayCategory<CartridgeAssemblyDisplay> {
    private final Renderer icon = EntryStacks.of(ModItems.CARTRIDGE_ASSEMBLY_MACHINE.getDefaultInstance());

    @Override
    public List<Widget> setupDisplay(CartridgeAssemblyDisplay display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        var inputs = display.getInputEntries();
        widgets.add(Widgets.createSlot(new Point(bounds.x + 16, bounds.y + 10)).entries(inputs.get(0)).markInput());
        widgets.add(Widgets.createSlot(new Point(bounds.x + 52, bounds.y + 10)).entries(inputs.get(1)).markInput());
        widgets.add(Widgets.createSlot(new Point(bounds.x + 16, bounds.y + 36)).entries(inputs.get(2)).markInput());
        widgets.add(Widgets.createSlot(new Point(bounds.x + 52, bounds.y + 36)).entries(inputs.get(3)).markInput());
        widgets.add(Widgets.createSlot(new Point(bounds.x + 130, bounds.y + 23))
                .entries(display.getOutputEntries().getFirst()).markOutput());
        widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) -> {
            var font = Minecraft.getInstance().font;
            graphics.text(font, "→", bounds.x + 101, bounds.y + 30, 0xFFFFFFFF, false);
            graphics.text(font, Component.translatable("rei.tacz.cartridge_assembly.explicit_slots"),
                    bounds.x + 8, bounds.y + 61, 0xFFAAAAAA, false);
        }));
        return widgets;
    }

    @Override
    public CategoryIdentifier<? extends CartridgeAssemblyDisplay> getCategoryIdentifier() {
        return REIClientPlugin.CARTRIDGE_ASSEMBLY;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.tacz.cartridge_assembly_machine");
    }

    @Override
    public Renderer getIcon() {
        return icon;
    }

    @Override
    public int getDisplayHeight() {
        return 78;
    }

    @Override
    public int getDisplayWidth(CartridgeAssemblyDisplay display) {
        return 170;
    }
}
