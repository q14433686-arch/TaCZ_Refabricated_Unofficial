# 玩家日志 `mclo.gs/39JqB2p` 可见 bug 五件套 · 六线移植指导（2026-09-08）

> **状态**：refab 26.2(main) 侧已落码（commit `1aca7c7`，CI compile-check + 全量 build 均通过，
> 编译日志已回推 `build-reports/compile-java.log`），**实机未验证**。
> 其余五线（refab 26.1.2 / 1.21.11，renov 26.2 / 26.1.2 / 1.21.11）为 **OPEN**：
> 五份补丁在**各目标分支的真实 sparse worktree** 上 `git apply --check` + 实落通过（§4），
> 但那五条线**均未经 CI 编译**，落码后必须各自跑一遍 compile-check 才算过第一关。
> **性质**：全部是「同代码、同机制」的移植件，不涉 GPU/纪元差异；每一项都能被源码坐实，
> 与「开光影世界全透明」的归因**无关**（那个问题另行跟进，玩家侧排查文案已给出）。
> 账本行：`HANDOFF_LEDGER.md` #17。

---

## 0. 起因

玩家（macOS 26.2 / Apple M4 / Sodium 0.9.1 + Iris 1.11.2 / TACZ `1.1.8+fabric.26.2.R3`）
反映「开光影世界全透明」，日志里**没有任何与该症状对应的报错**；但日志里有五处**能被源码证实**的
可见问题，维护者裁定「先把可见的 bug 修掉，透明的事之后再定」。本文把这五项按分支拆开。

---

## 1. 五项修复各是什么（机制一句话 + 证据）

| # | 日志现象 | 根因 | 修法 | 证据 |
|---|---|---|---|---|
| A | ~250 行 `Recipe tacz:… can't be placed due to empty ingredients`（含 lrtactical） | 1.21.11+ `RecipeManager#finalizeRecipeLoading` 对 `!isSpecial() && placementInfo().isImpossibleToPlace()` 的配方逐条 WARN；我们返回 `NOT_PLACEABLE` 且没覆写 `isSpecial` | `GunSmithTableRecipe` 加 `isSpecial()=true` | vanilla 26.2 `RecipeManager` :102（Renekovski/26.2-mcp）；NeoForge 26.2.x/26.1.x/1.21.11 的 `RecipeManager.java.patch` 只加 `recipes.order(...)`，**不碰这条 WARN** ⇒ Neo 线同样中招。`isSpecial=true` 的另一处作用点 `unpackRecipeInfo` 只遍历 `recipe.display()`（我们为空）⇒ 无副作用。工作台自身的材料校验不走 `placementInfo`。 |
| B | `Couldn't load tag tacz:interact_key/whitelist as it is missing following references: minecraft:boat, minecraft:chest_boat` | 1.21.2+ 船已拆成逐木种实体；`TagLoader#tryBuildTag` 对缺失引用是**整条标签丢弃**；`SyncConfig` 白名单列表默认为空 ⇒ 村民/矿车/展示框/骆驼……**所有**靠该标签放行的交互键全灭 | `#minecraft:boat` + 十种 `*_chest_boat`/`bamboo_chest_raft`（后者标 `required:false`） | `TagLoader.java` :115–135（26.2-mcp）；misode/mcmeta `1.21.11 / 26.1.2 / 26.2` 三版 `#minecraft:boat` 内容一致、均不含 chest boat、也没有 chest-boat 标签 |
| C | `[TACZ Sound] Missing gun sound resource … sound=minecraft:p24_pi_golf17_stockskel_raise` | `glock_17.animation.json` raise 段引用不存在的音效；上游 MCModderAnchor/TACZ 与 Sh1roCu 1.21.1 同样带着这行（上游手误） | 删掉 raise 的 `sound_effects` 段（其余动画的音效都是 `tacz:glock_17/…` 形态） | 六线的该文件是**同一个 blob `82de964`**（refab 26.1.2/1.21.11 在 bundle jar 里，见 §3 注） |
| D | 六行 `Found perfect program match for minecraft:pipeline/entity_cutout: HAND_CUTOUT`（Iris WARN） | `IrisCompat.assignCommonEntityPipelinesToHandIfNeeded()` 试图把 vanilla ENTITY_*/ITEM_* 管线 `assignPipeline` 到 HAND；Iris 静态表早已预注册这些管线且**逐 draw** 分派（`HandRenderer.isActive()` ⇒ HAND_*），`assignPipeline` 对已注册管线直接抛 `Shader already assigned`，被我们吞掉 ⇒ 调用从未生效，只剩日志；若某 Iris 版本恰好没预注册则会把 vanilla 管线钉死成常量 HAND 程序（全局风险） | 删方法 + 全部调用点 + 两个 flag；`assignPipeline` 只留给 `tacz:pipeline/scope_*` | Iris `IrisPipelines.java` **三条分支逐字核对**：`1.21.11`（含 2025-03-17 `49a7b4a` 起的全部历史）、`26.1`、`26.2` 都是 `assignToMain(ENTITY_CUTOUT, p -> getCutout(p))` + `getCutout` 首判 `HandRenderer.INSTANCE.isActive()` + `assignPipeline` 抛 already assigned；`IrisApiV0Impl.assignPipeline` 先 `findBestMatch`（打 WARN）再进 `IrisPipelines.assignPipeline`（抛） |
| E | `[TACZ Scope] Mask enabled but no ocular geometry was registered this frame`（进世界 1 秒、空手时） | 诊断条件太宽（手部 pass + 功能开 + 清单空） | 加前置「主手是装了瞄具的枪」 | 仅 refab 26.2 / renov 26.2 有这条 WARN（其余四线的 `ScopeMaskRenderer` 没有这段） |

