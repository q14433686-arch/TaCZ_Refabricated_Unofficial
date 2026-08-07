# 彻查报告：曳光弹枪口跟随 + 开镜镜头内枪体/手臂排除

> 日期：2026-08-06
> 范围：`EntityBulletRenderer`（曳光）、`GunItemRendererWrapper#cacheMuzzlePosition`（枪口偏移）、
> `BedrockAttachmentModel`/`ScopeMaskRenderer`/`ScopeBodyRenderTypes`（镜内裁剪）、
> `LeftHandRender`/`RightHandRender`（第一人称手臂）、`CameraSetupEvent`（FOV 双动力学）。
> 依据：本仓库当前分支代码（含 r25~r52 各轮注释），`docs/COMPAT_AND_ROADMAP.md` 第九节。

---

## 0. 结论速览

| 现象 | 根因定性 | 关键代码位置 |
|---|---|---|
| 曳光弹起点不跟随枪口 | ① 子弹实体出生点在**眼睛**而非枪口（上游设计），第一人称"从枪口出"全靠渲染期补偿；② 该补偿读到的枪口偏移是**上一帧**手部 pass 缓存的（帧序：实体先于手部），转头/开镜/后坐动画期间必然滞后；③ 补偿随距离 50 格线性衰减，中远距离起点介于"枪口"与"子弹"之间 | `EntityBulletRenderer.renderTracerAmmo`（L107 起）、`GunItemRendererWrapper.cacheMuzzlePosition`（L252） |
| 开镜镜头内能看到枪体、手臂 | 掩码裁剪（`scope_body.fsh` 的 discard）**只挂在瞄具配件模型（BedrockAttachmentModel）上**；枪身模型（`BedrockGunModel`）与第一人称手臂（`LeftHandRender/RightHandRender`）仍走普通 `entityCutout`/皮肤管线，不参与屏幕空间 discard → 目镜圆孔内透出枪身/手臂 | `GunItemRendererWrapper.renderFirstPerson`（L181）、`BedrockAttachmentModel.resolveBodyRenderType`（L706）、`ScopeBodyRenderTypes` |

两条链路有一个共同的技术背景：**26.2 无 stencil**，上游 1.21.1 用 stencil 做的事在这里全部退化为"屏幕空间掩码纹理 + shader discard"，而任何没接这条管线的几何都不会被裁。

---

## 1. 现象 A：曳光弹渲染不跟随枪口

### 1.1 全链路时序（一帧内）

```
┌─ 服务器 ─────────────────────────────────────────────────────┐
│ ModernKineticGunScriptAPI.shootOnce (L189)                   │
│   new EntityKineticBullet(world, shooter, ...)               │
│     → 出生点 = shooter.getEyeY() - 0.1F   ← 眼睛下方 0.1 格，不是枪口 │
│   doBulletSpread → shootFromRotation(pitch, yaw, speed)      │
│   ServerMessageGunFire 发给客户端（仅音效/事件，无弹道数据）      │
└──────────────────────────────────────────────────────────────┘
┌─ 客户端 每帧 ────────────────────────────────────────────────┐
│ ① Level pass（先）                                           │
│    LevelRenderer.submitFeatures → EntityBulletRenderer.submit│
│      → renderTracerAmmo（第一人称分支）                        │
│        读 GunItemRendererWrapper.muzzleRenderOffset  ← ★上一帧值│
│        worldOffset = muzzleOffset.rotate(camera.rotation())   │
│        poseStack.translate(worldOffset × offsetReducer)       │
│ ② Hand pass（后）                                            │
│    ItemInHandRendererMixin.tacz$submitArmWithGun (HEAD,cancel)│
│      → GunItemRendererWrapper.renderFirstPerson               │
│          → cacheMuzzlePosition 更新 muzzleRenderOffset        │
│            （沿 muzzleFlashPosPath 节点链算视图空间坐标,       │
│              z 乘 tan(itemFov/2)/tan(levelFov/2)）            │
└──────────────────────────────────────────────────────────────┘
```

要点：**①发生在②之前**（文档第九节已确认），所以曳光渲染时 `muzzleRenderOffset` 永远是上一帧手部 pass 的产物。

### 1.2 实现逻辑拆解（`EntityBulletRenderer.renderTracerAmmo`）

非第一人称：无补偿，曳光直接从子弹实体坐标画起（即从"眼睛"方向出来——上游原生表现）。

第一人称（`isFirstPerson` 且 `RenderConfig.FIRST_PERSON_BULLET_TRACER_ENABLE`）：

```java
offsetReducer = max(0, (50.0 - disToEye)) / 50.0;          // 0~50 格线性衰减
worldOffset   = globalMuzzleOffset.rotate(camera.rotation()); // 视图空间 → 世界空间
poseStack.translate(worldOffset × offsetReducer);            // 起点 = 子弹位置 + 枪口偏移
```

