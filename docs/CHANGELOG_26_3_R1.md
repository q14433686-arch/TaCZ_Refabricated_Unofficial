# TaCZ Refabricated 26.3 R1-hotfix4 release notes（草稿，尚未发布）

**构建元数据：`1.1.8+fabric.26.3.R1-hotfix4`**

**基线：`origin/26.2(main)` R3-hotfix2（`c7c3c55` 之前的 26.2 源码）→ 本线 `26.3`**

**环境：Minecraft 26.3 · Fabric Loader 0.19.5+ · Java 25+ · Fabric API 0.160.7+26.3 · Forge Config API Port 26.3.0+**

> 状态：R1 基础版编译/CI 全绿；下列标 ✅ 的基础版条目已由维护者在 26.3 实机验证（2026-09-18 ~ 09-21，
> 无光影 + Iris 光影 + 专服三种环境）；`R1-hotfix` 与 `R1-hotfix2` 新增项的历史 CI 结果见对应条目，尚未实机验证。
> `R1-hotfix3` 方块/标靶增量已通过 CI 构建和回归检查；`R1-hotfix4` 已通过隔离桩测试，
> CI 状态以对应 Actions 运行为准。两者尚未实机验证，且 **26.3 尚未正式发布**。
> 姊妹项目（NeoForge）移植指南：[`lineage/PORT_GUIDE_26_3_FOR_RENOVATED_NEOFORGE_20260921.md`](lineage/PORT_GUIDE_26_3_FOR_RENOVATED_NEOFORGE_20260921.md)。
> 版本命名沿用本仓历史：hotfix 序号直接追加在 `hotfix` 后、中间不加分隔符（`R1-hotfix` → `R1-hotfix2` → `R1-hotfix3` → `R1-hotfix4`，不另起 R2）。

## R1-hotfix4 增量（2026-10-26 UTC：标靶皮肤动态解析与回归测试）

- 改名标靶放置、改名矿车渲染的空 UUID 路径已在 `R1-hotfix3` 避免：分别使用名称动态档案，
  再交给原版 `PlayerSkinRenderCache`；本轮并非首次修复这两条路径。
- 标靶方块在服务端异步查到真实 UUID 后，改为保留 UUID **动态**档案而非固化当时的纹理快照，
  让客户端皮肤缓存按 UUID 异步查询；无结果、未就绪或运行期异常时使用默认皮肤。
  查询结果为空或异常时保留原档案；过期、移除或换世界的回调不能覆盖当前主人。
  之前已保存的静态纹理档案及显式传入的完整档案仍保持静态，不会主动丢弃其纹理。
- `test_block_entities.py` 现在直接运行真实 `TargetBlock#setPlacedBy` 源码，并用 API 桩**模拟**
  原版回调后才消耗物品的顺序，覆盖生存、创造、有效/无效/空名称、客户端和缺少方块实体；
  还模拟皮肤缓存待解析→默认皮肤→可用皮肤及异常回退，检查 UUID、旧存档及过期回调。
  此测试没有运行原版 `BlockItem`、真实网络、渲染设备或游戏。
- 本地隔离桩测试、14 项守卫正反向检查及严格静态检查已通过；提交后的真实构建状态以
  GitHub Actions 为准，游戏内验证仍待进行（改名后放置与物品消耗、专服同步、换肤、离线回退、
  矿车重命名与渲染、旧存档重进）。

## R1-hotfix3 增量（2026-10-26 UTC：同步 26.2 R3-hotfix4 方块修复）

本增量尚未正式发布或进行 26.3 实机验证；版本号是 26.3 自己的 `R1-hotfix3`。
代码来源为 26.2 PR #115；#116 是 26.2 的版本号更新，不复制其版本号。
26.3 保留自己的战利品表格式。

- **工作台**：26.3 已移除多方块非根半边的手工掉落；本次另外将 B 型工作台创造模式的
  抑制判据改为挖 `head` 时先无掉落移除产出半边 `foot`。B/C 的生存掉落继续由 26.3 的
  `match_block` 战利品表控制，枪包 `BlockId` 仍由 `copy_custom_data` 复制。
