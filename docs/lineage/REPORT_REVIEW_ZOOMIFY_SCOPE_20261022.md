# 玩家反馈「1.21.11 与 Zoomify 冲突」三点诊断的逐条甄别（2026-10-22）

> **状态：静态审查 + 离线复算。** 本地沙箱没有 JDK / Gradle / Minecraft，**没有编译、没有进游戏**；
> 每条结论都标了证据级别（源码 / 官方文档 / 离线复算 / 未验证）。**本文件对应的提交没有改动任何 mod 代码。**
> 按 `AGENTS.md` §2：下面没有任何「已修复」声明；「建议」不等于「已做」。

## 0. 这份材料是什么

- 来源：mcmod.cn 评论（用户「啊这这这3442」，2026-10-22 01:21:39）。评论里的三点「诊断」与对应「修法」是
  **评论者让 AI 生成的**，不是本仓库的工作；评论没有附日志、复现步骤或字节码证据。
- 本仓库没有因这条评论改过任何东西。评论者的 AI 改动版只存在于他自己手里，我们没有拿到、也没有采纳。
- 版本边界：R3-hotfix3 的 Release 发布于 2026-10-21 22:59 UTC；评论时间戳在 mcmod 上通常是北京时间
  （= 10-21 17:21 UTC，早于发布），所以评论者很可能用的是 hotfix2 或更早。无论哪个：hotfix2 → hotfix3 的 diff
  （17 个文件）**不含** FOV / Zoomify / 瞄具 / PIP / 分划任何文件；按文件级历史，`GameRendererMixin.java` 最近一次改动
  是 2026-09-01，`ReticleMarkFilter.java` 与 `ZoomifyCompat.java` 都是 2026-07-29。

## 1. 结论一览

| # | 评论者的说法 | 判定 | 证据级别 | 本仓库动作 |
|---|---|---|---|---|
| 1 | TACZ 的 `@ModifyReturnValue` 被 Zoomify「同类改写器挤掉」，FOV 事件不派发；改 `@Redirect` | **机理被反驳**；症状无法在此验证；`@Redirect` 是倒退 | 源码 + MixinExtras 官方文档 | 不改 |
| 2 | PIP 无条件压低世界放大，镜内画不出来就是双重失败；关掉 PIP | **默认配置下不适用**（PIP 默认就是关）；开启 PIP 时「世界让位但镜内没画」确是设计上存在的失败模式 | 源码 | 不改 |
| 3 | `ReticleMarkFilter` 硬编码 6.0，把整块大平面分划丢掉；在枪包侧把平面切条 | **属实（按构造成立）**——不是回归，是启发式的真实误伤；默认包的 `scope_vudu` 就是同类实例 | 源码 + 离线复算（未实机） | 不改；新增离线审计脚本 |

另有四条**评论没提、但读码时发现**的点（§2.3、§3.2、§4.3、§4.4），均未改，各自写明了为什么。

## 2. 第 1 点：FOV 钩子与 Zoomify

### 2.1 已核实的事实

- TACZ 侧：`src/main/java/com/tacz/guns/mixin/client/GameRendererMixin.java:220`
  `@ModifyReturnValue(method = "getFov", at = @At("RETURN"))` → `tacz$modifyFov` 派发 `ViewportEvent.FOV`。
  所在配置 `src/main/resources/tacz.mixins.json:32` 是 `"injectors": { "defaultRequire": 1 }` ——
  目标对不上会在启动期抛 `InvalidInjectionException`，**不会「静默失效」**。
- Zoomify 侧（`isXander/Zoomify`，tag `2.15.2+1.21.11`，即 `gradle.properties` 的 `zoomify_version`；
  1.21.11 上存在的 Zoomify 只有 2.15.0 / 2.15.1 / 2.15.2，2.16+ 与 2.17.0 只有 26.x，三个 1.21.11 版本这段代码逐字相同）：
  `src/main/java/dev/isxander/zoomify/mixins/zoom/GameRendererMixin.java:17-26`
  也是 `@ModifyReturnValue(method = "getFov", at = @At("RETURN"))`，内容 `fov / Zoomify.getZoomDivisor(tickDelta)`；
  `:65-81` 另有一个 `@ModifyExpressionValue`，命中 `renderLevel` 里第 2 个 `getFov(Camera,float,boolean)` 调用，
  只在「不影响手部 FOV」时抵消手部缩放（默认 `affectHandFov = true`，即默认空操作）。
  `zoomify.mixins.json`：`required: true`、`injectors.defaultRequire: 1`。
