# TaCZ Refabricated 26.1.2 R2 更新报告

> 日期：2026-08-16
>
> 发布版本：`1.1.8+fabric.26.1.2.R2`
>
> 目标环境：Minecraft 26.1.2 / Fabric / Java 25
>
> 目标基线：`origin/26.1.2` (`6c409eea0cfe01e070d0ed3c921b63a7a96cb50d`)
>
> 性质：非官方社区移植，不隶属于 TACZ Dev Team

## 一、版本定位与发布元数据

R2 是 26.1.2 移植线的第 2 个功能迭代。它把 1.21.11 R2 的全套功能语义完整移植到 26.1.2 平台上，同时严格保留目标分支已有的 Java 25、非混淆 Loom、渲染管线、LRTactical 内置支持与既有审计报告。

- **SemVer 核心与构建元数据**：`1.1.8+fabric.26.1.2.R2`（保留 `1.1.8` 基线与 `+` 构建元数据，确保枪包 `">=1.1.8"` 约束正常通过）；
- **产物文件名**：`TACZ-Refabricated-26.1.2-1.1.8+fabric.26.1.2.R2.jar`。

---

## 二、功能移植清单

### 1. 可替换弹药源 API (AmmoSource API)
- **API 包名**：`com.tacz.guns.api.item.ammo` (`AmmoSource`, `AmmoSourceProvider`, `AmmoSourceRegistry`);
- **适用场景**：第三方模组（如女仆 Backpack、自定义 Entity 仓储）无需混淆侵入 TACZ 内部逻辑，即可通过事件注册自定义弹药读取与消耗逻辑；
- **回退机制**：按照注册顺序首个非 `null` 胜出；无 Provider 时安全回退至原 `IItemHandler` + `IAmmo`/`IAmmoBox` 逻辑；
- **文档**：详见 [`docs/AMMO_SOURCE_API.md`](AMMO_SOURCE_API.md)。

### 2. P0/P1 具名 Gameplay Hooks
- 重构开火、换弹、拉栓、换弹打断与射击间隔判断中的匿名内部类与 Lambda，提供稳定可审查的具名方法；
- 暴露 `GunAnimationStateContext#hasAmmoToConsumeInEntity(Entity)` 替代编译器生成的 lambda，方便第三方扩展；
- 保持 `LocalPlayerShoot.SHOOT_LOCKED_CONDITION` 静态单例不变。

### 3. P2-min：Lua 助手与契约 Javadoc
- 在 `ModernKineticGunItem` 与 `ModernKineticGunScriptAPI` 中统一命名 `resolveScriptFunction` 与 `runLuaCycleTask` 助手方法；
- 补全 `LivingEntityAmmoCheck`、`LivingEntityShoot` 与 `ModernKineticGunItem` 的服务端校验与 Fallback 契约 Javadoc。

### 4. 多格工作台结构与 Carry On 2.10 正向兼容
- **多格结构修复**：仅 root 拥有 `GunSmithTableBlockEntity`，companion 格不拥有菜单与 `BlockId`；`onPlace` 统一在所有放置路径恢复 companion (B 恢复 HEAD, C 恢复 UPPER)；
- **Carry On 规避**：`GunSmithTableBlockC` 的 `half` 属性改用内部 `TableHalf` 枚举（序列化名保持 `"lower"`/`"upper"`），绕过 Carry On 对 vanilla `DoubleBlockHalf` 的通用拒绝规则；
- **双向拾取与原子放置**：从 B 的 HEAD 或 C 的 UPPER 发起搬运会自动重定向至 root 拾取；放置前做 Companion 位置边界、交互权限、可替换性与实体碰撞预检，失败时原子撤销并播放音效；
- **渲染修复**：`CarryOnRenderHelperMixin` 在 Carry On 生成临时渲染栈后，对 `ItemStackTemplate` / `ItemStack` 补全 TACZ `BlockId`；
- **文档**：详见 [`docs/CARRYON_COMPAT.md`](CARRYON_COMPAT.md)。

### 5. 内置 JEI / REI 弹药查询 (Ammo Query)
- 新增内置 `AmmoQueryCategory` 与 `AmmoQueryEntry`；
- 在 JEI/REI 中选择任意 TACZ 弹药即可查询所有适用该弹药的已加载枪械，无需另装第三方模组；
- 按枪 `sort` + `id` 排序，前 60 把固定显示，其余走 overflow 轮换组；
- 多语言资源包含 `en_us`, `zh_cn`, `zh_tw` (独立存于 `assets/tacz_ammo_query/lang/`)。

