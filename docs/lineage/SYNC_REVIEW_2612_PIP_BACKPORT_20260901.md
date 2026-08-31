# 26.1.2 这轮 PIP 回移植：哪些能加进 1.21.11、怎么加（评估 + 落地顺序）

日期 2026-09-01。对象是他们 `arena/01a05170` 的 `0a77ef52`…`8aca7374`（tip `8aca7374`，ci-log `4b0f3cc2`
success），以及他们自写的取证文档 `docs/SCOPE_PIP_STATE_REEXTRACT_AND_MASKED_TEXT_20260901.md`
（196 行，本文引用时称「他们的回归文档」）。

**先说结论**：这轮里**值得搬的只有两条半**，其余大部分是**从我们这边搬走的东西**（回搬等于空转）。

| 项 | 他们那轮的提交 | 我们能不能加 | 结论 |
|---|---|---|---|
| A. PIP 窄遍之后的**逐帧状态重提取 + 清提交节点** | `0bf4c482`（+新 `LevelRendererAccessor` mixin） | **待探针判定**（取决于 1.21.11 有没有共享状态袋），但**第一步（清提交节点）无论如何都该加** | 见 §1 |
| B. 镜内文字**掩码裁剪** | `e1c550ee`（`ScopeTextSubmitter` + `scope_text_final.fsh` + `ScopeRenderTypes.maskedText` + `clipToScopeMask` 旗） | **能加**，而且这是本分支那条"文字溢出圆孔"残留的唯一正解 | 见 §2 |
| C. `ScopePipRerenderInterval` 隔帧渲染 | `8aca7374`（源自 26.2） | 能加，成本很小，但**要排在 A 之后** | 见 §3 |
| D. `ScopePipRenderState` / `ScopePipDepthDebug` / PIP 配置面 / `DepthHandle` 只读快照 | `99ccb8c8`、`297f127a`、`bf42f3a3`、`8ea41c2d` | **不需要**：这些本来就是 1.21.11 的东西，他们是从我们这棵树上搬过去的 | 见 §4 |
| E. 「光影下两个 GPU 键默认 ON」 | `3e4eeb16` | **不要跟**：我们这边的 B 测结论相反 | 见 §5 |

---

## 1. A：窄遍之后的状态重提取 —— 我们的类注释里挂着的那条"后续阶段"

他们的实测链条（26.1.2 世代，逐条字节码定位，原文在他们回归文档 §0）：

```
GameRenderer.extract → LevelRenderer.extractLevel(...)   ← 每帧一次，把实体/区块/雾/天空写进 LevelRenderState
GameRenderer.renderLevel → LevelRenderer.renderLevel(9 参) ← 消费那袋状态
                     尾部 @560 → LevelRenderState.reset()  ← 消费即清空，一次性燃料
```
PIP 的窄 FOV 遍在主遍**之前**多调一次 `renderLevel` ⇒ 把袋子消费并清空 ⇒ 主遍拿到空袋 ⇒
**镜外实体、太阳/天空、雾与天气、粒子、方块高亮全灭**。他们据此在窄遍返回前补了两步：

1. `((LevelRendererAccessor) levelRenderer).tacz$getSubmitNodeStorage().clear()`（残件防御性清理，成本≈0）；
2. `levelRenderer.extractLevel(deltaTracker, mc.gameRenderer.getMainCamera(), partialTicks)`（重跑提取）。

### 1.1 我们这边为什么不能直接照抄，也不能直接说"我们没有这病"

- 我们的 `LevelRenderer#renderLevel` 是 **10 参**、投影/视图/裁剪全部显式传参（见我们
  `ScopePipRerender` 类注释的 javap 对照），这**只**说明几何输入自洽，**不**说明实体/雾/天空那部分是自洽的；
- 他们文档里"1.21.11 每次调用自洽 ⇒ 没这些 BUG"是从**维护者实机没见到症状**推的 —— 而我们这条
  路径**默认关闭**（`SCOPE_PIP_RERENDER=false`，账本 L-5 的 DECLINED 就是它）⇒ "没人开过"与"没有这个洞"
  是两件事，不能拿来当否证；