- **雕像**：在服务端 `BlockEntity#preRemoveSideEffects` 里弹出并清空陈列的枪，使用回调给出的
  旧方块状态确定方向；重复回调不会重复掉落。原版 `BLOCK_DROPS` 游戏规则和显式跳过方块实体
  副作用的更新标志仍然生效，不保证绕过这些原版限制。
- **标靶主人与皮肤**：读取以前写入的 `owner_uuid` / `owner_name`，并兼容旧 `Owner` 复合标签的
  `Name` / `Id`；无效 UUID 不再导致读取崩溃，空 UUID 不再传给 26.3 的 `GameProfile`。
  新存档/同步包使用 `ResolvableProfile` 保存档案及纹理，服务端在 IO 线程请求完整档案，
  回主线程后检查实体和主人是否已变化；客户端使用原版 `PlayerSkinRenderCache`，而非直接选默认皮肤。
  无效玩家名仍保存在扁平键中，查询失败会记录警告并保留待解析档案；服务端返回无纹理属性时也
  保留动态档案，让客户端有机会自行解析。
- **标靶矿车（关联修正）**：发现 26.3 原有命名矿车也会创建空 UUID 档案。改为按名称创建
  动态档案，名称改变或清除时更新缓存，渲染时使用 26.3 玩家皮肤缓存与默认皮肤回退。
  该项是检查方块皮肤时发现的同类问题，不是 26.2 PR #115 的新增提交。
- **边界**：旧 `Owner` 复合标签的历史纹理属性不被复制，只迁移名字/UUID，皮肤需重新解析；
  若查不到或客户端无法联网则可能保持默认皮肤。`R1-hotfix3` 查得并持久化的纹理是当时的快照；
  `R1-hotfix4` 不再为新解析结果固化快照，但不会自动转换已保存的静态档案。
- **离线验证**：`verify_block_drops.py --strict` 与 `verify_block_entity_persistence.py --strict`
  通过；`test_block_guards.py` 的 12 项正反向检查通过；`test_block_entities.py` 使用 Minecraft API
  隔离桩执行真实方块实体、标靶矿车和两处渲染器代码，覆盖存档回读、旧数据、异常 UUID、
  异步结果过期、单次掉枪、矿车名称变化及皮肤回退。本地桩测试使用 Java 25 运行时及 Eclipse JDT
  编译器；这是桩测试，**不是针对 26.3 Minecraft 依赖的实际编译**。本次尝试 `./gradlew compileJava`，
  Gradle 分发包下载时 TLS 握手失败，编译任务未启动。
- **CI 验证**（代码提交 `13d3fff`，2026-10-26 UTC）：
  - [`compile-check`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/40407976378)
    使用 Java 25 成功执行真实 `./gradlew compileJava`，编译日志回写至会话分支。
  - [`build`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/40407976635)
    通过方块/标靶静态守卫、隔离桩测试和完整 `./gradlew build`，并上传 jar artifact
    （保留 14 天；不是正式 Release）。
  - [`Version consistency`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/40407976824)
    严格版本一致性检查通过。CI 成功仍不代表游戏内行为已验证。
- **待实机**：分别挖 B/C 工作台的根/非根半边（生存、创造，含枪包 `BlockId`）；挖、炸、
  活塞及 `/setblock` 移除放枪雕像（应单次掉枪，并核对游戏规则）；使用真实和无效玩家名放置标靶，
  验证皮肤、重进存档、专服同步及断网回退；覆盖旧扁平键与 `Owner` 复合标签存档；
  在轨道上测试命名、重命名及取消命名的标靶矿车，核对皮肤、位置和两层头部渲染。

## R1-hotfix 增量（2026-09-29 ~ 09-30）

