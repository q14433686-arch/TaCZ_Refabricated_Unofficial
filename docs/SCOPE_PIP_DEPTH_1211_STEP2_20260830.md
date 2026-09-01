> **【26.1.2 移植横幅 / 2026-09-01】** 本文档是 1211 线（`coord-01a05759`）的原始实现与实机确认剧本，
> 随 Scope PIP 深度线移植带入本分支，作为**验收脚本**使用。文中所有实机结论（包括 Step 2 的 PASS、
> Step 3 的各项确认项）都是 1211 线的记录；本分支（26.1.2）的移植**编译已过（CI），运行期行为全部未验证**，
> 需按各节剧本在实机重跑后再标 PASS。移植适配差异（API 替换、注入点签名）见
> `docs/SCOPE_PIP_PORT_26_1_2_20260901.md`。默认全部关闭。

# 1.21.11 深度版镜内画中画（PIP）原型 — Step 2: 只涂纯品红的全屏判据

日期: 2026-08-30
分支: `arena/01a0518d-tacz-refabricated-unofficial` (1.21.11, depth-aperture 架构)
前置: Step 1（`ScopeDepthCopyState` 访问器）已实现，等你实机确认后再动 Step 3。
本文档是 Step 2 的源码级实现与实机确认步骤。

## 0. 本步目标

写一个「全屏 pass，只用孔径判据涂纯品红」的诊断：
- 采样 `tacz_WorldDepthSampler` 与 `tacz_ApertureDepthSampler`；
- 判据与 `scope_reticle_mask.fsh` 逐行一致：`if (!(ad < wd - 1.0e-6)) discard;`;
- 命中孔径的像素输出 `vec4(1,0,1,1)`；
- 实机看「只有瞄具镜片被涂成品红」，即认为孔径判据 + 深度拷贝链路通了。

**默认关闭。** 只有 JVM 启动参数 `-Dtacz.scope.pip.debug.paint=true` 才会启用。

## 1. 改了哪些文件

| 文件 | 性质 |
|---|---|
| `src/main/java/.../ScopePipDepthDebug.java` | 新增：诊断 pass（懒加载管线 + 裸 GL 深度纹理→GpuTextureView 包装 + 帧末绘制） |
| `src/main/java/.../GameRendererMixin.java` | 在 `renderItemInHand` RETURN 调 `ScopePipDepthDebug.renderAfterHand(...)` |
| `src/main/resources/assets/tacz/shaders/core/scope_pip_debug.vsh` | 新增：gl_VertexID 全屏三角形 |
| `src/main/resources/assets/tacz/shaders/core/scope_pip_debug.fsh` | 新增：孔径判据 + 品红输出 |
| `docs/SCOPE_PIP_DEPTH_1211_STEP1_20260830.md` | 已存在（Step 1） |

未改：`ScopeDepthCopyState` 的 mask/restore 逻辑、`ScopeFinalOverlayState` 的 flush 时机、
`ScopeLateReticleState`、任何配置项。Step 2 在正常模式下对画面零影响。

## 2. 取证

### 2.1 1.21.11 的 RenderPass / GpuTextureView API（Yarn docs 1.21.11 确认）

- `RenderPass.bindTexture(String, @Nullable GpuTextureView, @Nullable GpuSampler)`。
- `GpuDevice.createTextureView(GpuTexture)` / `(GpuTexture,int,int)` 存在，
  但**只能接收由 `createTexture(...)` 创建的 `GpuTexture`**。
- `GlTexture` 在 1.21.11 有 **protected** 构造器
  `GlTexture(int usage, String label, TextureFormat format, int width, int height, int depthOrLayers, int mipLevels, int glId)`。
- `GlTextureView` 有 **protected** 构造器 `GlTextureView(GlTexture, int baseMipLevel, int mipLevels)`。
- `RenderSystem.getSamplerCache()` 存在；官方 Mojang 映射下取最近邻采样器用
  `SamplerCache.getClampToEdge(FilterMode.NEAREST)`（Yarn 名 `SamplerCache.get(FilterMode)`）。
  完整签名是 `getSampler(AddressMode, AddressMode, FilterMode, FilterMode, boolean)`。