- MixinExtras 官方文档（LlamaLad7/MixinExtras wiki）：
  - ModifyReturnValue：「Multiple mods can do this at the same time, and all their modifications will be applied.」
  - ModifyExpressionValue：「unlike `Redirect`s and `ModifyConstant`s, this chains when used by multiple people.」
  - WrapOperation：「unlike `Redirect`s, this chains when used by multiple people.」
  即**两个 mod 在同一方法的 RETURN 上各挂一个 `@ModifyReturnValue` 正是文档保证可叠加的用法**；
  「被同类挤掉」与该文档直接矛盾。
- 间接证据：仓库里的 `RawOutput.log`（2026-09-12 的玩家崩溃日志，mod 列表含 `Zoomify 2.15.2+1.21.11`、
  `MixinExtras 0.5.4`）的栈里，`GameRenderer`（`class_757`）上出现 TACZ 的
  `redirect$cco000$tacz$scopeRenderLevel` —— 说明同一环境里 TACZ 的 `GameRendererMixin` 已成功合入；
  `defaultRequire: 1` 下同类里的 `getFov` 处理器也必须已注入，否则启动就失败。
  **这只证明「合入」，不证明运行期 FOV 效果。**

### 2.2 评论者 AI 的 `@Redirect` 方案

- `@Redirect` 是 Mixin 里最不可叠加的注入器（见上，文档原话）。把可叠加的换成独占的，没有收益。
- 只在被重定向的调用点生效；`getFov` 方法体内的其他调用者（含别的 mod）不再触发 TACZ 的事件。
- Zoomify 自己在 `renderLevel` 的同一个 `getFov` 调用点挂着 `@ModifyExpressionValue`。本仓
  `docs/HOLD_MY_ITEMS_COMPAT.md` §1.4 记录过 MixinExtras wrapper 与 `@Redirect` 在同点可共存（wrapper 在外），
  所以**我不断言它会崩**，只说它没有收益、失去链式叠加，且没有任何已复现的问题需要它解决。
- 结论：不采纳。

### 2.3 源码里真正看得见的交互（假设，**未实机**）

`ZoomifyCompat.java:12-17` 在 `ClientSetupEvent:127` 被初始化，装了 Zoomify 就往 `ViewportEvent.FOV` 注册一个监听：
`fov / Zoomify.getZoomDivisor(...)`，不区分世界 / 手部事件。而 Zoomify 自己的 `@ModifyReturnValue`
（§2.1）也除一次。**按源码字面，Zoomify 激活（按住缩放键）时世界 FOV 被除了两次**（手部同理；若用户关了 Zoomify 的
「影响手部 FOV」，它自己会乘回一次，兼容层多除的那一次仍在，等于该开关失效——这是推断）。

- 它**不能**解释评论者的「装着 Zoomify 就倍镜恒 1×」：Zoomify 没在缩放时除数是 1，两处都是空操作。
- 这个兼容层不是本仓库的发明：上游 `Sh1roCu/TACZ-Refabricated` 1.21.1 分支自 `6d5551d`（2025-08-26 "1.21 init"）
  就有，当时是在 TACZ 的 `getFov` 处理器里直接 `return ZoomifyCompat.getFov(event.getFOV(), partialTicks)`；
  2026-05-07（`539b3f8`）才改成 FOV 监听。原始动机没有记录（上游 issue #10 里维护者只回了一句
  「做了与 zoomify 的兼容，Zoomify 版本要 >= 2.14.2」）。**所以我没有因为「看起来冗余」就删它。**

### 2.4 要定论需要的实机步骤（三步，各几分钟）

1. 只装 Fabric API + 本 mod + Zoomify（不装 Iris/Sodium 等），`ScopePipEnable` 保持默认 false，不按 Zoomify 键，
   举 4× 以上倍镜：视野是否收窄？再去掉 Zoomify 重复一遍。**两种都收窄 ⇒ 评论者的症状在这条链上不成立。**
