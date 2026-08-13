package com.tacz.guns.client.tooltip;

import com.mojang.blaze3d.platform.InputConstants;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.inventory.tooltip.AttachmentItemTooltip;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class ClientAttachmentItemTooltip implements ClientTooltipComponent {
    private static final Cache<Identifier, List<ItemStack>> CACHE = CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.SECONDS).build();
    private final Identifier attachmentId;
    private final List<Component> components = Lists.newArrayList();
    private final MutableComponent tips = Component.translatable("tooltip.tacz.attachment.yaw.shift");
    private final MutableComponent support = Component.translatable("tooltip.tacz.attachment.yaw.support");
    private @Nullable MutableComponent packInfo;
    private List<ItemStack> showGuns = Lists.newArrayList();
    private ItemStack attachment;

    public ClientAttachmentItemTooltip(AttachmentItemTooltip tooltip) {
        this.attachmentId = tooltip.getAttachmentId();
        this.attachment = tooltip.getAttachmentItem();
        this.addText(tooltip.getType());
        this.getShowGuns();
        this.addPackInfo();
    }

    private void addPackInfo() {
        PackInfo packInfoObject = ClientAssetsManager.INSTANCE.getPackInfo(attachmentId);
        if (packInfoObject != null) {
            packInfo = Component.translatable(packInfoObject.getName()).withStyle(style -> style.withColor(0x5555FF)).withStyle(style -> style.withItalic(true));
        }
    }

    private static List<ItemStack> getAllAllowGuns(List<ItemStack> output, Identifier attachmentId) {
        ItemStack attachment = AttachmentItemBuilder.create().setId(attachmentId).build();
        TimelessAPI.getAllCommonGunIndex().forEach(entry -> {
            Identifier gunId = entry.getKey();
            ItemStack gun = GunItemBuilder.create().setId(gunId).build();
            if (!(gun.getItem() instanceof IGun iGun)) {
                return;
            }
            if (iGun.allowAttachment(gun, attachment)) {
                output.add(gun);
            }
        });
        return output;
    }

    @Override
    public int getHeight(Font font) {
        if (!isShiftDown()) {
            return components.size() * 10 + 28;
        }
        return (showGuns.size() - 1) / 16 * 18 + 50 + components.size() * 10;
    }

    @Override
    public int getWidth(Font font) {
        int[] width = new int[]{0};
        if (packInfo != null) {
            width[0] = Math.max(width[0], font.width(packInfo) + 4);
        }
        components.forEach(c -> width[0] = Math.max(width[0], font.width(c)));
        if (!isShiftDown()) {
            return Math.max(width[0], font.width(tips) + 4);
        } else {
            width[0] = Math.max(width[0], font.width(support) + 4);
        }
        if (showGuns.size() > 15) {
            return Math.max(width[0], 260);
        }
        return Math.max(width[0], showGuns.size() * 16 + 4);
    }

    @Override
    public void renderText(GuiGraphics graphics, Font font, int pX, int pY) {
        int yOffset = pY;
        for (Component component : this.components) {
            graphics.drawString(font, component, pX, yOffset, 0xFFffaa00);
            yOffset += 10;
        }
        if (!isShiftDown()) {
            graphics.drawString(font, tips, pX, pY + 5 + this.components.size() * 10, 0xFF9e9e9e);
            yOffset += 10;
        } else {
            yOffset += (showGuns.size() - 1) / 16 * 18 + 32;
        }
        // 枪包名
        if (packInfo != null) {
            graphics.drawString(font, this.packInfo, pX, yOffset + 8, 0xFFffffff);
        }
    }

    @Override
    public void renderImage(Font font, int mouseX, int mouseY, int width, int height, GuiGraphics graphics) {
        if (!isShiftDown()) {
            return;
        }
        int minY = components.size() * 10 + 3;
        int maxX = getWidth(font);
        graphics.fill(mouseX, mouseY + minY, mouseX + maxX, mouseY + minY + 11, 0x8F00b0ff);
        graphics.drawString(font, support, mouseX + 2, mouseY + minY + 2, 0xFFe3f2fd);

        for (int i = 0; i < showGuns.size(); i++) {
            ItemStack stack = showGuns.get(i);
            int x = i % 16 * 16 + 2;
            int y = i / 16 * 18 + minY + 15;
            graphics.renderItem(stack, mouseX + x, mouseY + y);
        }
    }

    private void getShowGuns() {
        try {
            this.showGuns = CACHE.get(attachmentId, () -> getAllAllowGuns(Lists.newArrayList(), attachmentId));
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    private static boolean isShiftDown() {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), InputConstants.KEY_RSHIFT);
    }

    public static String rgbToHex(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private void addText(AttachmentType type) {
        TimelessAPI.getClientAttachmentIndex(attachmentId).ifPresent(index -> {
            AttachmentData data = index.getData();

            @Nullable String tooltipKey = index.getTooltipKey();
            if (tooltipKey != null) {
                String text = I18n.get(tooltipKey);
                String[] split = text.split("\n");
                Arrays.stream(split).forEach(s -> components.add(Component.literal(s).withStyle(style -> style.withColor(0xAAAAAA))));
            }

            if (attachment.getItem() instanceof IAttachment iAttachment) {
                TimelessAPI.getClientAttachmentIndex(attachmentId).ifPresent(attachmentIndex -> {
                    if (iAttachment.hasCustomLaserColor(attachment)) {
                        int color = iAttachment.getLaserColor(attachment);
                        components.add(Component.translatable("tooltip.tacz.attachment.laser.color", rgbToHex(color)).withStyle(Style.EMPTY.withColor(color)));
                    } else if (attachmentIndex.getLaserConfig() != null) {
                        int color = attachmentIndex.getLaserConfig().getDefaultColor();
                        components.add(Component.translatable("tooltip.tacz.attachment.laser.color", rgbToHex(color)).withStyle(Style.EMPTY.withColor(color)));
                    }
                });
            }

            if (type == AttachmentType.SCOPE) {
                float[] zoom = index.getZoom();
                if (zoom != null) {
                    String[] zoomText = new String[zoom.length];
                    for (int i = 0; i < zoom.length; i++) {
                        zoomText[i] = "x" + zoom[i];
                    }
                    String zoomJoinText = StringUtils.join(zoomText, ", ");
                    components.add(Component.translatable("tooltip.tacz.attachment.zoom", zoomJoinText).withStyle(style -> style.withColor(0xFFAA00)));
                }
            }

            if (type == AttachmentType.EXTENDED_MAG) {
                int magLevel = data.getExtendedMagLevel();
                if (magLevel == 1) {
                    components.add(Component.translatable("tooltip.tacz.attachment.extended_mag_level_1").withStyle(style -> style.withColor(0xAAAAAA)));
                } else if (magLevel == 2) {
                    components.add(Component.translatable("tooltip.tacz.attachment.extended_mag_level_2").withStyle(style -> style.withColor(0x5555FF)));
                } else if (magLevel == 3) {
                    components.add(Component.translatable("tooltip.tacz.attachment.extended_mag_level_3").withStyle(style -> style.withColor(0xFF55FF)));
                }
            }

            data.getModifier().forEach((key, value) -> {
                List<Component> result = value.getComponents();
                components.addAll(result);
            });
        });
    }
}
