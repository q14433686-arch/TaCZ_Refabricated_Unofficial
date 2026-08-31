# 同步指导：refab 1.21.11 → refab 26.1.2 —— 内置 TML 与 GPU 静态烘焙（第 0/1/2/3 步）

> 写给 `arena/01a05170-tacz-refabricated-unofficial`（= refab **26.1.2** 分支）的下一位 agent / 维护者。
> 首刊 2026-08-31，对应本分支 R3。账本行见 `docs/lineage/HANDOFF_LEDGER.md` L-1。
>
> **本文的前提已经核实过**（不是猜的）：
> 26.1.2 分支上 `src/main/java/cn/sh1rocu/tacz/compat/meshloader/` 有 **0 个文件**、
> `docs/` 里没有 `MESH_LOADER.md` —— 也就是**TML 整条线在 26.1.2 上还不存在**，
> 所以这不是「搬一个 GPU 补丁」，而是「先把第 0 步搬过去，再按顺序往上叠」。

---

## 0. 取哪一份源码，以及为什么不是 26.2

TML（内置 mesh loader）起源于 26.2 线，**但请不要以 26.2 的 GPU 层为蓝本**：

| | 26.2（`arena/01a04e96`） | 本分支 1.21.11 @ R3 |
|---|---|---|
| 第 0 步 collector 安全子集 | 有 | 有（同一族，逐条对齐过） |
| 第 1 步 无光影第一人称常驻 VBO | 有 | 有，**实机 PASS** |
| 第 2 步 光影下常驻 VBO | 有 | 有，**几何/位置实机 PASS**；但同一天发现它会「继承」太阳/月亮的自发光 ⇒ **R3 发版前默认退回关**（见 §1.4） |
| 第 3 步 世界语境常驻 VBO | 有，**实机踩坑**：几何相对视角固定 + 烘焙时机过窄 | 有，**实机 PASS**（含光影组合，2026-08-31 一遍过；那次 PASS 不覆盖照明语义，见 §1.4） |
| 失败半径 | 一处异常 → 关总闸 + 回写配置文件 | 分表禁用 + 连续 30 次阈值 + **从不**回写配置 |
| 渲染目标 | 硬绑 `mainRenderTarget()` | 跟 `RenderSystem.outputColorTextureOverride` 解析，带 override 的那一遍直接跳过 |
| 光影下的管线归属 | 复用 `RenderTypes.entityCutout`（该管线已被归入 Iris **HAND** 程序） | 自建管线 + `IrisApi.assignPipeline(IrisProgram.ENTITIES)` |

26.2 那份的逐条审查与修法建议写在 `docs/REVIEW_UPSTREAM_TML_GPU_262_20260831.md`
（同一份代码，两个纪元，值得你读一遍再决定哪些照抄）。
**但 26.2 有三件事比本分支好，请从它那里拿**：按量化光照做 LRU、被逐出 VBO 进延迟释放池
下一帧才 close、每帧烘焙额度防「逐出-重烘打摆」——本分支的第 3 步就是照着这三件事做的，
只是把「额度」和「容量」解了耦。

> **别 cherry-pick 本分支的 arena 提交**：那条线上混着 CI 回推的 `ci-log:` 提交和临时探针提交。
> 正确做法是按 §1 的清单取**终态文件**，只有 §6 那几处「对既有文件的小改」值得看 diff。

---

## 1. 要搬的东西（本分支 @ R3 的终态清单）

### 1.1 整包新增（直接取文件）

```
src/main/java/cn/sh1rocu/tacz/compat/meshloader/            # 20 个文件
  TaczMeshyIntegration.java
  api/IPolyMeshBone.java
  config/{MeshyConfig, PolyRenderPolicy}.java
  core/{BedrockPartBoneAdapter, PolyMesh, PolyMeshModel, PolyMeshSnapshot, PolyMeshSupport}.java
  mixin/{ClientAmmoIndexMixin, ClientAttachmentIndexMixin, ClientBlockIndexMixin, GunDisplayInstanceMixin}.java
  model/{TaczPolyMeshGunModel, TaczPolyMeshAmmoModel, TaczPolyMeshAttachmentModel, TaczPolyMeshBlockModel}.java
  render/{PolyMeshGpuRenderer, ScreenRenderTracker, ShaderStateTracker}.java
src/main/resources/tacz.mesh.mixins.json                       # package = ...meshloader.mixin，4 条 client mixin
```

