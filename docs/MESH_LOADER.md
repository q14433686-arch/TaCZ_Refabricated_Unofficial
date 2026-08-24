# 内置 TacZ Mesh Loader [TML]

> 代码移植自 [VellEagle/TacZMeshLoader](https://github.com/VellEagle/TacZMeshLoader)
> `1.21.1_fabric` v0.1.7，GPL-3.0。不是官方 TacZ 附属。

## 1. 上一版为什么失败（PR #33，2026-08-09，已关闭）

第一次内置尝试把高面数 mesh（日志：`ak_enact` 365,848 顶点）做成 GPU 烘焙，
但实机「枪看不见 + 严重卡顿」。事后对照本仓库已经结案的渲染事实，有三处硬伤：

1. **画在错误的 pass。** 第一人称枪是在 `GameRenderer#renderItemInHand` 里
   再跑一次 `renderAllFeatures` 才画的。上一版把骨骼登记后，在**世界**那一次
   `executeAlwaysOnTop` 之后就画到 `mainRenderTarget`，手部投影/深度都还没就位。
2. **用了不可信的 modelView。** `RenderSystem.getModelViewMatrixCopy()` 在 26.2
   手部 pass 只是兼容残留（第 26 轮曳光案、第 30 轮案例⑧ 都实证过）。再乘一次
   骨骼矩阵等于把枪甩出画面。
3. **`visitBones` 的 skip 谓词写反了。** `isGpuBone == true` 被当成「跳过这根骨骼」，
   父骨骼一跳过还剪掉整棵子树。GPU 缓冲烘焙成功，但当帧零登记，画面当然是空的。

卡顿来源是另一条：consumer 路径每帧重建 30 万级顶点；上一版修深度时又每帧
每骨骼 `createBuffer` 法线 uniform（约 1200 buffer/s）。

## 2. 这一版怎么改

- 顶点保持骨骼本地空间，一次性上传为常驻 `GpuBuffer`。
- submit 时只收集 `poseStack.last().pose()`（立方体路径已经在用的物品+骨骼矩阵），
  **不再乘任何外部 modelView**。
- DynamicTransforms = 该矩阵；shader 做 `Proj × ModelView × local`，
  与 consumer 的 `Proj × I × (pose × local)` 等价。
- 手部 / 世界两套登记表，只在对应 `renderAllFeatures` 阶段边界画
  （手部由 `ScopeMaskRenderer.isInHandPass()` 判定，含 Iris HandRenderer）。
- 管线显式声明 `DepthStencilState.DEFAULT` + `ColorTargetState.DEFAULT`。
- 用 vanilla `core/entity` + `EMISSIVE`/`NO_OVERLAY`/`NO_CARDINAL_LIGHTING`，
  只绑 Sampler0，避开自定义 shader 与额外 sampler。
- Iris 光影包启用时整条 GPU 路径关闭，回退 consumer。
- GPU pass 抛异常则当次会话关掉 `MeshGpuBaking`。

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

并提供 `assets/mypack/geo_models/gun/mygun_geo.json`。
`model_type: "mesh"` 只对枪本身必需；配件只要旁边有同名 geo 就会替换。
目镜物体暂不支持 mesh（与上游 TML 相同）。

## 4. 配置

`tacz-client.toml` 的 `[mesh_loader]`：

| 键 | 默认 | 含义 |
|---|---|---|
| `MeshEnable` | true | 总开关 |
| `MeshGpuBaking` | true | 第一人称 GPU 烘焙 |
| `MeshPolyInShadow` | false | 阴影 pass 是否画 poly |
| `MeshMaxRenderDistance` | 48 | 非第一人称距离裁剪，0 = 不限 |
| `MeshPolyInPreview` | true | GUI/FIXED 预览是否画 poly |
| `MeshLogStats` | true | 加载时打骨骼/顶点统计 |

## 5. 验证状态

沙箱内完成：源码落地、接线、配置、mixin JSON；并按用户 2026-08-25 log 修了 8 参 submit 漏覆写。
**未执行** `./gradlew build`。需要本地验证：

1. 无 mesh 枪包：行为与改动前一致。
2. `model_type: mesh` + geo：第一人称可见，帧率应明显高于纯 consumer。
3. 日志应出现 `GPU-baked N bones` 与 `GPU mesh pass drew N bones (... ) on hand pass`。
4. 开 Iris 光影应自动走 consumer，不应崩。
5. 换弹时 additional_magazine 镜像仍在。
