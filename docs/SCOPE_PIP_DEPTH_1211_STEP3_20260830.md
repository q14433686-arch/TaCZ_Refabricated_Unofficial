# 1.21.11 深度版镜内画中画（PIP）原型 — Step 3: 真实镜内放大画面

日期: 2026-08-30
分支: `arena/01a0518d-tacz-refabricated-unofficial` (1.21.11, depth-aperture 架构)
前置: Step 2（纯品红全屏判据）已实机 **PASS**。Step 3 把品红换成「镜片里真的放大后的世界」。

本文档是 Step 3 的源码级实现与实机确认步骤。**默认关闭**；自本版起提供游戏内配置（`RenderConfig`），
开发用的 JVM 属性仍作为覆盖通道保留。

---

## 0. 本步目标

- 在主手绘制**之前**把「已画完的世界」颜色拷进一个离屏 RGBA 纹理；
- 在主手绘制**之后**全屏合成：只用 Step 2 的孔径判据 `ad < wd - 1e-6` 落笔，
  镜片内输出 `texture(scene, center + (uv - center) / Z)`；
- **镜外世界默认全程保持 1×**（抑制旧的整屏 FOV 变焦），镜片内出现物理正确的 Z× 放大；
  游戏内 `ScopePipWorldZoomShare` 可把一部分倍率还给世界（0 = 纯 PIP，1 = 旧整屏变焦）。

与 26.2 的「屏幕空间重投影」同一条原理：
> 窄 FOV 的画面 = 宽 FOV 画面绕光轴等比例放大，hence `wideUV = center + (narrowUV-center)/Z`。
> 它是恒等式，不是近似；唯一的代价是镜内分辨率 = 屏幕分辨率 ÷ Z（此步尚未做
> Catmull-Rom 重建 / 锐化 / aim-progress 渐变）。

---

## 1. 改了哪些文件

| 文件 | 性质 |
|---|---|
| `src/main/java/.../ScopePipRenderState.java` | 新增：抓取 + 合成 + FOV 抑制查询 + 准星/遮光罩延迟判定 + 离屏颜色 target |
| `src/main/java/.../mixin/client/GameRendererMixin.java` | HEAD 抓取、RETURN 合成、RETURN 刷新延迟准星/遮光罩 |
| `src/main/java/.../client/event/CameraSetupEvent.java` | `applyScopeMagnification` 抑制整屏 FOV 变焦 |
| `src/main/java/.../client/model/BedrockAttachmentModel.java` | 本帧 PIP 合成开着时，准星/遮光罩改走后合成覆盖层 |
| `src/main/resources/assets/tacz/shaders/core/scope_pip.fsh` | 新增：重采样 + 孔径 discard |
| `build.gradle` | `runClient` 增加 `-Dtacz.scope.pip.enable` 开关 |
| `src/main/java/.../ScopePipDepthDebug.java` | Step 3 启用时让位（不覆盖真实 PIP） |
| `src/main/java/.../config/client/RenderConfig.java` | 新增 `ScopePipEnable / MinAimingProgress / MinMagnification / WorldZoomShare / Sharpness / AllowShaderPacks / DebugNoComposite / DebugPaintLens` |
| `src/main/java/.../compat/cloth/client/RenderClothConfig.java` | ModMenu/Cloth 界面接入上述配置项 |
| `src/main/resources/assets/tacz/lang/{en_us,zh_cn}.json` | 配置项界面翻译（PIP 键 + 上游全量键合并修复） |
| `src/main/java/.../mixin/client/iris/IrisFinalScopeOverlayMixin.java` | Iris 成品帧抓取 + PIP 合成 + 后合成准星刷新 |
| `src/main/java/.../ScopeDepthCopyState.java` | Iris PIP 路径强制私有世界深度拷贝 |

