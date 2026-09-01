# Scope PIP 光影二次渲染（26.2 Iris 母版）26.1.2 移植记录 — 2026-09-01

> **立项裁定（用户，2026-09-01）**：「直接做，不想留代差——R 系列代表正式版；最难的母版
> 已被 26.2 做出来了；光影下的旧 PIP 可以抛弃，或统一成它的离屏渲染（更现代）」。
> 本轮先落「光影下二次渲染」全链；vanilla 路径（重投影/B1/深度孔径/掩码/文字）保持现状
> 不动（已实机 PASS）。1211 线同项是 DECLINED 状态（其维护者裁定），与本线无关。
> **编译已过（CI）；运行期行为全部未验证，待实机。**

## 0. 旧路 vs 新路

| | 旧（本轮之前） | 新（本轮） |
|---|---|---|
| 无光影 | 重投影 / B1 二次渲染（拷主目标） | **不变** |
| 光影（Iris） | 重投影成品帧变体（`ALLOW_SHADER_PACKS`，默认关）；B1 **硬拒** | **B1 二次渲染**：窄遍跑完整 Iris 管线，`ALLOW_SHADER_PACKS` 同一门（默认关雷区不绕），时域隔离默认开 |

B1 旧硬拒的理由（「光影下主目标里没有窄 FOV 的成品可拷」）**作废**：Iris 的成品是在
`LevelRenderer.renderLevel` 内部尾段的 `finalizeLevelRendering()` 合成到主帧缓冲的，
发生在该调用返回**之前**——窄遍返回后拷主目标的时机对光影同样成立（26.2 正是这么拷的）。

## 1. 移植映射（26.2 → 本线）

| 26.2 母版 | 本线落地 | 说明 |
|---|---|---|
| `IrisScopeDimensionMixin`（`Iris.getCurrentDimension` HEAD 改答 `tacz:scope_pip`） | 同名移植，门=`ScopePipRerender.isScopePassIsolated()` | 借 Iris 按维度缓存管线机制给镜内一遍独立 colortex/程序/previous 族；管线归 Iris 的 map 管，切维度/重载光影一并回收，不漏显存。require=0：Iris 内部改名→静默退化为共用管线（伪影回归，不崩） |
| `IrisScopePipelineCompat`（维度 id + 预热 + Voxy 第二套栈 + ShadowScale + 空闲释放） | **裁剪版**：维度 id + 句柄解析 + `prewarmIfNeeded` | 裁掉：Voxy（本线无 Voxy compat）、ShadowScale 阴影降采样、空闲释放（FPS 衰减调查线未随移植）。预热调用点=本线已有的 `GameRenderer.render` HEAD（26.2 用 extract HEAD，同为「世界渲染前的空档」，本注入点已实机验证过） |
| `ScopePipRenderer` 光影分支（不重定向，跑完拷主帧缓冲） | `ScopePipRerender` 光影分支 | 拷贝时机与 vanilla 分支同点（`captureSceneFromMain`，`copyMainColor` 无闸公共路径） |
| `SodiumCompat.overrideProjection`（Sodium 地形 + **Iris gbuffer 投影** 同源快照） | **上一轮已移植**（`3d8432f`） | 光影下 Iris 的 gbuffer 投影也读 Sodium 快照——本轮直接复用 |
| 抓取/合成在 `finalizeLevelRendering` TAIL | **已有**（`IrisFinalScopeOverlayMixin` + `captureSceneAfterIrisFinal`/`compositeAfterIrisFinal`） | rerender 模式下：抓取守卫直接 return（窄遍自己拷过），合成照常（倍率分流 `compositeZoom()`=1） |
| 配置 `ScopePipIsolatePipeline`（默认 true） | 同名同默认 + Cloth 条目 + lang en/zh | `ScopePipAllowShaderPacks`（默认 false）沿用为光影 opt-in 总闸 |
| 性能杠杆 `ScopePipRerenderInterval` | **已有**（`8aca737`） | 光影下整条管线跑两遍的砍半开关；ShadowScale 待后续轮 |

