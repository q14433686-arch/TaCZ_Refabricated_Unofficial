# 耳鸣消声失效 & 效果图标紫黑块 —— 定位与修复（2026-08-27）

用户报告：

1. **耳鸣的音频实际上不生效**，在 26.2 与 26.1.2（含姊妹项目）都这样，
   但 **1.21.11 上是生效的**；而且表现「没有逻辑」。
2. **耳鸣、致盲的效果贴图是紫黑块**，在 26.1.2 与 1.21.11 上。

两个问题**互不相干**，根因也不同。下面各自给结论、证据和跨分支移植清单。

---

## 问题一：耳鸣消声只对「可 tick 的音效」生效

### 结论

`me/xjqsh/lrtactical/mixin/client/SoundEngineMixin` 注入的是
`SoundEngine#calculateVolume(SoundInstance)`，而 **26.2 里新播放的音效根本不经过这个方法**。
所以耳鸣期间新响起来的声音一点没被压低；只有「可 tick 的音效」在下一 tick 被压、
以及玩家改音量滑条时重算的那批被压 —— 对玩家来说就是「有时闷有时不闷、毫无规律」，
正是「表现得没有逻辑」的来源。

### 证据（两条独立路径，结论一致）

本地 26.2 jar（`.gradle/loom-cache/.../minecraft-merged-6f7fc6e6bc-26.2.jar`）逐条核对：

```
SoundEngine.play(SoundInstance):
  @154  SoundInstance.getVolume()
  @177  SoundInstance.getSource()
  @189  SoundEngine.calculateVolume(F, SoundSource)     ← 直接调【内层】重载

calculateVolume(SoundInstance)F 的调用方【只有两个】：
  tickInGameSound()V                            @117    ← 每 tick 更新可 tick 的音效
  lambda$refreshCategoryVolume$0(...)V          @19     ← 改音量滑条时
```

- 路径 A：按指令流走一遍 `client/sounds` 包下 30 个类的所有方法；
- 路径 B：直接在每个方法的 Code 字节里搜 `calculateVolume(SoundInstance)` 那个
  Methodref 的常量池下标（`0xB6 02 38`），不依赖指令流对齐。
  两者结果一致：`play` 不在其中。

> 工具本身出过问题，记录在此以免后人重踩：第一版 class 解析器没把
> Methodref/NameAndType 存进常量池表（只存了 Utf8），导致所有「调用目标」查询返回空；
> 指令长度表也漏了 `i2f..i2s`（0x86–0x93），走到类型转换就失步。
> **只按方法名/描述符查签名不受影响**（那部分一直是对的）。

### 跨分支同源证据

`SoundEngineMixin` 的源码在 `origin/26.1.2` 与 `origin/1.21.11` 上**逐字节相同**
（`git diff` 0 行）。同一份 mixin 在 1.21.11 有效、在 26.x 无效 ⇒
差异**只可能来自 Minecraft 的 `SoundEngine`**，与上面的字节码结论吻合。
（1.21.1 那条线的 `play` 走的是外层重载；1.21.1 的 jar 本地没有，
这一句是从「同代码 + 用户实测有效」推出来的，未逐条核对。）

### 修法（已在 26.2 落地）

注入点从外层重载改为**内层** `calculateVolume(FLnet/minecraft/sounds/SoundSource;)F`。
内层才是 26.2 真正的单一收敛点：`play` 直接调它，`tickInGameSound` 与
`refreshCategoryVolume` 都经外层转调它 —— 一处覆盖三条路径。

**不要两处都挂**：外层会转调内层，两处都乘系数等于压两次（0.01 × 0.01），
耳鸣期间会近乎全静音。

### 【硬性约束】耳鸣声必须继续用 `SoundSource.MASTER`

内层重载只有 `(float, SoundSource)`，**拿不到 `SoundInstance`**，
所以原来那个 `DeafenState#isRingingSound`（靠 `instanceof` + 反射猜名字）已无处可用，
**已删除**。现在耳鸣声的豁免完全依赖：

- `StunRingingSound` 用 `SoundSource.MASTER` 构造；
- `DeafenState#getVolumeFactor` 对 `MASTER / MUSIC / UI` 整体放行。

谁把 `MASTER` 改成 `PLAYERS`（**1.21.11 与 26.1.2 用的正是 `PLAYERS`**），
耳鸣声就会被自己的消声压没。两处注释都已写明。

`sounds.json` 里的 `"category": "player"` 不参与音量计算 ——
26.2 的 `play()` 取的是 `SoundInstance#getSource()`（@177 → @189），
两者不一致无害，故未改动该文件。

### 顺带记录：淡入淡出的既有设计（未改）

`DeafenState#getVolumeFactor` 是「剩余时长 ≥ 100 tick 时压到 0.01，
最后 100 tick 线性恢复到 1.0」。因此**短时耳鸣**（默认数据 min 40 tick）
一开始就只有约 0.6 的衰减，**长时耳鸣**（max 220 tick）前 120 tick 近乎全静音 ——
强弱不一致，但这是三条分支共有的既有设计，且与 1.21.11 表现一致，
本轮**不动**（改手感需要实机对比，本环境做不到）。

---

## 问题二：效果图标紫黑块 = 贴图文件缺失

效果图标走 `assets/<ns>/textures/mob_effect/<注册名>.png`；文件不存在就是
missing texture（紫黑块）。两个效果注册名是 `lrtactical:blinded` 与 `lrtactical:deafened`
（见 `ModEffects`）。各分支实际文件：

