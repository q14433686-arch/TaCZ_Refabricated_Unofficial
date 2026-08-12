package com.tacz.guns.client.model.functional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockAmmoModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.IFunctionalSubmitter;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.resource.pojo.display.gun.ShellEjection;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.concurrent.ConcurrentLinkedDeque;

public class ShellRender implements IFunctionalSubmitter {
    // 抛壳队列
    private final ConcurrentLinkedDeque<Data> SHELL_QUEUE = new ConcurrentLinkedDeque<>();
    public static boolean isSelf = false;
    private static long lastDebugLogTime = 0L;

    private final BedrockGunModel bedrockGunModel;
    private final String shellNodeName;
    private final int shellNodeIndex;

    public ShellRender(BedrockGunModel bedrockGunModel) {
        this(bedrockGunModel, "shell", 0);
    }

    public ShellRender(BedrockGunModel bedrockGunModel, String shellNodeName, int shellNodeIndex) {
        this.bedrockGunModel = bedrockGunModel;
        this.shellNodeName = shellNodeName;
        this.shellNodeIndex = shellNodeIndex;
    }

    public void addShell(Vector3f randomVelocity) {
        if (SHELL_QUEUE.size() > 128) {
            SHELL_QUEUE.pollFirst();
        }
        double xRandom = Math.random() * randomVelocity.x();
        double yRandom = Math.random() * randomVelocity.y();
        double zRandom = Math.random() * randomVelocity.z();
        Vector3f vector3f = new Vector3f((float) xRandom, (float) yRandom, (float) zRandom);
        SHELL_QUEUE.offerLast(new Data(System.currentTimeMillis(), vector3f));
    }

    private void renderShell(GunDisplayInstance display, GunData gunData, PoseStack poseStack, BedrockGunModel gunModel) {
        // 【RecoilDebug 隔离】第 27.4 轮：旧(delegateRender)抛壳路径同样受运行时开关控制
        if (RenderConfig.DEBUG_DISABLE_SHELL != null && RenderConfig.DEBUG_DISABLE_SHELL.get()) {
            return;
        }
        ShellEjection shellEjection = display.getShellEjection();
        if (shellEjection == null) {
            SHELL_QUEUE.clear();
            return;
        }
        TimelessAPI.getClientAmmoIndex(gunData.getAmmoId()).ifPresent(ammoIndex -> {
            BedrockAmmoModel model = ammoIndex.getShellModel();
            if (model == null) {
                return;
            }
            Identifier location = ammoIndex.getShellTextureLocation();
            if (location == null) {
                return;
            }
            long lifeTime = (long) (shellEjection.getLivingTime() * 1000);

            // 检查有没有需要踢出去的队列
            checkShellQueue(lifeTime);

            // 各种参数的获取
            Vector3f initialVelocity = shellEjection.getInitialVelocity();
            Vector3f acceleration = shellEjection.getAcceleration();
            Vector3f angularVelocity = shellEjection.getAngularVelocity();

            // 缓存一下 PoseStack
            for (Data data : SHELL_QUEUE) {
                if (data.normal == null && data.pose == null) {
                    data.normal = new Matrix3f(poseStack.last().normal());
                    data.pose = new Matrix4f(poseStack.last().pose());
                }
            }

            // 渲染抛壳
            gunModel.delegateRender((poseStack1, vertexConsumer1, transformType1, light, overlay) -> {
                SHELL_QUEUE.forEach(data -> renderSingleShell(transformType1, light, overlay, data, initialVelocity, acceleration, angularVelocity, model, location));
            });
        });
    }

    private void renderSingleShell(ItemDisplayContext transformType1, int light, int overlay, Data data, Vector3f initialVelocity, Vector3f acceleration, Vector3f angularVelocity, BedrockAmmoModel model, Identifier location) {
        // 再检查一次
        if (data.normal == null && data.pose == null) {
            return;
        }
        // 先初始化到缓存位置和朝向
        PoseStack poseStack2 = new PoseStack();
        poseStack2.last().normal().mul(data.normal);
        poseStack2.last().pose().mul(data.pose);

        // 获取存留时间和各种参数
        long remindTime = System.currentTimeMillis() - data.timeStamp;
        double time = remindTime / 1000.0;
        Vector3f randomOffset = data.randomOffset;

        // 位移，满足标准的匀变速直线运动
        double x = (initialVelocity.x() + randomOffset.x()) * time + 0.5 * acceleration.x() * time * time;
        double y = (initialVelocity.y() + randomOffset.y()) * time + 0.5 * acceleration.y() * time * time;
        double z = (initialVelocity.z() + randomOffset.z()) * time + 0.5 * acceleration.z() * time * time;
        poseStack2.translate(-x, -y, z);

        // 旋转
        double xw = time * angularVelocity.x();
        double yw = time * angularVelocity.y();
        double zw = time * angularVelocity.z();
        poseStack2.mulPose(Axis.XN.rotationDegrees((float) xw));
        poseStack2.mulPose(Axis.YN.rotationDegrees((float) yw));
        poseStack2.mulPose(Axis.ZP.rotationDegrees((float) zw));
        poseStack2.translate(0, -1.5, 0);

        model.render(poseStack2, transformType1, shellRenderType(transformType1, location), light, overlay);
    }

