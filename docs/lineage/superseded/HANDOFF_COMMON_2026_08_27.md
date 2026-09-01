# 跨分支跟进 · 共用核心（2026-08-27）

> **这是三份分支提示词的共用部分。** 你只需要读这一份 + 你自己那一支的
> `HANDOFF_TO_*.md`（里面只写你这一支与共用核心的**差异**）。
>
> 来源分支：`q14433686-arch/TaCZ_Refabricated_Unofficial` 的
> `arena/01a043be-tacz-refabricated-unofficial`，提交 **`ae606f5`**
> （合并后用 `26.2(main)`）。下文所有「26.2 参考实现」都指这个 ref。
>
> 全部结论都来自实读（本地 26.2 jar 字节码 + 六个分支的 `git show` 逐文件比对），
> 不是推测。**唯一没有做的是实机验证**——那边没有 JDK 与依赖源。所以每一条都写成
> 「照此实现 + 你必须实机确认」，不要把它当成已验证结论转述给用户。

---

## 0. 现状矩阵（已核对，省得你重查）

六个分支（含来源分支）当前状态：

| 项 | 本仓 26.2（来源） | 本仓 26.1.2 | 本仓 1.21.11 | 姊妹 26.2 | 姊妹 26.1.2 | 姊妹 1.21.11 |
|---|---|---|---|---|---|---|
| 加载器 | Fabric | Fabric | Fabric | NeoForge | NeoForge | NeoForge |
| `mod_version` | `…26.2.R2-hotfix2` | `…26.1.2.R2-hotfix2` | `…1.21.11.R2-hotfix2` | `…26.2.R1` | `…26.1.2.R1.hotfix` | `…1.21.11.R1-hotfix` |
| LR 0.4.3（cook=prepare+life、`life>=0` 引信、idle 只给近战、`display_offset`/`entity_transform`、消耗品渲染通道、`getActionCount`） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `UsePressGate`（长按不松手的幽灵使用） | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `StuckUseRecovery` + `use()` 两端都查冷却 | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| 耳鸣消声注入点 | `AbstractSoundInstance#getVolume` | `SoundEngine#calculateVolume(SoundInstance)` | 同左 | 同左 | 同左 | 同左 |
| `assets/lrtactical/sounds.json` | ✅（已去掉顶层 `_comment`） | ❌ 缺文件 | ✅ 写法正确 | ❌ 缺文件 | ❌ 缺文件 | ❌ 缺文件 |
| `sounds/stun_ringing.ogg` | ✅ 28566 B | ❌ | ❌ | ❌ | ❌ | ❌ |
| `textures/mob_effect/deafened.png` | ✅ 302 B | ❌ | ❌ | ❌ | ❌ | ❌ |
| `textures/mob_effect/blinded.png` | ✅ 188 B | ❌ | ❌ | ❌ | ❌ | ❌ |
| `scripts/verify_lr_assets.py` / `gen_effect_icons.py` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| 瞄具镜内裁剪架构 | 离屏掩码 `ScopeMask*` | **深度孔径** `ScopeDepthCopy*` | 深度孔径 | 离屏掩码（已有 reticle/body 拆分） | 深度孔径 | 深度孔径 |

**读法**：LR 0.4.3 那一批六支都有了，**不要重复同步**。你要做的是下面 A/B/C 三组。

---

## 1. 任务 A：长按右键不松手时的「幽灵使用」（两支改动，都要做）

### 现象

拿着有使用时长的 LR 物品（手雷 / 闪光弹 / 消耗品）**一直按住右键**：
物品用完之后如果还不松手，进度条会**再读一次**、动作也重来一遍，但物品不会被消耗；
而且只要不松手，**姿势定格、进度条钉在末尾**。

### 根因（原版行为，不是我们的 bug）

26.2 `Minecraft#handleKeybinds` 字节码（偏移 657–687）：

```
663  options.keyUse.isDown()      // while：按住期间每 tick 都进
670  rightClickDelay == 0
680  !player.isUsingItem()
687      startUseItem()           // 只要「没在使用中」就重新开始
```

**使用一结束，下一个 tick 原版就自动再开一次。**对原版食物是特性，对 LR 物品是 bug：
服务端那一轮已经结算完（消耗 / 投出 / 进冷却），客户端却凭空再起一轮。