---

## 2. 六线适用性矩阵（2026-09-08 逐分支实拉核对，非记忆）

| 分支 | HEAD | A isSpecial | B 船标签 | C glock17 | D assignCommon | E 掩码 WARN |
|---|---|---|---|---|---|---|
| refab 26.2(main) | `1aca7c7` | **已落** | **已落** | **已落** | **已落**（3 调用点：ShellRender / MuzzleFlashRender / PolyMeshGpuRenderer） | **已落** |
| refab 26.1.2 | `3015e8b` | 需要（无 isSpecial） | 需要（仍是 `minecraft:boat`/`chest_boat`，日志同款报错必现） | 需要（bundle 内 blob 同款）→ **源码覆盖件** | 需要（1 调用点：GunItemRendererWrapper；方法 7 条管线） | 不适用 |
| refab 1.21.11 | `6db3af9` | 已有（R10 `1d8174db`） | **半有**：R10 已改 `#minecraft:boat` 但**没补 chest boat** → 补 10 条可选项 | 需要 → **源码覆盖件**（该分支 build.gradle 已显式枚举 src 覆盖，机制现成） | 需要（1 调用点；方法 5 条管线含 `ITEM_ENTITY_TRANSLUCENT_CULL`） | 不适用 |
| renov 26.2 | `61479e8` | 需要 | 不适用（Neo 线一直是逐木种显式列出，含 chest boat，无缺失引用） | 需要 | 需要（3 调用点：GunModClient `enqueueWork` / GunItemRendererWrapper / PolyMeshGpuRenderer + `ShaderCompat` 门面转发） | 需要 |
| renov 26.1.2 | `e4a31a2` | 需要 | 不适用 | 需要 | 需要（2 调用点 + `ShaderCompat` 门面 + 类 javadoc `{@link}` 去链） | 不适用 |
| renov 1.21.11 | `18ee726` | 需要 | 不适用 | 需要 | 需要（2 调用点 + `ShaderCompat` 门面；方法 3 条管线） | 不适用 |

**Neo 线 D 项的一个额外坑**：renov 三线在 `GunModClient.onClientSetup` 的 `enqueueWork` 里**启动期**就调一次
（不在手部 pass 内），所以 Neo 玩家的日志里那几行 WARN 会出现在**开光影之前**，更容易被误读；
而且 renov 26.2 / 1.21.11 的版本没有 `attempted` 守卫，理论上每次调用都重新走一遍 `findBestMatch`。

---

