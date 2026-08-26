# Iris 高倍目镜裁剪 / 开镜视角滞后 — 失败回退与跨仓审计

> # 同步注记（本仓 = TaCZ_Refabricated_Unofficial，Fabric 26.2）
>
> 本文由姊妹仓 TaCZ_Renovated `arena/01a03b03-tacz-renovated` 分支原样同步而来，
> 是其会话的失败回退与跨仓审计记录，正文保留原视角：文中「本仓」指 Renovated
> （NeoForge 26.2），「姊妹仓」指本仓库。§0 清单里的已落地内容已全部同步到本
> 分支；问题 2（Iris 高倍目镜裁剪）与问题 3（高倍/组合镜视角滞后）在两仓均未
> 解决，§2 的负结果与禁改清单同样约束本仓。注意 §3 表中「姊妹 Fabric 26.2」列
> 描述的就是本仓 26.2 线（含 IrisScopePipelineCompat / PIP 维度管线）；
> 动问题 2/3 前先读 §4/§5 的调查顺序。

日期：2026-08-26  
本仓分支：`arena/01a03b03-tacz-renovated`  
用户复测：`5f6b9e7` / `2ef8fab` **未修好**问题 2、问题 3。  
状态：**不得标 PASS**。问题 2/3 的**失败尝试代码**已从运行路径撤走；负结果与诊断仍是本分支内容。  
问题 1（温雷满进度永不爆）保留：`life >= 0 && tickCount >= life`，C4 `-1` 仍不超时。

姊妹仓：`q14433686-arch/TaCZ_Refabricated_Unofficial` `26.2(main)` 尖端约 `7f6d1bf`（2026-08-25）。  
官方：`MCModderAnchor/TACZ` 1.20.1 / 0.4.3 语义。  
宪章：refab 只取游戏语义，不抄 Fabric API 表面。

---

## 0. 本分支已落地内容（全部算数，禁止当不存在）

从 `cc6deec`（`26.2`）切出本会话分支后，下列提交**已经是仓库内容**。回退的只是 `5f6b9e7` 里被用户打回的 Iris 散弹注入和 `xBob` 缩放，不是整条分支。

| 提交 | 内容 | 下一任必须 |
|---|---|---|
| `8431e68` | Punchy! 持 TACZ/LR viewmodel 时走 blacklist，取消独立右臂与 walk/sprint/camera-lag 叠层 | 保留 mixin / plugin / `tacz.punchy.mixins.json` |
| `ce0d245` | 投掷物静止不再广播近战 `INPUT_IDLE`（官方手雷脚本字面量 `idle` 会取消拔销） | 保留 `LrTickAnimationEvent` 分流 |
| `c3acff7` | 官方 0.4.3：烟雾环境光、cook=`prepare+lifeTime`、`display_offset`、`entity_transform`、消耗品 Bedrock/Lua 渲染器 | 保留粒子/display/ConsumableItemRenderer |
| `305bed1` | `MeleeDisplay` record 补 `displayOffset` 组件（否则 `create()` 编不过） | 保留 |
| 本 HEAD | 温雷 `life>=0`；本审计文档 | 保留 fuse；负结果当证据用 |

失败尝试本身也是内容：`5f6b9e7` 证明「缩放 bob」和「多 hook Iris 编译入口」过不了用户复测。写在本文 §2，供下一任当禁区，不是「没做过」。

未复验、仍算本分支内容（禁止从提示词里消失）：

- 拉栓抖动修 = `ce0d245`，源码闭环，**未实机**。
- muzzle vs PAL：官方 0.4.3 第三人称枪口锁的病根是 LR `AdjustmentYRotModifier`；本仓没有这层，PAL 已对 `is3rdFixedHand` 跳过手臂。**NO-GO**，不要当本轮活。
- 近战 3P player_animator：源码已读，体积大、要内容包、可能和 PAL 抢层。**下一轮**，本轮不接入。

详见 `docs/records/LR_043_FOLLOWUP_20260826.md`。

---

## 1. 用户原词（验收口径）

| # | 现象 | 本轮结果 |
|---|---|---|
| 1 | 温雷进度条满 → 实体扔出后永不主动爆炸 | 源码级闭环；**保留**。未再被用户打回。 |
| 2 | 开光影后高倍镜目镜完全不裁剪 | **FAIL**。尝试已回退。 |
| 3 | 高倍镜/组合镜（含组合镜高低倍）枪体视角滞后、俯仰/水平偏转被放大，随视角移动不自然 | **FAIL**。尝试已回退。 |

不要把问题 3 写成「只有高倍 FOV」。用户明确组合镜低倍档也中。

---

## 2. 回退了什么、为什么回退

回退到 `305bed1` 的文件：

