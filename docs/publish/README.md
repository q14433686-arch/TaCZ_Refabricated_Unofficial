# 项目主页与发布文案

项目简介、游戏图与单文件更新说明分开使用。以下是**仓库内的文案与图源**，不代表站点已更新或 1.2.0 R1 已发布。只有字段模板中的 `[[ ]]` 需人工确认。

| 文件 | 平台 | 性质 |
|---|---|---|
| [Summary 与字段说明](CurseForge.md) · [双语 Description 正文](CurseForge-Description.md) | CurseForge | 正文可整页复制；不是文件更新日志 |
| [Modrinth 字段与英文正文](Modrinth.md) | Modrinth | §③ 是项目介绍，Gallery 单独上传；不要复制旧 26.2 文件 changelog |
| [MC 百科资料底稿](MCMOD.md) | MC 百科 (mcmod.cn) | 第三人称词条资料与配图，须由编辑核对、遵守站规；不是 jar 发布页 |
| [本次文件说明（英文）](26.3-1.2.0-R1-notes.md) · [中文开发变更](../CHANGELOG_26_3_R1.md#120-r1) | GitHub Release / 平台 File changelog 草稿 | 只对应 26.3 1.2.0 R1 **含 #124** 的功能快照；不用于旧发布件 |
| [玩家操作](../PLAYER_GUIDE_26_3.md) · [带图题的图册](../SCREENSHOTS_26_3.md) · [枪包 Wiki 补遗](../wiki/GUNPACK_26_3.md) | 仓库文档 / Wiki 素材 | Wiki 以原作实际已有的格式页为基础，本线新增开关单列，不改上游 Wiki |

## 当前维护口径（2026-11-27）

- 项目不再积极维护，所有版本均不承诺固定更新周期。
- **26.1.2、26.2 封存**：保留源码和历史下载，不再安排更新或修复。
- 仅 **Minecraft 1.21.11 与最新版本线**保留不定时维护；目前最新适配线为 **26.3**，不把尚未适配的新 MC 版本写成已支持，也不承诺响应、修复或适配时间。
- 欢迎有意接手维护的维护者自行 **fork**、独立维护；原有代码／资源许可证和署名要求不变。
- 本次更新的是仓库内文案及 Issue 模板，**不代表 CurseForge、Modrinth、MC 百科或 GitHub About 字段已在线更新**。旧文件的版本标签与历史 changelog 不因封存而删除。

## GitHub 项目主页

**About / Description**（仓库级简介，不更改默认分支）：

```text
Unofficial Fabric port of TaCZ. Occasional updates only for MC 1.21.11 and the latest version line (currently ported: 26.3). 26.1.2/26.2 archived. Forks and independent continuation welcome under the existing licenses. 非官方移植，不定时更新；欢迎 fork 接手维护。
```

**Website** 建议填 [26.3 开发版介绍与版本选择](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/26.3#readme)。默认分支 `26.2(main)` 的 README 属于 26.2；不要用 26.3 的版本表覆盖它。若在旧线首页引用，只需写：「[26.3 开发版](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/26.3#readme)包含挂板、弹药库与账号准星；各 MC 版本单独构建，请按文件标注选择。」

## 截图上传

下列是可直接打开的原文件与站内 Gallery 标题。**每张图的图注注明 `MC 26.3 development`**，不把它们放在旧 1.1.8 文件的截图说明下。先用项目正文的小图介绍玩法；要看其他视角、UI 语言或无光影版，到[完整图册](../SCREENSHOTS_26_3.md)取同组原图。

| English title / 中文标题 | 上传哪张 PNG | 图注要保留的信息 |
|---|---|---|
| Inside the gunsmithing table / 专用制作台 | [取消筛选后的列表](../../build-reports/visual/44627993715-tacz_hand_filter_cleared.png) | 专用工作台，不是原版 3×3 配方书。 |
| Preview a pegboard slot / 放入前的挂板预览 | [预览](../../build-reports/visual/44425974348-tacz_rack_ghost.png) · [放入后](../../build-reports/visual/44425974348-tacz_rack_placed.png) | 同一格，预览不扣物品。第二张的标题用 **Item placed / 放入实物**。 |
| Inside ammo storage / 弹药库 | [库存界面](../../build-reports/visual/44363012970-tacz_ammo_storage_frame.png) | 上方显示容量与弹种；来自早期 26.3 特性构建。 |
| A HUD for one gun / 单枪准星分配 | [M320 分配页](../../build-reports/visual/44482591156-tacz_view_tuning_m320_rule.png) | 这是编辑器；[中心对照图](../../build-reports/visual/44482591156-hud-rules.png)另标题为 **Same category, two reticles (crops) / 同类两枪（原帧裁剪）**。 |
| White-hot through the XL50 / XL50 白热镜 | [热源](../../build-reports/visual/44244608846-tacz_lens_classic_hot.png) · [挡墙](../../build-reports/visual/44244608846-tacz_lens_classic_wall.png) | 经典变焦；第二张 **Target behind a wall / 遮挡对照**。 |
| Muzzle light: off / on · 枪口墙地补光 | [Off](../../build-reports/visual/44626162782-reimagined-act-tacz_surface_ak_off.png) / [On](../../build-reports/visual/44626162782-reimagined-act-tacz_surface_ak_on.png) | 分两张上传，标题分别标 Off、On；两次都单发，Reimagined r5.9.3 / ACT 128。 |
| Impact light: off / on · 爆点墙地补光 | [Off](../../build-reports/visual/44626162782-reimagined-act-tacz_surface_rpg_off.png) / [On](../../build-reports/visual/44626162782-reimagined-act-tacz_surface_rpg_on.png) | 两次火箭都命中约 30 格外的墙；环境同上。不要称为镭射照明。 |
| One flash, two displays / 普通挂板与全亮展示柜 | [关](../../build-reports/visual/44639006524-reimagined-act-tacz_surface_objects_off.png) / [开](../../build-reports/visual/44639006524-reimagined-act-tacz_surface_objects_on.png) | 两次单发，盔甲架、铁盔、挂板和柜背板受光；柜内同款绿宝石始终全亮。Reimagined ACT 128；青色掉落物为测试贴图。 |
| Distant drops without a pack LOD / 无低模的掉落物 | [3D](../../build-reports/visual/44627993715-tacz_dropped_guns_off.png) / [Slot icon](../../build-reports/visual/44627993715-tacz_dropped_guns_on.png) | 对照用：16 件掉落物；**cyan test-pack icon / 青色为夹具槽位图**。不作为默认包宣传封面。 |

外站正文用指向**固定公开提交**的图片和图册 URL，不粘贴 `../`、临时 Actions 下载地址或有期限的附件。站内编辑器不支持外链图片时，下载表里的原 PNG 上传至 Gallery，保留标题、光影和开关条件。不要另做效果图或把测试标记抹掉。默认枪包资产署名 **TACZ Dev Team / CC BY-NC-ND 4.0**；不把截图或第三方光影宣称为本项目原创素材。

本目录文案**依据各站官方规则原文撰写**，来源：

- CurseForge [Moderation Policies](https://support.curseforge.com/support/solutions/articles/9000197279-moderation-policies)
- Modrinth [Content Rules](https://modrinth.com/legal/rules)
- MC百科 [模组收录规则](https://bbs.mcmod.cn/thread-3036-1-1.html)、[主站编辑规范](https://bbs.mcmod.cn/thread-646-1-1.html)

---

## 🔴 最重要的一条：项目名**不能带版本号**

这是查规则时发现的**硬性要求**，两大站都明文禁止：

> **CurseForge**：*"Names should not contain game name, versions, file versions
> ect.... Any technical information belongs in the description or relevant file
> tagging."*
>
> **Modrinth** §5.2：*"Project titles are only the name of the project, without
> any other unnecessary filler data."*

所以三站统一用：

```
[UNOFFICIAL]TaCZ Refabricated
```

（MC百科的**中文名**为「永恒枪械工坊:零 - Fabric 移植版」，不加前缀，理由见该文件）

**不要**用 `TaCZ Fabric 26.2 Port` / `TaCZ Refabricated 26.2 (Unofficial Port)`
—— 前者含版本号与游戏名，后者含版本号与 filler data，**都会被驳回**。

> 这同时解决了「以后移植到别的 MC 版本会很尴尬」的问题：
> 项目名长期稳定，版本信息由**文件名**（`TACZ-Refabricated-26.2-1.1.8+fabric.26.2.R3-hotfix2.jar`）
> 和**版本号字段**承载 —— 这正是 CurseForge 规则里说的
> "belongs in ... relevant **file tagging**"。

### 为什么保留 `[UNOFFICIAL]` 前缀是安全的

规则禁止的是 *game name, **versions**, file versions*，
`[UNOFFICIAL]` 属**状态标注**，不在禁止之列。另有三点支撑：

1. **实证先例**：`[UNOFFICIAL] TaCZ NeoForge Port`（CurseForge，48 万下载）
   与 `[UNOFFICIAL] TaCZ 1.21.1 NeoForge Port`（Modrinth，49 万下载）
   都用同一形式，均已过审并长期在线 —— 后者甚至带着版本号。
2. **Modrinth §5.2 是软规则**：该章前言明写
   *"will not necessarily always be enforced"*，只影响审核速度。
3. **它服务于一条硬规则**：Modrinth §1.9 禁止让人误以为项目获得他人背书。
   明确标注 UNOFFICIAL 正是在履行该要求，比裸名更稳妥。

---

## 建议发布顺序

```
1. Modrinth      ← 规则最明确、审核最快，先拿到下载链接
2. CurseForge    ← 可与 1 并行提交
3. MC 百科        ← 最后做：需填前两者的下载链接，
                    且站方规则要求「必须已发布可运行版本」
```

---

## 三站共同红线

1. **必须关闭收益分成 / 货币化。**
   默认枪包资源是 `CC BY-NC-ND 4.0`，**NC = 非商业**。
   开启 CurseForge Rewards Program 或 Modrinth Monetization 均违反该条款。

2. **必须署名原作者并说明是非官方移植。**
   代码 GPL-3.0 使移植合法，但三站都要求署名：
   - CurseForge：*"credit and link the original creator"*
   - Modrinth §1.9：不得让人误以为获得他人背书
   - MC百科 收录规则第 5 条：涉及侵权、剽窃实锤的Mod不收录

3. **必须说明「改了什么」，不能照抄上游描述。**
   CurseForge 明文：*"If it is a fork, you must describe what has changed from
   the original and you cannot copy the description of the original project."*
   → 文案里的「What was changed / 相较原版的改动」一节是**规则要求的**，不能删。

4. **配置项的措辞必须以游戏内界面为准。**
   绝大多数选项（含全部 `ScopePip*` 玩家项）都在 Mod Menu + Cloth Config 的界面里，
   保存后立即生效且会写回 TOML；**不要**再把「编辑 `tacz-client.toml` 并重启」
   写成唯一路径 —— 该说法在 R3 之后是错的，会让玩家白折腾一遍。
   只有诊断项（`ScopePipDebug*` 等）、`ScopePipMinAimingProgress`、
   `ScopePipReleaseIdlePipeline` / `ScopePipIdleReleaseDelayFrames` 与
   `AimingSwayIntensity` 仍只能在 TOML 里改。
   （权威清单：`src/main/java/com/tacz/guns/compat/cloth/client/RenderClothConfig.java`。）

---

## 各站独有的坑

### CurseForge
- **描述里不能放外部下载链接**（"External download links for files are not
  allowed"），GitHub Releases 也算。源码/Issue 放 Links 字段。
- 捐赠、个人网站、跨站链接**只能放页面最底部**。
- **Avatar 必须 400×400**，不可纯色，**不可使用受版权图像**
  → 别直接拿 TACZ 官方图。
- 项目类型选 `Mods`，不是 `Customization`。
- Relations 里 **Forge Config API Port 别漏**（硬依赖，缺了游戏起不来）。

### Modrinth
- Summary **不能带 Markdown 格式**，且**不能重复标题里的词**（§5.3）。
- 依赖必须填在 **Dependencies 字段**，只写在描述里不算（§5.6）。
- Gallery 每张图**都要有标题**（§5.5）。
- License 选 `GPL-3.0-only`，枪包资源的 CC 许可在描述里补充说明。

### MC 百科
- **先查 TACZ 是否已有词条** —— 站方一般不为非官方移植单开词条，
  优先在原词条下补充「移植版本」信息。
- **必须先有可下载的正式构建**（收录规则第 6 条：未发布过可运行版本不收录）。
- 正文**禁止**：主观词句、第一人称（「小编」「笔者」）、不确定语句
  （「据说」「貌似」）、个人署名（「由XXX汉化」）、冗余句（「欢迎各位补充」）。
  → 文案已按此撰写，**请勿自行加回这类句子**。
- **modid 沿用 `tacz` 需主动说明**，避免被误判为收录规则第 11 条
  「恶意占用已存在的MODID」。文案末尾的提交备注已包含该说明。

---

## 旧文件文案例子：26.2 R3-hotfix2

下表是历史单文件口径，不是跨版本项目页的当前依赖表。当前 26.3 见[分支 README](../../README.md)，另外三条线见[交接基线](../lineage/26.3-backports/README.md)。

| 项 | 值 |
|---|---|
| 模组版本号 | `1.1.8+fabric.26.2.R3-hotfix2` |
| Minecraft | `26.2` |
| Fabric Loader | `>=0.19.3` |
| Fabric API | `0.155.2+26.2` |
| Java | `>=25` |
| 硬依赖 | `forgeconfigapiport >=26.2.1` |
| modid | `tacz`（**不可更改**，枪包依赖此 ID） |
| 代码许可 | GPL-3.0（移植的第三方代码各自沿用上游 GPL-3.0） |
| 随包运行库许可 | MIT（Mayday Animation Engine 1.1.1） |
| 默认枪包资源许可 | CC BY-NC-ND 4.0 |
| 内置附属 | TacZ Mesh Loader [TML]，作者 **VellEagle**，来源 `1.21.1_fabric` v0.1.7（`provides: taczmeshloader`） |
| 配置入口 | Mod Menu → Timeless and Classics Guns → 齿轮 →「渲染」分类（**不是**只能改 TOML；界面无「客户端」层级，`setGlobalized(true)`） |
| PIP 状态 | 已实现、**实验性、默认关闭**，游戏内开关；不要写成「暂停开发 / 尚未实现」 |

项目正文保留两种常见缺图情况的区别：**整包资源加密**与**查看器作弊所得物品缺少组件**。后者可用创造栏同一物品做对照；旧版 REI 命令回退的技术分析见[专服诊断](../investigations/DEDICATED_SERVER_GETNAME_AUDIT_2026_08_21.md)。不要把所有紫黑贴图一律归因于某个前置。
