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
3. 使用 toggle consumable：绿条与取消提示出现；
4. 使用带 cooldown category 的手雷/消耗品：服务端禁止重复使用，物品图标同步出现遮罩；
5. 左/右键近战：准星下方显示冷却恢复条；
6. 专用服务器重复上述项目，确认不是仅单人共享 JVM 才生效。
