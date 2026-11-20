# 从 Fabric 26.2 接收 TaCZ 1.2.0

这套指导移植的是**游戏内行为**，不是把 `26.2` 改成别的版本号。供体是从 `f9ae55a` 到 [`0df51ea` 的完整改动](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/compare/f9ae55af2b507a2d556c3846127aba1c57e47d1c...0df51ea957f65defe63ca3d6b36c0023529fe2d0)，包含 1.2 内容、专服同步、账号 HUD，以及最后的 r5.8.1 瞄具修正。`608eaec` 只补文档。[1.2.0 成品散列与 CI](../../CHANGELOG_26_2_1_2_0.md#build-120)属于 **26.2 Fabric**；不能用来验收其他版本。此前 [26.3 的分批交接单](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/c5d536b7b1e61bcacb0cd660df73de78b7db6796/docs/lineage/26.3-backports)可查功能来历，不含本轮最后的画面修正。

| 接收方 | 从哪里开工 | 优先解决的差异 |
|---|---|---|
| Fabric 26.1.2 | [执行单](26.1.2.md) · `569869f` | Java 25，无 remap；默认包大多来自 bundle；镜孔用深度副本，不是 26.2 的离屏掩码。 |
| Fabric 1.21.11 | [执行单](1.21.11.md) · `aac343a` | Java 21，Loom remap；同为深度孔径，但手部投影、Mixin 目标和 shader include 必须用本版。 |
| NeoForge · TaCZ_Renovated | [执行单](neoforge.md) · 按游戏版本分列 | **先对照 Neo 26.2 已有 1.2 工作，再补差**；保留 FML 注册、协议和库存事务。 |

要找具体改动，在含供体提交的 **Fabric 仓库**内按模块看最终差异，无需切分支。例如：

```sh
# 新同步协议连同纯逻辑测试；目标自己的通道注册不能整批 cherry-pick
git diff f9ae55a 0df51ea -- src/main/java/com/tacz/guns/resource/network src/test/java/com/tacz/guns/network
# 只看 r5.8.1 报告之后的画面修正；2604e9d 是先加复现用例、尚未修正的起点
git diff 2604e9d 0df51ea -- src/main/java/com/tacz/guns/client/render/scope src/main/java/com/tacz/guns/compat/iris src/main/resources/assets/tacz/shaders
```

### 建议的移植顺序

先让不装可选模组的客户端和专服跑起来，再加资源和集成，**最后动光影**。下表类名指供体；[测试目录](../../../src/gametest/java/com/tacz/guns/test)中的同名用例给出了操作与断言，不要求目标分支已经有同名 Gradle 任务。

| 顺序 | 从 26.2 取什么；哪些留给目标版 | 可以进入下一步的判据 |
|---|---|---|
| 1 · 包加载、转换与同步 | `GunPackOverlayResolver` 的覆盖顺序、`GunPackSync` 的有界 GZIP 分片和完整后换表；用目标的版本谓词、注册表、DFU、codec 和网络通道。目录与 ZIP 都要测；不能表达的扩展索引不得删字段假装旧端兼容。 | `check` 不改原包，`export` 到另一目录，再**在目标游戏加载**；旧谓词仍生效。专服独有索引在加入时出现；服务端 `/reload` 替换后旧项消失；退出清掉缓存，重连能拿到新项。参照[双 JVM 夹具](../../../verification/packaged-runtime/README.md)。 |
| 2 · 玩法、配方与存档 | `DefaultGunAttachments` / `VirtualOemAttachment`、`GunTime`、三档弹药库、挂板与展示柜。存储、菜单、战利品和方块实体按目标版接线；保留原有扩展 API、标靶档案及雕像持物组件。 | 实际生存库存装拆、卸弹及双向存取守恒；虚拟件不返实体。拆多方块任一半只掉一份；保存退出再进，带名/附魔物仍在。`WorkbenchSettlementSmoke`：原版 3×3 做枪匠台，专用台每个产物各做足料/缺料对照，缺料不扣料。 |
| 3 · 默认包和升级 | 用[全量来源清单](../../../DEFAULT_PACK_120.tsv)及[增量清单](../../../RESOURCE_IMPORT_120.tsv)追到上游 blob，再按**目标**合成最终资源。26.2 是 4,176 个有效文件、229 个索引；[246 条既有引用例外](../../investigations/DEFAULT_PACK_120_SOURCE_GAPS.tsv)不是允许任意新增缺件。不要对别的树直接跑 26.2 导入器的 `--write`。 | 对**成品 JAR**列路径、查重复、散列和退役项；用旧版真实导出树改一项再加一个文件，升级后备份逐项等于旧编辑树，新导出等于目标 JAR；普通重载不二次覆盖。保留[来源、署名与各自许可](../../../LICENSES.md)，不把资源包、测试模组或重复 MAE 当依赖整包再塞进去。 |
| 4 · 查看器、输入、HUD | `RecipeViewerReloadBridge` / `ReiPackRefresh` 只更新本插件拥有的展示；注册物品相同的工作台按 `BlockId` 区分。`client/tuning` 可先搬档案、优先级及分享码，再接目标的屏幕、按键和手部变换。 | 原版配方书取铁块解锁；分别装 JEI、REI，普通配方先到、包后到、真 `/reload` 后仍能查普通及专用配方，选方不扣料。账号 A→B→A 分别保存/取消，单枪覆盖类别及默认；再分别测键鼠、PAL 第三人称、实际手柄绑定（未装的组合记未测）。 |
| 5 · 模型、粒子与瞄具 | 取 `ScopeLensFrame`、`ScopeThermalCapture`、`ThermalSceneDepth`、`ScopeFinalOverlayState` 的输入输出契约；投影、遮挡、Iris 最终合成与深度恢复在各目标重接。枪口局部与世界材质各跑一次；不能只在 GUI 看一眼。 | 无光影先过，再测目标版 Iris + Sodium + **Complementary Reimagined r5.8.1**。重投影 PIP 不等于再次渲染世界；分别记录实际启用的模式、倍率、游戏/光影版本及原帧。 |

<a id="optics"></a>
### 光影验收：两个曾经「看似修好」的误区

- **红板消失，但分划也被删了。** 固定机位比较正常／去 `division`／再去镀膜；镜孔要看得见背景，原包的点和中心以外的刻线要留下。先测 QMK152、标准 8× 两档（实际引用 `scope_scout_display`）、两款 DeltaPoint 与 Vudu 低档；再覆盖目标默认包的每个瞄具/倍率。不要投一个通用 HUD 红点代替原始几何。
- **热像变完整，但目标能透墙。** REAP-IR 和 XL50 都用实际实体模型，经典／镜内重投影、两档倍率、16／36 格、实际模型朝向 0°／45°／90°。固定机位先拍可见光、空景、暴露热像，再用实心墙遮挡：看原始热掩码和**叠字前**的墙面，漏热均应为 0；最终画面的白色刻线不是热源。不要靠包名特判、包围盒白块、无上限深度偏移或关掉传感器来过测。目标有不同渲染架构时，复制的是对照实验，不是像素坐标或 GL 常量。见[26.2 取证、原帧和逐档读数](../../investigations/ADS_COMPLEMENTARY_R581_26_2.md)。

<a id="rei"></a>
### REI 紫黑物品：诊断边界，不是这批的修复

配方页通过不代表作弊给物通过。[26.2 的调查 §9](../../investigations/DEDICATED_SERVER_GETNAME_AUDIT_2026_08_21.md)及 [REI 26.3 的给物分支](https://github.com/shedaniel/RoughlyEnoughItems/blob/b3bc5013d33f12bbea5737bba5fb76652e2b07d8/runtime/src/main/java/me/shedaniel/rei/impl/client/ClientHelperImpl.java#L196-L238)表明：无服务端 REI 通道时，命令回退可只给基础物品 ID；缺 `minecraft:custom_data` 中的 `GunId` / `AttachmentId` 等就不能还原所点变体。不要修翻译、写死默认模型、给普通玩家提权，也不要把它归到 JEI。

目标需排查时，保留**无查看器的双端**作为基线，另开一个真实 TCP 生存服，用有权限的玩家对比：仅客户端装 REI 时点选默认 AK47、同服用 `/give @s tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"tacz:ak47"}] 1`、两端装匹配 REI 时再点同一条目。比较服务器实际发出的栈组件与数量，不只看聊天名称；无权限玩家仍不能给物。旧 REI／Neo 的具体路径以其目标构件和结果为准。

### 交接回执

在[账本](../HANDOFF_LEDGER.md)按接收线填写：目标 commit、成品 JAR **本体** SHA-256、构建和测试链接、双端版本、升级备份结果、光影包/后端及未测项。**目标实现未验收就是 OPEN**，没有同名测试就移植夹具或记录同操作的实机结果。升为 `1.2.0` 是各目标接收行为后的独立决定；届时同步元数据、README、实际包依赖匹配和严格版本门禁。本轮不向接收线发包。
