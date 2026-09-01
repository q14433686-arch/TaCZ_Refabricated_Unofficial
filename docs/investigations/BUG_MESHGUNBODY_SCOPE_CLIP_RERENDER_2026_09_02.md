# BUG：mesh 枪身目镜裁剪被时序静默禁用（报告为「仅二次渲染时被高倍镜裁切」）

- **日期**：2026-09-02
- **报告人**：维护者（实机）
- **影响线**：26.2 Fabric（本仓）与 26.2 NeoForge 姊妹线（同架构、同移植、同病）；
  `1.21.11` / `26.1.2` 不受影响 —— 那两条分支**不含 mesh/TML 代码**
  （`git ls-tree` 实测：两条分支 mesh 文件数为 0），走的是各自的模板/深度孔径架构。
- **修复**：本仓本轮提交；姊妹线 `99253c5`（PR #28）。
- **证据级别**：**静态闭环**（时序穷举 + 逐闸门对照）。CI 编译绿；**实机未跑**，
  本文不宣称「已修好」，只宣称「根因已钉死 + 修法已实装」。

---

## 1. 报告的现象（原话要点）

> 高模枪的枪身（配件未知）在**仅开启二次渲染**时才会被「高倍镜」裁切，否则不会，
> 与是否开启光影无关。

## 2. 现象是假象：真正的事实是「从来没裁过」

「裁切」这个观感来自二次渲染模式的一个巧合：**镜内那幅画面本来就不含视模**。
`ScopePipRenderer.renderScopeView(...)` 在 vanilla 主世界渲染**之前**、用窄 FOV
把世界再画一遍（`GameRendererMixin` 的 `renderLevel` INVOKE+BEFORE 注入点），
而第一人称视模由 `GameRenderer#renderItemInHand` 在 `LevelRenderer#render`
**之外**渲染 —— 所以离屏那幅里没有枪。合成把这幅贴进孔径后，枪身在镜内
「消失」，看起来正像被镜片裁掉了。

关掉二次渲染（重投影模式）时，镜内画面是主画面中心那一块的放大拷贝
（`captureScene` 刻意抓在 `renderItemInHand` **之前**），随后枪身照常画在主画面上
——**包括画在孔径那块之上**。这才是未经裁剪的真实形态：枪管穿进镜片画面。

也就是说：**两种模式都没裁**，只是二次渲染那一种把缺裁藏起来了。

## 3. 根因：判据在绘制期恒为 false

裁剪的启用判据是 `ScopeBodyRenderTypes.maskReadyForViewmodel(...)`，它要求
`!ScopeMaskGeometry.isEmpty() && ScopeMaskGeometry.isViewmodelClipEnabled()`。
而这份「当场事实」的寿命只到阶段边界为止：

`ScopeMaskRenderer.renderAtPhaseBoundary()` 在 `finally` 里**无条件**
`ScopeMaskGeometry.clear()`（`ScopeMaskRenderer.java:397`；`clear()` 同时复位
entries 与 `viewmodelClipEnabled`，目的是防收起瞄具后掩码粘住）。

一帧内的时序（注入点见 `FeatureRenderDispatcherMixin`：掩码在 `executeSolid`
的 INVOKE+**BEFORE**（:147/:150），mesh 手部表在其 INVOKE+**AFTER**（:196/:199））：

| 时刻 | 谁在判定 | `ScopeMaskGeometry` | 判定 | 结果 |
|---|---|---|---|---|
| 手持 submit 期 | 立方体枪身 `BedrockGunModel:340`、配件 `AttachmentRender:62`、手臂 `RenderHelper:112`、火光 `MuzzleFlashRender:142` | 在册（瞄具提交时登记 + `enableViewmodelClip()`） | **true** | 一直正常裁 |
| 阶段边界 | 画掩码 → `finally` 清空 | 清空 | — | — |
| `executeSolid` | 立方体按 submit 期已选好的 RenderType 绘制 | — | — | 正常 |
| `executeSolid` **之后** | mesh GPU 手部表（自定义 pass `PolyMeshGpuRenderer:948` / 光影 RenderType 路线 `:833`） | **已空** | **恒 false** | **从未裁过** |

`RenderHelper.java:62` 的注释其实已经把这条规矩写下来了：

> 判定放在这里（submit task 执行期）而不是 extract 期：掩码清单登记发生在瞄具提交内部，
> 只有此刻的 `maskReadyForViewmodel` 才反映本帧真实状态。

mesh 的 GPU 路径是**绘制期**消费者，却沿用了同一个 submit 期判据 —— 两条路线
（无光影自定义 pass、光影 RenderType）同病，因为它们共用这一个判据。

## 4. 本线的定位过程（含一次未成形的错判，如实记录）

1. 本仓在收到报告的同一轮读码中**已经**推出「绘制期 `isEmpty()` 恒真 ⇒ 判据恒 false」，
   并与 `MESH_LOADER.md` §5.2-bis 第 9 项标注的「已实装，**待实测**」对上 —— 即该特性
   从未被实机验证过。
2. 但该结论与报告的现象（「只有二次渲染时裁」）**表面冲突**：若判据恒 false，
   两种模式都该「不裁」。当时未能解释这个冲突，于是停下来向维护者追问现象细节
   （裁切形状 / 关掩码是否复现 / 重投影下枪管是否穿镜），**没有**提交任何猜测性修复。
