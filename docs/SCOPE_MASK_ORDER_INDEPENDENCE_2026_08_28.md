# 镜内裁剪「与 mixin 注册顺序无关」加固记录

> 分支：`arena/01a045af-tacz-refabricated-unofficial`
> 日期：2026-08-28
> 性质：**加固（hardening），不是修一个正在发生的 bug。**
> 来源交接：`TaCZ_Renovated` 分支 `arena/01a0457c-tacz-renovated` 的
> `docs/handoff/HANDOFF_SCOPE_MASK_ORDER_INDEPENDENCE.md` 与
> `docs/handoff/scope-mask-order-independence.patch`。

---

## 0. 一句话结论

给镜内裁剪写了两个写入点（同一个注入点的两个 RETURN 处理器），让**谁最后跑都写对**，
从而不再依赖 tacz 与 Iris 的 mixin config 注册先后。Fabric 侧用户**未**报过镜内裁切失效，
本仓当前代码也未发作；本次改动是对**潜在风险**的加固，不声称修好了任何用户反馈的现象。

---

## 1. 改动的三处文件

| 文件 | 改动 |
|---|---|
| `src/main/java/com/tacz/guns/mixin/client/iris/IrisGlCommandEncoderMixin.java` | 新增 `trySetup` **HEAD** 注入 `tacz$captureScopeRenderPass`，记下当前 `GlRenderPass`（HEAD 必早于任何 RETURN 处理器）。RETURN 注入保持不变。 |
| `src/main/java/com/tacz/guns/mixin/client/iris/IrisExtendedShaderMixin.java` | `iris$setupState` RETURN 由「无条件写 0」改为调 `IrisScopeMaskState.applyToShaderProgram((Object) this)`，按记下的 pass 写正确 mode。 |
| `src/main/java/com/tacz/guns/compat/iris/IrisScopeMaskState.java` | 删 `resetShaderProgram`；新增 `setCurrentPass` / `applyToShaderProgram` / 共用 `writeScopeMaskState`；`applyToGlRenderPass` 删掉「`GL_CURRENT_PROGRAM` 为 0 时从 `pipeline.program()` 取 programId」的退回分支。 |

两个 mixin 文件用交接里的 `scope-mask-order-independence.patch` 打（`git apply --check`
exit 0、已干净应用）。`IrisScopeMaskState` 因本仓多一层反射结果缓存
（`cachedPassClass` / `cachedPipelineField` / `MODE_BY_PIPELINE` / `cachedMaxTextureUnits`
以及 `applyToGlRenderPass` 顶部的 `hasMaskThisFrame` / `hadMaskLastFrame` 快速路径），
补丁打不上，按文档描述手动改，**缓存层与快速路径全部保留**。

### 顺序无关的核心机制

- `IrisGlCommandEncoderMixin#trySetup` HEAD 记 `currentPass`：
  Iris 的 `MixinGlCommandEncoder` 也在 `trySetup` RETURN 注入并调
  `ExtendedShader#iris$setupState`（`_glUseProgram` + `samplers.update()` +
  `uniforms.update()`）。HEAD 永远早于所有 RETURN 处理器，所以无论 Iris 的 RETURN
  处理器何时跑，pass 都已经记下。
- `trySetup` RETURN（`applyToGlRenderPass`）与 `iris$setupState` RETURN
  （`applyToShaderProgram`）两个写入点共用 `writeScopeMaskState`，**最后跑的那个写的是同一套状态**。
- `applyToShaderProgram` 写前校验 `GL_CURRENT_PROGRAM == programId`，不一致就跳过并一次性告警
  （`glUniform1i` 只作用于当前程序，uniform location 按程序分配，跨程序写等于写进空气）。
- 非镜身 / 准星管线 mode 仍写 0，**防泄漏语义未动**。

---

## 2. 取证：本仓当前落在哪一行？

**结论：本沙箱内无法得出结论（UNKNOWN），须在有 JDK + 实机的环境用真实启动日志确认。
不猜测、不照抄 NeoForge 的结论。**

### 2.1 为什么本沙箱给不出答案

- 本构建沙箱**没有 JDK**（`java` / `javac` 均不存在），无法启动游戏，也**没有任何一份
  Fabric 侧的 `latest.log`** 留在仓库里（`find` 全仓无 `.log` / `latest.log`）。
- 因此「tacz 与 Iris 的 mixin config 注册时刻差」这条实机证据**取不到**。
  按任务要求，没有实机就如实写「未编译 / 未实机」，不把静态核对说成运行时 PASS，
  也不把兼容层任何一行标成 PASS。

### 2.2 静态侧能确认的一件事：顺序**没有**被钉死

- `src/main/resources/fabric.mod.json` 中 tacz 对 Iris **没有任何**
  `depends` / `recommends` / `breaks` 约束，mixins 列表里 `tacz.iris.mixins.json` 只是
  其中一个条目，与 Iris 自己的 config 没有顺序声明。