## 3. 补丁文件与应用方法

五份补丁在 `docs/patch/`，均由**目标分支真实 worktree** 上的 `git diff HEAD` 产出，
已在**干净的**目标 HEAD 上 `git apply --check` 通过：

| 目标 | 文件 | 触及 |
|---|---|---|
| refab 26.1.2 | `2026-09-08-visible-bugs-39JqB2p-refab-26.1.2.patch` | `GunSmithTableRecipe` / `whitelist.json` / `IrisCompat` / `GunItemRendererWrapper` |
| refab 1.21.11 | `2026-09-08-visible-bugs-39JqB2p-refab-1.21.11.patch` | `whitelist.json` / `IrisCompat` / `GunItemRendererWrapper` |
| renov 26.2 | `2026-09-08-visible-bugs-39JqB2p-renov-26.2.patch` | 上表 A/C/D/E 全部 8 文件 |
| renov 26.1.2 | `2026-09-08-visible-bugs-39JqB2p-renov-26.1.2.patch` | A/C/D 6 文件 |
| renov 1.21.11 | `2026-09-08-visible-bugs-39JqB2p-renov-1.21.11.patch` | A/C/D 6 文件 |

```bash
# 在目标分支干净 worktree 里：
git apply --check docs/patch/2026-09-08-visible-bugs-39JqB2p-<目标>.patch && \
git apply         docs/patch/2026-09-08-visible-bugs-39JqB2p-<目标>.patch
```

**refab 26.1.2 / 1.21.11 的 C 项要多做一步**（补丁里故意没带这个 600 KB 文件）：
这两条线的默认枪包在 `resources/tacz-26.1.2-source-and-resource-bundle.jar` 里，源码树没有
`glock_17.animation.json`；修法是放一份**源码覆盖件**，两条线的 `processResources` 都是
「`src/main/resources` 先于 `zipTree(bundle)` + `DuplicatesStrategy.EXCLUDE`」（1.21.11 还显式枚举了覆盖），
所以直接从 26.2(main) 取修好的那份即可：

```bash
git checkout 1aca7c74f8fd14f64e6a348e235eb87dfdfa8534 -- \
  src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/animations/glock_17.animation.json
grep -c golf17 src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/animations/glock_17.animation.json  # 期望 0
```

（26.2(main) 与 bundle 内的原文件是同一 blob `82de964`，所以这份覆盖件与 bundle 只差被删的那 5 行。）

renov 三线的 `glock_17.animation.json` 在源码树里（同 blob），补丁直接含这 5 行删除。

---

## 4. 各线落码后的检查项

1. **编译**：五线都没跑过 CI。refab 两线推到 `arena/**` 或分支本身即触发 compile-check；
   renov 三线的 CI 件见 `docs/ci/pending/`（若尚未上线，本地 `./gradlew compileJava`）。
2. **D 项的正向验证**（三线各一次、开光影）：第一人称抛壳 / 枪口火光 / 高模枪体在手部 pass 里
   仍正常受光影光照 —— 这是「Iris 逐 draw 分派」结论的实机对照；日志里不再出现
   `Found perfect program match … HAND_CUTOUT`。
3. **B 项**（refab 26.1.2 / 1.21.11）：日志不再有 `Couldn't load tag tacz:interact_key/whitelist`；
   对村民 / 矿车 / 展示框按交互键有反应。
4. **A 项**：日志不再有 `can't be placed due to empty ingredients`；工作台配方列表与合成不变；
   JEI/REI 的枪械工作台分类照常（它们不走 vanilla `RecipeManager`，从 `TableRecipeManager` 直接取）。
5. **C 项**：切到格洛克 17 不再打 `Missing gun sound resource … golf17`。

---

## 5. 纪律提醒

- 五线**全部未经实机验证**；写 changelog 时照 `AGENTS.md` 标「静态修复、待实测」。
- 版本号不动（不是发布）；若随下次发布合入，各线自行同步 README / 一致性脚本。
- **不要**顺手把 refab 26.2 的 IrisShaderCreatorMixin 开关/`void main` 收紧带进去 —— 那两项尚未定案，
  且与本件无关。