- `globalMuzzleOffset` 是视图空间常量（前方 -Z 约 1.8 格、略偏右下），r25 用 86 个日志样本验证过它在 307° 偏航跨度上几乎不动 → 判定为视图空间，用 `camera.rotation()`（视图→世界）旋转后与实体矩阵（世界坐标，`poseBefore ≡ bulletPos - eye`）相加。
- 语义是：**近距离把曳光"锚"到枪口，远距离淡出到子弹真实位置**。
- `bullet.getFirstPersonRenderOffset()` 只存首帧缓存，现在**仅作调试对照**（`fpOffsetBefore/After`），不参与定位。

### 1.3 为什么火光对、曳光不对（结构性差异）

- `MuzzleFlashRender` 与枪械模型在**同一个手部 pass、同一条变换链**内渲染 → 天然贴着 `muzzleFlashPosPath`，永远在枪口。
- 曳光是**实体路径**，且实体出生点在眼睛，想贴枪口只能靠"视图偏移 + 跨帧旋转"这层补偿 → 所有误差都出在这层补偿上。

### 1.4 已确认的根因与剩余缺陷（按影响排序）

1. **1 帧滞后（文档已确认）**：`muzzleRenderOffset` 是上一帧手部 pass 的值。静止射击影响小（枪口视图坐标几乎不变）；**转头、开镜/关镜过渡、后坐动画、跳跃摆动期间**，枪口视图偏移每帧都在变，曳光起点就会相对枪口漂移。
2. **FOV 因子错帧**：`cacheMuzzlePosition` 对 z 乘 `tan(itemFov/2)/tan(levelFov/2)`。item/world 两套 FOV 是独立动力学（`CameraSetupEvent.ITEM_MODEL_FOV_DYNAMICS` / `WORLD_FOV_DYNAMICS`），开镜时二者分离；用上一帧缓存的 z 因子 + 当帧旋转 → 开镜过渡帧前后错位。
3. **50 格线性衰减**：0~50 格内起点 = 子弹位置 + 部分枪口偏移；子弹飞出 10~20 格后起点就明显"不在枪口也不在子弹上"。这是有意的平滑（防"从胸口出"观感），但也是"中距离起点发飘"的来源。
4. **首帧无枪口数据**：子弹第一帧渲染时（甚至开火后第一个 Level pass），`muzzleRenderOffset` 可能是开火前 idle 姿态的值；若开火瞬间伴随后坐/开镜动画，首帧起点偏差最大。
5. **Iris/Sulkan 路径未闭环**：`cacheMuzzlePosition` 依赖 `renderFirstPerson` 被执行；Iris hand path（`HandRenderer` 直呼 `ItemInHandRenderer`）下该路径是否一致、`camera.rotation()` 与枪械渲染所用矩阵是否同一套，代码里只有 `isHandRendererActive()` 的判定，没有针对曳光定位的专项处理。
6. **第三人称无补偿**（上游设计）：别人看你的曳光/你看别人曳光，起点都在眼睛。若这是新反馈来源，需另做（把枪口世界坐标随 spawn 数据下发）。

### 1.5 修复方向（供后续实施）

- **方向 1（推荐）**：开火时（客户端收到射击指令/本地预测）直接算出**世界空间枪口位置**，作为子弹的 spawn 附加数据或 persistent data 随实体下发；渲染时 `translate(muzzleWorld - eye)`，彻底消灭"视图偏移+跨帧旋转"补偿与 1 帧滞后。服务器端实体仍从眼睛生成（保弹道与准星一致），仅客户端渲染起点用枪口。
- **方向 2（低成本止血）**：把 `cacheMuzzlePosition` 的结果写成"上一帧末尾手部 pass + 当帧相机旋转"的当前值，并把 `offsetReducer` 的 50 格阈值按反馈调小（35~40），衰减曲线可换二次；同时把首帧特判（`tickCount <= 1` 时强制 `offsetReducer = 1`）补上。
- **验证手段（现象 = 验收）**：`RenderConfig.TRACER_DEBUG=true` + `TRACER_DEBUG_GUN=<枪id>`，静止连续射击看 `globalMuzzle`/`fpWorldOffset` 是否恒定；转头/开镜时看 `camera` 与 `poseAfterOffset` 的差值是否等于期望枪口偏移。

---

## 2. 现象 B：开镜镜头内不排除枪体、手臂

### 2.1 掩码系统全链路（一帧内）

