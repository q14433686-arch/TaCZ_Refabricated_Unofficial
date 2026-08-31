# 给 Fabric 侧 26.1.2 线的同步清单（镜内文字裁剪 PASS 之后 + 我方 31 个真实提交的剩余确认项）

> 收件人提醒：本文的"你们"= 同仓库的 `arena/01a05170…`（**Fabric** 26.1.2）。姊妹**项目**
> （`q14433686-arch/TaCZ_Renovated`，NeoForge）的 1.21.11 线另见
> `docs/lineage/SYNC_CHECKLIST_1211_NEOFORGE_SISTER_20260901.md`，两份不要互相当作对方的更新。

日期 2026-09-01。方向：`arena/01a05759…`（1.21.11，本文所在树）→ `arena/01a05170…`（26.1.2，tip `7562abcb`）。
读法：§1-§2 是**可以直接用**的；§3-§5 是**双向要核对的**；§6-§8 是状态与纪律。
证据口径按 AGENTS §2 标在每格：`javap`＝CI 编译类路径字节码实测、`实机 PASS`＝维护者 2026-09-01 报告、
`静态`＝读代码/读文档得出、`未验`＝没有任何运行期证据。

---

## 1. 我方已实机 PASS、你们可以直接采信的结论

| 项 | 我方状态 | 对你们的意义 | 证据 |
|---|---|---|---|
| 镜内 `text_show` 文字按目镜孔径掩码裁剪（我方 `d076cf5`，等价你们 `e1c550ee`） | 无光影 A 格：文字出现、层序 画面→文字→准星→镜框、**贴边字形被圆孔裁掉不溢出**；F5 重载资源包后仍正常 | 你们那条"实现正确性"的怀疑可以卸掉一半：**同一语义在另一个世代、另一套 API 上独立复现通过** | 实机 PASS |
| 两条判据日志（`Flushed N in-lens text task(s) … (scopeMask=…)` / `Rendered deferred … K texts`） | 我方一直在用，本轮又加了第三条（见 §2 第 1 条） | 你们的验收矩阵若只有截图判据，建议同样收口到日志判据 | 实机 PASS |
| 你们 `0bf4c482` 的第 2 步（重提取）在 1.21.11 **不需要** | 我方按 javap 判"不加"，并已把我方类注释里那句"防护留给后续阶段"结案 | 别把这条当"两分支应对齐"的候选搬过来；两世代相位结构不同 | javap |
| 你们 `58831e4f`（FCAP `save()` no-op ⇒ 配置每次重启被重置）在 1.21.11 **不适用** | 我方同样**没有**任何 `save()` 调用（Cloth 侧只有 `setSaveConsumer(ConfigValue::set)`），但配置实测不会重置：依据是你们文档 §0 的"三分支配置装配逐字相同 + 1.21.11/26.2 无此病"与维护者在我方这一代的实机观察 | 请把它当**世代边界**而非通用缺陷；我方不跟改，也请在你们文档里保留这条世代边界，防止后人把 `ConfigPersist` + 两个 accessor mixin 当"对齐项"搬进 1.21.11 | 静态（读双方 `TaCZFabric`/`*ClothConfig` 与 FCAP `21.11.1` vs `26.1.5`）—— ⚠ 我方**未**对 FCAP 21.11.1 做 javap 核实；要字节码级结论我方可补一轮探针，但那只影响"要不要预防性加 save"，不影响当前行为 |

## 2. 建议你们补的三件小事（都来自我方本轮的实现经验，成本极小）

1. **加一条 log-once**：我方在 `ScopeTextSubmitter` 提交成功时打
   `[TACZ Scope] In-scope text is now clipped to the ocular aperture mask (N font page group(s)).`。
   没有它，"走了掩码"与"`isMaskCycleValid()` 为假 ⇒ 回退 vanilla"在屏幕上长得一模一样，你们的验收矩阵
   第 2/3 格无法区分这两种状态。
2. **把"字体页缓存遇到资源重载"写成剧本**：我方与你们都是 `Map<GpuTextureView, Identifier>` +
   `Map<Identifier, RenderType>` 的按 id 缓存（页 view 换了、缓存仍指旧壳）。我方新加了专门一格（镜内文字篇
   §4 剧本 F）并已实机 PASS；建议你们也补一格，别让它停在"双方都没测"。
3. **反向失败模式要写进"若不符"格**：我们的 fsh 在 `tacz_ScopeFinalOverlay == 0` 时**丢弃全部像素**
   （故意的 fail-closed）。⇒ 一旦某天文字整个消失，第一嫌疑人是这条丢弃路径（查这次绘制是否真用了
   `maskedText` 且被 `Operation.MASK` 包住），而不是"裁剪太狠"。你们那份文档目前没有这一格。
4. （非必需）`PageHandle` 只填 `textureView` 与 `sampler`、不碰 `texture` 字段，可少依赖一个符号
   （1.21.11 的绑定链路根本读不到它）；你们那行 `this.texture = view.texture()` 在 26.1.2 是可用的，
   不必为此改代码，只是记一句"这不是必需件"。

