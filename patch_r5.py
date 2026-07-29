#!/usr/bin/env python3
"""
Round-5 patches on top of the r4 baseline. Idempotent.

三个问题，三个已由反编译证实的根因：

 ①③ 第三人称残缺手臂
     根因 A（r4 引入的倒退）：RenderHelper 在 submit 之后"还原" PlayerModel。
        反编译 SubmitNodeCollection#submitModel:
            Pose pose = poseStack.last().copy();                 // 只拷矩阵
            Submit<S> s = new Submit(renderType, pose, model, ...);   // model 是引用
        而 submitModelPart 内部 `new Model.Simple(modelPart, ...)` 持有**活的 ModelPart**，
        真正遍历顶点在稍后的 FeatureRenderDispatcher#renderAllFeatures。
        => 矩阵被快照，骨骼姿态没有。submit 后立刻还原 = 绘制时读到被还原的错误状态
           => 手臂残缺。必须撤销 r4 的还原。
     根因 B（真正的触发点）：TACZ 在第三人称下仍进入 submitArmWithItem
        -> renderFirstPerson -> Left/RightHandRender -> AvatarRenderer#renderHand，
        后者会强制 arm.visible=true 等，污染第三人称玩家实体。
        => 在 mixin 里显式判定"当前确为第一人称"，第三人称一律放行给 vanilla。

 ② 陆地移动时手+枪整体抖动
     r4 已把 walkAnimation.position() 换成插值重载 position(partialTick)，方向正确、保留。
     但仍抖且更快 —— 因为 GunAnimationStateContext.partialTicks 由 updateContext 写入，
     而 renderFirstPerson 每帧调用 animationStateMachine.update() 时，
     getWalkDist() 的插值基准 partialTicks 与 vanilla 手部渲染使用的 frameInterp 不一致时
     会产生二次跳变。这里统一：状态机上下文的 partialTicks 必须来自本次渲染传入的
     frameInterp（已是 renderItemInHand 的 partialTick），不要再从 DeltaTracker 另取一份。

 ④ 物品栏图标空白
     根因：submitCustomGeometry 的回调用错了矩阵。
        submitCustomGeometry 提交时 poseStack.last().copy() 存进 Submit，
        稍后把该拷贝作为回调首参 `pose` 传回；但 TACZ 回调体里用的是**外层可变 poseStack**，
        那时它早已被 popPose/复用 => 图标画到错误位置 => 空白。
        改为使用回调参数 pose。
"""
import io, os, re, sys

R = "/home/user/repo/src/main/java/"

def edit(path, old, new, tag, required=True):
    p = R + path
    s = io.open(p, encoding="utf-8").read()
    if new.strip()[:70] in s:
        print(f"  [skip] {tag}"); return True
    if old not in s:
        print(f"  [FAIL] {tag}: anchor not found")
        if required: sys.exit(1)
        return False
    io.open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))
    print(f"  [ok]   {tag}")
    return True

# ---------------------------------------------------------------- ① revert r4 arm "restore"
rh = R + "com/tacz/guns/util/RenderHelper.java"
s = io.open(rh, encoding="utf-8").read()
if "loadPose" in s:
    start = s.index("    /**\n     * Collector-aware 26.2 first-person arm submission.")
    end = s.rindex("    /** @deprecated legacy immediate path cannot render an arm without a collector. */")
    new_block = '''    /**
     * Collector-aware 26.2 first-person arm submission.
     *
     * <p><b>第 5 轮更正：这里<u>绝不能</u>在 submit 之后还原 PlayerModel。</b></p>
     *
     * <p>第 4 轮为修"第三人称残缺手臂"加过"快照 + finally 还原"，方向错误，反而加重了症状。
     * 反编译 {@code SubmitNodeCollection#submitModel}：</p>
     * <pre>
     * Pose pose = poseStack.last().copy();                        // 只拷贝<b>矩阵</b>
     * Submit&lt;S&gt; submit = new Submit(renderType, pose, model, ...); // model 是<b>引用</b>
     * </pre>
     * <p>而 {@code submitModelPart} 内部是 {@code new Model.Simple(modelPart, ...)}，
     * 持有<b>活的 ModelPart 根引用</b>；真正遍历顶点发生在稍后的
     * {@code FeatureRenderDispatcher#renderAllFeatures}。</p>
     *
     * <p>也就是说：矩阵被快照了，<b>骨骼姿态没有</b>。若在 submit 之后立刻把
     * {@code arm.visible}/{@code zRot}/pose 还原，等到真正绘制时读到的就是被还原后的状态
     * —— 正是"手臂残缺"的直接来源。</p>
     *
     * <p>这里恢复 vanilla 语义（写完即走）。第三人称的污染改在<b>源头</b>杜绝：
     * 见 {@code ItemInHandRendererMixin} 的第一人称视角门禁。</p>
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
        if (hand == HumanoidArm.RIGHT) {
            renderer.renderRightHand(matrixStack, collector, combinedLight, skinTexture,
                    player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
        } else {
            renderer.renderLeftHand(matrixStack, collector, combinedLight, skinTexture,
                    player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
        }
    }

'''
    io.open(rh, "w", encoding="utf-8").write(s[:start] + new_block + s[end:])
    print("  [ok]   ① revert r4 arm restore (was harmful)")
