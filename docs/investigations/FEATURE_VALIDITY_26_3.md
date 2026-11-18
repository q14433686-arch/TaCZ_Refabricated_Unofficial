# 26.3 移植功能有效性总表（本轮复核）

## 先回答范围问题

**否，目前没有移植并验证 26.X 应有的全部功能。** 本线目标是 **26.3**；不能把 26.3 的构建/测试外推到所有 26.X，也不能把上游 1.21.1 的所有实现文件直接搬入新渲染架构。

本轮以 Fabric 上游固定提交 `356bdf9d48f2225bac6ceb56adc5576a418517a8` 为对照，而非声称覆盖将来上游版本或任意枪包。默认包4,176个有效目的地和229索引验证是资源完整性证据，不是4,176项玩家行为证明。

状态用语：
- **已实现且有限验证**：正式代码消费，标出运行环境和断言；不写“所有环境正常”。
- **部分实现**：明确列出缺少的行为。
- **依赖待核实/集成未做**：不是完成；没有找到26.3构件不等于证明全网没有。
- **原架构替代/不适用**：说明等价接点，不搬旧API；替代不自动保证性能相同。

## 用户日志 latest-2.log 的复核

本次用户环境为 MC26.3 / Iris1.11.6+mc26.3 / Sodium0.9.2+mc26.3，AMD RX7800XT，OpenGL；使用 `ComplementaryUnbound_r5.9.3 + EuphoriaPatches_1.10.5`。日志存在多次关闭/开启光影，掩码桥显示 active。没有热捕获异常日志**并不表示热成像成功**：旧 `ScopeLensFrame.beginFrame` 遇到 shader pack 主动清空镜片配置，根本没有执行热捕获。

同时发现 Iris 后置合成总是传 `null` 镜片，而且只允许PIP，不允许经典FOV的镜片处理。修复覆盖这两层与窄FOV场景热源深度的采集，私有热覆盖重放期间采用原版cutout程序，跳过Iris阴影pass；不把光影 gbuffer 输出误当热mask。

日志里的第三方包缺 data、非法 attachment type、非法路径、坏标签/翻译是另立问题，**不是本次热成像被代码禁用的借口**。本轮没有这些枪包原文件，不能宣称全部修好。

## 新增/替代功能逐组有效性清单

以下列出实现与现有专项测试名；最终运行号/本轮通过与否另记在文末。测试存在本身不等于通过。

| 功能组 | 检查路径与有效性判据 | 仍不覆盖的情形 |
|---|---|---|
| 包发现、版本/overlay、叠加标签 | GunPackOverlayTest及多包标签测试；目录/ZIP、两种顺序、普通资源覆盖与附件数组累加分开 | 任意不合规第三方包；多层包布局的全组合 |
| 同步、压缩/分片、重载 | 专服GameTest、ConversionReloadSmoke、TrackingActionSmoke；索引/组件/配方实际同步、动作结算 | 不同能力版本双客户端、公网延迟/丢包矩阵 |
| 默认包、语言/配方/进度 | verify_120_resources.py与默认catalog；4,176目的地、229索引；已知246上游引用例外单列 | 不把源包自带坏引用算作本端新功能有效；非70把枪逐帧录像 |
| OEM、虚拟件、variant、slot adapter、改装 | ScriptGuardSmoke/DefaultRefitSmoke/OpticInstallSmoke；正式C2S、库存结算、非法组合拒绝、39个默认scope合法宿主 | 不等于所有镜可装所有枪；14外部包未提供 |
| 装填、卸弹、动作后事件、时间 | DefaultReloadSmoke/GunActionSmoke/SessionClockSmoke/WorldReturnSmoke；脚本分支、库存、暂停及重进 | 不承诺所有第三方Lua脚本 |
| 射手位移 | ShotConsequencesSmoke；DB-2地面/空中、配件、取消、两端实际位置/速度 | 非全部枪的听感/手感对比 |
| 枪声激怒 | ShotConsequencesSmoke；敌对/中立、半径、已有目标、消音、禁用及取消 | 非生物寻路到枪声坐标功能 |
| 趴下与空中散布 | MovementRulesSmoke及服务器测试；空手允许/拒绝、退出、姿态归属、ADS空中开关 | 趴姿鼠标持续俯仰边界仍需人工输入验证 |
| 子弹穿透/免伤/方块破坏 | BulletRulesSmoke等专服测试；同队、实体列表、标签/硬度与开关 | 非独立双客户端复杂弹道矩阵 |
| 子弹存储/升级/挂板/柜子 | 事务、BE/组件、染色/掉落/搬移/存取与重入；实际菜单截图 | 中文+REI/JEI+多GUI比例仍分环境验收；CarryOn实际安装组合 |
| 创造栏/包筛选 | CreativeReloadSmoke；真实输入和已打开界面刷新 | 所有语言/分辨率 |
| JEI、REI | 独立viewer CI；双向查询、重载后实际页面与对照像素 | EMI不是同一集成；全部transfer点击/独立远程组合 |
| 枪包转换 | ConversionReloadSmoke；导出新ZIP、不改源包、实际重载；不支持项拒绝 | 非任意包无损转换承诺 |
| 首/三人称、外显/LOD、mesh | GunRigBaker/DisplayBake/collector/NativeMuzzleEffects；实际提交、F5、追踪者、卸载释放 | PAL/YSM/Iris/Voxy/远近/实体数量及所有显卡笛卡尔积未全测 |
| 默认renew枪口火光/烟 | MuzzleEffectsSmoke；AK47/Glock、F5、追踪者、烟关火光保留、离场清理 | 原生profile替代，不是通用Snowstorm运行时；非像素完全一致 |
| 枪模文字/背包直读 | RangefinderSmoke/CoreCompletionSmoke；真实库存17/26/9、普通弹匣3、PAPI与HUD对照 | 非任意枪包定位/字体/Iris文字裁剪的逐包保证 |
| XM157落点 | ImpactMarkerSmoke；恢复12个黄色像素、墙后零提交、解除ADS | 其它镜/颜色/独显与光影需独立复测 |
| 光学/热成像镜片 | ScopeLensSmoke；经典/PIP倍率不双乘、镜外不染色、活体热源、墙后冷、tag否决、资源重载 | 本轮前Iris路径实际禁用；本轮修复需看独立Iris运行结果，不能借无光影通过填绿 |
| 配置与玩家反馈 | ConfigPanelSmoke/PlayerFeedbackSmoke；9控件、save consumer、CLIENT/COMMON实际TOML；普通受伤hook/隐藏背挂/命中配送 | 重启持久化、双客户端、全部输入组合未完全自动化 |
| 兼容提示 | GunPackOverlayTest/CoreCompletionSmoke；不匹配无overlay分类、正式S2C、进服队列去重、本地隐藏命令 | 双客户端独立隐藏、恶意消息反复发送、重启视觉按钮人工矩阵 |
| 动画粒子 | AnimationParticleChannelTest/CoreCompletionSmoke；0帧、边界、循环限额、解析、实际检视渲染提交 | **部分**：Molang/pre_effect_script、自定义材质/组件、过渡混合、远端生命周期未完成 |
| 可选物理/配方/渲染集成 | Sable/Simulated/EMI/AR的公开版本与API证据见PLAYER_ACCEPTANCE清单E节 | **未完成**：没有验证过26.3运行时，不硬装1.21.1构件 |
| 旧GL VBO/CCA/KosmX形态 | 26.3原生collector、entity data、PAL及时钟等对应接点保留，不照搬旧二进制 | 旧API不适用不等于优化性能已对等；功能差异继续列为缺口 |