- 🔧 **枪包语言文件容错（绕过，不是根治）**：在 `26.3 R1` 基础上加入 `GunPackLangCompat`，避免 Enlisted Gun Pack v1.2.1.3 的无效 `en_us.json` 令 26.3 丢弃整组语言加载。对可恢复条目重写合法 JSON 并记录枪包/文件告警；不修改枪包，根因仍需作者修正缺失逗号。
- 🔧 **多枪包进档 `fabric:recipe_sync` `EncoderException` 断连修复（未实机验证）**：
  - `TaCZFabric#registerRecipeSync()` 改为仅登记 `minecraft:*` 原版配方序列化器，不再登记 `tacz:gun_smith_table_crafting`（客户端工作台/JEI 均读 `CommonAssetsManager` 自建同步通道，原版 `RecipeSynchronization` 触发 `GunSmithTableSerializer.STREAM_CODEC.encode -> getIngredientOrThrow()` 会在遇到单数 `data/<ns>/recipe/` 下未解析/空标签材料时直接抛 `EncoderException` 踢出玩家）；
  - `GunSmithTableSerializer.STREAM_CODEC.encode` 移除 `getIngredientOrThrow()`，改为仅编码已解析且非空的 `Ingredient` 并对 `id`/`result`/`group` 做非空兜底；
  - `CommonAssetsManager#onReload(RegistryAccess, boolean)` 对 `recipe.init()` 增加逐条异常隔离，并新增 `sanitizeSyncedRecipes` 在 `TAGS_LOADED` 阶段剔除 `RecipeMapMixin.bySyncedSerializer` 中含空标签（`placementInfo().isImpossibleToPlace()` / `ing.items().findAny().isEmpty()`）或预编码失败的配方；
  - `StrictNBTIngredient` 对齐 `"items"` + `"nbt"` 格式并补齐 `display()`；`GunSmithTableIngredient#normalizeLegacy` 支持将 JSON 数组内嵌的 `#tag` 展开为物品 ID 并过滤未安装联动模组物品。
- 🔧 **恢复 REI、Zoomify、Shoulder Surfing Reloaded 26.3 兼容适配并同步上游依赖（未实机验证）**：
  - **REI `26.3.823` + Architectury `22.0.3`**：恢复 `gradle.properties` / `build.gradle` 编译依赖、撤销 `cn/sh1rocu/tacz/compat/rei/**` 的 `sourceSets` 排除并恢复 `fabric.mod.json` 的 `rei_client` / `rei_common` 入口点；`GunSmithTableDisplay` 过滤延迟解析返回 `null` 或空材料的项以防 NPE；`REIClientPlugin#registerCategories` 补充 `displays.clear()` 并改用 `item.getName(icon)`；`REIPlugin#registerItemComparators` 补注册 `GUN_SMITH_TABLE` 与 `WORKBENCH_111/121/211` 的 `BlockId` 比较器；`rei/entry/AttachmentQueryEntry` 改为引用 REI 自身的 `AttachmentQueryCategory.MAX_GUN_SHOW_COUNT`；
  - **Zoomify `2.16.3+26.3`**：恢复 `maven.modrinth:zoomify:2.16.3+26.3` 编译依赖，撤销 `ZoomifyCompatInner.java` 的 `sourceSets` 排除，并在 `ZoomifyCompat` 中恢复按 `FabricLoader.isModLoaded("zoomify")` 委托 `ZoomifyCompatInner` 的逻辑；
  - **Shoulder Surfing Reloaded `26.3-5.2.0+fabric`**：恢复 `maven.modrinth:shoulder-surfing-reloaded:26.3-5.2.0+fabric` 编译依赖，撤销 `ShoulderSurfingCompatInner.java` / `ShoulderSurfingPlugin.java` 的 `sourceSets` 排除，恢复 `ShoulderSurfingCompat` 委托实现及 `src/main/resources/shouldersurfing_plugin.json` 插件描述文件；
  - **其余依赖版本同步**：ModMenu 升级至 26.3 正式版 `21.0.0`（原 `21.0.0-beta.1`），Cloth Config 升级至 `26.3.159`（原 `26.3.158`），JEI 升级至 `31.8.0.48`（含 `#4514` 自定义材料组件保留修复），Carry On 核实上游已发布 `26.3-2.12.0` 且与本模组 `@Pseudo` mixin / `CarryOnReflection` 签名完全兼容。
