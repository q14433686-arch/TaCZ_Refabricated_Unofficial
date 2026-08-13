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
import me.xjqsh.lrtactical.api.animation.ThrowableAnimationStateContext;
import me.xjqsh.lrtactical.client.audio.ICustomSoundSupplier;
import me.xjqsh.lrtactical.client.renderer.model.CustomBedrockModel;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * 一种投掷物的<b>客户端展示数据</b>。
 *
 * <p>结构与 {@link MeleeDisplayInstance} 完全平行，仅动画上下文类型不同
 * （投掷物需要 using/usingTick 来驱动拔销与蓄力动画）。
 * 两处 26.2 移植要点（{@code ItemTransforms} 不可反序列化、legacy 版本判断漏 else）
 * 的详细说明见 {@link MeleeDisplayInstance} 的类注释，此处不重复。
 *
 * <h2>模型类型为什么是 {@link CustomBedrockModel} 而不是 {@code BedrockAnimatedModel}</h2>
 * 上游此处的字段类型写的是 {@code BedrockAnimatedModel}，但实际 {@code new} 的是
 * {@code CustomBedrockModel}。这带来一个直接后果：
 * {@code ThrowableEntityRenderer} 想调 {@code setEntityRendering(true)}
 * （即隐藏 {@code entity_hide} 组）时，必须再做一次 {@code instanceof} 向下转型。
 * 这里把字段类型收紧为实际类型，转型随之消失，
 * {@code entity_hide} 的语义也不会因为某处忘了转型而静默失效。
 */
public class ThrowableDisplayInstance implements ICustomSoundSupplier {
    private Identifier id;
    private CustomBedrockModel model;
    private LuaAnimationStateMachine<ThrowableAnimationStateContext> stateMachine;
    private Identifier texture;
    @Nullable
    private Identifier slotTexture;
    private ItemTransforms transforms = ItemTransforms.NO_TRANSFORMS;
    private Map<String, Identifier> sounds;

    private ThrowableDisplayInstance() {
    }

    public Identifier getId() {
        return id;
    }

    public CustomBedrockModel getModel() {
        return model;
    }

    public LuaAnimationStateMachine<ThrowableAnimationStateContext> getStateMachine() {
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

    @Override
    public Map<String, Identifier> getSounds() {
        return sounds;
    }

    @NotNull
    public static ThrowableDisplayInstance create(ThrowableDisplay pojo, Identifier id) throws IllegalArgumentException {
        ThrowableDisplayInstance display = new ThrowableDisplayInstance();
        display.id = id;

        Preconditions.checkArgument(pojo != null, "display object is empty");
        Preconditions.checkArgument(pojo.modelLocation != null, "display object missing model field");
        Preconditions.checkArgument(pojo.stateMachineLocation != null, "display object missing state_machine field");
        Preconditions.checkArgument(pojo.textureLocation != null, "display object missing texture field");
        Preconditions.checkArgument(pojo.animationLocation != null, "display object missing animation field");

        BedrockModelPOJO modelPOJO = ClientAssetsManager.INSTANCE.getBedrockModelPOJO(pojo.modelLocation);
        Preconditions.checkArgument(modelPOJO != null, "no corresponding model found for " + pojo.modelLocation);

        // 见 MeleeDisplayInstance 类注释：上游此处漏了 else
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

        display.stateMachine = new LuaStateMachineFactory<ThrowableAnimationStateContext>()
                .setController(controller)
                .setLuaScripts(script)
                .build();

        display.texture = DisplayPaths.toTexturePath(pojo.textureLocation);
        display.slotTexture = DisplayPaths.toTexturePath(pojo.slotTextureLocation);
        display.transforms = BlockTransformParser.parse(pojo.transforms);
        display.sounds = Objects.requireNonNullElseGet(pojo.sounds, Maps::newHashMap);

        return display;
    }

    public record ThrowableDisplay(
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
            @SerializedName("sounds")
            Map<String, Identifier> sounds
    ) {
    }
}
