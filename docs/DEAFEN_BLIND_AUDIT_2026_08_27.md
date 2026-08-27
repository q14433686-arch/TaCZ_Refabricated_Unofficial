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

> **本节记录的是第一轮修法。当晚用户实测：消声生效了，但耳鸣声听不见 ——
> 于是有了第二轮，最终落地的注入点是 `AbstractSoundInstance#getVolume()`。
> 见文末「追加：耳鸣声听不见」一节。两轮的证据都保留，别只看前半段。**

### 修法（第一轮，已被第二轮取代）

注入点从外层重载改为**内层** `calculateVolume(FLnet/minecraft/sounds/SoundSource;)F`。
内层才是 26.2 真正的单一收敛点：`play` 直接调它，`tickInGameSound` 与
`refreshCategoryVolume` 都经外层转调它 —— 一处覆盖三条路径。

**不要两处都挂**：外层会转调内层，两处都乘系数等于压两次（0.01 × 0.01），
耳鸣期间会近乎全静音。

### ~~【硬性约束】耳鸣声必须继续用 `SoundSource.MASTER`~~（**已作废**）

> 第一轮把耳鸣声的豁免挂在「它用 `SoundSource.MASTER` 构造」上，并把这条写成硬约束。
> **当晚实测：耳鸣声听不见了。** 该约束连同 MASTER 一起被第二轮撤掉，
> 豁免改为在 `AbstractSoundInstance#getVolume()` 里用 `instanceof` 做实例级判断。
> 保留这段作废记录，是为了说明「为什么不能把豁免押在 SoundSource 类别上」——
> 见文末「追加：耳鸣声听不见」。

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
1.21.11 与 26.1.2 都没有这个文件（1.21.11 只有 `sounds.json`，26.1.2 连 `sounds.json` 都没有）。

> **【已撤回的推断】** 本文初版据此写了一句「1.21.11 上用户听到的『生效』更可能指消声
> 那半边，而不是蜂鸣声」。**这句是错的**：用户随后实测 1.21.11，**确实有耳鸣声**。
> 仓库里没有那个 ogg，但实机有声 —— 说明用户测试用的 1.21.11 客户端里的音源不来自
> 本仓的工作树（可能是本地未提交的文件、资源包，或另一处构建产物）。
> 我没法从这个仓库里查证是哪种，**不再猜测**；能确定的只有「1.21.11 实机有耳鸣声」这个事实。
> 教训：仓库里缺文件 ≠ 用户实机缺文件，别拿前者否定后者的实测。

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
4. **要移**：`sounds.json` + `sounds/stun_ringing.ogg`（这条线两个都没有，所以耳鸣声一定不响）。
   **注意 `sounds.json` 不要带顶层 `_comment`** —— 26.2 会把整个文件反序列化失败。
   搬完跑 `python3 scripts/verify_lr_assets.py --strict` 自查。
   ogg 是用户提供的 Freesound 公开/CC 素材（出处见第三轮那节），跨分支复制前确认授权口径。

### → `1.21.11`（本仓）

1. **不要动 mixin**。同一份源码在这条线上是**有效**的（用户实测 + 跨分支同源代码比对），
   说明 1.21.1 的 `play` 走的是外层重载。要改先用同样的字节码方法核对，别照搬 26.2 的结论。
2. **要移**：两张效果图标（当前都是紫黑块）。
3. **要移**：`sounds/stun_ringing.ogg`（这条线缺音源文件；`sounds.json` 本身已有且写法正确）。
   搬完同样跑 `verify_lr_assets.py`。
4. 若日后要统一三条线的耳鸣声豁免方式，注意这条线用的是 `PLAYERS`，
   豁免依赖的是外层重载能拿到 `SoundInstance` —— 与 26.x 的机制不同，不要机械对齐。

### → 姊妹仓 `TaCZ_Renovated` 26.2

1. **要移**：mixin 注入点（其 `SoundEngineMixin` 与本仓修复前逐字节相同，同一个病）。
2. 该仓 `StunRingingSound` 用的是什么 `SoundSource` 本轮**没查**（只确认了它没有
   `assets/lrtactical` 下的 textures/sounds），移植时一并核对。
3. **要移**：两张效果图标；`sounds.json`（**不带顶层 `_comment`**）+ ogg。
   该仓 `assets/lrtactical` 下目前 textures/ 与 sounds/ 都是空的。
4. 建议把 `scripts/verify_lr_assets.py` 一起搬过去（只依赖 stdlib 与文件布局）。

---

## 追加：耳鸣声听不见（同日第二轮）

用户实测第一轮的结果：**消声生效了，但耳鸣蜂鸣声依旧听不见**；
并且实测 1.21.11 **确实有耳鸣声**（推翻本文上面那句已撤回的推断）。

### 定位

26.2 `SoundEngine#play` 的字节码里有一条**只在 DEBUG 级别打日志**的静默丢弃分支：

```
@306  if (volume > 0) goto 355
@310  SoundInstance.canStartSilent()
@320  SoundSource.MUSIC
@338  LOGGER.debug("Skipped playing sound {}, volume was zero.")
@351  return NOT_STARTED          ← 默认日志级别下完全看不见
```

