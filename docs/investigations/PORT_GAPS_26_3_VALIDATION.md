# 26.3 特性线：修复后的验收账本

[11 月 18 日审计](PORT_GAPS_26X_2026_11_18.md)描述的是 **`f6678ba` 的生产代码**。本文单独记录后续工作，不把最初的缺口清单改写成发布说明，也不把 `compileJava` 成功解释为画面通过。仅涉及 `26.3_tacz-1.2.X` 的工作分支；没有回移到普通 26.3，也没有发布新版本。

**最近一次在同一代码提交上完整通过：[`81f474e`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/81f474e28f1e76c0ea0eeb056b58f36424841dc3)。** [完整构建、JUnit、专服、嵌套 MAE 与普通 Mesa 客户端][final-mesa]、[Iris/Sodium 启用回归光影][final-iris]、[PAL][final-pal]、[Controllable][final-pad]、[JEI 与 REI 两个独立作业][final-viewers]、[编译][final-compile]均通过。下文较早的失败保留其原提交与原因，不是当前代码的状态。

## 按原审计找结论

| 原审计 §2 | 实际验收路径 | 保留边界 |
|---|---|---|
| ① 重绑／卸弹、⑧ 工作台空筛选 | 客户端改绑键盘和鼠标侧键；独立卸弹及 Alt 备用绑定分别改变服务端弹匣，裸换弹仍可用。空配方页点清除后恢复。 | 不是每位玩家已有 `options.txt` 都自动改键。 |
| ② 带符号约束、③ 曳光 | 单测覆盖幅值／混合／复位；游戏内三条受控动画同时比 ADS 手部变换与原帧。同一游戏内曳光跨 20 个不同 tick 提交的位置与初始快照匹配。 | 曳光位置来自世界渲染提交，不是从截图反推服务端弹道；约束夹具不是所有枪包的动作。 |
| ④ 展示预览、⑫ 压测命令 | 普通板、折面、柜、普通物品、纯网格外显有空／预览／实物三帧；按键与实际生存库存一并检查。命令验证权限、场地和清理，两种 64 件场景有单板提取耗时。 | 预览合成色不是 shader alpha 标定；CPU 提取时间不是整帧 FPS。 |
| ⑤ Snowstorm、⑥ 命中碎屑 | 手部和世界六材质、俯仰／侧倾、镜内排除与镜外正对照分开做。八次服务端方块碰撞区分受客户端数量和开关约束的表面碎屑与服务端另发效果。 | 世界前后片另有深度与暗／亮 UV2 对照；不能据此推出手部也做了重叠像素测量或所有光影均相同。 |
| ⑦ 排序、⑨ 悬停、⑩ 客户端 API | 多包排序、开着的创造搜索重载／翻页／“全部”，中英文真实背包悬停正负例，以及客户端 variant、adapter 枚举和按 ID 反查。 | 不用翻译键或物品总数代替实际显示、选择和重载断言。 |
| ⑪ 配置与再导出 | 仅迁移原样旧玻璃列表；调序和自定义列表是负例。版本指纹变化时重新导出并在 ZIP 备份中核对原编辑。 | 执行的是同资源、不同旧清单指纹的升级夹具，不是两版发行 JAR 原地升级。 |

§3 的模板旋转、父子效果脚本作用域、测距预算、PAL 与发光骨骼另记在下文；§5 中真正运行过的可选组合包括 Controllable、PAL、JEI、REI，但不是四者同装的兼容承诺。

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

`4f2df05` 的[普通构建、专服与 Mesa 客户端](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44437138832/job/135268736218)、[手柄](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44437138908/job/135268736808)、[两个 viewer](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44437138855)通过。[PAL 作业](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44437138849/job/135268736137)已进入游戏，但测试自己构造的 `RemotePlayer` 没有出生包提供 ID，读 `getId()` 即报错；没有到达离屏动作断言。这是夹具的失败，不据此改 PAL 播放逻辑。[Iris 作业](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44437138863/job/135268736379)解析 Gson/Kotlin 元数据遇 HTTP 429，没有光影画面。当时最近同时跑完普通与光影渲染套件的提交是 `3594ef8`。

