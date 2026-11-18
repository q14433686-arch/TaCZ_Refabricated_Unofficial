# 1.21.1 Fabric 1.2.0-hotfix → 26.3 Fabric：全树对照与验收台账

**按游戏里的变化查：[玩家可感变化清单](FABRIC_120_PLAYER_CHECKLIST.md)**。直达：[霰弹枪的后坐力推动射手](FABRIC_120_PLAYER_CHECKLIST.md#射手位移) · [第一/第三人称枪口火光和烟雾](FABRIC_120_PLAYER_CHECKLIST.md#枪口火光和烟雾) · [AI 寻枪声](FABRIC_120_PLAYER_CHECKLIST.md#枪声与生物仇恨) · [其他玩法和界面变化](FABRIC_120_PLAYER_CHECKLIST.md#其他可感差异)。每项区分默认触发条件、已有实现、实际测到什么和剩余验收。

本页按工程子系统跟踪**剩余工作**。[逐路径 TSV](FABRIC_120_ALL_PATHS.tsv) 定位代码或资源，运行记录见[按提交排列的账本](FABRIC_120_PORT_26_3.md)。**路径已覆盖不等于功能已完成**。本页不是发行说明。

## 比了哪些文件？

固定 [上游旧树 `d290355`](https://github.com/Sh1roCu/TACZ-Refabricated/commit/d2903554da039d2355920953a81447784a3f2be2)（4,645 个文件）、[上游目标 `356bdf9`](https://github.com/Sh1roCu/TACZ-Refabricated/commit/356bdf9d48f2225bac6ceb56adc5576a418517a8)（6,037）、本线起点 `cc990c3`（4,960）、开工盘点 `5641868`（6,101；运行时代码同 `fa23a51`）。TSV 是这**四棵提交树的并集 7,104 条**：包括上游未改项、历史删除、26.3 独有实现、开发文件。列出每边 blob、上游变化/改名前路径、overlay 实际目的地和 blob。它是有版本的起点快照，不含自己的生成物；本次新增/替换的功能和用例在下表及末节滚动记录。

| 旧上游 → 新上游（1,402 A / 1,048 M / 198 R / 10 D） | 本地同目标 blob（含已删） | 同旧 blob | 无同名新路径 | 其它 blob | 合计 |
|---|---:|---:|---:|---:|---:|
| Java | 27 | 15 | 144 | 313 | 499 |
| 内置包物理文件 | 1,516 | 0 | 454 | 24 | 1,994 |
| 包外资源/元数据 | 89 | 0 | 18 | 49 | 156 |
| 构建/工具/文档 | 1 | 0 | 3 | 5 | 9 |
| **合计** | **1,633** | **15** | **619** | **391** | **2,658** |

**换个问题：游戏实际拿到什么？** 目标包 4,405 个物理文件减去被同包 `compat_1_21_1` 覆盖的 229 个 = 4,176 个有效目的地。本端 3,929 份等于胜出的源 blob；其余 247 份是 202 配方、23 进度、1 loot、1 metadata、1 readme、2 ADS、5 装填 Lua、12 条未分配动画轨道适配。**454 不是漏了 454 个资源。** [来源、规则与输出哈希](../../DEFAULT_PACK_120.tsv)；[包外导入](../../RESOURCE_IMPORT_120.tsv)；[原包自身的 246 条引用例外](DEFAULT_PACK_120_SOURCE_GAPS.tsv)。包外同名不同内容须看实现；例如旧 GL 的 scope 类不能原样移进 RenderPearl，反之新增路径也可能只是测试。

复核（在仓库根运行；只取 Git 对象，不检出/切换分支）：

```sh
git init --bare /tmp/tacz-reference.git
git -C /tmp/tacz-reference.git fetch --no-tags --depth=1 https://github.com/Sh1roCu/TACZ-Refabricated.git \
  d2903554da039d2355920953a81447784a3f2be2 356bdf9d48f2225bac6ceb56adc5576a418517a8
python3 scripts/compare_port_trees.py /tmp/tacz-reference.git --local 5641868 >/tmp/all-paths.tsv
cmp /tmp/all-paths.tsv docs/investigations/FABRIC_120_ALL_PATHS.tsv
python3 scripts/audit_upstream_120.py /tmp/tacz-reference.git --local 5641868 --paths >/tmp/deltas.tsv
python3 scripts/port_default_pack_120.py /tmp/tacz-reference.git  # 只读，核实当前工作树
python3 scripts/verify_120_resources.py
```

## 功能对照（不要把构建、游戏内断言、像素检查混成一栏）

下表「已跑」取自[默认包基线 CI](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44229658444) 及[历史分块运行](FABRIC_120_PORT_26_3.md)。客户端为真实 MC + Mesa 软件 Vulkan，不是独显。没有逐张看过的图不作画质判定。

| 上游能力 → 本线实现/差别 | 已跑到哪一步 | 剩余验收/边界 |
|---|---|---|
| 目录/ZIP、多版本 overlay、发现/顺序 → `GunPackOverlayResolver`、26.3 本地 pack loader | 目录与 ZIP 单测；两端读到实际 26.3 overlay | 外部不合规包不是一律兼容 |
| 压缩/分片、同步后重建索引 → `GunPackSync*`、`CommonNetworkCache` | 分片/协商单测；新枪、组件、配方、转接座实际同步；退出再进同一存档 | 独立进程、不同客户端能力、切服；不向缺少字段的旧端伪造支持 |
| 内置包资源、语言、进度、配方 → 来源账本 + 原生 26.3 schema | 229 indexes、70 个第一人称模型/状态机、202 配方、23 进度；完整性检查 | 不是 70 把枪全动作录像；原包 246 条例外单列 |
| 脚本、OEM/variant/slot adapter、refit → 虚拟件与真实库存分离 | `GunActionSmoke` / `ScriptGuardSmoke` / QR-24 实际改装请求；坏 Lua 隔离 | 不下发服务端 Lua；不代表每个外包 hook |
| 装填/主动退出瞄准/卸弹/动作后事件 → 原生弹药源、库存与客户端定时 | 六组实际脚本 × 六种供弹路径；旁观追踪实体与取消/结算、时钟暂停/重进 | 追踪牛实体的一个客户端不是两个独立玩家；不证明换弹动画每帧 |
| [射手被后坐力推动](FABRIC_120_PLAYER_CHECKLIST.md#射手位移) → 服务端速度与射手同步包 | `AttachmentDataTest` 测姿态/弹丸数计算；未测玩家位置 | 短双管单发拆/装超压反冲弹，地面/空中客户端与服务端位置对照；不是镜头后坐或目标击退 |
| [枪声吸引敌对、激怒中立生物](FABRIC_120_PLAYER_CHECKLIST.md#枪声与生物仇恨) → `GunSoundAngerEvent` 在成功击发后处理 | `GunSoundAngerTest` 只测半径，不测活体 | 生存玩家、两类生物、消音/距离/已有目标负对照；不是走到声源坐标 |
| 配件属性、声音对象、ADS 预设/双 FOV | 声音与预设单测；集成客户端实际瞄准 | 未听辨所有采样；不等于所有相机曲线目测 |
| 弹药库、升级、展示柜/板 → Fabric Transfer、组件/BE、collector | 事务回滚、升级带库存、染色/掉落/搬移顺序；重开后读回、客户端转移具名弹药 | Carry On 已装时的操作、密集展示性能与硬件矩阵 |
| 创造栏、包筛选与开放搜索 → vanilla/Fabric 分页 + `CreativePackFilters` | 真实点击/输入；原屏幕刷新新枪，检查 DMR 和 All | 非全部语言与分辨率 |
| JEI/REI 查询与工作台跳转 → 独立适配 + 共享正反向关系 | `44f0733` 两端重载前后注册表、截图前后页面和对照像素均通过（旧 JEI 截图不成立，见下） | 独立服务器无查看器的联机组合、实际工作台 transfer 点击 |
| 单数目录/物品组件/DFU/条件 → `UniversalPackConverter` | 真实旧夹具不改源包 → 导出 ZIP → 游戏 /reload 同步结果；拒绝不支持项 | 独立作者/布局的有权使用的包；不静默丢数据 |
| 外显/LOD/第三人称、mesh → `GunRigBaker` / `DisplayBake` / TML / 原生 collector；不加载旧 SBM | 三套独立 rig 与材质、真实 FIXED 提交/重载释放；PAL/YSM 有独立接入 | 带 mod、远近/多实体/不同视角的像素矩阵；非旧 VBO 性能承诺 |
| 命中表面、弹壳、FP tracer → 原粒子协议/客户端工厂 | 实际 display 控制弹孔开关，弹壳 override 加载、六面朝向检查 | 发射轨迹、声音及光影不由 provider 返回值证明 |
| [新版枪口火光与烟雾](FABRIC_120_PLAYER_CHECKLIST.md#枪口火光和烟雾) → **`renew` 运行时未移植** | 68 个 `renew` display 中 67 个有旧贴图可回退，Glock 17 无旧贴图；2 个定义文件不是出帧证据 | 分开验收第一人称枪焰/脱离的烟、F5/旁人视角、枪口配件与重载；默认动画 0 个 `particle_effects`，动画触发另需夹具 |
| `text_show`、落点 → `RangefinderReadout` + snapshot 字形 | 真 UH-1 显示两个方块距离；`64ae477` 的 XM157 原图在准星中心恢复一个 4×4 的空心黄框（12 像素），解除瞄准、墙后不提交 | 非默认色和比例目前是单测；其它 rig、PIP/Iris/独显仍需实测 |
| ocular / `scope_lens` / vision → 按实际目镜选择 display；与原生 PIP 共用一次合成 | [XL50 配置、倍率和热源对照](#原生镜片与热成像) | 混合目镜、透明/自定义实体、其它色盘与独显；Iris 光影下保留原路径 |
| MAE、依赖和开发工具 → 1 份 nested MAE；26.3 无混淆，非上游旧 jar 环境 | CI 验 jar 内容和无额外可选模组的游戏加载 | EMI、Sable、Simulated、AR 等无经验证等价集成；不靠空方法/换包名打勾 |
| 26.3 独有的 LR、AmmoSource、TML、PAL/YSM、Iris/Voxy 等 | 不因上游树缺失而删除。见 [LR 和 mesh 的署名](../../LICENSES.md)、[本线 YSM](../YSM_COMPAT.md) | 分别保留自己的回归边界，不能从 1.21.1 推断 26.3 可用性 |

## 本轮分块记录

1. **全树基线已复核**：按上面的两种口径分别生成；`--paths` 行数与 TSV 中非空 delta 一致。默认包生成器只读核对及离线引用检查通过。后续块在此登记实现、实跑 SHA、失败/未测条件，不勾选未完成的大项。
2. **查看器双向查询已过；开页与像素分别验收**：基线仍不装查看器；独立 `recipe viewer runtimes` 工作流按 `jei` / `rei` 分别加载固定完整客户端 jar。在新世界先核对 AK 的正反向条目且不存在探针，执行真实旧包转换与重载，再从各自的 runtime registry 找探针并打开它的 gun→ammo 页，保存 `tacz_viewer_*.png`。测试 mod 才注册 JEI 生命周期观察器；不把依赖嵌入发布 JAR。API 对照：[JEI 31.8 源码](https://github.com/mezz/JustEnoughItems/tree/d438dc15f13b102ae7e7e36832543514380bd718)、[REI 26.3 源码](https://github.com/shedaniel/RoughlyEnoughItems/tree/b3bc5013d33f12bbea5737bba5fb76652e2b07d8)。此门禁不代替两个独立客户端或工作台上的 transfer 点击。

   `86c8cb6` [两条运行时作业均成功](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44231476610)：[JEI 31.8.0.48 日志/PNG](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44231476610/artifacts/14394469160)、[REI 26.3.823 日志/PNG](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44231476610/artifacts/14394899280)。通过条件包含正确 screen 和非空帧，不只是进程退出 0。首次下载在存储端点 EOF。后来从同一次成功运行取回两张 **854×480 原图**：[REI](../../build-reports/visual/44231476610-tacz_viewer_rei.png) 显示 `Gun Ammunition`、枪械槽和弹药提示；[JEI](../../build-reports/visual/44231476610-tacz_viewer_jei.png) **只有世界和 HUD**。先前的 screen 断言和非空文件不足以证明截图时页面仍在。新门禁从实际物品栏开页，指针移出面板，截图前后都检查 screen，并与同场景世界对照帧做中心面板像素比较。

   `44f0733` 的[强化门禁](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44234305280)已过：17,160 个中心区采样点中，[JEI 31.8.0.48](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44234305280/artifacts/14396448654) 有 16,427 个、[REI 26.3.823](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44234305280/artifacts/14395884949) 有 15,643 个与世界负对照不同且符合面板色。逐张复核本次 [JEI 原帧](../../build-reports/visual/44234305280-tacz_viewer_jei.png) 和 [REI 原帧](../../build-reports/visual/44234305280-tacz_viewer_rei.png)，均可见 `Gun Ammunition`、枪和弹药槽位，非只有 HUD 的世界帧。这是菜单显示证据，不是所有 UI 主题或工作台 transfer 点击的证明。
3. **XM157 实体标记通过 26.3 原版渲染器 + Mesa 客户端门禁**。文字和标记按配置共享限频的 client-world 命中样本，只在第一人称 ADS、有效实体在首个方块之前时提交。干燥环境的游戏 tick 预览读取枪包数据；不执行服务端 Lua，不预测散布/入水/脚本。定位读取实际手部投影及动画父链（默认 XM157 的 locator 在被主 pass 隐藏的 `division` 下）。`ImpactMarkerSmoke` 仅在负对照时停交标记节点，取得正常/隐藏标记/恢复三帧，比较中心区的黄色像素，再用石墙遮挡并断言不画；单测覆盖无穷/零速度和过期。不以 UH-1 文字测试充当标记测试。

   `e01537c` [生产源码编译成功](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44232108232)，[完整构建](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44232108241) 在 Gradle 依赖解析遇 Maven Central HTTP 429，没进测试，不是像素验收结果。
   在 `77073ca` 的[完整客户端运行](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44232349588)中，专服及 UH-1 通过，XM157 已装备且 ADS 达标，但等不到 `drawBox`，**未过**。测试增加确定的空中视线以及区分测距/定位/投影的失败诊断，不放宽像素断言。
   `3bfe86c` 的[诊断运行](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44232836021)读到 `026 M`、有效样本及已初始化相机，但 locator 的 `clip.w=-7.59`。CPU 投影漏乘与目镜掩码共用的提交时 ModelView，误判在相机后面；只在求投影时补这一因子，绘制不重复相乘。新测试换用斜向且超过一个游戏 tick 的样本，并增加解除 ADS / 重新瞄准检查。

   `64ae477` [compile-check](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44233384533) 与[完整构建、专服及客户端](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44233384724/job/134563583101)通过。判据：实体样本非零偏移、四边形确实提交；对照帧关闭该节点再恢复，原门禁报告 **14 个**恢复的黄像素。逐张复核[实体](../../build-reports/visual/44233384724-tacz_xm157_entity.png)、[对照](../../build-reports/visual/44233384724-tacz_xm157_control.png)、[恢复](../../build-reports/visual/44233384724-tacz_xm157_restored.png)、[遮挡](../../build-reports/visual/44233384724-tacz_xm157_occluded.png)：其中 **12 个**是 `(425,240)–(428,243)` 的空心框，另 2 个来自右上方测距文字，不算标记。收紧后的门禁只取中心小窗口，并检查空心形状及对照/遮挡像素。放下瞄具 8 tick 零次提交，重新瞄准恢复，再隔石墙 8 tick 零次提交。[原始日志/截图](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44233384724/artifacts/14397021032) · [测试 JAR](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44233384724/artifacts/14397016170)。只证明这一游戏内配置，不代表所有配件、第三方包或图形后端。

   `02ac73e` 的[严格像素门禁与全量 build](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44234829581/job/134568328848)及[独立编译](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44234829578)通过：`PORT_OPTIC: XM157 showed 12 restored yellow pixels on entity, zero draws behind wall`。这次中心小窗口内两张负对照均无黄像素，正对照和恢复帧均保留空心。可取[该次日志/PNG](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44234829581/artifacts/14397295863)或[CI 测试 JAR](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44234829581/artifacts/14397465462)。

### 原生镜片与热成像

目标树的 [ScopeLensRenderer / ScopeLensContour / ScopeThermalRenderer](https://github.com/Sh1roCu/TACZ-Refabricated/tree/356bdf9d48f2225bac6ceb56adc5576a418517a8/src/main/java/com/tacz/guns/client/renderer/scope) 用旧 GL 采集镜片轮廓和已绘制的热源。本端没有引入 GL framebuffer 调用。`scope_lens` 解析普通光学、两种夜视、三种热色盘，限定输入范围；缺省/未知模式与关闭热采集时回到光学。

- **谁拥有镜片**：从实际提交了有效目镜几何的 display 取配置（含内置/替换件），不用放大倍率猜测镜片种类。用上一帧决定要不要拷贝世界，合成前须与**本帧**目镜的配置一致。仅有红点组、或多个不同配置的目镜共用一张掩码时，不套用单一滤镜。边缘距离从实际掩码有界取样，**不是**上游的完整 jump-flood 距离场。
- **热像是什么**：世界提取时读实体类型标签、存活/可见性；只取世界提交的普通 solid 模型、物品和方块节点，不重调实体渲染器，不取名牌、轮廓或碰撞箱。用世界深度的私有副本测试其材质，透明清屏后留下的覆盖才是热源。无可用深度退回光学；截至此块不含半透明/自定义几何的完整支持。
- **倍率归属**：普通 FOV 变焦和窄 FOV 二次渲染以 1× 采样，重投影 PIP 只在同一次片元合成里取一次镜内倍率；镜外 discard。启用 Iris 光影时不运行此 sensor 路径，不宣称光影兼容。
- **验收**：[ScopeLensSmoke](../../src/gametest/java/com/tacz/guns/test/ScopeLensSmoke.java) 经典 FOV 与重投影 PIP 各拍原色、光学、白热、石墙、移墙恢复、热标签重载六帧。另拍一张同样开镜但**只**关掉重投影合成的 1× 帧。用红条宽度测量倍率，用蓝底检查镜内偏色和镜外保色；白热帧与恢复帧共有、且光学帧原本不亮的像素，须在两张负对照里分别变冷。测试在游戏运行中把牛同时加入白名单和黑名单，验证黑名单优先。初次 [QR-24 夹具超时](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44240278132)：该枪的默认允许表不含 XL50，**没有**跑到镜片验收。后来改用包允许的 M700，并在服务端检查装备结果，不绕开允许表。

[第一次 M700 运行](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44240794013/job/134587917796)越过镜片断言，但退回标题时旧收枪期限仍有效，整套未过。镜片纹理和动作定时现在跟现有 `LOGGING_OUT` 一起在客户端卸载世界时收尾，而不是从可能位于 Netty 线程的回调销毁 GPU 对象；网络快照仍保留单人连接的清理规则。另一次[像素检查](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44242392074/job/134593145145)只有 13,322 / 17,160 个镜外色样稳定，**不**作为镜外一致的证据。夹具改用平整的原版混凝土色板，等实际世界投影到位才比较，保留镜外像素阈值。其后一次[经典变焦对照](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44243966018/job/134598284133)虽有偏色，但红条重叠不足；将条纹与牛错开放置，并在取帧期间关闭视角摇晃，避免把动画位移当成滤镜效果。

**通过的运行**：[`e92a998` 完整构建、专服、客户端](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44244608846/job/134600441865) · [独立编译](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44244608818) · [全部 13 帧和日志](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44244608846/artifacts/14401181401)。同一视角，[1× 对照](../../build-reports/visual/44244608846-tacz_lens_pip_1x.png)的红条宽 **7 px**；下表是在 XL50 默认 6× 档测得的值。条宽为中央取样区各行红像素数的上四分位；重投影对低分辨率源图重建，不能把阈值化条宽当成精确倍率校准。

| 无光影路径 | 原色 → 开光学镜片：红条宽 | 移墙后重合白热像素 | 原始帧（同一运行，未裁剪） |
|---|---:|---:|---|
| 经典世界 FOV | 45 → 45 px | 2,559 | [原色](../../build-reports/visual/44244608846-tacz_lens_classic_bare.png) · [光学](../../build-reports/visual/44244608846-tacz_lens_classic_optical.png) · [白热](../../build-reports/visual/44244608846-tacz_lens_classic_hot.png) · [石墙](../../build-reports/visual/44244608846-tacz_lens_classic_wall.png) · [恢复](../../build-reports/visual/44244608846-tacz_lens_classic_restored.png) |
| 重投影 PIP | 38 → 38 px | 2,499 | [原色](../../build-reports/visual/44244608846-tacz_lens_pip_bare.png) · [光学](../../build-reports/visual/44244608846-tacz_lens_pip_optical.png) · [白热](../../build-reports/visual/44244608846-tacz_lens_pip_hot.png) · [石墙](../../build-reports/visual/44244608846-tacz_lens_pip_wall.png) · [恢复](../../build-reports/visual/44244608846-tacz_lens_pip_restored.png) · [双标签否决](../../build-reports/visual/44244608846-tacz_lens_pip_tagged.png) |

[回存的原始帧](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44245218048)为 854×480。按测试窗口重读 RGB：经典 / PIP 的镜内彩色变暗像素分别为 **11,267 / 11,851**；原色→光学、光学→白热这两组镜外对照，各自 **17,160 / 17,160** 个色样都在 RGB 误差门槛内（经典原色→光学有 8 个不是字节完全相等）。复核墙帧时，两条路径的恢复点中均有 **0** 个仍呈中性白热；经典有 10 个位置落在红色分划上，不计作冷黑。PIP 双标签帧只剩 2 个这样的红色分划像素，没有中性白热。两个模式完整的墙及标签判据都是独立要求 ≥98% 恢复点变冷，不能用消失的色板、准星或单一灰阶滤镜代替热源判据。

**尚未验证**：窄 FOV 二次渲染、切换镜组/替换件时的实际像素、白名单单独启用非生物实体、其它色盘、独显及 Iris 光影包。普通 solid 牛的白热结果不代表任意材质，也不是整套上游效果逐像素复现。

### 仍待移植

- **玩法规则**：空手趴下与趴姿视角配置、可选空中腰射散布、同队穿透/实体免疫列表、可破坏方块标签与硬度阈值，不能从旧版持枪趴下、普通玻璃可碎等现象推断已经移植。见[默认条件和对照方法](FABRIC_120_PLAYER_CHECKLIST.md#移动与射击反馈)。
- **玩家界面**：枪模弹数与背包直读 HUD 同源、第三人称背挂显示开关、工作台手持筛选零结果时的提示和旧包登录指引仍有缺口。查[玩家清单](FABRIC_120_PLAYER_CHECKLIST.md)，不要把「对应类/翻译已经存在」当成验收。
- **`renew` 不等于旧四边形火光**。上游 [`MuzzleFlash`](https://github.com/Sh1roCu/TACZ-Refabricated/blob/356bdf9d48f2225bac6ceb56adc5576a418517a8/src/main/java/com/tacz/guns/client/resource/pojo/display/gun/MuzzleFlash.java) 分第一/三人称粒子定义；本端 `GunDisplayInstance.checkMuzzleFlash` 仍会丢弃无旧贴图的定义。验收要能单独观察两个视角、绑定/脱离和资源重载；默认包没有动画粒子关键帧，这项需要自制或有权使用的夹具。
