package com.tacz.guns.mixin.client;

import com.tacz.guns.client.gui.components.CreativePackFilters;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {
    @Shadow
    private static CreativeModeTab selectedTab;
    @Shadow
    private float scrollOffs;
    @Unique
    private CreativePackFilters tacz$packFilters;

    /**
     * mixin 类继承了目标类，就<b>必须</b>给出一个能对上目标构造器的构型，否则编译器会生成
     * 隐式的无参构造器去调 {@code super()}，直接编译失败。
     *
     * <p>26.2 的 {@code AbstractContainerScreen} 构造器多两个尺寸形参（195×136），
     * 1.21.11 的只有 {@code (menu, inventory, title)}。</p>
     */
    protected CreativeModeInventoryScreenMixin(CreativeModeInventoryScreen.ItemPickerMenu menu,
                                               Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void tacz$initPackFilters(CallbackInfo ci) {
        if (tacz$packFilters == null) {
            tacz$packFilters = new CreativePackFilters((CreativeModeInventoryScreen) (Object) this, () -> scrollOffs = 0);
        }
        // 直接 this::addRenderableWidget 会导致编译器生成妙妙方法直接引用 CreativeModeInventoryScreenMixin 然后原地爆炸
        tacz$packFilters.init(widget -> addRenderableWidget(widget), selectedTab, leftPos, topPos);
    }

    @Inject(method = "selectTab", at = @At("TAIL"))
    private void tacz$selectPackFilterTab(CreativeModeTab tab, CallbackInfo ci) {
        if (tacz$packFilters != null) {
            tacz$packFilters.updateTab(selectedTab, true);
        }
    }

    @Inject(method = "refreshCurrentTabContents", at = @At("TAIL"))
    private void tacz$refreshPackFilterItems(Collection<ItemStack> items, CallbackInfo ci) {
        if (tacz$packFilters != null) {
            tacz$packFilters.updateTab(selectedTab, true);
        }
    }

    // 26.2 的屏幕被拆成 extract/render 两段，这里原本注入 extractRenderState；
    // 1.21.11 的屏幕仍是单段 AbstractContainerScreen#render(GuiGraphics,int,int,float)。
    @Inject(method = "render", at = @At("HEAD"))
    private void tacz$updatePackFilters(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (tacz$packFilters != null) {
            tacz$packFilters.updateTab(selectedTab, false);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void tacz$scrollPackFilters(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        if (tacz$packFilters != null && tacz$packFilters.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            cir.setReturnValue(true);
        }
    }
}