| 文件 | 1.21.11 | 26.1.2 | 26.2（本仓） | 姊妹仓 26.2 |
|---|---|---|---|---|
| `textures/mob_effect/deafened.png` | ✗ | ✗ | ✓（18×18 RGBA） | ✗ |
| `textures/mob_effect/blinded.png` | ✗ | ✗ | **✓（本轮新增）** | ✗ |
| `sounds.json` | ✓ | ✗ | ✓ | ✗ |
| `sounds/stun_ringing.ogg` | ✗ | ✗ | ✓ | ✗ |

（`git ls-tree` 逐分支核对；姊妹仓 `TaCZ_Renovated` 26.2 的 `assets/lrtactical` 下
**没有任何 textures/ 与 sounds/**。）

所以：**26.2 上此前只有 `blinded` 是紫黑块**（`deafened` 有图），
26.1.2 / 1.21.11 / 姊妹仓是**两个都紫黑块** —— 与用户描述一致。

### 本轮新增

`textures/mob_effect/blinded.png`（18×18 RGBA，188 B），由
`scripts/gen_effect_icons.py` 生成（纯 stdlib，无 Pillow 依赖）。
图标**不是**原作 LRTactical 的美术（其素材 All Rights Reserved，本仓不分发、不二次创作），
是按既有 `deafened.png` 的同一套暖棕配色现画的「闭眼 + 斜杠」。
需要调整或给别的分支补文件时，跑脚本即可产出可复现的 PNG：

```bash
python3 scripts/gen_effect_icons.py --print blinded   # ASCII 预览
python3 scripts/gen_effect_icons.py                  # 只补缺失的
python3 scripts/gen_effect_icons.py --force          # 覆盖
```

### 顺带发现：耳鸣音源文件只有 26.2 有

`assets/lrtactical/sounds/stun_ringing.ogg` **只存在于 26.2**，
1.21.11 与 26.1.2 都只有 `sounds.json`（1.21.11）或连 `sounds.json` 都没有（26.1.2），
没有 ogg。也就是说那两条线上「耳鸣声」本身也是缺资源的
（1.21.11 上用户听到的「生效」更可能指**消声**那半边，而不是蜂鸣声）。
ogg 是用户提供的 Freesound 素材，本轮**没有**跨分支复制 —— 见下面的移植清单。

---

## 跨分支 / 姊妹仓移植清单

本会话固定在 26.2 的分支上，**不能**直接改其它分支，故列清单交接。

### → `26.1.2`（本仓）

1. **要移**：`SoundEngineMixin` 的注入点改为 `calculateVolume(FLnet/minecraft/sounds/SoundSource;)F`
   —— 但**移植前先用同一套方法核一遍 26.1.2 自己的 jar**
   （本地没有 26.1.2 的 loom 缓存，本轮无法核对；两条分支代码同构、用户实测同样失效，
   所以结论大概率一致，但不能靠推断落地）。
2. **必须同时改**：`StunRingingSound` 的 `SoundSource.PLAYERS` → `MASTER`。
   只改 mixin 不改这里，耳鸣声会被自己压没。
3. **要移**：`textures/mob_effect/deafened.png` + `blinded.png`（直接从 26.2 拷）。
4. **待定**：`sounds.json` + `stun_ringing.ogg`。ogg 是用户提供的素材，
   跨分支复制前请确认授权口径（26.2 的 `sounds.json` 注释写的是 Freesound 公开素材）。

### → `1.21.11`（本仓）

1. **不要动 mixin**。同一份源码在这条线上是**有效**的（用户实测 + 跨分支同源代码比对），
   说明 1.21.1 的 `play` 走的是外层重载。要改先用同样的字节码方法核对，别照搬 26.2 的结论。
2. **要移**：两张效果图标（当前都是紫黑块）。
3. **待定**：ogg（同上）。
4. 若日后要统一三条线的耳鸣声豁免方式，注意这条线用的是 `PLAYERS`，
   豁免依赖的是外层重载能拿到 `SoundInstance` —— 与 26.x 的机制不同，不要机械对齐。

### → 姊妹仓 `TaCZ_Renovated` 26.2

1. **要移**：mixin 注入点（其 `SoundEngineMixin` 与本仓修复前逐字节相同，同一个病）。
2. 该仓 `StunRingingSound` 用的是什么 `SoundSource` 本轮**没查**（只确认了它没有
   `assets/lrtactical` 下的 textures/sounds），移植时一并核对。
3. **要移**：两张效果图标；`sounds.json` + ogg 同样待授权确认。

---

## 验证状态（如实）

**已核对**：26.2 `SoundEngine` 的 `play` / `calculateVolume` 调用图（两种独立方法互证）；
`calculateVolume(F,SoundSource)F` 与 `calculateVolume(SoundInstance)F` 的存在与描述符；
三条分支 + 姊妹仓的资源文件清单；`SoundEngineMixin` 跨分支同源性；
`StunRingingSound` 各分支的 `SoundSource`；新 PNG 的解码回读（18×18 RGBA、颜色全部已知）。

**未做**：编译与实机（沙箱无 JDK、Maven/Fabric 源不可达）。
所以「耳鸣期间所有声音都被压低、耳鸣声本身清晰」这一条**仍需实机确认**；
1.21.1 的 `SoundEngine` 未逐条核对。

## 复测清单（26.2）

1. 扔闪光弹被震到：**枪声/脚步声/环境音应立刻明显变闷**（此前只有部分声音变闷）；
2. 耳鸣蜂鸣声应当**清晰可闻**，且不随消声一起被压掉（若听不见，先查
   `StunRingingSound` 是否还是 `SoundSource.MASTER`）；
3. 耳鸣结束前约 5 秒音量线性恢复，不应「啪」地一下跳回；
4. 效果列表 / HUD 上 `blinded` 与 `deafened` 两个图标**都不是紫黑块**；
5. 调音量滑条时不应出现异常（`refreshCategoryVolume` 也走同一个收敛点）。
