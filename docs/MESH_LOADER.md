# 内置 TacZ Mesh Loader [TML]

> 代码移植自 [VellEagle/TacZMeshLoader](https://github.com/VellEagle/TacZMeshLoader)
> `1.21.1_fabric` v0.1.7，作者 VellEagle，GPL-3.0。不是官方 TacZ 附属。
> 署名与许可详情见仓库根 [`LICENSES.md`](../LICENSES.md)。
>
> **状态（2026-08-31）：安全子集 + 第一人称 GPU 静态烘焙已实机 PASS（08-30）；
> 世界语境 GPU 烘焙（第 2 步）已实装、待实机验证（验证矩阵见 §5.2-bis）。**
> 已实测覆盖：无光影第一人称、光影下第一人称（vanilla RenderType 路线）、
> 世界语境近距全模（collector 路径）、光影开关切换（烘焙世代失效）。
>
> 路线图见 [`TML_PERF_DIRECTIONS_2026_08_29.md`](investigations/TML_PERF_DIRECTIONS_2026_08_29.md)。
> 已完成其中的第 0 步（安全子集）与第 1 步（无光影 GPU 路径），
> 且光影下也经由 vanilla RenderType 路线拿到了 GPU 收益（原方向 1 的替代实现）。

## 0. 与四个关闭 PR 的关系（为什么这是第五次、以及为什么这次砍掉了 GPU）

| 版本 | 结局 | 教训（本轮如何处置） |
|---|---|---|
| PR #33 | 关 | GPU 画在世界 pass + 不可信矩阵 + `visitBones` skip 剪子树 → **本轮无 GPU 路径，问题不存在** |
| PR #69 | 关 | 光影一开整条回退 CPU；声称做了的代码没做 → 本轮如实声明 CPU 路径是常态而非回退 |
| PR #70 | 关 | 全局 WORLD_DRAWS 表泄漏进世界 pass；弹匣没接 `IMirrorGeometry` → **无 GPU 表；弹匣链路照搬 v4 已修正的架构（见 §2）** |
| PR #71/#72 | 关 | v4 架构收敛但被要求从干净基线重做 → 本轮**逐文件对照 HEAD（含 #77/#82 之后的懒加载改动）重新落地**，只保留三轮教训打磨过的安全子集 |

维护者关闭 #72 的意见是「不应以重做名义复用已关闭的分支」。本轮的处理方式：
不 cherry-pick、不合并任何关闭分支；以关闭分支为**参考资料**逐文件审计后在
当前 HEAD（`fcaa2b8`，含 PR #82 的 PIP 修复与懒加载重构）上重写落地，
每个 mixin 注入点、每个反射字段名都对照当前 HEAD 源码逐一核实过
（`ClientAttachmentIndex` 在 #72 之后新增了 warmUp/懒加载路径，
注入点 `checkTextureAndModel`/`checkLod` 仍是模型装载的唯一入口，语义未变）。

## 1. 本轮包含什么 / 不包含什么

### 包含（安全子集）

- **poly_mesh 解析与渲染**：枪 / 配件 / 弹药（物品、掉落实体、抛壳）/ 方块，
  全部走 26.2 的 `SubmitNodeCollector.submitCustomGeometry` 延迟提交路径，
  submit 当刻冻结骨骼矩阵快照（与 `BedrockRenderSnapshot` 同一理由）。
- **geo JSON 解析缓存**（修复用户 2026-08-25 log 实证的双遍解析）：
  按 geo 路径缓存共享网格数据，资源重载时整体失效；统计日志按 geo 去重。
- **顶点预算闸门**：GUI/FIXED/HEAD 超 `MeshGuiMaxVertices` 只画立方体；
  第三人称/掉落物/展示框超 `MeshWorldMaxVertices` 同理；另有距离闸门。
  **近距离全模豁免**（`MeshWorldFullDetailDistance`，默认 16 格）：该距离内的
  世界语境 poly 无条件画全模，世界预算只保护远处/密集场景——否则无 LOD
  低模的高模枪（如 36 万顶点级枪包）在玩家眼前的第三人称/掉落物/展示台上
  会整层消失只剩立方体。枪包若在 display JSON 里提供了 `lod` 字段，
  TACZ 本体的 LOD 选择逻辑优先生效（`GunLodRenderDistance` 控制），
  该豁免只兜底「没有 LOD 可退」的枪包。
- **弹匣双通道**：主遍历 exclude `additional_magazine` 子树；立方体弹匣走
  26.2 原生 `IMirrorGeometry`；poly 弹匣在 `additional_magazine.visible` 时
  按该节点变换补画（与上游 TML `renderSubtreeDirect` 同构）。
- **阴影 pass 默认跳过 poly**（`MeshPolyInShadow=false`）：立方体已提供影子形状，
  光影下省一半顶点成本。
- **加载告警**：超 `MeshMaxModelVertices` 的模型加载时警告枪包作者。

### GPU 静态烘焙（第 1 步，已实装并实机 PASS）

安全子集落地后追加，**仅第一人称手部 pass**（`ScopeMaskRenderer.isInHandPass()`
判定，规避关闭 PR 的世界 pass 泄漏形态）：

- 顶点常驻骨骼本地空间的逐骨骼 VBO，每帧只上传 O(骨骼) 个矩阵，
  36 万顶点级高模的第一人称 CPU 变换成本从 O(顶点) 归零;
- 光照按 4 级量化烘进 UV2，跨档才重烘（1 秒节流）;
- **光影下同样走 GPU**：默认经 vanilla RenderType 管道
  （`RenderType.prepare()` + `drawFromBuffer`，管线是 Iris 已按 HAND program
  接管的 ENTITY_CUTOUT）——枪体拿到光影光照，顶点仍在常驻 VBO;
  `MeshGpuUnderShaders=true` 可强制裸 GPU pass（诊断用，无光影光照）;
- **光影开关翻转时烘焙缓存立即失效重烘**（烘焙世代号机制，绕过光照节流）——
  否则旧 VBO 被新管线按错位 stride 解读，模型拉伸（实测复现过并修复 PASS）;
- GPU 绘制失败自动回退 collector 路径并停用本会话 GPU（不崩不糊）。

### 世界语境 GPU 烘焙（第 2 步，已实装，**待实机验证**）

第三人称（其他玩家手持）/掉落物/展示框/展示台雕像共用同一套常驻 VBO，
每把枪每帧登记 O(骨骼) 个矩阵进世界表（`WORLD_DRAWS`），在世界帧图的
`PreparedFrame.executeSolid` RETURN 处统一绘制（**首版曾挂在
renderAllFeatures 上 —— MV-PROBE v2 字节码取证证明 26.2 的世界实体 pass
不经过它**：LevelRenderer.render 的帧图 lambda 直调 executeSolid（偏移 177），
而 renderLevel 偏移 560 那次 renderAllFeatures 在 MV 栈 pop 之后执行，
在那里画 = 丢相机旋转层 = 枪固定在视角空间，实测复现；正确消费点处
MV 栈顶恰为 viewRotation（render 内 30-45 push、591 pop、帧图执行 572
在两者之间），与手部两层变换完全同构）——多人满屏高模枪的
CPU 成本从「每帧每枪 O(顶点)」降到「每帧每枪 O(骨骼)」。上游 TML 本就对
一切非 GUI 场景走 VBO（`useVBO = !isRenderingScreen()`），本步是其 26.2
submit/collector 架构下的等价物。要点：

- **光照按量化档 LRU 缓存**（`MeshGpuLightCacheSize`，默认 4 档）：同屏
  不同光照的枪各用各的档；逐出的 VBO 延迟一帧释放（本帧绘制表可能还引用）；
  每帧新烘焙有额度闸门，病理场景（同帧光照档数超容量）回退 collector
  而不是逐帧「逐出-重烘」打摆;
- **提交侧防泄漏闸门**（关 PR #33/#69/#70/#71 的每个泄漏入口逐个封死）：
  GUI 语境按 transformType 拒收（热栏图标无 Screen，事件拦不住）；Screen
  内嵌 3D 预览（背包人偶/枪匠桌）用 Fabric ScreenEvents 精确框住 extract
  窗口拦截（不用 100ms 时间戳窗口——那会「一开背包全场景跌回 collector」，
  上游 TML 记载过的同款事故）；镜内那遍、阴影 pass、手部 pass 各自拒收;
- **镜内 PIP 二次渲染**：世界表在镜内那遍照画但不清表（与 collector 的
  「两遍内容一致」裁定同构），主画面那遍再正常消费;
- 光影下走与第一人称相同的 vanilla RenderType 管道；世界 pass 里
  ENTITY_CUTOUT 由 Iris 按 gbuffers_entities 链路接管（**此点未实测**，
  若个别包异常，`MeshGpuWorld=false` 一键回到纯 collector 现状）;
- 顶点预算（`MeshWorldMaxVertices`）只对 collector 回退路径生效——GPU
  路径没有 O(顶点) 提交成本，预算对它没有保护对象；这同时意味着 16 格外
  的纯 mesh 枪不再整层消失。

### 明确不包含（后续方向，见路线图）

- 姿态缓存 / 三角形配对（路线图方向 2，collector 兜底路径的常数优化）。
- 导入期焊接/索引化/自动 LOD（路线图方向 4）。
- mesh 目镜（上游 TML 同样不支持：ocular 物体必须用立方体）。

## 2. 弹匣链路（关 PR #70 的架构缺口，本轮的处理）

26.2 的换弹弹匣：`BedrockGunModel` 把 `additional_magazine` 的 FunctionalRenderer
设为返回 `IMirrorGeometry`（指向 `magazine` 节点），快照遍历器原生处理立方体镜像。

poly 部分：`TaczPolyMeshGunModel#submit` 里
1. 主遍历 `setExcludeSubtree(additional_magazine)`——否则换弹中它会出现在两个位置；
2. `super.submit` 照常（立方体 + IMirrorGeometry）；
3. 主 poly 快照提交（含 `magazine`）；
4. `additional_magazine.visible` 时，把该节点到根的变换链乘进新 PoseStack，
   `captureSubtree(mirrorRoot=true)` 补画 `magazine` / `additional_magazine`
   的 poly（mirrorRoot=true = 根骨骼自身变换不再套用，因为已在变换链里）。

## 3. 枪包怎么用

display JSON：

```json
{
  "model_type": "mesh",
  "model": "mypack:gun/mygun_geo",
  "texture": "mypack:gun/uv/mygun",
  "animation": "mypack:mygun"
}
```

并提供 `assets/mypack/geo_models/gun/mygun_geo.json`（Meshy 插件导出的
poly_mesh geo）。`model_type: "mesh"` 只对枪本身必需；配件/弹药/方块只要
模型旁存在同名 geo 就会替换。目镜物体不支持 mesh（与上游 TML 相同）。

`fabric.mod.json` `provides: ["taczmeshloader"]`——依赖外置 TML 的枪包
在本 mod 下视为依赖满足。

## 4. 配置（`tacz-client.toml` 的 `[mesh_loader]`）

| 键 | 默认 | 含义 |
|---|---|---|
| `MeshEnable` | true | 总开关（关掉后仅立方体渲染，行为同无 TML） |
| `MeshPolyInShadow` | false | 阴影 pass 是否画 poly |
| `MeshMaxRenderDistance` | 48 | 世界 poly 距离（0=不限） |
| `MeshPolyInPreview` | true | GUI/FIXED/HEAD 是否画 poly |
| `MeshGuiMaxVertices` | 65536 | GUI 顶点预算（0=不限） |
| `MeshWorldMaxVertices` | 120000 | 第三人称/掉落物顶点预算（0=不限） |
| `MeshWorldFullDetailDistance` | 16 | 该距离（格）内世界 poly 免顶点预算画全模（0=关闭豁免；已接 Cloth Config 界面） |
| `MeshMaxModelVertices` | 120000 | 加载时告警阈值（不影响渲染） |
| `MeshLogStats` | true | 加载统计日志 |
| `MeshGpuBaking` | true | 第一人称 GPU 静态烘焙总开关（已接 Cloth Config 界面） |
| `MeshGpuWorld` | true | 世界语境（第三人称/掉落物/展示框/展示台）GPU 烘焙（需 MeshGpuBaking；已接 Cloth Config 界面） |
| `MeshGpuLightCacheSize` | 4 | 每枪模保留的世界烘焙光照档数（LRU；已接 Cloth Config 界面） |
| `MeshGpuUnderShaders` | false | 光影下强制裸 GPU pass（诊断用：绕过光影管线，枪体无光影光照） |

## 5. 验证清单

### 5.1 编译（CI 闭环）——已打通

`.github/workflows/compile-check.yml` 已由仓库所有者放入分支，每次 push
自动跑 `./gradlew compileJava` 并把日志写回 `build-reports/compile-java.log`。
本文档涉及的全部提交均 CI 编译绿。

### 5.2 实机（下列 1-7 项 + GPU 各路径均已实测 PASS，2026-08-30）

1. **无 mesh 枪包回归**：行为应与改动前一致（默认包全立方体，mixin 注入点
   都是 TAIL + geo 存在性检查，无 geo 时零行为差异）。
2. `model_type: mesh` + geo：第一人称可见、贴图正确；日志出现
   `poly_mesh stats for ... N bones, M vertices`（每 geo 只一行——缓存生效）。
3. F5 / 掉落物 / JEI / 展示框：位置与投影正确（本轮全走 collector，
   不存在 #70 的世界 pass 泄漏形态）。
4. 换弹：枪上弹匣与手里弹匣都在（纯 mesh 弹匣尤其要看）；换弹全程无双影。
5. 高模包（duyupack 级）：JEI 打开一屏图标——应看到
   `poly preview suppressed in GUI` 且不卡死。
6. 光影（Complementary 系）：poly 枪身正常照明（走的是 vanilla
   entityCutout 提交，Iris 按 HAND program 处理，与立方体同一路径）；
   阴影里枪影仍在（立方体提供）。
7. 资源重载（F3+T）：poly 仍正常（解析缓存失效并重建）。

### 5.2-bis 世界 GPU 烘焙的待验证矩阵（2026-08-31 实装，全部**未实测**）

1. 无光影：掉落一把高模 mesh 枪 → 位置/贴图/光照正确，日志出现
   `GPU world-baked ... bones` 与 `GPU world mesh pass ... drew`;
2. 第三人称（F5 或第二个客户端）：手持高模枪正确，换弹/开火动画正常
   （逐骨骼矩阵天然跟随动画）;
3. 展示台雕像/物品展示框：位置与投影正确;
4. 打开背包/枪匠桌：GUI 预览照常（collector），**同屏世界里的 mesh 枪不消失
   也不掉帧**（ScreenRenderTracker 只拦 Screen 提取窗口）;
5. 明暗差异场景（洞口/火把旁）放多把枪：各枪光照正确，日志烘焙次数收敛
   （不逐帧重烘）;
6. 光影：世界 mesh 枪照明与立方体一致（gbuffers_entities 接管）——
   **本项风险最高**，异常时 `MeshGpuWorld=false` 回退并回报;
   **已修一发（法线/反光）**：光影包的 `gl_NormalMatrix` 是 Iris 在
   **绘制执行那一刻**从 RenderSystem MV 栈顶取逆转置
   （`ExtendedShader.iris$setupState` 26.2 源码实读），不走 prepare() 快照
   —— 首版在 prepare() 后就弹栈，法线丢掉 pose_bone 旋转层 ⇒ 反光的
   光源关系错乱（实测复现）；现弹栈移到 drawFromBuffer 之后，重点复测
   反光方向是否与立方体部件一致;
7. 开镜（PIP）：镜内那遍世界枪仍在（不消失、不双影）;
8. 光影开关翻转：世界枪不拉伸（世代号失效链路与第一人称共用）。
9. 全 GPU 提交的枪（每个可见部件都走 GPU 表）贴图正常不紫黑
   （05170 实机踩坑 `2ae4c29` 的移植验证：纹理已改为 pass 外预解析）。

**第 9 项（2026-09-01 已实装，待实测）**：开镜时 GPU 路径画的 mesh 枪身
目镜裁剪。修法按本仓掩码语义（非 05170 的深度孔径架构）：无光影裸 pass 用
新 `LIT_CLIPPED_PIPELINE`（core/scope_body + SCOPE_MASK，pass 内绑掩码）；
光影 RenderType 路线把手部表的 entityCutout 过一遍 `clipForViewmodel`
（与 collector 枪身同一份替换，scope_body_clipped 的 Iris 链路已被立方体
实证）；两路共用 `maskReadyForViewmodel(true)` 判据，与立方体裁剪同开同关；
世界表不裁。验证点：开镜 mesh 枪管不穿镜、松开右键枪身完整、低倍 sight 不啃洞。

**第 10 项（2026-09-01 已实装，实机 PASS 2026-09-02）**：配置持久化
（FCAP 保存断桥）。Cloth 界面改任意配置 → 保存退出 → 重启：值保留。
旧 TOML 里钉着的旧值需玩家改一次并保存（不追溯改写用户文件）。

**第 11 项（2026-09-02 已实装，待实测）**：开镜距离补偿。两道距离闸门
（`MeshMaxRenderDistance` 48 格 / `MeshWorldFullDetailDistance` 16 格）原按
裸眼距离判定，开镜放大 Z 倍后 4x 镜下观感只剩 12/4 格 —— 镜内的掉落物/
第三人称 mesh 枪几乎必然是立方体（实机回报「二次渲染镜头里还是未烘焙」）。
现闸门阈值乘以当前开镜放大系数（随开镜进度渐变、收镜回 1），角尺寸语义
一致；经典整屏变焦与 PIP 皆适用。验证点：4x/8x 开镜看 30-100 格外的
掉落 mesh 枪 → 应为高模；收镜后远处枪恢复原距离行为；帧率无异常
（世界 GPU 路径 O(骨骼)，补偿只是让更多枪走已烘焙路径）。

**第 12 项（2026-09-01 已实装，待实测）**：跨包合成 `tacz:nbt` 材料。
JEI/工作台里「附属包要另一包的枪/配件」的配方材料格应正常显示带 id 的
物品并可合成；latest.log 不再出现 `Failed to resolve gun smith table
ingredient`（tacz:nbt 形态）。

**第 13 项（2026-09-01 裁定：姊妹线修复<b>不移植</b>，改为加观测点；实机未验）**：
「二次渲染时视野内高模枪在镜内不烘焙」。1.21.11 的 `237dc153` 与 26.1.2 的
`db360639` 已按同一根因修好：它们那两条线的 `LevelRenderer#renderLevel`
**每调用一次就重新提交一遍世界几何**，于是镜内那遍会重新走一次
`TaczPolyMeshGunModel.submit`，被 `shouldSubmitGpuWorld()` 里
`isInsideScopeLevelRender()` 那条防御性闸门拒收 ⇒ 镜内那遍只能回
collector + 顶点预算 ⇒ 高模枪被打成裸立方体，而主画面那遍照常 GPU 烘焙。
它们的修法是「删闸门 + 镜内那遍画完也清表（主遍会重新提交一份）」。

**本线（26.2）不成立，逐条证据：**

| # | 证据 | 出处 |
|---|---|---|
| 1 | 26.2 的世界提交（含实体模型渲染）在**本帧 extract 阶段一次完成**，`LevelRenderer#render` 只是绘制阶段 —— 镜内那遍**复用** extract 那一批提交节点，闸门只在提交时过一次 | 本文档 `PolyRenderPolicy.detailZoom()` 的既有结论（第 11 项那次实机回报写的）；`PolyMeshGpuRenderer.renderWorldAfterSolid` javadoc |
| 2 | 镜内那遍会把 `SubmitNodeStorage` **抽干**（`sortInto` 末尾 `clear()`），主画面那遍因此拿不到节点 —— 本仓为此专门取消那次 `clear()`。**若每一遍 render 都重新提交，这个 bug 与这个 mixin 都不会存在** | `SimpleFeatureRenderPhaseMixin` / `TranslucentFeatureRenderPhaseMixin` 头注释（字节码实读）；PR#82 帧率衰减调查 §4.5 |
| 3 | 26.1.2 分支**没有**这两个 mixin（`git ls-tree` 核对）—— 正是因为它每遍各自提交，不存在「被抽干」问题 | `origin/arena/01a05db3-…` 的 `mixin/client/` 文件清单 |
| 4 | `LevelRenderer#render` 的签名吃的是 `CameraRenderState`（提取产物）+ `DeltaTracker`，`SubmitNodeStorage` 是 `LevelRenderer` 上的**字段**（由 extract 填、由每遍 render 抽） | `GameRendererMixin` 的注入 target 字符串；`LevelRendererAccessor` |
| 5 | 26.2 的 GUI 内嵌 3D（背包人偶/枪匠桌）submit 落在 **Screen 的 extract 窗口**（Fabric API 26.2 把 render 事件改名 extract）—— 模型提交属于 extract 阶段 | `ScreenRenderTracker` 头注释 |
| 6 | 帧内顺序：`Minecraft#runTick` extract(441) → render(520)；二次渲染那一遍注入在 `renderLevel` 里 `LevelRenderer#render` 调用的 BEFORE ⇒ **在 extract 之后** | `GameRendererMixin.tacz$beginScopeFrame` / `tacz$renderScopePipView` |

⇒ 本线上那条镜内闸门**在正常流程里不可达**（它自己的注释早就这么写着），
删掉换不到任何东西；而照抄「镜内画完清表」的那半会让主画面那遍拿到**空表**
—— 开着 `ScopePipRerender` 开镜时**镜外那遍拿到空表：世界 mesh 枪的 poly 层
整层不画、只剩立方体**（正是原报告那个观感，只是换到了镜外），
比原报告严重得多。**故不移植行为改动。**

**本轮实际落地的（观测点，非行为改动）：**

- `renderWorldAfterSolid`：镜内那遍首次画上世界表时 log-once
  `GPU world mesh pass active inside the scope PIP re-render pass: drew N world
  entries …` —— 把「镜内到底有没有走 GPU 烘焙」从**靠帧率反推**变成日志事实；
- `shouldSubmitGpuWorld`：镜内分支加 log-once 哨兵。它一旦被打印出来，就说明
  「提交每帧一次」这条本线前提被打破（MC 后续把提交挪回 render 阶段，或某个 mod
  在镜内那遍里重新渲染实体），**那时**姊妹线那条修复才适用于本线；
- `drawList` 的首画日志改为报**真实表名**（此前无论画哪张表都写 "on hand pass"），
  并让世界表在自定义 pass 上的首画单独记一次（原来会被更早的手部首画吃掉）。

**本线若仍复现「镜内是立方体」，按这个顺序查**（都不是二次渲染的锅）：
先看上面那条 log-once 有没有出现 —— 出现了就说明镜内在走 GPU 烘焙，问题在
**提交侧**（第 11 项的开镜距离补偿是否生效：`MeshMaxRenderDistance` 48 /
`MeshWorldFullDetailDistance` 16 / `MeshWorldMaxVertices`），或烘焙额度/LRU
（`MeshGpuBakeBudgetPerFrame` 默认 4、`MeshGpuLightCacheSize` 默认 4 档，
病理场景下逐帧收敛需要几帧）；没出现则是消费侧（看有没有
`GPU world mesh pass failed` 把世界闸整局关掉）。

**证据级别**：静态读码 + 本仓既有字节码取证 + 26.1.2/1.21.11 的实机回报；
**本线实机未验**（沙箱无 java/JDK 也无 MC 依赖源，编译走 CI 闭环）。

### 5.2-ter 下游 1.21.11 分支审查（A1-A10）处置记录（2026-08-31）

下游分支对本仓 587763c 做了 10 条静态审查。逐条核实后处置如下
（**A7 所指的「世界表挂错入口」在其审查基线 587763c 上属实，
b4cb497 已修**——审查与修复赛跑，基线早于修复）：

| # | 判定 | 处置 |
|---|---|---|
| A1 输出目标不看 override | 采纳（防御性） | 手部消费点带 override 跳过+清表；世界消费点跳过**不清表**；26.2 字节码：vanilla 只在 addAlwaysOnTopPass 设 override，防的是 mod |
| A2 总闸+回写配置 | **采纳全部三点** | 分表禁用（world 独立标志）、不再回写 `GPU_BAKING`、镜内 catch 误清世界表一并消除 |
| A3 漏接 LinkageError | 采纳 | 两处 catch 均改 `Exception \| LinkageError` |
| A4 世界表被归入 HAND | **误读，已核实驳回** | Iris 26.2 `IrisPipelines`：ENTITY_CUTOUT 映射到 `getCutout(p)` 逐 draw 动态判 `HandRenderer.isActive()`；且 `assignPipeline` 对静态表已有的管线抛 "already assigned"（我们当成功吞掉= no-op）。世界 pass 时 HandRenderer 非活跃 ⇒ 必落 ENTITIES_CUTOUT_DIFFUSE。证据链写进 drawWorldListViaRenderType javadoc |
| A5 格式硬编码 | 部分采纳 | 加 `ENTITY.getVertexSize()` 逐帧哨兵（stride 变即整代失效）；全参数化在 26.2 收益低不做 |
| A6 额度/容量一钮两用 | 采纳 | 新 `MeshGpuBakeBudgetPerFrame`（1..64，默认 4），额度与 LRU 容量解耦 |
| A7 handModelView 误导 | 部分误读+采纳 | 「挂错入口」指 587763c，b4cb497 已修（executeSolid RETURN，栈顶=viewRotation）；变量改名 `drawModelView`，前置条件写成两 pass 对照注释 |
| A8 静默降级 | 采纳（轻量） | 烘焙额度耗尽补一次性 INFO |
| A9 布尔标志跨帧残留 | **不采纳** | 26.2 `Minecraft#runTick` 的 extract(441)→render(520) 是无条件顺序（字节码），beginFrame 必然逐帧先行；帧号机制为不存在的前提付复杂度 |
| A10 镜像绕序+法线 | **部分采纳（绕序默认关）** | PolyMesh 重写落地，但「镜像时反转绕序」**已被姊妹 1.21.11 分支实机否证**：collector 走 entityCutout（剔背面），反转后被剔掉的是朝外的面 ⇒ 整枪近乎全黑（同包同光影拿 Forge 原版对照，「关着才对」）。真实枪包的绕序本就与镜像自洽。留下的实改：退化面不写零法线（防 NaN 随机高光）+ 三开关 `MeshPolyMirrorReverseWinding`(**false**)/`MeshPolyInvertNormals`(false)/`MeshPolyPreferPackNormals`(false)，构造期读取、资源重载生效 |
| A10 续 `_illuminated` 天空光 | **采纳机制，默认关** | 新 `IlluminatedRealSky`（`RenderConfig`，**默认 false**）：光影下 block=15、sky=环境真值；立方体层（BedrockPart）与 poly 层（collector+GPU 烘焙）走同一个 `IlluminatedLights.resolve()`，两半一致。默认关的依据：姊妹分支 A/B 实测把「继承日月亮度」症状追到了别的根因（它们的自开 GPU pass），本开关在那边没起效 —— 保留为诊断项，等有人复现「开了就好」再考虑默认开 |

全部新配置已接 Cloth Config 界面 + 双语言键。以上均为静态验证（编译级），
光影下的实机验证并入 §5.2-bis 第 6 项。

### 5.3 已知边界（如实）

- 第一人称与世界语境的 O(顶点) CPU 成本均已由 GPU 烘焙消除（世界侧待实测）;
  collector 仅剩三类场景：GUI/Screen 预览、translucent 骨骼、GPU 失败回退。
- PIP 二次渲染（`ScopePipRerender=true`）时镜内那遍的 poly 提交已跳过
  （纯白付的成本，镜内孔径本就不该有枪件）。
- 在两个不同光影包之间直接切换（不经过关闭状态）不触发烘焙世代失效；
  理论上格式补丁不变、无需重烘，若实测出现拉伸请回报（把包名变化也挂进检测即可）。
