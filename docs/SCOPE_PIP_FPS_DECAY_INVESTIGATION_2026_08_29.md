# 光影下开镜帧率随时间持续衰减（ScopePipIsolatePipeline=true）— 调查与实验

> 分支：`arena/01a04d12-tacz-refabricated-unofficial`
> 日期：2026-08-29
> 性质：**调查进行中 + 实验开关（未定位，未声称修复）。**
> 用户报告：`ScopePipIsolatePipeline=true`（二次渲染 + 独立 Iris 管线 + 光影），
> 开镜帧率从第一次 ADS 起持续衰减，一路降到 ~7 FPS；重进存档重置但不根除；
> 用户明确排除 Voxy（卸载后现象不变）。姊妹分支 `arena/01a04a2d` 已多轮探针，无果。

---

## 0. 症状签名（先固定它，再谈机理）

1. 触发点 = **第一次开镜**（哪怕只有一瞬），不是进世界；
2. 衰减 = 距第一次开镜的**时间/累积**单调函数；开镜/收镜切换不重置；
3. 空闲时不恢复（隔一会再开镜，帧率从上次的位置继续掉）；
4. 地板 ~7 FPS；重进存档重置全过程；
5. 仅 `ScopePipIsolatePipeline=true` 时发生（用户口径）；
6. 光影包：Complementary 系 + labPBR/SEUS PBR 材质档。

签名要点：**只增不减、重进世界才清零**。这与「固定的每帧开销」矛盾，
必须是某种「逐 scope pass（或逐帧）累积、跨帧保留、世界级生命周期」的资源/状态。

## 1. 已排除（姊妹分支探针 + 用户实验）

| 嫌疑 | 探针 | 结论 |
|---|---|---|
| 瞄具管线每帧重建 | scope 管线实例身份计数 | 无果 |
| 预热慢路径每帧重跑 | probeSlowPathRuns | 无果 |
| Iris pipelinesPerDimension 膨胀 | map 大小 | 无果 |
| 激活 SSBO 数量/字节增长 | ACTIVE_BUFFERS 计数+字节 | 无果 |
| Blaze3D 保留集合（device/levelRenderer/gameRenderer） | 反射扫描集合字段 | 无果 |
| SodiumWorldRenderer 集合增长 | 反射扫描 | 无果 |
| Voxy doTraversal/视口切换 | 计时+计数 | 无果（且用户卸载 Voxy 复现） |
| 第二遍渲染是否被闸门拦下 | lastBlockedGate | 无果 |

**探针没覆盖的一格：GPU 侧字节数。** 上面的结构探针数的是「条目数」，
不是「显存字节数」。每 scope pass 若在瞄具管线的保留状态里留下若干纹理/缓冲
（而不是往集合里塞对象），条目数不变、字节数在涨 —— 全部探针都会显示正常。

## 2. 假设排序

- **H1（头号）每 scope pass 在瞄具管线的保留 GPU 状态里累积资源**。
  机理猜测：独立管线自身的 colortex/gbuffer/阴影/自定义图像里，某一张（几张）在
  每次 `beginLevelRendering → finalizeLevelRendering` 轮里被「新建后没释放」，
  旧的被持有者引用着（Iris 的 RenderTargets/GlImage/ShaderStorageBufferHolder 都是
  「持有即保留」语义）。重进存档 → `destroyPipeline()` 整套释放 → 重置 ✓；
  空闲不恢复（占着）✓；地板（显存/分配压力）✓；CPU 探针全平 ✓；
  仅 isolate=true（只有这条路径存在第二套管线）✓。
- **H2 累积源在主管线 / 驱动层 / shaderpack 进程级状态**，瞄具管线只是触发器
  （比如把某计数器推过了临界点）。若空闲释放实验后衰减依旧，就落到这里。
- **H3 PreparedFrame/SubmitNode 内部列表增长**（姊妹分支扫描的是「字段是集合」，
  没扫 POJO 里的列表）。排在 H1 之后，因为「仅 isolate=true」这条对不上。

## 3. 本轮新增的实验装置

### 3.1 `ScopePipReleaseIdlePipeline`（实验开关，默认 false）

