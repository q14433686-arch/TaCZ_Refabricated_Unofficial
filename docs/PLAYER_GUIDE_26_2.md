# 玩家指南：Fabric 26.2 / 1.2.0

## 安装或从 R3-hotfix4 升级

1. 确认实例是 **Minecraft 26.2、Java 25、Fabric Loader 0.19.3 或以上**。安装对应 26.2 的 Fabric API（本线使用 `0.155.2+26.2`）和 Forge Config API Port（`26.2.1` 或以上）。Iris、JEI、REI 和手柄模组都不是基础安装的必需品。
2. 关闭游戏和专服，备份 `saves/` 或服务端世界、`tacz/` 及改过的配置。把旧的 TaCZ JAR 移出 `mods/`，换成 `TACZ-Refabricated-26.2-1.2.0+fabric.26.2.R1.jar`。联机时两端用相同构建；26.3、Forge 或 NeoForge 文件不能代替它。
3. 启动后在模组列表或 `latest.log` 核对 `tacz` 的完整版本。自制枪包放实例的 `tacz/`，不要覆盖 `tacz_default_gun/`。如果升级前改过默认包，可到 `tacz_backup/tacz_default_gun/` 找自动备份；重要修改仍应另存。为自己保留的包使用独立目录和命名空间。

## 第一次进世界看什么

- **制作：**取得铁块可解锁默认枪匠台的配方书条目；先用普通工作台制作枪匠台，再到专用台选择枪、配件或弹药。JEI / REI 可以查配方，不会替你搬材料或跳过缺料检查。
- **展示与设置：**持枪对准允许放置的展示位，用 TaCZ「交互」（默认 `O`）。暂停菜单可调持枪位置与准星；保存按账号区分，取消不写入。玩家的 HUD 准星不是枪包作者画在镜片上的分划。
- **开镜：**不改配置就是经典整屏变焦。想让镜外保持原视野，可在 Mod Menu → 本模组配置 →「渲染」打开 `ScopePipEnable`；开光影时还须放行 `ScopePipAllowShaderPacks`。默认只对 **4× 及以上**启用 PIP，使用重投影；只有打开 `ScopePipRerender` 才会再渲染一次世界。修复红板和热像不要求打开 PIP 或关掉光影。

## 镜内异常怎么区分

| 看到的现象 | 怎么记录 |
|---|---|
| 红色方片挡住背景，或细小标记不见 | 用默认包、同一机位分别截图，记下瞄具 ID、倍率档、经典或 PIP。正常的红点和细刻线应该保留；它们不是遮挡故障。 |
| REAP-IR / XL50 从某些角度只显示碎片 | 在游戏里观察同一暴露目标，再用实心方块完全遮住它。墙后不应呈现目标热像；固定的白色准星和读数不是热源。记录距离、角度、光影包及其选项。 |
| 只有第三方包出问题 | 先用默认包对照，再查[包根、版本约束与模型引用](wiki/GUNPACK_26_2.md)。不必为了诊断覆盖原包。 |

[实测环境与原帧入口](investigations/ADS_COMPLEMENTARY_R581_26_2.md)只覆盖指定组合。如果仍能复现，请把两张对照截图、`latest.log`、模组完整版本、显卡与驱动、Iris/Sodium/光影包版本交给[本移植 Issues](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/issues)。
