package com.tacz.guns.client.industry.magazine;

import com.mojang.blaze3d.platform.Window;
import com.tacz.guns.industry.magazine.IMagazine;
import com.tacz.guns.industry.magazine.PhysicalMagazineService;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Client-only long-R wheel for selecting a real physical reload source.
 *
 * <p>The wheel contains only compatible, more-loaded carriers discovered from
 * the player's actual inventory. It never mutates inventory locally: releasing
 * R returns the chosen slot to {@link com.tacz.guns.client.gameplay.LocalPlayerReload},
 * and the server revalidates that exact stack at both reservation and feed.</p>
 */
@Environment(EnvType.CLIENT)
public final class ReloadWheelOverlay {
    private static final int MAX_VISIBLE_CHOICES = 8;
    private static final int HOLD_RADIUS = 22;
    private static final int WHEEL_RADIUS = 58;

    private static List<PhysicalMagazineService.ReloadWheelCandidate> choices = List.of();
    private static int selectedIndex = -1;
    private static int hiddenChoiceCount;
    private static boolean open;

    private ReloadWheelOverlay() {
    }

    public static boolean open(LocalPlayer player, ItemStack gun) {
        List<PhysicalMagazineService.ReloadWheelCandidate> available =
                PhysicalMagazineService.getReloadWheelCandidates(player, gun);
        if (available.isEmpty()) {
            cancel();
            return false;
        }
        hiddenChoiceCount = Math.max(0, available.size() - MAX_VISIBLE_CHOICES);
        choices = List.copyOf(available.subList(0, Math.min(MAX_VISIBLE_CHOICES, available.size())));
        selectedIndex = -1;
        open = true;
        return true;
    }

    public static boolean isOpen() {
        return open;
    }

    /** Close the overlay and return the exact selected inventory slot, or -1 for normal reload policy. */
    public static int closeAndGetSelectedSlot() {
        int slot = selectedIndex >= 0 && selectedIndex < choices.size() ? choices.get(selectedIndex).slot() : -1;
        cancel();
        return slot;
    }

    public static void cancel() {
        choices = List.of();
        selectedIndex = -1;
        hiddenChoiceCount = 0;
        open = false;
    }

    public static void render(GuiGraphicsExtractor graphics, Window window) {
        if (!open || choices.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int width = window.getGuiScaledWidth();
        int height = window.getGuiScaledHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        updateSelection(minecraft, window, centerX, centerY);

        // Center hub and title intentionally use only GUI primitives: no new
        // art asset is implied by this selection control.
        graphics.fill(centerX - 18, centerY - 18, centerX + 18, centerY + 18, 0xC0000000);
        Component title = Component.translatable("gui.tacz.reload_wheel.title");
        var titleText = title.getVisualOrderText();
        graphics.text(minecraft.font, titleText, centerX - minecraft.font.width(titleText) / 2, centerY - 5, 0xFFFFFFFF, false);

        int size = choices.size();
        for (int index = 0; index < size; index++) {
            double angle = -Math.PI / 2.0 + (Math.PI * 2.0 * index / size);
            int x = centerX + (int) Math.round(Math.cos(angle) * WHEEL_RADIUS);
            int y = centerY + (int) Math.round(Math.sin(angle) * WHEEL_RADIUS);
            boolean selected = index == selectedIndex;
            int color = selected ? 0xE068A9D8 : 0xC0202020;
            graphics.fill(x - 28, y - 11, x + 28, y + 11, color);
            PhysicalMagazineService.ReloadWheelCandidate choice = choices.get(index);
            String label = label(choice.preview(), choice.rounds());
            var text = Component.literal(label).getVisualOrderText();
            graphics.text(minecraft.font, text, x - minecraft.font.width(text) / 2, y - 4,
                    selected ? 0xFFFFFFFF : 0xFFD7D7D7, false);
        }
        if (hiddenChoiceCount > 0) {
            Component overflow = Component.translatable("gui.tacz.reload_wheel.more", hiddenChoiceCount);
            var text = overflow.getVisualOrderText();
            graphics.text(minecraft.font, text, centerX - minecraft.font.width(text) / 2, centerY + 26, 0xFFFFD37A, false);
        }
    }

    private static String label(ItemStack stack, int rounds) {
        int capacity = stack.getItem() instanceof IMagazine magazine ? magazine.getCapacity(stack) : 0;
        return stack.getHoverName().getString() + " " + rounds + "/" + capacity;
    }

    private static void updateSelection(Minecraft minecraft, Window window, int centerX, int centerY) {
        double guiX = minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / Math.max(1.0, window.getWidth());
        double guiY = minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / Math.max(1.0, window.getHeight());
        double dx = guiX - centerX;
        double dy = guiY - centerY;
        if (dx * dx + dy * dy < HOLD_RADIUS * HOLD_RADIUS) {
            selectedIndex = -1;
            return;
        }
        double angle = Math.atan2(dy, dx) + Math.PI / 2.0;
        if (angle < 0) {
            angle += Math.PI * 2.0;
        }
        double step = Math.PI * 2.0 / choices.size();
        selectedIndex = Math.floorMod((int) Math.floor((angle + step / 2.0) / step), choices.size());
    }
}