## 2. 接线细节（互踩点逐一处理）

1. **`captureScene`（手部 HEAD 干净帧抓取）**：已有 `rerenderMode()` 早退，不覆盖窄遍成品。
2. **`captureSceneAfterIrisFinal`（光影成品帧抓取）**：**本轮新增** `rerenderMode()` 守卫——
   刻意不清 `sceneCaptured`（紧随其后的合成还要用它）。没有这条守卫，宽遍的 finalize TAIL
   会用宽视场成品覆盖镜内窄视场成品。
3. **`compositeAfterIrisFinal`**：倍率改走 `compositeZoom()` 分流（重投影=lensZoom()、
   二次渲染=1）。`IRIS_FULL_AIM_THRESHOLD`（≈开满镜）门保留：光影下的镜内画面含
   in-level 手部，未开满时中心区可能叠着 viewmodel（与重投影光影变体同一适配）。
4. **隔离标志**：`scopePassIsolated` 只在窄遍前置位、finally **最先**清——之后任何
   「问当前维度」的代码都拿真实值；切世界（ClientLevel 切换）才触发 Iris 重建主管线，
   逐帧标志够不着它。
5. **`prewarmIfNeeded` 指回主管线**：建/取瞄具管线后必须用真实维度再 `preparePipeline`
   一次（缓存命中）把「当前管线」指回去，finally 保证；漏掉=整帧主画面用瞄具管线渲染。
6. **Sodium 三通道**：窄投影的第三通道（Sodium 快照）对光影同样必要（26.2：Sodium 快照
   同时是 Iris gbuffer 投影来源）——上一轮的 `overrideProjection`/`restoreProjection`/
   `resetChunkUniformUpload` 序列原样覆盖光影分支。

## 3. 已知风险 / 降级矩阵（运行期未验证）

| 情形 | 表现 |
|---|---|
| `getCurrentDimension` 在 Iris 26.1 签名/名字不同 | mixin require=0 静默不生效 → 镜内与主画面共用管线 → 拖影/云噪点/镜外发糙三伪影（首帧日志无 "own Iris pipeline" 行即此情形） |
| 26.1 Iris 的 finalizeLevelRendering 不在 renderLevel 内部（与 26.2 不同构） | 窄遍返回后主目标还没成品 → 镜内空/垃圾画面。修法（预案）：抓取点后移到 `IrisFinalScopeOverlayMixin` TAIL（管线已在本轮备好，改动≈5 行） |
| `preparePipeline`/`getPipelineNullable` 改名 | 反射失败 log-once warn → 退懒加载（首次开镜卡一次） |
| 显存不足 | 关 `ScopePipIsolatePipeline`（伪影自负）；总闸 `ScopePipAllowShaderPacks` 关=整条光影 PIP 关 |
| 下界/末地开镜 | pack 按 fallback 目录（`world0 *` 档）选着色器，镜内可能用主世界的着色器（26.2 同款取舍） |

## 4. 验收剧本（实机）

- [ ] `ScopePipRerender=true` + `ScopePipAllowShaderPacks=true` + 光影：开镜镜内为**原生分辨率
      窄 FOV** 画面（地形/实体同一套比例——Sodium 快照已同步），镜外恒 1×；
- [ ] 时域健康：无整屏拖影、体积云无噪点闪烁、开镜时镜外不发糙（=隔离生效；首帧日志应有
      "own Iris pipeline (tacz:scope_pip)" 与 "Pre-built the scope pass' Iris pipeline"）；
- [ ] 未开满镜时镜内为 1× 世界（IRIS_FULL_AIM_THRESHOLD 门的已知取舍），开满出现镜内画面；
- [ ] 收镜/开镜循环无残留贴片；主画面地形在收镜帧立刻回宽 FOV（Sodium uniform 闸已重开）；
- [ ] `ScopePipIsolatePipeline=false`：功能仍在，但出现上述三伪影（用于判别隔离是否必要）；
- [ ] 无光影全矩阵回归：重投影/B1/掩码/文字与此前一致（本轮未动 vanilla 路径）；
- [ ] 首次开镜卡顿应已挪到进世界后一次性（预热生效；若仍卡在首次开镜=预热反射失败，看 warn）。

