# 跟进提示词 · 本仓 `26.1.2` 分支（Fabric / MC 26.1.2）

**先读** [`HANDOFF_COMMON_2026_08_27.md`](HANDOFF_COMMON_2026_08_27.md)（共用核心）。
本文件只写你这一支的差异。你的基线：`origin/26.1.2` @ `ebd0f91`，
`mod_version = 1.1.8+fabric.26.1.2.R2-hotfix2`。

---

## 你要做的（按优先级）

| 任务 | 状态 | 说明 |
|---|---|---|
| **A1** `UsePressGate` + `MinecraftUseRestartMixin` | ❌ 缺 | 照抄共用核心 §1.A1 |
| **A2** `use()` 两端都查冷却 + `StuckUseRecovery` | ❌ 缺 | 照抄共用核心 §1.A2。你的 `ThrowableItem#use` 在第 163–171 行，与来源分支改前**逐字节相同** |
| **B1** `assets/lrtactical/sounds.json` | ❌ **整个文件都没有** | 新建，内容逐字节照抄共用核心 §2.B1，**顶层不要 `_comment`** |
| **B2** ogg + 两张效果图标 + 两个脚本 | ❌ 缺 | `git show` 取，命令见共用核心 §2.B2 |
| **B3** `DeafenState#tick` 接住 `PlayResult` 并 WARN | ❌ 缺 | 照抄 |
| **C** 耳鸣消声注入点 → `AbstractSoundInstance#getVolume()` | ⚠️ **先核对再改** | 见下 |
| **H1** 光影 PBR 第一人称枪身闪烁修复（`IrisHandPhaseSplitFix`） | ❌ 缺 | 从 `origin/26.2(main)` 的 `arena/01a04d12` 取证移植；26.2 侧**在体 A/B 验证 PASS**。取证文档：`docs/IRIS_HAND_PHASE_SPLIT_FLICKER_2026_08_29.md`。移植前先核对你这一支的差异（见下「特殊注意 2」） |

## 你这一支的特殊注意

### 1. 任务 C 必须先自己核，别直接照搬

你的 `me/xjqsh/lrtactical/mixin/client/SoundEngineMixin.java` 与来源分支改前
**逐字节相同**（注入 `calculateVolume(SoundInstance)`）。26.2 上已证实这个注入点
对「新播放的音效」无效；26.1.2 与 26.2 同属 26.x 引擎家族，**大概率同样无效**——
但我**没有** 26.1.2 的 jar，这句话是推断，不是核对结论。

动手前请按共用核心 §5 的方法核你自己 loom 缓存里的 26.1.2 jar：
`SoundEngine#play` 是否调用 `calculateVolume(SoundInstance)`。
- 若**不调用**（与 26.2 相同）→ 按共用核心 §3 改成注入 `AbstractSoundInstance#getVolume()`；
- 若**调用** → 保持不动，并在文档里记下你的核对结果（这条信息对其它分支也有用）。

### 2. 你的瞄具是「深度孔径」架构，不是离屏掩码

你有 `ScopeDepthCopy*`（2 个文件），**没有** `ScopeMask*`，也没有 `SCOPE_SIGHT_CLIP_FIX`。
所以：

- **禁止**把来源分支的 `reticleMaskable` / `bodyMaskable` / `enableViewmodelClip`
  那一套搬过来（共用核心 §4.2）。
- 但请留意一个**开放问题**：来源分支上用户报过「低倍镜准星没有被限制在目镜内」。
  那是离屏掩码架构里「一个 `maskable` 同时喂两个消费者」造成的。你的架构用深度孔径
  约束镜内，**机制完全不同，不能类比推断**。如果用户在你这一支也报同样症状，
  请按你自己的架构从头查，不要照搬结论。目前**没有**你这一支的实测报告。

### 3. LR 0.4.3 那批你已经有了，不要重复同步

已核对你这一支具备：cook = `prepare + life`（并且你对 `lifeTime <= 0` 的处理注释
比来源分支更细）、`life >= 0 && tickCount >= life` 引信判定、
`LrTickAnimationEvent` 只驱动近战、`ConsumableItemRenderer`、`DisplayTransform`、
`CombatProperties#getActionCount`。**这些都不用动。**

### 4. 版本号

你已经是 `R2-hotfix2`。若这批改动要单独发版，按 `AGENTS.md` §1 处理
（改 `gradle.properties` 必须同步 README 六处，并跑
`bash scripts/check_release_consistency.sh --strict`）；
若并入下一次发版，本次**不要**动版本号。
hotfix 序号规矩：直接接在 `hotfix` 后面（`R2-hotfix3`），中间不放 `.`/`-`/`_`。

### 5. 注册位置（Fabric）

- 新 mixin 加进 `src/main/resources/lrtactical.mixins.json` 的 `client` 数组
  （`UsePressGate` 本身不是 mixin，不用注册）；
  `MinecraftUseRestartMixin` 属于 `cn.sh1rocu.tacz.mixin.client` 包，
  应加进 `src/main/resources/tacz.fabric.mixins.json` 的 `client` 数组。
- `UsePressGate::onClientTick` 与 `StuckUseRecovery::onClientTick`
  挂在 `ClientTickEvents.END_CLIENT_TICK`（来源分支挂在
  `cn/sh1rocu/tacz/client/TaCZFabricClient.java`，你可以照同样位置）。
  **必须是 END**：原版 `Minecraft#tick` 里 `handleKeybinds` 先于实体/世界 tick，
  挂 END 才能在同一次 tick 内采到使用状态的下降沿。

### 6. 任务 H1（光影 PBR 第一人称枪身闪烁）移植前必核清单

来源分支（26.2）改动仅两处：`RenderConfig` 新增 `IRIS_HAND_PHASE_SPLIT_FIX`
（`[FIX]`，默认 true），以及 `ItemInHandRendererMixin` 半透明遍早退闸门
（`IrisCompat.isHandRendererActive() && !IrisCompat.isHandRenderingSolid()` 时
不提交视模）。取证与验证记录见来源分支的
`docs/IRIS_HAND_PHASE_SPLIT_FLICKER_2026_08_29.md`（26.2 侧在体 PASS）。

移植到 26.1.2 前，**逐项核对**（不要照搬）：

1. **vanilla 方法名**：26.2 是 `submitHandsWithItems(float, PoseStack,
   SubmitNodeCollector, LocalPlayer, int)`；26.1.2 可能是旧的
   `renderHandsWithItems`（本仓 26.2 mixin 注释明确写过「26.2 迁移:
   renderHandsWithItems → submitHandsWithItems」）。注入点与
   `@WrapOperation(target = "…submitArmWithItem…")` 描述符都要改。
2. **Iris 版本能力**：先核对你配的 Iris 构建（26.1.2 线）是否同样存在
   `net.irisshaders.iris.pathways.HandRenderer`（`INSTANCE` / `isActive()` /
   `isRenderingSolid()`）。没有该类 = 没有双遍手部 pass = 该缺陷不存在，
   **不要移植**。你这一支的 `IrisCompat` 若只有 1.7.0 前的老接口，也要补句柄。
3. **在体验证**：同款协议 —— Complementary + labPBR/SEUS PBR 下枪身反射光源处
   是否闪烁；开关 true/false A/B。没有实机条件就**明说**。

## 交付前

```bash
python3 scripts/verify_lr_assets.py --strict      # 需要先按 B2 取到脚本
python3 scripts/verify_lr_lua_context_api.py --strict   # 若你也取了这个脚本
```

实机清单见共用核心 §6。没有实机条件就**明说**，别把源码级结论写成已验证。
