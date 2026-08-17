# 上游 Issue 草稿（Sh1roCu/TACZ-Refabricated）

用途：告知直接上游本仓库存在，方便寻找 26.x / 1.21.11 Fabric 版本的玩家找到入口。
定位：**告知 + 邀请纠错**，不是索取背书，不是催更。

标题建议：

> [Info] 非官方 26.2 / 26.1.2 / 1.21.11 Fabric 移植分支（社区维护，供 #52 #43 #35 参考）

正文：

---

你好，感谢维护 TACZ-Refabricated。

Issue #52、#43、#35 都在问 1.21.11 / 26.1+ 的适配情况。我基于本仓库的 1.21.1 Fabric 分支
（`0.7.0-forge1.1.8-hotfix`）做了一份**非官方**的高版本移植，目前有 26.2、26.1.2、1.21.11
三条构建线：

- 仓库：https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial
- 发布：https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/releases

开这个 Issue 只是为了留一个可检索的入口，避免玩家反复在这里询问高版本，也避免他们把移植分支的
问题提到你或 TaCZ 原作者那边。**不请求任何背书，也不催更。**

需要明确的几点：

1. 这是社区非官方移植，未经 TACZ Dev Team 或本仓库维护者审核、认可；
2. 代码沿用 GPL-3.0，README 与 `LICENSES.md` 中已标注 TaCZ、LRTactical、默认枪包
   （CC BY-NC-ND 4.0）、MAE（MIT）等各自的来源与许可；
3. 所有关于该移植的 bug 请提交到我的仓库 Issue，不要提交到这里或 MCModderAnchor/TACZ；
4. 移植过程中的 26.x API 适配改动（网络、资源加载、GUI、渲染接线等）如果对你上游有用，
   我可以按你希望的粒度拆成 PR 提交，或者整理成一份适配说明；
5. 如果你认为署名、命名、许可标注或分发方式有任何不妥，请直接在这里指出，我会立即修改，
   包括必要时改名或下架。

再次感谢原项目和上游移植的工作。

---

## 附：其他可选做法（与开 Issue 不冲突，建议一起做）

1. **改仓库描述 + topics**，让 GitHub 搜索能命中：
   topics 建议 `tacz`、`tacz-refabricated`、`fabric`、`minecraft-mod`、`minecraft-26-2`、
   `minecraft-1-21-11`、`lrtactical`、`unofficial-port`。
2. **README 顶部加一句英文定位句**，方便非中文玩家搜索命中，例如
   `Unofficial Fabric port of TaCZ (Timeless & Classics Guns: Zero) for Minecraft 26.2 / 26.1.2 / 1.21.11.`
3. **在上游相关 Issue（#52 / #43 / #35）下各回一条简短评论**并指向本 Issue，
   而不是分别粘贴长文，避免被视为刷屏。
4. **Release 标题与 tag 带上 MC 版本关键词**（已经做到），并在每个 Release 正文首行写清
   MC 版本 + 加载器 + 硬依赖。
5. 若要更进一步，可在 Modrinth 发布页写明「unofficial port of TaCZ, GPL-3.0」并链接上游，
   Modrinth 的搜索量比 GitHub 大得多——但这一步要先确认默认枪包的 CC BY-NC-ND 资源
   是否允许你以该形式再分发，不确定就只发不含枪包资源的构建。

## 不建议的做法

- 不要为了「好找」把仓库改名成看起来像官方的名字（例如去掉 Unofficial）。
- 不要现在把仓库转成 fork —— GitHub 无法把已有独立仓库直接转为 fork，重建会丢 star、
  Issue 和 Release 下载量。当前独立仓库 + 明确署名的形态在 GPL-3.0 下完全合规。
