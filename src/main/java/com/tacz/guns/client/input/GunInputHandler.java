package com.tacz.guns.client.input;

import cn.sh1rocu.tacz.api.event.InputEvent;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import java.util.List;
import static com.tacz.guns.util.InputExtraCheck.isInGame;

/** Both device events carry the native 26.3 physical key; dispatch each edge exactly once. */
public final class GunInputHandler {
    public static final KeyMapping UNLOAD_KEY = new KeyMapping("key.tacz.unload.desc", InputConstants.Type.KEYBOARD,
            InputConstants.UNKNOWN.getValue(), TaCZKeyCategory.TACZ);
    private record Binding(KeyMapping mapping, Runnable press) {}
    private static final List<Binding> BINDINGS = List.of(
            new Binding(AimKey.AIM_KEY, () -> AimKey.onAimControllerPress(true)),
            new Binding(CrawlKey.CRAWL_KEY, () -> CrawlKey.onCrawlControllerPress(true)),
            new Binding(ReloadKey.RELOAD_KEY, () -> ReloadKey.onReloadControllerPress(true)),
            new Binding(InspectKey.INSPECT_KEY, () -> InspectKey.onInspectControllerPress(true)),
            new Binding(FireSelectKey.FIRE_SELECT_KEY, FireSelectKey::doFireSelectLogic),
            new Binding(MeleeKey.MELEE_KEY, MeleeKey::doMeleeLogic),
            new Binding(ZoomKey.ZOOM_KEY, ZoomKey::doZoomLogic),
            new Binding(InteractKey.INTERACT_KEY, () -> InteractKey.doInteractLogic(false)),
            new Binding(RefitKey.REFIT_KEY, RefitKey::onRefitPress),
            new Binding(ConfigKey.OPEN_CONFIG_KEY, ConfigKey::onOpenConfig));

    public static void registerMappings() {
        BINDINGS.forEach(b -> KeyMappingHelper.registerKeyMapping(b.mapping));
        KeyMappingHelper.registerKeyMapping(UNLOAD_KEY);
        KeyMappingHelper.registerKeyMapping(ShootKey.SHOOT_KEY);
    }

    public static void registerEvents() {
        InputEvent.Key.EVENT.register(e -> dispatch(InputConstants.Type.KEYBOARD.getOrCreate(e.getKey()), e.getAction()));
        InputEvent.MouseButton.Post.EVENT.register(e -> dispatch(InputConstants.Type.MOUSE.getOrCreate(e.getButton()), e.getAction()));
        InputEvent.MouseButton.Pre.EVENT.register(e -> {
            if (e.getAction() == InputConstants.PRESS && closeRefit(InputConstants.Type.MOUSE.getOrCreate(e.getButton()))) e.setCanceled(true);
        });
    }

    private static boolean matches(KeyMapping mapping, InputConstants.Key key) {
        return !mapping.isUnbound() && mapping.matches(key);
    }

    private static boolean closeRefit(InputConstants.Key key) {
        var mc = Minecraft.getInstance();
        if (matches(RefitKey.REFIT_KEY, key) && mc.isWindowActive() && mc.gui.overlay() == null
                && mc.gui.screen() instanceof GunRefitScreen screen) {
            screen.onClose();
            return true;
        }
        return false;
    }

    private static void dispatch(InputConstants.Key key, int action) {
        if (action == InputConstants.RELEASE) {
            if (matches(AimKey.AIM_KEY, key)) AimKey.onAimControllerPress(false);
            if (matches(CrawlKey.CRAWL_KEY, key)) CrawlKey.releaseIfNotHeld();
            return;
        }
        if (action != InputConstants.PRESS || closeRefit(key) || !isInGame()) return;
        if (matches(UNLOAD_KEY, key) || (matches(ReloadKey.RELOAD_KEY, key)
                && (InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT)))) {
            var player = Minecraft.getInstance().player;
            if (player != null && !player.isSpectator() && IGun.mainHandHoldGun(player))
                IClientPlayerGunOperator.fromLocalPlayer(player).unloadAmmo();
            return; // Do not fall through to reload, even if unloading was rejected.
        }
        for (Binding binding : BINDINGS) {
            if (!isInGame()) break; // An earlier binding may have opened a GUI.
            if (matches(binding.mapping, key)) binding.press.run();
        }
    }
    private GunInputHandler() {}
}
