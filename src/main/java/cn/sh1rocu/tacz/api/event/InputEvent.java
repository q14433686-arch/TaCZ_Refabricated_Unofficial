package cn.sh1rocu.tacz.api.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.ApiStatus;

@Environment(EnvType.CLIENT)
public abstract class InputEvent extends BaseEvent {
    public static class InteractionKeyMappingTriggered extends InputEvent implements ICancellableEvent {
        public static final Event<IKMTCallback> EVENT = EventFactory.createArrayBacked(IKMTCallback.class, callbacks -> event -> {
            for (IKMTCallback e : callbacks) e.onInteractionKeyMappingTriggered(event);
        });

        private final int button;
        private final KeyMapping keyMapping;
        private final InteractionHand hand;
        private boolean handSwing = true;

        public InteractionKeyMappingTriggered(int button, KeyMapping keyMapping, InteractionHand hand) {
            this.button = button;
            this.keyMapping = keyMapping;
            this.hand = hand;
        }

        public void setSwingHand(boolean value) {
            this.handSwing = value;
        }

        public boolean shouldSwingHand() {
            return this.handSwing;
        }

        public InteractionHand getHand() {
            return this.hand;
        }

        public boolean isAttack() {
            return this.button == 0;
        }

        public boolean isUseItem() {
            return this.button == 1;
        }

        public boolean isPickBlock() {
            return this.button == 2;
        }

        public KeyMapping getKeyMapping() {
            return this.keyMapping;
        }
    }

    public static class Key extends InputEvent {
        public static final Event<KeyCallback> EVENT = EventFactory.createArrayBacked(KeyCallback.class, callbacks -> event -> {
            for (KeyCallback e : callbacks) e.onKey(event);
        });

        private final int key;
        private final int keycode;
        private final int action;
        private final int modifiers;

        /**
         * @param key     26.3 起是 <b>SDL scancode</b>（物理键位），可直接喂给
         *                {@code InputConstants.Type.KEYBOARD.getOrCreate(...)}；
         *                26.2 及以前是 GLFW keysym。
         * @param keycode 26.3 起是 <b>SDL keycode</b>（按当前键盘布局解析出的字符），
         *                取代了旧的 GLFW scancode 形参。
         */
        @ApiStatus.Internal
        public Key(int key, int keycode, int action, int modifiers) {
            this.key = key;
            this.keycode = keycode;
            this.action = action;
            this.modifiers = modifiers;
        }

        public int getKey() {
            return this.key;
        }

        /**
         * 26.3 起返回 SDL keycode（布局相关）。
         *
         * @deprecated 名字来自 GLFW 时代的 scancode，语义已变。新代码请用 {@link #getKeycode()}。
         */
        @Deprecated
        public int getScanCode() {
            return this.keycode;
        }

        /** 26.3 的 SDL keycode（按当前键盘布局解析出的字符码）。 */
        public int getKeycode() {
            return this.keycode;
        }

        public int getAction() {
            return this.action;
        }

        public int getModifiers() {
            return this.modifiers;
        }
    }

    public static class MouseButton extends InputEvent {
        public static final Event<MouseCallback> EVENT = EventFactory.createArrayBacked(MouseCallback.class, callbacks -> event -> {
            for (MouseCallback e : callbacks) e.onMouse(event);
        });

        private final int button;
        private final int action;
        private final int modifiers;

        @ApiStatus.Internal
        protected MouseButton(int button, int action, int modifiers) {
            this.button = button;
            this.action = action;
            this.modifiers = modifiers;
        }

        public int getButton() {
            return this.button;
        }

        public int getAction() {
            return this.action;
        }

        public int getModifiers() {
            return this.modifiers;
        }

        public static class Post extends InputEvent.MouseButton {
            public static final Event<MousePostCallback> EVENT = EventFactory.createArrayBacked(MousePostCallback.class, callbacks -> event -> {
                for (MousePostCallback e : callbacks) e.onMousePost(event);
            });

            @ApiStatus.Internal
            public Post(int button, int action, int modifiers) {
                super(button, action, modifiers);
            }
        }

        public static class Pre extends InputEvent.MouseButton implements ICancellableEvent {
            public static final Event<MousePreCallback> EVENT = EventFactory.createArrayBacked(MousePreCallback.class, callbacks -> event -> {
                for (MousePreCallback e : callbacks) e.onMousePre(event);
            });

            @ApiStatus.Internal
            public Pre(int button, int action, int modifiers) {
                super(button, action, modifiers);
            }
        }
    }

    public interface IKMTCallback {
        void onInteractionKeyMappingTriggered(InteractionKeyMappingTriggered event);
    }

    public interface KeyCallback {
        void onKey(Key event);
    }

    public interface MouseCallback {
        void onMouse(MouseButton event);
    }

    public interface MousePostCallback {
        void onMousePost(MouseButton.Post event);
    }

    public interface MousePreCallback {
        void onMousePre(MouseButton.Pre event);
    }
}
