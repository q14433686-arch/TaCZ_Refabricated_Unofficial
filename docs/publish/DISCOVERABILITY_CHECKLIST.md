# 可发现性检查单（Discoverability Checklist）

> `AGENTS.md` §5 引用的清单（尤其 §4 许可段）。首刊 2026-08-31
> ——此前该文件名被引用但从未写出，本文补上。目标：让搜「TaCZ 26.2 /
> TaCZ Fabric」的玩家能找到本项目，同时不踩许可与站规红线。

## 1. 入口矩阵

| 渠道 | 状态 | 备注 |
|---|---|---|
| GitHub Releases | 主发布渠道 | README 顶部即链接 |
| CurseForge | 已有条目（项目 id 1627909，README 徽章在用） | 文案见 `CurseForge.md` |
| Modrinth | 计划中 | 文案见 `Modrinth.md`；§4 的 ND 障碍先解决 |
| MC 百科 (mcmod.cn) | 计划中 | Wiki 型资料站，通常不托管 jar；文案见 `MCMOD.md` |
| 上游 issue [Sh1roCu/TACZ-Refabricated#57](https://github.com/Sh1roCu/TACZ-Refabricated/issues/57) | 维护者置顶入口 | 措辞已协调好，改动前谨慎（`AGENTS.md` §5） |

## 2. 命名一致性

- 三站项目名统一 `[UNOFFICIAL] TaCZ Refabricated`，**不带版本号与游戏名**
  （CurseForge 驳回项，论证见 `docs/publish/README.md`）。
- 版本信息由文件名与版本号字段承载。

## 3. 搜索词覆盖

描述里应自然出现的词（玩家实际搜什么）：TaCZ、Timeless and Classics Zero、
Fabric、枪械模组 / gun mod、对应 MC 版本号（26.2 / 26.1.2 / 1.21.11）、
TacZ Mesh Loader / TML（R3 起内置，搜 TML 的玩家应能搜到我们）。

## 4. 许可红线（引用锚点，勿删本节编号）

- **代码 GPL-3.0**：三站 License 字段选 `GPL-3.0-only`（或站内等价项）。
  内置移植部分（LRTactical、TML）同为 GPL-3.0，署名在 `LICENSES.md` 与
  `fabric.mod.json` 的 `contributors`。
- **默认枪包资源 CC BY-NC-ND 4.0**：
  - **NC（非商业）**：不参加任何按下载量付费的分成计划；CurseForge 的
    points 计划需先核对当时条款再决定。
  - **ND（禁演绎）**：枪包资源**改动后再分发受限**——这是 Modrinth 发布的
    前置障碍：Modrinth 要求上传者对全部内容有再分发权，含 ND 资源的 jar
    需在描述中明确该部分许可，或以「资源原样、未演绎」立场发布；拿不准就
    先只发 GitHub+CurseForge。
  - MAE 为 MIT（见 `LICENSES.md`）。
- 三站描述必须含**非官方声明**（不隶属、未获 TACZ Dev Team 背书）。

## 5. 发布后动作

- README 徽章（下载量/版本）指向正确项目 id。
- 新站上线后回到本文件 §1 更新状态列。
