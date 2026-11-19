> 当前新增粒子源码与验证边界见 [SNOWSTORM_RUNTIME_26_3.md](SNOWSTORM_RUNTIME_26_3.md)。下方历史“未实现”描述不代表新增批次，但也不能用源码存在替代实测通过。

# 1.2.0 剩余项：实现边界与逐项人工验收

> 最新全功能有效性与光影修复状态：[FEATURE_VALIDITY_26_3.md](FEATURE_VALIDITY_26_3.md)。下方是上一批交付清单，不应据此断言所有26.X或光影环境正常。

## 本批交付与明确未完成项

- 枪模 `%ammo_count%`：主手背包直读枪改用与 HUD 相同的客户端计数缓存；普通枪保持弹匣计数和闭膛膛内弹逻辑。缺数据安全返回。
- 枪包版本提示：区分客户端/服务端发现快照；不匹配且无适用 overlay 时提示；服务端只传有界名称；客户端进服延迟、去重、退出清理；本地说明/隐藏按钮、Cloth 开关及 TOML 保存。提示不强制启用、不替用户转换枪包。
- 动画粒子：解析 `particle_effects` 的字符串、对象、数组，时间轴采样、循环限额、消费队列、定位组姿态到渲染提交已接通。现阶段支持内置原生枪口火光/烟配置及无参数 Minecraft 粒子。
- **通用 Snowstorm 运行时仍未完成**：自定义材质、曲线、组件、Molang、`pre_effect_script`、绑定实体/远端第三人称等不在此批支持范围。未知效果/脚本明确诊断并跳过，绝不以 vanilla smoke 冒充。不能据此宣称 1.2.0 全功能移植完成。

## 通用准备

1. 新建独立 26.3 Fabric 实例和测试存档；先备份配置、枪包和日志。不要在生产存档测试。
2. 安装同一次 CI 的 mod JAR 及其必要依赖。记录 JAR 文件名/提交号、Fabric、Java、显卡、渲染模组和语言。
3. 初测只留默认枪包、Cloth/Mod Menu；先不开光影和额外枪包。再分别加回 REI/JEI、Iris/光影和个人枪包，不能将基础环境通过算作这些环境通过。
4. 将仓库 `tools/player-acceptance-pack` 中的 `gunpack.meta.json`、`assets/`、`data/` 复制至游戏目录 `tacz/player-acceptance/`，重启。该目录是原创测试 JSON，只引用默认包素材，不覆盖默认资源。
5. 创造栏搜索“验收用 inventory”和“验收用 magazine”；或执行：
   `/give @s tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"port_acceptance:inventory"}]`
   `/give @s tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"port_acceptance:magazine",GunCurrentAmmoCount:3}]`
   若组件字段随版本变化，以创造栏取枪为准。7.62×39 子弹和子弹盒也从创造栏取得。
6. 每条留存：操作前后截图或短录像、`logs/latest.log`、实际测试配置副本。失败时同时记录最小复现步骤和是否使用光影。

## A. 枪模背包直读余弹

- 准备：清空背包其他弹药，拿 inventory 探针；绿色 `INV` 文字位于模型 root。必要时用检视或调整视角观察；不得只看 HUD 代替枪模文字验收。
- 操作：放入 17 发 7.62×39 散弹；加入装有同弹种 9 发的普通子弹盒；移走散弹；移走子弹盒；重新放入散弹并射击一次。
- 预期：枪模计数与 HUD 同步，依次 17→26→9→0；射击后按实际耗弹下降。不应一直显示枪物品自身弹匣的 3，不出现 `Format error`。计数允许短暂缓存刷新，不允许长期滞后。
- 对照：换 magazine 探针，背包里增减弹药不应直接改变枪模当前弹匣数字；给普通闭膛枪分别测试有/无膛内弹，膛内弹只加一次；副手/物品栏预览不得借用另一把主手枪的库存数字。
- 证据：同一画面包含绿色枪模文字、HUD 和背包；记录子弹盒弹种和数量。自动探针覆盖 17/26/9 与普通枪 3，但不替代中文/光影视觉矩阵。

## B. 动画粒子关键帧（部分支持验收）