> ⚠️ 同族问题**已被排除**的一条（别再去翻它）：`MeshPolyInShadow=false` 让 poly 几何从不进 Iris 的
> 阴影图，静态上看确实像「枪身挡住太阳/月亮那一块反而被点亮」的成因，判别法与论证写在
> `docs/MESH_LOADER.md` §5.9、给上游的版本是审查文档 A11。**实机 A/B 已否证**：打开它没用，
> 关掉光影下的 GPU 两个开关才有用 ⇒ 根因在我们自开的那个 pass（§1.4 / `MESH_LOADER.md` §5.10）。
> 本分支保持 `false`，你们那边同理；如果你们要翻，前提是先复现出「只有阴影遍缺几何」的判别。

> ⚠️ `config/PolyRenderPolicy.java` 里还有 `MeshPolyIlluminatedRealSky`（**默认 false**，理由见 §1.4）：`_illuminated`
> 骨骼原本恒烘 `0xF000F0`（block=15 且 sky=15），光影包把 sky 读成「看得见天空」⇒ 屋顶遮不住太阳/月亮。
> 该项**只在装了光影包时**把 sky 换成环境真值、block 保持 15；无光影下逐字不变。三条消费路径
> （`PolyMeshModel#drawBoneMeshes` / `ensureBaked` / `ensureWorldBaked`）都走同一个入口，别只接一条。
> 立方体层的同一硬编码（`BedrockPart#render` 的 `15728880`）本分支刻意没动 —— 见审查文档 A10 续集。

> ⚠️ `core/PolyMesh.java` 与 `config/MeshyConfig.java` 里含 R3 追加的法线相关改动（上游审查 A10），
> 但**其中「镜像时反转发射绕序」那一条已被维护者实机否证**，现在 `MeshPolyMirrorReverseWinding`
> **默认 false**。留着的是三个开关（`MeshPolyMirrorReverseWinding` / `MeshPolyInvertNormals` /
> `MeshPolyPreferPackNormals`）与「退化面不写零法线」。整包取文件时它们会自动带过来，
> **别**在你们那边把 `FORCE_FLAT_SHADING` 恢复成编译期常量、也别把 `normals` 数组的解析删掉 ——
> 「无光影 PASS」从来不构成对法线的验证（原版实体程序不读 `va_normal`）。
> 为什么默认关掉反转：collector 那条走 `RenderTypes.entityCutout`，它**剔背面** ⇒ 绕序一反转，被剔掉的
> 是朝外的面，高模枪整把近乎全黑（2026-08-31 与 Forge 原版同包同光影对照过）。详见
> `docs/MESH_LOADER.md` §5.7 与本指导 §3 Q7。

### 1.2 GPU 层牵动的既有文件（这几处才是「本分支特有」的知识密度）

