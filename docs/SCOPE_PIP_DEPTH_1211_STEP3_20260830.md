# 1.21.11 深度版镜内画中画（PIP）原型 — Step 3: 真实镜内放大画面

日期: 2026-08-30
分支: `arena/01a0518d-tacz-refabricated-unofficial` (1.21.11, depth-aperture 架构)
前置: Step 2（纯品红全屏判据）已实机 **PASS**。Step 3 把品红换成「镜片里真的放大后的世界」。

本文档是 Step 3 的源码级实现与实机确认步骤。**默认关闭，且不是配置文件选项。**

---

## 0. 本步目标

- 在主手绘制**之前**把「已画完的世界」颜色拷进一个离屏 RGBA 纹理；
- 在主手绘制**之后**全屏合成：只用 Step 2 的孔径判据 `ad < wd - 1e-6` 落笔，
  镜片内输出 `texture(scene, center + (uv - center) / Z)`；
- **镜外世界全程保持 1×**（抑制旧的整屏 FOV 变焦），镜片内出现物理正确的 Z× 放大。

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

未改：`ScopeDepthCopyState` 深度备份/恢复链路、`ScopeLateReticleState`、Iris 路径主体、
配置项。`ScopeFinalOverlayState` 仅新增两个非破坏性扩展：裸遮光罩也可排队（自动抓取手部
transform）、空准星时允许仅含遮光罩的刷新；这两种都是原逻辑的能力超集，非 PIP/Iris 时行为不变。
正常模式（不带 `-Dtacz.scope.pip.enable=true`）下零影响。

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

### 2.4 FOV 抑制

`ScopePipRenderState.suppressesWorldFovZoom()` 被 `CameraSetupEvent#applyScopeMagnification`
调用。开着 PIP、未用光影（Iris）、当前持有 >1× 瞄具且**本地玩家已开始抬枪
（aim progress > 0）**时，`applyScopeMagnification` 直接 `return`（保持基础 FOV）。

键点一：**不要**再以 `sceneCaptured` 为依据。`sceneCaptured` 是 `renderItemInHand` HEAD 写出的
“本帧抓图是否成功”，而 FOV 计算发生在同一帧更早/更晚的位置，这条标志在开镜/收镜过渡中
会时真时假，导致世界 POV 短暂跳变（本次实机症状 3）。

键点二：aim-start 查询要用**当前 tick 的 progress（`partialTicks=1`）**，不能用
`partialTicks=0`。`LocalPlayerAim#getClientAimingProgress(0)` 返回的是**上一 tick** 的值；
而 `CameraSetupEvent#applyScopeMagnification` 用 `event.getPartialTick()` 插值。进入/离开 ADS
的边界帧上两者不一致，门就会放走一帧旧整屏变焦 —— 这正是把 `sceneCaptured` 改成
aim-start 查询后仍残留的 POV 跳变根因。`partialTicks=1` 得到的是当前 tick 的单调 progress，
整段过渡中门始终为真，世界 POV 保持 1×。

`IrisCompat.isUsingRenderPack()` 也保留在门里：它在整个会话内稳定为真/假。开着光影时本步
明确不画镜片，因此若照常抑制 FOV 就会变成“镜外 1×、镜内无画面”，必须让旧整屏变焦继续工作。

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

净效果：镜内画面在下，准星、遮光罩在上，符合真实光路。非 PIP 帧/非第一人称/不瞄准时
`shouldDeferReticleOverlay()` 为 false，仍走原来的 solid-pass 顺序；Iris 路径不受影响
（PIP 在 Iris 下显式跳过，本步的 overlay 刷新只在 vanilla 生效）。

### 2.6 为什么仍然跳过 Iris

本步沿用 Step 2 的结论：Iris 的 depthtex2/final-composite 桥接尚未完成，且 Iris 把手部
渲染搬进了 `LevelRenderer#render` 内部，`renderItemInHand` HEAD 抓不到「干净世界」。
因此本步只在 vanilla / 无光影路径跑。

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
   已用 `withShaderDefine`，应可编译；但 float 形式未经实际构建验证。
4. **镜内分辨率在 8× 下较糊**：这是重投影的固有上限，本步未做 Catmull-Rom/锐化。
5. **抬镜过程中的观感**：尚未做 aim-progress 渐入，抬镜中可能看到突变，属已知。

---

## 5. 实机确认步骤

> 开关是 **JVM 属性** `-Dtacz.scope.pip.enable=true`，不是 TACZ 配置文件选项。

1. **源码/开发环境**：
   ```bat
   gradlew build
   gradlew runClient -PtaczScopePip=true
   ```
2. **打包后的模组 / 第三方启动器**：JVM 参数加
   ```
   -Dtacz.scope.pip.enable=true
   ```
3. 进游戏：**不要开任何光影**（Iris 会跳过本步），拿一个 6× / 8× 的镜，**抬到满开镜**。
4. 观察（对照 Step 2 的品红形状）：镜片内现在应显示**放大的世界画面**，而不是品红；
   - 镜片外（镜身、屏幕四周）应为正常 1× 世界；
   - 镜内**不应出现枪 / 手**（抓取发生在手部绘制前）；
   - 镜内缩放倍数应与瞄具标称倍率一致（6× 镜应明显放大中心画面）；
   - 镜内画面上**能看到准星分划**，镜片边缘的**物理遮光罩黑圈**也在上层（不被镜内画面盖住）；
   - 反复进入/退出开镜，世界 POV **不出现短暂跳变**。
5. 无参数重启：确认整屏 FOV 变焦（旧行为）恢复，且无残留。

日志应各出现一次：
```
[TACZ Scope] Step3 captured a ... clean pre-hand world for 6x PIP.
[TACZ Scope] Step3 composite painted the 6x lens (...)
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
- [ ] 镜片内是放大的世界，镜外 1×，镜内无枪/手。
- [ ] 镜内画面上**能看到准星（分划）轮廓**，不再被镜内画面盖住。
- [ ] 镜片边缘的**物理遮光罩（黑圈）**仍在上层，不再被盖住。
- [ ] 进入/退出开镜时世界 POV **无短暂跳变**；镜外全程 1×。
- [ ] 开镜/收镜后无残留；关掉参数立刻恢复旧行为。
- [ ] 满开镜时倍率与瞄具标称一致。

### Iris（无论开关）
- [ ] Step 3 显式跳过；`Step3 capture` 日志不出现。
- [ ] Iris 路径零改动。