### 6. 枪包同步后的 Recipe Viewer 刷新桥 (`RecipeViewerReloadBridge`)
- 新增 `RecipeViewerReloadBridge`，在客户端收到服务端 `ServerMessageSyncGunPack` 后串行执行 cache 安装 -> `ClientIndexManager.reload()` -> `RecipeViewerReloadBridge.requestReload()`；
- 监听 `ClientTickEvents.END_CLIENT_TICK` 合并重复请求、防重入；断线时在 `CommonNetworkCacheEvent` 中清除 pending 状态；
- 反射调用 JEI / REI 运行时刷新入口，失败时一次性优雅回退至 `client.reloadResourcePacks()`。

---

## 三、文档与元数据链接

- [`README.md`](../README.md)：更新版号与功能链接；
- [`docs/AMMO_SOURCE_API.md`](AMMO_SOURCE_API.md)：弹药源 API 规范；
- [`docs/CARRYON_COMPAT.md`](CARRYON_COMPAT.md)：Carry On 兼容规范与回归矩阵。

---

## 2026-08-22 补丁：四处 `Item#getName` 改读 common 索引

`AbstractGunItem` / `AmmoItem` / `AttachmentItem` / `GunSmithTableItem` 的
`getName(ItemStack)` 此前挂着 `@Environment(EnvType.CLIENT)` 并读
`TimelessAPI.getClient*Index`。`Item#getName` 是双端公共方法（`/give` 回执、
容器标题、铁砧改名、死亡消息、其他 mod 在服务端读 `getHoverName` 都会调用）。

fabric-loader 会对环境不匹配的成员整体剥离（`STRIP_ENVIRONMENT` →
`ClassStripper`；Fabric Wiki「Class loading and transformation」同述），因此
**专服上这些覆写根本不存在**：服务端路径静默退回原版 `item.tacz.*` 键，
而不是枪包名字。反过来，若只删注解、不改实现，则会在专服加载
`com.tacz.guns.client.resource.index.Client*Index`，那才是
`NoClassDefFoundError`。

本次改为读 `TimelessAPI.getCommon*Index(...).getPojo().getName()`，空白名
兜底与 `Client*Index` 逐字符一致（`custom.tacz.error.no_name`）。common /
client 索引同源（同一份 index json、同一 POJO）；多人客户端经
`CommonAssetsManager.get()` → `CommonNetworkCache` 回退同样取得到，
客户端显示不变。语义与 26.2 线 `86e693e` 对齐。

**未改、且无需改**：`AmmoBoxItem#getName`（固定 `item.tacz.ammo_box.*` 键）；
LR 内置 `MeleeItem` / `ThrowableItem` / `ConsumableItem` 已走 common 索引。

**验证状态（诚实披露）**

- 已做：静态核对四个 `Common*Index#getPojo()`、四个 `*IndexPOJO#getName()`、
  `TimelessAPI#getCommon*Index`、`CommonAssetsManager.get()` 回退、
  `Client*Index` 空白名兜底键；`getName` 方法体不再含 `@Environment(CLIENT)`
  或 `getClient*Index`。
- 未做：本环境无 JDK，`./gradlew compileJava` 未执行；未在本环境跑 Fabric
  专服 `/give`。不要把本条写成「已实测修复崩服」。
- 正确复测命令（有构建后）：
  `/give @p tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"tacz:ak47"}]`
  服务端回执应显示 `tacz.gun.ak47.name`（无 lang 时显示该键本身即算通过），
  而不是 `item.tacz.modern_kinetic_gun`。裸 `/give tacz:modern_kinetic_gun`
  仍会回退 `item.*` —— 那是「无 GunId 组件」的既有行为，不是本补丁的回归。

## FAQ：专服上 REI/JEI 作弊拿取枪械/弹药/配件显示紫黑块与 `item.*` 原始键（2026-08-22 同步增补）

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
该 FAQ 与上面的 `getName` 代码修复相互独立：后者修的是「带 id 的物品在服务端叫错名」，
前者是「REI 未装服务端时根本没把 id 发给服务器」。
