# API 表面稳定性重构 —— 26.1.2 / 26.2 同步说明

> 本文记录 `#46` 相关 API 表面稳定性重构（`docs/API_SURFACE_AUDIT.md` + 两个 commit
> `0950d71`、`c70f177`）如何同步到 26.1.2 与 26.2 两条版本线。

## 结论（一句话）

三条版本线（1.21.11 / 26.1.2 / 26.2）在这批重构触及的 **7 个核心逻辑文件上几乎逐字节一致**，
因此整份重构可以**几乎原样照搬**；26.2 只有 4 个文件存在「仅注释差异」，需要保留其注释。

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
| `entity/shooter/LivingEntityShoot.java` | 相同 | **仅注释**（`consumeAmmoFromPlayer` javadoc） |
| `entity/shooter/LivingEntityBolt.java` | 相同 | 相同 |
| `entity/shooter/LivingEntityAmmoCheck.java` | 相同 | 相同 |
| `item/ModernKineticGunScriptAPI.java` | 相同 | **仅注释**（`getBoltByInt` 处） |

> 26.2 的差异全部是「审计/复核型注释」，不含任何代码逻辑改动；且与本重构的改动点
> 除 `LocalPlayerShoot#doShoot` 一处外均不重叠。

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

其余 3 个注释差异（`AbstractGunItem` / `LivingEntityShoot` / `ModernKineticGunScriptAPI`）不与该
重构的重构点重叠，`git apply --3way` 自动合并、无需手工处理。

## 移植后验证

与 1.21.11 相同（见 `docs/API_SURFACE_AUDIT.md` 末尾与 `#46` 的测试矩阵）：
换弹 / 开火 / 拉栓 / 动画四条路径各自等价；重点回归三种 bolt 类型 × 四种供弹方式的组合，
以及 BURST 连发与创造模式两条分支。
