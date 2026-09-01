# 同步指导：refab 1.21.11（arena/01a052b2）（2026-08-30）

> 背景裁定（维护者，2026-08-30）：**1.21.11 的 PIP rerender 移植无限期暂停**——
> Iris 兼容被证实需要拆其现有渲染逻辑重写一套离屏渲染，成本不可接受。
> 本文告知：暂停之后该分支还欠什么、从 26.2 侧能拿走什么、怎么收尾。
> 深度按「机制 + 锚点 + 风险」给到能独立开工的程度，不逐文件替你写代码。

## 0. 收尾清单（暂停裁定的直接后果，先做）

1. **剥离 TEMP 提交**（`a514425`..`7dc1003` 共 7 个 `TEMP: round N diagnostics`）。
   它们只碰了 5 处：`build.gradle` 里的 javap 诊断任务、`ScopePipRenderState`、
   `ScopePipRerender`、`GameRendererMixin` 的诊断注入、lang 双语的诊断键。
   由于这些提交已推远端且 CI 会回推 ci-log，**别 rebase 改史**——
   开一个「revert diagnostics」提交把诊断代码反向删掉即可。
   判据：`git diff 46f86c9 HEAD -- src/ build.gradle` 应归零（46f86c9 =
   最后一个实质提交「Wire ScopePipRerender/ScopePipResolutionScale into config」）。
2. **`SCOPE_PIP_RERENDER_1211_PORT_PLAN_20260830.md` 顶部加状态头**：
   `> STATUS: DECLINED（2026-08-30 维护者裁定：Iris 兼容需重写离屏渲染，无限期暂停）`。
   文档本体保留——里面的 26.2 文件清单和 API 断点表是将来若重启的起点。
3. **rerender 半成品的处置**：`ScopePipRerender.java`（202 行）与
   `SCOPE_PIP_RERENDER` / `SCOPE_PIP_RESOLUTION_SCALE` 两个配置项已经接进
   config 面板。二选一：(a) 保留但把配置项默认锁 false + tooltip 注明
   「实验性，无 Iris 兼容」；(b) 整个 revert 到 Step-3 纯重投影态。
   建议 (a)——代码无害（无光影下 rerender 路径本身能跑），砍掉反而丢掉
   已实测过的无光影收益。裁定权在维护者。
4. 上面做完后，把已 PASS 的 Step1-3 + 8 项配置**收口合并进 `1.21.11` 长期分支**。
   这批工作（20 个实质提交）已经历完整实机迭代（品红诊断→镜内 1x→POV 跳变三连修），
   压着不合并只会继续发酵成第二个 18 提交混装分支。

## 1. 从 26.2 侧可拿的改动（进度对比结论）

对比基准：本分支 `arena/01a04e96` @ `9f7412e`（全 CI 绿）vs `origin/1.21.11` @ `b336663`。
逐项核对过目标侧基线代码，不是猜的：

### 1.1 检视动画两连修 ★强烈建议同步（跨纪元通用）

- **源**：`4aa8d7b` + `12d6f3c`（两个必须一起拿，后者修正前者引入的新语义）。
- **症状**：开镜（机瞄/倍镜同样）时触发检视 → 动画不可打断，只有切枪能救。
- **机制一句话**：`stopAnimation` 只停了 `getAnimation(track)` 返回的旧 runner，
  而带过渡启动的新动画挂在旧 runner 的 `transitionTo` 上，0.2s 过渡窗口内的
  打断必然落空 → 修法是连 `transitionTo` 一起 stop；第二个提交再用
  「出生序号 + triggerSpawnFloor 栈式快照」豁免**同一 trigger 里刚启动的后继动画**
  （否则「检视→立刻开镜」这类脚本自己启动的过渡也被误杀）。
- **可搬性已核实**：目标侧 `AnimationStateContext.stopAnimation`（:177）与
  26.2 基线**逐字相同**；`ObjectAnimationRunner.getTransitionTo()`（:54）存在。
  三个文件 `ObjectAnimationRunner` / `AnimationStateContext` / `AnimationStateMachine`
  直接按 26.2 的 diff 平移即可，无 API 断点。
- **验证**：开镜后按检视键，开火/换弹应能立刻打断；再验「检视中途开镜」
  动画应平滑收敛不硬切。

### 1.2 meshloader（TML 内置）——只有 collector 安全子集可搬，默认不建议

- 按治理提案 §1 的「存量层默认不收特性」原则，**默认不搬**。若将来有枪包需求：
  - **可搬**：`8c6ad27` 的安全子集（纯 `submitCustomGeometry` collector 路径 +
    解析缓存 + 预算闸门 + 弹匣补画）。已核实 1.21.11 的 `BedrockModel` /
    `BedrockAttachmentModel` 就在用 `submitCustomGeometry`，接口面在。
  - **不可搬**：GPU 烘焙层（`8191f6b` 起的全部）。硬依赖 26.2 的
    `RenderPipeline.builder` + `BindGroupLayouts`（已核实 1.21.11 引用数为 0），
    烘焙/绘制层需整体重设计，等价于跨纪元移植，暂停原则同样适用。
- 若搬安全子集，连带拿 `f70867d`（近距全模豁免）里 **collector 侧**的逻辑
  （`PolyRenderPolicy.withinFullDetailDistance` + `withinContextBudget` 改造），
  跳过其中 `RenderDistance.isGuiRender()` 依赖前先确认该类在 1.21.11 存在
  （它是 TACZ 本体类，应该在——开工时 `git grep RenderDistance` 一下）。

### 1.3 明确不适用（别浪费时间对表）

| 我们的改动 | 不适用原因 |
|---|---|
| 镜内裁手 `94179d4` / 镜内文字 `9d03659` | 依赖掩码架构 `ScopeMaskRenderer`（R 通道离屏掩码）。1.21.11 是深度孔径纪元，等价需求应基于 `ad < wd - eps` 判定另行实现——且深度孔径天然裁 3D 几何，症状可能根本不存在。先实测有没有「手臂/文字浮在镜外」再说 |
| PIP 倍率闸门 `5a94623`/`c74b34b` | 052b2 已自带 `SCOPE_PIP_MIN_MAGNIFICATION`（已核实 config 里有），语义等价，无需重搬 |
| ShadowScale/RerenderInterval `4ec0dde`/`9df8718` | rerender 专属旋钮，随暂停裁定一并冻结 |
| 遮光环合成后重画 `4168e91` | 1.21.11 是这机制的**起源地**（final-overlay 族），它比我们的版本更完整 |
| PR#82 帧率衰减修复 | rerender 专属（SubmitNodeStorage 保留闸门），无 rerender 则无此漏 |

## 2. 给下任 agent 的开工顺序建议

§0 收尾（1-2 轮）→ §1.1 动画修复（半轮，纯平移）→ 合并进长期分支 →
在 `docs/lineage/HANDOFF_LEDGER.md`（本仓 arena/01a04e96 分支上）把
rerender 那行改 DECLINED、动画同步加 DONE 行。
