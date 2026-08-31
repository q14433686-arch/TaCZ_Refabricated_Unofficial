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
scope RenderType，vanilla 字体管线不在也不该在 —— 但**别据此以为文字会被裁进镜孔**：

**（2026-09-01 更正）本节上一版在这里断言「文字要的正是被镜筒深度剔掉这个行为，在本分支的孔径架构里
等价于 26.2 的掩码裁剪」。该论断已被 26.1.2 的实机证伪，他们据此在 `e1c550ee` 改走掩码裁剪。原因：
`submitText` 下游是 vanilla 字体管线（`TextFeatureRenderer` → `GlyphRenderTypes` 三件套写死 RenderType），
**不吃** scope body 写入的孔径深度 ⇒ 镜孔外的像素照画。所以本轮 flush 只保证「层序正确」（画面→文字→
准星→镜框），不保证「裁进镜孔」；裁剪是 §B 待加项，见
`docs/lineage/SYNC_REVIEW_2612_PIP_BACKPORT_20260901.md`。）**

---

## 3. 状态与边界（按 AGENTS §2，把「绕开」和「修好」分开写）

- **静态层面已闭合**：`submitFunctionalTasks` 从「全仓 1 处、且不在瞄具路径」变成「瞄具两条分支都覆盖」；
  任务来源、采集点、丢失点、修复点四处都能对上代码（§1）。
- **实机未验证**：本轮只做了代码改动与 CI 编译。下面 §4 是验收剧本，**没跑过之前不要宣称已修好**。
- **已知残留（不是本轮范围）**：
  ① **镜内文字当前没有任何裁剪**（上一版把范围写小了，只说延迟那一格）：字体管线不吃孔径深度 ⇒
  **立即路径与延迟路径都会溢出圆孔**，延迟格只是更明显。贴边数据（26.2 记过 MK5HD 文本在
  `y=22.375`、目镜 `y=21.875`）说明这不是理论风险；正解 = 26.2 `9d036594` 的语义 + 本分支的掩码管线
  （`ScopeTextSubmitter` + `maskedText`），本分支**未移植**，评估与成本见
  `docs/lineage/SYNC_REVIEW_2612_PIP_BACKPORT_20260901.md` §B；
  ② `deferReticleToIrisTranslucent`（未审计的旧 Iris 回退路径）保持**立即提交**，文字可能被光影包
  后处理盖掉一层 —— 与准星在那条回退路径上的既有取舍一致。
  ③ 掩码 `SCOPE_MASK_ENABLE=false` 时不成立 `orderedScopeSequence`，走 `else` 分支 ⇒ 文字常显（不裁），
  与上游 1.21.1 的「不裁剪」行为一致，属预期。

---

## 4. 实机剧本（四格 + 一格 PIP；维护者/26.1.2 任一边跑完回我们结论即可）

前置：装 MK5 与 MK5HD 的枪包，`gun` 上镜，进第一人称。判据统一是**镜内应出现弹药计数文本**。

两条**日志判据**（不必靠肉眼，每局各最多一条）：

- 立即提交那一格（A / B / E）：`[TACZ Scope] Flushed 1 in-lens text task(s) in the solid hand pass (scopeMask=true)`
  —— `scopeMask` 就是 `SCOPE_MASK_ENABLE`，可以直接确认自己站在剧本的哪一格；
- 延迟覆盖层那一格（C / D）：`[TACZ Scope] Rendered deferred reticle, ocular rim and scope text after the
  final cover (N reticles, M rims, K texts).`，**K ≥ 1** 才算文字真的走到了覆盖层。

