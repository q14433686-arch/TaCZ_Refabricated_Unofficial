# 上游（refab 26.2 / `arena/01a04e96`）TML GPU 实现审查

> 审查对象：`arena/01a04e96-tacz-refabricated-unofficial` @ `587763c`（该分支 HEAD）。
> 方式：**只读静态审查** —— 读的是 `git show 587763c:<path>`，本仓不碰那条分支。
> 因此下面所有结论都只到「代码/注释/字节码论证层面」，**没有任何一条声称已在上游实机验证**；
> 上游的**手部路径已实机 PASS**（维护者报告），本文不质疑那条。
> 行号都是那份文件里的行号（`PolyMeshGpuRenderer.java` 共 693 行）。

## 0. 摘要

| # | 位置（上游） | 严重度 | 一句话 |
|---|---|---|---|
| A1 | `drawList` :586 | 高 | 输出目标硬绑 `mainRenderTarget()`，全文件 0 处渲染目标覆盖检查 |
| A2 | :428-429 / :465-466 / :489-490 | 高 | 一次绘制异常 ⇒ 关**总闸** + **回写配置文件**（渲染线程里） |
| A3 | :425 / :463 / :487 / :670 | 中高 | `catch (Exception)` 不接 `LinkageError`，跨版本 `NoSuchMethodError` 会直穿渲染循环 |
| A4 | :252-268 + :521-541 | 中 | 光影下世界表复用**已归入 Iris HAND 程序**的 `ENTITY_CUTOUT` 管线 |
| A5 | :97 / :113 / :295 | 中 | 烘焙格式硬编码 `DefaultVertexFormat.ENTITY`，只有世代号一层保护 |
| A6 | :373 | 中 | 每帧烘焙额度 = `max(4, LRU 容量)`：一个旋钮当两个用 |
| A7 | `drawList` :~600（`handModelView`） | 低-中 | 世界表复用**名字与注释都写着手部**的方法，前置条件没写成断言 |
| A8 | :147 / :341 | 低 | 降级完全静默；`beginFrame` 挂 `GameRenderer#extract` 的跨帧含义没写明 |
| A9 | :174 / :176 | 低 | 每帧标志靠 `beginFrame` 复位，对「钩子与 beginFrame 的相对顺序」敏感 |
| A11 | `config/MeshyConfig.java`（`MeshPolyInShadow` 默认 false）+ `config/PolyRenderPolicy` 的唯一消费点 | ~~中-高~~ **实机否证** | poly 几何确实不进 Iris 阴影图，但打开这个键**不改变**「枪身盖住天体那块被点亮」⇒ 只作排除记录，不改默认值 |
| A12 | `PolyMeshGpuRenderer`（`resolveLightmap` / `EMISSIVE_PIPELINE` / `assignPipeline`） | **高（仅光影下）** | lightmap 视图取不到一次 ⇒ 整会话固定在带 `withShaderDefine("EMISSIVE")` 的管线上，并被登记进 Iris 的 HAND/ENTITIES 程序 ⇒ 包按「自发光 / 不查阴影」画，产生「挡住天体却继承天体亮度」；本仓已去闩锁 + 光影下拒收 |
| A10 | `core/PolyMesh.java` :31-35 + :67-99 | **高（仅光影下显形）** | `poly_mesh` 位置按 Y 轴镜像但绕序从不跟着反转 ⇒ 烘焙法线与 `gl_FrontFacing` 相互矛盾；另 `FORCE_FLAT_SHADING` 恒 true ⇒ 枪包的 `normals` 从不消费 |

**先说好的**（本仓第 3 步直接吸收，已在 `MESH_LOADER.md` 致谢）：按量化光照档做 LRU、被逐出
VBO 进延迟释放池下一帧才 close、每帧烘焙额度防「逐出-重烘打摆」这三件事是上游先做的，
思路正确，本仓只是把旋钮解耦并把失效判定做得更早。

---

