# 26.3 特性线：修复后的验收账本

[11 月 18 日审计](PORT_GAPS_26X_2026_11_18.md)描述的是 **`f6678ba` 的生产代码**。本文单独记录后续工作，不把最初的缺口清单改写成发布说明，也不把 `compileJava` 成功解释为画面通过。仅涉及 `26.3_tacz-1.2.X` 的工作分支；没有回移到普通 26.3，也没有发布新版本。

## 能复核的结果

| 范围 | 已观察到的结果 | 不能由此推出 |
|---|---|---|
| 可选 Controllable | [`ae8f1d9` 的独立运行][pad]安装 Fabric Controllable `0.26.2+26.3` 和 Framework `0.13.27+26.3`。SDL 创建虚拟 gamepad，经模组轮询和绑定注册表（9 个动作）进游戏：左扳机切换瞄准，鼠标和扳机同时按住时释放其中一个仍瞄准；半自动右扳机按住后服务端弹匣只减 1，收到两路非零的 SDL 震动回调；空手不进 ADS，持枪瞄准时拔出设备可退出。普通无手柄模组的客户端也能进世界。 | 未摸到实体手柄；震动回调不是马达实测，不能证明死区、舒适度、所有型号或每一个按键组合。没有把两个可选 JAR 捆进发行包。 |
| 挂板、展示柜画面 | [`249315f` 的普通客户端][rack]中，直板和**开锁柜**各留下同一格、同一视角的空格 → 单件预览 → 放入后三帧。带 UH-1 的 AKM 在中间帧可透出背板，放入后实心且手为空。客户端预览不改服务端手/格子；生存模式恰好转移一份、保留组件并同步。潜行、锁柜、已占格、拿木棍是负对照。 | 这次放入是用客户端命中点调用服务端方块交互，**不是按键到服务端的端到端通过**。不能从直板推断折面、普通烘焙物品、TML 外显或所有光影均正确。长枪也不是完全收进一格柜子。 |
| 键鼠与工作台 | 同一普通客户端到达后续测试：J 切换／按住瞄准，侧键开关改装界面，独立卸弹与 Alt + 换弹按键触发单次卸弹；能在脚本夹具上观察弹匣 `3→2→1`。工作台手持无匹配弹药时的[空筛选](../../build-reports/visual/44425974348-tacz_hand_filter_empty.png)和[点击清除后](../../build-reports/visual/44425974348-tacz_hand_filter_cleared.png)是实际截图。 | 当时未逐步断言服务端库存；[`ae8f1d9` 的 Iris 作业][input-failure]在改绑卸弹处超时，不能称所有时序已通过。上述截图不是中英文悬停验收。 |
| 服务器、纯逻辑 | [`043fba5` 的 Full build 和 Verify server smoke][server]已成功（Java 25、JUnit、GameTest、JAR 检查）。含正负旋转/位移约束、混合/复位，资源指纹的版本/内容两种变化，旧玻璃默认列表和自定义/调序负例；实际放置旋转 90°/180° 的 B 型双格工作台模板，核对根 BE ID、头部无重复 BE、配对方向；受权限/数量/空地限制的 `debug pegboard` 同款/不同款小样本。 | 不是所有动作下的第一人称手部画面、所有方块模板、模组真实升级备份、16,384 件的性能测试。只有 build 的**对应步骤**有此结论。 |
| 包、浏览器 | 固定 `356bdf9` 实算 4,176 个有效目的地，3,929 个逐字节相同，229 索引，246 个已记录的上游引用例外。此次没有改分发的默认枪包。`verify_120_resources.py`、掉落及 BE 静态检查、viewer reload 五项检查通过。`043fba5` 的[JEI、REI 两个独立客户端][viewers]均成功。 | 两个 viewer 均不是 EMI。资源路径齐不等于材质、动画或排序的全部游戏内行为已验收。 |

**怎样量预览，而不是只看“截图存在”：** 原始画面 854×480。在中心窗 `[383,471) × [200,280)` 中，以空格和实物 RGB 三通道绝对差之和 ≥48 标记变化像素，再按 `RackPreviewSmoke.compare` 找出预览颜色处于两者之间的像素。直板 **1,487 / 3,031**，柜 **1,141 / 1,803**（中间色 / 变化）。这是玩家能看到预览的证据，**不是**从最终合成画面倒推 shader alpha 恰为 0.65。

