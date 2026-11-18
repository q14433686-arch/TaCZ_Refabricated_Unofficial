# Fabric 1.2.0 → 26.3：差异与移植账本（2026-11-17）

**不是 release notes。** 目标是同等玩法/内容包语义，不是把 1.21.1 的 jar、mixin 或资源目录直接覆盖上来。阶段门禁未全部通过前，保留本线 1.1.8 SemVer 核心；不要放宽 `dependencies.tacz` 冒充 1.2.0。

**最近通过整套门禁的源码：[`e92a998`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/e92a998a576fe909ac68c34b78a8c46bc1ac8d95)。** [编译](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44244608818) · [完整构建 / 专服 / Mesa 客户端](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44244608846/job/134600441865) · [测试 JAR](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44244608846/artifacts/14401960049) · [日志和原始截图](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44244608846/artifacts/14401181401)。本次新增 XL50 的镜内偏色、白热实体、遮挡和重投影倍率对照；不是所有瞄具、光影、独显或独立多人的验收。下文按提交顺序保留较早阶段结果。

**按玩家操作查状态**：[玩家可感变化清单](FABRIC_120_PLAYER_CHECKLIST.md)（射手位移、枪口火烟、枪声引怪、移动、命中、改装和界面）。**按工程路径查**：[全量对比与当前门禁](FABRIC_120_COMPARISON.md)（含未变化与本地独有文件）。以下历史表不随新提交回写。

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

### 展示柜 / 挂板

