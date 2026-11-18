# Fabric 1.2.0 → 26.3：差异与移植账本（2026-11-17）

**不是 release notes。** 目标是同等玩法/内容包语义，不是把 1.21.1 的 jar、mixin 或资源目录直接覆盖上来。阶段门禁未全部通过前，保留本线 1.1.8 SemVer 核心；不要放宽 `dependencies.tacz` 冒充 1.2.0。

## 固定比较对象

- 旧端：[Sh1roCu 1.21.1 `d290355`](https://github.com/Sh1roCu/TACZ-Refabricated/commit/d2903554da039d2355920953a81447784a3f2be2)，`0.7.0-forge1.1.8-hotfix`。这是 [上次同路径审计](UPSTREAM_PARITY_AND_TODO_AUDIT_2026_08_12.md)的参考树，不是说本仓逐字节等于它。
- 新端：仍是 **Fabric 1.21.1**；`f4bde52` 升 1.2.0，`271d692` 同步 hotfix，`828fbab` 修 build，固定到含 ocular 修正的 [`356bdf9`](https://github.com/Sh1roCu/TACZ-Refabricated/commit/356bdf9d48f2225bac6ceb56adc5576a418517a8)。上游 `gradle.properties` 实际标识 **`1.0.1-neo1.2.0-hotfix+mc1.21.1`**，不是名为 `1.2.0` 的 GitHub release tag。[源码比较](https://github.com/Sh1roCu/TACZ-Refabricated/compare/d290355...356bdf9)。
- 本端：`26.3@cc990c3`，MC 26.3 / JDK 25 / 无 remap / Fabric API 0.160.7。三树而非两树比较，避免把 LRTactical、弹药源 API、TML、PAL、Iris/PIP 等本地工作误判为上游删除项。

按 Git `-M50%`（rename 算一项）共 **2,658** 项；Java **+25,062 / −3,167** 行。以下只比较相同目标路径的 blob：缺失≠缺功能，尤其注意资源目录单数化与跨包重命名。

| 上游变更 | 本端已等于目标（含已删） | 仍等于旧 blob | 无目标路径 | 另有改写 | 总数 |
|---|---:|---:|---:|---:|---:|
| Java | 0 | 44 | 223 | 232 | 499 |
| 内置默认包 | 2 | 727 | 1,260 | 5 | 1,994 |
| 其它资源 / loader 元数据 | 0 | 0 | 117 | 39 | 156 |
| 工具 / 构建 / 文档 | 1 | 0 | 3 | 5 | 9 |

可复跑，不依赖 compare API 的 300 文件截断，也不必切换工作分支：

```sh
git init --bare /tmp/tacz-upstream.git
git -C /tmp/tacz-upstream.git fetch --no-tags --depth=1 https://github.com/Sh1roCu/TACZ-Refabricated.git \
  d2903554da039d2355920953a81447784a3f2be2 356bdf9d48f2225bac6ceb56adc5576a418517a8
python3 scripts/audit_upstream_120.py /tmp/tacz-upstream.git --local cc990c3
# 加 --paths 输出全量 TSV，或省略 --local 比对当前提交。输出只用于定位，不自动复制。
```

## 依赖裁决

依据为上述两棵树的 `build.gradle`、`fabric.mod.json`、`libs/` 及实际 import；`modApi`、`modLocalRuntime` 与玩家硬依赖不是一回事。

| 上游差异 / 使用点 | 本线选择 |
|---|---|
| SimpleBedrockModel **2.3.0.1 → 2.6.3**，上游 flatDir 1.21.1 mod jar，含 external/static gun mesh、坐标变换及 `AnimationClocks` | **不装旧 jar**（[SBM Fabric 分支](https://github.com/Sh1roCu/SimpleBedrockModel-Fabric/branches)为 1.20.1、1.21.1）。外显模型/时钟按实际调用在 RenderPearl / collector 及 pause 生命周期上改写；本地两个 IFP 接口不是完整 SBM。 |
| MAE **1.1.1 → 1.1.4**，上游普通 Java `implementation`，其 SBM jar 又嵌 `META-INF/jars/mae-1.1.4.jar` | 本端没有外置 SBM，沿用**一份** MIT nested MAE，不引入同包桩；验证坐标、编译及产物内版本。 |
| **EMI 1.1.24+1.21.1** API + dev runtime；JEI/REI 代码重组为 jrei，增加正反向查询与 transfer | EMI 旧 artifact 不适用，未确认 26.3 API 前不注册入口。[EMI 源码](https://github.com/emilyploszaj/emi/branches)未见 26.3 分支。保留本线 JEI 31.8 / REI 26.3、Ammo Query 和收到服务端 cache 后的刷新；查询方向与工作台 transfer 需要分别补，不重命名包来假装同步。 |
| **Sable 2.0.6** (`modApi`)，`SableCompat` 在子世界射线、附着、显示姿态走 mod 检测；**Simulated 1.3.2 common** 仅 compileOnly（上游注明等待 Fabric），用于多格装配检查 | **不作硬依赖**，无经验证的 26.3 Fabric API 组合就不上 mixin、不声明支持；维持 vanilla 世界逻辑，日后同时验证缺席加载和装船/移动语义。仓库 URL 或 common jar 不等于目标 MC 的 Fabric 构件。 |
| KosmX、SSR、Cloth、Mod Menu、加速渲染等转 compileOnly + localRuntime；CCA 仍是 1.21.1 依赖 | 保留本线 PAL（非 KosmX）、26.3 SSR、FCAP、实体数据/出生包、Iris HAND 掩码及 TML；不回灌 CCA、intermediary/refmap、旧 OpenGL/Immediate buffer 或 AR 库。Cloth/Mod Menu 不变成玩家新硬依赖。 |
| Loom 1.11 → 1.16.3，Mojmap+Parchment；假人、键位、Flashback/lattice 等 dev-only scope 整理 | 保留 1.17 / Gradle 9.5.1 / 无 mappings；dev 工具不打进成品。LuaJ 3.0.8-figura、BCEL 6.6.1、commons-math3 3.6.1 上游未升。 |

## 分批门禁（有消费方、有测试再打勾）

| 批次 | 源码范围与本端接点 | 完成判据 |
|---|---|---|
| A 包发现/同步 | `PackMeta`、MC 版本 overlay、Maven/Fabric 范围、gzip 分片；保留旧包 recipe/lang 兼容、LR 版本与 TableRecipeManager | 目录与 zip 双侧资源、重载关闭句柄、同名覆盖一致；超单包快照只安装一次、换服清分片、保留 JEI/REI 刷新 |
| B 数据和玩法 | built-in/虚拟 OEM、variant/slot adapter 的 data/index/stack/refit；modifier、Lua attachment script、卸弹与 finish-reload/fire-post 事件、时钟/暂停、AI 声响、进度 | 不把脚本下发；服务端权威检查、联网/旧存档回归；不得绕开本地弹药源或具名 hook |
| C 展示/交互 | 弹药存储块/升级、枪柜/挂板/染色、BE 持久化、菜单和网络校验、creative 筛选、查询和工作台 transfer | 26.3 ValueInput/Output、item component、loot/schema；放置→存取→掉落→重进及专服，验证 Carry On 边界 |
| D 客户端 | ADS 曲线/预设、音效对象、首/三人称动画及粒子、外显模型、测距/热成像、ocular 膨胀 | 保留原 26.3 默认渲染与可选 PIP、Iris、PAL/YSM；无光影截图或光栅模拟只证明相应路径，不能当作 Iris/GPU 矩阵 |
| E 转换器与内置包 | 1.20.1→通用包转换 + `compat_1_21_1`、平移至 26.3 对应格式；脚本/索引/模型/音效/配方一一验证 | 转换到新目录，源包不变。默认美术依原授权与署名、单独追踪来源（[许可说明](../../LICENSES.md)）；不混入旧 loader jar、不先覆盖默认枪包 |
| F 切换基线 | 上述必选行为已消费、缺省第三方项有明确边界，再升 SemVer 与元数据 | `build`、无可选 mod 的专服启动/重载、单机/联网、渲染与旧包矩阵；README/许可/manifest 和 `--strict` 一致 |

## 已落地的切片与证据

阶段表是门禁，不是完成清单。以下 SHA 均为本次 **26.3 工作分支**；专服 GameTest 是 Loom 开启的真实游戏进程，不等于公网联机测试。JUnit、源码检查、进程启动、持物截图和人工画面判读分开计。

| 实现 | 验证范围 |
|---|---|
| `f737337`：新旧包元数据、双方独立的目录/ZIP overlay、优先级与句柄所有权；保留旧 recipe/lang | [构建](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44199536128)，`GunPackOverlayTest`。不把版本范围匹配等同实际懂得包里的内容。 |
| `a7d7e70`：重载后有界压缩快照/分片、旧协议协商，安装完成再重建索引和请求配方查看器刷新 | `GunPackSyncTest` 和接线检查；没有拿源码接线代替 JEI/REI 实际联机显示。 |
| `a41b95a` / `3d836d0` / `8fe9e5a`：附属脚本修饰符和标签、后坐推力；ADS 曲线/两套预设与独立双 FOV；声音字符串/对象与每次播放取样 | [`8fe9e5a` 编译](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44201061900)通过。[全量工作流](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44201061777)的 `Full build` 与 `Verify server smoke and nested MAE` 步骤通过：跑过 JUnit 和专服，找到 26.3 ZIP 覆盖标记、`tacz:ak47` 及恰好一份已注册的 MAE 1.1.4。客户端步骤 **12 分钟超时**，总体为失败；Checks 可见 Render thread 启动，未证实进入世界/出帧。首次专服缺少 server.properties 不是这个超时的根因。不是实机听感或 PIP/Iris 验证。 |
| `e870a74` / `49bc480`：虚拟 OEM 与真实物品区分；variant/slot adapter 从 data/index、协商、存档、改装到 collector 模型与瞄准取样 | 新增 `port_test:oem` 测试包。覆盖物品组件读回、转接座必需时拒绝拆下，以及客户端收到变体后用其倍率/模型；不在成品里打包测试数据。 |
| `1a1e8b7`：`force_aim_exit_on_reload/bolt` 双侧判断；开火后与换弹结束 Fabric 事件 | 发射者本地预测不再额外接收 FIRE 通知；只有结束换弹且状态已更新时通知自身和旁观客户端。新增服务端结束转移测试；不声明 KubeJS 支持。 |

仍不能用本表宣称 B–F 完成：例如主动卸弹与暂停时钟、新方块/菜单与 transfer、外显动画/热成像/测距、转换器和默认内容迁移仍各需其消费方、许可记录与回归证据。发布版本保持 `1.1.8+fabric.26.3.R1-hotfix3`。

### 第二次服务端/客户端门禁

`ae2c879` 的主源码编译通过，但 [build](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44203224937) 在 `compileGametestJava` 提示测试用了旧 `EntityType.HUSK`，那次没有运行服端断言。`e8b7616` 改用 26.3 `EntityTypes`： [build](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44203594821) 的 Full build、专服 overlay/默认包和 MAE 验证通过，包含 OEM、转接座移除拒绝、变体序列化和 finish-reload 转移用例。**客户端仍失败**：105s/285s 两份 render thread 栈都停在 `SDL_ShowSimpleMessageBox → MessageBox.error → Minecraft.<init>`；最新日志明确报 Vulkan 缺 `VK_KHR_surface`。不是进入世界后的倍率/模型断言失败。CI 改为固定 Ubuntu 24.04、安装 Mesa Vulkan 软件驱动、在 Xvfb 内预检 CPU ICD 与 WSI，测试专用 options 选择 Vulkan。不据此宣称 Iris 或硬件渲染通过。

后续切片：`353ad0d` 主动卸弹/回收、客户端定时任务与本地暂停时钟，`3c87a2c` 目镜子树 bake 与共享配件 mesh 加载，`b4a5557` 进度判据、`fdcda33` 成功开火后的原版生物声音反应。新增脚本回收/弹药箱、虚拟备弹上限、旧 AmmoSource 零接收和时钟/取消测试。**上述 e8b7616 的结果不覆盖这些提交。**

### 26.3 判据与客户端取证

`3ea742f` 在主源码编译失败：判据仍引用旧 `ContextAwarePredicate` / `EntityPredicate.ADVANCEMENT_CODEC`，收枪声音残留旧异步 accessor。`641ac67` 改用 26.3 的 `Optional<Holder<LootItemCondition>>` 和对应 codec，保留 player 谓词，并在所属客户端线程播放声音。[compile-check](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44205735081) 与 [build](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44205735097) 的 Full build、专服 GameTest 和嵌套 MAE 步骤通过，已覆盖脚本卸弹、备弹上限、旧弹药源、进度文件加载及单测。此次 Mesa 预检通过、集成服已启动并保存世界，不再卡在启动对话框；**客户端步骤仍失败（255）**。Checks 中可见 `flite` 缺失，不能据此认定它是终止原因；没有从 artifact 核实两帧。增加缺失的运行库、阶段标记及 crash-report 摘要以定位下一次失败。

### 弹药库、查看器与首个双帧门禁

`35901c8` 的 compile-check 和 Full build 均止于主源码编译，未执行方块测试。`221d54e` 纠正 26.3 block、菜单尺寸、tooltip 和配方 stream codec；[Full build / 专服](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44211747065)通过。`AmmoStorageSmoke` 实际查找 Fabric SIDED、回滚嵌套事务、区分具名组件；由掉落表取物，查找两级真实配方，放置后取出原有 73 件。客户端已保存默认帧，但切到测试变体时因 `RecoilModifier` 解引用缺省轴而崩溃，非 Vulkan 初始化问题。

`664ac54` 让 JEI/REI 的工作台跳转只在当前菜单排队选中有效配方；服务端合成、材料和旧包同步通道不变。`52fcd92` 对省略的相机关键帧轴使用零基值。`808c45d` 复用同一批已验证的物品关系生成双向查询，弹药反向和每类配件按 54 件分页；弹药库升级由真实配方提供 3×3 原版 display，而不是复制合成逻辑。该 SHA 的 [compile-check](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44212555153) 与 [完整 build](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44212555050) **均通过**：JUnit、专服（含跳转只选一次/不可跨台、共享查询/55 项分页、库存和升级）、单份 MAE 1.1.4、软件 Vulkan 集成世界、持有默认与变体物品、派生脚本能力/倍率/模型断言及两份非空 PNG。工件 `smoke-808c45da8c47719ed1ab504db4ee66beb322b894`。文件产生不等于截图已经人工判读，菜单断言也不等于已安装两种查看器联网操作。

`819a1d2` 创造栏侧栏的 [compile-check](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44213002226) 与 [完整 build](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44213002082) 已绿。集成世界切创造模式，真实打开 rifle tab、点击 `port_test`，核对隔离结果与注册 tab 未缩小，再点 All 恢复。此 SHA 不含后续展示墙，亦未覆盖所有 tab、可选查看器或实际服务器重连。