| 原帧 | 空格 | 预览 | 放入后 |
|---|---|---|---|
| 直板 | [空](../../build-reports/visual/44425974348-tacz_rack_empty.png) | [预览](../../build-reports/visual/44425974348-tacz_rack_ghost.png) | [放入](../../build-reports/visual/44425974348-tacz_rack_placed.png) |
| 柜 | [空](../../build-reports/visual/44425974348-tacz_case_empty.png) | [预览](../../build-reports/visual/44425974348-tacz_case_ghost.png) | [放入](../../build-reports/visual/44425974348-tacz_case_placed.png) |

## `043fba5` 光影复测

[同一提交的 Iris/Sodium + 实际启用的测试光影][iris] **通过**。改绑卸弹前同时等服务端 ADS 归零、切枪完成和本地锁解除后，独立卸弹、Alt 卸弹各自的服务端弹匣断言以及裸侧键正常换弹通过。改绑侧键的检视触发了动画粒子。AMG10 两个需裁剪倍率的中心未检出注入色，sight-only 倍率正对照重新可见。六材质世界夹具在深浅背景通过出像素和 alpha/blend 对 cutout 的对照；父/子表达式、8 次服务端碰撞至客户端碎屑、固定相机快照的直接模型提交也通过。XL50 实测 classic / PIP 恢复了 **2,787 / 2,438** 个热像像素。这些是集成环境的结果，不外推到所有光影包、手部六材质、真枪口跨世界帧或玩家机器。

从[正对照基准](../../build-reports/visual/44428762277-tacz_amg10_particle_baseline_2.png)和[注入后原帧](../../build-reports/visual/44428762277-tacz_amg10_particle_2.png)复算，zoom 2 中心有 **125 个**新增洋红像素。深色煤块上的 [cutout](../../build-reports/visual/44428762277-tacz_material_dark_particles_opaque.png) 是实心色块，[alpha](../../build-reports/visual/44428762277-tacz_material_dark_particles_alpha.png) 和 [blend](../../build-reports/visual/44428762277-tacz_material_dark_particles_blend.png) 均透出分级底色；按测试中心窗口，alpha 与 blend 相差 243 像素，与 cutout 相差 5,197 像素。亮色背板上的 alpha 对空墙相差 5,226 像素。add、emissive、swirl 在这张静止纹理上外观相近：出现并非三者在任意照明/纹理滚动下都等价。

## `13efe7b`：定位格线，而不是放入失败

[普通客户端][outline-fail]已从新默认 K 经原版 use，在生存模式将单件带配件物品放入挂板，服务端库存和客户端同步断言均通过。随后才失败：直板在同一机位从黄变红时，加入原版 `secondaryBlockOutline` 仍只覆盖 **73/174** 个参考边线像素，未达到 80% 门槛。两条原版线的组合不能当成目标的无深度测试格线。Full build 和专服步骤（包括版本变化才重导出、实际 ZIP 内保留用户编辑）已通过。

[光影客户端][trace-world]在同一提交测到一次游戏内绑定开火、服务端弹匣少一发、同一曳光在 **20 个不同游戏刻**提交模型，转头前后相对初始枪口/相机快照的位置匹配，随后石头命中发出五个普通碎屑。[转头前](../../build-reports/visual/44429872551-tacz_fired_tracer_before_turn.png)和[转头后](../../build-reports/visual/44429872551-tacz_fired_tracer_after_turn.png)原帧保留了机位和石块的相对变化。位置断言来自真实世界提交的条带原点，不是声称仅凭这两张图能量出四元数。8 个独立碰撞对照也通过。REI 和 Controllable 作业通过；本次 JEI 作业在下载 Maven 父 POM 时收到 HTTP 429，未进入游戏。

## `b4310c3`：展示与两个粒子对照