2. 在真实实例的 JVM 参数里加 `-Dmixin.debug.export=true`，到 `<实例>/.minecraft/.mixin.out/` 里反编译
   `GameRenderer` 的 `getFov`：应同时看到 `tacz$modifyFov` 与 `modifyFovWithZoom` 两处调用在每个 `freturn` 之前。
   这一步直接检验「被挤掉」。
3. 持非枪物品、按住 Zoomify 缩放键（默认 C，初始 4×），对比装 / 不装本 mod 的缩放强度：
   若装了本 mod 后约为 D²（而不是 D），§2.3 的叠加就坐实了——那是另一个真问题，修法再议。

## 3. 第 2 点：PIP

### 3.1 已核实的事实

- `RenderConfig.java:196-200`：`ScopePipEnable` 是 `.define("ScopePipEnable", false)`；`ScopePipRerender` 同样默认 false；
  `README.md:117` 写明「`ScopePipEnable`（默认关）」。注释原话：「功能默认全部关闭或保持旧行为」。
  所以**评论者的「修法」（关掉 PIP）就是默认状态**；默认配置的玩家走的是经典整屏变焦，PIP 不可能是他们症状的原因。
- 只有开了 PIP（配置或 `-Dtacz.scope.pip.enable=true`）才会进入 `suppressesWorldFovZoom`
  （`ScopePipRenderState.java:190`）这条「世界让位」路径。

### 3.2 开启 PIP 时，设计上确实存在「世界让位但镜内没画」的失败模式（未改）

- `suppressesWorldFovZoom` **有意**不看 `sceneCaptured`（类注释与方法注释都写了：看它会让开/退镜 POV 跳变）。
- `copyMainColor`（`:564`）对**非异常**失败（`target == null`、`copyFrom` 返回 false）只调
  `logCaptureFailure()`（`:792`）而不置 `failed`；该日志写的是「Falling back to whole-screen FOV zoom」——
  **这句与行为不符**：`failed` 没翻，世界仍让位。只有异常路径才会 `failed = true` 并真正回落。
- `compositeAfterHand` 里的多处 early-return（无场景、`!hasMaskCycleThisFrame()`、深度句柄不可用）也是同一后果；
  其中掩码周期那条是 fail-closed 的有意设计（宁可一帧不画，也不贴陈旧截图）。
- 代码里已有缓解：`failed` 永久回落、`irisCompatible()`、`shaderRerenderAllowed()`、
  `worldZoomForcedToOne()` → `scopePassRunnable()`。
- 我没改的理由：修法（把回落做成真的）要么是永久 `failed`（瞬时 GL 错误也会让 PIP 整局失效），要么是「最近 N 帧是否画过」的闩锁
  （正是作者们为避免 POV 抖动绕开的方向）；两者都需要实机，且这是**默认关**的实验功能。

### 3.3 与第 3 点的关系

评论者的症状 #1「恒 1×」+ #2「镜内没画面」恰好同时出现，**PIP 开启且抓取/合成失败时就是这个组合**，与 Zoomify 无关；
另一方面「镜内没准星」在没开 PIP 时有第 3 点这个现成解释。三个症状很可能不是同一个原因。

## 4. 第 3 点：`ReticleMarkFilter`

### 4.1 机制（源码）

- `ReticleMarkFilter.java:32` `THIN_MARK_MAX_EXTENT = 6.0F`；`isThinMark`（`:47-84`）只保留「AABB 第二长轴 ≤ 6.0」的 cube。
- 该过滤在**所有**提交路径上无条件生效：vanilla / Iris 晚 pass（`ScopeLateReticleState.java:135`）、
  Iris 终局覆盖层（`ScopeFinalOverlayState.java:201`）。`maskActive`（`BedrockAttachmentModel.java:769-775` 传入的是
  `etchedOnly`，并不是「掩码本帧真的生效」，与 `IReticleRenderer.Context` 的 javadoc 不符）不会关掉它。
- 节点分类（`BedrockAttachmentModel.java:98-104`）：`division` / `division_2` 是蚀刻根；名字匹配
  `…(division|divisions|dot|cross|crosshair|reticle|red)(_N)?_illuminated` 的是发光根。评论者说的子骨骼
  `cube_illuminated` **不匹配**发光正则；若这些镜没有别的匹配发光正则的节点，它们就被分到蚀刻路径，整棵 `division`
  子树（含该子骨骼）都过同一把尺。
