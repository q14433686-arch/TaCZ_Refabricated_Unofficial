# 上游内容 / 功能对齐与 TODO 真伪审计（2026-08-12）

> **当前结论以本文为准。** 本次没有把注释当证据：每项均对照
> `Sh1roCu/TACZ-Refabricated` 的 `1.21.1` 分支
> `d2903554da039d2355920953a81447784a3f2be2`，并反查本仓调用方、资源消费方或
> Minecraft 26.2 字节码。旧的 `PENDING_AUDIT_2026_08_11.md` 只作为过程记录。

## 1. 结论摘要

1. **枪械经验等级确实没有做完，但不是本移植漏移植。** 上游本身只有一套休眠脚手：
   NBT 读口、tooltip、toast 类与 S2C 包都在，实际 XP 写入、等级曲线、上限、属性加成、
   升级包发送方全部不存在。`ModernKineticGunItem` 上游原样对等级/经验/上限返回 0。
2. 全量资源文件名对照找到两个真正被忽略的上游细节，均已修：
   - **弹药盒染色语义**曾漏带。上游 1.21.1 只有 `minecraft:dyeable` tag；
     26.2 已把通用染色改成逐物品 `minecraft:crafting_dye` 数据配方，单补 tag 仍无效。
     现同时保留兼容 tag，并新增 `ammo_box_dyed.json`，模型原有 dye tint 才真正可用。
   - **Blood Strike 联动画**配方在移植时被降成了普通 `minecraft:painting`，名称与
     `ENTITY_DATA.variant=tacz:blood_strike_1` 被剥掉；现已让 custom result 同时接受
     26.2 的 `id + components` 和旧枪包的 `item + nbt`，并恢复专属画输出。
3. Java 文件名对照没有发现被整块漏掉的 TACZ 主功能。上游 760 个 Java 文件中，
   本仓有 739 个同路径文件；缺的 21 个全部能映射到 26.2 替代实现或已消失 API，
   不是“忘了复制文件”。详见 §3。
4. 发现并补齐一个真实浅兼容缺口：**Shoulder Surfing Reloaded 已有 26.2 Fabric 版**，
   原“26.2 尚不可用”的桩已经过时。现已迁移到其 5.x event-bus plugin API：
   TACZ 枪被识别为 adaptive aiming item，肩扛视角且未 free-look 时允许 TACZ 准星。
5. `EntityKineticBullet` 的 multipart TODO 不必等待 Fabric 提供 Forge `PartEntity`：
   26.2 原版 `EnderDragonPart` 有公开 `parentMob`。现已恢复末影龙部件→本体归属，
   命中部件仍负责接伤害，本体负责击杀事件、击退与无敌帧记账。
6. 发现 Iris 初始化入口被旧注释误关：其余 Iris 兼容虽在跑，
   `IS_RENDER_SHADOW_SUPPLIER` 却从未初始化，导致 `isRenderShadow()` 恒 false。
   现已恢复反射式 `IrisCompat.initCompat()` 注册（无硬依赖）。
7. 发现本仓一边依赖 MAE 1.1.1，一边又用同包名的空 `Pose/DummyPose` 覆盖它；
   stub 缺少真实接口的 `getBoneTransforms()`，存在二进制不兼容风险。现删除假 stub，
   将真实 MAE 库作为 nested dependency 打包。
8. 源码中的显式 TODO 从 24 处清到 6 处；剩余 6 处都是外部兼容阻塞，
   不再混入“历史上曾有 TODO”“已实现但忘删”“纯重构建议”。

---

## 2. 枪械经验等级：准确状态

### 2.1 存在的只是接口与 UI 壳

| 证据 | 当前行为 |
|---|---|
| `GunItemDataAccessor.GUN_EXP_TAG = "GunLevelExp"` | 只读；全仓无生产写入方 |
| `IGun#getLevel/getExp/getMaxLevel` | API 声明存在 |
| `ModernKineticGunItem` 三个实现 | 上游与本仓均固定 `return 0` |
| `ClientGunTooltip` | 因 `level >= maxLevel`，显示 `0 (MAX)` |
| `ServerMessageLevelUp` | 已注册 codec/receiver，但全仓无发送方 |
| `GunLevelUpToast` | 26.2 渲染已移植，但唯一创建代码仍封在注释块中 |
| `GunLevelManager` | 上游与本仓都不存在；注释块引用的是一个从未落地的设计类 |

因此“等级 0（MAX）”不是一个正在工作的单级等级制，而是**未完成系统的可见残留**。
网络上另有第三方 *TACz Weapon Leveling* 扩展，反而说明玩家实际使用的经验系统来自附属，
不是 TACZ 1.1.8 本体这套脚手。

### 2.2 为什么本次不擅自补等级系统

缺的不是一个 `setExp`：至少还要决定 XP 来源（命中/击杀/伤害）、曲线、上限、死亡/复制
规则、不同枪械实例的继承方式、属性加成范围、客户端同步与事件 API。上游注释还预想了
`DAMAGE_UP_LEVELS`，却没有给任何数值。自行填一套会从“忠实移植”变成玩法新增。

