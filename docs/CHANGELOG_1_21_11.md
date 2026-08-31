# 1.21.11 分支变更记录

本文件只记录 **1.21.11 分支相对 26.1.2 分支** 的变更，按交付轮次倒序。
每一轮的详细定位过程见 `PORT_1_21_11_PHASE2.md`。

---

## 姊妹对象更正：同步清单改写为发给 NeoForge 项目的 1.21.11 分支（新增第二份清单，第一份保留并更正收件人）2026-09-01

**维护者一句话纠正**：上一节的"同步清单"对象写错了 —— 姊妹指的是隔壁
**NeoForge 项目 `q14433686-arch/TaCZ_Renovated` 的 `1.21.11` 分支**（tip `e3d9dd5c`，MC 1.21.11 /
NeoForge 21.11.45 / `mod_version=1.1.8+neoforge.1.21.11.R1-hotfix`），不是我方仓库的 Fabric 26.1.2 线。
⇒ 按"我方全部真实 commit 的剩余确认项 / 改动 / 新增"**重新核对对方树**后重写。
上一份**没有作废**（它写的正是 Fabric 26.1.2）：保留原文件、标题更正为"给 Fabric 侧 26.1.2 线"；
新事实另立新文件。两份各自的"你们"不同，**别互相代签**。

**对方现状实测七条（全部静态读码，逐文件 `gh api` + grep，不看对方自述）**：
① 对方 `client/renderer/snapshot/BedrockRenderSnapshot.java:93` 已有 `submitFunctionalTasks`，
但 `client/model/BedrockAttachmentModel` 的瞄具序列里**一个调用点都没有** ⇒ **根因一在姊妹线上同样存在**；
② `ScopeFinalOverlayState`（205 行）无 `PENDING_TEXT`/`queueFunctionalTask` ⇒ 延迟格即使补了①仍会丢文字；
③ `client/model/papi/PapiManager#getTextShow` 仍是 `I18n.get(textKey)` ⇒ **根因二同样存在**
（症状会从"没字"变成"Format error:"脏字）；④ `TextShowRender` 61 行旧形无 `clipToScopeMask`、
无 `ScopeTextSubmitter`、`shaders/core/` 无 `scope_text_final.fsh`、`ScopeDepthCopyState` 只有私有 `maskValid`
⇒ 掩码裁剪整族（我方 `d076cf5` 六文件）适用；⑤ 对方 `RenderConfig` 只有 `SCOPE_MASK_ENABLE`、
全仓 `ScopePip*` 0 命中 ⇒ 我方 PIP 两条结论对他们是**非项**；⑥ 全仓 `meshloader`/`PolyMesh` 0 命中
⇒ 我方**全部** TML/mesh GPU 项（含仍未结的 L-12、L-8b）对他们非项；⑦ 对方 iris 设施齐
（`IrisCompat#assignPipelineToIris(RenderPipeline,String,String)`、`GlCommandEncoderScopeDepthCopyMixin`、
`tacz.iris.mixins.json`）且映射命名与我方一致（同用 `Identifier`）⇒ 可近逐字移植。

**新文档** `docs/lineage/SYNC_CHECKLIST_1211_NEOFORGE_SISTER_20260901.md`：
§1 对方现状表；§2 P0-a 四处改动 + 两个坑（`OrderedSubmitNodeCollector` 递错类型编译不过 = 26.1.2 首版挂因；
立即分支 flush 必须挪出 `!bodySnapshot.isEmpty()` 门外）+ 判据日志；§3 P0-b（**只给语义**，
成员名要求对方按他们 AGENTS §3 红线 2 自证，我方 `Language#getInstance().getOrDefault` 不可照抄）
+ 两处 tooltip 同源（26.2 仍未改，建议三方一起收）；§4 掩码裁剪的文件映射表 + 本世代四个渲染 API 事实
（含"**没有** `withTexture(String, Supplier)` 重载 ⇒ 必须注册壳纹理"）+ 两条失败语义 +
"深度剔除等价裁剪"这条我方**已撤回的错误**明写出来，防止对方重走；§5 别同步清单（PIP 两键、FCAP、
全部 mesh、我方 CI 状态）；§6 对方可直接 `git show` 我方哪三个提交；§7 我方 31 个真实提交的剩余项
与"哪些不必等我们"；§8 双向纪律（mixin 分包注册、TEMP 探针同轮删、`--strict`、表格审计）。
`docs/README.md` 索引加行。

**边界**：本轮**未动任何代码**、未改版本号；"对方还没做 X"= 读对方文件所得，不等于我方替对方验证过任何做法；
我方 PASS 对他们是**旁证**，按对方 AGENTS §4 口径不能升级成对方的 ✅。

---

## 镜内文字裁剪实机 PASS + 给 26.1.2 的同步清单（2026-09-01 同日收尾）

- **维护者报 PASS**，覆盖本轮验收清单的两格：无光影剧本 A（文字在镜内、层序正确、**贴边字形被圆孔裁掉**）
  与新增的剧本 F（F5 重载资源包后文字仍在且仍被裁剪）。据此收口的三处：镜内文字篇 §3 残留① 与 §4 A 格
  改为 PASS 记录、评估篇 §2.3 加"实机结果"段（并明确"重载未复现 ≠ 缓存设计被修好"）、账本 L-10/L-11 状态。
  光影格 C/D 与 PIP 格 E **是否同批 PASS 未明确** ⇒ 那三格继续按"未验"读，不跟着改口径。
- 新增 `docs/lineage/SYNC_CHECKLIST_1211_TO_2612_PIP_20260901.md`（**发给同仓库 Fabric 26.1.2 线**的同步清单
  —— 该文件里"你们"指 Fabric 26.1.2，不是姊妹 NeoForge 项目；姊妹对象的清单见上一节），内容按"能不能直接用"排：
  §1 我方可直接采信的结论（含两条**不必对齐**的判定：他们 `0bf4c482` 的重提取在 1.21.11 结构上无对应物；
  他们 `58831e4f` 的 FCAP `save()` no-op 配置重置病在 21.11.1 上不存在，我方**不跟**，也防止后人把
  `ConfigPersist` + 两个 accessor mixin 当对齐项搬过来）；§2 建议他们补的三件小事（log-once 判据、
  重载剧本、fail-closed 丢弃路径写进"若不符"）；§3 请他们把我方 javap 结论转给 26.2 作为"镜外实体消失"
  的候选根因；§5 **我方反过来向他们索取**"世界 GPU 消费点四点位表"；§6 请回项 7 条的逐项关闭复查。
- 按他们 tip `7562abcb` **读文件**复查（不看自述）：孤儿 mixin 配置、仓库卫生、`SoundEngineMixin` 目标名、
  `PapiManager` 与两处 tooltip 的纯查表改造 —— 我方 7 项请回**全部关闭**，只剩"光影下两个 GPU 键默认值
  相反"一条开放（等他们的实测数据）。复核篇因此加 §10 收口表并声明"后续沟通一律走同步清单"。
- 账本新开两条 Open：**L-12** —— 他们 `99e505f6` 证明 26.1.2 上把世界 GPU 表挂在
  `FeatureRenderDispatcher#renderAllFeatures` RETURN 会 (1) 没人消费（永久回落 collector）(2) 被 Iris 的
  in-level 手部渲染以"手部 MV 槽"消费 ⇒ 几何贴视空间；我方 1.21.11 的消费点**正是** `renderAllFeatures`
  RETURN，依据只有自己那条字节码注释、没有四点位全表 ⇒ 若本世代主通道那次调用不是"世界 solid flush"，
  消费点要挪；这也可能与仍未修的 L-8b「反光/高光偏一侧」同源。**L-13** —— 上述 FCAP 世代边界的正式记录。
  本轮对 L-12 只立项、不动代码。

---

## 镜内文字裁剪（2026-09-01，B 落地：7 个文件；版本号未变；**实机未验证**）

- 按维护者"动"的指示，把 26.1.2 `e1c550ee` 的**语义**移植到本世代（不是搬代码）：镜内 `text_show`
  文字现在会被目镜孔径掩码裁剪。新增 `client/render/scope/ScopeTextSubmitter.java` 与
  `assets/tacz/shaders/core/scope_text_final.fsh`；`ScopeRenderTypes` 加 `MASKED_TEXT_PIPELINE`
  （`clonePipeline(RenderPipelines.TEXT)` + 换 fsh + 两条既有深度 sampler + `assignPipelineToIris`
  `HAND_TRANSLUCENT`）与按字体页缓存的 `maskedText(Identifier)`（外层 `DepthCopyRenderType(MASK)`）；
  `ScopeDepthCopyState` 只加一个 `isMaskCycleValid()`；`TextShowRender` 加 `clipToScopeMask` 旗
  （瞄具侧 true、枪身 false，三参构造保留 ⇒ 枪包 API 不变）；`BedrockAttachmentModel` /
  `BedrockGunModel` 各改一行注册。
- **三处刻意偏离 26.1.2**（都因为本世代 API 不同，逐条有 javap 依据，见评估篇 §2.1）：
  ① visitor 用本世代的 `acceptGlyph(TextRenderable$Styled)` / `acceptEffect(TextRenderable)`，
  不再是他们覆写的 `accept(r,x,y,w)`，位置交给 `prepareText` 的 x/y，字形坐标由
  `TextRenderable#render(Matrix4f, VertexConsumer, int, boolean)` 自带；
  ② `PageHandle`（字体页壳纹理）只填 `textureView` 与 `sampler`、**不碰** `texture` 字段 —— 本世代绑定
  链路是 `RenderSetup(Identifier) → TextureManager#getTexture(id).getTextureView()`，少依赖一个未验证符号；
  ③ 新增一条 log-once（`In-scope text is now clipped to the ocular aperture mask (N font page group(s))`），
  否则"走了掩码"与"回退 vanilla"在屏幕上无法区分。
- 失败语义与 26.2/26.1.2 一致：**掩码不可用就回退 vanilla `submitText`**（宁可贴边溢出，不丢字、不画错）。
  反向风险也写进剧本：着色器在 `tacz_ScopeFinalOverlay == 0` 时丢弃全部像素 ⇒ 若某天文字整个消失，
  第一嫌疑人是这条丢弃路径（镜内文字篇 §4 剧本 A 的"若不符"格已这么写）。
- 两处已知未覆盖，明写不糊：① 字体图集页换 view 后 `MASKED_TEXT_TYPES` / `PAGE_HANDLES` 仍按 id 缓存
  （与 26.1.2 同源设计）⇒ 新增剧本 F 专测 F5 重载；② `DisplayMode.SEE_THROUGH` / `POLYGON_OFFSET`
  没有掩码版本（瞄具文字只用 NORMAL，本轮不需要）。
