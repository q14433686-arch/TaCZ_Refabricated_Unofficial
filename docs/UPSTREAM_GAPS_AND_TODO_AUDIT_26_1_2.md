# 26.1.2 上游功能缺口与 TODO 真伪审计

审计日期：2026-08-12

## 1. 对照范围

本轮不是按注释猜测，而是同时核对了代码、调用链和可选依赖的实际发布状态：

- 本仓库：PR #39，审计起点 `e43a3a9d`；
- TACZ-Refabricated `1.21.1`：`d2903554da039d2355920953a81447784a3f2be2`；
- TACZ 官方 `1.20.1`：`b43eb84c38e9768d8e73c8b14f0b845669704b38`；
- 资源归档基线：`58a1c5107ee82e02a055c6cc7b82be1537db6b62`；该点到当前
  Refabricated 只有一笔跨维度事件代码提交，没有资源改动；
- PAL `v1.2.5+26.1`：`6b2e002ba044132ec8490674bd4ba6b28f4dfc7b`；
- Controllable `v0.26.0+26.1.2`：`0b61e1e336edf48f8a18db74ee396c21db9f7230`；
- Shoulder Surfing `26.1.2-5.0.10`：`c1aa4d738deb88e4854288fff7ba39995e6822f9`；
- Carry On `26.1`：`e609245b4952e8705ed4e5957e81673e8785b0c9`，发布版 2.10.0。

## 2. 结论摘要

1. **枪械经验/等级不是本移植漏做。** 官方 1.20.1 与 Refabricated 1.21.1 都只有预留接口；
   `ModernKineticGunItem#getLevel/getExp/getMaxLevel` 全部恒为 0，全树没有经验写入点，
   没有 `GunLevelManager`，也没有服务端发送 `ServerMessageLevelUp` 的调用点。
2. **确实找到了三项被过期“无可用版本”注释错误关闭的上游兼容：**
   Controllable、Shoulder Surfing、Carry On。它们现在都有 Fabric 26.1.2 版本，本轮已恢复。
3. **枪包与 TACZ 自有内容没有整批漏带。** 资源 bundle + 显式资源覆盖了上游 TACZ 内容；
   唯一缺少的 `assets/tacz/models/item/ammo_box.json` 是旧物品模型入口，已由 26.1.2 的
   `assets/tacz/items/ammo_box.json` 动态模型取代。
4. **大量 TODO 是历史注释，不是功能缺口。** 枪模文字、枪口火光、普通激光束、热键 GUI
   取消和异步开火回主线程都已有完整实现；旧注释指向的是已废弃即时渲染路径。
5. **仍有少量真实缺口，主要是上游本身未设计完或内置 LRTactical 的边缘展示：**
   非爆头随机暴击没有数据契约；Fabric 没有通用 modded multipart parent API；
   LRTactical 自定义冷却遮罩、效果云 tooltip、受限音效与 flash shield 尚未完成。

## 3. 枪械等级的证据链

### 3.1 已存在的只是“壳”

- `IGun` 声明等级/经验查询接口；
- `GunItemDataAccessor` 能读取 `GunLevelExp`，但没有写入方法或调用点；
- `ClientGunTooltip` 原先无条件绘制等级；
- `ServerMessageLevelUp` 注册了解码器，但没有任何发送方；
- `GunLevelUpToast` 存在，但原先的调用代码被注释且引用不存在的 `GunLevelManager`。

### 3.2 两个上游均明确禁用

官方 `b43eb84c` 与 Refabricated `d2903554` 的 `ModernKineticGunItem` 都是：

```java
getLevel(int exp) -> 0
getExp(int level) -> 0
getMaxLevel() -> 0
```

所以 `0 (MAX)` 只是未完成原型泄漏到 UI，不代表有一个移植时遗漏的升级系统。

### 3.3 本轮处理

- 不凭空发明经验来源、等级曲线或属性成长，避免改变 TACZ 平衡与枪包契约；
- `maxLevel <= 0` 时不再绘制误导性的 `0 (MAX)`；
- 修正潜伏的区间计算错误：当前级经验应为 `exp - getExp(level)`，不是
  `exp - getExp(level - 1)`；这对当前禁用态无影响，但未来实现不会多算整整一级；
- 保留 level-up payload 以维持 API/协议形状，并明确记录它当前没有生产者。

## 4. 上游 53 个“本地缺失 Java 文件”的逐类核实

与 Refabricated 1.21.1 做路径集合比较，本地少 53 个 Java 文件，但没有 53 个功能漏项：

