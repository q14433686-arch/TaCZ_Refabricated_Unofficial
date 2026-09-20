package com.tacz.guns.compat.cloth.widget;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.Blaze3D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class OpenGunPackDirEntry extends AbstractConfigListEntry<Boolean> {
    private final Button button = Button.builder(Component.translatable("config.tacz.open_gunpack_folder"), button -> {
        // 26.3: Util.OS 不再负责开链接（openUri 从 OS 枚举上移除），
        // 改由 Blaze3D.openUri 统一处理（对齐 vanilla ConfirmLinkScreen 的做法）。
        Blaze3D.openUri(FabricLoader.getInstance().getGameDir().resolve("tacz").toUri());
        button.setFocused(false);
    }).bounds(0, 0, 150, 20).build();

    public OpenGunPackDirEntry(Component name) {
        super(name, true);
    }

    @Override
    @NotNull
    public List<? extends GuiEventListener> children() {
        return List.of(button);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(button);
    }

    @Override
    public Boolean getValue() {
        return true;
    }

    @Override
    public Optional<Boolean> getDefaultValue() {
        return Optional.of(true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
        button.setX(x + entryWidth - 150);
        button.setY(y);
        button.extractRenderState(graphics, mouseX, mouseY, delta);
        super.extractRenderState(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
    }
}