- 同步：镜内文字篇 §3 残留①改为"已落地待实机"，§4 剧本 A 恢复"贴边被裁"这条判据并加掩码日志判据、
  加剧本 F；评估篇加 §2.3 施工清单；账本 L-11 的 B 改为"已落地待实机"。AGENTS §2：本轮证据只到
  "CI 编译通过"，**不含任何实机或性能结论**。

---

## 评估 26.1.2 本轮 PIP 回移植（2026-09-01，评估 + 探针那一半；B 的实现见上一节）

- 新增 `docs/lineage/SYNC_REVIEW_2612_PIP_BACKPORT_20260901.md`：逐提交核他们 `0a77ef52`…`8aca7374`，
  可加项只有三条（A 窄遍后的状态重提取/清提交节点、B 镜内文字掩码裁剪、C 隔帧 `ScopePipRerenderInterval`），
  其余（`ScopePipRenderState` 922 行、`ScopePipDepthDebug`、`DepthHandle` 只读快照、FOV/hand-pass/Iris
  接线）都是**从我们这棵树搬走的**，回搬等于空转；配置面逐字段比对：他们 11 个 `SCOPE_PIP_*` 键、我们 10 个，
  唯一差异正是 C 那条 `SCOPE_PIP_RERENDER_INTERVAL`。
- **撤回一条我自己写错的技术论断**：上一轮我在镜内文字那节写「文字走字体管线 ⇒ 会被镜筒深度剔掉 ⇒
  等价于 26.2 的掩码裁剪」。他们用实机否证了这条（`e1c550ee` 的动机正是它不成立）：`submitText` 下游是
  vanilla 字体管线，**不吃**孔径深度 ⇒ **立即路径与延迟路径都会溢出圆孔**。已同步改口的三处：
  `BedrockAttachmentModel` 的 flush 注释（现在只承诺"层序正确"）、
  `docs/lineage/SCOPE_TEXT_SHOW_1211_20260901.md` §2 与 §3 残留①（范围从"延迟那一格"放大到"全部"）、
  §4 剧本 A 格的期望（删掉"不越过镜筒边缘"这一条判据，改为"贴边溢出属待加项 B，不算 flush 回归"）。
- 探针 v3 已回（CI 编译类路径 javap）：**A 判"不加"**——本世代 `LevelRenderer` 没有 `extractLevel`，
  提取是 `renderLevel` 内部私有步骤且以 `state.LevelRenderState` 为入参，`GameRenderer` 亦无独立 extract 阶段，
  而 `renderAllFeatures()` 自带 `submitNodeStorage.clear()`（与我们 `FeatureRenderDispatcherMixin` 的字节码记录一致）
  ⇒ 他们那两步对我们要么是空操作要么无对应物；`ScopePipRerender` 类注释里"把防护留给后续阶段"已改写为这组结论。
  **B 需要改写**：`Font#prepareText` 七参同形，但本世代 `Font$GlyphVisitor` 是
  `acceptGlyph(TextRenderable$Styled)`/`acceptEffect(TextRenderable)`，没有他们覆写的 `accept(r,x,y,w)`；
  `TextRenderable` 自带 `textureView()` 与 `render(Matrix4f, VertexConsumer, int, boolean)` ⇒ 按页分组反而更省事；
  `shaders/core/` 里没有 `rendertype_text.json`（管线由 Java 侧 `RenderSetup` 定义），本 era 的
  `rendertype_text.fsh/.vsh` 原文已随探针打出并抄进评估篇，作为 `scope_text_final.fsh` 的克隆底版。
- 挂了 TEMP javap 探针（`build.gradle`，`compileJava.finalizedBy`，v3→v4→v5 三轮，结论到手即删）。
  **v4 把 A 的依据收紧、B 的做法定型**：本世代 `state.LevelRenderState#reset()` 确实存在并被调用
  （`LevelRenderState.reset:()V` 在字节码 @1021），但 `WeatherEffectRenderer.extractRenderState` @295、
  `SkyRenderer.extractRenderState` @327、`WorldBorderRenderer.extract` @376、`ParticleEngine.extract` @814
  与它在**同一个方法体的单调偏移序列**里 ⇒ 本世代是"每次调用自填自清"，不是 26.1.2 的"先 extract 后 render"
  两段式 ⇒ A 仍判**不加**（v5 的上下文 dump 显示这段偏移里还有 Profiler 的 entities/blockEntities/blockOutline/blockBreaking 分段与尾部 `LevelTargetBundle.clear()`，是主渲染入口的轮廓；方法名的
  归属属交叉推证，但判定强度不依赖方法名 —— 要害是「填与清在同一次调用链内」）；
  另 `LevelRenderer` 自带 `private final SubmitNodeStorage submitNodeStorage` 并在同一方法体内两次调
  `renderAllFeatures()` ⇒ 窄遍的提交在窄遍内部就冲掉，不会攒给主遍 ⇒ 他们的 `clear()` 对我们仍是空操作。
  B 侧零件全部对上我们树上现成写法（`clonePipeline(RenderPipelines.TEXT)` + `withFragmentShader` +
  既有两条深度 uniform + `RenderSetup.builder(...).withTexture(...)` + `RenderType.create(name, setup)` +
  `DepthCopyRenderType(Operation.MASK)` + `assignPipelineToIris("HAND_TRANSLUCENT")`），落地清单见评估篇 §2.2；
  `AbstractTexture`（三个 protected 字段 + `getTextureView()`）与 `TextureManager#register(Identifier, AbstractTexture)`
  证明他们的"壳纹理"在本世代同样可行。v5 答完最后一问：本世代 `RenderSetup$RenderSetupBuilder` 的公开纹理入口只有两个 ——
  ⇒ 本世代必须走他们的"壳 AbstractTexture"路线（`withTexture` 只有 `withTexture(String, Identifier)`
  与带 `Supplier<GpuSampler>` 的那一条，`TextureAndSampler` 只在 `RenderSetup#getTextures()` 侧可见）。
  `AbstractTexture` 的三个 protected 字段可直接赋值 ⇒ 空壳子类可行；`Font$DisplayMode` =
  NORMAL/SEE_THROUGH/POLYGON_OFFSET，镜内 `text_show` 只需要 `TEXT` 一族。**三轮探针收工，TEMP 块已从
  `build.gradle` 删除**（树里只留 `-PmeshProbe` 那条 opt-in 通道）；B 的施工单是评估篇 §2.2，
  事实层面无阻塞，剩下的是优先级判断（它动的是字体绘制 + 一条新 `RenderType` 族）。
- 他们 `3e4eeb16` 把 `MeshGpuUnderShaders`/`MeshGpuWorldUnderShaders` 默认改回 ON（R3）——与我们这边的
  B 测结论相反（`9c29572` 退回 false）。本轮**不跟**，已回问其实测数据；账本 L-11 记了这个来回。

---

## 镜内 `text_show` 文本缺失（2026-09-01，代码修复：六个文件；版本号未变）

- **症状**：MK5 / MK5HD 的镜内弹药计数在 1.21.11 线**从不显示**（不是位置偏、不是偶发丢帧）。
  根因不在字体管线：`BedrockAttachmentModel.submit` 的深度孔径路径自己 capture + 自己按
  `SCOPE_APERTURE/BODY/DEPTH_CLEANUP/OCULAR_RING` 重放几何，**没有走 `super.submit(...)`**，
  于是 `BedrockModel.java:381` 那句 `snapshot.submitFunctionalTasks(collector)` 被整个绕开；
  而 `capturePart` 遇到 `IFunctionalSubmitter` 时**只**把任务塞进 `functionalTasks` 就 return
  （几何不采集）⇒ `TextShowRender.extract` 的 `submitText` 任务无人 flush，静默消失。
  静态证据三条（本篇全部可复算）：`submitFunctionalTasks` 全仓仅 1 个调用点；26.2 的
  `BedrockAttachmentModel.submit` 第 681 行确有 `super.submit(...)`（所以我方独立确认了"26.2 没这个洞"，
  不是转述）；本仓 `find` 无 `ScopeTextSubmitter`/`scope_text.*sh` ⇒ 26.2 的 `9d036594` 是**镜内裁剪**、
  与存在性无关，两边修法不可互换。
- **修法**（按 26.1.2 已提交的 `c290a1f3`+`74eb0ad2` 口径，另加两处差别）：`BedrockRenderSnapshot` 加
  只读 `functionalTasks()`；瞄具两条分支都补 flush —— 非延迟走 `collector` 默认 `order(0)`
  （cleanup `-1` 与准星 `1` 之间，**只保证层序**）；同轮我写的「文字被镜筒深度剔掉即等价掩码裁剪」
  已**撤回**：26.1.2 实机证伪该论断（他们 `e1c550ee` 改走掩码裁剪）⇒ 字体管线不吃孔径深度，文字会溢出圆孔。
  详见 `SCOPE_TEXT_SHOW_1211_20260901.md` §2/§3 与 §B 待加项，`deferReticleToIrisFinalOverlay`
  时经 `ScopeFinalOverlayState.queueFunctionalTask` 与准星/镜框同族推迟、在 reticle 之前用
  `task.submit(submitNodes)` 提交（`OrderedSubmitNodeCollector` 不是 `SubmitNodeCollector`，
  这正是 26.1.2 第一版编译失败的原因；本仓旁证是 `GunPreviewRenderer.java:91`）。
  **差别①**：`else` 分支我们把 flush 放在 `!bodySnapshot.isEmpty()` 门**外**（`isEmpty()` 只看几何 ⇒
  他们那边「只有文字没有本体几何」的快照仍会丢）；**差别②**：`ocularRingSnapshot` 的任务也 flush
  （镜框子树下的文字此前两处快照都拿不到）。
- 附**日志判据**（`submitScopeText` 每局一次 INFO，带 `scopeMask=` 直接标明剧本的哪一格；延迟那格由
  `ScopeFinalOverlayState` 报 `{} texts` 计数），这样验收不必只靠肉眼判断「镜里有没有字」。
- 明确**未验证**：本轮只有代码改动与 CI 编译，**没跑实机**。四格剧本（掩码开/关 × Iris 开/关 + PIP）
  与日志判据写在 `docs/lineage/SCOPE_TEXT_SHOW_1211_20260901.md` §4；已知残留也写在那里（延迟覆盖层那一格
  文字不参与镜筒深度测试 ⇒ 贴边计数仍可能溢出，真要裁需移植 26.2 的 `ScopeTextSubmitter`）。
### 同日第二处根因：`PapiManager` 把「查表」写成了「格式化」（与 26.2 的 `ec51f556` 同源）

- 维护者指出「和 26.2 近期修的 BUG 一模一样」成立：本分支 `model/papi/PapiManager.java:28` 用的是
  **`I18n.get(textKey)`**。1.21.11 的 `I18n.get` 经 CI javap 实测是「`Language.getOrDefault` →
  `String.format` → catch `IllegalFormatException` ⇒ 返回 `"Format error: " + 原文`」。枪包的
  `textKey` 常是内联显示串（MK5HD 用 `"%ammo_count%"`）⇒ 查表落空后 `%a` 被当格式说明符 ⇒
  镜内出现「Format error: … 末尾一个弹药数」；含 `%` 的译文（`%s发`）同炸。
