package cn.sh1rocu.tacz.api.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

@Environment(EnvType.CLIENT)
public abstract class RenderLivingEvent<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends BaseEvent {
    private final LivingEntity entity;
    private final LivingEntityRenderer<T, S, M> renderer;
    private final float partialTick;
    private final S renderState;
    private final PoseStack poseStack;
    private final SubmitNodeCollector submitNodeCollector;
    private final CameraRenderState cameraRenderState;

    public static final Event<PostCallback> POST = EventFactory.createArrayBacked(PostCallback.class, callbacks -> event -> {
        for (PostCallback callback : callbacks) {
            callback.post(event);
        }
    });

    public interface PostCallback {
        void post(Post<?, ?, ?> event);
    }

    protected RenderLivingEvent(LivingEntity entity, LivingEntityRenderer<T, S, M> renderer, float partialTick, S renderState) {
        this(entity, renderer, partialTick, renderState, null, null, null);
    }

    protected RenderLivingEvent(LivingEntity entity, LivingEntityRenderer<T, S, M> renderer, float partialTick, S renderState,
                                PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        this.entity = entity;
        this.renderer = renderer;
        this.partialTick = partialTick;
        this.renderState = renderState;
        this.poseStack = poseStack;
        this.submitNodeCollector = submitNodeCollector;
        this.cameraRenderState = cameraRenderState;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public LivingEntityRenderer<T, S, M> getRenderer() {
        return renderer;
    }

    public float getPartialTick() {
        return partialTick;
    }

    public S getRenderState() {
        return renderState;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public SubmitNodeCollector getSubmitNodeCollector() {
        return submitNodeCollector;
    }

    public CameraRenderState getCameraRenderState() {
        return cameraRenderState;
    }

    public static class Post<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLivingEvent<T, S, M> {
        public Post(LivingEntity entity, LivingEntityRenderer<T, S, M> renderer, float partialTick, S renderState) {
            super(entity, renderer, partialTick, renderState);
        }

        public Post(LivingEntity entity, LivingEntityRenderer<T, S, M> renderer, float partialTick, S renderState,
                    PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
            super(entity, renderer, partialTick, renderState, poseStack, submitNodeCollector, cameraRenderState);
        }
    }
}
