package cn.sh1rocu.tacz.compat.rei.category;

import cn.sh1rocu.tacz.compat.rei.REIClientPlugin;
import cn.sh1rocu.tacz.compat.rei.display.GunFeedReferenceDisplay;
import com.tacz.guns.industry.magazine.GunFeedDefinition;
import com.tacz.guns.industry.magazine.GunFeedReferenceEntry;
import com.tacz.guns.init.ModItems;
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

/** REI counterpart to JEI's read-only gun/ammunition/carrier reference page. */
public final class GunFeedReferenceCategory implements DisplayCategory<GunFeedReferenceDisplay> {
    private static final int WIDTH = 206;
    private static final int HEIGHT = 83;
    private final Renderer icon = EntryStacks.of(ModItems.MAGAZINE.getDefaultInstance());

    @Override
    public List<Widget> setupDisplay(GunFeedReferenceDisplay display, Rectangle bounds) {
        GunFeedReferenceEntry entry = display.getEntry();
        List<EntryIngredient> inputs = display.getInputEntries();
        List<Widget> widgets = new ArrayList<>();
        int startX = bounds.x;
        int startY = bounds.y;

        widgets.add(Widgets.createRecipeBase(bounds));
        widgets.add(Widgets.createSlot(new Point(startX + 8, startY + 23)).entries(inputs.get(0)).markInput());
        if (entry.hasDeclaredPhysicalCarrier() && inputs.size() > 1) {
            widgets.add(Widgets.createSlot(new Point(startX + 80, startY + 23)).entries(inputs.get(1)).markInput());
        }
        widgets.add(Widgets.createSlot(new Point(startX + 176, startY + 23))
                .entry(EntryStacks.of(entry.getGunStack())).markOutput());

        widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) ->
                drawText(graphics, startX, startY, entry)));
        return widgets;
    }

    private static void drawText(me.shedaniel.rei.api.client.gui.compat.GuiGraphics graphics,
                                 int x, int y, GunFeedReferenceEntry entry) {
        Font font = Minecraft.getInstance().font;
        graphics.text(font, Component.translatable("jei.tacz.gun_feed.reference_only"), x + 4, y + 2, 0xFFAA5555, false);
        graphics.text(font, Component.translatable("jei.tacz.gun_feed.ammo"), x + 8, y + 14, 0xFFAAAAAA, false);
        graphics.text(font, Component.translatable("jei.tacz.gun_feed.carrier"), x + 76, y + 14, 0xFFAAAAAA, false);
        graphics.text(font, Component.translatable("jei.tacz.gun_feed.gun"), x + 176, y + 14, 0xFFAAAAAA, false);
        graphics.text(font, "→", x + 145, y + 27, 0xFFFFFFFF, false);

        GunFeedDefinition definition = entry.getFeedDefinition();
        if (definition == null) {
            graphics.text(font, Component.translatable("jei.tacz.gun_feed.no_carrier"), x + 4, y + 51, 0xFFAAAAAA, false);
            return;
        }

        Component mechanism = Component.translatable(
                "jei.tacz.gun_feed.mechanism." + definition.getMechanism().serializedName());
        graphics.text(font, Component.translatable("jei.tacz.gun_feed.mechanism", mechanism,
                definition.getMagazineCapacity()), x + 4, y + 51, 0xFFAAAAAA, false);
        if (entry.hasFixedInternalFeed()) {
            graphics.text(font, Component.translatable("jei.tacz.gun_feed.fixed_feed"), x + 4, y + 63, 0xFFAAAAAA, false);
            return;
        }
        graphics.text(font, Component.translatable("jei.tacz.gun_feed.family", definition.getMagazineFamily()),
                x + 4, y + 63, 0xFFAAAAAA, false);
        if (definition.getFeedStandardId() != null) {
            graphics.text(font, Component.translatable("jei.tacz.gun_feed.standard", definition.getFeedStandardId()),
                    x + 4, y + 74, 0xFF777777, false);
        }
    }

    @Override
    public CategoryIdentifier<? extends GunFeedReferenceDisplay> getCategoryIdentifier() {
        return REIClientPlugin.GUN_FEED_REFERENCE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.tacz.gun_feed.title");
    }

    @Override
    public Renderer getIcon() {
        return icon;
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public int getDisplayWidth(GunFeedReferenceDisplay display) {
        return WIDTH;
    }
}