- 修法与 26.2 一致：`Language.getInstance().getOrDefault(textKey)`（纯查表、不格式化）。该成员在
  **我们这代**的存在性由同一次 javap 证实（探针输出在 `build-reports/compile-java.log`，TEMP 块已删），
  不是"照抄一个可能不存在的 API"。
- **同一形扩散到 tooltip，且 26.2 漏了两处**：`ClientAttachmentItemTooltip:165`、`ClientBlockItemTooltip:75`
  也是 `I18n.get(tooltipKey)`，下游立刻 `split` 换行 + `Component.literal` ⇒ 从不需要格式化 ⇒ 本分支三处
  一起收；26.2 至今只改了 `PapiManager`，26.1.2 三处全未改（已按 §9 第 7 项回给他们）。
- **两条根因是叠加的**：① flush 缺失 ⇒ 文字根本不出现；② 格式化 ⇒ 出现但是脏。26.2 只有 ②（他们 body 走
  `super.submit` 天然带 flush）⇒ 症状是「有字但脏」；本分支两个都有、① 在前 ⇒ 「什么都没有」。
  ⇒ 只移植 26.2 那条对本分支无效，只修 ① 会立刻暴露 ②。取证与三仓分布表见
  `docs/lineage/SCOPE_TEXT_SHOW_1211_20260901.md` §5。

- 顺带修文档 bug：`docs/RENDER_PIPELINE_SCOPE_AUDIT_26_1_2.md` §6.1 把 order `1`/`2` 两行的内容写反了
  （代码常量是 `SCOPE_RETICLE_ORDER=1`、`SCOPE_OCULAR_RING_ORDER=2`），已对齐并在 §5.11 补记本条缺陷。
- 版本号**未动**（仍是 `1.1.8+fabric.1.21.11.R3`）⇒ 按 AGENTS §1，README 那 6 处不需要跟改。

---

## 给 26.1.2 的移植复核（2026-09-01，只读复核；本分支代码无改动）

- 新增 `docs/lineage/SYNC_REVIEW_2612_TML_PORT_20260901.md`：把 26.1.2 那版 TML/GPU 移植**按代码**核了一遍
  （取他们 `arena/01a05170` @ `79a6391` 整棵树），每条都附可复算命令。三条 P0：
  ① **世界 GPU 表整条没接线** —— `FeatureRenderDispatcherMixin`（`renderAtWorldFlush()` 的唯一调用者）
  存在但**没写进任何 `*.mixins.json`** ⇒ 第 3 步永远静默不生效，`require = 0` 又把"目标找不到"也静音，
  表现与"设计上的静默回退"完全一样；② **lang 曾被整文件覆盖**（我按 commit 数出移植轮 `tacz/en_us.json`
  只剩 36 个键、`item.*`/`tooltip.*` 全空 ⇒ 就是维护者看到的"物品名/选项名变成 raw key"），`79a6391`
  已救回 334 键，但**仍缺 2 个被他们代码引用的键**（`attribute.name.tacz.bullet_resistance` ←
  `ModAttributes.java:16`、`commands.tacz.arguments.enum.invalid` ← `EnumArgument.java:35`）；
  ③ 孤儿 `tacz.compat.acceleratedrendering.mixins.json` 且它引用的 `BedrockPartMixin` 不存在（我们那条
  L-5 的反面）。另有 P1：`latest.log`/`.idea`/`.gradle` 被 track、`lrtactical.mixins.json` 少注册
  `client.SoundEngineMixin`（且那文件里还留着 1.21.11 的 `method_19757`）、README 对 mesh 零提及、
  他们重写的 `docs/MESH_LOADER.md` 丢了「枪包怎么用」「弹匣链路」两节。
- **他们回给我们的 Q8 改写了我们的解释**：26.1.2 用字节码核到他们那版 `RenderPipelines.ENTITY_CUTOUT`
  显式 `.withCull(false)` ⇒ §5.7/§6 里「collector 剔背面 ⇒ 绕序一反转就把朝外的面剔掉」这一支降级为
  未排除的次要分支；主解释改为**「光影包按 `gl_FrontFacing` 取反法线」是承重的**（上游那对组合 = "错两次
  对"，单方面反转发射顺序等于又翻一次 ⇒ 朝光面变暗、亮的是远侧内壁，图像上与剔面不可分）。
  判别法同步换成"`InvertNormals=true` 与 `MirrorReverseWinding=true` 两格是否几乎相同"（§6 第 ④ 步），
  仍未跑。Q9（枪包绕序约定）双方都还没测；Q10 他们与我们同选 ③（维持与上游一致、只记录不修）。
  证据边界：他们那条是**他们版本的字节码**，1.21.11 的 `ENTITY_CUTOUT` 剔除状态我们至今没核实过，
  所以旧的剔面分支保留而不删除。

---

## 1.1.8+fabric.1.21.11.R3（2026-08-31，R3 收口轮；版本号已改）

**本轮之上不再有独立的「未发版」段**：下面两条（TML 第 3 步、第 2 步 v2 收尾）连同本轮
一起构成 R3 的内容。`mod_version` 从 `1.1.8+fabric.1.21.11.R2-hotfix2` 改为
**`1.1.8+fabric.1.21.11.R3`**（去掉 `-hotfix` 后缀，与 26.2 侧 `5bb13af` 的 `R3` 做法一致；
SemVer 核心仍是 `1.1.8`，构建元数据不参与比较，见 `gradle.properties` 注释）。

- **第五轮（同日）：维护者复测 PASS + `MeshPolyMirrorReverseWinding` 退回 false**：
  ① **PASS**（本轮 jar、光影下两键为新默认 false）：「枪身挡住太阳/月亮那块继承自发光亮度」消失。
  按 AGENTS.md §2 把边界写清：这条验证的是**默认退回**（光影下全程 collector），**不**验证第四轮那条
  代码修法 —— EMISSIVE 闩锁当时是否真的参与，仍取决于老日志里有没有 `lightmap` 那行 WARN
  （判据表在 `MESH_LOADER.md` §5.10，两键打开再进一次光影即可自证，局内即时生效）。
  ② 退回 collector 之后显形的新问题：高模枪在光影下近乎全黑、高光只剩远侧。维护者拿**同枪包同光影的
  Forge 原版**做对照（两张截图）。根因是第四轮之外另一条我按推导做出的修复：`MeshPolyMirrorReverseWinding`
  （把镜像后的发射绕序整体反转）—— 推理只讲通了 `gl_FrontFacing` 那一半，没算到 **collector 用的
  `RenderTypes.entityCutout` 剔背面**：绕序一反转，被剔掉的是朝外的面，留下里侧 ⇒ 里朝外。
  与上游 `587763c` 的 `PolyMesh` 逐字对比核过：位置、法线（都是 `D·n`）、UV、三角形展开完全相同，
  **只差绕序这一位** ⇒ 关掉它即与上游/Forge 观感逐字等价（这一条是静态可证的，不依赖观感判断）。
  默认值随之退回 **false**（TOML / Cloth `setDefaultValue` / en·zh 语言键三处齐改，parity 脚本通过）；
  开关与实现都保留，`MESH_LOADER.md` §5.7 那张矩阵已按实机填好（第 4 格 = 新默认，第 1 格标 ❌）。
  **注意这条不是「反光问题修好了」**：绕序退回 false 之后，§5.7 最初那个症状（光影下高光像在错误一侧）
  回到「与上游一致」的状态，也就是仍然没人修好、只是不再比上游更糟；想继续只有两个不碰剔除的选项
  （`MeshPolyPreferPackNormals` / `MeshPolyInvertNormals`，都是 F3+T 生效的观感判断）。
  与绕序无关、确实留下的两条：退化面不再写零法线（避免包里 `normalize()` 出 NaN 的随机高光）、
  `MeshPolyPreferPackNormals`（上游本就有这条分支，被常量编译掉）。真正想「两头都对」需要
  collector 换 `entityCutoutNoCull` 或从数据反推绕序 —— **都没做**，需要实机，见 §5.7 末。
  **已立项记录（不再猜、也不再「顺手修」）**：光影包名确认为 **Complementary Shaders - Unbound**；
  `MESH_LOADER.md` 新开 **§6「未修 BUG 记录：poly 绕序 × 背面剔除」** —— 症状原话、环境与变量、四步复现
  （含我们自己唯一没做的那步：绕着枪看有没有「看穿外壳」的镂空）、三层来源与「为什么会互相抵消」、
  三个候选修法各要付什么。状态 **OPEN / 不修**：本分支只有规避（默认值退回与上游一致），数据层那个
  不自洽仍在，最初那条「高光像在错误一侧」也仍在。
  给 26.1.2 的同步写成 `lineage/SYNC_GUIDE_1211_TO_2612_TML_GPU_20260831.md` **§1.6 + §3 Q8-Q10**：
  他们那边还没有 TML（`meshloader` 目录 404、版本仍 `1.1.8+fabric.26.1.2.R2-hotfix2`、`UPSTREAM_GAPS`
  里没有 TML 条目），且 26.1.2 渲染层与 1.21.11 差得远（`RenderType#draw` 每批自建 `RenderPass`、
  `ScopeRenderTypes` 直接克隆 `ENTITY_CUTOUT` ⇒ **剔除状态他们能静态读到**，我们只能反推）。所以那节的
  要求是「你们自己决定 + 别照抄我们任何一版做法（含那个开关键名）」，不是搬实现；§3 从七件事扩到十件。