### A1 — `UsePressGate`：一次按压只消耗一次使用

照抄 26.2 参考实现的 `me/xjqsh/lrtactical/client/input/UsePressGate.java`
（约 120 行，纯客户端、无 mixin），并新增
`cn/sh1rocu/tacz/mixin/client/MinecraftUseRestartMixin.java`（`@Inject(method="startUseItem", at=@At("HEAD"), cancellable=true)`，
只在 `UsePressGate.shouldBlockRestart()` 时 `ci.cancel()`），在 mixin 配置里注册。

上锁的**三个条件缺一不可**（少一个就会误伤）：
1. 右键**仍按着**（松手结束的使用是正常的，连点投掷不能受影响）；
2. 刚用完的是 LR 物品（`ICustomItem`）——原版「按住连吃」与其它模组一概不管；
3. 手里**还是同一件物品**——使用中途切快捷栏导致的结束不算「用完了」，不能拦住新物品。

解锁：右键抬起即解锁。玩家实例变化（重生 / 跨维度）时全部作废。

### A2 — `use()` 两端都查冷却 + `StuckUseRecovery` 兜底

六支的 `ThrowableItem#use` 当前都是同一个形状（第 163–171 行）：

```java
boolean onCooldown = false;
if (!level.isClientSide()) {          // ← 只查服务端
    …
}
```

**去掉 `if (!level.isClientSide())` 外壳，两端都查各自那张表**
（`ModCapabilities#coolDowns` 已经按端返回 `SERVER_COOL_DOWNS` / `CLIENT_COOL_DOWNS`）。
`ConsumableItem#use` 同样处理。

为什么客户端可以当门禁（三条依据，都核对过）：
1. **客户端表确实在走**：`ModCapabilities#init` 把 `coolDowns(player).tick()` 挂在
   `PlayerTickEvent.START`，而 `PlayerMixin` 注入的是 `Player#tick` 的 **HEAD、不分端**；
2. **偏差方向安全**：客户端 `startTime` 取自收到 `ServerMessageCustomCooldown` 那一刻的本地
   `tickCount`，必然**不早于**服务端起点 ⇒ 只会「多拒一会儿」，不会「少拒」。
   多拒一次 = 玩家再按一下；少拒一次 = 卡死；
3. **窗口会被显式收口**：服务端冷却到期时 `onCooldownEnded` 再发一条 `duration=0`，
   客户端立刻 `removeCooldown`，多拒窗口≈一个单向延迟。

服务端仍是唯一权威（真正投不投出仍由 `releaseUsing` 的服务端判定决定）。

再加 `me/xjqsh/lrtactical/client/input/StuckUseRecovery.java` 兜底：
客户端若陷进服务端不存在的使用状态，越过「数据允许的最长预燃 + 20 tick 延迟余量」
就本地 `stopUsingItem()`。
- **必须是 `stopUsingItem()`，不是 `releaseUsingItem()`** —— 后者会回调
  `Item#releaseUsing`，在投掷物上那就是**真的把手雷扔出去**；
- 只处理 `cookable=true` 且 `life_time > 0` 的投掷物：非预燃投掷物一直按着等投是
  合法操作；C4 的 `life_time=-1` 没有「最长按住时长」可言，都不碰；
- 与 A1 是「兜底 + 防复发」的组合：收手后 A1 会采到下降沿并上锁，不会立刻又重开。

---

## 2. 任务 B：耳鸣资源（六支里五支都缺，必做）

### B1 — `sounds.json`：**顶层不能有 `_comment`**

这是 26.2 上耳鸣声一直不响的**真因**，已由用户实机日志确认
（`result=NOT_STARTED`）。机制：`SoundManager` 把 `assets/<ns>/sounds.json` **整体**按
`Map<String, SoundEventRegistration>` 用 Gson `fromJson` 反序列化
（`SoundManager` 常量池里可直接看到 `TypeToken<Map<String,SoundEventRegistration>>`），
**顶层每个键都会被当成音效 id**；一个值是字符串的 `_comment` 会让**整个文件作废**，
该命名空间一个音效定义都没有 → `resolve` 置 `EMPTY_SOUND` → `play` 返回 `NOT_STARTED`。

