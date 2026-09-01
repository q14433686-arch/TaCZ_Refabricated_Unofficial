# 同步指导：renov 26.2（arena/01a04ea3，开发主线）（2026-08-30）

> 维护者裁定：`arena/01a04ea3` 是 renov 26.2 的开发主线；`01a04ec0`（精简 PIP）
> 已搁置不动。本文对比 04ea3 与我们 `arena/01a04e96` @ `9f7412e` 的进度差，
> 列出可同步项。**语义搬运，不抄 Fabric 表皮**（他们仓的既有纪律，继续遵守）。

## 0. 进度差一览（实测）

04ea3 的 PIP 同步自我们分支的**早期状态**（`9895739`，其 `ScopePipRenderer`
1126 行；我们现在 1515 行）。同步之后我们这边又发生了三轮它没有的演进，
外加两个它可能同样存在的 bug 修复。04ea3 自己也有我们没有的东西（遮光环
26.2 形态重画 `0c446f4` 已实机 PASS——我们的 `4168e91` 是同工作，两边已对齐）。

| # | 我们侧改动 | 04ea3 现状（已核实） | 建议 |
|---|---|---|---|
| 1 | PR#82：光影下开镜**帧率衰减泄漏**修复 + ResolutionScale 实装 + 全套选项进游戏内配置 | 有 `shouldPreserveSubmits`（1 处）与 `SCOPE_PIP_RESOLUTION_SCALE`，但取货点早于 PR#82 终态；是否含泄漏修复**未核实到 commit 级** | **优先核对**，见 §1 |
| 2 | `9df8718`：ResolutionScale 真实作用域文档化 + `SCOPE_PIP_RERENDER_INTERVAL` 新旋钮 | 无 `RERENDER_INTERVAL`（config 项清单里没有） | 同步 |
| 3 | `4ec0dde`：ShadowScale 热应用 + 钩子验证 | 有 `SCOPE_PIP_SHADOW_SCALE` 配置但热应用逻辑未核实 | 核对后补差 |
| 4 | `4aa8d7b`+`12d6f3c`：检视动画两连修 | `stopAnimation` 基线与我们修复前**逐字相同**、`getTransitionTo` 存在（均已核实）→ **bug 必然同在** | **同步，最高优先** |
| 5 | `94179d4` 镜内裁手 + `9d03659` 镜内文字 | 无（他们的 handoff 只覆盖了镜内裁剪顺序加固，不含这两个新特性） | 同步（同纪元同架构，见 §2） |
| 6 | meshloader 全套（9 提交，全实机 PASS） | 无任何 meshloader（已核实） | 一事一议，见 §3 |
| 7 | `f70867d`+`9f7412e`（meshloader 的 LOD 豁免与烘焙世代） | 随 #6 | 随 #6 |

## 1. 第一优先：重锚定 PIP 同步基准（治理提案 §2.2 的实操）

04ea3 从我们的活分支取货后，我们又动了。别逐 commit 追——**做一次终态对表**：

1. 取我们侧终态：`arena/01a04e96` @ `9f7412e` 的
   `ScopePipRenderer.java`（1515 行）+ `RenderConfig` 的 `SCOPE_PIP_*` 全集 +
   `RenderClothConfig` 对应条目；
2. 与 04ea3 现有实现逐段对差。已知的三个差异热点：
   - **PR#82 泄漏修复**：`SubmitNodeStorage` 清空被误拦截 → 修复是把
     `shouldPreserveSubmits` 的保留范围精确限定为只保留主 storage。
     04ea3 有这个方法但需要核对语义是「精确保留」还是修复前的「全保留」——
     全保留就是每帧泄漏、光影下帧率持续衰减，症状明确可实测（开镜挂机
     2 分钟看帧率曲线）；
   - **`SCOPE_PIP_RERENDER_INTERVAL`**（隔 N 帧重渲，性能旋钮）：确认缺失，直接补;
   - **ResolutionScale 的语义修正**（`9df8718`：只对 rerender 路径有效，
     文档与 tooltip 都改过）：把 tooltip 一并对齐，避免用户在重投影模式下
     调它然后报「没效果」的假 bug。
3. 对完在他们的同步文档里把基准 commit 写死为我们侧的里程碑
   （建议等我们 PR A-D 并入 main 后用 merge commit，见治理提案 §2）。

## 2. 镜内裁手 + 镜内文字（新特性，同纪元可整段搬语义）

两者都建立在 04ea3 已有的掩码架构上（`ScopeMaskRenderer` 已同步过去），
不需要新地基。搬运要点：

