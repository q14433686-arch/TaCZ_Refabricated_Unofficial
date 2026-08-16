# 1.21.11 分支变更记录

本文件只记录 **1.21.11 分支相对 26.1.2 分支** 的变更，按交付轮次倒序。
每一轮的详细定位过程见 `PORT_1_21_11_PHASE2.md`。

---

## 1.1.8+fabric.1.21.11.R2

**新增：内置弹药适配枪械查询（JEI / REI）**

- 新增与 “TaCZ Ammo Query” 同类的内置查询分类，无需再安装单独附属模组；
- 在 JEI 与 REI 中选择任意 TACZ 弹药，可查看当前已加载枪包中所有使用该弹药的枪械；
- 两个查看器共用同一份查询数据，支持服务端同步的第三方枪包，并复用现有枪包同步后自动刷新流程；
- 大型枪包超过单页显示上限时，末格会轮换显示其余枪械。

**修复：Carry On 2.9.2 搬运 TACZ 工作台**

- 双格工作台改在 block-state 放置路径补全 companion，不再依赖 Carry On 不会调用的
  `setPlacedBy`；放置前会原子检查两格空间，失败时保留搬运数据；
- 配件工作台保留 `half=lower/upper` 序列化格式，但不再使用会被 Carry On 通用规则拒绝的
  `DoubleBlockHalf` value class；该冲突也存在于官方 TACZ 1.20.1 设计，并非端口遗漏；
- 从同步的 Carry On 方块实体 NBT 恢复枪包工作台 `BlockId`，修复手持紫黑模型；
- 从任一半格发起搬运都会解析到 root，显式覆盖 `pickupAllBlocks=true`，不会打开菜单或生成
  非 root 幽灵方块；
- 工作台从 Carry On 黑名单移除，`target` / `statue` 继续保留。

实现依据、兼容边界和游戏内回归矩阵见 [`CARRYON_COMPAT.md`](CARRYON_COMPAT.md)。

**下游兼容 API：可替换实体弹药源（Issue #46）**

新增公共 `com.tacz.guns.api.item.ammo` API。下游模组可向
`AmmoSourceRegistry.EVENT` 注册 provider，由自有库存实现无副作用的弹药查询与有界消耗，
不再需要 mixin TaCZ 的高层弹药方法。provider 按注册顺序匹配，首个非 `null` 结果生效；
未匹配时继续使用原有实体 `IItemHandler`，原版 `IAmmo` / `IAmmoBox` 行为不变。

以下旧兼容目标现在统一经过 registry：

- `AbstractGunItem#canReload` / `hasInventoryAmmo`；
- `LivingEntityShoot#consumeAmmoFromPlayer`；
- `ModernKineticGunScriptAPI#consumeAmmoFromPlayer` / `hasAmmoToConsume`；
- `GunAnimationStateContext#hasAmmoToConsume`。

动画上下文另新增稳定命名的 protected 方法 `hasAmmoToConsumeInEntity(Entity)`，避免依赖
javac 合成的 `lambda$hasAmmoToConsume$...` 名称。假弹、创造模式和无限弹药的现有优先级
保持不变。接入方式、双端注册要求和契约详见 [`AMMO_SOURCE_API.md`](AMMO_SOURCE_API.md)。

**稳定命名的行为扩展钩子**

将开火、换弹、拉栓和动画路径中原本只能通过 private 方法或合成 lambda 定位的业务阶段，
提取为 protected 具名方法。主要入口包括：

- `LocalPlayerShoot` 的校验、冷却、连发周期、主线程表现及状态锁判定；
- `LocalPlayerReload` / `LocalPlayerBolt` 的事务、动画和手动拉栓判定；
- `LivingEntityShoot` / `LivingEntityReload` / `LivingEntityBolt` 的服务端事务与蓄力校验；
- `ModernKineticGunScriptAPI` 的射击周期、继续条件、弹丸生成和过热脚本分派；
- `ModernKineticGunItem` 的默认过热、拉栓和换弹 fallback；
- `GunAnimationStateContext#getInterpolatedWalkDistance`。