- `GunItemRendererWrapper`（去掉 `aimingViewLagMultiplier`）
- `BedrockAttachmentModel` / `ScopeMaskGeometry` / `ScopeMaskRenderer`（去掉 Iris 提前 flush）
- `IrisScopeMaskState` / `IrisShaderCreatorMixin` / `tacz.iris.mixins.json`
- 删除：`IrisScopeMaskPatch`、`IrisGlShaderMixin`、`IrisProgramBuilderMixin`、`IrisGlRenderPassMixin`

保留：

- `ThrowableItemEntity.tick`：`life >= 0 && tickCount >= life`
- `ThrowableItem.onThrow` 注释（`0` = 立刻炸，`-1` = 永不）

### 2.1 问题 3 试过什么（禁止再试）

本仓给第一人称 `xBob/yBob * 0.1` 和 root 偏移乘了 `1 - aim + aim/zoom`。  
用户复测：症状仍在。

姊妹仓 `b88cb11`（2026-08-25）**已经试过同类缩放并撤回**，理由原文：

> Keep the viewmodel sway identical to upstream. Do not scale it by aiming progress: the vanilla bob is already magnified by the ADS projection and applying a second ADS multiplier makes pitch and roll visibly larger than upstream.

官方 1.20.1 / 0.4.3 与回退后的本仓、当前姊妹仓，开镜时都是未缩放的 `* 0.1`。  
**再乘 aim/zoom、1/sqrt(zoom)、或 `(1-aim)` 都视为已知失败。**

### 2.2 问题 2 试过什么（禁止当「已修」再铺）

本仓误判 1：`shouldDisableScopeMaskUnderShaderPack()` 会关 Iris。  
事实：它只认 `sulkan`。Iris 下掩码仍登记。

本仓误判 2：把 `ShaderCreator.link` 参数下标钉死再 `require=0` 静默失败，于是扩成「见字符串就补丁 + drawIndexed 绑 uniform + submit 里提前画掩码」。  
用户复测：高倍目镜仍完全不裁。说明不是「再多钩几个编译入口」就能过。

姊妹仓有同一套 HAND + `tacz_ScopeMaskMode` uniform 桥，另外还有 **Fabric 专用** 的 `IrisScopePipelineCompat`（给镜内 PIP 单独 `tacz:scope_pip` 维度管线 + Voxy）。那是「光影下镜内世界」隔离，**不是** 视模目镜孔洞 discard 的已核 NeoForge 解。  
2026-07 姊妹提交「iris 加载的光影包已支持镜内渲染」指 PIP/镜内世界，不能当成问题 2 已 PASS。

---

## 3. 跨仓审计：成功修复怎么分

| 症状 | 官方 0.4.3 / 1.20.1 | 姊妹 Fabric 26.2 | 本仓 NeoForge 26.2 | 分配 |
|---|---|---|---|---|
| 温雷 `life=0` 不爆 | 扔出后实体超时炸 | 未作为本轮对照 | `life >= 0` **保留** | 本仓已收；勿回退 |
| 开镜 `xBob` 幅度 | 固定 `* 0.1` | 一度缩放，`b88cb11` 撤回，回到官方 | 缩放被用户打回，已回到官方 | **无成功修复可搬**。两边语义已对齐官方 |
| 「整枪随朝向转 / 斜向后坐侧漏」 | 1.21.1 约束位移无 26.2 基座 B | 用户 A/B：`ConstraintCompensateMode=3`（`v = Q·W·C·Wᵀ·v0`，Iris HAND 强制 mode 0）在体三项通过 | 另一套 `Bᵀ·C·B` 三明治，写成「终案」，**本用户未按高倍/组合镜验收** | **相关、未证明就是问题 3**。只许当假设做 A/B，不许当已修合并 |
| 开光影高倍目镜不裁 | stencil，无 26.2 Feature | 与本仓同族的 HAND uniform 桥 + Fabric PIP 隔离管线。无 NeoForge Iris 高倍目镜 PASS | 阶段边界离屏掩码；无光影路径未在本轮被打回；有光影高倍 **FAIL** | **两边都缺已核成功修复**。PIP 管线是 Fabric 表面，禁止整文件搬 |

结论：

1. 问题 3 的「缩放 bob」两边都失败。当前代码与官方/姊妹一致，剩下的差不在这一行。
2. 姊妹 mode 3 是**相邻病**的已核公式，可以当问题 3 的**第一个对照实验**，不是免费胜利。
3. 问题 2 没有可搬的已核实现。再改必须先证明「掩码纹理在 Iris HAND 采样点上是白的」以及「HAND 程序里 discard 分支真的在跑」。

---

## 4. 问题 3 下一刀该看哪里（不是 bob 系数）

