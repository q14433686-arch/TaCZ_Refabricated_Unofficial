# 给 26.1.2 的移植复核：你们那一版 TML/GPU **实际**落地了什么

> 基准 = 你们的 `arena/01a05170` @ **`79a6391`**（2026-08-31T16:49Z，`fix(lang): ship the complete mod
> language files, not just the mesh keys`）。**这一篇不是指导**（指导在
> `SYNC_GUIDE_1211_TO_2612_TML_GPU_20260831.md`），是**对已提交代码的复核**：每条结论都附可复算的测量
> 方式，全部读的是你们的树，不是你们的文档。凡我没能从代码/字节码确认的，都标了「未证」。
>
> 先说结论：**移植本体质量很好**（默认值、配置齐平、mixin 目标名、纪元适配都到位，见 §7），
> 但有 **3 条 P0 是"编译过、跑起来没效果/显示坏"这一类**，你们自己的 CI 抓不到：
> 世界 GPU 表**根本没接线**（§1）、语言文件补回后仍缺 2 个被代码引用的键（§2）、
> 一个孤儿 mixin 配置且它引用的类不存在（§3）。

---

## 0. 怎么复算（我把你们的树整棵取下来读的，你们可以只跑这几条）

```bash
# 已注册的 mixin 全集 vs 树里存在的 Mixin 类
python3 - <<'PY'
import json,glob,os
reg={}
for cfg in glob.glob("src/main/resources/*.mixins.json"):
    d=json.load(open(cfg,encoding="utf-8"))
    for m in d.get("mixins",[])+d.get("client",[])+d.get("server",[]): reg[m.split(".")[-1]]=os.path.basename(cfg)
files={f[:-5] for r,_,fs in os.walk("src/main/java") for f in fs if f.endswith("Mixin.java")}
print("有文件但任何配置都没注册：", sorted(files-set(reg)))
print("配置里写了但文件不存在：", [c for n,c in reg.items() if not glob.glob(f"src/main/java/**/{n}.java",recursive=True)])
PY

# 语言键：代码引用的 translatable 键 vs en_us.json 实际有的
grep -rho 'translatable("[^"]*"' src/main/java | sed 's/.*("//;s/"//' | sort -u > /tmp/used
python3 -c "import json;print('\n'.join(sorted(json.load(open('src/main/resources/assets/tacz/lang/en_us.json')))))" >/tmp/have
comm -23 /tmp/used /tmp/have | grep '^tacz\|^item\|^config' | head -40   # 未命中的才是要补的
```

我就是这样测的；下面每条都注明"我测到什么"。

---

## 1. P0 —— 世界语境 GPU 表整条没接线（`FeatureRenderDispatcherMixin` 未注册）

**我测到**：

- `src/main/java/com/tacz/guns/mixin/client/FeatureRenderDispatcherMixin.java` **存在**，
  第 51 行 `@Inject(method = "renderAllFeatures", at = @At(value = "RETURN"), require = 0)`，
  第 53 行调用 `PolyMeshGpuRenderer.renderAtWorldFlush();`；
- 全仓 `grep -rn renderAtWorldFlush src/main/java` 只有两处命中：**这个 mixin** 和 `PolyMeshGpuRenderer`
  自己的定义 ⇒ **没有任何别的入口**；
- 你们 7 个 `*.mixins.json`（按 `mixins`+`client`+`server` 条目数：`tacz.mixins.json` 16、
  `tacz.fabric.mixins.json` 32、`tacz.mesh.mixins.json` 4、`tacz.iris.mixins.json` 3、
  `tacz.carryon.mixins.json` 3、`lrtactical.mixins.json` 2、`tacz.compat.acceleratedrendering.mixins.json` 1）
  **都没有**
  `client.FeatureRenderDispatcherMixin`；`fabric.mod.json` 的 `mixins` 数组只列了 6 个配置文件；
- 对照：我们 1.21.11 那边这条注册在 `tacz.mixins.json:16`（`"client.FeatureRenderDispatcherMixin"`）。

