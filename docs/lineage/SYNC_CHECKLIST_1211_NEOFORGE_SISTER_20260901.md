# 给 NeoForge 姊妹线（TaCZ_Renovated @ `1.21.11`）的同步清单

2026-09-01。方向：本仓库（**Fabric** 1.21.11，`arena/01a05759…`，本文提交基线 `e0a4a0c`）→
**TaCZ_Renovated 的 `1.21.11` 分支**（NeoForge 21.11.45，tip `e3d9dd5c`，mod 版本 `1.1.8+neoforge.1.21.11.R1-hotfix`）。

> 定位说明：我方上一轮把一份清单写给了 Fabric 侧 26.1.2 线，那份**没有作废**，仍留在
> `docs/lineage/SYNC_CHECKLIST_1211_TO_2612_PIP_20260901.md`（收件人是同仓库的另一条 arena 分支）。
> **本文才是给姊妹项目 NeoForge `1.21.11` 的**，两份的"你们"不同，别互相代签。

**核对方式**：下列每一条都是我方**读你们的树**得出的（`gh api` 逐文件取内容 + `grep`），不是看你们的自述，
也不是推测。证据级别按你们 AGENTS §2 标在每格；带 `未验` 的格子请勿在你们的文案里升级成"已修"。

---

## 1. 你们 `1.21.11` 线与镜内 `text_show` 相关的现状（我方实测）

| # | 事实（读你们文件所得） | 后果 | 级别 |
|---|---|---|---|
| 1 | `client/renderer/snapshot/BedrockRenderSnapshot.java:93` 已有 `submitFunctionalTasks(SubmitNodeCollector)`，但 `client/model/BedrockAttachmentModel.java` 的瞄具序列里**没有任何调用点**（`grep -n submitFunctionalTasks` 在该文件 0 命中；那里只有 `SCOPE_APERTURE_ORDER=-3` 与 `SCOPE_DEPTH_CLEANUP_ORDER=-1` 两段几何重放） | 镜内 `text_show`（MK5/MK5HD 弹药计数）**一帧都不画** —— 与 26.1.2/Fabric 侧同一个根因 | 静态（读码） |
| 2 | `client/render/scope/ScopeFinalOverlayState.java` 只有 `FINAL_RETICLE_ORDER`/`FINAL_OCULAR_RING_ORDER` 两个队列，**没有文字队列**（无 `PENDING_TEXT`/`queueFunctionalTask`） | 延迟覆盖层那一格（光影下）即使补了 1 也会丢文字 | 静态 |
| 3 | `client/model/papi/PapiManager.java`：`String text = I18n.get(textKey);` | 第二个根因**在你们线上同样存在**：`I18n.get` 是"查表 + `String.format`"，枪包把 `textKey` 写成含 `%` 的内联显示串时，`IllegalFormatException` 被吞掉、返回 `"Format error: " + 原文` ⇒ 症状从"没字"变成"脏字/Format error" | 静态 + javap（我方世代，见 §3） |
| 4 | `TextShowRender.java` 是 61 行的旧形（三参构造、无条件 `collector.submitText(...)`）；无 `ScopeTextSubmitter`、`shaders/core/` 下无 `scope_text_final.fsh`；`ScopeDepthCopyState` 只有私有 `maskValid`（无 `isMaskCycleValid()`） | 镜内文字**没有任何裁剪** ⇒ 贴边字形会溢出圆孔（不是"被深度剔掉"，见 §4 的机制段） | 静态 |
| 5 | `config/client/RenderConfig.java` 的 scope 键只有 `SCOPE_MASK_ENABLE`；全仓 `ScopePip*`/`scope_pip*` **0 命中** | 我方关于 PIP 的两条结论（重提取防护、隔帧 interval）对你们是**非项**，别去对齐（§5） | 静态 |
| 6 | 全仓 `meshloader`/`PolyMesh`/`Mesh` **0 命中**（你们 1.21.11 线没有 TML/GPU 那套） | 我方账本里所有 mesh/GPU 项（含仍未结的 L-12「世界 GPU 消费点」、L-8b「绕序×剔除」立项）对你们**都不是项** | 静态 |
| 7 | `compat/iris/IrisCompat.java:72` 的 `assignPipelineToIris(RenderPipeline, String, String)`、`mixin/client/GlCommandEncoderScopeDepthCopyMixin.java`、`mixin/client/iris/IrisFinalScopeOverlayMixin.java`、`tacz.iris.mixins.json` 均在 | §4 的掩码管线**不需要新设施**，照 §4 抄即可 | 静态 |

