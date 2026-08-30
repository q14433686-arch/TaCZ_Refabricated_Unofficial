# 内置 TacZ Mesh Loader [TML] —— 安全子集（第 0 步）

> 代码移植自 [VellEagle/TacZMeshLoader](https://github.com/VellEagle/TacZMeshLoader)
> `1.21.1_fabric` v0.1.7，GPL-3.0。不是官方 TacZ 附属。
>
> **状态：源码完成，等待 CI 编译验证与实机验证。**
> 按 AGENTS.md §2：本文没有一句「已实测修好」。
>
> 可行性论证与分步计划见
> [`TML_GPU_FEASIBILITY_1211_20260831.md`](TML_GPU_FEASIBILITY_1211_20260831.md)。
> 本轮是其中的**第 0 步**：从当前 1.21.11 基线内置「不含 GPU 赌注」的部分。

## 0. 与四个关闭 PR 的关系（为什么这是第五次、以及为什么这次砍掉了 GPU）

| 版本 | 结局 | 教训（本轮如何处置） |
|---|---|---|
| PR #33 | 关 | GPU 画在世界 pass + 不可信矩阵 + `visitBones` skip 剪子树 → **第 0 步无 GPU 路径，问题不存在** |
| PR #69 | 关 | 光影一开整条回退 CPU；声称做了的代码没做 → 本轮如实声明 CPU 路径是常态而非回退 |
| PR #70 | 关 | 全局 WORLD_DRAWS 表泄漏进世界 pass；弹匣没接 `IMirrorGeometry` → **无 GPU 表；弹匣链路照搬已修正的架构（见 §2）** |
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
- **状态追踪基建**（纯 CPU，为第 1 步 GPU 路径铺路）：
  - `ScreenRenderTracker`：用 Fabric `ScreenEvents` 精确检测「正在画 GUI screen
    的瞬间」（而非「菜单开着」），避免菜单开着时世界内无关渲染被误伤；
  - `ShaderStateTracker`：用 `RenderTickEvent`（START 相位）检测 Iris 光影包
    开关翻转，弱引用失效全部已注册模型的 VBO 缓存。
- **加载告警**：超 `MeshMaxModelVertices` 的模型加载时警告枪包作者。

### 明确不包含（后续步骤，见可行性文档 §5）

- **GPU 静态烘焙 / 逐骨骼 VBO**（第 1 步）——关闭 PR 四次翻车的部分，
  等无光影 PoC 通过后单独提交。**因此本轮对 36 万顶点级高模的第一人称
  帧率成本没有量级改善**，只有闸门和缓存级别的削减。这是如实声明。
- 光影下的 GPU 照明（`assignPipeline(HAND)`，第 2 步）。
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

**注意没有 `MeshGpuBaking` 等键**——GPU 路径第 0 步不存在，
不注册「没人读的配置」。

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

### 5.3 已知边界（如实）

- 36 万顶点级高模第一人称**仍有帧率成本**（每帧 O(顶点) CPU 变换 +
  逐顶点 VertexConsumer 调用）。这是第 1 步 GPU 烘焙要解决的，本轮不解决。
- PIP 二次渲染（`ScopePipRerender=true`）时镜内那遍会重放 collector 回调，
  poly 成本 ×2。降级方案见路线图方向 3，待镜内行为实机确认后做。
