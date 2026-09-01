# 全量对照 26.1.2 的 42 个 commit：我方"镜内外均 1X"的根因、审计方法与结论

2026-09-01（当日第二次）。维护者实机：**开二次渲染 + 开光影后，瞄准时镜内和镜外都是 1X**，
并怀疑"把他历史上的 BUG 搬过来时忘了看之后的修复"，要求全量对照其 commit。

**判定：这个怀疑成立，而且比"漏了某个 commit"更具体——是我方那批"放行光影窄遍"只搬了**放行**、
没搬同批**为这条路径配套的两道守卫**。两者各自都能独立产出"镜内是 1x 主画面"的症状。**

## 1. 两道漏掉的守卫（都在既有方法体内，方法名两侧完全相同）

| 他们的 commit | 漏掉的守卫 | 机制（为什么会 1x） |
|---|---|---|
| `825d2c5` "enable the rerender pass under shader packs (drop the B1 Iris hard-refuse)" | `ScopePipRenderState#captureSceneAfterIrisFinal` 开头的 `if (ScopePipRerender.rerenderMode()) return;`（且**刻意不清** `sceneCaptured`） | 终局钩子里那次"补一次捕获"是给重投影路径用的。二次渲染模式下镜内画面已由 `renderScopeView` 在窄遍返回后拷好；这里再拷一次，就是拿**宽视场的成品帧覆盖窄视场成品** ⇒ 合成把这块 1x 主画面贴进镜孔 ⇒ 镜内=镜外=1X。**注意：这条守卫与"删掉硬拒"是同一个 commit**——放行与它是整体，只搬一半必然坏 |
| `95590b0` "break the capture/composite feedback loop in the narrow Iris pass" | `IrisFinalScopeOverlayMixin#tacz$drawScopeAfterShaderPackFinal` 里的 `if (ScopePipRerender.isInsideScopeLevelRender()) { ScopeFinalOverlayState.discardPendingOverlays(); return; }` | `finalizeLevelRendering` 是**每遍 `renderLevel` 各触发一次**，光影+二次渲染下一帧触发**两次**（窄遍内 + 宽遍内）。窄遍里那次如果照常跑，就会：①把合成画在主目标上（马上被宽遍重画覆盖，白做）；②把 reticle/遮光罩 flush 掉，而**紧随其后的 `renderScopeView` 捕获会把"上一帧镜内画面 + 遮光罩"当成新镜内画面拷进去** ⇒ 合成回灌自身，镜内容定格（他们实机原话：`lens frozen at the first full-ADS frame, shade copy-pasted every frame`）。另外窄遍期间提交的覆盖层必须显式丢弃，否则会攒到下一帧被宽遍画在错误的投影上 |

`captureScene`（vanilla 手前那个入口）的同款守卫我方**有**——正因为一处有一处没有，逐 commit 挑拣时没显出异常。

## 2. 审计方法：三层判据（已固化成脚本，可复跑）

`scripts/audit_sibling_render_line.py <theirs-ref> <base-ref>`

| 层 | 判据 | 能抓什么 | 抓不到什么 |
|---|---|---|---|
| 1 | 他们每个 commit **新增的方法名/配置键**逐个在我方全树 `-w` 搜索 | 整块没搬（方法/键级别） | 嵌在既有方法体里的守卫 ← **本次的两条都在这里溜过去** |
| 2 | 双方**终态**代码行 diff（滤掉注释与空行）行数，按文件排序 | 任何形态的漏项/错搬：只要终态不同形就报数 | 需要人工定性："世代改写" vs "漏项" |
| 3 | 双方终态**方法清单**差集（他有我没有） | 方法级缺口的快速定位 | 方法在、内容短一截 |

**为什么之前"逐 commit cherry-pick"不够**：那条路对 G1–G4 有效，因为那些改动各自新增方法/键；
而"放行窄遍"这类改动的**语义在别的文件里**（守卫落在 `ScopePipRenderState`/`IrisFinalScopeOverlayMixin`），
cherry-pick 时那两个文件不在冲突面里，就被无声跳过了。⇒ 结论写进账本：**移植一个"放行/开关"型改动，
必须把它所放行路径上的每个消费点守卫一起搬，并用第 2 层（终态 diff）复验。**

## 3. 本轮三层输出与逐条定性

- **层 1（全 42 commit）：真实提交零缺口**。报出的 5 条全部来自他们那批 `TEMP`/javap 探针的 **ci-log 提交**
  （`setLevel`、`addMainPass`、`backupProjectionMatrix`、`submitItem`、`pollEvents` 等一次性探针符号）与一个文档文件名；
  据此确认 `ScopePipDebugTrace`/`ScopePipDebugGpuMem`/`ScopePipIrisFlushLog`/`ScopePipCaptureEveryFrame` 等我方**已有**（早先批次搬过），
  "装了 Sodium 就拒"那种假键名不存在。
