# 内置 TacZ Mesh Loader [TML] —— 安全子集 + GPU 烘焙（第 0/1/2/3 步全实机 PASS；R3 曾把四项开关默认全开，光影下那两项同日退回默认关、已复测 PASS，见 §5.10；光影下退回 collector 后显形的绕序开关也已退回 false，见 §5.7）

> 代码移植自 [VellEagle/TacZMeshLoader](https://github.com/VellEagle/TacZMeshLoader)
> `1.21.1_fabric` v0.1.7，GPL-3.0。不是官方 TacZ 附属。
>
> **状态：第 0/1/2 步与第 3 步（世界语境常驻 VBO `MeshGpuWorld`）全部实机 PASS。**
> 第 3 步的两条重点验收项（他人手持的 mesh 枪必须随相机正确移动 = 26.2 分支踩到的那个坑；
> 光影组合 `MeshGpuWorldUnderShaders=true`）由维护者 2026-08-31 **一遍过**。
> 因此 R3 起四个 GPU 开关（`MeshGpuBaking` / `MeshGpuWorld` / `MeshGpuUnderShaders` /
> `MeshGpuWorldUnderShaders`）默认全开 —— 但**同一天傍晚，光影下那两项又退回默认关**：
> 维护者实机发现「高模枪挡住太阳/月亮的那部分几何会继承天体的自发光亮度」，只有把它们关掉才消失
> ⇒ 光影下的常驻 VBO 路径与光影包的照明语义**还不等价**（判别过程 §5.9，结论与已修的连带缺陷 §5.10）。
> 无光影那两条（`MeshGpuBaking` / `MeshGpuWorld`）保持默认开。每一项仍保留
> 「钩子失联/异常 ⇒ 静默回退 collector」，并且被拒时按原因去重打一行 INFO（`GPU world submit refused: …`）。**
> 按 AGENTS.md §2：以上实机 PASS 均为**维护者 2026-08-31 报告**（换弹无双影、光影下常驻 VBO
> 收 `gbuffers_hand` 照明、世界语境含光影一遍过）；本文不替他们补任何未回报条目的结论 ——
> §5.5 / §5.6 仍是逐条清单，未回报的条目按「未验证」对待。
>
> 可行性论证与分步计划见
> [`TML_GPU_FEASIBILITY_1211_20260831.md`](TML_GPU_FEASIBILITY_1211_20260831.md)。
> 分步：第 0 步 collector 安全子集 → 第 1 步无光影手部 GPU → 第 2 步 v2 光影手部 GPU →
> 第 3 步世界语境 GPU（本轮）。

## 0. 与四个关闭 PR 的关系（为什么这是第五次、以及为什么这次砍掉了 GPU）

| 版本 | 结局 | 教训（本轮如何处置） |
|---|---|---|
| PR #33 | 关 | GPU 画在世界 pass + 不可信矩阵 + `visitBones` skip 剪子树 → **第 1 步 GPU 表只收手部 pass（`renderItemInHand` HEAD/RETURN 门禁），不认 `firstPerson()` 上下文** |
| PR #69 | 关 | 光影一开整条回退 CPU；声称做了的代码没做 → 第 1 步如实：无光影走 GPU、光影回退 collector（`GPU_UNDER_SHADERS` 仅诊断强开） |
| PR #70 | 关 | 全局 WORLD_DRAWS 表泄漏进世界 pass；弹匣没接 `IMirrorGeometry` → GPU 表只有 `HAND_DRAWS` 且仅手部消费；弹匣链路照搬已修正的架构（见 §2） |
| PR #71/#72 | 关 | 架构收敛但被要求从干净基线重做 → 本轮**逐文件对照 1.21.11 HEAD 重新落地**，只保留三轮教训打磨过的安全子集 |

维护者关闭 #72 的意见是「不应以重做名义复用已关闭的分支」。本轮的处理方式：
不 cherry-pick、不合并任何关闭分支；以关闭分支为**参考资料**逐文件审计后在
当前 HEAD（1.21.11 工作树）上重写落地，每个 mixin 注入点、每个反射字段名都
对照当前 HEAD 源码逐一核实过（模型类位置已按 1.21.11 实际结构改写：
`BedrockGunModel` / `BedrockAttachmentModel` / `BedrockAmmoModel` 在
`com.tacz.guns.client.model` 而非 26.2 的 `client.model.bedrock`）。

## 1. 本轮包含什么 / 不包含什么

### 包含（安全子集）

- **poly_mesh 解析与渲染**：枪 / 配件 / 弹药（物品、掉落实体、抛壳）/ 方块，
  全部走 `SubmitNodeCollector.submitCustomGeometry` 延迟提交路径，
  submit 当刻冻结骨骼矩阵快照（与 `BedrockRenderSnapshot` 同一理由）。
- **geo JSON 解析缓存**（修复「每枪 geo JSON 双遍解析」）：按 geo 路径缓存
  共享网格数据，资源重载时整体失效；统计日志按 geo 去重。
- **顶点预算闸门**：GUI/FIXED/HEAD 超 `MeshGuiMaxVertices` 只画立方体；
  第三人称/掉落物/展示框超 `MeshWorldMaxVertices` 同理；另有距离闸门与
  近距全模豁免。
- **弹匣双通道**：主遍历 exclude `additional_magazine` 子树；立方体弹匣走
  `IMirrorGeometry`；poly 弹匣在 `additional_magazine.visible` 时按该节点
  变换补画（与上游 TML `renderSubtreeDirect` 同构）。
- **半透明拆分**：骨骼名含 `translucent` 的骨骼单独走 `entityTranslucent`
  提交（排序混合），其余走 `entityCutout`。
- **阴影 pass 默认跳过 poly**（`MeshPolyInShadow=false`）：立方体已提供影子形状 —— 严格说只保证
  「有个影子」，不保证逐面遮挡。2026-08-31 曾把「高模吃天体光」怀疑到这里，**打开后实测无效**（§5.9）。
- **状态追踪基建**（纯 CPU，第 1 步 GPU 路径的地基）：
  - `ScreenRenderTracker`：用 Fabric `ScreenEvents` 精确检测「正在画 GUI screen
    的瞬间」（而非「菜单开着」），避免菜单开着时世界内无关渲染被误伤；
  - `ShaderStateTracker`：用 `RenderTickEvent`（START 相位）检测 Iris 光影包
    开关翻转，弱引用失效全部已注册模型的 VBO 缓存。
- **加载告警**：超 `MeshMaxModelVertices` 的模型加载时警告枪包作者。

### 第 1 步新增（无光影第一人称 GPU 静态烘焙，见可行性文档 §6.1）

- **`PolyMeshGpuRenderer`**：逐骨骼常驻 VBO（顶点留在骨骼本地系、光按 4 级
  量化烘进 UV2），每帧只上传 O(骨骼) 个 `DynamicTransforms`，在
  自定义 `RenderPass` 画，`GPU mesh pass drew N bones` 日志即验收锚点。
  （**第 1 步**当时挂在 `GameRenderer#renderItemInHand` 的 RETURN —— 那个时机在光影下
  与 Iris 自己的手部 flush 不一致，第 2 步 v2 才把绘制点搬进 `ItemInHandRendererMixin`
  的 flush 钩子里；GUI 语境在两步里都始终走 collector，世界/第三人称/掉落物自第 3 步起进
  常驻 VBO。）
- **光照分档缓存**：`ensureBaked` 4 级 quantize + 1s 节流；illuminated 骨骼恒烘
  `FULL_BRIGHT`；光影包开关翻转 bump 烘焙世代号 → 持缓存的模型立即重烘
  （26.2 `9f7412e` 的修法，绕开 1s 节流）。
- **失效与降级**：GPU pass 抛异常 → `catch (Exception | LinkageError)` →
  置 `gpuDisabledThisSession`，本会话回退 collector，**不回写配置文件**（R3 起；第 1 步
  沿用了 26.2 的 `MeshyConfig.GPU_BAKING.set(false)`，理由与两处差异见
  `REVIEW_UPSTREAM_TML_GPU_262_20260831.md` A2）。世界表另有独立标志与「连续 30 次」阈值，
  两张表互不连坐；换模型 `releaseBaked()` / `releaseWorldBaked()` 防泄漏。
- **配置**：`MeshGpuBaking`（默认 true）、`MeshGpuUnderShaders`（光影下常驻 VBO，R3 曾默认 true、
  现已退回 false，见 §5.10）、`MeshWorldFullDetailDistance`。R3 起 **18 项 TML 配置全部**（14 项
  第 2 步 v2 起默认 true）、`MeshWorldFullDetailDistance`。R3 起 **18 项 TML 配置全部**（14 项
  接进局内 cloth 面板 + en/zh 语言键（TOML 能改的局内都能改，`setDefaultValue` 与
  `MeshyConfig` 的默认值逐字对齐）。

### 第 2 步 v2 新增（光影下的常驻 VBO；R3 实机 PASS 后曾默认开启，同日退回默认关，见 §5.10）

- **绘制点整体搬迁**：1.21.11 的手部几何在 `ItemInHandRenderer#renderHandsWithItems` 末尾
  就 `renderAllFeatures()` + `endBatch()` flush（不是延迟到世界渲染末尾），Iris 也是 hook
  这两个调用接管手部绘制。因此 GPU 骨骼改画在**该方法 RETURN**（`ItemInHandRendererMixin#
  tacz$drawMeshGpuAfterHandFeatureFlush`，`require=0`）—— 一个注入点同时覆盖无光影与光影，
  ModelView / Projection / 输出目标覆写都是「刚被原版手部批次用过」的那一份。
  第 1 步在 `renderItemInHand` RETURN 现取矩阵的做法随之删除（那正是「相对人物世界位置恒定」
  的成因）。
- **光影下**：`MeshGpuUnderShaders=true` 时 pass 开在 Iris `HAND_SOLID` 阶段内
  （Iris 的 `HandRenderer#endRender` 就发生在我们这个 RETURN 之前），输出目标按
  `RenderType#draw` 同款规则解析 → 落在 gbuffer；管线经
  `IrisApi.assignPipeline(pipeline, IrisProgram.HAND)` 收 `gbuffers_hand` 照明。
  **不 mixin Iris 内部类、不 patch `RenderType#draw`。**
- **顶点格式跟随**：烘焙用 `LIT_PIPELINE.getVertexFormat()`（Iris 会把它换成
  `IrisVertexFormats.ENTITY`），格式记进 `BakedBone`，变了立即重烘 + 绘制端二次校验。
- **三层回退**：配置关 / 非 Iris 1.10.x / 上一帧没跑到 flush 钩子（存活证明）→ 全部回
  collector；绘制抛错 → 本会话禁用。详见
  [`TML_GPU_STEP2_HANDFLUSH_20260831.md`](TML_GPU_STEP2_HANDFLUSH_20260831.md) §2.4。

### 第 3 步新增（世界语境常驻 VBO：第三人称 / 掉落物 / 展示框 / 雕像）

- **同一条手法，换一次 flush**：1.21.11 的世界几何是 `LevelRenderer` frame-graph 主通道里
  `renderAllFeatures()` 写 builder + 紧随的 `endLastBatch()` 真 draw；GPU 骨骼挂在
  `FeatureRenderDispatcher#renderAllFeatures` 的 **RETURN**（`require=0`，见
  `FeatureRenderDispatcherMixin`），ModelView 现取 flush 当刻那份 —— 与手部第 2 步同构。
- **提交侧闸门**（`shouldSubmitGpuWorld` + `isWorldGpuContext`）：GUI/`FIXED_GUI`* 语境、
  Screen 提取窗口、`FIXED`/`HEAD` 命中枪匠桌标记、镜内那遍、阴影 pass、手部 pass 全部拒收；
  另需「世界 flush 钩子存活证明」（钩子失联 ⇒ 下一帧自动回 collector，不会丢枪）。
- **多光照档 LRU**（`MeshGpuLightCacheSize`，默认 4 档）+ 每帧烘焙额度 + **延迟释放池**
  （本帧可能已有条目引用被逐出的 VBO，下一帧 `beginFrame` 才 close）。
- **顶点预算只挡 collector**：GPU 每帧只传 O(骨骼) 个矩阵，预算对它没有保护对象；
  若照旧先过预算闸门，「16 格外高模枪整把消失」的老毛病就没解决。
- **镜内那一遍（PIP 二次渲染）**：画但**不清表**、不占本帧消费标志（提交每帧只登记一次，
  这里清了主画面就没得画；collector 在镜内那遍照常重放，两遍内容必须一致）。
- **光影**（`MeshGpuWorldUnderShaders`，R3 曾默认 true、同日退回 false，见 §5.10）：世界那一次 flush 里要受光需要把自建
  管线登记进 Iris 的实体 program，常量已由 CI javap 核实为 **`IrisProgram.ENTITIES`**
  （全量枚举见 `TML_GPU_STEP2_HANDFLUSH_20260831.md` §4.2）；这条组合已于 2026-08-31 实机 PASS。
  隔壁 26.2 分支靠 `RenderTypes.entityCutout` + `RenderType#prepare()` 天然落在 Iris 已接管的
  `ENTITY_CUTOUT` 上 —— **这一点两个分支不等价，不要照抄**。
- 完整证据（1.21.11 三个 `renderAllFeatures` 调用点、MV 归属、`EntityRenderDispatcher` 的
  相机相对平移）、与隔壁分支的差异、待验清单：
  [`TML_GPU_STEP2_HANDFLUSH_20260831.md`](TML_GPU_STEP2_HANDFLUSH_20260831.md) §4。

### 明确不包含（后续步骤，见可行性文档 §5）

- 光影下常驻 VBO 的实机验收：第 2/3 步均已 PASS，**剩下的空洞是「性能量化」**
  （高模多人场景下 GPU vs collector 的帧时间差值本仓从没测过，只有「不卡」的定性报告）。
- 姿态缓存 / 三角形配对 / LOD（远期方向）。
- mesh 目镜（上游 TML 同样不支持：ocular 物体必须用立方体）。

## 2. 弹匣链路（关 PR #70 的架构缺口，本轮的处理）

`BedrockGunModel` 把 `additional_magazine` 的 FunctionalRenderer 设为返回
`IMirrorGeometry`（指向 `magazine` 节点），快照遍历器原生处理立方体镜像。

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
| `MeshPolyMirrorReverseWinding` | false（2026-08-31 补成 true，同日按实机对照退回 false） | 镜像（`FLIP_MODEL_Y`，det<0）时把发射绕序整体反转，理论上让 `gl_FrontFacing` 与朝外法线一致。**但 collector 走 `RenderTypes.entityCutout`（剔背面）**⇒ 反转后朝外的面被剔掉，枪画成里朝外、近乎全黑；维护者拿 Forge 原版同包同图对照后确认「关着才对」。只在光影下有意义，改了要 `F3+T`（值在 `PolyMesh` 构造期读一次）。GPU 那两条管线 `withCull(false)`，所以在那边这个键只是细微差别 |
| `MeshPolyInvertNormals` | false | 烘焙法线再整体取反一次。若高光仍在错误一侧，用它与上一项做二选一（它修不动 front/back 与剔除） |
| `MeshPolyPreferPackNormals` | false | 改用枪包自带的逐顶点法线（平滑着色）而非每面一条平面法线。上游一直强制平面着色；枪包没写 `normals` 时无变化 |
| `MeshPolyIlluminatedRealSky` | false（同日补，先提 true 又退回） | `*_illuminated` 骨骼原本恒烘 (block=15, sky=15)；光影包把 sky 读成「看得见天空」。开着时**仅在装了光影包**把 sky 换成环境真值、block 仍 15。无光影下逐字不变。这是针对早期误读写的**独立**改动，不是 §5.9 那个现象的答案 ⇒ 默认关，想验证再打开（详见 §5.8） |
| `MeshPolyInShadow` | false | 阴影 pass 是否画 poly。它同时也是「poly 几何进不进光影包阴影图」的唯一开关 —— 曾据此怀疑「高模吃太阳光」来自这里，**实测否证**（打开无效），见 §5.9 |
| `MeshMaxRenderDistance` | 48 | 世界 poly 距离（0=不限） |
| `MeshPolyInPreview` | true | GUI/FIXED/HEAD 是否画 poly |
| `MeshGuiMaxVertices` | 65536 | GUI 顶点预算（0=不限） |
| `MeshWorldMaxVertices` | 120000 | 第三人称/掉落物顶点预算（0=不限） |
| `MeshWorldFullDetailDistance` | 16 | 世界语境近距全模豁免距离（0=关闭豁免） |
| `MeshMaxModelVertices` | 120000 | 加载时告警阈值（不影响渲染） |
| `MeshLogStats` | true | 加载统计日志 |
| `MeshGpuBaking` | true | 第一人称 GPU 静态烘焙（第 1 步）总闸。运行期异常**只改内存标志**（`gpuDisabledThisSession`），不回写配置文件 —— 26.2 那边是 `MeshyConfig.GPU_BAKING.set(false)`，本分支刻意不这么做（理由见 `REVIEW_UPSTREAM_TML_GPU_262_20260831.md` A2） |
| `MeshGpuUnderShaders` | false（R3 曾 true，同日退回） | 第 2 步 v2：光影下也走常驻 VBO，pass 开在 Iris 自己那次手部 flush 之内。需 Iris 1.10.x；钩子失联自动回 collector。几何/位置 2026-08-31 实机 PASS，但**照明不等价**（§5.10）⇒ 退回默认关，代码保留供 A/B |
| `MeshGpuWorld` | true | 世界语境也走常驻 VBO（第 3 步）：他人手持 / 掉落物 / 展示框 / 雕像。GUI/预览/镜内/阴影在提交侧拒收；钩子失联自动回 collector |
| `MeshGpuWorldUnderShaders` | false（R3 曾 true，同日退回） | 光影下的世界 GPU 路径（自建管线登记进 `IrisProgram.ENTITIES`，常量已审计）。2026-08-31 实机 PASS（几何）；照明不等价 ⇒ 退回默认关，见 §5.10 |
| `MeshGpuLightCacheSize` | 4 | 世界 GPU 每模型缓存的量化光照档数（LRU，1-16）。每档显存 ≈ 模型顶点数；上游 TML 按未量化光照缓存 8 档 |

> 第 3 步之后 GPU 路径覆盖**第一人称手部 + 世界语境**（他人手持 / 掉落物 / 展示框 / 展示台）。
> **GUI 语境、半透明部件与弹匣永远走 collector**（按语境与材质在提交侧分流，见 §5.6）。
> 光影下的两条各自还要求「已审计 Iris + 对应 flush 钩子的存活证明」同时成立
> （第 2/3 步，见 `TML_GPU_STEP2_HANDFLUSH_20260831.md` §3-§4）。
>
> 18 项都在 `tacz-client.toml` 的 `[mesh_loader]` 段，并且全部接进了局内「渲染」页（Cloth）；
> 两边默认值/键位齐平由 **`python3 docs/check_mesh_config_parity.py`** 把关（零依赖：键集合、字段绑定、
> 默认值、`defineInRange` ↔ `setMin/setMax`、语言键存在性、en/zh 齐平，六项一起查；R3 反光轮次加，
> 注入四类错误都实测报出，不是摆设）。`docs/ci/build.yml`（待上线）里对应的那步就是跑它；
> 清单见 `docs/ci/README.md`。

## 5. 验证清单

### 5.1 编译（CI 闭环）

沙箱无 JDK。编译验证走 `compile-check.yml` CI 闭环：push 触发 →
Actions 跑 `./gradlew compileJava` → 日志 commit 回推分支 → 沙箱读取。

### 5.2 实机（本地）

1. **无 mesh 枪包回归**：行为应与改动前一致（默认包全立方体，mixin 注入点
   都是 TAIL + geo 存在性检查，无 geo 时零行为差异）。
2. `model_type: mesh` + geo：第一人称可见、贴图正确；日志出现
   `poly_mesh stats for ... N bones, M vertices`（每 geo 只一行——缓存生效）。
3. F5 / 掉落物 / JEI / 展示框：位置与投影正确（本轮全走 collector，
   不存在 #70 的世界 pass 泄漏形态）。
4. 换弹：枪上弹匣与手里弹匣都在（纯 mesh 弹匣尤其要看）；换弹全程无双影。
5. 高模包：JEI 打开一屏图标——应看到 `poly preview suppressed in GUI` 且不卡死。
6. 光影（Complementary 系）：poly 枪身正常照明（走 vanilla entityCutout 提交，
   Iris 按 HAND program 处理，与立方体同一路径）；阴影里枪影仍在（立方体提供）。
   注意这一条只覆盖「画得出来、明暗不脱节」，**不覆盖法线朝向**（无光影时原版程序根本不读
   `va_normal`）；反光侧的判定见 §5.7。
7. 资源重载（F3+T）：poly 仍正常（解析缓存失效并重建）。
8. 枪匠桌预览 / 物品栏内嵌展示：近距高模枪在 GUI 语境按 GUI 预算闸门处理，
   世界展示台雕像 / 物品框在近距按全模豁免正常显示。
9. **光影下的反光/法线**（§5.7 那张矩阵）：**已回包（2026-08-31 晚，维护者拿 Forge 原版同包做对照）**
   ⇒ 结论是「第 4 格（三项全 false = 与上游逐字相同）才对」，`MeshPolyMirrorReverseWinding` 已退回默认
   false。第 2/3 格（`PreferPackNormals` / `InvertNormals`）仍没人跑过，属可选项，见 §5.7 末。
   无光影的第 0-3 轮 PASS 从来不构成对这一项的验证（原版实体程序不读 `va_normal`）。
10. **太阳/月亮会不会照穿屋顶**（§5.8）：站在屋里或夜里、让枪身对着光源方向，看枪是否仍按「露天」
    的亮度被照明。`MeshPolyIlluminatedRealSky`（**现默认关**，手工开）之后应当变成「被屋顶给的 sky 值压住」；
    若仍然亮，说明亮度来源不是 sky 分量（多半是光影包对手部 pass 不做阴影测试，见 §5.8 末尾）。
11. **挡住天体的那块模型是否继承天体亮度**（§5.9，A/B/C 三步）：A `MeshPolyInShadow=true` →
    看第三人称/掉落物/展示框的枪；B 再把 `MeshGpuWorldUnderShaders`（必要时连
    `MeshGpuUnderShaders`）关掉；C 两步都没用就归到包侧手部 exposure 惯例。三个开关都是每帧读值，
    不用重启、不用 F3+T。**已跑完（2026-08-31）：A 无效、B 有效** ⇒ 结论与连带修复见 §5.10，
    `MeshPolyInShadow` 保持 false，光影下那两个 GPU 键退回 false。

### 5.3 第 1 步 GPU 烘焙（实机，无光影）

1. 配置确认 `MeshGpuBaking=true`、`MeshGpuUnderShaders=false`（默认）。
2. 无光影 + 高模 mesh 枪（36 万顶点级）第一人称：日志出现
   `GPU mesh pass drew N bones (...) in vanilla hand flush`（N > 0），且 spark 热点里
   逐顶点 collector 开销消失（`#24 蒙皮/骨骼烘焙` 相关热点下降）。
3. 无光影 + 低模/立方体枪：无 `GPU mesh pass` 日志行或 N=0，行为不变。
4. 开光影（Complementary 系）：GPU 日志停发（回退 collector），枪身照明正常
   （与立方体同一 `entityCutout` + HAND program 路径）。
5. 换弹（纯 mesh 弹匣）：全程无双影。当前绘制点在**手部批次 flush 之后**（同一条
   栈上），GPU 骨骼与手臂/立方体的遮挡由深度测试决定（LEQUAL + 深度写），无双影
   才代表注入点正确；若出现「手臂穿过枪身/枪穿过手臂」的顺序性穿帮，说明还需要
   把绘制点提到 flush 之前（`@At("HEAD")` 包住那两个调用）而不是继续挪矩阵。
6. GUI 不卡死：JEI/物品栏打开时 GPU 不接管（`ScreenRenderTracker` 门禁），
   无 `GPU mesh pass` 行。
7. F5 / 掉落物 / 展示框：不触发 GPU（仅手部语境），走 collector，位置正确。
8. 光影包开关翻转：`ShaderStateTracker` bump 烘焙世代号，切回无光影后 GPU
   立即重烘、枪身光照档位正确（验证 26.2 `9f7412e` 修法的移植）。
9. `MeshGpuBaking` 运行时手动设 false → 立即回退 collector；再设 true 恢复。

### 5.4 第 2 步 v2：光影下的常驻 VBO（实机，`MeshGpuUnderShaders=true`）

默认关，需要手工打开；打开前请先读 `TML_GPU_STEP2_HANDFLUSH_20260831.md` §3。

1. **先看 CI 日志**：`build-reports/compile-java.log` 里 `> Task :dumpHandFlushApi`
   段确认 `ItemInHandRenderer.renderHandsWithItems` 的 flush 结构、`RenderPass` 成员、
   `BufferBuilder` 对未知分量的默认填充、以及 Iris 侧 `HandRenderer#endRender` /
   `IrisApi.assignPipeline` / `IrisVertexFormats.ENTITY` / `ShaderKey.HAND_CUTOUT` 全部存在。
   **任一条不成立就不要进实机**，先改注入点。
   —— 2026-08-31 已完成：除「`BufferBuilder` 对 Iris 多出分量的默认填充」这一条（观感级，
   需实机看）之外全部 ✅，`renderHandsWithItems` 实测 143 行、**单个 return**、尾部
   `renderAllFeatures()` + `endBatch()`，详见 `TML_GPU_STEP2_HANDFLUSH_20260831.md` §3。
2. 光影（Complementary Reimagined）+ `MeshGpuUnderShaders=true`：日志出现
   `GPU mesh pass drew N bones (...) in Iris hand flush: lit=true, ...`，
   并且**枪可见**（第 2 步 PoC 的失败形态是整把枪消失 → 若复现，说明 pass 没进 gbuffer）。
3. 照明正确性：走进暗室枪应变暗、对着发光方块/手电应有明暗变化；与旁边立方体枪对比
   不应明显更亮或更暗（`HAND_CUTOUT` 生效的判据）。
4. 不应拉伸/乱飞：`vertexFormat` 日志值应与 `IrisVertexFormats.ENTITY` 一致，
   且 `Mesh bake vertex format changed underneath` 只在切包那一帧出现。
5. 运行中开关光影包各 3 次：无崩溃、无残留（世代号 + 格式双校验）；关掉开关后立即回
   collector（`entityCutout` + HAND program 照明本来就正确）。
6. 换弹 + 半透明骨骼：与 §5.3 第 5 条同判据。
7. spark：`PolyMesh#writeCutout` / `writeTranslucent` 在第一人称不再是热点。
8. **非 1.10.x 的 Iris**（如 1.11/旧版）：只 WARN 一次
   `needs the audited Iris hand-flush hook`，渲染行为与默认一致。

### 5.5 第 3 步：世界语境常驻 VBO（实机，`MeshGpuWorld=true` 默认即开）

逐条清单见 `TML_GPU_STEP2_HANDFLUSH_20260831.md` §4.3（已含实机结论）。最关键的几条：

1. **多人视角**：他人手持的 mesh 枪必须随相机正确移动 —— 「钉在视角方向上 / 转身时漂」
   就是隔壁 26.2 分支踩到的那条坑；出现即说明 MV 取自了错误的时刻，别再挪烘焙时机。
   → **2026-08-31 维护者两轮实机均未复现**（无光影 + 光影组合；这条是第 3 步的主验收项）。
2. **预算解耦**：近处高模纯 mesh 枪不因预算整把消失；日志出现
   `GPU world-baked N bones (M vertices) at quantized light …`。
3. **光照打摆防护**：明暗边界上一排掉落枪时，`GPU world-baked` 只在前两次是 info 级；
   逐帧刷说明 LRU 容量不够（调 `MeshGpuLightCacheSize`）或场景确实跨太多档。
4. **不泄漏**：开背包 / 枪匠桌 / 热栏 / 开镜（F3+T 也来一次）之后，世界里不多画、
   GUI 内不少画、不崩；显存不随重载单调增长（走延迟释放池）。

5. **光影组合**（需手工把 `MeshGpuWorldUnderShaders=true` 打开 —— R3 曾默认，现已退回默认关）：日志出现
   `Assigned mesh_entity_world to the Iris ENTITIES program.`，世界里的 mesh 枪**受光影照明**
   （夜里变暗、进照明块变亮），不发白也不全黑 → **2026-08-31 维护者一遍过**。
   上一轮那条「失效」回报是当时默认关所致的正常回退（详见 §5.6）。
   诊断键 `GPU world submit refused: <reason>` 留在原位，服务于将来「别的 mod 改了渲染结构」
   这类情况。

### 5.6 已知边界（如实）
- **光影下世界 GPU 路径**：上一轮报的「失效」经核实是**默认关**（当时
  `MeshGpuWorldUnderShaders=false` ⇒ 光影下世界语境按设计走 collector，不是缺陷）。维护者随后
  打开该键复测：2026-08-31 **一遍过**（含 `Assigned mesh_entity_world to the Iris ENTITIES
  program.` 那条）。R3 起该键默认 true。诊断日志留在原位，它以后要服务的场景是「别的 mod 改了
  渲染结构」：`TaczPolyMeshGunModel` 被门闸拒收时按原因去重打一条 INFO
  （`GPU world submit refused: <reason>`），`PolyMeshGpuRenderer#worldSubmitBlocker` 逐条重判
  门闸给出第一条命中项；绘制侧异常本来就有带栈的 `LOGGER.error`。拿到 `refused:` 行还是那条
  ERROR，就能分清「没提交」与「画的时候抛异常」。

- 第 1 步的 GPU 覆盖范围只有「无光影 + 第一人称手部」；**世界/第三人称/掉落物自第 3 步起
  进常驻 VBO，GUI 语境永久留在 collector**（那是设计边界，不是未完成项）。
  36 万顶点级第三人称/掉落物的 CPU 成本问题即由此消解，但**帧时间收益没有量化数字**。
- 光影下第 1 步默认回退 collector（`assignPipeline(HAND)` = 第 2 步，未做）。
- PIP 二次渲染（`ScopePipRerender=true`）时镜内那遍会重放 collector 回调，
  poly 成本 ×2。降级方案见路线图方向 3，待镜内行为实机确认后做。

### 5.7 光影下的反光与法线（2026-08-31 追查轮；同日二次修订：绕序那半被实机否证）

**症状**（维护者报）：装光影包后枪的反射光源「关系不对」，高光像落在错误的一侧。

**静态根因**（`core/PolyMesh.java`，两条都**只在光影下显形**；第 1 条的修法当天被实机否证，见其下的 ⚠）：

1. **镜像与绕序不自洽。** `poly_mesh` 的位置相对 pivot 在 Y 轴取反（`FLIP_MODEL_Y=true`）。
   单轴镜像是 det<0 的合同变换 ⇒ **每个面的正反面互换**。烘焙法线本身没错：它是
   「原始顺序的叉积 × 翻转符号」= `D·n`，即镜像后的**朝外**法线（`BedrockPolygon` 对
   `mirror` 的处理是「反转顶点顺序 + 只把被镜像轴的分量取反」，与本仓立方体路径一致）。
   当时的结论是**绕序应当反转**：法线说「朝外」，`gl_FrontFacing` 说「背面」，所以把发射顺序倒过来就
   一致了。前半句成立、后半句不成立，理由见下面那条 ⚠。原版实体程序不读
   `va_normal` ⇒ 无光影下这条不可见，所以第 0-3 轮的实机 PASS **不能**当作「法线正确」的证据；
   装上 Iris 后，包里 `gbuffers_entities`/`gbuffers_hand` 的常见写法
   `normal *= gl_FrontFacing ? 1.0 : -1.0`（为双面几何自洽而做）会把我们那条朝外法线取反
   ⇒ 高光/反射跑到错误一侧。与症状吻合。
   **⚠ 这条「修法」当天就被实机否证了**（见本节末的对照结论）：`gl_FrontFacing` 那半边推理确实成立，
   但「反转发射绕序」同时改变了**剔除**的结果 —— collector 用的 `RenderTypes.entityCutout` 剔背面，
   于是被剔掉的是朝外的面，留下里侧，观感是整枪近乎全黑、只有远侧墙上有高光。换句话说：上游那对
   组合（不反绕序 + `D·n`）在真实枪包与真实光影包下是自洽的，而我推的「源绕序约定」与之相反 ——
   那个前提我当时没有任何实测依据。
2. **`FORCE_FLAT_SHADING` 恒 `true` ⇒ 枪包写的 `normals` 数组从未被消费**（解析出来后直接丢弃）。
   后果是每个面一条平面法线，枪管、护木这类曲面在光影下呈棱角状高光。

**修法**（落在数据层 ⇒ GPU 与 collector 两条路一起修好：两者共用同一份 `bakedN*` 数组，
`PolyMesh#compile` 只是在其上再乘 `pose.normal()`；因此**没有**去 shader 侧补偿）：

- 面顶点先按 QUADS 展开成发射顺序（三角形 `[0,1,2]` → `[0,1,2,2]`），需要时整体倒序 ⇒ `[2,2,1,0]`：
  退化三角形在前，有效三角形 `(2,1,0)` 朝向正好相反。**展开与反绕序因此可以分开做，不用特例**
  —— 实现保留，但**默认不启用**（`MeshPolyMirrorReverseWinding=false`）：机制上它是对「正反面一致性」
  的补偿，代价是把开了剔除的那条路的面剔错方向，而光影下的主路恰好就是那条。
- 平面法线**仍从原始（未翻转）顺序**求叉积再乘 `D`：发射顺序反过来只改变「哪一面算正面」，
  不改变朝外方向；跟着发射顺序走等于把 `D` 乘两遍，法线会翻回错误的一侧（实现时踩过一次，代码里留了注释）。
- 退化面（三点共线、叉积长度 ≤1e-6）不再写零向量：先退回枪包法线，没有就写 `(0, NORMAL_SY, 0)`。
  零向量在光影里 `normalize()` 出 NaN，表现是那一面带随机高光。
- 三个开关（`MeshyConfig`）：`MeshPolyMirrorReverseWinding`（**false** = 与上游一致；曾默认 true 一天）、
  `MeshPolyInvertNormals`（false）、`MeshPolyPreferPackNormals`（false）。
  本轮真正留下来的两条与绕序无关：**退化面不再写零法线**（零向量在包里 `normalize()` 出 NaN ⇒
  那一带随机高光），以及 `MeshPolyPreferPackNormals` 这个「消费枪包 `normals`」的开关（上游那段分支本来
  就在，只是被常量 `FORCE_FLAT_SHADING=true` 编译掉了 ⇒ 打开它不算偏离上游）。
  值在 `PolyMesh` 构造里读一次 ⇒ 只在**重新解析网格**时生效，局内改完按 `F3+T`
  （`TaczMeshyIntegration` 注册的 CLIENT_RESOURCES reload 监听会清 `PolyMeshSupport.PARSE_CACHE`，
  这条失效路径已静态核实）。

**证据边界**（AGENTS.md §2）：本轮**只到静态**。已证 = 代码路径与上面那几条推导、两条自研管线
`.withCull(false)`（⇒ 反转绕序不会让 GPU 路径的面消失）、`PARSE_CACHE` 失效时机、
17 项 ↔ Cloth ↔ en/zh 语言键齐平、CI 编译通过。**未证 = 光影下的实际观感**，请按矩阵各跑一次
（每格一次 F3+T）：

| MirrorReverseWinding | InvertNormals | PreferPackNormals | 预期 | 实机（维护者 2026-08-31 晚：同枪包、同光影，拿 Forge 原版做对照） |
|---|---|---|---|---|
| true | false | false | 法线与 front/back 自洽，高光在朝外一侧；曲面仍偏棱角 | ❌ **不对**：collector 剔背面 ⇒ 朝外面的被剔掉，整枪近乎全黑（就是「法线又不对了」那条回报） |
| true | false | **true** | 上一行 + 枪管/护木这类曲面变平滑 | 未跑；同一个剔除问题还在，预计也不行 |
| false | true | false | 只靠取反补偿：光影观感与第 1 格相同 | 未跑。它是「包按 `gl_FrontFacing` 取反」那一派的备选补偿，**别默认开** |
| false | false | false | **R3 之前 = 与上游逐字相同** | ✅ **这一格才对** ⇒ 现在的新默认 |

跑出来的正是「第 1 格比第 4 格差」那一支 ⇒ 默认值退回 false，本节按实机改写。上游侧同步改在
`REVIEW_UPSTREAM_TML_GPU_262_20260831.md` A10：**那条建议里的「反转绕序」部分已被本仓自己否证，别照搬**。
还缺的材料：包名（这次用的是哪个光影包）、以及第 3 格（`MeshPolyPreferPackNormals=true`）会不会让
枪管/护木这类曲面明显变好 —— 那一格与剔除无关，是纯粹的美观项，值得单独跑。

**别把这一节读成「反光问题也一并修好了」**：绕序退回 false 之后，**第 0 步那轮最初的症状**（光影下高光/
反射像在错误一侧）回到「与上游一致」的状态 —— 也就是**仍然没人修好**，只是不再比上游更糟。想继续往下走的
两条都不碰剔除：`MeshPolyPreferPackNormals=true`（曲面平滑，上游本就有这条分支）与
`MeshPolyInvertNormals=true`（整体取反，若你的包确实按 `gl_FrontFacing` 改法线，这格会比第 4 格更接近
「物理正确」，但那是观感判断，得眼睛看）。两格都是 F3+T 生效、局内即时可回退。

**判别实验（数据层 vs 消费层）**：collector 独占的部件（半透明部件、弹匣、GUI 预览、镜内）与
GPU 部件（第一人称 / 第三人称 / 掉落物）**是否一起错**？一起错 ⇒ 就是这份共享的 `bakedN*` 数据
（本轮假设）。只有 GPU 那两侧错 ⇒ 去查提交侧用 `mat3(ModelViewMat)` 而非逆转置的差异
（只有非均匀骨骼缩放才会显形），本轮没动那块。

**上一版在这里写的「已知风险」当天兑现、次日被 26.1.2 的证据改口**（原文：「如果 poly 面消失/镂空，
就是它开了剔除 —— 关掉 `MeshPolyMirrorReverseWinding` 即可，并请回报」）。`entityCutout` 在本 MC 版本
是否剔背面，沙箱里**至今核不了**（无 Loom jar）；维护者那两张同包同光影的对照图只证明了
「绕序反转 ⇒ 全黑」这个**现象**，「因为有剔除」是我给的解释。2026-09-01 收到 26.1.2 的字节码结论：
他们那版 `RenderPipelines.ENTITY_CUTOUT` **显式 `.withCull(false)`**（见
`docs/lineage/SYNC_REVIEW_2612_TML_PORT_20260901.md` §7）⇒ 「剔面」这一支在兄弟分支上不成立，
本仓的解释因此改写为：**承重的是「包按 `gl_FrontFacing` 取反法线」**——上游那对组合（不反绕序 + `D·n`）
是"错两次 = 对"，单方面反转发射顺序等于把已经正确的有效法线又翻一次 ⇒ 朝光面变暗、亮的是远侧内壁，
与"朝外面的面被剔掉"在图像上不可分。「剔面」降级为**未排除的次要分支**，把两者分开的判据见 §6 第 ④ 步
（`InvertNormals=true` 与 `MirrorReverseWinding=true` 两格观感是否几乎相同）。

想「两头都对」只有两条路，都**没做**（都需要实机，我不想再交付一次推导式修复）：
① collector 换 `RenderTypes.entityCutoutNoCull` —— 这是行为改动（双面几何开始遮挡内部件、半透明叠加
   也会变），影响所有 poly 语境；
② 不猜源绕序约定，改成**从数据里推**：面叉积方向与「面中心 − 模型质心」做点积，逐面决定是否反转
   （多数凸体能自洽，凹形与薄板会误判）。上游 26.2 `587763c` 的 `PolyMesh` 与本仓改前逐字相同 ⇒
   他们那对组合同样是「实测对、推导不闭合」，所以 A10 改成了「只建议第 ② 条，别动默认值」。

### 5.8 高模枪「遮不住太阳/月亮」：自发光部件的天空光（2026-08-31 第二轮）

> ⚠️ **定性（同一天第三轮之后补的）**：本节针对的症状是我**按维护者那句话的字面读出来的**
> （「遮不住」「屋顶墙遮不住它」），而他们真正说的是「挡住天体的那一块会继承天体的亮度」。
> 下面的因果链本身站得住（`0xF000F0` 的 sky nibble 确实会被包读成「露天」），但它**不是**那个现象的
> 成因 —— 判据：`MeshPolyIlluminatedRealSky` 关掉之后，B 项实验里只有停掉光影下的 GPU 两个开关才
> 恢复正常。所以这一项**默认 false**，只作为「光照值确实被写歪」的独立改进留着；真正的结论在 §5.10。

**症状**（我当时理解的）：光影下高模枪不会遮住太阳/月亮 —— 枪身会跟着天空的亮度被照明，屋顶和墙遮不住它。

**根因**（这次不在法线上，而在**烘焙进去的光照值**）：枪包里骨骼名以 `_illuminated` 结尾 =
「自发光 / 不受环境光」，本仓与上游 TACZ 的做法都是把它的 packed light 硬写成 `0xF000F0`
（block=15 **且** sky=15）：`BedrockPart#render`（立方体层，代码注释就写着「最大亮度」）与
`PolyMeshModel`（poly 层）是同一个数字。无光影下这是**对的**：原版光照图是 block 列与 sky 列
**相乘**的，只把 block 拉满、sky 给 0，结果基本是黑的 —— 想「不受环境光」必须两列都拉满。

问题在光影包那一侧的语义：包里普遍把 **sky 分量读成「这个表面看得见天空」**，并据此决定太阳/月亮的
直射光与高光（很多包对 sky=15 干脆不去查阴影图）。于是「常亮」被翻译成「永远晒得到太阳月亮」
⇒ 正是维护者看到的现象。而且这个值**沿子骨骼继承**（`BedrockPart#render` 往下传 `cubePackedLight`，
`PolyMeshModel#buildIlluminatedBones` 往下传 `parentIlluminated`），所以顶层一个骨骼被命名成
`*_illuminated`，整把枪都跟着「露天」。

**修法**（第 18 项 `MeshPolyIlluminatedRealSky`，默认 **false** —— 它针对的是另一个现象，见本节末）：只在**装了光影包时**
block 保持 15 ⇒ 洞里照样看得见，但不再声称自己晒得到太阳。无光影下逐字保持上游行为（这一点是静态可证的：
判据里第一个条件就是 `isUsingRenderPack`）。三条消费路径同源：

| 路径 | 位置 |
|---|---|
| collector（半透明部件 / GUI / 镜内 / 弹匣 / GPU 回退） | `PolyMeshModel#drawBoneMeshes` |
| GPU 第一人称手部烘焙 | `TaczPolyMeshGunModel#ensureBaked` |
| GPU 世界烘焙（第三人称 / 掉落物 / 展示框） | `TaczPolyMeshGunModel#ensureWorldBaked` |

三处都调 `PolyRenderPolicy#illuminatedLight`（值在配置里，光影状态由 `ShaderStateTracker` 每帧推进一个
缓存布尔 —— 反射查 Iris 的 `isShaderPackInUse` 每次都要 `Class.forName` + `getMethod`，不能放在
每帧每骨的路径上；缓存还没写过时才直接查一次）。光影状态翻转本来就会让烘焙世代失效重烘，
所以这里不需要额外的失效逻辑；**开关值本身**改了要按 `F3+T`（poly 的光照在解析/烘焙定型）。
sky 真值不是新采的：上游传进来的那个 light 就是相机方块位置的光照，本分支没有另加采样，
所以「头顶有屋顶 ⇒ sky=0」这件事本来就在那一位里。

**刻意没动的两半**（不让这一轮牵连过广）：

1. **立方体层**（`BedrockPart#render` 里的 `15728880`）：那是 TACZ 本体的行为，影响所有枪包与所有
   `_illuminated` 准星点 / 激光，配置的合理归属是 `ClientConfig` 而不是 `[mesh_loader]`。
   若你发现**低模/立方体那半边照样晒得到太阳**，要改的就是那一处 —— 那一次得连着 26.2 一起改，
   我不建议只改一半（本分支这样切，也是为了让你能判断只改 poly 层够不够）。
2. **EMISSIVE 兜底**：`lightmap` 视图拿不到时整条 GPU 路退到 EMISSIVE 管线（会打一条 WARN
   `Level lightmap view unavailable; GPU path falls back to EMISSIVE.`）。那是「无条件全亮」而不是
   「跟天空走」，与本条现象可区分：日志里没有这条 WARN 就说明兜底没参与。

**如果开关拨来拨去观感都不变**：那亮度来源就不是我们烘的 sky 值，而是光影包对**手部 pass** 的态度 ——
第一人称几何不在世界的深度/阴影体系里（手部 pass 用另一套投影，包里 `IS_HAND` 分支常常直接不做阴影
测试），于是手持部分本来就不可能被自己遮住。判别：第三人称看同一把枪（世界 pass，走
`gbuffers_entities` + 我们的 `IrisProgram.ENTITIES` 登记）—— 第三人称下正常、只有第一人称不对 ⇒
是包侧惯例，不是本分支的缺陷；两边都不对 ⇒ 回头查那个枪包到底给哪些骨骼起了 `_illuminated` 名字
（加载统计日志里有现成计数：`poly_mesh stats for <geo>: … (translucent=…, illuminated=…)`，
`illuminated` 非 0 才说明这条路径参与了你看到的现象）。

### 5.9 「枪身盖住太阳/月亮那一块反而发亮」：候选解释之一（poly 没进阴影图）—— **已被实机否证**

维护者把现象说清楚了：**不是屋顶**。是开光影后，高模枪的几何*本身*挡在太阳/月亮前面时，
**挡着的那部分模型会继承那颗天体的自发光亮度**；其它自发光物品、以及不属于 TML 的模型都没这问题。

**当前最合理解释（静态可推，未实机）**：光影包判断「这个表面晒得到太阳吗」不是看屏幕遮挡，
而是拿片元的世界位置去查**阴影图**（`shadowtex0/1`，由 Iris 的阴影遍渲染进 `RenderPass`）。
一个**不在阴影图里**的表面，按构造就是「完全露天」⇒ 太阳/月亮的 `sunEmissive`/高光整份打上去
⇒ 看起来正是「枪身挡住太阳的那块被太阳点亮」。

而我们这一层恰好从来进不了那张图：`MeshPolyInShadow` 的**唯一**消费点是
`PolyRenderPolicy#shouldRenderPoly`（`isRenderShadow() && !POLY_IN_SHADOW ⇒ return false`），
默认 false ⇒ 阴影遍里 poly 部件一个都不提交 ⇒ 阴影图里只有**立方体那层**。高模包的意义就是
poly 表面比立方体外壳大、比它细，于是**超出立方体的那些面**在阴影图里等于不存在 ⇒
「只有高模部分会吃太阳光」与「非 TML 的模型没这问题」两条同时被解释（后者全部图元都在阴影图里）。

**这条解释的边界**（必须写清楚，否则会被当成结论）：
`IrisCompat.isRenderShadow()` 为真的那一遍是 Iris 的阴影遍，它渲染的是**实体**（以及方块）；
**自己的第一人称手部几何根本不经过这一遍**（手部不是实体）。所以：
- 第三/两人称手持、掉落物、展示框/展示台 ⇒ `MeshPolyInShadow=true` 应当能修（poly 由 collector
  提交进阴影遍，路径已核实存在，且是每帧读值 ⇒ 不用重启、也不用 F3+T）；
- 纯第一人称 ⇒ 多半修不动，那是包侧对手部几何的 exposure 处理（很多包对 `gbuffers_hand`
  干脆不做阴影测试，因为它没有可用的世界位置）。

**判别矩阵**（三步，全是局内即时生效的开关，按顺序做，每步只看「挡住太阳那块还亮不亮」）：

| # | 操作 | 结果 ⇒ 结论 |
|---|---|---|
| A | `MeshPolyInShadow=true` | 世界语境的枪不亮了、第一人称仍亮 ⇒ 上面那条解释成立；我把默认改成 true 并写下代价 |
| B | 再把 `MeshGpuWorldUnderShaders=false`（必要时连 `MeshGpuUnderShaders=false`） | 若 A 没用、这步才有用 ⇒ 问题在**我们自建 pass 与 Iris frame graph 的时序/附着关系**，与阴影图无关（下一步要查的就是这个） |
| C | A、B 都没用 | 是包侧对常驻几何/手部的 exposure 惯例；本分支能做的只有减少差异（例如不再自开 pass），不是「修 bug」 |

**代价说明**（为什么不无脑默认 true）：`MeshPolyInShadow` 只在**装了光影包**时有意义
（`isRenderShadow()` 无 Iris 时恒 false ⇒ 这个键对无光影用户是彻底的 no-op）；有意义的那批用户
要付「每把枪在阴影遍里多提交一层 poly」的成本 —— 这正是当初把它关掉的唯一理由（上游注释写的是
「立方体已经提供阴影形状」，但立方体只保证*有个影子*，保证不了*逐面遮挡*）。阴影遍走的是
collector，与 GPU 主画面路径不叠加，所以代价只有「多一遍 CPU 顶点变换」这一项。

**与上一轮的区别**（别把两件事混成一个）：§5.8 那条讲的是「`_illuminated` 骨骼恒烘 sky=15 ⇒
包把它读成『看得见天空』」，那是**烘焙进顶点的光照值**；本节讲的是**阴影图里没有这个几何**。
两者独立：前者那条改法（`MeshPolyIlluminatedRealSky`）是**顺手做的**，判据来自我对症状的误读，
维护者报的从来不是这个；本节则由上面的 A/B/C 判别定案 —— 见紧接其实测结果那一段。

**实测结果（2026-08-31，维护者）**：A（`MeshPolyInShadow=true`）**无效**；B（把光影下的
`MeshGpuWorldUnderShaders` / `MeshGpuUnderShaders` 关掉）**有效**，而且第一人称、第三人称、展示台
三种语境表现一致。⇒ 上面那条「阴影图里缺几何」的解释被否证，根因在**我们自开的那个 pass** 与
光影包照明语义的关系上；`MeshPolyInShadow` 保持 false（不为一个无效的解释付每帧阴影遍的成本）。
结论与连带修复见 §5.10。

### 5.10 B 命中之后的结论：光影下的常驻 VBO 照明不等价（默认退回关 + 一条连带缺陷已修）

**证据**（维护者 2026-08-31，三种语境一致：第一人称 / 第三人称 / 展示台）：
关掉 `MeshGpuUnderShaders` + `MeshGpuWorldUnderShaders` 之后，「枪身盖住太阳/月亮那一块继承天体
自发光亮度」的现象消失；开着就能复现。同一批几何走 collector（`submitCustomGeometry` +
`RenderTypes.entityCutout`）没有这问题 ⇒ 差别不在模型数据（法线、光照值都在两条路共用），
而在**由谁提交、以什么管线提交**。

**两条路的实际差别**（静态可列，沙箱内无法进一步核实）：

| | collector | 我们的常驻 VBO pass |
|---|---|---|
| 提交者 | TACZ/vanilla 的 `RenderType` 批次，被 Iris 自己接管 | 我们在 flush 之后 `createRenderPass` **自开一个 pass** |
| 管线 | 原版 `ENTITY_CUTOUT`（Iris 认识它，按包扩展 color target / 顶点格式） | 我们 `RenderPipelines.register` 的自建管线，再 `IrisApi.assignPipeline` 登记进 HAND / ENTITIES |
| 输出目标 | 由 Iris 的 frame graph 决定 | 按 `outputColorTextureOverride` / `outputDepthTextureOverride` 手工挑（`drawList` 里逐条对齐 `RenderType#draw`） |
| lightmap | 原版批次自带 | 我们显式取 `gameRenderer.lightTexture().getTextureView()` 绑到 `Sampler2` |

**最可疑的一条已经修掉了**（本轮代码改动），链条每一环都在源码里：

```
resolveLightmap() 取不到 lightmap 视图  ->  lightmapUnavailable = true     <- 一次性闩锁，整会话不再重试
  ->  drawList: pipeline = lit ? LIT_PIPELINE : EMISSIVE_PIPELINE         <- 从此恒走 EMISSIVE
  ->  EMISSIVE_PIPELINE 带 .withShaderDefine("EMISSIVE")                   <- 关键
  ->  if (irisFlush) assignMeshPipelineTo{Entity,Hand}(pipeline)          <- 把**这条**管线登记进光影程序
  ->  光影包按 #ifdef EMISSIVE 走「自发光 / 不查阴影」分支
  ->  现象：几何盖住天体，自己却「继承」天体的自发光亮度；三种语境一致；只影响 TML 的模型
```

`resolveLightmap` 以前是**一次性闩锁** ——
`getTextureView()` 只要有一次返回 null 或在光影下抛异常，`lightmapUnavailable` 就永久为真，
此后整条 GPU 路一律改用 `EMISSIVE_PIPELINE`。而在光影包里 EMISSIVE 是「**自发光、不受阴影**」
的语义（这正是包用来画准星点、发光方块的那条）——「几何挡住天体、自己却继承天体亮度」就是这个
语义的样子，而且它天然对三种语境一视同仁、只在开光影时出现、且只影响 TML 的模型（collector 那条
不走这个兜底）。改法两条：

1. 去掉闩锁：每帧重试（`getTextureView()` 是缓存读），日志只去重、不再永久降级；
2. **光影下拿不到 lightmap 就不再画**：`gpuMasterUsable()` 里加一条
   `isUsingRenderPack() && !lightmapResolvable() ⇒ 整条拒收` ⇒ 退回 collector，由包按正常
   `entityCutout` 路径照明。理由是「EMISSIVE 退化」在这里不是外观降级，而是**换了照明语义**；
   宁可不进 GPU。`GPU world submit refused:` 那条诊断也补了这个原因串。

**仍然没有定论的部分**（别把上面读成「已修复，只是没测」）。给维护者一条决定性查证，不用编译：
翻之前那次「开着 GPU + 光影」的日志，搜 `lightmap` —— 老代码是闩锁，所以
`Level lightmap view unavailable; GPU path falls back to EMISSIVE.` 或
`Failed to read level lightmap; …` **只要出现过一次**，上面那条链就完整成立，本轮的改动就是你这个
现象的修复；**如果一次都没有**，EMISSIVE 兜底没参与过，症状要归到「自建管线在包里的 MRT /
color target 集合与原版 `ENTITY_CUTOUT` 不一致」那一类（需要拿 Iris 的 `ExtendedRenderPipeline` /
`MixinRenderPipeline` 逐条对表，沙箱里既没有光影包也没有可反编译的 Loom jar，做不了）。
所以本轮只做了两件有证据的事：**默认值退回**（`MeshGpuUnderShaders` / `MeshGpuWorldUnderShaders`
都回 false，代码保留供 A/B）+ **上面那条连带缺陷**（去闩锁、光影下拿不到 lightmap 就整条拒收）。

**复测回包（2026-08-31 晚，本轮 jar）**：维护者报「PASS，上述问题没了」。**这条 PASS 的边界要看清**：
他测的时候两键就是新默认 false ⇒ 光影下全程走 collector，所以它验证的是「默认退回」这件事，
**不**验证上面那条代码修法（EMISSIVE 闩锁是否就是你那次的实际路径，仍然只有日志能定）。
副作用也在这条里：正因为光影下回到了 collector，§5.7 那个 `MeshPolyMirrorReverseWinding` 的默认值
才第一次显形（这条路剔背面）—— 同一天按对照图退回 false。

**下一轮的判据**（把两键手工打开再进一次光影，其余不动）：

| 日志 | 结论 | 下一步 |
|---|---|---|
| 出现 `GPU path refused while a shader pack is active: the level lightmap view is unavailable`（手/通用）或 `GPU world submit refused: shaders are on but the level lightmap view is unavailable`（世界），且**现象消失** | 兜底就是成因，本轮修法命中 | 剩下的问题是「怎么在光影下稳定拿到 lightmap 视图」，另开一轮，别急着把默认改回 true |
| 两行都没有（lightmap 解析正常）、现象**回来了** | EMISSIVE 被排除 | 只剩「自建管线的 MRT / color target 集合与 `ENTITY_CUTOUT` 不一致」：需要你再给一个**非 deferred / compatibility 档**的包做对照（那个档通常不受影响），我再去对 Iris 的 `ExtendedRenderPipeline` |
| 两行都没有、现象也**没有** | 说明它其实只跟「老配置里那两键 = true」有关 | 检查 `tacz-client.toml`：默认值改动**只影响新生成的配置**，老档里手工开过的仍是 true |

新加的两行日志都是去重的一次性输出（`loggedLightmapRefusal` / `loggedLightmapFailure` 会在状态恢复时复位），
不会逐帧刷。第一人称以前是静默拒收（审查 A8 那条），现在也能从日志判定了。

**同步影响**：26.2 那边光影下的 GPU 路径是**默认开**的，而且他们的 `drawList` 更硬
（直接绑 `mc.gameRenderer.mainRenderTarget()`，完全不看 override = 审查 A1），所以同一个现象在
26.2 上只会更明显 —— 已记 A12（本条 + EMISSIVE 闩锁在他们那边同样存在）。

---

### 5.11 GPU pass 体内不得触发纹理懒加载（2026-09-01 维护者实机定位；本分支同日修）

**症状原话（维护者）**：「某些高模枪贴图错误」「你日志里连续三条就是逐帧重试」——duyupack 的 kar98un
这类**全部件都走 GPU** 的模型才会出现；普通枪至少有一个部件走 collector。

**根因**：`PolyMeshGpuRenderer#drawList` 把 `resolveTextureView`（内部是
`TextureManager#getTexture(id).getTextureView()`）放在了**已打开的 render pass 里面**（逐组 bind 循环中）。
`getTexture` 对**未加载**的纹理会同步懒加载：`registerAndLoad → ReloadableTexture#apply →
CommandEncoder#writeToTexture`，而 `writeToTexture` 属于「pass 打开期间禁止的命令」类 ⇒ 直接抛
`Close the existing render pass before performing additional commands`。
这与本函数里「DynamicTransforms 切片必须 pass 前写」「顺序索引缓冲必须预热」是**同一条不变量**，
只是纹理这一路当时漏了。

**为什么只有全 GPU 高模包会踩**：只要有任何一个部件被 collector 画过，那张 UV 就早已在 pass 外被请求过；
全部件走 GPU 时**首个请求者就是我们自己** ⇒ 在 pass 内解析必炸 → 被 `catch` 吞掉、回退 missing texture
（表现＝贴图错），而且这次炸之后纹理永远没机会在 pass 外完成加载 ⇒ 下一帧再炸、逐帧重试（日志里那三条）。

**本分支的修法（同日，与 26.1.2 的 `2ae4c29` 同一形状）**：
① 所有组的纹理视图（含 missing 回退）在 `createRenderPass` **之前**解析进 `viewsByTexture`，
pass 体内只对已解析的视图 `bindTexture` —— pass 体内除既有 draw/bind 命令外零外部调用（顺手把 lightmap 的
`getSamplerCache()` 也提了出去）；② `resolveTextureView` 的失败日志改 **per-texture log-once**
（`loggedTextureFailures`），原来是每次失败一条 ERROR，逐帧重试就刷屏；③ 结构其余不动，
资源重载后视图逐帧重新解析，因此**不需要额外失效逻辑**；唯一附带的一处：那条"首帧判据日志"从 pass 体内挪到
`pass` 关闭之后，让"体内只留 bind/draw/scissor"这条不变量**字面**成立（只改日志时序，同帧、绘制内容不变）。

**同批自查（结论：只有这一处）**：全仓其余自建/嵌入式绘制点都不进 `registerAndLoad` 那条懒加载路 ——
① 镜内覆盖层的几何走 `ScopeFinalOverlayState` 的 collector flush，纹理由 vanilla 在各自批次里解析；
② `ScopeTextSubmitter.pageId(...)` 只在 **submit 期**（任何 pass 之外）调一次
`TextureManager#register(id, shell)`（put 语义、不加载；壳重写 `close()` 也不拥有纹理），
每帧只是重指 `textureView`/`sampler`，所以后续 `RenderSetup(Identifier) → getTexture(id).getTextureView()`
命中的是**已注册对象**，不会走懒加载分支 —— 这条"先注册、绘制期只读"正是本不变量的正面写法；
③ `ScopePipDepthDebug`/`ScopePipRenderState` bind 的是 `RenderTarget.getColorTextureView()` /
`getDepthTextureView()`（缓存读）。**注意**：若哪天 `TextureManager#register` 在升级中变成
`registerAndLoad` 语义，②要一起重查（届时与账本 L-16 一起回看）。

**证据级别**：根因与两条判别 = 维护者实机（日志里连着同一条 = 逐帧重试；「普通枪至少有一个部件走 collector
⇒ 贴图早被加载，所以没事」是这条根因的对照面）；我方这份改动 = 同一根因的机械落地 + CI 编译门，
**实机待验**（判别见下）。按 AGENTS §2：不写「已修好」，写「已按实锤根因同批修」。
另注：这与 §5.10 的「关掉光影下两键即消失」不是同一条证据 —— 那条查的是 EMISSIVE 照明语义，本条查的是
pass 内懒加载，两者症状与修法都不同，别互相顶替。

**判别法（接手者跑这个）**：duyupack 的 kar98un（或任意"全部件走 GPU"的高模包）+ `MeshGpuBaking=true`
+ `MeshGpuWorld=true`，第一/第三人称各看一次：**贴图应正常**且日志里不再出现
`Failed to resolve texture view` 与 `GPU world mesh pass failed`；把光影关掉再看一次（应与开启前一致）。
若仍报 `Close the existing render pass...`，说明还有第二个 pass 内懒加载入口，届时把栈贴回来即可定位。

---

## 6. 未修 BUG 记录：poly 绕序 × 背面剔除（2026-08-31 立项；本分支只做了规避）

**状态**：**OPEN / 不修**。本分支只把触发器退回默认关（`MeshPolyMirrorReverseWinding=false` ⇒ 与上游、
与 Forge 原版一致），数据层那个不自洽**仍然存在**。之所以单独开一节：这个问题的三层原因彼此**互相抵消**，
任何「只修其中一层」的做法都会立刻显形 —— 我自己已经踩了一次，别再有人凭推导改它。

**维护者原话**（症状以此为准，不要转述成机制）：
「PASS。倒是没上述的问题了，就是这高模枪的法线又不对了吧？还是说本来就不对？我们的是“32”，
作为对照的是尾缀为“25”的原版」—— 两张截图是同一枪包、同一光影包下的第一人称，本仓 vs Forge 原版。

**环境与变量**（少一项，结论就可能不同）：

- MC 1.21.11 + Fabric，本仓 `1.1.8+fabric.1.21.11.R3`；Sodium + Iris —— 本分支核过的是
  **Iris 1.10.7 那个 jar**（`IrisProgram` 常量与 flush 钩子用 CI javap 对过）以及分支 `1.21.11` 的源码，
  两者**不视为同一物**；维护者实际用的 Iris / Sodium 小版本未回报；
- 光影包：**Complementary Shaders - Unbound**（具体版本未回报）；
- 光影下两个 GPU 键为**默认 false** ⇒ 高模走 collector（`RenderTypes.entityCutout`）；
- 触发器：`MeshPolyMirrorReverseWinding=true`（我上一轮的默认值）+ `F3+T`（值在 `PolyMesh` 构造期读一次）。

**复现（约 3 分钟，不需要调试工具）**：

1. 上面那套环境，创造超平坦，拿任意**高模 mesh 枪包**的一把枪（poly 面明显超出立方体外壳的那种；
   纯立方体枪不受影响，因为立方体路径自己把 mirror 与绕序成对处理了）。
2. 默认配置（绕序 false）先看一次：观感应当与「同枪包 + Forge TACZ 0.7.0 + 官方 TML」并排一致 —— 这是基线。
3. 局内把 `MeshPolyMirrorReverseWinding` 打开 → `F3+T` → 同一角度再看：poly 部分近乎全黑、只剩远侧内壁
   上有高光；第一人称 / 第三人称 / 展示台三种语境一致。关掉再 `F3+T` 即恢复。**这一步是「能稳定复现」的判据。**
4. **判别到底是剔面还是「包的 facing 取反」**（这一条决定本记录的解释是哪一支；26.1.2 已回 Q8 的字节码
   证据，见下面第 2 层）：
   · 分两跑各按一次 `F3+T`：① `MeshPolyInvertNormals=true`（绕序保持 false）；②
     `MeshPolyMirrorReverseWinding=true`（取反保持 false）。**两格观感几乎相同** ⇒ 承重的是「包按
     `gl_FrontFacing` 取反法线」，与剔不剔面无关（**当前主推解释**）；只有 ② 会露内壁/出现镂空 ⇒
     才是剔面，此时 ① 应当与基线一致。
   · 顺手记录：绕着枪看准星座、弹匣底、枪口内壁这类薄板/开口处有没有镂空。

**来源（三层，逐层可核；关键在「抵消」）**：

1. **数据层**（`core/PolyMesh.java`，与上游 `587763c` 逐字同源，改前也是）：`FLIP_MODEL_Y=true` ⇒
   位置相对 pivot 单轴镜像（det<0）；法线乘 `D=diag(1,-1,1)`（= `BedrockPolygon` 处理 mirror 的**那一半**），
   但发射顶点顺序不跟着反转（= 它的**另一半**被省略）。于是「法线说朝外、`gl_FrontFacing` 说背面」。
   ⚠ 这条结论成立与否取决于一个**我们没有权威来源的前提**：枪包 `poly_mesh` 的绕序约定是「从外侧看 CCW」。
   Bedrock 方块模型是 Y-down、从外看 CW；若枪包按 Bedrock 习惯导出，那么镜像之后**恰好自洽**，
   上游那个组合就是对的，「缺陷」根本不存在 —— 第 3 步能稳定复现的就是这一点。
2. **消费层（主/次分支已按 26.1.2 的字节码证据换位）**：自研两条 GPU 管线显式 `.withCull(false)`
   （静态可证）；collector 用 `RenderTypes.entityCutout`，它的剔除状态我们**核不了**（无 Loom jar），
   而 26.1.2 测到他们那版 `ENTITY_CUTOUT` 管线**不剔背面**。⇒ 主推解释**不依赖剔面**：
   **包按 `gl_FrontFacing` 取反法线**那一步是承重的，上游那对组合是"错两次 = 对"，所以看着正常；
   单方面反转发射顺序 ⇒ 有效法线又翻一次 ⇒ 朝光面暗、远侧内壁亮。
   「collector 剔背面 ⇒ 朝外面的面被剔掉」降级为**未排除的次要分支**（若为真，同一改动也会全黑，
   只是伴随镂空 —— 用第 ④ 步分开）。为什么第 0-3 轮没显形：GPU 路上 facing 差异只是细微观感，
   光影下回到 collector 之后包对 entity 程序的处理才把差异放大。
3. **包层**：Complementary Unbound 的 `gbuffers_entities` / `gbuffers_hand` 里凡是按 facing 修 normal 的
   写法（`normal *= gl_FrontFacing ? 1.0 : -1.0` 一类，为双面几何自洽而做），会把第 1 层放大成
   「高光/反射落在错误一侧」—— 也就是维护者最初报的那条。**包源码没读**，属惯例推断。

**所以「本来就不对吗」的准确回答**：如果第 1 层那个前提为真，那么不一致**从上游继承就一直存在**，
但在「Bedrock 导出习惯 + collector 剔背面 + 这个包」的组合下它恰好**被抵消掉**，原版观感正确；
单方面把绕序「修好」会让第 2 层反噬（面被剔掉）。要闭合必须**同时**动两层，而第 2 层是全局行为改动。

**三个候选修法与代价**（本分支**都没做**；在没有第 4 步判别结果之前，谁做都等于再赌一次）：

| 方案 | 动的地方 | 代价 / 必须先有的证据 |
|---|---|---|
| ① collector 换成 `RenderTypes.entityCutoutNoCull` | 所有 poly 语境的剔除状态 | 双面几何会开始遮挡内部件、半透明叠加也会变；掉落物 / 展示框 / 弹匣 / GUI 要整轮重验（§5.2 清单） |
| ② 从数据反推绕序（面叉积与「面中心 − 模型质心」点积，逐面决定是否反转） | `PolyMesh` 构造函数，两条路一起 | 凹形与薄板会误判；需要一批真实枪包做统计，不能拿一把枪定结论 |
| ③ 维持现状（当前） | —— | 与上游观感一致；第 1 层不自洽留在数据层（近全黑风险仍在）。最初那条「高光偏一侧」**已另有确认根因**：法线矩阵的读取时刻，见下面 §6.1，与本表的三层不是一回事 |

**接手者需要的材料**：Iris 与光影包的版本号、上面第 4 步的镂空判别、以及若选 ①：改完之后
第一人称 / 第三人称 / 展示框 + 一把带半透明部件的枪的截图。

### 6.1 另一条已定案根因：光影包的 `gl_NormalMatrix` 读取时刻（2026-09-01，随 26.2 `83daf16` 同步）

**症状**：GPU 路径画出的 mesh 枪在光影包下**反光/高光偏一侧**、与光源的相对关系不对
（维护者 2026-09-01 从 26.1.2 线转来 26.2 的实锤，机制对 Iris 1.10/1.11 同样成立）。

**机制**：光影包引用的 `gl_NormalMatrix`（Iris 经 `VanillaCoreTransformer` 改名 `iris_NormalMat`）
**不来自** DynamicTransforms 快照，而是 Iris 在**绘制执行那一刻**读 RenderSystem 的 MV 栈顶求逆转置
（`ExtendedShader#iris$setupState`：`RenderSystem.getModelViewMatrixCopy().invert(…).transpose3x3(…)`；
1.21.11 的触发点是 `GlCommandEncoder#executeDraw → trySetup`，逐次绘制都会过）。
本文件的 GPU 路径把顶点**留在骨骼本地系**（`PolyMesh#writeRaw` 裸写 `setNormal`），
法线的全部旋转就指望这一个矩阵补上 ⇒ 栈顶没有 pose 层时，平行光/反射按骨骼本地法线计算。
**位置不受影响**：`ModelViewMat` 走 `writeTransform` 写好的 DynamicTransforms 切片，一直是错的时刻无关正确的。

**我方与 26.2 的形状差别**：他们首版是「压栈 → `prepare()` → 立刻弹栈 → 再 `drawFromBuffer`」，弹早了；
我方 `PolyMeshGpuRenderer#drawList` 则**从未压过栈**（只在 pass 外写切片、pass 内 `setUniform` 换 slice），
所以病灶更彻底。同批修法：每次绘制的 `pass.drawIndexed(…)` 整段包在
`mvStack.pushMatrix(); mvStack.mul(entry.model()); … finally { mvStack.popMatrix(); }` 里
—— pose 必须留在栈上直到该次绘制执行完。同一个手法我方在镜内覆盖层里早已在用：`client/render/scope/ScopeFinalOverlayState.java:165-214`
（`modelView.pushMatrix()` + `set(...)` 罩住整段 flush，`finally` 里 `popMatrix()`），本次只是把它搬到逐 entry 的绘制上。

**与两条控制观察自洽**（任何解释都必须过这一关）：① 「不属于 TML 的模型都没这问题」——本文件里
collector 路径写的是 `pose.normal()` 变换后的法线（`PolyMesh.java:199` 取 `Matrix3f n = pose.normal()`、
`221` 写 `setNormal(tnx, tny, tnz)`），pose 已经烘进顶点，栈顶有没有 pose 层都与它无关；
只有 GPU 烘焙那条走 `writeRaw` 裸写（`240` 行 `setNormal(bakedNX, bakedNY, bakedNZ)`，类注释里写明
「`setNormal` 不再传 Pose，否则法线矩阵会被乘两次」）——正是「指望绘制当刻那个矩阵补上旋转」的设计。
② 「关掉光影现象即消失」——vanilla 侧不读法线（下面第一条），与光影无关的 EMISSIVE 语义那条（§5.10）
是**另一个**病灶，别混：那条解释的是「挡住天体的那块反而亮」，本条解释的是「高光朝向/光源关系不对」。
**不受牵连的两条**（所以这批改落在没装光影包时是纯空转）：
① 本文件 §5.10 与 vanilla 路径都带 `NO_CARDINAL_LIGHTING`，核心 entity shader 那条分支不读法线；
② collector 路径把 pose 烘进顶点，栈顶是什么都不影响它的法线。

**证据级别**：机制 = 26.2 实机实锤（`83daf16`）；我方代码 = 静态推导 + CI 编译门，**实机待验** ——
按本仓库 AGENTS §2，"病灶一致 + 修法同批"不等于"我方已修好"。
**判别法**（接手者跑这个就够）：同光影包同枪，慢慢水平转视角，看高光是否**跟着枪身表面**走；
`MeshGpuBaking=false` 时同一段应无变化（那时走 collector，本来就没这个病灶）。

**交叉引用**：§5.7（矩阵已按实机回填，第 4 格 = 现状）、§5.10（光影下两键退回 false 与那次 PASS 的边界）、
`docs/REVIEW_UPSTREAM_TML_GPU_262_20260831.md` A10（上游侧已按本节缩小建议）、
`docs/lineage/HANDOFF_LEDGER.md` L-8b、`docs/lineage/SYNC_GUIDE_1211_TO_2612_TML_GPU_20260831.md` §1.6
（26.1.2 视角：他们还没有 TML，所以要的是「自己决定」而不是「照抄我们的规避」）。
