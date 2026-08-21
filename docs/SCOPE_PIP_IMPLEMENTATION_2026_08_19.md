# 瞄准镜镜内画中画（Scope PIP）· 实现记录

> **⚠️ 本文是 08-19 当天的阶段记录，部分结论已被后续实测推翻。**
> **当前状态与接续工作请先读 [`SCOPE_PIP_HANDOFF_2026_08_21.md`](SCOPE_PIP_HANDOFF_2026_08_21.md)。**
> 尤其：本文 §3 关于「镜内分辨率上限」的讨论只适用于重投影模式；
> 二次渲染模式已实装并可用，且当晚被当作「溢出」追查了很久的现象，
> 真因是 Sodium 的 `hasUpdatedThisFrame` 每帧单次上传闸，与溢出无关。

**日期**：2026-08-19
**分支**：`26.2(main)`
**状态**：**代码完成、`./gradlew build` 通过；第二版尚未实机验证**
**开关**：`ScopePipEnable`，**默认关闭**

> ⚠️ 按 `AGENTS.md` §2：本文区分「已实测」与「静态推导」。
> §1 的失败结论是**用户实机观察到的**；§2 之后的新方案是静态推导 + 编译通过，
> **尚未实机验证**。

---

## 0. 目标

| | FOV 整屏变焦（改动前，默认） | PIP（本次新增，默认关） |
|---|---|---|
| 镜内 | 放大 | 放大 |
| **镜外（镜筒周围的画面）** | **也跟着放大** | **保持 1×** |
| 上游 1.21.1 有没有 | 有 | 没有 |

「镜外不该跟着放大」是 FOV 方案在物理上做不到的事 —— 它只有一个摄像机 FOV。

---

## 1. 【已作废】第一版：重定向 target + 再渲染一遍世界

方案：把 `GameRenderer#mainRenderTarget()` 临时重定向到离屏 target，
用窄 FOV 再跑一次 `LevelRenderer#render`。即 `SCOPE_PIP_PLAN.md` §8.3 的方案 A。

**实机结果：失败。** 用户环境 Sodium 0.9.1 + ImmediatelyFast + Iris 1.11.2（无光影包）。

### 1.1 【已更正】首次归因是错的

失败当天我给出的根因是「Sodium 完全接管地形渲染、不走 `mainRenderTarget()`，
所以重定向对它无效」。**那是错的。** 事后逐条反编译 Sodium 0.9.1 查清了真正的机理：

| 症状 | **真正的**根因 |
|---|---|
| 镜内「部分物件放大、大部分方块没放大，画面糊在一起」 | **投影矩阵，不是渲染目标。** Sodium 的地形输出<b>确实</b>调用 `GameRenderer#mainRenderTarget()`（`TerrainRenderPass` 字节码实读），重定向对它是有效的。但它的投影用的是自己快照的那一份 —— `GameRendererStorage.sodium$getProjectionMatrix()`，由 Sodium 的 `GameRendererMixin` 用 `WrapOperation` 包住 `renderLevel` 里的 `ProjectionMatrixBuffer#getBuffer(Matrix4f)` 抓取。第一版只改了 `RenderSystem.setProjectionMatrix`，Sodium 压根不看那个 ⇒ 地形用宽 FOV、原版路径的实体用窄 FOV，两套比例叠在一起 |
| **镜外**的实体与部分物件整个消失 | 一帧内两次驱动 `LevelRenderer#render`，打乱了 Sodium 与 ImmediatelyFast 的逐帧状态。**这一条至今未查清** |

### 1.2 该记住的教训（两条）

1. `SCOPE_PIP_PLAN.md` §8.3 那套推导逐条核对的是**原版**字节码，在原版渲染下成立。
   错在没有把「玩家实际会装 Sodium」当作前提去检验。本仓库的目标受众几乎必然使用
   Sodium，任何依赖「所有绘制都经过某个原版方法」的方案都要先问：**Sodium 还走那条路吗？**
2. 更要紧的一条：**症状对上了不等于归因对了。** 「地形没放大」既可以由
   「渲染目标没跟随」解释，也可以由「投影矩阵没跟随」解释，两者在画面上完全一样。
   当时选了前者且没有验证，直接据此推翻了整条技术路线。
   正确的做法是**先读 Sodium 的字节码再下结论** —— 那只花了十几分钟。

### 1.3 这条路还有没有救

有，但没走通：
- 投影矩阵问题**可解** —— Sodium 的快照挂在 `GameRenderer` 实例上，可以同步改写；
- 逐帧状态问题**未解**（§1.1 第二行），且需要在体反复试错。

