# 曳光弹跟随枪口 & 开镜镜内枪体/手臂 —— 代码实现彻查

**调查人**：Arena Agent
**日期**：2026-08-07
**方法**：只读实现，不采信注释结论。逐文件通读字节码可核实的关键链路：
曳光弹渲染链、枪口偏移采集、开镜遮罩/准星渲染链、FOV 双通道、渲染顺序 mixin。
**重要前置**：`git log` 只有单个 commit；代码注释里引用的"第 25/35/45 轮"在
`docs/archive`（只到第 22 轮）里不存在 —— 代码领先文档一大截。
因此本文全部以**当前源码**为准，注释里的结论只当线索、需用代码自证。

---

## 一、曳光弹"跟随枪口"

### 1.1 数据流全貌

**① 子弹从"眼部"生成（世界坐标，不是枪口）**
`EntityKineticBullet` 构造函数（`src/main/java/com/tacz/guns/entity/EntityKineticBullet.java`，约 L165-172）：

```java
double posX = throwerIn.xOld + (throwerIn.getX() - throwerIn.xOld) / 2.0;
double posY = throwerIn.yOld + (throwerIn.getY() - throwerIn.yOld) / 2.0 + throwerIn.getEyeHeight();
double posZ = throwerIn.zOld + (throwerIn.getZ() - throwerIn.zOld) / 2.0;
this.setPos(posX, posY, posZ);
```

曳光弹挂在**子弹实体**上渲染（`EntityBulletRenderer#submit` 里 `bullet.isTracerAmmo()` → `renderTracerAmmo`）。
也就是说曳光弹的"真实"轨迹是挂在世界坐标里的子弹上；**没有**在服务端从枪口发弹。

**② 第一人称枪械渲染时采集"枪口在视图空间的位置"**
`GunItemRendererWrapper`（`src/main/java/com/tacz/guns/client/renderer/item/GunItemRendererWrapper.java`）：

- L58 `public static final Vector3f muzzleRenderOffset = new Vector3f();` —— **静态字段**，每帧手部渲染时覆盖。
- `renderFirstPerson`（约 L205-249）：`gunModel.submit(...)` 之后调用 `cacheMuzzlePosition(poseStack, gunModel)`。
- `cacheMuzzlePosition`（L252-274）沿 `muzzleFlashPosPath`（即枪械模型里的 `muzzle_flash` 定位组，
  `GunModelConstant.MUZZLE_FLASH_ORIGIN_NODE`）逐个 `bedrockPart.translateAndRotateAndScale(poseStack)`，
  读出 `poseStack.last().pose()` 的位移 `m30/m31/m32`，即**视图空间**里枪口相对相机原点的偏移；并施加 FOV 因子：

```java
muzzleRenderOffset.set(
    pose.m30(), pose.m31(),
    pose.m32() * Math.tan(itemRenderFov / 2 * Math.PI / 180)
               / Math.tan(levelRenderFov / 2 * Math.PI / 180));
```

只对 **z** 乘 `tan(itemFov/2)/tan(levelFov/2)`。这一步是**开镜时枪口定位正确的关键**
（原因见 1.3 ③）。

**③ 曳光弹渲染时把视图偏移转回世界、平移到子弹 poseStack**
`EntityBulletRenderer#renderTracerAmmo`（`src/main/java/com/tacz/guns/client/renderer/entity/EntityBulletRenderer.java`，
约 L99-150）：

```java
boolean isFirstPerson = ...cameraType.isFirstPerson() && shooter instanceof LocalPlayer;
...
Vector3f globalMuzzleOffset = new Vector3f(GunItemRendererWrapper.muzzleRenderOffset);
...
if (isFirstPerson) {
    // 首帧缓存（仅调试对照用）
    Vector3f offset = bullet.getFirstPersonRenderOffset();
    if (offset == null) { offset = new Vector3f(globalMuzzleOffset); ...bullet.setFirstPersonRenderOffset(offset); ... }
    double offsetReducer = Math.max(0, (50.0 - disToEye)) / 50.0;
    // 视图空间 -> 世界空间
    Vector3f worldOffset = new Vector3f(globalMuzzleOffset).rotate(camera.rotation());
    poseStack.translate(worldOffset.x()*offsetReducer, worldOffset.y()*offsetReducer, worldOffset.z()*offsetReducer);
}
...
poseStack.mulPose(...bullet 朝向...);
poseStack.translate(0, isFirstPerson ? 0 : -0.2, trailLength / 2.0);
poseStack.scale(width, width, (float) trailLength);
```