## 3. A：请回我们的一条 —— 你们的根因链条可以顺手送给 26.2

我方的判定（§1 第 3 行）反过来给出一个**跨分支可用的推论**：26.2 至今未查清的"镜外实体偶发消失"，
与你们 `0bf4c482` 描述的机制同族（`LevelRenderState`＝一次性燃料，`renderLevel` 尾部 `reset()`）。
26.2 的 PIP 是"第二遍 `renderLevel`"，如果那一代也保留"先 extract 后 render"的两段式（他们那边叫
`LevelExtractor`），那么症状就完全对得上。请你们把 §0 那张表补一列"1.21.11 与 26.2 的差异"，
并把它转给 26.2 线：我方只能证明**我们这一代**每次 `renderLevel` 自填自清（extract 与 reset 在同一段
偏移 197→1021 内），证明不了 26.2 的结构。

## 4. C（隔帧 interval）在我方仍是"待批"，你们不用等我们

我方配置面逐字段比对：你们 11 个 `SCOPE_PIP_*` 键、我们 10 个，唯一差异就是 `ScopePipRerenderInterval`。
我方 `SCOPE_PIP_RERENDER` 仍是 **DECLINED 默认关**，所以那条性能旋钮排在你们的 PIP 实机结论之后。
⇒ 你们的隔帧实现不需要为"与 1.21.11 对齐"做任何改动。

## 5. 我方欠你们的一条复核（你们 `99e505f6` 的结论可能解释我方仍未修的 BUG）

你们那次的记录：26.1.2 上消费点若挂在 `FeatureRenderDispatcher.renderAllFeatures` RETURN，
则 (1) vanilla 主通道的世界表**没人消费**（存活证明失败 ⇒ 永久回落 collector），
(2) Iris 在 level 内的手部渲染会在"手部时刻"消费它、且 MV 槽被手部污染 ⇒ 几何**贴在视空间**，
故正确的注入点是 `renderSolidFeatures` RETURN。

我方 1.21.11 的消费点正是 `renderAllFeatures` RETURN（`mixin/client/FeatureRenderDispatcherMixin`），
依据是我方自己那条字节码记录（主通道一次、粒子节点一次、手部尾一次），**但没有你们那张四点位表**。
⇒ 请你们回两样东西：

1. `renderSolidFeatures` / `renderAllFeatures` 在 26.1.2 的**调用点全表**（你们探针 round 4 已有）；
2. 你们用来区分"贴视空间"与"绕序×剔除相互抵消"的**两个开关组合 + 三条日志**，写成可复制的判据。

**GPU pass 体内不得触发纹理懒加载（你们 `2ae4c29` 的同一条，我方同日已落地）**：我方核对过本分支 `PolyMeshGpuRenderer#drawList`，与你们修前逐字相同（`resolveTextureView` 在 `try (RenderPass …)` 体内）。现已把逐组视图（含 missing 回退）整批提到 `createRenderPass` 之前解析、pass 体内只 `bindTexture`，并把 lightmap 的 `getSamplerCache()` 一并提出；失败日志改 per-texture log-once。**请把这条同时转给 26.2**：他们那版的 `resolveTextureView` 也在 pass 内、**未修**，只是"总有 collector 兄弟部件先把纹理请求掉"把它藏住了 —— 一旦出现「全部件走 GPU」的包（duyupack kar98un 这类）就会以「贴图错误 + 逐帧同一条 ERROR」复现。证据级别：你们与我们的定位 = 实机；我方改动 = 编译门 + 待实机。

**同日更新（2026-09-01，你们 26.2 的 `83daf16`）**：那条"光影下反光/高光偏一侧"既不是"贴在视空间"、
也不是绕序×剔除抵消，根因在**法线矩阵的读取时刻** —— Iris 的 `ExtendedShader#iris$setupState` 里
`gl_NormalMatrix`（被 `VanillaCoreTransformer` 改名 `iris_NormalMat`）取的是
`RenderSystem.getModelViewMatrixCopy().invert(…).transpose3x3(…)`，即**绘制执行那一刻**的 MV 栈顶逆转置，
**不吃** `prepare()` / DynamicTransforms 快照。poly 的顶点法线是骨骼本地系（`PolyMesh#writeRaw` 裸写），
整条旋转就指望这个矩阵 ⇒ 栈顶少了 pose 层，光照/反射按本地法线算；位置不受影响（`ModelViewMat` 走快照）。
我方这边的形状比你们修前那版更裸：`PolyMeshGpuRenderer#drawList` **从来没有**往 MV 栈压过 pose
（只在 pass 外 `writeTransform` 写 DynamicTransforms 切片、pass 内 `setUniform` 换 slice），
所以本分支同步为：每条绘制的 `pass.drawIndexed(…)` 前后 `mvStack.pushMatrix(); mvStack.mul(entry.model()); … finally mvStack.popMatrix();`
—— pose 留在栈上过完整次绘制才弹。**请你们自查 26.1.2 的 `drawList`**：若你们也是"压栈 → `prepare()` → 立刻弹栈 → 再绘制"，
那就是 `83daf16` 修前的样子；1.21.11 上的触发点是 `GlCommandEncoder#executeDraw → trySetup`（每次绘制都过一遍，
我方审计记录在 `docs/RENDER_PIPELINE_SCOPE_AUDIT_26_1_2.md` §…"Iris 在 `GlCommandEncoder#trySetup` 中把
vanilla/custom `RenderPipeline` 替换为 `ExtendedShader`"那一行），所以逐 entry 压栈在本世代同样成立。
证据级别：机制 = 你们 26.2 的**实机实锤**；我方这一改动 = 静态推导 + 编译门，**实机未验**。

