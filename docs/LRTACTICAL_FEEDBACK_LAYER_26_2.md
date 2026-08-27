# LRTactical 反馈层在 Minecraft 26.2 的实现（2026-08-12）

## 本轮完成

### 1. 三类物品 tooltip

- 服务端安全的数据载体：`inventory/tooltip/{Throwable,Melee,Consumable}Tooltip`；
- 客户端经 Fabric `ClientTooltipComponentCallback` 转成 26.2
  `ClientTooltipComponent`；
- 绘制入口是 `extractText(GuiGraphicsExtractor, Font, x, y)`，不再使用旧
  `GuiGraphics + MultiBufferSource`；
- 支持说明文本换行、效果行折叠、Shift 展开；
- 投掷物显示冷却/预燃/持续时间及爆炸、闪光、效果云参数；
- 近战显示左右键倍率和冷却；
- 消耗品显示治疗、饥饿、效果/概率、解除效果、次数、使用时间与冷却；
- MobEffect 属性修饰通过 26.2 `MobEffect#createModifiers` 的 holder API 输出。

### 2. 使用进度 HUD

`UsingProgressOverlay` 注册到 Fabric `HudElementRegistry`，使用
`GuiGraphicsExtractor`：

- 白条：准备/使用进度；
- 红条：可预燃投掷物的引信进度。分母与 `ThrowableItem#onUseTick` 的引爆阈值**必须同步**：
  2026-08-27 同步官方 0.4.3 后两边都改成完整 `life_time`（此前是 `life_time * 0.9`，
  见 `docs/SYNC_26_2_FROM_RENOVATED_2026_08_27.md`）；
- 绿条与文本：toggle consumable；
- 近战冷却恢复进度；
- 潜行减速投掷提示没有复制原作 ARR 箭头贴图，改用四行 `fill` 画几何箭头。

### 3. 自定义分类冷却遮罩

- `ServerMessageCustomCooldown`：服务端只向所属玩家同步开始/结束；
- 客户端 `CLIENT_COOL_DOWNS` 按自身 tick 驱动，不参与权威判定；
- `GuiGraphicsExtractorMixin` 注入私有
  `itemCooldown(ItemStack,int,int)` 的 TAIL；
- 叠加方式逐坐标对齐 26.2 vanilla：`RenderPipelines.GUI`、16px 高度、
  `Integer.MAX_VALUE` 颜色；
- 因 key 是 `Identifier` 而非共享的基础 `Item`，M67 冷却不会让所有投掷物一起遮罩。

### 4. 长按右键不松手时的「自动重新读条 + 卡住」（2026-08-27 修）

**用户报告的现象**：拿着有使用时长的 LR 物品（手雷 / 闪光弹 / 消耗品）长按右键，
物品用完之后如果还不松手，进度条会**再读一次**、动作也重来一遍，但物品不会被消耗；
并且只要不松手，**姿势定格、进度条钉在最末尾**。

**根因不在我们的代码里，是原版输入循环的行为**（本地 26.2 jar 字节码逐条核对，
`Minecraft#handleKeybinds` 偏移 657-687）：

```
663  options.keyUse.isDown()      // while：右键按住期间每 tick 都进
670  rightClickDelay == 0
680  !player.isUsingItem()
687      startUseItem()           // 只要「没在使用中」就重新开始
```

即**使用一结束，下一个 tick 原版就自动再开一次**。对原版食物（吃完一个接着吃）这是特性，
对 LR 物品是 bug：服务端那一轮已经结算完（消耗 / 投出 / 进冷却），客户端却凭空再起一轮。

两类物品因此表现不同，但都指向同一处：

| 物品 | 为什么卡住 |
|---|---|
| 消耗品 | 客户端起用了，服务端因冷却拒绝 → 两端分叉；客户端这轮读条走完也不消耗任何东西（`finishUsingItem` 的效果段有 `!level.isClientSide()` 门禁），看起来就是「读了个空条」 |
| 手雷 / 闪光弹 | `ThrowableItem#getUseDuration` 与上游一致返回 **72000**（实际结束由 `releaseUsing`/`onUseTick` 决定），所以这轮凭空重来的使用**永远不会自己结束** → `isUsingItem()` 恒 true → Lua 停在 `using_hold`（姿势定格）、HUD 白条分母是 `prepare_time` 而分子一直涨（钉在末尾），直到松手 |