- **第四轮：B 命中 —— 光影下的两个 GPU 开关退回默认关 + 修掉一条 EMISSIVE 永久降级（本轮）**：
  上一条留下的 A/B/C 判别维护者跑完了：**A（`MeshPolyInShadow=true`）无效**，
  **B（把光影下的 `MeshGpuUnderShaders` / `MeshGpuWorldUnderShaders` 关掉）有效**，且第一人称、
  第三人称、展示台三种语境表现一致。⇒ 「poly 不进阴影图」这个解释被**否证**（`MeshPolyInShadow` 保持
  false，不为一个无效解释付每帧阴影遍的成本；§5.9 保留作排除记录），根因落在**我们自开的那个 GPU pass**
  与光影包照明语义的关系上：`EMISSIVE_PIPELINE` 带 `withShaderDefine("EMISSIVE")`，而且被
  `IrisApi.assignPipeline` 登记进 `IrisProgram.HAND/ENTITIES` ⇒ 包按「自发光 / 不查阴影」画这条几何，
  正是「挡住天体却继承天体亮度」的样子。由此修掉一条**独立的真缺陷**：`resolveLightmap` 以前一旦
  `getTextureView()` 返回 null 或抛异常就把 `lightmapUnavailable` **永久**置真（只 WARN 一次，整会话不再
  重试），此后每一帧都走 EMISSIVE。改法：① 去闩锁，每帧重试（`getTextureView()` 是缓存读），日志只去重；
  ② 光影下真取不到 lightmap 就**整条拒收**（`gpuMasterUsable()`），退回 collector 由包正常照明 ——
  兜底不该改变照明语义，宁可不进 GPU；`GPU world submit refused:` 补了这个原因串，手/通用那条以前是
  **静默**拒收（第一人称无法从日志判定），现在也有一行去重的 INFO
  `GPU path refused while a shader pack is active: the level lightmap view is unavailable`
  （两行都在状态恢复时复位，不逐帧刷）。下一轮判据表在 `MESH_LOADER.md` §5.10。
  **默认值随之退回**：`MeshGpuUnderShaders` / `MeshGpuWorldUnderShaders` = **false**（`MeshPolyIlluminatedRealSky`
  同样 false —— 它是上一轮我按误读的症状写的，不是维护者报的问题，见 §5.8 的定性块）。TOML 默认值只影响
  新档：老档里已被翻成 true 的用户需要在 Cloth 里手动关，或删掉 `[mesh_loader]` 段。
  **证据边界**（AGENTS.md §2）：现象与 A/B 结论来自维护者实机；本轮的代码改动只到静态 + CI，
  「EMISSIVE 兜底是否就是你那次的实际路径」取决于日志里有没有那行 WARN —— 已在 §5.10 给出这条查证，
  没有它就还剩「自建管线 MRT/color target 集合与 `ENTITY_CUTOUT` 不一致」这条未排除的分支。
  **两项开关没有删**：修好之后想重测，局内打开即可（每帧读值，不用重启）。
- **实机状态更新**：第 3 步（世界语境常驻 VBO）维护者**两轮实机通过** —— 第一轮无光影
  （26.2 那条「相对视角固定」的坑未复现），第二轮打开 `MeshGpuWorldUnderShaders` 后
  **一遍过**（含 `Assigned mesh_entity_world to the Iris ENTITIES program.` 那条）。
  上一轮报的「光影下失效」经核实是**当时默认关**，不是缺陷。
- **默认值**：`MeshGpuUnderShaders` 与 `MeshGpuWorldUnderShaders` 由 false 改为 **true**
  （`MeshGpuBaking` / `MeshGpuWorld` 本来就是 true）→ 四项 GPU 开关默认全开。
  （**已被上面那条撤销**：维护者随后测出光影下常驻 VBO 会「继承」天体自发光，这两项同日退回默认 false；
  当时的 PASS 只覆盖几何/位置与「收得到 `gbuffers_*` 照明」，没覆盖「照明语义是否与 collector 等价」。）
  回退语义不变：钩子失联/绘制异常 ⇒ 分表静默回 collector，`catch (Exception | LinkageError)`，
  **从不回写配置文件**。
- **局内可配置（胶水轮次）**：`MeshyConfig` 的 **14 项全部**接进 Cloth「渲染」页
  （本段「反光 / 法线」那条又补了 3 项 ⇒ 现在 17 项）——
  新增 `MeshEnable` / `MeshPolyInPreview` / `MeshPolyInShadow` / `MeshLogStats` /
  `MeshMaxRenderDistance` / `MeshGuiMaxVertices` / `MeshWorldMaxVertices` /
  `MeshMaxModelVertices` 八条（此前只有 6 条）。逐条核对：`setDefaultValue` 与 TOML 默认值
  一致、范围取自 `defineInRange` 不擅自收窄、en/zh 28 个 `config.tacz.client.render.mesh_*`
  键齐平。这 8 项全部是**每帧/每次提交读值**（`shouldRenderPoly` / 预算判定 / 日志开关），
  所以局内改立即生效，不存在「需要重启」的假选项。
- **第三轮：「枪身盖住太阳/月亮那一块反而发亮」——只写了分析，代码没动**：维护者把现象澄清成
  「开光影后，高模枪几何自己挡在天体前面时，**挡着的那部分模型继承了天体的自发光亮度**；其它自发光
  物品和不属于 TML 的模型都没有」。这条与上面那条 `_illuminated` 天空光**无关**（那条是烘焙进顶点的
  光照值，这条是几何根本不在光影包的阴影图里）。静态推导与三条判别（A/B/C，全是局内即时生效的开关）
  写在 `MESH_LOADER.md` §5.9，上游侧记为 `REVIEW_UPSTREAM_TML_GPU_262_20260831.md` **A11**。
  机制候选：`MeshPolyInShadow` 默认 false ⇒ poly 部件在 Iris 阴影遍里一个都不提交 ⇒ 阴影图里只有
  立方体外壳 ⇒ 高模超出外壳的面按构造是「完全露天」⇒ `sunEmissive` 整份打上去。**没改成默认 true 的
  原因**：还缺维护者那三个开关的结果（若 A 有效就翻，若 B 有效要查的是自建 pass 与 Iris frame graph
  的时序，是另一件事）；同时已核实开这个键不会与常驻 VBO 叠加（`shouldSubmitGpuWorld` 在阴影遍拒收），
  所以代价只有一遍 collector 的 CPU 顶点变换，且**对无光影用户是彻底 no-op**（唯一消费点在
  `isRenderShadow()` 后面）。上一条「`MeshPolyIlluminatedRealSky`」解决的是另一个现象，不要当成本条的答案。
- **高模枪「遮不住太阳/月亮」（同日第二轮，配置 17 → 18 项）**：维护者反馈反光改好后剩下的问题是
  「光影下枪身会继承太阳/月亮的亮度，屋顶墙遮不住」。根因不在法线，而在**烘焙进去的光照值**：
  骨骼名以 `_illuminated` 结尾 = 自发光，本仓与上游 TACZ 都硬写成 `0xF000F0`（block=15 **且** sky=15；
  立方体层 `BedrockPart#render` 与 poly 层 `PolyMeshModel` 同一个数）。无光影下这是对的（原版光照图
  是两列**相乘**，sky=0 基本就是黑的，光靠 block 拉满不亮）；但光影包把 **sky 读成「这表面看得见天空」**，
  于是「常亮」= 「永远晒得到太阳月亮」，且该值沿子骨骼继承 ⇒ 顶层一个骨骼叫 `*_illuminated`，整把枪跟着露天。
  新增 `MeshPolyIlluminatedRealSky`（当时默认 true，**现已退回 false**：它针对的是我误读出来的症状，
  真正的原因见下面第四轮那条）：**仅在装了光影包时**把 sky 换成环境真值、block 保持 15
  —— 洞里照样看得见，但不再声称晒得到太阳；**无光影下逐字保持上游行为**（判据第一条件是 `isUsingRenderPack`，
  静态可证）。三条消费路径（collector / GPU 手部 / GPU 世界）统一走 `PolyRenderPolicy#illuminatedLight`；
  光影状态用 `ShaderStateTracker` 每帧推进的缓存布尔（`IrisCompat.isUsingRenderPack()` 每次要
  `Class.forName`+`getMethod`，不能放在每帧每骨的路径上）。开关值改了按 F3+T；烘焙世代本来就随光影状态翻转失效。
  **刻意没动的两半**（写清楚，别当成已全改）：立方体层的同一硬编码属于 TACZ 本体、影响所有枪包与所有准星点，
  合理归属是 `ClientConfig`，本轮没碰；EMISSIVE 兜底是「无条件全亮」与本条可区分（日志有 WARN）。
  若拨开关观感不变，亮度来源就不是我们烘的 sky 值而是**光影包对手部 pass 不做阴影测试**（判别法与依据见
  `MESH_LOADER.md` §5.8 末段）。**证据边界**：同样只到静态 + CI； sky=15 被包读成太阳暴露这件事
  依赖具体光影包的实现，需要维护者实机确认。
- **光影下的反光 / 法线（同日追查轮，配置由 14 项增至 17 项）**：维护者报「装光影后反射光源很怪」，
  查 `core/PolyMesh.java` 得到两条**静态可证**的缺陷：
  ① `poly_mesh` 的位置相对 pivot 在 Y 轴取反（`FLIP_MODEL_Y=true`），单轴镜像 = det<0 的合同变换
  ⇒ 每个面的正反面互换；而烘焙法线是「原始顺序叉积 × 翻转符号」= 镜像后的**朝外**法线 —— 方向没错，
  错在**绕序从没跟着反转**，于是法线说「朝外」、`gl_FrontFacing` 说「背面」。原版实体程序不读
  `va_normal` ⇒ 无光影下不可见（**所以第 0-3 轮的实机 PASS 不能当作法线已验证**）；Iris 包里
  `normal *= gl_FrontFacing ? 1.0 : -1.0` 的常规写法会把那条朝外法线取反 ⇒ 高光落在错误一侧。
  ② `FORCE_FLAT_SHADING` 恒 true ⇒ 枪包写的 `normals` 数组解析出来直接丢，曲面（枪管/护木）呈棱角状高光。
  修法落在**数据层**（GPU 与 collector 共用同一份 `bakedN*` 数组 ⇒ 两条路一起修好，没有去 shader 侧补偿）：
  镜像时反转发射绕序（与 `BedrockPolygon` 对 `mirror` 的处理同构）；平面法线仍从**未翻转的原始顺序**求
  叉积（跟着发射顺序走等于把 `D` 乘两遍）；退化面（叉积长度 ≤1e-6）不再写零向量，避免光影里
  `normalize()` 出 NaN；新增 `MeshPolyMirrorReverseWinding`(**true** = 修复本身) /
  `MeshPolyInvertNormals`(false) / `MeshPolyPreferPackNormals`(false) 三项供局内 A/B，值在 `PolyMesh`
  构造里读一次 ⇒ 按 `F3+T` 生效（`TaczMeshyIntegration` 的 CLIENT_RESOURCES reload 监听清
  `PolyMeshSupport.PARSE_CACHE`，已核实）。三项同时接进 Cloth + en/zh（17 ↔ 17 ↔ 34，齐平改用
  **`docs/check_mesh_config_parity.py`** 把关，替代上一轮那条正则写歪的临时脚本）。
  **证据边界（AGENTS.md §2）**：只到静态 + CI 编译；两条自研管线 `.withCull(false)` 已核实
  ⇒ 反转绕序不会让 GPU 路径丢面，但 collector 的 `RenderTypes.entityCutout` 在本 MC 版本的剔除状态
  沙箱内无法核实（无 Loom jar）。**光影下观感待维护者实机**，判定矩阵、判别实验（数据层 vs 消费层）
  与回退方法见 `MESH_LOADER.md` §5.7。上游 26.2 `587763c` 的 `PolyMesh` 与本仓逐字相同 ⇒ 同一缺陷
  （`REVIEW_UPSTREAM_TML_GPU_262_20260831.md` A10），26.1.2 合入 TML 时要一并带上。
