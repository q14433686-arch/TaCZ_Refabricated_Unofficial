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
 * 一种消耗品（药品 / 食物）的<b>客户端展示数据</b>。结构与 {@link MeleeDisplayInstance} 平行。
 *
 * <p>由内容包的 {@code assets/<ns>/display/consumable/<name>.json} 反序列化而来，
 * 加载入口是 {@link me.xjqsh.lrtactical.client.resource.manager.ConsumableDisplayManager}。</p>
 *
 * <h2>为什么现在才补上</h2>
 * 本仓早就打包了 {@code assets/lrtactical/scripts/consumable_state_machine.lua}
 * 与 {@code data/lrtactical/index/consumable/*}，但一直<b>没有消耗品的渲染通道</b> ——
 * 服务端效果（{@code ConsumableItem}）能跑，客户端却只能显示原版占位模型，
 * 那份 Lua 状态机因此是死代码。官方 0.4.3 补齐了消耗品的第一人称 Bedrock/Lua 渲染，
 * 本轮同步过来，Lua 与 index 才真正接上。
 *
 * <h2>官方 0.4.3 里<b>没有</b>照搬的部分</h2>
 * 官方还有 {@code third_person_animation}（player_animator 层）。姊妹仓
 * TaCZ_Renovated 的 0.4.3 跟进文档把它列为「只调查、不接入」：需要内容包提供
 * player_animator 文件，且可能与 TACZ 的 PAL 层抢轨道。本仓同此结论 ——
 * JSON 里出现该字段时<b>直接忽略</b>，不解析、不报错。
 */
public class ConsumableDisplayInstance implements ICustomSoundSupplier {
    private Identifier id;
    private CustomBedrockModel model;
    private LuaAnimationStateMachine<ConsumableAnimationStateContext> stateMachine;
    private Identifier texture;
    @Nullable
    private Identifier slotTexture;
    private ItemTransforms transforms = ItemTransforms.NO_TRANSFORMS;
    /** 官方 0.4.3 {@code display_offset}；未配置时为零向量。 */
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

    /**
     * 校验并解析。失败一律用 {@code Preconditions} 抛 {@code IllegalArgumentException}，
     * 由 {@code ConsumableDisplayManager#apply} 逐个 catch —— 一个内容包写错一件消耗品，
     * 不该让整个资源重载失败。
     */
    @NotNull
    public static ConsumableDisplayInstance create(ConsumableDisplay pojo, Identifier id)
            throws IllegalArgumentException {
        ConsumableDisplayInstance display = new ConsumableDisplayInstance();
        display.id = id;

        Preconditions.checkArgument(pojo != null, "display object is empty");
        Preconditions.checkArgument(pojo.modelLocation != null, "display object missing model field");
        Preconditions.checkArgument(pojo.stateMachineLocation != null, "display object missing state_machine field");
        Preconditions.checkArgument(pojo.textureLocation != null, "display object missing texture field");
        Preconditions.checkArgument(pojo.animationLocation != null, "display object missing animation field");

        BedrockModelPOJO modelPOJO = ClientAssetsManager.INSTANCE.getBedrockModelPOJO(pojo.modelLocation);
        Preconditions.checkArgument(modelPOJO != null, "no corresponding model found for " + pojo.modelLocation);

        // 见 MeleeDisplayInstance 类注释：legacy 版本必须单独一支，
        // 否则 format_version 1.10.0 的模型会被按新格式解析而错位/不显示。
        if (BedrockVersion.isLegacyVersion(modelPOJO)) {
            display.model = new CustomBedrockModel(modelPOJO, BedrockVersion.LEGACY);
        } else {
            display.model = new CustomBedrockModel(modelPOJO, BedrockVersion.NEW);
        }

        var animation = ClientAssetsManager.INSTANCE.getBedrockAnimations(pojo.animationLocation);
        Preconditions.checkArgument(animation != null, "no corresponding animation found for " + pojo.animationLocation);
        AnimationController controller = Animations.createControllerFromBedrock(animation, display.model);

        var script = ClientAssetsManager.INSTANCE.getScript(pojo.stateMachineLocation);
        Preconditions.checkArgument(script != null,
                "no corresponding state machine found for " + pojo.stateMachineLocation);

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
     * display JSON 的原始结构。
     *
     * <p>{@code transforms} 用 {@link JsonObject} 而非 {@code ItemTransforms}，
     * 理由同 {@link MeleeDisplayInstance.MeleeDisplay}（TACZ 的
     * {@code BlockTransformParser} 才认这套字段名）。</p>
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
