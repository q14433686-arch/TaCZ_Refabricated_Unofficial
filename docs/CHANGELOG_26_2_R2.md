# TaCZ Refabricated 26.2 R2 release notes

**发布日期记录：2026-08-16**

**构建元数据：`1.1.8+fabric.26.2.R2`**

**目标基线：`origin/26.2(main)` / `99b472a6a8e1438f22a29abe8b3804b349cb5dfd`**

这是 Minecraft 26.2 Fabric 线的 R2 发布说明，不替代 `docs/archive/` 中的历史审计。
本 release 保持 `1.1.8` 为 SemVer 核心；`+fabric.26.2.R2` 是不参与版本谓词排序的 build
metadata，不能写成 `1.1.8-R2`。

## R3（源码状态，2026-08-31 起）

**构建元数据：`1.1.8+fabric.26.2.R3`**

R2-hotfix2 之后的主线增量，全部经维护者实机验证 PASS（A 卡 + Iris 环境；
NV 卡未实测，征测点见 Release 说明）：

- **内置 TacZ Mesh Loader（TML）**：VellEagle 的 mesh 高模附属内置移植
  （GPL-3.0，`provides: taczmeshloader`），含本仓原创的第一人称 GPU 静态烘焙
  （逐骨骼常驻 VBO、光照 4 级量化烘焙、光影下走 vanilla RenderType 管道、
  光影开关翻转触发重烘、GPU 失败自动回退 collector）、世界语境顶点预算门
  与 16 格近距全模豁免。文档：[MESH_LOADER.md](MESH_LOADER.md)。
- **世界语境 GPU 烘焙（多人高模枪帧数保卫战）**：其他玩家第三人称手持、
  掉落物、展示框、展示台雕像共用常驻 VBO，每枪每帧只传 O(骨骼) 矩阵；
  光照按量化档 LRU 缓存（`MeshGpuLightCacheSize`）+ 每帧烘焙额度
  （`MeshGpuBakeBudgetPerFrame`）防逐出-重烘打摆 + 逐出 VBO 延迟一帧释放。
  两轮实机返修已含：世界消费点钉在 `PreparedFrame.executeSolid` RETURN
  （MV 栈顶=viewRotation，字节码取证）、Iris 法线矩阵取自绘制时刻 MV 栈
  （弹栈时机后移）。**光影下世界枪照明待实测**，异常时游戏内关
  `MeshGpuWorld` 即回到 R3 前行为。
- **姊妹分支审查回合（A1-A10）**：吸收 1.21.11 分支对本仓 GPU 层的静态审查
  ——异常降级分表化（世界失败不连坐手部）+ 不再从渲染线程回写配置文件 +
  `LinkageError` 也接住（跨版本兼容问题回退而非崩溃）+ 渲染目标 override
  防御 + 顶点格式 stride 哨兵 + 烘焙额度独立旋钮；`PolyMesh` 退化面不再写
  零法线（光影下 NaN 随机高光）。诊断开关三件
  （`MeshPolyMirrorReverseWinding`/`MeshPolyInvertNormals`/
  `MeshPolyPreferPackNormals`）与 `IlluminatedRealSky` **默认全关**——
  绕序反转与 RealSky 均被姊妹分支实机否证为默认值（详见
  MESH_LOADER.md §5.2-ter），保留为按包诊断项。A4/A9 两条以
  Iris 26.2 源码证据驳回，证据链在代码注释里。
- **镜内裁剪三件套**：光影下开镜时第一人称手臂、瞄具挂载文字（如 MK5HD
  弹药计数）与准星一致地裁剪在目镜圆孔内。
- **瞄具文字 `Format error:` 前缀修复**：26.2 的 `I18n.get` 是格式化接口，
  枪包把显示串内联进 `text_key`（含 `%` 字符）时触发格式化异常；改用
  `Language.getInstance().getOrDefault` 纯查表，等价上游 1.20.1 语义。
- **检视动画修复**：开镜时触发检视不再不可打断（`stopAnimation` 漏掉
  transition 中的 runner + 同触发器后继动画被误停，两案连修）。