- 准备：inventory 或 magazine 探针，默认第一人称，等待拔枪结束；确认粒子选项不是最少。
- 操作：不射击，按“检视”（默认 H）；重复、切枪、暂停恢复、退出重进；随后 F5 测本地第三人称。
- 预期：一次检视的 0 秒枪口 `end_rod`，0.2 秒枪口原生火光/烟，0.4 秒 root `happy_villager`。这些由动画触发，不消耗弹药、不产生弹丸；不应依赖先射击。切枪/重进不得重放上一局旧队列。
- 留存：至少 60fps 短录像，拍下不同定位组；记录重复检视是否一次对应一组效果。测试暂停时应随游戏时间冻结。
- 负例：在备份副本将一个 effect 改为 `port_acceptance:not_supported` 或添加非空 `pre_effect_script`；应在日志诊断并跳过，不能崩溃或偷换烟雾。恢复文件后重启。
- 边界：本地第三人称需实测；远端玩家、自定义 Snowstorm 材质/组件/脚本**未完成，不能验收为通过**。自动提交计数不是像素/材质正确性的证明。

## C. 枪包兼容提示及生命周期

- 准备：复制探针到另一个测试实例（不要同时装两份相同 namespace），把 meta 中 minecraft 从 `[26.3]` 改为 `[1.21.1]`，不添加 overlay；关闭“隐藏枪包兼容提示”。
- 操作：进单人世界，等待一秒以上；反复打开聊天；点击“说明”；点击“不再提示”；退出并重启游戏再次进服。
- 预期：提示包名、同一连接不重复刷屏；说明仅给出本地固定帮助文字；隐藏后不再显示且重启仍隐藏；不改变枪包启用条件、不自动修改玩家文件。
- 对照：改回 `[26.3]` 不提示；不匹配但有实际适用的 `compat_26_3` overlay 不提示；重新开启 Cloth 隐藏开关的反向设置后应恢复提示。
- 联机：专服放置不匹配测试包，客户端不放该包；进服应收到服务端包名。双客户端 A 隐藏不影响 B；断线换服不残留旧服提示。名字包含控制字符不得构成可执行聊天命令。未安装本功能客户端不应被无条件发送新载荷。
- 证据：聊天提示/两个按钮、说明输出、Cloth 保存前后、客户端 `tacz-common.toml` 的 `HideGunPackCompatibilityWarning` 与重启后的截图。自动测试覆盖真实 S2C 和本地 dismiss 路径；双客户端仍需人工验证。

## D. 必须回归的已交付功能

| 项目 | 操作 | 预期/证据 |
|---|---|---|
| 合法瞄具装卸 | 复测各 scope 标签与内置槽，装卸后重进 | 合法组合成功且库存结算一次；非法组合拒绝。不要把 39 个合法组合测试解释为任意枪包兼容。 |
| 热成像/弹道计算镜 | XM157、XL50，目标与墙后目标，经典/PIP 各一次 | 可见目标与遮挡正确；录像保存镜内外对照。Iris 另测。 |
| 子弹存储界面 | 中文/英文、原版/REI/JEI 分开打开，切换 GUI 缩放 | 边框连续、标签不压槽位、拖放/数量正确；各环境独立截图。 |
| 配置保存 | Cloth 修改弹药五项/其他控制，保存后重启 | UI 值、CLIENT/COMMON TOML 与实际行为一致；取消不误写。 |
| 反馈与场景返回 | 真实受伤、命中、爆炸，退出再进/跨世界 | 反馈不残留；镜片/库存/粒子状态不串局；记录伤害来源。 |

## E. 可选依赖：证据与阻塞，不记作已完成

本轮核对的是公开 GitHub 源码/发布记录，不是对所有托管站的全量扫描。当前环境只允许有限互联网主机，不能核验 RyanHCode Maven / Modrinth 的实时 26.3 元数据。

