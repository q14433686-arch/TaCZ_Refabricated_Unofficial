# 甄别记录：26.1.2 渲染线 2026-08-31→09-01 批次 → 1.21.11 的等价移植

2026-09-01。对象：同仓库 Fabric **26.1.2** 线 `arena/01a05170-tacz-refabricated-unofficial`，
区间 `7562abcb`（我方上次复查的 their tip）→ `5c45a787`，共 **69 个提交 / 42 个非 ci-log**。

**方法**：不采信他们的自述文档（`docs/SYNC_1211_RENDER_20260901.md`，下称"他们的同步文本"），
按 `git log` 逐条取其**真实 diff**，逐个 `git cherry-pick -n` 到我方分支，冲突处手工合并并按
1.21.11 的渲染 API 改写。每个判定的依据都是"他们那个提交的补丁正文 + 我方文件实读"，不是他们的段落描述。

## 1. 判定总表

| 组 | 他们的 commit | 内容（读补丁所得） | 判定 | 我方落点 |
|---|---|---|---|---|
| G1 mesh 目镜裁剪 | `ee77059` `e203984` `d6743e5` | 新增 `core/mesh_entity_scope_clip.fsh`（他们 `scope_flash_clip` 的 mode-2 硬编码克隆）+ `LIT_PIPELINE_CLIP` + `drawList` 分流：无光影走自研 fsh 并绑两份私有深度拷贝，光影走 `beginExternalMaskOutsideDraw()`（GL-uniform 路线）+ `try/finally end()`；`ScopePipRenderState` 补公有访问器 `worldDepthViewFor/apertureDepthViewFor` | **移植（含 API 改写）** | `0fcb9512` 之前的两个移植提交；改写点：`withDepthStencilState/ColorTargetState/DefaultVertexFormat.ENTITY` → `withDepthTestFunction(LEQUAL)+withDepthWrite+withColorWrite+NEW_ENTITY`（本世代无前两型，见 `ScopeRenderTypes` 同款注释），fsh 路径用 `Identifier.fromNamespaceAndPath(MOD_ID,…)` |
| G2 掩码周期帧戳 | `752ee9e` `3a62d00` | 前者"帧首清标志"，**同日被后者推翻**：改为 `frameCounter` + `maskCycleFrame` 盖戳 + `hasMaskCycleThisFrame()`，**不清** `maskValid`（终局叠加在本帧手部阶段之前还要读跨帧真值） | **只移植终态（`3a62d00`）** | `ScopeDepthCopyState` + `GameRendererMixin`（render HEAD 递增帧计数）。`752ee9e` 的清标志部分被 `3a62d00` 就地删除，故按序 pick 后净效果正确 |
| G3 手部坐过镜内窄遍 | `5a41777` | `renderAtHandFlush()` 在 `isInsideScopeLevelRender()` 时清表早退（否则高模枪在镜内留"枪前端残影"） | **移植** | `PolyMeshGpuRenderer#renderAtHandFlush`（世界表相反：画而不清，我方既有裁定不变） |
| G4 PIP 合成闸与显示阈 | `f3e0f9c` `2f0e81c` `6f7b690` `7eca413` `5a2f280` `711dab2` `1ea031d` | ①`compositeAfterIrisFinal` 补 `rerenderMode() && !hasScene()` 早退（我方实读确认**缺**）；②两个合成入口加掩码周期**时效**闸；③显示阈从"重投影压全 ADS"演化为"两模式同一条 `PIP_REVEAL_THRESHOLD=0.35`"，且**捕获闸与 `needsIrisWorldDepthCopy` 同阈联动**；④重投影倍率沿滑入渐变（10 档、按参数缓存 `PIPELINES`）修"镜缘放大镜环残影" | **移植全部七条** | 见下方"踩过的坑"③：只搬 ③ 的前半会造出不自洽 |
| — 否决 | `b9f9db7` | 把全 ADS 门扩大到两种模式（把"一帧陈旧截图"与"过渡期正常镜内画面"混为一谈），用户实机否决，其同步文本也列为勿搬 | **不移植** | — |
| G5 光影下二次渲染隔离大件 | `3e8b22e` `1c2c5b5` `825d2c5` `95590b0` `82b3262` `c42b047` `2027261` `7b4a9a2` `3d8432f` `d3f0fdc` | Iris scope-pipeline 隔离（dimension id + 预热）、`ScopePipIsolatePipeline` 配置、ShadowScale、idle release 熔断、Voxy 第二渲染栈 + reload 钩子、Sodium 私有投影快照同步、ESC 崩溃三道闸 | **不移植（本世代前提不同）** | 上一版判定「不移植（写理由）」——**已被维护者裁定推翻**（"我说 VOXY 也搬过来"）：现整套落地，见 `SCOPE_PIP_SHADER_ISOLATION_PORT_2612_20260901.md` §2/§2b |
| G6 我方已各自落地 | `93178de` `2ae4c29` | 法线压 MV 栈、纹理解析移出 pass | **非项** | 我方 `014f4b0`（逐 entry `pushMatrix/mul/finally popMatrix`）与 `99e36e2`+`26bb33c`（`viewsByTexture` 整批 pass 前解析 + per-texture log-once）内容等价、形状不同：我们的 pass 是自建的，不像他们走 `RenderType#prepare + drawFromBuffer` |
| G7 纯查表 | `03a807e` | scope text + tooltips 的 `I18n.get` → 纯查表 | **非项（他们列错了）** | 我方 `c9b8ba1` 早做且范围更大（`PapiManager` + `ClientBlockItemTooltip` + `ClientAttachmentItemTooltip` 三处）；照他们说的"直接搬"会重复改 lang 与 PapiManager |
| G8 配置落盘 | `58831e4` `0651171` | FCAP `save()` no-op 的显式持久化规避 | **判错了，现已移植** | 原判依据是"我方全树搜不到 `save()`/`markDirty` 调用 ⇒ 21.11.1 无该病"——**搜不到落盘调用正是病本身**，而"21.11.1 无此病"出自他们类注释里的版本推测、不是我方实读。维护者实机确认本线同样"重启后配置回默认"；修法已按他们的形状落地（`config/ConfigPersist` + `mixin/client/ForgeConfigSpecAccessor` + Cloth `setSavingRunnable`，我方另加 `instanceof FileConfig` 分岔），见账本 L-21 与 CHANGELOG 第九则 |
| G9 世界消费点拓扑 | `99e505f` | 26.1.2 上把世界 GPU 表挂 `renderAllFeatures` RETURN 的两个后果 | **不移植（他们亦如此判）** | 我方 `renderAtWorldFlush` 已按本世代分派。L-12 更新：他们的四点位表不再是"挪前必读"，但"1.21.11 主通道那次是否世界 solid flush"仍未自证 ⇒ L-12 继续 OPEN，只是不再阻塞在对方 |
| G10 杂项 | `ba4f720` `3cf9d0f` `4c2e983` `b9ec08c` `6f55e07` `a70ad40` `27b7292` `e3af08e` `5a9d682` 等 | `RawOutput.log` 上传/删除、CI 重触发、TEMP javap 探针系列、docs 系列 | **非项** | 探针与崩溃附件无移植价值；我方按自家规矩"TEMP 同轮删" |