- **跨包合成修复（`tacz:nbt` 材料类型补齐）**：上游 1.21.1+ 把
  `forge:nbt`/`forge:partial_nbt` 合并成新的 `tacz:nbt`（带 `partial` 布尔），
  社区枪包升级工具 TaCZPackUpgrader 批量把旧包配方转成该形态（且 `items`
  写单字符串非数组）——本仓移植自 1.20.1 线从不认识它，Fabric 的材料
  CODEC 分发失败，整条材料作废：表现为「附属包要默认包的枪就显示不出
  也合不了，要自己包的枪就正常」（新旧两代配方文件混在同一包里，坏的
  是被升级过的那批，与命名空间无关）。新增 `TaczNbtIngredient`
  （partial=true 子集匹配 / false 严格全等）+ JSON 归一化（items 字符串
  →数组、`fabric:type` 判别键）；另修 no-type `{item+nbt}` 旧写法的 nbt
  被静默丢弃问题。NeoForge 家族继承上游新代码故无此病。
- **开镜距离补偿（mesh 闸门）**：`MeshMaxRenderDistance`/
  `MeshWorldFullDetailDistance` 原按裸眼距离判定，开镜放大 Z 倍后镜内
  掉落物/第三人称 mesh 枪几乎必然退化为立方体；现阈值乘以当前开镜
  放大系数（随开镜进度渐变），角尺寸语义一致，整屏变焦与 PIP 皆适用。
- **「二次渲染时视野内高模枪在镜内不烘焙」—— 跨线裁定：本线不改行为**：
  1.21.11（`237dc153`）与 26.1.2（`db360639`）已按同一根因修好这份实机
  回报 —— 它们那两条线**每调用一次 `renderLevel` 就重新提交一遍世界几何**，
  镜内那遍的提交被 `shouldSubmitGpuWorld()` 的镜内闸门拒收 ⇒ 镜内只能回
  collector + 顶点预算 ⇒ 高模枪被打成裸立方体，主画面那遍照常 GPU 烘焙。
  **26.2 的结构相反**：提交（含实体模型渲染）在 extract 阶段每帧一次完成，
  `LevelRenderer#render` 只是绘制阶段，镜内那遍复用同一批提交节点（本仓
  `SimpleFeatureRenderPhaseMixin` 正是为此存在）—— 那条闸门在本线**不可达**，
  而照抄姊妹线「镜内画完也清表」的那半会让主画面那遍拿到空表 ⇒ 开着二次
  渲染开镜时**镜外的世界 mesh 枪整层消失**。故**不移植行为改动**；
  六条证据与「若仍复现按什么顺序查」写在 `MESH_LOADER.md` §5.2-bis 第 13 项。
  本轮只加观测点（把「镜内有没有走 GPU 烘焙」从靠帧率反推变成日志事实）：
  镜内首次画上世界表、镜内提交被拒各一条 log-once，自定义 pass 的首画日志
  改报真实表名（此前画世界表也写 "on hand pass"）。**本线实机未验**
  （沙箱无 JDK 也无 MC 依赖源，编译走 CI 闭环）。
- **配置持久化修复（FCAP 26.x 保存断桥）**：Cloth 界面保存只改内存、
  从不写回 TOML（`ConfigValue.set` 不落盘 + `ForgeConfigSpec.save()` 在
  新架构下恒 no-op），重启即「配置重置」；现于保存流程末尾显式调 FCAP
  自己的 `LoadedConfig.save()`。实机 PASS。旧文件里钉着的旧值需改一次
  并保存才刷新。
- **PIP 修复与新配置**：倍率下限闸门 `ScopePipMinMagnification`（默认 4.0）、
  `ScopePipRerenderInterval`、`ScopePipShadowScale` 热应用、镜内那遍跳过
  poly 顶点提交；新配置均接入游戏内 Cloth Config 界面（中英文条目齐备）。
- 元数据：`fabric.mod.json` 新增 `contributors`（TACZ Dev Team / LesRaisins /
  VellEagle）；`LICENSES.md` 的 TML 条目钉死到来源版本 `1.21.1_fabric` v0.1.7。

---