正确内容（**逐字节照抄，不要加任何顶层注释键**）：

```json
{
  "entity.stun_grenade.ringing": {
    "category": "player",
    "sounds": [
      {
        "name": "lrtactical:stun_ringing",
        "stream": false
      }
    ]
  }
}
```

> 别外推：`items/*.json`、`models/item/*.json` 里的 `_comment` 是**对象内部**字段，无害。
> `sounds.json` 的顶层是 map，性质完全不同。

音源出处（原来写在 `_comment` 里，搬到这里保存）：用户提供 Freesound 公开/CC 素材，
已转 OGG Vorbis（MC 不接受 wav/mp3），并做等功率交叉淡化处理成可循环片段。

### B2 — 从来源分支取二进制与脚本

```bash
SRC=ae606f5   # 合并后可换成 26.2(main)
R=q14433686-arch/TaCZ_Refabricated_Unofficial
git fetch https://github.com/$R.git $SRC          # 或已有 remote 时直接 fetch
for f in src/main/resources/assets/lrtactical/sounds/stun_ringing.ogg \
         src/main/resources/assets/lrtactical/textures/mob_effect/deafened.png \
         src/main/resources/assets/lrtactical/textures/mob_effect/blinded.png \
         scripts/verify_lr_assets.py \
         scripts/gen_effect_icons.py ; do
  mkdir -p "$(dirname $f)"
  git show FETCH_HEAD:$f > $f
done
```

`deafened.png` / `blinded.png` 缺失时效果图标是**紫黑块**（missing texture）。
两张图是**自绘**的（18×18 RGBA），不使用原作 LRTactical 的 ARR 美术；
要改样式跑 `python3 scripts/gen_effect_icons.py --force`。

### B3 — 让失败可见（很值钱，别省）

`DeafenState#tick` 里接住 `SoundManager#play` 的返回值
（`SoundEngine.PlayResult.STARTED / STARTED_SILENTLY / NOT_STARTED`），
非 `STARTED` 就 **WARN 一次**（只一次，别每 tick 刷屏），消息里列出三个已知坑：
① `sounds.json` 顶层混入非对象值；② ogg 不存在；③ 对应音量滑条为 0。
参考实现已经把这段写好了，照抄即可。

> 这一条是本次排查里回报最高的改动：26.2 上唯一的线索原本是一条 **DEBUG** 级别日志，
> 默认看不见，导致连续两轮查错方向。

---

## 3. 任务 C：耳鸣**消声**的注入点（**条件性**，看你那一支的 MC 版本）

六支现在的 `me/xjqsh/lrtactical/mixin/client/SoundEngineMixin.java` **逐字节相同**，
都注入 `SoundEngine#calculateVolume(SoundInstance)`。

**在 26.x 上这个注入点是错的。**26.2 `SoundEngine` 字节码：

```
play(SoundInstance):
  @154  SoundInstance.getVolume()
  @177  SoundInstance.getSource()
  @189  calculateVolume(F, SoundSource)        ← play 直接调【内层】重载

calculateVolume(SoundInstance) 的调用方只有两个：
  tickInGameSound()V                    @117   ← 每 tick 更新「可 tick 的」音效
  lambda$refreshCategoryVolume$0(...)V  @19    ← 改音量滑条时
```

⇒ 耳鸣期间**新响起来的声音一点没被压低**，只有「可 tick 的音效」和改滑条时重算的那批被压。
玩家看到的就是「有时闷有时不闷、毫无规律」。

**正确注入点：`AbstractSoundInstance#getVolume()`**
（把 `SoundEngineMixin` 换名为 `SoundInstanceVolumeMixin`，同步改 mixin 配置里的条目）。
理由：
- **覆盖完整**：`play()` 在 @154 取 `getVolume()` 再交给 @189；
  `calculateVolume(SoundInstance)` 的实现也就是 `calculateVolume(getVolume(), getSource())`。
  三条路径全读它，一处全覆盖，且**不会**像内外两层都挂那样把系数乘两次；
- **拿得到实例**：`this` 就是音效实例，`instanceof StunRingingSound` 即可豁免，
  不依赖 `SoundSource` 是哪个类别；
