# TaCZ Refabricated 26.2 R2 release notes

**发布日期记录：2026-08-16**

**构建元数据：`1.1.8+fabric.26.2.R2`**

**目标基线：`origin/26.2(main)` / `99b472a6a8e1438f22a29abe8b3804b349cb5dfd`**

这是 Minecraft 26.2 Fabric 线的 R2 发布说明，不替代 `docs/archive/` 中的历史审计。
本 release 保持 `1.1.8` 为 SemVer 核心；`+fabric.26.2.R2` 是不参与版本谓词排序的 build
metadata，不能写成 `1.1.8-R2`。

## R2 内容

- **可替换弹药源 API**：新增 `com.tacz.guns.api.item.ammo` 的 `AmmoSource`、
  `AmmoSourceProvider` 和 `AmmoSourceRegistry.EVENT`。provider 按注册顺序选择首个非 null
  source；没有 provider 时仍走原 `IItemHandler`。查询只读，消费结果防御性 clamp 到
  `0..requestedAmount`，弹药箱耗尽会重置 ammo id。详见 [AMMO_SOURCE_API.md](AMMO_SOURCE_API.md)。
- **具名 gameplay hooks**：将 P0/P1 的客户端开火、换弹、拉栓和服务端 shooter 路径公开为
  可读的受保护 hook；`GunAnimationStateContext#hasAmmoToConsumeInEntity(Entity)` 取代对
  `lambda$...` 的依赖。`LocalPlayerShoot.SHOOT_LOCKED_CONDITION` 仍是同一静态单例，身份比较
  仍使用 `==`；历史 `INPUT_BOLT = "blot"` 未改。
- **P2-min Lua / 契约文档**：提取 `resolveScriptFunction(...)` 与 `runLuaCycleTask(...)`，但没有
  改变每个调用点的 fallback、参数、次数、异常或 cycle true/false 语义。补充 reload/heat fallback
  与服务端 charge 校验的契约 Javadoc，未放宽 finite、阈值、最大进度或网络抖动边界。
- **多格工作台与 Carry On**：B/C companion 不再拥有 block entity、菜单或 `BlockId`；普通和
  Carry On `setBlockAndUpdate` 放置都会恢复 HEAD/UPPER。C 继续保存 `half=lower|upper`，但用本地
  `TableHalf` 避开 Carry On 对 vanilla `DoubleBlockHalf` 的拒绝。完整矩阵见
  [CARRYON_COMPAT.md](CARRYON_COMPAT.md)。
- **内置 JEI/REI Ammo Query**：共享 `AmmoQueryEntry` 按 `sort` 再 id 排序；每种至少被一把枪
  使用的弹药有一条查询，前 60 把枪固定显示，其他枪组成 viewer 的 overflow 轮换组。新增语言资源
  位于独立的 `assets/tacz_ammo_query/lang/` namespace，未截断既有完整语言 bundle。
- **远程枪包同步后的 viewer 刷新**：cache 安装 → `ClientIndexManager.reload()` → 合并的
  `RecipeViewerReloadBridge.requestReload()` 全部在客户端事件循环完成。桥等待 level/player，JEI 与
  REI 同装时两者都尝试，轻量入口失败时每条连接最多一次 `reloadResourcePacks()` fallback，并在
  完成或异常后复位，避免 resource reload loop。

## 来源提交（只移植语义，不整栈 cherry-pick）

| 范围 | 1.21.11 R2 来源 |
| --- | --- |
| 弹药源 API | [`28aa9bba37b78fb8d9bca0769e9996863a55c707`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/28aa9bba37b78fb8d9bca0769e9996863a55c707) |
| P0/P1 hooks | [`ce9d4b2f9384e1030b53db58f8944f7f4d1839f2`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/ce9d4b2f9384e1030b53db58f8944f7f4d1839f2) |
| P2-min Lua / Javadoc | [`729df9816058d36988d3d6387df6af83b3a0d76a`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/729df9816058d36988d3d6387df6af83b3a0d76a)、[`c637e9c770ab459e695d7554b2d151ba5d434dfe`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/c637e9c770ab459e695d7554b2d151ba5d434dfe) |
| 工作台结构 | [`5b149f357efff53faa24159c801c1aff20cd435c`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/5b149f357efff53faa24159c801c1aff20cd435c) |
| Carry On layer | [`166cf676bb76a57deda37ef0df3fa3712f9d9b6a`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/166cf676bb76a57deda37ef0df3fa3712f9d9b6a)、[`3a02c226b37bc910defaa5d1f0462caac0509a74`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/3a02c226b37bc910defaa5d1f0462caac0509a74)、[`3ee41fa60873aea823edfff4f81464905a2ecf8c`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/3ee41fa60873aea823edfff4f81464905a2ecf8c) |
| Ammo Query | [`f488a822c92175c4a5930bbf671b3e4d802c7bcb`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/f488a822c92175c4a5930bbf671b3e4d802c7bcb) |
| viewer 刷新语义/调用点 | [`4a983253b557c4d6c6cd9a7159aaec8e4a2cdbc8`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/4a983253b557c4d6c6cd9a7159aaec8e4a2cdbc8) |

