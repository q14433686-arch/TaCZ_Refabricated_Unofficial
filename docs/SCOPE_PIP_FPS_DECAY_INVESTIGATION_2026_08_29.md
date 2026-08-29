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
2. **衰减推进 = 开镜活动**（2026-08-29 用户裁决）：每次开镜会话推进一格，
   闲置不推进（闲置 1 分钟帧率不继续降、且该分钟堆零增长）。原报告
   「与开镜时长无关」修正为「与开镜会话/次数有关」；每次会话的沉积是否
   按帧数×时长缩放，待实验 A/C 区分（快速点射 vs 持续按住）；
3. 空闲时不恢复（隔一会再开镜，帧率从上次的位置继续掉）；
4. 地板 ~7 FPS；重进存档重置全过程；
5. 仅 `ScopePipIsolatePipeline=true` 时发生（用户口径）；
6. 光影包：Complementary 系 + labPBR/SEUS PBR 材质档。

修正后签名与堆数据完全自洽：闲置堆零增长（§4.4）+ 闲置帧率不降
⇒ 每次开镜会话沉积一块「不被回收的东西」，闲置时原地保留。

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
| **GPU 显存耗尽** | 用户排除 | GPU 独占 16G / 可用 ~14G，衰减在几秒-几十秒量级，
  吃满 14G 不现实；且真爆显存在本仓有案底 = 直接 `GpuOutOfMemoryException`
  崩游戏（见 `ScopePipTrace` 类注释），不是优雅降到 7fps 稳住 |

**探针没覆盖的两格：GPU 侧字节数（非条目数）、Java 堆与 GC。**
前者已在本轮探针补齐；后者是头号新嫌疑（见 §2）。

## 2. 假设排序（2026-08-29 三轮修正后的最终签名）

最终签名（用户裁决 + §4.4 数据互证）：
- 衰减由**开镜活动**推进（每次开镜会话沉积）；闲置 1 分钟帧率不降、堆零增长；
- 沉积跨闲置**保留**（「隔一会再开镜从上次位置继续掉」）；
- 16G 显存排除 GPU 耗尽；重进存档重置；
- 尚未区分：每次会话**固定**沉积 vs 按**开镜帧数**沉积（实验 A 裁决）。

- **H1（头号）开镜活动期间在 Java 堆沉积的保留对象 → 累积 GC 压力**。
  7fps 地板不崩溃 + 重进存档重置 + 闲置保留 + 闲置不推进 —— 全部吻合。
  对象类别未知，实验 C 的 #2−#1 差值直接点名。候选排查顺序：
  per-pass 分配却挂在长生命周期持有者上的东西（Iris 侧 per-dimension 缓存、
  pack 的 custom uniform/图像状态、帧图/命令缓冲池残留）。
- **H2（降级）瞄具管线的 GPU 保留资源按会话沉积**（堆摘要看不见这一层；
  由 GPU 字节探针与 F3 显存曲线裁决）。
- **H3（备选）per-aiming-session 一次性副作用**：与开镜帧数无关的固定块，
  若实验 A 显示沉积不随时长缩放，此假设升级 —— 届时在「开镜起点/终点各跑
  一次」的代码点里找。
- **H4 主管线 / 驱动层 / shaderpack 进程级状态**：兜底，前面全部排除才回头。

## 3. 本轮新增的实验装置

### 3.1 `ScopePipReleaseIdlePipeline`（实验开关，默认 false）

连续空闲 `ScopePipIdleReleaseDelayFrames`（默认 120）帧后，在 extract 安全位
销毁瞄具管线（`IrisScopePipelineCompat#releaseScopePipelineIfPresent`：
`WorldRenderingPipeline#destroy()` 逐项释放 + 从 pipelinesPerDimension 摘除 +
失效预热状态与 Voxy 第二套栈）；重新开镜的 extract 里由预热重建。

- **判读（对应重排后的假设）**：开启后衰减消失 ⇒ 累积源在瞄具管线的保留状态里
  （GPU 资源或 Voxy 栈，H2）；衰减依旧 ⇒ 源在主管线/堆/队列（H1/H3/H4），
  此开关对堆泄漏无效（堆增长与管线无关）。