而音量 = `clamp(getVolume(),0,1) * clamp(options.getSoundSourceVolume(source),0,1)`
（`calculateVolume(F,SoundSource)` 的实现），
`Options#getSoundSourceVolume` = `soundSourceVolumes.get(source).get().floatValue()`
（`getSoundSourceOptionInstance` 就是 `Objects.requireNonNull(map.get(source))`）。

第一轮为了豁免消声，把耳鸣声改成了 `SoundSource.MASTER` —— 如果 MASTER 在这张表里
取到 0，就会走进上面那条静默分支，表现正是「不报错、也不响」。
**MASTER 在该表中的取值我没能从字节码定案**（`soundSourceVolumes` 的填充点在
流/lambda 里，没继续挖）。但结论不依赖它：既然存在这条静默路径，
就不该把耳鸣声押在 MASTER 上。1.21.11 用的是 `PLAYERS` 且实机可闻。

### 最终修法（三处）

1. **`StunRingingSound`：`SoundSource.MASTER` → `PLAYERS`**（与 1.21.11 一致、用户实测可闻）。
   `sounds.json` 里的 `"category": "player"` 现在也与之一致了。
2. **消声注入点搬到 `AbstractSoundInstance#getVolume()`**
   （新 mixin `SoundInstanceVolumeMixin`，删除 `SoundEngineMixin`）。理由：
   - **覆盖完整**：`play()` 在 @154 取 `getVolume()` 再交给 @189 的
     `calculateVolume(F,SoundSource)`；`calculateVolume(SoundInstance)` 的实现也就是
     `calculateVolume(getVolume(), getSource())`。三条路径（新播放 / tick 更新 / 改滑条）
     全都读它，一处即全覆盖，且不会像「内外两层都挂」那样把系数乘两次。
   - **拿得到实例**：`this` 就是音效实例，`instanceof StunRingingSound` 即可豁免 ——
     不再依赖 SoundSource 是哪个类别，「改类别就把耳鸣压没」的隐雷随之消失。
   - 已知边界：只覆盖 `AbstractSoundInstance` 的子类（原版与绝大多数模组）；
     直接实现 `SoundInstance` 接口的音效不会被消声，可接受的降级。
3. **让失败可见**：`DeafenState#tick` 接住 `SoundManager#play` 的返回值
   （`PlayResult.STARTED / STARTED_SILENTLY / NOT_STARTED`），非 `STARTED` 就 **WARN 一次**。
   上一轮之所以排查很久，正是因为唯一的线索是一条 DEBUG 日志。

### 跨分支清单据此更新

- **→ 26.1.2**：`SoundInstanceVolumeMixin`（而非第一轮的 `calculateVolume` 方案）；
  它的 `StunRingingSound` 本来就是 `PLAYERS`，**不用改类别**。
- **→ 1.21.11**：消声 mixin **仍然不要动**（该线实测有效）；
  但 `DeafenState#tick` 的 PlayResult 告警值得回移 —— 1.21.1 的
  `SoundManager#play` 是否有同样的返回值**未核对**，回移前先确认签名。
- **→ 姊妹仓 26.2**：同 26.2 的三处。

## 追加：真正的根因 —— `sounds.json` 顶层的 `_comment`（同日第三轮）

用户带回了第二轮加的那行告警：

```
[Render thread/WARN]: [LRTactical] Stun ringing sound did not start:
    result=NOT_STARTED id=lrtactical:entity.stun_grenade.ringing
```

`NOT_STARTED` 把范围缩到 `play()` 的几条分支，而其中「找不到音效定义 →
`EMPTY_SOUND` → NOT_STARTED」（@116–@152）与下面这个结构差异对上了：

**26.2 的 `SoundManager` 把 `assets/<ns>/sounds.json` 整体按
`Map<String, SoundEventRegistration>` 用 Gson `fromJson` 反序列化**
（`SoundManager` 常量池里可直接看到
`TypeToken<Map<String,SoundEventRegistration>>` 与 `fromJson`）。
也就是说**顶层的每个键都会被当成音效 id**。而我们的文件是：

```json
{
  "_comment": "Sound index for LRTactical. The tinnitus clip was supplied by ...",
  "entity.stun_grenade.ringing": { "category": "player", "sounds": [ ... ] }
}
```

`_comment` 的值是**字符串**，不是 `SoundEventRegistration` 对象 →
反序列化失败 → **整个文件作废** → `lrtactical` 命名空间一个音效定义都没有 →
`getSoundEvent(id)` 返回 null → `resolve` 置 `EMPTY_SOUND` → `NOT_STARTED`。

**与 1.21.11 的对照刚好印证**：那条线的 `sounds.json` **没有 `_comment`**
（就一个 `entity.stun_grenade.ringing` 条目），所以它能响。
这也解释了为什么这个坑只在 26.2 上出现——文件内容不同，不是引擎不同。

> 注意别把这个结论外推到别的资源文件：`items/*.json`、`models/item/*.json` 里的
> `_comment` 是**对象内部**的字段，无害。`sounds.json` 的顶层是 map，性质完全不同。