开镜后 `scope_view` 被锁到相机原点。任何**写在锁定之后、或没被约束吃掉**的旋转，都会变成目镜在画面里的平移；世界 FOV 再按倍率缩小，高低倍和组合镜低倍档都会显脏。

已排除：单纯把 `xBob` 再乘一个瞄准系数。

仍活着的嫌疑人（按优先级）：

1. **约束位移公式**（`FirstPersonRenderGunEvent.applyAnimationConstraintTransform`）  
   本仓：`authored = Bᵀ·rawWorld`，再 `C`，再 `B`。注释自称修的是「北向正常、东西反偏、南向过压」。  
   姊妹：mode 0 plain 被用户选过「不转但斜向漏」；mode 1/2 注入「随朝向转」；mode 3 在姊妹 26.2 过了「不转 / 不漏 / 自然」。  
   Iris HAND 时姊妹强制 mode 0（`B=I`）。本仓用 `copyHandCameraRotation()`，Iris 时单位阵。
2. **物品 FOV vs 世界 FOV**（`CameraSetupEvent.applyGunModelFovModifying` / `viewsFov`）与锁定变换的组合。组合镜切高低倍换 `scope_view` + `viewsFov`，两边都会中。
3. **相机动画**已按 `1-aim+aim/√zoom` 衰减，视模 bob 不衰减。这是官方语义。不要再改 bob 去「对齐」相机，除非能证明官方高倍也不自然且用户要偏离官方。
4. Punchy 让出已接线，未在本轮复验。若未装 Punchy 仍中，不要先怪 Punchy。

---

## 5. 问题 2 下一刀该看哪里（不是再 hook 一次 link）

无光影：阶段边界掩码 + `scope_body.fsh` `SCOPE_MASK`。本轮用户没说这条坏。  
有光影：管线被 `assignPipeline(..., HAND)` 换掉，裁剪只剩注入的 GLSL + `IrisScopeMaskState` 每 draw 写 uniform。

必须先回答、禁止瞎改：

1. 有光影开镜时，日志有没有 `[TACZ Scope] Ocular mask drawn`？没有 = 掩码没画，先修 `inHandPass` / `IrisCompat.isHandRendererActive()` / `FeatureRenderDispatcher` 时机。
2. 调试预览里白色目镜形状是否跟着枪？形状在、仍不裁 = shader/uniform。形状无/钉在北 = ModelView 烘焙（`collectMaskGeometry` 已乘 submit 时 ModelView；不要在 mask pass 再乘一遍）。
3. 有没有 `[TACZ Scope] Injecting dormant scope-mask branch` 和 `Iris scope-mask bridge active`？都没有 = 注入或 `trySetup` 没挂上。有注入、mode 仍 0 = `resolveMode` 认不出 `tacz:pipeline/scope_body_clipped`。
4. 高倍才裁、低倍本来就不裁镜身（`SCOPE_SIGHT_CLIP_FIX` + `activeGroupIsScope`）。用户说的是高倍目镜，不要去关低倍门禁冒充修复。

禁止：

- 把 `shouldDisableScopeMaskUnderShaderPack` 扩成 Iris（等于承认失败并关掉裁剪）。
- 整文件复制姊妹 `IrisScopePipelineCompat`（Fabric `FabricLoader` / Voxy / 独立维度管线）。
- 在 `submit` 里无条件 `createRenderPass`（r51 在 pass 内切 target 会丢设备）。

---

## 6. 给下一任 AGENT 的统一提示词

下面整段可原样粘贴。不要再把问题 2/3 当「缺一个缩放/缺一个 mixin 下标」。