- ✅ GitHub Actions `build` / `compile-check` 对语言文件容错变更通过（commit `81e71cf`）；**上述增量待 CI 编译并尚未在 26.3 实机验证**。

## R1-hotfix2 追加（2026-10-21：同步 26.2 线 R3-hotfix3 的两项修复）

来源：`26.2(main)` 的 `4397753`（枪包同步后 JEI 丢配方）与 `2e9b62a`（弹孔朝向），
逐条按 26.3 的实现与依赖适配，不是整段拷贝。差异、证据与验证边界见
[`investigations/SYNC_26_2_HOTFIX3_TO_26_3_20261021.md`](investigations/SYNC_26_2_HOTFIX3_TO_26_3_20261021.md)。

- 🔧 **枪包同步后 JEI 丢配方（客户端显示层，未实机验证）**：`RecipeViewerReloadBridge` 不再
  「优先反射 `Internal.restartJei()`、失败再伪造 `AFTER_RECIPES_UPDATED`」。26.3 线原实现的回退分支
  在 JEI 31.8 的 `ClientLifecycleHandler#registerEvents` 里会先 `if (!receivedRecipeSync) Internal.clearClientRecipes();`
  —— 没有紧随真实配方同步包的自造事件等于「换服」，把服务端同步来的 vanilla 型配方整批清掉，只剩
  客户端原版 fallback（`Loaded N vanilla recipes...` + `This fabric server does not provide recipes to JEI`）。
  现改为：新增可选 Mixin `compat.jei.JeiClientLifecycleHandlerMixin`（`@Pseudo` + 字符串 target +
  `require = 0` + `MixinPlugin` 的 `isModLoaded("jei")` 门禁），在
  `mezz.jei.fabric.startup.ClientLifecycleHandler#getReloadListener()` 返回时捕获 JEI 自己的生命周期
  listener；刷新时调用它，只做 JEI 的 stop/start，不伪造同步语义、不读写 JEI 私有配方表。
  反射 `Internal.restartJei()` 的路径一并移除（handler 未初始化时它会抛 `IllegalStateException`）。
  REI 刷新路径与「每连接一次资源重载兜底」保持不变。**REI 的全量 `reloadPlugins(null, null)` 仍可能
  丢掉服务端已同步的显示（跨分支审计列为未修项），本次不声称修好 REI。**
- 🔧 **弹孔朝向错误（未实机验证）**：`BulletHoleParticle#extract` 原先把 `Direction#getRotation()`
  直接当作四边形旋转，但 vanilla `QuadParticleRenderState` 的顶点取自 XY 平面（局部 +Z 法线），
  而 `Direction#getRotation()` 的约定是局部 +Y 法线。结果是六个命中面的弹孔贴面方向/UV 走向都不对。
  同步 26.2 的回移实现：旋转改为 `direction.getRotation() · Rx(-90°) · Rz(180°)`，
  原来的局部 Y 防 z-fighting 偏移改为沿命中面法线移动 `0.01 × getQuadSize(partialTick)`，
  位置仍以显式相机相对坐标传给六参数 `extractRotatedQuad` 重载（颜色继续走 `rCol/gCol/bCol/alpha`
  字段，`finally` 里照旧还原）。
- ✅ 静态验证：`python3 scripts/verify_recipe_viewer_reload.py`（4 项）、
  `python3 scripts/verify_bullet_hole_orientation.py`（5 项）、
  `bash scripts/check_release_consistency.sh --strict` 均通过。
