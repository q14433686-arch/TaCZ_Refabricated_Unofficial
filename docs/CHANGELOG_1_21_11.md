# 1.21.11 分支变更记录

本文件只记录 **1.21.11 分支相对 26.1.2 分支** 的变更，按交付轮次倒序。
每一轮的详细定位过程见 `PORT_1_21_11_PHASE2.md`。

---

## R7

**修复：开光影时准星被雾效/水面「覆盖·叠加」**

准星管线之前带 `depthWrite=true`，把自己的手部近深度写进了目镜区域，
覆盖掉 depth-cleanup 刚恢复的世界远深度。Iris 的 composite 阶段因此认为
该像素是贴脸表面，把雾/水面按手部距离叠加上去。

- 两条 reticle 管线改为 `withDepthWrite(false)`；
- encoder mixin 删掉 `_depthMask(true)`，保留 `_enableDepthTest` + `_depthFunc(GL_ALWAYS)`。

语义变为「恒通过深度测试 + 不写深度」。

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