| 文件 | 为什么要改 |
|---|---|
| `com/tacz/guns/mixin/client/GameRendererMixin.java` | 帧首 `beginFrame()`（清表 + 光影开关翻转检测 + 延迟释放）；`renderItemInHand` HEAD/RETURN 的 `inHandPass` 标志；**`tacz$scopeRenderLevel` 的 try/finally 里维护 `setLevelRenderActive`** |
| `com/tacz/guns/mixin/client/ItemInHandRendererMixin.java` | 手部 flush 的绘制钩子 `tacz$drawMeshGpuAfterHandFeatureFlush`（`require=0`） |
| `com/tacz/guns/mixin/client/FeatureRenderDispatcherMixin.java` | 世界 flush 的绘制钩子：`@Inject(renderAllFeatures, RETURN)`，`require=0` |
| `com/tacz/guns/compat/iris/IrisCompat.java` | 反射面：`isUsingRenderPack / isRenderShadow / supportsHandFlushHook / assignMeshPipelineToEntity`（**全部反射，不产生硬依赖**） |
| `com/tacz/guns/client/render/scope/ScopePipRerender.java` | `isInsideScopeLevelRender()` 从私有标志改为 public（世界路径要按它拒收/「画但不清表」） |
| `com/tacz/guns/util/RenderDistance.java` | `isGuiRender()` 改 public（世界语境按 transformType 挡 GUI 预览） |
| `com/tacz/guns/compat/cloth/client/RenderClothConfig.java` | 18 项 TML 配置全部接进局内面板（R3「胶水」14 项 + 反光轮次 3 项法线开关 + 自发光天空光 1 项） |
| `src/main/resources/fabric.mod.json` | `mixins` 数组加 `tacz.mesh.mixins.json`；`provides` 加 `taczmeshloader` |
| `src/main/resources/assets/tacz/lang/{en_us,zh_cn}.json` | 36 个 `config.tacz.client.render.mesh_*` 键（18 项 × 标题+说明） |
| `com/tacz/guns/config/ClientConfig.java` | 若你们的 TML 入口注册挂在配置上，照抄本分支的接线点 |

### 1.3 顺手要带的文档

`../MESH_LOADER.md`（状态块 + 配置表 + §5 验证清单）、
`docs/TML_GPU_FEASIBILITY_1211_20260831.md`（可行性与「为什么这样设计」）、
`docs/TML_GPU_STEP2_HANDFLUSH_20260831.md`（手部/世界两条路的字节码取证链，§4 是世界那半）、
`docs/TML_GPU_PROBE_TOOL_20260831.md`（**先读这篇，见 §3**）、
`docs/REVIEW_UPSTREAM_TML_GPU_262_20260831.md`（A10 就是本指导 §3 Q7 的出处）、
`docs/check_mesh_config_parity.py`（齐平自查脚本；§6 要跑的就是它，不带过去那条 CI 步骤就是空跑）。

---

### 1.4 ⚠ 两个「光影下走 GPU」的默认值：别照本分支任何一版抄成 true

R3 一度把 `MeshGpuUnderShaders` / `MeshGpuWorldUnderShaders` 翻成 true，当天晚上又退回 false —— 中间
只隔了一次 A/B。原因写在 `docs/MESH_LOADER.md` §5.10：开着这两键时，**高模枪挡住太阳/月亮的部分会
「继承」天体的自发光亮度**（第一人称 / 第三人称 / 展示台三种语境都中），走 collector 就没有；
本分支的嫌疑是 `EMISSIVE_PIPELINE` 那条兜底（自建管线带 `withShaderDefine("EMISSIVE")`，还把它登记进
`IrisProgram.HAND/ENTITIES` ⇒ 包按「自发光 / 不查阴影」画）。已做两步：

1. **默认值**：两键一律 `false`；`MeshPolyIlluminatedRealSky` 同样 false（它是同一天我按误读的症状写的，
   不是维护者报的问题，别默认开）；
2. **连带缺陷（这个建议照抄）**：`resolveLightmap` 以前一旦取不到 lightmap 视图就**永久**把整条 GPU 路
   降级到 EMISSIVE；现在改成每帧重试 + 只在光影下真取不到时整条拒收（`gpuMasterUsable()`），
   并在 `worldSubmitBlocker` 里给出原因串。这样「兜底 = 换照明语义」不再可能发生。

移植时请顺手确认你们那边的 `resolveLightmap` 等价物没有同类闩锁（26.2 有同款）。


## 2. 六条不可谈判的设计不变量

搬的时候最容易被「顺手简化」掉的就是这五条，每一条都是踩出来的：

1. **`require=0` + 安全回退**。三个渲染钩子（`GameRendererMixin` 的两处、
   `ItemInHandRendererMixin`、`FeatureRenderDispatcherMixin`）必须允许注入失败：注入失败的正确
   后果是「这条枪走 collector」，绝不能是「少画一帧 / 启动崩」。