## A1（高）输出目标：硬绑 `mainRenderTarget()`，且不看渲染目标覆盖

```java
RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();          // :586
GpuTextureView colorView = mainTarget.getColorTextureView();           // :590
...encoder.createRenderPass(() -> "tacz_mesh_gpu", colorView, ...)     // :618
```

`drawList` 是**两张表共用**的（世界路径在 :424 / :457 调它）。全文件搜
`outputColorTextureOverride` / `isBoundFboOverride` / 任何 override —— **0 处**。

为什么这是隐患而不是风格问题：

1. 上游自己在 `FeatureRenderDispatcherMixin` 的注释里记着 **r51 事故**：给某个 RenderType 配了
   不同 outputTarget，于是「主 target → 掩码 target → 主 target」的切换被零散穿插进 solid
   阶段内部，触发 `VK_ERROR_DEVICE_LOST`。硬绑主 target 并在**阶段边界之外**开一个
   `RenderPass`，与那次事故是同一族动作——只是因为我们总在某个 `renderAllFeatures` 的收尾处，
   有没有撞上取决于当刻主画面 target 是否就是当前绑定目标。
2. 世界那一次 `renderAllFeatures` 在 26.2/1.21.11 都**不止一个调用点**：1.21.11 实测有三个
   （主通道节点、always-on-top/粒子离屏节点、`ItemInHandRenderer#renderHandsWithItems`），
   其中第二个调用点会 `RenderSystem.setOutputColorTextureOverride(...)`。上游的
   `worldDrawnThisFrame` 只保证「一帧画一次」，不保证「画在正确的那一遍里」——第一次命中的
   就可能是一次带 override 的离屏遍。
3. :414 的注释说「无光影时 `mainRenderTarget()` 已被重定向到 pip target」——那是**镜内那一遍**
   的机制；一旦某个版本/某个 mod 改用 override 而不是真的换掉 `mainRenderTarget()` 对象，
   这里就会画进主画面（镜内该有枪的地方没枪、主画面多出枪）。

本仓的做法（1.21.11 实测过的机制）：世界 flush 里
`if (RenderSystem.outputColorTextureOverride != null) return;`（跳过这一遍，不清表），
并且解析目标时 `outputColorTextureOverride != null ? override : mainRenderTarget()` ——
即**跟着当前绑定走**，而不是跟着「主画面」这个名字走。

> 给上游的最小修法：`drawList` 开头加 override 检查 + 目标取自 override；
> `WORLD_DRAWS` 的消费点加一句「本遍是否是带 override 的那一遍」的门。

## A2（高）异常处理：把总闸和配置文件一起写了

三处 catch 全是同一套（:427-429 镜内、:464-466 世界、:488-490 手部）：

```java
gpuDisabledThisSession = true;
MeshyConfig.GPU_BAKING.set(false);
```

三个问题：

1. **范围过大**：世界 pass 抛异常，把 `GPU_BAKING`（第一人称 + 世界共用的总闸）关掉 —— 已经
   实机 PASS 的手部路径被一条从未验证的世界路径连坐。
2. **渲染线程回写配置**：`set(false)` 会让 ForgeConfig 的 spec 变 dirty；如果该 mod 的
   配置保存策略是即时/周期性 flush，就是渲染线程触发磁盘写（而且**重启后仍是关的**，用户
   看到的是「GPU 烘焙自己关了」，得去 TOML 里找回来）。
3. **一处小 bug**：镜内那处 catch（:425-431）顺手 `WORLD_DRAWS.clear()`，而同一方法里世界
   那条刻意「跳过不清表」（:446-448 的注释解释了为什么不能清）—— 镜内异常时反而把主画面
   那遍要用的条目清了。