- **裁手（`94179d4`）**：机制 = 在 collector 上做**动态代理包装**，把
  `entityTranslucent(skin)` 的 buffer 请求替换为带掩码裁剪的
  `armClipped` 变体。两个可移植性关键（都已在我们侧验证）：
  1. RenderType 判等用 **identity 比较**是可靠的——`ENTITY_TRANSLUCENT`
     经 `Util.memoize`，同贴图恒同实例（26.2 vanilla 行为，两仓同 MC 版本，成立）;
  2. 裁剪 shader 就是掩码采样（`r > 0.5` = 镜内丢弃），他们已有同款采样约定
     （从我们这同步的 `scope_body` 族 shader 里就是这个约定）。
  - NeoForge 适配点只有代理挂接位置：我们挂在 Fabric mixin 的 collector 入口，
    他们找 NeoForge 侧等价的手部渲染 collector 交接点即可，其余逻辑照搬。
- **裁字（`9d03659`）**：机制 = 拦 `prepareText`，经 `visit` 后门把字形
  quad 徒手提交进 `scope_text` 裁剪管线；任何一步失败回退 vanilla
  `submitText`；保留 0.35 开镜进度门禁；只对 `BedrockAttachmentModel`
  的文字启用（`clipToScopeMask=true` 只从这一处传）。
  - 已知边界原样带走：ttf/unihex 灰度字体走回退路径（不裁但也不裂）。
- **验证剧本**：开镜看镜身侧面的文字/瞄具皮肤手臂穿镜——镜内区域不应出现；
  退镜正常；换 ttf 字体包不崩。

## 3. meshloader：建议「安全子集先行」二段式

他们仓没有任何 meshloader（已核实）。我们侧 9 个提交全 PASS，但**别一次全搬**：

- **第一段（低风险）**：`8c6ad27` 安全子集 = collector 路径 + geo 解析缓存 +
  顶点预算闸门 + `IMirrorGeometry` 弹匣双通道 + `f70867d` 近距全模豁免。
  全部走 `submitCustomGeometry`，26.2 vanilla API，两仓同版本零断点；
  Fabric 特有的只有 mixin 挂载方式（`GunDisplayInstanceMixin` 等三个装载点），
  NeoForge 侧用他们的模型装载事件等价替换。配置面同时接 TOML + Cloth
  （维护者的硬性惯例）。
- **第二段（等第一段实测 PASS 再动）**：GPU 静态烘焙层
  （`8191f6b`→`0ea0fb6`→`6e275d0`→`9f7412e` 按序，每个都是修一个真实翻车）。
  搬运时四条红线原样带走：
  1. GPU 表只收第一人称手部 pass（`isInHandPass` 判定，不是
     `transformType.firstPerson()`——后者对 GUI 也真）;
  2. 自建绘制必须自乘 draw 时刻的 ModelView（两层变换定理，`0ea0fb6` 的教训）;
  3. 光影下走 vanilla RenderType 路线让 Iris 按管线接管（`6e275d0`）;
  4. 光影开关翻转 → 烘焙世代号立即失效重烘，绕过光照节流（`9f7412e`，
     否则站桩切光影模型拉伸）。
- 参考文档一并搬：我们侧 `docs/MESH_LOADER.md`（含配置表）。

## 4. 动画两连修（最高优先，半天工作量）

§0 表 #4。三文件 diff 平移（`ObjectAnimationRunner` / `AnimationStateContext` /
`AnimationStateMachine`），纯引擎层无加载器差异，基线逐字相同已核实。
两个提交**必须一起拿**：只拿第一个会引入「同 trigger 后继动画被误杀」的新 bug
（第二个提交就是修这个的——出生序号 + `triggerSpawnFloor` 栈式快照豁免）。
验证：开镜检视可被开火/换弹打断；「检视中途开镜」不硬切。

## 5. 建议顺序

```
1. 动画两连修           （§4，半天，独立）
2. PIP 终态对表+补差     （§1，防泄漏 bug 在他们侧潜伏）
3. 镜内裁手+裁字         （§2，新特性）
4. meshloader 第一段     （§3，按需启动）
5. meshloader 第二段     （§3，第一段 PASS 后）
每步完成 → 两仓 HANDOFF_LEDGER 记 DONE(commit)
```

04ec0 按裁定不动、不合、不删（留档即可）；若将来重启，先按治理提案 §3
把 04ea3 的两轮矩阵修正补进去再说。
