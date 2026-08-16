> **历史状态已被 26.2 R2 取代（2026-08-16）：** 本归档中“Carry On 无 26.2 构建/保持 exclude”的结论只记录当时核查，现已由公开的 Carry On Fabric 26.2 2.11.0 和 R2 反射/mixin 兼容层取代。请以 [`../CARRYON_COMPAT.md`](../CARRYON_COMPAT.md) 为当前状态；不要把本归档当作发布兼容性结论。

# TACZ-26.2-V5-source 审计报告（2026-07-25）

> 审计对象：`q14433686-arch/TACZ-26.2-V5-source`，提交 `e4ffee2ba9f5c24e2bd86b941b069f847208650f`。  
> 结论等级：**不可作为“已完成的 26.2 移植版”发布**。服务端核心可启动；客户端已通过加载器、Mixin、OpenGL/Xvfb 与资源重载的前段验证，但受 1 GiB 沙盒内存与软件渲染器限制，未完成到主菜单/实机画面验证。第一人称、手臂、完整枪/配件画面仍必须在真实 GPU 环境的客户端—服务端联机中验收。

## 1. 文档与声明核对

### 1.1 原仓库没有可核对的项目文档

* Git 跟踪文件中**没有** `README*`、`LICENSE*`、`gradle.properties`、测试源码或任何 `AUDIT_REPORT_verified.md`。
* GitHub README API 对本仓库返回 404；仓库只有一个 initial commit。
* `build.gradle` 却引用不存在的 `AUDIT_REPORT_verified.md §7`、`NEXT_STEPS Phase3`，并同时声称“原 sourceSets exclude 块已移除”，但第 260–278 行仍有实际 `sourceSets.main.java.exclude`。
* `fabric.mod.json` 声明 `GPL3 / CC BY-NC-ND 4.0`，而初始仓库没有许可证文本；`jar { from("LICENSE") }` 因而没有可打入的许可证文件。

因此，仓库内关于“已核查”“已恢复”“已审批”或“兼容”的注释**不能当作证据**；本报告只以实际构建、运行日志、26.2 jar/API 和源码调用链定级。

### 1.2 已发现的基础交付缺失

| 分类 | 初始事实 | 影响 | 当前处理 |
|---|---|---|---|
| 构建元数据 | `gradle.properties` 缺失，首个 Gradle 配置即报 `unknown property mod_version` | 不能构建 | 已补齐并固定 26.2 依赖版本 |
| Java/Wrapper | Gradle 9.5.1 不能在 JDK 11 上运行；Git 中 `gradlew` 为非可执行 | 新 clone 后 `./gradlew` 直接 Permission denied | 验证使用 Temurin JDK 25.0.3 + `bash gradlew`；发布前应修正可执行位 |
| Access Widener | 构建引用的 `tacz.accesswidener` 不存在 | Loom 无法配置 MC | 已重建为 26.2 `official` namespace，仅保留由编译器确认的 3 条访问项 |
| 二进制资源 | 初始 `src/main/resources` 有 **0 个 PNG**；缺枪/配件 geo、PNG、OGG、方块纹理、blockstates、语言、声音等 | 正好解释“空白图标/无模型”，不是紫黑缺失纹理的正常 fallback | 已从本仓库自带下载脚本指定的上游 `Sh1roCu/TACZ-Refabricated` `1.21.1@58a1c510…` 仅补入缺失的 `assets/tacz/**`、`icon.png`、`logo.png` |
| 资源可追溯性 | 原 `download-resources.sh` 以后台 `curl -sL` 下载、没有 `--fail`/哈希/状态校验，且只覆盖一部分资源 | 会把 404 HTML 当资源；也无法补基础纹理 | `RESOURCE_IMPORT_MANIFEST.tsv` 记录本次 2,594 个补入文件的 SHA-256 与来源提交；脚本本身仍需重写 |
| 自动化测试 | `test NO-SOURCE` | 没有回归保护 | 本次只能以双端冒烟、静态资源清单和编译验证；应补 GameTest/集成冒烟脚本 |

## 2. 用户所报现象：证据与归类

### A. 已定位且已修复的直接原因

