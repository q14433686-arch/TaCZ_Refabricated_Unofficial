# Scope PIP 实机回归修复：逐帧状态重提取 + 镜内文字掩码裁剪 — 2026-09-01

依据：维护者实机反馈（2026-09-01）两条裁定：
1. 「你移植 PIP 时出现了错误，镜外实体、太阳、雾等均不渲染，和你同世代的 1.21.11 没有这些 BUG」。
2. 「镜内文字出现的 BUG 和 26.2 近期修的一模一样，自己想为什么」（= 26.2 `9d036594` 修的
   「文字溢出目镜圆孔/未裁剪」）。

**运行期行为全部未验证；编译验证走 CI。**

---

## 0. Bug 1：PIP 二次渲染导致镜外实体/太阳/雾全灭

### 根因（全部 merged jar 字节码定位）

26.1.2 的渲染主流程把「逐帧状态提取」与「渲染」拆成两段：

```
Minecraft.renderFrame
  → GameRenderer.extract(DeltaTracker, boolean)            ← 每帧只跑一次
      @103 → LevelRenderer.extractLevel(DeltaTracker, Camera, float)   [public]
              → extractVisibleEntities / extractVisibleBlockEntities /
                extractBlockOutline / extractBlockDestroyAnimation /
                WeatherEffectRenderer.extractRenderState（雾/雨）/
                SkyRenderer.extractRenderState（太阳/天空）/
                WorldBorderRenderer.extract / prepareChunkRenders（区块准备）
              → 全部写进共享状态袋 LevelRenderState
  → GameRenderer.render(...) → GameRenderer.renderLevel(DeltaTracker)
      @412 → LevelRenderer.renderLevel(9 参)               ← 消费 LevelRenderState
          尾部 @560 → LevelRenderState.reset()             ← 清空整袋
```

`LevelRenderState` 只有 `<init>` + `reset()` 两个方法、一袋字段
（entityRenderStates/blockEntityRenderStates/skyRenderState/weatherRenderState/
particlesRenderState/blockOutlineRenderState/cloudColor/chunkSectionsToRender/
cameraRenderState(含 fogData)/…）——**它是一次性燃料**：每帧提取一次，被
`renderLevel` 消费一次就 `reset()` 清空。

PIP 的窄 FOV 二次渲染在 vanilla 那遍**之前**又调了一次 `renderLevel`：

- 窄遍消费并**清空**了共享状态；
- vanilla 主遍随后拿到**空状态** → 实体/太阳(skyRenderState)/雾与天气
  (weatherRenderState)/粒子/方块高亮全部消失 —— 与实机症状逐项吻合；
- 1.21.11 的 `renderLevel` 把相机/投影/裁剪全部走参数显式传入、没有共享状态袋，
  每次调用自洽 —— 所以「同世代的 1.21.11 没有这些 BUG」。

### 修法（`ScopePipRerender.renderScopeView`）

窄遍 `renderLevel` 返回、主目标拷走之后，在返回前补两步：

1. **清提交节点**：`((LevelRendererAccessor) levelRenderer).tacz$getSubmitNodeStorage().clear()`
   —— 主遍内的 `renderSolidFeatures` 已消费过 solid 节点，但窄遍未消费的残件若不清理，
   会在 vanilla 遍唯一的 `renderAllFeatures` flush（`GameRenderer.renderLevel` @570，
   `clearSubmitNodes` 在其后）处叠加成重影/半透明加倍。防御性清理，成本≈0。
2. **重跑提取**：`levelRenderer.extractLevel(deltaTracker, mc.gameRenderer.getMainCamera(), partialTicks)`
   —— `extractLevel` 是 **public**（字段访问位 0x0001，字节码核实），vanilla 的调用
   （`GameRenderer.extract` @103）与此处同源参数；其内部重建 `ChunkSectionsToRender`
   并写回 `levelRenderState`，`GameRenderer.renderLevel` 正是从该字段读取
   传给本次 `renderLevel` 的实参（@409→@412）。重提取后 vanilla 遍拿到的是
   **本帧新鲜状态**，与 1.21.11 的「每次调用自洽」语义对齐。

新 mixin：`client.LevelRendererAccessor`（`@Accessor("submitNodeStorage")`）——
字段 private，`SubmitNodeStorage.clear()` 本身是 public（字节码核实）。

26.2 记录过的「镜外实体偶发消失」（其类注释第三条）属同一架构病灶的另一表现；
26.2 的重定向变体没有做重提取 —— 那部分在 26.2 侧仍是开放问题，本分支已给出答案。

---

## 1. Bug 2：镜内文字溢出目镜（= 26.2 `9d036594` 的病）

### 根因（自行推理 + 26.2 提交对读）

镜内文本的「存在性」此前已修（c290a1f：attachment 快照重放从不 flush
functionalTasks → 延迟入队/立即提交）。**用户裁定现病与 26.2 `9d036594` 修前一致** =
文字能显示但不被裁进目镜圆孔。我方此前「镜筒深度剔除 ≈ 掩码」的论断**被实机证伪**：
`submitText` 下游是 vanilla 字体管线（`TextFeatureRenderer` → `GlyphRenderTypes`
三件套写死 RenderType），**不吃 scope body 的深度** —— 文字几何按自己的深度
走 vanilla 深度测试，溢出圆孔的像素照画。