| 分类 | 数量 | 结论 |
|---|---:|---|
| 已由 26.1.2 原生 API/本地实现替代 | 19 | 方块爆炸覆写、动态物品模型、GUI 控件、玩家重生、复杂实体生成包、工作台材料解析等均有替代链路 |
| KubeJS Fabric 集成 | 19 | 26.1.2 只有 NeoForge KubeJS；Fabric 无可编译目标，保留无操作事件门面 |
| 旧 KosmX PlayerAnimator | 5 | 已由 PAL 1.2.5 的独立实现完整替换 |
| Accelerated Rendering | 4 | 没有 26.1.2 Feature Rendering 版本/API；普通 collector 渲染完整 |
| 旧 Controllable 实现 | 2 | “没有版本”的判断已过期，本轮按 0.26.0 API 恢复 |
| 旧 Carry On Java hook | 2 | 2.10.0 已改用 `carryon:block_blacklist` 数据标签，本轮用官方标签契约恢复 |
| OptiFine 探测 | 1 | Fabric 目标不使用 OptiFine；Iris/Sodium 有独立兼容 |
| Tweakeroo 空 mixin | 1 | 上游类本身无字段、无注入、无行为，删除不损失功能 |

主要替代关系：

- `IBlockExtension + BlockBehaviourMixin` → `StatueBlock#onExplosionHit` 原生覆写；
- `ArmorStandMixin` → `AbstractMinecart#isRideable` 直接注入；
- `ServerEntityMixin + IEntityWithComplexSpawn` → `IEntityAdditionalSpawnData` payload；
- `RecipeManagerMixin + NBTIngredient` → `TableRecipeManager` 与 Partial/Strict NBT ingredient；
- `FirstPersonRenderEvent` → `FirstPersonRenderGunEvent` + collector 动态物品模型；
- `ExtendedSlider/ImageButton` → `ForgeSlider/TaczImageButton`；
- `RenderTargetStencil` → 26.1.2 原生 scope depth-aperture 管线。

## 5. 资源完整性核实

有效资源由两层组成：

1. `resources/tacz-26.1.2-source-and-resource-bundle.jar`；
2. `src/main/resources` 的 26.1.2 覆盖文件（同名时覆盖 bundle）。

对 Refabricated 当前资源树做集合比较后：

- `assets/tacz/custom/tacz_default_gun/**` 没有内容遗漏；
- 动画、模型、贴图、声音、枪/配件/弹药 index 与 display 均在 bundle 中；
- 缺少的 341 个 `data/c/tags/{block,item}/**` 是上游附带的通用 Convention Tags 集合，
  不是 TACZ 内容；26.1.2 由 Fabric API 和本仓库所需的显式 `data/c` 标签提供；
- 旧 `models/item/ammo_box.json` 被新 `items/ammo_box.json` 取代；
- `kubejs.plugins.txt` / `kubejs.classfilter.txt` 随 Fabric KubeJS 集成禁用而不打包；
- `pack.mcmeta` 由现代 mod 资源包元数据路径处理，不是枪包内容缺失。

## 6. TODO / “未实现”注释真伪

