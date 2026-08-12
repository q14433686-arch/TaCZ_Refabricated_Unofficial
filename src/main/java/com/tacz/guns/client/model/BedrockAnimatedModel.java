package com.tacz.guns.client.model;

import com.tacz.guns.api.client.animation.AnimationListener;
import com.tacz.guns.api.client.animation.AnimationListenerSupplier;
import com.tacz.guns.api.client.animation.ObjectAnimationChannel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.bedrock.ModelRendererWrapper;
import com.tacz.guns.client.model.listener.camera.CameraAnimationObject;
import com.tacz.guns.client.model.listener.constraint.ConstraintObject;
import com.tacz.guns.client.model.listener.model.ModelRotateListener;
import com.tacz.guns.client.model.listener.model.ModelScaleListener;
import com.tacz.guns.client.model.listener.model.ModelTranslateListener;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import com.tacz.guns.client.resource.pojo.model.BonesItem;
import org.joml.Quaternionf;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.tacz.guns.client.model.GunModelConstant.ROOT_NODE;

public class BedrockAnimatedModel extends BedrockModel implements AnimationListenerSupplier {
    public static final String CAMERA_NODE_NAME = "camera";
    public static final String CONSTRAINT_NODE = "constraint";
    private final CameraAnimationObject cameraAnimationObject = new CameraAnimationObject();
    /**
     * 动画约束组的路径
     */
    protected @Nullable List<BedrockPart> constraintPath;
    private @Nullable ConstraintObject constraintObject;

    // 根组
    protected @Nullable BedrockPart root;
    // 第一人称idle状态摄像机定位组的路径
    protected @Nullable List<BedrockPart> idleSightPath;