- ✅ GitHub Actions 在 commit `382dcb5` 上通过：`build`（mixin 配置完整性 / 语言键齐平 / 版本一致性 +
  完整 `./gradlew build`，run `39680020522`）与 `compile-check`（run `39680020194`）。
  这同时证实 26.3 的 `SingleQuadParticle` 仍有六参数
  `extractRotatedQuad(state, quaternion, x, y, z, partialTick)` 重载与 `Mth.PI/HALF_PI` 常量。
  **仍无实机验证**（JEI/REI 刷新、六面弹孔均未进游戏）。

---

## R1-hotfix2 追加（2026-10-21：YSM / Yes Steve Model 适配）

- 🔧 **新增 `com.tacz.guns.compat.ysm.YsmCompat` 门面（未实机验证）**：把 YSM 需要的 9 个
  molang 变量（`tac_hold_gun` / `tac_gun_type` / `tac_gun_id` / `tac_is_fire` / `tac_is_aim` /
  `tac_is_reload` / `tac_is_melee` / `tac_is_draw` / `tac_fire_mode`）收敛成静态方法，语义逐条对齐
  YSM 1.20.1 Forge 版 `TacBinding`/`TacAnimHandler`；内部改用 `instanceof IGunOperator`
  而不是 `IGunOperator.fromLivingEntity` 的无检查转型，索引查询失败/实体未被 mixin 时返回中性值并只记一次 WARN。
  另提供 `beginSelfFlashPass()/endSelfFlashPass()` 成对控制 `MuzzleFlashRender.isSelf`/`ShellRender.isSelf`。
- 🔧 **第一人称渲染归属握手（可选、默认不影响任何行为）**：`YsmCompat.setExternalGunRender(true)`
  让外部渲染器声明「这一帧的枪我自己画」，`FirstPersonHandsAndItemsRendererMixin` 的视模分支据此早退，
  避免与 YSM 在模型定位点上 `renderStatic` 出的枪叠成两把；标志由 `END_CLIENT_TICK` 每 tick 复位，
  YSM 漏掉 `finally` 最多多抑制一帧。新增客户端配置 `YsmCompat`（`tacz-client.toml`，默认 `true`）。
- 📄 **YSM 侧尚未恢复 compat**：ModernYSM `port/26.3` 仍把 `rip/ysm/compat/**` 排除出编译
  （`TacCompat.isLoaded()` 恒 false），因此**装在一起现在仍没有枪械动画**；本次只是把 TaCZ 这侧
  的契约与门面备好，迁移清单见 [`YSM_COMPAT.md`](YSM_COMPAT.md)。**不构成「YSM 已适配完成」。**
- ✅ `python3 scripts/verify_ysm_compat.py`（11 项）通过：核对 YSM 1.20.1 用到的每个 TaCZ 符号仍存在、
  枪械 `ItemModel` 确实透传 `ItemDisplayContext`、握手是可选且每 tick 失效、配置项默认开。
- ✅ GitHub Actions 对 `0a7cd08`：`build`（run `39681806932`）与 `compile-check`（run `39681806929`）成功。
  **未实机验证**：YSM 与 TaCZ 从未一起进游戏跑过，且 YSM 侧 compat 仍是 stub。

## 1. 引擎适配（Mojang 26.3）

- ✅ 渲染底层 `com.mojang.blaze3d.*` → `com.mojang.renderpearl.*` 包迁移；`PoseStack#mulPose → rotate`、
  `BindGroupLayout#withSampler → withUniform(COMBINED_IMAGE_SAMPLER)`、`TextureTarget` 新构造、
  `RenderPipeline` 显式 `ColorTargetState` 等 API 改名（明细见移植指南 §2.2）。
- ✅ 第一人称渲染随 vanilla 拆分：`ItemInHandRendererMixin` → `FirstPersonHandsAndItemsMixin`（收枪保持）
  + `FirstPersonHandsAndItemsRendererMixin`（viewmodel 接管）。
