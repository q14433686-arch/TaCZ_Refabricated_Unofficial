# 26.2 → Fabric 1.21.11：TaCZ 1.2.0 全量移植验收记录

这是一份**未完成**的移植记录，不是发布说明。比较基线：目标 `aac343af891858589eaea69bbefc413c0f2b1977`，供体 `307f063a8828894917302b2b73cbc104a951dc4e`。以这两个提交的源码、资源及目标版本 API 为准，不以供体的通过日志代替目标验证。

- 目标保持 Minecraft 1.21.11、Java 21、Fabric API 0.141.6、FCAP 21.11.1 与 Loom remap。`1.2.0+fabric.1.21.11.R1` 是工作分支源码版本，不代表已有可安装的 1.2.0 发布包。
- `DEFAULT_PACK_120.tsv` 的 4,176 条文件 SHA-256 均与当前内置包一致，包目录没有未登录的文件。台配方、进度、脚本、材质与模型是否实际解码和可用，仍须在目标游戏中确认。
- 初次导入后的目标编译失败。已恢复目标网格选项与 FCAP 落盘桥，适配了若干存储、菜单、GUI、渲染材质 API。编译报告 `e0e696f`（Actions `44686575249`）中的 `commit:` 对得上该次源码；其中仍有世界渲染提交、热成像、REI/JEI 和其他 API 缺口。下一次 `7da553f` 检查（`44686911662`）在三个误改的矩阵调用处先遇到语法错误，本轮后续提交已更正，尚不等于 Java 类型检查通过。应读取与待测源码 SHA 相符的报告。

## 用 26.2 同型方式验收，但实际运行 1.21.11

本分支的 `game-smoke-12111.yml` 在构建通过后运行 Fabric GameTest。开发环境可在 Java 21 下运行：

```sh
./gradlew -PtaczGameTests runGameTest --console=plain --no-daemon
LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null \
  xvfb-run -a ./gradlew -PtaczGameTests runClientGameTest --console=plain --no-daemon
```

- Dedicated GameTest：加载并检查默认目录、工作台材料结算与拒绝路径、弹药箱库存事务及升级保留组件。看 `build/run/gameTest/logs/latest.log`，不能只看进程退出码。
- Xvfb 客户端 GameTest：建真实单人世界，等新增索引同步，再核对主手物品并截屏。看 `build/run/clientGameTest/logs/latest.log` 和截图。截图是实机记录，不是像素自动判定，也不是独立服务器联机证据。
- 这两个测试入口是 Loom 开发运行，**不替代**用 remapped JAR 在独立 Fabric 客服端上验证。后者需要目标版本的依赖组合，并分别记录新版分片协议、旧协议回退、重载/退服、第三方枪包转换和导出、实际玩法、JEI/REI、普通渲染和 Iris 的 PiP/准星/热成像及粒子。

## 本轮结论（2026-11-20）：目标平台编译与双入口冒烟均通过

证据链（都在本分支的 Actions 里，报告文件与源码 SHA 对齐）：

| 检查 | 结果 | 位置 |
| --- | --- | --- |
| `compileJava` | 通过（9 warning，0 error） | PR #126 / `compile-check`，日志 `build-reports/compile-java.log`（`commit:` 行即被测 SHA） |
| `./gradlew build`（含 remapJar） | 通过 | `build` run `44782235664` |
| Dedicated GameTest + 回执 | 通过（`PORT_CATALOG` / `PORT_SETTLEMENT` / `PORT_STORAGE` 三条都 grep 到） | 同上，job `game-smoke / game (runGameTest)` |
| Xvfb 客户端 GameTest + 回执 | 通过（`PORT_12111_CLIENT: live world` + 截图存在） | 同上，job `game-smoke / game (runClientGameTest)` |

排查过程中真实出现过的两类失败，都是 26.2 源码直接搬过来、目标 API 已经不存在：

1. **编译期**（第一轮 38 条）：`KeyMapping#matches(InputConstants$Key)`、`Player#sendSystemMessage`、
   `Screens#getWidgets`、`CameraRenderState#projectionMatrix`、`SpecialModelRenderer#submit` 少一个
   `ItemDisplayContext`、`ItemModel.Unbaked#bake` 只收一个参数、`PolyMeshGpuRenderer#bakeBone/tryReserveBake`
   多两个参数等。逐条按 1.21.11 等价物改写，未删调用点。
2. **运行期**（dedicated 绿、客户端红）：`MinecraftMixin` 注入 `Minecraft#handleGlobalKeyPress`
   与 `KeyMapping#matches(InputConstants$Key)`，这两个在 1.21.11 都不存在（按键路由走
   `KeyMapping` 的静态 `click`/`set`），默认 `require=1` 直接在客户端启动时 apply 失败。
   1.21.11 的社交界面键是 `Options#keySocialInteractions`（默认 P），与枪械交互键 O 不冲突，
   因此这条「Friends 抢键」裁决整段删除，而不是换个注入点硬塞。