- **代价**：每次重新开镜付一次 shaderpack 编译（预热在同一帧 extract 完成，
  不落在渲染中途）；重建会重置全局 SystemTimeUniforms 帧计数（对 pack 的
  frameCounter 类效果是相位跳变，观感无害）。这是实验装置，不是成品方案。

### 3.2 `ScopePipDebugGpuMem`（诊断，默认 false；已扩充为资源探针）

每 600 帧打一行（`[TACZ Scope][probe]`）：
- scope pass 累计次数、管线表大小、瞄具/主管线各自的 **GPU 纹理字节数**
  （反射遍历对象图求和，深度 3、身份去重、预算封顶）；
- **Java 堆**：`heapUsedMiB` / `heapMaxMiB`；
- **GC**：本窗口的 GC 次数增量 `gcCountDelta` 与累计耗时增量 `gcTimeDeltaMs`。

- **判读**：
  - `heapUsedMiB` 随 frame 单调爬升、`gcTimeDeltaMs` 同步涨 ⇒ **H1 坐实**（堆泄漏/
    GC 饱和），这正是「7fps 地板不崩溃」的直接解释；
  - scopePasses 涨且 scopePipelineTextureMiB 同步涨 ⇒ H2；
  - 全平 ⇒ H3/H4，转「积压反馈」调查。
- `getMemorySize` 拿不到时字节列记 -1；堆/GC 数据直接读 JDK API，恒有效。

### 3.3 零代码观察：F3 双曲线

衰减进行中盯 F3 右上角的**两**条曲线：
- **Mem%（第 2 行左侧，Java 堆）**：持续爬升、到达高水位后反复触发 GC
  → H1 的现场目击（比探针更细粒度）；
- **GPU 内存**：持续爬升 → H2 的旁证；平稳 → 与 H2 无关。
（16G 显存背景下，GPU 曲线预期平稳——它只需要证明自己「没涨」即可。）

## 4. 已获得的旧数据

### 4.1 用户 latest.log 分析（`26.2(main)` @ `79b6e4c`，2026-08-29，ComplementaryUnbound_r5.8.1）

2 分钟会话、两次短暂开镜（53 次 + 20 次 scope pass）。全部 32 行 pulse 的关键列：

| 指标 | 走势 | 结论 |
|---|---|---|
| `avgMainRenderMs` | 全程 1-2ms 平坦 | 主渲染**未**退化 |
| `avgScopeMs` | 12 → 20ms，两次开镜间 35 秒**未开镜**纯墙钟流逝 | 镜内那遍在不开镜的时间段里变贵（样本 20，弱） |
| `pipelinesPerDimension` | 恒 2 | 无管线膨胀 |
| `activeSSBOs / MiB` | 恒 0 | 无 SSBO 泄漏 |
| `irisSlowPath` / `voxyBuilds` | 恒 5 / 2 | 一次性事件，无每帧重建 |
| scope 管线集合字段 | 全平坦 | 无结构增长 |
| `avgCompositeMs` / `avgVoxyTraversalMs` | 0 / 0 | 合成与 Voxy 遍历都不是成本源 |

**推论**：泄漏不在任何被数过的结构里；用户已确认堆爬高水位反复 GC。
统一画像：Java 堆 live-set 自第一次开镜起增长 → 镜内那遍（完整第二遍管线，
比主渲染重一个量级）最先显形；主渲染 2ms 尚扛得住。**必须直接看堆内容。**

### 4.2 决定性工具已在用户包里：spark

日志里有 spark 的线程（`spark-monitoring-*`）。下一步直接用它的堆摘要做
**两次快照差值**，把泄漏类点出来：

```
/spark heapsummary     ← 第一次开镜后立刻跑一次，存下输出
（玩到衰减明显）
/spark heapsummary     ← 再跑一次
```

比较两次输出里按类分组的占用：**涨幅最大的类 = 泄漏对象**（实例数暴涨的类
同理）。文本输出可直接贴回来。可选佐证：

```
/spark profiler start --thread render --only-allocations
（开镜 10-20 秒）
/spark profiler stop    ← 看分配热点站点
```

分配热点 ≠ 保留对象，heapsummary 才是主判据。