**定案：标为 `UPSTREAM-INCOMPLETE[gun-level]`，不把它算作 26.2 移植回归。**
若以后要做，应单开设计议题，而不是“解封 toast”就宣布完成。

---

## 3. 上游文件级对照

### 3.1 Java

- 上游：760 个 `.java`
- 本仓（审计开始时）：891 个（含 26.2 适配层与内置 LRTactical；本轮删除 2 个假 MAE stub 后为 889）
- 同路径：739 个
- 上游有、本仓同路径没有：21 个

这 21 个的归类：

| 类别 | 文件 | 结论 |
|---|---|---|
| 旧 stencil | `RenderTargetStencil`、`RenderTargetMixin` | 26.2 无 stencil API；已由 scope-mask target/shader 重写接管 |
| 旧 GUI API | `AbstractSliderButtonAccessor`、`ExtendedSlider`、`ImageButton` | 目标成员/即时 GUI 已变；现有 `ForgeSlider` / `TaczImageButton` 接管 |
| 旧第一人称入口 | `FirstPersonRenderEvent` | 26.2 由 `ItemInHandRendererMixin` + collector 提交接管 |
| 旧实体附加出生包 | `ServerEntityMixin`、`AdvancedAddEntityPayload`、`IEntityExtension`、`IEntityWithComplexSpawn` | 由 `IEntityAdditionalSpawnData` + 精确坐标生成包接管 |
| 旧配方兼容 | `RecipeManagerMixin`、`NBTIngredient` | 由独立 `TableRecipeManager` 与 Partial/Strict NBT ingredient 接管 |
| 旧重生事件 | `ClientPacketListenerMixin` | 注入点消失；由客户端逐 tick 玩家引用替换检测接管 |
| 旧爆炸接口 | `IBlockExtension`、`BlockBehaviourMixin`、`ExplosionAccessor` | 26.2 Explosion 已改接口；雕像在 block override 中按新 API 处理 |
| 旧 FOV/手部 accessor | `GameRendererAccessor` | 26.2 render-state 路径已有直接替代 |
| minecart 辅助 | `ArmorStandMixin` | 26.2 有 `AbstractMinecart#isRideable`；目标车通过当前 mixin 返回 false |
| 外部兼容 | Controllable binding、Tweakeroo 空 mixin | 目标 API 不可用/原文件本来就是空壳 |
| 空配置壳 | `PreLoadModConfig` | 上游文件本身整类注释，无行为 |

### 3.2 Resources

初始文件名差集为 346：

- 341 个上游附带的全量 `data/c/tags/**` 通用标签镜像；本仓只保留 TACZ 实际引用的
  子集，并依赖 Fabric conventions，其余不是 TACZ 内容；
- 2 个 KubeJS metadata（KubeJS 26.2 兼容层仍外部阻塞）；
- 1 个旧 `models/item/ammo_box.json` overrides 文件（26.2 已由
  `items/ammo_box.json` 的 select 模型替代）；
- 1 个 `pack.mcmeta`（Fabric mod 容器不靠它发现内置资源）；
- **1 个真正漏项：`data/minecraft/tags/item/dyeable.json`。** 本次补回后又核对
  26.2 `DyeRecipe` 字节码与 vanilla 数据，确认它已不消费通用 dyeable tag，故额外新增
  逐物品 `minecraft:crafting_dye` 配方；否则“补了文件”仍是假修复。

共同存在但内容变化的 242 个文件中，绝大部分是必需的 26.2 语法迁移：
172 个工作台 recipe ingredient 从对象式改为字符串/tag、21 个语言文件只多移植配置键、
物品模型改为新 item-model 格式。人工抽查非机械变化后，找到 Blood Strike 画作组件丢失，
已修。

---

## 4. TODO / 假待办逐项裁决

### 4.1 仍然是真的（显式 TODO 共 6 行）

> 原列于此的 `CustomItemCoolDowns` 已完成：`ServerMessageCustomCooldown` 同步起止，
> `GuiGraphicsExtractorMixin#itemCooldown` 叠加分类遮罩，因此源码 TODO 已删除。

| 项 | 真伪与影响 |
|---|---|
| Controllable | **真（外部阻塞）。** 这里指 MrCrayfish Controllable，不是已有 26.2 版的 Controlify |
| Accelerated Rendering（4 处同一件事） | **真（外部阻塞）。** Fabric fork最高只到 1.21.1；普通渲染路径不受影响 |
| KubeJS event bridge | **真（外部阻塞）。** 26.2 API/构建未接回，事件本体仍在，只是不投递给 KubeJS |

### 4.2 上游确实未完成，但不应伪装成本移植 TODO

| 项 | 裁决 |
|---|---|
| 枪械等级/经验 | 整套系统休眠，见 §2 |
| 非爆头“暴击 flag” | 上游没有判定公式，事件与 S2C wire format 也没有字段；不能只补一个 if |
| glTF CUBICSPLINE | 上游 `Spline` 是 TODO；本仓旧“线性基础实现”不是真 spline，且 loader 对 `CUBICSPLINE`→`SPLINE` 命名、切线三元组转换也未完成。内置 Bedrock 动画不走此路径 |