- 已知边界：只覆盖 `AbstractSoundInstance` 子类（原版与绝大多数模组）。

**⚠️ 但 1.21.x 上不要动这个 mixin** —— 用户实测 1.21.11 的消声是**生效**的，
说明 1.21.1 的 `play` 走的是外层重载。同一份代码在两条线表现不同 ⇒ 差异来自引擎。
要不要改，先用下面第 5 节的方法核你自己那一支的 jar，别照搬结论。

---

## 4. 禁止事项（都是这几轮真踩过的坑）

1. **不要把「仓库里缺文件」当成「用户实机缺文件」**，更不要用它否定用户的实测。
   本仓 26.2 的 ogg 存在、1.21.11 的仓库里没有，但用户实测 1.21.11 **有声**。
2. **不要把离屏掩码那套（`ScopeMask*` / `reticleMaskable` / `enableViewmodelClip`）
   搬进深度孔径分支。**两套架构完全不同（26.2 是离屏掩码 + shader discard，
   26.1.2/1.21.11 是深度孔径），照搬会直接编译不过或行为错乱。
3. **不要声称未实机验证的修复。**写 commit / 文档时区分「源码级闭环」与「实机 PASS」。
4. **不要为了对齐而机械统一 `SoundSource`。**六支的耳鸣声现在都是 `PLAYERS`，
   而豁免是靠 `instanceof` 做的，与类别无关；改成别的类别只会引入新变量。
5. **不要在 `sounds.json` 顶层加注释键**（见 B1）。
6. **改版本号要守规矩**：hotfix 序号**直接**接在 `hotfix` 后面
   （`R2-hotfix2`），中间不放 `.` / `-` / `_` —— TaCZTweaks 按版本号字符串识别本项目。
   本仓 `scripts/check_release_consistency.sh` 已接受该形式；改版本号必须同步 README
   （见 `AGENTS.md` §1 的六处）。

---

## 5. 你自己核对引擎行为的方法（不要信我的结论，去核）

本地 loom 缓存里有映射好的 MC jar，可以直接读字节码：

```
.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/<mc>/*.jar
```

要点：
- 类文件常量池里能读到方法名/描述符与调用目标；`javap` 需要 JDK，
  没有 JDK 时用 Python 解析 class 文件（`struct` + 常量池表）即可；
- **两个坑**（我踩过）：① 常量池要把 Methodref/NameAndType 也存下来，
  只存 Utf8 会让所有「调用目标」查询返回空；② 指令长度表别漏 `i2f..i2s`（0x86–0x93），
  漏了走到类型转换就失步，后面全是错的；
- **先做 sanity check**：拿一个你确定存在的调用去验工具，返回空说明工具坏了，
  不是结论。

---

## 6. 验证与交付标准

必跑（都是纯 stdlib，无需编译）：

```bash
python3 scripts/verify_lr_assets.py --strict   # sounds.json 结构 / ogg / 效果图标
```

必做（需要能跑游戏的环境；没有就**如实说明**，不要含糊过去）：

1. 长按右键把手雷/闪光弹/消耗品用完 → **不应**自动再读一次条，姿势不应定格；
   松手再按应能正常开始下一次；连点投掷手感不变；
2. 冷却期内（20–40 tick）松手再按 → 不应出现「读条但物品不消耗」；
3. 被闪光弹震到 → 枪声/脚步/环境音应**立刻整体变闷**；
4. **耳鸣蜂鸣声清晰可闻**，且不随消声一起被压掉；
   若听不见，看日志有没有
   `[LRTactical] Stun ringing sound did not start: result=…`
   —— `NOT_STARTED` = 被引擎拒了（资源/音量）；**完全没有这行** = 根本没走到播放
   （查 `DeafenState#tick` 或 `DEAFENED` 是否同步到客户端）；
5. 效果列表 / HUD 上 `blinded` 与 `deafened` 两个图标都不是紫黑块；
6. 高延迟下重复第 1、2 条：即使分叉也应约 1 秒内自行恢复（`StuckUseRecovery`）。

交付时请写明：改了哪些文件、每条对应上面哪一项、哪些**已实机确认**、哪些**只是源码级**。