本仓的做法（R3 起，两条路一致）：分表禁用（`gpuDisabledThisSession` /
`gpuWorldDisabledThisSession` 各自独立）、
**连续 30 次**才算病理（单次抖动不自关）、`catch (Exception | LinkageError)`、
**从不**回写配置（只改内存标志；`MESH_LOADER.md` 的表格里那句「运行期异常也会自写 false」是
   第 1 步沿用上游的残留，**R3 已把这条从本仓代码里删掉**（手部 catch 只置
   `gpuDisabledThisSession`），文档同步改掉）。

## A3（中高）`catch (Exception)` 漏掉链接错误

渲染路径上最常见的失败模式恰恰是链接类：Iris / Sodium / 别的渲染 mod 升级后方法签名变了 ⇒
`NoSuchMethodError` / `NoClassDefFoundError`，都是 `Error` 不是 `Exception`。
上游的降级网捕不到它，于是「一次跨版本兼容问题」表现为**游戏崩**而不是「回退 collector」。
本仓两条路都是 `catch (Exception | LinkageError)`。

## A4（中）光影下世界表用的是 HAND 程序登记的管线

链路：`useRenderTypeRoute()`（:268）= `isUsingRenderPack() && !GPU_UNDER_SHADERS` —— 也就是
**开着光影且没强开自定义 pass 时的默认路径**，两张表共用。手部那条在绘制前先调
`IrisCompat.assignCommonEntityPipelinesToHandIfNeeded()`（:521-523）把
`ENTITY_CUTOUT` 管线归入 Iris 的 **HAND** program；世界那条（:537-541）刻意不调，理由是

> 世界 pass 里 ENTITY_CUTOUT 就是 vanilla 世界实体在用的管线，Iris 对它的默认接管
> （`gbuffers_entities` 链路）正是我们想要的；这里主动去动管线归属反而可能干扰别的实体。

问题在于「归入 HAND」是**按 pipeline 对象**登记的、不是按 pass 段的：手部那遍每帧都先跑，
等世界那遍用同一个 `RenderTypes.entityCutout(...)` 时，那条 pipeline 已经被归到
`IrisProgram.HAND` 了。于是光影下的世界 mesh 枪拿的是 `gbuffers_hand` 的照明与状态
（不是 `gbuffers_entities`）。这会同时带来两个方向的可疑现象：光影作者写在
`gbuffers_hand` 里的专属逻辑（视手上浮、手部雾/裁剪平面）被套到世界物体上，
而实体专属的阴影/照明（`gbuffers_entities` + shadowmap）拿不到。

**必须实机验证才能定性**（本文只指出与注释自相矛盾的地方）。若确认，修法两条：
① 世界路径不共用 `entityCutout`，改自定义管线 + `IrisApi.assignPipeline(pipeline, ENTITIES)`；
② 或者在世界 pass 绘制前把该 pipeline 归还 ENTITY 程序（但要防「别的 mod 也在用」的副作用）。
本仓走的是 ①（`IrisCompat#assignMeshPipelineToEntity` → `ENTITIES`，常量已按 Iris 1.10.7 jar
用 CI javap 审过：**没有** `ENTITY` 也没有 `MAIN`；`EMISSIVE_ENTITIES` 不能拿来当「全亮」用）。

## A5（中）烘焙格式写死

`bakeBone(...)` 里 `new BufferBuilder(scratch, QUADS, DefaultVertexFormat.ENTITY)`（:295），
而管线侧 `withVertexBinding(0, DefaultVertexFormat.ENTITY)`（:97 / :113）。
光影激活时 Iris 会**扩展实体顶点格式**（附加属性、stride 变化），经 `BufferBuilder` 写出的
常驻 VBO 布局随之不同 —— 上游已经用 `bakeGeneration`（:180-189，逐帧检测光影开关翻转）
兜住了「开关光影必现的模型拉伸」，但格式的**唯一来源**仍是这个硬编码常量：

- 别的 mod 也扩展 `DefaultVertexFormat.ENTITY` 时，世代号不会变（只认光影开关）⇒ 旧 buffer
  按新 stride 解读；
- `GPU_UNDER_SHADERS`（自定义 pass）与光影状态组合出第三种期望格式时同理。