- `CommandEncoder.createRenderPass(Supplier<String>, GpuTextureView, OptionalInt)` 存在。
- `RenderPipeline.Builder` 是**扁平 setter**：`withVertexShader/withFragmentShader/withVertexFormat/withSampler/withCull/withoutBlend/withColorWrite`；
  没有 26.2 的 `ColorTargetState` / `DepthStencilState` 聚合对象。

### 2.2 本分支私有深度纹理是裸 GL，不是 GpuTexture

`ScopeDepthCopyState.DepthTextureTarget` 用 `glGenTextures` + `glTexImage2D` + `glFramebufferTexture`，
没有 `GpuTexture` 对象。**没有公开 API 能把已有 GL int 包成 `GpuTexture`。**
因此 Step 2 采用「子类借用」：`ImportedDepthTexture extends GlTexture`、`ImportedDepthTextureView extends GlTextureView`，
两者 `close()` 都故意 no-op，明确不拥有私有深度纹理，绝不释放 `ScopeDepthCopyState` 的资源。

### 2.3 为什么不用 `pass.bindTexture` 的前置条件绕开问题

26.2 的 `ScopeMaskTextureHandle` 用 `AbstractTexture` + `TextureManager.register` 走 RenderType/vanilla 绑定；
这正是你交接里说「准星那条路绑进 RenderType 走 vanilla 绑定」的那条路。Step 2 要的是我们自己的
裸 `RenderPass`，所以不能用 TextureManager 方案，只能在本类里造 `GpuTextureView`。

### 2.4 时序

用手持后置（与 26.2 相反）。挂在 `GameRenderer#renderItemInHand` RETURN：
- 该时刻目镜已光栅化、`APERTURE_COPY` 已完成；
- 该时刻主 target 已有手持画面；
- 随后才有 GUI/HUD，所以品红诊断能直接盖在画面上被看到。

Iris 下 `renderItemInHand` 被 HandRenderer 绕开（既有注释 + `IrisHandRendererReticlePassMixin`），
而 Iris depthtex2 桥接未证实，所以 Step 2 **显式跳过 shader pack**，只跑 vanilla。

## 3. 自审（源码级）

- **管线不声明深度**：在 `RenderPipeline.Builder` 上刻意**不调用** `withDepthTestFunction` / `withDepthWrite`，
  让深度状态保持在 Builder 的 Optional 默认值，从而不向 `createRenderPass` 要深度附件。
  这与 26.2 踩过的「不要 DepthStencilState」一致——1.21.11 没有该聚合对象，等价表达就是不设深度 setter。
- **不写 alpha**：直接输出 `fragColor.a = 1.0`。诊断 stage 无后续用 alpha 的必要；管线 `withColorWrite(true)`
  只表示写颜色。
- **不开 scissor**：Step 2 是验收「判据本身」，故意不加屏幕包围盒剪裁，避免 sccissor 掩盖判据错误。
  若整屏被涂品红，说明判据/绑定失败，这正是要看的。
- **失败即停用**：`pipeline()`、`renderAfterHand` 都在 try/catch 里；一旦异常 `failed=true`，之后每帧直接返回，
  不影响普通渲染。Step 2 不接入任何整屏变焦 / 覆盖 flush 逻辑。
- **生命周期**：`ImportedDepthTexture.close()` 与 `ImportedDepthTextureView.close()` 均为 no-op；
  子类不触碰 superclass 的 `closed`/`isClosed()`，只依赖 `AutoCloseable.close()` 这个稳定方法名，
  从而不依赖官方映射下私有字段/方法的具体拼写，且绝不释放私有深度拷贝。

## 4. 未验证项（必须实机确认）

0. **官方映射下 `GlTexture` / `GlTextureView` 的包路径 — 已修复。**
   你首次 `build` 的编译错误正是 Yarn 包不存在：`net.minecraft.client.texture.*`。
   依据官方 Mojang 映射（`com.mojang.blaze3d.opengl` 包内有 `GlCommandEncoder/GlRenderPass/GlProgram`，
   Mojang 的 `client.txt` 亦把 `GlTexture` 列为该包），本实现已把两个 import 改为
   ```java
   import com.mojang.blaze3d.opengl.GlTexture;
   import com.mojang.blaze3d.opengl.GlTextureView;
   ```
   同时子类 `close()` 统一 no-op、不再引用 superclass 的 `closed`/`isClosed`，避免映射拼写差异。