## 2. P0-a：把被绕开的 `functionalTasks` flush 补回来（我方提交 `1cfa42b` + `cb39564`）

四处改动，全部在你们已有的类里，无需新文件：

1. `BedrockRenderSnapshot`：你们已经有 `submitFunctionalTasks`；我方额外加了
   `public List<IFunctionalSubmitter.SubmitTask> functionalTasks()`（只读访问器）—— 因为 flush 点在外层，
   要按快照取任务。
2. `BedrockAttachmentModel`：新增一个私有 `submitScopeText(snapshot, collector, deferToFinalOverlay, scopeMaskEnabled)`
   （全文见我方 `src/main/java/com/tacz/guns/client/model/BedrockAttachmentModel.java:728` 起，可直接抄），
   并在**三处**调用：`bodySnapshot`、每个 `ocularSnapshots` 元素、`ocularRingSnapshot`（第三方镜的文字节点常挂在
   ocular/镜框子树下，漏了它们就只有默认镜有效）。
3. 延迟那一格：`ScopeFinalOverlayState` 加 `PENDING_TEXT` 队列 + `queueFunctionalTask(...)`，并在
   `render…` 里 **先 flush texts、再 reticle、再 rim**（`FINAL_*_ORDER` 保持你们现值），
   `hasPendingReticles()`/`hasPendingOverlay()` 的条件要把 `PENDING_TEXT` 并进去（否则只有文字待画时整条覆盖层不触发）。
4. **两个坑（我方与 26.1.2 各踩过一次，直接省你们一轮 CI）**：
   - 任务收集器类型：`task.submit(...)` 要传 **`SubmitNodeCollector`**（就是 `submitNodes` 本身；
     `SubmitNodeStorage` 在本世代同时是二者）。把 `collector.order(n)` 返回的
     `OrderedSubmitNodeCollector` 递给任务会**编译不过** —— 26.1.2 首版就是这么挂的。
   - 立即分支的 flush 位置必须在 `if (!bodySnapshot.isEmpty()) { … }` **门外**，
     `ocularRingSnapshot` 的任务也要独立 flush；写在门里 = 镜身几何为空时（低模/被隐藏）文字又被丢一次。
5. 判据日志（每局一次，我方 `cb39564`）：
   `[TACZ Scope] Flushed {} in-lens text task(s) in the solid hand pass (scopeMask={})`
   与延迟格沿用 `… ({} reticles, {} rims, {} texts)`，**`texts ≥ 1`** 才算走到覆盖层。

级别：我方这一格 **实机 PASS（2026-09-01，维护者报告）**；你们这边照做后仍属"未验"，直到你们自己跑一遍。

## 3. P0-b：`PapiManager#getTextShow` 改成"只查表、不格式化"

- 语义：`textKey` **不是** translation key 的保证不存在 ⇒ 必须走"查表拿原文（查不到就返回 key 本身），
  **不**跑 `String.format`"。
- 我方在本世代（Fabric layered 映射）用的是 `Language.getInstance().getOrDefault(textKey)`；
  **这个成员名不要直接抄** —— 你们 AGENTS §3 红线 2 要求自证：请按 `I18n.get` 在你们 jar 里的实现
  读它用的那张表（我方在 CI javap 里核到 `I18n.get` = 查表后 `String.format(Locale.ROOT, …)`，
  且 `Exception table` 里有 `catch IllegalFormatException → "Format error: " + 原文`，
  这条**同时**解释了我方最初看到的"镜内什么都没有"与后来"脏字"两种表象）。