1. **枪、配件、工作台等图标/模型空白（Critical）**
   * 初始资源树只有 JSON/Lua，PNG 数量为 0；没有默认枪包所需的 geo、UV、slot 图、音频，也没有方块纹理。
   * 资源导入后：PNG 868、OGG 1,352，`assets/tacz` 文件数 3,470；PNG/OGG 魔数检查均为 0 个错误。
   * 这不是“26.2 写法改了”本身，而是发布源包缺少绝大多数二进制资产。资源缺失足以造成 GUI 空白、模型索引为空或 fallback。

2. **已放置的工作台、靶子、雕像不可见（Critical）**
   * `AbstractGunSmithTableBlock#getRenderShape()` 返回 `RenderShape.INVISIBLE`。
   * `GunSmithTableRenderer`、`TargetRenderer`、`StatueRenderer` 虽然存在，但初始客户端入口没有任何 `BlockEntityRendererRegistry.register` 调用。
   * 26.2 实际 Fabric API 的签名已由本地 `javap` 确认：`BlockEntityRendererRegistry.register(BlockEntityType, BlockEntityRendererProvider)`。
   * 已在 `TaCZFabricClient` 注册三个 BlockEntity renderer。未注册时方块仍能交互（故“能打开”），却必然不可见。

3. **默认枪包信息解析失败（High）**
   * 导入的 `gunpack_info.json` 使用 `//` 注释；Gson 2.14 默认严格模式。
   * 首次 Xvfb 客户端冒烟实际报：`Couldn't parse pack info ... MalformedJsonException ... setStrictness(LENIENT)`。
   * 已将 `PackInfoManager` 改为 `JsonReader.setStrictness(Strictness.LENIENT)`，与已存在的 `ResourceScanner` 宽松解析策略一致。
   * 修复后的客户端资源重载日志未再出现该 PackInfo 解析错误。

4. **开发客户端 384 MiB 堆必然不足（High）**
   * 原 `loom.runs.client.vmArg "-Xmx384m"` 在完整默认包首轮重载中实测 `OutOfMemoryError: Java heap space`。
   * 已提升客户端开发运行堆至 768 MiB。真实用户环境应至少以 768 MiB（建议 1–2 GiB）验证；本沙盒总内存约 1 GiB，768 MiB 堆 + llvmpipe 最终被 cgroup SIGKILL（exit 137），这不是 Java 级的源码异常。

5. **JEI/REI/Mod Menu 入口丢失（High）**
   * 初始源码有 `GunModPlugin`、`REIClientPlugin`、`REIPlugin`、`ModMenuApiImpl`，但 `fabric.mod.json` 只留下 `main/client` entrypoint，集成类不会被相应 mod 发现。
   * 已恢复 `modmenu`、`jei_mod_plugin`、`rei_client`、`rei_common` entrypoint。JEI/REI 本身未放入本次运行时；仅完成编译、元数据和基础客户端加载验证，仍需带 JEI/REI 的实机回归。

### B. 已确认的风险/未完成，不应宣传为已实现

| 分类 | 源码证据 | 结论 |
|---|---|---|
| 第一人称枪 | `TaczDynamicItemModel` 与 `AnimateGeoItemRenderer#renderFirstPerson` 走 26.2 `SubmitNodeCollector`，且本地 26.2 `ItemInHandRenderer#submitHandsWithItems` 签名已用 `javap` 核对 | 代码路径存在，不能据此等同于画面正确；完整客户端没跑到可截图画面，仍为 **待实机验收** |
| 第一人称手臂 | 新路径 `LeftHandRender/RightHandRender#extract` 会调用 `AvatarRenderer#renderLeftHand/renderRightHand`；26.2 jar 中这两个方法存在 | 旧的无 collector 重载仍是刻意空实现；只能证明新路径可编译、可加载，不能证明模型定位正确 |
| 第三人称副手/手臂 | `ItemInHandLayerMixin` 在主手为枪时取消左手 `submitArmWithItem`，随后调用的 `HumanoidOffhandRender#renderGun` 是完整 no-op/TODO | **高风险且与“持枪手臂/动作异常”高度相关**；不能称已适配。需先恢复有 collector 的副手提交或停止取消 vanilla 路径，再做左右手/左右利手回归 |
| 配件 | 新 `AttachmentRender#extract` 走 `submitAttachment`；但旧 `renderAttachment` 仍调用已 no-op 的 `BedrockModel#render` | 主新路径可用性待画面验证；旧 delegate fallback 会丢失配件。不能称“配件已完整实现” |
| 抛壳/枪口火焰/激光/文字 | `ShellRender` 仍调用 no-op legacy render；`MuzzleFlashRender`、`BeamRenderer`、`TextShowRender` 标有 TODO/no-op | **未实现或不可信**，应在功能表明确标为未适配 |
| 模板工作台功能 | 服务端加载 1,768 recipes；`AbstractGunSmithTableBlock` 实际能 openMenu，`GunSmithTableMenu#doCraft` 有扣料/产物逻辑 | 服务端基础可用，但没有客户端菜单点击/联网合成回归，不能证明用户看到的界面功能完整 |
| 资源包 JSON | 默认包 156/1,142 个 JSON 含注释；索引扫描走宽松 `ResourceScanner`，但个别直接 Gson 读取点仍可能严格 | 已修 `gunpack_info.json` 路径；外部枪包仍应加入 JSON-with-comments 回归 |