- 我们自己的类注释早就写着：「一帧内驱动两次 `renderLevel` 会推进两遍区块编译/实体提取等逐帧状态 …
  **把提交节点保留等防护留给后续阶段**」⇒ 这条防护是**已知欠账**，不是新发现。

### 1.2 判定所需的事实（本轮已挂 TEMP 探针，输出进 `build-reports/compile-java.log`）

- `net.minecraft.client.renderer.LevelRenderer` 里有没有 `extractLevel(...)`（以及它的可见性）；
- 有没有 `LevelRenderState` 这个类、它有没有 `reset()`；`LevelRenderer` 是否持有该字段；
- `GameRenderer` 是否有独立的 `extract(...)`；
- `SubmitNodeStorage#clear()` 是否存在（我们的 `ScopeFinalOverlayState` 已经在用 `endFrame()`）。

### 1.3 两种结果各自的改法（都很小，都不影响默认路径）

**若 1.21.11 也有共享状态袋**（存在 `extractLevel` + `LevelRenderState.reset`）⇒ 完整搬他们两步：

```java
// ScopePipRerender.renderScopeView(...) 里，窄遍 renderLevel 返回、主目标已拷走之后：
((LevelRendererAccessor) levelRenderer).tacz$getSubmitNodeStorage().clear();
levelRenderer.extractLevel(mc.getDeltaTracker(), mc.gameRenderer.getMainCamera(), partialTicks);
```
外加：新 mixin `com/tacz/guns/mixin/client/LevelRendererAccessor.java`（`@Accessor("submitNodeStorage")`）
**并注册进 `tacz.mixins.json` 的 `client` 段**（他们上一轮就是栽在漏注册，我复核篇 §1；别重演）；
`@Mixin(LevelRenderer.class)` 的字段访问器在 1.21.11 用 intermediary 时**不需要**（accessor 目标名是
混淆可变的字段名 ⇒ 我们的 AGENTS §3 要求：1.21.11 侧 mixin 目标一律用 `method_NNNNN`/官方可编译名，
`@Accessor("submitNodeStorage")` 属字段名，需要与我们的 `verify_mixin_targets.py` 一起过一遍）。

**若 1.21.11 没有状态袋**（`renderLevel` 自己提取自己消费）⇒ 只加第 1 步（清提交节点），
并在我们的 `ScopePipRerender` 类注释里把「把防护留给后续阶段」这句改掉，同时**回赠**给 26.1.2：
他们的第 2 步对我们不适用，原因写清（免得下次有人按他们版本"对齐"出一条无用的重提取）。

⚠ 无论哪一种，**验收都必须实机**：`ScopePipRerender=true` + 开镜，看镜外实体/太阳/雾/天气/粒子
与关掉时一致、镜内窄 FOV 画面不变、无重影/半透明加倍（他们回归文档 §5 的第 1 条就是这条）。

---

## 2. B：镜内文字的掩码裁剪 —— 我们那条残留的唯一正解

上一轮我在 `SCOPE_TEXT_SHOW_1211_20260901.md` §2/§3 断言「文字走字体管线会被镜筒深度剔掉，等价于
26.2 的掩码裁剪」，**该论断已被他们的实机否证**，我在本轮已把文档与代码注释改成"只保证层序、不保证
裁剪"，并把这条列为待加项。他们的做法（`e1c550ee`）是把 26.2 `9d036594` 的**语义**接到**深度孔径**架构上：