所以第一版代码整体删除；若将来要追求高倍清晰度，必须从这两条重新开始。

---

## 2. 第二版（当前）：屏幕空间重投影

### 2.1 核心恒等式

透视投影下，「把 FOV 压窄 Z 倍」在屏幕空间**恒等于**「绕光轴把画面放大 Z 倍」：

```
窄FOV下某点的 NDC = 宽FOV下同一点的 NDC × Z          （Z = 倍率）
  ⇒ 反过来采样： wideUV = center + (narrowUV − center) / Z
```

推导：视线偏离光轴 θ 的点，宽 FOV 下 NDC ∝ `tan θ / tan(fov_w/2)`，
窄 FOV 下 NDC ∝ `tan θ / tan(fov_n/2)`，比值 = `tan(fov_w/2)/tan(fov_n/2)` = Z
（这正是 `MathUtil.magnificationToFov` 的定义）。

**这不是近似。** 相机位置与朝向都没变，只有 FOV 变了，所以「重渲一遍」与
「重采样一遍」逐像素等价，差别只在分辨率。

于是根本不需要再渲染世界：把已经画好的世界拷出来、按上式重采样即可。
`|uv − center| ≤ 0.5` 且 `Z ≥ 1`，采样点必然落在 `[0,1]` 内，连边界钳制都不需要。

### 2.2 每帧流程

```
GameRenderer#renderLevel
 ├─ levelRenderer.render(...)               ← vanilla（或 Sodium）照常画，FOV 未被瞄具修改
 │
 ├─【注入 AFTER】ScopePipRenderer.captureScene
 │     └─ copyTextureToTexture(主target色纹理 → 离屏拷贝)     ← 唯一的额外 GPU 工作
 │
 ├─ 清主 target 深度
 └─ renderItemInHand
      └─ renderAllFeatures
           ├─【阶段边界】ScopeMaskRenderer.renderAtPhaseBoundary()   目镜掩码
           ├─【阶段边界】ScopePipRenderer.compositeAtPhaseBoundary()
           │      全屏三角形：掩码内的像素 ← 拷贝在 center+(uv-center)/Z 处的颜色
           └─ executeSolid / executeTranslucent …
                 镜身 → 掩码内 discard（PIP 画面留住）
                 准星 → 反向裁剪只画掩码内（浮在 PIP 画面之上）
```

### 2.3 拷贝时机是一个很窄的窗口

必须夹在 `LevelRenderer#render` **之后**、`renderItemInHand` **之前**：

- 再早 → 世界还没画完；
- 再晚 → 拷贝里混进枪和手，镜片里会出现一把缩小的枪。

renderLevel 偏移 405 之后、502 之前正是这个唯一窗口。
该位置不在任何 render pass 内（vanilla 紧接着就调 `clearDepthTexture`，
那个方法有同样的约束，等于替我们证明了）。

### 2.4 为什么要拷贝，不能直接采样主 target

合成那一趟是往主 target **写**。若同时又从主 target **读**，
就是在同一个 render pass 里对同一张纹理又读又写 —— 未定义行为。
拷贝那一步存在的全部意义就在这里。

一次全屏 `copyTextureToTexture` 的开销与「多渲一遍世界」不在一个量级。
两张纹理都由 `RenderTarget#createBuffers` 建，usage 位是
`bipush 15` = `COPY_DST|COPY_SRC|TEXTURE_BINDING|RENDER_ATTACHMENT`（字节码实读），
拷贝需要的两个位都在。目标纹理的格式取自源纹理本身，保证两端一致。

### 2.5 倍率怎么送进着色器

借 `DynamicTransforms` 的 `ColorModulator.r`。`bindDefaultUniforms` 只管
Projection / Fog / Globals / Lighting 四个块，`DynamicTransforms` 得自己
`setUniform` —— 与 `ScopeMaskRenderer` 同一套路。

管线在 `POST_PROCESSING_SNIPPET`（= public 的 `builder(GLOBALS_SNIPPET)` +
TRIANGLES，偏移 1120-1142 实读）之上加三个 layout：
`IN_SAMPLER` + 自建掩码采样器 + `DYNAMIC_TRANSFORMS`。

### 2.6 孔径判定与 `scope_body.fsh` 共用同一份逻辑

`assets/tacz/shaders/core/scope_pip.fsh` 里的 `insideOcular` 判定
（含按开镜进度沿掩码边缘向内收缩那一段）是从 `scope_body.fsh` **原样搬来的**。
一个在孔径内 `discard` 让路、一个在孔径内落笔，判定只要错开一点点，
边缘就会出现「既没被镜身画、也没被 PIP 贴」的裂缝。