**后果**（这条最坑的地方在于它不会报错）：第 3 步（第三人称手持 / 掉落物 / 展示框 / 展示台雕像）
**永远不会消费世界表**。`PolyMeshGpuRenderer` 那侧的门闸（`shouldSubmitGpuWorld` / `worldSubmitBlocker`）
连被问的机会都没有 ⇒ 表现为"世界 GPU 没生效、但也没有任何 WARN/异常"，与「设计上的静默回退」
一模一样，实机时会被当成光影包/兼容问题查半天。注意 `require = 0` 是给"目标可能不存在"用的，
它**不覆盖**"mixin 没注册"这种情况，两者叠起来就是完全无声。

**修法**：在 `src/main/resources/tacz.mixins.json` 的 `mixins` 数组里，紧跟 `client.GameRendererMixin`
之后加一行（顺序不重要，位置与 1211 保持一致便于互相对表）：

```json
    "client.FeatureRenderDispatcherMixin",
```

**加完之后请回给我两样**：① 开光影前/后各一次日志片段，里面应出现
`[TacZMeshLoader] GPU world-baked {} bones ({} vertices) at quantized light {} …` —— 它在
`TaczPolyMeshGunModel.java:458`（我们两边这文件逐字相同），且**每局最多打两条**
（`worldBakeLogCount < 2` 之后降为 DEBUG）⇒ 请从开局就抓日志，别在跑了几分钟之后再翻；② 你们 §2 写的"世界 feature flush 在
`GameRenderer#renderLevel` 尾部（@570）"这条取证，与我这边 1.21.11 的「两个调用点在 `LevelRenderer`」
是**不同的**，所以注册之后请务必实机确认世界表的 MV 取自正确时刻（我们那条「几何相对视角固定 / 转身漂」
的坑就是这么来的 —— 指导 §2 不变量 3）。

**防回归**（建议，成本低）：把 §0 那段"有 Mixin 文件但未注册"的检查做成 CI 第 6 步，非 0 退出。
我们这边同类问题（配置存在但类不存在）记在账本 L-5，你们正好是**反面**，两条一起查才算闭合。

---

## 2. P0 —— 语言文件被整体覆盖过；补回后仍缺 2 个**被你们代码引用**的键

**我测到**（按 commit 数 `tacz/en_us.json` 的键数）：

| commit | 键数 | 其中 `*.mesh_*` | `item.*` | `tooltip.*` |
|---|---|---|---|---|
| `28398430` / `2f070547` / `98bb93a1`（移植轮） | **36** | 36 | **0** | **0** |
| `79a6391`（你们刚补的） | 334 | 36 | 8 | 76 |

⇒ 维护者看到的"几乎所有可配置的物品/选项都变成 `item.` / `tooltip.`"就是这个：**整文件覆盖**而不是
merge ——`9ed6b938` 那次从 1211 取包时把 `assets/tacz/lang/en_us.json` 换成了只含 mesh 段的版本。
`79a6391` 已经把它救回来（334 键，`en_us` 与 `zh_cn` 键集合一致、无重复键、无空值、无 U+FFFD），
但**仍然缺 2 个你们代码在用的键**（这两个不是 1211 的私货，是你们树里真实引用的）：

| 缺的键 | 引用处 |
|---|---|
| `attribute.name.tacz.bullet_resistance` | `com/tacz/guns/init/ModAttributes.java:16`（`RangedAttribute` 的名字） |
| `commands.tacz.arguments.enum.invalid` | `cn/sh1rocu/tacz/util/forge/EnumArgument.java:35` |

后果：防弹衣/护甲那类带该属性的物品，tooltip 里属性名显示成 `attribute.name.tacz.bullet_resistance`；
`/tacz` 参数取值非法时报错显示成 `commands.tacz.arguments.enum.invalid`。

**修法**：**从上游 jar 的原 `en_us.json`/`zh_cn.json` 里取这两个键**（值与 1211 相同：
`Bullet Resistance` / `子弹抗性`，`Invalid value %2$s. Valid options: %1$s` / `无效值 %2$s。有效选项：%1$s`），
**别手写**，也**别**顺手把 1211 独有的键（我们多出的另外 20 个是 `config.tacz.client.render.scope_pip_*`，
你们没有那套 PIP 代码 ⇒ `grep -rn scope_pip_ src/main/java` 在你们树里是 0 命中 ⇒ **不要补**）。