P2-min 另将 `ModernKineticGunItem` / `ModernKineticGunScriptAPI` 的 Lua 函数解析与 cycle task
转发提取为 protected 具名 helper，并为默认 reload/heat fallback、弹药动作策略和服务端蓄力
校验补齐覆写契约；没有扩大已拒绝候选，也没有改变脚本或 gameplay 行为。

原调用路径、判断和副作用顺序保持不变；射击状态锁仍使用同一个 Predicate 实例进行身份判断。
这些具名方法用于避免下游绑定 javac 合成名称，不代表合成 `lambda$...` 方法属于兼容 API。

---

## R12

**审计 + 恢复：LRTactical 爆炸屏幕震动**

对仓库 `26.1.2`、`26.2(main)` 与原始 LRTactical NeoForge 1.21.1 的显式 no-op/TODO
逐项对照后，确认绝大多数空实现是 ABI 门面、旧 collector API、上游未设计功能或资源授权限制。
新增 `docs/EXPLICIT_GAPS_AUDIT_R12.md` 记录每项证据与处理边界。

发现并恢复了一个真实功能缺口：爆炸数据里的 `screen_shake_time` /
`screen_shake_amplitude` 以前从未生效。R12 新增范围限定的 S2C payload、客户端 tick
衰减状态和 `ViewportEvent.CAMERA` 视觉层；震动在枪械后坐之后叠加，不修改玩家真实朝向，
不影响服务端爆炸/伤害权威性。

仍未粗暴实现 `destroy_multiplier`：原版 `Level#explode` 会同时影响伤害、击退与方块，
不能等价替代上游只放大方块射线能量的 `CustomExplosion`。

---

## R11

**修复：Complementary 在 R9 late hand 之后仍雾化准星/目镜黑边**

R10 实机截图确认 R9 的前景深度写入仍不足：Complementary 的剩余雾来自
`HAND_TRANSLUCENT` 之后的 screen-space composite，读取不可被该 hand draw 改写的深度输入。
因此不再继续调整 hand pipeline 的深度状态。

R11 保留 solid pass 中原有 aperture、body、depth cleanup 与 3D 快照；仅对已核实的
Iris 1.10.7，把 reticle/rim 的最终颜色提交移动到
`IrisRenderingPipeline#finalizeLevelRendering()` 的 TAIL：

- Iris 所有 composite/final pass 已完成，core RenderPipeline 不再被 shader pack 替换；
- 复用固化的 hand projection、model-view、原始 Bedrock snapshot、ADS、后坐、晃动和 aperture mask；
- 使用新的无雾 `scope_reticle_final.fsh` / `scope_ring_final.fsh`，不是 HUD 或第二次世界渲染；
- 为 final reticle 在 solid hand 阶段额外保存私有 world-depth copy，保证最终 aperture mask
  不依赖 Iris 已解绑的 `depthtex2`；
- 其他 Iris 版本仍回退 R8/R9 的 `HAND_TRANSLUCENT` 路径，避免未审计内部时序导致准星消失。

R11 实机开镜日志应包含：

```text
[TACZ Scope] Queued reticle for Iris post-composite overlay.
[TACZ Scope] Final overlay masked by private world/aperture depth copies.
[TACZ Scope] Rendered reticle and ocular rim after Iris final composite.
```

---

## R10

**修复：1.21.11 数据包、实体 tag、默认枪包复合音效与工作台 recipe-book 噪声**

- `ammo_box_dyed.json` 不再使用 26.1 才新增的 `minecraft:crafting_dye`；1.21.11 改回
  `minecraft:crafting_special_armordye`。`tacz:ammo_box` 已在 `#minecraft:dyeable`，故继续使用
  原版的动态颜色混合与 `minecraft:dyed_color`，不会丢失弹药盒已有组件。
- 交互白名单的裸 `minecraft:boat` / `minecraft:chest_boat` 已改为 `#minecraft:boat`。1.21.2
  之后船实体类型拆分，原版 entity-type tag 负责覆盖普通船、箱船、木筏及未来变体。
