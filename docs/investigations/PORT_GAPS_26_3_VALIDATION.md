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

## 还要以实际消费者判定的项目

- **输入与展示：** 用提示所显示的 Interact（非枪物品用原版使用键）放入。`ae8f1d9` 的[普通客户端][rack-failure]在等待生存手变空时超时；`043fba5` 已捕获原因：客户端和服务端均未收到 use，当前 GUI 是原版 `FriendsListConfirmScreen`，默认 O 与好友列表冲突。改新默认 K，保留用户已有绑定；不退回直接改 BE 来变绿。分别检查方块白名单、按键派发、两端潜行状态、客户端预测、服务端库存、拐角第二面和普通物品的不透明／半透明。
- **粒子与曳光：** 六种材质不能合并为“有东西提交过”。要有同纹理、两种背板、cutout 对 alpha/blend 的像素对照；手／世界、镜内／外、俯仰／roll、普通／Iris 各自有边界。[旧 Iris 基准](../../build-reports/visual/44425974217-tacz_amg10_particle_baseline_2.png)和[注入后](../../build-reports/visual/44425974217-tacz_amg10_particle_2.png)是 **失败样本**（zoom 2 的中心正对照为零）。碰撞碎屑按实际 `BulletHoleOption` 的子调用区分于服务端另外发送的熔岩；单纯收包不是效果数量证明。曳光要在玩家开火后同一实体的多个真实世界帧看模型位置，不只看保存的四元数。
- **菜单和 API：** 对六件物品真实悬停（两种语言），对照应该有／不该有的行。创造栏四类物品按 namespace 及 sort，在打开的界面重载后再查；公开 variant、slot-adapter 全集应和逐 ID 查询对应。不能只数翻译键。
- **§3 取舍：** 保留测距 1,024 格、标记倍率 32、模拟 600 tick 及实体寿命上限；超预算或不可达不画，不能用解除上限来追求源码相似。普通发光骨骼的环境 sky 与面法线是两件事，需与方向光和遮挡对照，不能用准星满亮代替。格线遮挡、PAL 离屏/再入镜和大场景 CPU/GPU 性能仍需分开测。转换器对不可证等价的条件仍须报出，不静默改包。
- **外部组合：** Sable/Simulated、独立 EMI 插件、Accelerated Rendering 都没有在本轮获得可验证的 26.3 运行组合；AR 仍是禁用，不写成已修。未找到可验证组合不等于不存在。KubeJS 不把目标 Fabric 上游已注释的事件投递算作已运行能力。其它 26.X 线的欠账保留在原审计。

[pad]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44427465994/job/135236056454
[rack]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44425974348/job/135230940163
[input-failure]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44427465913/job/135236058051
[rack-failure]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44427465932/job/135235909898
[server]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44428762294/job/135240376511
[viewers]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44428762236
[iris]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44428762277/job/135240376445
