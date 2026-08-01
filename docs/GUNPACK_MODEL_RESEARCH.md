# 自制 TACZ 枪包：不会建模也能拿到"其他枪械模型"——调查与实操指南

> 调查日期：2026-08-01
> 针对仓库：`q14433686-arch/TaCZ_Refabricated_Unofficial`（TACZ 26.2 移植版，内置 `tacz_default_gun` 完整默认枪包）
> 一句话结论：**枪模不是"3D 建模"，而是"搭积木"**——TACZ 枪械模型是 Blockbench 基岩版实体模型（`geo.json`，方块+骨骼的纯 JSON）。你缺的不是建模技术，而是①一套正确比例的标准、②现成模型/素材的来源、③把素材变成 TACZ 格式的流程。这三样本报告全部给到。

---

## 0. 一分钟速览

| 问题 | 答案 |
|---|---|
| TACZ 枪模型是什么格式？ | Blockbench **基岩版实体模型**（Bedrock `geo.json`，format 1.12.0），全部由方块（cube）+ 骨骼（bone）组成，纯 JSON 文本 |
| 比例的标准是什么？ | 官方铁律：**1000mm = 48 grid**，模型长度 = 真实全长(mm) × 48 ÷ 1000 |
| 默认枪包的资源能不能抄？ | **不能**。默认枪包全部美术资源是 **CC BY-NC-ND 4.0**（不能改、不能再分发）；但比例、尺寸数据、骨骼命名规范、JSON 写法是官方 Wiki 公开的"接口规范"，可以放心借鉴 |
| 不会建模怎么拿到其他枪？ | 三条主路线：**A.** 找现成的 TACZ 格式模型直接改；**B.** 用真实侧视图+真实尺寸在 Blockbench 里"描"（官方推荐工作流，零基础可做）；**C.** 下载 CC0/CC-BY 枪模 → 体素化 → 导入 Blockbench 精修 |
| 本报告附带什么？ | ① 默认枪包 **56 把枪的实测比例表**（用脚本量出来的，见第 3 节）；② 可复用的测量脚本 `docs/gunpack_tools/measure_proportions.py` |

---

## 1. 协议边界：能借鉴什么，不能抄什么

### 1.1 默认枪包的许可（这就是你说的"协议"）