- 默认枪包的 `reload_*` / `inspect*` 聚合 sound id 并不对应单个 OGG；实际音频是动画 keyframe
  中的多个 `*_raise`、`*_magin`、`*_end` 等片段。播放代码现在将聚合 id 视作可选容器：有单一
  OGG 时照常播放；没有时静默交给 `ObjectAnimationSoundChannel` 播放真实片段，不再误报
  `Missing gun sound resource`，也不伪造/复制音频文件。
- `GunSmithTableRecipe#isSpecial()` 现在返回 true。枪械工作台材料带数量且不属于原版 3×3 配方，
  因此不再被 RecipeManager 强制转换为 recipe-book placement；这消除了大量
  `can't be placed due to empty ingredients` 警告，且不影响 TACZ 工作台自己的材料检查和合成。

---

## R9

**修复尝试：Complementary Reimagined 的后处理雾仍将晚期准星当作远景**

R8 已把 reticle/rim 提交延后到 Iris `HAND_TRANSLUCENT`，排除了水面、水体与粒子
world pass 的覆盖；实机截图确认剩余现象只随雾效出现。原因是 shader pack 的后续
screen-space fog/composite 仍读取当前深度：R8 late reticle 保持 `depthWrite=false`，因此
这些像素仍携带 cleanup 恢复的远处世界深度，被后处理雾当作远景颜色处理。

R9 新增仅供 Iris late hand pass 使用的 reticle pipeline：

- 普通/vanilla reticle 仍是 `GL_ALWAYS + depthWrite=false`，绝不破坏世界透明绘制；
- late etched / illuminated reticle 改为 `GL_ALWAYS + depthWrite=true`；此时水、粒子、
  天气等 world pass 已全部结束，写入近处深度只会让后续 fog、DOF 与 composite 正确识别前景；
- late `ocular_ring` 同样改为 `GL_ALWAYS + depthWrite=true`，确保其在准星之后盖住边缘像素，
  并作为前景参与 post-process 深度判定；
- `_depthMask(true)` 仍未在 mixin 中硬编码；写入掩码只由这三条 late RenderPipeline 自身声明。

BSL/Complementary 实测时应出现：

```text
[TACZ Scope] Queued reticle for Iris HAND_TRANSLUCENT.
[TACZ Scope] Deferred reticle and ocular rim to Iris HAND_TRANSLUCENT with late foreground depth.
```

并新增 Iris pipeline 分配日志：`scope_late_etched_reticle` 或
`scope_late_visible_reticle`、以及 `scope_late_ocular_ring` 均应归入
`HAND_TRANSLUCENT`。

---

## R8

**运行期修复：`GlRenderPipeline.info()` 不能经由反射调用**

`GlCommandEncoderScopeDepthCopyMixin` 原先把 `GlRenderPipeline` 收为 `Object`，再用
`getMethod("info")` 取 record accessor。1.21.11 是混淆运行时：源码里的 `info()`
在运行时是 `comp_3801()`；反射字符串不会经过 Loom remap，因此这条路径会抛
`NoSuchMethodException`，并使 reticle 的 `GL_ALWAYS` 覆写被静默停用。

现改为直接接收 `GlRenderPipeline` 并调用 `glRenderPipeline.info()`，使 Loom 将正常的
方法调用重映射到运行时名称。同时 `needsForcedAlwaysDepth` 改为接收
`RenderPipeline`，移除反射缓存、失败日志和 `Object` 类型逃逸。

两条 reticle 管线仍保持 `withDepthWrite(false)`；mixin 只执行
`_enableDepthTest()` 与 `_depthFunc(GL_ALWAYS)`，绝不恢复 `_depthMask(true)`。

**R7 结论修正：深度状态不是“雾/水覆盖准星”的完整解释**

`depthWrite=false` 已保证准星不破坏 depth-cleanup 恢复的世界深度。反射失败时实际为
`NO_DEPTH_TEST + depthWrite=false`，修复后为 `GL_ALWAYS + depthWrite=false`；两者对
“只写颜色、不写深度”的结果很接近。因此本次反射问题是确定的运行期 Bug，但不能把它
单独当作 BSL 水面、雾或 composite 覆盖准星的完整根因。