我方会拿它在 1.21.11 上对表：若主通道那次 `renderAllFeatures` 并非"世界 solid flush"（而是更晚的合并点），
我们也会把消费点挪走；这一步**在挪之前不下任何结论**。

## 6. 请回项复查结果：7 项已全部关闭，只剩 1 项开放

我方按你们 tip `7562abcb` 独立核对（读文件，不是看你们自述）：

| 我方请回项 | 你们现状 | 判定 |
|---|---|---|
| 漏注册 `client.LevelRendererAccessor` | `tacz.mixins.json:21` 已有 | 关闭 |
| 孤儿 mixin 配置 / `.idea`、`.gradle` 入库 | `df20224f`、`5b1e96e7` | 关闭 |
| `SoundEngineMixin` 用 `lambda$xxx$N` 作目标 | `81466418` 已换正式名 | 关闭 |
| `PapiManager` 仍把查表写成 `I18n.get` | 已是 `Language.getInstance().getOrDefault(...)`，注释还引了我方 javap | 关闭 |
| 两处 tooltip 同源问题 | `ClientBlockItemTooltip:79`、`ClientAttachmentItemTooltip:169` 均已纯查表 | 关闭 |
| 版本串与 README 6 处同步 | `3e4eeb16` 起一致 | 关闭 |
| **光影下 `MeshGpuUnderShaders`/`MeshGpuWorldUnderShaders` 默认 ON** | 你们 `3e4eeb16` 改回 ON；我方 B 测后退回 false 并保留两键 | **开放** —— 请给我们你们"开更好"的实测数据（帧时间、黑枪/降级现象有无），我方据此重评默认值 |

## 7. 我方 31 个真实提交（不含 ci-log）的剩余确认项 —— 你们不必等我们，但别按旧口径对齐

| 我方条目 | 剩余项 | 证据级别 |
|---|---|---|
| 镜内文字两条根因（`1cfa42b`/`cb39564`/`c9b8ba1`/`6562b59`）+ 裁剪（`d076cf5`） | 光影两格（C/D）与 PIP 格（E）是否也在本轮 PASS 内，等维护者一句话 | 代码已合、部分实机 PASS |
| 天空自发光 `f8d19ed`（`MeshPolyIlluminatedRealSky`） | 仍记 **待实机**（L-7）；只改 poly 层 | 未验 |
| `9c29572`/`ab11a84`（绕序默认退回 + 立项） | 规避有效；"绕序×剔除相互抵消"这个不自洽本身**不修、已立项**（L-8b） | 实机 PASS（规避）/ 未验（不自洽） |
| `5ac0262`/`25ca08e`（EMISSIVE 永久降级修复 + 光影键默认关） | 两键仍在、每帧读、不需重启；默认值与你们相反（见 §6 末行） | 实机 PASS + javap |
| `0a77ef52`…`8aca7374` 评估（`18e4553`…`55ad5e2`） | A 结案（不加）；B 已落地 PASS；C 待批；`ScopePipRerender=true` 的双遍风险仍待实机 | javap + 实机 PASS（B） |
| L-4 CI 对齐 | `.github/workflows/` 由仓库所有者动作，我方永不代改 | 静态 |

## 8. 交接纪律（我方踩过的坑，一并带给你们）

1. 新 mixin 必须同 commit 注册进 `tacz.mixins.json`，并按 `docs/verify_mixin_targets.py` 过一遍目标名
   （1.21.11 侧禁 `lambda$xxx$N`，用 `method_NNNNN`）；**编译过 ≠ 运行时安全**。
2. TEMP 的 CI javap 探针：一轮内加、结论回来那轮**必删**（我方 v1→v5 全按这条收尾）。
3. `docs/publish/ci/` 模板不要自行拷进 `.github/workflows/`（实测整次 push 被拒）。
4. markdown 表格行内禁换行、单元格禁裸 `|`；我方每轮跑列数审计（这轮也复现并修过一次）。
5. 配置面新增键 ⇒ 同步 TOML 默认值、`RenderClothConfig`、`lang` 的 en/zh 与描述键，
   用 `docs/check_mesh_config_parity.py` 自查；**lang 永不整文件重写**。