---

## 6. 续（2026-09-09）：§5 纪律项已定案 —— IrisShaderCreatorMixin 三件套在本线落地

§5 曾要求「不要顺手把 refab 26.2 的 IrisShaderCreatorMixin 开关 / `void main` 收紧带进去 ——
那两项尚未定案」。现已定案，并在 refab 26.2（本分支）落码。其它五线**继续沿用 §5 纪律**
（它们不携带 scope-mask 注入体系，是否移植由各线自行裁决，本件不代判）。

### 6.1 起因

玩家（Apple M4 + Sodium + Iris 1.11.2 + 任意 shaderpack）：「只留 TACZ 和 Iris，
开光影地形/世界即透明；移除 TACZ 即恢复」。两份日志（`2mmxQlg` 最小复现、
`39JqB2p` 全量包）共同特征：被注入的程序**全部编译通过**、无报错，
且世界程序的 `tacz_ScopeMaskMode` 运行期永远只会被写 0（分支永不执行）——
即注入体对世界程序零语义贡献，却改变了它们的源码文本。结合 20 处
`isUsingRenderPack()` / 12 处 `isHandRendererActive()` 调用点的穷举审计
（均为 RecoilDebug 日志、枪身光照/提交、瞄准门控 PiP/掩码、mesh-GPU 枪械路由，
无一改动世界绘制），注入作用域被收紧为 HAND-only。

### 6.2 改了什么（2 文件）

- `mixin/client/iris/IrisShaderCreatorMixin.java`（重写注入判定，注入体 GLSL 文本逐字节不变）：
  1. **HAND-only 作用域**：对 Iris 26.2 源码实读确认全部四条 `create*`
     （`create / createShadow / createFallback / createFallbackShadow`）都同步
     （同线程、无延迟）调用 `link`，且入口带 `(name, shaderKey)` —— 用两个 HEAD
     注入（`@Coerce Object` 接收 Iris 内部类，与 `IrisGlCommandEncoderMixin`
     同一手法）把上下文记进 ThreadLocal 单槽，`link` 的 ModifyVariable 消费后按
     `ShaderKey` 枚举名 `HAND` 前缀判定（program 名含 hand 为第二道保险，只多注不少注）。
     世界程序（地形/实体/天空/阴影/DH）保持与原生 Iris **逐字节一致**，
     连 dormant 的 uniform 写入都不再有（`IrisScopeMaskState` 查不到 location 即跳过）。
  2. **注释感知的 `void main() {` 定位**：替代 `indexOf("void main")` 首击命中。
     单遍剔除 `//`、块注释、双引号字面量，再验证 `( [void] ) {` 完整定义形状；
     找不到 fail-closed（原样返回 + 按 program 名一次性告警）。
     修掉两类静默事故：声明落进注释（无声失效）、声明与分支分家
     （未声明标识符 → 整个程序编译失败 → Iris 静默回退无光影 fallback）。
  3. **诊断**：每 link 一条 DEBUG（`name / key / 决策`），INFO 汇总行
     （10 秒节流：HAND 已注数 / 世界原样数 / 配置跳过 / 无 main 跳过 / fail-open 数）。
     无上下文（未来 Iris 改签名）时 fail-open 全量注入 + 一次性告警，不静默断瞄具裁剪。
- `config/client/RenderConfig.java`：新增 `ScopeMaskIrisInjection`（默认开）。
  关 = 注入全停（光影下瞄具裁剪同时失效），仅用于对照实验。

瞄具裁剪不受影响的依据：全部 `tacz:pipeline/scope_*_clipped` 管线都经
`IrisCompat.assignScopePipelineToHand` 显式钉在 `IrisProgram.HAND` 上
（Iris 侧按 ENTITY/GLYPH 格式落到 `HAND_CUTOUT* / HAND_TRANSLUCENT / HAND_TEXT*`，
均为 `HAND_` 前缀 key）；枪身/手/世界绘制本来就只需要 mode=0（= 默认值）。

### 6.3 验证状态（AGENTS.md 口径：未验证的不声称）

