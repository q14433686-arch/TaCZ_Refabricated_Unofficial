# 第三方供弹发现策略：候选不是事实

## 结论

未知 TACZ 枪包不能由枪名、GunIndex class、模型弹匣井、`bolt`、`ammo`、
`ammo_amount` 或 `reload.type = magazine` 自动启用实体可拆卸弹匣。

这些字段最多用于缩小人工审计范围。真正会接管服务器库存、制造出口和换弹事务的
`GunFeedDefinition` 仍只能来自明确的作者声明或兼容 sidecar。

```text
作者内联 tacz_industry.feed
        或
兼容数据包 data/<namespace>/industry/gun_feed/<gun>.json
        ↓
当前 GunIndex/GunData 严格验证
        ↓
真实实体供弹事务 / 制造出口
```

没有这条链时，枪保持 legacy。候选测绘绝不生成创造弹匣、不会改写
`InstalledMagazine`，也不会随机把两个同口径枪归为可互插。

## 为什么不能直接按“类名”或栓动判断

GunIndex 的 `type`（pistol / rifle / shotgun / sniper / mg 等）是有价值的审计
线索，但不是供弹机构。

| 信号 | 可说明什么 | 不能说明什么 |
|---|---|---|
| `manual_action` | 原包有手动 bolt 行为 | AWM/M107 可拆卸弹匣与 Kar98/Mosin 固定仓都可能出现 |
| `closed_bolt` | TACZ 膛内逻辑 | 可拆卸、固定仓、管仓均可能出现 |
| `open_bolt + semi-only` | 转轮风险较高 | 不是绝对转轮证明 |
| `shotgun` class | 需重点核对管仓/盒式 | AA-12 是盒式，M870 是管式 |
| `sniper` class | 需重点核对固定/可拆卸 | AWM、M95、M107 与传统固定仓并存 |
| 模型 `magazine` / `magrelease` 骨骼 | 人工审计证据 | 服务器可验证的机械语义 |

因此 class 会被候选测绘打印，但不会直接进入 `FeedMechanism`。

## 候选测绘的硬排除与复核类别

管理员可执行：

```text
/tacz industry feed candidates
/tacz industry feed inspect <namespace:gun_id>
/tacz industry feed export
```

测绘读取当前加载的真实 GunData，并只给出下列类别：

| 类别 | 可观察信号 | 运行时行为 |
|---|---|---|
| `validated` | 已有通过验证的内联/sidecar 声明 | 按声明运行 |
| `excluded_non_magazine` | fuel / inventory / 非 MAGAZINE | 保持 legacy |
| `excluded_infinite` | 无限备弹 | 保持 legacy |
| `excluded_low_capacity` | 容量 ≤ 2 | 保持 legacy |
| `incremental_or_clip` | `loop_feed`、`roundN_feed`、`clip_load_feed` | 保持 legacy，等待 tube/clip route 审计 |
| `action_ambiguous` | open-bolt + semi-only 等转轮风险 | 保持 legacy |
| `review_external_or_fixed` | 一次性 reload 时序候选 | **仍保持 legacy**，等待确认可拆卸或固定内仓 |

`feed export` 会把全量结果写成 `config/tacz/industry-feed-survey.json`。这是单个、可提交给兼容作者审阅的 JSON 报告，不是 datapack；其中的 `draft_sidecar.mechanism` 故意为 `REQUIRES_HUMAN_CONFIRMATION`，所以无法误触发实际供弹逻辑。

对于最后两类，命令会给出一个仅供作者使用的私有 family 建议：

```text
surveyed_<namespace>_<path>
```

它不是实际 family，也不允许共享。确认可拆卸机构后，作者或兼容数据包可以把
其替换成稳定、事实性的 `magazine_family`；确认是固定仓后则写
`internal_box` / `tube` / `stripper_clip` 等正确机制。

## 公开实现的启发与不照搬之处

### TaCZMagazines

[Raiiiden/TaCZMagazines](https://github.com/Raiiiden/TaCZMagazines) 的 0.2.0
实现会自动筛选 `FeedType.MAGAZINE`、容量、reload 时间、逐发脚本参数和
open-bolt/semi-only 转轮风险，然后以 `ammo id + capacity` 分组。它同时提供
per-gun override、`none` 排除和 isolated family，说明自动猜测本身需要人工纠错。

本项目复用其**负面筛选思路**作为只读候选测绘，但不复用“同 Ammo + 同容量
自动共用实体弹匣”的结论。后者会把物理不互插的 AK、SKS、Type 81、不同枪包
平台等错误合并。

它把已安装实体弹匣的容量回写为扩容等级以驱动 HUD/模型的做法也验证了本项目
当前规则：实体载具容量优先于旧虚拟 `extended_mag` 配件。

### GunsmithLib

[ChloePrime/GunsmithLib](https://github.com/ChloePrime/GunsmithLib) 使用 Mixin 为
GunData 添加可选 `gunsmithlib_extension`。这证明“枪包作者在 GunData 写命名空间
扩展、未知字段不改变原 TACZ 行为”的模式是可行的。

本项目采用同样的 opt-in 原则，但字段为：

```json5
"tacz_industry": {
  "schema_version": 1,
  "feed": { /* 与 gun_feed sidecar 相同 */ }
}
```

为了不要求改第三方 ZIP，现有 sidecar 仍完全支持且优先级更高。

### 上游 TACZ

[MCModderAnchor/TACZ](https://github.com/MCModderAnchor/TACZ) 的公开 GunData
合同提供 FeedType、Bolt、ammo、容量、脚本和 script_param，却没有 `magwell`、
`detachable_magazine`、`fixed_box` 或 carrier family 字段。因此不存在可以从原始
通用 API 安全恢复真实弹匣井的隐藏规则。

## 推荐的枪包作者最小交付

对每把要进入实体供弹系统的枪，至少给出：

```text
机制：detachable / belt / internal / tube / clip / revolver / single
稳定 family
准确 Ammo 与基础容量
是否有真实一次性 FEEDING → FINISHING reload contract
扩容实体容量（如有）
```

然后由服务器验证当前 GunData。这样能让新枪包无需等待本项目内置映射表，也不会
让未知旧枪因一个看起来像弹匣的模型而被错误接管。