`b49d4e9` 首次[跑完 PAL 1.2.7 的离屏验收](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44438204138/job/135272258500)。这个集成客户端给测试 `RemotePlayer` 分配了一个未占用的负 ID，不向服务端发假玩家。转身背对角色时模型 `setupAnim` 计数为零，步枪上身循环已启动并推进；转回看见角色，背过身收枪、换手枪时无需再渲染就切到了不同的待机动画，第二次转回仍在播。本地玩家 F5 后自己的控制器启用，回第一人称后停止；远端手枪层不受影响。**不是两台网络客户端，也没有测任意第三人称模组的组合。**

同一提交的[普通 Mesa / Vulkan](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44438204095/job/135272258661)与[启用测试光影的 Iris / OpenGL](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44438204109/job/135272258600)均通过完整套件。为了从数值断言走到画面，在 AK 第一人称模型的同一父骨骼上写入相同位移与 12° 旋转，唯一区别是 `constraint` 三轴的位移和旋转通道：**+1 → 0 → −1**。中间的零防止沿用上一条正向动画蒙混过关。ADS 权重 ≥0.95 时在实际姿态栈写入前后取样，再用同一机位的图比较（不含聊天框和快捷栏；RGB 三通道绝对差之和 ≥48 的像素数）。

| 客户端 | ±1 的平移／旋转校正上界 | 零对照平移／旋转（弧度） | + 与 − 不同的像素 | + 与 0 / − 与 0 |
|---|---:|---:|---:|---:|
| 普通 | <0.000001 / 0 | 0.3998 / 0.2103 | 6,877 | 109,523 / 109,700 |
| Iris 光影 | <0.000001 / 0 | 0.4010 / 0.2094 | 4,096 | 111,198 / 111,153 |

结论是**这组三条实际动画**中正负输入被当成同一幅值，零输入则产生可见的不同姿态；没有声称正负两帧逐像素相等，或每个第三方模型的 ADS 都无偏移。之前的 JUnit 仍分别覆盖混合、复位和根约束节点。

**原帧复核：** [普通 +](../../build-reports/visual/44438204095-tacz_constraint_ads_positive.png) / [0](../../build-reports/visual/44438204095-tacz_constraint_ads_zero.png) / [−](../../build-reports/visual/44438204095-tacz_constraint_ads_negative.png)，[Iris +](../../build-reports/visual/44438204109-tacz_constraint_ads_positive.png) / [0](../../build-reports/visual/44438204109-tacz_constraint_ads_zero.png) / [−](../../build-reports/visual/44438204109-tacz_constraint_ads_negative.png)。这是人为加大动作的测量模型，不是原版 AK 持枪演示。[PAL 第一次入镜](../../build-reports/visual/44438204138-tacz_pal_remote_rifle.png)与[第二次](../../build-reports/visual/44438204138-tacz_pal_remote_pistol.png)可见同一角色，但本地第一人称模型挡住了一部分手臂；上一段对离屏切换的判断来自控制器和模型计数，而非这两张图的枪形。

## 普通发光骨骼，不用准星代测

`9ac8d64` 的顶点转发器少了 26.3 要求的 packed-color 重载，未通过编译；`3f8aeb8` 的 Full build（含 JUnit）和专服通过。[同一提交的 PAL 作业](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44440040072/job/135278303144)将角色移到第一人称物品左侧，侧身取样。原包的步枪／手枪 `hold_upper` 右臂通道相同，但支撑手不同：在真正的 `PlayerModel.setupAnim` 结束时，这两次入镜的**左臂 z 坐标相差 4.00 个模型单位、滚转相差 0.338 弧度**。[步枪入镜](../../build-reports/visual/44440040072-tacz_pal_remote_rifle.png)和[手枪再入镜](../../build-reports/visual/44440040072-tacz_pal_remote_pistol.png)现在都能在本地第一人称物品左侧看清角色的支撑手，不仅是控制器换了 clip 名。