| 集成 | 已核对证据 | 当前结论 |
|---|---|---|
| Sable | `ryanhcode/sable` main 的 gradle.properties 为 MC 1.21.1、Java 21；近期 release 标签 mc1.21.1-2.0.6-fabric/neoforge；上游使用 sable-fabric-${minecraft_version}:2.0.6 | 本仓库没有经验证的 26.3 依赖/API；不硬链旧版，不宣称兼容。 |
| Simulated | 上游 compileOnly 使用 dev.simulated_team.simulated:simulated-common-1.21.1:1.3.2，来自 RyanHCode Maven；API 为 SimBlockMovementChecks | 公开搜索不能证明不存在新版；当前未取得可验证 26.3 artifact，属于依赖未核实阻塞。 |
| EMI | emilyploszaj/emi 的 latest release 1.1.24+1.21.1，仅 1.21.1 Fabric/NeoForge 资产 | 没有本轮可用且验证过的 26.3 运行时；REI/JEI 通过不能替 EMI 通过。 |
| Accelerated Rendering | Origin-Technology/AcceleratedRendering-Fabric main 为 MC 1.21.1 / mod 1.0.0；上游编译依赖 accrelatedrendering-refabricated:pgm8CkON | 26.3 SubmitNodeCollector 渲染路径尚无经过验证的对应适配；未完成。 |
| 通用粒子引擎 | MCModderAnchor/SimpleBedrockModel 1.21.1-dev 含 particle/data/component、Molang、firstperson/world/render 完整子系统 | 不是增加 JSON 解析器就能替代；需要独立移植/依赖升级及测试，不应当计为纯依赖阻塞或已完成。 |

源码入口：
- https://github.com/ryanhcode/sable/blob/main/gradle.properties
- https://github.com/ryanhcode/sable/releases
- https://github.com/emilyploszaj/emi/releases
- https://github.com/Origin-Technology/AcceleratedRendering-Fabric/blob/main/gradle.properties
- https://github.com/MCModderAnchor/SimpleBedrockModel/tree/1.21.1-dev

后续工作顺序：先拿到 26.3 依赖坐标并核验 loader/MC 范围，再编译对应 API 分支，再建可选运行时 CI；物理世界需验证移动子世界弹道/装配方块对，渲染需验证缓存失效、粒子混合和 Iris。不能为了表格填满而安装 1.21.1 JAR。

## 自动验证记录（不替代以上人工矩阵）

测试源码 b07f94c：完整 build [44368846181](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44368846181)、compile-check 44368846184、viewer runtimes 44368846315 全部 success。包含外置 ZIP 加载、PAPI/HUD 17/26/9 对照、普通弹匣3、实际检视粒子提交、S2C提示和本地隐藏命令；旧39镜安装和配置 TOML 等门禁也通过。新截图保存在该 run 的 smoke artifact，本轮下载遇 EOF，未人工目视复核。过渡混合期间事件当前跳过，不计为完整动画粒子支持。


## F. 本轮补充：Iris 光影热成像人工验收

准备：同版26.3修复JAR、Iris1.11.6、Sodium0.9.2。先只用默认包与光影；再加入用户的 ComplementaryUnbound_r5.9.3 + EuphoriaPatches_1.10.5、DH和外部包。每组分别记录，不能合并结论。

1. 默认M700装XL50，确认 `EnableExperimentalScopeLens`、`EnableScopeThermal`、`ScopeMaskEnable` 开启（默认true）。准星对准无遮挡牛/僵尸，观察热目标和冷地形。不开PIP也要有效。
2. 在目标前放实心墙：墙后不能留下热轮廓；拆墙恢复。撤销瞄准、切枪、F5、死亡/重入不得残留全屏或旧帧热像。
3. 开PIP重投影重复；再开真二次渲染重复。目标移动时热mask与画面一致，不能放大两次、透墙、漂移或只出现光学底图。
4. 游戏内关闭光影，再开启；重载资源、缩放窗口。热像应恢复，镜外不得灰化，遮光环和文字/准星不应被后置合成覆盖。开关热成像本身时只改镜内效果。
5. 如发生采集异常，应看到一次黄色聊天告警与 `latest.log` 堆栈，不再无声当作“支持正常”。这属于失败降级，不属于功能通过；保留日志提交。

留证：镜内有目标/遮墙/拆墙三张图，PIP各档、光影关闭/恢复录像，实际配置及日志。CI的最小shader pack不含Complementary/Euphoria的TAA、抖动和自定义深度写入，因此该用户组合仍须独立验收。