### 修法

1. **删掉 `sounds.json` 顶层的 `_comment`**（信息搬到本文，不丢）。
   原文记录在此：音源是用户提供的 Freesound 公开/CC 素材，已转成 OGG Vorbis
   （Minecraft 不接受 wav/mp3），并做了等功率交叉淡化处理成可循环片段。
2. **新增 `scripts/verify_lr_assets.py`**，把这类「静默失效」变成可自查项：
   - `sounds.json` 顶层是否存在非对象值（就是这个坑）；
   - 每个 `sounds[].name` 指向的 `.ogg` 是否真的存在（26.1.2 / 1.21.11 都缺）；
   - `ModEffects` 注册的每个效果是否有 `textures/mob_effect/<id>.png`（紫黑块）。
   已做过反向验证：把修复前的文件放回去，脚本 `--strict` 退出 1 并指名 `_comment`。
3. **把已知坑写进那行 WARN**，下次不用再从头查。

### 对前两轮结论的修正

- 第二轮怀疑的「MASTER 在 `soundSourceVolumes` 里取到 0 → 静默丢弃」**没有得到证实**，
  现在看**不是**本次不响的原因（真正原因是定义根本没加载，与音量无关）。
  不过第二轮的三处改动仍然保留，理由各自独立成立：
  `PLAYERS` 与 1.21.11 一致且用户实测可闻；`getVolume()` 注入点覆盖更全、
  且让豁免不再依赖 SoundSource 类别；`PlayResult` 告警正是这次定位到根因的原因。
- 教训记两条：**①「仓库里缺文件」和「用户实机缺文件」是两回事**（第一轮的错）；
  **② 静默失败必须先变成可见失败，才谈得上定位**（第二轮加的那行 WARN 值回本次全部成本）。

## 验证状态（如实）

**已核对**：26.2 `SoundEngine` 的 `play` / `calculateVolume` 调用图（两种独立方法互证）；
`calculateVolume(F,SoundSource)F` 与 `calculateVolume(SoundInstance)F` 的存在与描述符；
三条分支 + 姊妹仓的资源文件清单；`SoundEngineMixin` 跨分支同源性；
`StunRingingSound` 各分支的 `SoundSource`；新 PNG 的解码回读（18×18 RGBA、颜色全部已知）。

**第二轮新增核对**：`SoundEngine#play` 全部分支（含 @306 的静默丢弃）、
`Options#getSoundSourceVolume` / `getSoundSourceOptionInstance` 的实现、
`AbstractSoundInstance#getVolume()F` 存在且为 public 非 final、
`SoundManager#play` 返回 `SoundEngine$PlayResult`（常量 STARTED/STARTED_SILENTLY/NOT_STARTED）、
`SoundSource` 常量表（MASTER/MUSIC/PLAYERS/UI 等均在）。

**第三轮新增核对**：`SoundManager` 常量池中的
`TypeToken<Map<String,SoundEventRegistration>>` 与 `fromJson`（即 sounds.json 的整体解析方式）；
`play()` 中「定义为空 → `EMPTY_SOUND` → NOT_STARTED」的分支（@116–@152）；
`verify_lr_assets.py` 对修复前/后两个文件的双向验证（前者退出 1，后者退出 0）。

**未做**：编译与实机（沙箱无 JDK、Maven/Fabric 源不可达）。所以
①「删掉 `_comment` 后耳鸣声就能响」仍是**待实机确认**的结论——机制是从解析方式推出的，
我没有真的跑一次 Gson；
②`soundSourceVolumes` 里 MASTER 的取值没定案（已不是本问题的关键）；
③1.21.1 的 `SoundEngine` 未逐条核对。
若这次仍听不见，日志里现在会有一行 `[LRTactical] Stun ringing sound did not start: result=...`
——把那行贴出来就能直接定位（`NOT_STARTED` = 被引擎拒了，多半是资源或音量；
没有这行 = 根本没走到播放，问题在 `DeafenState#tick` 或效果同步）。

## 复测清单（26.2，第二轮之后的最终状态）

1. 扔闪光弹被震到：**枪声/脚步声/环境音应立刻明显变闷**（第一轮已实测生效，
   换注入点后需再确认没退化）；
2. **耳鸣蜂鸣声应当清晰可闻**，且不随消声一起被压掉。
   若仍听不见：看日志里有没有
   `[LRTactical] Stun ringing sound did not start: result=...`
   —— `NOT_STARTED` 说明被引擎拒了（资源/音量），**没有这行**说明根本没走到播放
   （问题在 `DeafenState#tick` 或 DEAFENED 效果没同步到客户端）；
3. 耳鸣结束前约 5 秒音量线性恢复，不应「啪」地一下跳回；
4. 效果列表 / HUD 上 `blinded` 与 `deafened` 两个图标**都不是紫黑块**；
5. 调音量滑条时不应出现异常（三条路径现在共用 `getVolume()` 这一个收敛点）；
6. 把「玩家」音量滑条拉到 0 时耳鸣声会消失 —— 这是改用 `PLAYERS` 的预期行为，
   不是 bug（1.21.11 同样如此）。