默认数据实测：`test_flashbang` 是 `cookable: true` / `prepare_time: 4` /
`cooldown: 20` / `cooldown_category: lrtactical:grenade` —— 按住 44 tick 就会在手心里炸掉
一颗，然后立刻触发上面那一轮幽灵读条。

**修法**：新增 `client/input/UsePressGate` + `mixin/client/MinecraftUseRestartMixin`，
把「一次按压只消耗一次使用」这条规则补上。

- 每客户端 tick **末尾**采样使用状态；发现「使用刚结束、而右键还按着、且刚用完的是
  LR 物品（`ICustomItem`）」时上锁，`Minecraft#startUseItem` 在 HEAD 被取消；松手即解锁。
- **必须挂 END 而不是 START**：26.2 `Minecraft#tick` 的顺序是
  `handleKeybinds`(偏移181) → `ClientLevel#tickEntities`(244) → `ClientLevel#tick`(379)，
  使用结束发生在实体/世界 tick 里，挂末尾才能在同一次 tick 内采到下降沿，
  而下一次 `handleKeybinds` 已是下一个 tick —— 不存在「慢一帧」的窗口。
- **必须拦在输入层而不是 `Item#use`**：`MultiPlayerGameMode#useItem` 把
  `ServerboundUseItemPacket` 放在 `startPrediction` 回调里构造，**先于**
  `ItemStack#use`。只在 `use` 里返回 FAIL 拦不住包，服务端照样被问一次，
  而服务端没有「这次按压用过了」的概念 → 换一个方向的分叉。拦在 HEAD 则两头都干净。
- 收窄范围：只对 LR 物品生效（原版「按住连吃」与其它模组不受影响）；
  只在右键仍按着时拦（TACZ `InteractKey` 主动调 `startUseItem` 不受影响）；
  投掷物正常投出时右键已抬起，不上锁，连点投掷手感不变。
- 回退方式：删掉 `tacz.fabric.mixins.json` 里 `client.MinecraftUseRestartMixin` 一行。
- 已知边界：内容包把 `use_duration` 写成 0 时，使用在同一个 tick 内起停，
  tick 末尾采不到「使用中」，不会上锁（默认枪包与 LR 官方数据都不是 0）。

**同一症状还有一条未修的路径（记录，未动）**：客户端 `use` 刻意不查 LR 冷却表
（「客户端一律乐观放行，权威判定交给服务端」是既有决定）。因此**松手再按**、
且服务端冷却仍在跑时，客户端仍会起一轮服务端不认的使用，同样会卡到松手为止。
这条路要动的是那条既有决定（客户端是否可用本地冷却表做门禁），不属于本次小修范围。

## 为什么 flash shield 没有顺手做

它不是同等级的“反馈层小补丁”，而是独立战斗子系统：

- `FlashShieldItem` 本体约 244 行；
- 需要正面受击角度判定、伤害减免、耐久、禁用冷却；
- 依赖 NeoForge shield-block/disable 事件的 26.2 Fabric 替代或 hurt 链 mixin；
- 另有 `SShieldDisable`、`SShieldShake`、动画 context；
- `FlashShieldItemRenderer` 仍是旧 `MultiBufferSource/model.render` 路径，必须完整改写成
  26.2 `SubmitNodeCollector/model.submit`；
- 原作 shield 模型、贴图、音效为 All Rights Reserved，本仓不能分发。

估计至少 700–1000 行代码与一轮独立的伤害链/渲染实机验证。把它与 tooltip、HUD、
冷却遮罩同批处理反而会违背“一次只引入一个变量”。当前保持未注册，不伪造一个只有
物品壳、不能可靠格挡的半成品。

## 建议实测

1. 打开创造栏查看测试手雷、刀、医疗包 tooltip，按 Shift 展开；
2. 按住手雷：白条完成后红条增长；潜行显示几何箭头；
   **按住不松手直到在手心里炸掉**：炸完之后进度条与动作**不应**自动再来一轮，
   姿势也不应定格；松手再按应能正常开始下一次使用；
3. 使用 toggle consumable：绿条与取消提示出现；
4. 使用带 cooldown category 的手雷/消耗品：服务端禁止重复使用，物品图标同步出现遮罩；
5. 左/右键近战：准星下方显示冷却恢复条；
6. 专用服务器重复上述项目，确认不是仅单人共享 JVM 才生效。