## R2-hotfix2（已发布）

**构建元数据：`1.1.8+fabric.26.2.R2-hotfix2`**（hotfix 序号直接接在 `hotfix` 后面，
中间不放 `.` / `-` / `_` —— TaCZTweaks 按版本号字符串识别本项目，规矩记在
`gradle.properties` 注释里）

本轮可核实的三项改动（其中「光影 PBR 枪身闪烁修复」已在体 A/B 验证 PASS，
其余两项为源码级、**未实机验证**）：

- **从姊妹仓 `TaCZ_Renovated` 26.2 同步 LRTactical 官方 0.4.3**：预燃阈值与实体引信
  （`life >= 0`，温雷满预燃不再永不爆、C4 `-1` 仍不超时）、烟雾粒子改采环境光、
  移动输入只驱动近战（修静止拉栓抖动）、`display_offset` / `entity_transform`、
  消耗品 Bedrock/Lua 渲染通道；并补上本仓一直缺失的
  `CombatProperties#getActionCount`（自带 Lua 一直在调它，运行期 `LuaError`、编译期无感）。
  刻意**不**同步的条目与理由见 [SYNC_26_2_FROM_RENOVATED_2026_08_27.md](investigations/SYNC_26_2_FROM_RENOVATED_2026_08_27.md)。
- **低倍镜准星恢复目镜约束**：`BedrockAttachmentModel` 把掩码的两个消费者
  （准星反向裁剪 / 镜身+视模裁剪）拆成 `reticleMaskable` 与 `bodyMaskable`。
  此前案例⑨ 第二轮的 `ScopeSightClipFix` 用同一个开关把「建掩码」和「准星裁剪」
  一起关掉，低倍/红点通道的准星因此能溢出镜片。新增开关 `ScopeSightReticleClip`
  （默认开）可秒回退。详见 `COMPAT_AND_ROADMAP.md` 案例⑨ 第四轮。
- **镜内裁剪消除对 mixin 注册顺序的依赖（加固，非 bug 修复）**：
  `IrisGlCommandEncoderMixin` 在 `trySetup` 的 **HEAD** 记下当前 `GlRenderPass`；
  `IrisExtendedShaderMixin` 在 `iris$setupState` RETURN 由「无条件写 `tacz_ScopeMaskMode=0`」
  改为按记下的 pass 写正确 mode（`applyToShaderProgram`）；`IrisScopeMaskState` 删掉
  「`GL_CURRENT_PROGRAM` 为 0 时从 `pipeline.program()` 取 programId」那条静默无效退回分支，
  并把 `trySetup` RETURN 与 `iris$setupState` RETURN 两个写入点抽成共用的
  `writeScopeMaskState`，保证「最后跑的那个」写同一套状态。`applyToShaderProgram`
  写前校验 `GL_CURRENT_PROGRAM == programId`，不一致跳过并一次性告警。
  背景：tacz 与 Iris 都往 `GlCommandEncoder#trySetup` 的同一 RETURN 点注入，二者处理器的
  先后由 mixin config 注册顺序决定；tacz 在前时是坏行（mode 被 Iris 重绑程序写回 0）。
  本仓用户**未**报过镜内裁切失效、当前也**未**发作，本次只消除这个顺序依赖，
  **不**声称修好了任何用户反馈的现象。详细取证与「当前落在哪一行」的实机回填位见
  [SCOPE_MASK_ORDER_INDEPENDENCE_2026_08_28.md](investigations/SCOPE_MASK_ORDER_INDEPENDENCE_2026_08_28.md)。
  （均为源码级，**未实机验证**：本执行环境无 JDK，未编译、未跑游戏。）