## 2. 踩过的坑（写给下一个跨世代搬运的人）

1. **他们的同步文本把 G4 拆成"第一批/第二批"，但阈值那条不自洽**：只搬 `7eca413`（0.35 只约束二次渲染）会留下
   "合成提前接管、捕获与强制拷贝仍压在全 ADS" ⇒ 显示第一帧无成品帧可贴。自洽的最小集合是
   `7eca413 + 5a2f280 + 711dab2 + 1ea031d`（阈值 + 四处联动 + 渐变）。我第一轮只搬了前半，第二轮补齐。
2. **`93178de`/`2ae4c29` 他们当作"待搬项"，其实我方当日已各自落地**（我方是这两处的原始受害/修复方）⇒
   跨分支同步文本对"对方已经做了什么"没有实时视野，一切以"我方文件实读"为准。
3. **冲突合并的手工失误**：`d6743e5` 与我的纹理预解析改动同函数，解决时少了一层右括号并把判据日志留成两份
   ⇒ CI 首轮红（`'try' without 'catch'` + 游离 CJK 文本被当代码）。教训：**移植完必须在同一轮跑编译门**，
   本地大括号配平只能证明"数目相等"，证不了嵌套形状。
4. **行号引用一律会过期**：他们文档写"`PolyMeshGpuRenderer.java:145` 的 LIT_PIPELINE"，我方移植后已在 170 附近
   ⇒ 复核只认 `类#方法` 与符号名。

