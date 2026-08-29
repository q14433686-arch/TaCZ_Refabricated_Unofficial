# 跟进提示词 · 姊妹仓 `TaCZ_Renovated`（NeoForge，三条线）

**先读** [`HANDOFF_COMMON_2026_08_27.md`](HANDOFF_COMMON_2026_08_27.md)（共用核心，
它写的是本仓 Fabric 视角；本文件负责翻译到 NeoForge 并给出三条线各自的差异）。

适用分支与基线：

| 分支 | 基线 | `mod_version` | 瞄具架构 |
|---|---|---|---|
| `26.2` | `3c9b0ab` | `1.1.8+neoforge.26.2.R1` | 离屏掩码 `ScopeMask*`（4 个文件） |
| `26.1.2` | `e6e5cbd` | `1.1.8+neoforge.26.1.2.R1.hotfix` | 深度孔径 `ScopeDepthCopy*` |
| `1.21.11` | `41fc53d` | `1.1.8+neoforge.1.21.11.R1-hotfix` | 深度孔径 |

取源码的方式（跨仓）：

```bash
git remote add refab https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial.git
git fetch refab arena/01a043be-tacz-refabricated-unofficial   # 或 ae606f5 / 合并后的 26.2(main)
git show FETCH_HEAD:<path>
```

---

## 三条线都要做的

| 任务 | 三条线状态 | 备注 |
|---|---|---|
| **A1** `UsePressGate` + `startUseItem` HEAD 取消 | ❌ 全缺 | 见下「NeoForge 适配」 |
| **A2** `use()` 两端都查冷却 + `StuckUseRecovery` | ❌ 全缺 | 三条线的 `ThrowableItem#use` 第 163–171 行与来源分支改前**逐字节相同** |
| **B1** `assets/lrtactical/sounds.json` | ❌ **三条线全都没有这个文件** | 新建，逐字节照抄共用核心 §2.B1，**顶层不要 `_comment`** |
| **B2** ogg + 两张效果图标 + 两个脚本 | ❌ 三条线全缺 | 你们 `assets/lrtactical` 下 **textures/ 与 sounds/ 都是空的**，所以两个效果图标现在都是紫黑块 |
| **B3** `DeafenState#tick` 接住 `PlayResult` 并 WARN | ❌ 全缺 | 26.x 两条线可直接照抄；1.21.11 先确认返回类型 |
| **H1** 光影 PBR 第一人称枪身闪烁（Fabric 26.2 的 `IrisHandPhaseSplitFix`） | ✅ **三条线已有等价闸门，无需移植** | 已核实：三条线 `ShaderCompat#shouldRenderInCurrentHandPhase` 均委托 `IrisCompat#shouldRenderInCurrentHandPhase`（镜像 Iris 的 `iris$skipTranslucentHands`），且都已在 `ItemInHandRendererMixin` 视模分支接入（26.2 L126、26.1.2/1.21.11 L124）。用户实测三条线均未发现闪烁。**不要**再加 Fabric 26.2 的开关 |

**LR 0.4.3 那批三条线都已经有了**（cook=`prepare+life`、`life>=0` 引信、
idle 只给近战、`ConsumableItemRenderer`、`DisplayTransform`、`getActionCount`）——
已逐文件核对，**不要重复同步**。

---

## 任务 C（耳鸣消声注入点）：三条线**结论不同**

三条线的 `me/xjqsh/lrtactical/mixin/client/SoundEngineMixin.java` 与来源分支改前
**逐字节相同**（注入 `SoundEngine#calculateVolume(SoundInstance)`）。

| 分支 | 该不该改 | 依据 |
|---|---|---|
| `26.2` | ✅ **改** → `AbstractSoundInstance#getVolume()` | 26.2 已用字节码证实 `play()` 不经过外层重载 |
| `26.1.2` | ⚠️ **先自己核**再改 | 同属 26.x，**大概率**同样失效，但没有该版本 jar，属推断 |
| `1.21.11` | 🚫 **不要改** | 用户实测 1.21.11 消声生效；同一份代码两边表现不同 ⇒ 差异在引擎 |

