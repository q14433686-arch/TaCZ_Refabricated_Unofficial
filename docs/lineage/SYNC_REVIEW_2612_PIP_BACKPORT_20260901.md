# 26.1.2 这轮 PIP 回移植：哪些能加进 1.21.11、怎么加（评估 + 落地顺序）

日期 2026-09-01。对象是他们 `arena/01a05170` 的 `0a77ef52`…`8aca7374`（tip `8aca7374`，ci-log `4b0f3cc2`
success），以及他们自写的取证文档 `docs/SCOPE_PIP_STATE_REEXTRACT_AND_MASKED_TEXT_20260901.md`
（196 行，本文引用时称「他们的回归文档」）。

**先说结论**：这轮里**值得搬的只有两条半**，其余大部分是**从我们这边搬走的东西**（回搬等于空转）。

| 项 | 他们那轮的提交 | 我们能不能加 | 结论 |
|---|---|---|---|
| A. PIP 窄遍之后的**逐帧状态重提取 + 清提交节点** | `0bf4c482`（+新 `LevelRendererAccessor` mixin） | **不加**（javap 已判：本世代 `renderLevel` 自带提取、`renderAllFeatures` 自带 `clear()`），但结论与依据已写进我们的类注释 | 见 §1 |
| B. 镜内文字**掩码裁剪** | `e1c550ee`（`ScopeTextSubmitter` + `scope_text_final.fsh` + `ScopeRenderTypes.maskedText` + `clipToScopeMask` 旗） | **能加，但必须改写**：本世代 `GlyphVisitor` 不是他们那个带坐标的 `accept`（§2.1 有实测签名表）；这是"文字溢出圆孔"的唯一正解 | 见 §2 |
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

### 1.2 探针 v3 的答案（2026-09-01，来自 CI 编译类路径上的 `minecraft-merged-…1.21.11…jar`，javap）

- `LevelRenderer` **没有** `extractLevel`：只有一条 `public void renderLevel(GraphicsResourceAllocator, DeltaTracker, boolean, Camera, Matrix4f, Matrix4f, Matrix4f, GpuBufferSlice, Vector4f, boolean)`，
  提取全是**私有且以状态为入参**的 `extractVisibleEntities(Camera, float, LevelRenderState)` /
  `extractVisibleBlockEntities(...)` / `extractBlockOutline(...)` / `extractBlockDestroyAnimation(...)`；
- 状态袋确实存在（`private final state.LevelRenderState levelRenderState`），但它在每次 `renderLevel` 内部被填满；
- `GameRenderer` 侧只有 `public void renderLevel(DeltaTracker)` + `private void extractCamera(float)`，
  **没有** 26.1.2 那个独立的 `GameRenderer#extract` 阶段；
- `SubmitNodeStorage` 同时有 `public void clear()` 与 `public void endFrame()`；而**我们自己的**
  `mixin/client/FeatureRenderDispatcherMixin` 类注释里已有一条字节码记录：
  「`renderAllFeatures()` 自身以 `submitNodeStorage.clear()` 收尾」。

⇒ **判定：A 不加。** 他们的第 2 步（重提取）在我们世代结构上不成立 —— 没有"主遍之前一次性填好、消费即清空"
的相位；他们的第 1 步（清提交节点）对我们是空操作 —— 窄遍自己那次 `renderAllFeatures()` 已经 clear 过。
这同时也解释了"为什么 1.21.11 侧维护者从没见到镜外实体消失"：**不是运气，是世代差异**。

### 1.3 落地（本轮已做，只改注释、零行为变化）

`ScopePipRerender` 类注释里那句「26.2 为此默认关闭本开关；本移植同样默认关闭，**并把提交节点保留等防护留给
后续阶段**」已改写为：把上面这组 javap 事实与"不加也不欠"的结论写进去（含 `extractVisibleEntities` 为私有入参、
`FeatureRenderDispatcherMixin` 的 `clear()` 收尾两条依据）。这条欠账由此**结案**，不再留给后人。

⚠ 仍然留着的、**只能实机**的一条：`ScopePipRerender=true` 时镜外实体/太阳/雾/天气/粒子与关掉时是否逐项一致、
有无重影/半透明加倍。上面只否证了"空袋"这一种机制，**没有**证明双遍 `renderLevel` 在我们世代完全无害
（区块编译队列、`extractCamera` 的相位、frame-graph 的资源句柄都还是各跑两遍）⇒ 归入镜内文字那篇 §4 的
剧本 E（PIP 开关开/关对比），不因为"结构上不同"而免测。

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

### 2.1 探针 v3 已经改写了 B 的做法（**他们的 visitor 签名在我们世代不存在**）

v3 顺手把字体侧也 dump 了，结果对 B 是好消息加一个坑：