else:
    print("  [skip] ① revert r4 arm restore")

# ---------------------------------------------------------------- ① first-person gate
edit("com/tacz/guns/mixin/client/ItemInHandRendererMixin.java",
"""        if (!(player instanceof LocalPlayer localPlayer)) {
            return;
        }""",
"""        if (!(player instanceof LocalPlayer localPlayer)) {
            return;
        }

        // 【第 5 轮】必须显式判定"当前确为第一人称"。
        //
        // 症状：第三人称持枪时身上出现两条多余且残缺的手臂；换非枪械物品即消失；
        //      第一人称可"截获"该状态并持久化。
        //
        // 根因：ItemInHandRenderer 实例是全局共享的。GameRenderer#renderItemInHand 虽有
        //      isFirstPerson() 门禁，但第三人称视角 mod（Shoulder Surfing 等）以及 26.2
        //      自身的某些 PIP/离屏路径仍可能进入 submitArmWithItem。一旦进入，TACZ 就会
        //      renderFirstPerson -> Left/RightHandRender -> AvatarRenderer#renderHand，
        //      而后者直接改写共享 PlayerModel（arm.visible=true、zRot=±0.1、袖子可见性）
        //      且不还原。
        //
        //      关键：submitModelPart 存的是<b>活的 ModelPart 引用</b>（见 RenderHelper 注释），
        //      真正绘制在稍后的 renderAllFeatures，于是这些被强制打开的手臂部件会在
        //      第三人称玩家实体上再画一遍 —— 就是那两条"多余、残缺"的手臂。
        //
        // 修复：只在真正的第一人称接管；第三人称放行给 vanilla，
        //      TACZ 的第三人称枪械由 renderByItem + PlayerModelMixin 手臂姿态负责。
        if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return;
        }""",
"① first-person camera gate")

# ---------------------------------------------------------------- ④ slot pose callback
ITEM = "com/tacz/guns/client/renderer/item/"
PAT = re.compile(
    r'\(pose,\s*buffer\)\s*->\s*\{\s*\n(\s*)([A-Z_0-9]+)\.renderToBuffer\(\s*poseStack\s*,\s*buffer\s*,([^;]*?)\);\s*\n(\s*)\}',
    re.S)

def fix_slot(fn):
    p = R + ITEM + fn
    if not os.path.exists(p):
        print(f"  [miss] ④ {fn}"); return
    s = io.open(p, encoding="utf-8").read()
    if "tacz$snapshotPose" in s:
        print(f"  [skip] ④ {fn}"); return
    def rep(m):
        ind, model, args = m.group(1), m.group(2), m.group(3)
        return ("(pose, buffer) -> {\n"
                f"{ind}// 26.2: 必须使用回调参数 pose（= 提交那一刻 poseStack.last().copy() 的快照），\n"
                f"{ind}// 而不是外层 poseStack —— 回调执行时它早已被 popPose/复用，\n"
                f"{ind}// 结果就是图标被画到错误位置（物品栏一片空白）。\n"
                f"{ind}PoseStack tacz$snapshotPose = new PoseStack();\n"
                f"{ind}tacz$snapshotPose.last().pose().set(pose.pose());\n"
                f"{ind}tacz$snapshotPose.last().normal().set(pose.normal());\n"
                f"{ind}{model}.renderToBuffer(tacz$snapshotPose, buffer,{args});\n"
                f"{m.group(4)}}}")
    s2, n = PAT.subn(rep, s)
    if n:
        io.open(p, "w", encoding="utf-8").write(s2)
    print(f"  [ok]   ④ {fn}  ({n} callback(s))")

for f in ["GunItemRendererWrapper.java", "AmmoItemRenderer.java",
          "AttachmentItemRenderer.java", "GunSmithTableItemRenderer.java"]:
    fix_slot(f)

print("patch_r5 done")