先前蚀刻准星走 `HAND_CUTOUT`，发光准星走 `HAND_TRANSLUCENT`；它们仍属于
shader pack 的 hand geometry，而不是最终 HUD。实机 BSL 回归确认：反射修复后水雾仍会覆盖
准星，因此按既定第二步实施局部时序重构。

**修复：将准星与 ocular rim 延后到 Iris `HAND_TRANSLUCENT`**

Iris 1.10.7 的 `HandRenderer#renderTranslucent` 会在“没有原版半透明手持方块”时直接返回；
TACZ 枪不是 `BlockItem`，所以不能只改 pipeline 分类。现在 solid hand pass 仅冻结不可变的
reticle/rim `BedrockRenderSnapshot`：完整保留原 3D 模型、ADS、后坐、晃动和 ocular mask。
新的 Iris-only mixin 在存在待提交准星时强制运行一次 late hand pass，并在 Iris 设置
`HAND_TRANSLUCENT` phase 后、其自身 `endBatch()` 前将快照交给原本的 hand collector。

- 蚀刻与发光准星都映射为 `HAND_TRANSLUCENT`（Iris `HandWater`）；
- 物理 `ocular_ring` 使用独立的 late cutout pipeline，按更高 order 在准星后绘制，继续覆盖边缘溢出；
- cleanup、world-depth backup 和 aperture mask 仍留在 `HAND_SOLID`，不会重绘世界或转成 HUD/PIP；
- 同时修正 Iris 1.10.7 的 `isHandTranslucent(InteractionHand)` 反射签名，避免 late pass 中完整枪体被
  fail-open 地重复提交。

启用 BSL 且实际开镜后，日志应依次出现：

```text
[TACZ Scope] Queued reticle for Iris HAND_TRANSLUCENT.
[TACZ Scope] Deferred reticle and ocular rim to Iris HAND_TRANSLUCENT.
```

**实机回归清单**：无光影、BSL、水下、隔水看目标、浓雾、雨天；日志中不应再出现
`Cannot read GlRenderPipeline.info()` 或 `reticle depth override disabled`。

**下一轮待核对（仅记录，尚未改动）**

- `data/tacz/recipe/ammo_box_dyed.json` 使用不存在的 `minecraft:crafting_dye` serializer，
  当前染色弹药盒配方不会加载；
- 交互白名单中的 `minecraft:boat` 与 `minecraft:chest_boat` 在 1.21.11 不是可直接引用的
  通用实体类型，应改为正确 tag 或具体船实体；
- 默认枪包缺少 `tacz:aug/aug_reload_empty`、`tacz:aug/aug_reload_tactical` 等音效，需要核对
  bundle、`sounds.json` 和实际资源路径；
- 大量 recipe placement 的“empty ingredients / ignored”警告需用枪械工作台实际合成验证，
  再判断是 `PlacementInfo` 的正常回退还是配方数据/实现问题。

---

## R7

**调整：准星不再写深度，保护 depth-cleanup 恢复的世界深度**

准星管线曾带 `depthWrite=true`，会以手部近深度覆盖 depth-cleanup 刚恢复的世界深度。
因此：

- 两条 reticle 管线改为 `withDepthWrite(false)`；
- encoder mixin 删除 `_depthMask(true)`，只保留 `_enableDepthTest` + `_depthFunc(GL_ALWAYS)`。

语义变为「恒通过深度测试 + 不写深度」。这确保准星不会破坏恢复后的深度，但当时将它
直接归因为“已修复光影雾/水覆盖准星”是过度结论；完整修正见本文件顶部的 R8 / R9。

**文档**：README 从 26.1.2 全面更新到 1.21.11（版本、Java 21、依赖、混淆说明、
移植章节）；补充 `.gitignore`；删除误提交的 `latest.log`。

---

## R6

**修复 1：Iris 下镜内不渲染水体/水面/粒子/云/雾（阶段 9）**

`IrisDepthRestoreShaderMixin` 此前被整包排除，因为 Iris 只在 `-PwithIris` 的
`modRuntimeOnly` 里、编译期不可见。改为 `modCompileOnly`（不进成品、不进普通运行
classpath），恢复该 mixin。

