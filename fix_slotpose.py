#!/usr/bin/env python3
"""
26.2 修复：slot 贴图（物品栏图标）空白。

submitCustomGeometry 的语义（反编译 SubmitNodeCollection#submitCustomGeometry 确认）：

    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer r) {
        var submit = new CustomFeatureRenderer.Submit(poseStack.last().copy(), renderType, r);
        ...
    }

即：提交时把当前 Pose **拷贝**一份存进 Submit，稍后 FeatureRenderDispatcher 执行时
再把这个拷贝作为回调的第一个参数 `pose` 传回来。

但 TACZ 的回调体里用的是**外层那个可变 PoseStack**，而不是回调参数 pose：

    collector.submitCustomGeometry(poseStack, type, (pose, buffer) ->
            SLOT_MODEL.renderToBuffer(poseStack, buffer, ...));   // ← 用错了

等到回调真正执行时，外层 poseStack 早已被 popPose()/复用，矩阵不再是提交那一刻的值，
于是四边形被画到任意错误的位置（通常在可视区外）——表现就是**物品栏图标一片空白**。

修复：回调内改用参数 `pose` 构造一个只含该矩阵的 PoseStack 再交给 renderToBuffer。
"""
import io, os, re

ROOT = "/home/user/repo/src/main/java/com/tacz/guns/client/renderer/item"

FILES = ["GunItemRendererWrapper.java", "AmmoItemRenderer.java",
         "AttachmentItemRenderer.java", "GunSmithTableItemRenderer.java"]

# (pose, buffer) -> { XXX.renderToBuffer(poseStack, buffer, A, B, ...); }
PAT = re.compile(
    r'\(pose,\s*buffer\)\s*->\s*\{\s*\n(\s*)([A-Z_0-9]+)\.renderToBuffer\(\s*poseStack\s*,\s*buffer\s*,([^;]*?)\);\s*\n\s*\}',
    re.S)

REPL = (r'(pose, buffer) -> {\n'
        r'\1// 26.2: 必须使用回调参数 pose（提交时的矩阵快照），\n'
        r'\1// 而不是外层 poseStack —— 回调执行时它已被 popPose/复用，会把图标画到错误位置。\n'
        r'\1PoseStack tacz$snapshot = new PoseStack();\n'
        r'\1tacz$snapshot.last().pose().set(pose.pose());\n'
        r'\1tacz$snapshot.last().normal().set(pose.normal());\n'
        r'\1\2.renderToBuffer(tacz$snapshot, buffer,\3);\n'
        r'\1}')

total = 0
for fn in FILES:
    p = os.path.join(ROOT, fn)
    if not os.path.exists(p):
        print("  [miss]", fn); continue
    s = io.open(p, encoding="utf-8").read()
    s2, n = PAT.subn(lambda m: REPL.replace(r'\1', m.group(1))
                                  .replace(r'\2', m.group(2))
                                  .replace(r'\3', m.group(3))
                                  .replace('\\n', '\n'), s)
    if n:
        io.open(p, "w", encoding="utf-8").write(s2)
        total += n
    print(f"  {n:>2}  {fn}")

print("total slot-pose callbacks fixed:", total)
