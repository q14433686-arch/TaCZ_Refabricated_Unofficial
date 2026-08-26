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
import me.xjqsh.lrtactical.api.animation.BaseAnimationStateContext;
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
 * 一把近战武器的<b>客户端展示数据</b>（模型 / 动画 / 状态机 / 贴图 / 变换 / 音效）。
 *
 * <p>由内容包的 {@code assets/<ns>/display/melee/<name>.json} 反序列化而来。
 * 注意它与已完成的 {@code data/<ns>/index/melee/*}（服务端数据）是<b>两套独立通道</b>：
 * 前者走资源包（客户端、可被材质包覆盖），后者走数据包（服务端权威、需要网络同步）。
 *
 * <h2>26.2 移植要点</h2>
 * <ol>
 *   <li><b>{@code ItemTransforms} 不能再由 Gson 直接反序列化。</b>
 *       上游在 {@code LrClientAssetsManager} 里注册了
 *       {@code registerTypeAdapter(ItemTransforms.class, new ItemTransforms.Deserializer())}。
 *       26.2 上这两个 {@code Deserializer} 类<b>虽然还是 public，但构造器已降为包级私有</b>
 *       （字节码确认：{@code <init>()V} 的 access flags 不含 {@code ACC_PUBLIC}）——
 *       典型的「存在 ≠ 能用」，外部包 {@code new} 不出来。
 *       <p>本仓库为完全相同的问题（{@code BlockDisplay} 的 transforms）已经写过
 *       {@link BlockTransformParser}，按 26.2 反编译源逐行重实现了解析。
 *       这里<b>直接复用它</b>：POJO 字段声明为 {@link JsonObject}（与
 *       {@code BlockDisplay#transforms} 一致），加载时再交给该解析器。</li>
 *   <li><b>解析失败不抛异常</b>：{@code BlockTransformParser.parse(null)} 返回
 *       {@code NO_TRANSFORMS}，与本仓库 {@code ClientBlockIndex#checkTransforms}
 *       「宁可回退默认值，也不让整个内容包加载不出来」的取舍保持一致。</li>
 *   <li>{@code ResourceLocation} → {@link Identifier}。</li>
 * </ol>
 *
 * <h2>修正上游的一处真 bug</h2>
 * 上游判断基岩版模型版本时写的是：
 * <pre>
 * if (BedrockVersion.isLegacyVersion(modelPOJO)) {
 *     display.model = new CustomBedrockModel(modelPOJO, BedrockVersion.LEGACY);
 * }
 * display.model = new CustomBedrockModel(modelPOJO, BedrockVersion.NEW);   // 缺 else！
 * </pre>
 * 少了 {@code else}，导致 legacy 模型<b>先按 LEGACY 建一次、立刻被 NEW 覆盖</b> ——
 * 旧格式（{@code format_version 1.10.0}）的模型全部按新格式解析，结果要么错位要么整个不显示。
 * 这里补上 {@code else}（TACZ 自身的 {@code GunDisplayInstance} 就是带 else 的正确写法）。
 */
public class MeleeDisplayInstance implements ICustomSoundSupplier {
    private Identifier id;
    private CustomBedrockModel model;
    private LuaAnimationStateMachine<BaseAnimationStateContext> stateMachine;
    private Identifier texture;
    @Nullable
    private Identifier slotTexture;
    private ItemTransforms transforms = ItemTransforms.NO_TRANSFORMS;
    private Vector3f displayOffset = new Vector3f();
    private Map<String, Identifier> sounds;

    private MeleeDisplayInstance() {
    }

    public Identifier getId() {
        return id;
    }

    public CustomBedrockModel getModel() {
        return model;
    }

    public LuaAnimationStateMachine<BaseAnimationStateContext> getStateMachine() {
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
    public static MeleeDisplayInstance create(MeleeDisplay pojo, Identifier id) throws IllegalArgumentException {
        MeleeDisplayInstance display = new MeleeDisplayInstance();
        display.id = id;

        Preconditions.checkArgument(pojo != null, "display object is empty");
        Preconditions.checkArgument(pojo.modelLocation != null, "display object missing model field");
        Preconditions.checkArgument(pojo.stateMachineLocation != null, "display object missing state_machine field");
        Preconditions.checkArgument(pojo.textureLocation != null, "display object missing texture field");
        Preconditions.checkArgument(pojo.animationLocation != null, "display object missing animation field");

        BedrockModelPOJO modelPOJO = ClientAssetsManager.INSTANCE.getBedrockModelPOJO(pojo.modelLocation);
        Preconditions.checkArgument(modelPOJO != null, "no corresponding model found for " + pojo.modelLocation);

        // 见类注释：上游此处漏了 else，legacy 模型会被 NEW 覆盖
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

        display.stateMachine = new LuaStateMachineFactory<BaseAnimationStateContext>()
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
     * <p>{@code transforms} 刻意用 {@link JsonObject} 而非 {@code ItemTransforms} —— 见类注释。
     */
    public record MeleeDisplay(
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
