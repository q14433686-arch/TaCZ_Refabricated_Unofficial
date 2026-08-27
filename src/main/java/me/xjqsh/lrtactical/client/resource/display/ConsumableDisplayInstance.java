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
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Objects;

/**
 * 一种消耗品的<b>客户端展示数据</b>。
 *
 * <p>结构与 {@link MeleeDisplayInstance} / {@link ThrowableDisplayInstance} 完全平行，
 * 仅动画上下文类型不同（消耗品需要 using/usingTick 驱动「喝药 / 打针」的分段动画）。
 * 两处移植要点（{@code ItemTransforms} 不可反序列化、legacy 版本判断漏 else）
 * 的详细说明见 {@link MeleeDisplayInstance} 的类注释，此处不重复。
 *
 * <h2>官方 0.4.3 的 {@code third_person_animation} 未实现</h2>
 * 那是 player_animator 的第三人称层，需要额外的内容包契约、且可能与本仓
 * 第一人称动画兼容层抢同一条手臂链。JSON 里出现该字段时<b>被静默忽略</b>
 * （Gson 对未声明的字段不报错），不会导致 display 加载失败 ——
 * 这是刻意的降级，不是「已支持」。
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

    /** 官方 0.4.3 的 {@code display_offset}，语义见 {@link MeleeDisplayInstance#getDisplayOffset()}。 */
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

    /**
     * 反序列化 POJO。
     *
     * <p>{@code transforms} 刻意用 {@link JsonObject} 而非 {@code ItemTransforms} —— 见类注释。
     */
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