## 5. 开放事项

- `ScopePipShadowScale`（阴影降采样）与空闲释放（FPS 衰减调查线）未随移植——性能问题出现时
  以 26.2 为母版补。
- Voxy 镜内 LOD（第二套渲染栈）未移植。
- 1211 线的 SodiumCompat 转发文本：用户裁定「一会再说」，未写。

## 6. 实机首测与反馈回路修复（2026-09-01，ComplementaryUnbound r5.8.1）

**实测症状**：只正确放大一帧；随后镜内容冻结在开镜那一刻；移动时遮光罩在镜内逐帧
「复制粘贴」累积；帧率照常减半（窄遍确实每帧在跑）。

**根因（字节码拓扑+症状互证）**：`finalizeLevelRendering` 在**每一遍** renderLevel 内部
都会执行——一帧共两次（窄遍尾部一次、宽遍尾部一次）。窄遍尾部的钩子会把上一帧的镜内
合成画面+遮光罩画上主目标；随后 `renderScopeView` 的「拷主目标」把这份残留连新窄帧一起
拷走 ⇒ 合成结果回灌自身：帧 1 干净（首帧无东西可回灌），帧 2 起镜内容恒等于上一帧
（冻结）且遮光罩逐帧叠加（复制粘贴）。

**修复（本节随附提交）**：
- `IrisFinalScopeOverlayMixin`：注入体头部加 `ScopePipRerender.isInsideScopeLevelRender()`
  守卫——窄遍里跳过抓取/合成/覆盖层三连，改为丢弃当次累积的延迟队列
  （`ScopeFinalOverlayState.discardPendingOverlays()`）；宽遍自己的 finalize 照常三连
  （那时 `scopePassActive` 已复位、`sceneCaptured` 已由窄遍拷贝就绪）。
- 顺带修正认知：窄遍里的合成/覆盖层本来就会被宽遍的主 pass 清屏+整幅重画覆盖，
  从来就是纯浪费+污染源，跳过没有视觉损失。

**未触发预案**：§3 第二行（finalize 在 renderLevel 之外）——实测 finalize 时机正确，
成品帧存在，抓取点无需后移。

## 7. 性能与 Voxy 补全（2026-09-01 第二批，用户裁定「都移植一下」）

实测反馈「帧率比 26.2 掉得更狠」⇒ 补齐此前裁剪的三件：

| 件 | 提交 | 说明 |
|---|---|---|
| `ScopePipShadowScale` | 本批 | 阴影每帧被画两遍是大头；0.5 = 镜内那遍阴影约 25% 代价，仅镜内受损。构造窗口拦截 `getResolution` + 每帧比对热重建 + 静默失效告警 |
| 空闲释放 | 本批 | `ScopePipReleaseIdlePipeline`（默认 false）+ `ScopePipIdleReleaseDelayFrames`（默认 120）：26.2 FPS 衰减调查线的实验杠杆——瞄具管线保留 GPU 状态逐 pass 累积的清零手段 |
| Voxy 适配 | 本批 | `VoxyCompat`/`VoxyScopePipelineCompat`（第二套 Voxy 渲染栈：逐帧换绑 pipeline/viewportSelector/traversal.pipeline；身份判据所有权；"Pipeline data already bound" 先查后建防整局崩）+ 3 个 voxy mixin（镜内专用视口/隔离未换绑时坐过/节点 tick 每帧一次）+ 插件门注册 |

**26.1.2 拓扑差异（javap 实证）**：26.2 的重载钩子入口 `LevelExtractor.allChanged` 在本世代
不存在（probe r8：class not found）；等价物是 **`LevelRenderer.allChanged()`**（本世代还在
LevelRenderer 本尊，probe r8b javap 确认 public void allChanged()）。落地为
`LevelRendererAllChangedScopePassMixin`：镜内那遍期间取消（Iris 首渲染会请求一次 full
reload，否则 Voxy 会重绑到瞄具管线并永久污染主画面远景）；货真价实的重载则通知
`IrisScopePipelineCompat.onLevelRendererReload()`（归还并打回 Voxy 第二套栈的重建状态）。
**已注册**（tacz.mixins.json，类/方法均已字节码确认）。

