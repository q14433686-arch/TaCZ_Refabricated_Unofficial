# 进度报告 · 第 23 轮（曳光弹枪口跟随 + 开镜镜内排除枪体/手臂）2026-08-06/07

> 基线：`7d55503`（26.2 移植）。对照：`docs/INVESTIGATION_TRACER_SCOPE_2026-08-06.md`（彻查报告）。
> 本沙盒**无法编译/无法看画面**，所有改动均按「与已验证 PASS 的既有路径同构」的原则实现：
> 曳光提交流程与 `ShellRender`（手部 pass 内提交）同构；
> 枪身裁剪与 `BedrockAttachmentModel` 的镜身裁剪共用同一套掩码/管线。

---

## 一、反馈现象

1. **曳光弹渲染释出不正确跟随枪口**：第一人称曳光起点不在枪口/不跟枪口动，
   尤其是开镜、后坐、转头、行走摆动期间。
2. **开镜镜头内不排除枪体、手臂**：透过目镜圆孔能看到枪身（机匣/导轨/护木）
   和持枪手臂，应该只看到放大的世界与准星。

## 二、根因（详见彻查报告）

1. **曳光 1 帧滞后（结构性）**：实体（Level）pass 每帧先于手部（Hand）pass 执行。
   旧实现 `EntityBulletRenderer#renderTracerAmmo` 在 Level pass 里读
   `GunItemRendererWrapper.muzzleRenderOffset` —— 那是**上一帧**手部 pass 缓存的枪口偏移，
   且 z 轴乘的是上一帧的 FOV 因子（item/world 两套 FOV 动力学在开镜时分离）。
   转头/开镜/后坐/摆动期间枪口视图偏移每帧都在变 → 起点相对枪口漂移。
2. **曳光首 5 tick 不渲染**：旧代码 `if (tickCount >= 5 || bulletDistance > 2)` 门禁
   让第一人称曳光在出膛后 0.25 秒内根本不显示，玩家看不到「从枪口释出」的那一刻。
3. **镜内只裁了镜身**：26.2 无 stencil，镜内裁剪是「屏幕空间掩码 + shader discard」
   （`scope_body.fsh` 用 `gl_FragCoord` 采样目镜掩码）。这套裁剪**只挂在瞄具配件模型
   （`BedrockAttachmentModel`）的镜身/准星**上；枪身（`BedrockGunModel`）走普通
   `entityCutout`，手臂走 `AvatarRenderer` 皮肤管线 —— 两者都不参与 discard，
   于是目镜圆孔内透出枪身与手臂。

## 三、修复实现

### 1. 曳光弹：第一人称改由手部 pass 提交（起点 = 当帧枪口）

- `GunItemRendererWrapper#cacheMuzzlePosition` 新增
  **`muzzleRenderOffsetView`**（视图空间、未乘 FOV 因子的枪口偏移）。
- `GunItemRendererWrapper#renderFirstPerson` 在缓存枪口后调用
  **`EntityBulletRenderer#submitFirstPersonTracers`**：在手部 pass 内遍历
  本玩家 ≤256 格的曳光子弹，用**当帧**枪口视图偏移作为起点，
  把拖尾（`energySwirl` + 默认子弹模型）提交到**手部 pass collector**。
  - 起点 = 当帧枪口；方向 = 子弹速度向量换算到视图空间后的 yaw/pitch（与实体路径同约定）；
  - 拖尾长度 = `min(0.85·|v|, max(disToEye·0.8, 1.5), 32)`：近处保底 1.5 格（刚出膛可见），
    上限 32 格防全屏拉线；
  - 与枪械同投影（枪模 FOV）、同 pass → 不存在跨帧滞后；
  - Iris 手部兼容：`assignCommonEntityPipelinesToHandIfNeeded`（与抛壳/枪口火光一致）。
- `EntityBulletRenderer#renderTracerAmmo`（Level pass）：第一人称子弹在
  「手部 pass 本帧会渲染枪械 && 有枪口锚点 && 子弹在射程内」时**跳过**（避免双份拖尾）；
  其余情况（收枪/切非枪械/超射程/无枪口节点）退回旧锚点逻辑兜底，曳光不会凭空消失。
- 移除了第一人称的 `tickCount >= 5` 门禁影响（手部 pass 路径从一开始就画）。

### 2. 开镜镜内排除枪体：枪身换用「目镜掩码裁剪」RenderType