    public BedrockAnimatedModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
        // 初始化相机动画对象
        ModelRendererWrapper cameraRendererWrapper = modelMap.get(CAMERA_NODE_NAME);
        if (cameraRendererWrapper != null) {
            cameraAnimationObject.cameraRenderer = cameraRendererWrapper;
        }
        // 初始化动画约束对象
        constraintPath = getPath(modelMap.get(CONSTRAINT_NODE));
        if (constraintPath != null) {
            constraintObject = new ConstraintObject();
            BedrockPart constraintNode = constraintPath.get(constraintPath.size() - 1);
            if (shouldRender.contains(constraintNode)) {
                constraintObject.bonesItem = indexBones.get(CONSTRAINT_NODE);
            } else {
                constraintObject.node = constraintNode;
            }
        }
        root = Optional.ofNullable(modelMap.get(ROOT_NODE)).map(ModelRendererWrapper::getModelRenderer).orElse(null);
        idleSightPath = getPath(modelMap.get("idle_view"));
    }


    @Nullable
    public List<BedrockPart> getConstraintPath() {
        return constraintPath;
    }

    public void cleanAnimationTransform() {
        for (ModelRendererWrapper rendererWrapper : modelMap.values()) {
            rendererWrapper.setOffsetX(0);
            rendererWrapper.setOffsetY(0);
            rendererWrapper.setOffsetZ(0);
            rendererWrapper.getAdditionalQuaternion().set(0, 0, 0, 1);
            rendererWrapper.setScaleX(1);
            rendererWrapper.setScaleY(1);
            rendererWrapper.setScaleZ(1);
        }
        if (constraintObject != null) {
            constraintObject.rotationConstraint.set(0, 0, 0);
            constraintObject.translationConstraint.set(0, 0, 0);
        }
    }

    /** 【RecoilDebug】孤儿清理日志节流器（2s），避免非枪械路径持有时刷爆日志。 */
    private static volatile long recoilDebugLastOrphanLogMs = 0;

    public void cleanCameraAnimationTransform() {
        // 【RecoilDebug 探针】本方法按枪械渲染约定只应被第一人称渲染路径在
        // 消费完摄像机动画数据后调用（调用前 30ms 内必有 GunItemRendererWrapper 心跳）。
        // 若心跳缺席，说明存在**第二条消费者**（其他渲染 pass/其他渲染器）在清倒
        // rotationQuaternion —— 它同时解释了「本次值为何与上次写入对不上」。
        if (com.tacz.guns.config.client.RenderConfig.RECOIL_DEBUG.get()
                && !com.tacz.guns.client.renderer.item.GunItemRendererWrapper.recoilDebugExpectedCleanOwner()) {
            long now = System.currentTimeMillis();
            if (now - recoilDebugLastOrphanLogMs > 2000) {
                recoilDebugLastOrphanLogMs = now;
                StackTraceElement[] st = Thread.currentThread().getStackTrace();
                StringBuilder sb = new StringBuilder();
                for (int i = 2; i < Math.min(st.length, 10); i++) {
                    sb.append(st[i].getClassName()).append('.').append(st[i].getMethodName());
                    if (i < Math.min(st.length, 10) - 1) {
                        sb.append(" <= ");
                    }
                }
                com.tacz.guns.GunMod.LOGGER.info("[TACZ RecoilDebug] cleanOrphan caller={}", sb);
            }
        }
        cameraAnimationObject.rotationQuaternion = new Quaternionf(0.0F, 0.0F, 0.0F, 1.0F);
    }

    /**
     * @param node     想要进行编程渲染流程的 node 名称
     * @param function 输入为 BedrockPart，返回 IModelRenderer 以替换渲染
     */
    public void setFunctionalRenderer(String node, Function<BedrockPart, IFunctionalRenderer> function) {
        ModelRendererWrapper wrapper = modelMap.get(node);
        if (wrapper == null) {
            FunctionalBedrockPart functionalPart = new FunctionalBedrockPart(function, node);
            modelMap.put(node, new ModelRendererWrapper(functionalPart));
        } else if (wrapper.getModelRenderer() instanceof FunctionalBedrockPart functionalPart) {
            functionalPart.functionalRenderer = function;
        }
    }

    @Nonnull
    public CameraAnimationObject getCameraAnimationObject() {
        return cameraAnimationObject;
    }

    @Nullable
    public ConstraintObject getConstraintObject() {
        return constraintObject;
    }

    @Override
    protected void loadNewModel(BedrockModelPOJO pojo) {
        assert pojo.getGeometryModelNew() != null;
        pojo.getGeometryModelNew().deco();
        if (pojo.getGeometryModelNew().getBones() == null) {
            return;
        }
        for (BonesItem bones : pojo.getGeometryModelNew().getBones()) {
            // 将 FunctionalBedrockPart 先塞入 modelMap 中，以支持 functionalRender 操作
            FunctionalBedrockPart bedrockPart = new FunctionalBedrockPart(null, bones.getName());
            modelMap.putIfAbsent(bones.getName(), new ModelRendererWrapper(bedrockPart));
        }
        super.loadNewModel(pojo);
    }

    @Override
    protected void loadLegacyModel(BedrockModelPOJO pojo) {
        assert pojo.getGeometryModelLegacy() != null;
        pojo.getGeometryModelLegacy().deco();
        if (pojo.getGeometryModelLegacy().getBones() == null) {
            return;
        }
        for (BonesItem bones : pojo.getGeometryModelLegacy().getBones()) {
            // 将 FunctionalBedrockPart 先塞入 modelMap 中，以支持 functionalRender 操作
            FunctionalBedrockPart bedrockPart = new FunctionalBedrockPart(null, bones.getName());
            modelMap.putIfAbsent(bones.getName(), new ModelRendererWrapper(bedrockPart));
        }
        super.loadLegacyModel(pojo);
    }

    @Override
    public AnimationListener supplyListeners(String nodeName, ObjectAnimationChannel.ChannelType type) {
        ModelRendererWrapper model = modelMap.get(nodeName);
        if (model == null) {
            return null;
        }
        AnimationListener cameraListener = cameraAnimationObject.supplyListeners(nodeName, type);
        if (cameraListener != null) {
            return cameraListener;
        }
        if (constraintObject != null) {
            AnimationListener constraintListener = constraintObject.supplyListeners(nodeName, type);
            if (constraintListener != null) {
                return constraintListener;
            }
        }
        if (type.equals(ObjectAnimationChannel.ChannelType.TRANSLATION)) {
            return new ModelTranslateListener(this, model, nodeName);
        }
        if (type.equals(ObjectAnimationChannel.ChannelType.ROTATION)) {
            return new ModelRotateListener(model);
        }
        if (type.equals(ObjectAnimationChannel.ChannelType.SCALE)) {
            return new ModelScaleListener(model);
        }
        return null;
    }

    public boolean getRenderHand() {
        return true;
    }

    public BedrockPart getRootNode() {
        return root;
    }

    public List<BedrockPart> getIdleSightPath() {
        return idleSightPath;
    }
}