- 所以「9.9×9.9 / 14.85×14.85 / 22×22 的整块平面被丢弃」**按构造成立**；评论者的「x 向切条」之所以有效，
  是因为度量看的是第二长轴：切成宽 ≤ 6 的条，每条的第二长轴就是条宽，于是被当成「细线」放行。
  这是**绕开**启发式，不是修复；也无法靠调阈值解决（§4.5）。

### 4.2 默认枪包的离线复算（`scripts/audit_reticle_marks.py`，**未与游戏对拍**）

对 `resources/tacz-26.1.2-source-and-resource-bundle.jar` 里的默认包（`build.gradle:264-300` 正是用它作为 1.21.11 构建的
资源来源）：有分划节点的模型 33 个（27 发光 + 6 纯蚀刻），实际会提交的分划 cube 505 个；
按「旋转前的 size(+inflate) 外包盒、第二长轴」口径：保留 436、丢弃 69；保留的最大值 5.0（AUG `[5,7.45584,0]`），
丢弃的最小值 7.0（`scope_1873_6x` 的 45° 菱形片 `[7,14.08326,0]`）。被丢的 69 个里：56 个是实心单 texel 黑块（遮光板）；
13 个带大块贴图，其中 12 个只有黑 / 透明（遮光框，或「黑角 + 圆形透明窗」的圆窗遮罩：`scope_98k`、`scope_retro_2x`），
**只有 VUDU 的 `[50,50,0]` 带非黑不透明像素**（下一节）。

### 4.3 默认包里的同类实例：`scope_vudu`（静态推断，**强烈建议实机看一眼**）

`scope_vudu_geo.json` 的 `division_illuminated`（挂在 `division_2` 下，即高倍档 view 2）有 6 个 cube：
`[50,50,0]` 一块、`[50,100,0]`×2、`[100,250,0]`×2、以及中心 `[0.25,0.25,0]` 一个 45° 小菱形。

- `[50,50,0]` 以光轴为中心，UV 取 256 贴图网格里 (64,64) 起的 192×192 一块。把这块贴图裁出来：77.5% 透明、
  22.2% 纯黑（四角，圆形窗之外）、**360 个非黑不透明像素 —— 是一个带刻度的红色十字线**。
- 另外四块的 UV 只有 4.5×4 个 texel，是实心黑色遮光框。
- 代码注释（`ReticleMarkFilter` / `IlluminatedReticleRenderer`）把这五块**一概判为「整版遮光面」**，
  「只有第 6 块才是真正的准星点」。按贴图看这个判断对其中四块成立，对 `[50,50,0]` 不成立：
  它承载的是 VUDU 的真分划图。
- 按代码路径（`IlluminatedReticleRenderer` → `ScopeLateReticleState.submitReticleGeometry` →
  `writeFiltered(isThinMark)`），这块 `middle = 50 > 6` 被丢，VUDU 高倍档只会剩中心 0.25 单位的小点。
  **预测**：上游 / Forge 原版 VUDU 高倍档应显示红色带刻度十字线，本移植只有一个小点。实机一看便知。
- 这使「第 3 点是真问题」有了一个**不依赖碧蓝档案枪包**的仓内证据（评论者那 7 个镜我们没有枪包，无法核）。

### 4.4 注释里的校准数字与代码的度量口径对不上（未改，需要一行日志才能定论）

`ReticleMarkFilter.java:18-27`（以及 `docs/RENDER_PIPELINE_SCOPE_AUDIT_26_1_2.md` §6.7）写「全包 73 个准星 cube，
最大合法标线 middle = 5.804（qmk152 圆环弧段 `[3.40021,4.80769,0]`），最小遮光板 middle = 8.8」。

- 这两个数恰好等于**旋转后**的 AABB：`(3.40021 + 4.80769) / √2 = 5.804`（该弧段带 45° 旋转）。
- 而 `isThinMark` 量的是 cube 自己的顶点：`BedrockModel.java:145-180` 把带 rotation 的 cube 放进子 `BedrockPart`，
  旋转在 part 层（`xRot/yRot/zRot`），不烘进顶点；`BedrockRenderSnapshot.java:118-131` 把原始 cube 交给谓词。
  按这个口径该弧段 `middle = 3.40`，全包的真实分界是 **[5.0, 7.0]**，不是 [5.8, 8.8]；cube 总数也不是 73。