## 2026-08-16 联网核验

| 项目 | 结果 |
| --- | --- |
| 目标头 | `git fetch origin` 后 `origin/26.2(main)` 仍为 `99b472a6…`；没有目标基线之后的新提交。 |
| JEI | `gradle.properties` 的 pin 是 **30.13.0.86**（并已纠正 `build.gradle` 的陈旧 `.80` 注释）。[Modrinth Maven metadata](https://api.modrinth.com/maven/maven/modrinth/jei/maven-metadata.xml) 和 [JEI Fabric source](https://github.com/mezz/JustEnoughItems/tree/1cb7814331c90c5e5b24b5055a0741ab65f58f58/Fabric) 核验了 `mezz.jei.fabric.events.JeiLifecycleEvents.AFTER_RECIPES_UPDATED` 为 `Event<Runnable>`；其 listener stop/start plugin lifecycle。 |
| REI | `gradle.properties` 的 pin 是 **26.2.820**；[REI Maven metadata](https://maven.shedaniel.me/me/shedaniel/RoughlyEnoughItems-api-fabric/maven-metadata.xml) 可见该版本（更新的 26.2.821 同时存在）。[REI source](https://github.com/shedaniel/RoughlyEnoughItems/tree/2be20928abd9f1164fd9fd251268041c036b580f/runtime) 核验 `me.shedaniel.rei.RoughlyEnoughItemsCoreClient#reloadPlugins(MutableLong, ReloadStage)` 的两参数轻量入口；桥通过反射按该 descriptor 选择。 |
| Carry On | [26.2 branch](https://github.com/Tschipp/CarryOn/tree/26.2) HEAD `e50ddbc1c7461f381c62af5f4960db9d97751d16`/2.11.1 与 [公开 Fabric 26.2 2.11.0 文件 metadata](https://api.modrinth.com/v2/project/joEfVgkn/version/EzG8eAml) 已核验。签名、`DoorBlock.HALF` value-class 检查、`setBlockAndUpdate`、`ItemStackTemplate#create()` 调用点和数据标签的详细记录在 [CARRYON_COMPAT.md](CARRYON_COMPAT.md)。建议版本因此是 `>=2.11.0`。 |
| `ItemStackTemplate` | [Fabric 26.1 migration note](https://fabricmc.net/2026/03/14/261.html) 说明其为不可变的 item/count/DataComponentPatch 模板；R2 不写模板组件，而在 Carry On 2.11 `CarriedObjectRender#drawBlock` 的 `.create()` 后修改真实 `ItemStack`，在 item-model 提交前恢复 `BlockId`。 |

没有新增 Carry On、JEI 或 REI 依赖；Carry On 仅由 `suggests.carryon`、字符串 target、`@Pseudo`
和反射加载。旧的、已被 source-set exclude 的 `ConfigLoaderMixin` / `BlackList` 已删除。

## 验证状态

已执行：Java patch applicability / 三方合并审查（唯一手工冲突为 `LocalPlayerShoot`）、
`git diff --check`、JSON 解析、关键符号/排除检查、以及 26.2 源码层的 Carry On / JEI / REI
入口核验。

**未执行，不能宣称通过：** 此执行环境没有 `java`/`javac` 或 `JAVA_HOME`；因此
`./gradlew compileJava --no-daemon`、`test`、`build` 都不能启动，最终 JDK 25 编译和游戏启动矩阵
（未装 viewer、JEI-only、REI-only、Carry On 2.11.0）需要在具备工具链的环境完成。实际 Carry On
2.11.0 CDN jar 在本环境 TLS 下载失败；公开 artifact metadata、hash 和 upstream 26.2 源码描述符已
记录，但这不替代最终 jar 的运行验证。

### 最终游戏内清单

- 弹药源 provider 的 fallback、首个非 null、只读、partial/full/clamped consume、ammo box 清空，
  以及 dummy/creative/infinite/feed/Bolt 回归；
- 单发/连发/蓄力/dry fire/取消/战术换弹/拉栓，且 `RECOIL_DEBUG` 打开仍输出
  `TACZ Case08 RELOAD_START`、关闭不额外输出；
- 默认和枪包 A/B/C 工作台从任一半格搬起、原子放下、BlockId/菜单/模型保持；
- JEI 和 REI 各自验证 Ammo Query 的第三方包、排序、60 项 overflow、三语资源；
- 远程同步发生在 viewer 首轮注册后时的刷新、连续同步合并、断线清 pending、轻量 hook 故障的
  单次 fallback 与无 reload loop。

## FAQ：专服上 REI/JEI 作弊拿取枪械/弹药/配件显示紫黑块与 `item.*` 原始键（2026-08-22 增补）

**症状**：专用服务器上从 REI/JEI 拿取（或 `/give tacz:modern_kinetic_gun` 不带组件）得到的
枪械、弹药、配件显示缺失贴图，名称为 `item.tacz.modern_kinetic_gun` /
`item.tacz.attachment` 一类原始键；物品在 REI/JEI 列表内显示正常，单人/局域网正常。

**原因**：TACZ 的枪/弹/配件/工作台物品的内容 id 存放在 `minecraft:custom_data` 组件
（`GunId` / `AmmoId` / `AttachmentId` / `BlockId`）。服务器未安装 REI 时，REI 作弊给物
退回客户端拼装的 `/give` 命令，该命令只携带物品注册表 id、组部分恒为空
（REI `ClientHelperImpl#tryCheatingEntry` 源码 `tagMessage = ""`，`TODO 24w09a`
标注组件化后未适配），服务端拿到裸物品 → TACZ 读 `tacz:empty` → 名字回退 `item.*`
（无翻译键）→ 无模型。原版 Forge 与上游 Fabric 移植版行为相同，**非本移植版缺陷**。
（铁弹药盒仍显示 Iron Ammo Box，恰因其名称键在 mod jar 内。）

**解决**：
1. 专服安装与客户端同版本的 REI —— 之后作弊走 REI 网络包，组件完整；
2. 或用 TACZ 自带创造标签页 / 内置弹药查询、配件查询、工作台配方分类拿取；
3. `/give` 务必附带组件，如
   `/give @p tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"tacz:ak47"}]`。

JEI 客户端兜底走原版创造槽位包（携带组件）；若仅 JEI 也复现，反馈时附查看器与服务器版本。
完整源码推导见 `docs/DEDICATED_SERVER_GETNAME_AUDIT_2026_08_21.md` 第 8、9 节；
发布文案已同步（`docs/publish/{Modrinth,CurseForge,MCMOD}.md` 的 FAQ 一节）。

---

## 同步：姊妹仓 TaCZ_Renovated `arena/01a03b03-tacz-renovated`（2026-08-26）

以本仓 26.2 Fabric 环境为准，接收姊妹仓（NeoForge 26.2）该分支相对其 `26.2`
基线（`cc6deec`）的净增量。来源提交：`8431e68`（Punchy 让出）、`ce0d245`
（投掷物 idle 分流）、`c3acff7`（官方 0.4.3：烟雾环境光 / cook 全引信 /
`display_offset` / `entity_transform` / 消耗品渲染）、`305bed1`（MeleeDisplay
record 补组件）、`5f6b9e7` 仅保留温雷 fuse（其余被 `daf2df5` 回退）。

- `16e5fb1` 已落：LR 0.4.3 全套（ConsumableItemRenderer 与 display 通道、
  环境光烟雾、cook = prepare + 完整 lifeTime、`ThrowableItemEntity` 的
  `life >= 0` 超时、display 新键、消耗品 `items/consumable.json` 分流）、
  `FirstPersonAnimationCompat` 的 Punchy 检测日志与契约 javadoc；
- 本轮补齐（对齐姊妹最终形态）：Punchy 让出 mixin 迁入专用配置
  `tacz.punchy.mixins.json`（`required=false` + `PunchyCompatMixinPlugin`，
  检测到 `punchy` 才应用；从 `tacz.fabric.mixins.json` 移出，`fabric.mod.json`
  注册并在 `suggests` 声明 `punchy`）、四个 mixin 的完整 javadoc、
  `LrTickAnimationEvent` 类注释、记录文档 `docs/records/`、compile-check 工作流
  （temurin 25，日志回推 `build-reports/` 供离线沙箱读取）；
- 明确未同步：被用户复测打回的 Iris 高倍目镜裁剪与开镜 bob 缩放尝试
  （姊妹仓已回退，负结果与其跨仓审计见
  `docs/records/SCOPE_IRIS_VIEWLAG_AUDIT_20260826.md`）；neoforge.mods.toml
  的 optional 依赖（本仓等价物为 `fabric.mod.json` `suggests`）。

未实机验收，不标 PASS。