- **光影 PBR 下第一人称枪身闪烁修复（在体 A/B 验证 PASS）**：
  Iris 26.x 的 `HandRenderer` 一帧跑两遍手部 pass（实心 + 半透明），Iris 对实心物品的
  半透明遍取消注入在 `submitArmWithItem` HEAD；本仓用 `@WrapOperation` 替换了该调用点，
  取消对 TACZ 视模永不生效 ⇒ 枪身每帧被提交进 `gbuffers_hand` 与 `gbuffers_hand_water`
  两遍、动画状态机一帧推进两次。labPBR/SEUS PBR 光影下两遍照明不同，叠加表现为
  「反射光源时枪身整块明暗闪烁」（用户报告：Complementary + Iris 1.11.2+mc26.2，
  仅第一人称、仅 PBR 开启时出现）。新增开关 `IrisHandPhaseSplitFix`（`[FIX]`，
  **默认开**）：视模只提交实心遍，复刻 Iris 对普通实心物品的语义；`false` 秒回退。
  2026-08-29 用户回报 **PASS**。证据链与验证记录见
  [IRIS_HAND_PHASE_SPLIT_FLICKER_2026_08_29.md](investigations/IRIS_HAND_PHASE_SPLIT_FLICKER_2026_08_29.md)。

> 说明：本节只列本轮**亲手改过并核对过**的内容。相对 tag `26.2_R2_HOTFIX`
> 的完整差异是 67 个文件（含此前已合并的 scope PIP / 兼容层等工作），
> 那些改动的记录在各自的文档里，本节不复述、也不代为背书。

## R2 内容

- **可替换弹药源 API**：新增 `com.tacz.guns.api.item.ammo` 的 `AmmoSource`、
  `AmmoSourceProvider` 和 `AmmoSourceRegistry.EVENT`。provider 按注册顺序选择首个非 null
  source；没有 provider 时仍走原 `IItemHandler`。查询只读，消费结果防御性 clamp 到
  `0..requestedAmount`，弹药箱耗尽会重置 ammo id。详见 [AMMO_SOURCE_API.md](AMMO_SOURCE_API.md)。
- **具名 gameplay hooks**：将 P0/P1 的客户端开火、换弹、拉栓和服务端 shooter 路径公开为
  可读的受保护 hook；`GunAnimationStateContext#hasAmmoToConsumeInEntity(Entity)` 取代对
  `lambda$...` 的依赖。`LocalPlayerShoot.SHOOT_LOCKED_CONDITION` 仍是同一静态单例，身份比较
  仍使用 `==`；历史 `INPUT_BOLT = "blot"` 未改。
- **P2-min Lua / 契约文档**：提取 `resolveScriptFunction(...)` 与 `runLuaCycleTask(...)`，但没有
  改变每个调用点的 fallback、参数、次数、异常或 cycle true/false 语义。补充 reload/heat fallback
  与服务端 charge 校验的契约 Javadoc，未放宽 finite、阈值、最大进度或网络抖动边界。
- **多格工作台与 Carry On**：B/C companion 不再拥有 block entity、菜单或 `BlockId`；普通和
  Carry On `setBlockAndUpdate` 放置都会恢复 HEAD/UPPER。C 继续保存 `half=lower|upper`，但用本地
  `TableHalf` 避开 Carry On 对 vanilla `DoubleBlockHalf` 的拒绝。完整矩阵见
  [CARRYON_COMPAT.md](CARRYON_COMPAT.md)。
- **内置 JEI/REI Ammo Query**：共享 `AmmoQueryEntry` 按 `sort` 再 id 排序；每种至少被一把枪
  使用的弹药有一条查询，前 60 把枪固定显示，其他枪组成 viewer 的 overflow 轮换组。新增语言资源
  位于独立的 `assets/tacz_ammo_query/lang/` namespace，未截断既有完整语言 bundle。
- **远程枪包同步后的 viewer 刷新**：cache 安装 → `ClientIndexManager.reload()` → 合并的
  `RecipeViewerReloadBridge.requestReload()` 全部在客户端事件循环完成。桥等待 level/player，JEI 与
  REI 同装时两者都尝试，轻量入口失败时每条连接最多一次 `reloadResourcePacks()` fallback，并在
  完成或异常后复位，避免 resource reload loop。

## 来源提交（只移植语义，不整栈 cherry-pick）

