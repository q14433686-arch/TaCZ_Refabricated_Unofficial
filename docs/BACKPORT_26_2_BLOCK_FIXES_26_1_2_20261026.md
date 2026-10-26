# 26.2 方块层问题回移植到 26.1.2（2026-10-26）

## 来源与范围

依据 `26.2(main)` 近期方块层调查，把确认与目标版本代码结构相符的修复语义适配到本分支；**不是 cherry-pick，也未复制 26.2 的版本专属 API 实现**。

## 已移植

1. **多方块工作台双掉落**：移除 `AbstractGunSmithTableBlock#playerWillDestroy` 对非根半边的手工 `popResource`。工作台掉落继续由现有战利品表负责，避免手工克隆与邻接更新触发的根半边战利品重复叠加。
2. **创造模式 B 工作台半边判据**：修正为挖 `HEAD` 时主动抑制对侧 `FOOT` 的掉落，与 `workbench_b` 战利品表仅允许 `FOOT` 产出的条件一致。
3. **雕像内容物移除**：在 `StatueBlockEntity#preRemoveSideEffects` 服务端掉出所存枪械；使用回调给定的方块状态计算朝向，避免移除时再从已变为空气的世界位置读取状态。普通交互取枪仍走原路径。
4. **标靶主人存档恢复**：`TargetBlockEntity` 改为保存/读取原版 `ResolvableProfile` codec，并兼容读取早期构建写入的 `owner_uuid` / `owner_name`。
5. **标靶皮肤按名字查询**：渲染侧改用原版 `PlayerSkinRenderCache#createLookup` 异步解析；未解析或查询失败时取缓存默认皮肤。方块标靶与标靶矿车共用解析器。

26.2 的服务器侧档案补全 API 未直接搬入：其 `services().nameToIdCache()` 属于版本特定接口。本分支不再依赖服务器同步补全，profile 名称交由客户端原版缓存异步解析。

## 静态验证

- `python3 scripts/verify_block_drops.py --strict`：通过。
- `python3 scripts/verify_block_entity_persistence.py --strict`：通过（检查 3 个方块实体）。
- 未执行 Gradle 编译：本环境没有 Java/JDK（`java` 命令不存在）。
- 未做游戏内测试。发布前仍需实测 B/C 工作台两半在生存/创造模式下的掉落、雕像挖掘/爆炸/活塞移除时的枪械掉落，以及标靶退出重进后的主人与皮肤显示。

静态检查不等于编译或实机验证，也不据此宣称上述行为已实机通过。