| 成员 | 26.1.2 用法（他们文档原文） | 1.21.11 实测 | 对移植的影响 |
|---|---|---|---|
| `Font#prepareText` | `prepareText(seq, x, y, color, shadow, false, 0)` | `public Font$PreparedText prepareText(FormattedCharSequence, float, float, int, boolean, boolean, int)` | **同形**，可直接调 |
| `Font$PreparedText` | `visit(...)` | `public interface`，含 `void visit(Font$GlyphVisitor)` + `ScreenRectangle bounds()` | 同 |
| `Font$GlyphVisitor` | 覆写 `accept(TextRenderable, int, int, float)`（带 x/y/width） | 是**两个方法**：`default void acceptGlyph(TextRenderable$Styled)` + `default void acceptEffect(TextRenderable)`，另有静态 `forMultiBufferSource(MultiBufferSource, Matrix4f, DisplayMode, int)` | 他们那个匿名类**编译不过**；我们改成在两个 accept 里收集 renderable |
| `TextRenderable` | 只当"要画的东西" | `render(Matrix4f, VertexConsumer, int, boolean)` + `textureView()` + `guiPipeline()` + `left()/top()/right()/bottom()` | **更简单**：位置由 renderable 自己带，不必自己算 x/y/w；`textureView()` 天然就是"图集页"分组键 |
| 管线常量 | `com.mojang.blaze3d.pipeline.RenderPipelines.TEXT` | 该 FQN 下**类不存在**；我们树上用的是 `net.minecraft.client.renderer.RenderPipelines`（`ScopeRenderTypes` 的 import） | 移植时按我们的 FQN 与 `RenderSetup.builder(...)` 写法来，**别照抄他们的 `clonePipeline`** |
| 着色器定义方式 | 有 `rendertype_text.json`（core shader 由 JSON 声明 sampler/vertex format） | jar 里 `shaders/core/` 只有 `.fsh`/`.vsh`，**没有 `rendertype_text.json`** | 管线参数（顶点格式、uniform 列表）在本世代是 Java 侧 `RenderSetup` 声明的 ⇒ fsh 直接克隆 `rendertype_text.fsh` 即可 |

`rendertype_text.fsh`（1.21.11 原文，探针从 jar 里打出，克隆底版就照这份，别再猜）：

```glsl
#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
```

⇒ 我们的 `scope_text_final.fsh` = 这一份 + `tacz_ScopeMaskMode` 开关 + 孔径深度比较（`discard` 语义与
`scope_reticle_final.fsh` 里那段"绕过目标身份守卫、读私有深度拷贝"的写法**同构**，那条我们已经验过一个世代）。
`.vsh` 用 vanilla `rendertype_text.vsh`（含 `Sampler2` 光照纹理那行，别漏 —— 我们的 `scope_*` 着色器不写 UV2，
但文字走 `DefaultVertexFormat` 的那条要带）。

**还缺的事实**（探针 v4 在问，本轮已把 TEMP 块改成 v4）：本世代 `RenderPipelines` 里 TEXT 那族的常量名、
`RenderSetup`/`RenderPipeline` 能不能挂额外 `Sampler2D`（`tacz_ScopeWorldDepth` 那两条在我们 reticle 管线里
是**怎么**挂的，照着抄即可，但要先确认 API 形状）、`AbstractTexture`/`TextureManager` 是否支撑"壳纹理"绑定
（若 1.21.11 的 `RenderType` 已改为直接吃 `GpuTextureView`，那连壳纹理都不需要 —— 可能比他们更省事）。

---

## 3. C：`ScopePipRerenderInterval`（隔帧渲染）—— 能加，但要排队

他们从 26.2 搬来的三件套：`RenderConfig.SCOPE_PIP_RERENDER_INTERVAL`（键名/默认 1/范围 1-4 与 26.2
`defineInRange` 逐字对齐）+ `ScopePipRenderState.sceneTargetGeneration()`（**比较代数不比较引用**，防
窗口缩放后复用陈旧画面）+ `ScopePipRerender` 的闸门顺序（闸门全过 → 判 `interval>1 && 距上次真渲<N &&
代数未变 && 上次抓帧仍在` → 复用；真渲成功记帧号+代数；任何闸门失败清 `sceneCaptured`）。

配置面实测对比（两边 `RenderConfig` 的 `SCOPE_PIP_*` 字段集合）：他们 11 个、我们 10 个，
**唯一差异就是 `SCOPE_PIP_RERENDER_INTERVAL`**（我们的 20 个 `scope_pip_*` lang 键 = 10 键×2，也不缺别的）
⇒ 加 C 不需要动 lang 之外的任何配置面设施。
`ScopePipRerender` 210 行里**没有** interval；`ScopePipRenderState`/`ScopePipRerender` 两边都**没有**任何 `generation`
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
`DepthHandle` 只读快照、`bf42f3a3` 的配置面与 lang（我们只缺 interval 一个键，见 §3） —— **全部是我们树上的东西**（他们 commit
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

- 本轮**没有**实现 B/C（A 的结论已经是"不加"）。做完的事：撤回被证伪的裁剪论断（代码注释 + 两份文档）；
  挂探针 v3 并把 A 判死（javap 级依据，见 §1.2）；把 v3 的结论落进 `ScopePipRerender` 类注释，让那条
  "防护留给后续阶段"的欠账结案（注释改动，零行为变化，实机结论一概不给）；把 B 的形状按 v3 结果重写
  （§2.1）；把 v3 的 TEMP 块升级为 v4 继续问 B 剩下的三件事。
- 沙箱里没有 JDK、没有 MC jar，所有世代事实只能走 CI 通道 ⇒ 探针块的存在期就是"下一轮"，结论到手即删。