| 范围 | 1.21.11 R2 来源 |
| --- | --- |
| 弹药源 API | [`28aa9bba37b78fb8d9bca0769e9996863a55c707`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/28aa9bba37b78fb8d9bca0769e9996863a55c707) |
| P0/P1 hooks | [`ce9d4b2f9384e1030b53db58f8944f7f4d1839f2`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/ce9d4b2f9384e1030b53db58f8944f7f4d1839f2) |
| P2-min Lua / Javadoc | [`729df9816058d36988d3d6387df6af83b3a0d76a`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/729df9816058d36988d3d6387df6af83b3a0d76a)、[`c637e9c770ab459e695d7554b2d151ba5d434dfe`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/c637e9c770ab459e695d7554b2d151ba5d434dfe) |
| 工作台结构 | [`5b149f357efff53faa24159c801c1aff20cd435c`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/5b149f357efff53faa24159c801c1aff20cd435c) |
| Carry On layer | [`166cf676bb76a57deda37ef0df3fa3712f9d9b6a`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/166cf676bb76a57deda37ef0df3fa3712f9d9b6a)、[`3a02c226b37bc910defaa5d1f0462caac0509a74`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/3a02c226b37bc910defaa5d1f0462caac0509a74)、[`3ee41fa60873aea823edfff4f81464905a2ecf8c`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/3ee41fa60873aea823edfff4f81464905a2ecf8c) |
| Ammo Query | [`f488a822c92175c4a5930bbf671b3e4d802c7bcb`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/f488a822c92175c4a5930bbf671b3e4d802c7bcb) |
| viewer 刷新语义/调用点 | [`4a983253b557c4d6c6cd9a7159aaec8e4a2cdbc8`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/4a983253b557c4d6c6cd9a7159aaec8e4a2cdbc8) |

## 2026-08-16 联网核验

