# 1.21.11 分支变更记录

本文件只记录 **1.21.11 分支相对 26.1.2 分支** 的变更，按交付轮次倒序。
每一轮的详细定位过程见 `PORT_1_21_11_PHASE2.md`。

---

## R7 后续修订（未发布）

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

准星仍被 Iris 分类为手部几何：蚀刻准星走 `HAND_CUTOUT`，发光准星走
`HAND_TRANSLUCENT`。Iris hand fragment shader 或更晚的 shader-pack composite 阶段
仍可能对已画出的准星颜色应用水、雾或后处理。若修复反射后问题仍存在，下一步应将
“准星提交”从普通枪体的 solid hand submission 中局部解耦，放在更晚的 hand/translucent
边界绘制，再由镜框覆盖溢出部分；必须保留原有 3D 模型、ADS/晃动、ocular mask，且不能
退化为 HUD、PIP 或第二次世界渲染。

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
直接归因为“已修复光影雾/水覆盖准星”是过度结论；完整修正见本文件顶部的 R7 后续修订。

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