```
你在 TaCZ_Renovated（NeoForge 26.2，分支 arena/01a03b03-tacz-renovated）。先读 AGENTS.md、CHARTER.md、docs/records/SCOPE_IRIS_VIEWLAG_AUDIT_20260826.md 全文（尤其 §0 已落地清单）、docs/records/LR_043_FOLLOWUP_20260826.md。refab 只取游戏语义，禁止抄 Fabric API，禁止碰 tacz-port jar。不要声称未实机 PASS。

【本分支已有内容，全部算数，禁止当空白仓、禁止回退、禁止重做一遍】
- 8431e68 Punchy 让出（tacz.punchy.mixins.json + 四个 mixin + plugin）
- ce0d245 投掷物不再吃近战 INPUT_IDLE
- c3acff7 烟雾环境光、cook=prepare+lifeTime、display_offset、entity_transform、ConsumableItemRenderer
- 305bed1 MeleeDisplay.displayOffset 组件（编译所需）
- ThrowableItemEntity：life>=0 才超时；C4 -1 仍不炸
- 阶段边界 ocular mask、低倍 reticle-only / 高倍 viewmodel clip、ocular_ring 重画
- 本审计 §2 的负结果：缩放 xBob、Iris 多入口散弹注入，已复测失败
- 拉栓抖动修已落地、未实机；muzzle vs PAL 已判 NO-GO；近战 3P player_animator 已读源、留给下一轮。见 LR_043_FOLLOWUP

【仓库不是空白。上一任已经落地 Punchy / 0.4.3 / fuse / display。这一轮只补两个被打回的洞】
A. 开 Iris 光影包后，高倍筒镜目镜必须按目镜投影裁剪（镜内不该看到完整不透明目镜板/枪管穿镜）。无光影不要回退。低倍 sight 按上游 renderSight：不裁镜身，只约束准星。
B. 高倍镜与组合镜（高低倍两档都要测）开镜后，转视角时枪体不得出现被放大的滞后、俯仰、水平偏转。腰射保持官方手感。

【绝对不要做】
1. 不要再给 xBob/yBob 或 root offset 乘 aimingProgress、1/zoom、1/sqrt(zoom)。本仓 5f6b9e7 被用户打回；姊妹 b88cb11 已从同一条路撤回。官方就是未缩放 *0.1。
2. 不要再靠「多 hook 几个 Iris ShaderCreator/GlShader/ProgramBuilder/glShaderSource」当修复。本仓 5f6b9e7 已试，无效。
3. 不要把 shouldDisableScopeMaskUnderShaderPack 扩成 Iris。那是 sulkan 失败回退，不是修裁剪。
4. 不要整文件搬姊妹 IrisScopePipelineCompat / VoxyScopePipelineCompat。
5. 不要在 submit 中途无保护地切 RenderPass。
6. 不要动、不要重写 §0 清单里的已落地提交。
7. 不要为了「看起来稳」关掉约束或开镜定位。

【问题 B 调查顺序】
1. 对比本仓 FirstPersonRenderGunEvent.applyAnimationConstraintTransform 与姊妹 26.2(main) 同函数。姊妹默认 ConstraintCompensateMode=3（Q·W·C·Wᵀ，Iris HAND 强制 0），用户在姊妹仓三项在体通过；本仓是另一套 cameraR 三明治。先做可开关 A/B（默认保持本仓现状），用组合镜高低倍 + 纯高倍 + 纯机瞄走四个朝向和抬头低头。禁止静默改默认却声称已修。
2. 若 mode 3 语义在 NeoForge 上也消掉 B，再以游戏语义转写（用已有 copyHandCameraRotation，不要引入 Fabric 配置名除非必要）。写清和本仓旧三明治的逐项差异。
3. 若约束 A/B 无差，再查物品 FOV（viewsFov）与 scope_view 锁定、SWITCH_VIEW_DYNAMICS，不要回头改 bob。

【问题 A 调查顺序】
1. 先取证再改代码：开光影开高倍时日志是否有 Ocular mask drawn / Injecting dormant / Iris scope-mask bridge active。
2. 打开掩码调试预览：白斑是否随枪。不随枪 = 矩阵；随枪仍不裁 = HAND 程序没 discard 或 mode/sampler 没绑上。
3. 用 RenderDoc/日志证明当前 program 是否还有 tacz_ScopeMaskMode。没有就针对「当前 Iris 1.11.x + mc26.2 真正的 HAND 编译入口」做一处有 require 证据的注入，禁止 require=0 的散弹枪。
4. 证明 GlCommandEncoder.trySetup（或 26.2 等价 draw setup）确实在 HAND 那次 draw 上跑到 applyToGlRenderPass，且 resolveMode 对 tacz:pipeline/scope_body_clipped 返回 1。
5. 低倍不裁镜身的门禁保持 SCOPE_SIGHT_CLIP_FIX + activeGroupIsScope。

【完成标准】
- 只提交有证据的改动；失败实验写回本审计文档，不要留死 hook。
- 推到 arena/01a03b03-tacz-renovated。
- 用户没复测前不得写 PASS。
```

---

## 7. 本仓现状

相对会话基线 `cc6deec`，本分支**已经有** Punchy 让出、投掷物 idle 分流、0.4.3 display/cook/烟雾/消耗品渲染、`MeleeDisplay.displayOffset`、温雷 `life>=0`，以及本文。这些都算内容。

相对 `305bed1`，运行路径上只多了温雷 fuse；`5f6b9e7` 的 Iris/bob 尝试已从代码撤走，负结果留在本文当禁区，不是「没做过」。

- 开镜视模 bob = 官方未缩放 `* 0.1`。
- Iris 桥 = `ShaderCreator.link` 下标 5 + `trySetup` + ExtendedShader reset。已知脆弱；加宽钩子已复测无效。
- 约束公式仍是本仓 `Bᵀ·C·B` 三明治，未经本用户高倍/组合镜验收。