`d94bd3e` 的生产源码通过了编译，但测试用了不存在的 `Items.BLUE_DYE`，未运行 GameTest。`2f4c627` 改为 `Items.DYE.blue()`；[完整 build](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44214227400) 与 [compile-check](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44214227379) 均通过。专服真实方块、物品组件、原版染色配方/掉落、联锁、存取、镜像、普通拆除与“保存 BE → 先移除 BE → 移除方块 → 回填”的不重复掉落验证通过。集成客户端验证挂板 RGB/格/角度和柜内物品同步，朝向陈列墙并取得非空 `tacz_display_wall.png`。工件 `smoke-2f4c627baa68a68e9d8868dfb75add7e236311d8`。搬运顺序来自 [Carry On 26.3](https://github.com/Tschipp/CarryOn/tree/26.3)，不是带该 mod 的实机矩阵。未作人工画面比对或声称移植了旧 GL VBO 加速器。

### 转换器首轮

`83d99a3` 的 [compile-check](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44214829649) 和 [build](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44214829949) 通过。专服 `PackConversionSmoke` 实际执行 DFU、用该服 registry 校验、保留 1.21.1 overlay、写入 26.3 overlay 并读回 ZIP；检查 typed 结果的 AmmoId、自定义 byte、嵌套配件、部分谓词、缺失 ID 和禁用标记、重复导出不覆盖。另有真实加载的 Fabric true/false 工作台配方。该 SHA 的集成客户端仍是先前的持物/展示墙门禁，**没有安装导出的 ZIP 再重载**。命令与输出边界：[转换说明](../GUNPACK_CONVERSION_26_3.md)。

`da48051` 增加原生旧 `conditions`、顶层石切 `count` 及无等价标签空值测试的保护。可复跑的 `ConversionReloadSmoke` 在活跃集成世界内生成旧包 → 导出 ZIP → 放进测试实例的 `tacz/` → 重新发现包并执行实际 `server.reloadResources`；等待客户端收到新快照，核对原版 registry 只留下两份所需配方、工作台 typed/custom 结果的 ID/数量/byte 与禁用配方不回退。[build 44215505004](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44215505004) 与 [compile-check](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44215504991) 通过。测试的是合成夹具，不是所有第三方包或物理双端。

### 默认弹药 / 命中表现

`b1e1474` 的 [编译](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44216061928) 和 [build](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44216061927) 通过。115 份资源保留上游字节，1 份 `.300 BLK` 工作台配方用 26.3 原生材料格式；`verify_120_resources.py` 查了累计 203 个哈希、25 份弹药索引的引用。专服断言新增配方结果，集成客户端解析弹壳/烟尘 display，真实调用两种 display 的 impact provider（弹孔开/关）。保留原粒子包协议和六面朝向，额外粒子与声音独立于贴花；不是人工听感、燃烧特效逐帧比对或完整默认包迁移。

### 外显骨架的 26.3 实现

`464b13a` 的 [编译](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44216851071) 和 [build](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44216851054) 通过。`GunRigBaker` 统一原生与 TML 模型工厂，外显高模与手持动画的可变节点不共享，远距使用独立 LOD。`DisplayBake` 在提交时不等待后台任务，未就绪用已加载的另一档或图标；重建索引后不再取旧结果。`ExternalRigSmoke` 用含三角网格的测试包核对三套骨架、两档材质和柜上实际 FIXED 提交，数据重载之后旧 display 不再返回模型。这验证的是软件 Vulkan 和本线 collector/TML，不是 SBM、Iris 硬件矩阵或性能测量。

下一份默认内容为 QR-24、AFG/Troy 握把和 5 个转接座；仅同步 16 份原列表除新增 adapter 标签外不变的旧枪 allowlist，其余旧枪随各自的附件/标签一起迁移。新增 transfer/schema 走法：**带 `index/slot_adapters` 的默认包需要支持扩展快照的客户端**；不能把新枚举发送给旧客户端。没有扩展字段的小包仍保留旧同步路径。该切片不把 `muzzle_flash.type: renew` 的旧四边形回退冒充 Snowstorm 粒子实现。

`095f592` 的 [编译](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44217511834)和 [build](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44217511851) 均通过。专服用实际配方得到 QR-24，并解析五个转接座；集成客户端持有该枪，解析 Lua 状态机、模型、ADS 和转接座偏移，真实发送 C2S 请求，验证不兼容的瞄具不被消耗、安装依赖的座后可安装、不能拆掉仍需要的座、握把和背包状态已同步。累计 300 个哈希；截图和日志见该 run 的 `smoke-095f592…` 工件。不是公网双客户端，也不以截图存在证明每个姿态都正确。

### 镜内测距文字

UH-1 的 `text_show` 经 26.3 第一人称 extraction 读取实际游戏方块距离，文字沿用现有遮罩/原版文字回退，不在渲染提交时再查世界。`%rangefinder%` 只在含占位符的文本里计算；配置按节点区分，默认 512 方块/2 游戏 tick、未命中为 `---`，读数上限 1024 方块。实体查询仅扫描至首个可碰撞方块，分段并复用同一刷新周期的结果。`RangefinderSmoke` 的观察器**仅在测试模组**，用于核对持物开镜时远、近两块遮挡物对应的文字。此切片只处理读数，不含落点标记、热成像或光影兼容结论。

### 暂停与重开同一存档

枪械逻辑和动画脚本现在都分别暴露玩法时间与真实日期（见[脚本里的时间](../GUNPACK_CONVERSION_26_3.md#脚本里的时间)）。动画 runner 的姿态/仅音频两条更新路径在连接代次变化后都重设采样起点；旧收枪期限、旧弹壳和晃动插值不再按新连接的经过时间推进。空 `sound_effects: {}` 不应让整个 Bedrock 动画文件加载失败。

新增门禁：集成服使用暂停界面，跨客户端 tick 检查冻结/恢复和两种 LuaJ 宿主的时间；退出到标题，再用 `TestWorldSave.open()` 打开**原存档**，要求新的服务端快照、保留含配件的物品和两块陈列，以及一次本地/服务端事件和一次生存菜单的具名库存转移。它仍是一个进程里的新集成服，不是同时在线的旁观者，也不证明所有历史存档或跨维度切换。

`d05637d` 的独立编译、Full build 和专服步骤通过；该次运行在安装 Mesa 测试依赖时被新推送取代，**没有客户端结论**。包含上述时钟修正及测试的 `bbc03b3` 已通过 [compile-check](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44220410704) 和[完整 build](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44220410616)。此次 Mesa 客户端实际观察到 UH-1 的远、近遮挡读数随世界变化；暂停五个客户端 tick 再恢复；重开原存档后，本地与服务端各收到一次开火后事件，弹药库中的 17 件具名弹药转入玩家背包，3 件普通弹药仍在库中。日志和截图在 [smoke 工件](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44220410616/artifacts/14390991870)；未做像素判读或 Iris/独立双客户端验证。

### 默认包全量清单与运行时门禁

`955ebc4` 不再按示例枪分批复制：从同一 pin 解析 4,176 个有效路径，其中 229 个采用组件时代的 overlay 对应物，再转换成 26.3 会实际读取的布局与字段。每个成品路径的来源、blob、转换分类与 SHA-256 见 [`DEFAULT_PACK_120.tsv`](../../DEFAULT_PACK_120.tsv)。`python3 scripts/verify_120_resources.py` 可离线复核；`python3 scripts/port_default_pack_120.py /tmp/tacz-upstream.git` 另从固定 Git 对象验算，不加 `--write` 不落盘。[246 个上游缺口](DEFAULT_PACK_120_SOURCE_GAPS.tsv)保留了精确引用者；不能把可选 LOD、声音回退和真正缺失项混称为 246 个运行错误。

[编译](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44226492723)和[全量工作流](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44226492728)的 Full build、专服 GameTest 和单份 MAE 检查已通过。专服断言覆盖 70 把枪、126 个配件、25 种弹药、5 个转接座、3 个块的索引，以及逻辑脚本引用、23 个进度、202 个工作台配方及其材料。同一次客户端 GameTest 也通过（03:06:47 UTC）：逐个打开 70 把枪的主模型与状态机，并核对 M1897 的专用弹壳材质。工件：[JAR](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44226492728/artifacts/14394185834)、[日志/截图](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44226492728/artifacts/14393473145)。渲染全部外观、逐动作脚本、`renew` 粒子、镜片/热成像或第三方包行为，均不能从以上计数推导。

### 服务端脚本动作与旁观事件

以一次 draw 为边界隔离 LuaJ 异常；错误清空换弹/拉栓/待执行近战与脚本临时值，但不重置射击冷却，不把部分已执行的脚本当成可回滚事务。延迟 Lua 回调和同一轮的后续 cycle 还必须属于原 draw、原主手物品实例；不改旧包的 `runLuaCycleTask` / `runShootCycle` 接点。先投票再提交本地开火效果；取消触发不再挪动弹药。`inventory_cooling_per_tick` 在服务端的未选中物品上生效，主手仍走原 `tick_heat`。

新测试分别覆盖非法 Lua 返回值、嵌套即时回调失败、换枪后旧定时器、库存冷却，以及一个真实追踪客户端收到**其他实体**的成功事件。后者也在同一连接上撤销单项 Fabric 接收能力、等服务端协商生效再检查旧事件；不是使用历史客户端 JAR，也不是两个独立客户端。通过与否以包含这些测试的源码 SHA 对应工作流为准。

创造栏：枪包索引重建时使 vanilla 的标签页缓存失效；下一次打开或已打开界面的下一 tick 由原版一同重建分类、名字与 tag 搜索树。集成测试在同一界面输入唯一查询，转换并挂载含新索引的 ZIP，等待同一查询出现新物品，再检查跨分类的包选择与「全部」。测试不向已注册分类写入筛选结果。

上述动作与创造栏的 [3755f96 编译](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44228548893)、[服务端与客户端 GameTest](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44228548923)已全部通过；客户端门禁同时检查 `PORT_ACTION`、`PORT_CREATIVE` 和 `PORT_CATALOG`，证据工件 `14394965034`。追踪的是另一个实体；撤销的是这条连接的一项接收能力，不是历史版本握手测试。

### 默认 Lua 装填的库存守恒

对实际引用的五个逻辑脚本（M1866、SPAS-12、M1014、Kar98、Mk23）增加 `native-reload-accounting` 来源分类。它们的装填时序沿用包配置，但不再假设「请求几件，弹药源一定供应几件」。`loadMagazineFromReserve(n)` 按剩余容量向原生弹药源或虚拟备弹取用，返回**实装数**；`loadChamberFromReserve()` 只在能装入时返回 true。保留旧 Lua 方法（`putAmmoInMagazine` 仍返回**溢出数**），不把第三方脚本重新解释成这种新语义。空仓 Mk23 与不带镜的 Kar98 在一次 tick 跨过装填与结束两个时间点时，也只结算一次。可审查的 Lua 差异见 [`default-pack-120-reloads.patch`](../../scripts/patches/default-pack-120-reloads.patch)。

`fa23a51` 的专服 `DefaultReloadSmoke` 通过：五份上述逻辑加一份未改写的 HCAR xmag 逻辑，每份各跑 6 个装填场景（生存空仓与战术的零/部分供给、虚拟备弹、创造）。测试用实际 LuaJ 和物品组件，跨过多个供弹时间点，检查已装总数、来源扣量与结束后再次 tick；不是逐帧动画比对。当前 4,176 个内置包文件中，3,929 个与固定 blob 逐字节一致，另外 5 个脚本单列 `native-reload-accounting`，其余适配分类见清单。

### XM157 与查看器：区分命中、提交与实际画面

`64ae477` 修复手部提交时漏乘 ModelView 导致的 XM157 标记剔除。[实体、负对照、恢复与遮挡的原始帧，以及 CI 和失败诊断](FABRIC_120_COMPARISON.md#本轮分块记录)可复核：默认配置有 12 个黄色像素构成中央空心框，不把测距文字算进去。`44f0733` 的 JEI 和 REI 也分别通过了重载前后双向查询、真实页面保持与像素对照。先前 JEI 文件只有世界的一帧不能作为页面证据。上述运行用软件 Vulkan，均不是硬件/光影或异进程联机验收。

### XL50：测倍率、偏色和可见热源，而不是看截图是否灰了

从 `f8020ae` 起，镜片绑定到实际提交的目镜。`e92a998` 的[完整运行](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44244608846/job/134600441865)检查默认包允许的 M700 + XL50、固定相机的 13 张游戏截图和旧存档重开。1× 对照红条宽 **7 px**；在 6× 档，经典变焦的原色/光学都是 **45/45 px**，重投影 PIP 是 **38/38 px**（阈值化的条宽，不是两种采样逐像素相同）。移墙后分别有 **2,559 / 2,499** 个与第一次可见时重合的白热点；放墙或同时把牛加入黑白名单后，这些位置通过冷像素门禁。镜外色样及原始帧见[像素对照和边界](FABRIC_120_COMPARISON.md#原生镜片与热成像)。完整 run 也要求重进后收枪期限失效、新快照、恰好一次动作与库存守恒；没有在测试里重设时钟。`aac6223` 的 [JEI / REI 独立运行](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44243966031)亦通过。未升级版本、未发布。


### 批量玩法移植：先记录实现，不预报游戏通过

本轮 `f0161ea` 添加真实生存连接的 `ShotConsequencesSmoke` 和枪声激怒的 Cloth 入口；`f56820d`/`2482635` 接入空手趴下、姿态所有权、可配置趴姿角度、可选空中腰射散布，以及命中前的同队/实体列表过滤、爆炸免伤、可破坏和可穿透方块 ID/tag、硬度上限。配置重载重建方块缓存，tag 键保留动态匹配。默认空中腰射、同队穿透、实体免疫列表均关闭。

测试分为服务端 `BulletRulesSmoke` 和集成连接 `ShotConsequencesSmoke` / `MovementRulesSmoke`，客户端 runner 要求结果日志。前者调用真实击中处理但不是飞行子弹；射击测试调用默认物品服务端 shoot（包括 Lua），不伪造速度包/激怒回调，也不声称验证键鼠开火链。尚缺矩阵见[玩家清单顶部](FABRIC_120_PLAYER_CHECKLIST.md)。本轮尚未改 `renew` 运行时。

执行记录：`f0161ea` 独立 compile-check 成功，完整 build 在依赖 `org.ow2:ow2:1.5.1` 解析失败，未运行游戏。`2482635` 完整 build 实际报出 `EnderDragonPart` 包名迁移错误，已在 `5f78983` 修正；[后续工作流](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44337530393)正在验证，不能拿此前热成像绿色结果代替本轮验收。未发布。
