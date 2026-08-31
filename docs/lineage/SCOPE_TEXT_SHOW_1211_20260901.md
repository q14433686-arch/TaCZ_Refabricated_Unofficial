# 镜内 `text_show` 文本缺失（MK5 / MK5HD 弹药计数）：根因、修法与实机剧本

日期 2026-09-01。触发材料：26.1.2 线的根因报告与他们的修复（`c290a1f3` + 编译更正 `74eb0ad2`）。
本篇是**本分支（1.21.11）侧的核对与落地记录**；本仓此前把这条症状归到「文字溢出」那类外观问题上，
**没有单独立项**，所以这份文档同时补上缺的那段历史。

> **一句话**：不是字体管线坏了，是**提交任务被整段丢掉**。`BedrockAttachmentModel.submit` 在深度孔径
> 路径上把瞄具冻结成 `bodySnapshot` 之后只调 `bodySnapshot.write(consumer)`（重放几何），而
> `TextShowRender.extract` 写进 `BedrockRenderSnapshot#functionalTasks` 的那条 `submitText` 任务
> 从未被 flush ⇒ 镜内文字**永远不出现**。26.2 没这个洞（见 §1.3）。

---

## 1. 静态证据（本沙箱里可复算，不需要实机）

### 1.1 flush 只有一个调用点，而且不在瞄具路径上

```bash
grep -rn "submitFunctionalTasks" src/main/java
# src/main/java/com/tacz/guns/client/renderer/snapshot/BedrockRenderSnapshot.java:93   ← 定义
# src/main/java/com/tacz/guns/client/model/bedrock/BedrockModel.java:381              ← 唯一调用
```

`BedrockModel.submit(...)` 的形状是「`capture(...)` → `collector.submitCustomGeometry(... snapshot.write ...)`
→ **`snapshot.submitFunctionalTasks(collector)`**」。而 `BedrockAttachmentModel.submit(...)`（子类自己重写的
完整实现）**没有调用 `super.submit(...)`**：它自己 capture、自己在
`SCOPE_APERTURE_ORDER/BODY/DEPTH_CLEANUP/OCULAR_RING` 上重放几何 —— 那一句 flush 因此被整个绕开。
`IFunctionalSubmitter#render` 是刻意空实现（防止旧的同缓冲即时绘制路径被误用），所以**没有任何兜底**：
任务既不进几何，也不即时画 ⇒ 静默消失。

### 1.2 任务确实被采集了（不是「根本没生成」）

`BedrockRenderSnapshot.Builder#capturePart` 遇到 `IFunctionalSubmitter` 时调用
`submitter.extract(new ExtractionContext(..., this.functionalTasks::add))` 然后**直接 return**
（几何不采集）——也就是说 `text_show` 节点的全部产出**只存在于 `functionalTasks` 里**。
`TextShowRender.extract` 在 `displayContext().firstPerson()` 且文本非空时 `context.add(collector ->
collector.submitText(...))` ⇒ 一旦快照的 `functionalTasks` 不被 flush，文字就 100% 丢失，
不存在「偶尔画不出来」的中间态。这与维护者的报告一致：1.21.11 线**从不显示**。

另外两条边界（都在我们这边核过，说明为什么这条一直没人发现）：

- 枪身上的 `text_show`（`BedrockGunModel.setTextShowList`）走 `BedrockModel.submit` ⇒ 有 flush ⇒
  弹匣计数那类**一直是好的**，只有瞄具本体上的是空的；
- 本分支 `BedrockAttachmentModel.setTextShowList` 有一道 `TEXT_SHOW_AIM_START` 门禁
  （未开镜不提交，见该类第 433-467 行的 javadoc），所以腰射时本来就不该看到 ⇒ 观感被解释成
  「镜内没这功能」而不是「渲染丢了」。

### 1.3 「26.2 没这个洞」是我自己核过的，不是转述

```bash
gh api "repos/<repo>/contents/src/main/java/com/tacz/guns/client/model/BedrockAttachmentModel.java?ref=arena/01a04e96-tacz-refabricated-unofficial" \
  --jq '.content' | base64 -d | grep -n "super.submit"
# 681:            super.submit(poseStack, transformType, collector, ...
```

26.2 的瞄具 body 走 `super.submit(...)`，天然带 §1.1 那句 flush。26.2 后来另修的 `9d036594`
（`ScopeTextSubmitter` + `scope_text.vsh/fsh`，镜内掩码裁剪）处理的是**文字溢出镜筒**，
与「存在性」无关 —— 本分支**没有**这套（`ScopeTextSubmitter`/`ScopeTextRenderTypes`/`scope_text.*sh`
四个文件在本仓 `find` 均无命中），只有 `TEXT_SHOW_AIM_START` 那道保守门禁。⇒ 两边修法不可互换：
**先补 flush（存在性），再谈裁剪（观感）**。

