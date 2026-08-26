package me.xjqsh.lrtactical.client.resource.display;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.Animations;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.client.animation.statemachine.LuaStateMachineFactory;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.display.block.BlockTransformParser;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import me.xjqsh.lrtactical.api.animation.ConsumableAnimationStateContext;
import me.xjqsh.lrtactical.client.audio.ICustomSoundSupplier;
import me.xjqsh.lrtactical.client.renderer.model.CustomBedrockModel;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Objects;

/**
 * 消耗品的客户端展示数据。结构与 {@link MeleeDisplayInstance} 平行。
 *
 * <p>26.2 移植要点（{@code ItemTransforms} 不可反序列化、legacy 版本判断漏 else）
 * 的详细说明见 {@link MeleeDisplayInstance} 的类注释，此处不重复。
 *
 * <p>官方 0.4.3 还有 {@code third_person_animation}（player_animator 层）。
 * 那一套本轮只调查、不接入，JSON 里出现该字段时直接忽略。
 */
public class ConsumableDisplayInstance implements ICustomSoundSupplier {
    private Identifier id;
    private CustomBedrockModel model;
    private LuaAnimationStateMachine<ConsumableAnimationStateContext> stateMachine;
    private Identifier texture;
    @Nullable
    private Identifier slotTexture;
    private ItemTransforms transforms = ItemTransforms.NO_TRANSFORMS;
    private Vector3f displayOffset = new Vector3f();
    private Map<String, Identifier> sounds;

    private ConsumableDisplayInstance() {
    }

    public Identifier getId() {
        return id;
    }

    public CustomBedrockModel getModel() {
        return model;
    }

    public LuaAnimationStateMachine<ConsumableAnimationStateContext> getStateMachine() {
        return stateMachine;
    }

    public Identifier getTexture() {
        return texture;
    }

    @Nullable
    public Identifier getSlotTexture() {
        return slotTexture;
    }

    public ItemTransforms getTransforms() {
        return transforms;
    }

    public Vector3f getDisplayOffset() {
        return displayOffset;
    }

    @Override
    public Map<String, Identifier> getSounds() {
        return sounds;
    }

    @NotNull
    public static ConsumableDisplayInstance create(ConsumableDisplay pojo, Identifier id) throws IllegalArgumentException {
        ConsumableDisplayInstance display = new ConsumableDisplayInstance();
        display.id = id;

        Preconditions.checkArgument(pojo != null, "display object is empty");
        Preconditions.checkArgument(pojo.modelLocation != null, "display object missing model field");
        Preconditions.checkArgument(pojo.stateMachineLocation != null, "display object missing state_machine field");
        Preconditions.checkArgument(pojo.textureLocation != null, "display object missing texture field");
        Preconditions.checkArgument(pojo.animationLocation != null, "display object missing animation field");

        BedrockModelPOJO modelPOJO = ClientAssetsManager.INSTANCE.getBedrockModelPOJO(pojo.modelLocation);
        Preconditions.checkArgument(modelPOJO != null, "no corresponding model found for " + pojo.modelLocation);

        // 见 MeleeDisplayInstance 类注释：上游此处漏了 else，legacy 模型会被 NEW 覆盖
        if (BedrockVersion.isLegacyVersion(modelPOJO)) {
            display.model = new CustomBedrockModel(modelPOJO, BedrockVersion.LEGACY);
        } else {
            display.model = new CustomBedrockModel(modelPOJO, BedrockVersion.NEW);
        }

        var animation = ClientAssetsManager.INSTANCE.getBedrockAnimations(pojo.animationLocation);
        Preconditions.checkArgument(animation != null, "no corresponding animation found for " + pojo.animationLocation);
        AnimationController controller = Animations.createControllerFromBedrock(animation, display.model);

        var script = ClientAssetsManager.INSTANCE.getScript(pojo.stateMachineLocation);
        Preconditions.checkArgument(script != null, "no corresponding state machine found for " + pojo.stateMachineLocation);

        display.stateMachine = new LuaStateMachineFactory<ConsumableAnimationStateContext>()
                .setController(controller)
                .setLuaScripts(script)
                .build();

        display.texture = DisplayPaths.toTexturePath(pojo.textureLocation);
        display.slotTexture = DisplayPaths.toTexturePath(pojo.slotTextureLocation);
        display.transforms = BlockTransformParser.parse(pojo.transforms);
        display.displayOffset = Objects.requireNonNullElseGet(pojo.displayOffset, Vector3f::new);
        display.sounds = Objects.requireNonNullElseGet(pojo.sounds, Maps::newHashMap);

        return display;
    }

    public record ConsumableDisplay(
            @SerializedName("model")
            Identifier modelLocation,
            @SerializedName("animation")
            Identifier animationLocation,
            @SerializedName("state_machine")
            Identifier stateMachineLocation,
            @SerializedName("texture")
            Identifier textureLocation,
            @SerializedName("slot_texture")
            Identifier slotTextureLocation,
            @SerializedName("transforms")
            JsonObject transforms,
            @SerializedName("display_offset")
            Vector3f displayOffset,
            @SerializedName("sounds")
            Map<String, Identifier> sounds
    ) {
    }
}
