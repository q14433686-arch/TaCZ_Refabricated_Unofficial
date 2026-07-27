package com.tacz.guns.client.gui.components.refit;

import cn.sh1rocu.tacz.util.forge.ForgeSlider;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.util.LaserColorUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class HSVSliderGroup {
    private final Inventory inventory;
    private final int gunItemIndex;

    private final AttachmentType type;

    private final LaserColorSlider hueSlider;
    private final LaserColorSlider saturationSlider;

    public HSVSliderGroup(int x, int y, int width, int height, Inventory inventory, int gunItemIndex, @NotNull AttachmentType type) {
        this.inventory = inventory;
        this.gunItemIndex = gunItemIndex;
        this.type = type;

        int color = getColor(type);
        float[] hsb = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);

        hueSlider = new LaserColorSlider(x, y, width, height, this, hsb[0]);
        saturationSlider = new LaserColorSlider(x, y + 2 + height, width, height, this, hsb[1]);
    }

    public LaserColorSlider getHueSlider() {
        return hueSlider;
    }

    public LaserColorSlider getSaturationSlider() {
        return saturationSlider;
    }


    public void apply() {
        // 需要检查的实现
        // 这里写往客户端写nbt其实是脏写，只为了确保能实时预览染色效果
        // 需要在合适的时机向服务器发包通知改动
        // 不在此直接向服务器发包是因为这个组件在滑动时会被非常频繁的调用，不希望频繁向服务器发包
        ItemStack gun = inventory.getItem(gunItemIndex);
        if (gun.getItem() instanceof IGun iGun) {
            int rgb_new = Color.HSBtoRGB((float) hueSlider.getValue(), (float) saturationSlider.getValue(), 1f);

            if (type == AttachmentType.NONE) {
                iGun.setLaserColor(gun, rgb_new);
                return;
            }

            // 【必须改「枪上那份配件 NBT」，不能改 getAttachment() 返回的 ItemStack】
            //
            // getAttachment(gun, type) 内部是
            //     ItemNbtUtils.loadItemStack(nbt.getCompoundOrEmpty(key))
            // —— 每次调用都用 Codec【反序列化出一个全新的 ItemStack】，
            // 与枪上真正存着的那份数据没有任何引用关系。
            //
            // 原先这里写的是
            //     ItemStack laser = iGun.getAttachment(gun, type);
            //     iAttachment.setLaserColor(laser, rgb_new);
            // 等于把颜色写进了一个临时副本，方法返回后即被丢弃。
            // 后果是【客户端本地这份也没改成】，于是：
            //   1. 拖动滑块时镭射颜色毫无变化（本方法本来就是为了实时预览而"脏写"客户端 NBT，
            //      写不进去，预览自然不动）；
            //   2. 界面上任何一次重新读取 NBT（点其他按钮触发重建、或关闭界面）
            //      都会让显示回到默认色；
            //   3. 更隐蔽的是，退出界面时发给服务端的 ClientMessageLaserColor
            //      是遍历 hasCustomLaserColor(attachment) 来收集要同步的颜色的，
            //      而这份 NBT 压根没被写过 -> colorMap 为空 -> 服务端什么也不会改。
            //      所以上一轮只修服务端 handle 是不够的，两侧是同一个 bug。
            //
            // 上游 1.21.1 的写法（逐行对照）：
            //     CompoundTag tag = iGun.getAttachmentTag(gun, type);
            //     if (tag != null) { AttachmentItemDataAccessor.setLaserColorToTag(tag, rgb_new); }
            //     iGun.setAttachmentTag(gun, type, tag);
            // getAttachmentTag/setAttachmentTag 操作的是枪 NBT 里
            // 「配件 ItemStack 的 components.custom_data」那一层，改动会真正生效。
            CompoundTag tag = iGun.getAttachmentTag(gun, type);
            if (tag != null) {
                AttachmentItemDataAccessor.setLaserColorToTag(tag, rgb_new);
                iGun.setAttachmentTag(gun, type, tag);
            }
        }
    }


    private int getColor(AttachmentType type) {
        if (inventory == null) {
            return 0XFF0000;
        }
        ItemStack gun = inventory.getItem(gunItemIndex);

        if (gun.getItem() instanceof IGun iGun) {
            if (type == AttachmentType.NONE) {
                return LaserColorUtil.getLaserColor(gun);
            } else {
                ItemStack attachment = iGun.getAttachment(gun, type);
                return LaserColorUtil.getLaserColor(attachment);
            }
        }

        return 0XFF0000;
    }

    public static class LaserColorSlider extends ForgeSlider {
        private final HSVSliderGroup parent;

        public LaserColorSlider(int x, int y, int width, int height, HSVSliderGroup parent, double current) {
            super(x, y, width, height, Component.empty(), Component.empty(), 0, 1, current, 0.01, 0, true);
            this.parent = parent;
        }

        @Override
        protected void applyValue() {
            parent.apply();
        }
    }
}