- ✅ Render pass 归属倒置：`FeatureRenderDispatcher.prepareFrame` + 静态 `renderAllFeatures(RenderPass, PreparedFrame)`；
  `RenderSystem.output*TextureOverride` 删除后 `ScopeFinalOverlayState` 自建 pass；世界高模改挂
  `LevelRenderer#executeSolid` RETURN 复用传入的 pass。
- ✅ 着色器改 shaderc/SPIR-V 方言：`#moj_import → #include`、显式 `layout(location)`、`OIT_ALPHA_ONLY` 条件同步。
- ✅ 自定义管线预热（`ScopePipelinePrewarm`）：26.3 管线按需异步编译，未预热时 Iris 首次开镜 NPE。
- ✅ `swing(hand, SwingAnimation, boolean)`、`invulnerableTime` 私有化（新 `DamageCooldownUtil` 同时清 `lastHurt`）、
  `InputConstants` 键位常量、`Blaze3D.openUri(URI)`、`AbstractPackMetadataResources`、`Pack.ResourcesSupplier` 新签名、
  `FriendlyByteBuf#readMap/writeMap` 移除（`BufMapCodec`）、方块 `codec()` 移除、`PushReaction.POPPED`。
- ✅ 方块战利品表迁移到 26.3 schema（`condition/function → type`、`match_block`）；第三方枪包注入 loot 运行期迁移。
  顺带移除 `AbstractGunSmithTableBlock` 的手工半边掉落（26.2 起就会双掉落）。

## 2. Iris 26.3 适配

- ✅ 光影下 `tacz_ScopeMaskMode` 判定重做：`GlRenderPipeline#info()` 被删后不再按管线身份反查，改为
  `FrontendRenderPass → boundPipeline → uniforms()` 声明表按名判定（新 `IrisFrontendRenderPassMixin`），
  mode 2 由标记采样器 `ScopeMaskMode2Sampler` 区分。
- ✅ `GlCommandEncoder#trySetup → setupDraw`、`ExtendedShader#iris$setupState` 新签名两处 hook 随改名跟进
  （旧名 `require=0` 会静默不装）。
- ✅ 掩码 pass 绑空雾 UBO：修水下/失明开镜不裁剪（vanilla 亦受影响）。
- ✅ 高模 GPU 路径每骨骼强制 program rebind，修光影下法线只在开枪瞬间正确。

## 3. 行为修复（26.3 实机发现）

- ✅ JEI 刷新改用 `Internal.restartJei()`，不再丢服务端同步的 vanilla 型配方。
- ✅ 枪匠台配方材料改惰性解析：旧语法第三方枪包不再导致 `RECIPE` 注册表加载失败、进不去存档。
- ✅ 高模第一人称：消费点回到 `renderAllFeatures` 内 executeSolid 之后（修朝向漂移 + 光影下拉伸成片）；
  提交时预加载贴图（修首帧回退 collector）；collector 回退路径也套镜内裁剪。
- ✅ 专服：把 `minecraft:*` 与 `tacz:*` 配方序列化器登记进 Fabric 配方同步（`RecipeSynchronization`），
  服务端不装 JEI 时客户端 JEI 也能看到 mod 的 crafting 配方、枪匠台材料不再是空槽位。**服务端也需更新到本 build。**
