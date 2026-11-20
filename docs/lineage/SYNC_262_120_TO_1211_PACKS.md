# 26.2 TaCZ 1.2.0 → Fabric 1.21.11：枪包与同步首批

供体 `26.2(main)`：`307f063`（PR #125），代码差异以 `git diff f9ae55a 307f063` 为准。接收基线 `aac343a`。本批**不升版**，继续使用 Java 21、官方映射及 Loom remap、原 bundle 和 1.21.11 依赖，不改 `fabric.mod.json`。26.2 的构建、实机与成品散列不是本线的证据。

## 实际对照与处置

| 项目 | 基线与移植决定 |
|---|---|
| 枪包发现 | 本线原来把同一个可变 `PackType` 用于所有仓库；实际 `AddPackFindersEvent` 分别从 Minecraft、世界创建界面和服务端发出。现在从事件捕获类型。`Pack` 的元数据预读会关闭 `openPrimary`，因此只在 `openFull` 打开具体目录或独立 ZIP 文件系统，关闭一个仓库不关闭另一个。 |
| 版本与层顺序 | 保留 Fabric 谓词及捆绑 LRTactical 0.3.0 的判定，增加 Maven 区间与 `overlays.entries`；后声明的匹配层覆盖先声明的层和 base，跨包用扫描路径升序。和供体不同：没有匹配 overlay 时仍拒绝明确不支持 1.21.11 的包，不把供体转换告警当作转换器。一个包中使用 `{"directory":"compat_1_21_11","minecraft":"[1.21.11]"}` 即可声明目标覆盖层。 |
| 配方与标签 | 接收方 `PathPackResources` 与供体不同：旧 `recipes/` 的原版配方迁移在接收方 `DelegatingPackResources` / `LegacyGunPackRecipeMigrator`，TaCZ 自有旧配方仍由 `TableRecipeManager` 单独扫；保留原路径，只对资源列表做优先级去重。配件标签跨层累积；筛选规则取每个包的有效层再跨包合并。空白名单经服务器序列化（`[]`）和客户端反序列化仍表示不设白名单。 |
| 网络 | 本线旧 `s2c_sync_gunpack` 仍是 `DataType` → `Identifier` → 字符串，未改枚举顺序。新通道按 Fabric `canSend` 协商；支持的客户端收 256 KiB 的 GZIP 分片，压缩及解压后分别限 64 MiB，完整解包并校验后再替换客户端数据和刷新索引。旧客户端只回退到原格式、原 `readUtf` 上限及 1.21.11 客户端原版 1 MiB 负载限制。不要在此通道加入 1.2 附件新枚举或对象格式；后续批次需要独立协商 schema。 |
| 生命周期 | 核对 Fabric 1.21.11 `PlayerList` 的 `SYNC_DATA_PACK_CONTENTS` 接线、PLAY addon 的配置阶段待注册频道，以及 1.21.11 的 `disconnect(Screen,ZZ)` / `clearClientLevel(Screen)` 两条调用路径。保留本线既有 logout 注入，使用同一事件清理尚未完成的传输及远程客户端缓存，只为当前连接安装数据；不移植供体 mixin。保留本线的 JEI 生命周期 listener，不模拟 vanilla 配方更新。 |

## 验证边界

- 回归用例在 `src/test/java/com/tacz/guns/{network/message,resource}`：目录/ZIP 同行为、独立关闭、旧配方迁移、覆盖优先级、附件标签、版本谓词、配方筛选 round-trip、分片乱序/中断/长度限制，以及与原版 TaCZ 1.21.11 旧 map 编解码双向对照。`./gradlew build` 会跑这些 JUnit 用例。
- 本地版本一致性（严格模式）、工作台基础/查看器静态脚本可独立于 Gradle 运行；容器没有 Java 或 Loom 缓存，本地不具备完整构建条件。
- **未验收（OPEN）**：实际发行 JAR 的双 JVM TCP 加入、服务端 `/reload` 替换旧项、退服/重连、旧客户端降级、单人世界重入，以及 remap 成品下的实际 JEI/REI。建议使用相同产品 JAR 和本版硬依赖，专服独占一个含独有索引的外置包，重载时删除首个索引并添加第二个。用超出原 `readUtf` 限制的单条 JSON 再执行一次，确认走分片而非压缩失败或静默降级；检查 JAR 中的 `tacz.refmap.json`，记录 JAR 本体 SHA-256。26.2 的测试构件不能替代这一步。
- **后续移植**：通用转换器及目标 DFU/注册表、1.2 扩展索引和玩法/库存、默认包来源重建与旧导出备份、查看器/输入/账号 HUD、材质/粒子/瞄具及本版 Iris 矩阵。不要把供体默认包、Java 25 构建设置或 26.2 stencil 路径直接覆盖到本线。