**改其中一个文件时必须同时改另一个。** 两处注释都写了这句。

---

## 3. 代价：高倍镜下镜内会变软

### 3.1 放大倍数就是瞄具倍率（初稿把这条算错了，已更正）

初稿写的是「设镜片直径约占屏高的 0.4，实际放大 ≈ `0.4 × Z`」。**那是错的**，
它把「镜片在屏幕上多大」与「镜片里的内容被放大了多少」混为一谈。

正确的推导：合成是对**整屏**做重投影，镜片只是这张重投影图上的一个**窗口**。
屏幕上直径 D 的镜片，其像素映射回原画面只覆盖 `D / Z` 个像素，
却要铺满 D 个像素 ⇒ **放大倍数恒等于 Z**，与镜片大小无关。

| 瞄具 | 倍率 | 镜内实际放大 |
|---|---|---|
| ACOG TA31 | 2.5× | 2.5× |
| ELCAN 4× | 4.25× | 4.25× |
| 1873 6× | 6× | 6× |
| 标准 8× | 8× | 8× |

也就是说软化比初稿说的严重得多，**没有哪一档是「基本等同原生」的**。

### 3.2 能做的两件事（已实现）

真实细节找不回来 —— 信息量本来就不在主画面里。但重建质量和主观锐度都还有空间：

1. **双三次重建（Catmull-Rom）取代硬件双线性。**
   双线性在放大时就是线性插值，这种倍数下必然糊成一片；
   Catmull-Rom 是插值型三次样条，放大时明显更锐利。
   用 Matt Pettineo 那套「中间抽头合并成硬件双线性抽头」的写法，
   4×4=16 次点采样降到 9 次 —— 前提是采样器必须是 `LINEAR`（合成阶段就是这么绑的）。
   代价是高对比边缘会轻微过冲，结果需 `max(0.0)` 夹一下。
2. **按倍率加权的钝化蒙版锐化。**
   强度从 1× 的 0 线性升到 6× 的满值（`ScopePipSharpness` 控制上限，默认 0.5）。
   低倍镜不会被过度处理，高倍镜也不会锐化不足。
   抽头取在**源图**的相邻像素上（先锐化再放大）：高频细节只存在于源图的采样率上，
   在放大后的坐标系里做邻域只会放大插值伪细节，而且每个抽头都要再跑一遍
   Catmull-Rom，不划算。

只有镜内像素会付这些采样开销 —— 镜外在更早的 `discard` 就退出了。

### 3.3 真正的高倍清晰度需要什么

只有回到「用窄 FOV 把世界再渲一遍」，那样镜内像素是原生渲染出来的。
而那条路必须先解决 §1 的 Sodium 兼容问题，目前无解。

---

## 4. 判定链：谁能走 PIP

`ScopePipRenderer.isEnabledForHeldGun()` 全部满足才走：

| 条件 | 不满足时 |
|---|---|
| `ScopePipEnable` = true | 整屏变焦（改动前行为） |
| `ScopeMaskEnable` = true | 同上（没有掩码就没有孔径，PIP 无处落地） |
| 无光影包 | 同上 |
| 第一人称、在世界里 | 同上 |
| 装了**瞄具配件**且 `zoom > 1` | 同上 |

再加一条**通道级**判据：`ScopeMaskRenderer.hasMaskThisFrame()`。

它替代了「在这里重算一遍 sight / scope / 组合镜的分档」——
那套状态机（`BedrockAttachmentModel#activeGroupIsScope`）牵扯 `views[]` 索引与
目镜节点命名，复制一份必然走样。直接读模型自己的结论：
**掩码没产出 = 这条通道不该走 PIP**，于是红点/全息、组合镜低倍档
继续用整屏变焦，不会变成「既没 PIP 也没放大」。

代价是一帧延迟（FOV 事件在 `extract` 阶段、掩码画在同帧稍后）。
唯一读到旧值的时刻是刚抬镜那一帧，此时 `aimingProgress ≈ 0.02`，
变焦公式 `1 + (zoom-1)·progress` 几乎等于原始 FOV，再过二阶平滑，推导上不可见。

> 该标志在 `renderItemInHand` 的 HEAD 清零（`setInHandPass(true)` 里），
> 不是在阶段边界清 —— 因为 `renderItemInHand` 有若干提前 return
> （F1 隐藏 HUD、旁观者、第三人称、睡觉），走那些分支时根本到不了阶段边界，
> 标志会粘住上一帧的值。

---

## 5. 失败姿态

任何一环抛异常 → `failed = true` → 本特性永久停用（当次会话），并打一条 error 日志。
此后 `applyScopeMagnification` 的整屏变焦在**下一帧**自动接管 ——
它每帧重新问一次 `suppressesWorldFovZoom()`，不缓存。

