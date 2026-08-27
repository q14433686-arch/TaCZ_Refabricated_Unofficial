# 跟进提示词 · 本仓 `1.21.11` 分支（Fabric / MC 1.21.11 / 混淆映射）

**先读** [`HANDOFF_COMMON_2026_08_27.md`](HANDOFF_COMMON_2026_08_27.md)（共用核心）。
本文件只写你这一支的差异。你的基线：`origin/1.21.11` @ `423c1f7`，
`mod_version = 1.1.8+fabric.1.21.11.R2-hotfix2`。

---

## 你要做的（按优先级）

| 任务 | 状态 | 说明 |
|---|---|---|
| **A1** `UsePressGate` + `MinecraftUseRestartMixin` | ❌ 缺 | 照抄共用核心 §1.A1，但注意下面「混淆映射」一节 |
| **A2** `use()` 两端都查冷却 + `StuckUseRecovery` | ❌ 缺 | 你的 `ThrowableItem#use` 在第 163–171 行，与来源分支改前**逐字节相同** |
| **B1** `sounds.json` | ✅ **已有且写法正确**（无顶层 `_comment`） | **不要动**。只请确认它仍然没有顶层注释键 |
| **B2** ogg + 两张效果图标 + 两个脚本 | ❌ 缺 | `git show` 取，命令见共用核心 §2.B2 |
| **B3** `DeafenState#tick` 接住 `PlayResult` 并 WARN | ❌ 缺 | 照抄。**但先确认 1.21.1 的 `SoundManager#play` 也有返回值**（见下） |
| **C** 耳鸣消声注入点 | 🚫 **禁止改动** | 见下，这是本文件最重要的一条 |

## 你这一支的特殊注意

### 1. 🚫 任务 C 在你这一支**不要做**

用户**实测过 1.21.11：耳鸣的消声是生效的、耳鸣声也在**。
你的 `SoundEngineMixin` 与来源分支改前逐字节相同（注入 `calculateVolume(SoundInstance)`），
同一份代码在 26.x 上失效、在你这一支有效 ⇒ **差异来自引擎，不来自我们的代码**。

所以：
- **保持 `SoundEngineMixin` 不动**（不要改名、不要换注入点）；
- 也**不要**为了「三支统一」去改 `StunRingingSound` 的 `SoundSource`
  （你现在是 `PLAYERS`，与来源分支改后一致，本来就是对的）；
- 如果你想搞清楚为什么两边不同，可以按共用核心 §5 核 1.21.1 的
  `SoundEngine#play` 是否调用 `calculateVolume(SoundInstance)`，
  并把结果写进文档（对其它分支有价值）——但**核对归核对，不要顺手改代码**。

### 2. B3 之前先确认 `SoundManager#play` 的返回类型

来源分支（26.2）的 `SoundManager#play` 返回
`SoundEngine$PlayResult`（`STARTED` / `STARTED_SILENTLY` / `NOT_STARTED`）。
**1.21.1 是否同样有返回值我没有核对。**若 1.21.1 的 `play` 返回 `void`，
就不要硬套这段代码——那正是「照搬 26.2 结论」的典型翻车方式。
没有返回值时的替代做法：在 `DeafenState#tick` 里改为检查
`mc.getSoundManager().isActive(ringing)` 在 `play()` 之后是否变为 true，
不变则 WARN 一次（信息量略低，但同样能把静默失败变可见）。

### 3. 混淆映射（Loom remap）——新 mixin 的主要风险

你这一支是**混淆**分支。要点：
- `@Inject(method = "startUseItem", ...)` 这类**具名**方法目标没问题
  （你现有的 `MinecraftMixin` 就用 `startAttack` / `continueAttack` 等具名目标，
  靠 refmap 处理生产环境）；
- 但**先确认 1.21.1 的 `Minecraft` 上确实有 `startUseItem()`** ——
  我没有 1.21.1 的 jar，没核对过。若方法名不同（或签名带参数），按实际签名写；
- **绝对不要**用 `lambda$xxx$N` 这类 javac 合成名（`AGENTS.md` §3 明确禁止）；
- 改完跑：
  ```bash
  python3 docs/verify_mixin_targets.py
  python3 docs/verify_shader_imports.py
  ```
  这两个脚本是你这一支特有的，**必须跑**。编译通过不等于运行期安全。

### 4. 你的瞄具是「深度孔径」架构

同 `26.1.2`：你有 `ScopeDepthCopy*`，没有 `ScopeMask*`。
**禁止**搬来源分支的 `reticleMaskable` / `bodyMaskable` / `enableViewmodelClip`。
「低倍镜准星是否被限制在目镜内」这个症状在你这一支**没有实测报告**，
不要主动改；若用户报了，按你自己的架构从头查。

### 5. LR 0.4.3 那批你已经有了，不要重复同步

已核对具备：cook = `prepare + life`、`life >= 0 && tickCount >= life` 引信判定、
`LrTickAnimationEvent` 只驱动近战、`ConsumableItemRenderer`、`DisplayTransform`、
`CombatProperties#getActionCount`。**都不用动。**

### 6. 版本号

你已经是 `R2-hotfix2`。发版规则同 `26.1.2` 那份提示词的第 4 条。

## 交付前

```bash
python3 scripts/verify_lr_assets.py --strict   # 需要先按 B2 取到脚本
python3 docs/verify_mixin_targets.py           # 你这一支必跑
```

实机清单见共用核心 §6。其中第 3、4 条（消声 + 耳鸣声）在你这一支
**本来就是好的**，改动后请确认**没有退化**——这是你这一支最主要的回归风险。