- **失败降级去掉配置回写（行为变更）**：第 1 步的手部 catch 沿用了 26.2 的
  `MeshyConfig.GPU_BAKING.set(false)`，本轮删掉，只置内存标志 `gpuDisabledThisSession`。
  两个理由：绘制线程里改配置可能触发磁盘写；且用户重启后会看到「GPU 烘焙自己关了」
  而不知其因。世界表本来就是「分表 + 连续 30 次阈值 + 不回写」，现在两条路语义一致、
  互不连坐。（对照论证：`REVIEW_UPSTREAM_TML_GPU_262_20260831.md` A2。）
- **TEMP 剥离**：`build.gradle` 里两段 `TEMP …` 诊断任务（`dumpHandFlushApi` /
  `dumpWorldFlushProbe`，靠 `compileJava.finalizedBy` 搭 CI 便车输出 javap）改成
  **`scripts/mesh_render_probe.gradle` + `-PmeshProbe` 显式开**，默认零成本、发布无遗留；
  用法与「换 MC 版本只改哪三处」写在 [`TML_GPU_PROBE_TOOL_20260831.md`](TML_GPU_PROBE_TOOL_20260831.md)。
  这一步同时做掉了 26.2 侧指导文档 `SYNC_GUIDE_REFAB_1211_20260830.md` §0.1 对本分支的要求。
- **CI 对齐 26.2 的 v4（暂存件，未上线）**：`docs/ci/compile-check.yml` 是正式件的替换版，补
  `1.21.11` 主分支 push + `pull_request` 触发、`concurrency` 取消过期 run、**日志回推只在
  arena/\*\***。实测本沙箱凭据推不动 `.github/workflows/`（GitHub App 无 `workflows` 权限，
  远端直接 `remote rejected`），所以本分支正式件仍是旧版，等维护者粘贴；
  新建件 `docs/ci/build.yml`（全量 build + jar artifact + 四个静态校验）与 `docs/ci/consistency.yml` 放在
  `docs/ci/`（沙箱 token 无 `workflows` 权限，按 AGENTS.md §1 由维护者粘贴上线），
  清单见 [`docs/ci/README.md`](ci/README.md)。Java 用 21（26.x 是 25）。
- **顺手抓到并删掉一个孤儿 mixin 配置**：`tacz.compat.acceleratedrendering.mixins.json`
  在 1.21.11 上既没有对应的 `BedrockPartMixin` / `ARCompatMixinPlugin` 类，也不在任何
  `fabric.mod.json` 的 `mixins` 数组里（本分支 AR 兼容是 `ARCompat` 空壳：AR 只发到 1.21.1、
  其 API 面向旧的即时实体渲染器，这条理由写在该类注释里）⇒ 永不生效。26.2 侧同名配置**有**
  实现，所以这条只适用于本纪元。新增的那条「mixin 配置注册性」校验就是为了它。
- **文档**：`docs/README.md`（按 26.2 那轮的「现在 vs 当时」判据做索引；**存量文件不搬目录**，
  因为跨分支账本按根路径互相引用，搬动会让别的分支的引用变幽灵）；
  `docs/lineage/HANDOFF_LEDGER.md`（本分支副本，回填上游指向本分支的 4 行 + 新开 L-1…L-5）；
  `docs/lineage/SYNC_GUIDE_1211_TO_2612_TML_GPU_20260831.md`（给 26.1.2 的 TML 整包移植指导：
  文件清单、五条不可谈判不变量、26.1.2 必须先实测的 Q1-Q6、分步验收清单）；
  `docs/REVIEW_UPSTREAM_TML_GPU_262_20260831.md`（对 26.2 GPU 层的只读审查 A1-A9）。
  `MESH_LOADER.md` 里两处**已被第 3 步推翻的旧表述**同时改掉：
  「GPU 路径只接管第一人称手部语境」和「运行期异常也会自写 `false`」（后者是 26.2 的做法）。
- **核实无需搬的**：26.2 指导文档 §1.1 的「检视动画两连修」（`4aa8d7b` + `12d6f3c`）
  本分支**已有等价实现**（`AnimationStateContext#stopAnimation` 的出生序号判据、
  `ObjectAnimationRunner.SPAWN_COUNTER`、`AnimationStateMachine#trigger` 的栈式快照逐一核到）。

**验证状态（如实记录）**：`bash scripts/check_release_consistency.sh --strict` 本地通过
（6 ok / 0 fail / 1 warn：arena 分支名不是 MC 系列，属预期跳过）；`docs/ci/build.yml` 里那
四个静态校验在写入时对本源码树逐个跑过，全绿；en/zh 语言键与 Cloth 条目、TOML 默认值的齐平
用脚本核过（14 ↔ 14 ↔ 28）。**默认值翻转本身没有再跑实机**：翻的是两个已被实机 PASS 覆盖的
开关，回退路径未变，但「新玩家首开即为 GPU 路径」这一整体形态尚未实测。

---

## TML 第 3 步：世界语境常驻 VBO（`MeshGpuWorld`）——内容归入上面的 R3

把第 2 步 v2 的同一手法搬到世界那一次 flush：**常驻 VBO + 每帧只上传 O(骨骼) 个矩阵**，
覆盖他人手持 / 掉落物 / 展示框 / 展示台雕像。依据与逐条 javap 见
[`TML_GPU_STEP2_HANDFLUSH_20260831.md`](TML_GPU_STEP2_HANDFLUSH_20260831.md) §4。

- **消费点**：新增 `client.FeatureRenderDispatcherMixin` —— `@Inject(renderAllFeatures, RETURN)`
  （`require=0`）。1.21.11 该方法有三个调用点（实测：`LevelRenderer` 主通道节点
  `method_62214`、次级节点 `method_62213`、`ItemInHandRenderer#renderHandsWithItems`），
  本钩子按语境分流：手部那次仍由 `ItemInHandRendererMixin` 在 `endBatch()` 之后消费，
  阴影遍与 `outputColorTextureOverride != null`（离屏帧图节点）跳过且不清表。
- **MV 语义（这是隔壁 26.2 分支同类改动出「相对视角钉死」的原因）**：1.21.11 的
  `RenderType#draw` 是 `getModelViewStack()` + `34: getModelViewMatrix()` 现取，即
  collector 那批几何用的是**它自己 draw 当刻**的栈顶；所以 GPU pass 也必须在同一时刻现取，
  绝不能在别的时机把 MV 与 pose 烘在一起。`EntityRenderDispatcher#submit` 传入的 x/y/z
  已是相机相对（另有 `Vec3.negate()` 的 translate 包在 pushPose/popPose 内），
  「pose 带平移、MV 只带旋转」在世界语境同样成立。
- **烘焙时机不再局限**：每模型 **多光照档 LRU**（`MeshGpuLightCacheSize` 默认 4）+ 每帧烘焙额度
  + 延迟释放池（本帧 `WORLD_DRAWS` 可能已引用被逐出的 VBO，下一帧才 close）；
  烘焙日志只有前两次是 info，避免逐帧刷屏。
- **顶点预算只挡 collector**（`TaczPolyMeshGunModel#submit` 顺序调整）：GPU 路径没有 O(顶点)
  的 CPU 成本可保护，照旧先过预算就等于「16 格外高模枪整把消失」没解决。
- **提交侧闸门**：`PolyMeshGpuRenderer#shouldSubmitGpuWorld`（总闸 + `MeshGpuWorld` + 世界钩子
  存活证明 + 非手部 / 非 Screen 提取 / 非镜内 / 非阴影 + 光影需 `MeshGpuWorldUnderShaders`）
  与 `TaczPolyMeshGunModel#isWorldGpuContext`（`GUI`/`FIXED_GUI`* 一律拒收；`FIXED`/`HEAD`
  再按 `RenderDistance.isGuiRender()` 挡枪匠桌预览）。
- **镜内那一遍（PIP 二次渲染）**：画但不清表、不占本帧消费标志（提交每帧只登记一次）。
  `ScopePipRerender#isInsideScopeLevelRender()` 为此从私有标志改为公开读取。
- **消费语境圈定**：`PolyMeshGpuRenderer#setLevelRenderActive` —— 世界表只在正在跑
  `LevelRenderer#renderLevel` 期间消费（标志由既有的 `GameRendererMixin#tacz$scopeRenderLevel`
  `@Redirect` 用 try/finally 维护，不新增 mixin）。`renderAllFeatures()` 是公开 API，别的 mod
  自己调它时投影/目标都不是世界那套；圈定之后「未知调用点」最坏只是这一帧不画，
  而且这条检查排在记存活证明之前 ⇒ 注入点失效时不会误报「钩子还活着」。
- **光影下默认不走**：世界 GPU 要受光需要把 `tacz:pipeline/mesh_entity` 登记进 Iris 的实体
  program：`IrisProgram` 全量常量表已由 `dumpHandFlushApi` 打全 ⇒ 世界这条用 **`ENTITIES`**
  （**没有** `ENTITY`/`MAIN`；按候选名试探只会留一条 WARN 并让枪不受光。`EMISSIVE_ENTITIES`
  亦不可误用 —— 本仓的 EMISSIVE 管线只是不采光照图，不是恒最亮）。
  新加 `MeshGpuWorldUnderShaders`（默认 false，理由只剩「没跑过实机」）。
  （隔壁分支用现成 `RenderTypes.entityCutout` + `RenderType#prepare()` 天然落在 Iris 已接管的
  管线上；1.21.11 **没有** `prepare()`/`drawFromBuffer()`，两条路不等价，见可行性文档 §2.2。）
- `RenderDistance#isGuiRender` 由 private 改 public（只读时间戳判定，无行为变化）。
- 配置：`MeshGpuWorld`（true）、`MeshGpuWorldUnderShaders`（false）、`MeshGpuLightCacheSize`（4），
  全部接 Cloth Config + en_us / zh_cn 文案（parity 已核）。
- TEMP 脚手架：`dumpWorldFlushProbe`（带上下文的字节码探针，回答「谁 flush、谁拥有 MV 栈」）；
  `dumpHandFlushApi` 增加 `IrisProgram` **全量常量**输出（已据此把世界这条钉成 `ENTITIES`）。
  两个任务都在 try/catch 内、发布前删。

- **世界 GPU 被拒时留原因**：`PolyMeshGpuRenderer#worldSubmitBlocker` 逐条重判门闸给出第一条
  命中原因，`TaczPolyMeshGunModel#noteWorldSkip` 按原因去重打一条 INFO。静默回退 collector 仍是
  正确行为，但「光影下世界路径没生效」这类问题此前在 latest.log 里一个字都不留，无法定位。

