# 进度报告 · 第 2 轮（P1）2026-07-25

环境：Temurin JDK 25.0.3 + Gradle 9.5.1 + Fabric Loom 1.17.17 + MC 26.2
对照源：`minecraft-merged-deobf-26.2.jar`（Vineflower 1.11.1 反编译）
上游对照：`Sh1roCu/TACZ-Refabricated` @ `1.21.1`

> 本轮处理第 1 轮列出的 P1 全部四项。所有结论均来自**反编译源码比对 + 上游逐行对照 + 字节码校验**。
> 仍然**没有实机画面验收**（沙盒 2 GB 内存、无 GPU）。

---

## 1. 重要澄清：推翻一个被反复传抄的错误结论

三份历史文档（`AUDIT_REPORT` §2.B、`FIX_SUMMARY_QUICK`、`STAGE1_COMPLETION_REPORT`）都把
**"第三人称持枪时手臂消失"** 归因于 `ItemInHandLayerMixin` 取消了左手 + `HumanoidOffhandRender` 是空实现。

**这个因果链不成立。** 反编译 `ItemInHandLayer` 可见：

```java
protected void submitArmWithItem(S state, ItemStackRenderState item, ItemStack itemStack,
                                 HumanoidArm arm, PoseStack poseStack,
                                 SubmitNodeCollector submitNodeCollector, int lightCoords) {
   if (!item.isEmpty()) {
      poseStack.pushPose();
      this.getParentModel().translateToHand(state, arm, poseStack);
      ...
      item.submit(poseStack, submitNodeCollector, lightCoords, ...);   // ← 只提交“手里的物品”
      poseStack.popPose();
   }
}
```

该方法**只负责渲染手中物品**；手臂本身是 `PlayerModel`/`HumanoidModel` 在实体模型阶段画的，
两者完全独立。取消它**不可能**让手臂消失，只会让**副手物品**不显示 —— 而这正是上游刻意的设计
（主手持枪时隐藏副手物品，改由 `HumanoidOffhandRender` 以"背挂"姿态重画）。

所以"手臂消失"如果真实存在，根因在别处（`PlayerModelMixin` / `InnerThirdPersonManager`
的第三人称姿态动画，或 PAL 迁移层），**不在本 mixin**。已在代码注释中写明，避免继续误导。

---

## 2. 本轮修复

### 2.1 `HumanoidOffhandRender.renderGun` 空实现 → 已按 26.2 实现

**它到底该做什么**（上游 1.21.1 原文）：渲染**背在身上的枪** —— 副手枪 + 快捷栏中未手持的枪。
不是渲染手臂。旧 TODO 注释把它描述成"offhand/hotbar gun rendering"是对的，但没人实现。

**26.2 迁移点**：上游用 `ItemRenderer#renderStatic(stack, ctx, light, overlay, poseStack,
MultiBufferSource, level, seed)`。26.2 已无此方法，实体层改为两段式：

| 阶段 | 26.2 API（均经 javap/反编译确认） |
|---|---|
| 取 resolver | `Minecraft#getItemModelResolver()` |
| extract | `ItemModelResolver#updateForTopItem(ItemStackRenderState, ItemStack, ItemDisplayContext, Level, ItemOwner, int)` |
| submit | `ItemStackRenderState#submit(PoseStack, SubmitNodeCollector, int, int, int)` |

这与 vanilla `ItemInHandLayer#submitArmWithItem` 结尾用的是同一个 `submit` 方法，链路对齐。

**两处刻意的实现选择**：
- 不用 `updateForLiving`：其 seed 为 `entity.getId() + displayContext.ordinal()`，同一实体上的
  多把枪（副手 + 多个快捷栏槽位）会撞 seed。改用 `updateForTopItem` 并把槽位号混入 seed。
- 26.2 实体层拿到的是 render state 而非实体，通过 `AvatarRenderState.id` 反查实体；
  GUI/展示柜等无真实实体的场景直接跳过。

坐标变换与 1.21.1 **逐行一致**（translate → `scale(-x,-y,z)` → 欧拉转四元数），只换提交方式。

### 2.2 `ItemInHandLayerMixin` 两个真实缺陷 → 已修

| 缺陷 | 说明 |
|---|---|
| **取消条件写错（会导致左利手玩家主手枪不渲染）** | 旧代码判 `arm == HumanoidArm.LEFT`，但 `LEFT ≠ 副手` —— 左利手玩家主手就是 LEFT，于是**主手的枪被取消渲染**。上游语义是"主手持枪 && 当前 arm 不是主手"。已改为按 `state.mainArm` 判定 |
| **`isSelf` 只置 false 从不置 true** | 上游在 `renderArmWithItem` HEAD 对"渲染对象是本地玩家"置 `true`，移植时丢了，导致第三人称抛壳/枪口火焰的自机判定恒为 false。已按上游补回（用 `AvatarRenderState.id == player.getId()` 判定） |

### 2.3 死代码清理 → 已删

- 删除 `com/tacz/guns/client/event/FirstPersonRenderEvent.java`
- 删除 `cn/sh1rocu/simplebedrockmodel/api/event/RenderHandEvent.java`（stub，无任何 fire 点）
- `TaCZFabricClient` 中被注释的注册行替换为说明性注释
- `AnimateGeoItemRenderer` 的 javadoc 从指向已删类改为指向真实入口

真实第一人称入口（已在第 1 轮确认）：
`客户端 ItemModel(tacz:dynamic_item)` → `TaczDynamicItemModel` 的 `SpecialModelRenderer`
→ `AnimateGeoItemRenderer#render` 的 `mode.firstPerson()` 分支。

