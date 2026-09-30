# TaCZ Refabricated 26.3 R1-hotfix release notes（草稿，尚未发布）

**构建元数据：`1.1.8+fabric.26.3.R1-hotfix`**

**基线：`origin/26.2(main)` R3-hotfix2（`c7c3c55` 之前的 26.2 源码）→ 本线 `26.3`**

**环境：Minecraft 26.3 · Fabric Loader 0.19.5+ · Java 25+ · Fabric API 0.160.7+26.3 · Forge Config API Port 26.3.0+**

> 状态（2026-09-29）：R1 基础版编译/CI 全绿；下列标 ✅ 的基础版条目已由维护者在 26.3 实机验证（2026-09-18 ~ 09-21，
> 无光影 + Iris 光影 + 专服三种环境）；R1-hotfix 新增项标 🔧，仅 CI 编译通过，未实机验证。**尚无可下载构建。**
> 姊妹项目（NeoForge）移植指南：[`lineage/PORT_GUIDE_26_3_FOR_RENOVATED_NEOFORGE_20260921.md`](lineage/PORT_GUIDE_26_3_FOR_RENOVATED_NEOFORGE_20260921.md)。
> 版本命名沿用本仓历史：hotfix 直接追加在 `R1` 后（`R1-hotfix`，不另起 R2；无序号的首个 hotfix 不加数字）。

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

---

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