- **静态修复、待实测**：本沙箱无 JDK，**连编译都没跑过**（只做过词法级括号配平 +
  全 diff 人工复核；另：落码途中曾抓到编辑工具链向文件尾追加杂散 `} )/`
  与行内 splice 缺字，均已通过 bash 原子重写消除并复验干净）。
- 第一关：push 后看本分支 CI compile-check。
- 实机矩阵（开光影 + TACZ，默认配置）：① 世界不再透明；② 瞄具裁剪照常
  （镜身孔径内 discard、准星/镜内文字约束）；③ 日志出现汇总行且
  `HAND patched > 0、world left byte-identical ≫ 0`，无 `without a TACZ create-context`
  告警；④ 对照：`ScopeMaskIrisInjection=false` 后瞄具裁剪停止（预期内），
  若此时透明同步消失则把 DEBUG 行连同症状上报。

---

## 6.4 续（2026-09-09 晚）：与兄弟分析对账 + 补丁对齐

兄弟分析（`docs/investigations/MAC_IRIS_TERRAIN_TRANSPARENT_ROOT_CAUSE_2026_09_09.md`，
交接文案 A/B/C/D）把机制推进了一层：TACZ 注入的 `sampler2D tacz_ScopeMaskSampler`
平时恒为 GL 默认 unit 0，而 Sodium 地形程序的 unit 0 是 `isamplerBuffer
u_SectionTimeInfo` —— 不同类型采样器同 unit 违反规范，Apple 丢 draw。
该理论同时解释"只 Mac / 只地形 / 只 26.x / 日志干净 / 去 TACZ 即好"，
与本线 §6.1 的穷举排除结论相互印证。本线已按其文案 C 补齐（替代 §6.2 的布尔开关）：

- **C-4**：`RenderConfig.IRIS_SCOPE_MASK_INJECTION` 三态枚举
  `HAND_ONLY（默认）/ ALL / OFF`（配置键 `IrisScopeMaskInjection`），
  Cloth 界面 + 中英文案；mixin 按策略分流，OFF 全跳、ALL 全注（旧行为）。
- **C-2**：`IrisScopeMaskState.ensureMaskUnit` —— 每个被注入程序首次 setup
  枚举 active 采样器、选最高空闲 unit 写入，mode==0 也写（采样器永不留 0）；
  无空闲时别名主贴图 unit 并禁用该程序裁剪；`onPipelineRebuild` 由 link 钩子
  每次调用清空 id 缓存（防 id 复用错配）。实现用 `java.util` 容器
  （不用 fastutil，零依赖风险），采样器类型区间取保守上延。
- **C-3**：`bindMaskTexture` —— active 单元恢复进入时真实值（替代无条件回 0），
  并 `glBindSampler(unit, 0)` 清残留 sampler object。未用新 @Accessor
  （读 `GL_ACTIVE_TEXTURE` 真实值等价，少一个 mixin 文件）；未绑 NEAREST
  sampler object（`ScopeMaskTextureHandle` 的 sampler 活在 vanilla 路上，
  Iris 路拿不到其 GL id，纹理自身参数是安全退路）。
- **C-5**：`SCOPE_MASK_DEBUG` 开启时每个程序首次 setup 打印采样器 unit 表 +
  `glValidateProgram` 结果（位置在写入之前，抓的是 Iris/原版落定态）。

**本线保留、兄弟方案没有的**：① HAND 判定用 `ShaderKey` 枚举名而非 link 的
`name` 参数 —— `name` 到底是 `hand_cutout` 还是 `gbuffers_hand` 未及证实
（Iris 26.2 的 `NewWorldRenderingPipeline` 已不在老路径，grep.app 只拿到
`ShaderCreator.create(this, name, key, …)` 一行），key-based 对两种命名都对，
name-based 若用 `startsWith("hand_")` 在 `gbuffers_hand` 命名下会静默零注入；
若兄弟线坚持 name 方案，建议改 `contains("hand")`（审计过：无世界程序名含 hand）。
② 注释感知 `void main() {` 定位（兄弟 C-1 沿用旧 `indexOf`）。
③ fail-open 方向：无上下文时全注 + 告警（= 旧行为 + 可见），而非静默零注入。

