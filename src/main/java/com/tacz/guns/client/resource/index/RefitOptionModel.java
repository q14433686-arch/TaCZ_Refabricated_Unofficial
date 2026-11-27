package com.tacz.guns.client.resource.index;

import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.resource.ClientAssetLoadDispatcher;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Per-display model instance: its sight flags and active view group must not leak to another option. */
public abstract class RefitOptionModel {
    protected final AttachmentDisplay display;
    private final Component name;
    private final Supplier<BedrockAttachmentModel> model;
    private final AtomicBoolean warmUpRequested = new AtomicBoolean();

    protected RefitOptionModel(String name, AttachmentDisplay display) {
        Preconditions.checkArgument(display != null, "Missing refit option display");
        this.display = display;
        this.name = name == null ? Component.empty() : Component.translatable(name);
        this.model = Suppliers.memoize(() -> {
            try {
                var result = ClientAttachmentIndex.getOrLoadAttachmentModel(display.getModel());
                if (result != null) {
                    result.setIsScope(display.isScope());
                    result.setIsSight(display.isSight());
                    result.setScopeLens(display.getScopeLens());
                }
                return result;
            } catch (RuntimeException e) {
                GunMod.LOGGER.warn("Failed to load refit option model {}", display.getModel(), e);
                return null;
            }
        });
    }

    public Component getName() { return name; }
    public @Nullable BedrockAttachmentModel getModel() { return model.get(); }
    public @Nullable Identifier getModelTexture() { return display.getTexture(); }
    public @Nullable Vector3f getMountOffset() { return display.getMountOffset(); }
    public void warmUp() {
        if (warmUpRequested.compareAndSet(false, true)) ClientAssetLoadDispatcher.executor().execute(model::get);
    }
}