本仓做法：`bakeBone(meshes, lightKey, format)` 把格式做成入参，消费侧 `bakeFormat()`
一变就整代失效（`MESH_LOADER.md` §5.4 记过这条根因），并把「按错 stride 解读 = 模型拉伸」
写成注释留在两处。

## A6（中）一个旋钮当两个用

```java
int cap = Math.max(4, MeshyConfig.GPU_LIGHT_CACHE_SIZE.get());   // :373
```

`GPU_LIGHT_CACHE_SIZE` 同时是「每模型缓存几档光照」（显存语义）和「本帧还能烘几根」（
防打摆额度）的上限，而且额度下限写死 4。后果：想省显存把容量调到 1 的用户，额度仍是 4；
大模型场景想把额度调大，只能连带把 LRU 撑大（白花显存）。本仓：额度走独立入参
`tryReserveBake(cap)`，`MeshGpuLightCacheSize` 只管容量，1..16。

## A7/A8/A9（低到中，一起说）

- **A7** `drawList` 内部变量叫 `handModelView`、注释写「本方法跑在**手部** renderAllFeatures 的
  `executeSolid` 之后……取一次全体通用」，但世界表也调它（:424/:457）。「两层变换定理」
  （顶点里烘 pose、绘制时再乘当刻 MV）在两个 pass 里都成立，成立的是**前提**：
  消费时刻的 MV 必须是这批几何将被比较的那套 MV。前提没写成断言 ⇒ 改手部时静默改到世界。
  建议至少改成 `drawList(draws, Pass pass)`，把「这次是哪个 pass」显式化。
  （本仓在 1.21.11 上就撞到过这条前提的**反例**：`GameRenderer#renderItemInHand` 会在
  `renderHandsWithItems` 前后 push/pop model-view，那一刻的「世界 MV」是错的。上游若把
  世界表挂到与手部同一个入口，症状就是维护者报的「相对视角固定」。本仓因此把世界消费点
  单独放在 `FeatureRenderDispatcher#renderAllFeatures` 的 RETURN，并且 `inHandPass` 时整段跳过。）
- **A8** 降级全静默：门闸拒收不打日志、`beginFrame` 每帧清表 ⇒ 「世界路径怎么没生效」在
  `latest.log` 里一个字都不留。本仓 R3 补了 `worldSubmitBlocker()` + 按原因去重的一行 INFO
  （见 `CHANGELOG_1_21_11.md` R3 段），这个成本很低，值得上游同样加。
- **A9** `drawnThisFrame` / `worldDrawnThisFrame` 是布尔，靠 `beginFrame` 复位。当一帧里
  `GameRenderer#extract` 没跑（暂停、别的 mod 直接调 `LevelRenderer#render`、镜内重渲染那遍
  自己的一轮），标志会跨帧残留。本仓用帧号比对（`lastWorldFlushFrame == frameId || frameId-1`），
  对「钩子与 beginFrame 谁先谁后」不敏感。

## A10（~~高，但只在光影下显形~~ **修法的一半被实机否证**）：`PolyMesh` 的镜像没有反转绕序 + 枪包法线被丢弃

> **2026-08-31 晚更正（本仓自己拿实机推翻的部分）**：下面「镜像 ⇒ 应当反转绕序」这一半**不要照搬**。
> 本仓把它做成默认开的开关跑了一轮，维护者用同枪包、同光影包与 **Forge 原版**做左右对照：开着它 ⇒ 高模枪
> 近乎全黑、高光只剩远侧；关掉 ⇒ 与原版逐字一致。原因是这条路（以及你们那边光影下的世界表）
> 用的 `RenderTypes.entityCutout` **剔背面**：绕序一反转，被剔掉的就是朝外的面。法线那半边推理仍然成立
> （`D·n` 是朝外方向，`gl_FrontFacing` 与它相反），但**结论应当是「别动绕序」**，而不是「反转绕序」。
> 真正值得上游做的是下面第 2 条（丢弃枪包 `normals`）与「别写零法线」这两件；若要彻底闭合正反面，
> 得先决定 `entityCutout` vs `entityCutoutNoCull`（行为改动）或从数据反推绕序，两者都需要实机。
> 本仓现状：`MeshPolyMirrorReverseWinding` 默认 **false**（开关保留，见 `docs/MESH_LOADER.md` §5.7）。


