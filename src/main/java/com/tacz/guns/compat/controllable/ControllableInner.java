package com.tacz.guns.compat.controllable;

import com.mrcrayfish.controllable.Controllable;
import com.mrcrayfish.controllable.client.binding.ButtonBinding;
import com.mrcrayfish.controllable.client.binding.context.InGameContext;
import com.mrcrayfish.controllable.client.binding.handlers.OnPressAndReleaseHandler;
import com.mrcrayfish.controllable.client.input.Buttons;
import com.mrcrayfish.controllable.client.input.Controller;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.input.*;
import com.tacz.guns.client.resource.pojo.display.gun.ControllableData;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.util.InputExtraCheck;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.EnumMap;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** Loaded only on clients that actually have the optional 26.3 Fabric mod installed. */
public final class ControllableInner {
    private static final InGameContext CONTEXT = new InGameContext(Identifier.parse("tacz:gun_input")) {
        @Override public int priority() { return 1; }
    };

    private static boolean gunActive() {
        var player = Minecraft.getInstance().player;
        return InputExtraCheck.isInGame() && player != null && !player.isSpectator() && IGun.mainHandHoldGun(player);
    }

    private static boolean crawlActive() {
        var player = Minecraft.getInstance().player;
        return InputExtraCheck.isInGame() && player != null && !player.isSpectator() && !player.isPassenger()
                && SyncConfig.ENABLE_CRAWL.get() && (IGun.mainHandHoldGun(player) || SyncConfig.ENABLE_CRAWL_WITHOUT_GUN.get());
    }

    private static ButtonBinding bind(int button, String name, BooleanSupplier active, Runnable press) {
        var binding = new ButtonBinding(button, "key.tacz." + name + ".desc", "key.category.tacz", CONTEXT,
                OnPressAndReleaseHandler.create(ctx -> active.getAsBoolean() ? Optional.of(press) : Optional.empty(),
                        ctx -> active.getAsBoolean()));
        Controllable.getBindingRegistry().register(binding);
        return binding;
    }

    public static void init() {
        var aim = bind(Buttons.LEFT_TRIGGER, "aim", ControllableInner::gunActive, () -> AimKey.onAimControllerPress(true));
        var shoot = bind(Buttons.RIGHT_TRIGGER, "shoot", ControllableInner::gunActive, () -> {});
        var crawl = bind(Buttons.LEFT_THUMB_STICK, "crawl", ControllableInner::crawlActive, () -> CrawlKey.onCrawlControllerPress(true));
        bind(Buttons.B, "reload", ControllableInner::gunActive, () -> ReloadKey.onReloadControllerPress(true));
        bind(Buttons.X, "melee", () -> gunActive() && !aiming(), () -> MeleeKey.onMeleeControllerPress(true));
        bind(Buttons.X, "zoom", () -> gunActive() && aiming(), () -> ZoomKey.onZoomControllerPress(true));
        bind(Buttons.DPAD_LEFT, "fire_select", ControllableInner::gunActive, () -> FireSelectKey.onFireSelectControllerPress(true));
        bind(-1, "interact", ControllableInner::gunActive, () -> InteractKey.onInteractControllerPress(true));
        bind(-1, "inspect", ControllableInner::gunActive, () -> InspectKey.onInspectControllerPress(true));
        // Refresh even without a controller: disconnects, GUI transitions and rebinds must clear stale holds.
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            Controller pad = Controllable.getController();
            boolean active = pad != null && gunActive();
            AimKey.setControllerAimDown(active && !aim.isUnbound() && pad.isButtonPressed(aim.getButton()));
            ShootKey.shootControllerTick(active && !shoot.isUnbound() && pad.isButtonPressed(shoot.getButton()));
            CrawlKey.setControllerDown(pad != null && crawlActive() && !crawl.isUnbound() && pad.isButtonPressed(crawl.getButton()));
        });
    }

    private static boolean aiming() {
        return IClientPlayerGunOperator.fromLocalPlayer(Minecraft.getInstance().player).isAim();
    }

    public static void rumbleShoot(ItemStack mainHandItem, FireMode fireMode) {
        Controller controller = Controllable.getController();
        if (controller == null) {
            return;
        }
        IGun iGun = IGun.getIGunOrNull(mainHandItem);
        if (iGun == null) {
            return;
        }

        TimelessAPI.getGunDisplay(mainHandItem).ifPresent(index -> {
            EnumMap<FireMode, ControllableData> data = index.getControllableData();
            if (data.containsKey(fireMode)) {
                ControllableData controllableData = data.get(fireMode);
                controller.rumble(controllableData.getLowFrequency(), controllableData.getHighFrequency(), controllableData.getTimeInMs());
            } else {
                if (fireMode == FireMode.AUTO) {
                    controller.rumble(0.15f, 0.25f, 80);
                } else {
                    controller.rumble(0.25f, 0.5f, 100);
                }
            }
        });
    }

}