**验证状态（如实记录）**：**编译 ✅（CI 多次 `BUILD SUCCESSFUL`，无 error、无本仓新增告警）+
静态审计 ✅（读的是 CI 上 1.21.11 / Iris 1.10.7 的真实 classpath）+ 实机 ✅（2026-08-31 维护者两轮：
无光影与含光影 `MeshGpuWorldUnderShaders=true` 均通过，最要紧那条「他人手持的 mesh 枪必须随相机
正确移动」（隔壁同题材改动踩到的坑）未复现）。**当时**那条「光影下世界路径失效」的回报经核实是
默认关（R3 起默认开）。清单其余条目（掉落物与展示框逐条、预算解耦、GUI/镜内不泄漏、显存曲线）
未逐项回报，仍按未验证对待。

---

## TML 第 2 步 v2：mesh GPU pass 开进手部 flush ——内容归入上面的 R3

> **2026-08-31 更新：本节描述的实机验收由维护者报告 PASS**（含光影下常驻 VBO 正常受光、
> 无光影回归不退化）。因此第 0/1/2 步不再是「仅编译通过」。`MeshGpuUnderShaders` 仍保持
> 默认关闭 —— 它是实验位，别的机器/别的 Iris 小版本上的表现不在本报告范围内。

**只做了一件事：把「常驻 VBO 在什么时刻画」这个决定改对，并据此把光影下的路径打通到可实机验证的程度。
全部证据与逐条 javap 见 [`TML_GPU_STEP2_HANDFLUSH_20260831.md`](TML_GPU_STEP2_HANDFLUSH_20260831.md)。**

- **审计推翻了上一轮留的 TODO 前提**：`FeatureRenderDispatcher#renderAllFeatures`（1.21.11）
  里根本没有 `RenderPass` 局部变量，也没有 `renderSolidFeatures` / `renderTranslucentFeatures`；
  pass 是每个批次在 `RenderType#draw(MeshData)` 内部创建即关闭的。所以「mixin renderAllFeatures
  + `@Local` 捕获 flush pass」这条路**不成立**，已作废并记档（`TML_GPU_FEASIBILITY` §6.3）。
- **改道**：`RenderType#draw` 的输出目标是
  `RenderSystem.outputColorTextureOverride` / `outputDepthTextureOverride` 优先，且 Iris 1.10.7
  用 `MixinGlCommandEncoder` 代管 pass 创建期的 framebuffer 绑定 —— 于是**自己按同款规则开
  pass** 就能在世界渲染阶段内落进 gbuffer，不需要借别人的 pass。
- **绘制点搬迁**：`PolyMeshGpuRenderer.renderAfterSolid()`（`GameRenderer#renderItemInHand`
  RETURN）→ `renderAtHandFlush()`（`ItemInHandRenderer#renderHandsWithItems` 的 RETURN，
  `require=0`）。1.21.11 的手部几何本来就在这个方法末尾 `renderAllFeatures()` + `endBatch()`
  flush（Iris 亦是从同一方法进来、用 `HandRenderer#endRender` 替换那两个调用），一个注入点
  同时覆盖有/无光影两条路。**不再 mixin Iris 内部类，也不再 patch `RenderType#draw`。**
- **顺带修掉一个由构造决定的错**：第 1 步为绕开「RETURN 时 ModelView 已被还原」而在 submit
  时刻偷拍 `Bᵀ` 的做法删掉了 —— 现在 ModelView / Projection / 目标覆写取的都是刚被原版手部
  批次用过的那一份，GPU 与 collector 逐帧等价不再依赖时点巧合。
- **顶点格式跟随**：Iris 的 `MixinRenderPipeline` 会在光影激活时把 `NEW_ENTITY` 换成
  `IrisVertexFormats.ENTITY`（stride 不同）。烘焙改为按 `LIT_PIPELINE.getVertexFormat()`
  当刻返回值写，格式记进 `BakedBone`，`ensureBaked` 比对 + 绘制端二次校验，不一致跳过当帧
  并 bump 世代号（避免「拉伸的枪模」按错 stride 解读 buffer）。
- **光影路径**：`MeshGpuUnderShaders` 从「恒 no-op 的诊断位」改成实际生效的实验开关
  （**默认仍为 false**）：需 Iris 1.10.x（`IrisCompat.supportsHandFlushHook()`）+
  `IrisApi.assignPipeline(pipeline, IrisProgram.HAND)`，并有**钩子存活证明**兜底 ——
  上一帧没真正跑到 flush 钩子就不允许跳过 collector，杜绝第 2 步 PoC 的「枪整体消失」。
- 文档：`MESH_LOADER.md` 配置表与 §5.3/新增 §5.4 实机清单、`TML_GPU_FEASIBILITY` §6.1 更正
  + 新 §6.3、`PolyMeshGpuRenderer` / `MeshyConfig` / en_us / zh_cn 文案同步。
- 诊断脚手架：`build.gradle` 的 TEMP task 由 `dumpFeatureDispatcherApi` 换成 `dumpHandFlushApi`
  （javap 逐项核对 `renderHandsWithItems` flush 结构、`RenderPass`/`ScissorState`/`RenderTarget`
  成员、`BufferBuilder` 对未知分量的默认填充、Iris 侧 `HandRenderer#endRender` 等；发布前删）。

**验证状态（如实记录）**：**编译 ✅（CI 两次 `BUILD SUCCESSFUL`，无 error、无新告警）+ 静态审计 ✅；实机未做。**
沙箱无 JDK / 无 loom 缓存，编译与 javap 审计走 `compile-check.yml` 回推的 `build-reports/compile-java.log`，
逐条结论见 `TML_GPU_STEP2_HANDFLUSH_20260831.md` §3。最关键的一条是本轮全部前提的正证：
1.21.11 的 `ItemInHandRenderer#renderHandsWithItems` 共 143 行、**只有 1 个 `return`**，尾部指令
正是 `getFeatureRenderDispatcher().renderAllFeatures()` + `renderBuffers().bufferSource().endBatch()`
—— 所以 `@At("RETURN")` 必然命中且必在那次 flush 之后，也不存在「提前 return 绕过钩子」。
`RenderPass` / `ScissorState` / `RenderTarget` / `RenderSystem` / `BufferBuilder` 与 Iris 的
`HandRenderer#endRender`、`IrisApi#assignPipeline`、`IrisVertexFormats.ENTITY`、`ShaderKey.HAND_CUTOUT`、
`ImmediateState.safeToMultiply` 逐项存在且签名与调用一致。
顺带记下一个包名变化：`RenderType` 在 1.21.11 位于 `net.minecraft.client.renderer.rendertype`
（脚手架第一版按旧包名查所以 `class not found`，已修）。
光影下 GPU 是否真收 `gbuffers_hand` 照明、无光影回归是否退化，仍须按 `MESH_LOADER.md` §5.3 / §5.4 实机确认。

---

## 2026-08-27 轮（叠在 R2-hotfix2 之上）——内容归入上面的 R3

**跟进：26.2 排查的长按幽灵使用 / 耳鸣资源。任务 C（消声注入点）按该线实测有效，未改。**

来源：`docs/handoff/HANDOFF_TO_1_21_11.md` + `HANDOFF_COMMON_2026_08_27.md`（`26.2(main)`）。
本分支对照当前树自行核对后再改，没有整文件照搬 26.2 的 `DeafenState`（那边删了 `isRingingSound`，本分支 `SoundEngineMixin` 还在用）。

- **A1** 新增 `UsePressGate` + `MinecraftUseRestartMixin`（注入具名目标 `startUseItem` HEAD）。
  方法名由本分支已有的 `MinecraftMixin` / `InteractKey` 交叉确认，不是照搬 26.2 字节码偏移。
- **A2** `ThrowableItem#use` / `ConsumableItem#use` 两端都查各自冷却表；新增 `StuckUseRecovery`
  （cookable 且 `life_time > 0` 时，越过 prepare+life+20tick 则本地 `stopUsingItem()`，不走 `releaseUsingItem()`）。
- **B1** `assets/lrtactical/sounds.json` 本来就没有顶层 `_comment`，未改。
- **B2** 从 `origin/26.2(main)` 取 `stun_ringing.ogg`（28566 B）与自绘 `deafened.png` / `blinded.png`，
  以及 `scripts/verify_lr_assets.py` / `scripts/gen_effect_icons.py`。
- **B3** `DeafenState#tick` 接住 `SoundManager#play` 的返回值。1.21.11 确有返回值：
  yarn `1.21.11+build.4` 为 `SoundSystem.PlayResult`（`STARTED` / `STARTED_SILENTLY` / `NOT_STARTED`），
  官方映射为 `SoundEngine.PlayResult`。非 `STARTED` 时 WARN 一次。
- **C** `SoundEngineMixin`（注入 `calculateVolume(SoundInstance)`）**未改**。用户实测本线消声有效。

**验证状态（如实记录）**：`python3 scripts/verify_lr_assets.py --strict` 已通过。
沙箱无 JDK、无 loom 缓存的 1.21.11 jar，故 `docs/verify_mixin_targets.py` 与 `./gradlew build` 未跑，
也**没有实机验证**。发版前仍需 remap 构建、跑两个 verify 脚本，并按 handoff 共用核心 §6 做实机清单
（本线尤其要确认耳鸣声与消声没有退化）。

---

## 1.1.8+fabric.1.21.11.R2-hotfix2

**回流：26.2 分支的 LR 0.4.3 战术同步（源提交 `630ef87`）**

全部改动落在 LRTactical 层，不碰瞄准镜架构；逐项取舍与适配论证见
[`SYNC_LR_043_1_21_11_2026_08_26.md`](SYNC_LR_043_1_21_11_2026_08_26.md)。

- 投掷物引信：预燃（cook）拉满后扔出不再「永不自爆」——实体超时判定改为
  `life >= 0`，手上引爆门槛与 HUD 红条改为完整 `lifeTime`（官方 0.4.3，无 10% 余量）；
  C4 / 遥控起爆（`life_time = -1`）的两道防线保留，遥控起爆语义不变。
- 投掷物不再吃到近战的 `INPUT_IDLE`：静止拔销时 `unlock_safe` 不再被每 tick 掐断抖动
  （官方手雷脚本用字面量 `"idle"` 表示取消拔销）。
- 烟雾粒子由全亮改为环境光采样（天光/块光 + 邻格扫描 + 保底 2），夜里/洞内不再自发光；
  覆写方法保留本分支的 `getLightColor` 名字（26.2 已改名 `getLightCoords`）。
- 内容包新增 `display_offset` 与 `entity_transform` 支持（`DisplayTransform`），
  `GSON` 注册 `Vector3f` 适配器；无内容包时飞行姿态与手持渲染不变。
- 消耗品在内容包提供 display 时走 Bedrock 模型 + Lua 动画
  （`ConsumableItemRenderer` 及配套 display/manager），并接入 `IItem` 扩展点。