`disToEye = bulletPosition.distanceTo(eyePosition)`；`offsetReducer` 随子弹飞远把枪口补偿线性淡出
（`disToEye>=50` 后减到 0），把"从枪口出发"平滑过渡到"挂在子弹实体上"。

### 1.2 坐标归属（这是整段最容易被看错的地方）

- 实体 poseStack 是世界空间：`EntityBulletRenderer` 的调用栈是 LevelRenderer → submitEntities →
  `EntityRenderDispatcher#submit`，只 `translate(entity.pos - camera.pos)`，无 mulPose。
  所以渲染时 poseStack 原点 ≈ 相机位置。
- `muzzleRenderOffset` 是**视图空间**偏移（手部渲染时的 poseStack 停留在视图空间，
  `renderFirstPerson` 开头用 `mulPose(XP, xRot*-0.1)/mulPose(YP, yRot*-0.1)` 把原版的 bob 抵消）。
- 因此要把偏移放回世界，必须乘 `camera.rotation()`。`Camera#setRotation` 用
  `rotationYXZ(PI - yRot*DEG, -xRot*DEG, 0)` 构造，`FORWARDS(0,0,-1).rotate(rotation)` 得到世界前向 ——
  这正是"视图→世界"，**直接 rotate，不能 conjugate**。当前代码方向正确。

### 1.3 四个必须知道的点 / 隐患

**① 渲染顺序：曳光弹其实是"上一帧的枪口"**（这是注释与实现互相矛盾的地方，需要重点标注）

- `GameRendererMixin#tacz$endHandPass`（`src/main/java/com/tacz/guns/mixin/client/GameRendererMixin.java`）证实
  `renderItemInHand` 是独立方法，在 `GameRenderer.render` 里**世界渲染之后**才调用。
- 子弹实体在世界 pass 提交，`muzzleRenderOffset` 只在**之后**的手部 pass 被 `cacheMuzzlePosition` 覆盖。
- 所以曳光弹读到的是**上一帧**的 `muzzleRenderOffset`。
- `EntityBulletRenderer` 顶部"第 25 轮"注释声称"枪械模型渲染与本次实体提交在同一帧、同一相机下完成，
  实时取用即可始终贴合枪口" —— 这与上面的渲染顺序**不符**：实体在前、手部在后。
  实测症状会是：**静止/慢速转向时贴着枪口，快速甩镜 / 开镜过渡（手部 FOV 与 pose 剧烈变化）时起点滞后一拍。**
  （若想一帧对齐，需在实体 pass 之前先跑一次手部定位，或把枪口偏移按当前相机/进度重算。）

**② 静态缓存 `firstPersonRenderOffset` 实际不参与定位**
`bullet.getFirstPersonRenderOffset()` 只在首帧写入；真正的平移用的是**当帧** `globalMuzzleOffset`
（静态字段）。缓存值 + `setCameraXRot/YRot` 只进 `debugTracer` 日志。即"缓存"这层逻辑已基本是死代码，
只留调试对照。

**③ FOV 因子必须保留（开镜错位的根）**
开镜时有两套独立 FOV：`applyScopeMagnification`（`CameraSetupEvent` L85-115）改**世界 FOV**
（`WORLD_FOV_DYNAMICS`，按放大倍率缩小）；`applyGunModelFovModifying`（L117-150）改**手部物品 FOV**
（`ITEM_MODEL_FOV_DYNAMICS`，朝瞄具 `viewsFov`/`getZoomModelFov` 插值）。二者开镜时分离。
`cacheMuzzlePosition` 用两者比值缩放 z 才能把视图空间枪口换算到世界尺度 —— 删掉就会开镜后前后错位。
当前代码保留了它。

**④ "曳光弹不从枪口射出"是设计，不是 bug**
服务端从眼部生成子弹（上游 1.21.1 同款）。第一人称只是**视觉补偿**把渲染起点拉回枪口；
第三人称/他人视角**没有任何补偿**（`isFirstPerson` 分支外没有平移），曳光弹从眼部/胸口出。
若玩家要"真从枪口射出"，需改弹道生成点并同步服务端校验，那是另一回事。

---

## 二、开镜镜内"不排除枪体、手臂"

### 2.1 数据流全貌