[普通客户端][b43-mesa]已越过完整生存放入测试。独立、只写颜色的格线管线在[空格](../../build-reports/visual/44430716200-tacz_rack_empty.png)与[实物](../../build-reports/visual/44430716200-tacz_rack_placed.png)之间覆盖 **174/174** 个原边线像素（同一 ±2 像素定位窗）。第二面的[空格](../../build-reports/visual/44430716200-tacz_corner_empty.png)、[预览](../../build-reports/visual/44430716200-tacz_corner_ghost.png)、[放入](../../build-reports/visual/44430716200-tacz_corner_placed.png)测得 **1,601/2,639** 个中间色／变化像素。允许展示的普通[绿宝石预览](../../build-reports/visual/44430716200-tacz_vanilla_quad_ghost.png)对[空](../../build-reports/visual/44430716200-tacz_vanilla_quad_empty.png)／[实](../../build-reports/visual/44430716200-tacz_vanilla_quad_placed.png)为 **1,867/2,065**。这些是 K（枪）或原版使用键（普通物品）经客户端、服务端各自的交互和库存同步，不是直接调用 BE。

这次两项粒子断言仍红，原帧指出了夹具的问题：

- 普通客户端在[亮色天空上](../../build-reports/visual/44430716200-tacz_snowstorm_runtime.png)拍无背板的加色脉冲。中心有浅粉色，但旧的全图深洋红筛选数为零。改为对准同一片煤块空墙取前后帧，仍须观察真实的四粒、实际提交、局部新增色及到期回收。
- [Iris 的 zoom 1][b43-iris] 单次差分报 60。比对[基准](../../build-reports/visual/44430716192-tacz_amg10_particle_baseline_1.png)和[注入帧](../../build-reports/visual/44430716192-tacz_amg10_particle_1.png)，**全部 60 个都在 y=264**，是草地 `(82,102,46)` 变成蓝天（G 仍大于 R），不是一排洋红粒子。夹具改为避开地平线，同时验新增能量及最终色相；不放宽镜内零像素门槛。回算旧 zoom 2 正对照仍为 **125/125**。这不证明任意相机／光影都能裁剪。

修正对照后，`529273a` 的 [Iris 作业](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44432265446/job/135252327423) 成功，实际 AMG10 三档中心新增色为 **0 / 0 / 125**，真实开火曳光再次跨 20 个游戏刻，煤块背板四粒脉冲、世界六种材质均已通过。

同一提交的[完整普通客户端、专服、构建](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44432265461/job/135252327768)也通过，不再停在展示交互或世界粒子。它在真实生存背包中逐个悬停六件物品，en_us 和 zh_cn 都检查应有的条件行只出现一次、无能力项不冒出该行；在已打开的创造搜索中完成一次新的资源同步，翻页选择 `reload_smoke` 后只剩目标物品，点“全部”恢复排序后的内容。两种 64 件／16 板场景都见齐所有板、独立采集 CPU 提取时间并清场；随后关世界、重进同一份存档也通过。

**悬停原帧：** [XM157 英文测距与火控](../../build-reports/visual/44432265461-tacz_hover_en_us_scope_xm157.png)、[MK5HD 中文弹容计数](../../build-reports/visual/44432265461-tacz_hover_zh_cn_scope_mk5hd.png)、[减少火光／增加烟雾](../../build-reports/visual/44432265461-tacz_hover_en_us_less_flash.png)、[增加火光／减少烟雾](../../build-reports/visual/44432265461-tacz_hover_zh_cn_more_flash.png)、[中文柜子说明](../../build-reports/visual/44432265461-tacz_hover_zh_cn_gun_display_case.png)。同次运行还通过了标准 8x 没有这些能力行的负对照。客户端在重载前后也检查了 variant、slot-adapter 全集及逐 ID 反查；不是靠翻译键存在作结论。

**64 件物品的观测，不是 FPS 测评：** 同一 Mesa 软渲染作业、同一位置放 16 块板，每板 4 件；等最后一个槽位同步、区块渲染就绪，预热 20 tick 后收集 30 tick。下列是每次单块 BE 调用 `extractRenderState` 的 CPU 时间，取自该作业的[原始日志复核](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44433542077)。没有把 GPU、提交、材质烘焙总耗时或游戏整帧算在内。

