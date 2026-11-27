# 给旧枪包加上 Fabric 1.21.11 兼容层

**适用对象：** 正在移植 TaCZ 1.2 内容的 1.21.11 工作分支（[已验证的测试件与散列](PORT_1_2_1_21_11.md)），源码版本为 `1.2.0+fabric.1.21.11.R1`（源码版本不是 Release 公告）。模组 SemVer 核心为 `1.2.0`，转换器不会改包的 `dependencies.tacz` 来绕过版本约束。

1. 先备份存档和枪包。把已有 `gunpack.meta.json` 的目录或根元数据 ZIP 放入测试实例的 `tacz/`。
2. 用专服控制台或有权限的账号运行 `/tacz convert universal "Example Pack.zip" check`，只会在 `tacz-converted/` 写报告。`/tacz convert universal all check` 可批量检查。
3. 看 `*-universal.report.json`。`partial` 表示不能等价转换的配方会被禁用，不是“全部兼容”；`missingItemIds` 中的可选物品仍需要对应模组。确认后运行 `/tacz convert universal "Example Pack.zip" export`。已有的导出 ZIP 不覆盖，原目录或 ZIP 不动。
4. 单独安装导出的 `*-universal.zip` 到干净测试实例，不要与源包同时加载。核对目标版原版配方书、实际工作台、战利品、脚本及资源；只要含客户端资源，就还要在客户端安装相应资源。服务端同步数据索引不等于传输了模型或贴图。

导出层是 `compat_1_21_11`，目标 Minecraft 谓词是 `[1.21.11]`。完整物品栈走本版 DFU（**3465 → 4671**）；由旧 `tag` 限定的部分匹配仍是谓词，不平白给谓词加上整栈默认值。配方、进度和战利品使用本版单数目录。无法证明等价的 `forge:tag_empty` 不译成“标签未定义”；目标禁用条件用本版实际注册的 `fabric:not` 包住 `fabric:true`。`targetFormatValidated` 不等于运行过该包，`gameValidated` 不是转换器能代填的结果。

内置默认包升级与这个命令是两件事。默认包需要替换时，旧目录先备份到 `tacz_backup/tacz_default_gun/backup-*.zip`；来源不变的普通重载不该抹掉后续编辑。自制内容仍建议放到单独命名的包并自行保留备份。

逻辑与动画脚本的 `getCurrentTimestamp()` 现在是单次会话的玩法毫秒，暂停单人游戏时不前进。要读取 Unix 毫秒日期请使用 `getRealSystemTimestamp()`；不要把两者相减或把玩法时钟存成跨会话日期。转换命令保留而不执行、也不自动重写脚本。
