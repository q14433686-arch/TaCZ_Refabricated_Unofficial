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