| 需要的部件 | 他们的实现 | 我们这边的现状 |
|---|---|---|
| 字形级遍历 | `Font#prepareText(seq,x,y,color,shadow,false,0)` → `PreparedText#visit(GlyphVisitor)`，按 `TextRenderable` 的图集页分组 | 未用过（全仓 `prepareText` 0 命中）⇒ **待探针核实我们的 era 是否同形** |
| 页纹理绑定 | `AbstractTexture` 空壳 `PageHandle` + 壳 `Identifier`，每帧刷新指向 | 无 ⇒ 需要新写（约 60 行） |
| 裁剪 RenderType | `ScopeRenderTypes.maskedText(pageId)` = `clonePipeline(RenderPipelines.TEXT)` + 换 fsh + 两个深度采样器，Iris 归 `HAND_TRANSLUCENT` | **有同款设施**：`ScopeRenderTypes` 已经在克隆 `ENTITY_CUTOUT` 并挂 `worldDepthTarget/apertureDepthTarget` 采样（蚀刻准星那条 MASK 分支）⇒ 是"再加一族"，不是新架构 |
| 掩码着色器 | `scope_text_final.fsh` = 本 era `rendertype_text.fsh` 逐行克隆 + `tacz_ScopeMaskMode` 分支 + `tacz_ScopeFinalOverlay`（post-composite 走私有深度拷贝并绕过目标身份守卫） | 我们有 `scope_reticle_final.fsh`/`scope_reticle_mask.fsh`，**同一条绕过守卫的写法可直接复用**；缺的是本 era 的 `rendertype_text.fsh` 原文（本轮探针连 jar 里的 `.fsh/.json` 一起 dump 出来，不必猜） |
| 语义接入 | `TextShowRender` 加 `clipToScopeMask` 旗（瞄具侧 true、枪身 false），任务执行时掩码失败**回退 vanilla `submitText`**，"不丢字、不画错" | 我们的 `TextShowRender` 与他们改前**逐字相同**（同一祖先）⇒ 那段 diff 可以原样落 |
| 有效性闸门 | `ScopeDepthCopyState.isMaskCycleValid()`（本帧是否走完「备份 + 孔径拷贝」周期） | 我们有 `worldDepthTarget()/apertureDepthTarget()` + `DepthHandle.snapshot()`（他们 `8ea41c2d` 就是搬这个）⇒ 只需加一个"本帧周期是否完整"的判据 |

**能不能加**：能，且**该加** —— 没有它，剧本 A 格永远不可能"贴边不溢出"；而镜内弹药计数（MK5HD）恰好
就贴在边上。
**代价**：新增 1 个类（约 180 行）+ 1 个 `.fsh`（约 57 行）+ `ScopeRenderTypes` 一族 + `TextShowRender`
旗 + 一个"页→壳纹理"的小管理器；需要探针确认 5 个 API（`prepareText`、`PreparedText#visit`、
`GlyphVisitor` 的两个方法名、`TextRenderable$Styled`、`RenderPipelines.TEXT` 的管线构造入口）与
`rendertype_text.fsh` 原文。**全部落在字体绘制这一条线上，不动孔径三步本身** ⇒ 回归面可控。
**风险**：① 自定义 program 的 uniform 名/采样器与我们的 `verify_shader_imports.py` 要对齐；
② 多页字体/资源包字体（他们的验收矩阵第 5 条），我们这边同样只能靠实机；③ Iris 归类若错会多出一次
后处理 ⇒ 沿用准星那族的 `HAND_TRANSLUCENT` 归类即可。
**兜底设计照抄他们**：掩码不可用/程序失败 ⇒ 回退 vanilla `submitText`（宁可溢出，不可丢字）。

---

## 3. C：`ScopePipRerenderInterval`（隔帧渲染）—— 能加，但要排队

他们从 26.2 搬来的三件套：`RenderConfig.SCOPE_PIP_RERENDER_INTERVAL`（键名/默认 1/范围 1-4 与 26.2
`defineInRange` 逐字对齐）+ `ScopePipRenderState.sceneTargetGeneration()`（**比较代数不比较引用**，防
窗口缩放后复用陈旧画面）+ `ScopePipRerender` 的闸门顺序（闸门全过 → 判 `interval>1 && 距上次真渲<N &&
代数未变 && 上次抓帧仍在` → 复用；真渲成功记帧号+代数；任何闸门失败清 `sceneCaptured`）。

我们的现状（`grep` 实测）：`ScopePipRerender` 210 行里**没有** interval；`RenderConfig` 里没有
`SCOPE_PIP_RERENDER_INTERVAL`；`ScopePipRenderState`/`ScopePipRerender` 两边都**没有**任何 `generation`
概念（`grep -n generation` 0 命中）。我们的 B1 走「拷主目标」（`ScopePipRerender` 类注释），
`resolutionScale()` 只读不生效；默认路径是 `ScopePipRenderState` 的屏幕空间重投影 ⇒ 代数守卫要挂在哪
得先选：
若仍走"拷主目标"，代数其实等价于主目标尺寸变化 ⇒ 得自建一个"capture generation"。