**先说清一件事**：上游与本仓这段是**逐字相同**的（`587763c:…/core/PolyMesh.java` 的
:31-35 常量与 :67-99 的叉积/写回逻辑，与本仓改前一致），所以这一条既是给上游的审查意见，
也是本仓自己的修复来源 —— 不是「上游写歪了我们抄对了」。

- **机制**：位置烘焙成 `p′ = D(p − pivot)`，`D = diag(1, −1, 1)`（`FLIP_MODEL_X=false` /
  `FLIP_MODEL_Y=true`）。单轴镜像 `det(D) < 0` ⇒ 每个面的**正反面互换**（数学上是确定的，
  不依赖光影）。而烘焙进 `bakedN*` 的是「**原始顺序**的叉积 × 翻转符号」= `D·n`，也就是镜像后
  该面的**朝外**法线 —— 方向本身是对的。错在绕序没有配套反转：于是「法线朝外」与
  「`gl_FrontFacing` 说这是背面」同时成立。原版实体程序不读 `va_normal` ⇒ **无光影下这条完全不可见**；
  光影包里 `gbuffers_entities` / `gbuffers_hand` 的常见写法 `normal *= gl_FrontFacing ? 1.0 : -1.0`
  （为双面几何自洽而做）会把这条朝外法线取反 ⇒ 高光/反射出现在错误一侧。
- **对照物就在仓库里**：`com/tacz/guns/client/model/bedrock/BedrockPolygon` 处理 `mirror` 的方式是
  **反转顶点顺序** + 只把被镜像轴的分量取反（`normal.mul(-1, 1, 1)`，前置 `direction.step()`）。
  `poly_mesh` 这条路径缺了前半截。
- **第二条**：`FORCE_FLAT_SHADING` 恒 `true` ⇒ `normals` 数组在 :47 附近解析出来后从不使用，
  曲面（枪管、护木、瞄具外壳）在光影下呈每面一条法线的棱角状高光。平滑与否不该由加载器决定。
- **顺带**：三点共线的退化面叉积长度为 0 ⇒ 写进缓冲的是零向量，光影里 `normalize()` 出 NaN，
  表现是那一面带随机高光。
- **本仓已改（可作为上游直接搬的补丁）**：`PolyMesh` 里把「发射顺序展开（三角形→QUADS 重复第 3 顶点）」
  与「镜像时整体倒序」分开做，法线仍从原始顺序求叉积；退化面退回枪包法线、没有就写确定方向；
  三个行为收到 `MeshyConfig`：`MeshPolyMirrorReverseWinding`(true) / `MeshPolyInvertNormals`(false) /
  `MeshPolyPreferPackNormals`(false)，在构造期读一次（改了要重载资源）。判定矩阵与「哪些只到静态、
  哪些待实机」写在 `docs/MESH_LOADER.md` §5.7（本仓分支）。
- **为什么没有直接在 shader 侧把法线取反**：两条消费路径（常驻 VBO 与 collector）共用同一份
  `bakedN*`，数据层修一次两条都修好；而且 `withCull(false)` 之外还有阴影 pass 等消费者，
  在 shader 侧补偿会把不自洽留在数据里。