### 4.3 spark 堆摘要差值分析（08:41 → 08:42，间隔 1 分钟）

用户提交两份 spark heapsummary（FIgvdblgnd → uuEbT01EKZ），top 类逐一做差：

| 类 | Δ实例 | Δ大小 | 判读 |
|---|---|---|---|
| `PalettedContainer`(+6,153) / `ThreadingDetector`(+6,153) / `Semaphore`/`ReentrantLock`(+6,1xx) / `LevelChunkSection`(+2,976) | 同步增长 | ~1MB | **纯区块加载**：`ThreadingDetector` 实例数恒等于 2×`LevelChunkSection`（每 section 两个 paletted container 各带锁/检测器）——走动载入 ~3k 个 section 的正常噪声 |
| `long[]` / `byte[]` / `int[]` / `Object[]` / `ArrayList` | +824 / +9,968 / +1,596 / +4,940 / +14,852 | 合计 ~6MB | 与区块加载同源（顶点/索引/调色板缓冲），无独立异常 |
| **全部 TACZ 模型/动画数据**：`BakedQuad`、`BedrockVertex`、`FaceItem`、`CubesItem`、`FaceUVsItem`、`AnimationKeyframes$Keyframe`、`Float`、`ModelPart$Vertex` | **恒 0（个别 −100 级）** | 0 | **模型/动画数据完全不涨**，排除 TACZ 资源缓存泄漏 |
| `org.antlr.v4.runtime.CommonToken` | 恒 264,133 | 0 | 静态保留（Iris 的 shader AST；antlr 非本仓依赖），不涨 |

**结论**：这一分钟窗口是「走动载入区块」的噪声，没有覆盖到 scope 衰减的累积过程；
任何可疑类都没有出现量级异常的增长。方法本身已被验证灵敏（ThreadingDetector
恒等式精确成立），所以下一步不是换方法，而是**把窗口对准衰减过程**（见 §6 实验 B/A）。

绝对量级备注：`long[]` 总占用 562MB / 85k 实例（平均 6.6KB），静态不涨；
`byte[]` 138MB。两数在两次快照间都只随区块加载微涨，不是当前主嫌，但若
闲置括号实验里它们随墙钟增长，嫌疑立刻上升。

### 4.4 spark 堆摘要差值（08:54 → 08:55，闲置 1 分钟括号）

用户提交（9DsfronYwG → ZkKXgsiDNf），间隔恰 1 分钟：

| 指标 | 结果 | 判读 |
|---|---|---|
| `ThreadingDetector` / `PalettedContainer` / `LevelChunkSection` / `ZeroBitStorage` | **Δ = 0 全零** | 括号期间**完全站桩**（零区块加载），数据质量干净 |
| 全部 TACZ 模型/动画类（BakedQuad/BedrockVertex/FaceItem/Keyframe/Float…） | Δ = 0 | 依旧不涨 |
| `long[]`(558MB) / `byte[]`(120MB) / `Object[]` / `String` | −36/+1,282/−383/+1,331 实例，≤0.2MB | 闲置游戏 tick 噪声，非泄漏 |
| `int[]` / `Class` / `FillerElement[]` | +3,591 / +378 / +196，≤0.1MB | 同上 |
| 其余 top-60 类 | ≤0.05MB | 全平 |
| **净变化** | **≈ 0** | **站桩不开镜时堆不涨** |

**这是一个决定性数据点**：闲置时 Java 堆**不增长** ⇒ 泄漏不是纯墙钟驱动的；
累积只发生在**开镜活动**期间（或更早观察到的 Mem% 爬升本身就是开镜期间发生的）。
结合用户「隔一会再开镜帧率不恢复」：要么每次开镜会话沉积一块堆、闲置不动它
（帧率不恢复 ✓、堆闲置不涨 ✓），要么衰减机制根本不在堆里（闲置帧率仍继续掉的话）。

**裁决性问题已由用户回答（2026-08-29）**：闲置那一分钟里帧率**不继续下降** ——
第二条为真：衰减靠开镜活动推进，「与开镜时长无关」修正为「与开镜会话/次数有关」
（§0 已更新）。沉积按会话发生、闲置保留，重进存档才释放。