| 项目 | 结果 |
| --- | --- |
| 目标头 | `git fetch origin` 后 `origin/26.2(main)` 仍为 `99b472a6…`；没有目标基线之后的新提交。 |
| JEI | `gradle.properties` 的 pin 是 **30.13.0.86**（并已纠正 `build.gradle` 的陈旧 `.80` 注释）。[Modrinth Maven metadata](https://api.modrinth.com/maven/maven/modrinth/jei/maven-metadata.xml) 和 [JEI Fabric source](https://github.com/mezz/JustEnoughItems/tree/1cb7814331c90c5e5b24b5055a0741ab65f58f58/Fabric) 核验了 `mezz.jei.fabric.events.JeiLifecycleEvents.AFTER_RECIPES_UPDATED` 为 `Event<Runnable>`；其 listener stop/start plugin lifecycle。 |
| REI | `gradle.properties` 的 pin 是 **26.2.820**；[REI Maven metadata](https://maven.shedaniel.me/me/shedaniel/RoughlyEnoughItems-api-fabric/maven-metadata.xml) 可见该版本（更新的 26.2.821 同时存在）。[REI source](https://github.com/shedaniel/RoughlyEnoughItems/tree/2be20928abd9f1164fd9fd251268041c036b580f/runtime) 核验 `me.shedaniel.rei.RoughlyEnoughItemsCoreClient#reloadPlugins(MutableLong, ReloadStage)` 的两参数轻量入口；桥通过反射按该 descriptor 选择。 |
| Carry On | [26.2 branch](https://github.com/Tschipp/CarryOn/tree/26.2) HEAD `e50ddbc1c7461f381c62af5f4960db9d97751d16`/2.11.1 与 [公开 Fabric 26.2 2.11.0 文件 metadata](https://api.modrinth.com/v2/project/joEfVgkn/version/EzG8eAml) 已核验。签名、`DoorBlock.HALF` value-class 检查、`setBlockAndUpdate`、`ItemStackTemplate#create()` 调用点和数据标签的详细记录在 [CARRYON_COMPAT.md](CARRYON_COMPAT.md)。建议版本因此是 `>=2.11.0`。 |
| `ItemStackTemplate` | [Fabric 26.1 migration note](https://fabricmc.net/2026/03/14/261.html) 说明其为不可变的 item/count/DataComponentPatch 模板；R2 不写模板组件，而在 Carry On 2.11 `CarriedObjectRender#drawBlock` 的 `.create()` 后修改真实 `ItemStack`，在 item-model 提交前恢复 `BlockId`。 |

没有新增 Carry On、JEI 或 REI 依赖；Carry On 仅由 `suggests.carryon`、字符串 target、`@Pseudo`
和反射加载。旧的、已被 source-set exclude 的 `ConfigLoaderMixin` / `BlackList` 已删除。

## 验证状态

已执行：Java patch applicability / 三方合并审查（唯一手工冲突为 `LocalPlayerShoot`）、
`git diff --check`、JSON 解析、关键符号/排除检查、以及 26.2 源码层的 Carry On / JEI / REI
入口核验。

**未执行，不能宣称通过：** 此执行环境没有 `java`/`javac` 或 `JAVA_HOME`；因此
`./gradlew compileJava --no-daemon`、`test`、`build` 都不能启动，最终 JDK 25 编译和游戏启动矩阵
（未装 viewer、JEI-only、REI-only、Carry On 2.11.0）需要在具备工具链的环境完成。实际 Carry On
2.11.0 CDN jar 在本环境 TLS 下载失败；公开 artifact metadata、hash 和 upstream 26.2 源码描述符已
记录，但这不替代最终 jar 的运行验证。

### 最终游戏内清单

- 弹药源 provider 的 fallback、首个非 null、只读、partial/full/clamped consume、ammo box 清空，
  以及 dummy/creative/infinite/feed/Bolt 回归；
- 单发/连发/蓄力/dry fire/取消/战术换弹/拉栓，且 `RECOIL_DEBUG` 打开仍输出
  `TACZ Case08 RELOAD_START`、关闭不额外输出；
- 默认和枪包 A/B/C 工作台从任一半格搬起、原子放下、BlockId/菜单/模型保持；
- JEI 和 REI 各自验证 Ammo Query 的第三方包、排序、60 项 overflow、三语资源；
- 远程同步发生在 viewer 首轮注册后时的刷新、连续同步合并、断线清 pending、轻量 hook 故障的
  单次 fallback 与无 reload loop。

## FAQ：专服上 REI/JEI 作弊拿取枪械/弹药/配件显示紫黑块与 `item.*` 原始键（2026-08-22 增补）

**症状**：专用服务器上从 REI/JEI 拿取（或 `/give tacz:modern_kinetic_gun` 不带组件）得到的
枪械、弹药、配件显示缺失贴图，名称为 `item.tacz.modern_kinetic_gun` /
`item.tacz.attachment` 一类原始键；物品在 REI/JEI 列表内显示正常，单人/局域网正常。

**原因**：TACZ 的枪/弹/配件/工作台物品的内容 id 存放在 `minecraft:custom_data` 组件
（`GunId` / `AmmoId` / `AttachmentId` / `BlockId`）。服务器未安装 REI 时，REI 作弊给物
退回客户端拼装的 `/give` 命令，该命令只携带物品注册表 id、组部分恒为空
（REI `ClientHelperImpl#tryCheatingEntry` 源码 `tagMessage = ""`，`TODO 24w09a`
标注组件化后未适配），服务端拿到裸物品 → TACZ 读 `tacz:empty` → 名字回退 `item.*`
（无翻译键）→ 无模型。原版 Forge 与上游 Fabric 移植版行为相同，**非本移植版缺陷**。
（铁弹药盒仍显示 Iron Ammo Box，恰因其名称键在 mod jar 内。）

**解决**：
1. 专服安装与客户端同版本的 REI —— 之后作弊走 REI 网络包，组件完整；
2. 或用 TACZ 自带创造标签页 / 内置弹药查询、配件查询、工作台配方分类拿取；
3. `/give` 务必附带组件，如
   `/give @p tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"tacz:ak47"}]`。

JEI 客户端兜底走原版创造槽位包（携带组件）；若仅 JEI 也复现，反馈时附查看器与服务器版本。
完整源码推导见 `docs/DEDICATED_SERVER_GETNAME_AUDIT_2026_08_21.md` 第 8、9 节；
发布文案已同步（`docs/publish/{Modrinth,CurseForge,MCMOD}.md` 的 FAQ 一节）。
