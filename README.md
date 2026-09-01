# [UNOFFICIAL] TaCZ Refabricated — Minecraft 26.2 / Fabric

[![CurseForge Downloads](https://cf.way2muchnoise.eu/full_1627909_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/unofficial-tacz-refabricated)
[![CurseForge Versions](https://cf.way2muchnoise.eu/versions/1627909.svg)](https://www.curseforge.com/minecraft/mc-mods/unofficial-tacz-refabricated/files)
[![GitHub Downloads](https://img.shields.io/github/downloads/q14433686-arch/TaCZ_Refabricated_Unofficial/total?logo=github&label=GitHub%20Downloads)](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/releases)

> **Unofficial Fabric port of TaCZ (Timeless & Classics Guns: Zero) for Minecraft 26.2,
> 26.1.2 and 1.21.11, with an LRTactical compatibility framework. Not an official TaCZ
> release; not reviewed or endorsed by the TACZ Dev Team. GPL-3.0.**

> **非官方社区移植，不是 TaCZ 官方发布，也未获 TACZ Dev Team 审核或背书。**

本分支把 [Sh1roCu/TACZ-Refabricated](https://github.com/Sh1roCu/TACZ-Refabricated)
的 Minecraft 1.21.1 Fabric 分支移植到 **Minecraft 26.2 Fabric**。直接上游的版本号为
`0.7.0-forge1.1.8-hotfix`；本分支当前源码版本为 **`1.1.8+fabric.26.2.R3`**。

[下载构建](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/releases)
· [CurseForge](https://www.curseforge.com/minecraft/mc-mods/unofficial-tacz-refabricated)
· [问题反馈](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/issues)
· [直接上游](https://github.com/Sh1roCu/TACZ-Refabricated/tree/1.21.1)
· [原始 TaCZ 项目](https://github.com/MCModderAnchor/TACZ)

### 选择你的 Minecraft 版本 / Pick your Minecraft version

| Minecraft | 源码分支 |
|---|---|
| **26.2** | [`26.2(main)`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/26.2%28main%29) |
| **26.1.2** | [`26.1.2`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/26.1.2) |
| **1.21.11** | [`1.21.11`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/1.21.11) |

本页面对应 **26.2** 分支。所有版本都需要 Fabric API 与 Forge Config API Port，
具体版本见下方表格与对应 Release 说明。

> 仓库源码已使用 R3 版本号；实际可下载版本及其发布日期以 Releases 页面为准。

---

## 1. 支持环境

| 项目 | 26.2 分支要求 |
|---|---|
| Minecraft | **26.2** |
| 加载器 | **Fabric Loader 0.19.3+** |
| Java | **25+** |
| Fabric API | 需要安装；R3 构建使用 **0.155.2+26.2** |
| Forge Config API Port | **26.2.1+，硬依赖** |
| 本 mod | **`1.1.8+fabric.26.2.R3`** |

这里只提供 Fabric 构建，不能与 Forge / NeoForge 版 TaCZ 或 LRTactical 混装。

R2 的可选集成（并非硬依赖）如下：

| 可选 mod | R2 核验/建议版本 | 用途 |
|---|---|---|
| JEI | 编译 pin **30.13.0.86** | 内置 Ammo Query 与工作台类别 |
| REI | 编译 pin **26.2.820** | 内置 Ammo Query 与工作台类别 |
| Carry On | 建议 **>=2.11.0** | A/B/C 多格工作台的搬运兼容 |

---

## 2. 新增 API 与功能

- [可替换弹药源 API](docs/AMMO_SOURCE_API.md)：下游可在不混入 TaCZ 内部背包代码的情况下，
  为特定实体/枪械提供只读查询和服务端消费弹药源；
- 稳定的具名 gameplay / Lua dispatch hooks，避免扩展依赖编译器生成的 `lambda$...` 名称；
- [Carry On 2.11 工作台兼容](docs/CARRYON_COMPAT.md)：从任一半格搬运、原子放下多格结构，
  并在 Carry On 的 26.2 `ItemStackTemplate` 渲染路径恢复枪包 `BlockId`；
- 内置 JEI/REI **Ammo Query**：从每一种已被加载枪械使用的弹药反查兼容枪械，排序、前 60 项
  固定显示和 overflow 轮换在两个 viewer 中共享同一份数据；
- 远程枪包同步完成后合并请求并刷新已安装的 recipe viewer，避免首轮注册早于网络 cache 时
  显示陈旧类别/查询数据。

完整发布范围、联网核验和未执行的实机矩阵见
[26.2 R2 release notes](docs/CHANGELOG_26_2_R2.md)。R1 的移植基础和历史说明仍保留在仓库历史中。

**R3**（当前源码版本）相对 R2-hotfix2 的增量（除标注「待实测」的项外均实机 PASS）：

- **内置 TML**（见第 3 节与 [`docs/MESH_LOADER.md`](docs/MESH_LOADER.md)）：`model_type: "mesh"`
  高模枪渲染 + 第一人称 GPU 静态烘焙;
- **世界语境 GPU 烘焙**：其他玩家手持、掉落物、展示框/展示台上的高模枪也走常驻 VBO 烘焙
  （每枪每帧只传骨骼矩阵），配光照量化档 LRU 缓存与每帧烘焙额度，多人场景帧数不再被
  逐顶点 CPU 变换吃掉（**光影下的世界枪照明仍待实测**，异常时游戏内关 `MeshGpuWorld` 即回退）;
- **镜内裁剪三件套**：光影下开镜时，第一人称手臂、瞄具挂载文字（如 MK5HD
  弹药计数）与准星一样被裁剪在目镜圆孔内，不再穿出镜筒;
- **瞄具文字内容修复**：枪包把显示串直接内联在 `text_key`（如 `%ammo_count%`）
  时不再出现 `Format error: ...` 前缀（26.2 移植期 `I18n.get` 误用回归）;
- **检视动画修复**：开镜时触发检视不再不可打断（开火/换弹可正常打断）;
- **跨包合成修复**：社区枪包被升级工具转成新 `tacz:nbt` 材料写法后「配方不显示也合不出来」
  的问题修好了（`TaczNbtIngredient` + JSON 归一化）;
- **开镜距离补偿**：开镜放大后，镜内的掉落物/第三人称高模枪不再因为「裸眼距离超阈值」
  退化成低模立方体（阈值按当前放大系数换算，整屏变焦与 PIP 都适用）——**待实测**;
- **二次渲染下的镜内高模枪修复**：开着 PIP 二次渲染时，视野内的高模枪在镜内也是未烘焙
  立方体的问题已修（与 1.21.11 / 26.1.2 姊妹线同因同修）——**根因由实机日志证明，
  修复效果待实测**;
- **配置持久化修复**：游戏内配置界面保存后**真的写回 TOML** 了（此前 FCAP 26.x 的保存
  断桥让保存只改内存、重启即「配置重置」）;
- **PIP 若干修复与新配置项**（倍率下限闸门、`ScopePipRerenderInterval`、
  `ScopePipShadowScale` 热应用等）。**全部 `ScopePip*` 玩家项现在都在游戏内配置界面里**
  （Mod Menu → 客户端 → 渲染，见 §4.2），不再需要手动编辑 TOML。

---

## 3. 项目范围

本仓库包含：

- TaCZ 的 Fabric 26.2 端口及随上游带来的默认枪包；
- 为 26.x API 改写的网络、资源加载、GUI 和渲染接线；
- 一套内置的 **LRTactical 兼容框架**；
- 内置的 **TacZ Mesh Loader [TML]**（`model_type: "mesh"` 高模 poly_mesh 渲染，
  移植自 VellEagle 的 [TacZMeshLoader](https://github.com/VellEagle/TacZMeshLoader)，GPL-3.0。
  含第一人称 GPU 静态烘焙——高模枪的逐顶点 CPU 变换成本归零，光影下同样生效；
  世界语境有近距全模豁免与顶点预算保护。依赖外置 TML 的枪包在本 mod 下
  视为依赖满足（`provides: ["taczmeshloader"]`）。范围、配置与已知边界见
  [`docs/MESH_LOADER.md`](docs/MESH_LOADER.md)）；
- 若干可选模组的兼容接线。

这不代表本项目是 TaCZ 或 LRTactical 的官方版本，也不代表所有第三方枪包、
战术装备包或 shader pack 都已经兼容。

### LRTactical 的准确范围

本分支的 `fabric.mod.json` 通过 `provides: ["lrtactical"]` 提供依赖标识；源码中已接入
melee、consumable、detonator，以及 explode / sticky / smoke / stun / effect-cloud
五类 throwable 的数据加载和基础运行路径。

同时必须说明：

- 这是**部分代码与数据驱动框架**，不是原作的完整 Fabric 发行版；
- `flash_shield` 没有移植；
- 仓库没有打包原作的完整美术资源集，内容包仍需自带其获准分发的模型、贴图、动画和音效；
- `provides` 只能满足 Fabric 的依赖 ID 检查，不等于任意 LRTactical 内容包都能直接运行；
- 本移植产生的问题请反馈到本仓库，不要要求原作者为本端口提供支持。

---

## 4. 瞄具渲染：默认与上游一致，实验性画中画（PIP）可选

### 4.1 默认路径：目镜掩码

**TaCZ 直接上游的瞄具不是 Picture-in-Picture，也不会为镜片再渲染一次世界。这一事实没有变化。**

人工核对直接上游 1.21.1 的提交
[`d290355`](https://github.com/Sh1roCu/TACZ-Refabricated/commit/d2903554da039d2355920953a81447784a3f2be2)
中 `BedrockAttachmentModel` 与 `BedrockGunModel` 后，可以确认：上游在当前第一人称场景中
使用 stencil 值控制目镜、镜身、准星和枪体片元；镜片后看到的
仍是同一次世界渲染。它没有第二台相机，也没有第二次 `renderLevel`。

26.2 端口的**默认实现**仍是目镜掩码方案，无法沿用上游的即时 stencil 调用与绘制时序，因此改为：

1. 把目镜几何写入一张离屏**掩码纹理**；
2. 枪身、相关配件、枪口火光和准星的 shader 采样该掩码并按区域丢弃片元；
3. 世界场景本身仍只渲染一次。

这里的离屏目标只保存目镜掩码，**不是第二份世界画面**。默认配置下「镜内放大」依旧来自
整屏 FOV 变焦，镜筒周围的画面会跟着放大——与上游行为一致。代码中的
`PictureInPictureRenderer` 仅用于枪械工作台的 GUI 模型预览，与瞄具实现无关（本节的
画中画是 `ScopePipRenderer`，二者是两套东西）。

Iris 有单独的 HAND shader 接线；其他 shader pack 仍可能改写自定义管线的最终效果。
Sulkan 目前没有等价接线，检测到时会回退到不启用镜内掩码裁剪的普通瞄具几何。

### 4.2 实验性镜内画中画（Scope PIP）：默认关闭，按需开启

26.2 主线最新合并（[PR #66](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/pull/66)）
新增了可选的镜内画中画：**镜筒外保持 1×，只有镜片内按瞄具倍率放大**。这是对上游行为的
刻意偏离，属于**实验性功能**：

- **默认完全关闭**（`ScopePipEnable=false`）：不动配置，行为与之前版本完全一致；
- 两种镜内成像方式：**重投影**（默认；复用已渲染好的主画面，代价小，但高倍镜下镜内偏软）
  与**二次渲染**（`ScopePipRerender=true`；用窄 FOV 把世界真画一遍，镜内原生分辨率，
  代价是每帧多一遍完整世界渲染）；
- 光影包（Iris）下也可用：光影开启时镜内走独立的第二世界渲染通道，需额外开关放行（见下）；
- 对 Sodium、Voxy、Physics Mod 的投影同步做了专门处理，但兼容面仍在扩大中。

#### 开启教程

**这些项都在游戏内配置界面里**，正常情况下不需要手动改文件。

1. 装 [Mod Menu](https://modrinth.com/mod/modmenu)（本模组的配置入口）；
   要在光影下开 PIP 再装 [Iris](https://modrinth.com/mod/iris)。
2. **Mod 列表 → Timeless and Classics Guns → 配置（齿轮图标）→ 客户端 → 渲染**，
   在「瞄具画中画（PIP）」一组里改：

   | 界面里的名字 | 对应 TOML 键 | 什么时候要开 |
   |---|---|---|
   | 瞄具画中画（PIP） | `ScopePipEnable` | 主开关，先开它 |
   | PIP 二次渲染模式 | `ScopePipRerender` | 想「镜内真能瞄准」就开它（高倍镜强烈建议） |
   | 允许在光影下开启 PIP | `ScopePipAllowShaderPacks` | 用光影时额外开它 |
   | 最低倍率 / 分辨率比例 / 重渲染频率 / 共享变焦 / 锐化 / 独立管线 / 阴影缩放 | 同名 `ScopePip*` | 按需微调，不动也能用 |

3. 界面里保存后**立即生效，不需要重启**；唯一的例外是 `ScopePipShadowScale`
   （镜内阴影贴图比例）—— 它在瞄具管线构建时读取，改完需**重启游戏或切换维度**。
4. 界面保存会**真的写回** `.minecraft/config/tacz-client.toml`（R3 修好了 FCAP 26.x
   的保存断桥），重启后配置仍在。**注意**：修复前 TOML 里已经写死旧值的字段不会
   自动更新，需要在界面里改一次并保存，才会被新值覆盖。

不装 Mod Menu、或习惯直接改文件也可以：编辑 `.minecraft/config/tacz-client.toml`
的 `[render]` 段，**改完重启游戏**生效。TOML 同时是**唯一**能改到「没进界面」那几项的
地方：`ScopePipDebug*`（诊断输出）、`ScopePipMinAimingProgress`、
`ScopePipReleaseIdlePipeline` / `ScopePipIdleReleaseDelayFrames`（空闲释放策略），
以及与 PIP 无关的 `AimingSwayIntensity`（开镜持枪晃动倍率）。

最小开启（无光影环境）等价于：

```toml
[render]
ScopePipEnable = true        # 总开关，默认 false
# ScopeMaskEnable = true     # PIP 依赖目镜掩码定位镜片；该项默认即 true，勿关
```

高倍镜（6×/8×）建议同时开二次渲染，镜内不再受「屏幕分辨率 ÷ 倍率」的上限约束：

```toml
ScopePipRerender = true      # 镜内原生分辨率；每帧多一遍完整世界渲染
```

光影包（Iris）环境还需显式放行；二次渲染 + 光影时建议保持管线隔离默认开启：

```toml
ScopePipAllowShaderPacks = true   # 默认 false 是保守默认，不是已知不兼容
# ScopePipIsolatePipeline = true  # 默认 true：镜内那一遍用独立 Iris 管线，避免时域效果污染主画面
# ScopePipShadowScale = 0.5       # 镜内阴影贴图比例，默认值可省约 3/4 的镜内阴影开销
```

可选微调（前两项界面上都有）：`ScopePipSharpness`（重投影模式镜内锐化，默认 0.5）、
`ScopePipWorldZoomShare`（把多少倍率还给世界画面：0 = 纯 PIP、1 = 等同关闭，默认 0）；
`AimingSwayIntensity`（开镜持枪晃动倍率，默认 1.5，高倍镜下可适当调高）**只能在 TOML 里改**。

注意两点：Sulkan 渲染器下目镜掩码本身会回退，PIP 随之无条件停用；若 PIP 运行中出现渲染
故障，它会在日志报错后自动停用当次会话，在界面里关掉「瞄具画中画（PIP）」（或把 TOML 里的
`ScopePipEnable` 改回 `false`）即完整回到默认行为。

#### 实验性声明

**PIP 是实验性功能，出 bug 完全正常。** 可能出现的问题包括但不限于：高倍镜画质不及预期、
光影下镜内外色调差异、帧率大幅下降（尤其二次渲染 + 光影，帧率约减半）、以及个别渲染模组
组合下的画面异常。遇到问题先把 `ScopePipEnable` 改回 `false` 复测，确认与 PIP 相关后再反馈，
并附上：全部 `ScopePip*` 配置取值（配置界面里能看到全部名字与当前值）、光影包名称、
以及 Sodium / Iris / Voxy 等渲染模组版本。
不打算尝鲜的玩家无需任何操作，默认路径不受该功能影响。

---

## 5. 安装

1. 安装 Minecraft 26.2、Fabric Loader 0.19.3+ 与 Java 25+；
2. 安装 Fabric API 和 Forge Config API Port 26.2.1+；
3. 从 [Releases](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/releases)
   下载明确标注为 **26.2 / Fabric** 的构建；
4. 把三个 mod 的 `.jar` 放入 `.minecraft/mods/`；
5. 启动游戏。第三方枪包按下一节安装。

不要只看文件名中的 `1.1.8`：还必须核对 Minecraft 版本与加载器。

---

## 6. 第三方枪包

### 加载目录

现代枪包放在 **`.minecraft/tacz/`**：

```text
.minecraft/
├── mods/
│   ├── TACZ-Refabricated-....jar
│   ├── fabric-api-....jar
│   └── forgeconfigapiport-....jar
├── tacz/
│   ├── some_pack.zip
│   └── another_pack/
└── tacz_backup/
```

zip 可以直接加载，也可以解压为目录。无论哪种形式，包根目录都必须有：

```text
gunpack.meta.json
```

最小示例：

```json
{ "namespace": "your_pack_namespace" }
```

对 zip 来说，该文件必须位于压缩包根部，不能再多套一层目录。缺少它时，
`GunPackLoader` 不会把该文件当作现代枪包加载；zip 情况会在日志中记录
`No gunpack.meta.json found`。

### 旧包转换

不要只凭“适用于 1.20”之类的游戏版本标签判断是否需要转换：部分 1.20.1 枪包已经是
现代布局，可以直接放进 `tacz/`。应以**包结构**为准。

只有旧布局包才放进 `.minecraft/tacz_backup/`，然后在客户端执行：

```text
/tacz convert
```

转换器会生成带 `gunpack.meta.json` 的输出；它不能保证自动修复所有旧资源、配方或脚本差异，
请保留原包备份并检查游戏日志。

### 版本约束

枪包可以在 `gunpack.meta.json` 的 `dependencies` 中声明版本谓词。本分支用 `1.1.8`
作为 SemVer 核心，`+fabric.26.2.R3` 是构建元数据，不参与 Fabric 的版本先后比较。
一个枪包最终是否通过检查，仍取决于它写下的完整谓词，不能笼统理解为“所有旧包都兼容”。

### 依赖 TacZ:Arcana 的内容

本仓库不提供 Arcana，也没有实现 Arcana 的 API 或资产保护/加载流程。
截至 **2026-08-12** 核对，[Arcana 的官方发布页](https://www.curseforge.com/minecraft/mc-mods/tacz-arcana-timeless-and-classics-guns)
提供的是 **Minecraft 1.20.1 Forge** 文件；因此明确要求 Arcana 的内容不能视为本 Fabric
26.x 端口的受支持内容。

请先查看内容包作者列出的前置依赖。紫黑贴图或模型缺失本身不能证明“这个包一定依赖 Arcana”，
也可能是目录层级、资源路径、版本谓词或包本身不完整造成的。

---

## 7. 当前已知边界

- LRTactical 是部分兼容框架，`flash_shield` 未实现；第三方包兼容性需要逐包验证。
- 26.2 的瞄具裁剪默认仍是 branch-specific 的掩码实现，不应描述成上游行为；镜内画中画（PIP）
  是实验性可选开关（见第 4 节），不保证每个 shader pack 与渲染模组组合都得到完全相同的结果。
- 26.2 的现有实测记录中，Player Animation Library 在经历 TaCZ 趴姿再站起后，下一次切枪的
  短暂第三人称 crossfade 仍可能带入旧姿态；切换完成后的稳态持枪不是该问题。
- 明确依赖 Arcana 的内容不受支持；其他枪包也不能仅凭“能被扫描到”就视为完全兼容。
- 镜内文字裁剪对 ttf/unihex 灰度字体（第三方资源包替换默认字体时）回退 vanilla
  管线：文字照常显示但不参与镜内裁剪，属可接受降级。
- TML 高模（poly_mesh）：世界语境（第三人称/掉落物/展示台）在
  `MeshWorldFullDetailDistance`（默认 16 格）内始终画全模，超出后受
  `MeshWorldMaxVertices` 顶点预算保护（超预算只画立方体）；这是刻意的
  远近取舍，不是渲染缺失。mesh 目镜不支持（与外置 TML 一致）。
- TML 高模的**世界语境贴图**解析走的是枪包原始贴图路径，与第一人称的 `…/gun/uv/…`
  变体不同源；把贴图放在 `uv/` 子目录下的枪包，世界里的高模枪会报
  `Missing resource` 并显示缺材质贴图（第一人称正常）。**已知未修**，遇到时可游戏内
  关 `MeshGpuWorld` 或把该枪包贴图同时放一份到不带 `uv/` 的路径。
- 「二次渲染下的镜内高模枪」修复与「开镜距离补偿」两项**尚未经实机验证**，
  措辞与验收清单见 [`docs/MESH_LOADER.md`](docs/MESH_LOADER.md) §5.2。

提交兼容问题时请给出实际包名与版本、完整日志和最小复现环境，不要只给缺失贴图截图。

---

## 8. 许可与来源

本仓库**不只有一套资产许可，也不应把整个仓库简单概括为“两套许可”**：

- TaCZ 与本端口对应代码使用 GPL-3.0；
- 移入的 LRTactical 代码部分沿用其 GPL-3.0；
- 移入的 TacZ Mesh Loader（TML）代码部分沿用其 GPL-3.0，来源为
  [VellEagle/TacZMeshLoader](https://github.com/VellEagle/TacZMeshLoader) 的
  `1.21.1_fabric` v0.1.7（作者已在 `fabric.mod.json` 的 `contributors` 中署名）；
  本仓在其之上另写的第一人称 / 世界语境 GPU 烘焙层、光照量化缓存与瞄具相关适配
  为本仓原创，同样以 GPL-3.0 释出；
- 默认枪包的 `gunpack_info.json` 声明其资源为 CC BY-NC-ND 4.0；
- 随 jar 打包的 Mayday Animation Engine 使用 MIT；
- 其他第三方库、资源和外部内容包可能有各自许可。

详见 [`LICENSE`](LICENSE) 与 [`LICENSES.md`](LICENSES.md)。代码许可不会自动覆盖美术资源，
本仓库兼容某个第三方内容包也不代表取得、转授或改变了该内容包的许可。

本项目按“原样”提供，不附带担保。请勿把本移植的问题提交给 TaCZ 或 LRTactical 原作者。

---

## 9. 从源码构建

需要 Java 25：

```bash
./gradlew build
```

产物位于 `build/libs/`。

---

## 10. 反馈

请在[本仓库 Issues](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/issues)提交：

1. 完整 `latest.log` 或崩溃报告；
2. Minecraft、Fabric Loader、Fabric API、Forge Config API Port 与本 mod 的完整版本；
3. 第三方枪包和可选模组的名称与版本；
4. 是否能在“本 mod + 两项硬依赖”的最小环境复现；
5. 使用 shader pack 时，注明 Iris/Sodium 与 shader pack 的具体版本。