- 6.0 仍落在真实分界内，所以默认包上的行为（除 VUDU）与作者意图一致；但**按注释里的数字去调阈值会越界**：
  超过 7.0 就会放行 `scope_1873_6x` 的菱形遮光片。
- 我对加载路径的理解可能有遗漏，所以只记录、没改注释。定论办法：临时在 `isThinMark` 里对前若干个 cube
  打一条 `middle` 日志，对比 qmk152 的弧段是 3.40 还是 5.80。

### 4.5 能怎么修（**均未实施**；都涉及渲染失败模式，需要实机矩阵）

| 方案 | 评价 |
|---|---|
| 抬高阈值 | 不可行：默认包最小遮光件 7.0（代码口径）/ 8.8（注释口径），`scope_98k` 的圆窗遮罩 9.218，与碧蓝档案的 9.9 同量级；尺寸本身分不开 |
| 按 UV 判别：实心单 texel 才是遮光板，大贴图面放行 | 数据现成（face 的 `uv_size`），改动最小；放行后 `scope_98k` / `scope_retro_2x` 的圆窗遮罩也会开始上屏（更接近上游，但黑角在掩码降级时会外露，且孔径边缘可能出现暗环）——要实机矩阵 |
| 读贴图 alpha / 非黑像素判别 | 最准（VUDU 的 360 个非黑像素），但要在模型加载时拿到贴图像素，改动大；纯黑蚀刻分划图仍需第二条判据 |
| 只在掩码确定生效时跳过 CPU 过滤 | 不可行：掩码是否生效在绘制边界才知道，提交 / 写顶点时不可知 |
| 着色器侧 fail-closed（掩码模式 0 时 discard） | 能彻底去掉 CPU 过滤，但改的是四组 reticle 管线的语义，风险最大 |
| 枪包侧切条（评论者做法） | 今天就能用；性质是绕开，不要写成「修复」；且默认包 CC BY-NC-ND 不能改了再分发 |

我倾向的顺序：先实机确认 §4.3 的预测 → 若确认，做「按 UV 判别」并先放在**默认关**的开关后面让维护者实机 A/B
（VUDU、98k、retro_2x、contender × 有 / 无 Iris）→ 再决定是否默认开。

### 4.6 工具

`python3 scripts/audit_reticle_marks.py [-v] [--textures] <目录 | geo.json | 枪包.zip>`：
读枪包的 geo JSON，按上述 Java 语义列出每个模型里会被丢的 cube；`--textures`（需 Pillow）把被丢大平面的贴图
内容（透明 / 纯黑 / 非黑不透明像素）也打出来。阈值直接从 `ReticleMarkFilter.java` 读。
**这是对 Java 的离线复刻，没有对拍过**（文件头写明了复刻的每一处 Java 语义与局限）。
对碧蓝档案枪包跑一遍，就能把评论者的「7 个镜」落到具体 cube 上。

## 5. 给评论者的回复要点（引导到本仓库 Issue，`AGENTS.md` §5）

1. 目前没有证据表明是 Zoomify 冲突：Zoomify 的 FOV 钩子与本 mod 的是同类、可叠加的注入器；`@Redirect` 方案不采纳。
2. 请提供：完整 `latest.log`（含 mod 列表）、`config/tacz-client.toml` 里 `ScopePip*` 各项与是否带了
   `-Dtacz.scope.pip.enable`、是否用了光影 / Sodium、**去掉 Zoomify 后是否仍复现**。
3. 「镜内没准星」：请给枪包名与瞄具 id，或跑上面的审计脚本。这一条我们已知有真实的误伤（见 §4）。
4. 不要把「已改好的版本」当修复发布；本仓库没有采纳那份改动。

## 6. 本次提交包含什么

- 新增本文件、`scripts/audit_reticle_marks.py`，并在 `docs/README.md` 的索引里登记。
- **没有改动任何 mod 源码 / 资源 / 版本号**；未运行 Gradle / 游戏；`scripts/audit_reticle_marks.py` 只在沙箱里对
  默认包、合成的碧蓝档案式平面（9.9 / 14.85 / 22 与切条版）跑过。