`3f8aeb8` 的普通 `_illuminated` 立方体在提取时沿骨骼树传递发光标记，写顶点时采用 26.3 当前光照条目的主光方向，不把“输入 packed light 刚好全亮”误当发光标记。保留原材质、透明度、掩码、深度与原来的环境 sky 选项。校准夹具给**普通和发光两者同样的满亮 lightmap**，只在父骨骼名字上有无后缀，比较子立方体转向 35° 与 125° 的青色各面。[`3f8aeb8` 的 Iris](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44440040071/job/135278413865)两种模型在两种朝向的 G 通道 q10/q90 均为 244/244：此回归光影直接输出纹理乘顶点色，普通对照本身不做法线光照，所以**不能**据此称方向光对照已过。[普通作业](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44440040228/job/135278303704)在客户端步骤的前两秒退出，只有空的 `tacz-vulkan.log`、没有游戏日志；具体 WSI 错误未取到，不是画面失败或通过。后续捕获 `vulkaninfo` 的 stderr，不跳过设备/窗口表面门槛。

`cb50c35` 的[普通作业](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44440883621/job/135281169510)又在更早的 Full build 阶段缺少 ASM 的 `org.ow2:ow2:1.5.1` 父 POM。原镜像仅随客户端 Gradle 命令传入，未覆盖 CI 的首次 `build` / `compileJava`；将同一份限定坐标的 init script 也传给这两个 CI 步骤。不改变玩家的构建仓库或依赖版本。

[`88cb2c3` 的 Mesa 客户端](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44441481899/job/135283115485)终于到达同一画面对照，**失败**：35° [普通子骨骼](../../build-reports/visual/44441481899-tacz_bone_light_35_plain.png) G10/G90 = **98/208**，[发光子骨骼](../../build-reports/visual/44441481899-tacz_bone_light_35_glow.png) = **98/98**，各面一致却全是暗面；没有到第二个朝向。26.3 实体 cutout 在 `PER_FACE_LIGHTING` 下按 `gl_FrontFacing` 选择正或负点积，单一朝光法线不能保证两面都亮。不能靠放宽亮度门槛验收。

上一轮 `cb50c35` 的 [PAL](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44440883638/job/135281235952) 和 [Iris](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44440883614/job/135281245553) 未进游戏（Loom 依赖注解/Cadix 解析失败）；手柄、两个 viewer 与编译已过。CI 改为仅把实际 Central URL 指向同坐标的 Central 镜像，保留 Fabric/插件与各 mod 仓库、ASM 父子优先顺序及玩家构建不变。

## 粒子的深度、光照与 UV 分开验证

`1686095` [普通客户端](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44442691518/job/135287185794)先测世界粒子，全部粒子断言返回后才在骨骼又测到 98/208 对 98/98；该作业总结果是失败。[同提交的 Iris 完整作业](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44442691213/job/135287184608)成功。校准在同一煤墙前，先画近处的蓝片，再画远处的绿片。每张 854×480 原帧用中心 33×33 去除准星 13×13，共 920 像素，逐像素减去本机位空墙，再算通道均值。下表四舍五入到整数。

| 路径 | 只画后片 ΔG | 前片 add + 后片 ΔG | 前片 emissive + 后片 ΔG | add 暗 / 满 UV2 的 ΔB | emissive 暗 / 满 UV2 的 ΔB |
|---|---:|---:|---:|---:|---:|
| 普通 Mesa（[空墙](../../build-reports/visual/44442691518-tacz_material_dark_control.png)） | [232](../../build-reports/visual/44442691518-tacz_particle_pass_rear.png) | [232](../../build-reports/visual/44442691518-tacz_particle_pass_add_rear.png) | [0](../../build-reports/visual/44442691518-tacz_particle_pass_emissive_rear.png) | [19](../../build-reports/visual/44442691518-tacz_particle_pass_add_dark.png) / [201](../../build-reports/visual/44442691518-tacz_particle_pass_add_full.png) | [201](../../build-reports/visual/44442691518-tacz_particle_pass_emissive_dark.png) / [201](../../build-reports/visual/44442691518-tacz_particle_pass_emissive_full.png) |
| Iris 回归光影（[空墙](../../build-reports/visual/44442691213-tacz_material_dark_control.png)） | [225](../../build-reports/visual/44442691213-tacz_particle_pass_rear.png) | [225](../../build-reports/visual/44442691213-tacz_particle_pass_add_rear.png) | [0](../../build-reports/visual/44442691213-tacz_particle_pass_emissive_rear.png) | 201 / 201 | 201 / 201 |

