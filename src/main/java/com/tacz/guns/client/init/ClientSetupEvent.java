package com.tacz.guns.client.init;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.other.ThirdPersonManager;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import com.tacz.guns.client.gui.overlay.HeatBarOverlay;
import com.tacz.guns.client.gui.overlay.InteractKeyTextOverlay;
import com.tacz.guns.client.gui.overlay.KillAmountOverlay;

import com.tacz.guns.client.input.*;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.tooltip.ClientAmmoBoxTooltip;
import com.tacz.guns.client.tooltip.ClientAttachmentItemTooltip;
import com.tacz.guns.client.tooltip.ClientBlockItemTooltip;
import com.tacz.guns.client.tooltip.ClientGunTooltip;
import com.tacz.guns.compat.ar.ARCompat;
import com.tacz.guns.compat.controllable.ControllableCompat;
import com.tacz.guns.compat.immediatelyfast.ImmediatelyFastCompat;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import com.tacz.guns.compat.shouldersurfing.ShoulderSurfingCompat;
import com.tacz.guns.compat.zoomify.ZoomifyCompat;
import com.tacz.guns.inventory.tooltip.AmmoBoxTooltip;
import com.tacz.guns.inventory.tooltip.AttachmentItemTooltip;
import com.tacz.guns.inventory.tooltip.BlockItemTooltip;
import com.tacz.guns.inventory.tooltip.GunTooltip;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

@Environment(EnvType.CLIENT)
public class ClientSetupEvent {
    public static void init() {
        registerKeyMappings();
        registerClientTooltips();
        registerGuiOverlays();
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> onClientSetup());
        onClientResourceReload();
    }

    public static void registerKeyMappings() {
        // 注册键位 (26.2: MKB 不可用，直接注册 KeyMapping)
        KeyMappingHelper.registerKeyMapping(InspectKey.INSPECT_KEY);
        KeyMappingHelper.registerKeyMapping(ReloadKey.RELOAD_KEY);
        KeyMappingHelper.registerKeyMapping(ShootKey.SHOOT_KEY);
        KeyMappingHelper.registerKeyMapping(InteractKey.INTERACT_KEY);
        KeyMappingHelper.registerKeyMapping(FireSelectKey.FIRE_SELECT_KEY);
        KeyMappingHelper.registerKeyMapping(AimKey.AIM_KEY);
        KeyMappingHelper.registerKeyMapping(CrawlKey.CRAWL_KEY);
        KeyMappingHelper.registerKeyMapping(RefitKey.REFIT_KEY);
        KeyMappingHelper.registerKeyMapping(ZoomKey.ZOOM_KEY);
        KeyMappingHelper.registerKeyMapping(MeleeKey.MELEE_KEY);
        KeyMappingHelper.registerKeyMapping(ConfigKey.OPEN_CONFIG_KEY);
    }

    public static void registerClientTooltips() {
        // 注册文本提示 (26.2: TooltipComponentCallback → ClientTooltipComponentCallback)
        ClientTooltipComponentCallback.EVENT.register(tooltip -> {
            if (tooltip instanceof GunTooltip gunTooltip) {
                return new ClientGunTooltip(gunTooltip);
            }
            if (tooltip instanceof AmmoBoxTooltip ammoBoxTooltip) {
                return new ClientAmmoBoxTooltip(ammoBoxTooltip);
            }
            if (tooltip instanceof AttachmentItemTooltip attachmentItemTooltip) {
                return new ClientAttachmentItemTooltip(attachmentItemTooltip);
            }
            if (tooltip instanceof BlockItemTooltip blockItemTooltip) {
                return new ClientBlockItemTooltip(blockItemTooltip);
            }
            return null;
        });
    }

    public static void registerGuiOverlays() {
        HudElementRegistry.addLast(id("gun_hud"), (graphics, deltaTracker) -> GunHudOverlay.render(graphics, deltaTracker.getRealtimeDeltaTicks()));
        HudElementRegistry.addLast(id("heat_bar"), (graphics, deltaTracker) -> HeatBarOverlay.render(graphics, deltaTracker.getRealtimeDeltaTicks()));
        HudElementRegistry.addLast(id("interact_key_text"), (graphics, deltaTracker) -> InteractKeyTextOverlay.render(graphics, deltaTracker.getRealtimeDeltaTicks()));
        HudElementRegistry.addLast(id("kill_amount"), (graphics, deltaTracker) -> KillAmountOverlay.render(graphics, deltaTracker.getRealtimeDeltaTicks()));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GunMod.MOD_ID, path);
    }

    public static void onClientSetup() {
        // 注册自己的的硬编码第三人称动画
        ThirdPersonManager.registerDefault();

        // 26.2 已解决: ColorProviderRegistry.ITEM 与 ItemProperties 均已移除。
        // 弹药箱染色改由 items/ammo_box.json 模型里的 minecraft:dye tint 完成；
        // 变体选择改由 minecraft:select + tacz:ammo_statue 属性完成
        // （属性实现见 AmmoBoxStatueProperty，注册点在 TaCZFabricClient）。

        // 初始化自己的枪包下载器
//       ClientGunPackDownloadManager.init();

//        // 与 player animator 的兼容
//       PlayerAnimatorCompat.init();

        // 与 Shoulder Surfing Reloaded 的兼容
        ShoulderSurfingCompat.init();

        // 与 Controllable 的兼容
        ControllableCompat.init();

        // 与 Accelerated Rendering 的兼容
        ARCompat.init();

        ZoomifyCompat.init();
        ImmediatelyFastCompat.init();
    }

    public static void onClientResourceReload() {
        PlayerAnimatorCompat.init();

        ClientAssetsManager.INSTANCE.reloadAndRegister(ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)::registerReloadListener);
        if (PlayerAnimatorCompat.isInstalled()) {
            PlayerAnimatorCompat.registerReloadListener(ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)::registerReloadListener);
        }
    }
}
