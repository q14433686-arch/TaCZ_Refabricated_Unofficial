# 26.2 Fabric · TaCZ Refabricated 1.2.0

**环境：Minecraft 26.2 · Fabric Loader 0.19.3+ · Java 25+ · Fabric API 0.155.2+26.2（构建用）· Forge Config API Port 26.2.1+。**

版本名 **1.2.0**，完整模组版本 **`1.2.0+fabric.26.2.R1`**，文件名 `TACZ-Refabricated-26.2-1.2.0+fabric.26.2.R1.jar`。这是本线接收 TaCZ 1.2 功能后的第一个版本号；不是将 26.3 的 JAR 改名，也不是发布公告。

## 从旧 26.2 基线升级

- 客户端和专服使用同一份 26.2 构建。先备份存档、额外枪包和自己编辑过的默认包，再移除旧的 TaCZ JAR。不要把两个 `tacz` 同时放进 `mods/`。
- `dependencies.tacz` 开始按 **1.2.0** 比较。`>=1.1.8` 仍匹配，但锁死 `1.1.8` 或排除 `1.2.0` 的包不会通过。不要改掉上限来绕过检查；请用包作者适配的版本。
- 模组版本参与默认包导出指纹。导出开启时，即使与上一测试版相比默认包文件相同，换版也可能刷新 `tacz/tacz_default_gun/`；旧目录会先备份到 `tacz_backup/tacz_default_gun/`。这不是对第三方包执行转换。

## 这条基线包含什么

| 范围 | 26.2 上的行为与证据 |
|---|---|
| 默认包与工作台 | 从[固定的 1.21.1 Fabric 上游](https://github.com/Sh1roCu/TACZ-Refabricated/tree/356bdf9d48f2225bac6ceb56adc5576a418517a8)适配 4,176 个有效默认包文件、229 个索引。默认枪匠台用原版 3×3 配方；专用台按服务端库存结算。曾逐项制作 202 个产物，并做 202 个缺料对照；[测试提交与日志](lineage/HANDOFF_LEDGER.md#tacz-12-262)。 |
| 在线与存档 | 匹配版本的两端在连接、`/reload`、退出再加入时重建服务端索引。展示物品保留栈组件；带名标靶支持旧档案迁移。转换器按 26.2 的 DFU 和配方格式导出，不把转换成功当作游玩验证。 |
| 显示与输入 | 账号独立保存持枪位置和准星。JEI 与 REI 的查询、第三人称 PAL、可选手柄桥各有独立验收；不要求玩家一起安装这些模组。 |
| Iris 开镜 | 修复 Complementary Reimagined r5.8.1 下镀膜红板遮挡场景、细分划消失和暴露的白热目标缺面。经典整屏变焦仍是默认，PIP 仍为可选。详见[逐档及隔墙实测](investigations/ADS_COMPLEMENTARY_R581_26_2.md)。 |

**如何看 PASS：** 光影回归在 `a119e32` 使用 Iris 1.11.2、Sodium 0.9.1 和 r5.8.1 默认设置：46 个默认瞄具的 59 个档位、20 个 PIP 档位通过；两种热瞄具的 48 组墙后原始热掩码全部为零。这是 Mesa 软件 OpenGL 上的游戏帧，不是所有显卡、光影配置或第三方包的保证。该报告与更早的行为回移账本各自保留当时的 **1.1.8 热修构建及其校验值**，不能拿来校验 1.2.0 JAR。

<a id="build-120"></a>
## 1.2.0 成品核验

[下载该次 Actions 构建](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44676085976/artifacts/14602168455)（解压 ZIP 后取 JAR；归档有保留期限）。提交 `0df51ea` 的[构建审计](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44676085976/job/136088143039)与[独立专服和客户端 TCP 测试](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44676086074/job/136088143097)都取得了同一份 **JAR 本体**的 SHA-256：

```text
32654cb8696f775c23915578bb60f7b91be41122dfc372626b8375bd8313d942
```

测试专服上的独立枪包要求 `tacz >=1.2.0`；客户端成功收到该包的索引，重载、退出和重连均通过。[同一提交的 r5.8.1 复测](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44676085948)中，59 个经典档位、20 个 PIP 档位及在 2560×1417 复查的 9 个视图均通过；48 组热像遮挡对照的原始热掩码与叠字前漏热点均为 **0**。[原生客户端](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44676086001)和[Iris 原有回归](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44676086011)也通过。

本提交共 **10 个工作流、22 个作业通过**，包括分开安装的 [JEI / REI](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44676085982)、[PAL](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44676086109) 和 [Controllable](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44676086030)。

[玩家指南](PLAYER_GUIDE_26_2.md) · [枪包 Wiki](wiki/GUNPACK_26_2.md) · [转换说明](GUNPACK_CONVERSION_26_2.md) · [旧基线记录](CHANGELOG_26_2_R2.md) · [来源与许可](../LICENSES.md)