## 3. 26.2 API 对照（非“凭注释迁移”）

本地从 Loom 下载的 `minecraft-merged-deobf-26.2.jar` 用 `javap -p` 复核：

* `ItemInHandRenderer.submitHandsWithItems(float, PoseStack, SubmitNodeCollector, LocalPlayer, int)` 存在；当前 mixin 的目标签名可加载。
* `ItemInHandLayer.submit(PoseStack, SubmitNodeCollector, int, ArmedEntityRenderState, float, float)` 和 `submitArmWithItem(...)` 存在。
* `AvatarRenderer.renderRightHand/LeftHand(PoseStack, SubmitNodeCollector, int, Identifier, boolean)` 存在。
* `FeatureRendererType.create(String)`、`RenderTypeFeatureRenderer#getVertexBuilder(RenderType)`、`BlockEntityRendererRegistry.register(...)` 存在。
* 已用实际 `compileJava` 反推并恢复 AW 中的 `LivingEntity.jumping`、`MultiPlayerGameMode.ensureHasSentCarriedItem()`、`Minecraft.startUseItem()`；这三项不是猜测。

这只说明目标 API/签名成立，**不等同于几何、动画、渲染顺序或视觉效果通过**。

## 4. 兼容 mod 状态（截至 2026-07-25）

| 项目 | 26.2 发行/来源核查 | 本源码实际状态 | 建议 |
|---|---|---|---|
| Fabric Loader / Fabric API / Forge Config API Port / Cloth / Mod Menu | 本次实测加载：Loader 0.19.3、FAPI 0.155.2+26.2、FCAP 26.2.1、Cloth 26.2.155、Mod Menu 20.0.1 | 基础运行通过；Mod Menu 入口已恢复 | 必装：Loader/FAPI/FCAP；Cloth/Mod Menu 可选 |
| JEI | Modrinth 有 26.2 Fabric `30.13.0.86` | API 编译通过，入口原先缺失、现已恢复；未带 JEI 冒烟 | 将 JEI 作为可选客户端测试矩阵项 |
| REI | 官方 Maven 有 `26.2.820` API/runtime | API 编译通过，入口原先缺失、现已恢复；未带 REI 冒烟 | 同上；不要宣称已经实测 |
| Player Animation Library（zigythebird） | Modrinth 有 `1.2.5+mc.26.2` | `PlayerAnimatorCompat` 已写 PAL 迁移层；旧 KosmX API 源文件仍被 exclude | 采用 PAL 1.2.5，测试第三人称动作；“PAL 不兼容/无 26.2”的旧注释与实际发行冲突 |
| Iris | Iris 1.11.2 有 Fabric 26.2 发行 | `CompatRegistry` 把 Iris 初始化整段注释；`IrisCompat` 也把 Sulkan 路径写成 TODO/no-op | 不能说支持 Iris/Sulkan。以 Iris 1.11.2 + Sodium 0.9.0 做专项回归；源中“26.2 Iris 不可用”已过时 |
| Sodium | 0.9.0 有 26.2 Fabric 发行 | 未列为本项目依赖/测试对象 | 推荐作为性能测试基线；其 Vulkan 支持仍是实验性 |
| ImmediatelyFast | 1.16.2 有 26.2 Fabric 发行 | `ImmediatelyFastCompat` 是 no-op，且注释错误称无 26.2 | 可安装但 TACZ 专项兼容功能当前关闭；不要宣传已适配 |
| Shoulder Surfing Reloaded | 5.0.7 有 26.2 Fabric 发行 | 检测/门面存在，但内部兼容为 stub，且旧注释称未发布 | 可作为替代视角 mod；需实机准星回归 |
| AcceleratedRendering-reFabricated | Modrinth 26.2 Fabric 查询为 0 个版本 | `ARCompat` 强制 `LOADED=false`，核心实现及 mixin 被 exclude | 保持禁用，不要承诺兼容；无明确同类作者推荐，使用 Sodium/ImmediatelyFast 作为不同机制的性能替代 |
| Controllable | ~~原目标没有可核实 26.2 Fabric 条目~~ **已过时，见下方更正** | `ControllableCompat` 纯 no-op，内层被 exclude | **改：直接对 Controllable `0.26.1+26.2` 恢复兼容**，不必换 Controlify |
| Carry On | 原 Carry On 26.2 Fabric 查询为 0 个版本 | 完整兼容包与 mixin 被 exclude | 功能替代可用 **Serverside CarryOn**（服务器侧搬运生物/方块），但不是 API 等价替换 |
| KubeJS | 26.2 Fabric 查询为 0 个版本 | 19 个 KubeJS 源文件被 exclude | 保持禁用，等待官方 26.2 Fabric；不要假装事件/脚本可用 |
| OptiFine | 无 Fabric 26.2 合理目标 | 整包被 exclude | 不支持；用 Sodium + Iris（OpenGL）或单独评估 Vulkan 路线 |
| SimpleBedrockModel | 原外部依赖被注释；项目内塞入 event/renderer stub | stubs 只能让编译通过，不等价原库 | 这是第一人称/动画验证的核心风险，必须写入发行说明 |

