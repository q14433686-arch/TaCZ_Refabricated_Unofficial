# Mesh GPU 法线修复（26.2 `83daf16` 同理移植）— 2026-09-01

触发：维护者实机反馈——「1.21.11 总没解决的法线问题，26.2 也有，但早解决了」。
自行定位到 26.2 修复：`83daf16`（meshloader: fix shader-pack normals - MV stack popped
before Iris reads it (wrong reflections)）+ 验证矩阵记录 `9dc5cc5`。
**结论：与 26.2 的病完全同理，已在 26.1.2 侧应用同语义修复。运行期未验证（等实机）。**

## 0. 26.2 的诊断（其 commit message 字节码/源码实读，逐字有效于本分支）

光影包的 `gl_NormalMatrix`（被 Iris 改名 `iris_NormalMat`）**不来自**
`DynamicTransforms` 的 prepare/writeTransform 快照，而是 **Iris 26.x
`ExtendedShader.iris$setupState` 在「绘制执行那一刻」读
`RenderSystem.getModelViewMatrixCopy()`（MV 栈顶）做逆转置**
（`iris_ModelViewMatInverse` 同源）。GPU 路径的顶点法线是骨骼本地系裸写，
指望这个矩阵补上全部旋转：弹栈早了（或像我们这样根本没压），setupState 读到的
栈顶只剩相机 MV，`pose_bone` 的旋转层丢失 ⇒ 法线仍朝骨骼本地方向 ⇒ 光影的
平行光/反射按错误法线算 ⇒「反光的光源关系不对」。位置不受影响（走快照）。
vanilla 无光影路径不受此病影响。

## 1. 本分支为什么同病

- 光影下 GPU 世界/手部管线经 `IrisCompat.assignMeshPipelineToEntity/ToHand`
  登记进 Iris 的 `ENTITIES`/手部程序（R3 定稿、2026-08-31 实机 PASS 的照明通路）——
  即光影包的 `gbuffers_entities`/`gbuffers_hand` 程序在画我们的 VBO，
  其 `iris_NormalMat` 正是上述机制；
- 我们的 `drawList` 走自建 RenderPass：position 用
  `writeTransform(flushMV × pose)` 的 DynamicTransforms 切片（正确），
  但 **MV 栈从不动** ⇒ setupState 读到的栈顶 = 相机 MV，与 26.2 首版
  「prepare 后立即弹栈」的病灶等价。

## 2. 修复（`drawList` per-draw 压/弹）

每次 `pass.drawIndexed` 之前把该骨骼的 `entry.model()` 压到
`RenderSystem.getModelViewStack()`（栈顶 = flushMV × pose_bone），`finally` 弹出：

- 与 26.2 终版语义一致（其终版：压栈 → prepare 自取 → **drawFromBuffer 之后**才弹）；
- 位置切片不变（快照早已正确，压栈只服务「栈顶 = 该次 draw 的真实 MV」这一
  绘制期不变量，vanilla 路径 pass 内无该栈的读者，无条件压/弹无害）；
- 手部与世界两条路径共用 `drawList`，一并修复。

## 3. 验收（实机前全标「未验证」）

1. 光影下 GPU 烘焙的高模枪（第一人称手部 + 第三人称/掉落物）：反光与平行光的
   光源关系恢复正确（枪随视角转动时明暗面正确跟随）。
2. 无光影路径：明暗无回归（核心 entity 照明不读该栈，应零变化）。
3. 镜内（PIP 窄遍）重放：法线同样正确（同一 drawList）。

## 4. 给 1.21.11 的同步（该线尚未修，本文件可直接转发）

> 【26.1.2 → 1.21.11 同步 · mesh GPU 法线 · 2026-09-01】
> 根因（26.2 `83daf16` 实锤，机制对 1.21.11 的 Iris 1.10/1.11 同样成立）：
> 光影包 `gl_NormalMatrix` = Iris 在**绘制执行时刻**读 RenderSystem MV 栈顶的逆转置，
> 不吃 DynamicTransforms 快照；GPU 路径顶点法线是骨骼本地系，栈顶没有 pose 层
> ⇒ 法线朝向错 ⇒ 反光/平行光关系错。修法：`drawList` 每次绘制前
> `getModelViewStack().pushMatrix(); mul(entry.model());`，`finally popMatrix()`
> —— 位置切片不动（快照已对），vanilla 路径零影响。
