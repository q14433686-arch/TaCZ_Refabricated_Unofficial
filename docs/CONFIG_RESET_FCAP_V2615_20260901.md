# 配置每次重启被重置（26.1.2 独有）：根因证据链与修复 — 2026-09-01

触发：维护者实机反馈——「每次重新进入游戏，游戏配置就会被重置，1.21.11、26.2 都没有这个问题」；
此前「V烘焙/世界烘焙明明默认开却表现为关」正是本病的表象（R2 时代生成的旧 TOML 把
`false` 钉在文件里，玩家改的值永远写不回去）。

**运行期修复效果未验证（等实机）；根因证据全部来自 CI javap 探针（build-reports/compile-java.log，
三个 TEMP 轮：`7eb7a2b`/`61d98c2`/`9f73b5b`，日志本体随 ci-log 提交在仓库里）。**

---

## 0. 排除过程（先证伪「我们的代码不同」）

- 三分支的配置装配 Java **逐字相同**：`TaCZFabric#onInitialize` 的三个
  `ConfigRegistry.INSTANCE.register(...)`、Cloth 五个 `*ClothConfig` 的
  `setSaveConsumer(ConfigValue::set)`、`MenuIntegration` 组屏 ——
  `git diff HEAD refs/coord-262` / `refs/coord-1211` 均无差异。
- `PreLoadConfig`（tacz-pre.toml）自建 `CommentedFileConfig` + `autosave()`，自带落盘，不病。
- 依赖差异：FCAP 我们 `26.1.5`（= 26.1 线最新 v26.1.5-mc26.1.x，2026-06-08）、
  26.2 用 `26.2.1`、1.21.11 用 `21.11.1`；Cloth `26.1.154` / `26.2.155` / `21.11.153`。
  ⇒ 差异只剩外部依赖的 26.1 构建。

## 1. 根因（FCAP v26.1.5 内部，三轮探针拼出的完整链条）

FCAP v26.1.5 把 **NeoForge 的新配置架构**（`LoadedConfig` record + 显式保存）搬了进来，
但它的 Forge 兼容层没接桥：

| 环节 | 字节码事实（v26.1.5） |
|---|---|
| 加载 | `ConfigTracker.readConfig` 手动解析 TOML 进**内存** `SynchronizedConfig`（bulkCommentedUpdate）；老 Forge 是 `CommentedFileConfig`+autosave。随后 `new LoadedConfig(config, path, modConfig)` → `ModConfig.setConfig` → `ForgeConfigSpec.acceptConfig(childConfig)` 塞进 spec |
| 写值 | `ForgeConfigSpec$ConfigValue.set(T)`：`spec.childConfig.set(path, value)` + `cachedValue=value` 后 **return** —— 无任何落盘调用（Cloth 的保存回调改的只是内存） |
| 「保存」 | `ForgeConfigSpec.save()`：`if (childConfig instanceof FileConfig fc) fc.save();` —— 新架构下 childConfig 是 `SynchronizedConfig`，**永远不是 FileConfig ⇒ save() 恒为静默 no-op** |
| FCAP 自己的写盘 | `LoadedConfig.save()`（public）：`path != null` 时 `ConfigTracker.writeConfig(path, config)` + 锁内 `ModConfigEventsHelper.onReloading` —— **存在但无人调用**（spec 拿不到 LoadedConfig） |

⇒ 玩家在 Cloth 界面保存 → 内存变了 → 文件永远不动 → 重启读回旧文件 = 「重置」。
1.21.11/26.2 的 FCAP 构建无此断桥，故只有 26.1.2 发病。

## 2. 修复（我方闭合，不动 FCAP 内部不可达点）

`Cloth 的 ConfigBuilder.setSavingRunnable`（保存流程最后一步）→ `ConfigPersist.saveAll()`：

- **首选实现（`LoadedConfig.save()`，FCAP 官方保存路径）被编译事实否决**：
  `LoadedConfig` 类与 `ModConfig.loadedConfig` 字段都是**包私有**（CI 编译错实录），
  签名都无法从外部引用。
- **落地实现**：注册时用带文件名的 `register` 重载把文件钉死在 Forge 惯例名
  （`tacz-client.toml` / `tacz-common.toml`，= FCAP 默认命名）；新 accessor
  `client.ForgeConfigSpecAccessor`（`@Accessor("childConfig")`，声明类型是公开的
  nightconfig `Config`）取出内存配置（`set()` 已把新值写进去），`TomlWriter` 显式写回。
  注释保留（SynchronizedConfig 带注释解析）。SERVER（世界生命周期所有物，面板不编辑）不动。
- `TaCZFabric#onInitialize`：COMMON/CLIENT 注册行改为显式文件名 + `ConfigPersist.record`。

已知边界：①`setSavingRunnable` 是否在全部 entry 保存回调之后执行以 Cloth 实现为准
（ClothConfigScreen.save() → AbstractTabbedConfigScreen.save()，实机确认项）；
②若用户用 FCAP 自身配置改过默认 config 目录，我们写的是 FabricLoader 的 configDir
（= FCAP 默认目录），改过目录的极端场景需实机复核。

## 3. 与「V烘焙/世界烘焙默认开」的关系

R3 已把两键默认置 true（`MeshyConfig`/Cloth 双侧）。本 bug 修复前：R2 时代写盘的
`false` 永远盖不掉 ⇒ 表现为「默认开了却不起作用」。修复后第一次保存即把文件刷新为
当前内存值；**若历史文件里已有 `false`，需要玩家在界面里开一次（或删文件重生）**——
修复只保证「此后改动能存住」，不追溯改写用户文件。

## 4. 验收（实机前全标「未验证」）

1. Cloth 界面改任意客户端配置 → 保存退出 → 重启游戏：值保留（`config/tacz-client.toml`
   mtime 变化）。
2. COMMON 类目（弹药/枪械/其他）同上（`tacz-common.toml`）。
3. 光影下 V烘焙/世界烘焙：界面打开一次并保存后，重启仍为开。
4. 回归：配置界面无 PIP/镜内功能相关行为变化；`tacz-pre.toml` 行为不变。