2. **存活证明（liveness proof）**。凡是「依赖某个 mod/版本的钩子」的路径，都要先证明那个钩子
   本帧或上一帧真的跑过，否则**在提交侧就拒收**（不是绘制侧跳过）。手部那条依赖 Iris 的 flush
   钩子，世界那条依赖 `renderAllFeatures` 的注入点。用**帧号比对**，不要用「本帧标志 + 帧首复位」
   —— 后者对钩子与 `beginFrame` 的相对顺序敏感（审查文档 A9）。
3. **变换取自消费时刻，不是提交时刻**。两层变换：顶点里烘 pose、绘制时再乘当刻 MV。
   「当刻」必须是**这批几何将被 vanilla 比较的那一套** MV/投影/目标。
   26.2 世界语境那个「相对视角固定」的症状，根因就是取自另一个时刻的渲染状态。
   本分支因此：世界消费点单独放在 `renderAllFeatures` 的 RETURN；`inHandPass` 时整段跳过
   （1.21.11 的 `GameRenderer#renderItemInHand` 会在 `renderHandsWithItems` 前后 push/pop MV，
   那一刻的世界 MV 是错的）；渲染目标跟 `outputColorTextureOverride` 解析。
4. **烘焙时机不能绑死在某个瞬间**。世界路径按**量化光照档位做 LRU**（`MeshGpuLightCacheSize`，
   1..16），烘焙发生在**提交侧**（哪一档出现就烘哪一档），世代号同时认「光影开关翻转」和
   「消费格式变化」；淘汰的 VBO 进延迟释放池，`beginFrame` 里才 close（同一帧内可能还有
   条目引用它）。**别把「额度」和「容量」做成一个旋钮**（审查文档 A6）。

5. **失败半径 = 一张表**。世界表异常只关世界（`gpuWorldDisabledThisSession`），手部表异常只关
   手部，`GPU_BAKING` 只在总闸层面被用户手动关。连续 30 次才算病理（抗抖动）。
   `catch (Exception | LinkageError)`。绝不 `MeshyConfig.*.set(false)`。

6. **光影下的兜底不能改变照明语义**。自建管线一旦在 `RenderPipeline` 上写了 `withShaderDefine("EMISSIVE")`，
   光影包就把它当「自发光 / 不查阴影」的几何画 —— 而 `assignPipeline` 登记的正是这条管线，于是「挡住天体
   却继承天体亮度」。所以：lightmap 取不到时**不许**永久降级成 EMISSIVE（本分支 R3 之后已改成每帧重试 +
   光影下取不到就整条拒收回 collector）；新增管线定义时，任何 shader define 都要问一遍「包会因此少算什么」。

另外三条边界，别当 bug 修：半透明部件与弹匣**永远**留在 collector；镜内那一遍
「画但不清表、不占本帧消费标志」；GUI / 内嵌预览 / 阴影在**提交侧**按 transformType 拒收
（热栏图标是以 GUI 语境在 HUD 里提取的，`ScreenRenderTracker` 拦不住，只能按语境挡）。

---

## 3. 26.1.2 上必须先测、不能照抄的七件事

本分支的结论全部来自 **1.21.11（混淆）+ Iris 1.10.7** 的 CI javap 实测；26.2 的结论来自
26.2。**26.1.2 是第三种组合**（Mojang 正式名 + frame-graph/submit-node 时代），
它自己那份 `docs/RENDER_PIPELINE_SCOPE_AUDIT_26_1_2.md` 已核实了这些事实：
`FeatureRenderDispatcher#renderAllFeatures` 存在、`RenderType#draw` 每批次自建 `RenderPass`、
`ItemInHandRenderer#renderHandsWithItems` 在 `renderAllFeatures()` 返回**之后**才 `endBatch()`、
Iris `HandRenderer` 有自己的 `SubmitNodeStorage`/`FeatureRenderDispatcher` 且
`endRender()` 里是 `renderAllFeatures()` → `endBatch()`。

**还没核实、且决定设计的**七个问题 —— 用 `scripts/mesh_render_probe.gradle`
（`docs/TML_GPU_PROBE_TOOL_20260831.md` 讲了怎么改类名/成员名、怎么让输出落进
`build-reports/compile-java.log` 让沙箱读回）逐条回答：

