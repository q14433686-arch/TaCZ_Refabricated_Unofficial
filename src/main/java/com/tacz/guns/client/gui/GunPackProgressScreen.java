package com.tacz.guns.client.gui;

import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProgressListener;

import javax.annotation.Nullable;

/**
 * 26.2: GUI system reworked - GuiGraphics → GuiGraphicsExtractor, render → extractRenderState
 * Implemented with 26.2 GUI API
 */
public class GunPackProgressScreen extends Screen implements ProgressListener {
    private @Nullable Component header;
    private @Nullable Component stage;
    private int progress;
    private boolean stop;

    public GunPackProgressScreen() {
        super(GameNarrator.NO_TITLE);
    }

    @Override
    protected void init() {
        Button button = Button.builder(
                Component.translatable("gui.tacz.client_gun_pack_downloader.background_download"), b -> this.stop()
        ).bounds((width - 200) / 2, 120, 200, 20).build();
        this.addRenderableWidget(button);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        if (this.stop) {
            this.minecraft.gui.setScreen(null);
        } else {
            this.extractBackground(gui, mouseX, mouseY, partialTick);
            // 【本轮修复：枪包加载进度界面的文字不显示】
            // 16777215 = 0x00FFFFFF，alpha 为 0；26.2 的 GuiGraphicsExtractor#text 开头即
            //     if (ARGB.alpha(color) == 0) return;
            // 直接整段跳过绘制（见 docs/PORTING_NOTES.md §1）。1.21.1 无此短路故上游正常。
            if (this.header != null) {
                gui.centeredText(this.font, this.header, this.width / 2, 70, 0xFFFFFFFF);
            }
            if (this.stage != null && this.progress > 0) {
                Component text = this.stage.copy().append(" " + this.progress + "%");
                gui.centeredText(this.font, text, this.width / 2, 90, 0xFFFFFFFF);
            }
            super.extractRenderState(gui, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void progressStartNoAbort(Component component) {
        this.progressStart(component);
    }

    @Override
    public void progressStart(Component header) {
        this.header = Component.translatable("gui.tacz.client_gun_pack_downloader.downloading");
    }

    @Override
    public void progressStage(Component component) {
        this.stage = component;
        this.progressStagePercentage(0);
    }

    @Override
    public void progressStagePercentage(int progress) {
        this.progress = progress;
    }

    @Override
    public void stop() {
        this.stop = true;
    }
}