```
Hand pass 内：
  submitHandsWithItems
    ├─ GunItemRendererWrapper.renderFirstPerson (枪身, entityCutout ← 不裁剪!)
    │    └─ BedrockGunModel.submit
    │         ├─ scope 配件 → AttachmentRender.submitAttachment
    │         │    └─ BedrockAttachmentModel.submit
    │         │         ├─ registerOcularMaskGeometry()
    │         │         │    沿目镜祖先链套变换, 递归收集 cube
    │         │         │    bakedPose = ModelView(提交时刻) × pose
    │         │         │    → ScopeMaskGeometry.add(entry)
    │         │         ├─ resolveBodyRenderType() → ScopeBodyRenderTypes.clipped(贴图)
    │         │         │     （仅当 firstPerson && 有目镜 && aiming>0.02 && 掩码可用）
    │         │         └─ 准星 → reticle / reticleEmissive（反向裁剪）
    │         └─ LeftHandRender / RightHandRender (手臂 ← 不裁剪!)
    │              └─ AvatarRenderer.renderLeftHand/RightHand (皮肤管线)
  └─ renderAllFeatures
       └─ FeatureRenderDispatcherMixin（executeSolid 边界, 仅 inHandPass）
            └─ ScopeMaskRenderer.renderAtPhaseBoundary()
                 → 把当帧目镜几何画进离屏 ScopeMaskTarget
                   （白=镜内, 绿通道=开镜进度; ModelView 传 identity）
       └─ solid/translucent … 镜身/枪身/手臂按各自 RenderType 绘制
            └─ scope_body.fsh: gl_FragCoord 采样 ScopeMaskSampler
                 insideOcular → discard（镜身） / !insideOcular → discard（准星）
```

### 2.2 关键事实：裁剪是"屏幕空间"的，且只挂给瞄具配件

- `scope_body.fsh` 的裁判断是 **`gl_FragCoord` 屏幕像素**是否落在目镜投影（掩码白色区）内，**与被裁对象是谁、模型矩阵是什么无关**。
- 但目前只有 `BedrockAttachmentModel` 的镜身/准星通过 `resolveBodyRenderType/reticleRenderType` 换上了这套 RenderType；
- `GunItemRendererWrapper.renderFirstPerson` 给**整支枪**用的是 `RenderTypes.entityCutout(display.getModelTexture())`（L236）；
- 手臂走 `AvatarRenderer` 的皮肤管线（`RenderHelper.renderFirstPersonArm`）。

→ 目镜圆孔内：镜身被 discard 露出放大的世界，但**枪身部件（机匣顶部/导轨/护木等）与手臂（握持位置贴近镜筒）没有 discard**，于是从圆孔里透出来。不同枪包模型布局不同，露出的严重程度不同——这解释了"总有玩家反馈"且反馈分散。

### 2.3 放大此现象的辅助因素

1. **开镜过渡期**：掩码从 `aimingProgress > 0.02` 起登记，绿通道渐进收缩（`scope_body.fsh` 的距离场收缩只在 progress<0.999 时生效）——收缩只作用于"镜身"的 discard 边界，枪身/手臂没有对应的渐进，过渡帧边缘对不齐更明显。
2. **FOV 双动力学**：世界 FOV 按 `sqrt(zoom)` 缩放、枪模 FOV 按镜的 `views_fov` 缩放，两者独立平滑；镜内世界与镜外枪身/手臂的相对缩放不同步时，"露出的枪身"会被放大，观感更糟。
3. **Iris/Sulkan 回退**：`IrisCompat.shouldDisableScopeMaskUnderShaderPack()` 在 Sulkan 存在时返回 true → 整条掩码链路关闭，镜身也不裁（已知降级"镜内可见镜筒内壁"）。此时"枪体/手臂不排除"依旧存在，但属于同一开关的降级表现。

### 2.4 修复方向（供后续实施）

- **方向 1（推荐，利用"屏幕空间裁剪与对象无关"这个性质）**：
  - 开镜且掩码可用时，把 `GunItemRendererWrapper.renderFirstPerson` 的枪身 RenderType 换成 `ScopeBodyRenderTypes.clipped(枪贴图)`；
  - 手臂（`LeftHandRender/RightHandRender`）的提交也换成同款裁剪 RenderType（需包装 `AvatarRenderer` 的皮肤管线，注意它内部用的 RenderType 与贴图，构造等价裁剪版）；
  - 这样枪身/手臂与镜身共用同一张当帧掩码，圆孔内全部被 discard，且开镜渐进的屏幕空间收缩行为完全一致。
  - 风险点：`AvatarRenderer` 内部 RenderType 由引擎创建，包装时需保证 `SCOPE_MASK` define + `ScopeMaskSampler` 绑定齐全（参考 `ScopeBodyRenderTypes.create`），并同步 `ensureIrisCompatibility()` 的 pipeline assignment。