链接见报告末尾“外部核查来源”。“替代”只表示同类用途，不表示与旧 mod API 二进制兼容。

### 4.1 【更正 · 2026-07-26】Controllable 已有官方 26.2 Fabric 版

本表原判定「Controllable 没有可核实的 26.2 Fabric 条目」并建议改用 Controlify，
**该结论已过时**。MrCrayfish 官方仓库实际已发布 **`0.26.1+26.2`**（2026-07-08），
changelog 明确写着：

```
✨ Update to Minecraft 26.2   (482922b)
🐛 Fix broken mixins on Fabric (b169646)
🐛 Fix invalid aw            (a0c993c)
```

来源：<https://github.com/MrCrayfish/Controllable/releases>

**结论**：要恢复手柄兼容，**直接对 Controllable 0.26.1+26.2 做即可**，
不需要改投 Controlify、也不需要重写按键桥接层。原 `ControllableCompat`
的 API 形状大概率仍可沿用（需按该版本实际签名复核）。

> 本表其余「保持禁用」的判定（KubeJS / Carry On / AcceleratedRendering）
> 经 2026-07-26 复查**仍然成立**：
> - KubeJS 官网明示「Last supported Minecraft version is 1.21」
> - Carry On 官方最高 1.21.11，26.x 仍在等待
> - AcceleratedRendering-reFabricated 全部 release tag 停在 1.20.1 / 1.21.1
>   （最新 `1.0.11-1.21.1-alpha-fabric.1`，2026-07-04），无任何 26.x

## 5. 冒烟认证记录

### 环境

* MC 26.2、Fabric Loader 0.19.3、Fabric API 0.155.2+26.2、JDK Temurin 25.0.3。
* 客户端使用 Xvfb + Mesa llvmpipe/OpenGL；没有真实 GPU、音频设备、Microsoft/Mojang 登录令牌。
* 因此 narrator/flite、OpenAL、Realms 认证报错属于沙盒设施限制，不归为 TACZ 功能失败。

### 结果

| 变更后检查 | 服务端 | 客户端 | 结论 |
|---|---|---|---|
| 补构建元数据/AW | `build` 成功 | 启动前 API/Mixin 通过 | 构建门槛已打通 |
| 补资源 | 服务端成功导出并扫描 `tacz_default_gun`，加载 1,768 recipes，监听并 `stop` 正常退出 | 首轮发现 384 MiB Java OOM 与 PackInfo 严格 JSON 失败 | 发现并修复两个客户端阻断项 |
| JSON 宽松读取 + 768 MiB | 服务端正常 | Xvfb 客户端加载 mod、注册 FeatureRenderer、扫描默认枪包、进入 ResourceManager，成功创建多个纹理 atlas；没有再出现 PackInfo 解析错误；最终因 1 GiB cgroup + llvmpipe 被 SIGKILL (137) | **客户端部分通过；完整画面未认证** |
| BlockEntity renderer 注册 | 服务端正常启动/停止 | 客户端启动到 FeatureRenderer 注册、默认包扫描且没有 `BlockEntityRendererRegistry` 类型/链接错误；短时冒烟由 timeout 中断 | 代码/API 通过；仍待真实画面确认 |
| JEI/REI/Mod Menu entrypoint 恢复 | 服务端正常启动/停止 | Mod Menu 在运行环境加载，客户端前段无 entrypoint 错误；JEI/REI 未加入 runtime | metadata 已恢复，第三方集成仍待专项测试 |