**① 开镜定位（把枪移到瞄具位）**
`FirstPersonRenderGunEvent#applyFirstPersonPositioningTransform`（`.../client/event/FirstPersonRenderGunEvent.java`
约 L126-215）：

- 未装瞄具 → `aimingNodePath = model.getIronSightPath()`（机瞄）；
- 装了瞄具 → `scopePosPath + scopeViewPath`（瞄具定位组 + 视野定位组），组合镜再按 `views[]` 选当前镜组。
- `getPositioningNodeInverse(...)` 求逆变换，`applyMatrixLerp` 按 `aimingProgress` 在 idle 位与瞄具位之间插值。
- 结果：完全开镜时相机"钻进"瞄具目镜，枪体向下/向后延伸。

**② 枪体与手臂 —— 普通 RenderType，永不被镜内掩码裁剪**
`GunItemRendererWrapper#renderFirstPerson`（L233-235）：

```java
RenderType renderType = display.enablesTransparency()
        ? RenderTypes.entityTranslucent(display.getModelTexture())
        : RenderTypes.entityCutout(display.getModelTexture());
gunModel.submit(...);
```

手臂走 `RightHandRender` / `LeftHandRender`（`.../client/model/functional/`），
用 `RenderHelper.renderFirstPersonArm` + vanilla 手臂管线。
**两者都是普通 RenderType，不属于 `ScopeBodyRenderTypes`，不含 `SCOPE_MASK` shader 分支**，
因此**开镜时枪体、手臂既不会被裁掉，也没有任何东西盖住它们。**

**③ 镜身与准星的掩码裁剪（只作用于瞄具自己，不作用于枪/手）**
`BedrockAttachmentModel#submit`（`.../client/model/BedrockAttachmentModel.java` 约 L497-637）：

- 第一人称且开镜进度 `> AIM_CLIP_START(0.02)` 且无光影冲突时，
  `registerOcularMaskGeometry(poseStack)`（L657）把"当前激活镜组"的**目镜几何**登记进离屏掩码；
- `ScopeMaskRenderer.renderAtPhaseBoundary()`（`ScopeMaskRenderer.java` L168）在阶段边界
  （`FeatureRenderDispatcherMixin`，`executeSolid` 之前）把目镜投影画进掩码 target：
  白色 = 目镜盖到，绿通道 = 开镜进度；
- 镜身用 `ScopeBodyRenderTypes.clipped(texture)`，`scope_body.fsh` 里
  `if (insideOcular) discard;` → **目镜投影内不画镜身**（开个"洞"让世界透出来）；
- 准星用 `ScopeBodyRenderTypes.reticle(...)` 反向：`if (!insideOcular) discard;` → 只在镜内画。

即：掩码裁剪的"对象集合" = **镜身 + 准星**。**枪体和手臂不在这个集合里。**

**④ 目镜"黑片"**
`shouldDrawOcularBlackout`（L844）：纯筒镜 → 画黑片；纯红点 → 不画；组合镜 → 只给筒镜组画。
这层黑片是**目镜自己那块几何**经 `super.submit` 正常渲染，只盖目镜那一小块区域。

### 2.2 关键结论：为什么"镜内不排除枪体、手臂"

对比上游 1.21.1（`docs/archive/SCOPE_UPSTREAM_TRUTH_2026-07-27.md` §3 的 renderScope 顺序）：

```
1  enableItemEntityStencilTest() + clearStencil(0)
2  ocular_ring        : 正常画外环
3  renderOcularStencil: colorMask(false) + stencilOp(REPLACE)  目镜只写模板
4  scope_body         : stencilFunc(EQUAL,0)                   只在圆【外】画镜身
5  renderOcularAndDivision: 圆形 INVERT → 圆外目镜=黑遮罩, 圆内才画 division(分划大平面)
6  disableItemEntityStencilTest()
7  super.render(...)                                          → 其余枪械部件(含枪体)
```

- 上游靠**屏幕空间的圆形 stencil**（`rad = 80 * modifier * aimingProgress`，圆心=目镜投影中心）
  做"圆内 vs 圆外"的强制二分；
- 步骤 5 的 `division` 是**一张覆盖整个目镜的大平面**（`scope_acog_ta31` 第一块 cube 52×52、
  UV 128×128），在圆内以 `disableDepthTest` 画，**把镜头正中的枪体/手臂物理盖住**；
- 加上枪包把 `scope_view` 定位组调到"枪体在圆外"。