| # | 条件 | 期望 | 若不符 |
|---|---|---|---|
| A | 无光影，`SCOPE_MASK_ENABLE=true`（默认） | 开镜进度过 0.35 后文字出现在镜内，层序为画面→文字→准星→镜框；日志有 `Flushed N in-lens text task(s) … (scopeMask=true)` | ⚠ 上一版此处「不越过镜筒边缘（被深度剔掉）」**作废**：没有机制会剔它 ⇒ 贴边溢出属 §B 待加项，不是 flush 回归 |
| B | 无光影，`SCOPE_MASK_ENABLE=false` | 文字常显（可能在镜筒外也看得到，属预期）；日志 `… (scopeMask=false)` | 走到 `else` 分支，正是 §2.2 差别① 那一格 ⇒ 若这里没文字而 A 有，说明只有 text 的快照又被 `isEmpty()` 门挡了（本分支已挪出门外） |
| C | 有光影（Iris 已审计的 final-overlay 路径），PIP 关 | 文字与准星同批出现，不被雾/后处理盖掉；日志出现一次 `[TACZ Scope] Rendered deferred reticle, ocular rim and scope text after the final cover (N reticles, M rims, K texts)` 且 **K ≥ 1** | K=0 ⇒ 说明 `deferReticleToIrisFinalOverlay` 没成立，走了立即提交；再看是否与 §3 残留 ② 一致 |
| D | 有光影 + `ScopePipAllowShaderPacks=true` | 镜内画面之上叠文字，文字在准星**之下**（准星压住文字） | 顺序反了 ⇒ 检查 `renderAfterFinalComposite()` 里 texts 是否在 reticle 之前 |
| E | 无光影 + PIP 开（`SCOPE_PIP_RERENDER` 那套） | 同 D；`GameRendererMixin` 的 flush 门（`hasPendingOverlay()` 或 `isEnabled()` 任一成立）兜住 | 文字完全不见 ⇒ 检查 `PENDING_TEXT` 是否被 `beginSolidSubmission()` 提前清掉 |

另外两条顺手确认（都是这次改动可能碰到的既有行为）：

1. **第三人称 / 物品栏 / 展示框**：瞄具外观无变化（`extract` 里 `firstPerson()` 门禁 ⇒ 这些语境任务为空）；
2. **性能**：文字任务每帧一次 `submitText`，无新增 FBO/掩码；若发现开镜时帧时间抖动，
   优先怀疑 `captureStandalonePart(ocularRingPart)` 那一族的可见性临时改动，而不是 flush 本身。

---

## 5. 第二个根因：`PapiManager` 把「查表」写成了「格式化」（2026-09-01 同日补修）

维护者指出「镜内文字这个 BUG 和 26.2 近期修的一模一样」。核完代码，这句话**成立**，而且答案与渲染架构无关：

- 26.2 在 `ec51f556`（2026-08-30）修的就是 `PapiManager.getTextShow` —— 从 `I18n.get(textKey)`
  换成 `Language.getInstance().getOrDefault(textKey)`；
- 本分支的 `PapiManager.java:28` 当时**仍是** `I18n.get(textKey)`（`grep -n` 可自查）⇒ 同一个 bug，
  因为这一行是两条分支从同一祖先**逐字继承**的共享代码，不在 aperture / stencil / collector 任何一层的架构差异里。
  所以「架构不同却症状相同」不是巧合，而是「bug 根本不在架构那一层」。

### 5.1 `I18n.get` 在 1.21.11 上到底做什么（javap 实测，不是引用 26.2 的结论）

本沙箱没有 JDK/MC jar，所以结论走 CI 的 javap 通道（TEMP 探针在 `build.gradle`，输出已由
`ci-log` 提交 `03a3fa2` 落进 `build-reports/compile-java.log`，第 85-128 行；探针块本轮已删除）：

```
public static String get(String, Object...):
   0: getstatic     language:Lnet/minecraft/locale/Language;
   4: invokevirtual Language.getOrDefault:(Ljava/lang/String;)Ljava/lang/String;
   7: astore_2                                  // s = 查表结果（键不存在时就是键本身）
  13: invokestatic  String.format:(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)
  Exception table: 8-16 -> 17 Class java/util/IllegalFormatException
  19: invokedynamic makeConcatWithConstants(String)   // "Format error: " + s
```

