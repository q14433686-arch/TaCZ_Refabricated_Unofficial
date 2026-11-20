<!-- 此文件按 26.1.2 分支根目录 README.md 的位置和用途编写，可直接复制替换。 -->

# [UNOFFICIAL] TaCZ Refabricated — Minecraft 26.1.2 / Fabric
[![CurseForge Downloads](https://cf.way2muchnoise.eu/full_1627909_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/unofficial-tacz-refabricated)
[![CurseForge Versions](https://cf.way2muchnoise.eu/versions/1627909.svg)](https://www.curseforge.com/minecraft/mc-mods/unofficial-tacz-refabricated/files)
[![GitHub Downloads](https://img.shields.io/github/downloads/q14433686-arch/TaCZ_Refabricated_Unofficial/total?logo=github&label=GitHub%20Downloads)](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/releases)

> **Unofficial Fabric port of TaCZ (Timeless & Classics Guns: Zero) for Minecraft 26.2,
> 26.1.2 and 1.21.11, with an LRTactical compatibility framework. Not an official TaCZ
> release; not reviewed or endorsed by the TACZ Dev Team. GPL-3.0.**

> **非官方社区移植，不是 TaCZ 官方发布，也未获 TACZ Dev Team 审核或背书。**

本分支把 [Sh1roCu/TACZ-Refabricated](https://github.com/Sh1roCu/TACZ-Refabricated)
的 Minecraft 1.21.1 Fabric 分支移植到 **Minecraft 26.1.2 Fabric**。原移植基线为
`0.7.0-forge1.1.8-hotfix`；现已回移[上游 1.2.0 的功能与默认枪包](https://github.com/Sh1roCu/TACZ-Refabricated/tree/356bdf9d48f2225bac6ceb56adc5576a418517a8)。本分支当前源码版本为 **`1.2.0+fabric.26.1.2.R1`**。

[下载构建](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/releases)
· [问题反馈](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/issues)
· [26.1.2 源码](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/26.1.2)
· [直接上游](https://github.com/Sh1roCu/TACZ-Refabricated/tree/1.21.1)
· [原始 TaCZ 项目](https://github.com/MCModderAnchor/TACZ)

> 仓库源码已使用 R1 版本号（1.2.0 基线）；实际可下载版本及其发布日期以 Releases 页面为准。

### 选择你的 Minecraft 版本 / Pick your Minecraft version

| Minecraft | 源码分支 |
|---|---|
| **26.2** | [`26.2(main)`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/26.2%28main%29) |
| **26.1.2** | [`26.1.2`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/26.1.2) |
| **1.21.11** | [`1.21.11`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/1.21.11) |

本页面对应 **26.1.2** 分支。

---

## 1. 支持环境

| 项目 | 26.1.2 分支要求 |
|---|---|
| Minecraft | **26.1.2** |
| 加载器 | **Fabric Loader 0.19.3+** |
| Java | **25+** |
| Fabric API | **0.155.2+**；R1 构建使用 **0.155.2+26.1.2** |
| Forge Config API Port | **26.1.5+，硬依赖** |
| 本 mod | **`1.2.0+fabric.26.1.2.R1`** |

这里只提供 Fabric 构建，不能与 Forge / NeoForge 版 TaCZ 或 LRTactical 混装。

以下均为可选集成，不影响基础安装：

| 可选 mod | 26.1.2 核验/建议版本 | 用途 |
|---|---|---|
| JEI | 编译 pin **29.5.0.26** | 内置 Ammo Query 与工作台类别 |
| REI | 编译 pin **26.1.819** | 内置 Ammo Query 与工作台类别 |
| Player Animation Library | **1.2.5+mc.26.1** | 第三人称持枪/动作动画 |
| Controllable | **0.26.0+26.1.2** | 手柄键位、组合键与震动兼容 |
| Carry On | 建议 **>=2.10.0** | A/B/C 多格工作台与展示架搬运控制 |

---

## 2. 项目范围与 1.2.0 回移

本仓库包含：

- TaCZ 1.2.0 的功能与默认枪包（70 把枪械、126 件配件、25 种弹药、展示架/展示柜/弹药箱与热成像瞄具）在 Fabric 26.1.2 上的完整回移；
- 为 26.1.2 API 改写的网络、资源加载、GUI、UniversalPackConverter（`compat_26_1_2`）、光学与热成像瞄具渲染、Snowstorm 粒子与动态表面光；
- 一套内置的 **LRTactical 兼容框架**；
- 一个内置的 **Mesh 加载器（TML）**：枪包可在 geo.json 骨骼上携带 `poly_mesh`
  网格（`"model_type": "mesh"`），由本 mod 直接解析渲染，详见
  [`docs/MESH_LOADER.md`](docs/MESH_LOADER.md)；
- JEI / REI（含远程枪包同步后的局部刷新与 Ammo Query）、PAL、Controllable、Shoulder Surfing、Zoomify、Iris 1.11.2 / Sodium 0.9.1（含 Complementary Reimagined r5.8.1 瞄具与热成像兼容）等可选模组的兼容接线。

**从 1.1.8 热修构建升级到 1.2.0：** 客户端和服务端请同步换用 `1.2.0+fabric.26.1.2.R1`，升级前先备份存档和修改过的 `tacz/`。版本号参与内置包导出指纹；若检测到旧版导出目录，启动时会先备份到 `tacz_backup/tacz_default_gun/` 再刷新为 1.2.0 默认枪包。

---

## 3. 安装

1. 安装 Minecraft 26.1.2、Fabric Loader 0.19.3+ 与 Java 25+；
2. 安装 Fabric API 0.155.2+ 和 Forge Config API Port 26.1.5+；
3. 从 [Releases](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/releases)
   下载明确标注为 **26.1.2 / Fabric** 的构建；
4. 把三个 mod 的 `.jar` 放入 `.minecraft/mods/`；
5. 启动游戏。第三方枪包放入 `.minecraft/tacz/`（旧 1.20.1 布局包可通过 `/tacz convert` 生成 `compat_26_1_2` 兼容层）。

---

## 4. 第三方枪包与版本约束

现代枪包放在 **`.minecraft/tacz/`**（zip 或目录均可，根部须有 `gunpack.meta.json`）。
本分支用 `1.2.0` 作为 SemVer 核心，`+fabric.26.1.2.R1` 是构建元数据，不参与 Fabric 的版本先后比较（因此同时满足 `>=1.1.8` 与 `>=1.2.0`）。

本仓库不提供 Arcana，明确要求 Arcana（仅发布 Forge 1.20.1）的内容包不属于本 Fabric 26.x 端口的受支持范围。

---

## 5. 许可与来源

- TaCZ 与本端口对应代码使用 GPL-3.0；
- 移入的 LRTactical 代码部分沿用其 GPL-3.0；
- 内嵌的 SimpleBedrockModel 适配源码沿用 LGPL-3.0（随构建附带 `META-INF/licenses/` 声明）；
- 默认枪包的 `gunpack_info.json` 声明其资源为 CC BY-NC-ND 4.0；
- 随 jar 打包的 Mayday Animation Engine 使用 MIT。

详见 [`LICENSE`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/blob/26.1.2/LICENSE)
与 [`LICENSES.md`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/blob/26.1.2/LICENSES.md)。

---

## 6. 从源码构建

需要 Java 25：

```bash
./gradlew build
```

产物位于 `build/libs/`。若要使用分支内的 Iris/Sodium 开发运行配置：

```bash
./gradlew runClient -PwithIris
```

---

## 7. 反馈

请在[本仓库 Issues](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/issues)提交完整 `latest.log` / 崩溃报告、MC 与双端硬依赖版本、第三方枪包与可选模组版本，以及使用 shader pack 时的 Iris/Sodium 与光影包版本。