**本 fork 只复刻了"镜身丢 / 准星留"这半边（shader discard），没有等价于上游圆外黑遮罩 + 镜内 division
大平面的"盖住层"。**

具体差异：

| 项 | 上游 1.21.1（stencil） | 本 fork（26.2 shader discard） |
|---|---|---|
| 圆外枪体/手臂 | 被圆外黑色遮罩 + 镜筒盖住 | **普通渲染，不被任何东西盖** |
| 圆内(镜内)枪体/手臂 | 被 division 大平面（depth off）盖住 | **无遮挡层，枪/手若延伸到圆心就露出来** |
| 准星 | division 整根画，靠 stencil 裁成圆 | 只画分划线条（`IlluminatedReticleRenderer`/`EtchedReticleRenderer`），`EtchedReticleRenderer` 甚至把遮光板 discard 掉 |
| 枪体/手臂的 RenderType | 非裁剪 | 非裁剪（同） |

因此：**"开镜镜头内不排除枪体、手臂"的根因 = 缺少一个能盖住/裁掉枪体与手臂的屏幕空间层。**
镜身与准星被 mask 正常裁剪，但枪体和手臂既不在 mask 的裁剪集合里，也没有 division 大平面这种
"前景遮挡板"把它们挡在镜外/镜内区域之外。至于实际露多少，取决于具体枪包把 `scope_view` 定位组
调得离圆心多远 —— 模型调得越差，枪/手露进镜内的就越多。

> 注：本 fork 也**没有任何"开镜隐藏枪体/手臂"的逻辑** —— 全仓 `setRenderHand(false)` 只有
> `GunItemRendererWrapper.java:232` 一处，且仅当改装界面（`RefitTransform.getOpeningProgress()!=0`）时触发。

### 2.3 若要"排除"枪体/手臂，方向（供决策，未实现）

1. **按开镜进度隐藏/位移**（最轻、最稳）：开镜时把枪体/手臂移到镜外或淡出，
   类似 `renderHand` 门禁但按 `aimingProgress` 驱动。改动小、不碰渲染管线、无光影冲突。
2. **给枪体/手臂加"圆外盖黑"的 screen-space 层**（复刻上游圆外遮罩）：
   需要一个覆盖整个手持物、只在目镜圆内挖洞的不透明 pass —— 但 26.2 无 stencil，
   目前只有 shader discard 手段，而枪/手用的是普通 entityCutout，得新增专用管线并处理光影，
   成本高、风险高（参考 r46/r51/r52 的历史崩法）。
3. **对枪体/手臂用"圆外 discard"的 RenderType**：让枪/手只在目镜圆外画。但枪/手的掩码语义是"圆外可见"
   （正好和镜身一样），可复用 `ScopeBodyRenderTypes.clipped`。代价是开镜时枪/手在圆内完全消失，
   可能不符合"枪体在镜外可见"的预期 —— 需按产品口径定夺。

---

## 三、给玩家/决策者的"现象 → 代码"对照

| 玩家现象 | 代码根因（本文核实） |
|---|---|
| 曳光弹不严格贴枪口，尤其甩镜/开镜时 | ① `muzzleRenderOffset` 是**上一帧**手部渲染的结果，实体 pass 在前、手部 pass 在后（`GameRendererMixin` 确认）；② 它是视图空间，靠 `camera.rotation()` 转回世界，转头期间会滞后 |
| 曳光弹似乎"从胸口/眼部"出来 | 设计如此：服务端从眼部生成子弹（`EntityKineticBullet` 构造），仅第一人称本机做视觉补偿，第三人称/他人无补偿 |
| 开镜后枪体、手臂还能看见/伸进镜内 | 枪体、手臂用普通 `entityCutout`，不在 `ScopeBodyRenderTypes` 掩码裁剪集合内，也无一层盖住它们；上游靠 stencil 圆形遮罩 + division 大平面挡住，本 fork 只有镜身/准星被 mask 裁剪 |

---

## 四、附：验证/取证建议

1. 曳光弹：打开 `RenderConfig.TRACER_DEBUG`，看 `[TACZ TracerDebug]` 行里的
   `globalMuzzle` vs `camera(...)` vs `fpWorldOffset`，重点对比**快速甩镜/开镜瞬间**三者的差值
   （若 `globalMuzzle` 明显滞后于相机转向，即坐实 1.3①）。
