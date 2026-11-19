# 26.3 R1：哪些移植真正跑过，哪些还没有？

**上游默认枪包的 4,176 个有效文件都已对上，但 1.2 的玩家行为和扩展接口还不完全等价。** 工作台、改装、仓储、键鼠、光学、动画、第三人称和特效都有实际游戏路径。剩下的是具体的行为差异、旧渲染接口的替代边界、尚未接入的可选模组，以及未测的组合。这个判断只针对 26.3，不给其他 MC 版本盖章。

## 比较基线

直接目标是 [Sh1roCu 的 Fabric `356bdf9`](https://github.com/Sh1roCu/TACZ-Refabricated/tree/356bdf9d48f2225bac6ceb56adc5576a418517a8)，不是把原版 NeoForge 源码或另一个模组的设想一并计入。以该上游 `d290355` → `356bdf9` 确定 1.2 的新增变化，再与 26.3 原生起点 `cc990c3` 和本 PR 的 `a3847d0d` 比较**完整树**，不只看新增类。该审计点已包含这次实体／展示物补光。后续画面和性能复测另列于[受光报告](SURFACE_LIGHT_26_3.md#实体装备和陈列物)。

- 四树分别为 **4,645 / 6,037 / 4,960 / 6,757 个路径**，并集 7,748。排除已按 overlay 归位的内置包，`no-same-path` 的 518 条中，146 条是 Java、360 条是包外资源／元数据、12 条是工程／资料。仅 `data/c/tags/` 就占 340 条。本端使用 26.3 的标签和配方系统；**不意味着每个古老标签或第三方包都兼容**。按改名追踪的 499 条 Java 变化另算：34 同目标（含双方都已删）、6 仍是旧 blob、134 无同名目的地、325 已改写。不能从任一行求「完成率」。
- 逐个核对 6 个旧 Java blob：`ItemHandlerCapability`、`TextureAtlasMixin` 是工具包迁位；`ReloadCommand` 的目标差异是删注释；`BedrockPolygon` 的旧 bake 帮助函数、`AnimationDataRegisterFactory` 的手部过滤要分别看本线快照与 PAL；`IFunctionalRenderer` 新上游还有带 `MultiBufferSource` 的重载，见下表。另查 `ArmorStandMixin`：它管**矿车骑乘**，不是盔甲架没有被光照亮的原因；`tweakeroo/ItemMixin` 在目标没有生效的注入。
- 扫描 Java 中 `@SerializedName("…")` 的不同字面值，目标 349 个（新增 75）；`com/tacz/guns/config` 中 `define*` 的目标键 92 个（新增 30）；`TimelessAPI` 的目标 `public static` 方法名 27 个（新增 8，不按重载计）。本线分别为 409 / 146 / 27 个；目标序列化键和 API **名字**都能找到。缺的三个配置键是`EnableExperimentalDisplayGunVbo`、`EnableStaticItemGunVbo` 和 `EnableSableCompat`，不是缺 3 个 JSON 字段。下表再追生产调用和测试，不拿字符串命中充当运行证据。

## 按玩家操作和消费方复核

| 子系统 | 本线做法与证据 | 没证明什么／下一项对照 |
|---|---|---|
| 内置包、资源与生命周期 | 4,405 个目标物理文件按 overlay 取胜者得到 **4,176** 个目的地；3,929 份同源、247 份有[逐文件适配记录](../../DEFAULT_PACK_120.tsv)。`DefaultCatalogSmoke` 检查 70 枪 / 126 配件 / 25 弹药 / 5 转接座 / 3 台的实际索引；客户端解析 70 个模型和状态机。`WorldReturnSmoke` 关闭并重进同一存档。 | 已知 [246 条上游资源引用例外](DEFAULT_PACK_120_SOURCE_GAPS.tsv) 不是 246 个新漏拷的文件。没有逐枪录下全部动作、音频，也没有测试所有外置包。 |
| 普通合成、专用工作台 | 普通 3×3 产出工作台、玩家配方书解锁与 REI 同步展示有[分层回归](ORDINARY_CRAFTING_26_3.md)。新增[服务端结算测试](../../src/gametest/java/com/tacz/guns/test/WorkbenchSettlementSmoke.java)：给生存测试玩家实际背包材料，调用对应台的生产 `doCraft`，检查掉落产物组件、数量及库存；每条先做缺一份材料不扣料／不出货的负例。覆盖 **70 枪 + 107 配件 + 25 弹药**。 | 这是 202 条正确结算 + 202 条拒绝，不是只看列表，也不是从两台网络客户端点了 404 次按钮。第三方配方、服主过滤器和相互重叠的材料条件不由此担保。 |
| 改装、变体和供弹 | `DefaultRefitSmoke`、`OpticInstallSmoke` 用客户端正式改装请求、枪上组件及服务端库存验收；`ClientMessageSelectRefitOption` 承担上游两种选择，不凭包名判缺失。`GunActionSmoke`、`DefaultReloadSmoke` 检查脚本结算、六组装填与多种供弹；`TrackingActionSmoke` 查观察端事件。 | 126 个配件索引不等于 126 个可独立制造品。单个集成客户端观察另一个实体不是两个独立玩家联机；六组脚本不等于全包所有中断时机。 |
| 玩家操作、声音和生物 | `PortInputSmoke`、`PlayerFeedbackSmoke`、`ShotConsequencesSmoke`、`MovementRulesSmoke` 分别测重绑／卸弹、受伤倾斜／背挂、实际射手位移／生物目标与姿态规则。[配置与反馈](FABRIC_120_REAUDIT.md)。 | 音量／音高采样、服务端吸引生物与真人听感三者不同；老 `options.txt` 和全部按键组合未测。 |
| 收纳与陈列 | `AmmoStorageSmoke`、`DisplayRackSmoke`、`RackPreviewSmoke` 分开验证服务端事务、带库存升级、染色、锁、结构旋转以及实际单件预览。挂板配色走原生染色配方；展示物走 `FIXED`，不是掉落代理。两块不同载体上的同一绿宝石还有补光／全亮对照。 | 箱内物品、烘焙背板、物品自发光是三层；不是所有自定义 renderer 都吃同一个光照值。Carry On 安装后的操作不是普通拆放测试。 |
| 输入设备、创造栏和查看器 | 真实 JEI、REI **分开**加载，测默认包制作条目、双向查询、选方与重载；创造筛选/翻页与中英文悬停有屏幕对照。Controllable 独立客户端以 SDL 虚拟手柄输入、混合键鼠及震动回调验证；PAL 独立客户端量离屏换枪、再入镜的支撑臂姿态。[此前验收与负例](PORT_GAPS_26_3_VALIDATION.md)。 | 不是 EMI，也不是四个可选模组同装；虚拟设备回调不是摸过实体手柄。PAL 不证明 YSM 端已经调用本端握手。 |
| 镜片、HUD、外显、掉落物 | UH-1 数字、XM157 空心框、XL50 的遮挡与经典／PIP；准星用 framebuffer 像素；[Reimagined](REIMAGINED_26_3.md) 测实际光影而不只检测模组存在。没有作者 LOD 的掉落代理仅对 `GROUND` 的 `ItemEntity` 生效；[实测顶点、重载与帧时间](DROPPED_GUN_LOD_26_3.md)。 | 原生 collector／TML、PAL、镜片合成替代上游 GL／KosmX，不是 API 或性能一比一。独显、其他光影、远景模组、未知特殊材质和高密度陈列另测。 |
| 枪焰／烟、动画粒子、命中表现 | `renew` 和配件有[开火帧对照](MUZZLE_ATTACHMENTS_26_3.md)。[Snowstorm](SNOWSTORM_RUNTIME_26_3.md) 有解释器、Molang、发射器、材质/裁剪、所有者清理与追踪事件夹具；不是只搬了 JSON。`SurfaceFragmentSmoke`、`WorldTracerSmoke` 区分实际碎屑、跨帧曳光及枪焰。[世界补光](SURFACE_LIGHT_26_3.md) 是端口的可选增强。 | 不保证所有脚本／粒子组件组合和双客户端版本错配都与 SBM 二进制相同。物体亮起来不等于修改了区块光或整个光影包的动态光源。 |
| 内容升级、转换与扩展接口 | `PackConversionSmoke`、`ConversionReloadSmoke` 用原包不变的导出、真实 `/reload` 和新索引；版本/资源指纹、备份、兼容提示以及 client variant／adapter 枚举各有专测。[作者须知](../GUNPACK_CONVERSION_26_3.md)。 | 不能证等价的 Forge 条件应报告、人工处理，不做静默转换。未做外部 Java 模组二进制兼容承诺。 |

**本轮运行证据（`a28882da`，与四树审计点的 `src/main` 完全相同）：** [完整构建、专服结算、普通客户端](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44640438182/job/135969809591)及[独立 PR 构建](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44640444974/job/135969970072)、[三种 Iris 作业](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44640438319)、[两个查看器](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44640438208)、[PAL](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44640438350)及[虚拟手柄](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44640438178)均 success。push 和 PR 的专服测试各自报告 `PORT_SETTLEMENT: 202 survival menu products {ammo=25, attachments=107, gun=70}`，并通过 202 条缺料负例。没有把一个旧的成功运行借给后来改过的实现。

## 确认的差异与未验收项

按依赖和能否在普通 26.3 重现来分，不把“没有截图”和“没有实现”混在一起：

1. **普通世界还有一个实际源码差异：翻倒／立起的标靶逐 tick 跳角度。** [目标 `TargetRenderer`](https://github.com/Sh1roCu/TACZ-Refabricated/blob/356bdf9d48f2225bac6ceb56adc5576a418517a8/src/main/java/com/tacz/guns/client/renderer/block/TargetRenderer.java#L42-L45) 用 `partialTick` 在 `oRot` 和 `rot` 之间插值；[本线](../../src/main/java/com/tacz/guns/client/renderer/block/TargetRenderer.java) 提取两值却只向模型提交最新的 `rot`。方块实体每 tick 以 45° 翻下、18° 收回。这是较老版本就有的行为差异，**不是新 1.2 枪包条目，也不是这次补光引入或修好的问题**。建议用同一对前后角度取 0、½、1 三个子帧，看板体与命名头部；现有皮肤／存档测试和本次雕像受光图都没有测这个过渡。
2. **实现了交互，但提示时机不同。** 上游 `GunDisplayCaseClientInteractionEvent → DisplayCaseLockHintOverlay` 在输入后于准星下方提示 40 tick；本线 [`InteractKeyTextOverlay`](../../src/main/java/com/tacz/guns/client/gui/overlay/InteractKeyTextOverlay.java) 在指向柜子且持物条件匹配时随交互 HUD 在准星上方显示，也受该 HUD 的配置控制。锁和连锁柜逻辑不缺，不称提示行为完全相同。扩展作者也需注意：上游函数式渲染的 `MultiBufferSource` 重载并非本线 API；在 26.3 应走 [`IFunctionalSubmitter`](../../src/main/java/com/tacz/guns/client/model/IFunctionalSubmitter.java) 的快照任务。只实现旧回调、不提供 collector 任务或镜像几何的自定义节点，会在 `BedrockRenderSnapshot` 计入 `skippedFunctionalNodes` 并跳过，不能说旧扩展可直接沿用。
3. **原生替代不等于同名同语义。** 目标 `AmmoItem.verifyComponentsAfterLoad` 从索引取单弹种的堆叠上限；本线在写弹种 ID 时写 `MAX_STACK_SIZE`（夹至 1–99），无数据时原型为 99，服务端物品库存 tick 再校正。不能由默认配方通过推出所有旧箱子在载入瞬间已改写。另一例是目标已弃用的 `TimelessAPI.getRecipe/getAllRecipes` 返回空，推荐读原版配方管理器；本线方法仍从同步枪包数据提供配方，**不是同签名就应仿照返回空**。
4. **确实缺集成：** 独立 **EMI** 入口及其工作台／双向查询／选方；**Sable** 移动子世界坐标、附着和命中；**Simulated** 多格装配联动。**Accelerated Rendering** 的 [`shouldAccelerate()`](../../src/main/java/com/tacz/guns/compat/ar/ARCompat.java) 返回 `false`，是禁用，不是“冲突已修复”。不把旧 1.21.1 JAR 硬装到 26.3；未建立可验证组合不等于证明全网没有新构件。[逐依赖的依据](PORT_GAPS_26X_2026_11_18.md)。
5. **静态契约／待对端验证：** 本轮 [YSM 11 项契约检查](../../scripts/verify_ysm_compat.py) 通过，包含掉落优化不能截走 `FIXED` 提交。未加载 YSM 验收，不把它写成已联调。KubeJS 三个事件投递体在[目标 Fabric 本身](https://github.com/Sh1roCu/TACZ-Refabricated/blob/356bdf9d48f2225bac6ceb56adc5576a418517a8/src/main/java/com/tacz/guns/api/event/common/KubeJSGunEventPoster.java)也已注释；不算丢失一个原本能用的事件系统。LR 资源、脚本调用和本线 AmmoSource 是端口自己的范围，不因 1.21.1 没有同名文件而删除。

**复算入口**（不切换工作分支；不从其他版本复制资源）：

```sh
# UPSTREAM 指向含上述两个 Sh1roCu 提交的 Git 仓库。输出 TSV 约七千行，按需生成。
python3 scripts/compare_port_trees.py "$UPSTREAM" --local a3847d0d > /tmp/tacz-four-trees.tsv
python3 scripts/audit_upstream_120.py "$UPSTREAM" --local a3847d0d
python3 scripts/verify_120_resources.py
python3 scripts/verify_ysm_compat.py
```

另跑了方块掉落及 BE 持久化的 `--strict` 接线检查、弹孔六面 6/6、查看器重载契约 7/7、LR 资产 3/3 和 5 个 Lua 文件对 46 个上下文方法的静态检查。这些不是游戏测试。仍应优先用**两台独立客户端 + 同一专服**验收重载、库存和远端表现，再做目标用户的显卡／光影／第三方包组合；不从本次单集成客户端或软件渲染的 P95 推算所有玩家的帧率。