两边的 add 重叠帧中央均能看见后片叠成的青色，emissive 重叠帧则只剩蓝片；不是只比较材质枚举名。普通路径的 add 随 lightmap 变化，emissive 不变；这套 Iris 光影本身不采 lightmap，不能拿它证明 add 受光。另核对世界、手部、镜片裁剪三种 **实际 RenderSetup** 的深度状态与 UV 矩阵（add/emissive 为单位矩阵，SBM swirl 为 (1,1) 固定位移，原生裁剪枪焰为零）。世界重叠校准不是手部、镜内各自的重叠像素测量，也不是时间滚动演示。

## 发光骨骼的双面材质

为修正骨骼暗面，通用快照不再伪造法线或拦截 Lighting UBO：只为带标记的连续几何选无方向光的实体材质，保留 UV2、原始几何法线、贴图、cutout/半透明、镜片外掩码。普通节点仍用原材质，未知自定义材质不猜测替换。立即写顶点的调用方可传两个材质各自的 consumer；旧的单 consumer 入口不能更改调用方已选的 shader。JUnit 用同一满亮 UV2 断言普通根 → 发光功能节点的子 → 普通兄弟分别写进正确的 buffer。

`81f474e` 的[普通客户端][final-mesa]与[启用光影的 Iris][final-iris]同时跑完。对照中材质、UV2 和视角相同，只改父节点后缀。另把 X 取反改变面绕序，并使用实体半透明材质；纹理仍为不透明的青色，不以此标定透明度。下表是屏幕中央立方体绿色通道的第 10 / 90 百分位：

| 渲染路径 | 朝向 | 普通子骨骼 G10/G90 | 发光子骨骼 G10/G90 | 入样像素（普通 / 发光） |
|---|---:|---:|---:|---:|
| Mesa cutout | 35° | 98/208 | **244/244** | 11,074 / 11,162 |
| Mesa cutout | 125° | 101/244 | **244/244** | 10,554 / 10,554 |
| Mesa 半透明材质、镜像 | 35° | 98/244 | **244/244** | 11,162 / 11,162 |
| Mesa 半透明材质、镜像 | 125° | 98/211 | **244/244** | 10,554 / 10,554 |

这次不只是“各面一致”：普通对照明显分面，而发光件没有退回此前的暗值 98，改变绕序仍然亮。Iris 四组的普通／发光都是 **244/244**，说明这些几何在该光影中可见，但光影本身不做法线对照，方向光结论来自普通客户端。同提交还复现了上一节两行粒子测量；PAL 再次测得支撑手 Δz=4.00、Δroll=0.338。

## 尚不能外推的部分

- **输入与展示：** `ae8f1d9` 的[超时][rack-failure]后来在 `043fba5` 定位到原版好友 GUI 吞掉 O（两端均未收到 use）。新默认 K 和旧 `options.txt` 的手动改绑说明见 README。已过的是生存模式的指定板／柜和烘焙物品，不是每种第三方特殊渲染器或用户现存的所有键位组合。
- **粒子与曳光：** 世界六材质、玩家开火跨帧、0/5 碎屑和本测试光影的三档准镜有独立证据。普通客户端的手部六材质与侧倾有单独对照；不能外推到任意光影包。[旧版正对照为零的原帧](../../build-reports/visual/44425974217-tacz_amg10_particle_2.png)也不能作通过证据。
- **近似实现与预算：** 保留测距 1,024 格、标记倍率 32、模拟 600 tick 及实体寿命上限；超预算或不可达不画。已做非准星骨骼两朝向和反绕序画面对照；这不测量第三方光影如何解释环境 sky，也不是对每种 OIT 设置或特殊材质的测试。约束的指定 ADS 夹具和 PAL 单客户端离屏/再入镜实测见上；并非所有 ADS 动作或两人联网验收。第三方大包与 GPU 性能不能由 16 板的 CPU 样本推出。转换器遇不可证等价的条件仍报出，不静默改包。
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

[final-mesa]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44443513579/job/135289937996
[final-iris]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44443513680/job/135289938691
[final-pal]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44443513586/job/135289938132
[final-pad]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44443513634/job/135289938491
[final-viewers]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44443513754
[final-compile]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44443513653/job/135289938045