---

## 2. 本分支的修法（三个文件，全部按 26.1.2 的口径，另加两处差别）

| 文件 | 改动 |
|---|---|
| `renderer/snapshot/BedrockRenderSnapshot.java` | 新增只读访问器 `functionalTasks()`（延迟路径要逐条转发，不能整批 flush） |
| `client/model/BedrockAttachmentModel.java` | ① `orderedScopeSequence` 分支在 depth-cleanup 提交之后补 flush：默认走 `collector` 的 `order(0)`，落在 `SCOPE_DEPTH_CLEANUP_ORDER=-1` 之后、`SCOPE_RETICLE_ORDER=1` 之前；`deferReticleToIrisFinalOverlay` 时改道覆盖层。`bodySnapshot`、每个 `ocularSnapshots`、`ocularRingSnapshot` 各 flush 一次。② 新增私有静态 `submitScopeText(snapshot, collector, defer)` 收敛这个分叉 |
| `client/render/scope/ScopeFinalOverlayState.java` | 新增 `PENDING_TEXT` 队列 + `queueFunctionalTask(task)`（内部 `captureHandTransform()`，所以「只有文字、没排准星」的那一帧也能 flush）；`beginSolidSubmission()` 一并清空；`hasPendingOverlay()` 计入；`renderAfterFinalComposite()` 在 reticle(20_000)/rim(20_001) **之前**用 `task.submit(submitNodes)` 提交 |

### 2.1 为什么延迟路径必须 `task.submit(submitNodes)`，不能用 `submitNodes.order(FINAL_TEXT_ORDER)`

`SubmitTask#submit` 要的是 `SubmitNodeCollector`；`order(int)` 返回的 `OrderedSubmitNodeCollector`
**不是**它能接收的类型（26.1.2 那边第一版 `c290a1f3` 就是这么写的，编译失败，`74eb0ad2` 改成直发
storage）。本仓 `SubmitNodeStorage` 就是 collector 这件事有现成旁证：`GunPreviewRenderer.java:91` 把
`getFeatureRenderDispatcher().getSubmitNodeStorage()` 直接当 collector 传给 `submit(...)`，注释也写了
「SubmitNodeStorage 就是本帧的 SubmitNodeCollector」。⇒ 文字落在 storage 的**默认 order 桶**，
天然在 reticle/rim 之下 ⇒ 覆盖层内的物理顺序是：镜内画面 → 文字 → 准星 → 镜框遮光。

### 2.2 与 26.1.2 那版补丁的两处差别（回礼，已写进账本 L-10）

1. **`else`（非镜内序列）分支**：他们把 `bodySnapshot.submitFunctionalTasks(collector)` 放在
   `else if (!bodySnapshot.isEmpty())` **里面**。而 `BedrockRenderSnapshot.isEmpty()` 只看
   `drawCommands` ⇒ 「只有 `text_show`、本体没有几何」的快照（带文字的第三方镜骨骼、或本体被全部
   过滤掉的场合）在他们那边仍会被丢。我们把几何重放关进内层 `if`，flush 放在门外。
2. **`ocularRingSnapshot` 的任务**：他们只兜了 `bodySnapshot` 与 `ocularSnapshots`。镜框子树下挂文字
   时那份任务既不在 body（部件被临时隐藏）也不在 ocular 快照里。我们把 ring 一并 flush，
   且在延迟模式下同样改道覆盖层（与 ring 几何的延迟保持一致）。

**高模（TML）瞄具一并受益**：`cn/sh1rocu/tacz/compat/meshloader/model/TaczPolyMeshAttachmentModel.java:50`
就是 `super.submit(attachmentItem, …)` 转发 ⇒ 它走的正是本次修好的那条路径，不需要单独补。枪身本体
（`BedrockGunModel`）本来就走 `BedrockModel.submit`，不受影响。

顺带核过、**不需要**动的：`GlCommandEncoderScopeDepthCopyMixin` 的 `GL_ALWAYS` 白名单只匹配 TACZ 自己的
scope RenderType，vanilla 字体管线不在也不该在 —— 文字要的正是「被镜筒深度剔掉」这个行为
（本分支的孔径架构里它就等价于 26.2 的掩码裁剪）。

---

## 3. 状态与边界（按 AGENTS §2，把「绕开」和「修好」分开写）