- `BedrockAttachmentModel`：`AIM_CLIP_START` 改 public；新增 `hasOcularGeometry()`。
- `GunItemRendererWrapper#resolveScopeMaskActive`：与 `BedrockAttachmentModel#submit`
  的 `maskable` 判定**逐条一致**（总开关、光影回退、装带目镜的瞄具、开镜进度 > 0.02、
  掩码纹理可绑定）。
- `GunItemRendererWrapper#resolveGunBodyRenderType`：`scopeMaskActive` 时枪身
  换用 `ScopeBodyRenderTypes.clipped(枪贴图)` —— 目镜圆孔内 discard，
  与镜身共用同一张当帧掩码、同一套屏幕空间渐进。

### 3. 开镜镜内排除手臂：整段隐藏（安全降级）

- 手臂走 `AvatarRenderer` 皮肤管线，内部 RenderType 由引擎创建，**无法**替换为裁剪版
  （沙盒无 26.2 vanilla 源码，不能盲写 mixin 改它的内部调用）。
- 退而采用与改装界面相同的既有机制 `gunModel.setRenderHand(false)`：
  `scopeMaskActive` 时整段隐藏手臂，由新配置 **`ScopeMaskHideArms`（默认 true）**控制。

## 四、改动文件

| 文件 | 改动 |
|---|---|
| `client/renderer/item/GunItemRendererWrapper.java` | `muzzleRenderOffsetView`、`resolveScopeMaskActive`、`resolveGunBodyRenderType`、开镜隐藏手臂、手部 pass 曳光提交入口 |
| `client/renderer/entity/EntityBulletRenderer.java` | `submitFirstPersonTracers`/`submitFirstPersonTracer`、Level pass 跳过条件、手部 pass 调试日志（`[TACZ TracerDebug-FP]`） |
| `client/model/BedrockAttachmentModel.java` | `AIM_CLIP_START` public、`hasOcularGeometry()` |
| `config/client/RenderConfig.java` | `ScopeMaskHideArms`（默认 true） |

## 五、已知限制 / 后续

1. **手臂是「隐藏」不是「裁剪」**：开镜时屏幕底部也看不到持枪手（与改装界面同款行为）。
   若要做真裁剪，需要 26.2 `AvatarRenderer#renderLeftHand/renderRightHand` 的完整方法体
   （沙盒拿不到 vanilla 源码），对其内部 RenderType 做替换/重定向后，手臂才能
   「只在圆孔外可见」。`docs/archive/PROGRESS_ROUND4.md` 留下的反编译片段
   （`resetPose/visible/submitModelPart`）可作为切入点。
2. **射程边界**：第一人称子弹 ≤256 格走手部 pass（枪口锚定），>256 格退回实体 pass
   （旧锚点，起点趋近子弹）。边界处视觉会有一跳，属于可接受的取舍。
3. **第三人称无枪口锚定**：他人视角看你的曳光仍从眼睛出（上游设计，弹道与准星一致），
   本轮未改；如需改需把枪口世界坐标随 spawn 数据下发。
4. **枪身裁剪用 cutout 管线**：`ScopeBodyRenderTypes.clipped` 基于 ENTITY_CUTOUT
   （ALPHA_CUTOUT 0.1）；开镜裁剪激活期间，开透明贴图的枪模会短暂失去混合，
   与镜身现状一致（既有先例）。

## 六、验证方法（现象 = 验收）

1. **曳光**：`RenderConfig.TracerDebug=true`（+`TracerDebugGun` 过滤），
   静止/移动/跳跃/开镜/连射各测：
   - 出膛瞬间（tick 0~1）就应从枪口出现拖尾；
   - 开镜过渡、后坐上跳、行走摆动时，拖尾起点始终钉在枪口（对照 `[TACZ TracerDebug-FP]`
     里 `muzzleView` 与 `viewBullet` 的距离变化）；
   - 收枪/切非枪械后，在飞子弹的拖尾不消失（实体 pass 兜底）。
2. **镜内排除**：`ScopeMaskDebug=true` 看左上角掩码预览（白色形状随枪移动、与目镜重合）；
   开镜后目镜圆孔内只看到放大的世界 + 准星，无枪身轮廓、无手臂；
   未开镜时枪身/手臂完整；关掉 `ScopeMaskEnable` 后回到旧行为；
   关掉 `ScopeMaskHideArms` 后开镜可见手臂（圆孔内会露出，属预期降级）。
3. **回归**：第三人称曳光与手臂、Iris/Sulkan 开启/关闭、无瞄具机瞄开镜（不应有任何裁剪）。