- TACZ 官方：**代码 GPL-3.0/AGPL-3.0，全部美术资源（模型、贴图、动画、音效、默认枪包）CC BY-NC-ND 4.0**（[官方资料页](https://www.zitbbs.com/thread-6066-1-1.html)、[MineBBS 授权说明](https://www.minebbs.com/resources/timeless-and-classics-zero.10215/)）。
- 本仓库的 `LICENSES.md` 也写明：美术资产可能按各自原作者的许可（如 CC BY-NC-ND 4.0）单独授权，仓库不主张所有权。
- **CC BY-NC-ND 的含义**：署名（BY）+ 非商业（NC）+ **禁止演绎（ND）**。ND 意味着不仅不能直接拷贝，连"改一下再分发"都不行——所以"把默认包的 AK 改成 SCAR 再发布"这条路是死的。
- 注意：**社区枪包绝大多数也是 CC BY-NC-ND**（[TaCZ JS 的 MC百科页](https://www.mcmod.cn/class/16961.html)原话："TaCZ 的大部分枪包都是使用 CC BY-NC-ND 4.0 授权的"）。所以你从别处下的每一个包，发布前都要单独核对许可。

### 1.2 可以放心借鉴的部分（本报告的前提）

1. **事实数据**：真实枪械的尺寸、全长、口径——事实不受版权保护。官方 Wiki 甚至**要求**你做模型时查真实数据。
2. **官方 Wiki 公开的教程与规范**：骨骼组名、JSON 字段、工作流（[官方 Wiki](https://tacwiki.mcma.club/) 本身就是为了让人照着做枪包而写的）。
3. **"写法"（JSON 结构）**：`display`/`index`/`data` 文件里的键名、嵌套结构属于接口规范，照着自己写一遍没有任何问题——本仓库默认枪包里的 `ak47_display.json` 甚至自带中文注释，是最好的教材。
4. **比例数据**：通过测量得出的数值（尺寸、长度、位置关系）是事实。本报告第 3 节就是这么干的——**量**默认包，而不是**抄**默认包。

一句话：**抄"规矩"不抄"资产"**。模型、贴图、动画、音效文件本身一个字都别复制；比例、命名、写法随便用。

---

## 2. TACZ 枪模型的格式与"写法"（借鉴对象）

### 2.1 文件结构（默认枪包即活教材）

```
tacz_default_gun/                    # 枪包根目录
├── gunpack.meta.json                # {"namespace": "tacz"} 命名空间
├── assets/tacz/
│   ├── geo_models/gun/ak47_geo.json # ★ 枪械模型（Bedrock geo，Blockbench 导出）
│   ├── geo_models/gun/lod/ak47.json # 低模（第三人称/远处用）
│   ├── animations/ak47.animation.json   # 动画（Bedrock 格式）
│   ├── display/guns/ak47_display.json   # ★ 客户端配置（模型/贴图/动画/音效引用）
│   ├── textures/gun/uv/ak47.png     # 模型 UV 贴图（128×128）
│   ├── textures/gun/slot|hud/lod/   # 背包2D图 / HUD图 / 低模贴图
│   ├── scripts/ak47_state_machine.lua   # 状态机（射速/连发模式）
│   └── tacz_sounds/ak47/*.ogg       # 音效
└── data/tacz/
    ├── index/guns/ak47.json         # 枪械定义（注册）
    ├── data/guns/ak47_data.json     # 数值（伤害/射速/后座/弹容）
    ├── recipe/guns/ak47.json        # 合成配方
    └── ...                          # 弹药、配件同构
```

> 新版枪包格式（1.1.4+）：模型放 `assets/{ns}/geo_models/...`、定义放 `data/{ns}/index/...`，详见[官方枪包指南](https://tacwiki.mcma.club/gunpack/)。**这些路径和字段照抄默认包就是标准答案。**

### 2.2 模型里的"特殊组名"（必须遵守的接口规范）

用 Blockbench 打开 `ak47_geo.json` 就能看到下面的骨骼树。**组名大小写敏感，程序按名字识别，错一个就崩或功能失效**。完整的官方规范见[《具有特殊功能的组名》](https://tacwiki.mcma.club/zh/gunpack/gun/03_sp_group_name.html)，我在本仓库模型里实测核对的清单：

| 组名 | 作用 | 备注 |
|---|---|---|
| `root` | 根组，所有枪体内容挂它下面 | **硬性要求**，没有它默认动画失效 |
| `camera` | 第一人称摄像机动画分组 | 用 Blockbench Cameras 插件加相机 |
| `views` → `iron_view` / `idle_view` | 机瞄/腰射的摄像机定位 | 只定位不渲染，pivot 即"眼球位置" |
| `positioning` → `ground` / `thirdperson_hand` / `fixed` | 掉落物/生物手持/展示框定位 | 同上 |
| `refit_view`、`refit_muzzle_view`、`refit_scope_view`、`refit_stock_view`、`refit_extended_mag_view`、`refit_grip_view`、`refit_laser_view` | 改枪界面相机 | 没有会怎样？参考[旧版文档](https://tacwiki.mcma.club/zh/gunpack/legacy/gun_refit/) |
| `muzzle_pos` / `scope_pos` / `stock_pos` / `grip_pos` / `laser_pos` | 配件安装定位 | pivot 位置即配件挂点，须受动画影响 |
| `muzzle_flash` | 枪口火焰/曳光弹定位 | 在枪口前方一点 |
| `shell` | 抛壳定位 | 抛壳口 |
| `magazine`（含 `mag_standard`、`mag_extended_1/2/3`） | 默认/三级扩容弹匣 | 允许扩容就必须放全 3 级（即使模型相同） |
| `bullet_in_mag` | 弹匣内子弹 | 弹容为 0 时自动隐藏 |
| `additional_magazine` | 换弹动画里"旧弹匣"副本 | pivot 必须与 `magazine` 完全一致，**绝不能放在 `magazine` 下面**（循环调用崩溃） |
| `sight` / `sight_folded` | 基础瞄具折叠逻辑 | 装瞄具后隐藏 `sight` 显示 `sight_folded` |
| `muzzle_default` / `stock_default` / `handguard_default` | 默认枪口/枪托/护木 | 装对应配件时自动隐藏 |
| `mount` / `attachment_adapter` | 瞄具导轨 / 枪托适配器 | 如 AK 适配 AR 枪托 |
| `constraint` | 动画约束（ICA） | 高级功能 |
| 任意名 + `_illuminated` | 发光组 | 满亮度渲染（荧光机瞄），别加在固定组名后 |

### 2.3 动画与数据"写法"

- 动画：`ak47.animation.json` 是标准 Bedrock 动画文件；偷懒方案是 `display` 里写 `"use_default_animation": "rifle"` 或 `"pistol"`（[官方说明](https://tacwiki.mcma.club/gunpack/animation/)），先用默认动画跑通，再慢慢自研。
- 状态机：`scripts/ak47_state_machine.lua`——照抄结构改参数即可（这是脚本代码，GPL 的 mod 本体代码与"自定义枪包"互不构成演绎，放心写）。
- 数值：`data/guns/xxx_data.json` 里伤害、射速、后座等，直接以同类枪为基准调参。

---

## 3. 比例标准 + 默认包实测比例表（本报告核心）

### 3.1 官方标准比例（[官方模型指南](https://tacwiki.mcma.club/zh/model_guide/model.html)）

- **1000mm = 48 grid**：模型长度 = 真实全长(mm) × 48 ÷ 1000。
- 例：全长 880mm 的 AKM → 880×48/1000 = **42.24 grid**（我实测默认包 AK47 模型为 43.5 grid ≈ 906mm，含枪口装置，吻合度 97%+）。
- 建模硬规则：方块尺寸取 **0.125 的倍数**；模型关于 **Z-Y 平面对称**（枪居中）；**枪口朝 -Z（北）**；方块/骨骼旋转轴用 pivot。
- 参考尺度（官方给出，用于目测其他部件宽度）：**皮卡汀尼导轨底部宽 0.75 grid**，**握把通常宽 1.5–2 grid**。
- 圆的做法：半径 >1 用八边形（枪管、瞄具），细小的（<0.75）用单方块，弹鼓类可用十六边形；**尽量实心**。
- 防 Z-fighting：交叉面用 `inflate` 0.001–0.005 微胀。

### 3.2 默认枪包 56 把枪实测比例表

以下数据由我写的脚本 `docs/gunpack_tools/measure_proportions.py` 直接测量本仓库默认枪包得出（逐方块套骨骼变换求包围盒，排除手部模型；`len_mm` = grid × 1000/48 换算）。**这就是"基于默认包比例"最实在的产出**——全是数值事实，不是资产拷贝：

| 枪械 | 全长(grid) | 等效mm | 枪口z | 枪械 | 全长(grid) | 等效mm | 枪口z |
|---|---|---|---|---|---|---|---|
| aa12 | 41.93 | 874 | -22.2 | m1911 | 11.13 | 232 | -6.25 |
| ai_awp | 57.07 | 1189 | -32.88 | m249 | 47.66 | 993 | -22.19 |
| ak47 | 43.54 | 907 | -19.32 | m320 | 21.25 | 443 | — |
| aug | 36.85 | 768 | -14.06 | m4a1 | 46.88* | 977 | -24.25 |
| b93r | 13.00 | 271 | -4.33 | m700 | 38.10* | 794 | -24 |
| cz75 | 13.56 | 283 | -5.97 | m870 | 46.56 | 970 | -23.12 |
| db_long | 43.78 | 912 | — | m95 | 63.18 | 1316 | -34.1 |
| db_short | 34.60 | 721 | — | m9a4 | 11.01 | 229 | -4.45 |
| deagle | 13.05 | 272 | -2.97 | minigun | 50.36 | 1049 | — |
| fn_evolys | 46.36 | 966 | -19.06 | mk14 | 46.38 | 966 | -19.1 |
| fn_fal | 52.50 | 1094 | -22.5 | p320 | 11.41 | 238 | -4.44 |
| g36k | 83.40* | 1738 | -18.25 | p90 | 23.32 | 486 | -3.81 |
| glock_17 | 12.45 | 259 | -5.05 | qbz_191 | 40.65 | 847 | -16.38 |
| hk416a5 | 35.94 | 749 | -13.19 | qbz_95 | 37.12 | 773 | -14.25 |
| hk416d | 31.50 | 656 | -11.38 | rhino357 | 13.41 | 279 | — |
| hk_g3 | 49.14 | 1024 | -13.93 | rpg7 | 52.17 | 1087 | — |
| hk_mk23 | 12.40 | 258 | -1.48 | rpk | 61.81* | 1288 | -27.7 |
| hk_mp5a5 | 32.09 | 669 | -10.97 | scar_h | 43.27 | 901 | -14.75 |
| kar98 | 53.17 | 1108 | — | scar_l | 39.04 | 813 | -14.06 |
| lonetrail | 22.42 | 467 | -12.25 | sks_tactical | 50.15 | 1045 | -23.62 |
| m1014 | 47.56 | 991 | -23.51 | spas_12 | 51.86 | 1080 | -18.75 |
| m107 | 61.56 | 1282 | -25.38 | spr15hb | 50.12 | 1044 | -23.75 |
| m16a1 | 47.54 | 990 | -21.4 | springfield1873 | 62.38 | 1300 | -39.25 |
| m16a4 | 47.56* | 991 | -21.75 | taurus500 | 19.49 | 406 | -9.38 |
| taurus943 | 9.94 | 207 | — | timeless50 | 10.87 | 226 | -4.44 |
| type_81 | 46.21* | 963 | -21.32 | ump45 | 33.44 | 697 | -10.56 |
| uzi | 22.57 | 470 | -10.25 | vector45 | 28.30 | 590 | -11.53 |

\* = 模型含大角度旋转部件（折叠托/旋转细节件），包围盒偏大；枪口位置请以 `muzzle_pos` 的 z 值为准。

**按枪种归纳的"比例速查"（做新枪时直接套）：**

| 枪种 | 全长范围 | 典型值 | 备注 |
|---|---|---|---|
| 手枪 | 10–14 grid（210–290mm） | ~12 | 转轮手枪（taurus500）可到 19 |
| 冲锋枪/PDW | 22–33 grid（470–700mm） | ~30 | 微型冲锋枪（b93r）13 |
| 卡宾枪 | 31–36 grid（650–750mm） | ~33 | HK416D、AUG |
| 突击步枪 | 37–52 grid（770–1090mm） | ~44 | AK47 为 43.5 |
| 战斗步枪/精确射手 | 46–53 grid（960–1100mm） | ~50 | FAL、SKS、MK14 |
| 狙击步枪 | 53–63 grid（1100–1316mm） | ~58 | M95 最长 |
| 霰弹枪 | 35–52 grid（720–1080mm） | ~46 | 短管截断版 34.6 |
| 机枪 | 46–62 grid（960–1290mm） | ~50 | RPK 61.8 |
| 火箭筒/榴弹 | 21 / 52 grid | — | M320 21.3、RPG7 52.2 |

### 3.3 怎么用这张表

1. 选定目标枪（比如想加一把 G36C）→ 查真实全长（维基百科 infobox 的 "Length"）→ G36C 约 1000mm（托伸）→ 1000×48/1000 = **48 grid**。
2. 与同类对比：突击步枪默认包 37–52 grid，48 落在范围内 → 比例合格。
3. 枪口位置：真实世界"枪口到枪托底"的分布决定 `muzzle_pos` 的 z 值；默认包里突击步枪枪口 z 在 -14 ~ -25 之间，可参考（例如 AK47 在 -19.3、M4A1 在 -24.25）。
4. 宽度参考：握把 1.5–2 grid、导轨底 0.75 grid、机匣厚度约 1–1.5 grid、枪管口径 0.5–0.75 grid。

---

## 4. 获得"其他枪械模型"的五条路线（重点）

### 路线 A：直接找现成的 TACZ 格式模型（最快，零建模）

现成模型 = 别人已经按 TACZ 规范做好的 Blockbench 枪模，下载后换贴图/改骨骼名即可进包（发布需作者授权）。

| 渠道 | 怎么找 | 注意 |
|---|---|---|
| [Sketchfab](https://sketchfab.com/search?q=tacz&type=models) | 搜 `tacz`、`timeless and classics`、`blockbench gun`；左侧筛选 **Downloadable** + **CC BY / CC0** | 有作者把 TACZ 风格模型传上来（例如 [TACZ SCAR-L](https://sketchfab.com/3d-models/timeless-and-classic-scar-l-custom-3d07d16840814a03a2d7ae04359e77f1)、[TACZ ray_gun](https://sketchfab.com/3d-models/tacz-ray-gun-b65b94001da44d86858c9bd9813c0dd9)）。**注意**：标着"基于 TACZ 默认模型改模"的，同样是默认包的衍生品，受 NC-ND 限制，不能拿来再发布 |
| CurseForge（Customization 分类） | 搜 `tacz`：EMX-Arms、TTI 塔兰、The Division、Helldivers 2、Xiaomantou 等枪包 | 绝大多数 **CC BY-NC-ND：自用可以，改完再发布不行**；要改着用，先私信作者要授权 |
| B站 / 贴吧 / QQ 群 | 搜"TACZ 枪包"、"永恒枪械工坊 枪包"；B站有 [TACZ 内容整合导航](https://space.bilibili.com/8376008) | 网盘分享的包，逐包看作者附带的许可/授权声明 |
| GitHub | 搜 `tacz gun pack` / `tacz 枪包`，如 [unknowObject/Menophage](https://github.com/unknowObject/Menophage)（命运2异星噬菌体）、[LesRaisins-Marlin-1895](https://github.com/unknowObject/LesRaisins-Marlin-1895) | 看仓库 LICENSE/README 声明；开源附属包是**研究"写法"和"比例"的最佳样本** |
| mcmodels.net / Planet Minecraft | 搜 `gun blockbench` / `tacz` | 下载看许可 |

拿到 `.bbmodel`（Blockbench 工程）最理想：直接改骨骼、换贴图；只有 `geo.json` 也能用（Blockbench 直接打开编辑）。

### 路线 B：真实侧视图 + 真实尺寸，"描"出完全原创的模型（官方推荐，零 3D 基础）

这是官方 Wiki 推荐的[枪械模型工作流](https://tacwiki.mcma.club/zh/model_guide/model.html)，本质是**描图搭积木**，不需要任何"建模艺术"：

1. **查数据**：维基百科/厂商官网拿目标枪的全长（注意型号差异：G36C 与 G36K 长度不同、托折叠与否长度不同）。
2. **找侧视图**：Google 搜 `{枪名} side view`，或维基百科条目页、Modern Firearms（world.guns.ru）、IMFDB。**裁剪到枪口和枪托紧贴图片边缘、无留白**。
3. **算尺寸**：模型全长 = 真实mm × 48/1000。例：要做全长 700mm 的冲锋枪 → 700×48/1000 = 33.6 grid。
4. **建参考平面**：Blockbench 新建基岩版实体模型 → 建一个方块 → 按图片分辨率设长宽比（例：1332×700 的图 → 平面 60×31.5）→ 把侧视图 PNG 拖到方块上铺满 → 锁定参考方块 → 切换正视图。
5. **描**：用 0.125 倍数尺寸的方块沿图轮廓搭；先大块面分部件（机匣/枪管/枪托/弹匣/握把），每个部件一个骨骼，再细化；`Ctrl+G` 把方块归入骨骼。
6. **对比例**：宽度用"导轨 0.75、握把 1.5–2"做标尺目测；多找几张角度图交叉验证，别只信侧视图。
7. **分骨骼命名 → 导出**：按第 2.2 节表格命名，`File → Export → Bedrock Entity Model` 导出 `geo.json`，同时 `File → Export Texture` 保存贴图。

> 这条路线产出**完全原创**的模型，无任何版权问题，且比例绝对正确——因为公式就是官方标准。**强烈推荐作为主线。** 贴图阶段再用 Blockbench 的"截图模型"功能生成背包 2D 图（[官方教程](https://tacwiki.mcma.club/zh/gunpack/gun/01_simple_gun.html)）。

### 路线 C：下载 CC0/CC-BY 枪模 → 体素化 → 导入 Blockbench 精修（半自动）

Blockbench 的官方 FAQ 说得很清楚：**多边形模型（OBJ/glTF/FBX）不能直接变成方块模型**，必须经过"体素化"（把表面网格变成一格格方块）。社区常用流程：

```
① 下载低模枪械（OBJ/GLTF/FBX，须 CC0/CC-BY 等允许改作）
        ↓
② 体素化（任选其一）：
   - OBJ2MC Addon Studio https://techno-rope.com/（网页版，直接输出 Blockbench 基岩版方块模型！）
   - drububu voxelizer https://drububu.com/miscellaneous/voxelizer/（输出 .vox/.obj）
   - Blender 4.1+：修改器 Remesh → 模式选 Blocks（生成方块状网格）
   - MagicaVoxel：导入 OBJ 体素化后导出 .vox
        ↓
③ Blockbench 打开/导入体素结果（.vox 用插件；OBJ2MC 输出可直接打开）
        ↓
④ 精修：把碎方块合并进骨骼、按第 2.2 节命名、清理面数、重新分 UV/贴图
```

**CC0/CC-BY 枪模素材源**（都允许改作+再发布，只需署名）：

| 来源 | 许可 | 内容 |
|---|---|---|
| [poly.pizza](https://poly.pizza/) | CC0 | 搜 `gun`、`rifle`、`pistol`；[Quaternius 的 AK47](https://poly.pizza/m/em1Hi9GuCv)、霰弹枪、科幻突击步枪等，低模 |
| [Quaternius](https://quaternius.com/) | CC0 | Ultimate Weapons 系列（科幻为主） |
| [OpenGameArt](https://opengameart.org/) | CC0/CC-BY/CC-BY-SA | 搜 `weapon`、`gun`，注意逐个看许可 |
| [Sketchfab](https://sketchfab.com/search?q=gun&type=models) | 筛 **Downloadable** + CC BY/CC0 | 大量低模枪械 |
| Kenney | CC0 | [Blaster Kit](https://kenney.nl/assets/blaster-kit) 科幻枪 |

**开源 FPS 游戏资源**（合法、免费，但注意署名+相同方式共享）：

| 游戏 | 资源许可 | 备注 |
|---|---|---|
| [AssaultCube](https://assault.cubers.net/docs/license.html) | CC BY-NC-SA 3.0 | 非商业+同协议+署名；个人自用无压力 |
| Xonotic / Unvanquished / Warsow | 内容多为 CC BY-SA 3.0 | 发布你的包时须署名并同协议共享 |
| OpenArena | GPLv2 | 内容 GPL，发布需 GPL 兼容协议 |
| Red Eclipse / Cube 2 | 内容 CC BY-SA | 同上 |

> ⚠️ **红线**：不要下载 COD / 战地 / CS / 命运 2 等商业游戏的拆包模型（Sketchfab 上一堆挂着游戏名的模型，大多不可下载或仅"展示"）——那是社区最常见的侵权坑。

### 路线 D：AI 生成 3D 模型（适合原创/科幻枪）

- 文本/图片 → 3D 网格：**Meshy、Tripo、Luma Genie、腾讯混元 3D** 等，输出 OBJ/GLTF → 走路线 C 的体素化流程。
- 或者用文生图 AI 生成一张"侧视蓝图参考图"→ 走路线 B 描图。
- 现状：AI 生成**真实枪械**的轮廓精度有限，但做**原创科幻枪**完全可行（先 AI 出造型，再 Blockbench 还原成方块）。

### 路线 E：委托定制 / 找人合作（不想动手）

- 渠道：B站 TACZ 相关 UP、贴吧"永恒枪械工坊"吧、MC百科讨论区、各大 MC 交流 QQ 群。
- 话术示例：*"想做一个 XX 枪包，不会建模，求接单/合作。参考图：xxx，风格参照 TACZ 默认包，可谈授权分成。"*
- 谈合作时**书面确认授权**（能否修改、能否再分发、署名要求），避免日后纠纷。

---

## 5. 推荐落地流程（从零到能玩的枪包）

```
1. 装 Blockbench（免费，网页版/桌面版皆可）
2. 定命名空间：比如 "mypack"（目录名/枪包文件夹名决定）
3. 选路线：A 现成模型 / B 侧视图描图（推荐起步） / C 体素化
4. 建模规范：0.125 倍数、ZY 对称、枪口朝 -Z、按 2.2 节骨骼表命名
5. 导出 geo.json + UV 贴图；用"截图模型"生成 32×32 背包图
6. 照默认枪包搭"写法"（第 2.1 节结构）：
   - data/{ns}/index/guns/xx.json   （注册）
   - data/{ns}/data/guns/xx_data.json （数值，抄同类枪改）
   - assets/{ns}/display/guns/xx_display.json （模型/贴图/动画/音效引用，
     先写 "use_default_animation": "rifle" 或 "pistol" 跑通）
   - assets/{ns}/lang/zh_cn.json + en_us.json（显示名）
7. 打包 zip（zip 内根目录是 gunpack.meta.json）→ 放入 .minecraft/tacz/
8. 游戏内 /tacz reload（不行就重进存档）
9. 跑通后再逐步：自定义动画（拷贝默认动画改关键帧）→ 状态机 lua → LOD 低模 → 音效
```

调试小技巧：`tacz-pre.toml` 里把 `DefaultPackDebug` 设为 `true` 可防止默认包被覆盖（默认包 README 原文）；自己改坏的包与默认包对照，99% 的问题是组名拼错或路径写错。

---

## 5.5 成品示例：COP 357 四管德林格手枪（已生成）

为了验证这套方法，我用脚本把 **COP 357**（真实全长 142mm / 高 103mm / 宽 27mm / 枪管 82.5mm）完整换算并生成成了可用的枪包：

- **位置**：`docs/gunpack_tools/cop357_gunpack.zip`（直接丢进 `.minecraft/tacz/` 即可）
- **生成器**：`docs/gunpack_tools/gen_cop357.py`（改参数 → 重跑 → 自动重新打包）
- **使用说明**：`docs/gunpack_tools/COP357_README.md`

换算结果（官方公式 48grid = 1000mm）：

| 项目 | 真实 | 换算目标 | 模型实测 | 误差 |
|---|---|---|---|---|
| 全长 | 142 mm | 6.82 grid | 7.20 grid (150mm) | +5.6% |
| 高 | 103 mm | 4.94 grid | 4.63 grid (96mm) | -6.6% |
| 宽 | 27 mm | 1.30 grid | 1.25 grid (26mm) | -3.7% |

模型由 14 个方块 + 27 个骨骼组成（四管 2×2、铰链后膛块、外露击锤、后倾木握把、门字护圈），定位组（`idle_view`/`iron_view`/`ground`/`thirdperson_hand`/`fixed`/`refit_*`/`muzzle_flash`）齐全，直接套默认枪包的 `pistol` 默认动画；数值以默认枪包的 `.357 左轮`（rhino357）为基准：4 发弹容、`manual` 手动装填、10.5 伤害、无配件槽。贴图是程序生成的金属/木纹 128×128 UV + 32×32 背包图，全部可重绘。

**这个成品就是"换算工作"的模板**：以后想要任何枪，改 `gen_cop357.py` 里的 `CUBES` 坐标和 `BONES` 定位组、换一套真实尺寸，就能再生成一把。

## 6. 许可证检查清单（发布前逐项过）

对包里的**每一件素材**（模型/贴图/音效/动画/别人的代码片段）建一个清单：

| 素材 | 来源 | 作者 | 许可证 | 可改作？ | 可再分发？ | 需署名？ |
|---|---|---|---|---|---|---|
| xx 步枪模型 | poly.pizza | Quaternius | CC0 | ✅ | ✅ | 建议署名 |
| xx 音效 | AssaultCube | AC 团队 | CC BY-NC-SA | ✅ | ✅(同协议) | ✅ |
| ... | | | | | | |

快速判定：
- **CC0**：随便用。**CC BY**：署名即可。**CC BY-SA**：署名 + 你的包也按同协议发布。**CC BY-NC / NC-SA**：只能非商业。**CC BY-NC-ND**：不能改、不能发（默认枪包和多数社区枪包都是这个）。**GPL**：内容随协议传染，谨慎。**游戏拆包**：一律不用。
- 你自己的包建议直接声明一个宽松许可（如 CC BY 或 CC0），并附上素材来源表——这也是社区里口碑好的枪包的标准做法。

---

## 7. 链接汇总

**官方文档**
- 官方 Wiki（新）：https://tacwiki.mcma.club/ （枪包指南 https://tacwiki.mcma.club/gunpack/ ）
- 模型比例与工作流：https://tacwiki.mcma.club/zh/model_guide/model.html
- 特殊功能组名：https://tacwiki.mcma.club/zh/gunpack/gun/03_sp_group_name.html
- 配件定位组：https://tacwiki.mcma.club/zh/gunpack/legacy/gun_refit/
- 动画规范：https://tacwiki.mcma.club/gunpack/animation/
- 官方 GitHub：https://github.com/MCModderAnchor/TACZ （1.20.1 分支含默认枪包源码）

**工具**
- Blockbench：https://www.blockbench.net/ （Cameras 插件在 插件市场 里搜）
- OBJ2MC（OBJ→基岩版方块模型）：https://techno-rope.com/
- drububu voxelizer：https://drububu.com/miscellaneous/voxelizer/
- Blender（Remesh→Blocks 体素化）：https://www.blender.org/

**素材源**
- poly.pizza（CC0，含 Quaternius AK47 等）：https://poly.pizza/
- OpenGameArt：https://opengameart.org/
- Sketchfab（筛选 Downloadable + CC）：https://sketchfab.com/
- AssaultCube 许可说明：https://assault.cubers.net/docs/license.html

**参考枪包（学习"写法"与比例的合法样本）**
- 本仓库内置默认枪包：`src/main/resources/assets/tacz/custom/tacz_default_gun/`（Blockbench 直接打开里面的 `*_geo.json` 研究）
- GitHub 开源附属包：https://github.com/unknowObject/Menophage 、https://github.com/unknowObject/LesRaisins-Marlin-1895

---

## 附录：实测脚本说明

`docs/gunpack_tools/measure_proportions.py` 是本报告的配套工具：递归套用骨骼变换求每把枪的包围盒，输出 grid 长度/等效 mm/枪口 z 值。

```bash
# 默认测本仓库默认枪包
python3 docs/gunpack_tools/measure_proportions.py
# 测其他枪包（解压后指定 geo 目录）
python3 docs/gunpack_tools/measure_proportions.py /path/to/pack/assets/xxx/geo_models/gun
```

你拿到任何第三方枪包，都可以先解压跑一遍脚本——**用数据"量"别人的比例**，比自己肉眼看快得多，这也是完全合规的借鉴方式。