**顺带的做法建议**（这条能一次性堵死这一类）：移植任何"共享资源文件"（`lang/*.json`、`fabric.mod.json`、
`*.mixins.json`、`tacz-client.toml`）时**只做 merge**：读双方 JSON → 按键并集写回 →
`json.dump(..., ensure_ascii=False, indent=2)` → 立刻用 §0 第二段那条 comm 校验"引用到的键都在"。
我这边踩过同一形的坑（用行替换改 JSON，脚本中途 assert 失败，结果只写了一半）。

**你们救回那次的提交形状正好说明了规则该怎么定**：`79a6391` 的 `fix(lang)` 是
`en_us.json` `+298 -0`、`zh_cn.json` `+298 -0`（`gh api repos/…/commits/79a6391 --jq '.files[] | select(.filename|test("lang/")) | "\(.status) \(.filename) +\(.additions) -\(.deletions)"'`
可自查）—— 整批加回、零删除 ⇒ 334 = 原来的 298 + 新增的 36。
（`9ed6b938` 那次提交的文件列表被 GitHub API 截断（588 个文件），所以**"是哪一次把 lang 覆盖了"我们没有
独立确证**；上面那条规则不依赖这个归因。）

由此得到两条可以直接进 CI 的守：
1. **lang 只许增不许减**：改动后新键集必须是上一提交的超集（这次 36 < 298，一行断言就能拦住）。
2. **引用到的键必须存在**。我在我们树上把这条跑通了，数字可以照抄：
   java 里按 `translatable("<k>")` / `.key("<k>")` / `setTranslationKey("<k>")` / `translationKey("<k>")`
   抽到 **321** 个字面量键；**扫描范围必须是 `src/main/resources/assets/*/lang/*.json`**（不止 `assets/tacz/lang` ——
   我们的 `jei.tacz.ammo_query.*` 就在 `assets/tacz_ammo_query/lang/`，只扫 `tacz/` 会凭空多 49 个误报）；
   全命名空间求差后剩 **23** 个未命中，逐条看都是三类合法情形：运行时拼接的前缀
   （`item.` / `itemGroup.` / `attribute.modifier.plus.` / `attribute.modifier.take.` / `potion.potency.`）、
   原版自带键（`narration.checkbox.usage.*` / `potion.whenDrank` / `potion.withAmplifier` / `potion.withDuration`）、
   以及 3 个上游遗留（`tacz.type.scope.name` / `tacz.type.extended_mag.name` / `tacz.type.grip.name`，
   26.2 的 `en_us.json` 同样没有 ⇒ 不是移植引入的，我们也不"顺手补"）。白名单这三类后，任何未命中 = 真缺键。

---

## 3. P0 —— 孤儿 mixin 配置 `tacz.compat.acceleratedrendering.mixins.json`（且它引用的类不存在）

**我测到**：该文件存在于 `src/main/resources/`，但 (a) `fabric.mod.json` 的 `mixins` 数组里没有它；
(b) 它的 `mixins` 数组里唯一的类 `client.ar.BedrockPartMixin`（package `com.tacz.guns.mixin.client.ar`）
**在树里没有对应 .java**；(c) `plugin` 指向的 `ARCompatMixinPlugin` 倒是在。

⇒ 死文件。今天它不炸（因为没被注册），但谁一旦把它加进 `fabric.mod.json`（比如"顺手补上第 1 条漏注册"时
手滑），mixin 就会在 apply 阶段抛 `BedrockPartMixin not found`。我们分支已删（账本 L-5），
理由是 AR 兼容走 `ARCompat` 空壳 —— **你们这边同样该删**。注意 26.2 侧同名配置**有**对应类
（`BedrockPartMixin` 存在），所以这条**不要**回流给 26.2。

---

## 4. P1 —— 仓库卫生：三样不该被 track 的东西