未改：`ScopeLateReticleState`、Iris 镜身/手部主体。
`ScopeFinalOverlayState` 仅新增两个非破坏性扩展：裸遮光罩也可排队（自动抓取手部
transform）、空准星时允许仅含遮光罩的刷新；这两种都是原逻辑的能力超集，非 PIP/Iris 时行为不变。
默认配置（PIP 关）下零影响；无光影时走 vanilla 抓取-合成；**Iris 下默认仍然跳过**，
玩家显式打开 `ScopePipAllowShaderPacks` 时才走成品帧屏幕空间合成（见 §2.6/§2.7）。

---

## 2. 实现要点

### 2.1 抓取时机 = `renderItemInHand` HEAD

`GameRenderer#renderItemInHand` HEAD 时：
- 世界已完全画进主 target（第 2 步已证该 target 可读可写）；
- 枪/手**尚未**光栅化 ⇒ 抓到的就是干净的镜内画面。
Step 2 在 RETURN 合成（孔径深度拷贝此刻已完成），抓取则必须更早 ⇒ 放在 HEAD。

### 2.2 离屏颜色 target

`SceneColorTarget`：`glGenTextures` + `glTexImage2D(GL_RGBA8)`，不建 FBO。
抓取直接走 `CommandEncoder.copyTextureToTexture(main.getColorTexture(), scene, ...)` ——
与 26.2 `ScopePipRenderer.captureScene` 同一条已被实机验证的路径。这样抓取不依赖
`renderItemInHand` HEAD 那一刻当前绑定的是哪个 FBO（用旧版 `glBlitFramebuffer` 从
“当前 draw FBO”拷，恰好会拷错/拷空，导致只有 FOV 被抑制、镜头却没贴图 —— 这就是
“里外都 1×”的根因）。用 `GlTexture`/`GlTextureView` 子类包装成可绑定的
`GpuTextureView` —— 与 Step 2 已验证的裸 GL 深度纹理绑定手法同源。

### 2.3 合成管线

复用 Step 2 已实测的 `RenderPipelines.ENTITY_OUTLINE_BLIT` 底子 + `minecraft:core/screenquad`。
新增第 3 个采样器 `tacz_SceneColorSampler`。**不声明深度**（纯屏幕空间覆盖）。
倍率用**编译期 define** `#define TACZ_PIP_ZOOM N` 通过 `withShaderDefine` 送入（1.21.11
的 `RenderPass.setUniform` 只收 `GpuBuffer`，本步刻意绕过那段 API 风险）。
同一把镜子的 zoom 是整数，管线只在 zoom 变化时重建一次。

### 2.4 FOV 替代

`ScopePipRenderState.suppressesWorldFovZoom()` 被 `CameraSetupEvent#applyScopeMagnification`
调用。开着 PIP、未用光影（Iris）、当前持有 >1× 瞄具且**本地玩家已开始抬枪
（aim progress > 0）**时，`applyScopeMagnification` 不再整屏放大，而是按配置
`ScopePipWorldZoomShare` 只把“世界应承担的那一份”喂给 FOV；默认 `share=0` 时等价于保持
基础 FOV。门还要求倍率 ≥ `ScopePipMinMagnification`（默认 4×）：低倍镜直接回到旧整屏变焦，
免得 PIP 既糊又白付全屏拷贝成本。

键点一：**不要**再以 `sceneCaptured` 为依据。`sceneCaptured` 是 `renderItemInHand` HEAD 写出的
“本帧抓图是否成功”，而 FOV 计算发生在同一帧更早/更晚的位置，这条标志在开镜/收镜过渡中
会时真时假，导致世界 POV 短暂跳变（本次实机症状 3）。

键点二：aim-start 查询必须用**和 FOV 回落分支完全相同的插值 progress**，即把帧的
`event.getPartialTick()` 透传给门，而不是固定 `partialTicks=0` 或 `partialTicks=1`。
`LocalPlayerAim#getClientAimingProgress(0)` 是上一 tick、`(1)` 是当前 tick，而
`CameraSetupEvent#applyScopeMagnification` 的回落分支用 `event.getPartialTick()` 插值：
- 进入边界，上一 tick 可能是 0、插值已 > 0 → 固定 `0` 提前放走变焦；
- 退出边界，当前 tick 已到 0、插值仍 > 0 → 固定 `1` 也提前放走一帧残余变焦。