**仍未闭环**（与兄弟文案一致）：① 26.2 `setupBindGroupLayouts` 是否仍先分
TEXEL_BUFFER（文案 A/B，任意 Windows 机可证；NeoForge primer 只确认了改名）。
② Mac 实测（文案 D）。③ 本沙箱无 JDK，以上全部静态落码，CI 编译是第一关；
`startEnumSelector` 本仓无先例（Cloth 标准 API，若 CI 报不存在再换）。

---

## 6.5 续（2026-09-09）：文案 A 已闭环 —— unit 0 身份升格为官方二进制实证（转述）

兄弟 agent 报告（其沙箱 `/home/user/deliver/`，**本沙箱不可见，未独立复验**；
方法可复现：Temurin JDK 25 + Mojang piston 26.2 release client.jar +
Vineflower 1.12 反编译 + `javap -c` 字节码交叉核对）：

- 26.2 `GlProgram#setupBindGroupLayouts` 与 26.1 `setupUniforms` 逐行等价：
  UBO 与采样器是两个独立计数器，`TEXEL_BUFFER`（`Utb`）与具名 sampler
  **共享** `nextSamplerIndex`，「先全量 uniforms、后全量 samplers」两段循环
  在改名后原样保留，`flatten*` 纯顺序拼接不重排。
- 代入 Sodium 地形绑定组 ⇒ `isamplerBuffer u_SectionTimeInfo` = unit 0，
  `u_LightTex` = 1，`u_BlockTex` = 2 —— 与根因文档预期完全一致。

**结论升级**（注意口径，不多声称一步）：被证实的是 **unit 0 的身份**
（机制链唯一的承重假设 §2.4），即"地形程序里确实存在
`isamplerBuffer@u0` + `sampler2D@u0` 的规范非法状态"。Apple 端"draw 被静默丢弃"
仍是强推理（Apple 对该违规的 validator 原话有案可查、Metal 参数槽无法表达、
症状与之严丝合缝、已无存活的竞争假说），最终由 Mac 实测一锤定音。

- **文案 B 降级为可选**：它原本只是为了绕开"拿不到 26.2 源码"死结，
  现死结已除。保留价值是运行时二次确证，且现在有了**硬预测**：
  Windows 上旧版 TACZ（或本分支 `ALL` 策略 + `ScopeMaskDebug`）的地形程序
  诊断行应为 `u_SectionTimeInfo:0x8dd0@u0` + `tacz_ScopeMaskSampler:0x8b5e@u0`
  且 `validate=FAIL`；`HAND_ONLY` 下地形程序无 tacz 采样器且 `validate=OK`。
  若预测落空，机制回到 open。
- **文案 D 不变**，仍是最后一关：Mac 上 HAND_ONLY / ALL / OFF 三档行为
  （ALL 亦应不透明 —— 这是 C-2 独立充分性的判决实验）。
- **附带发现**（转述，未核实）：26.2 客户端 OpenGL 与 Vulkan 两套后端并存。
  本案链路经 Iris（OpenGL-only），确认的 unit-0 逻辑属 GL 后端，结论不受影响；
  若将来出现 Vulkan 后端 + 光影组合，Apple 端机制需另述。暂不采取行动。
- 本分支修复方向无需改动：HAND_ONLY 拿掉地形注入、C-2 拿掉 unit-0 驻留，
  两层与被证实的机制精确对应；C-5 诊断正是为 B/D 取证准备的。

---

## 6.6 续（2026-09-09）：fabric 三版本审计 —— 只有 26.2 需要修（git grep 实测）

- **26.2**：唯一的 GLSL 注入器是 `IrisShaderCreatorMixin`（本分支已修）；
  本分支无 `IrisDepthRestoreShaderMixin`，不存在第二个注入点。
- **26.1.2**：有 `IrisDepthRestoreShaderMixin`（link 内 `createShader` ordinal 4
  的 `@ModifyArgs`），注入 `sampler2D`×2（`tacz_ApertureDepthSampler` /
  `tacz_PostBodyDepthSampler`）+ mode ints；但 name 白名单只进手部程序
  （`gbuffers_hand` / `hand_cutout` 两式，world/entity/water 不动）——
  地形程序无 TACZ 采样器，本 bug（地形透明）不存在，无需 port。