⇒ **查表之后必过 `String.format`**，`%` 触发的 `IllegalFormatException` 被 catch 成 `"Format error: " + 原文`。
MK5HD 的 `textKey` 是内联串 `"%ammo_count%"`：查表落空 → 原样返回 → `String.format` 把 `%a` 当格式说明符
→ 返回 `"Format error: %ammo_count%"` → 之后的占位符替换又在这串尾部补上真实弹药数 ⇒
**镜内显示「Format error: … 30」**。含 `%` 的正常译文（如 `%s发`）同样炸。
`Language#getInstance()` / `#getOrDefault(String)` 在 1.21.11 的存在性也由同一次 javap 证实（第 80-84 行），
所以修复用的 API 与 26.2 完全一致，不是"照抄了一个可能不存在的成员"。

### 5.2 两条 bug 是叠加的，不是一条

| | 症状 | 本分支状态 |
|---|---|---|
| ① `functionalTasks` 没 flush（§1-§2） | 文字**完全不出现** | 已修（`1cfa42b`） |
| ② `I18n.get` 当格式串（§5） | 文字出现但被 `"Format error: …"` 污染 | 已修（`c9b8ba1`） |

26.2 只有 ②（他们的 body 走 `super.submit` ⇒ ① 天然不成立），所以他们的症状是「有字但脏」；
本分支两个都有，① 在前 ⇒ 表现为「什么都没有」。**只做②的移植对本分支无效**（照抄他们的补丁会留下
「修了但还是不显示」）；只做①则会立刻暴露 ②。这就是维护者看到"一模一样"却"不知道哪里有问题"的原因：
本分支需要先补自己独有的那一条，再对齐他们那一条。

### 5.3 同一形在本仓/兄弟仓的分布（逐文件 `grep` 实测，别按"应该只有 text_show"理解）

| 位置 | 1.21.11（本分支） | 26.2 `arena/01a04e96` | 26.1.2 `arena/01a05170` |
|---|---|---|---|
| `model/papi/PapiManager.getTextShow` | 已改 `getOrDefault` | 已改（`ec51f556`） | **仍是 `I18n.get(textKey)`（`:28`）** |
| `client/tooltip/ClientAttachmentItemTooltip` | 已改 | **仍是 `I18n.get(tooltipKey)`（`:165`）** | 仍是 |
| `client/tooltip/ClientBlockItemTooltip` | 已改 | **仍是（`:75`）** | 仍是 |

两处 tooltip 的下游就是 `text.split("\n")` → 逐行 `Component.literal(s)` ⇒ **从来不需要格式化**，
纯查表才是本意；枪包把 `tooltip_key` 写成内联串（含 `%`，例如"伤害 +20%"）时同样会变 "Format error"。
⇒ 本分支是三处一起收的；26.2 只收了一处；26.1.2 三处都还在（他们刚补的 flush 会把 ② 立刻显出来）。
已按 §9 的口径把这张表回给两兄弟分支。

---

## 6. 与 26.1.2 的往来

- 他们已提交同一修复（`c290a1f3` + `74eb0ad2`），报告里给的「掩码开/关 × Iris 开/关按 STEP 剧本复核」
  我们照抄成 §4，并注明本分支的 STEP 文档是 `docs/SCOPE_PIP_DEPTH_1211_STEP1/2/3_20260830.md`
  （他们的 `docs/` 里没有这几篇，别按我们的编号去找）。本篇按本仓约定放在 `docs/lineage/` 下
  （「新文档进 `lineage/`，存量文件不动」，见 `docs/README.md` 首节）。
- 回礼两条见 §2.2。若他们同意，`else` 分支那句挪出 `isEmpty()` 门值得他们同步 —— 那是同一类
  「快照 isEmpty 只看几何」的判定偏差，`BedrockRenderSnapshot#isEmpty()` 本身也建议在文档里写明
  「不代表没有 functional 任务」。