- **静态层面已闭合**：`submitFunctionalTasks` 从「全仓 1 处、且不在瞄具路径」变成「瞄具两条分支都覆盖」；
  任务来源、采集点、丢失点、修复点四处都能对上代码（§1）。
- **实机未验证实机**：本轮只做了代码改动与 CI 编译。下面 §4 是验收剧本，**没跑过之前不要宣称已修好**。
- **已知残留（不是本轮范围）**：
  ① 延迟覆盖层那一格（Iris final-overlay / PIP）里文字在**覆盖层阶段**画，镜筒深度已不参与 ⇒
  贴边的计数（26.2 记过 MK5HD 文本在 `y=22.375`、目镜 `y=21.875`）**仍可能溢出圆孔边缘**；
  要真正裁掉需要把 26.2 的 `ScopeTextSubmitter` + `scope_text.*sh` 那套移植过来，本分支现在没有。
  ② `deferReticleToIrisTranslucent`（未审计的旧 Iris 回退路径）保持**立即提交**，文字可能被光影包
  后处理盖掉一层 —— 与准星在那条回退路径上的既有取舍一致。
  ③ 掩码 `SCOPE_MASK_ENABLE=false` 时不成立 `orderedScopeSequence`，走 `else` 分支 ⇒ 文字常显（不裁），
  与上游 1.21.1 的「不裁剪」行为一致，属预期。

---

## 4. 实机剧本（四格 + 一格 PIP；维护者/26.1.2 任一边跑完回我们结论即可）

前置：装 MK5 与 MK5HD 的枪包，`gun` 上镜，进第一人称。判据统一是**镜内应出现弹药计数文本**。

| # | 条件 | 期望 | 若不符 |
|---|---|---|---|
| A | 无光影，`SCOPE_MASK_ENABLE=true`（默认） | 开镜进度过 0.35 后文字出现在镜内，**不越过镜筒边缘**（被深度剔掉） | 若完全没有文字 ⇒ 查 `TextShow` 是否配在 attachment display 上、`PapiManager` 是否返回空串 |
| B | 无光影，`SCOPE_MASK_ENABLE=false` | 文字常显（可能在镜筒外也看得到，属预期） | — |
| C | 有光影（Iris 已审计的 final-overlay 路径），PIP 关 | 文字与准星同批出现，不被雾/后处理盖掉；日志出现一次 `[TACZ Scope] Rendered deferred reticle, ocular rim and scope text after the final cover (N reticles, M rims, K texts)` 且 **K ≥ 1** | K=0 ⇒ 说明 `deferReticleToIrisFinalOverlay` 没成立，走了立即提交；再看是否与 §3 残留 ② 一致 |
| D | 有光影 + `ScopePipAllowShaderPacks=true` | 镜内画面之上叠文字，文字在准星**之下**（准星压住文字） | 顺序反了 ⇒ 检查 `renderAfterFinalComposite()` 里 texts 是否在 reticle 之前 |
| E | 无光影 + PIP 开（`SCOPE_PIP_RERENDER` 那套） | 同 D；`GameRendererMixin` 的 flush 门（`hasPendingOverlay()` 或 `isEnabled()` 任一成立）兜住 | 文字完全不见 ⇒ 检查 `PENDING_TEXT` 是否被 `beginSolidSubmission()` 提前清掉 |

另外两条顺手确认（都是这次改动可能碰到的既有行为）：

1. **第三人称 / 物品栏 / 展示框**：瞄具外观无变化（`extract` 里 `firstPerson()` 门禁 ⇒ 这些语境任务为空）；
2. **性能**：文字任务每帧一次 `submitText`，无新增 FBO/掩码；若发现开镜时帧时间抖动，
   优先怀疑 `captureStandalonePart(ocularRingPart)` 那一族的可见性临时改动，而不是 flush 本身。

---

## 5. 与 26.1.2 的往来

- 他们已提交同一修复（`c290a1f3` + `74eb0ad2`），报告里给的「掩码开/关 × Iris 开/关按 STEP 剧本复核」
  我们照抄成 §4，并注明本分支的 STEP 文档是 `docs/SCOPE_PIP_DEPTH_1211_STEP1/2/3_20260830.md`
  （他们的 `docs/` 里没有这几篇，别按我们的编号去找）。本篇按本仓约定放在 `docs/lineage/` 下
  （「新文档进 `lineage/`，存量文件不动」，见 `docs/README.md` 首节）。
- 回礼两条见 §2.2。若他们同意，`else` 分支那句挪出 `isEmpty()` 门值得他们同步 —— 那是同一类
  「快照 isEmpty 只看几何」的判定偏差，`BedrockRenderSnapshot#isEmpty()` 本身也建议在文档里写明
  「不代表没有 functional 任务」。