改法与理由见共用核心 §3。注意：换注入点后**豁免改为 `instanceof StunRingingSound`**，
不再依赖 `SoundSource` 类别；你们三条线的 `StunRingingSound` 现在都是 `PLAYERS`，
**保持不动**（不要改成 MASTER —— 来源分支试过，结果耳鸣声听不见了）。

---

## NeoForge 适配要点（与共用核心的差异）

### 1. 客户端 tick 挂钩

共用核心说挂 Fabric 的 `ClientTickEvents.END_CLIENT_TICK`。你们换成
NeoForge 的 `ClientTickEvent.Post`（与你们现有 LR 客户端接线同一处，
参考 `me/xjqsh/lrtactical/client/LrClientEvents.java`）。
**语义要求不变**：必须在「实体/世界 tick 之后、下一次输入处理之前」执行，
这样才能在同一次 tick 内采到使用状态的下降沿。

### 2. mixin 注册

你们的 `src/main/resources/lrtactical.mixins.json` 结构与本仓相同
（`package: me.xjqsh.lrtactical.mixin`，`client` 数组）。已核对各分支的
`compatibilityLevel`：`26.2` = `JAVA_25`，`26.1.2` = `JAVA_17`，`1.21.11` = `JAVA_17`
—— 加新 mixin 时不要顺手改这个值。新 mixin 加进 `client` 数组即可；
`neoforge.mods.toml` 的 `mixinConfigs` 已经列了 `lrtactical.mixins.json`，通常不用改。
`startUseItem` 的取消 mixin 若放在 `com.tacz.guns.mixin.*` 包，
则要加进对应的 `tacz.*.mixins.json`。

### 3. 「两端都查冷却」在你们那边的前提

本仓的 `ModCapabilities#coolDowns(player)` 按端返回
`SERVER_COOL_DOWNS` / `CLIENT_COOL_DOWNS` 两张表，所以「两端都查」直接成立。
**你们是 NeoForge attachment（`player.getData(...)`）**，客户端与服务端天然各有一份实例，
所以同样成立 —— 但请先确认两件事：
1. 客户端那份 attachment 的 `tick()` **确实每客户端 tick 都被调用**
   （本仓这条是生死线：早先漏掉 tick 调用导致 `isOnCooldown` 恒 true、
   「一局只能用一次手雷」）；
2. `ServerMessageCustomCooldown`（或你们的等价包）在客户端**确实会调用
   `addCooldown` / `removeCooldown`**。

这两条任一条不成立，就**不要**在客户端加门禁，只做 A1 + `StuckUseRecovery`，
并在文档里写明原因。

### 4. 版本号

你们三条线现在是 `R1` / `R1.hotfix` / `R1-hotfix` —— **三种写法不一致**。
本仓维护者的规矩是：hotfix 序号**直接**接在 `hotfix` 后面
（`R2-hotfix2`），中间不放 `.` / `-` / `_`，因为 **TaCZTweaks 按版本号字符串识别本项目**。
`R1.hotfix` 这种带点的形式请与维护者确认是否要统一；
另外注意 SemVer 里 `-` 之后是 prerelease 段，**只能出现在 `+` 之后的 build metadata 内部**。

### 5. 分支专属：`26.2` 的瞄具

你们 `26.2` 已经有 `ScopeMask*` 与 `reticleMaskable`/`bodyMaskable` 拆分
（已核对，与本仓实现一致），**这部分不需要跟进**。
`26.1.2` / `1.21.11` 是深度孔径架构，**禁止**把掩码那套搬过去。

---

## 交付前

```bash
python3 scripts/verify_lr_assets.py --strict   # 按 B2 取到脚本后跑；只依赖 stdlib
```

实机清单见共用核心 §6。三条线各自的回归风险：

- `26.2`：改了消声注入点 → 必须确认「消声仍生效」且「耳鸣声可闻」两条同时成立；
- `26.1.2`：若改了注入点，同上；若没改，确认新加的 A1/A2 没影响既有行为；
- `1.21.11`：**没改**消声 → 重点确认没有退化（这条线的消声与耳鸣声本来就是好的）。

没有实机条件就**明说**，不要把源码级结论写成已验证。