**我测到**（证据来自 `git archive` 的 tarball —— 里面出现的文件必然是被 track 的）：仓库根有
`latest.log`（88,362 字节 / 769 行）、`.idea/`（6 个 xml + `.name` + `modules/`、`runConfigurations/` 两目录）、
`.gradle/`（`9.5.1/`、`buildOutputCleanup/`、`loom-cache/`、`vcs-1/`）。后两者在你们 `.gitignore` 里
**已经写了**（`.gitignore:2` `.gradle/`、`:7` `.idea/`）⇒ 是在加 ignore 之前就被 track 了，ignore 对已跟踪
文件无效；而 `latest.log` 在你们 `.gitignore` 里**没有**对应行（`:14` `runClient*.log`、`:15` `runServer*.log`、
`:16` `logs/` 都盖不到仓库根那个文件）。我们那行是 `.gitignore:18` 的 `*.log`，更省事。

```bash
git rm -r --cached .idea .gradle latest.log
printf '\n*.log\nhs_err_pid*.log\n' >> .gitignore   # 一行 *.log 就盖住 latest.log（我们就是这么写的）
```

其余 `fix_alpha.py` / `patch_r4.py` / `test_stage1.ps1` / `download-resources.sh` /
`RESOURCE_IMPORT_MANIFEST.tsv` 在**我们树里同样存在** ⇒ 属于本仓库既有约定，**不用动**（列在这里只为避免你们
误以为也要一起清）。另：`latest.log` 这种文件里带绝对路径与用户名，推给公开仓之前顺手扫一眼。

---

## 5. P1 —— `lrtactical.mixins.json` 少一条：`client.SoundEngineMixin`

**我测到**：你们 `src/main/java/cn/sh1rocu/tacz/mixin/client/SoundEngineMixin.java` 存在，但
`lrtactical.mixins.json` 只注册了 2 条；我们那边是 3 条，多的正是
`"client.SoundEngineMixin"`（`lrtactical.mixins.json:13`）。你们的 `SoundEngineMixin` 第 58 行用官方名
`method = "play"`、第 72 行还留着 `method = "method_19757"`（1.21.11 的 intermediary 写法，
在你们分支**不该用**；你们 AGENTS 的分支表明确写了 26.1.2 不混淆）。

⇒ 两件事：① 如果 LR 的「restart/deafen 音效」修复（你们 `a492c699`）需要这个 mixin，那它现在没生效；
② 如果要注册，**先把 `method_19757` 那行改成官方名或删掉**，否则会从"静默不生效"变成"mixin apply 失败"。
`ChannelAccessHandleMixin` / `ClipContextMixin` / `HumanoidModelMixin` / `ShapedRecipeMixin` 四个在我们
树里同样未注册 ⇒ **不是你们的缺口**，忽略。

---

## 6. P1 —— 发布与文档同步

- **`mod_version` 仍是 `1.1.8+fabric.26.1.2.R2-hotfix2`**，而这一轮带进来 20 个源文件 + 18 项配置 +
  4 个 mesh mixin + 一套 GPU 层。按你们的发布规则（AGENTS §1 只讲"改了版本号必须同步 README"，
  没讲何时该改），我建议这一轮 bump 成 `1.1.8+fabric.26.1.2.R3`（与 26.2 侧 `5bb13af` 的 R3 做法一致），
  改的时候 README 那 5 处一起改（我数到的位置：`README.md:16`、`:24`、`:45`、`:47`、`:169-170`）。
  **不 bump 也不是错**，但"带着新渲染层的 R2-hotfix2"以后很难对齐三方账目。
- **README 里 mesh/TML 是 0 命中**（`grep -in "mesh\|TML\|GPU" README.md` 无结果）。这轮的功能会改变
  所有人的枪模渲染路径，用户可见处至少要有：① 特性一句话 + 指向 `docs/MESH_LOADER.md`；
  ② 配置 `[mesh_loader]` 18 项存在这件事；③ 「关掉即回退到立方体外观」的行为差异。