- 这意味着 mixin config 的注册先后由 Fabric Loader 的 mod 发现 / 排序决定，**随已安装
  mod 集合漂移** —— 正是交接文档里「坏的那一行」赖以复现的前提。换句话说：本仓现在落在
  好的那一行**不是被任何约束保证的**，只是当前 mod 组合恰好如此。本次加固把这个不确定性消掉。

### 2.3 要在实机上确认，跑这条（把结论回填本文件 §2.4）

tacz 侧注册时刻 marker（来自 `IrisCompatMixinPlugin`）：

```
[TACZ Scope] Iris compat mixin config loaded: package=com.tacz.guns.mixin.client.iris
```

Iris 侧 marker（Fabric 上通常是 mixin 子系统为 Iris 各 config 打印的行；NeoForge 日志里对应的是
`[mixin] Reference map 'iris.refmap.json' for mixins.iris.json could not be read` 一类）。

```bash
# 在真实 latest.log 上比对两套 marker 的时间戳
grep -nE "\[TACZ Scope\] Iris compat mixin config loaded|iris\.refmap\.json|mixins\.iris\.(json|vertexformat|compat\.sodium)" latest.log
```

判读：

| 早注册 | 含义 |
|---|---|
| Iris 先于 tacz | 好的那一行（`iris$setupState` → 我们写 mode）。当前已发作安全。 |
| **tacz 先于 Iris** | 坏的那一行（我们写 mode → Iris 重绑程序把 mode 写回 0，同 pass 内不再有人写）。**本次加固后这条也不再失效**，但仍建议回填确认。 |

> 注：NeoForge 侧那台机器是「tacz 早 11 ms」落在坏行；Fabric 侧**没有**任何日志佐证，
> 不假定 Fabric 也落在坏行或好行。

### 2.4 实机结论回填位

- 环境 JDK / 游戏版本 / 是否装 Iris + Sodium：_（待填）_
- tacz marker 时间戳：_（待填）_
- Iris marker 时间戳：_（待填）_
- 当前落在：_（好行 / 坏行 / 未知 — 待填）_
- 备注：本次加固后，无论落哪行裁剪都正确，该结论仅用于记录「为什么要做这次加固」。

---

## 3. 验证状态（如实填写）

| 检查项 | 结果 |
|---|---|
| `bash scripts/check_release_consistency.sh --strict` | **PASS**（EXIT=0；唯一 warning 是本 arena 分支名非 MC 版本形态，与本次改动无关）。 |
| 三个文件括号 / 字符串配平 | 静态核对通过（见 §4 的 import 使用性核对）。 |
| `resetShaderProgram` 全仓无残留引用 | **0 处**（定义与调用均已移除，换为 `applyToShaderProgram`）。 |
| `applyToShaderProgram` / `setCurrentPass` / `writeScopeMaskState` 引用 | 定义与调用齐全（mixin 两处调用 + 类内互调）。 |
| **编译（JDK 实机 javac / gradle build）** | **未编译（NO JDK in sandbox）** —— 未声称编译通过。 |
| **实机运行 / 兼容层 PASS** | **未实机（NO RUNTIME）** —— 未把任何兼容层一行标为 PASS。 |

### import 使用性核对（`IrisScopeMaskState.java`）

`RenderTarget` / `GunMod` / `ScopeMaskRenderer` / `ScopeMaskTarget` / `GL11C` / `GL13C` /
`GL20C` / `Field` / `Method` / `Locale` / `Map` —— 均在文件中被使用，无失效 import。

---

## 4. 刻意没碰的东西（按禁用清单）

- `IrisShaderCreatorMixin` 的 `@ModifyVariable(index=5)`：已核对 `ShaderCreator.link` 共 8 参、
  index 5 确为 `String fragment`，索引正确，未动。
- `unit = Math.max(15, GL_MAX_TEXTURE_IMAGE_UNITS - 1)`：只有光影包用 ≥29 个动态采样器才相撞，
  已知低危，未动。
- `ScopeMaskRenderer` 的凸包算法：那是另一个独立病灶，未混进本次 PR。
- `IrisScopeMaskState` 的反射结果缓存与 `applyToGlRenderPass` 顶部的快速路径：保留，未绕开。
- 未写「修好了用户报的镜内裁切失效」之类的 CHANGELOG 文案；CHANGELOG 条目以加固口径、
  并标注「源码级 / 未实机验证」写入 `docs/CHANGELOG_26_2_R2.md` 的 R2-hotfix2 节。

---

## 5. 一句话版本（给 review）

`IrisGlCommandEncoderMixin` 在 `trySetup` HEAD 记当前 pass；`IrisExtendedShaderMixin` 在
`iris$setupState` RETURN 改为按 pass 写正确 mode；`applyToGlRenderPass` 删掉「无当前程序就退回
`pipeline.program()`」那条静默无效分支。两个写入点谁最后跑都写对，不再依赖 tacz / Iris 的
mixin config 注册先后。本仓用户未报过失效、当前也未发作，本次为加固，非 bug 修复。
