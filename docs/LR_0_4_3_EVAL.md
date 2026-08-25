# LR File 8652673（官方 1.20.1-0.4.3）评估

> 对照源：`LesRaisins-Studios/LesRaisins-Tactical-Equipements` tag `0.4.3`
> （CurseForge File ID **8652673**，`lrtactical-1.20.1-0.4.3.jar`；对应 commit `de97def`）。
>
> 截至评估日：官方主干在 `de97def` 之后没有必须跟的新提交。把 8652673 **整包**搬到 26.2 不现实（ARR 资源、flash_shield、player_animator）。

## changelog 对照

| # | 官方说明 | 难度 | 本轮 |
|---|---|---|---|
| 1 | 一批新消耗品 | 高 | **不做资源打包**（ARR）。框架已能加载第三方 consumable |
| 2 | 第三人称枪口锁 | 高 | **不做**（本仓跳过了 player_animator） |
| 3 | tooltip 改进 | 低 | 已有 expandable tooltip，不重复做 |
| 4 | 投掷物 `entity_transform` | 中 | **已做** |
| 5 | 烟雾跟环境光 | 低 | **已做**（真正改 `getLightCoords`） |
| 6 | 预燃在手上炸 | 低 | 已有 `ThrowableItem#onUseTick` |

## 本轮落地

1. **`entity_transform`**：display JSON 新字段；缺省 = 官方默认 Z90 + `(-0.3, 0.15, 0)`；自建 `EntityExtraTransform`，不用 26.2 `ItemTransform#apply` 的 -0.5 回中。`ThrowableEntityRenderer` 删写死平移。
2. **粘性雷 `STUCK_FACE`**：byte 同步 + 贴面渲染偏移 + detach 清理。经 render state 适配 26.2 两段式。
3. **烟雾环境光**：`BlockPos.containing` 处块光/天光 + `LightCoordsUtil.pack`。官方无钳制，本版也不加。

## 明确不做

- 官方内置消耗品美术、flash_shield、第三人称枪口锁、cms uv / 配方平衡。