| 位置 | 原注释判断 | 人工核实结果 | 本轮处理 |
|---|---|---|---|
| `TextShowRender#render` | 枪模文字未实现 | **假/过期**：`extract` 已用 `collector.submitText` 完成 | 删除废弃即时路径与 TODO |
| `MuzzleFlashRender#doRender` | 枪口火光未实现 | **假/过期**：collector 路径提交普通层和 glow 层，且已有 scope 外裁剪 | 删除废弃即时路径 |
| `BeamRenderer` collector | 调用者不给 collector，激光不画 | **假/过期**：枪和配件内置调用点全部传 collector | 改成准确的 legacy-overload 说明 |
| `BeamRenderer` AR | 等待 AR | **真**：AR 没有 26.1.2 Feature Rendering API | 保留普通完整路径，明确只缺可选加速 |
| `RenderHelper` | 等待 BufferUploader 替代 | **假/已完成**：GUI/模型分别走 GuiGraphics/collector | 删除 TODO |
| `LootTableInjectorModifier` | 类注释同时声称静态 registry 可用和不可用 | **错误/自相矛盾**：loot table 只在 reloadable layer | 重写为最终正向候选查表结论 |
| `PreventsHotbarEvent` | 行为待测 | **假/已接线**：`GuiMixin#extractRenderState` 明确消费结果 | 改成调用链说明 |
| `LocalPlayerShoot` | 回主线程待检查 | **假/必要代码**：burst scheduler 在异步线程，事件/声音/动画必须回客户端线程 | 改成线程契约说明 |
| `LivingEntityShoot` | 背包扣弹待找简化方法 | **假/优化备忘**：已统一调用 AbstractGunItem 提取逻辑 | 改成行为说明 |
| `ModernKineticGunScriptAPI#getBoltByInt` | enum 也许可直接给 Lua | **不是缺口**：整数接口是外部枪包 ABI | 明确保留理由 |
| `AnimationConstant` | 空 TODO | **不是缺口**：通用预留命名空间，实际常量在 `GunAnimationConstant` | 改成事实说明 |
| `EntityKineticBullet` multipart | 等待 `PartEntity` | **部分解决**：通用 modded parent 仍缺，但 vanilla EnderDragon 已特判 | 去掉误导的 NeoForge 注释，记录 fail-safe 行为并增加原版特判 |
| `EntityKineticBullet` 普通暴击 | 尚未判定 | **上游未设计**：数据 schema/event 没有非爆头暴击字段 | 明确只有 headshot multiplier |
| `ServerMessageLevelUp` | 等升级逻辑后解封 | **上游未实现**，不是本移植 TODO | 改成协议预留说明，隐藏 0(MAX) |
| `ControllableCompat` | 没有 26.1.2 版本 | **假/过期**：0.26.0 Fabric 已发布 | 恢复按键、持续射击轮询和震动 |
| Shoulder Surfing stubs | 没有 26.1.2 版本 | **假/过期**：5.0.10 Fabric 已发布且 API 已改事件总线 | 迁移插件和准星判断 |
| Carry On exclusions | 没有 26.1.2 版本 | **假/过期**：2.10.0 Fabric 已发布 | 添加官方 block blacklist tag |
| ImmediatelyFast stub | mod 不存在 | **假/过期**：mod 存在；但旧 batching API 已删除 | 保持无需专用 API 的空门面并更正说明 |
| MAE basic API stub | MAE不可用 | **假/过期**：本轮已重新打包真实 MAE 1.1.1 消除 ABI 冲突 | 删除假类，使用内联依赖 |
| 弹药盒染色 | 配方缺失 | **真**：1.21.1 变动导致旧 26.1.x 失效 | 本轮补上 26.1.2 专属数据配方和兼容 tag |
| Blood Strike 联动画 | 普通画作 | **真**：serializer normalizer 直接丢弃 id 后成分 | 本轮已增加 modern stack component 旁路恢复 |
| Iris Shadow Pass | 未初始化 | **真**：被废弃的 supplier 门面截断 | 本轮重新接入 26.1.2 的 shadow pass supplier |
| KubeJS facade | 等 Fabric 版本 | **真**：26.1.2 发布物是 NeoForge-only | 保留原生 TACZ 事件，KubeJS mirror 无操作 |
| LRT throwable index | 网络未同步 | **假/过期**：已有 cache、S2C payload、登录/重载发送及客户端重建 | 更正文档 |
| LRT 类型/物品/API | 只完成第一步 | **假/过期**：五种投掷类型、四个基础物品、近战/消耗品/API 均已接线 | 更新类级说明 |
| LRT custom cooldown overlay | 未同步 | **真**：无专用 S2C cooldown payload/overlay | 保留为明确展示限制 |
| LRT bounce/death sounds | 未实现 | **真且有授权原因**：原作音效 ARR，本仓库不分发 | 改成审计后限制说明 |

## 7. 当前仍真实存在、但不应伪装成“本轮回归”的限制

### 核心 TACZ

- 枪械等级系统是上游未完成 API，不能在没有产品规则的情况下擅自补一套；
- 非爆头随机暴击没有数据协议；
- 对 modded multipart entities，Fabric 没有 NeoForge `PartEntity#getParent` 的通用等价物（但 vanilla EnderDragon 已特判）；
- Accelerated Rendering 与 Fabric KubeJS 没有可用 26.1.2 目标。

### 内置 LRTactical

- `flash_shield` 未移植；
- 自定义冷却没有客户端物品栏遮罩，但服务端判定正常；
- 效果云专属 tooltip 未迁移；
- JSON 自定义尾迹粒子反序列化未迁移；
- 手雷弹跳/死亡原作音效因资源授权不分发；
- 屏幕震动与爆炸破坏力倍率仍缺少对应协议/原版扩展点。

这些项目均已从“过期阶段注释”中分离出来。以后看到功能异常时，应先按本表判断是已完成链路、
可选依赖缺失，还是有明确后果的真实限制，避免再次把历史 TODO 当作现状。

> 注：源码中仍有不少“26.2 API 变化”字样，它们记录的是这批代码最初迁移到 Feature
> Rendering/无混淆 API 时的技术来源，不等于“当前构建目标是 26.2”或“功能未完成”。本轮只改
> 已被调用链证伪的状态描述，不做无证据的全局版本号替换。
