# 内置 TacZ Mesh Loader [TML] —— v3（第三次尝试）

> 代码移植自 [VellEagle/TacZMeshLoader](https://github.com/VellEagle/TacZMeshLoader)
> `1.21.1_fabric` v0.1.7，GPL-3.0。不是官方 TacZ 附属。
>
> **状态：源码完成、API 逐条核对、结构检查通过；`./gradlew build` 未执行
>（编写环境无 JDK 且无法访问 Maven 仓库），实机验证待用户本地进行。**

## 0. 三次尝试的时间线

| 版本 | 日期 | 结局 | 根因（事后逐条核实） |
|---|---|---|---|
| PR #33 | 2026-08-09 | 关闭 | 画在世界 pass 边界 + 乘不可信的 `RenderSystem.getModelViewMatrixCopy()` + `visitBones` skip 谓词写反剪掉整棵子树 → 「枪看不见 + 严重卡顿」 |
| PR #69 | 2026-08-24 | 关闭 | 修对了 pass 与矩阵，补了 8 参 `submit` 覆写；但光影包启用即整体回退 CPU 路径（卡顿依旧）、`EMISSIVE` 满亮烘焙（暗处枪发光）、5 参 `createRenderPass` 与若干调用从未编译验证、从未跑过 build。**PR 正文声称落地了「烟雾环境光」，实际代码没改 `getLightCoords`（仍返回满亮 15728880）—— 本版已真正实现，见 `docs/LR_0_4_3_EVAL.md`** |
| **v3（本版）** | 2026-08-25 | 待验证 | 见下 |

v3 的直接输入是用户 2026-08-25 的实机日志（仓库根目录 `latest.log`）：
`duyupack:ak_enact` 20 骨骼 / **365,848 顶点**、`mcx_virtus` 18 骨骼 / 309,464 顶点、
`ar15_anime` 11 骨骼 / 54,836 顶点；每把枪的 geo JSON 被完整解析**两遍**；
consumer 路径下动态缓冲反复扩容（`Resized a dynamic immediate buffer to …`）。
这些数字就是「严重性能问题」的实体。

## 1. v3 的架构

### 1.1 GPU 静态烘焙路径（第一人称 + 世界）

```
extract() 阶段（每帧）
 └─ TaczPolyMeshGunModel.submit(…, collector, …)
     ├─ super.submit(...)                    ← 立方体部分照旧走引擎
     └─ poly 层：GPU 路径可用时
         ├─ ensureBaked(texture, light)      ← 顶点只上传一次；光照档位变了才重烘焙
         ├─ polyMeshModel.visitBones(...)    ← 每帧只收每根骨骼的矩阵（O(骨骼)）
         │    └─ PolyMeshGpuRenderer.submitBone(matrix, texture, bone, handPass)
         └─ 半透明骨骼仍走 collector（引擎排序）

renderAllFeatures 阶段边界（每帧）
 ├─ 世界那次（inHandPass=false，LevelRenderer#render 内部）
 │    └─ FeatureRenderDispatcherMixin → renderAtPhaseBoundary() → 画 WORLD_DRAWS
 │        （此刻主 target 深度含地形/实体，遮挡正确；
 │          ScopePipRenderer.redirectTarget() 非空时跳过，把登记表留给主画面那一遍）
 └─ 手部那次（renderItemInHand 开头，inHandPass=true）
      └─ 同一 hook → 画 HAND_DRAWS
          （vanilla 在 renderItemInHand 之前 clearDepthTexture(0.0)，
            手部深度从零开始，同样正确）
```

三次 `renderAllFeatures` 调用的时序核对（vanilla `GameRenderer` 反编译实读 +
本仓库在役 `GameRendererMixin` 的 HEAD/RETURN 标志维护）：

1. `LevelRenderer#render` 内部（世界）：inHandPass=false → 画世界登记；
2. `renderItemInHand` 首行（GameRenderer 反编译 359 行附近）：inHandPass=true
   （HEAD 注入已置位），此刻 flush 手部几何 → 画手部登记；
3. `renderLevel` 末尾（615 行附近）：登记已清空 → 空转。
   Iris `HandRenderer` 一帧调两次（solid+translucent）时，第二次同样空转。

### 1.2 光照（v3 新增）

- 管线不再 `EMISSIVE`。顶点 UV2 烘焙 packedLight，片元采样原版光亮度表
  （`Sampler2` ← `gameRenderer.levelLightmap()`，26.2 公开 getter）。
- 26.2 的 packed 布局是 `block << 4 | sky << 20`（**不是** 1.20.1 的 `sky << 8`；
  26.2 `LightCoordsUtil` 反编译实读），量化/重打包必须用同一布局。
- sky/block 各按 4 级一档量化；档位变化 ≥1s 才重烘焙（防边界抖动导致每帧重写）。
  误差最多 4 个光照级。发光（illuminated）骨骼恒满亮。
- 世界 pass 的实例沿用当前烘焙档位 —— 多实例光照是近似值（已知限制）。
- 光亮度表视图拿不到时回退 `EMISSIVE` 满亮管线并警告一次（是回退不是等价）。

### 1.3 深度遮挡（v3 新增）

5 参 `createRenderPass(label, color, Optional.empty(), depthView, OptionalDouble.empty())`
挂主 target 深度视图、不清空（LOAD），`DepthStencilState.DEFAULT`。
枪模被方块、镜身、手臂正确遮挡；骨骼间自遮挡由深度测试解决，
不再依赖「骨骼提交顺序当画家算法」。

### 1.4 光影包（Iris）策略

默认回退 consumer。依据：Iris 26.2 分支
`MixinGameRenderer#iris$disableVanillaHandRendering` 在包启用时**跳过原版手部
提交**、由 Iris 自己的 `HandRenderer` 画手；自建 pass 画进 vanilla 主 target 的
结果不保证参与 Iris 的最终合成，最坏是「一把无光影一把有光影」的双枪。

- `MeshGpuUnderShaders=true`（默认 **false**）供实验性强开，后果写进日志。
- 回退发生且模型很大时，日志提示一次原因与开关名，不再沉默变卡。

### 1.5 解析缓存（v3 新增）

用户日志里每把 mesh 枪的 geo JSON 被解析两遍（stats 行成对出现、间隔约 1s，
都在 `tacz-client-asset-preload` 线程）。36 万顶点级 JSON 每遍都是秒级 IO+解析。

- `PolyMeshSupport` 按 geoPath 缓存解析结果（`PolyMesh` 不可变，跨模型实例共享）；
  第二个 display 引用同一 geo 时零解析成本。
- 客户端资源重载时经 Fabric `IdentifiableResourceReloadListener` 整体失效
  （注册形态逐行对照 `ClientAssetsManager` 的匿名监听器）。
- stats 日志同样按 geo 去重。

### 1.6 GUI/JEI 顶点闸门（v3 新增）

高模图标在 JEI/创造背包一屏几十个地重建，代价与第一人称相同。
`MeshGuiMaxVertices`（默认 65536；0 = 不限）超限时 GUI/FIXED/HEAD 只画立方体
（纯 mesh 枪会不可见，日志提示一次）。

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

## 3. 配置（`tacz-client.toml` 的 `[mesh_loader]`）

| 键 | 默认 | 含义 |
|---|---|---|
| `MeshEnable` | true | 总开关 |
| `MeshGpuBaking` | true | GPU 静态烘焙（第一人称 + 世界）。异常时当次会话自动关闭并回退 |
| `MeshGpuUnderShaders` | false | 实验性：光影包启用时仍走 GPU pass（枪身不接收光影包光照） |
| `MeshPolyInShadow` | false | 阴影 pass 是否画 poly（立方体枪身已提供影子形状） |
| `MeshMaxRenderDistance` | 48 | 世界上下文（第三人称/掉落物）poly 距离裁剪，0 = 不限 |
| `MeshPolyInPreview` | true | GUI/FIXED 预览是否画 poly |
| `MeshGuiMaxVertices` | 65536 | GUI/FIXED/HEAD 的顶点预算，超限只画立方体，0 = 不限 |
| `MeshLogStats` | true | 加载时打骨骼/顶点统计（按 geo 去重） |

## 4. 验证清单（用户本地执行）

> 本沙箱无法运行 `./gradlew build`（无 JDK、Maven 仓库不可达）。
> 所有 MC API 引用已逐条对照：本仓库在役且已编译通过的使用点
>（`ScopeMaskRenderer` / `ScopePipRenderer` / `ScopeBodyRenderTypes` /
> `BedrockModel` / `AttachmentRender`…）+ 社区 26.2 反编译源
>（`CommandEncoder` / `RenderTarget` / `LightCoordsUtil` / `Lightmap` /
> `TextureManager` / `FeatureRenderDispatcher`）+ Iris 26.2 分支源码。
> 27 个改动 Java 文件通过结构检查（括号配平/包声明/类型声明）。
> **以下都还没做过：**

1. `./gradlew build` —— 编译是第一优先级；若与实际构建目标有出入，改动集中在
   `PolyMeshGpuRenderer`，最可能对不上的三处：5 参 `createRenderPass`（深度
   附件那个重载）、`BindGroupLayouts.SAMPLER2` 常量名、
   `gameRenderer.levelLightmap()`。前两者的在役参照分别是本仓库
   `ScopeMaskRenderer`（3 参色附件版）与 `ScopeBodyRenderTypes` 头注
   （「常量只到 SAMPLER2」），后者是 26.2 反编译源 `GameRenderer#levelLightmap`
   （另有 `lightmap()` 会按 UI 状态切表，别用错）。
2. 无 mesh 枪包：行为应与改动前完全一致（所有新路径都在
   `hasPolyMesh()` 门后）。
3. `model_type: mesh` + geo、无光影：第一人称应可见；日志应有
   `GPU-baked N bones` 与 `GPU mesh pass drew N bones (…) on hand pass, lit=true`；
   暗处枪身应变暗（不再满亮）。
4. F5 第三人称 + 世界内的同枪：日志 `on world pass`；被方块遮挡应正确。
5. 开 Iris 光影包：默认回退 CPU（日志有 shader-pack 提示行）；
   `MeshGpuUnderShaders=true` 后观察是否可见/是否双枪/光影是否缺失。
6. 换弹动画：additional_magazine 镜像仍在（consumer 路径）。
7. JEI/创造背包查看 mesh 枪图标：超 65536 顶点的枪图标只画立方体 + 一条日志。
8. 开镜（PIP rerender 模式）：世界 mesh 枪不应从主画面消失
   （redirectTarget 守卫）。
9. 两次资源重载（F3+T）后 stats 日志仍按 geo 打一遍（缓存失效正确）。

## 5. 明确不做 / 已知限制

- 目镜物体 mesh 化（上游也没有）。
- 世界 pass 多实例各自独立光照（当前共用一个烘焙档位，近似）。
- 光影包下的等价视觉（需要走 Iris 自己的管线，另开议题）。
- 半透明骨骼不走 GPU（引擎排序语义保留）。