- 顺带两处同源：`ClientBlockItemTooltip`、`ClientAttachmentItemTooltip` 的 `tooltipKey` 也是内联串，
  我方已改（提交 `c9b8ba1`），**Fabric 26.1.2 已跟改，26.2 仍未改** ⇒ 建议三方一起收。

## 4. P1：镜内文字的掩码裁剪（我方提交 `d076cf5`，等价 Fabric 26.1.2 的 `e1c550ee`）

**先记一条口径，免得你们重走我们的弯路**：我方曾写过"文字走 vanilla 字体管线 ⇒ 会被镜筒深度剔掉 ⇒
等价于 26.2 的掩码裁剪"，**这句是错的、已撤回**。`submitText` 的下游
（`TextFeatureRenderer` → `GlyphRenderTypes`）把 RenderType 写死、**不吃**孔径深度，
所以立即路径与延迟路径都会溢出圆孔。任何"深度剔除"的说法都不成立，裁剪必须在 RenderType 上做。

新增两个文件 + 改五个既有文件（我方逐一对应，你们类名完全同名）：

| 部件 | 我方实现要点（本世代 javap 事实） |
|---|---|
| `ScopeTextSubmitter`（新增，184 行） | `Font#prepareText(FormattedCharSequence,float,float,int,boolean,boolean,int)` → `Font$PreparedText#visit(Font$GlyphVisitor)`；本世代 visitor 是 `acceptGlyph(TextRenderable$Styled)` + `acceptEffect(TextRenderable)` 两个 default 方法（**没有** 26.1.2 那个带 `(x,y,width)` 的 `accept`）；按 `TextRenderable#textureView()` 分组；每组一次 `submitCustomGeometry(new PoseStack(), ScopeRenderTypes.maskedText(pageId), (p, consumer) -> 逐个 r.render(pose, consumer, packedLight, false))` |
| 字体页壳纹理 | 本世代 `RenderSetup$RenderSetupBuilder` 的公开纹理入口只有 `withTexture(String, Identifier)` 与 `withTexture(String, Identifier, Supplier<GpuSampler>)` 两个（javap）；`RenderSetup$TextureAndSampler` 是 `record(GpuTextureView, GpuSampler)`，但只在 `RenderSetup#getTextures()` 侧可见 ⇒ **没有"直接塞 view"的重载**，所以**必须**注册壳 `AbstractTexture`（`TextureManager#register(Identifier, AbstractTexture)`），每帧把 protected `textureView`/`sampler` 指向当前页；`sampler` 用 `RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)`；`close()` 空实现（不拥有纹理）。我方只填 `textureView`/`sampler`、不碰 `texture` 字段 |
| `ScopeRenderTypes`（+72 行） | `MASKED_TEXT_PIPELINE = clonePipeline(RenderPipelines.TEXT, "pipeline/scope_masked_text")` + `withFragmentShader("core/scope_text_final")` + `withSampler(两条既有深度 uniform)` + `RenderPipelines.register` + `assignPipelineToIris(pipeline, "HAND_TRANSLUCENT", "scope_masked_text")`；`maskedText(pageId)` 走 `RenderSetup.builder(…).withTexture("Sampler0", pageId).withTexture(两条深度, pageId).useLightmap()` → `RenderType.create(…)` → 外层 `DepthCopyRenderType(…, Operation.MASK)` |
| `scope_text_final.fsh`（新增，65 行） | = 本 era `rendertype_text.fsh` 逐行克隆（`#moj_import <minecraft:fog.glsl>`/`dynamictransforms.glsl`、`texture(Sampler0,texCoord0)*vertexColor*ColorModulator`、`color.a<0.1 discard`）**去掉 `apply_fog`** + `tacz_ScopeMaskMode`/`tacz_ScopeFinalOverlay` + 孔径深度比较（`apertureDepth < worldDepth - 1e-6` 才保留）。⚠ 本世代 `shaders/core/` 下**没有** `rendertype_text.json`（管线全在 Java 侧 `RenderSetup`）⇒ 别去找 JSON、也别新建一个 |
| `ScopeDepthCopyState`（+14 行） | 只加 `public static boolean isMaskCycleValid() { return maskValid; }`；语义 = "本帧走完『世界备份 + 孔径拷贝』才允许采样掩码"，否则**宁可不裁** |
| `TextShowRender`（+40 行） | 加 `clipToScopeMask` 旗与四参构造（**保留三参重载** ⇒ 枪包/上游注册面不受影响）；任务体：`if (!ScopeTextSubmitter.submit(…)) collector.submitText(…)`；瞄具侧传 `true`、枪身侧显式 `false` |
| `BedrockAttachmentModel` / `BedrockGunModel` | 各改一行注册（true / false） |

