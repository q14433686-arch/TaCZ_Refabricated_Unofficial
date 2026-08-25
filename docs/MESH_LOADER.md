# 内置 TacZ Mesh Loader [TML] —— v4

> 代码移植自 [VellEagle/TacZMeshLoader](https://github.com/VellEagle/TacZMeshLoader)
> `1.21.1_fabric` v0.1.7，GPL-3.0。不是官方 TacZ 附属。
>
> **状态：源码完成。沙箱无 JDK，`./gradlew build` 与实机验证待本地执行。**
> 按 AGENTS.md §2：下面没有写「已实测修好」。

## 0. 为什么关了的 PR 不能原样再开一遍

| 版本 | 结局 | 不该照抄的点 |
|---|---|---|
| PR #33 | 关 | 画在世界 pass + 乘不可信 `getModelViewMatrixCopy()` + `visitBones` skip 剪子树 |
| PR #69 | 关 | 光影一开就整条回退 CPU（36 万顶点卡顿本体还在）；声称做了烟雾光照但代码没改 |
| PR #70 | 关 | 编译后来了，但 **WORLD_DRAWS 全局表把 GUI/掉落物/第三人称登记进世界 pass** → 帖图空间不对；弹匣没接 26.2 `IMirrorGeometry` / 上游 TML 的 FunctionalRenderer 钩子 → **某些模型弹匣没了** |

v4 相对 #70 只改架构，不改「把同一份 GPU pass 再调一次参数」：

1. **GPU 表只收第一人称手部 pass。** 判定用 `ScopeMaskRenderer.isInHandPass()`，不用 `transformType.firstPerson()`（后者 GUI 也可能为 true）。世界那次 `renderAllFeatures` 直接清空残留，不会把 GUI 枪画进世界。
2. **画在 `executeSolid` 之后。** 手部 FOV / 深度已经是视模那一遍；立方体先入深度。
3. **弹匣：** 主路径 exclude `additional_magazine` 子树（与上游 TML 一致）；`super.submit` 仍走立方体 `IMirrorGeometry`；`additional_magazine.visible` 时用 `captureSubtree` 在该节点变换下补画 magazine poly。换弹弹匣走 collector，不走 GPU visitBones（避免把枪树再乘一遍）。
4. **36 万顶点：** 第一人称 GPU 烘焙后每帧只提交 O(骨骼) 矩阵。世界/GUI 用顶点预算闸门，避免 JEI 一屏几十把高模。

## 1. 性能（对应 `optimize-high-poly-vertex-transformation`）

26.2 `entity.vsh`：`gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0)`。

立方体路径把 submit 时 pose 烘焙进顶点，collector 用 identity 绘制。GPU 路径顶点留在骨骼本地，同一份 pose 写成 `DynamicTransforms.ModelViewMat`。这是文档里的 **Tier 1（逐骨骼 VBO，零 CPU 顶点变换）**，只用于第一人称。

未做（明确不做，避免再交一个半成品 GPU 蒙皮）：量化 16B 顶点、骨骼调色板 UBO、Iris 自定义 program、QEM LOD。光影包默认仍走 collector 快路径（SoA 热循环，仍是 O(顶点)，但去掉了巨态虚调用里的临时 `Vector4f`）。

## 2. 枪包怎么用

display JSON：

```json
{
  "model_type": "mesh",
  "model": "mypack:gun/mygun_geo",
  "texture": "mypack:gun/uv/mygun",
  "animation": "mypack:mygun"
}
```

并提供 `assets/mypack/geo_models/gun/mygun_geo.json`。
`model_type: "mesh"` 只对枪本身必需；配件只要旁边有同名 geo 就会替换。
目镜物体暂不支持 mesh（与上游 TML 相同）。

`fabric.mod.json` `provides: ["taczmeshloader"]`。

## 3. 配置（`tacz-client.toml` 的 `[mesh_loader]`）

| 键 | 默认 | 含义 |
|---|---|---|
| `MeshEnable` | true | 总开关 |
| `MeshGpuBaking` | true | 第一人称 GPU 静态烘焙 |
| `MeshGpuUnderShaders` | false | 实验性：光影下仍走 GPU |
| `MeshPolyInShadow` | false | 阴影 pass 是否画 poly |
| `MeshMaxRenderDistance` | 48 | 世界 poly 距离 |
| `MeshPolyInPreview` | true | GUI/FIXED 是否画 poly |
| `MeshGuiMaxVertices` | 65536 | GUI 顶点预算 |
| `MeshWorldMaxVertices` | 120000 | 第三人称/掉落物顶点预算 |
| `MeshMaxModelVertices` | 120000 | 加载时告警阈值 |
| `MeshLogStats` | true | 加载统计 |

## 4. 验证清单（本地）

1. `./gradlew build`
2. 无 mesh 枪包：行为应与改动前一致
3. `model_type: mesh` + geo、无光影：第一人称可见；日志 `GPU-baked N bones` 与 `GPU mesh pass drew N bones ... on hand pass`
4. F5 / 掉落物 / JEI：枪应在正确位置，**不应**出现在世界原点或错误投影里
5. 换弹：枪上弹匣与手里弹匣都在（纯 mesh 弹匣尤其要看）
6. 开 Iris：默认 collector；`MeshGpuUnderShaders=true` 自测双枪/光照

## 5. 明确不做

- 目镜物体 mesh 化（上游也没有）
- 光影包下的等价视觉
- 官方 TML 的即时 `VertexBuffer#drawWithShader`（26.2 已无该 API）
