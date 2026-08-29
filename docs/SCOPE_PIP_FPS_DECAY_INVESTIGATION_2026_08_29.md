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
| **GPU 显存耗尽** | 用户排除 | GPU 独占 16G / 可用 ~14G，衰减在几秒-几十秒量级，
  吃满 14G 不现实；且真爆显存在本仓有案底 = 直接 `GpuOutOfMemoryException`
  崩游戏（见 `ScopePipTrace` 类注释），不是优雅降到 7fps 稳住 |

**探针没覆盖的两格：GPU 侧字节数（非条目数）、Java 堆与 GC。**
前者已在本轮探针补齐；后者是头号新嫌疑（见 §2）。

## 2. 假设排序（2026-08-29 依用户 16G 显存信息重排）

用户补充（关键修正）：「衰减与开镜时长无关，只与距第一次开镜的时间有关」。
若严格成立，**按 pass 数累积的所有假设（包括 GPU 逐 pass 泄漏）全部出局**
——持续开镜一分钟（数千个 pass）与只开一瞬间，衰减速度应差几个数量级。
所以累积按**墙钟/帧数**走。加上 16G 显存排除项，重排如下：

- **H1（头号，本轮新晋）Java 堆持续增长 → GC 饱和**。7fps 地板不崩溃、
  重进存档重置、与开镜时长无关 —— 全部是 GC 时间占比爬升的经典签名。
  每帧（或每开镜帧）在 Java 堆里漏一点（1 字节/帧，60000 帧/20 分钟就是 60KB
  级别的慢火，或更大），GC 越来越频繁，渲染线程被暂停的比例越来越高。
  姊妹分支扫了若干具体集合，**从未量过堆本身**。
- **H2（降级）每 scope pass 在瞄具管线保留 GPU 状态里累积资源**。
  与「开镜时长无关」冲突，除非用户对衰减速率的感受不精确；保留为第二顺位，
  由 GPU 字节探针裁决。
- **H3（队列积压反馈）无任何泄漏**：第一次开镜把帧率砍半（整条光影管线跑两遍），
  直接打破积压平衡（区块构建/上传/光照任务追不上渲染），积压单调增长、GC 压力
  随之上升；「隔一会再开镜从上次的位置继续掉」是因为积压从未被消化（空闲时
  积压有上限，一旦到顶……此说弱在：空闲时积压应被消化掉一部分）。
- **H4 累积源在主管线 / 驱动层 / shaderpack 进程级状态**，瞄具管线只是触发器。
  若空闲释放实验后衰减依旧，落到这里或 H3。

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

**待用户确认的裁决性事实**：这一分钟内，开镜帧率**是否仍在继续下降**？
- 继续下降 ⇒ 累积器不是 Java 堆（堆在这分钟里没动），转 H3/H4（GPU/队列/驱动）；
- 不下降 ⇒ 衰减需要开镜活动来推进，「与开镜时长无关」需修正为「与开镜**会话**数有关」，
  泄漏按 pass/会话沉积，实验 A/C 点名泄漏类。

## 5. 本轮改动清单

| 文件 | 改动 |
|---|---|
| `IrisScopePipelineCompat.java` | 解析 pipelinesPerDimension 字段；新增 `scopePipeline()` / `mainPipeline()` / `pipelineMapSize()` / `releaseScopePipelineIfPresent()`（销毁+摘除+回指主管线+失效预热与 Voxy 栈；失败一次即放弃） |
| `ScopePipRenderer.java` | `prewarmShaderPipelineIfNeeded` 接入空闲释放闸门（空闲延迟帧数、空闲期不预热）；新增 `scopePassCount()` 会话计数；镜内 pass 起止挂 per-pass 堆探针钩子 |
| `ScopePipResourceProbe.java` | 新文件：600 帧脉冲资源探针 = GPU 纹理字节数（瞄具/主管线）+ Java 堆 used/max + GC 次数/耗时窗口增量；另有 per-pass「耗时 + heapUsedMiB」细粒度行（约每 120 帧一次）。上一轮的 `ScopePipGpuMemoryProbe` 已删除并入本类 |
| `RenderConfig.java` | 新键 `ScopePipReleaseIdlePipeline` / `ScopePipIdleReleaseDelayFrames` / `ScopePipDebugGpuMem`（全部默认关，均为 EXPERIMENT/DEBUG 性质） |
| `GameRendererMixin.java` | extract HEAD 挂资源探针 beginFrame |

## 6. 验证协议（更新：spark 括号实验是决定性一步）

**实验 B（闲置括号 —— 先做，回答「不碰瞄具堆涨不涨」）：**

1. 重进存档（重置衰减状态），原地站 30 秒等一切settled，**不要走动**；
2. 开镜一次（触发衰减时钟），关镜；
3. `/spark heapsummary` → 存输出 #1；
4. **原地不动、不开镜，站 3-5 分钟**，全程盯 F3 Mem%：不碰瞄具时堆涨不涨？
5. `/spark heapsummary` → 存输出 #2；
6. 最后开镜一次看帧率（应已衰减）。

**判读**：站桩不动 ⇒ 区块加载噪声为零 ⇒ #2−#1 的差值就是「纯墙钟泄漏」：
涨幅最大的类直接点名。若堆不涨而开镜帧率仍衰减 ⇒ 不是堆机制，转回 §2 的 H3/H4。

**实验 A（连续开镜括号 —— 回答「开镜期间堆涨多少」）：**

1. 重进存档；`/spark heapsummary` → #1；
2. 连续开镜 60 秒（收放无所谓，多数时间保持开镜）；
3. `/spark heapsummary` → #2。

**判读**：Δ 最大的类 = 每次 scope pass 的分配产物；与 B 对照即可分离
「每 pass 累积」与「每墙钟累积」两个来源。

**可选佐证**：`/spark profiler start --thread render --only-allocations`，
开镜 10-20 秒，`/spark profiler stop` —— 分配热点站点（≠保留对象，
只作旁证）。

**优先序**：B 已执行并给出关键事实（闲置堆不涨）。接下来：
- **先回答裁决性问题**（§4.4 末尾）：闲置那一分钟里 FPS 是否继续下降？
- **实验 C（单次开镜沉积，最快）**：heapsummary #1 → 快速开镜 1 秒 → 关镜
  → heapsummary #2 → 闲置 1 分钟 → heapsummary #3。
  判读：C 的 #2−#1 = 单次开镜沉积的堆；#3−#2 = 该沉积是否被 GC 回收
  （回收 = 只是瞬时压力；不回收 = 每次开镜留下一块，点名其类）。
- **实验 A（连续开镜 60 秒）**：判每次 pass 的分配量与类，与 C 对数量级。

没有实机条件就**明说**；定位前不得把任何一步写成修复。
