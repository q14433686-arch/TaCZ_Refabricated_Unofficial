package com.tacz.guns.client.input;

import cn.sh1rocu.tacz.api.event.InputEvent;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.overlay.DisplayCaseLockHint;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import java.util.List;
import static com.tacz.guns.util.InputExtraCheck.isInGame;

/** Both device events carry the 26.1.2 physical key (including scancode-only keyboards); dispatch each edge exactly once.
 * View tuning remains on its native queued-click path; shooting remains polled. */
public final class GunInputHandler {
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

    public static void registerEvents() {
        InputEvent.Key.EVENT.register(e -> dispatch(InputConstants.getKey(new net.minecraft.client.input.KeyEvent(e.getKey(), e.getScanCode(), e.getModifiers())), e.getAction(), e.getModifiers()));
        InputEvent.MouseButton.Post.EVENT.register(e -> dispatch(InputConstants.Type.MOUSE.getOrCreate(e.getButton()), e.getAction(), e.getModifiers()));
        InputEvent.MouseButton.Pre.EVENT.register(e -> {
            if (e.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS && closeRefit(InputConstants.Type.MOUSE.getOrCreate(e.getButton()))) e.setCanceled(true);
        });
    }

    private static boolean matches(KeyMapping mapping, InputConstants.Key key) {
        return !mapping.isUnbound() && KeyBindingHelper.getBoundKeyOf(mapping).equals(key);
    }

    private static boolean closeRefit(InputConstants.Key key) {
        var mc = Minecraft.getInstance();
        if (matches(RefitKey.REFIT_KEY, key) && mc.isWindowActive() && mc.getOverlay() == null
                && mc.screen instanceof GunRefitScreen screen) {
            screen.onClose();
            return true;
        }
        return false;
    }

    private static void dispatch(InputConstants.Key key, int action, int modifiers) {
        if (action == org.lwjgl.glfw.GLFW.GLFW_RELEASE) {
            if (matches(AimKey.AIM_KEY, key)) AimKey.onAimControllerPress(false);
            if (matches(CrawlKey.CRAWL_KEY, key)) CrawlKey.releaseIfNotHeld();
            return;
        }
        if (action != org.lwjgl.glfw.GLFW.GLFW_PRESS || closeRefit(key) || !isInGame()) return;
        var mc = Minecraft.getInstance();
        if (matches(mc.options.keyUse, key)) DisplayCaseLockHint.onEmptyHandUse(mc);
        if (matches(ReloadKey.UNLOAD_KEY, key) || (matches(ReloadKey.RELOAD_KEY, key)
                && ((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_ALT) != 0))) {
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