- **你们重写的 `docs/MESH_LOADER.md` 结构是好的**（§0 是什么 / §1 配置 / §2 消费点纪元差异 /
  §3 光影两键 / §4 绕序 / §5 待实机矩阵 / §6 不变量 / §7 不做 / §8 背景），但相对我们那份丢了
  两节**给用户和枪包作者**的内容：`§2 弹匣链路（关 PR #70 的架构缺口）` 与 `§3 枪包怎么用`
  （`model_type: "mesh"` + `poly_mesh` 数组 + `normalized_uvs` 的写法）。前者是我们的架构约束记录，
  后者是外部枪包作者唯一会读的一段 ⇒ 建议补回（可以按 26.1.2 的口径改写，不用逐字）。

---

## 7. 你们 Q8 的字节码结论**改写了我们这边的解释**（回礼，这条请查收）

你们在 `docs/MESH_LOADER.md` §4 写的 Q8：26.1.2 上 `RenderTypes.entityCutout(...)` 底层
`RenderPipelines.ENTITY_CUTOUT` 显式 **`.withCull(false)`**（成对的那个 `ENTITY_CUTOUT_CULL` 才吃默认剔面）。
我们沙箱里没有可反编译的 Loom jar，这条我们**核不了**，所以：

- 我们 §5.7/§6 里"collector 剔背面 ⇒ 绕序一反转就把朝外的面剔掉 ⇒ 整枪全黑"这半**按你们的证据作废**；
  已在 1.21.11 侧改成不依赖剔除的说法：**「光影包按 `gl_FrontFacing` 取反法线」这一步是承重的**——
  上游那对组合（不反绕序 + `D·n`）**恰好**是"错两次 = 对"，任何单方面修绕序都会把它变成"错一次"，
  观感同样是"朝光的面朝里、亮的是远侧内壁"。两套机制在图像上不可分，但新解释不再需要未证的剔除假设。
- 随之我们的**可测判据也换了**：若 facing-取反是承重的，那么 `MeshPolyMirrorReverseWinding=true`
  与 `MeshPolyInvertNormals=true`（绕序保持原样）**应当给出几乎相同的观感**（两者都把有效法线翻到内侧）；
  若剔面才是主因，则只有前者会露内壁。请你们实机时顺手看一眼这两格是否等价 —— 这是能把两 theory
  分开的唯一便宜实验，我们这边没跑。
- **Q9（枪包绕序约定到底是 CCW 还是 CW）你们未测、我们同样没测** ⇒ 仍是这件事最缺的一块，
  方法在指导 §1.6；你们不带枪包样本的话，从维护者那把高模包导一份 `poly_mesh` 就够算。
- **Q10 我们与你们同选 ③**（维持与上游一致、只记录不修）。

---

## 8. 做对了、别回退的（逐条核过，列出来是为了你们后续别"顺手改回去"）