### 4.5 实验 C/A 差值：每开镜帧沉积的保留渲染状态（2026-08-29 用户提交）

四份快照：C#1(09:12) → C#2(09:12, 5×1s 点射) → C#3(09:15, 闲置 2 分钟) →
A(09:16, 60s 连续开镜；基线≈C#3，无重进，Climate$Parameter 上行证明期间有走动)。

**C 括号（5 次点射 ≈ 300 scope pass）**：全部类增长 < 0.2MB，低于 top-137
可见阈值（~380KB）——沉积量太小，看不到不等于没有（按 A 的速率推算应沉积
~1,100 个 ≈ 50KB，恰好沉在阈值下）。C#2→C#3 闲置期只有 CHM$Node +6,884 等
tick 噪声，**小沉积未被回收**。

**A 括号（60s ≈ 3,300-3,600 scope pass）**：出现一组高度同步的新类，且数量
互为比例：

| 类 | 60s 增量 | 每 pass 沉积率 | 备注 |
|---|---|---|---|
| `ItemStackRenderState` | +13,296 | ~3.7 | 与下方 Layer 数**严格相等** |
| `ItemStackRenderState$LayerRenderState` | +13,296 | ~3.7 | 每个 state 恰 1 层 |
| `BedrockRenderSnapshot$DrawCommand`（**TACZ**） | +13,300 | ~3.7 | 枪模快照绘制命令 |
| `ModelFeatureRenderer$Submit` | +11,082 | ~3.1 | 提交节点 |
| `org.joml.Matrix4f` | +27,529 | ~7.7 | ≈2× DrawCommand（pose + 快照/掩码矩阵） |
| `org.joml.Matrix3f` | +15,185 | ~4.2 | ≈1× DrawCommand（normal） |
| `Optional` | +27,113 | ~7.5 | 与 Matrix4f 同阶 |
| `SlimeRenderState` | +2,397 | ~0.67 | 实体渲染状态（视域内少量实体） |

**结论（指纹）**：
- 沉积率 ∝ **开镜帧数**（A 的 60s 沉积 ≈ C 的 5×1s 的 12 倍 ≈ 帧数比 12 倍）——
  对「与开镜时长无关」的最终修正：**与闲置时长无关，与累计开镜帧数成正比**；
- 沉积对象 = 每次 scope pass 的**提交节点及其载荷**（枪模 DrawCommand、
  ItemStackRenderState、实体渲染状态、矩阵）——Submit 节点被保留时，
  上面整张对象图一起被保留（DrawCommand 经 `collector.submitCustomGeometry`
  的 lambda 闭包挂在 Submit 上，ItemStackRenderState/SlimeRenderState 经
  26.2 渲染状态挂在 Submit 上，字节码级一致解释）；
- 跨闲置**不回收**；重进存档重置；
- **已排除的持有者**（静态核对 + 数据双证）：
  phase 列表（主 pass 清空，mixin 取消只限 scope pass 内、对称平衡）、
  PreparedFrame.allSubmits（闲置帧同样跑手部 renderAllFeatures，若它增长
  闲置也该涨，B 实验零增长）、HandRenderer.frame 字段（未使用）、
  ScopeMaskGeometry.ENTRIES（每帧 clear）、RenderType 缓存（键=贴图 id，有界）。

**仍未定案**：Submit 节点究竟被谁保留。唯一确定的是保留发生在 scope pass 期间
（闲置零沉积）。下一步 = 堆图取证（§6 heapdump 步骤），GC root 路径会直接点名。

**附带校准问题**：C 的 5 次点射只沉积 ~50KB，若用户当时已能感到 FPS 下降，
说明单个保留对象的隐性重量（GPU 资源尾随）远大于其 Java 侧字节数 ——
heapdump 的 dominator tree 能同时给出答案。

## 5. 本轮改动清单

| 文件 | 改动 |
|---|---|
| `IrisScopePipelineCompat.java` | 解析 pipelinesPerDimension 字段；新增 `scopePipeline()` / `mainPipeline()` / `pipelineMapSize()` / `releaseScopePipelineIfPresent()`（销毁+摘除+回指主管线+失效预热与 Voxy 栈；失败一次即放弃） |
| `ScopePipRenderer.java` | `prewarmShaderPipelineIfNeeded` 接入空闲释放闸门（空闲延迟帧数、空闲期不预热）；新增 `scopePassCount()` 会话计数；镜内 pass 起止挂 per-pass 堆探针钩子 |
| `ScopePipResourceProbe.java` | 新文件：600 帧脉冲资源探针 = GPU 纹理字节数（瞄具/主管线）+ Java 堆 used/max + GC 次数/耗时窗口增量；另有 per-pass「耗时 + heapUsedMiB」细粒度行（约每 120 帧一次）。上一轮的 `ScopePipGpuMemoryProbe` 已删除并入本类 |
| `RenderConfig.java` | 新键 `ScopePipReleaseIdlePipeline` / `ScopePipIdleReleaseDelayFrames` / `ScopePipDebugGpuMem`（全部默认关，均为 EXPERIMENT/DEBUG 性质） |
| `GameRendererMixin.java` | extract HEAD 挂资源探针 beginFrame |

## 6. 验证协议（更新：spark 括号实验是决定性一步）

**实验 B（闲置括号）✅ 已执行**：站桩 1 分钟堆零增长（§4.4），且用户确认
该分钟帧率不继续降 —— 纯墙钟泄漏出局，衰减靠开镜活动推进。

**实验 C（单次开镜沉积 + 回收检验 —— 现在做这个，3 分钟出结果）：**

1. **重进存档**（从零开始），原地站 20 秒不动；
2. `/spark heapsummary` → #1；
3. **快速开镜 5 次**（每次按住约 1 秒、间隔 2 秒，快速点射式），记下
   每次开镜时的大致 FPS（第 1 次 vs 第 5 次应已有可见差异）；
4. `/spark heapsummary` → #2；
5. **闲置 2 分钟，不动不开镜**；
6. `/spark heapsummary` → #3。

**判读**：
- `#2−#1` = 5 次开镜会话沉积的堆。**涨幅最大的类 = 泄漏对象，直接点名**；
- `#3−#2` ≈ 0（沉积不被 GC 回收）⇒ 与「空闲不恢复」互证；
- 三次快照里 FPS 变化（步骤 3 记录）与堆沉积是否同步。

**实验 A（时长缩放 —— 区分「每会话固定沉积」还是「每开镜帧沉积」）：**

1. 重进存档；`/spark heapsummary` → #1；
2. **单次开镜按住 60 秒**；`/spark heapsummary` → #2。

**判读**：A 的 #2−#1 ≈ C 的 #2−#1 ⇒ 每次会话固定沉积（与时长无关）；
A ≫ C（≈12 倍）⇒ 按开镜帧数沉积。两者指向不同的代码点。

**可选佐证**：`/spark profiler start --thread render --only-allocations`，
开镜 10-20 秒，`/spark profiler stop` —— 分配热点站点（≠保留对象，只作旁证）。

**优先序（2026-08-29 更新：C/A 已执行，§4.5 已给出指纹）**：
- **堆图取证（决定性，下一步做这个）**：
  1. 重进存档 → 连续开镜 60-120 秒积累沉积 → `/spark heapdump`，按提示上传；
  2. 下载 spark 给的 .hprof，用 VisualVM（File → Load）或 Eclipse MAT 打开；
  3. 找到类 `com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot$DrawCommand`
     （或 `net.minecraft.client.renderer.item.ItemStackRenderState`），
     任选一个实例 → **Show nearest GC root** → 截图/复制整条引用链发我。
     **那条链就是泄漏持有者**，类名+字段名出来即可圈定修复点；
  4. 顺带看 MAT 的 dominator tree（若用 MAT）：DrawCommand 的 retained size
     能同时回答 §4.5 末尾的「隐性重量」问题。
- 校准问题：C 的 5 次点射时第 1 次与第 5 次的 FPS 读数（验证 ~50KB 沉积
  是否已可感知）。
- 若 heapdump 不方便：我这边可以加装「WeakReference 存活率 + 静态根 BFS
  反向可达」探针，在游戏内自动指出持有者候选（下一轮）。

没有实机条件就**明说**；定位前不得把任何一步写成修复。
