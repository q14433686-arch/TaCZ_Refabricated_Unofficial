package com.tacz.guns.client.input;

import cn.sh1rocu.tacz.api.event.InputEvent;
import cn.sh1rocu.tacz.api.event.PlayerTickEvent;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.client.KeyConfig;
import com.tacz.guns.client.industry.magazine.ReloadWheelOverlay;
import com.tacz.guns.industry.magazine.PhysicalMagazineService;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

@Environment(EnvType.CLIENT)
public class ReloadKey {
    public static final KeyMapping RELOAD_KEY = new KeyMapping("key.tacz.reload.desc",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            TaCZKeyCategory.TACZ);

    private static final int WHEEL_HOLD_TICKS = 8;
    private static boolean pendingPhysicalReload;
    private static boolean wheelOpened;
    private static int heldTicks;

    public static void onReloadPress(InputEvent.Key event) {
        if (!isInGame() || !RELOAD_KEY.matches(InputConstants.Type.KEYSYM.getOrCreate(event.getKey()))) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) {
            cancelWheel();
            return;
        }
        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (!(player.getMainHandItem().getItem() instanceof IGun iGun) || iGun.useInventoryAmmo(player.getMainHandItem())) {
                return;
            }
            // Sneak+R remains the immediate eject action. Only a normal
            // physical-carrier reload is deferred for a possible long hold.
            if (!player.isShiftKeyDown() && PhysicalMagazineService.usesPhysicalMagazine(player.getMainHandItem())) {
                pendingPhysicalReload = true;
                wheelOpened = false;
                heldTicks = 0;
                return;
            }
            IClientPlayerGunOperator.fromLocalPlayer(player).reload();
            return;
        }
        if (event.getAction() == GLFW.GLFW_RELEASE && pendingPhysicalReload) {
            int selectedSlot = wheelOpened ? ReloadWheelOverlay.closeAndGetSelectedSlot() : -1;
            pendingPhysicalReload = false;
            wheelOpened = false;
            heldTicks = 0;
            IClientPlayerGunOperator.fromLocalPlayer(player).reload(selectedSlot);
        }
    }

    /** Called every client tick while R is held; a tap still reloads on release. */
    public static void tickReloadWheel(Minecraft minecraft) {
        if (!pendingPhysicalReload) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (!isInGame() || player == null || player.isSpectator()
                || !RELOAD_KEY.isDown()
                || !PhysicalMagazineService.usesPhysicalMagazine(player.getMainHandItem())) {
            cancelWheel();
            return;
        }
        heldTicks++;
        if (!wheelOpened && heldTicks >= WHEEL_HOLD_TICKS) {
            wheelOpened = ReloadWheelOverlay.open(player, player.getMainHandItem());
        }
    }

    private static void cancelWheel() {
        pendingPhysicalReload = false;
        wheelOpened = false;
        heldTicks = 0;
        ReloadWheelOverlay.cancel();
    }

    public static boolean onReloadControllerPress(boolean isPress) {
        if (isInGame() && isPress) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isSpectator()) {
                return false;
            }
            if (IGun.mainHandHoldGun(player)) {
                IClientPlayerGunOperator.fromLocalPlayer(player).reload();
                return true;
            }
        }
        return false;
    }

    @Environment(EnvType.CLIENT)
    public static void autoReload(PlayerTickEvent.Pre event) {
        if (!event.getEntity().level().isClientSide())
            return;

        if (!KeyConfig.AUTO_RELOAD.get()) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator() || player.tickCount % 5 != 0) {
            return;
        }
        ItemStack currentGunItem = player.getMainHandItem();
        if (player.getMainHandItem().getItem() instanceof IGun iGun) {
            // 如果使用背包直读，且没有换弹冷却机制，则在输入时就屏蔽换弹
            if (iGun.useInventoryAmmo(player.getMainHandItem())) {
                return;
            }
            boolean flag = TimelessAPI.getCommonGunIndex(iGun.getGunId(currentGunItem))
                    .map(gunIndex -> gunIndex.getGunData().getBolt() != Bolt.OPEN_BOLT)
                    .orElse(false);

            int ammoCount = iGun.getCurrentAmmoCount(currentGunItem) + (iGun.hasBulletInBarrel(currentGunItem) && flag ? 1 : 0);
            if (ammoCount > 0) {
                return;
            }
            IClientPlayerGunOperator.fromLocalPlayer(player).reload();
        }
    }
}