    private static RenderType shellRenderType(ItemDisplayContext displayContext, Identifier texture) {
        // In Iris/Sulkan first-person hand passes, vanilla ENTITY_* pipelines may be assigned to
        // the world/entity program instead of the hand program.  Shells are visually part of the
        // held item in first person, so use the ITEM_CUTOUT pipeline there; third-person/world
        // rendering keeps the entity pipeline for vanilla parity.
        return displayContext.firstPerson()
                ? RenderTypes.itemCutout(texture)
                : RenderTypes.entityCutout(texture);
    }

    private void checkShellQueue(long lifeTime) {
        if (!SHELL_QUEUE.isEmpty()) {
            Data data = SHELL_QUEUE.peekFirst();
            if ((System.currentTimeMillis() - data.timeStamp) > lifeTime) {
                SHELL_QUEUE.pollFirst();
                checkShellQueue(lifeTime);
            }
        }
    }

    @Override
    public void extract(ExtractionContext context) {
        // 【RecoilDebug 隔离】第 27.4 轮：运行时关闭抛壳，用于定位斜向"后坐力固定侧偏"的视觉载体
        if (RenderConfig.DEBUG_DISABLE_SHELL != null && RenderConfig.DEBUG_DISABLE_SHELL.get()) {
            return;
        }
        if (IrisCompat.isRenderShadow() || !isSelf || !shellContextMatchesCamera(context.displayContext())) {
            return;
        }
        // 光影手部兼容：把实体管线显式归到 HAND，避免在 Iris hand pass 中不渲染/位置错
        if (IrisCompat.isHandRendererActive()) {
            IrisCompat.assignCommonEntityPipelinesToHandIfNeeded();
        }
        ItemStack currentGunItem = bedrockGunModel.getCurrentGunItem();
        IGun iGun = IGun.getIGunOrNull(currentGunItem);
        if (iGun == null) {
            return;
        }
        Identifier gunId = iGun.getGunId(currentGunItem);
        GunData gunData = TimelessAPI.getClientGunIndex(gunId)
                .map(ClientGunIndex::getGunData).orElse(null);
        GunDisplayInstance display = TimelessAPI.getGunDisplay(currentGunItem).orElse(null);
        if (gunData == null || display == null || display.getShellEjection() == null) {
            return;
        }

        ShellEjection shellEjection = display.getShellEjection();
        var ammoIndex = TimelessAPI.getClientAmmoIndex(gunData.getAmmoId()).orElse(null);
        if (ammoIndex == null || ammoIndex.getShellModel() == null || ammoIndex.getShellTextureLocation() == null) {
            return;
        }
        BedrockAmmoModel model = ammoIndex.getShellModel();
        Identifier texture = ammoIndex.getShellTextureLocation();
        long lifeTime = (long) (shellEjection.getLivingTime() * 1000);
        checkShellQueue(lifeTime);

        Vector3f initialVelocity = shellEjection.getInitialVelocity();
        Vector3f acceleration = shellEjection.getAcceleration();
        Vector3f angularVelocity = shellEjection.getAngularVelocity();
        PoseStack origin = context.poseStack();
        ItemDisplayContext displayContext = context.displayContext();
        int light = context.light();
        int overlay = context.overlay();
        boolean debug = shellDebugEnabled(gunId);

        for (Data data : SHELL_QUEUE) {
            if (data.normal == null || data.pose == null) {
                data.normal = new Matrix3f(origin.last().normal());
                data.pose = new Matrix4f(origin.last().pose());
                data.capturePitch = currentPitch();
                data.captureYaw = currentYaw();
                debugShell(gunId, "capture", displayContext, data, origin, 0, 0, 0,
                        initialVelocity, acceleration, angularVelocity, texture, debug);
            }
            long ageMs = System.currentTimeMillis() - data.timeStamp;
            double time = ageMs / 1000.0;
            Vector3f randomOffset = data.randomOffset;

            PoseStack frozenShellPose = new PoseStack();
            frozenShellPose.last().normal().set(data.normal);
            frozenShellPose.last().pose().set(data.pose);
            double x = (initialVelocity.x() + randomOffset.x()) * time + 0.5 * acceleration.x() * time * time;
            double y = (initialVelocity.y() + randomOffset.y()) * time + 0.5 * acceleration.y() * time * time;
            double z = (initialVelocity.z() + randomOffset.z()) * time + 0.5 * acceleration.z() * time * time;
            frozenShellPose.translate(-x, -y, z);
            frozenShellPose.mulPose(Axis.XN.rotationDegrees((float) (time * angularVelocity.x())));
            frozenShellPose.mulPose(Axis.YN.rotationDegrees((float) (time * angularVelocity.y())));
            frozenShellPose.mulPose(Axis.ZP.rotationDegrees((float) (time * angularVelocity.z())));
            frozenShellPose.translate(0, -1.5, 0);
            debugShell(gunId, "submit", displayContext, data, frozenShellPose, x, y, z,
                    initialVelocity, acceleration, angularVelocity, texture, debug);

            context.add(collector -> {
                PoseStack taskPose = new PoseStack();
                taskPose.last().pose().set(frozenShellPose.last().pose());
                taskPose.last().normal().set(frozenShellPose.last().normal());
                model.submit(taskPose, displayContext, collector, shellRenderType(displayContext, texture), light, overlay);
            });
        }
    }

