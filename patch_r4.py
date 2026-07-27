#!/usr/bin/env python3
"""Round-4 patches on top of the r3 baseline. Idempotent: safe to re-run."""
import io, os, sys

R = "/home/user/repo/src/main/java/"

def edit(path, old, new, tag):
    p = R + path
    s = io.open(p, encoding="utf-8").read()
    if new.strip()[:60] in s:
        print(f"  [skip] {tag} (already applied)")
        return
    if old not in s:
        print(f"  [FAIL] {tag}: anchor not found")
        sys.exit(1)
    io.open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))
    print(f"  [ok]   {tag}")

# ---------------------------------------------------------------- #1 arm leak
edit("com/tacz/guns/util/RenderHelper.java",
"""    /** Collector-aware 26.2 first-person arm submission. */
    public static void renderFirstPersonArm(LocalPlayer player,
                                            HumanoidArm hand,
                                            PoseStack matrixStack,
                                            SubmitNodeCollector collector,
                                            int combinedLight) {
        if (player == null) {
            return;
        }
        AvatarRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player);
        var skinTexture = player.getSkin().body().texturePath();
        if (hand == HumanoidArm.RIGHT) {
            renderer.renderRightHand(matrixStack, collector, combinedLight, skinTexture,
                    player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
        } else {
            renderer.renderLeftHand(matrixStack, collector, combinedLight, skinTexture,
                    player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
        }
    }""",
"""    /**
     * Collector-aware 26.2 first-person arm submission.
     *
     * <p><b>26.2 修复：第三人称多出残缺手臂的根因。</b></p>
     *
     * <p>反编译 {@code AvatarRenderer#renderHand} 可见，它会<b>直接改写共享的
     * {@code PlayerModel} 实例</b>后才提交：</p>
     * <pre>
     * arm.resetPose();
     * arm.visible = true;
     * model.leftSleeve.visible  = hasSleeve;
     * model.rightSleeve.visible = hasSleeve;
     * model.leftArm.zRot  = -0.1F;
     * model.rightArm.zRot =  0.1F;
     * submitNodeCollector.submitModelPart(arm, ...);   // 延迟绘制
     * </pre>
     *
     * <p>{@code EntityRenderDispatcher#getPlayerRenderer} 返回的是<b>全局唯一</b>的
     * {@code AvatarRenderer}，其 {@code PlayerModel} 被第一人称手臂与第三人称玩家实体
     * <b>共用</b>。上面那串写操作立即生效且<b>从不还原</b>，于是
     * {@code arm.visible = true}、{@code zRot = ±0.1}、袖子可见性会污染第三人称玩家实体的渲染
     * —— 表现为身上多出一条姿态错误、残缺的手臂；且状态被"粘住"，切回第一人称依旧存在，
     * 直到换成非枪械物品（不再走这条路径）才恢复。</p>
     *
     * <p>因此这里对被写入的字段做快照 + finally 还原。手臂自身的提交已经完成，
     * 还原只影响后续复用该模型的渲染，不改变本次手臂外观。</p>
     */
    public static void renderFirstPersonArm(LocalPlayer player,
                                            HumanoidArm hand,
                                            PoseStack matrixStack,
                                            SubmitNodeCollector collector,
                                            int combinedLight) {
        if (player == null) {
            return;
        }
        AvatarRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player);
        var skinTexture = player.getSkin().body().texturePath();

        net.minecraft.client.model.player.PlayerModel model =
                renderer.getModel() instanceof net.minecraft.client.model.player.PlayerModel pm ? pm : null;

        boolean lArmVis = false, rArmVis = false, lSlvVis = false, rSlvVis = false;
        float lZRot = 0F, rZRot = 0F;
        net.minecraft.client.model.geom.PartPose lPose = null, rPose = null;
        if (model != null) {
            lArmVis = model.leftArm.visible;
            rArmVis = model.rightArm.visible;
            lSlvVis = model.leftSleeve.visible;
            rSlvVis = model.rightSleeve.visible;
            lZRot = model.leftArm.zRot;
            rZRot = model.rightArm.zRot;
            lPose = model.leftArm.storePose();
            rPose = model.rightArm.storePose();
        }

        try {
            if (hand == HumanoidArm.RIGHT) {
                renderer.renderRightHand(matrixStack, collector, combinedLight, skinTexture,
                        player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
            } else {
                renderer.renderLeftHand(matrixStack, collector, combinedLight, skinTexture,
                        player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
            }
        } finally {
            if (model != null) {
                model.leftArm.loadPose(lPose);
                model.rightArm.loadPose(rPose);
                model.leftArm.visible = lArmVis;
                model.rightArm.visible = rArmVis;
                model.leftSleeve.visible = lSlvVis;
                model.rightSleeve.visible = rSlvVis;
                model.leftArm.zRot = lZRot;
                model.rightArm.zRot = rZRot;
            }
        }
    }""",
"#1 arm state leak -> third person")

# ------------------------------------------------------- #2 walk interpolation
edit("com/tacz/guns/client/animation/statemachine/GunAnimationStateContext.java",
"""    public void anchorWalkDist() {
        processCameraEntity(entity -> {
            if (entity instanceof LivingEntity livingEntity) {
                walkDistAnchor = livingEntity.walkAnimation.position();
            }
            return null;
        });
    }""",
"""    public void anchorWalkDist() {
        processCameraEntity(entity -> {
            if (entity instanceof LivingEntity livingEntity) {
                walkDistAnchor = tacz$interpolatedWalkPosition(livingEntity);
            }
            return null;
        });
    }

    /**
     * 取<b>按 partialTick 插值后</b>的行走距离。
     *
     * <p><b>26.2 修复：陆地行走/奔跑时枪械剧烈抖动的根因。</b></p>
     *
     * <p>上游 1.21.1 是手动插值：
     * {@code entity.walkDist + (entity.walkDist - entity.walkDistO) * partialTicks}。</p>
     *
     * <p>1.21.2+ 把 walkDist/walkDistO 收进了 {@code WalkAnimationState}。移植时改成了无参的
     * {@code walkAnimation.position()} —— 该重载返回<b>未插值、每游戏刻(20Hz)才更新一次</b>的原始值。
     * 渲染按帧跑（60~144Hz），行走动画驱动量因此变成阶梯状跳变 —— 就是"陆地移动时枪剧烈抖动"。</p>
     *
     * <p>这同时解释了"游泳/飞行/边跳边走反而不抖"：那些状态下脚不沾地，
     * {@code WalkAnimationState#update} 的 speed 近 0，position 几乎不变，没有阶梯跳变。</p>
     *
     * <p>26.2 的 {@code WalkAnimationState} 提供带参重载 {@code position(float)}（javap 已确认），
     * 语义与上游手动插值一致。</p>
     */
    private float tacz$interpolatedWalkPosition(LivingEntity livingEntity) {
        return livingEntity.walkAnimation.position(this.partialTicks);
    }""",
"#2 walk distance interpolation")

edit("com/tacz/guns/client/animation/statemachine/GunAnimationStateContext.java",
"""                float currentWalkDist = livingEntity.walkAnimation.position();""",
"""                // 必须用插值值，否则每游戏刻才跳变一次 -> 阶梯抖动。见 tacz$interpolatedWalkPosition。
                float currentWalkDist = tacz$interpolatedWalkPosition(livingEntity);""",
"#2 walk distance interpolation (getWalkDist)")

print("patch_r4 done")