**A10 的续集（26.2 那边同一个 `MeshyConfig` 该加的第二项）**：维护者接着报「光影下高模枪遮不住
太阳/月亮，枪身继承天空亮度」。这条不在 `PolyMesh.java` 里，而在**光照值**：`_illuminated` 骨骼被
硬写成 `0xF000F0`（block=15 **且** sky=15）。这数字在 26.2 与本仓的 `PolyMeshModel` /
`BedrockPart#render` 里逐字相同 ⇒ 同样是共享缺陷，只是它属于 TACZ 本体的约定而非 TML 的新增。
无光影下必须两列都拉满（原版光照图是 block 列与 sky 列相乘），但光影包把 sky 读成「这表面看得见天空」
⇒ 常亮被翻译成「太阳月亮永远照得到」。本分支的处理：新增 `MeshPolyIlluminatedRealSky`（**现默认 false** —— 见 A12：它针对的是另一个被误读的症状），
**只在装了光影包时**把 sky 换成环境真值、block 仍 15；只覆盖 poly 层，立方体层刻意没动（影响面是所有
枪包与所有准星点，配置归属应该是 `ClientConfig`）。26.2 若要跟，建议一次性把两层都收进
`ClientConfig` 的一个键，别只改 poly 层 —— 否则一把枪的两半会一个跟天空走、一个不跟。

## A11（~~中-高，只在光影下~~ **已被实机否证，仅保留为排除记录**）：`MeshPolyInShadow=false` 让 poly 表面在阴影图里不存在

> **2026-08-31 维护者实机：把 `MeshPolyInShadow` 打开没有任何变化；关掉光影下的 GPU 两个开关才恢复。**
> 所以这条**不是**「挡住天体那块发亮」的成因，真因见 **A12**。下面整段作为排除记录保留（机制本身在静态上
> 仍然成立 —— poly 确实不进阴影图，只是它不产生那个可见后果；「阴影形状由立方体层承担」也没变）。

维护者报的现象：开光影后，**高模枪挡住太阳/月亮的那部分模型反而继承了天体的自发光亮度**；
其它自发光物品、非 TML 的模型都没这问题。

- **机制**：光影包判断「这个表面晒得到太阳」不看屏幕遮挡，而是拿片元世界位置查**阴影图**
  （Iris 阴影遍渲染出来的 `shadowtex0/1`）。不在阴影图里的表面按构造就是「完全露天」
  ⇒ `sunEmissive`/高光整份打上去 ⇒ 挡住太阳的那块反而发亮。
- **TML 侧为什么会命中**：`MeshPolyInShadow` 的唯一消费点是 `PolyRenderPolicy#shouldRenderPoly`
  （`isRenderShadow() && !POLY_IN_SHADOW ⇒ return false`），默认 false ⇒ 阴影遍里 poly 一个都不提交，
  阴影图里只剩**立方体层**。高模包的意义就是 poly 表面比立方体外壳大且细 ⇒ 超出立方体的那些面
  等于不在阴影图里 ⇒ 「只有高模部分吃太阳光」「非 TML 模型没这问题」两条同时被解释。
  上游这句注释（「立方体已经提供阴影形状」）把「有个影子」和「逐面遮挡」混为一谈了。
- **本仓现状**：**代码不改**，`MeshPolyInShadow` 保持 false；判别矩阵与最终结论在 `docs/MESH_LOADER.md`
  §5.9 / §5.10。已核实开这个键不会与常驻 VBO 叠加（`shouldSubmitGpuWorld` 在阴影遍拒收），所以它
  对无光影用户是彻底 no-op —— 但也**仅此而已**，别把它当修法。
- **对 26.2 的建议**：同一个键、同一个默认值、同一个消费点 ⇒ 你们那边现象应当一模一样。
  先跑判别（都在局内即时生效，不用重启）：① `MeshPolyInShadow=true` 看世界语境（第三人称 / 掉落物 /
  展示框）的枪是否不再吃光；② 若无效，把光影下的 GPU 键关掉（你们那边是总闸/分表）看是否变好
  —— ①有效 ⇒ 阴影图这条成立，建议默认改 true 并把代价写进配置注释；②有效 ⇒ 是我们自建 pass 与
  Iris frame graph 的时序问题，与阴影图无关。