3. 姊妹线把缺失的那一环钉死了：**二次渲染的镜内画面不含视模**（§2），冲突消解 ——
   代码侧结论（判据恒 false）成立，报告侧观感（像被裁）也成立，两者不矛盾。
4. 本线**没有**提交过错误修复，因此无需回滚；需要修正的只是「等现象确认再定方向」
   这个判断 —— 时序穷举本身已经足以定案，缺的只是对观感成因的一步推理。
   姊妹线有一笔误读提交并自行回滚（`21605f9`），记录见其
   `docs/records/BUG_MESHGUNBODY_SCOPE_CLIP_RERENDER_20260902.md` §3。

## 5. 修法（三文件，与姊妹线同形）

| 文件 | 改动 |
|---|---|
| `ScopeMaskRenderer` | 新增帧快照 `viewmodelClipMaskThisFrame`（:281）：在 `drawMask` 的**成功路径**上、`finally` 清空**之前**记下 `ScopeMaskGeometry.isViewmodelClipEnabled()`（:470）；`buildMesh()` 返回 null（掩码被清空）时置 false（:407）；`beginFrame()` 每帧复位（:309）；对外只读 `isViewmodelClipMaskThisFrame()`（:329） |
| `ScopeBodyRenderTypes` | 新增绘制期变体 `maskReadyForViewmodelAtDraw()`（:491）与 `clipForViewmodelAtDraw(original, texture)`（:513）：闸门与 submit 期版本逐条相同，只把「掩码就绪」从当场几何改问帧快照 |
| `PolyMeshGpuRenderer` | 两处绘制期判据换用变体：自定义 pass 的 `clipAgainstOcular`（:948）、光影 RenderType 路线的 `clipForViewmodelAtDraw`（:833）；裁剪首次生效打 log-once |

**log-once（刻意加的，就是为了让「裁剪生效」在日志里有直接证据，不必靠观感反推）**：

```
[TacZMeshLoader] GPU hand mesh pass: ocular clip ACTIVE (custom pass route) - …（logged once）
[TacZMeshLoader] GPU hand mesh pass: ocular clip ACTIVE (RenderType route) - …（logged once）
```

（两条路线共用一个 once 标志，谁先生效谁播报，消息里带路线名。）

### 逐闸门对照（证明与立方体同开同关）

| 闸门 | submit 期 `maskReadyForViewmodel` | 绘制期 `…AtDraw` |
|---|---|---|
| 调用点确属第一人称视模 | 参数 `applies` | 由调用点保证（只有 mesh **手部表**走这条路；世界表刻意不裁） |
| `ScopeMaskEnable` 配置 | ✓ | ✓ |
| 光影下掩码回退（`IrisCompat.shouldDisableScopeMaskUnderShaderPack`） | ✓ | ✓ |
| 本帧有目镜几何 | `!ScopeMaskGeometry.isEmpty()` | 帧快照蕴含（无几何 → 掩码不画 → 快照 false） |
| 允许裁视模（低倍 sight 的 reticle-only 掩码不许裁枪身） | `isViewmodelClipEnabled()` | 帧快照的**内容** |
| 掩码 target 就绪 | `ScopeMaskTextureHandle.syncToMaskTarget()` | 同 |
| 熔断（`failed`） | 由 `renderAtPhaseBoundary` 不画掩码传导 | 由快照 false 传导 |

## 6. 实机判据（未跑）

1. 高倍镜 + mesh 枪：枪身/配件与立方体枪身一样被孔径裁掉；
2. **重投影、二次渲染、PIP 关的经典整屏变焦三种形态都裁**（这是与修复前最直接的对照）；
3. 镜内画面干净、收镜后枪身完整、光影下同一行为；
4. 低倍 sight（红点/内红点）不在镜片投影内啃洞（reticle-only 掩码不裁枪身）；
5. 日志出现**一次** `ocular clip ACTIVE` —— 出现即证明判据不再恒 false。

## 7. 回归风险与回退

- 判据从「恒 false」变为「按帧快照」，因此**首次**让 mesh 枪身真的参与镜内 discard。
  最坏情况是裁剪范围与立方体不一致（观感：镜内边缘有残余枪身像素），不会画错模型：
  两条路线用的是同一张掩码、同一份 `core/scope_body` discard 语义。
- 世界表（第三人称/掉落物/展示台）**刻意不裁** —— 世界枪本就该出现在镜内画面里，
  与 collector 的世界枪一致。本次改动没有触碰世界表的判据。
- 回退：把两处 `…AtDraw()` 换回 `maskReadyForViewmodel(true)` 即恢复修复前行为
  （= 裁剪不生效），无配置项、无数据迁移。
- `ScopeMaskEnable=false` 时整条裁剪链路（含本次改动）全部让开，回到无掩码行为。

## 8. 跨线对应关系

| 项 | 26.2 Fabric（本仓） | 26.2 NeoForge 姊妹线 |
|---|---|---|
| 修复提交 | 本轮提交（见 `git log`） | `99253c5`（PR #28） |
| 误判回滚 | 无（未提交过错误修复） | `21605f9` 已回滚 |
| 记录 | 本文 | `docs/records/BUG_MESHGUNBODY_SCOPE_CLIP_RERENDER_20260902.md` |
| 三文件 | `ScopeMaskRenderer` / `ScopeBodyRenderTypes` / `PolyMeshGpuRenderer` | 同名同改（无 loader 差异） |
| 文档 | `MESH_LOADER.md` §5.2-bis 第 9 项 | `MESH_LOADER.md` §5.2 第 18 条 |
