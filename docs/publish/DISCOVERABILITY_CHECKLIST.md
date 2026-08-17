# 可发现性清单（玩家「找得到」这件事）

背景：本仓库是独立仓库而非 upstream 的 fork。**这不影响 GPL-3.0 合规性**，
署名与许可标注已在 `README.md` / `LICENSES.md` 中完成。fork 与否只影响可发现性。

> 不要为了「好找」把仓库转成 fork。GitHub 不能把已有独立仓库直接转为 fork，
> 重建会丢失 star、Issue 和 Release 下载计数。当前形态是正确的。

按性价比排序，`[x]` 为本次已完成。

---

## 1. 已完成（代码内改动）

- [x] `README.md` 顶部加入英文定位句，覆盖 26.2 / 26.1.2 / 1.21.11 三个版本关键词，
      便于英文玩家与搜索引擎命中。
- [x] `README.md` 加入「选择你的 Minecraft 版本」导航表，直连三条分支与各自最新 Release。
- [x] `docs/README_26_1_2.md` 同步以上两项（该文件用于替换 26.1.2 分支根 README）。
- [x] 表格内 6 条链接已联网核验，全部返回 HTTP 200；`26.2(main)` 的括号已转义为
      `26.2%28main%29`，否则 Markdown 会截断 URL。

### 需要你手动同步到其他分支

本次改动在 `arena/01a00da4-...` 分支上，合并进 `26.2(main)` 后请另外处理：

- 把 `docs/README_26_1_2.md` 的内容复制到 **`26.1.2` 分支**的根 `README.md`；
- 给 **`1.21.11` 分支**的根 `README.md` 加同样的英文定位句与版本导航表
  （把「本页面对应」一行改为 1.21.11）。

---

## 2. 需要你手动操作（我的 token 无权修改仓库设置）

尝试 `gh repo edit` 时返回 `HTTP 403: Resource not accessible by integration`，
仓库描述与 topics 必须由你在网页端设置。

### 2.1 仓库描述

进入仓库首页 → 右侧 About 的齿轮图标 → Description 填：

```
Unofficial Fabric port of TaCZ (Timeless & Classics Guns: Zero) for Minecraft 26.2 / 26.1.2 / 1.21.11, with LRTactical compatibility. 非官方社区移植，GPL-3.0
```

比现有描述多了 `26.1.2`、`Timeless & Classics Guns: Zero` 全称和 `GPL-3.0`
——全称是玩家实际会搜的词，现在的描述里没有。

### 2.2 Topics（当前一个都没有，这是最影响站内搜索的一项）

同一个 About 面板的 Topics 栏，逐个添加：

```
tacz
timeless-and-classics-guns
tacz-refabricated
fabric
fabric-mod
minecraft
minecraft-mod
minecraft-26-2
minecraft-1-21-11
lrtactical
unofficial-port
gun-mod
```

### 2.3 Release 正文首行

后续每次发版，正文第一行写清「MC 版本 + 加载器 + 两项硬依赖版本」。
现有 Release 标题（如 `26.2的mod文件_R2`）对中文玩家可读，但英文玩家搜不到
——可考虑把标题改成 `TaCZ Refabricated 26.2 R2 (Minecraft 26.2 Fabric)`。

---

## 3. 上游 Issue

文案见 [`UPSTREAM_ISSUE_DRAFT.md`](UPSTREAM_ISSUE_DRAFT.md)，建议用你自己的账号发，语气更自然。

上游 `Sh1roCu/TACZ-Refabricated` 现有三个无人回复的高版本适配请求，本项目正好全覆盖：

| Issue | 标题 | 时间 |
|---|---|---|
| [#52](https://github.com/Sh1roCu/TACZ-Refabricated/issues/52) | `1.21.11 compat?` | 2026-05 |
| [#43](https://github.com/Sh1roCu/TACZ-Refabricated/issues/43) | 是否有对 26.1+ 做兼容的计划？ | 2026-03 |
| [#35](https://github.com/Sh1roCu/TACZ-Refabricated/issues/35) | 请问最近是否有适配 1.21.11 版本的计划 | 2026-01 |

发完主 Issue 后，在这三个下面各回**一条短评论**指向主 Issue，不要分别粘贴长文。

---

## 4. Modrinth：流量最大，但有一个必须先解决的许可前提

Modrinth 的搜索量比 GitHub 高一到两个数量级，是真正决定玩家能否找到的渠道。
但**不能直接把现在的 jar 传上去**，原因如下。

默认枪包 `src/main/resources/.../tacz_default_gun/assets/tacz/gunpack_info.json` 声明：

```json
"license": "CC BY-NC-ND 4.0",
"authors": ["TACZ Dev Team"]
```

两个风险点：

1. **ND（NoDerivatives，禁止演绎）** —— 原样转发整个枪包通常可以，但移植过程中若对枪包内的
   模型、动画、贴图或其 JSON 做过任何适配性修改，就构成演绎作品，ND 不允许再分发。
   请先确认本仓库对默认枪包资源是否做过改动（可与上游 1.21.1 分支逐文件比对）。
2. **NC（NonCommercial，非商业）** —— Modrinth 有创作者分成计划。如果发布时开启了
   monetization，就可能落入 NC 禁止的范围。

**稳妥做法**：Modrinth 上只发**不打包默认枪包资源**的构建，让玩家自行从官方渠道获取枪包；
或先向 TACZ Dev Team 取得书面许可再打包。发布页需写明
`unofficial port of TaCZ, GPL-3.0` 并链接上游与原始项目。

在这一条确认清楚之前，建议维持 GitHub Releases 作为唯一分发渠道。