两次实机分别踩到这两个方向后，正确做法是门本身回答“本帧是否要应用非 1× 整屏变焦”，
因此直接用同一 `partialTicks` 查询 `getClientAimingProgress`，progress > 0 即抑制；progress
为 0 时回落分支算出的倍率就是 1×、不产生跳变。

键点三：抑制期间**不能裸 `return`**，否则 `WORLD_FOV_DYNAMICS` 停在抬枪前的旧值。疾跑等
动态 FOV 会在 ADS 过程中持续变化；退出那个门重新放行的一帧会从旧值跳到当前 base FOV，
即实机看到的“疾跑时退出瞄准仍跳变”。因此 PIP 分支仍要用与回落分支**同式同 progress**算出
{`1 + (worldZoomTarget - 1) · progress`}，喂给 `WORLD_FOV_DYNAMICS` 并写回：`share=0` 时该项
恒为 1、等价保持基础 FOV；`share>0` 时世界承担部分倍率且 smoother 始终跟随实时目标，退出时
无缝衔接。`cacheMuzzlePosition` 还直接读 `WORLD_FOV_DYNAMICS.get()` 当 level FOV，保持该状态
同步也让镜口偏移与镜外画面一致。

`IrisCompat.isUsingRenderPack()` 仍保留在门里，但语义变为「未打开 `ScopePipAllowShaderPacks`
时，光影下不抑制」。开着光影却不允许 PIP 时本步不画镜片，因此若照常抑制 FOV 就会变成
“镜外 1×、镜内无画面”，必须让旧整屏变焦继续工作。玩家显式打开允许后，`irisCompatible()`
变为真，光影下也走 PIP 的成品帧合成路径（见 §2.7）。

PIP 永久失败（`failed=true`）或未开（`isEnabled()=false`）时该查询自然为 false，自动回落到
旧的整屏 FOV 变焦，不存在“镜外 1×、镜内也 1×”的兜底缺口。

### 2.5 合成在镜身/孔径之后、但必须在准星和遮光罩之前

第一次实机把合成放在 `renderItemInHand` RETURN，结果输入顺序是：
孔径(-3) → 镜身(-2) → 深度恢复(-1) → 准星(1) → 遮光罩(2) → **合成被最后画**。
于是镜内画面把准星和遮光罩盖住了（本次实机症状 1/2）。

为不动复合管线（它仍是在手部 pass 结束时以 `RenderPass` 覆盖镜内像素）并保持 vanilla
判定顺序，本步改为**当本帧 PIP 合成活跃时，把准星和物理遮光罩从常规 solid pass 挪到
“合成之后”的覆盖层**：

- `BedrockAttachmentModel` 里 `pipDefersReticle = ScopePipRenderState.shouldDeferReticleOverlay()`
  为真时，令 `deferReticleToIrisFinalOverlay = true`，复用 `ScopeFinalOverlayState`
  （其 `final*` 管线本就用无雾 vanilla fragment + 私有世界/孔径深度 mask，天然适合此刻）；
- `GameRendererMixin` 在 RETURN 先 `compositeAfterHand()` 画镜内画面，再
  `ScopeFinalOverlayState.renderAfterFinalComposite()` 把准星、遮光罩画回镜片上方。

`shouldDeferReticleOverlay()` **不用 `sceneCaptured` 判定**，而与
`suppressesWorldFovZoom()` 共用同一稳定逐帧判定（PIP 已开 + 非 Iris + 倍率达标 +
插值开镜进度 > 0）。`sceneCaptured` 是手部 pass HEAD 写入的帧内状态，用它做延迟判定
在下列情况会造成“本帧合成活跃、但准星/遮光罩仍走了旧 solid pass”的错位：
- 捕捉成功但 `BedrockAttachmentModel` 提交发生在标志写入之前；
- 该帧捕捉失败（`sceneCaptured=false`），但手部 RETURN 仍会画准星/遮光罩；
- 其他绕过 `captureScene` 的手部渲染入口。

