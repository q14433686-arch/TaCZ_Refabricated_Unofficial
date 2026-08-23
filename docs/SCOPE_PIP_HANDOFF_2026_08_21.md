# 瞄准镜镜内画中画（Scope PIP）· 会话交接

**日期**：2026-08-21（凌晨，接续 08-19 夜间会话）
**分支**：`26.2(main)`
**当前 jar**：`build/libs/TACZ-Refabricated-26.2-1.1.8+fabric.26.2.R2.jar`（05:3x 构建，`./gradlew build` 通过）

> **2026-08-23 状态更新**：光影路径已随
> [PR #66](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/pull/66)
> （含 `3b42fc9`「光影下镜内改为真实第二世界渲染」）合并进 `26.2(main)`，
> 以**实验性功能**形式随主线发布，默认关闭（`ScopePipEnable=false`）。
> 本文「🟡 待实机验证」为 08-21 交接时的状态描述；用户侧开启教程与实验性声明见
> 根目录 `README.md` §4.2。

> ⚠️ **实测前务必确认 `mods/` 里那个 jar 就是刚构建的那个。**
> 08-21 有整整两个会话（47366 + 4074 条报错）是在追一个已经改过、但 jar 没换的问题上浪费的；
> 更早还有一次 R1 盖住 R2。日志里认这一行：`- tacz 1.1.8+fabric.26.2.R2`，
> 再对一眼 `mods/` 里 jar 的时间戳。

> 本文用于**新会话冷启动**。读完这一篇就能接着干，不必回看聊天记录。
> 按 `AGENTS.md` §2：**已实测**与**推导未验证**分开标注，不写没验证过的结论。

---

## 0. 一句话现状

| 环境 | 状态 |
|---|---|
| **无光影** | ✅ **完全可用**。镜外 1×、镜内原生分辨率放大、无溢出、地形正确 |
| **开光影（Iris）** | 🟡 **代码完成、编译通过、待实机验证**。已换成<b>屏幕空间</b>方案（§2.3e）：等 Iris 整条管线画完，直接在最终画面上放大。不再依赖任何 pack 的 colortex 约定 |

**默认配置是安全的**：`ScopePipEnable=false`。玩家不动配置 = 与本次改动前完全一致。

> **读本文的顺序**：光影路径只看 **§2.3e**（当前方案）。
> §2.1–2.3d 全是已被推翻的历史，保留是为了「别再走第四遍」，不是现状描述。

> **04:1x 已修的一个回归**（曾表现为「Voxy 坏了」）：shader 注入里的 `colortex1` 前缀撞名
> 会让 pack 编译失败、Iris 整体禁用光影。详见 §2.6。
> **§2.3e 之后注入里已经完全不出现 `colortex`，这类事故从根上消失。**

---

## 1. 无光影路径：做完了，别动

### 1.1 最终形态

`ScopePipRerender=true` 时：用**窄 FOV 把世界再渲染一遍**到离屏 target，
再由目镜掩码把这张原生分辨率的图贴进镜片。镜外世界 FOV 完全不动。

关键实现点（都踩过坑，改之前先读注释）：

| 文件 | 作用 |
|---|---|
| `client/render/scope/ScopePipRenderer.java` | 判定闸门、二次渲染、合成 |
| `client/render/scope/ScopePipTarget.java` | 离屏 target 生命周期 |
| `compat/sodium/SodiumCompat.java` | **两处** Sodium 状态同步（见 §1.2，最关键） |
| `mixin/client/GameRendererMixin.java` | 帧状态归零、二次渲染注入、`mainRenderTarget()` 重定向 |
| `mixin/client/SkyRendererMixin.java` | 天空渲染跟随重定向（它缓存了 target） |
| `assets/tacz/shaders/core/scope_pip.fsh` | 合成着色器（重投影模式用） |

### 1.2 【最重要】那个折腾了整晚的「溢出」根本不是溢出

症状：镜内正常，但**镜外**的近处水面、冰柱被拉伸放大，远处地形却正常。

真因：**Sodium 每帧只上传一次区块投影**。

```java
// UniformBufferManager.update  (字节码实读)
public void update(ChunkRenderMatrices m, FogParameters fog) {
    if (this.hasUpdatedThisFrame) return;   // ← 偏移 0-7
    this.hasUpdatedThisFrame = true;
    ... 上传 m.projection() ...
}
public void prepareFrame() { this.hasUpdatedThisFrame = false; }
```

镜内那一遍**先到**，带着窄 FOV 上传成功并把闸关上；随后 vanilla 那一遍带正常矩阵调
`update()` 时**直接 return**。于是主画面里 Sodium 的地形全部用镜内的窄投影绘制。
远处地形归 Voxy LOD（另一套 uniform 通路）管，所以看着正常 —— 那个「远近差异」
正是这条的签名。

**修法**：两遍之间调 Sodium 自己的 public `prepareFrame()` 把闸重新打开
（`SodiumCompat.resetChunkUniformUpload()`，放在 `finally` 里）。

> **教训**：症状对上不等于归因对上。「地形没跟着放大」既能由「渲染目标没跟随」解释，
> 也能由「投影/uniform 没跟随」解释，画面上完全一样。整晚有 6 次误判都源于此。

### 1.3 其余已修的真 bug

- **Sodium 投影快照**：Sodium 用 `GameRendererStorage.sodium$getProjectionMatrix()`
  自己那份，**不看** `RenderSystem.setProjectionMatrix`。→ `SodiumCompat.overrideProjection()`。
- **`SkyRenderer` 缓存 target**：全客户端只有它把 `mainRenderTarget()` 存成
  `private final` 字段，重定向对它无效。→ `SkyRendererMixin` 改字段**读取**表达式。
  （已核对：`ChunkSectionLayerGroup`、`CloudRenderer`、`QuadParticleFeatureRenderer`、
  `WorldBorderRenderer`、`OutputTarget`、Sodium `TerrainRenderPass` 全是每次现问，不受影响。）
- **掩码标志被 Iris 双手部 pass 抹掉**：Iris `HandRenderer` 一帧调两次
  `renderAllFeatures`（`renderSolid` + `renderTranslucent`），旧实现每次手部 pass 都清零，
  于是帧末恒为 false。→ 改为每帧在 `GameRenderer#extract` HEAD 清一次
  （`ScopeMaskRenderer.beginFrame()`），并区分 `hasMaskThisFrame` / `hadMaskLastFrame`。
- **凸包近平面炸包**：目镜顶点擦过近平面时 w≈0.001，NDC 飙到 ±1000，凸包撑满全屏。
  → `NDC_SANITY_LIMIT = 2.0` 过滤。
- **合成硬件剪裁**：按目镜屏幕包围盒开 scissor，掩码失效时也不会整屏糊上去。

---

## 2. 光影路径：卡在哪，已排除什么

> ⚠️ **§2.1 – §2.3d 是历史记录，不是现状。** 当前方案见 **§2.3e**。
> 这几节描述的「在 pack 着色器里采样 colortex 输出颜色」已整体删除。

### 2.1 【历史】曾经的实现

**纯 shader 源码注入**，在 `IrisShaderCreatorMixin` 里改 pack 的片元着色器字符串。
**全程内存操作，不碰磁盘上的 `.zip`**（用户明确要求：只许改本仓库的文件）。

模式约定（`tacz_ScopeMaskMode`，由 `IrisScopeMaskState` 逐 draw 设置）：

| 模式 | 语义 |
|---|---|
| 0 | 关闭 |
| 1 | 镜身：孔径**内** discard（透视镜片，原有功能） |
| 2 | 准星：孔径**外** discard |
| 3 | ~~镜身：孔径内输出放大后的世界~~ —— **已从代码中删除**，见 §2.3e |

### 2.2 已验证可用的部分（实测）

黑色八边形那次截图证明了**机制本身全通**：
- 注入能编译进真实 shaderpack（Eclipse-Shader-Unstable）；
- 模式经 `IrisScopeMaskState` 正确送达着色器；
- **掩码精确定义了镜片形状**；
- **写出去的颜色能到屏幕上**（这是外部所有尝试都做不到的）。

### 2.3 唯一的缺口

> **2026-08-21 04:1x 更新**：探针此前**从来没能跑起来**，有两个独立原因，均已修：
> 1. 闸门 `tacz_ScopePipZoom < 0.0` 恒不成立 —— 唯一写这个 uniform 的
>    `IrisScopeMaskState` 送的是 `magnification()`，被 `Math.max(1.0f, …)` 夹过；
>    而 `ScopePipBridge.paintLensDebug()` **一个调用方都没有**。
> 2. 见 §2.6：网格代码把整个 shaderpack 编译弄挂了。
>
> 现在探针改成**链接期**开关（`tacz$probeEnabled()`）：关着时整段网格不生成。
> 因此**改完 `ScopePipDebugPaintLens` 必须重载一次 shaderpack（R 键）才生效**。
> 日志会打印 `colortex probe grid: ON/off`，照着确认。


`texture(colortex0, uv)` 在手部着色器里**采样结果是黑的**。也就是说：
手部阶段 `colortex0` 里没有已着色的场景。

**下一步就是找出哪张 colortex 有场景。** 03:37 那版已经写好了探针：
`ScopePipDebugPaintLens=true` 时把孔径切成 4×2 网格，
每格采样一张不同的 `colortex0..7`（**通过 Iris 亲自绑定的采样器**，
不是外部反射拿的纹理 id）。哪一格有画面，哪张就是要读的。

> ⚠️ 这个探针**还没跑过** —— 跑之前撞上了 §2.5 的崩溃。

### 2.3b 【04:3x 已定性】黑镜片的真因是**阶段顺序**，不是采样错了 colortex

反编译 Iris 1.11.2 实读，三条互相独立的证据：

```
MixinLevelRenderer.iris$beginTranslucents:
    偏移 51  HandRenderer.renderSolid(...)      ← 镜身在这里画
    偏移 69  pipeline.beginTranslucents()
IrisRenderingPipeline.beginTranslucents():
    copyPreTranslucentDepth();
    deferredRenderer.renderAll();               ← 延迟光照在这里才跑
```

1. `gbuffers_hand`（HAND_CUTOUT）跑在延迟光照**之前** ⇒ 那一刻
   **没有任何 colortex 装着已着色的场景**，取哪张都是黑的。
   **§2.3 的八格探针即使跑起来也找不到答案** —— 八个格子里根本没有正确答案。
2. `MixinItemInHandRenderer.iris$skipTranslucentHands`：
   `isRenderingSolid() == isHandTranslucent(stack)` 时 `ci.cancel()`。
   枪不是 `BlockItem` ⇒ `isHandTranslucent` 恒 false ⇒ 枪**只在 solid pass 提交**。
3. 08-21 日志：`HAND_TRANSLUCENT`/`HAND_WATER` 出现 **0 次**，
   六条 tacz 管线全部只解析到 `HAND_CUTOUT`。与 1、2 完全吻合。

**已落地**：`IrisScopeMaskState` 里镜身通道恒返回 1（原为「开 PIP 就返回 3」）。
模式 3 在 HAND_CUTOUT 下采样 colortex0 正是黑镜片的成因；
恒 1 让镜片退化成**透视 1×**而不是纯黑 —— 这也是后续方案失败时的安全底线。

### 2.3e 【当前方案 · 05:2x】屏幕空间：等 Iris 画完，直接在最终画面上放大

> **2.3c / 2.3d 那条「在 pack 的着色器里采样 colortex」的路已整体放弃并从代码中删除。**
> 下面两节保留为路标，说明它为什么走不通 —— 别再走第三遍。

**核心转变**：不再试图在 Iris 管线<b>内部</b>取素材，
而是等它**整条跑完**，在最终画面上做屏幕空间放大。

#### 为什么这条路不再需要任何猜测

| | 着色器内采样（已废弃） | 屏幕空间（当前） |
|---|---|---|
| 素材来自 | 某张 `colortexN`，**逐 pack 不同** | Iris 的**最终画面**，全局唯一 |
| 要不要猜 | 要（Eclipse=2，别家未知） | **不要** |
| 颜色是否与镜外一致 | 不保证（可能是中间态、线性空间） | **逐字节同源，必然一致** |
| 炸包风险 | 高（补 colortex 声明曾整体禁用光影） | **零**（注入里不再出现 colortex 三个字） |

#### 关键：镜内为什么不会出现一把缩小的枪

这是整条路能成立的支点。镜身在孔径内是 `discard` 的（模式 1），
所以**最终画面里孔径那一块就是干净的 1× 世界**，没有枪。
而重投影采样点是 `center + (uv-center)/Z`：`uv` 取遍孔径时，
采样点只覆盖以屏幕中心为原点、半径缩到 `1/Z` 的一小块 —— **整个落在孔径内部**。
于是采到的全是干净的世界像素。

（无光影那条路必须在 `renderItemInHand` <b>之前</b>抓图，正是因为它没有这个性质。）

#### 倍率跟着开镜进度走

抬镜过程中瞄具还没移到屏幕中心，而采样点恒定绕屏幕中心收缩，
此时那一小块可能还压在枪身上 —— 直接给满倍率会让镜内闪过一段放大的枪。

改用 `1 + (Z-1)·progress`：`progress→0` 时倍率→1，采样点 == 当前像素本身，
合成结果与底下已经画好的 1× 画面**逐像素相同**，肉眼零变化；
瞄具归位时才到满倍率。顺带与整屏变焦那条老路的公式一致，过渡手感也对得上。

#### 落点

| 文件 | 改动 |
|---|---|
| `GameRendererMixin` | 在既有的「`LevelRenderer#render` 之后」注入点追加 `compositeAfterLevelUnderShaders()`。**光影下这一点的含义变了**：Iris 把手部渲染搬进了 `LevelRenderer#render` 内部，所以此刻延迟光照、composite、色调映射、手部**全部跑完** |
| `ScopePipRenderer` | 新增 `compositeAfterLevelUnderShaders()`；合成本体抽成共用的 `runComposite(magnification)`；`captureScene` 在光影下不再让开 |
| `IrisScopeMaskState` | 镜身通道恒为 1（只 discard，不写颜色） |
| `IrisShaderCreatorMixin` | **删掉**模式 3、colortex 声明、探针网格、输出变量解析。注入只剩 discard |

#### 顺带的两个收益

1. **注入里不再出现 `colortex`**，`undefined variable "colortex1"` → 光影被禁用 →
   Voxy 视口全 0 那一类事故**从根上消失**。
2. 不再需要解析 pack 的输出变量名（`tacz$findFragmentOutput`），
   于是**那些解析不出输出变量的 pack 现在也能有目镜掩码**了 —— 以前是整段放弃注入。

#### 已知代价

镜内实际只由孔径内 `1/Z` 那一小块像素放大而来，高倍镜下会明显变软 ——
与重投影模式同一个上限（IMPLEMENTATION 文档 §3）。
换来的是**一定出图、颜色一定对、不依赖任何 pack 约定**。

---

### 2.3c 【已废弃 · 保留为路标】把枪挪进半透明手部 pass

由 §2.3b 第 2 条反推：**只要 `isHandTranslucent(枪)` 返回 true**，
`iris$skipTranslucentHands` 的判等就反过来 ——
solid pass 被 cancel、translucent pass 得以提交，
于是整把枪走 `gbuffers_hand_water`，**跑在 `beginTranslucents()` 之后**，
`colortex0` 这时才真的装着已着色的场景，模式 3 的采样就成立了。

改动量极小（远小于「自己在半透明边界画一遍目镜网格」那条路 ——
那条要自建顶点格式、还要跟 pack 的 vsh 对 MVP，盲改风险太高）。

**已落地的五处**：

| # | 文件 | 改动 |
|---|---|---|
| 1 | `mixin/client/iris/IrisHandTranslucentMixin.java`（新） | `HandRenderer#isHandTranslucent` HEAD 注入：持枪且 `wantsIrisComposite()` 时返回 true |
| 2 | `tacz.iris.mixins.json` | 注册上面这个 mixin |
| 3 | `compat/iris/IrisCompat.java` | 新增 `isHandRenderingSolid()`，**Method 句柄惰性解析并缓存**（逐 draw 调用，不能每次反射查表） |
| 4 | `compat/iris/IrisScopeMaskState.java` | 镜身通道：`wantsIrisComposite() && !isHandRenderingSolid() ? 3 : 1` |
| 5 | `client/render/scope/ScopePipRenderer.java` | 新增 `irisOwnsLens()`；光影下 `captureScene` / `compositeAtPhaseBoundary` 整条让开 |

着色器侧**没动**：模式 3 的分支早就写好，且黑八边形那次已证明它能把颜色写到屏幕上。

#### 两个必须知道的坑（都已处理）

**(a) 逐帧 latch —— 不做的话枪会整把消失。**
`isHandTranslucent` 一帧被问<b>两次</b>（实心一次、半透明一次），
而 Iris 的判据是 `isRenderingSolid() == isHandTranslucent(stack) → cancel`。
两次答案必须一致：
- 都答 true：实心 `true==true` cancel（让位），半透明 `false==true` 不成立 → 提交 ✔
- 若半透明那次答成 false：`false==false` → **也 cancel** ⇒ 两遍都没画，**枪没了**。

而现算的输入里确实有帧内会漂的量（开镜进度按 partialTick、掩码标志由渲染过程置位）。
所以 `ScopePipBridge.wantsIrisComposite()` 改成**本帧第一次问就定下答案并缓存**，
由 `ScopePipBridge.beginFrame()`（挂在 `GameRenderer#extract` HEAD）每帧清空。

**(b) 镜片只能有一个主人。**
光影下若让「拷贝主画面 + 全屏重投影」那条老路继续跑，它会用 vanilla 管线把一张
**未经光影着色**的图糊进孔径，正好盖掉 shader 注入刚写对的颜色。
`irisOwnsLens()` 把它整条关掉，顺带省一次每帧全屏拷贝。

#### 实测时照这三行日志走（按出现顺序）

```
[TACZ Scope] Routing the scoped gun into Iris' translucent hand pass ...
[TACZ Scope] Iris scope-mask bridge active (mode=..., ...)
[TACZ Scope] Scope PIP is drawing in the Iris translucent hand pass (post-deferred); ... zoom=Nx
```

- 第 1 行没出现 ⇒ `IrisHandTranslucentMixin` 没生效（`require = 0`，**失败是静默的**）。
  查 Iris 版本是否改了 `isHandTranslucent` 的名字/签名。
- 第 1 行有、第 3 行没有 ⇒ 枪挪过去了但镜身没解析到模式 3，
  查 `isHandRenderingSolid()` 的反射是否解析成功（解析失败会保守返回 true）。

### 2.3d 【已废弃 · 保留为路标】镜内「灰噪块 + 黑」：场景色不在 colortex0

挪进半透明 pass 之后镜片不再是纯黑，但内容不对：
上部是天空、左下一块灰噪矩形、右下纯黑。

**真因**：写死了 `colortex0`，而那一刻 colortex0 装的是 **gbuffer 数据**，不是着色后的场景。
各家 pack 把场景色放哪张是自己定的，用 `/* RENDERTARGETS:a,b,c */` 声明，
**第一个索引**就是该程序 location 0 的输出 —— 对半透明程序而言正是「要混进去的那张场景色」。

逐文件实读用户的 Eclipse-Shader-Unstable（只读解包，未改动 zip）：

| 文件 | 指令 | 说明 |
|---|---|---|
| `world0/gbuffers_hand_water.fsh` | → `#include /dimensions/all_translucent.fsh` | 镜片就跑在这个程序里 |
| `dimensions/all_translucent.fsh` | `RENDERTARGETS:2,7,11,14` | **location 0 → colortex2** |
| `dimensions/deferred2.fsh` | `RENDERTARGETS:2,1,9` + `gl_FragData[0] = texelFetch(colortex2, …)` | 场景色确实是 colortex2 |

⇒ 该 pack 的**场景色 = colortex2**。

**顺带解决了「会不会读写同一张纹理」那个悬念**（原 §2.3c 待确认项 2）：
`deferred2` 自己就同时读写 colortex2 且工作正常，
**证明 Iris 对它做了乒乓翻转** —— 我们在同一程序里读它拿到的是「本 pass 之前」的内容，
正是想要的已着色场景，不是未定义行为。

**当时的修法**（现已连同整条路一起删除）：新增配置 `ScopePipShaderSceneTex`（默认 `-1` = 自动）。
自动 = 从源码里解析 `RENDERTARGETS`/`DRAWBUFFERS` 取第一个索引，解析不到退回 0
（与 Iris 自己对无指令 pack 的默认一致）。

> ⚠️ **自动判定可能失灵**：该指令是 pack 加载期由 `ProgramDirectives` 解析的，
> 而我们拿到的是 glsl-transformer **变换后**的源码，注释很可能已被剥掉。
> **所以先看日志那一行**：
> `Injecting scope-mask branch into Iris linked fragment shaders (scene tex: colortexN, …)`
> 若 N 不是期望值，就用配置显式指定。**Eclipse-Shader-Unstable 应设为 2。**
> 改完要重载 shaderpack（R）。

#### 仍待实测确认的一件事

1. **观感**：枪整体改走 `gbuffers_hand_water`，不再参与延迟光照，
   着色可能与实心通道略有差异。只在「持倍镜 + 开镜」那几帧发生。
   若不可接受，退路是加一个配置项把 (1) 关掉 —— 届时镜片自动退回透视 1×。

> 原来的待确认项 2（读写同一张纹理是否会炸）已由 §2.3d 解决：
> `deferred2` 自己就同时读写 colortex2，证明 Iris 会翻转，不是未定义行为。

### 2.4 已经排除的（别再试一遍）

| 尝试 | 结果 |
|---|---|
| 读当前绑定 FBO 的 `COLOR_ATTACHMENT0` | 读到法线缓冲（实测：镜内是放大的法线+营火） |
| 写当前绑定 FBO | **写是成功的**（画面能到屏幕），但那是 gbuffer，不是场景 |
| 反射 `RenderTargets.get(n).getMainTexture()` | 平铺 8 格**全空** |
| 同上但取 `getAltTexture()` | 同样**全空** ⇒ 这条反射取法本身不可信 |
| `getFlippedAfterPrepare/AfterTranslucent()` | 报「未翻面」，与实测不符，别拿它当准 |
| 自建 FBO 挂 colortex0 去写 | 写进去的内容被下游丢弃，屏幕无变化 |
| 二次渲染（rerender）在光影下 | **禁用**：一帧两次驱动 Iris 管线会把它的时域/乒乓缓冲搞乱，整屏噪点。已在 `rerenderMode()` 里硬性拦下 |

### 2.5 【已推翻】曾把 Voxy 的 viewport 归因于 `IrisScopePip`

当时写的是：「`IrisScopePip.drawColortexGrid()` 逐格设 `glViewport(...)` 而
`restoreGlState()` 没还原，Voxy 读到残留视口」。

**这条是错的**，2026-08-21 的日志逐条推翻了它：

- 08-21-**2** 会话：光影已加载（6 条 `Found fine program match`），Voxy 报错 **0 次**；
- 08-21-**7** 会话（03:41，跑的正是含 `IrisScopePip` 的 03:37 jar）：**47366 次**；
- 08-21-**8** 会话（04:05，跑的是已删除 `IrisScopePip` 的 03:44 jar）：**仍有 4074 次**。

删掉 `IrisScopePip` 前后症状一模一样 ⇒ 它从来就不是原因。
`IrisScopePip` 与 `IrisBeginHandMixin` 的删除本身无害（§2.4 已证伪那条路），但**没修任何东西**。

真凶见 §2.6。

> **方法论**：这是本项目第 9 次「有理有据但错误」的归因，模式与前 8 次完全一致 ——
> 症状对上就下结论。真正定位它的动作只有一个：**把报错次数按会话拉成一张表**，
> 让「删除前/删除后」自己说话。

### 2.6 【真凶 · 已修】`colortex1` 前缀撞名 → 光影被整体禁用 → Voxy 陪葬

**因果链**（全部来自 08-21 的日志，非推导）：

1. `IrisShaderCreatorMixin` 用 `source.contains("colortex" + i)` 判断 pack 是否已声明该采样器；
2. Eclipse-Shader-Unstable 的 `dimensions/all_translucent.fsh` 声明了
   `colortex11`/`12`/`13`/`14`，**没有 `colortex1`**。
   `"colortex11".contains("colortex1")` 为真 ⇒ 我们**跳过**了 `colortex1` 的补声明；
3. 而探针网格**无条件**引用 `colortex0..7`（GLSL 两个分支都要编译，运行期闸门救不了）；
4. `terrain_translucent: error C1503: undefined variable "colortex1"`；
5. Iris：`Failed to create shader rendering pipeline, disabling shaders!`；
6. Iris 管线没建起来 ⇒ Voxy 的 `CAPTURED_VIEWPORT_PARAMETERS` 从没被 apply ⇒
   `Viewport` 恒为 0×0 ⇒ `renderOpaque` 每帧打
   `Viewport width or height was zero, this is bad bad bad, exiting frame`。

**判据（值得记住的取证手法）**：`VoxyRenderSystem` 里那句话有**两个**调用点，
文案差一个后缀。日志里 `exiting frame` 变体 4074 次、`setupViewport` 变体 **0 次** ——
「从没被建立」而不是「被建立成了 0」，一眼就把 Voxy 摘成了下游受害者。

**修法**（两处，均在 `IrisShaderCreatorMixin`）：

1. 判定改成整词匹配 `\bcolortexN\b`，`colortex11` 不再冒充 `colortex1`；
2. 探针网格改成**链接期**生成。关着时只声明并引用 `colortex0`，
   炸包面缩到最小；开着时才声明 `colortex0..7`。

`./gradlew build` 通过（04:1x）。

> 光影路径现在**只剩 shader 注入**一条，不含任何裸 GL 状态改动。

---

## 2.7 镜内清晰度：上限在哪、能怎么提

### 2.7.1 硬上限（先认清这条，再谈优化）

镜内画面是主画面中心那一块放大来的，所以

```
镜内可用的真实像素 = 屏幕像素 ÷ 镜内放大倍数
```

8 倍镜就是只有 1/8 的像素铺满整个镜片。这是**信息量**上限，不是算法问题 ——
锐化、双三次重建都只能改善<b>主观</b>锐度，**变不出真实细节**。
（当前已实装：Catmull-Rom 双三次重建 + 按倍率加权的钝化蒙版，基本到顶了。）

### 2.7.2 真正能增加像素的三条路

| 办法 | 镜内像素 | 代价 | 状态 |
|---|---|---|---|
| **① 二次渲染**（`ScopePipRerender=true`） | **原生，无上限** | 每帧多跑一遍世界渲染 | ✅ 无光影下可用；**光影下被硬性拦下**（一帧两次驱动 Iris 会搞乱它的时域/乒乓缓冲，整屏噪点） |
| **② 倍率拆分**（`ScopePipWorldZoomShare`，本轮新增） | **×Z^share** | 镜外也放大 Z^share 倍 | ✅ 两条路都可用 |
| **③ 提高整体渲染分辨率** | 按比例 | 全局性能 | 玩家侧的事（更高显示分辨率／渲染缩放类 mod），本 mod 不介入 |

### 2.7.3 倍率拆分怎么算的

倍率是**相乘**的，所以按指数分配：

```
世界 W = Z^share      镜内 P = Z^(1-share)      W × P ≡ Z（恒等，与 share 无关）
```

镜内只需放大 `P`，于是它能用的真实像素**多 W 倍**。

| share | 世界 W | 镜内 P | 镜内真实像素 | 说明 |
|---|---|---|---|---|
| `0.0` | 1.00 | Z | ×1 | 纯 PIP，镜外 1×（最软） |
| `0.5` | √Z | √Z | ×√Z | 对半分 |
| `1.0` | Z | 1.00 | ×Z | 整屏变焦（最锐，等于关掉 PIP） |

以 8× 镜为例：`share=0.5` → 世界 2.83×、镜内 2.83×，镜内真实像素是纯 PIP 的 **2.83 倍**。

**默认 `0.0` 与改动前逐位等价**：`W=1` ⇒ `magnificationToFov(1, fov) == fov`，
合成侧 `Z/1 == Z`，两边都不变。

> ### ⚠️ 第一版用的是「绝对上限」`ScopePipMaxWorldZoom`，已废弃 —— 别再改回去
>
> 那个设计有个致命缺陷：`W` 必须被 `Z` 夹住（世界放大不能超过总倍率），
> 于是**任何 ≥ Z 的取值都得到 `W = Z` ⇒ 镜内倍率 `Z/W = 1` ⇒ 镜内彻底不放大**，
> PIP 名存实亡。而那个临界点<b>就是瞄具倍率本身</b>，换把枪就换个位置。
>
> 玩家实测直接撞上：「超过 3.0 就没有放大了」—— 那把镜正好 3×，
> 而配置上限是 8.0，也就是 3.0~8.0 整段是行为相同、且功能已被关掉的**死区**。
>
> 按比例分配没有这个问题：`[0,1]` 上每一点都有意义、都不同，
> 且**与瞄具倍率无关**（2× 镜和 8× 镜上同一个 share 表现一致）。

实装要点（改这块必须两边同时改，否则镜内外放大程度会打架）：

- `CameraSetupEvent`：PIP 生效时不再「完全不动 FOV」，而是按 `1+(W-1)·progress` 放大；
- `ScopePipRenderer`：两个 `runComposite(...)` 调用点都**除掉**世界已放大的那一份。
  两者相乘恒等于总倍率，这是这套拆分的不变式。
- **二次渲染模式下 `worldZoomTarget()` 恒返回 1** —— 那条路镜内本来就是原生像素，
  放大世界只会白白牺牲镜外画质、换不到任何东西。

### 2.7.4 给玩家的建议

- **无光影**：开 `ScopePipRerender=true`，镜内原生分辨率，不需要动 ②。
- **开光影**：只能走屏幕空间那条，高倍镜偏软是必然的。
  嫌软就把 `ScopePipWorldZoomShare` 调到 `0.3`～`0.5`（镜内真实像素约 ×1.5～×2.8）；
  完全不在意镜外跟着放大就调 `1.0`，那等于回到整屏变焦（最清晰，但没有 PIP 效果）。

---

## 2.8 开镜晃动强度（`AimingSwayIntensity`）

### 2.8.1 晃动是什么

不是新加的效果，而是本来就有的「枪跟不上视角转动」的滞后量：

```java
xRot = player.getViewXRot(partialTick) - lerp(partialTick, player.xBobO, player.xBob);
```

`getViewXRot` 是当前朝向，`xBob/yBob` 是 vanilla 维护的平滑滞后值，两者之差就是
「刚才甩了多少」。这个差值驱动 6 个分量：整模型 pitch/yaw 反向旋转各一、
`rootNode` 的 X/Y 偏移各一、`additionalQuaternion` 的 pitch/yaw 各一。

### 2.8.2 为什么要给开镜单独加一档

原实现对腰射与开镜**一视同仁**。但开镜后视野被瞄具收窄，PIP 更是把镜内又放大了 Z 倍
—— 同样的角度抖动在镜内被放大成同样倍数的位移。现实里高倍镜正是「越放大越难稳住」，
而原来的镜内反而显得过于稳定。

按开镜进度插值（**不是**开镜就切换）：`scale = lerp(aimingProgress, 1.0, intensity)`。
用插值是为了避免抬镜那一瞬幅度突然跳一下 —— 那种跳变比晃动本身更容易被察觉。

| intensity | 腰射 | 满开镜 | 满开镜时最大旋转 / 位移 |
|---|---|---|---|
| `0.0` | ×1 | ×0 | 0.00° / 0.0000 |
| `1.0` | ×1 | ×1 | 1.25° / 0.0521（**与改动前逐位一致**） |
| `1.5`（默认） | ×1 | ×1.5 | 1.88° / 0.0781 |
| `3.0` | ×1 | ×3 | 3.75° / 0.1563 |

**腰射手感在任何取值下都不变** —— 进度 0 时插值恒为 1。

### 2.8.3 两处必须同时改

同一套公式在代码里有**两份拷贝**：

| 文件 | 说明 |
|---|---|
| `client/renderer/item/GunItemRendererWrapper.java` | **枪械实际走的那一份** |
| `client/renderer/item/AnimateGeoItemRenderer.java` | 父类的通用实现 |

只改一处的表现是「有的枪改了有的没改」，极难定位。
缩放系数统一由父类的 `aimingSwayScale()` 提供，避免两边算法漂移。

> `tanh` 饱和限幅保持在缩放**之前**：它防的是快速转身时枪飞出画面，
> 那道保护必须先生效，缩放只放大限幅后的结果。上表的「最大位移」列就是限幅后的上界。

> lrtactical 的近战 / 投掷物渲染器里也有同样的 sway 公式，但**刻意不动** ——
> 它们没有瞄具，开镜进度对它们没有意义。

---

## 2.9 【08:2x 已修】`ScopePipDebugTrace` 会把显存撑爆 —— 一个诊断开关引发的崩溃

### 2.9.1 症状

四处跑图约 90 秒后崩溃：

```
com.mojang.blaze3d.GpuOutOfMemoryException: Could not allocate buffer of 88106400
    at ...sodium...GlBufferArena.resize(GlBufferArena.java:66)
    at ...RenderSectionManager.processChunkBuildResults(...)
```

**在静止的小测试区完全不复现** —— 只有真正跑图时才炸。
（也正因如此一开始被误判成「显存泄漏」，查了半天分配点，其实一处都没漏。）

### 2.9.2 真因：预算永远扣不掉，诊断整局常驻

`ScopePipTrace` 本来设计成「只采 3 帧就收摊」：

```java
enabled()  =  配置为真  &&  framesTraced < FRAME_BUDGET
framesTraced++ 只发生在 sawScopePass 为真的帧
sawScopePass 只由 mark("SCOPE-PASS BEGIN") 置位
mark("SCOPE-PASS BEGIN") 只在 renderScopeView（二次渲染）里发出
```

而**二次渲染在光影下是被硬性拦下的**（§2.4）。于是：

> 光影下 `sawScopePass` 永远为 false ⇒ `framesTraced` 永远是 0 ⇒
> `enabled()` 永远为真 ⇒ **整局游戏每帧都在武装状态**。

代价是每帧最多 400 次 `StackWalker.walk()`，而它挂在 `mainRenderTarget()` 上 ——
Sodium 地形、Voxy、帧图导入全都要调这个方法。

### 2.9.3 从「渲染线程慢」到「显存爆掉」的那一步

这一步是本案最不直观的地方，值得记住：

1. 渲染线程被 StackWalker 拖垮；
2. 区块构建在**工作线程**上照常进行，不受影响 —— 于是构建结果越堆越多；
3. 渲染线程终于轮到 `processChunkBuildResults` 时，要**一次性**上传巨量结果；
4. Sodium 的 `GlBufferArena.resize` 于是要一口气申请 88 MB，
   而扩容期间**新旧缓冲并存**（≈2 倍）；
5. 显存给不出 ⇒ `GpuOutOfMemoryException`。

所以「跑图才炸」是必然的：**只有跑图才有大量区块构建**。
静止时构建队列是空的，渲染线程再慢也堆不出东西来。

### 2.9.4 修法（两条，缺一不可）

1. **绝对上限** `ARMED_FRAME_LIMIT = 600`：无论有没有采到样本，
   武装满 600 帧就永久收摊并打一行说明。
   计数在 `beginFrame()` 里**无条件**累加 —— 只要这一帧武装了就算数。
2. **让光影那条路也能扣预算**：`compositeAfterLevelUnderShaders()` 发出
   `PIP COMPOSITE`，`mark()` 认它与 `SCOPE-PASS BEGIN` 等价。

> ### 教训（比这个 bug 本身值钱）
>
> **诊断开关的收摊条件，绝不能依赖「被诊断的那条路」自己发信号。**
> 那条路不跑，恰恰是你要诊断的情形 —— 于是最需要它收摊的时候它永不收摊。
> 任何自限诊断都必须带一个**与业务逻辑完全无关**的绝对上限。

---

## 3. 配置键（全部在 `config/tacz-client.toml` 的 `[render]` 下）

| 键 | 默认 | 说明 |
|---|---|---|
| `ScopePipEnable` | `false` | PIP 总开关 |
| `ScopePipRerender` | `false` | 二次渲染（原生分辨率）。光影下自动忽略 |
| `ScopePipAllowShaderPacks` | `false` | 允许光影下跑 PIP（目前不出图，见 §2） |
| `ScopePipSharpness` | `0.5` | 重投影模式的锐化上限（只提升主观锐度） |
| `ScopePipWorldZoomShare` | `0.0` | 瞄具倍率里由**世界**承担的比例，用来换镜内真实分辨率。见 §2.7。`0.0` = 与改动前逐位等价；`1.0` = 等于关掉 PIP |
| `AimingSwayIntensity` | `1.5` | 开镜时持枪晃动的强度倍数。见 §2.8。腰射永不受影响；`1.0` = 与改动前逐位一致；`0.0` = 满开镜完全不晃 |
| `ScopePipMinAimingProgress` | `0.05` | 低于此开镜进度不做 PIP |
| `ScopePipDebugPaintLens` | `false` | 诊断：把合成实际覆盖到的区域涂成纯品红（整屏变色 = 合成没被掩码约束住） |
| `ScopePipDebugNoComposite` | `false` | 诊断：跑 PIP 但不合成 |
| `ScopePipDebugTrace` | `false` | 诊断：打印渲染目标解析顺序。**开销极大**，正常游玩务必保持关闭，见 §2.9 |

**推荐给玩家的组合**：`ScopePipEnable=true`、`ScopePipRerender=true`、其余默认。

---

## 4. 下一步（按优先级）

0. **先确认光影没被我们弄挂**（§2.6 修完后的第一件事）：日志里**不应**再出现
   `undefined variable "colortex1"` 或 `disabling shaders!`，且**应当**出现
   `Found fine program match for tacz:pipeline/...`。Voxy 的刷屏会随之自己消失。
1. **跑 §2.3 的孔径网格探针**：`ScopePipEnable/AllowShaderPacks/DebugPaintLens` 全开，
   **改完配置要重载 shaderpack（R 键）**，日志确认 `colortex probe grid: ON`，
   用 **3.25× 或 4.25× 的真倍镜**（红点/全息走 sight 通道，不产掩码，恒不出 PIP），
   截图看哪一格有画面 → 那就是要在模式 3 里采样的 colortex。
2. 把模式 3 的采样源换成第 1 步找到的那张，验证放大是否出现。
3. 若出现但颜色不对：手部输出会被 pack 再光照一次，需要补偿
   （已着色的值当成 albedo 再打光）。属可控的小问题。
4. `tacz$findFragmentOutput` 目前认三种写法（`layout(location=0) out vec4`、
   裸 `out vec4`、`gl_FragData[0]`）。**认不出就整段不注入** —— 宁可没功能，
   也不能塞进编译不过的代码把整个 shaderpack 弄挂。换 pack 测试时留意这条。

---

## 5. 工作方式上的教训（这条比代码值钱）

整晚**至少 8 次**「有理有据但错误」的归因。共同模式：
**拿到一个事实 → 修那个事实 → 后面又冒出一个新事实**。
真正推动进展的只有两类动作：

1. **读实际字节码**（Sodium / Iris / Voxy 的 jar，只读解包到临时目录）；
2. **加诊断把猜测变成读数** —— 帧序 trace、`gate ->` 闸门日志、涂色探针、孔径网格。

反过来，纯靠「症状像什么」的推断**命中率接近零**。
另外有一次诊断日志本身把病症整形成了正常样（两秒限流把逐帧抖动打成整齐间隔），
**诊断日志的形状会骗人，限流要小心**。

### 环境上的坑
- 用户 `mods/` 里曾同时存在 R1 与 R2 两个 TaCZ jar，Fabric 选了 **R1**，
  导致「改了没反应」且配置被旧 spec 重写、PIP 键全丢。
  **每次实测前先确认日志里 `- tacz 1.1.8+fabric.26.2.R2`。**
- 用户实例：Sodium 0.9.1 + Iris 1.11.2 + Voxy 0.2.18 + ImmediatelyFast + Physics Mod。
  任何「所有绘制都经过某个原版方法」的假设都要先对这几个验证。

---

## 6. 约束（用户明确要求）

- **只允许改本仓库的文件。** 其他 mod 的 jar、shaderpack 的 zip、
  `.minecraft` 下的任何东西**一律不得写入**。
- 运行时反射/改内存状态是**允许**的（`SodiumCompat` 就是这么干的）；
  改磁盘上的字节**不允许**。
- shader 注入是**内存内**改字符串，pack 的 zip 从未被打开写入 —— 符合上述约束。
