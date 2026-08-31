# 内置 TacZ Mesh Loader [TML] —— 安全子集 + GPU 烘焙（第 0/1/2 步与第 3 步（无光影）已实机 PASS；第 3 步光影下失效，待诊断）

> 代码移植自 [VellEagle/TacZMeshLoader](https://github.com/VellEagle/TacZMeshLoader)
> `1.21.1_fabric` v0.1.7，GPL-3.0。不是官方 TacZ 附属。
>
> **状态：第 0 步（collector 安全子集）、第 1 步（无光影第一人称 GPU 静态烘焙）与第 2 步 v2
>（光影下把手部 pass 开进 Iris 自己的手部 flush）均已实机 PASS。第 3 步（世界语境常驻 VBO，
> `MeshGpuWorld`，见下方「第 3 步新增」与可行性文档）：<b>无光影下已实机 PASS</b>（维护者
> 2026-08-31 报告：邻居分支那两个坑——世界空间固定 + 烘焙时机过窄——未复现）；
> **光影下世界路径失效**（表现为回退 collector），已补一次性原因日志待诊断，见 §5.6。
> 两条光影下的开关（`MeshGpuUnderShaders` / `MeshGpuWorldUnderShaders`）仍是实验性、默认关。**
> 按 AGENTS.md §2：第 0/1/2 步的实机 PASS 是维护者 2026-08-31 报告的（换弹无双影、
> 光影下常驻 VBO 收 `gbuffers_hand` 照明）；**第 3 步：无光影已实机 PASS、光影下失效**——
> 本文对它
> 只写「源码完成 + 静态审计」。
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
- **阴影 pass 默认跳过 poly**（`MeshPolyInShadow=false`）：立方体已提供影子形状。
- **状态追踪基建**（纯 CPU，第 1 步 GPU 路径的地基）：
  - `ScreenRenderTracker`：用 Fabric `ScreenEvents` 精确检测「正在画 GUI screen
    的瞬间」（而非「菜单开着」），避免菜单开着时世界内无关渲染被误伤；
  - `ShaderStateTracker`：用 `RenderTickEvent`（START 相位）检测 Iris 光影包
    开关翻转，弱引用失效全部已注册模型的 VBO 缓存。
- **加载告警**：超 `MeshMaxModelVertices` 的模型加载时警告枪包作者。

### 第 1 步新增（无光影第一人称 GPU 静态烘焙，见可行性文档 §6.1）

- **`PolyMeshGpuRenderer`**：逐骨骼常驻 VBO（顶点留在骨骼本地系、光按 4 级
  量化烘进 UV2），每帧只上传 O(骨骼) 个 `DynamicTransforms`，在
  `renderItemInHand` RETURN 用自定义 `RenderPass` 画。`GPU mesh pass drew N bones`
  日志即验收锚点。世界/GUI/第三人称/掉落物仍全走 collector。
- **光照分档缓存**：`ensureBaked` 4 级 quantize + 1s 节流；illuminated 骨骼恒烘
  `FULL_BRIGHT`；光影包开关翻转 bump 烘焙世代号 → 持缓存的模型立即重烘
  （26.2 `9f7412e` 的修法，绕开 1s 节流）。
- **失效与降级**：GPU pass 抛异常 → 本会话自禁用并写回 `MeshGpuBaking=false`，
  永久回退 collector（与 26.2 语义一致）；换模型 `releaseBaked()` 防泄漏。
- **配置**：`MeshGpuBaking`（默认 true）、`MeshGpuUnderShaders`（光影下实验性常驻
  VBO，默认 false）、
  `MeshWorldFullDetailDistance`；cloth UI + en/zh 语言键已接。

### 第 2 步 v2 新增（光影下的常驻 VBO，**默认关闭、待实机**）

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
- **光影**：默认不走（`MeshGpuWorldUnderShaders=false`）。世界那一次 flush 里要受光需要把自建
  管线登记进 Iris 的实体 program，常量已由 CI javap 核实为 **`IrisProgram.ENTITIES`**
  （全量枚举见 `TML_GPU_STEP2_HANDFLUSH_20260831.md` §4.2）；默认关只剩「这套组合没跑过实机」
  一条理由。
  隔壁 26.2 分支靠 `RenderTypes.entityCutout` + `RenderType#prepare()` 天然落在 Iris 已接管的
  `ENTITY_CUTOUT` 上 —— **这一点两个分支不等价，不要照抄**。
- 完整证据（1.21.11 三个 `renderAllFeatures` 调用点、MV 归属、`EntityRenderDispatcher` 的
  相机相对平移）、与隔壁分支的差异、待验清单：
  [`TML_GPU_STEP2_HANDFLUSH_20260831.md`](TML_GPU_STEP2_HANDFLUSH_20260831.md) §4。

### 明确不包含（后续步骤，见可行性文档 §5）

- 光影下常驻 VBO 的**实机验收**（第 2 步 v2 只有编译期证据，见上）。
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
| `MeshPolyInShadow` | false | 阴影 pass 是否画 poly |
| `MeshMaxRenderDistance` | 48 | 世界 poly 距离（0=不限） |
| `MeshPolyInPreview` | true | GUI/FIXED/HEAD 是否画 poly |
| `MeshGuiMaxVertices` | 65536 | GUI 顶点预算（0=不限） |
| `MeshWorldMaxVertices` | 120000 | 第三人称/掉落物顶点预算（0=不限） |
| `MeshWorldFullDetailDistance` | 16 | 世界语境近距全模豁免距离（0=关闭豁免） |
| `MeshMaxModelVertices` | 120000 | 加载时告警阈值（不影响渲染） |
| `MeshLogStats` | true | 加载统计日志 |
| `MeshGpuBaking` | true | 第一人称 GPU 静态烘焙（第 1 步）。关闭→永久 collector；运行期异常也会自写 false |
| `MeshGpuUnderShaders` | false | 实验性（第 2 步 v2）：光影下也走常驻 VBO，pass 开在 Iris 自己那次手部 flush 之内。需 Iris 1.10.x；钩子失联自动回 collector。默认 false = 光影走 collector |
| `MeshGpuWorld` | true | 世界语境也走常驻 VBO（第 3 步）：他人手持 / 掉落物 / 展示框 / 雕像。GUI/预览/镜内/阴影在提交侧拒收；钩子失联自动回 collector |
| `MeshGpuWorldUnderShaders` | false | 实验性：光影下的世界 GPU 路径（自建管线登记进 `IrisProgram.ENTITIES`，常量已审计）。默认 false = 光影下世界走 collector（照明本来就正确；这条只是没跑过实机） |
| `MeshGpuLightCacheSize` | 4 | 世界 GPU 每模型缓存的量化光照档数（LRU，1-16）。每档显存 ≈ 模型顶点数；上游 TML 按未量化光照缓存 8 档 |

> GPU 路径只接管**第一人称手部**语境；世界/GUI/第三人称/掉落物无论开关如何一律走
> collector（第 1 步范围）。光影下还需 `MeshGpuUnderShaders` + 已审计 Iris + 存活证明
> 三条同时成立（第 2 步 v2，见 `TML_GPU_STEP2_HANDFLUSH_20260831.md`）。

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
7. 资源重载（F3+T）：poly 仍正常（解析缓存失效并重建）。
8. 枪匠桌预览 / 物品栏内嵌展示：近距高模枪在 GUI 语境按 GUI 预算闸门处理，
   世界展示台雕像 / 物品框在近距按全模豁免正常显示。

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

逐条清单见 `TML_GPU_STEP2_HANDFLUSH_20260831.md` §4 末「待实机」。最关键的四条：

1. **多人视角**：他人手持的 mesh 枪必须随相机正确移动 —— 「钉在视角方向上 / 转身时漂」
   就是隔壁 26.2 分支踩到的那条坑；出现即说明 MV 取自了错误的时刻，别再挪烘焙时机。
   → **2026-08-31 维护者无光影实机：未复现**（这条是第 3 步的主验收项）。
2. **预算解耦**：近处高模纯 mesh 枪不因预算整把消失；日志出现
   `GPU world-baked N bones (M vertices) at quantized light …`。
3. **光照打摆防护**：明暗边界上一排掉落枪时，`GPU world-baked` 只在前两次是 info 级；
   逐帧刷说明 LRU 容量不够（调 `MeshGpuLightCacheSize`）或场景确实跨太多档。
4. **不泄漏**：开背包 / 枪匠桌 / 热栏 / 开镜（F3+T 也来一次）之后，世界里不多画、
   GUI 内不少画、不崩；显存不随重载单调增长（走延迟释放池）。

光影那一条（第 5 项，`MeshGpuWorldUnderShaders=true`）实测**失效**：世界路径回退 collector，
且当时日志里没有任何原因（静默回退是设计）。已补 `GPU world submit refused: <reason>` 诊断，
详见 §5.6。

### 5.6 已知边界（如实）
- **光影下世界 GPU 路径失效（2026-08-31 维护者实机报告）**：表现为回退 collector（无光影时
  一切正常）。根因未定位，先补了诊断：`TaczPolyMeshGunModel` 在被门闸拒收时按「原因去重」打
  一条 INFO（`GPU world submit refused: <reason>`），`PolyMeshGpuRenderer#worldSubmitBlocker`
  逐条重判门闸并给出第一条命中项；绘制侧异常本来就有 `LOGGER.error(..., e)` 带栈。
  拿到 `refused:` 那行还是那条 ERROR，就能分清是「没提交」还是「画的时候抛异常」。
  在此之前 `MeshGpuWorldUnderShaders` 保持默认 false（原版路径不受影响）。

- 第 1 步 GPU 只覆盖**无光影 + 第一人称手部**；世界/GUI/第三人称/掉落物仍走
  collector（36 万顶点级第三人称/掉落物仍有 CPU 成本，属后续步骤）。
- 光影下第 1 步默认回退 collector（`assignPipeline(HAND)` = 第 2 步，未做）。
- PIP 二次渲染（`ScopePipRerender=true`）时镜内那遍会重放 collector 回调，
  poly 成本 ×2。降级方案见路线图方向 3，待镜内行为实机确认后做。