改用稳定逐帧判定后，只要本帧 PIP 必然接管 FOV（即必然在 hand-pass 末尾合成镜片），
准星/遮光罩就一定被挪到后合成覆盖层；本帧没有 PIP 时该判定为 false，仍走原顺序。

`GameRendererMixin` 的刷新条件也扩展为 `hasPendingOverlay() || (PIP 已开且非 Iris)`，
即使判定在合成前后被重算，已排队的准星/遮光罩也不会滞留在镜片下方。

净效果：镜内画面在下，准星、遮光罩在上，符合真实光路。非 PIP 帧/非第一人称/不瞄准时
`shouldDeferReticleOverlay()` 为 false，仍走原来的 solid-pass 顺序。

`GameRendererMixin` 的刷新现在只对 vanilla 生效：`!IrisCompat.isUsingRenderPack() &&
(hasPendingOverlay() || PIP 已开)`。光影下无论在哪个时机调用都被挡回来，避免把 Iris 的
延迟准星提前刷到 Iris composite pass 之前。

### 2.6 默认仍然跳过 Iris

默认（`ScopePipAllowShaderPacks=false`）沿用 Step 2 的结论：Iris 把手部渲染搬进了
`LevelRenderer#render` 内部，`renderItemInHand` HEAD 抓不到「干净世界」，因此不画镜片、
FOV 走旧整屏变焦。`irisCompatible()` 在光影下为 false，`captureScene`/`suppressesWorldFovZoom`/
`shouldDeferReticleOverlay` 都不进入 PIP 分支。

### 2.7 显式开启光影 PIP（`ScopePipAllowShaderPacks=true`，默认关）

参考 26.2 分支的成品帧屏幕空间方案，按我们的 1.21.11 能力适配（**不抄其代码**）：

1. **抓取点从 `renderItemInHand` HEAD 挪到 `IrisRenderingPipeline#finalizeLevelRendering` TAIL。**
   这时 Iris 已完成全部 composite/final（包括手部），主 target 里就是逐 pack 一致的成品帧。
   镜身已在孔径内被深度裁剪，孔径区域本来就是干净的 1× 世界，所以镜内重投影采样
   `center + (uv-center)/Z` 只覆盖孔径内部，采到的全是干净世界，没有 pack 相关 colortex 猜测。
2. **门限**：只在该帧 `aimingProgress >= 0.995`（接近满开镜）时抓取+合成。因为成品帧包含枪/手，
   开镜滑动时采样区会压到 viewmodel；我们没有 26.2 的 ColorModulator 动态 uniform 通路，
   把 zoom 作为 `#define` 每帧重建管线会泄漏/卡顿，所以宁愿满开镜才生效，避免镜内放大出一把枪。
3. **准星/遮光罩**仍复用 `ScopeFinalOverlayState`：`IrisFinalScopeOverlayMixin` 在 TAIL 先
   `captureSceneAfterIrisFinal`、再 `compositeAfterIrisFinal`、最后
   `renderAfterFinalComposite()`，成品帧 → 镜内画面 → 准星/遮光罩的顺序成立。
4. **世界深度**：`ScopeDepthCopyState.BACKUP` 在本帧 PIP 光影路径下强制拷一份私有世界深度
   （`ScopePipRenderState.needsIrisWorldDepthCopy()`），因为 Iris 在 final 之后不再绑定 `depthtex2`，
   而合成要读我们的私有拷贝才能做孔径 mask。孔径深度本就是在镜身边界拷的，本就私有可读。
5. **安全网**：所有光影 PIP 判定都同时要求 `IrisCompat.supportsFinalScopeOverlay()`（当前只对
   已字节码审计的 Iris 1.10.7 为真）；其余 Iris 版本即使打开开关也走原整屏变焦，绝不盲试。

---

## 3. 自审（源码级）

- **抓取失败即退回**：`SceneColorTarget.copyFromCurrentDrawFramebuffer()` 任一 GL 错误都会
  `sceneCaptured=false`，不画合成；异常则 `failed=true` 永久停用并回落到整屏 FOV 变焦。