**仍裁剪**：`ScopePipDebugGpuMem`/`ScopePipDebugTrace`（诊断线，非性能件）——需要时按 26.2 补。

### 验收补充（实机）
- [ ] 光影+二次渲染开镜：帧率与 26.2 同场景相当（ShadowScale 默认 0.5 生效；日志
      "Scope pass gets a ...x... shadow map" 一行 + "Pre-built" 行）；
- [ ] 改 `ScopePipShadowScale` 数值：日志 "ScopePipShadowScale changed ... rebuilding"，
      无需重进世界；
- [ ] 装 Voxy：镜内有 LOD 远景且主画面远景不错乱；改区块视距后镜内 LOD 仍正常
      （allChanged 钩子在开镜中会取消、镜外会重建第二套栈）；
- [ ] `ScopePipReleaseIdlePipeline=true`：久置后开镜帧率不衰减（或衰减消失）——
      这是实验判定，非修复承诺。

## 8. 实机 ESC 崩溃 RCA + 开镜节奏变更（2026-09-01，RawOutput.log 实证）

### 8.1 崩溃链（`IllegalStateException: Tried to use destroyed RenderTargets`）

```
prewarm: preparePipeline(tacz:scope_pip)   ← scope 管线成为「当前管线」
  └ 管线构建触发 LevelRenderer.allChanged
     └ 取消门只认「镜内遍期间」→ 放行
        └ Voxy 系统恰在此刻全量重建（log: Shutting down → Creating Voxy render system）
           └ 重建出的 Voxy 主栈绑到 scope 管线（"Creating voxy iris render pipeline"）
ESC 暂停 → 空闲释放 destroy scope 管线（RenderTargets 一并销毁）
  └ 宽遍地形 → Voxy（主栈仍绑 scope 管线）→ getOrCreate(已销毁 RTs) → 崩
```

修复三道：
1. **allChanged 取消门扩到 `isBuildingScopePipeline()` 窗口**（预热的 preparePipeline
   全程）：窗口内的 full reload 一律取消（block-id 状态全局、主管线早已设好；被取消的
   重载没有执行，无需通知 Voxy 兼容层）。重绑路径就此关闭。
2. **释放前身份兜底**（`VoxyScopePipelineCompat.isMainStackBoundTo`）：主 Voxy 栈若仍绑着
   scope 管线，拒绝释放并熔断本会话——宁可少一次释放，不赌整局崩溃。
3. （既有）释放时 `onRendererRebuilt` 已先归还第二套栈。

### 8.2 开镜节奏：开镜即接管（用户裁定，母版实机行为优先于其文档声明）

`compositeAfterIrisFinal` 的 `IRIS_FULL_AIM_THRESHOLD`（≈开满镜）门只保留给**重投影成品帧**
变体（它采样屏幕中心，滑入途中中心区叠着 viewmodel）。二次渲染（含光影）的合成只是把
窄 FOV 真画等位贴回，无此约束——`rerenderMode()` 下跳过该门，开镜即出现镜内画面
（26.2 母版实机行为）。lang 的 rerender 描述同步去掉 full-ADS 子句。

### 8.3 实机回归点
- [ ] 开镜+ESC 反复：不再崩溃（三道修复后，重绑与销毁路径均closed）；
- [ ] 光影二次渲染：滑入途中镜内即有画面（开镜即接管），镜外 1×；
- [ ] 装Voxy：预热线程不再出现 "Shutting down/Creating Voxy render system" 夹在
      "Creating pipeline for dimension tacz:scope_pip" 之后；
- [ ] 空闲释放（若开启）：日志 "Released" 后主画面 Voxy 远景照常、不崩。
