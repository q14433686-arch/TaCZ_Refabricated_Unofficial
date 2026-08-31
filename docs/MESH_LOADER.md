# 内置 TacZ Mesh Loader [TML] —— 26.1.2 移植（第 0/1/2/3 步整线）

> 代码移植自 [VellEagle/TacZMeshLoader](https://github.com/VellEagle/TacZMeshLoader)
> `1.21.1_fabric`（GPL-3.0），货源是本仓库 1211 分支的 R3 定稿树
>（`arena/01a05759` @ `ab11a84`，含该分支对上游的全部 R3 修正），
> 按 [`SYNC_GUIDE_1211_TO_2612_TML_GPU_20260831.md`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/blob/7b690be/docs/lineage/SYNC_GUIDE_1211_TO_2612_TML_GPU_20260831.md)
>（1211 树内 `docs/lineage/`）整线搬运。
>
> **状态（2026-09-01，如实声明）：**
> - **编译级完成**：CI `compile-check` 在 `2f07054` 上 BUILD SUCCESSFUL
>   （run 33414214423）。
> - **齐平自查通过**：`python3 docs/check_mesh_config_parity.py`
>   → 18 TOML ↔ 18 Cloth ↔ 36 语言键，键/字段绑定/默认值/区间/en·zh 全对齐。
> - **运行期行为全部未验证**：本分支没有实机环境，§5 的每一条都按
>   「待实机」对待；本文不写任何 PASS。
>
> 1211 侧的设计论证与字节码取证链随本仓一并带入（见文末「背景文档」），
> 其中针对 1.21.11 的注入点结论**不可**直接搬到 26.1.2 —— 26.1.2 的注入点
> 已重新取证，见
> [`TML_GPU_PORT_2612_20260901.md`](TML_GPU_PORT_2612_20260901.md)。

## 0. 这是什么

`poly_mesh` 内置加载器：枪包 geo.json 的骨骼可以带 `poly_mesh` 数组，
按骨骼本地坐标解析成静态网格并延迟提交渲染。三层能力：

1. **collector 安全子集**（第 0 步）：`SubmitNodeCollector.submitCustomGeometry`
   延迟提交，submit 当刻冻结骨骼矩阵快照；geo 解析缓存；顶点预算闸门；
   弹匣双通道（`IMirrorGeometry` + `additional_magazine` 补画）；半透明骨骼拆分。
2. **GPU 静态烘焙**（第 1/2 步，第一人称手部）：顶点留在骨骼本地坐标、
   光照量化后烘进顶点，常驻 VBO；每帧只上传 O(骨骼) 个矩阵；
   绘制点在「手部 geometry flush 之后」（见 §2）。
3. **世界语境 GPU**（第 3 步）：第三人称手持 / 掉落物 / 展示框 / 雕像。
   提交侧闸门逐个拒收 GUI / Screen 提取 / 镜内 / 阴影 / 手部语境；
   按量化光照档做 LRU 缓存 + 每帧烘焙额度；失败半径分表（世界挂了不影响手部）。

## 1. 配置（`tacz-client.toml` 的 `[mesh_loader]`，18 项）

| 键 | 默认 | 说明 |
|---|---|---|
| `MeshEnable` | `true` | 总开关；关掉 = 行为等价于没装 |
| `MeshPolyMirrorReverseWinding` | `false` | 镜像时反转绕序。**保持关**（1211 实机否证：开了在剔面的路径上整枪变黑；26.1.2 上消费路径不剔面，见 §4） |
| `MeshPolyInvertNormals` | `false` | 全局取反烘焙法线 |
| `MeshPolyPreferPackNormals` | `false` | 用枪包逐顶点法线（平滑着色）；需模型重载（F3+T）生效 |
| `MeshPolyIlluminatedRealSky` | `false` | 光影下 `_illuminated` 骨骼的 sky 用环境真值（block 仍 15） |
| `MeshPolyInShadow` | `false` | 阴影 pass 是否画 poly（独立需求，别和 GPU 混） |
| `MeshMaxRenderDistance` | `48.0`（0..1e6） | 世界语境 poly 最大距离，0=无限 |
| `MeshPolyInPreview` | `true` | GUI/FIXED/HEAD 预览语境是否画 |
| `MeshLogStats` | `true` | 模型加载统计日志（按 geo 去重） |
| `MeshGpuBaking` | `true` | GPU 静态烘焙总闸；失败自动回 collector |
| `MeshGpuUnderShaders` | **`false`** | 光影下手部也走常驻 VBO（§3 的照明语义问题） |
| `MeshGpuWorld` | `true` | 世界语境也走常驻 VBO |
| `MeshGpuWorldUnderShaders` | **`false`** | 光影下世界也走常驻 VBO（§3） |
| `MeshGpuLightCacheSize` | `4`（1..16） | 世界 LRU 的光照档容量 |
| `MeshGuiMaxVertices` | `65536`（0..1e7） | GUI 预览顶点预算，超出只画立方体 |
| `MeshWorldMaxVertices` | `120000`（0..1e7） | 世界语境顶点预算 |
| `MeshWorldFullDetailDistance` | `16.0`（0..1024） | 近距全模豁免距离，0=关 |
| `MeshMaxModelVertices` | `120000`（0..1e7） | 单模型顶点告警线 |

18 项全部接进 Cloth 局内面板（Render 配置页），三方齐平由
`docs/check_mesh_config_parity.py` 把关。**两个光影下 GPU 键的默认 false 是
1211 维护者实机结论，移植时特意原样保留**（见 §3）。

## 2. 26.1.2 的消费点（与 1211 的纪元差异，全部字节码取证）

完整证据表见 [`TML_GPU_PORT_2612_20260901.md`](TML_GPU_PORT_2612_20260901.md)。
要点：

- **手部**：`ItemInHandRenderer#renderHandsWithItems` 尾部自己就是
  `renderAllFeatures()` + `endBatch()`（@281/@294），GPU 手部表在该方法
  RETURN 处消费（`ItemInHandRendererMixin`，`require=0`）。
  Iris 26.1（`MixinItemInHandRenderer`）只是把那两个调用换成
  `HandRenderer#endRender()`、并从同一个 `renderHandsWithItems` 进来 ——
  注入点不变，天然落在 Iris 手部阶段内。
- **世界**：26.1.2 的世界 feature flush 在 `GameRenderer#renderLevel` 尾部
  （@570），不在 `LevelRenderer` 里。世界表在
  `FeatureRenderDispatcher#renderAllFeatures` 的 RETURN 处消费
  （`FeatureRenderDispatcherMixin`，`require=0`），消费窗口由
  `GameRendererMixin` 对 `LevelRenderer.renderLevel` 调用的
  `@Redirect` + try/finally 圈定（`setLevelRenderActive`）。
  `renderItemInHand` 开头 @9-@22 的「清遗留几何」预 flush 与四个 GUI 调用点
  会被 `inHandPass` / `levelRenderActive` / `ScreenRenderTracker` 门正确拒收。
- **顶点格式**：26.1.2 常量是 `DefaultVertexFormat.ENTITY`（无 `NEW_ENTITY`）。
  Iris 26.1 的 `MixinRenderPipeline#iris$change` 在光影 + level 渲染期间把
  `ENTITY` 换成 `IrisVertexFormats.ENTITY`（源码核实）⇒ 烘焙格式必须
  「问绘制端当刻的 getter」+ 世代号同时认「光影开关翻转」与「消费格式变化」
  —— 两条机制都从 1211 原样保留。
- **管线定义**：Builder 没有 `withDepthTestFunction/withDepthWrite/withColorWrite`；
  两条自建管线用聚合形态
  `withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
  + withColorTargetState(new ColorTargetState(Optional.empty(), WRITE_COLOR))`。
- **lightmap**：`LightTexture` 类消失；`GameRenderer#lightmap()` 直接给
  `GpuTextureView`；`pack(II)I` 不存在 ⇒ 按消费端公式内联
  `packLight(block, sky) = (block<<4) | (sky<<20)`（`0xF000F0` 可复算）。
- **Screen 闸门**：fabric-screen-api 0.155.2 移除了逐 Screen 的
  `beforeRender/afterRender`，同一事件改名为
  `beforeExtract/afterExtract`（`ScreenRenderTracker` 已适配；该窗口正是
  GUI 内嵌 3D 提交的提取阶段，语义不变）。
- **scope PIP**：26.1.2 无镜内二次渲染 ⇒ `isInsideScopeLevelRender()`
  暂为常 false 门（代码里两处调用点按原形保留，PIP 深度线移植时恢复）。

## 3. 光影下的两条键为什么默认 false（1211 实机结论，原样保留）

1211 维护者实机：开着 `MeshGpuUnderShaders` / `MeshGpuWorldUnderShaders` 时，
**高模枪挡住太阳/月亮的那部分几何会「继承」天体的自发光亮度**（第一人称 /
第三人称 / 展示台三种语境一致），只有把这两键关掉才消失 ⇒ 光影下的常驻 VBO
路径与光影包的照明语义还不等价。连带修掉的缺陷（已随移植保留）：以前
「拿不到 lightmap」会一次性闩锁、把整条路永久退化到 EMISSIVE 管线（在光影包
眼里 = 自发光、不受阴影）；现在是每帧重试 + 光影下真取不到就**整条拒收**
回 collector（`gpuMasterUsable()` + `worldSubmitBlocker()` 原因串）。

26.1.2 上这两键的对应实测**还没做**（§5.3/§5.4 复测矩阵的第一优先项）。

## 4. poly 绕序 × 背面剔除（指导 §1.6 的 Q8/Q9/Q10 —— 本仓的回答）

- **Q8（静态可证）**：26.1.2 上 poly collector 路径用的
  `RenderTypes.entityCutout(texture)` 底层是 `RenderPipelines.ENTITY_CUTOUT`，
  字节码实证该管线显式 **`.withCull(false)`**（与其成对的
  `ENTITY_CUTOUT_CULL` 不写 `withCull`，吃 snippet 默认剔面）。
  ⇒ **消费层不剔背面**：1211 那套「镜像位置但不反转绕序 ⇒ 被剔掉朝外面 ⇒
  整枪变黑」的解释在 26.1.2 的 collector 路径**不成立**；不自洽数据在这里
  只会以「高光偏一侧」的更轻形态出现。自建 GPU 管线两条都 `withCull(false)`
  （与 1211 相同）。
- **Q9（待样本）**：枪包 `poly_mesh` 的绕序约定（从外看 CCW 还是 CW）
  需要真实枪包算「面叉积 ×（面中心 − 质心）」点积统计；本仓不带枪包样本，
  **未测**。方法与判据照指导 §1.6 原文。
- **Q10（本仓的决定）**：选 **③ 维持与上游一致、只记录不修**。
  理由：(a) 消费层不剔面 ⇒ 现症上限本来就低；(b) `MeshPolyMirrorReverseWinding`
  在 1211 被实机否证（默认关已随移植保留）；(c) 任何「顺手修」都要先有 Q9
  的样本证据。复现/对照步骤照指导 §1.6 四步（第 ④ 步「绕枪看有无镂空」在
  26.1.2 上按 Q8 的静态结论预期为「无镂空」——这条也是待实机）。

## 5. 待实机复测矩阵（全部未验证；按步走，逐步勾）

### 5.1 第 0 步（collector 安全子集）
- [ ] mesh 枪包在背包 / 手持 / 掉落物 / 展示框都画得出来；
- [ ] 高面数枪按预算降级（日志有降级行）；
- [ ] 关 `MeshEnable` 行为等价于没装；
- [ ] `tacz.mesh.mixins.json` 4 条全应用（启动日志无 `Invalid mixin`）。

### 5.2 第 1 步（无光影第一人称常驻 VBO，`MeshGpuBaking=true`）
- [ ] 第一人称枪与 collector 路径逐像素一致（同光照档 / 同缩放 / 同俯仰摆动）；
- [ ] 换弹 / 开火 / 检视连打：无双影、无残影；
- [ ] `GPU baked … bones` 只出现一次，之后每帧无日志；
- [ ] F3+T ×5：显存不单调增长。

### 5.3 第 2 步（光影，`MeshGpuUnderShaders=true`）
- [ ] 光影下位置/朝向随相机正确变化，转身不漂；
- [ ] 明暗变化照明跟着变；`Assigned mesh_entity_hand to the Iris HAND program.` 只出现一次；
- [ ] 对着天空/太阳/月亮转视角：枪身挡住天体的部分**不得**跟着亮（§3 的现象；
      若出现，先查 `lightmap view is unavailable` 那行 INFO）；
- [ ] Iris 卸载 / 换光影包 / F3+T：只回 collector，不崩不黑屏。

### 5.4 第 3 步（世界语境，`MeshGpuWorld=true`）
- [ ] **他人手持的 mesh 枪随相机正确移动**（「钉在视角方向 / 转身漂」= 变换取自
      错误时刻，出现即报，不要挪烘焙时机绕过）；
- [ ] 近处高模枪不因预算整把消失；日志出现 `GPU world-baked N bones …`；
- [ ] 光照边界上一排掉落枪：`GPU world-baked` 只在前两次是 info 级；
- [ ] 开背包 / 枪匠台 / 热栏：世界里不多画、GUI 内不少画；
- [ ] 光影组合（`MeshGpuWorldUnderShaders=true`）：
      `Assigned mesh_entity_world to the Iris ENTITIES program.` + 夜晚变暗、
      进照明块变亮 + §5.3 的「挡天体不继承自发光」；
- [ ] 任一组合没生效时先查 `GPU world submit refused: <原因>` 行。

### 5.5 收尾
- [ ] 本文件状态块改写成实机结论（没验的写「待实机」）；
- [ ] `python3 docs/check_mesh_config_parity.py` 保持 0 退出；
- [ ] 把 §8 回传表（见 TML_GPU_PORT_2612_20260901.md）回给 1211 侧，
      并在协调分支 `HANDOFF_LEDGER.md` 记 DONE(<sha>)。

## 6. 设计不变量（从 1211 逐字继承，改前先读）

1. `require=0` + 安全回退：注入失败的后果是「走 collector」，绝不是少画或崩。
2. 存活证明用**帧号比对**（钩子本帧/上一帧真跑过才允许跳过 collector），
   不用「本帧标志 + 帧首复位」。
3. 变换取自**消费时刻**：顶点烘骨骼本地 pose，绘制时乘当刻
   `RenderSystem.getModelViewMatrix()`（26.1.2 的 `RenderType#draw` 同样是
   绘制时现取——字节码实证）。
4. 烘焙不绑瞬间：世界路径按量化光照档 LRU（`MeshGpuLightCacheSize`）+
   每帧额度（`tryReserveBake`）+ 延迟释放池（`beginFrame` 才 close）；
   世代号同时认光影翻转与格式变化。额度与容量是两个旋钮，别合并。
5. 失败半径 = 一张表：世界 30 次连败只关世界；`catch (Exception | LinkageError)`；
   **绝不**在渲染路径里写配置。
6. 光影下兜底不得换照明语义：lightmap 取不到 ⇒ 每帧重试 + 光影下整条拒收；
   新增 shader define 前先问「包会因此少算什么」（EMISSIVE 的教训见 §3）。

边界（别当 bug 修）：半透明部件与弹匣永远走 collector；镜内那遍画但不清表
（26.1.2 暂无镜内遍，门保留）；GUI/预览/阴影在提交侧拒收。

## 7. 本仓明确不做的事

- 不做 `Lightmap` 自定义烘焙/纹理拷贝（走 `RenderSystem.bindDefaultUniforms`
  + 现有光照贴图）。
- 不把半透明部件、弹匣搬进常驻 VBO。
- 不在世界路径用 `IrisProgram.HAND`（手部专项，世界用会串）。
- 不把手部与世界两张表合成一张（两个消费时刻的渲染状态不同）。
- 不在渲染路径写配置文件。
- `MeshPolyInShadow` 保持 false。
- 不「顺手修」绕序（见 §4 Q10）。

## 8. 背景文档（1211/26.2 侧证据链，随本仓带入；其中的 1.21.11 注入点结论
已被本仓 `TML_GPU_PORT_2612_20260901.md` 的 26.1.2 取证取代）

- [`TML_GPU_FEASIBILITY_1211_20260831.md`](TML_GPU_FEASIBILITY_1211_20260831.md)
  —— 可行性论证与分步计划（为什么这样设计）。
- [`TML_GPU_STEP2_HANDFLUSH_20260831.md`](TML_GPU_STEP2_HANDFLUSH_20260831.md)
  —— 手部/世界两条路的 1.21.11 字节码取证链。
- [`TML_GPU_PROBE_TOOL_20260831.md`](TML_GPU_PROBE_TOOL_20260831.md)
  —— 1211 侧 CI 探针用法。26.1.2 未混淆 ⇒ 本仓直接读本地 merged jar 取证，
  探针流程备而不用。
- [`REVIEW_UPSTREAM_TML_GPU_262_20260831.md`](REVIEW_UPSTREAM_TML_GPU_262_20260831.md)
  —— 对 26.2 分支同款实现的审查记录（A2/A4/A6/A9/A10 与本实现的不变量一一对应）。