- **两个 debug 开关互斥**：`-Dtacz.scope.pip.debug.paint`（Step 2）在
  `-Dtacz.scope.pip.enable`（Step 3）打开时自动让位，避免品红覆盖真实画面。
- **生命周期**：深度包装类与 Step 2 一样不 close/不 free；`SceneColorTarget` 仅由本类在
  尺寸变化/失败时重建，wrapper 引用不会释放底层 GL id。
- **倍率只在满开镜正确**：本步直接用 `IGun#getAimingZoom`，没有
  `1 + (Z-1)·progress`（那是后续步）。抬镜过程中镜内会直接显示满倍率，属已知阶段限制。

---

## 4. 未验证项（必须实机确认）

1. **主 target 颜色格式 ≠ RGBA8**：本步 target 直接读 `main.getColorTexture().getFormat()`
   并用同格式建离屏纹理，因此格式不匹配的窗口已收窄。若报
   `Step3 could not capture ... falling back`，说明主颜色格式不在
   `RGBA8/RED8` 白名单内，请发日志。
2. **`copyTextureToTexture` 的格式/usage 校验**：26.2 已实测同一条 API，风险较低；
   仍需实机确认官方映射下目标 `GlTexture` 包装能被接受。
3. **`withShaderDefine(..., float)` 在官方映射下编译为 `#define`**：本分支 `ScopeRenderTypes`
   已用 `withShaderDefine`，本次又新增 `TACZ_PIP_SHARPNESS` / `TACZ_PIP_PAINT_LENS` 两个 float
   define，应可编译但未经实际构建验证。
4. **镜内分辨率在 8× 下较糊**：这是重投影的固有上限；本步加了可配置的 5 抽头钝化蒙版锐化
   （默认 0 = 关），但未做 Catmull-Rom 双三次重建。
5. **抬镜过程中的观感**：尚未做 aim-progress 渐入，抬镜中可能看到突变，属已知。

---

## 5. 实机确认步骤

> 开关：游戏内 ModMenu → 渲染 → 「瞄准镜画中画（PIP）」；开发环境仍可用 JVM 属性
> `-Dtacz.scope.pip.enable=true` 作为覆盖（二者任一为真即生效）。

1. **源码/开发环境**（用 JVM 覆盖，省去手动开配置）：
   ```bat
   gradlew build
   gradlew runClient -PtaczScopePip=true
   ```
2. **打包后的模组 / 第三方启动器**：在 ModMenu 里打开 PIP（或 JVM 参数加
   `-Dtacz.scope.pip.enable=true`）。
3. 相关配置项（皆为可选）：`ScopePipMinAimingProgress`（默认 0.05）、
   `ScopePipMinMagnification`（默认 4.0，低于该倍率走整屏变焦）、`ScopePipWorldZoomShare`（默认 0）、
   `ScopePipSharpness`（默认 0.5）、`ScopePipDebugNoComposite`（默认关）、
   `ScopePipDebugPaintLens`（默认关）。
4. 进游戏：**不要开任何光影**（Iris 会跳过本步），拿一个 6× / 8× 的镜，**抬到满开镜**。
5. 观察（对照 Step 2 的品红形状）：镜片内现在应显示**放大的世界画面**，而不是品红；
   - 镜片外（镜身、屏幕四周）应为正常 1× 世界（默认 share=0；调高 `WorldZoomShare` 会相应变焦）；
   - 镜内**不应出现枪 / 手**（抓取发生在手部绘制前）；
   - 镜内缩放倍数应与瞄具标称倍率一致（6× 镜应明显放大中心画面）；
   - 镜内画面上**能看到准星分划**，镜片边缘的**物理遮光罩黑圈**也在上层（不被镜内画面盖住）；
   - 反复进入/退出开镜，世界 POV **不出现短暂跳变**。
6. 无参数重启：确认整屏 FOV 变焦（旧行为）恢复，且无残留。

