package cn.sh1rocu.simplebedrockmodel.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Stub for simplebedrockmodel RenderTickEvent (library not yet available for 26.2)
 */
public class RenderTickEvent {
    public final Phase phase;
    public final float renderTickTime;

    public RenderTickEvent(Phase phase, float renderTickTime) {
        this.phase = phase;
        this.renderTickTime = renderTickTime;
    }

    public enum Phase {
        START, END
    }

    public static final Event<Callback> EVENT = EventFactory.createArrayBacked(Callback.class, callbacks -> event -> {
        for (Callback callback : callbacks) {
            callback.onRenderTick(event);
        }
    });

    @FunctionalInterface
    public interface Callback {
        void onRenderTick(RenderTickEvent event);
    }
}
