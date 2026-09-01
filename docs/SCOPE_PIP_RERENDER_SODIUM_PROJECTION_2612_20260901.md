# Scope PIP 二次渲染：镜内实体错位/独立于视界 —— Sodium 投影快照根因 — 2026-09-01

## 0. 实机症状（用户口径）

- `ScopePipRerender=true`（B1 二次渲染）时：镜内实体相对镜内世界「视界空间为绝对的」，
  追问后判别为**整体错位一个固定量**；**所有实体**都错。
- `ScopePipRerender=false`（屏幕空间重投影）时镜内正常 ⇒ 病只在二次渲染路径。
- 客户端**装了 Sodium**。26.2 / 1.21.11 两兄弟线无此病。

## 1. 字节码排查（CI 探针 round 5/5b/6/7，merged jar minecraft-merged-9d5daa536c-26.1.2）

逐项排除了 vanilla 侧的全部嫌疑（两遍 renderLevel 输入等价性）：

| 嫌疑 | 结论 | 证据 |
|---|---|---|
| MV 栈两遍不齐 | **排除**：`LevelRenderer.renderLevel` @45-63 自push+`mul(viewMatrix参数)`、@552 pop，两遍各自正确 | r5 dump |
| 投影槽位窄遍没盖上 | **排除**：GameRenderer @297-303（vanilla `ProjectionMatrixBuffer`）先设宽投影，我们的 `renderScopeView` 在 redirect 点覆盖槽位+`cameraState.projectionMatrix` 双通道，finally 双还原 | r5 dump |
| 实体节点一帧只提交一次（窄遍烧光） | **排除**：主 pass lambda（`lambda$addMainPass$0`）@220 `submitEntities(PoseStack, LevelRenderState, collector)` **每 pass 现场提交**，@537 `clearSubmitNodes` 收尾 | r7 dump |
| 节点提交时捕获投影 slice | **排除**：`SubmitNodeStorage.submit*` 只透传 PoseStack；`ModelFeatureRenderer.renderSolid` 只是把节点转进 bufferSource，绘制走当刻槽位 | r6 dump |
| extract 一次/per-pass 差异 | **排除**：`extractLevel` 只填 `LevelRenderState` 状态袋（@116-119 `extractVisibleEntities` 等）；26.1.2 的共享袋病已由重提取（`2ae4c29` 前的 B1 修复）解决 | r5b dump |

**结论：vanilla 路径在两遍里都干净。唯一被我们忽略的投影通道在 Sodium 侧。**

## 2. 根因（26.2 `SodiumCompat` javadoc 的同族病，方向相反）

Sodium 的地形**不读 `RenderSystem` 投影槽位**：它 `@WrapOperation` 包住 vanilla
`renderLevel` 里 `ProjectionMatrixBuffer#getBuffer` 那个**调用点**，把矩阵存进自己的私有快照
（`GameRendererStorage.sodium$getProjectionMatrix()`）。我们的二次渲染传给槽位的是
**自建 `ProjectionMatrixBuffer` 实例**的 slice —— 不经过那个被包住的调用点 ⇒ 快照纹丝不动。

于是镜内那遍：

- **地形（Sodium）= 宽 FOV 快照 = 1×**
- **实体/粒子/天空（原版路径）= 窄槽位 = M×**

两套比例糊在一起 —— 与用户「实体相对镜内世界错位」的观察一致（准星中心处重合、
越偏中心偏移越大，高倍镜下即「整体错位」感）。

第二层：Sodium `UniformBufferManager.update` 有「每帧只上传一次」的闸（`hasUpdatedThisFrame`）。
一帧两遍世界渲染时**镜内遍先到**，上传后关闸；主遍 `update()` 被早退挡掉，主画面地形沿用
镜内那遍的 uniform —— 26.2 记录的「镜内画面溢出到镜外」的真因（不是溢出，是主画面自己
被用错投影重画）。我们此前的 B1 未触发它的显眼形态（主遍 uniform 与主遍矩阵恰好同为宽视场），
但闸的语义性风险一直在。

## 3. 修复（26.2 同名 compat 的移植，语义逐条对应）

新类 `compat/sodium/SodiumCompat.java`（纯反射对 Sodium 自有类；Throwable 兜底安静降级）：

1. `overrideProjection(narrow)`：就地改写 `sodium$getProjectionMatrix()` 返回的可变
   `Matrix4f`（存原值）—— 镜内地形跟随窄 FOV；
2. `restoreProjection()`：写回原值 —— 主遍地形回宽 FOV；
3. `resetChunkUniformUpload()`：`SodiumWorldRenderer.instanceNullable()` → 反射取
   `uniformBufferManager` → 调 public 的 `prepareFrame()` 重开上传闸。

`ScopePipRerender.renderScopeView` 接线（26.2 同序）：
窄遍前 `sodiumPatched = SodiumCompat.overrideProjection(NARROW_MATRIX)`；
finally：还原 `cameraState.projectionMatrix` → `restoreProjection()` →
`resetChunkUniformUpload()` → 还原槽位（闸的重开放 finally，异常路径也绝不把主画面
留在镜内 uniform 上）。首帧日志补记 `sodium terrain projection synced`。

## 4. 降级矩阵

| 情形 | 表现 |
|---|---|
| 没装 Sodium | `overrideProjection` 直接 false，零开销（闸的重开同样早退） |
| Sodium 改名/换实现 | 反射失败 log-once warn，镜内地形回到 1×（=修复前的已知症状），不崩 |
| `sodium$getProjectionMatrix` 返回不可变实现 | 同上，直接放弃不硬来 |

## 5. 验收（运行期未验证，待实机）

- [ ] `ScopePipRerender=true` + Sodium：镜内地形与实体**同一套比例**（地形跟随倍率放大），
      实体相对镜内世界不再错位；
- [ ] 收镜后主画面地形立刻回宽 FOV（无「近处水/冰柱被放大」的残留帧）；
- [ ] 无 Sodium 环境回归：镜内外行为与修复前一致；
- [ ] 首帧日志出现 `sodium terrain projection synced: true`（装 Sodium 时）。
- [ ] 第一人称 / 非瞄准场景零变化（compat 只在镜内窗口内改写）。