**认证门禁：** `build` 和 dedicated server 为 PASS；client bootstrap/PACK discovery 为 PASS；client full resource reload、主菜单、单人/联机画面、第一/三人称、工作台 GUI/合成、JEI/REI、shader 的认证均为 **BLOCKED/NOT YET PASS**。

## 6. 本次变更清单

1. 新增 `gradle.properties`（26.2 版本固定、JDK 25/构建参数）。
2. 新增最小且已编译验证的 `src/main/resources/tacz.accesswidener`（official namespace）。
3. 补入 2,594 个缺失资产并写入 `RESOURCE_IMPORT_MANIFEST.tsv`。
4. `PackInfoManager` 改为宽松 JSON 读取。
5. 客户端开发堆由 384 MiB 调整为 768 MiB。
6. 在 `TaCZFabricClient` 注册工作台、靶子、雕像的 BlockEntity renderer。
7. 在 `fabric.mod.json` 恢复 Mod Menu、JEI、REI entrypoint。

未做“为了让编译通过而删业务源码”的处理；原有 exclude 仍完整列入上表并需后续逐项迁移。

## 7. 发布前必须完成的下一步（按优先级）

1. **真实 GPU 双端联机回归**：客户端给枪、配件、三种工作台、靶子、雕像；第一/第三人称、左右利手、双手、换枪、瞄准、开火、装填、装配、丢弃、重连；截图/录屏留档。
2. 修 `ItemInHandLayerMixin` 取消左手却调用 no-op `HumanoidOffhandRender` 的路径；验证左/右手模型与动作。
3. 完成 `ShellRender`、枪口火焰、激光、文字显示和所有 legacy `BedrockModel.render` 调用的 collector 迁移。
4. 给默认包和外部枪包建立 JSON-with-comments/严格 JSON 回归；重写 `download-resources.sh` 为失败即退出、哈希锁定、完整资产清单。
5. 带 JEI、REI、PAL、Iris/Sodium、ImmediatelyFast、Shoulder Surfing 分别启动测试，不要只凭存在 Maven artifact 宣称兼容。
6. 删除失实注释、补 README、LICENSE/资产许可与明确的“已测/未测”支持矩阵；修复 `gradlew` 可执行位。
7. 增加 GameTest/集成测试，至少断言：枪包索引数、模型/纹理资源存在、BlockEntity renderer 注册、工作台菜单和一次服务器合成。

## 8. 外部核查来源

* Fabric Loader 26.2 元数据：<https://meta.fabricmc.net/v2/versions/loader/26.2>
* Fabric API Maven 元数据：<https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml>
* Forge Config API Port Maven 元数据：<https://raw.githubusercontent.com/Fuzss/modresources/main/maven/fuzs/forgeconfigapiport/forgeconfigapiport-fabric/maven-metadata.xml>
* Cloth Config Maven 元数据：<https://maven.shedaniel.me/me/shedaniel/cloth/cloth-config-fabric/maven-metadata.xml>
* JEI 26.2：<https://modrinth.com/mod/jei>
* Iris 1.11.2+26.2 Fabric：<https://modrinth.com/mod/iris/version/1.11.2+26.2-fabric>
* Sodium 0.9.0 26.2：<https://modrinth.com/mod/sodium/version/mc26.2-0.9.0-fabric>
* Player Animation Library：<https://modrinth.com/mod/player-animation-library/versions>
* ImmediatelyFast：<https://modrinth.com/mod/immediatelyfast?version=26.2&loader=fabric>
* Controlify：<https://modrinth.com/mod/controlify>
* Serverside CarryOn：<https://modrinth.com/mod/serverside-carryon/versions>
* 上游资源来源：<https://github.com/Sh1roCu/TACZ-Refabricated/tree/1.21.1>
