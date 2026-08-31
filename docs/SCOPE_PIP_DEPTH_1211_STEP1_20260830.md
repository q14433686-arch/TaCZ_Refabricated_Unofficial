> **【26.1.2 移植横幅 / 2026-09-01】** 本文档是 1211 线（`coord-01a05759`）的原始实现与实机确认剧本，
> 随 Scope PIP 深度线移植带入本分支，作为**验收脚本**使用。文中所有实机结论（包括 Step 2 的 PASS、
> Step 3 的各项确认项）都是 1211 线的记录；本分支（26.1.2）的移植**编译已过（CI），运行期行为全部未验证**，
> 需按各节剧本在实机重跑后再标 PASS。移植适配差异（API 替换、注入点签名）见
> `docs/SCOPE_PIP_PORT_26_1_2_20260901.md`。默认全部关闭。

# 1.21.11 深度版镜内画中画（PIP）原型 — Step 1: 只加访问器

日期: 2026-08-30
分支: `arena/01a0518d-tacz-refabricated-unofficial` (1.21.11, depth-aperture 架构)
目的: 按交接约定, 本分支不搬 26.2 的离屏掩码架构, 用深度信号做等价实现。

## 0. 本步做了什么

只给 `ScopeDepthCopyState` 增加两个**只读访问器**, 不改变任何运行时行为:

```java
public static DepthHandle worldDepthTarget()    // 对应 private WORLD_TARGET
public static DepthHandle apertureDepthTarget() // 对应 private APERTURE_TARGET
```

返回的 `ScopeDepthCopyState.DepthHandle` 是一个不可变快照, 携带:

- `textureId`   — 当前 private 深度纹理的 GL 纹理 ID
- `framebuffer` — 当前 private 深度 FBO (仅供诊断/未来裸 GL 路径)
- `width / height / internalFormat`
- `available`   — 是否已经至少有一次 BACKUP / APERTURE_COPY 成功分配并 blit

没有新增/删除任何常量、没有改动 `beforeDraw()` 分支、没有改动 FBO/纹理生命周期,
没有触碰 `ScopeFinalOverlayState` 的 flush 时机, 也没有改动 `ScopeLateReticleState`。

## 1. 取证结果

### 1.1 本分支现有的深度孔径信号

- `ScopeDepthCopyState` 的流程已由类注释与代码明确:
  `BACKUP → 目镜写近深度 → APERTURE_COPY → 镜身被深度裁出孔径 → RESTORE → MASK`。
- 判据是现成的: `scope_reticle_mask.fsh` 里
  `wd = texture(tacz_WorldDepthSampler, wUv).r;`
  `ad = texture(tacz_ApertureDepthSampler, aUv).r;`
  `if (!(ad < wd - 1.0e-6)) discard;`。
- `WORLD_TARGET` / `APERTURE_TARGET` 此前是 `private static final DepthTextureTarget`, 没有任何
  访问器给合成阶段使用。

### 1.2 26.2 的 PIP 语义 (只作为参考, 不搬实现)

26.2 分支 (Fabric/neoforge 26.2) 已有 `ScopePipTarget / ScopePipRenderer / scope_pip.fsh`:

- PIP 的目标: 镜外保持 1×, 镜内是放大的世界; `suppressesWorldFovZoom()` 让整屏 FOV 变焦让位。
- `scope_pip.fsh` 的重投影数学:
  `wideUV = center + (uv - center) / Z`, 倍率 = 瞄具倍率,
  Catmull-Rom 双三次重建 + 按倍率加权的 unsharp mask。
- 合成阶段在 26.2 里直接开一个 `RenderPass`, 采样「本帧世界拷贝」+「目镜掩码」,
  写主 target 的颜色, **不声明深度状态**。

本分支需把「目镜掩码」换成「世界深度 + 孔径深度」这两个采样器, 其余语义对齐。

### 1.3 本分支与 26.2 的 API 差异 (必须在原型里处理)