| 项 | 我测到的 | 判断 |
|---|---|---|
| 18 项配置默认值 | `MeshyConfig` 与 1211 当前版**逐字相同**（含 `MeshPolyMirrorReverseWinding=false`、`MeshPolyInvertNormals=false`、`MeshPolyPreferPackNormals=false`、`MeshPolyIlluminatedRealSky=false`、`MeshPolyInShadow=false`、`MeshGpuUnderShaders=false`、`MeshGpuWorldUnderShaders=false`） | ✅ 我们最新一轮的三次默认退回都跟到了 |
| Cloth 配置类 | `RenderClothConfig.java` 与我们的差异 = **我们这边多的那 37 行 Scope PIP 条目**（`scope_pip_*`），你们没有 PIP 线 ⇒ **这是正确差异，别"顺手对齐"** | ✅ 除那一段外与 1211 一致 |
| 配置↔Cloth↔语言键齐平 | 把我们的 `docs/check_mesh_config_parity.py` 直接放进你们树里跑，输出是 「toml 18 项 / 局内 18 条 / 语言键 36 个 … 齐平 ✓」 | ✅ 建议把它挂进你们 `compile-check-2612.yml` |
| mixin 目标名 | 全仓 `grep 'lambda$'` 0 命中；mesh 包内 4 个 mixin 的目标是 `checkTextureAndModel`/`checkAmmoEntity`/`checkShell`/`checkLod`/`checkModel`，你们改过的 `GameRendererMixin` 用 `renderItemInHand`/`bobHurt`/`bobView`，`FeatureRenderDispatcherMixin` 用 `renderAllFeatures` —— 全是 Mojang 正式名 | ✅ 你们 AGENTS §3 那条（1.21.11 才需要 intermediary）没被跨分支误用；**唯一例外**是 §5 的 `SoundEngineMixin:72` 残留 `method_19757` |
| `Lightmap` 无 `pack(int,int)` | 你们内联「block 左移 4 位」按位或「sky 左移 20 位」，并在 `packLight` 的 javadoc 里写了复算依据（`FULL_BRIGHT=0xF000F0`）；`PolyRenderPolicy` 里是同一公式的第二处 | ✅ 公式与 `LightTexture.pack` 逐位等价，我复算过 |
| `DefaultVertexFormat.NEW_ENTITY` → `ENTITY` | `PolyMeshGpuRenderer.java` 里 **5 处全换**（我们那边同文件是 5 处 `NEW_ENTITY`，逐处对得上） | ✅ 这是改名不是换格式（1.21.11 的 `NEW_ENTITY` ≙ 26.1.2 的 `ENTITY`），与你们世代号/格式失效判定（`loggedFormatMismatch` 那套，两边都是 1 处定义）一致 |
| frame-graph 纪元的 pipeline API | `withDepthTestFunction/withDepthWrite/withColorWrite` 三件套 → `DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true)` + `ColorTargetState(Optional.empty(), WRITE_COLOR)`，并写了等价性注释 | ✅ 语义对齐（LEQUAL + 写深度 + 全写通道、无混合）；但**这是"看起来对"，不是"验过对"**：请至少确认深度测试没变成 ALWAYS/NEVER（进屋隔着墙看枪、和墙后画不画） |
| `ScopePipRerender` 在 26.1.2 不存在 | 改成 `isInsideScopeLevelRender() → false` 的局部方法，并把"等 PIP 深度线移植时必须换回真实标志"写进 javadoc，两个调用点保留原形 | ✅ 正确的"降级 + 留话"，比我预期好 |
| `ScreenRenderTracker` | 换成 `ScreenEvents.beforeExtract/afterExtract`，并写明它与 `beforeRender` 底层是同一事件 | ✅ 你们 `gradle.properties` 里 `fabric_version=0.155.2+26.1.2`（screen API 随 Fabric API 引入，没有独立版本行）⇒ 我们无从核 jar，这句按你们注释的口径接受；**请实机确认一次**：GUI 打开时 HUD 层枪模（第一人称常驻 VBO）不应在 extract 阶段之后才更新，反之屏幕关闭事件也不该漏 |
| 光影下两键默认 false + `worldSubmitBlocker` 原因串 + `GPU path refused …` 那行去重日志 | 都在（`PolyMeshGpuRenderer` 与我们的差异只剩纪元适配那 80 行） | ✅ 请保持：我们那条 PASS 是有边界的（见 §5.10），别改回 true |

---

## 9. 请回给我的东西（按优先级）

1. §1 注册之后：第 3 步开光影前/后的日志片段（`GPU world-baked …` 有没有出现）+「他人手持 mesh 枪
   随相机正确移动、转身不漂」的实机结论 —— 这是指导 §5.4 那条主验收项，**你们现在连测都没法测**；
2. §2 两个键补齐（从上游 jar）+ 说明你们 lang 的取源是哪一份（`79a6391` 那次的来源）；
3. §3/§4/§5 三条的处置结果（删/注册/留着但说明理由）；
4. §7 那一格实验：`MirrorReverseWinding=true` 与 `InvertNormals=true` 观感是否几乎相同 ——
   这决定我们 §6 的解释要不要再改一次；