**建议顺序**：A（状态正确性）→ 实机 PASS → 再上 C；C 默认 1 时行为与现在逐比特一致，属零风险加键，
但在 A 之前上 C 等于给一条已知有状态问题的路径加"每 N 帧才走一次"的变体，回归归因会变复杂。
另外：这键只对 `SCOPE_PIP_RERENDER=true` 有意义，而那个开关在我们这边是 **DECLINED 默认关**（账本 L-5），
所以 C 的取舍本质是"要不要给这条实验路径配性能旋钮" ⇒ 这是**你的决定**，我只把成本和顺序写清。

---

## 4. D：不要回搬的部分（省时间）

`99ccb8c8` 加的 `ScopePipRenderState`(922) / `ScopePipRerender`(235) / `ScopePipDepthDebug`(237) /
`scope_pip.fsh` / `scope_pip_debug.*`、`297f127a` 的接线（`CameraSetupEvent` FOV 让位、
`GameRendererMixin`、`IrisFinalScopeOverlayMixin`）、`0a77ef52` 的 bare-rim 延迟、`8ea41c2d` 的
`DepthHandle` 只读快照、`bf42f3a3` 的 12 键配置面与 lang —— **全部是我们树上的东西**（他们 commit
标题自己写了 "from 1.21.1x line (1211)"）。两边逐文件对比后唯一实质差异仍是纪元适配那一层
（`DefaultVertexFormat.ENTITY`↔`NEW_ENTITY`、`Lightmap` 内联打包、frame-graph 的
`DepthStencilState/ColorTargetState`），没有"他们想到了我们没想到"的内容 —— **A/B/C 之外没有可搬项**。

---

## 5. E：顺手提醒他们的一条（与我们相反的选择）

`3e4eeb16`（同一批里）把 `MeshGpuUnderShaders` / `MeshGpuWorldUnderShaders` **默认改回 ON（R3）**。
我们这边这条做过 B 测：光影下默认开会让高模枪在 collector 与 GPU 之间来回切、并把"继承天体自发光"
那类问题带到默认路径 ⇒ 我们退回 false（`9c29572`，账本 L-6/复核篇 §8）。他们现在开回去，等于把
我们刚交付的一条"默认值不要照抄"（指导 §1.4）又翻过来。**这不是我们要改的**，但值得回一句：
如果他们是因为"退回 ON 后维护者实测更好"才改的，请把实测数据给我们，我们重新评估我们这边的默认值。

---

## 6. 请回他们的三件事

1. A 的实测细节：他们 `0bf4c482` 之后，"镜外实体/太阳/雾"是否逐项恢复？有没有出现**重影/半透明加倍**
   （这决定我们加第 1 步时是否还要额外处理提交节点）；
2. B 的验收矩阵第 2/3/5 条结论（镜内文字裁剪、掩码回退、多页字体/资源包字体）—— 他们跑过我们就能
   把 §2 的风险项直接标成"兄弟分支已验"；
3. `rendertype_text.fsh` 在我们这一代是否被光影包重写过（他们那边有没有遇到 `#moj_import` 冲突），
   以及 `RenderPipelines.TEXT` 是否有他们知道的坑（例如 `seeThrough`/背景参数在 26.1.2 与 1.21.11 的差别）。

## 7. 本文没做的事（明确边界）

- 本轮**没有**实现 A/B/C 中任何一项，只做了：撤回被证伪的裁剪论断（代码注释 + 两份文档）、把三条
  可加项的成本/顺序/验收写成可执行方案、以及给判定 A 挂 javap 探针（TEMP 块在 `build.gradle`，
  下一轮删）。原因：A 的方向取决于 1.21.11 的 `LevelRenderer` 结构，B 取决于 5 个字体 API 的同形性，
  两者都要 javap 事实而不是推理 —— 我们沙箱里没有 jar，只能走 CI 通道。
- 探针结论回来后：A 至少落"清提交节点"那一步；B 若 5 个 API 全部同形则整套落，否则落"只加旗 +
  回退"的一半并把掩码路径标为受阻。