- 补齐此前缺失的 `assets/lrtactical/items/*.json` 与 `models/item/*.json`
  （移植遗漏，非版本契约差异）：近战/投掷物/起爆器/消耗品自此才有物品模型定义，
  无内容包时渲染原版占位图标，占位纹理全部复用原版材质。

明确不含：Iris 高倍目镜裁剪、ADS bob 缩放（姊妹仓回退项）、`third_person_animation`
（刻意降级，Gson 静默忽略该字段）。

**验证状态（如实记录）**：本轮回流在编写环境中未编译、未实机验证（沙箱无 JDK、
无 Maven/Mojang 网络）；已做逐文件与源提交比对及静态符号核对，
且本批未新增/修改任何 mixin、未触碰任何 shader。发版前仍需
`./gradlew build`（含 remap 阶段）、`docs/verify_*.py` 与上文档中的实机复测清单。

---

## 1.1.8+fabric.1.21.11.R2-hotfix

**修复：开启「显示爆头范围」后再打开碰撞箱会崩溃**

1.21.11 已删除 `SubmitNodeCollector#submitHitbox` / `ShapeRenderer#renderLineBox`，
调试盒改走 `Gizmos`。R2 仍通过实体 `submitCustomGeometry(RenderTypes.lines(), …)`
画爆头判定盒，与 F3+B 同时开启时会把 LINES 几何送进 custom-geometry 管线并崩溃。

现改为在原版 `EntityHitboxDebugRenderer#showHitboxes` 的 per-frame GizmoCollector
里发射黄色 `Gizmos.cuboid`。配置项仍只在碰撞箱显示开启时生效；版本号把
`hotfix` 放在 `+` 构建元数据里，SemVer 核心仍是 `1.1.8`，枪包 `>=1.1.8` 谓词不受影响。

---

## 1.1.8+fabric.1.21.11.R2

**新增：内置弹药适配枪械查询（JEI / REI）**

- 新增与 “TaCZ Ammo Query” 同类的内置查询分类，无需再安装单独附属模组；
- 在 JEI 与 REI 中选择任意 TACZ 弹药，可查看当前已加载枪包中所有使用该弹药的枪械；
- 两个查看器共用同一份查询数据，支持服务端同步的第三方枪包，并复用现有枪包同步后自动刷新流程；
- 大型枪包超过单页显示上限时，末格会轮换显示其余枪械。

**修复：Carry On 2.9.2 搬运 TACZ 工作台**

- 双格工作台改在 block-state 放置路径补全 companion，不再依赖 Carry On 不会调用的
  `setPlacedBy`；放置前会原子检查两格空间，失败时保留搬运数据；
- 配件工作台保留 `half=lower/upper` 序列化格式，但不再使用会被 Carry On 通用规则拒绝的
  `DoubleBlockHalf` value class；该冲突也存在于官方 TACZ 1.20.1 设计，并非端口遗漏；
- 从同步的 Carry On 方块实体 NBT 恢复枪包工作台 `BlockId`，修复手持紫黑模型；
- 从任一半格发起搬运都会解析到 root，显式覆盖 `pickupAllBlocks=true`，不会打开菜单或生成
  非 root 幽灵方块；
- 工作台从 Carry On 黑名单移除，`target` / `statue` 继续保留。

实现依据、兼容边界和游戏内回归矩阵见 [`CARRYON_COMPAT.md`](CARRYON_COMPAT.md)。

**下游兼容 API：可替换实体弹药源（Issue #46）**

新增公共 `com.tacz.guns.api.item.ammo` API。下游模组可向
`AmmoSourceRegistry.EVENT` 注册 provider，由自有库存实现无副作用的弹药查询与有界消耗，
不再需要 mixin TaCZ 的高层弹药方法。provider 按注册顺序匹配，首个非 `null` 结果生效；
未匹配时继续使用原有实体 `IItemHandler`，原版 `IAmmo` / `IAmmoBox` 行为不变。

以下旧兼容目标现在统一经过 registry：

- `AbstractGunItem#canReload` / `hasInventoryAmmo`；
- `LivingEntityShoot#consumeAmmoFromPlayer`；
- `ModernKineticGunScriptAPI#consumeAmmoFromPlayer` / `hasAmmoToConsume`；
- `GunAnimationStateContext#hasAmmoToConsume`。

动画上下文另新增稳定命名的 protected 方法 `hasAmmoToConsumeInEntity(Entity)`，避免依赖
javac 合成的 `lambda$hasAmmoToConsume$...` 名称。假弹、创造模式和无限弹药的现有优先级
保持不变。接入方式、双端注册要求和契约详见 [`AMMO_SOURCE_API.md`](AMMO_SOURCE_API.md)。

**稳定命名的行为扩展钩子**

将开火、换弹、拉栓和动画路径中原本只能通过 private 方法或合成 lambda 定位的业务阶段，
提取为 protected 具名方法。主要入口包括：

- `LocalPlayerShoot` 的校验、冷却、连发周期、主线程表现及状态锁判定；
- `LocalPlayerReload` / `LocalPlayerBolt` 的事务、动画和手动拉栓判定；
- `LivingEntityShoot` / `LivingEntityReload` / `LivingEntityBolt` 的服务端事务与蓄力校验；
- `ModernKineticGunScriptAPI` 的射击周期、继续条件、弹丸生成和过热脚本分派；
- `ModernKineticGunItem` 的默认过热、拉栓和换弹 fallback；
- `GunAnimationStateContext#getInterpolatedWalkDistance`。

P2-min 另将 `ModernKineticGunItem` / `ModernKineticGunScriptAPI` 的 Lua 函数解析与 cycle task
转发提取为 protected 具名 helper，并为默认 reload/heat fallback、弹药动作策略和服务端蓄力
校验补齐覆写契约；没有扩大已拒绝候选，也没有改变脚本或 gameplay 行为。

原调用路径、判断和副作用顺序保持不变；射击状态锁仍使用同一个 Predicate 实例进行身份判断。
这些具名方法用于避免下游绑定 javac 合成名称，不代表合成 `lambda$...` 方法属于兼容 API。

---

## R12

**审计 + 恢复：LRTactical 爆炸屏幕震动**

对仓库 `26.1.2`、`26.2(main)` 与原始 LRTactical NeoForge 1.21.1 的显式 no-op/TODO
逐项对照后，确认绝大多数空实现是 ABI 门面、旧 collector API、上游未设计功能或资源授权限制。
新增 `docs/EXPLICIT_GAPS_AUDIT_R12.md` 记录每项证据与处理边界。

发现并恢复了一个真实功能缺口：爆炸数据里的 `screen_shake_time` /
`screen_shake_amplitude` 以前从未生效。R12 新增范围限定的 S2C payload、客户端 tick
衰减状态和 `ViewportEvent.CAMERA` 视觉层；震动在枪械后坐之后叠加，不修改玩家真实朝向，
不影响服务端爆炸/伤害权威性。

仍未粗暴实现 `destroy_multiplier`：原版 `Level#explode` 会同时影响伤害、击退与方块，
不能等价替代上游只放大方块射线能量的 `CustomExplosion`。

---

## R11

**修复：Complementary 在 R9 late hand 之后仍雾化准星/目镜黑边**

R10 实机截图确认 R9 的前景深度写入仍不足：Complementary 的剩余雾来自
`HAND_TRANSLUCENT` 之后的 screen-space composite，读取不可被该 hand draw 改写的深度输入。
因此不再继续调整 hand pipeline 的深度状态。

R11 保留 solid pass 中原有 aperture、body、depth cleanup 与 3D 快照；仅对已核实的
Iris 1.10.7，把 reticle/rim 的最终颜色提交移动到
`IrisRenderingPipeline#finalizeLevelRendering()` 的 TAIL：

- Iris 所有 composite/final pass 已完成，core RenderPipeline 不再被 shader pack 替换；
- 复用固化的 hand projection、model-view、原始 Bedrock snapshot、ADS、后坐、晃动和 aperture mask；
- 使用新的无雾 `scope_reticle_final.fsh` / `scope_ring_final.fsh`，不是 HUD 或第二次世界渲染；
- 为 final reticle 在 solid hand 阶段额外保存私有 world-depth copy，保证最终 aperture mask
  不依赖 Iris 已解绑的 `depthtex2`；
- 其他 Iris 版本仍回退 R8/R9 的 `HAND_TRANSLUCENT` 路径，避免未审计内部时序导致准星消失。

R11 实机开镜日志应包含：

```text
[TACZ Scope] Queued reticle for Iris post-composite overlay.
[TACZ Scope] Final overlay masked by private world/aperture depth copies.
[TACZ Scope] Rendered reticle and ocular rim after Iris final composite.
```

---

## R10

**修复：1.21.11 数据包、实体 tag、默认枪包复合音效与工作台 recipe-book 噪声**

- `ammo_box_dyed.json` 不再使用 26.1 才新增的 `minecraft:crafting_dye`；1.21.11 改回
  `minecraft:crafting_special_armordye`。`tacz:ammo_box` 已在 `#minecraft:dyeable`，故继续使用
  原版的动态颜色混合与 `minecraft:dyed_color`，不会丢失弹药盒已有组件。
- 交互白名单的裸 `minecraft:boat` / `minecraft:chest_boat` 已改为 `#minecraft:boat`。1.21.2
  之后船实体类型拆分，原版 entity-type tag 负责覆盖普通船、箱船、木筏及未来变体。
- 默认枪包的 `reload_*` / `inspect*` 聚合 sound id 并不对应单个 OGG；实际音频是动画 keyframe
  中的多个 `*_raise`、`*_magin`、`*_end` 等片段。播放代码现在将聚合 id 视作可选容器：有单一
  OGG 时照常播放；没有时静默交给 `ObjectAnimationSoundChannel` 播放真实片段，不再误报
  `Missing gun sound resource`，也不伪造/复制音频文件。
- `GunSmithTableRecipe#isSpecial()` 现在返回 true。枪械工作台材料带数量且不属于原版 3×3 配方，
  因此不再被 RecipeManager 强制转换为 recipe-book placement；这消除了大量
  `can't be placed due to empty ingredients` 警告，且不影响 TACZ 工作台自己的材料检查和合成。

---

## R9

**修复尝试：Complementary Reimagined 的后处理雾仍将晚期准星当作远景**

R8 已把 reticle/rim 提交延后到 Iris `HAND_TRANSLUCENT`，排除了水面、水体与粒子
world pass 的覆盖；实机截图确认剩余现象只随雾效出现。原因是 shader pack 的后续
screen-space fog/composite 仍读取当前深度：R8 late reticle 保持 `depthWrite=false`，因此
这些像素仍携带 cleanup 恢复的远处世界深度，被后处理雾当作远景颜色处理。

R9 新增仅供 Iris late hand pass 使用的 reticle pipeline：

