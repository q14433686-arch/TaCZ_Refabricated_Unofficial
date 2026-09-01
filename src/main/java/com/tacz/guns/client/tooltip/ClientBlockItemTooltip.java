package com.tacz.guns.client.tooltip;

import com.google.common.collect.Lists;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.inventory.tooltip.BlockItemTooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class ClientBlockItemTooltip implements ClientTooltipComponent {
    private final Identifier blockId;
    private final List<Component> components = Lists.newArrayList();
    private @Nullable MutableComponent packInfo;

    public ClientBlockItemTooltip(BlockItemTooltip tooltip) {
        this.blockId = tooltip.getBlockId();
        this.addText();
        this.addPackInfo();
    }

    private void addPackInfo() {
        PackInfo packInfoObject = ClientAssetsManager.INSTANCE.getPackInfo(blockId);
        if (packInfoObject != null) {
            packInfo = Component.translatable(packInfoObject.getName()).withStyle(style -> style.withColor(0x5555FF)).withStyle(style -> style.withItalic(true));
        }
    }


    @Override
    public int getHeight(Font font) {
        return components.size() * 10 + (packInfo != null ? 16 : 0);
    }

    @Override
    public int getWidth(Font font) {
        int[] width = new int[]{0};
        if (packInfo != null) {
            width[0] = Math.max(width[0], font.width(packInfo) + 4);
        }
        components.forEach(c -> width[0] = Math.max(width[0], font.width(c)));
        return width[0];
    }

    @Override
    public void extractText(GuiGraphicsExtractor graphics, Font font, int pX, int pY) {
        int yOffset = pY;
        for (Component component : this.components) {
            graphics.text(font, component, pX, yOffset, 0xFFffaa00);
            yOffset += 10;
        }
        // 枪包名
        if (packInfo != null) {
            graphics.text(font, this.packInfo, pX, yOffset + 6, 0xFFffffff);
        }
    }

    @Override
    public void extractImage(Font font, int mouseX, int mouseY, int width, int height, GuiGraphicsExtractor graphics) {
    }

    private void addText() {
        TimelessAPI.getClientBlockIndex(blockId).ifPresent(index -> {
            @Nullable String tooltipKey = index.getTooltipKey();
            if (tooltipKey != null) {
                // 纯查表而非 I18n.get —— 与 PapiManager 的 Format error 修复（ec51f55）同病：
                // I18n.get 是【格式化】接口，枪包把含 '%' 的显示串内联进 tooltip key 时
                // （MK5HD 一族），String.format 会吃掉 '%x' 或直接抛
                // IllegalFormatException 变成 "Format error: ..."。下游随后 split("\\n")
                // 逐行 literal，从来不需要格式化。（05170 的 03a807e 指出本仓这两处漏网。）
                String text = net.minecraft.locale.Language.getInstance().getOrDefault(tooltipKey);
                String[] split = text.split("\n");
                Arrays.stream(split).forEach(s -> components.add(Component.literal(s).withStyle(style -> style.withColor(0xAAAAAA))));
            }
        });
    }
}
