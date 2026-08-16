# API 表面稳定性重构 —— 26.1.2 / 26.2 同步说明

> 本文记录 `#46` 相关 API 表面稳定性重构（`docs/API_SURFACE_AUDIT.md` + 各 commit
> 到本轮 P1⑤–P1⑨ 为止）如何同步到 26.1.2 与 26.2 两条版本线。

## 结论（一句话）

三条版本线（1.21.11 / 26.1.2 / 26.2）在这批重构触及的 **15 个核心逻辑文件上几乎逐字节一致**，
因此整份重构可以**几乎原样照搬**；26.2 有 7 个文件存在「仅注释差异」，需要保留其注释。

## 版本线现状（无共同历史）

三条线是**相互独立的根提交**（`git merge-base` 为空），无法 `cherry-pick`/`merge`，只能以
patch 形式移植：

| 版本 | 基线引用 | minecraft_version |
|------|----------|-------------------|
| 1.21.11 | tag `1.21.11_R1`（= 本分支基线 `4a98325`） | 1.21.11 |
| 26.1.2 | tag `26.1.2_R1`（`6aeecec`） | 26.1.2 |
| 26.2 | tag `26.2_R1`（`5b25d1a`）；`26.2(main)` 源码与之一致（只多 README/docs 改动） | 26.2 |

## 触及文件的差异矩阵（基线对比 `1.21.11_R1`）

| 文件 | 26.1.2 | 26.2 |
|------|--------|------|
| `api/item/gun/AbstractGunItem.java` | 相同 | **仅注释**（`dropAllAmmo` 处的「上游遗留边界」说明） |
| `api/item/gun/AmmoAvailability.java`（新增） | — | — |
| `client/animation/statemachine/GunAnimationStateContext.java` | 相同 | 相同 |
| `client/gameplay/LocalPlayerShoot.java` | 相同 | **仅注释**（连发调度 `tacz$submitAsync` 处） |
| `client/gameplay/LocalPlayerBolt.java` | 相同 | 相同 |
| `client/gameplay/LocalPlayerReload.java` | 相同 | **仅注释**（`doReload` 末尾的 Case⑧ 调试探针，不与改动点重叠） |
| `entity/shooter/LivingEntityShoot.java` | 相同 | **仅注释**（`consumeAmmoFromPlayer` javadoc） |
| `entity/shooter/LivingEntityBolt.java` | 相同 | 相同 |
| `entity/shooter/LivingEntityReload.java` | 相同 | 相同 |
| `entity/shooter/LivingEntityAmmoCheck.java` | 相同 | 相同 |
| `entity/shooter/LivingEntityDrawGun.java` | 相同 | **仅注释**（`getDrawCoolDown` 上方大段排查记录，与改动点不重叠） |
| `entity/shooter/LivingEntityMelee.java` | 相同 | 相同 |
| `entity/shooter/ShooterDataHolder.java` | 相同 | 相同 |
| `item/ModernKineticGunScriptAPI.java` | 相同 | **仅注释**（`getBoltByInt` 处） |
| `item/ModernKineticGunItem.java` | 相同 | **仅注释**（`getLevel/getExp` 处的升级系统说明，与改动点不重叠） |

> 26.2 的差异全部是「审计/复核型注释 + 调试探针」，不含任何代码逻辑改动；且与本重构的
> 改动点除 `LocalPlayerShoot#doShoot` 一处外均不重叠（该处已在 26.2 patch 中保留 26.2 注释）。

## 如何应用

两个已生成并验证过的 patch 就在本目录：

- `docs/port/api-surface-refactor-1.21.11.patch` —— 相对 26.1.2 基线可直接 `git apply`（**干净应用，无冲突**）。
- `docs/port/api-surface-refactor-26.2.patch` —— 相对 26.2 基线可直接 `git apply`（**已含注释冲突的解决**）。

```bash
# 26.1.2
git checkout 26.1.2_R1           # 或相应的 26.1.2 工作分支
git apply docs/port/api-surface-refactor-1.21.11.patch

# 26.2
git checkout 26.2_R1             # 或 26.2(main)
git apply docs/port/api-surface-refactor-26.2.patch
```

> 验证方式：`git apply --check docs/port/<patch>` 应在对应分支上无输出（已在本仓库用
> `git worktree` 对 `26.1.2_R1` 与 `26.2_R1` 实测通过）。

## 26.2 那处注释冲突的处理

`git apply --3way` 时只有 `LocalPlayerShoot.java` 一处冲突：26.2 把 `doShoot` 内
`tacz$submitAsync` 上方的注释改写成了「已核实：本段运行在 ScheduledExecutorService 线程…」。
解决方式为：**保留 26.2 的新注释 + 采用重构后的 `fireOnce(...)` 调用**，最终差异仅注释：

```java
// 已核实：本段运行在 ScheduledExecutorService 线程，而动画状态机、声音
// 与 Fabric 事件都属于客户端主线程状态；必须经 Minecraft 的事件循环提交，
// 否则会并发修改集合。这里不是未完成分支。
((BlockableEventLoopAccessor) Minecraft.getInstance()).tacz$submitAsync(() -> fireOnce(display, mainHandItem, gunData));
```

其余 6 个注释差异（`AbstractGunItem` / `LivingEntityShoot` / `ModernKineticGunScriptAPI` /
`LocalPlayerReload` / `LivingEntityDrawGun` / `ModernKineticGunItem`）不与本轮重构的重构点
重叠，`git apply --3way` 自动合并、无需手工处理。

## 移植后验证

与 1.21.11 相同（见 `docs/API_SURFACE_AUDIT.md` 末尾与 `#46` 的测试矩阵）：
换弹 / 开火 / 拉栓 / 动画四条路径各自等价；重点回归三种 bolt 类型 × 四种供弹方式的组合，
以及 BURST 连发与创造模式两条分支。

## Carry On 搬运兼容修复：仅 1.21.11 线，不随补丁移植

`docs/CARRYON_COMPAT.md` 记录的四个搬运修复（两格工作台 onPlace 自愈补全、
`workbench_c` 自定义 `TableHalf` 枚举、非 root 半块不再创建方块实体、手持渲染
`CarryOnRenderHelperMixin`）**有意不移植到 26.1.2 / 26.2**：

- 26.x 线采用自己的 Carry On 集成（`com.tacz.guns.compat.carryon.BlackList` +
  `cn.sh1rocu.tacz.mixin.compat.carryon.ConfigLoaderMixin`），在 Carry On 配置加载时
  把全部 TACZ 方块加入黑名单——搬运整类功能在 26.x 是被有意禁用的，两个补丁文件
  （api-surface-refactor-*.patch）不含本批改动；
- `CarryOnRenderHelperMixin` 的注入目标是 Carry On 2.9.x 的
  `ItemStack getRenderItemStack(Player)`；26.1.2/26.2 的 Carry On（2.10+/2.11+）已改为
  `ItemStackTemplate` 返回值（渲染处再 `.create()`）。若 26.x 未来也开放搬运支持，
  需改用 `ItemStackTemplate` 变体（在 template 上补 BlockId 组件），并替换 mixin 回调类型。