日志应各出现一次：
```
[TACZ Scope] Step3 captured a ... clean pre-hand world for 6x PIP.
[TACZ Scope] Step3 composite painted the 6x lens from a 1-magnified world (total 6x; ...)
```

如果没有第一条 → 抓取被跳过（没有瞄准 / Iris / 主颜色纹理为空）。
如果日志出现 `Step3 could not capture a clean pre-hand world this frame ... falling back`
且画面“里外都 1×”，说明抓图失败导致 FOV 抑制也回退；回传该日志。
如果只有第一条、没有第二条 → 合成失败，回传 `Step3 composite failed` 异常栈。

---

## 6. 回归复测清单

### 关闭（默认）
- [ ] 无任何画面变化，与 Step 2 关闭时一致；整屏 FOV 变焦照常。
- [ ] `ScopeFinalOverlayState` / `ScopeLateReticleState` 未受影响。
- [ ] 控制台无 `Step3 ...` 日志。

### 开启（仅 vanilla / 无光影）
- [ ] 镜片内是放大的世界，镜外默认 1×（`WorldZoomShare=0`），镜内无枪/手。
- [ ] 镜内画面上**能看到准星（分划）轮廓**，不再被镜内画面盖住。
- [ ] 镜片边缘的**物理遮光罩（黑圈）**仍在上层，不再被盖住。
- [ ] 若仍未盖住，看日志对照（`compositeAfterHand` 后刷新延迟准星/遮光罩）：
      - `[TACZ Scope] Queued reticle for post-composite overlay (Iris or PIP lens).`
        —— 已进入延迟队列；
      - `[TACZ Scope] Rendered deferred reticle and ocular rim after the final cover (N reticles, M rims).`
        —— 已在合成后真正重画；
      - 只见第二句不见第一句：`shouldDeferReticleOverlay()` 在提交时为 false，
        该帧准星/遮光罩走了普通 solid pass，被合成盖住（稳定判定已改为与 FOV 抑制同源）；
      - 只见第一句不见第二句：`renderAfterFinalComposite()` 未在合成后触发，
        查 `GameRenderer` 的 RETURN 刷新条件。
- [ ] 进入/退出开镜时世界 POV **无短暂跳变**；默认配置下镜外全程 1×。
- [ ] `WorldZoomShare > 0` 时，镜外按比例变焦、镜内仍补足到总倍率；满开镜总倍率不变。
- [ ] `Sharpness>0` 时镜内更锐且无溢出到镜外。
- [ ] 低于 `MinMagnification` 的倍镜走旧整屏变焦；高于才走 PIP。
- [ ] `DebugNoComposite` 开启时镜片为空、无合成日志之外无异常。
- [ ] 开镜/收镜后无残留；关闭 PIP 配置后立刻恢复旧行为。
- [ ] 满开镜时倍率与瞄具标称一致。

### Iris（默认，`AllowShaderPacks=false`）
- [ ] Step 3 显式跳过；`Step3 capture` 日志不出现。
- [ ] Iris 路径零改动；准星/遮光罩仍走已实机通过的 Iris final-overlay 控制路径。
- [ ] 镜外 FOV 仍走旧整屏变焦（不会被误判成 PIP 而变成镜外 1×）。

### Iris（显式开启 `AllowShaderPacks=true`，待实机验证）
- [ ] 满开镜后镜片内出现放大世界，镜外默认 1×；准星/遮光罩仍在镜片上方。
- [ ] 开镜滑动途中镜片保持现状（本步刻意等到 aim progress ≥ 0.995 才合成，避免镜内扫到 viewmodel）。
- [ ] 镜内颜色与镜外一致（成品帧屏幕空间合成，无 colortex 猜测）。
- [ ] 日志出现 `Step3 captured a ... Iris finished frame for ...x PIP` 与随后的
      `Step3 composite painted ...`，且没有 `failed` / 异常栈。
- [ ] 若镜片没画面：先看是否出现 `Step3 could not capture ...`（抓取失败）或
      `Step3 composite failed`（合成失败），回传对应日志。