### 2.4 副弹匣节点被静默丢弃 → 已修（本轮唯一影响画面的 legacy 残留）

`BedrockRenderSnapshot#capturePart` 的规则是：若 `FunctionalBedrockPart` 的 provider
**返回了 renderer 但它不是 `IFunctionalSubmitter`**，则计入 `skippedFunctionalNodes` 并
**直接 return** —— 连子节点都不再遍历。

`BedrockGunModel#renderAdditionalMagazine` 恰好返回了一个裸 `IFunctionalRenderer` lambda，
于是**副弹匣（`mag_additional`）及其整棵子树被静默丢弃、完全不显示**。

细看那个 lambda 的内容：画自己的 cubes → 递归画 children —— 这和快照遍历器**本来就会做的事完全一样**；
唯一额外语义是"顺带画 `magazineNode`"，而 `mag_normal` 本身就在模型树里会被正常遍历，
可见性由 `extendedMagHiddenRender` 控制。

**修复**：改为只做可见性判定、`return null`，把几何交还给快照遍历器统一处理 ——
与 `attachmentAdapterNodeRender` / `scopeHiddenRender` 等其它纯可见性钩子写法一致。

---

## 3. 裁决：`AUDIT_REPORT` vs `FIX_SUMMARY_QUICK` 的互斥结论

两份文档对 `ShellRender` / `MuzzleFlashRender` / `BeamRenderer` / `TextShowRender` /
`AttachmentRender` 的适配状态给出相反结论。逐个查证后，**两边都不完全对**：

| 组件 | 实际状态 | 裁决 |
|---|---|---|
| `ShellRender` | 同时有 `extract()`（collector）和 legacy `render()` | `FIX_SUMMARY` 对。**collector 路径已实现且是活路径** |
| `MuzzleFlashRender` | 同上 | `FIX_SUMMARY` 对 |
| `TextShowRender` | 同上 | `FIX_SUMMARY` 对 |
| `AttachmentRender` | 同上，且实现了 `IFunctionalSubmitter` | `FIX_SUMMARY` 对 |
| `BeamRenderer` | 有 collector 重载；无 collector 的旧重载确为 no-op | **两边各对一半**。活路径（`BedrockGunModel#submit` / `BedrockAttachmentModel#submit`）传的都是真实 collector，激光正常；只有已不可达的 legacy `render()` 路径会走 no-op |

**关键判据**：`AUDIT_REPORT` 说"旧 delegate fallback 会丢失配件"—— 该担忧**已不成立**，
因为承载它的 `BedrockModel#render(...)`（`@Deprecated`，方法体为空）以及
`BedrockGunModel#render(...)` / `BedrockAttachmentModel#render(...)`
经全仓 grep 确认**没有任何调用点**，是不可达死代码。活路径只有 `submit(...)`。

> 结论：这批组件里，**真正因 legacy 写法而丢画面的只有副弹匣一处**（已修，见 §2.4）。
> 其余 legacy 重载建议后续统一删除，但不影响当前画面。

---

## 4. 构建与验证

| 检查 | 状态 |
|---|---|
| `compileJava` | ✅ PASS |
| `build`（jar） | ✅ PASS，57.2 MB |
| 死代码类已从 jar 移除 | ✅ `FirstPersonRenderEvent` / `RenderHandEvent` 不在产物中 |
| 新类已入 jar | ✅ `HumanoidOffhandRender`(8,921 B)、`ItemInHandLayerMixin`(3,971 B) |
| **字节码引用校验** | ✅ `updateForTopItem` / `ItemStackRenderState.submit` / `Inventory.getSelectedSlot` / `AvatarRenderState.id` / `ArmedEntityRenderState.mainArm` 全部解析到真实 26.2 签名 |
| 实机画面 | ❌ 未做（沙盒无 GPU） |

---

## 5. TODO（交接用）

### P0 · 需实机验收本轮
- [ ] **左利手玩家**（设置里切主手为左手）持枪，确认枪正常渲染 —— 这是 §2.2 的关键判据
- [ ] 主手持枪 + 副手放物品，确认副手物品被隐藏、枪以背挂姿态出现在身上
- [ ] 快捷栏放多把枪，确认背挂显示且各枪独立（seed 修复的判据）
- [ ] 带副弹匣（`mag_additional`）的枪，确认副弹匣显示（§2.4）
- [ ] 第三人称开火，确认抛壳/枪口火焰对**他人**可见（`isSelf` 修复的判据）

### P0 · 第 1 轮修复仍待验收
- [ ] 工作台 UI 在不同 GUI Scale 下对齐
- [ ] 第一人称枪械对位、ADS 对位

### P1 · 下一轮
- [ ] 若"第三人称手臂消失"实测仍存在，排查 `PlayerModelMixin` / `InnerThirdPersonManager`（**不是** `ItemInHandLayerMixin`，见 §1）
- [ ] 统一删除已不可达的 legacy `render()` 重载（`BedrockModel` / `BedrockGunModel` / `BedrockAttachmentModel` / 各 functional renderer），消除后续误判来源
- [ ] 瞄具 stencil/PIP：当前 `submit` 路径降级为普通几何，镜内会看到枪体
- [ ] `ARCompat.shouldAccelerate()` 分支指向的 `renderAccelerated` 已随 legacy 路径失效，需明确废弃或重写
- [ ] 其余 GUI 组件在 26.2 分层坐标系下的偏移复查

### P2 · 工程卫生
- [ ] 修 `gradlew` 可执行位
- [ ] 补 README / LICENSE
- [ ] GameTest：断言 item 模型 display 为恒等、断言 `skippedFunctionalNodes == 0`