    private static boolean shellContextMatchesCamera(ItemDisplayContext displayContext) {
        boolean cameraFirstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson();
        return cameraFirstPerson == displayContext.firstPerson();
    }

    private boolean shellDebugEnabled(Identifier gunId) {
        try {
            if (RenderConfig.SHELL_EJECTION_DEBUG == null || !RenderConfig.SHELL_EJECTION_DEBUG.get()) {
                return false;
            }
            String filter = RenderConfig.SHELL_EJECTION_DEBUG_GUN == null ? "" : RenderConfig.SHELL_EJECTION_DEBUG_GUN.get();
            if (filter == null || filter.isBlank()) {
                return true;
            }
            filter = filter.trim();
            return filter.equalsIgnoreCase(gunId.toString()) || filter.equalsIgnoreCase(gunId.getPath());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int shellDebugIntervalMs() {
        try {
            return RenderConfig.SHELL_EJECTION_DEBUG_INTERVAL_MS == null
                    ? 250
                    : RenderConfig.SHELL_EJECTION_DEBUG_INTERVAL_MS.get();
        } catch (Throwable ignored) {
            return 250;
        }
    }

    private void debugShell(Identifier gunId,
                            String phase,
                            ItemDisplayContext displayContext,
                            Data data,
                            PoseStack poseStack,
                            double x,
                            double y,
                            double z,
                            Vector3f initialVelocity,
                            Vector3f acceleration,
                            Vector3f angularVelocity,
                            Identifier texture,
                            boolean enabled) {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        int interval = shellDebugIntervalMs();
        if (now - lastDebugLogTime < interval) {
            return;
        }
        lastDebugLogTime = now;

        Matrix4f pose = poseStack.last().pose();
        Matrix4f captured = data.pose;
        GunMod.LOGGER.info("[TACZ ShellDebug] gun={} node={}#{} phase={} ctx={} irisHand={} shader={} pitch={} yaw={} capturePitch={} captureYaw={} ageMs={} offset=({},{},{}) poseT=({},{},{}) captureT=({},{},{}) random={} initial={} accel={} angular={} texture={} queue={}",
                gunId,
                shellNodeName,
                shellNodeIndex,
                phase,
                displayContext,
                IrisCompat.isHandRendererActive(),
                IrisCompat.isUsingRenderPack(),
                currentPitch(),
                currentYaw(),
                data.capturePitch,
                data.captureYaw,
                now - data.timeStamp,
                trim(x), trim(y), trim(z),
                trim(pose.m30()), trim(pose.m31()), trim(pose.m32()),
                captured == null ? "null" : trim(captured.m30()),
                captured == null ? "null" : trim(captured.m31()),
                captured == null ? "null" : trim(captured.m32()),
                data.randomOffset,
                initialVelocity,
                acceleration,
                angularVelocity,
                texture,
                SHELL_QUEUE.size());
    }

    private static float currentPitch() {
        var player = Minecraft.getInstance().player;
        return player == null ? Float.NaN : player.getXRot();
    }

    private static float currentYaw() {
        var player = Minecraft.getInstance().player;
        return player == null ? Float.NaN : player.getYRot();
    }

    private static String trim(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light, int overlay) {
        if (IrisCompat.isRenderShadow()) {
            return;
        }
        if (!isSelf || !shellContextMatchesCamera(transformType)) {
            return;
        }
        ItemStack currentGunItem = bedrockGunModel.getCurrentGunItem();
        IGun iGun = IGun.getIGunOrNull(currentGunItem);
        if (iGun == null) {
            return;
        }
        GunData gunData = TimelessAPI.getClientGunIndex(iGun.getGunId(currentGunItem)).map(ClientGunIndex::getGunData).orElse(null);
        if (gunData == null) {
            return;
        }
        TimelessAPI.getGunDisplay(currentGunItem).ifPresent(display -> {
            this.renderShell(display, gunData, poseStack, bedrockGunModel);
        });

    }

    public static class Data {
        public final long timeStamp;
        public final Vector3f randomOffset;

        public Matrix3f normal = null;
        public Matrix4f pose = null;
        public float capturePitch = Float.NaN;
        public float captureYaw = Float.NaN;

        public Data(long timeStamp, Vector3f randomOffset) {
            this.timeStamp = timeStamp;
            this.randomOffset = randomOffset;
        }
    }
}