### 修法（26.2 `9d036594` + `c4eb4e2` 的 26.1.2 语义移植）

26.2 的机制：`Font#prepareText → PreparedText#visit(GlyphVisitor)` 徒手拿到字形
renderable，按图集页分组，经壳 AbstractTexture（PageHandle）绑定页纹理，
塞进自带的掩码裁剪 RenderType（`ScopeTextRenderTypes.clippedText`）；掩码不可用
回退 vanilla `submitText`，不丢字。

26.1.2 的 API 全部就位（字节码核实）：

| 26.2 | 26.1.2 |
|---|---|
| `GlyphVisitor.acceptRenderable(TextRenderable)` | `acceptGlyph(TextRenderable$Styled)` + `acceptEffect(TextRenderable)`（`Styled implements TextRenderable, ActiveArea`，同为 renderable） |
| `PreparedText.visit` / `TextRenderable.render(Matrix4fc, VertexConsumer, I, Z)` | 同形（`BakedSheetGlyph.render` = addVertex/setColor/setUv/setLight × 4 顶点） |
| `Font.prepareText(seq, x, y, color, shadow, false, 0)` | 同形 7 参 `(FormattedCharSequence;FFIZZI)` |
| `RenderPipelines.WORLD_TEXT_SNIPPET` 配方 | `clonePipeline(RenderPipelines.TEXT)`（既有工具；TEXT = `RenderTypes.text` 的管线） |
| 掩码 = 纹理掩码（ScopeMaskTextureHandle 壳） | 掩码 = `ScopeDepthCopyState` 双深度拷贝（世界备份 + 孔径拷贝），`DepthCopyRenderType(Operation.MASK)` 在绘制边界现场绑定 —— 与蚀刻准星同构 |
| 门禁 = 总开关/光影禁用/掩码 target 同步 | 门禁 = `ScopeDepthCopyState.isMaskCycleValid()`（本帧是否走完「备份+孔径拷贝」周期） |
| `renderable.textureView()` 按页分组 | 同形；页经 `PageHandle`（AbstractTexture 空壳 + `getRepeat(FilterMode.NEAREST)`）注册壳 Identifier，每帧刷新指向 |
| fsh = text 克隆 + SCOPE_MASK 分支 | `scope_text_final.fsh` = 26.1.2 `rendertype_text.fsh` 逐行克隆 + `tacz_ScopeMaskMode` 掩码分支 + `tacz_ScopeFinalOverlay`（post-composite 路径走私有深度拷贝并绕过目标身份守卫，与 `scope_reticle_final.fsh` 同款；雾刻意省略 —— 冻结手部变换下雾 uniforms 已失效） |

新部件：

- `client/render/scope/ScopeTextSubmitter.java` —— 26.2 同名部件的移植（见上表映射）。
- `ScopeRenderTypes.maskedText(Identifier pageId)` —— 掩码文字 RenderType（管线
  `pipeline/scope_masked_text` = `clonePipeline(RenderPipelines.TEXT)` + fsh 换
  `core/scope_text_final` + 两个占位深度采样器；Iris 归类 `HAND_TRANSLUCENT`）。
- `TextShowRender` 加 `clipToScopeMask` 旗（26.2 同款）：瞄具 ocular 侧 true、
  枪身侧 false；任务执行时掩码管线失败即回退 vanilla `submitText`。

裁剪语义与准星同族：**镜外 discard**（只保留
`apertureDepth < worldDepth - epsilon` 的像素），文字浮在镜内画面之上、被约束在
真实目镜足迹内 —— 不依赖文字自身深度，与「深度剔除」论断无关。

---

## 2. 文档订正

- `BedrockAttachmentModel` 中「文字顶点被镜筒深度正常剔除 —— 深度孔径架构里这
  等价于 26.2 的掩码裁剪」的注释**作废重写**（该论断被实机证伪）。
- 给 1.21.11 的同步文本相应句子作废（见本次会话交付的修订版文本）。

## 3. 验收矩阵（实机前全标「未验证」）

1. `ScopePipRerender=true` + 开镜：镜外实体/太阳/雾/天气/粒子与无 PIP 时一致；
   镜内窄 FOV 画面不变；无重影/半透明加倍。
2. 镜内文字：MK5HD/scope_standard_8x 弹药计数只出现在目镜圆孔内，溢出像素消失；
   枪身文本（ak47/minigun/rpk/type_81）行为不变。
3. 掩码回退：未开镜/序列中断帧文字不消失（回退 vanilla 管线），不采样陈旧掩码。
4. 回归：PIP 全关 + 无光影时既有行为不变（masked-text 仅在 `isMaskCycleValid()`
   的帧生效，普通帧走原路径）。
5. 多页字体/资源包字体：`PageHandle` 壳按页分组、资源重载后指向刷新；
   TTF 灰度图集字体若异常走回退路径（26.2 同款可接受降级）。
