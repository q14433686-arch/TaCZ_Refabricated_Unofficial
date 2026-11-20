package com.tacz.guns.client.input;

import cn.sh1rocu.tacz.api.event.InputEvent;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.ViewTuningScreen;
import com.tacz.guns.client.gui.overlay.DisplayCaseLockHint;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import java.util.List;
import static com.tacz.guns.util.InputExtraCheck.isInGame;

/** Both device events carry the native 26.2 physical key; dispatch each edge exactly once. */
public final class GunInputHandler {
    public static final KeyMapping UNLOAD_KEY = new KeyMapping("key.tacz.unload.desc", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), TaCZKeyCategory.TACZ);
    public static final KeyMapping VIEW_TUNING_KEY = new KeyMapping("key.tacz.view_tuning.desc", InputConstants.Type.KEYSYM,
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
            new Binding(ConfigKey.OPEN_CONFIG_KEY, ConfigKey::onOpenConfig),
            new Binding(VIEW_TUNING_KEY, () -> ViewTuningScreen.open(null)));

    public static void registerMappings() {
        BINDINGS.forEach(b -> KeyBindingHelper.registerKeyBinding(b.mapping));
        KeyBindingHelper.registerKeyBinding(UNLOAD_KEY);
        KeyBindingHelper.registerKeyBinding(ShootKey.SHOOT_KEY);
    }

    public static void registerEvents() {
        InputEvent.Key.EVENT.register(e -> dispatch(InputConstants.Type.KEYSYM.getOrCreate(e.getKey()), e.getAction()));
        InputEvent.MouseButton.Post.EVENT.register(e -> dispatch(InputConstants.Type.MOUSE.getOrCreate(e.getButton()), e.getAction()));
        InputEvent.MouseButton.Pre.EVENT.register(e -> {
            if (e.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS && closeRefit(InputConstants.Type.MOUSE.getOrCreate(e.getButton()))) e.setCanceled(true);
        });
    }

    /**
     * 1.21.11 的 {@code KeyMapping#matches} 只接受 {@code KeyEvent}，鼠标另有
     * {@code matchesMouse(MouseButtonEvent)}；26.2 那个 {@code matches(InputConstants.Key)}
     * 重载已经不存在。这里按 {@link InputConstants.Key#getType()} 还原成对应事件，
     * 键码/按钮号原样带过去（枪械按键都不带修饰键，因此修饰符填 0 与 26.2 行为一致）。
     */
    static boolean matches(KeyMapping mapping, InputConstants.Key key) {
        if (mapping.isUnbound()) return false;
        return switch (key.getType()) {
            case KEYSYM -> mapping.matches(new KeyEvent(key.getValue(), 0, 0));
            case SCANCODE -> mapping.matches(new KeyEvent(InputConstants.UNKNOWN.getValue(), key.getValue(), 0));
            case MOUSE -> mapping.matchesMouse(new MouseButtonEvent(0.0D, 0.0D, new MouseButtonInfo(key.getValue(), 0)));
        };
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

    private static void dispatch(InputConstants.Key key, int action) {
        if (action == org.lwjgl.glfw.GLFW.GLFW_RELEASE) {
            if (matches(AimKey.AIM_KEY, key)) AimKey.onAimControllerPress(false);
            if (matches(CrawlKey.CRAWL_KEY, key)) CrawlKey.releaseIfNotHeld();
            return;
        }
        if (action != org.lwjgl.glfw.GLFW.GLFW_PRESS || closeRefit(key) || !isInGame()) return;
        var mc = Minecraft.getInstance();
        if (matches(mc.options.keyUse, key)) DisplayCaseLockHint.onEmptyHandUse(mc);
        if (matches(UNLOAD_KEY, key) || (matches(ReloadKey.RELOAD_KEY, key)
                && (InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), InputConstants.KEY_LALT) || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), InputConstants.KEY_RALT)))) {
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