- **层 2（20 个文件）修复后**：`SodiumCompat`、`VoxyCompat`、`VoxyScopePipelineCompat`、`GameRendererMixin`、
  `LevelRendererAllChangedScopePassMixin`、`IrisFinalScopeOverlayMixin`、`IrisScopeDimensionMixin`、
  `IrisShadowResolutionMixin`、`mixin/client/voxy/*` = **0 行（与终态同形）**；
  `ScopePipRenderState` 31、`ScopeDepthCopyState` 4、`ScopeFinalOverlayState` 7、`RenderClothConfig` 20、
  `IrisScopePipelineCompat` 75、`RenderConfig` 98、`PolyMeshGpuRenderer` 149、`ScopePipRerender` 168。
  残余逐条定性：**全部是世代改写或我方有意裁剪**，无漏项——
  `ColorTargetState`/`Optional` 导入与 `withColorWrite`（1.21.11 无 `ColorTargetState`）、
  `compositeScene(Minecraft)` 重载（我方保留旧入口）、`shaderRerenderAllowed()` 位置、`sceneTargetGeneration` 声明位置；
  `PolyMeshGpuRenderer` 是他们 `cameraState`/`RenderType#prepare` 形状与我方 `drawList`+`irisFlush` 形状之差；
  `ScopePipRerender` 是 1.21.11 的 `renderLevel` 带投影参数、我方无需逐帧状态修复（其类注释已记差异清单）。
- **层 3**：只剩 `PolyMeshGpuRenderer` 的私有包装 `isInsideScopeLevelRender()`（我方直接调 `ScopePipRerender.`，等价）
  与 `packLight`（他们侧的 UV 烘焙细节，非本路径）。

## 4. 本轮改动（全部过编译门，`success`）

1. `ScopePipRenderState#captureSceneAfterIrisFinal`：补 `rerenderMode()` 早退（不清 `sceneCaptured`）。
   另记一处**刻意不同形**：`captureScene`（vanilla 手前入口）里他们把 `rerenderMode()` 守卫放在
   `!isEnabled() || failed || mc == null` **之后**、我方放在**之前**；差异只落在"配置关了/已 failed/mc 为空"
   这几帧上（我方不清 `sceneCaptured`）。不改：我方 `compositeAfterHand` 的停跑契约依赖"窄遍停跑时该字段被
   每帧重置"，动顺序等于把两处的时序重新耦合，为凑 diff 行数不值得。
2. `IrisFinalScopeOverlayMixin`：补窄遍早退 + `ScopeFinalOverlayState.discardPendingOverlays()`（新方法，清 `PENDING_RETICLES/RINGS/TEXT` 与 `handTransform`）。
3. `ScopePipRenderState#shaderRerenderAllowed()`：补这个公有闸（`allowShaderPacks() && supportsFinalScopeOverlay()`），
   并把 `ScopePipRerender#shaderIsolateSafe()` 与预热从"只看配置键"改成看它——终局钩子不可用时镜内画面没人能上屏，症状与本次同型。
4. 对齐审计捞出的唯一功能差：`ScopePipRerenderInterval`（1–4，默认 1）+ `sceneTargetGeneration` 代次守卫（复用上一帧成品前必须比对画布代数，窗口缩放/格式变化重建过就绝不复用）。默认 1 ⇒ 行为不变。
5. **两条 TOML 说明此前已是假话**，一并修：`ScopePipRerender` 还写着"本分支只实现无光影路径"；`ScopePipIsolatePipeline` 还写着"装 Sodium 或 Voxy 就拒绝"（Sodium 已就地同步、Voxy 已搬第二渲染栈）。
   根因：那次改注释的脚本在同一次运行里因别处 assert 失败而中断，只落了别的文件——**同一批里的多处编辑必须逐条 assert 后统一落盘，失败要显式报错**。

## 4b. 一处**我方主动比他们多做**的加固（会在下一轮全量对照里显示为"有意不同形"）

`ScopePipRerender#worldZoomForcedToOne()` 他们与我方原本是同一式子 `rerenderMode() && !failed`。
它与 `compositeAfterIrisFinal` 的 `rerenderMode() && !hasScene()` 早退合起来是一条**死路**：只要窄遍被
任何原因拒掉（光影下没开 opt-in、Iris 终局钩子不可用、隔离前提不满足——包括本批新加的
`shaderRerenderAllowed()` 判假），世界就被压成 1×、镜内又拒绝合成，屏幕内外一起 1X 且不自愈。
这正好是维护者这次看到的现象形态之一，所以把它改成"这一帧窄遍真会跑才压 1×"（新增
`scopePassRunnable()`），拒掉时退回"重投影 / 整屏 FOV 变焦"——即开关未生效时的既有形态，可用画面优于一屏 1X。
他们那边没这条：他们实机是在放行成功的前提下 PASS 的，没暴露这个死角。**若后续把这条同步给他们，请连理由一起给。**

## 5. 证据级别（AGENTS §2）

- 上述四项 CI `success`（`2314b97e`、`ad8391a`）。
- **本批之前"全绿"并不代表功能对**：本次症状是维护者实机发现的，我方三层判据里**只有第 2 层能抓到它**，
  而我上一轮只做了 cherry-pick + 我自己那几处的局部检查。这条教训比修的问题本身更该记住。
- 实机待复测项：开 `ScopePipRerender` + `ScopePipAllowShaderPacks` + 任意光影包 ⇒ 镜内应恢复放大且随窄 FOV 移动；
  遮光罩/reticle 只应在宽遍出现（若镜内出现"镜内画面的又一层拷贝"，就是第 2 条守卫没命中）；
  把 `ScopePipRerenderInterval` 调到 2–4 应看到镜内内容滞后但主画面帧率上升，窗口缩放一帧后不得出现未定义内容。