| # | 问题 | 本分支（1.21.11）的答案 | 若 26.1.2 不同，怎么办 |
|---|---|---|---|
| Q1 | `RenderType#draw` 是**绘制时**读 `RenderSystem.getModelViewMatrix()`，还是绑定 pass 时就读完？ | 绘制时读（字节码 `0: getModelViewStack / 34: getModelViewMatrix → writeTransform`） | 若在 pass 创建前就读完，消费点必须整体后移 |
| Q2 | `LevelRenderer` 里 `renderAllFeatures()` 有几个调用点、各在哪个私有方法内、`endBatch()` 相对位置 | 两个（`method_62214` 主通道、`method_62213` 离屏遍）＋ `ItemInHandRenderer` 一个 | 决定 `require=0` 钩子挂哪、以及要不要 override 门 |
| Q3 | 有没有 `PreparedFrame#executeSolid` / `RenderType#prepare()` / `drawFromBuffer` | **没有** ⇒ 26.2 的「压栈取 MV + prepare()」路线不可用，本分支才改成自建 pass | 有 → 可以走 26.2 的路线；没有 → 照抄本分支 |
| Q4 | `IrisProgram` 的**全量常量**（别猜名字） | 无 `ENTITY`、无 `MAIN`，有 `ENTITIES` / `ENTITIES_TRANSLUCENT` / `EMISSIVE_ENTITIES` | 常量不存在时 `assignPipeline` 只打 WARN、枪不发光照（静默错误，最难查） |
| Q5 | `GameRenderer#renderItemInHand`（或等价方法）是否在 `renderHandsWithItems` 前后 push/pop model-view | 是 ⇒ 世界钩子必须 `inHandPass` 跳过 | 若否，世界/手部可以共用消费点，但门 2 仍要留 |
| Q6 | 光影激活时 `DefaultVertexFormat.ENTITY` 被 Iris 扩展成什么、stride 是否随之变 | 会变 ⇒ 世代号必须同时认「光影开关」与「消费格式」 | 不变则世代号可以简单些，但别去掉 |
| Q7 | 光影包是否按 `gl_FrontFacing` 取反顶点法线（`normal *= gl_FrontFacing ? 1.0 : -1.0` 一类写法），以及你们那边 poly 用的 `RenderTypes.entityCutout` 开不开背面剔除 | **已答（实机替代证据）**：自研 GPU 管线两条都 `.withCull(false)`（静态可核），collector 的 `entityCutout` **剔背面** —— 沙箱里仍然没 Loom jar 核不了字节码，但「只把绕序反转就整枪全黑、关掉与 Forge 原版逐字一致」这组对照只有这一种解释；`entityCutout` 与 `entityCutoutNoCull` 成对存在，名字差一个 NoCull，与此一致 | 推论 ⇒ **绕序不要反转**（反了就把朝外的面剔掉）。`gl_FrontFacing` 与朝外法线不自洽这件事在数据层仍然真实存在，但要闭合它得先动剔除（`entityCutoutNoCull` = 行为改动，双面会遮内部件）或从数据反推绕序，两者都需要实机，本分支都没做 |

**Q4 是最容易翻车的一条**：猜错常量不会崩，只会「光影下这把枪照明不对 + 一行 WARN」，
很容易误判成渲染逻辑错。

---

## 4. mixin 目标的纪元差异（AGENTS.md §3）

- 26.1.2 与 26.2 一样是**非混淆**分支，所以目标方法名用 Mojang 正式名，不需要 intermediary。
- 但**仍然禁止**在 1.21.x/26.x 里用 javac 合成名（`lambda$xxx$1`、`method_62214` 这种
  编译期编号）作为 `@Inject.method` —— 编号跨构建不稳。本分支 `FeatureRenderDispatcherMixin`
  之所以只挂 `renderAllFeatures`（public API）而**不**挂 `LevelRenderer` 的私有合成方法，就是这个原因。