- **边界**：自己的第一人称手部几何**不经过** Iris 的阴影遍（它渲染实体，第一人称手不是实体），
  所以纯第一人称那一半多半修不动，那是包对 `gbuffers_hand` 的 exposure 惯例。

## A12（高，只在光影下）：GPU 路径的 `EMISSIVE` 兜底是一条**一次性永久**降级，而且它会改变照明语义

A11 被否证之后，B（关掉光影下的 GPU 开关）命中，把范围收窄到**我们自己开的那个 pass**。本仓
`PolyMeshGpuRenderer` 里这条链每一环都在源码里，26.2 那边形态一样（同一个 EMISSIVE/LIT 二选一，
lightmap 取不到时同样退化）：

```
resolveLightmap() 取不到 lightmap 视图 -> lightmapUnavailable = true      <- 一次性闩锁，整会话不重试
  -> pipeline = lit ? LIT_PIPELINE : EMISSIVE_PIPELINE                    <- 从此恒 EMISSIVE
  -> EMISSIVE_PIPELINE 带 .withShaderDefine("EMISSIVE")                     <- 关键
  -> if (irisFlush) assignMeshPipelineTo{Entity,Hand}(pipeline)            <- 把**这条**管线登记进包
  -> 包按 #ifdef EMISSIVE 走「自发光 / 不查阴影」分支
  -> 现象：几何盖住天体，自己却「继承」天体亮度；第一/第三人称/展示台一致；只影响 TML 模型
```

- **为什么正好是这个现象**：EMISSIVE 在光影包里就是「这东西自己亮、别给它算遮挡」的旗子（准星点、
  发光方块走的就是它）。打上它之后包不查阴影图、也不把天体光当被遮挡的量 ⇒ 观感即「模型挡住了太阳，
  但挡住的那块和太阳一样亮」。collector 那条走 `RenderTypes.entityCutout`，包按普通实体几何处理，
  所以「其它自发光物品没事」「非 TML 模型没事」两条负控制同时成立 —— 这是本条比 A11 强的地方。
- **触发条件不是「有没有光影」，而是「那一帧 lightmap 视图取不取到」**：
  `mc.gameRenderer.lightTexture().getTextureView()` 在光影（尤其 deferred / 自建光照图）下最容易返回
  null 或抛异常，所以缺陷看起来「只在开光影时出现」，实际是「只在光影让 lightmap 视图取不到时出现」。
  原代码**只要失败过一次就整局固定在 EMISSIVE**，且只 WARN 一次（日志里容易被滚掉）。
- **本仓已改（两条，都不依赖具体光影包）**：① 去闩锁，每帧重试（`getTextureView()` 是缓存读），日志只去重；
  ② 光影下真取不到 lightmap 就**整条拒收**（`gpuMasterUsable()` 加
  `isUsingRenderPack() && !lightmapResolvable()`），退回 collector —— 兜底不该换照明语义，宁可不进 GPU。
  世界路径 `GPU world submit refused:` 补了同一原因串；手/通用那条原本**静默**，现在有一行去重 INFO
  `GPU path refused while a shader pack is active: the level lightmap view is unavailable`（两个标志都在状态
  恢复时复位）。判据表在 `docs/MESH_LOADER.md` §5.10，26.2 若照抄，日志字符串建议原样保留，方便互相回贴。
- **对 26.2 的建议**：
  1. 去掉闩锁；顺手把同类「一次性状态位」都扫一遍（`BONE_BUFFER_CAPACITY` 那族）；
  2. 光影下的两个 GPU 键**保持 false**（本仓 R3 一度翻成 true，同日退回），并把「光影 + GPU」的实机判据
     从「能不能收到 `gbuffers_*` 照明」改成「挡住天体的那块亮不亮」—— 前者过了不代表后者过；
  3. 想彻底收口，判据仍是日志里那行 WARN：出现过 ⇒ 本条就是成因；从没出现过 ⇒ 剩下「自建管线的
     MRT / color target 集合与 `ENTITY_CUTOUT` 不一致」这条**未排除**分支（沙箱里没光影包、没有可反编译的
     Loom jar，本仓同样没排除）。**别把 A12 读成「上游照抄这两条就算修完」。**