- **1.21.11**：该文件与 26.1.2 逐字一致，同 verdict；且老 Iris 1.10.7 +
  老 MC 无 bind group，本机制三重不适用。
- **附带证实**（26.1.2 该文件注释）：`createShader` 收到的 `name` 两式并存
  （`hand_cutout` 与 `gbuffers_hand`）—— 26.2 用 `ShaderKey` 枚举过滤而非
  `startsWith("hand_")` 是对的，name-based 会漏 `gbuffers_*` 式。
- **移植备注**：26.1 与 26.2 同代 bind-group 架构（Sodium UTB@u0 同理成立）。
  若将来 26.1.2 引入全程序注入桥，必须同时带上 HAND_ONLY 默认 +
  `ensureMaskUnit`，否则同一 bug 在 26.1 复活。
- **姊妹仓 `TaCZ_Renovated`（NeoForge）三版本**：仓库不在本沙箱，待对方按同一
  清单审计（`tacz_ScopeMaskSampler` / `ShaderCreator` / 注入过滤范围）；
  两仓无共同祖先，只能语义同步，不能 cherry-pick。

---

## 6.7 续（2026-09-09）：Windows A 卡实测 —— 机制全胜，过滤全败，Fix A（本轮已修）

实测环境（用户回传 `latest.log`，后从分支删除，blob 见 `204b83a`）：
MC 26.2 / Fabric Loader 0.19.3 / Iris 1.11.2+mc26.2 / Sodium 0.9.1+mc26.2 /
ComplementaryUnbound r5.8.1，一局内 ALL、HAND 两档各跑一次、中间重载光影包。

**机制侧：全胜（文案 B 闭环，硬预测逐字命中）。** 6 个地形程序
（两档各 3 个：377/380/383 与重载后的 437/440/443）全部：

```text
samplers=[u_SectionTimeInfo:0x8dd0@u0 ... tacz_ScopeMaskSampler:0x8b5e@u0]
validate=FAIL log='the set of active program objects contains two active
samplers of different types, but refer to the same texture image unit'
```

即：unit 0 身份（isamplerBuffer）运行时确认 + TACZ 采样器同程序 @u0 +
驱动亲口报出预言的违规。附带：C-2 工作（`textureUnit=31`）、开镜 mode=1
正常、composite 系（ProgramBuilder 路）无 tacz 项符合设计。

**过滤侧：全败（我的桥，用户的操作无误）。**
`0 HAND patched, 0 world skipped, 77 fail-open, 0 legacy-ALL` —— create*-HEAD
ThreadLocal 上下文 77/77 缺席，HAND_ONLY 名存实亡，两档实际都是全注入，
**本轮 ALL/HAND 对照 VOID**。根因未定（26.2 HEAD 源码核对：create* 签名与钩子
一致、link 同步直调；疑实机 Iris 1.11.2 版本 skew + require=0 静默；
1.11.2 对应源码 tag 未定位）。教训：require=0 的钩子必须自带存活计数。

**Fix A（已实施，待 CI + 重跑）：** 删 ThreadLocal 桥 → `@ModifyArgs` 钩 link
内 `createShader`（描述符与 26.1.2 先例逐字一致，26.2 HEAD 已核对 link 内
5 次调用、fragment 为第 5 次；实现按 `ShaderType==FRAGMENT` 守卫，
不依赖 ordinal）→ HAND 判定改大小写不敏感 `contains("hand")`
（`hand_cutout` / `gbuffers_hand` 两系全含 hand）→ 汇总行加
SAW_FRAGMENT 存活计数 + 完整管线 0-hand 一次性 WARN。失败只许大声。

**Mac 送测 HOLD**：当前构建 fail-open = 老行为，在 Mac 上必然还透明，
送了白送。等 Windows 重跑出现 HAND/ALL 分化（HAND 档地形程序无 tacz 项
且 validate=OK、汇总行 HAND>0 且 world>0）后再送。