- 普通/vanilla reticle 仍是 `GL_ALWAYS + depthWrite=false`，绝不破坏世界透明绘制；
- late etched / illuminated reticle 改为 `GL_ALWAYS + depthWrite=true`；此时水、粒子、
  天气等 world pass 已全部结束，写入近处深度只会让后续 fog、DOF 与 composite 正确识别前景；
- late `ocular_ring` 同样改为 `GL_ALWAYS + depthWrite=true`，确保其在准星之后盖住边缘像素，
  并作为前景参与 post-process 深度判定；
- `_depthMask(true)` 仍未在 mixin 中硬编码；写入掩码只由这三条 late RenderPipeline 自身声明。

BSL/Complementary 实测时应出现：

```text
[TACZ Scope] Queued reticle for Iris HAND_TRANSLUCENT.
[TACZ Scope] Deferred reticle and ocular rim to Iris HAND_TRANSLUCENT with late foreground depth.
```

并新增 Iris pipeline 分配日志：`scope_late_etched_reticle` 或
`scope_late_visible_reticle`、以及 `scope_late_ocular_ring` 均应归入
`HAND_TRANSLUCENT`。

---

## R8

**运行期修复：`GlRenderPipeline.info()` 不能经由反射调用**

`GlCommandEncoderScopeDepthCopyMixin` 原先把 `GlRenderPipeline` 收为 `Object`，再用
`getMethod("info")` 取 record accessor。1.21.11 是混淆运行时：源码里的 `info()`
在运行时是 `comp_3801()`；反射字符串不会经过 Loom remap，因此这条路径会抛
`NoSuchMethodException`，并使 reticle 的 `GL_ALWAYS` 覆写被静默停用。

现改为直接接收 `GlRenderPipeline` 并调用 `glRenderPipeline.info()`，使 Loom 将正常的
方法调用重映射到运行时名称。同时 `needsForcedAlwaysDepth` 改为接收
`RenderPipeline`，移除反射缓存、失败日志和 `Object` 类型逃逸。

两条 reticle 管线仍保持 `withDepthWrite(false)`；mixin 只执行
`_enableDepthTest()` 与 `_depthFunc(GL_ALWAYS)`，绝不恢复 `_depthMask(true)`。

**R7 结论修正：深度状态不是“雾/水覆盖准星”的完整解释**

`depthWrite=false` 已保证准星不破坏 depth-cleanup 恢复的世界深度。反射失败时实际为
`NO_DEPTH_TEST + depthWrite=false`，修复后为 `GL_ALWAYS + depthWrite=false`；两者对
“只写颜色、不写深度”的结果很接近。因此本次反射问题是确定的运行期 Bug，但不能把它
单独当作 BSL 水面、雾或 composite 覆盖准星的完整根因。

先前蚀刻准星走 `HAND_CUTOUT`，发光准星走 `HAND_TRANSLUCENT`；它们仍属于
shader pack 的 hand geometry，而不是最终 HUD。实机 BSL 回归确认：反射修复后水雾仍会覆盖
准星，因此按既定第二步实施局部时序重构。

**修复：将准星与 ocular rim 延后到 Iris `HAND_TRANSLUCENT`**

Iris 1.10.7 的 `HandRenderer#renderTranslucent` 会在“没有原版半透明手持方块”时直接返回；
TACZ 枪不是 `BlockItem`，所以不能只改 pipeline 分类。现在 solid hand pass 仅冻结不可变的
reticle/rim `BedrockRenderSnapshot`：完整保留原 3D 模型、ADS、后坐、晃动和 ocular mask。
新的 Iris-only mixin 在存在待提交准星时强制运行一次 late hand pass，并在 Iris 设置
`HAND_TRANSLUCENT` phase 后、其自身 `endBatch()` 前将快照交给原本的 hand collector。

- 蚀刻与发光准星都映射为 `HAND_TRANSLUCENT`（Iris `HandWater`）；
- 物理 `ocular_ring` 使用独立的 late cutout pipeline，按更高 order 在准星后绘制，继续覆盖边缘溢出；
- cleanup、world-depth backup 和 aperture mask 仍留在 `HAND_SOLID`，不会重绘世界或转成 HUD/PIP；
- 同时修正 Iris 1.10.7 的 `isHandTranslucent(InteractionHand)` 反射签名，避免 late pass 中完整枪体被
  fail-open 地重复提交。

启用 BSL 且实际开镜后，日志应依次出现：

```text
[TACZ Scope] Queued reticle for Iris HAND_TRANSLUCENT.
[TACZ Scope] Deferred reticle and ocular rim to Iris HAND_TRANSLUCENT.
```

**实机回归清单**：无光影、BSL、水下、隔水看目标、浓雾、雨天；日志中不应再出现
`Cannot read GlRenderPipeline.info()` 或 `reticle depth override disabled`。

**下一轮待核对（仅记录，尚未改动）**

- `data/tacz/recipe/ammo_box_dyed.json` 使用不存在的 `minecraft:crafting_dye` serializer，
  当前染色弹药盒配方不会加载；
- 交互白名单中的 `minecraft:boat` 与 `minecraft:chest_boat` 在 1.21.11 不是可直接引用的
  通用实体类型，应改为正确 tag 或具体船实体；
- 默认枪包缺少 `tacz:aug/aug_reload_empty`、`tacz:aug/aug_reload_tactical` 等音效，需要核对
  bundle、`sounds.json` 和实际资源路径；
- 大量 recipe placement 的“empty ingredients / ignored”警告需用枪械工作台实际合成验证，
  再判断是 `PlacementInfo` 的正常回退还是配方数据/实现问题。

---

## R7

**调整：准星不再写深度，保护 depth-cleanup 恢复的世界深度**

准星管线曾带 `depthWrite=true`，会以手部近深度覆盖 depth-cleanup 刚恢复的世界深度。
因此：

- 两条 reticle 管线改为 `withDepthWrite(false)`；
- encoder mixin 删除 `_depthMask(true)`，只保留 `_enableDepthTest` + `_depthFunc(GL_ALWAYS)`。

语义变为「恒通过深度测试 + 不写深度」。这确保准星不会破坏恢复后的深度，但当时将它
直接归因为“已修复光影雾/水覆盖准星”是过度结论；完整修正见本文件顶部的 R8 / R9。

**文档**：README 从 26.1.2 全面更新到 1.21.11（版本、Java 21、依赖、混淆说明、
移植章节）；补充 `.gitignore`；删除误提交的 `latest.log`。

---

## R6

**修复 1：Iris 下镜内不渲染水体/水面/粒子/云/雾（阶段 9）**

`IrisDepthRestoreShaderMixin` 此前被整包排除，因为 Iris 只在 `-PwithIris` 的
`modRuntimeOnly` 里、编译期不可见。改为 `modCompileOnly`（不进成品、不进普通运行
classpath），恢复该 mixin。

对 Iris 1.10.7 逐条核实注入点：`ShaderCreator.link` 存在；其内 `createShader`
第 5 次调用（ordinal=4）为 FRAGMENT；`create`/`createFallback`/`createFallbackShadow`/
`createShadow` 四条路径全部汇聚到该 `link`；传入的 name 是
`ShaderKey.getName()` = `toString().toLowerCase(ROOT)`。

运行期双重兜底：`required=false` + `IrisCompatMixinPlugin` 的 `isModLoaded("iris")`。
已确认成品 jar 中 `net/irisshaders` 类数量为 0。

**修复 2：准星溢出目镜到镜框**

不是掩码 epsilon 的问题。准星的镜内判据读 `APERTURE_TARGET`，该快照拍摄于
order −2，那时物理镜框（order 1）尚未绘制，掩码里没有镜框信息。
上游 1.21.1 本就是「先准星、后镜框」，移植时把两个 order 写反了。
改回 `reticle=1, ocular_ring=2`，epsilon 未动。

---

## R5

**修复：不开光影时镜内准星完全不显示**

1.21.11 的 `DepthTestFunction` 没有 ALWAYS，移植时全部退用 `GREATER_DEPTH_TEST`。
对 depth-cleanup 正确，但对准星致命：cleanup 把目镜区域写成世界远深度，
准星在手部近深度，`GL_GREATER` 要求 new > old，准星像素被全部丢弃。
（开光影时 Iris 用自己的 HAND 程序，绕开了这条管线状态，所以反而正常 ——
「有无光影表现相反」正是定位到这里的线索。）

改为 `NO_DEPTH_TEST` 声明 + encoder mixin 强制 `GL_ALWAYS`。
单用 `NO_DEPTH_TEST` 不行：`glDisable(GL_DEPTH_TEST)` 会连深度写入一起丢弃。

---

## R4

**修复：点开世界选择界面崩溃**

`ReloadableResourcesMixin` 的 `@ModifyArg` 目标写的是 `lambda$loadResources$2` ——
这是**非混淆**版本（26.x）javac 的合成名。1.21.11 是混淆版本，refmap 里没有
`lambda$` 条目，名字原样传给 mixin 必然找不到目标，在 APPLY 阶段崩溃。
改用 intermediary 名 `method_58296` + 完整描述符。

同时修掉 `verify_mixin_targets.py` 的盲区：它此前**主动跳过** `lambda$`/`method_`
前缀的目标（正是这行 `continue` 放过了导致崩溃的目标）。现在 `lambda$` 直接判错，
`method_NNNNN` 走正常校验。校验项 101 → 102。

---

## R3

**修复：启动黑屏（非崩溃）**

`scope_body.vsh` 是从 26.2 逐字节抄来的 `entity.vsh`，`#moj_import`
了 `<minecraft:sample_lightmap.glsl>` —— 这个 include 是 26.x 才有的，
1.21.11 只有 8 个 include，没有它。`ShaderManager` 解析悬空 import 时抛 NPE，
整个资源重载失败，客户端停在黑屏。改用 1.21.11 原版写法
`texelFetch(Sampler2, UV2 / 16, 0)`。

顺带修 `assets/lrtactical/sounds.json`：字符串形式的 `_comment` 键让整个音效索引
失效（`sounds.json` 要求每个顶层条目都是 JsonObject）。

`processResources` 改为显式枚举 `src/main/resources` 的覆盖项，
不再依赖 `DuplicatesStrategy` 的隐式顺序。

新增 `docs/verify_shader_imports.py`。

---

## R1 / R2

Phase 1（构建文件）+ Phase 2（编译错误族）。编译错误 146 → 0。
逐次修复的启动崩溃：`Minecraft#pickBlockOrEntity` → `pickBlock`；
`Camera#update` → `setup`；`calculateFov`/`calculateHudFov` 合并为
`GameRenderer#getFov` 的 `@ModifyReturnValue`；
`renderItemInHand`/`bobHurt`/`bobView` 的处理函数参数列表重签。

详见 `PORT_1_21_11_PHASE1.md` 与 `PORT_1_21_11_PHASE2.md`。