| 场景 | 单板调用次数 | 中位数 | p95 |
|---|---:|---:|---:|
| [64 件同款](../../build-reports/visual/44432265461-tacz_rack_load_same.png) | 1,568 | 1 μs | 4 μs |
| [64 件不同款](../../build-reports/visual/44432265461-tacz_rack_load_different.png) | 784 | 3 μs | 9 μs |

## 手部、侧倾、纯网格和旋转模板

`b57d091` 的[普通客户端](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44433542086/job/135256637739)在纯网格预览处失败：0/6,972 个中间色像素。[预览](../../build-reports/visual/44433542086-tacz_mesh_ghost.png)和[放入后](../../build-reports/visual/44433542086-tacz_mesh_placed.png)几乎全屏青色，无法分辨手中网格与挂板。该提交的 [Iris](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44433542106/job/135256637522) 在第一张手部对照图以前报 `PreparedFrame already in use`，不是“手部粒子不可见”的测量。测试旧代码替换了状态机拥有的轨道表，违背 `AnimationStateContext` 的身份检查；不能据此在产品里强制关闭主帧。

`bdd681a` 的[普通 Mesa 客户端、Full build 与专服](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44434717131/job/135260690861)通过。现在手持普通 AK 模型；**青色只存在于 `FIXED` 外显的纯 `poly_mesh` LOD**，槽位以外的天空也不允许整片变色。对准第二面时有中间色网格，实际按 K 后变为不透明，服务端逐项核对组件与计数。A/B/C 工作台、标靶、雕像都实际从模板旋转 90° 和 180° 放置；双格关系和对应 BE 内容经 GameTest 检查。

六个外部动画关键帧在状态机自己分配的轨道播放，发射器跟随真实枪口，`to_world=false`。Mesa 下统计新增粉色像素的色强分位比（q20/q85），煤块前 cutout **1.00**、alpha/blend 都是 **0.318**；浅色背板分别为 **1.00 / 0.467 / 0.467**。另外三种手部材质也有可见像素，不用这些数证明照明、深度写入或 UV 滚动都已对齐。相机仰视 35° 再侧倾 0°/30° 时，从画面取狭长纹理的主轴：`rotate_y` 和 `lookat_y` 与投影的世界竖直方向吻合（绝对点积 ≥0.98），高度缩短；对照 `rotate_xyz` 仍在屏幕上竖直。`b57d091` 的 [JEI 和 REI](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44433542067)也分别跑通了重载后的展示和双向查询，不再停在 429。

可对照正常的[纯网格空槽](../../build-reports/visual/44434717131-tacz_mesh_empty.png)、[青色预览](../../build-reports/visual/44434717131-tacz_mesh_ghost.png)、[实物](../../build-reports/visual/44434717131-tacz_mesh_placed.png)：同一个中心窗口，**2,820 / 3,065** 个青色网格像素符合中间色条件。[30° 时绕 Y](../../build-reports/visual/44434717131-tacz_billboard_roll_30_rotate_y.png)与[同机位完全朝相机](../../build-reports/visual/44434717131-tacz_billboard_roll_30_rotate_xyz.png)的长边也可直接比较。

`bdd681a` 的 [Iris 手部对照](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44434717123/job/135260570748)跑到了深色六材质，但旧 q85 判据失败。对[基准](../../build-reports/visual/44434717123-tacz_hand_material_dark_control.png)复算，[alpha](../../build-reports/visual/44434717123-tacz_hand_material_dark_particles_alpha.png) 和 [blend](../../build-reports/visual/44434717123-tacz_hand_material_dark_particles_blend.png)都**只有同样的三个**新增色强值 36、76、120。最高一档分别占 55/349 和 47/325，手部遮挡的一小块使 q85 刚好落在台阶两边；[cutout](../../build-reports/visual/44434717123-tacz_hand_material_dark_particles_opaque.png) 则为 349 个同色强 127 的像素。新判据比较完整累计直方图的距离，并要求两种半透明材质相对实心对照仍有宽的强度分布；并非简单调大 0.17 的容差。