**必须一起抄的两条失败语义**（这是我方实现与 26.1.2 的差异点，也是可运维性的关键）：
1. 掩码不可用 ⇒ `submit` 返回 `false` ⇒ 回退 vanilla `submitText`：**不丢字、不画错**，最差回到"贴边溢出"。
2. fail-closed：fsh 里 `if (tacz_ScopeFinalOverlay == 0) discard;` ⇒ 若哪天**文字整个不见**，
   第一嫌疑人是这条丢弃路径（查这次绘制是否真用了 `maskedText` 且被 `Operation.MASK` 包住），
   不要先去怀疑"裁剪太狠"。

**我方实机结果（维护者 2026-09-01 报 PASS）**：无光影 A 格（文字在镜内、层序 画面→文字→准星→镜框、
贴边字形被圆孔裁掉）+ 剧本 F（F5 重载资源包后文字仍在且仍被裁剪）。
**光影格 C/D 与 PIP 格 E 未验**（其中 PIP 格对你们是非项）。按你们 `docs/COMPATIBILITY.md` 的口径，
这条最多算"姊妹世代旁证"，**不能写成你们的 ✅**，除非你们自己跑过。

**加载器差异（我方核过、你们要注意的）**：你们 1.21.11 线也在用 `net.minecraft.resources.Identifier`
（我方逐个 import 核过）⇒ §4 的类名/import **可以整段照搬**；真正要改的只有
①`net.fabricmc.api.EnvType` 注解（我方新增文件已刻意不带它，与你们 `scope` 包风格一致）；
②日志器（我方 `GunMod.LOGGER`）；③ mixin 注册分包 —— 你们有独立 `tacz.iris.mixins.json`，
我方 §4 不新增 mixin，所以只要不引入新 mixin 就无此项。

## 5. 请你们**不要**从我方同步的东西（避免反向污染）

| 我方条目 | 为什么对你们是非项 |
|---|---|
| PIP 二次渲染的"提交节点清理 + 逐帧状态重提取"（Fabric 26.1.2 的 `0bf4c482`，我方评估后判**不加**） | 我方 javap 实测本世代 `LevelRenderer` **没有** `extractLevel`：`extractVisibleEntities`/`extractVisibleBlockEntities`/`extractBlockOutline`/`extractBlockDestroyAnimation` 与 `LevelRenderState.reset()`（偏移 197→1021）在同一次调用链里 ⇒ 每次 `renderLevel` 自填自清；且 `renderAllFeatures()` 自带 `submitNodeStorage.clear()`。你们也没有 `ScopePip*`，等你们真做 PIP 时再回看这段 |
| `ScopePipRenderInterval` 隔帧渲染（我方仍待批） | 同上，你们无 PIP |
| FCAP 配置落盘那一类改动 | 我方是 `fuzs.forgeconfigapiport` 21.11.1，你们不用 FCAP；Fabric 侧那条"配置重启被重置"是 26.1.x 世代的 FCAP 断桥问题，与本世代无关 |
| 全部 TML / mesh GPU 相关（`MeshGpu*` 两键默认值之争、`FeatureRenderDispatcher` 消费点、`MeshPoly*` 三键） | 你们 `1.21.11` 线没有 meshloader 包 |
| 我方 `.github/workflows/` 的状态 | 你们已装 `consistency.yml`；我方这边那份模板要由仓库所有者粘贴，我方不代改 |

## 6. 反向：我方需要你们回的 / 可提供的

