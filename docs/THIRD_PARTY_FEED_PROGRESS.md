# 第三方枪包供弹兼容进度

此文件由 `tools/generate_third_party_feed_progress.py` 从指定的 survey 导出生成，
只供作者/CI追踪；普通玩家不需要运行 Python。它不启用任何供弹。

## 当前 survey 来源

- schema：`2`
- SHA-256：`919d71b5e0217bc061e45e4b386328f8f7aaf27b00ec29e72a43e85938e3a20a`
- 原始汇总：`{"excluded": 46, "incremental_or_clip": 57, "review_required": 428, "total": 594, "validated": 63}`

## 按枪包进度

| 枪包 | Namespace | Survey 总数 | 已有事实 profile | Active 实体供弹 | Explicit legacy | 未覆盖 | 状态 |
|---|---|---:|---:|---:|---:|---:|---|
| Default TACZ | `tacz` | 54 | 53 | 52 | 2 | 1 | `in_progress` |
| Apocalypse | `bf1` | 30 | 11 | 11 | 0 | 19 | `in_progress` |
| Cold War | `rainforest` | 17 | 15 | 15 | 0 | 2 | `in_progress` |
| GunpowderRevolution | `hamster` | 36 | 13 | 13 | 0 | 23 | `in_progress` |
| Enlisted | `ww` | 52 | 26 | 26 | 0 | 26 | `in_progress` |
| CCRP / ClassicR | `ccrp, classicr` | 164 | 164 | 136 | 28 | 0 | `complete` |
| CIBR | `cib, cibs` | 106 | 106 | 82 | 24 | 0 | `complete` |
| KhanPowder | `murasamet` | 76 | 76 | 28 | 48 | 0 | `complete` |
| Suffuse GunSmoke | `suffuse` | 46 | 21 | 21 | 0 | 25 | `in_progress` |
| Delta Force: Storm Assault | `wemql_r` | 13 | 13 | 13 | 0 | 0 | `complete` |

`complete` 的含义是该 survey 中每把枪都已有明确 reference 结论；它不意味着每把都被强行实体弹匣化。
其中的 `Explicit legacy` 是有意保留原包行为、并记录原因的安全结论。

## Namespace 机制明细

| Namespace | Active mechanism 数量 |
|---|---|
| `bf1` | `belt`=2, `detachable_magazine`=9 |
| `ccrp` | `belt`=2, `detachable_magazine`=108 |
| `cib` | `belt`=6, `detachable_magazine`=61 |
| `cibs` | `detachable_magazine`=15 |
| `classicr` | `belt`=1, `detachable_magazine`=25 |
| `hamster` | `belt`=1, `detachable_magazine`=4, `en_bloc_clip`=1, `speedloader`=2, `stripper_clip`=5 |
| `murasamet` | `belt`=2, `detachable_magazine`=26 |
| `rainforest` | `belt`=3, `detachable_magazine`=11, `stripper_clip`=1 |
| `suffuse` | `belt`=1, `detachable_magazine`=20 |
| `tacz` | `belt`=2, `detachable_magazine`=36, `internal_box`=2, `revolver`=3, `single_shot`=5, `stripper_clip`=1, `tube`=3 |
| `wemql_r` | `detachable_magazine`=13 |
| `ww` | `belt`=3, `detachable_magazine`=22, `en_bloc_clip`=1 |
