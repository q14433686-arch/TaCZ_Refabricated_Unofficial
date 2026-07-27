# 变更清单（第 1–2 轮合并）

源码改动仅 12 个文件，全部集中在渲染对齐，未触碰业务逻辑。

## 第 1 轮

| 文件 | 改动 |
|---|---|
| `client/gui/GunSmithTableScreen.java` | 背景绘制从 `extractContents` 迁至 `extractBackground`（修复 UI/槽位错位） |
| `assets/tacz/models/item/modern_kinetic_gun.json` | 去掉 `minecraft:item/generated` parent（修复第一人称错位） |
| `assets/tacz/models/item/gun_smith_table.json` | 同上 |
| `assets/tacz/models/item/workbench_a/b/c.json` | 同上 ×3 |
| `assets/tacz/models/item/ammo.json` | 去 parent，保留上游原有 display 块 |
| `assets/tacz/models/item/attachment.json` | 同上 |

## 第 2 轮（P1）

| 文件 | 改动 |
|---|---|
| `client/renderer/other/HumanoidOffhandRender.java` | 空 TODO → 按 26.2 extract/submit 两段式实现背挂枪渲染 |
| `mixin/client/ItemInHandLayerMixin.java` | 修副手判定（`mainArm` 而非硬编码 LEFT）、补回 `isSelf=true` |
| `client/model/BedrockGunModel.java` | `renderAdditionalMagazine` 返回 null（修复副弹匣整棵子树被静默丢弃） |
| `client/renderer/item/AnimateGeoItemRenderer.java` | javadoc 指向真实第一人称入口 |
| `cn/sh1rocu/tacz/client/TaCZFabricClient.java` | 移除失效注册行，替换为说明注释 |
| ~~`client/event/FirstPersonRenderEvent.java`~~ | **删除**（死代码） |
| ~~`simplebedrockmodel/api/event/RenderHandEvent.java`~~ | **删除**（stub，无 fire 点） |

## 新增文档

- `PROGRESS_ROUND1.md` — 第 1 轮结论与证据
- `PROGRESS_ROUND2.md` — 第 2 轮结论、互斥文档裁决
- `HANDOVER.md` — 接手说明、26.2 三大渲染范式坑、代码地图
- `CHANGES_ROUND1-2.md` — 本文件

## 第 3 轮（实机反馈修复）

| 文件 | 改动 |
|---|---|
| `client/renderer/item/TaczDynamicItemModel.java` | EXTENTS ±1.5 → ±0.5，避免误入 OversizedItemRenderer PIP 路径（修复 GUI 图标空白） |
| `mixin/client/ItemInHandRendererMixin.java` | 新增 `submitArmWithItem` HEAD 拦截，对齐 SBM 注入点（修复第一人称位置/缩放 + 移动抖动） |
| `client/renderer/item/AnimateGeoItemRenderer.java` | firstPerson 分支改为 return，避免双重渲染 |
| `client/resource/pojo/display/block/BlockTransformParser.java` | **新增**，按 26.2 ItemTransform.Deserializer 逐行语义解析枪包 transforms |
| `client/resource/index/ClientBlockIndex.java` | 恢复 checkTransforms/getTransforms |
| `client/renderer/item/GunSmithTableItemRenderer.java` | 恢复应用 display transforms（修复装配台手持模型大 4 倍） |

新增文档：`PROGRESS_ROUND3.md`

## 第 4 轮（实机反馈修复）

| 文件 | 改动 |
|---|---|
| `util/RenderHelper.java` | 手臂渲染前后快照/还原共享 PlayerModel（修复第三人称残缺手臂） |
| `client/animation/statemachine/GunAnimationStateContext.java` | walk 距离改用插值重载 `position(partialTick)`（修复陆地移动抖动） |
| 10 个 GUI/tooltip 文件（43 处色值） | 6 位 RGB 补 `0xFF` alpha（修复说明文字不显示） |
| `client/renderer/item/TaczDynamicItemModel.java` | identity key 改为值语义（修复物品栏图标空白） |

新增文档：`PROGRESS_ROUND4.md`