对 Iris 1.10.7 逐条核实注入点：`ShaderCreator.link` 存在；其内 `createShader`
第 5 次调用（ordinal=4）为 FRAGMENT；`create`/`createFallback`/`createFallbackShadow`/
`createShadow` 四条路径全部汇聚到该 `link`；传入的 name 是
`ShaderKey.getName()` = `toString().toLowerCase(ROOT)`。

运行期双重兜底：`required=false` + `IrisCompatMixinPlugin` 的 `isModLoaded("iris")`。
已确认成品 jar 中 `net/irisshaders` 类数量为 0。

**修复 2：准星溢出目镜到镜框**

不是掩码 epsilon 的问题。准星的镜内判据读 `APERTURE_TARGET`，该快照拍摄于
order −2，那时物理镜框（order 1）尚未绘制，掩码里没有镜框信息。
上游 1.21.1 本就是「先准星、后镜框」，移植时把两个 order 写反了。
改回 `reticle=1, ocular_ring=2`，epsilon 未动。

---

## R5

**修复：不开光影时镜内准星完全不显示**

1.21.11 的 `DepthTestFunction` 没有 ALWAYS，移植时全部退用 `GREATER_DEPTH_TEST`。
对 depth-cleanup 正确，但对准星致命：cleanup 把目镜区域写成世界远深度，
准星在手部近深度，`GL_GREATER` 要求 new > old，准星像素被全部丢弃。
（开光影时 Iris 用自己的 HAND 程序，绕开了这条管线状态，所以反而正常 ——
「有无光影表现相反」正是定位到这里的线索。）

改为 `NO_DEPTH_TEST` 声明 + encoder mixin 强制 `GL_ALWAYS`。
单用 `NO_DEPTH_TEST` 不行：`glDisable(GL_DEPTH_TEST)` 会连深度写入一起丢弃。

---

## R4

**修复：点开世界选择界面崩溃**

`ReloadableResourcesMixin` 的 `@ModifyArg` 目标写的是 `lambda$loadResources$2` ——
这是**非混淆**版本（26.x）javac 的合成名。1.21.11 是混淆版本，refmap 里没有
`lambda$` 条目，名字原样传给 mixin 必然找不到目标，在 APPLY 阶段崩溃。
改用 intermediary 名 `method_58296` + 完整描述符。

同时修掉 `verify_mixin_targets.py` 的盲区：它此前**主动跳过** `lambda$`/`method_`
前缀的目标（正是这行 `continue` 放过了导致崩溃的目标）。现在 `lambda$` 直接判错，
`method_NNNNN` 走正常校验。校验项 101 → 102。

---

## R3

**修复：启动黑屏（非崩溃）**

`scope_body.vsh` 是从 26.2 逐字节抄来的 `entity.vsh`，`#moj_import`
了 `<minecraft:sample_lightmap.glsl>` —— 这个 include 是 26.x 才有的，
1.21.11 只有 8 个 include，没有它。`ShaderManager` 解析悬空 import 时抛 NPE，
整个资源重载失败，客户端停在黑屏。改用 1.21.11 原版写法
`texelFetch(Sampler2, UV2 / 16, 0)`。

顺带修 `assets/lrtactical/sounds.json`：字符串形式的 `_comment` 键让整个音效索引
失效（`sounds.json` 要求每个顶层条目都是 JsonObject）。

`processResources` 改为显式枚举 `src/main/resources` 的覆盖项，
不再依赖 `DuplicatesStrategy` 的隐式顺序。

新增 `docs/verify_shader_imports.py`。

---

## R1 / R2

Phase 1（构建文件）+ Phase 2（编译错误族）。编译错误 146 → 0。
逐次修复的启动崩溃：`Minecraft#pickBlockOrEntity` → `pickBlock`；
`Camera#update` → `setup`；`calculateFov`/`calculateHudFov` 合并为
`GameRenderer#getFov` 的 `@ModifyReturnValue`；
`renderItemInHand`/`bobHurt`/`bobView` 的处理函数参数列表重签。

详见 `PORT_1_21_11_PHASE1.md` 与 `PORT_1_21_11_PHASE2.md`。
