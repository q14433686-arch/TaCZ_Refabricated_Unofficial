# 从 1.20.1 包导出 26.3 兼容层

有管理员权限时，先把已有 `gunpack.meta.json` 的源目录或 ZIP 放在 `tacz/` 下。**先检查，再决定是否导出**。不传 `export` 时只写报告；不会自动安装或在进服时改写枪包。

```text
/tacz convert universal "Example Pack.zip" check
/tacz convert universal "Example Pack.zip" export
/tacz convert universal all check
```

结果在游戏目录的 `tacz-converted/`，不在加载目录：`*-universal.report.json` 和（仅导出）`*-universal.zip`。已有 ZIP 不覆盖，后续报告使用新文件名。先看报告，备份存档，单独安装导出包到测试实例（不要与同一源包一起加载）。原来的 `/tacz convert` 仍处理更早的遗留布局，不是这条命令。

- 原始资源与已有 `compat_1_21_1` 层按原字节保留；导出 ZIP 的根元数据增加匹配 **26.3** 的 `compat_26_3`。不会拿本线 codec 冒充 1.21.1 验证器；不会把 `dependencies.tacz` 改成能通过检查的值。目标层已存在则跳过，来源层需要先展开或有同名新旧文件则报告冲突。
- 完整物品栈从 1.20.1 DataVersion **3465** 用当前游戏 DFU 升级到运行时 DataVersion（不是 1.21.1 的 3955）。嵌入配件栈按栈处理，部分 NBT **谓词**只变换它实际限定的字段，不加一整套栈默认值。`data/tacz/gunpack_conversion_rules.json` 是内置标签映射，`config/tacz/gunpack-conversion-rules.json` 可用同一 schema 扩充/覆盖。脚本保留而不执行，不把脚本保留当成 API 已验证。
- 报告区分 `failed`、`skipped`、`partial`、`complete`。无法证明等价的配方不转换，26.3 层写禁用标记以防加载器回退到旧 `recipes/`。缺少的可选物品 ID 记入 `missingItemIds`，不会在产物中被替换成石头。安装前仍要确认对应模组、标签、实际配方、脚本和渲染；`targetFormatValidated` 是格式检查，**`gameValidated` 始终是 false**。

`forge:tag_empty` 不会被偷换成 `not fabric:tags_populated`：26.3 后者检查标签**是否定义**，不是成员数。在确认等价适配之前将这类配方标成需人工处理。旧石切配方的顶层 `count` 移入结果栈。

### 脚本里的时间

本线枪械逻辑 API 和动画 `context` 的 `getCurrentTimestamp()` 返回**玩法毫秒数**：可在同一次游戏会话内做差，单人暂停时冻结。不要用它判断日期，也不要把它当作跨存档的计时起点。如果旧脚本确实要读 Unix 毫秒时间戳，请显式用 `getRealSystemTimestamp()`；勿将两种时钟的值相减。转换器不会自动改写 Lua。