合成管线**懒加载**：本类的静态初始化会被每帧的 FOV 事件触发（不看 PIP 开没开），
若管线在 `<clinit>` 里构建，一旦构建抛异常就是 `ExceptionInInitializerError`，
连关着 PIP 的玩家都会被带崩。

---

## 6. 配置项

| 键 | 默认 | 说明 |
|---|---|---|
| `ScopePipEnable` | `false` | 总开关 |
| `ScopePipMinAimingProgress` | `0.05` | 低于该开镜进度不做 PIP |
| `ScopePipSharpness` | `0.5` | 镜内锐化上限（0 = 关），实际强度按倍率加权 |
| `ScopePipAllowShaderPacks` | `false` | 允许光影包启用时也跑 PIP，见 §7.3 |

第一版的 `ScopePipResolutionScale` / `ScopePipUpdateInterval` **已删除**：
前者只会让镜内更糊（拷贝必须满分辨率），后者会让镜内画面滞后，
而拷贝本身已经足够便宜，两个都没有存在理由。

---

## 7. 已知缺口

1. **高倍镜镜内变软**（§3），方案内不可消除。
2. **镜内看不到发光实体的描边**：`levelRenderer.doEntityOutline()` 在
   `renderLevel` 返回**之后**才把描边贴到主画面（GameRenderer 偏移 275），
   而拷贝发生在那之前。观感差异，不影响正确性。
3. **光影包下默认关闭，但可由 `ScopePipAllowShaderPacks` 打开。**

   注意这里**不**沿用 `IrisCompat.shouldDisableScopeMaskUnderShaderPack()` 的结论 ——
   那个方法对 Iris 返回 `false`（本仓库专门做了 `assignPipeline` → Iris HAND program
   的兼容层，目镜掩码在光影下是支持的）。PIP 比掩码多两件**未验证**的事：

   1. 抓取时机在 `LevelRenderer#render` 之后，而延迟管线的 composite 可能还没跑完，
      拷到的也许是未着色的中间结果；
   2. 合成写的是裸颜色，而光影通常在 tonemap 之前工作于线性/HDR 空间，
      镜内可能偏灰或过曝。

   两者都只是**观感**风险 —— 重投影不重新驱动世界渲染，不可能像第一版那样
   打乱别的 mod 的逐帧状态。所以「打开试一眼」是安全的，
   默认关是保守，不是「已知不兼容」。若实测证实可用，就把默认翻过来；
   若确实偏色，那说明要挂到 Iris 的 composite 阶段之后再抓，届时再单独处理。
4. `ScopePipTarget` 没有接到任何生命周期回调（与 `ScopeMaskTarget` 一致），
   只在尺寸/格式变化时销毁重建。

---

## 8. 第二版首次实机需要确认的项

1. **不崩**，且日志无 `[TACZ Scope] Scope PIP ... failed`。
2. **镜外保持 1×** —— 本次改动的全部意义。
3. **镜内是放大的，且地形/实体/一切都一起放大**（第一版正是这里失败的）。
4. **镜内画面与镜外对齐**：孔径中心指向的物体应与不开镜时准星指向的一致。
5. **镜内看不到枪**（拷贝时机正确的直接检验）。
6. **准星仍在最上层**。
7. **回归**：红点/全息、组合镜低倍档表现完全不变（走的还是整屏变焦老路）。
8. **回归**：`ScopePipEnable=false` 时一切与改动前一致。
9. 高倍镜（6×/8×）镜内的软化程度是否可接受 —— 这是要你拍板的观感问题。

---

## 9. 涉及文件

| 文件 | 改动 |
|---|---|
| `client/render/scope/ScopePipRenderer.java` | 判定、抓取场景拷贝、重投影合成 |
| `client/render/scope/ScopePipTarget.java` | 场景拷贝纹理的生命周期 |
| `assets/tacz/shaders/core/scope_pip.fsh` | 合成片元着色器（重投影 + 掩码 discard） |
| `client/render/scope/ScopeMaskRenderer.java` | 新增 `hasMaskThisFrame()` 及其置位/清零 |
| `mixin/client/GameRendererMixin.java` | `renderLevel` 里注入场景拷贝 |
| `mixin/client/FeatureRenderDispatcherMixin.java` | 阶段边界追加合成调用 |
| `client/event/CameraSetupEvent.java` | PIP 生效时整屏变焦让位 |
| `config/client/RenderConfig.java` | 2 个配置项（顺带清掉第 18 轮遗留的悬空注释与无主 `builder.comment`） |