`3594ef8` 的[启用光影的复测](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44436002454/job/135264924855)成功：深浅背板上手部 cutout/alpha/blend 的 q10/q95 都是 **1.0 / 0.30 / 0.30**。累计直方图距离（alpha 对 blend / 半透明对 cutout 的较小值）深色 **1.69 / 73.18**，浅色 **0.94 / 73.77**。两种相机侧倾、六种手部及世界材质、AMG10 0/0/125 和 XL50 热像恢复 **2,611 / 2,302** 像素（经典／PIP）均通过。深浅背板是背景对照，不是多方向照明或深度写入对照。

同一提交的[无可选模组普通构建及客户端](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44436002439/job/135265017304)亦通过；手部深/浅背景的 alpha 对 blend 距离为 **0.65 / 0.32**（各自对 cutout 均超过 26），不是同一份 Iris 结果。独立的 [JEI/REI 两项](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44436002559)通过。该轮 PAL 与手柄作业却未启动游戏：Gradle 无法为 Fabric Maven 的 ASM POM 解析 `org.ow2:ow2:1.5.1`，不能作为这两个可选组合的结果。CI 仅为此组 Central 坐标在解析前加入一个镜像；不替换版本，也不跳过游戏测试。

## PAL 离屏与第一人称约束的独立验收

`4f2df05` 的[普通构建、专服与 Mesa 客户端](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44437138832/job/135268736218)、[手柄](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44437138908/job/135268736808)、[两个 viewer](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44437138855)通过。[PAL 作业](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44437138849/job/135268736137)已进入游戏，但测试自己构造的 `RemotePlayer` 没有出生包提供 ID，读 `getId()` 即报错；没有到达离屏动作断言。这是夹具的失败，不据此改 PAL 播放逻辑。[Iris 作业](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44437138863/job/135268736379)解析 Gson/Kotlin 元数据遇 HTTP 429，没有光影画面。最近同时跑完普通与光影渲染套件的提交仍是 `3594ef8`。

复测把未占用的负 ID 赋给这个仅用于测试的角色，不向服务端发假玩家。另用同一套第一人称模型、同一个运动的父骨骼和三条等时动画，在 ADS 实际写入约束矩阵前后取样；+1 → 0 → −1 同机位留三张图，比较两种符号与零对照的姿态差异。这两种新验收不能靠测试代码存在就标绿。

## 尚不能外推的部分

- **输入与展示：** `ae8f1d9` 的[超时][rack-failure]后来在 `043fba5` 定位到原版好友 GUI 吞掉 O（两端均未收到 use）。新默认 K 和旧 `options.txt` 的手动改绑说明见 README。已过的是生存模式的指定板／柜和烘焙物品，不是每种第三方特殊渲染器或用户现存的所有键位组合。
- **粒子与曳光：** 世界六材质、玩家开火跨帧、0/5 碎屑和本测试光影的三档准镜有独立证据。普通客户端的手部六材质与侧倾有单独对照；不能外推到任意光影包。[旧版正对照为零的原帧](../../build-reports/visual/44425974217-tacz_amg10_particle_2.png)也不能作通过证据。
- **近似实现与预算：** 保留测距 1,024 格、标记倍率 32、模拟 600 tick 及实体寿命上限；超预算或不可达不画。普通发光骨骼的环境 sky 与面法线是两件事，仍需非准星骨骼的方向光画面对照。约束通道的数值/复位不是所有 ADS 动作的手部姿态证明。PAL 玩家离屏／再入镜、第三方大包与 GPU 性能也不能由 16 板的 CPU 样本推出。转换器遇不可证等价的条件仍报出，不静默改包。
- **外部组合：** Sable/Simulated、独立 EMI 插件、Accelerated Rendering 都没有在本轮获得可验证的 26.3 运行组合；AR 仍是禁用，不写成已修。未找到可验证组合不等于不存在。KubeJS 不把目标 Fabric 上游已注释的事件投递算作已运行能力。其它 26.X 线的欠账保留在原审计。

[pad]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44427465994/job/135236056454
[rack]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44425974348/job/135230940163
[input-failure]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44427465913/job/135236058051
[rack-failure]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44427465932/job/135235909898
[server]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44428762294/job/135240376511
[viewers]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44428762236
[iris]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44428762277/job/135240376445
[outline-fail]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44429872717/job/135244203544
[trace-world]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44429872551/job/135244203201
[b43-mesa]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44430716200/job/135247080971
[b43-iris]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44430716192/job/135247080786
