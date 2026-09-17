package com.tacz.guns.client.gui.compat;

import com.mojang.blaze3d.Blaze3D;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;

public class ClothConfigScreen extends Screen {
    public static final String CLOTH_CONFIG_URL = "https://www.curseforge.com/minecraft/mc-mods/cloth-config";
    private final Screen lastScreen;
    private MultiLineLabel message = MultiLineLabel.EMPTY;

    protected ClothConfigScreen(Screen lastScreen) {
        super(Component.literal("Cloth Config API"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int posX = (this.width - 200) / 2;
        int posY = this.height / 2;
        this.message = MultiLineLabel.create(this.font, Component.translatable("gui.tacz.cloth_config_warning.tips"), 300);
        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.tacz.cloth_config_warning.download"), b -> openUrl(CLOTH_CONFIG_URL))
                        .bounds(posX, posY - 15, 200, 20).build()
        );
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_BACK, b -> this.minecraft.setScreenAndShow(this.lastScreen))
                        .bounds(posX, posY + 50, 200, 20).build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int pMouseX, int pMouseY, float pPartialTick) {
        this.extractBackground(gui, pMouseX, pMouseY, pPartialTick);
        int centerX = this.width / 2;
        int centerY = this.height / 4 - 20;
        int lineY = centerY;
        for (var line : this.font.split(Component.translatable("gui.tacz.cloth_config_warning.tips"), 300)) {
            gui.centeredText(this.font, line, centerX, lineY, 0xFFFFFFFF);
            lineY += 9;
        }
        super.extractRenderState(gui, pMouseX, pMouseY, pPartialTick);
    }

    private void openUrl(String url) {
        if (StringUtils.isNotBlank(url) && minecraft != null) {
            // 26.3: ConfirmLinkScreen 只收 URI（不再有 String 重载），
            // 开链接也从 Util.OS#openUri 移到 Blaze3D#openUri
            // （对齐 vanilla ConfirmLinkScreen#confirmLinkNow）。
            // 这里的 URL 是硬编码常量，但仍走 parseAndValidateUntrustedUri ——
            // 它会校验协议白名单（http/https），解析失败就不弹窗，而不是抛到调用栈上。
            final URI uri;
            try {
                uri = Util.parseAndValidateUntrustedUri(url);
            } catch (URISyntaxException e) {
                return;
            }
            minecraft.setScreenAndShow(new ConfirmLinkScreen(yes -> {
                if (yes) {
                    Blaze3D.openUri(uri);
                }
                minecraft.setScreenAndShow(this);
            }, uri, true));
        }
    }
}
