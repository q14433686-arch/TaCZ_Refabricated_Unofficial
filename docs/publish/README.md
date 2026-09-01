# 发布文案（三站）

复制粘贴用。`[[ ]]` 里的内容**需你自己替换**，发布前搜 `[[` 全部处理掉。

| 文件 | 平台 | 性质 |
|---|---|---|
| `CurseForge.md` | CurseForge | 文件托管站，审核较严 |
| `Modrinth.md` | Modrinth | 文件托管站，规则条目化、可预期 |
| `MCMOD.md` | MC 百科 (mcmod.cn) | **Wiki 型资料站，通常不托管 jar** |

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
> 项目名长期稳定，版本信息由**文件名**（`TACZ-Refabricated-26.2-1.1.8+fabric.26.2.R3.jar`）
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
   绝大多数选项（含全部 `ScopePip*` 玩家项）都在 Mod Menu 的配置界面里，
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

## 文案中的事实（均已核对仓库，改写时别改错）

| 项 | 值 |
|---|---|
| 模组版本号 | `1.1.8+fabric.26.2.R3` |
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

三份文案都包含「需要 TacZ:Arcana 的加密枪包无法加载」一节 ——
该问题症状是紫黑块、极易被误判为本移植版的 bug，
且因版本号已对齐 `1.1.8`，这类包反而能通过版本校验、正常建出条目，
迷惑性更强。提前写入描述可省去发布后大量重复答疑。

三份文案还都包含「专服 REI/JEI 作弊拿取 → 紫黑块 + item.* 原始键」FAQ
（Modrinth/CurseForge 为英文段落，MC 百科为正文第八节）——
同样属于"症状像本移植版 bug、实际是 REI 服务端未安装时的 /give 命令兜底
丢掉了数据组件"的高频问题；起因核到 REI 26.2.820 源码
（`ClientHelperImpl#tryCheatingEntry` 的 `tagMessage` 硬编码为空，
`TODO 24w09a` 组件化后未适配）。完整技术推导与源码引用见
`docs/investigations/DEDICATED_SERVER_GETNAME_AUDIT_2026_08_21.md` 第 8、9 节。