连续空闲 `ScopePipIdleReleaseDelayFrames`（默认 120）帧后，在 extract 安全位
销毁瞄具管线（`IrisScopePipelineCompat#releaseScopePipelineIfPresent`：
`WorldRenderingPipeline#destroy()` 逐项释放 + 从 pipelinesPerDimension 摘除 +
失效预热状态与 Voxy 第二套栈）；重新开镜的 extract 里由预热重建。

- **判读**：开启后衰减消失 ⇒ H1 坐实（累积源在瞄具管线保留资源里）；
  衰减依旧 ⇒ H2/H3，转下一轮。
- **代价**：每次重新开镜付一次 shaderpack 编译（预热在同一帧 extract 完成，
  不落在渲染中途）；重建会重置全局 SystemTimeUniforms 帧计数（对 pack 的
  frameCounter 类效果是相位跳变，观感无害）。这是实验装置，不是成品方案。

### 3.2 `ScopePipDebugGpuMem`（诊断，默认 false）

每 600 帧打一行：scope pass 累计次数、管线表大小、瞄具/主管线各自的
**GPU 纹理字节数**（反射遍历对象图求和，深度 3、身份去重、预算封顶）。

- **判读**：scopePasses 涨且 scopePipelineTextureMiB 同步涨 ⇒ 瞄具管线在逐 pass
  累积 GPU 纹理，H1 直接坐实；两条曲线不相关 ⇒ 看 mainPipelineTextureMiB。
- `getMemorySize` 拿不到时字节列记 -1（此时以 F3 显存占用为准）。

### 3.3 零代码观察：F3 显存曲线

衰减进行中盯 F3 右上角 GPU 内存：**持续爬升 → 显存压力**（H1 的直接旁证）；
平稳 → 累积在 CPU/驱动不可见处。

## 4. 还想要的旧数据

姊妹分支的 pulse 日志（`[TACZ Scope][pulse] ...`）里其实已有决定性信息：
`avgScopeMs / avgMainRenderMs / avgCompositeMs` 三个每 600 帧窗口均值 +
`sinceFirstScopeS`。请把「第一次开镜后、衰减明显时」的几行 pulse 贴出来：

- `avgScopeMs` 随窗口涨、`avgMainRenderMs` 平 ⇒ 增长在镜内那一遍内部；
- `avgMainRenderMs` 也涨 ⇒ 整帧都被拖累（更指向显存/驱动压力）；
- 两者都平 ⇒ 增长在被探针漏掉的边角（H3）。

## 5. 本轮改动清单

| 文件 | 改动 |
|---|---|
| `IrisScopePipelineCompat.java` | 解析 pipelinesPerDimension 字段；新增 `scopePipeline()` / `mainPipeline()` / `pipelineMapSize()` / `releaseScopePipelineIfPresent()`（销毁+摘除+回指主管线+失效预热与 Voxy 栈；失败一次即放弃） |
| `ScopePipRenderer.java` | `prewarmShaderPipelineIfNeeded` 接入空闲释放闸门（空闲延迟帧数、空闲期不预热）；新增 `scopePassCount()` 会话计数 |
| `ScopePipGpuMemoryProbe.java` | 新文件：600 帧脉冲 GPU 纹理字节数探针（默认关） |
| `RenderConfig.java` | 新键 `ScopePipReleaseIdlePipeline` / `ScopePipIdleReleaseDelayFrames` / `ScopePipDebugGpuMem`（全部默认关，均为 EXPERIMENT/DEBUG 性质） |
| `GameRendererMixin.java` | extract HEAD 挂 GPU 探针 beginFrame |

## 6. 验证协议

1. 复现基线：`ScopePipRerender=true`、`ScopePipIsolatePipeline=true`、
   `ScopePipAllowShaderPacks=true`，Complementary + labPBR；开镜若干次确认衰减。
2. `ScopePipDebugGpuMem=true` 跑一轮衰减，收集 gpu-probe 行（判据见 §3.2）。
3. `ScopePipReleaseIdlePipeline=true`（延迟默认 120 帧）再跑一轮：
   - 每次开镜会话是否都从「满血」开始（判据 §3.1）；
   - 重新开镜的编译停顿是否可接受；
   - 连续快速开收镜（< 延迟窗口）是否出现反复拆建抖动。
4. 全程记录 F3 显存。

没有实机条件就**明说**；定位前不得把任何一步写成修复。
