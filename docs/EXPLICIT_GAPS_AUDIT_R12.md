# R12：显式 no-op / TODO / 空实现审计

审计日期：2026-08-13

## 对照基线

本轮不把 `return false`、`return null` 或空方法机械视为功能缺失。逐项比对：

| 基线 | Commit | 用途 |
|---|---|---|
| 本分支 1.21.11 起点 | `1bf91c0` | 识别移植后新增/保留的空实现 |
| 仓库 `26.1.2` | `6c409eea` | 旧版渲染/API 基线 |
| 仓库 `26.2(main)` | `99b472a6` | 最新上游 Feature Rendering 基线 |
| LRTactical NeoForge 1.21.1 | `Nahiyus512/...:neoforge1.21.1` | LRT 独立模块的原始功能语义 |

## 结论摘要

1. 大部分显式空方法是 **ABI 门面、默认接口、无 payload 的 marker 包、现代 collector 替代的旧即时渲染入口**，不是漏实现。
2. `AimInaccuracyModifier`、枪械等级、`tickHeat`、Accelerated Rendering、ImmediatelyFast、KubeJS 等，均与 26.1.2/26.2 的明确设计或可用依赖状态一致，不能凭空补逻辑。
3. 找到一个可安全恢复、且数据已长期携带却从未生效的真实缺口：**LRTactical 爆炸屏幕震动**。R12 已实现。
4. 审计还发现旧文档中“自定义冷却遮罩未同步”“效果云 tooltip 未迁移”已过期：当前代码已有 `ServerMessageCustomCooldown` + `GuiGraphicsMixin`，以及 `EffectCloudThrowableData#getTooltipLines()`。

## 分类结果

| 位置 / 标记 | 26.1.2 / 26.2 对照 | 判定 | R12 动作 |
|---|---|---|---|
| `GunSmithTableRecipe#matches` 返回 false | 自定义工作台由 `GunSmithTableMenu` + `CommonAssetsManager` 处理，不是原版单槽配方 | 有意 | 保留；R10 已 `isSpecial()` 隐藏 recipe-book 路径 |
| `FeatureRenderCompat#submit` 返回 false | 26.2 旧 `FabricOrderedSubmitNodeCollector` API；1.21.11 已由 `submitCustomGeometry` 直接替代，当前无调用方 | 死兼容脚手 | 保留为 ABI 门面，不把旧 API 强行移植 |
| `AimInaccuracyModifier` 多个空方法 | 26.1.2 与 26.2 完全相同，类已 `@Deprecated` 并重定向到 `InaccuracyModifier` | 上游废弃兼容 | 保留 |
| `AbstractGunItem#tickHeat` 空实现 | 两个上游均为默认扩展点，枪械数据没有完整热量系统契约 | 上游预留 | 保留 |
| `ModernKineticGunItem#getLevel/getExp/getMaxLevel` 恒 0 | 26.1.2 / 26.2 与官方 Refabricated 同样恒 0，无经验写入点 | 上游未设计 | 保留；不凭空设计等级平衡 |
| `ARCompat` / `ImmediatelyFastCompat` 空门面 | 旧 AR/ImmediatelyFast batching API 与 1.21.11 collector 不兼容；普通渲染路径完整 | 可选加速缺失 | 保留，不能误报核心功能缺失 |
| LRT `screenShakeTime/screenShakeAmplitude` 未生效 | 原始 NeoForge LRT 有 `SShakeScreenMessage` 与 camera callback；26.1.2/26.2 移植均漏此专用 payload | **真实缺口** | **R12 已恢复** |
| LRT `destroyMultiplier` 未生效 | 原始 `CustomExplosion` 只放大方块射线能量；当前 `Level#explode` 同时控制伤害、击退、方块 | 真实但不可粗暴等价 | 保留 1.0 语义，待针对 1.21.11 `ServerExplosion` 重做方块循环 |
| LRT `flash_shield` | 原作代码有功能，但模型/贴图/音效属于 ARR 且当前框架无内容包数据契约 | 真实、资源受限 | 不注册空壳物品 |
| LRT JSON 自定义尾迹粒子 | `ParticleOptions` 没有 Gson adapter；写 JSON 会静默失效 | 真实缺口 | 记录为后续独立 serializer 工作 |
| LRT 弹跳/死亡原作音效 | 代码可调用但原作音频 ARR | 授权限制 | 不伪造/复制资源 |

## R12：爆炸屏幕震动

从原始 LRTactical 恢复行为，但按 1.21.11 Fabric 的网络与相机架构实现：

```text
GrenadeEntity.onDeath (server)
  -> ServerMessageScreenShake(time, radius, amplitude, origin)
  -> PlayerLookup.world 范围广播
  -> ScreenShakeState (client)
  -> ViewportEvent.CAMERA
```

实现特性：

- 服务端仅广播视觉数据，不能由客户端伪造伤害/爆炸；
- 以游戏 tick 而非渲染帧衰减，避免 FPS 影响持续时间；
- 使用距离衰减、平滑包络、车辆减弱和最大角度上限；
- 在 TACZ 后坐事件之后叠加，且不写玩家实际 yaw/pitch，因此不影响服务端朝向；
- `screen_shake_time`、`screen_shake_amplitude` 现在确实被默认 C4 等数据消费。

## 后续审计规则

后续发现显式空实现时，按以下顺序处理：

1. 是否有实际调用点；
2. 是否在 26.1.2 与 26.2 中同样为空；
3. 是否存在现代 API 的替代链路；
4. 是否缺少数据契约、资源授权或服务端权威语义；
5. 仅对“有调用点 + 上游语义明确 + 可安全映射”的项目直接实现。

这样避免把接口默认值、过期兼容层或资源授权限制误改成会破坏存档/枪包兼容的“修复”。
