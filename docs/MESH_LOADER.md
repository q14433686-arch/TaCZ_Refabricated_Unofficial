# 内置 TacZ Mesh Loader [TML]

> 代码移植自 [VellEagle/TacZMeshLoader](https://github.com/VellEagle/TacZMeshLoader)
> `1.21.1_fabric` v0.1.7，作者 VellEagle，GPL-3.0。不是官方 TacZ 附属。
> 署名与许可详情见仓库根 [`LICENSES.md`](../LICENSES.md)。
>
> **状态（2026-08-30）：安全子集 + GPU 静态烘焙均已实机 PASS。**
> 实测覆盖：无光影第一人称、光影下第一人称（vanilla RenderType 路线）、
> 世界语境近距全模（第三人称/掉落物/展示台）、光影开关切换（烘焙世代失效）。
>
> 路线图见 [`TML_PERF_DIRECTIONS_2026_08_29.md`](TML_PERF_DIRECTIONS_2026_08_29.md)。
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

### 5.3 已知边界（如实）

- 第一人称的 O(顶点) CPU 成本已由 GPU 烘焙消除（无光影与光影下均生效）;
  **世界语境（第三人称/掉落物/展示台）仍走 collector**，近距全模豁免范围内
  的高模枪每帧仍有 O(顶点) CPU 变换成本——这是「眼前能看到完整高模」的
  代价，预算与距离闸门保护远处/密集场景。
- PIP 二次渲染（`ScopePipRerender=true`）时镜内那遍的 poly 提交已跳过
  （纯白付的成本，镜内孔径本就不该有枪件）。
- 在两个不同光影包之间直接切换（不经过关闭状态）不触发烘焙世代失效；
  理论上格式补丁不变、无需重烘，若实测出现拉伸请回报（把包名变化也挂进检测即可）。