### 4.3 原注释错误/过时，已更正

| 位置 | 旧说法 | 代码核实后的事实 |
|---|---|---|
| `ThrowableIndexManager` | 网络同步未实现 | `networkCache → ServerMessageSyncLrPack → fromNetwork` 已完整在役 |
| `AbstractGunItem#dropAllAmmo` | 已改 LivingEntity、已退膛内弹 | 签名仍为 Player；只处理弹匣计数；Javadoc 本来就说不退膛内弹 |
| `SlotItemHandler#isSameInventory` | “修复 26.2” | 26.2 `Slot` 没有此方法，非 override、仓内零调用；只是 legacy helper |
| `LocalPlayerShoot` | “需要检查” | 定时线程→客户端事件循环是必须的线程切换，调用链完整 |
| `PreventsHotbarEvent` | “需要测试行为” | 已由 `GuiMixin` 在 `Hud#extractRenderState` 调用，确实负责容器界面后方 HUD 隐藏 |
| `ModernKineticGunScriptAPI#getBoltByInt` | 待测试 enum | int 版本是旧 Lua 脚本兼容 API；与 enum 版本并存合理 |
| `LivingEntityShoot#consumeAmmoFromPlayer` | TODO | 虚拟备弹/物品栏两条权威扣除链完整；仅有去重重构机会 |
| `RenderHelper` stencil 开关 | 待重写 | 仓内零调用，scope 已走掩码；已删除这组死 no-op |
| `ImmediatelyFastCompat` | 26.2 没有该 mod | 26.2 版存在；消失的是旧公开 batching API。新 feature-rendering 路径无需旧手动断批 |
| `CompatRegistry` | Iris/CarryOn 都不可用，Iris init 一起注释 | Iris 26.2 在役且桥接为反射；误关导致 shadow supplier 恒 false，现只恢复 Iris；Carry On 继续等待 |
| `IrisCompatNewly/Legacy` | “no-op / Iris 不兼容 Vulkan” | `isRenderShadow` 实际有完整反射调用；头注错误，已改 |
| 本地 MAE `Pose/DummyPose` | “MAE 26.2 不可用” | build 已依赖 MAE 1.1.1；stub 反而少真实接口方法并覆盖依赖，已删除并 bundle 真库 |
| SimpleBedrockModel 事件/接口 | 一律叫“stub” | 事件有真实发射/订阅，renderer 有真实实现；准确说法是 26.2 最小 ABI replacement |
| LRTactical 多处阶段注释 | 投掷索引/五类型/动画/Detonator 尚未移植 | 四项均已在役；tooltip/使用进度/冷却遮罩也已于后续 26.2 反馈层批次补齐 |
| LRTactical bounce/death sound | 普通 TODO | 属 ARR 音源与未移植通用自定义音效通道的有意边界，不是把代码补两行就能交付 |

### 4.4 本次直接完成

- 末影龙部件归属：`EnderDragonPart.parentMob`。
- Shoulder Surfing Reloaded 5.x plugin + 准星判定。
- Iris shadow-pass 检测初始化重新接线。
- 真实 MAE 1.1.1 替代同包名空 stub，并作为 nested dependency 打包。
- 弹药盒兼容 tag + 26.2 `crafting_dye` 配方（只补旧 tag 在 26.2 不生效）。
- Blood Strike 专属画作配方组件。

---

## 5. 当前仍应公开的限制（按玩家可见性）

1. **Player Animation Library**：准确症状不是“稳态持枪手臂持续错形”，而是
   **趴姿→站立后，下一次切枪的约 8 tick 第三人称 crossfade 使用了脏姿态**；切完后的
   稳态持枪不是病灶。26.1.2 的 `e43a3a9d` 有效，但逐字移植到 26.2 无效；随后尝试的
   gun-to-gun GunDraw 三层硬复位也经用户实测无效，已回退并按用户决定挂起。
2. **LRTactical 仍是部分内置框架**：flash shield、完整 consumable 动画/模型、专属
   ARR 音效/素材等未全量移植；tooltip、使用进度 HUD 与分类冷却遮罩已经补齐。
3. **Accelerated Rendering / KubeJS / Controllable**：等待目标 26.2 Fabric API/构建；
   不影响这些 mod 不存在时的 TACZ 主流程。
4. **枪械经验等级与普通暴击**：上游未完成，不应写成“本移植未来必补”的承诺。
5. **第三方 glTF CUBICSPLINE 动画**：上游 loader/interpolator 未完成；内置默认枪包
   使用 Bedrock 动画，不受影响。

## 6. 验证建议

- 弹药盒 + 任意染料放工作台，确认可得到带 `minecraft:dyed_color` 的弹药盒且模型变色；
- 枪械制造台合成 Blood Strike 画，确认名称为联动画、放置后固定使用
  `tacz:blood_strike_1`，不是随机普通画；
- 安装 Shoulder Surfing Reloaded 26.2-5.0.7，肩扛视角持枪确认准星显示，按住
  free-look 时不强制 TACZ 准星；
- 射击末影龙头/翼部，确认伤害、击杀事件/命中反馈归属龙本体。
