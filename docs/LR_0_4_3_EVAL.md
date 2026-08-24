# LR File 8652673（官方 1.20.1-0.4.3）评估

> 对照源：`LesRaisins-Studios/LesRaisins-Tactical-Equipements` tag `0.4.3`
> （CurseForge File ID **8652673**，2026-08-15，`lrtactical-1.20.1-0.4.3.jar`）。
> 官方 changelog 六条，下面按「难度 / 本仓现状 / 本轮是否动手」写。

## changelog 对照

| # | 官方说明 | 难度 | 本仓现状 | 本轮 |
|---|---|---|---|---|
| 1 | Added a batch of new consumables | **高** | 消耗品数据/物品/tooltip 框架已在；官方新增 10 种消耗品（ai2 / cms / blood_pack 等）带 **ARR 模型、贴图、动画、音效** | **不做资源打包**。框架已能加载第三方内容包自己的 consumable index/display。把官方美术打进本仓不合法。 |
| 2 | Fixed muzzle direction locked in TACZ third-person holding animation | **高** | 官方修在 `player_animator` 兼容层（`PlayerAnimatorAssetManagerMixin` 等）。本仓第一版跳过了整块 `compat/player_animator/` | **不做**。要跟就要单独开 PAL/player_animator 议题，不能把 1.20.1 Forge mixin 直接贴到 26.2。 |
| 3 | Improved tooltips: display some data + configurable custom descriptions | **低** | 26.2 extracted-GUI 的 expandable tooltip 已在（描述折叠、数值行、Shift 展开） | **已有，不重复做**。 |
| 4 | Additional transforms for grenade entities + fix wrong orientations in some addons | **中** | 本仓渲染器写死了两段 translate，没有 `entity_transform` | **已做**。见下。 |
| 5 | Smoke particles now respond to ambient lighting | **低** | 本仓故意满亮（15728880） | **已做**。改为采环境光，下限钳 2，邻格兜底。 |
| 6 | Cookable throwables explode after fuse expires in hand | **低** | 本仓 `ThrowableItem#onUseTick` 已经做了，而且比官方更谨慎（先 `stopUsingItem` 再投，避免 `releaseUsingItem` 递归） | **已有，保持本仓写法**。 |

## 本轮落地的 0.4.3 行为

1. **`entity_transform`**
   - display JSON 新字段，缺省 = 官方默认（绕 Z 90° + 平移 `(-0.3, 0.15, 0)`）。
   - 不用 26.2 `ItemTransform#apply`（它自带 `-0.5` 物品槽回中）。
   - `ThrowableEntityRenderer` 去掉写死的补偿平移，改走数据包变换。
2. **粘性雷贴面方向**
   - 官方 0.4.3 给 `StickyGrenadeEntity` 加了同步的 `STUCK_FACE`，渲染时按法线微移，避免「嵌进墙里朝向错」。
   - 本仓补了同一条同步数据与渲染偏移。
3. **烟雾环境光**
   - `SmokeCloudParticle#getLightCoords` 改为天光/块光采样，不再永远满亮。

## 明确不做、以及为什么

- **官方内置消耗品资源**：ARR。本仓定位仍是「代码框架 + 内容包自带资源」。
- **flash_shield**：0.4.3 也还在，但整条盾牌/禁用/网络/渲染仍是单独大项，不塞进这次。
- **第三人称枪口锁**：属于 TACZ + player_animator，不是 LR 物品逻辑；26.2 PAL 路径与 1.20.1 不同。
- **player_animator / JEI / Cloth 整包**：第一版就跳过，0.4.3 没有改变这个判断。

## 难度总评

「把 8652673 整包搬到 26.2」不现实：资源 ARR、盾牌未移植、第三人称动画在另一套 API 上。
跟进行为层（手雷朝向、烟雾光照、预燃在手上炸）是中低难度，本轮能做的已经做完。
消耗品「一批新物品」对玩家可见的部分几乎全是美术，代码框架早就接得上内容包。