## 3. 回答他们文档 §6 的五点（我方实读结论）

| 他们要我们核的 | 结论 |
|---|---|
| ① `IrisDepthRestoreShaderMixin` 的 `ShaderCreator.link` ordinal=4 在本世代是否仍命中 | **沙箱无法验**（无 Iris jar，`find` 无果）。我方该 mixin 存在且沿用同一条注入；失败语义是 fail-open（mode 恒 0 = 不裁剪、不炸）。判别：光影下镜内枪身不被裁但日志无异常 ⇒ 就是没命中，届时按 `docs/` 里"换 `_depthFunc` 补一刀"的既有路线处理 |
| ② `drawList` 结构差异（UBO/索引预热/纹理解析顺序） | 已逐行对齐：我方保留"pass 前写切片 + pass 外预热索引缓冲 + pass 外批解析纹理视图"三件，`apertureClip` 分支的 fsh/uniform 路线照他们终态并入；见坑 3 |
| ③ 我方 `renderScopeView` 对光影的当前闸状态 | **硬拒仍在**（`ScopePipRerender:150` `isUsingRenderPack() ⇒ return false`）⇒ 我方不是"放行但无隔离"，三症状无从出现；G5 因此可安全推迟 |
| ④ `compositeAfterIrisFinal` 的门栈全貌 | 移植后依次为：`isEnabled/failed` → `rerenderMode && !hasScene` → 当帧掩码周期时效闸 → Iris/包/终局钩子支持三闸 → `suppressesWorldFovZoom` → 同阈 `PIP_REVEAL_THRESHOLD` → `revealZoom(compositeZoom())` |
| ⑤ FCAP 版本是否同样需要显式 save | **需要**。原判"不需要"作废：本线同样中招、已落 `ConfigPersist`。同时纠正你们类注释里那句"1.21.11（FCAP v21.11.1）与 26.2 无此病"——对 1.21.11 不成立；26.2 与 NeoForge 全族经维护者确认无此病 |

## 4. 验证状态（按 AGENTS §2）

- 他们侧：其同步文本自称"CI 绿 + 用户实机 PASS（含当日全部反馈轮）"——**这是他们那条线的状态，不外推给我方**。
- 我方侧：`0fcb9512` 及两个移植提交，**编译门为准**（CI 首轮红已修，重推结果见账本 L-17 与本文件末尾的 run 记录）；
  **实机一律未验**，特别是 G1 的光影路线（依赖 ① 的 mixin 命中）与 G4 的渐变观感。
- 我方刻意不同的一处：`ScopeTextSubmitter` 仍用 `isMaskCycleValid()`（跨帧真值），**不**改成 `hasMaskCycleThisFrame()` ——
  镜内文字绘制发生在终局钩子（本帧手部阶段之前），"当帧"闸会把光影下的文字掩码整个打回 mode 0。
  这一条与他们的帧戳结论不冲突（他们治的是"与目镜序列同帧"的消费者），但**必须在两边文档里写明**，
  否则下一个搬运者会顺手统一掉。

## 5. 编译门记录（本分支）

| 提交 | 内容 | compile-check |
|---|---|---|
| `8d28e575` | G1+G2+G3+G4 前半 | **红**（`'try' without 'catch'`、游离 CJK 文本、`RERENDER_REVEAL_THRESHOLD` 已被改名） |
| `04869be2` | G4 阈值统一 + 倍率渐变 | 同上（同一次 HEAD 一起失败） |
| `0fcb9512` | 补右括号、删重复日志尾巴、删引用旧常量的重复门 | **绿**（run `3349…` 之后的重推 run：success） |
| `docs` 提交 | 本甄别篇 | **绿** |

结论：形状与符号问题已全部收敛到编译门通过；**实机仍全未验**（G1 光影路线依赖 ① 的 mixin 命中、
G4 的渐变观感需人眼）。