- 触碰渲染 mixin 后跑：`python3 docs/verify_mixin_targets.py`、
  `python3 docs/verify_shader_imports.py`。
  注意：**这两个脚本都要 loom 合并后的 jar**，沙箱里跑会报
  `Loom merged jar not found` —— 那是环境限制，不是失败；要么在本地跑，要么先按 §3 的探针把
  成员名在 CI 上核实。Iris 内部类的 mixin 用 `remap=false` + 真实 mod 名。

---

## 5. 分步验收清单（每一步都要过完再进下一步）

### 5.1 第 0 步（collector 安全子集）
- [ ] mesh 枪包（`model.geo.json` 带 `TACZ:mesh` / poly 部件）在背包、手持、掉落物、展示框里都画得出来；
- [ ] 高面数枪在无 GPU 路径时按预算降级（`MeshGuiMaxVertices` / `MeshWorldMaxVertices`），日志有降级行；
- [ ] 关掉 `MeshEnable` 行为等价于「没装 TML」（不残留、不崩）；
- [ ] `tacz.mesh.mixins.json` 4 条 mixin 全应用（启动日志无 `Invalid mixin`）。

### 5.2 第 1 步（无光影第一人称常驻 VBO）
- [ ] 第一人称枪模型与 collector 路径**逐像素一致**（同光照档、同缩放、同俯仰/摆动）；
- [ ] 换弹/开火/检视连打：无双影、无残影（这条是历史上最常见的回归）；
- [ ] `latest.log` 里 `GPU baked … bones` 只出现一次，之后每帧无日志；
- [ ] F3+T 重载 ×5：显存不单调增长（延迟释放池在工作）。

### 5.3 第 2 步（光影下常驻 VBO，`MeshGpuUnderShaders`）
- [ ] 光影下第一人称枪**位置/朝向**随相机与视角正确变化，转身不漂；
- [ ] 明暗变化（进屋/挖掉脚下光源）枪体照明跟着变，且 `Assigned mesh_entity to the Iris HAND program.` 之类日志只出现一次；
- [ ] 开光影后 vanilla 手部（空手/剑）不受影响；`IrisHandRendererReticlePassMixin` 相关的镜内 reticle 仍正常；
- [ ] **对着天空 / 太阳 / 月亮转视角**：枪身挡住天体的那部分**不得**跟着亮起来（本分支 §1.4 退回默认关的
  就是这个现象）。若出现，先查 `Level lightmap view unavailable` 那行 WARN 在不在，在就是 EMISSIVE 兜底；
- [ ] 把 Iris 卸载 / 换光影包 / F3+T：都只回退 collector，不崩、不黑屏。

### 5.4 第 3 步（世界语境，本分支实机 PASS 的那两条重点）
- [ ] **他人手持的 mesh 枪必须随相机正确移动** —— 「钉在视角方向上 / 转身时漂」就是 26.2 踩到的坑，
  出现即说明变换取自错误时刻（不变量 3），**不要**去挪烘焙时机来「绕过」；
- [ ] 近处高模枪不再因预算整把消失，日志出现 `GPU world-baked N bones (M vertices) at quantized light …`；
- [ ] 明暗边界上一排掉落枪：`GPU world-baked` 只在前两次是 info 级；逐帧刷说明 LRU 容量不够；
- [ ] 开背包 / 枪匠台 / 热栏 / 开镜（F3+T 也来一次）：世界里不多画、GUI 内不少画；
- [ ] 光影组合：`MeshGpuWorldUnderShaders=true` 时看到
  `Assigned mesh_entity_world to the Iris ENTITIES program.`，且夜晚变暗、进照明块变亮；
  **并**补 §5.3 那条「挡天体不继承自发光」—— 本分支这两键后来退回默认 false 就是因为漏了这一步；
- [ ] 任一光影/其它 mod 组合下若世界路径没生效，**先查** `GPU world submit refused: <原因>` 这一行
  （本分支 R3 加的诊断；每种原因只打一次）。没有原因 = 门闸静默拒收，那是设计，但没法排查。