- **方向 2（保守，若方向 1 有兼容风险）**：完全开镜（`aimingProgress` 接近 1 且有目镜）时隐藏枪身与手臂（`gunModel.setRenderHand(false)` + 跳过枪身提交），只留瞄具——比"漏枪体"干净，但丢失"镜外镜身"观感，与上游不一致。
- **回退联动要求**：裁剪开关（`SCOPE_MASK_ENABLE`、shader 回退、`maskable`）必须让"镜身/枪身/手臂/准星"**同进同退**，否则会出现"镜身裁了枪身没裁"的半边状态（`BedrockAttachmentModel` 已有这个约定，扩散到枪身/手臂时要保持）。
- **验证手段（现象 = 验收）**：`SCOPE_MASK_DEBUG=true` 看左上角掩码预览（白色形状应随枪移动、与目镜重合）；开镜后确认圆孔内只看到放大的世界，无枪身/手臂轮廓；分别测：静止、开镜过渡中、移动/跳跃、Iris/Sulkan 开启/关闭。

---

## 3. 两个现象的共同技术背景

1. **26.2 渲染架构是"提交-绘制"两阶段**：模型在 `submit` 里只登记（含把提交时刻 ModelView 烘焙进掩码矩阵），真正绘制在 `renderAllFeatures`。所有跨阶段引用（掩码纹理、枪口偏移）都要面对"提交时 vs 绘制时"矩阵/状态差异——曳光的 `muzzleRenderOffset`、掩码的 `bakedPose` 都是这个架构下的产物。
2. **帧序固定**：Level pass（实体/曳光）先于 Hand pass（枪械/掩码），任何"第一人称枪口相关"的实体侧渲染都天然滞后一帧。
3. **无 stencil**：上游所有 stencil 语义（镜身区域二分、曳光锚点）都必须换成"屏幕空间掩码/视图偏移"的等价实现，改一处漏一处就产生这两类反馈。

---

## 4. 关键文件索引

| 文件 | 职责 | 关键行 |
|---|---|---|
| `client/renderer/entity/EntityBulletRenderer.java` | 曳光渲染 + 调试日志 | `renderTracerAmmo` L107~；`debugTracer` L300~ |
| `client/renderer/item/GunItemRendererWrapper.java` | 第一人称枪械渲染、枪口偏移缓存 | `renderFirstPerson` L181；`cacheMuzzlePosition` L252 |
| `entity/EntityKineticBullet.java` | 子弹实体（出生点=眼睛-0.1） | 构造 L170~；`readSpawnData` |
| `client/event/CameraSetupEvent.java` | 世界/枪模双 FOV 动力学 | `applyScopeMagnification`、`applyGunModelFovModifying` |
| `client/model/BedrockAttachmentModel.java` | 瞄具渲染、目镜几何登记、镜身/准星裁剪 RenderType 选择 | `submit` L505~；`registerOcularMaskGeometry` L649~；`resolveBodyRenderType` L706 |
| `client/render/scope/ScopeMaskRenderer.java` | 阶段边界绘制掩码 target | `renderAtPhaseBoundary` |
| `client/render/scope/ScopeBodyRenderTypes.java` | 带 `SCOPE_MASK` 的 pipeline / RenderType | `clipped`/`reticle`/`reticleEmissive` |
| `client/render/scope/ScopeMaskGeometry.java` | 当帧目镜几何收集器（提交时刻烘焙矩阵） | `add`/`clear` |
| `client/render/scope/ScopeMaskTarget.java` / `ScopeMaskTextureHandle.java` | 离屏 target 生命周期、伪装注册纹理 | — |
| `client/model/functional/LeftHandRender.java` / `RightHandRender.java` | 第一人称手臂（不裁剪） | `extract`/`render` |
| `util/RenderHelper.java` | 手臂渲染入口（AvatarRenderer） | `renderFirstPersonArm` L57 |
| `mixin/client/FeatureRenderDispatcherMixin.java` | 阶段边界注入点（掩码 pass 时机） | `tacz$scopeMaskAtPhaseBoundary` |
| `mixin/client/GameRendererMixin.java` | `inHandPass` 标志（掩码只在手部 pass 画） | HEAD/RETURN 注入 |
| `mixin/client/ItemInHandRendererMixin.java` | 第一人称枪械渲染接管（取消 vanilla 手臂） | `tacz$submitArmWithGun` |
| `config/client/RenderConfig.java` | 开关：`SCOPE_MASK_ENABLE`(默认 true)、`SCOPE_MASK_DEBUG`(默认 false)、`FIRST_PERSON_BULLET_TRACER_ENABLE`(默认 true)、`TRACER_DEBUG`(默认 false) | L64~L96 |
| `resources/assets/tacz/shaders/core/scope_body.fsh` | 屏幕空间掩码 discard + 开镜渐进 | `insideOcular` 判定 |
