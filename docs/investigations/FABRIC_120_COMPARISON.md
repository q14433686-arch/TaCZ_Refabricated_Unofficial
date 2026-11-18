# 1.21.1 Fabric 1.2.0-hotfix → 26.3 Fabric：全树对照与验收台账

这里跟踪**剩余工作**。先看本页判断功能，再查 [逐路径 TSV](FABRIC_120_ALL_PATHS.tsv) 定位代码或资源，运行记录见[按提交排列的账本](FABRIC_120_PORT_26_3.md)。**路径已覆盖不等于功能已完成**。本页不是发行说明。

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

下表「已跑」取自 [最近的基线整套 CI](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44229658444) 及[历史分块运行](FABRIC_120_PORT_26_3.md)。客户端为真实 MC + Mesa 软件 Vulkan，不是独显。没有逐张看过的图不作画质判定。

| 上游能力 → 本线实现/差别 | 已跑到哪一步 | 剩余验收/边界 |
|---|---|---|
| 目录/ZIP、多版本 overlay、发现/顺序 → `GunPackOverlayResolver`、26.3 本地 pack loader | 目录与 ZIP 单测；两端读到实际 26.3 overlay | 外部不合规包不是一律兼容 |
| 压缩/分片、同步后重建索引 → `GunPackSync*`、`CommonNetworkCache` | 分片/协商单测；新枪、组件、配方、转接座实际同步；退出再进同一存档 | 独立进程、不同客户端能力、切服；不向缺少字段的旧端伪造支持 |
| 内置包资源、语言、进度、配方 → 来源账本 + 原生 26.3 schema | 229 indexes、70 个第一人称模型/状态机、202 配方、23 进度；完整性检查 | 不是 70 把枪全动作录像；原包 246 条例外单列 |
| 脚本、OEM/variant/slot adapter、refit → 虚拟件与真实库存分离 | `GunActionSmoke` / `ScriptGuardSmoke` / QR-24 实际改装请求；坏 Lua 隔离 | 不下发服务端 Lua；不代表每个外包 hook |
| 装填/主动退出瞄准/卸弹/动作后事件 → 原生弹药源、库存与客户端定时 | 六组实际脚本 × 六种供弹路径；旁观追踪实体与取消/结算、时钟暂停/重进 | 追踪牛实体的一个客户端不是两个独立玩家；不证明换弹动画每帧 |
| modifier、AI 对声音反应、声音对象、ADS 预设/双 FOV | 声音/预设/时钟单测和专服动作；集成客户端实际瞄准 | 未听辨所有采样；不等于所有相机曲线目测 |
| 弹药库、升级、展示柜/板 → Fabric Transfer、组件/BE、collector | 事务回滚、升级带库存、染色/掉落/搬移顺序；重开后读回、客户端转移具名弹药 | Carry On 已装时的操作、密集展示性能与硬件矩阵 |
| 创造栏、包筛选与开放搜索 → vanilla/Fabric 分页 + `CreativePackFilters` | 真实点击/输入；原屏幕刷新新枪，检查 DMR 和 All | 非全部语言与分辨率 |
| JEI/REI 查询与工作台跳转 → 独立适配 + 共享正反向关系 | `86c8cb6` 两个独立 CI 作业均实际加载查看器、查询重载前后并打开新页面（见下） | 独立服务器无查看器的联机组合、实际工作台 transfer 点击 |
| 单数目录/物品组件/DFU/条件 → `UniversalPackConverter` | 真实旧夹具不改源包 → 导出 ZIP → 游戏 /reload 同步结果；拒绝不支持项 | 独立作者/布局的有权使用的包；不静默丢数据 |
| 外显/LOD/第三人称、mesh → `GunRigBaker` / `DisplayBake` / TML / 原生 collector；不加载旧 SBM | 三套独立 rig 与材质、真实 FIXED 提交/重载释放；PAL/YSM 有独立接入 | 带 mod、远近/多实体/不同视角的像素矩阵；非旧 VBO 性能承诺 |
| 命中表面、弹壳、FP tracer → 原粒子协议/客户端工厂 | 实际 display 控制弹孔开关，弹壳 override 加载、六面朝向检查 | 发射轨迹、声音及光影不由 provider 返回值证明 |
| `renew` 烟雾/动画粒子通道 → 基线**只有**旧 flash 回退 | 68 个 display、2 定义文件、默认动画 0 个 `particle_effects` keyframe | 绑定/脱离、第一/三人称、按定义取样与重载、第三方关键帧运行时 |
| `text_show`、落点 → `RangefinderReadout` + snapshot 字形 | 真 UH-1 对两个方块距离有文字变化；没有证据表明 XM157 标记可用 | 命中实体、遮挡、解除瞄准、配置色和尺寸的实际绘制 |
| ocular / `scope_lens` / vision → 本线 mask/PIP/Iris final owner | 节点 bake、已有掩码路径；10 个镜片 display 不等于 10 个渲染通过 | 镜片轮廓/材质参数、镜内夜视/冷热实体、PIP 开关、Iris 开关的画面 |
| MAE、依赖和开发工具 → 1 份 nested MAE；26.3 无混淆，非上游旧 jar 环境 | CI 验 jar 内容和无额外可选模组的游戏加载 | EMI、Sable、Simulated、AR 等无经验证等价集成；不靠空方法/换包名打勾 |
| 26.3 独有的 LR、AmmoSource、TML、PAL/YSM、Iris/Voxy 等 | 不因上游树缺失而删除。见 [LR 和 mesh 的署名](../../LICENSES.md)、[本线 YSM](../YSM_COMPAT.md) | 分别保留自己的回归边界，不能从 1.21.1 推断 26.3 可用性 |

## 本轮分块记录

1. **全树基线已复核**：按上面的两种口径分别生成；`--paths` 行数与 TSV 中非空 delta 一致。默认包生成器只读核对及离线引用检查通过。后续块在此登记实现、实跑 SHA、失败/未测条件，不勾选未完成的大项。
2. **查看器双向查询及开页已过游戏门禁**：基线仍不装查看器；独立 `recipe viewer runtimes` 工作流按 `jei` / `rei` 分别加载固定完整客户端 jar。在新世界先核对 AK 的正反向条目且不存在探针，执行真实旧包转换与重载，再从各自的 runtime registry 找探针并打开它的 gun→ammo 页，保存 `tacz_viewer_*.png`。测试 mod 才注册 JEI 生命周期观察器；不把依赖嵌入发布 JAR。API 对照：[JEI 31.8 源码](https://github.com/mezz/JustEnoughItems/tree/d438dc15f13b102ae7e7e36832543514380bd718)、[REI 26.3 源码](https://github.com/shedaniel/RoughlyEnoughItems/tree/b3bc5013d33f12bbea5737bba5fb76652e2b07d8)。此门禁不代替两个独立客户端或工作台上的 transfer 点击。

   `86c8cb6` [两条运行时作业均成功](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44231476610)：[JEI 31.8.0.48 日志/PNG](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44231476610/artifacts/14394469160)、[REI 26.3.823 日志/PNG](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44231476610/artifacts/14394899280)。通过条件包含正确 screen 和非空帧，不只是进程退出 0。本次下载工件在存储端点 EOF，**不记作看过这两张图**。
