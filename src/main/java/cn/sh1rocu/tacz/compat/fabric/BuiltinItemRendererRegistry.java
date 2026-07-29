package cn.sh1rocu.tacz.compat.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Local replacement for Fabric API's BuiltinItemRendererRegistry (removed in 26.2).
 * Provides a simple Item → DynamicItemRenderer registry.
 */
public class BuiltinItemRendererRegistry {
    public static final BuiltinItemRendererRegistry INSTANCE = new BuiltinItemRendererRegistry();

    private final Map<Item, DynamicItemRenderer> renderers = new IdentityHashMap<>();

    private BuiltinItemRendererRegistry() {
    }

    public void register(Item item, DynamicItemRenderer renderer) {
        renderers.put(item, renderer);
    }

    public DynamicItemRenderer get(Item item) {
        return renderers.get(item);
    }

    /**
     * Interface for custom item renderers in 26.2.
     * Replaces the old BlockEntityWithoutLevelRenderer + DynamicItemRenderer pattern.
     */
    public interface DynamicItemRenderer {
        void render(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay);
    }
}