## 时序对照（本仓 ↔ 上游）

| 事项 | 上游 26.2 | 本仓 1.21.11 |
|---|---|---|
| 手部消费点 | 手部 `renderAllFeatures` 的 `executeSolid` 之后（`@WrapOperation`） | `HandRenderer#endRender` RETURN（Iris 自己那次 flush **之后**） |
| 世界消费点 | `renderAllFeatures`：非手部、非镜内、非阴影 | `FeatureRenderDispatcher#renderAllFeatures` RETURN，另加 `inHandPass` / `isInsideScopeLevelRender` / `isRenderShadow` / `outputColorTextureOverride` / `levelRenderActive` 五道门 |
| 「一帧一次」实现 | 布尔 + `beginFrame` 复位 | 帧号比对（`consumedFrame`） |
| 光影照明 | 复用 `RenderTypes.entityCutout` + `prepare()`（管线归 HAND，见 A4） | 自建管线 + `IrisApi.assignPipeline(ENTITIES)` |
| 失败半径 | 总闸 + 回写配置 | 分表 + 30 次阈值 + 不回写 |
| 烘焙格式 | 硬编码 ENTITY + 世代号 | 格式入参，格式变即整代失效 |
| 为什么不能直接互相抄 | —— | 1.21.11 **没有** `PreparedFrame#executeSolid` / `RenderType#prepare()` / `drawFromBuffer`（CI javap 核实），26.2 那套压栈取 MV 的机制在这边不存在；反过来本仓的 `HandRenderer` 注入点在 26.2 上也与手部 pass 结构不匹配 |

## 建议给上游的动作（按性价比排序）

1. `drawList` 加渲染目标 override 处理（A1）——改动最小、能同时摘掉一个 `VK_ERROR_DEVICE_LOST`
   类别的风险。
2. 失败处理改成「分表 + 阈值 + 不回写配置」（A2），并 `catch (Exception | LinkageError)`（A3）。
3. 光影下世界表的管线归属做一次实机对表（A4）：同一把掉落枪，分别在 `gbuffers_entities`
   与 `gbuffers_hand` 照明差异明显的包（夜晚/暗巷）下看亮度是否随世界走。
4. 格式入参（A5）与额度/容量解耦（A6）。
5. 静默降级补一行原因日志（A8）——这条本仓已有实现可以直接搬。
6. **A10 续集**（`_illuminated` 的天空光）：推导本身仍成立（`0xF000F0` 的 sky nibble 会被包读成「露天」），
   但**默认值请保持 false** —— 那条是按我对症状的误读写的，维护者报的不是它，B 实验也证明它不影响那个现象。
   键名与三条消费点可以照搬；`BedrockPart#render` 那半仍然没动。
7. ~~A11 默认值 + 注释一起改~~ → **A11 已被实机否证，别翻那个默认值**；该翻的是 **A12**（光影下两个
   GPU 键保持 false + 去掉 EMISSIVE 闩锁）。A11 那句注释可以补一行「阴影形状够用、逐面遮挡不够用，
   但实测不显形」，免得下一个人再走一遍。
8. **A10**（法线/绕序）：**按上面那条更正缩小范围** —— 别把绕序反转做成默认开（本仓已实机否证），
   建议搬的是「消费枪包 `normals`」+「退化面别写零法线」两件，加上把常量 `FORCE_FLAT_SHADING` 变成配置。
   `entityCutout` 的剔除状态在沙箱里仍然核不了（没 Loom jar），但维护者的对照图给出了答案：
   那条路剔背面（`entityCutout` 与 `entityCutoutNoCull` 成对存在，名字差一个 NoCull）。