1. 要 §4 的**完整 patch**，直接 `git show d076cf5 -- src/main/java/com/tacz/guns/client/render/scope/ScopeTextSubmitter.java src/main/resources/assets/tacz/shaders/core/scope_text_final.fsh src/main/java/com/tacz/guns/client/render/scope/ScopeRenderTypes.java`（我方分支 `arena/01a05759-tacz-refabricated-unofficial`）；
   P0-a 的两个提交同理：`1cfa42b`、`cb39564`；P0-b 是 `c9b8ba1`。
2. 若你们已经自己动过 §2/§3 任一条，请回一句"哪几条已在哪个提交"，我方就把本表的"你们现状"改成
   带 commit 的对照（我方这份是 `e3d9dd5c` 时点的快照，会随你们推进而过期）。
3. 我方欠 26.2/Fabric 线的一条（"世界 GPU 消费点"四点位表）对你们无意义（你们无 mesh GPU），不必代问。

## 7. 我方全部真实提交（31 个，不含 ci-log）的剩余确认项 —— 供你们判断"哪些别等我们"

| 我方条目 | 剩余项 | 与你们的关系 |
|---|---|---|
| 镜内文字：flush 洞 + `I18n.get` + 掩码裁剪（`1cfa42b`/`cb39564`/`c9b8ba1`/`6562b59`/`d076cf5`） | 光影格 C/D（与 PIP 格 E）是否在 PASS 批次内待维护者明确；其余已 PASS | **就是 §2-§4**，可即刻开工 |
| 天空自发光 `f8d19ed`（`MeshPolyIlluminatedRealSky`） | 仍 **待实机**（L-7），只改 poly 层 | 非项（你们无 mesh） |
| `9c29572`/`ab11a84`（绕序默认退回 + 立项） | 规避有效；不自洽本身不修、已立项（L-8b「反光/高光偏一侧」） | 非项 |
| `5ac0262`/`25ca08e`（EMISSIVE 永久降级修复 + 光影两键默认关） | 两键仍在、每帧读、不需重启；与 Fabric 26.1.2 的默认值选择相反（他们 `3e4eeb16` 开 ON） | 非项 |
| 对 26.1.2 线本轮 PIP 回移植的**评估结论**（那是**他们**的提交 `18e4553`…`55ad5e2`，我方只出结论、我方线上没跟做 PIP 重渲染，`SCOPE_PIP_RERENDER` 仍锁 false） | A 结案（不加）；B 已 PASS；C 待批；他们的双遍风险仍待实机 | 见 §5 头两行 |
| L-12 世界 GPU 消费点是否要从 `renderAllFeatures` RETURN 挪走 | **OPEN**，等 Fabric 26.1.2 回四点位表；我方不动代码 | 非项 |
| L-13 FCAP 世代边界记录 | CLOSED（不跟，静态证据） | 非项 |
| L-4 CI 对齐 | 待仓库所有者粘贴 workflow | 与你们已装门禁对照可互为模板 |

## 8. 交接纪律（我方这轮踩过的、你们 AGENTS 里同款的坑）

1. 新 mixin 必须**同 commit** 注册进对应 mixin 配置（你们分 `tacz.mixins.json`/`tacz.iris.mixins.json` 两份，
   注册错文件比漏注册更难查）；混淆分支里禁 `lambda$xxx$N` 目标名。
2. **编译过 ≠ 运行时安全**：§4 的自定义 program 只有实机能证明；`require = 0` 之类的降级要写清降级后是什么行为。
3. TEMP 的 CI javap 探针：一轮加、结论回来那轮**必删**（我方 v1→v5 都按这条收尾，日志随 ci-log 留档）。
4. 文档表格：单元格内禁换行、禁裸 `|`；改完跑列数审计（我方这轮就复现并修过两次）。
5. `lang` 永不整文件重写；新增配置键要同步 TOML 默认值 / 配置类 / en+zh 键与描述。
6. 动 `mod_version` ⇒ 按你们 AGENTS §1 同步 README 三处 + `CHANGELOG.md`，并跑
   `bash scripts/check_release_consistency.sh --strict`（本清单不改版本号）。
