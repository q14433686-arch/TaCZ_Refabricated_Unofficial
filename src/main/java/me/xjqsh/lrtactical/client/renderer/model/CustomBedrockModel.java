package me.xjqsh.lrtactical.client.renderer.model;

import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.FunctionalBedrockPart;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.bedrock.ModelRendererWrapper;
import com.tacz.guns.client.model.functional.LeftHandRender;
import com.tacz.guns.client.model.functional.RightHandRender;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.regex.Pattern;

import static com.tacz.guns.client.model.GunModelConstant.LEFTHAND_POS_NODE;
import static com.tacz.guns.client.model.GunModelConstant.RIGHTHAND_POS_NODE;

/**
 * LRTactical 通用的基岩版动画模型：在 TACZ 的 {@link BedrockAnimatedModel} 之上，
 * 补两类由<b>模型组名</b>驱动的可见性开关。
 *
 * <table border="1">
 *   <caption>识别的特殊组名</caption>
 *   <tr><th>组名（正则）</th><th>作用</th></tr>
 *   <tr><td>{@code 1p_effect} / {@code 1p_effect_<n>}</td>
 *       <td><b>仅第一人称</b>可见的装饰件（刀身反光、握把细节等）</td></tr>
 *   <tr><td>{@code entity_hide} / {@code entity_hide_<n>}</td>
 *       <td>作为<b>掉落/飞行实体</b>渲染时隐藏的部件（如拉环、保险销）</td></tr>
 * </table>
 *
 * <h2>26.2 可行性核对（这是本类能原样移植的前提）</h2>
 * 本类依赖的机制是「{@code functionalRenderer} 返回 {@code null}，只借这次回调改
 * {@code part.visible}」。26.2 的渲染管线已从「即时写 VertexConsumer」改为
 * 「先 {@code BedrockRenderSnapshot.capture} 提取、后 {@code SubmitNodeCollector} 提交」，
 * 因此必须确认这种「可见性钩子」在新管线下仍会被调用。
 *
 * <p>字节码/源码确认（{@code BedrockRenderSnapshot#capturePart}）：
 * <pre>
 * // FunctionalBedrockPart always evaluates its provider, even if visible is false. Providers
 * // returning null are visibility/state hooks and can be snapshotted normally.
 * if (part instanceof FunctionalBedrockPart functional &amp;&amp; functional.functionalRenderer != null) {
 *     legacyFunctionalRenderer = functional.functionalRenderer.apply(part);   // 仍会调用
 * }
 * ...
 * if (legacyFunctionalRenderer != null) { ... }   // 返回 null 则落到下面的常规几何提取
 * if (part.visible) { ...emit DrawCommand... }    // 读的正是我们刚改过的 visible
 * </pre>
 * 即：provider <b>先于</b> {@code visible} 判定执行，返回 {@code null} 时走常规提取路径。
 * 这正是本类所需的语义，<b>无需改动</b>。
 *
 * <p>（对照：{@code LeftHandRender}/{@code RightHandRender} 属于「返回真实渲染器」的那一类，
 * 26.2 已把它们改成实现 {@code IFunctionalSubmitter} 走 collector 分支。本类通过
 * {@code setFunctionalRenderer} 挂载它们，拿到的是 TACZ 侧<b>已适配好</b>的实现。）
 */
public class CustomBedrockModel extends BedrockAnimatedModel {
    private static final Pattern FIRSTPERSON_EFFECT_PATTERN = Pattern.compile("^1p_effect(_(\\d+))?$");
    private static final Pattern ENTITY_HIDE_PATTERN = Pattern.compile("^entity_hide(_(\\d+))?$");

    private boolean effectVisible = false;
    private boolean entityRendering = false;

    public CustomBedrockModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
        // 第一人称左右手手臂：直接复用 TACZ 已适配 26.2 的实现
        this.setFunctionalRenderer(LEFTHAND_POS_NODE, bedrockPart -> new LeftHandRender(this));
        this.setFunctionalRenderer(RIGHTHAND_POS_NODE, bedrockPart -> new RightHandRender(this));

        for (Map.Entry<String, ModelRendererWrapper> entry : modelMap.entrySet()) {
            if (FIRSTPERSON_EFFECT_PATTERN.matcher(entry.getKey()).find()) {
                if (entry.getValue().getModelRenderer() instanceof FunctionalBedrockPart functionalPart) {
                    functionalPart.functionalRenderer = this::renderEffect;
                }
            } else if (ENTITY_HIDE_PATTERN.matcher(entry.getKey()).find()) {
                if (entry.getValue().getModelRenderer() instanceof FunctionalBedrockPart functionalPart) {
                    functionalPart.functionalRenderer = this::renderEntityHide;
                }
            }
        }
    }

    /** 返回 null = 「只改可见性，几何仍按常规路径提取」。见类注释。 */
    @Nullable
    private IFunctionalRenderer renderEffect(BedrockPart part) {
        part.visible = effectVisible;
        return null;
    }

    @Nullable
    private IFunctionalRenderer renderEntityHide(BedrockPart part) {
        part.visible = !entityRendering;
        return null;
    }

    public void setEffectVisible(boolean visible) {
        this.effectVisible = visible;
    }

    public boolean isEffectVisible() {
        return effectVisible;
    }

    public void setEntityRendering(boolean entityRendering) {
        this.entityRendering = entityRendering;
    }

    public boolean isEntityRendering() {
        return entityRendering;
    }
}