2. 镜内枪体/手臂：切高倍镜完全开镜，若掩码正常（镜身被裁、准星在镜内），而枪/手仍露在镜内圆里，
   即坐实 2.2 —— 与 `SCOPE_MASK_ENABLE` 开关无关（那个开关只控镜身/准星的 mask，不控枪/手）。

---

## 五、本轮已落地改动（2026-08-07）

**目标：开镜镜内排除枪体 + 手臂。** 采用「discard」路线。

| 文件 | 改动 |
|---|---|
| `client/render/scope/ScopeClipHelper.java`（新增） | 统一判定「当前第一人称是否目镜掩码生效」：掩码总开关开 && 无光影接管 && 开镜进度 > 0.02 && `syncToMaskTarget()` 可用。判据与镜身/准星那条路径逐条一致。安全退化：没装瞄具/机瞄时掩码全黑 → 判定 false → 什么都不裁 |
| `client/renderer/item/GunItemRendererWrapper.java` | `renderFirstPerson` 里，**不透明枪体**在掩码生效时改用 `ScopeBodyRenderTypes.clipped(gunTexture)`（目镜圆内 discard）。translucent 枪体不裁（避免不透明裁剪管线改变透明语义） |
| `client/model/functional/LeftHandRender.java`、`RightHandRender.java` | `extract` 与 `render` 两处：掩码生效时直接不提交手臂 |

**行为：** 完全开镜后，目镜圆内的枪体被 discard（露出世界），手臂整体隐藏；圆外枪体照常。腰射/未装瞄具/机瞄/有光影时全部走回退，行为与之前一致。

### 5.1 手臂的取舍（必须知道）
手臂本应按你说的**圆外 discard** 处理，但 vanilla 的 `AvatarRenderer` 手臂路径内部硬编码
`RenderTypes.entityTranslucent(skin)`（已从字节码 strings 确认），不给外部换 RenderType。
而本仓 mixin 配置是 `defaultRequire:1` —— 一旦对 vanilla 类的注入点写错，**整个游戏启动即崩**
（正是 r46/r51/r52 那类事故）。本环境无 Java/无网，无法编译、无法实机验证，所以本轮**没有**给
`AvatarRenderer` 加 mixin，改用 mod 侧最稳的「开镜隐藏手臂」等效手段。

**若你要真正的手臂圆外 discard**（镜头最外侧还想看到持枪手），需要：
- 给 `AvatarRenderer` 加 mixin，把内部传给私有 render 方法的 RenderType 换成
  `ScopeBodyRenderTypes.clipped(skin)`（该方法描述符已从字节码确认：`(ModelPart, PoseStack,
  SubmitNodeCollector, RenderType, int, int, TextureAtlasSprite)V`）；
- 但必须在**有 Java 能编译、能进游戏**的环境里做，我不能在此提交一个未验证的 vanilla mixin。

---

## 六、曳光弹彻底重做：从枪口射出 —— 待你拍板的设计

你要的效果：「曳光弹从枪口射出、类似枪口火光」，不再是现在的「眼部发弹 + 视觉补偿」。
这需要动**服务端弹道**，不只是渲染。下面 4 个是必须你定的设计点，定了我再动手：

| # | 问题 | 为什么必须你定 |
|---|---|---|
| 1 | **交汇点距离（convergence）**：从枪口平行飞出会在远距离平行偏在准星一侧。要不要让弹道在某个距离(如 50/100 格)与准星交汇？ | 决定近处是否明显偏移、远处是否打准。这是「从枪口射」的核心体验取舍 |
| 2 | **散布(spread)从哪起算**：现在 `shoot()` 用 `vector2d` 在眼部起算散布。改到枪口后，散布圆是仍以准星为中心(只是发射点移到枪口)，还是以枪口为中心？ | 直接影响命中手感 |
| 3 | **服务端是否同步改弹道**：若客户端从枪口发弹、服务端仍从眼部发弹，会造成命中判定与画面不一致。要不要服务端也把生成点移到枪口？ | 影响反作弊/命中一致性；上游目前是眼部发弹(设计如此) |
| 4 | **第三人称/他人视角**：别人看你时曳光弹也从枪口出吗？还是只有第一人称本机这样？ | 决定同步成本与观感一致性 |

**建议默认（可改）**：交汇距离 = 枪械 `effectiveRange` 的一半；散布仍以准星为中心；服务端生成点
同步移到枪口（弹道一致性）；第三人称保持从眼部(上游默认)，第一人称用枪口。你若认可，我按此实现。