1. **`SamplerCache.get(FilterMode)` 在官方映射下不存在 — 已修复。**
   第二次编译只剩这两处：“method get(FilterMode) in class SamplerCache”。
   官方 Mojang 命名为 `SamplerCache.getClampToEdge(FilterMode)`（见 §2.1），
   已把两处 `bindTexture` 的采样器改为 `getClampToEdge(FilterMode.NEAREST)`。
2. **`RenderPipelines.ENTITY_OUTLINE_BLIT` 是否存在 — 两次编译均未报错，已确认存在。**
   备选仍为 `GUI_TEXTURED`（本分支已知存在），仅在需要退回时启用。
3. **`ImportedDepthTexture` + `ImportedDepthTextureView` 能否被 `GlCommandEncoder` 正确绑定。**
   构造器是 protected（子类可用），但实际 `bindTexture` 走的是 `view.texture().getGlId()`；
   需要实机确认。
4. **Packed depth-stencil 标记为 `TextureFormat.DEPTH32` 是否影响采样。**
   私有深度拷贝内部格式可能是 `GL_DEPTH24_STENCIL8`，而包装层诚实报告 `DEPTH32`。
   采样 `.r` 理论上仍取 depth 分量；若实机整屏品红/全黑/报错，则需反馈。
5. **裸 `RenderPass` 下 `depthtex2`（Iris）不可绑**：Step 2 跳过 Iris，未解决。
6. **`core/screenquad` 在 1.21.11 存在且无 snippet 也能编译**：本 step 直接引用 `minecraft:core/screenquad`，
   未在本地编译验证（沙箱无 JDK / merged jar）。

## 5. 实机确认步骤

> **注意：品红诊断不是 TACZ 配置文件里的选项。** `tacz-client` 配置里没有也不可能出现
> `ScopeDebugPaint` 或类似字段；它是 JVM 属性 `-Dtacz.scope.pip.debug.paint=true`。
> 没在配置文件里找到是正常的。

1. 编译并运行 1.21.11 客户端，**不带**启动参数：
   - 确认准星、镜身、镜外世界与改动前逐帧一致，无品红、无日志错误。
2. 再带参数启动（二选一）：

   - **源码/开发环境（本仓库）**：先确保 `gradlew build` 通过，然后
     ```bat
     gradlew runClient -PtaczScopeDebug=true
     ```
     已把该 JVM 属性接入 `build.gradle` 的 `loom.runs.client`（`vmArg`）。
   - **打包后的模组 / 第三方启动器**：在启动器的 JVM/虚拟机参数里加
     ```
     -Dtacz.scope.pip.debug.paint=true
     ```
     不要把该参数写进 TACZ 配置文件。

3. 进游戏：**不要开任何光影/着色器包**（Iris 会跳过此诊断），开一个 6× / 8× 倍镜并抬镜到满开镜；
   - 观察：**目镜镜片内应为纯品红**；
   - 镜片外（镜身、视野边缘、屏幕四周）不得出现品红；
   - 观察是否能看到「只有孔径被涂」的准确形状（圆形/椭圆，边缘与镜身孔径一致）。
3. 若整屏品红 → 判定/绑定失败，回传日志（`[TACZ Scope] Step2 ...`）。
4. 若镜片没被涂色 → `available()` 为 false（无备份）或纹理绑定失败，回传日志。

## 6. 回归复测清单（Step 2 在 debug 开启/关闭两种状态下）

### 关闭（默认）
- [ ] 无品红，普通开镜画面与 Step 1 完全一致。
- [ ] `ScopeFinalOverlayState` 未改，Iris TAIL 仍正常 flush。
- [ ] `ScopeLateReticleState` 旧路径未受影响。
- [ ] 控制台无新增 `[TACZ Scope] Step2 ...` 日志。

### 开启（仅 vanilla / 无光影）
- [ ] 只有孔径是品红，镜外无泄漏。
- [ ] 关闭 debug 后立刻恢复正常，无残留。
- [ ] 开/收镜过程中品红形状与正在瞄准的目镜孔径同步。

### Iris（无论 debug 开关）
- [ ] Step 2 显式跳过 Iris，日志出现一次 `skipped under a shader pack`（仅 debug 开启时）。
- [ ] 不开 debug 时 Iris 路径零改动。