- 🔧 **（CI 编译通过，未实机验证）** 枪包 lang 文件保底（`GunPackLangCompat`）。玩家反馈"装了某些枪包后游戏变英文、所有文字变成 `item.xxx`"，
  复现条件收敛为 **Enlisted Gun Pack v1.2.1.3**：其 `assets/ww/lang/en_us.json` 少一个逗号
  （`"ww.gun.p38.desc"` 行末）。26.3 起 vanilla `ClientLanguage.loadFrom` 去掉了 26.2 里逐命名空间的
  `catch (Exception)`（只剩 `appendFrom` 里的 `catch (IOException)`），任一 lang 文件的 `JsonSyntaxException`
  会一路抛到 `LanguageManager.onResourceManagerReload`，后者只记一条
  `WARN Unable to load languages: [en_us, zh_cn] (com.google.gson.JsonSyntaxException: ...)`
  就跳过 `Language.inject` —— 于是**整局游戏**退回 jar 内置 en_us，没有弹窗。26.2 同一个包只会
  `Skipped language file` 跳过该文件。这是**枪包缺陷 + vanilla 行为变更**，不是本 mod 的 bug；
  本 mod 的 47 个 lang 文件全部严格合法。
  处理：`DelegatingPackResources` 对 `lang/*.json` 的 `getResource`/`listResources` 套一层
  `GunPackLangCompat`——先按 vanilla 同样的宽松规则解析，合法则**原字节原样放行**；
  不合法（少逗号、多逗号、根不是对象、空文件、值不是字符串……）则用容错扫描救回能救的
  `"key": "value"` 条目、重新序列化为严格 JSON，并在日志里以 `[GunPackLang]` 开头的 WARN 指出是哪个枪包
  （`<namespace> (<zip/目录名>)`）的哪个文件、Gson 原始错误、保留/丢弃条目数。属于**绕过**（bypass），
  不修改枪包文件；根治仍需枪包作者补上逗号。
  已做的验证：GitHub Actions `build` 工作流对 commit `f52dab8` 跑完整 `./gradlew build` 成功
  （含 mixin 配置完整性 / 中英语言键齐平 / 版本一致性三项静态校验，并产出 jar artifact），
  `compile-check` 亦通过；另用 ECJ 3.39 + Gson 2.11 在沙箱里单独运行了 `GunPackLangCompat`
  （MC 类型用桩替代）——Enlisted 的 `en_us.json` 6 条全部救回，vanilla 同款 `new Gson().fromJson`
  能接受输出；仓库自带 47 个 lang 文件全部走原样放行分支。**没有人在 26.3 实机上跑过。**
  实机验收：放入原版 Enlisted zip 进入游戏，语言应保持中文；`latest.log` 应出现一条
  `[GunPackLang] Language file ww:lang/en_us.json of gun pack ww ([TaCZ] Enlisted Gun Pack v1.2.1.3.zip) ...`
  的 WARN，且**不再**出现 `Unable to load languages`。

## 4. 兼容层变化

- 🔧 **26.3 移植期曾因上游无构件而禁用的三项兼容已全部回补（2026-09-30 核实并恢复编译，待实机复验）**：
  - **REI** `26.3.823` + **Architectury** `22.0.3`（正式版）
  - **Zoomify** `2.16.3+26.3`（正式版）
  - **Shoulder Surfing Reloaded** `26.3-5.2.0+fabric`（正式版）
- 🔧 **ModMenu** 已从 `21.0.0-beta.1` 升级至 26.3 正式版 `21.0.0`，**Cloth Config** 升级至 `26.3.159`；**Carry On** 上游已发布 26.3 正式版 `2.12.0`（本模组 `@Pseudo` mixin 与反射签名无需改动即可兼容）。
- 🔧 **JEI** `31.8.0.48`、**PAL** `1.2.7+26.3` 目前仍为 **beta** 通道，正式版发布后需重新钉版本。
- **Voxy** 暂无 26.3 构件，其 mixin 走 `@Pseudo` 字符串目标，保留未验证；26.2 线起已禁用的 KubeJS / Controllable / Accelerated Rendering 在 26.3 仍无 Fabric 构件，保持门面禁用态。

## 5. 已知未验证 / 未做

- Sodium 0.9.2+mc26.3 下的高模与 PIP 未专项复测（Iris 测试均带 Sodium，无独立矩阵）。
- 世界语境 GPU 高模烘焙（`MeshGpuWorld`）继承 26.2 状态，26.3 未单独跑 `MESH_LOADER.md` §5.2-bis 矩阵。
- 26.3 OIT 对半透明附件/枪口火光的影响未系统评估（实机未见异常，但没有专门看）。
