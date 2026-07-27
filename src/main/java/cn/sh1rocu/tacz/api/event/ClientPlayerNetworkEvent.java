package cn.sh1rocu.tacz.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import org.jetbrains.annotations.Nullable;

public abstract class ClientPlayerNetworkEvent extends BaseEvent {
    private final MultiPlayerGameMode multiPlayerGameMode;
    private final LocalPlayer player;
    private final Connection connection;

    public static final Event<LoggingOutCallback> LOGGING_OUT = EventFactory.createArrayBacked(LoggingOutCallback.class, callbacks -> event -> {
        for (LoggingOutCallback callback : callbacks) {
            callback.post(event);
        }
    });

    /**
     * @deprecated 【26.2 / r42】本事件<b>已不会被触发</b>。
     * <p>它原先由 {@code ClientPacketListenerMixin} 注入 {@code handleRespawn} 里的
     * {@code ClientLevel#addPlayer} 调用点来发射，但 26.2 已无该方法
     * （对 {@code ClientLevel} 逐方法核对无此项，{@code handleRespawn} 反汇编中亦无该调用），
     * 那个 mixin 已随之删除。</p>
     * <p>如果你需要「玩家重生 / 跨维度后」的时机，改为在客户端 tick 里比对
     * {@code Minecraft#player} 的实例是否被替换 —— 本体的
     * {@code RefreshClonePlayerDataEvent#onClientTick} 就是这么做的，可作参考。</p>
     * <p>保留本字段仅为不破坏已有第三方代码的编译，注册回调不会收到任何调用。</p>
     */
    @Deprecated
    public static final Event<CloneCallback> CLONE = EventFactory.createArrayBacked(CloneCallback.class, callbacks -> event -> {
        for (CloneCallback callback : callbacks) {
            callback.post(event);
        }
    });

    public interface LoggingOutCallback {
        void post(LoggingOut event);
    }

    public interface CloneCallback {
        void post(Clone event);
    }

    protected ClientPlayerNetworkEvent(final MultiPlayerGameMode multiPlayerGameMode, final LocalPlayer player, final Connection connection) {
        this.multiPlayerGameMode = multiPlayerGameMode;
        this.player = player;
        this.connection = connection;
    }

    public MultiPlayerGameMode getMultiPlayerGameMode() {
        return multiPlayerGameMode;
    }

    public LocalPlayer getPlayer() {
        return player;
    }

    public Connection getConnection() {
        return connection;
    }

    public static class LoggingOut extends ClientPlayerNetworkEvent {
        public LoggingOut(@Nullable final MultiPlayerGameMode controller, @Nullable final LocalPlayer player, @Nullable final Connection networkManager) {
            super(controller, player, networkManager);
        }

        @Nullable
        @Override
        public MultiPlayerGameMode getMultiPlayerGameMode() {
            return super.getMultiPlayerGameMode();
        }

        @Nullable
        @Override
        public LocalPlayer getPlayer() {
            return super.getPlayer();
        }

        @Nullable
        @Override
        public Connection getConnection() {
            return super.getConnection();
        }
    }

    public static class Clone extends ClientPlayerNetworkEvent {
        private final LocalPlayer oldPlayer;

        public Clone(final MultiPlayerGameMode pc, final LocalPlayer oldPlayer, final LocalPlayer newPlayer, final Connection networkManager) {
            super(pc, newPlayer, networkManager);
            this.oldPlayer = oldPlayer;
        }

        public LocalPlayer getOldPlayer() {
            return oldPlayer;
        }

        public LocalPlayer getNewPlayer() {
            return super.getPlayer();
        }

        @Override
        public LocalPlayer getPlayer() {
            return super.getPlayer();
        }
    }
}