- **26.2 的 `RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)` +
  `withColorTargetState(...)` / `withBindGroupLayout(...)` 在 1.21.11 并不等价。**
  本分支 `ScopeRenderTypes.clonePipeline` 的注释/实现已确认: 1.21.11 的
  `RenderPipeline.Builder` 没有 `ColorTargetState` / `DepthStencilState` 聚合对象,
  使用的是扁平 setter (`withColorWrite`, `withDepthTestFunction`, `withDepthWrite`,
  `withDepthBias`)。合成管线必须按 1.21.11 的 Builder 面写。
- **本分支已有 `DepthTextureTarget` 是裸 GL 纹理 (`int textureId`), 不是 26.2 的
  `TextureTarget` / `GpuTextureView`。** 这是 Step 2 之前必须解决的绑定问题。

### 1.4 两个「未证实」项 (按用户清单逐条记录)

1. **深度纹理怎么绑进我们自己开的 RenderPass (要求 GpuTextureView)?**
   - 现状: `pass.bindTexture(name, view, sampler)` 需要 `GpuTextureView`;
     `ScopeDepthCopyState.DepthTextureTarget` 只有裸 `int textureId`。
   - 已看到 26.2 的 `ScopeMaskTextureHandle` 用 `AbstractTexture` 包装 + TextureManager
     注册, 但那走的是 **RenderType/vanilla 绑定**, 与我们 Step 2 要的裸 `RenderPass` 不符。
   - 1.21.11 (NeoForge primer / Yarn 1.21.6 文档) 显示 `GpuDevice.createTextureView(GpuTexture)`
     存在, 但需要一个已经由 `createTexture(...)` 创建的 `GpuTexture`; **没有任何公开 API
     "把已有 GL int 包成 GpuTexture/GpuTextureView"**。因此大概率要么把 `DepthTextureTarget`
     改造成 `GpuTexture` 目标 (行为更靠近 26.2, 但会动到现有 depth-copy 的 blit/FBO), 要么
     在合成阶段用裸 GL 绑定 + 手动 sampler uniform 绕开 `pass.bindTexture`。
   - **状态: 未证实 / 未实现。** 在 Step 2 开工前应由本步日志/调试把 "是否已有
     `GpuTextureView(int)` 转换入口" 确认掉, 或者直接选中"改造 `DepthTextureTarget`"。

2. **Iris 下世界深度用的是 `depthtex2` (IRIS_WORLD_DEPTH_UNIFORM), 能否在裸 pass 绑到?**
   - 现状: `ScopeDepthCopyState.begin(BACKUP)` 在 Iris 路径读到
     `IRIS_WORLD_DEPTH_UNIFORM = "depthtex2"` 且用 `useIrisPreHandDepth=true`;
     但是那是给 **RenderType 里的 vanilla shader program** 用的, 由 Iris 的 sampler 注入。
   - 我们自己用 `RenderPass` + tacz 管线时, Iris 不会替我们绑定 `depthtex2`;
     也没有任何证据表明 `depthtex2` 的 `GpuTextureView` 在 `finalizeLevelRendering` 之后
     还能直接取到。
   - **状态: 未证实 / 未实现。** 用户已给兜底: 先做不开光影路径, Iris 后置。

### 1.5 时序 (与 26.2 相反) 已确认

- 26.2: 掩码在阶段边界已画好 → 合成排在手持【之前】。
- 本分支: 孔径信号在手持那一遍里才产生 (目镜光栅化 → 拷深度) → 合成只能排在手持【之后】。
- 这会盖掉手持画进孔径里的准星/目镜框, 所以未来要改
  `ScopeFinalOverlayState.renderAfterFinalComposite()` 的调用策略让它在 PIP 合成之后也 flush。
- **本步没有触碰这个时序。** 现有 Iris 路径的对应关系:
  `IrisFinalScopeOverlayMixin` 在 `finalizeLevelRendering` TAIL 调
  `ScopeFinalOverlayState.renderAfterFinalComposite()`。

### 1.6 为什么没写任何 "PASS"

按交接规矩, 我们只在源码级做了 Step 1; Step 2 之后才是实机验证点。
本步没有实机验证结论, 因此文档不写 PASS。

## 2. 自审 (源码级)

- `worldDepthTarget()` / `apertureDepthTarget()` 都不调用 `ensure(...)`, 不 `glGen*`,
  不 `glBind*`, 不改变 `backupValid / maskValid / WORLD_TARGET / APERTURE_TARGET` 内部状态。
