# 跨分支跟进提示词（2026-08-27）

来源：本仓 `26.2` 线的一次排查（长按右键的幽灵使用 / 耳鸣消声 / 耳鸣资源 / 效果图标）。
目标：把结论推给另外五个分支的 agent，**尽量复用**，只按分支差异分版。

## 结构：1 份共用核心 + 3 份分支差异

| 文件 | 给谁 | 内容 |
|---|---|---|
| [`HANDOFF_COMMON_2026_08_27.md`](HANDOFF_COMMON_2026_08_27.md) | **所有分支** | 六分支现状矩阵、任务 A/B/C 的完整依据与代码要点、禁止事项、自查方法、验证与交付标准 |
| [`HANDOFF_TO_26_1_2.md`](HANDOFF_TO_26_1_2.md) | 本仓 `26.1.2`（Fabric） | 任务 C 需先自行核对；深度孔径架构禁搬掩码；注册位置 |
| [`HANDOFF_TO_1_21_11.md`](HANDOFF_TO_1_21_11.md) | 本仓 `1.21.11`（Fabric，混淆） | **任务 C 禁改**（该线实测有效）；混淆映射注意；`verify_mixin_targets.py` 必跑 |
| [`HANDOFF_TO_SISTER_NEOFORGE.md`](HANDOFF_TO_SISTER_NEOFORGE.md) | 姊妹仓 `TaCZ_Renovated` 三条线 | NeoForge 适配（tick 事件、mixin 注册、attachment 前提）、三条线各自的任务 C 结论、版本号写法 |

用法：把「共用核心 + 对应差异」两份一起发给该分支的 agent。

## 为什么是 3 版而不是 5 版

差异只有两个维度，交叉后正好三类：

1. **耳鸣消声的注入点该不该改** —— 取决于 MC 版本：26.x 要改（26.1.2 需先自核），
   1.21.x 禁改（实测有效）。
2. **加载器** —— Fabric（本仓）vs NeoForge（姊妹仓），影响 tick 事件、mixin 注册、
   冷却表的前提确认方式。

姊妹仓三条线的第 1 维差异用文件内的小表格区分，不再拆成三份。

## 状态矩阵的来源

全部由 `git show` / `git ls-tree` 对六个 ref 逐文件核对得出（见共用核心 §0 的表），
不是凭印象：`origin/26.1.2` `ebd0f91`、`origin/1.21.11` `423c1f7`、
`sister/26.2` `3c9b0ab`、`sister/26.1.2` `e6e5cbd`、`sister/1.21.11` `41fc53d`。

一个重要发现：**LR 0.4.3 那一批（cook 阈值、引信判定、idle 只给近战、
`display_offset`/`entity_transform`、消耗品渲染通道、`getActionCount`）五个分支都已经有了**，
所以三份提示词都明确写了「不要重复同步」——避免下一轮 agent 做无用功或引入冲突。