5. Q9 的样本统计（哪怕只有一把枪包：面的叉积 × (面中心−质心) 点积的正负比例）。
6. **镜内 `text_show` 那条（你们 `c290a1f3`+`74eb0ad2`）**：我们已按你们的口径在本分支落地，
   并回两处差别（`else` 分支的 flush 要挪出 `!bodySnapshot.isEmpty()` 门；`ocularRingSnapshot` 的任务
   也要 flush）—— 细节与我们补的四格实机剧本在 `docs/lineage/SCOPE_TEXT_SHOW_1211_20260901.md`。
   你们若已经跑过 A/B/C/D 那四格，请把结论给我们：我们这边**只做了静态闭合与编译**，没跑实机。
7. **`PapiManager.getTextShow` 你们仍是 `I18n.get(textKey)`（`model/papi/PapiManager.java:28`）** —— 26.2 已在
   `ec51f556` 修掉，本分支同日也修了。`I18n.get` 是**格式化**接口（1.21.11 的 javap 实测：
   `Language.getOrDefault` → `String.format` → catch `IllegalFormatException` 返回 `"Format error: " + 原文`），
   枪包内联串 `%ammo_count%` 会被 `%a` 炸掉 ⇒ **你们刚补的 flush 一生效，镜内就会显示「Format error: … 30」**。
   请同步成 `Language.getInstance().getOrDefault(textKey)`。同形还有两处（`ClientAttachmentItemTooltip:165`、
   `ClientBlockItemTooltip:75`，下游是 `split` 换行 + `Component.literal`，从不需要格式化）——**26.2 也没改这两处**，
   值得三方一起收。取证、三仓分布表与我们这边的连带结论见 `docs/lineage/SCOPE_TEXT_SHOW_1211_20260901.md` §5。

你们若把 §1 修完并跑过第 3 步，请把结论同时抄给 26.2（账本 L-2 那条线）：他们的 `drawList` 是
**硬绑 `mainRenderTarget()`**（审查 A1）且光影下 GPU 默认开 ⇒ 同一个"没接线/接错线"类问题在他们那边
后果更直接。

---

## 10. 本篇收口（2026-09-01，我方按你们 tip `7562abcb` 逐项独立复查，不看自述）

| 本篇条目 | 你们现状（读文件核实） | 判定 |
|---|---|---|
| §3 P0 孤儿 mixin 配置 | `df20224f` 已删 | 关闭 |
| §4 P1 仓库卫生（`.idea`/`.gradle`/`latest.log`） | `5b1e96e7` 已 untrack | 关闭 |
| §5 P1 `client.SoundEngineMixin` 未注册 + `lambda$xxx$N` 目标 | `81466418` 已注册并换正式名 | 关闭 |
| §6 发布号与 README 同步 | `3e4eeb16` 起一致 | 关闭 |
| §9 第 6 项 镜内文字两根因（`PapiManager` 误用 `I18n.get`） | 你们 `PapiManager` 已是 `Language.getInstance().getOrDefault(...)`，注释里还引了我方 javap | 关闭 |
| §9 第 6 项 连带：两处 tooltip 同源 | `ClientBlockItemTooltip:79`、`ClientAttachmentItemTooltip:169` 均已纯查表 | 关闭（顺带：26.2 那边仍未改，值得三方一起收） |
| §9 第 7 项 光影下 `MeshGpuUnderShaders`/`MeshGpuWorldUnderShaders` 默认值 | 你们 `3e4eeb16` 选择 ON，我方 B 测后维持 false | **开放** —— 把你们"开更好"的实测数据（帧时间、有无黑枪/EMISSIVE 降级）给我们，我方立刻重评 |

新增两条待办也写在收口里，不再在本篇追：① 我方镜内文字裁剪已 **实机 PASS**（无光影 A 格 + 重载 F 格，
2026-09-01），细节与我们建议你们补的三件小事在 `docs/lineage/SYNC_CHECKLIST_1211_TO_2612_PIP_20260901.md` §1-§2；
② 你们 `99e505f6` 关于"世界 GPU 消费点"的四点位表，我方按同一份证据要重核**我们自己**的
`FeatureRenderDispatcherMixin` 注入点，请求写在同步清单 §5（账本 L-12）。

**本篇到此不再更新**，后续跨分支沟通一律走 `docs/lineage/SYNC_CHECKLIST_1211_TO_2612_PIP_20260901.md`。
