package com.tacz.guns.compat.controllable;

import com.mrcrayfish.controllable.Controllable;
import com.mrcrayfish.controllable.client.binding.ButtonBinding;
import com.mrcrayfish.controllable.client.binding.context.BindingContext;
import com.mrcrayfish.controllable.client.binding.context.InGameContext;
import com.mrcrayfish.controllable.client.binding.handlers.OnPressAndReleaseHandler;
import com.mrcrayfish.controllable.client.input.Buttons;
import com.mrcrayfish.controllable.client.input.Controller;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.input.CrawlKey;
import com.tacz.guns.client.input.FireSelectKey;
import com.tacz.guns.client.input.InspectKey;
import com.tacz.guns.client.input.InteractKey;
import com.tacz.guns.client.input.MeleeKey;
import com.tacz.guns.client.input.ReloadKey;
import com.tacz.guns.client.input.ShootKey;
import com.tacz.guns.client.input.ZoomKey;
import com.tacz.guns.client.resource.pojo.display.gun.ControllableData;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Optional;

/** Controllable 0.25.7 bindings and gun-fire rumble integration. */
public final class ControllableInner {
    public static final BindingContext GUN_KEY_CONFLICT =
            new GunKeyConflict(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "gun_key"));
    private static final InGameContext CRAWL_CONTEXT = new InGameContext(Identifier.parse("tacz:crawl_input")) {
        @Override public int priority() { return 1; }
    };
    private static boolean gunActive() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return com.tacz.guns.util.InputExtraCheck.isInGame() && player != null && !player.isSpectator() && IGun.mainHandHoldGun(player);
    }
    private static boolean crawlActive() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return com.tacz.guns.util.InputExtraCheck.isInGame() && player != null && !player.isSpectator() && !player.isPassenger()
                && com.tacz.guns.config.sync.SyncConfig.ENABLE_CRAWL.get()
                && (IGun.mainHandHoldGun(player) || com.tacz.guns.config.sync.SyncConfig.ENABLE_CRAWL_WITHOUT_GUN.get());
    }
    private static boolean aiming() {
        return com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(net.minecraft.client.Minecraft.getInstance().player).isAim();
    }
    public static final ButtonBinding AIM = new ButtonBinding(
            Buttons.LEFT_TRIGGER, "key.tacz.aim.desc", "key.category.tacz", GUN_KEY_CONFLICT,
            OnPressAndReleaseHandler.create(
                    context -> Optional.of(() -> AimKey.onAimControllerPress(true)),
                    context -> AimKey.onAimControllerPress(false)));
    public static final ButtonBinding SHOOT = new ButtonBinding(
            Buttons.RIGHT_TRIGGER, "key.tacz.shoot.desc", "key.category.tacz", GUN_KEY_CONFLICT,
            OnPressAndReleaseHandler.create(context -> Optional.of(() -> {
            }), context -> true));
    public static final ButtonBinding RELOAD = new ButtonBinding(
            Buttons.B, "key.tacz.reload.desc", "key.category.tacz", GUN_KEY_CONFLICT,
            OnPressAndReleaseHandler.create(
                    context -> Optional.of(() -> ReloadKey.onReloadControllerPress(true)),
                    context -> ReloadKey.onReloadControllerPress(false)));
    public static final ButtonBinding MELEE = new ButtonBinding(
            Buttons.X, "key.tacz.melee.desc", "key.category.tacz", GUN_KEY_CONFLICT,
            OnPressAndReleaseHandler.create(
                    context -> !aiming() ? Optional.of(() -> MeleeKey.onMeleeControllerPress(true)) : Optional.empty(),
                    context -> MeleeKey.onMeleeControllerPress(false)));
    public static final ButtonBinding ZOOM = new ButtonBinding(
            Buttons.X, "key.tacz.zoom.desc", "key.category.tacz", GUN_KEY_CONFLICT,
            OnPressAndReleaseHandler.create(
                    context -> aiming() ? Optional.of(() -> ZoomKey.onZoomControllerPress(true)) : Optional.empty(),
                    context -> ZoomKey.onZoomControllerPress(false)));
    public static final ButtonBinding CRAWL = new ButtonBinding(
            Buttons.LEFT_THUMB_STICK, "key.tacz.crawl.desc", "key.category.tacz", CRAWL_CONTEXT,
            OnPressAndReleaseHandler.create(
                    context -> crawlActive() ? Optional.of(() -> CrawlKey.onCrawlControllerPress(true)) : Optional.empty(),
                    context -> crawlActive()));
    public static final ButtonBinding FIRE_SELECT = new ButtonBinding(
            Buttons.DPAD_LEFT, "key.tacz.fire_select.desc", "key.category.tacz", GUN_KEY_CONFLICT,
            OnPressAndReleaseHandler.create(
                    context -> Optional.of(() -> FireSelectKey.onFireSelectControllerPress(true)),
                    context -> FireSelectKey.onFireSelectControllerPress(false)));
    public static final ButtonBinding INTERACT = new ButtonBinding(
            -1, "key.tacz.interact.desc", "key.category.tacz", GUN_KEY_CONFLICT,
            OnPressAndReleaseHandler.create(
                    context -> Optional.of(() -> InteractKey.onInteractControllerPress(true)),
                    context -> InteractKey.onInteractControllerPress(false)));
    public static final ButtonBinding INSPECT = new ButtonBinding(
            -1, "key.tacz.inspect.desc", "key.category.tacz", GUN_KEY_CONFLICT,
            OnPressAndReleaseHandler.create(
                    context -> Optional.of(() -> InspectKey.onInspectControllerPress(true)),
                    context -> InspectKey.onInspectControllerPress(false)));

    private ControllableInner() {
    }

    public static void init() {
        Controllable.getBindingRegistry().register(AIM);
        Controllable.getBindingRegistry().register(SHOOT);
        Controllable.getBindingRegistry().register(RELOAD);
        Controllable.getBindingRegistry().register(MELEE);
        Controllable.getBindingRegistry().register(CRAWL);
        Controllable.getBindingRegistry().register(ZOOM);
        Controllable.getBindingRegistry().register(FIRE_SELECT);
        Controllable.getBindingRegistry().register(INTERACT);
        Controllable.getBindingRegistry().register(INSPECT);

        ClientTickEvents.START_CLIENT_TICK.register(client -> pollHeldBindings());
    }

    private static void pollHeldBindings() {
        Controller pad = Controllable.getController();
        boolean active = pad != null && gunActive();
        AimKey.setControllerAimDown(active && !AIM.isUnbound() && pad.isButtonPressed(AIM.getButton()));
        ShootKey.shootControllerTick(active && !SHOOT.isUnbound() && pad.isButtonPressed(SHOOT.getButton()));
        CrawlKey.setControllerDown(pad != null && crawlActive() && !CRAWL.isUnbound() && pad.isButtonPressed(CRAWL.getButton()));
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
                controller.rumble(controllableData.getLowFrequency(), controllableData.getHighFrequency(),
                        controllableData.getTimeInMs());
            } else if (fireMode == FireMode.AUTO) {
                controller.rumble(0.15F, 0.25F, 80);
            } else {
                controller.rumble(0.25F, 0.5F, 100);
            }
        });
    }

    public static final class GunKeyConflict extends InGameContext {
        private GunKeyConflict(Identifier id) {
            super(id);
        }

        @Override
        public int priority() {
            return 1;
        }
    }
}