### 本轮实际搬过来的 1.2.0 行为

- 渲染提交链路：`ScopeRenderTypes`（孔径/可见准星/发光/臂裁切/终局与 late 家族）承接 26.2 的
  `ScopeBodyRenderTypes`；`ScopePipRenderState` / `ScopePipRerender` 承接 `ScopePipRenderer`
  的静态面（`frameNumber` 直接复用 `ScopeDepthCopyState` 的帧计数，不另起第二个计数器）。
- 世界事件：Fabric 0.141.6 的 `WorldRenderEvents`（`BEFORE_ENTITIES` / `AFTER_ENTITIES` /
  `AFTER_BLOCK_OUTLINE_EXTRACTION` / `BEFORE_BLOCK_OUTLINE`）取代 26.2 的
  `LevelRenderEvents` / `LevelExtractionEvents`；枪口闪光的面光源走
  `WorldFlashFrame.beginWorld/endWorld`，不再需要注入 `LevelRenderer#submitFeatures`。
- 关卡投影镜像：1.21.11 的 `CameraRenderState` 没有 `projectionMatrix`，改为在
  `PerspectiveProjectionMatrixBuffer#getBuffer` 处镜像一份（`ScopeProjection.levelProjectionM11()`），
  手部投影仍单独镜像 `CachedPerspectiveProjectionMatrixBuffer`。
- 1.2.0 杂项：测距仪落点预测框、弹壳、瞄具发光材质、显示架格子描边（`ShapeRenderer` 走
  `submitCustomGeometry`）、JEI 枪械工作台配方转移、包装填筛选、视角微调屏、热键分发。
- mixin 注册表补齐：`CreativeModeInventoryScreenMixin`（`extractRenderState` → `render`）、
  `DetonationLightMixin`、`ThermalEntityMixin`（只剩枪口归属那半边）、
  `GameRendererProjectionAccessor`、`CachedPerspectiveProjectionMixin`、`PerspectiveProjectionMixin`、
  三个 `RenderSetup`/`RenderType`/`TextureBinding` accessor；作废的
  `LevelRendererReceiverLightMixin` 与 `ScopeProjectionUploadMixin` 已删。

### 仍未搬（明确记为缺口，不算通过）

- **热成像**：26.2 的 `ThermalBodyNodesMixin` + `ScopeThermalCapture`/`ScopeThermalMaterial`/
  `ThermalSceneDepth` 依赖 `SimpleFeatureRenderPhase`/`TranslucentSubmit` 那套 phase 拆分与
  `GpuFormat`/`CompareOp`/`DepthStencilState`/`BindGroupLayout` 的 GPU 描述，
  1.21.11 没有对应 API，需要按 `RenderPipelines` + `RenderSetup` 重写，本轮下线。
- **掩码纹理族**：`ScopeMaskRenderer`/`ScopeMaskTarget`/`ScopeMaskTextureHandle`/`ScopeMaskGeometry`/
  `ScopeLensFrame`/`ScopeMaskDebugOverlay` 属 26.2 的掩码纹理路线；本线目镜遮罩走深度孔径
  （`ScopeDepthCopyState` + `ScopeRenderTypes`），故不搬，HUD 调试层也随之取消
  （`RenderConfig.SCOPE_MASK_DEBUG` 在本线只剩日志语义）。
- **KubeJS / OptiFine / PlayerAnimator(旧 KosmX 实现) 兼容层**：26.2 有 18+5+2 个源文件，
  本线 `build.gradle` 明确把这些包排除在产物外，等于本线从未声明对应依赖；
  要搬得先补依赖与版本对齐，属独立工作项。
- **AR / Iris 扩展着色器**：`compat/ar/ARCompatImpl`、`AcceleratedBeamRenderer`、
  `mixin/client/iris/IrisExtendedShaderMixin` / `IrisGlCommandEncoderMixin` / `IrisShaderCreatorMixin`
  依赖 26.2 的渲染管线注入点，本线 Iris 侧只有 `IrisDepthRestore`/`IrisScopeDimension` 等五个，
  属下一步。
- **REI**：`ReiPackRefresh` 现在能编译（`RoughlyEnoughItems-fabric` 只作 `modCompileOnly`），
  但未在真实 REI 21.11.816 下跑过一次重载，属待验证而非通过。

当前无本轮可安装 JAR 的发布结论；CI 产物 `TACZ-Refabricated-1.21.11-<sha>.jar` 是开发构建，
不等于已测试的 1.2.0 发行包。