### 5.5 全部完成后
- [ ] `docs/MESH_LOADER.md` 的状态块改成你们自己的实机结论（AGENTS.md §2：没验的就写「待实机」）；
- [ ] 配置 18 项 ↔ Cloth 18 条 ↔ 语言键 36 个，三方齐平：`python3 docs/check_mesh_config_parity.py`
  （脚本已随本仓附上，键集合 / 字段绑定 / 默认值 / `defineInRange`↔`setMin·setMax` / 语言键 /
  en·zh 齐平六项一起查，非 0 退出即失败）；
- [ ] 光影下的法线/绕序矩阵（`docs/MESH_LOADER.md` §5.7）**本分支已跑过一轮**：结论是三项全 false
  （= 与上游逐字相同）才对，`MeshPolyMirrorReverseWinding` 因此退回默认关。你们只需确认移植后
  「关掉它」的那一格仍然与你们的原版观感一致；顺手的话再跑 `MeshPolyPreferPackNormals=true`
  （纯美观项，与剔除无关，没人验过）；
- [ ] 在 `docs/lineage/HANDOFF_LEDGER.md` 上把你们认领的行改成 `DONE(<sha>)`，并回填「与指导文档不一致之处」。

---

## 6. 配置 / 语言键 / 局内面板的齐平脚本

R3 那轮把「TOML 里能改的」全部接进了局内面板。齐平性用仓库里的脚本自查（零依赖，非 0 退出即失败）：

```bash
python3 docs/check_mesh_config_parity.py
```

它查六件事：`MeshyConfig` 里每个 `builder.define*("Key", …)` **恰好**有一条 Cloth 引用；那条引用绑的
**字段名**与 toml 键是同一个选项（按 `FIELD = builder.define("Key")` 配对 —— `MeshEnable` ↔
`ENABLE_MESH` 这种命名不机械，别用蛇形转换去猜）；`setDefaultValue` 与 toml 默认值一致；
`startIntField/startDoubleField` 的 `setMin/setMax` 与 `defineInRange` 区间一致；每个键的
`config.tacz.client.render.<snake(key)>` 与 `.desc` 都在；en/zh 的 `config.tacz.client.render.*`
键集合完全相同。

> 上一版这里是一段内联的 `python3 - <<EOF` 正则脚本，**它的蛇形转换是错的**
> （`re.sub(r"(?<!^)(?=[A-Z])", "_", "GPU_BAKING")` 会在每个大写字母前插下划线），
> 于是「期望差集为空」永远达不到、真正该报的错反而混在噪声里。已换成上面那份文件并加了
> 四类错误的注入实测（默认值 / 字段绑错 / 范围收窄 / 缺 `.desc` 键，四类都被准确报出）。
> 26.1.2 若沿用内联版请一并替换。

## 7. 本分支明确不做的事（你们也别顺手做）

- 不做 `Lightmap` 的自定义烘焙/纹理拷贝：走 `RenderSystem.bindDefaultUniforms` + 现有光照贴图。
- 不把半透明部件、弹匣搬进常驻 VBO（alpha 排序与逐帧 pose 都要重做，收益不值）。
- 不在世界路径上使用 `IrisProgram.HAND`：那是手部 pass 的专项修复，世界用会串
  （26.2 的世界表正踩在这条上，审查文档 A4）。
- 不「为了少一个 mixin」把手部与世界两张表合成一张：两个消费时刻的渲染状态不同，
  合成就是 26.2 那个 bug 的形状。
- 不在渲染路径里写配置文件（见不变量 5）。
- `MeshPolyInShadow` 保持默认 false：那是「让 mesh 枪投影」的独立需求，成本在阴影图上，
  和 GPU 烘焙没关系，别混进同一个开关。

## 8. 搬完之后请回给我的东西

1. 一张表：§3 的 Q1-Q6 在 26.1.2 上的实测答案（尤其 Q3：有没有 `prepare()`/`drawFromBuffer`）。
2. §5.4 前两条的实机结论（过 / 不过 + 日志片段）。
3. 你们为 26.1.2 改写的注入点差异说明——我要据此更新本分支的 `MESH_LOADER.md`「已知边界」，
   并把可通用的部分回流给 26.2（账本 L-2）。
