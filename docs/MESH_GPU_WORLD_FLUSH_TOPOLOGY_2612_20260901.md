# Mesh GPU 世界烘焙消费点移植缺口（26.1.2 独有）：证据链与修复 — 2026-09-01

触发：维护者实机反馈（config 持久化修复 PASS 后）两个新症状：
1. **不开光影**：高模枪的掉落物等第三人称形态**不会被烘焙**（应进 GPU 路径却走了 collector）；
2. **开光影**：第三人称形态**会**烘焙，但几何**相对玩家视界空间静止**（转视角时贴着屏幕走）——
   「这个 bug 1.21.11 一开始就防范了，26.2 最开始做的时候才有」。

证据：CI javap 探针 round 4（`9626fe7`，日志随 ci-log 提交在 `build-reports/compile-java.log`），
对 26.1.2 merged jar 逐方法反汇编。**运行期修复效果未验证（等实机）。**

---

## 0. 移植完整性审计（回应「还有多少类似问题」）

`cn/sh1rocu/tacz/compat/meshloader/` 全包 + 相关 mixin 对 1.21.11 源线（`refs/coord-1211`）
逐一 diff：

| 文件 | 与 1211 差异 | 结论 |
|---|---|---|
| `core/PolyMesh.java`、`render/ShaderStateTracker.java`、`model/TaczPolyMesh*` ×4、`core/PolyMeshSupport.java`、`mixin/GunDisplayInstanceMixin.java` 等 | **0 行** | 逐字一致 |
| `render/PolyMeshGpuRenderer.java`（190 行 diff） | 全部为 26.1.2 API 适配（`DepthStencilState`/`ColorTargetState` 聚合形态替 `withDepthTestFunction` 族、`LightTexture.pack` 内联、`DefaultVertexFormat.ENTITY`、PIP 标志接回） | 逻辑等价 |
| `render/ScreenRenderTracker.java`（68 行） | `ScreenEvents.beforeRender/afterRender` → `beforeExtract/afterExtract`（26.1 fabric-screen 纪元更名，同一事件） | 逻辑等价 |
| `config/PolyRenderPolicy.java`（23 行） | `LightTexture.pack` 适配 | 逻辑等价 |
| `render/PolyMeshGpuRenderer.renderAtWorldFlush` 的**调用点** | `FeatureRenderDispatcherMixin` 挂 `renderAllFeatures` RETURN —— **这就是缺口**（见 §1） | 唯一实质移植缺陷 |

结论：烘焙本体（VBO/光照量化/矩阵合成/LRU/Screen 闸门/手部路径）移植完整；缺陷是
**世界表的消费点拓扑**——26.1.2 的世界 flush 站位与 1.21.11 不同构，而消费点 mixin
照搬了 1.21.11 的挂点。

## 1. 根因（26.1.2 的 flush 拓扑 vs 1.21.11）

**1.21.11**：`LevelRenderer` 的 frame-graph 主 pass 节点内调 `renderAllFeatures()`
（1211 侧 mixin javadoc：`popPush("renderFeatures") → renderAllFeatures → endLastBatch`）。
消费点挂它的 RETURN ⇒ 在 `levelRenderActive` 窗口内、MV 槽 = vanilla 世界批次 draw 当刻
现取的那份 —— 1211 一开始就防住了「MV 取自别的时刻」。

**26.1.2**（探针 round 4 字节码）：

```
lambda$addMainPass$0（主 pass，在 LevelRenderer.renderLevel 内执行）:
  @220 submitEntities → @273 renderSolidFeatures()      ← 世界实心几何在此 draw
  @390 renderTranslucentFeatures()  @522 particles  @537 clearSubmitNodes
  （主 pass 内 outputColorTextureOverride 恒 null；putstatic 只在
   lambda$addLateDebugPass$0 @34/@49/@111/@115 = gizmo 节点）

renderAllFeatures() 只剩两个站位:
  @281 ItemInHandRenderer.renderHandsWithItems 尾部      ← inHandPass 门拒收
  @570 GameRenderer.renderLevel 尾部（@517 renderItemInHand 之后）
                                                          ← levelRenderActive==false 拒收
```

旧消费点（`renderAllFeatures` RETURN）在这两个站位全部落空：

- **vanilla**：@570 被拒且**不记存活证明** ⇒ `worldFlushAlive()` 恒假 ⇒
  `shouldSubmitGpuWorld()` 恒假 ⇒ 一切世界语境 submit 静默回退 collector =
  **症状①（不烘焙）**；
- **Iris 26.1**：把手部渲染搬进 `LevelRenderer` 内部（其 flush 在 redirect 窗口内、
  override==null、`levelRenderActive==true` ⇒ 全部闸门放行）⇒ 世界表在**手部时刻**
  被消费，`MV 槽 = 手部的模型视图`，乘上世界实体的相机相对 pose ⇒ 几何粘在视界空间 =
  **症状②**。这正是 1211 一开始防范、26.2 早期踩过的同一类「MV 取自别的时刻」病。

## 2. 修复

- `FeatureRenderDispatcherMixin`：注入点 `renderAllFeatures` RETURN →
  **`renderSolidFeatures` RETURN**（26.1.2 世界实心 flush 真身，主 pass 内）。
  同一时刻、同一 MV 槽、同一输出目标与深度状态 —— 与 collector
  「pose 烘进顶点 + 同一份 MV」逐帧等价；实心先于 translucent 的顺序也与 vanilla 一致。
- 拒收矩阵全部由既有闸门自动覆盖（无需新代码）：手部尾/@570 的 `renderAllFeatures`
  经其内部第一步 `renderSolidFeatures` 触发本钩子，分别被 `inHandPass` /
  `!levelRenderActive` 拒收且不记存活；Iris 的 in-level 手部点被
  `worldConsumedFrame` 首消费守卫跳过（主 pass 必然先跑）；Iris 阴影 = `isRenderShadow`
  拒收；gizmo 节点 = override 门拒收（探针证实那是 26.1.2 唯一设 override 的站位）；
  PIP 窄遍 = `insideScope` 画但不清表（既有裁定）；GUI = 窗口外 + `ScreenRenderTracker`。
- `PolyMeshGpuRenderer`：`renderAtWorldFlush` javadoc 重写为新拓扑；`drawList` 的
  MV 注释区分手部/世界两路径（`handMv` → `flushMv`）。门逻辑与消费/清表语义不变。

## 3. 验收（实机前全标「未验证」）

1. 不开光影：掉落物/展示框/第三人称手持的高模枪与第一人称一致地走 GPU 烘焙
   （`[TacZMeshLoader] GPU mesh pass drew ... world flush` 日志出现）。
2. 开光影：同上，且转动视角时第三人称枪**钉在世界里**（不随视界平移/旋转）。
3. 手部路径回归：第一人称烘焙行为与修复前一致（消费点未动）。
4. PIP 开镜、Iris 阴影、GUI 内嵌 3D：无世界表泄漏（枪不出现在镜内错误位置/阴影里/GUI 里）。
5. 光影切换瞬间：格式世代守卫照常重烘，无「拉伸的枪模」。