## 测试环境必须分列

1. 基础：真实MC26.3、Mesa软件Vulkan、无shader pack的完整客户端/专服/JUnit。
2. JEI和REI：各自独立运行，不共享“某查看器正常”的结论。
3. Iris：真实Iris1.11.6与Sodium0.9.2，26.3 Fabric限定、下载hash校验；OpenGL软件环境加自编最小shader pack，验证真实程序替换/后置合成与热源像素，不以假API标志代替。
4. 用户环境：AMD + ComplementaryUnbound/Euphoria + Distant Horizons与个人包，不能由第3项外推为全部通过。需用户环境复验，不能隐瞒。

人工验收准备/动作/预期/证据详见 [PLAYER_ACCEPTANCE_120_REMAINDER.md](PLAYER_ACCEPTANCE_120_REMAINDER.md)。


## 本轮验证结果

**测试源码 c68ca72；生产修复6a93ea6、4a0fdc3。**

| 门禁 | 运行 | 结果 |
|---|---|---|
| compile-check | [44375655842](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44375655842) | success |
| 完整build / JUnit / 专服 / 无光影真实客户端 | [44375655862](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44375655862)，job135054230686 | success |
| Iris/Sodium + active shader pack，OpenGL/EGL | [44375655879](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44375655879)，job135054042332 | success |
| JEI/REI独立运行（本轮较早源码，后续未改viewer实现） | [44371849362](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44371849362) | success |

Iris实际像素断言：XL50经典2859 / PIP重投影2102热源像素；墙后冷、live tag否决冷；真实Iris开关入口关闭/重新启用后2312热源像素；stripe倍率7/45:45/40:40，镜外保持不变。还执行测距、XM157、射手位移/激怒、趴下、枪口、反馈、库存PAPI、检视粒子、提示消息、配置和39镜安装；该run的总门禁成功。不是仅安装Iris后保持光影关闭。

无光影同提交：XL50经典2950/PIP2540；XM157恢复6黄色像素、墙后零；39镜实际C2S安装、配置TOML、库存PAPI17/26/9、普通3等均通过。没有为光影测试放宽原有像素阈值。PIP测试显式开启本来就需玩家选择的 `ScopePipAllowShaderPacks`；经典热成像不依赖开启该PIP选项。

本地只读资源复核4,176文件/229索引通过，246原上游引用例外保留；弹孔六面离线回归6/6、viewer静态检查5/5、版本严格检查6通过0失败1分支名警告；这些不冒充光影运行证据。

**未验证项仍保留**：真二次渲染热像分支已接线但这次自动像素用例测的是重投影，不把它填成已通过；用户Complementary/Euphoria/DH组合、硬件、多客户端及上表各项人工矩阵不在本轮全通过声明内。

调试运行如实保留：44371849790是依赖名称匹配准备失败；44371969829/44373432466是headless GLX窗口创建失败，未执行效果断言；改用SDL3正确的EGL hint后44374086310跑到PIP选项未开启，44374981563跑到错误的测试开关调用（reload重新读取旧配置）；修正测试准备/使用Iris真实按键入口后44375655879整组通过。未通过关闭真实shader pack、伪造API状态或删除热源/遮挡断言来取绿。

完整原始日志/PNG在 `iris-c68ca7294c65896c78a91f0c1873d729fde88d2c` artifact（14460153537）。选取经典热像/遮墙/光影恢复帧供目视复核，不将完整日志写入Git。
