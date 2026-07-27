package cn.sh1rocu.tacz.util.forge;

import cn.sh1rocu.tacz.api.event.ClientPlayerNetworkEvent;
import cn.sh1rocu.tacz.api.event.ComputeFovModifierEvent;
import cn.sh1rocu.tacz.api.event.TextureStitchEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class ClientHooks {
    public static float getFieldOfViewModifier(Player entity, float fovModifier) {
        ComputeFovModifierEvent fovModifierEvent = new ComputeFovModifierEvent(entity, fovModifier);
        ComputeFovModifierEvent.CALLBACK.invoker().post(fovModifierEvent);
        return fovModifierEvent.getNewFovModifier();
    }

    public static void firePlayerLogout(@Nullable MultiPlayerGameMode pc, @Nullable LocalPlayer player) {
        ClientPlayerNetworkEvent.LOGGING_OUT.invoker().post(new ClientPlayerNetworkEvent.LoggingOut(pc, player, player != null ? player.connection != null ? player.connection.getConnection() : null : null));
    }

    // 【r42】firePlayerRespawn 已删除：它的唯一调用者 ClientPacketListenerMixin
    // 依赖 ClientLevel#addPlayer 注入点，而 26.2 已无该方法，那个 mixin 已删。
    // 重生后的配件缓存刷新改由 RefreshClonePlayerDataEvent#onClientTick
    // 检测 Minecraft#player 实例变化来触发，不再需要本 hook。

    public static void onTextureStitchedPost(TextureAtlas map) {
        TextureStitchEvent.POST.invoker().post(new TextureStitchEvent.Post(map));
    }
}