- `DepthHandle` 是 immutable record; `DepthTextureTarget.snapshot()` 只是读取既有字段。
- 新增方法均处于 `ScopeDepthCopyState` 的单例/静态路径中, 不占用 render-thread 之外的资源,
  没有引入锁、线程、分配循环。
- 没有改动 `ScopeFinalOverlayState` 的 flush, 也没有改动 `ScopeLateReticleState`,
  因此旧路径可作对照。
- **未见的问题:** 1.21.11 的 `RenderPipeline` 面是否与 26.2 兼容只有在 Step 2 写管线的
  时候才会暴露; 本步没有新增管线/着色器, 所以没有新增编译风险。
- `DepthHandle` 中 `available()` 覆盖了 record 自动生成的访问器, 语义是
  `available && textureId != 0`; 无副作用。

## 3. 未验证项 (后续步骤开工前必须确认)

1. **GpuTextureView 绑定:** 裸 GL 深度纹理能否被 `RenderPass.bindTexture` 使用。
2. **Iris depthtex2:** 裸 PIP pass 能否直接绑 `depthtex2`。
3. **合成管线 API 面:** 1.21.11 的 `RenderPipeline.Builder` 是否存在独立的
   `withSampler` / `withFragmentShader` 调用, 以及是否需要 `withLocation` / `RenderPipelines.register`。
4. **ScopeFinalOverlayState flush:** 本步未动; 后续需要确认"PIP 合成之后 flush"对
   no-shader (不开光影) 与 Iris 两条路径分别插在哪个调用点, 并保留
   `ScopeLateReticleState` 旧路径作对照。
5. **配置:** 本分支 `RenderConfig` 目前没有任何一个 `ScopePip*` 项; 26.2 有 12 个
   `ScopePip*`, 对齐工作属于 Step 7, 本步未做。

## 4. 下一步 (必须等用户实机确认再进)

1. 用户实机编译/运行本步, 确认现有深度孔径、准星、镜身、Iris 表面无回归。
2. 之后才进入 Step 2: 写「只涂纯品红」的全屏 pass, 采样
   `tacz_WorldDepthSampler` / `tacz_ApertureDepthSampler`, 按
   `ad < wd - 1.0e-6` 落笔, 确认只有孔径被涂红。

## 5. 回归复测清单 (Step 1 完成后)

> 这些是实机对照项。本步未动任何渲染行为, 因此预期结果一律与改动前逐帧一致。

### 无光影 (vanilla)
- [ ] 开镜时准星仍在孔径内、目镜框仍盖住镜头边缘。
- [ ] 镜外世界、镜身、枪口/手持枪体无遮挡异常。
- [ ] 开/收镜过程中深度恢复 (`RESTORE`) 后, 水面/粒子/云不会被错误穿透。
- [ ] 近距/远距场景下目镜孔径裁剪无洞、无残影。
- [ ] 切换红点、全息、组合镜低倍档时不出现深度 mask 泄漏。

### Iris (光影)
- [ ] `ScopeFinalOverlayState` 的 final-overlay 路径仍按 TAIL flush 准星/目镜框。
- [ ] `ScopeLateReticleState` 的 HAND_TRANSLUCENT 晚路径 (R8/R9 对照路径) 仍可用。
- [ ] 光影包 (如 Complementary) 下开镜无水雾/体积雾把准星单独盖掉的问题 (保持现状)。
- [ ] Iris `depthtex2` 分支 (`useIrisPreHandDepth`) 日志仍出现, 且不误杀普通 mask 帧。

### 诊断
- [ ] 控制台无新增 `ScopeDepthCopyState` 失败日志。
- [ ] `GlCommandEncoderScopeDepthCopyMixin` 仍在正常 draw 边界执行, 未因新访问器改变分支。

### 回归对照
- [ ] 保留 `ScopeLateReticleState` 旧路径为对照: 切到旧路径 (关闭 final overlay) 时行为与
      本步一模一样。
- [ ] `ScopeFinalOverlayState` 暂未被改, 因此 `renderAfterFinalComposite` 仍只在 Iris 路径调用。
