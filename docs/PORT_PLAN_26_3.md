# TaCZ Refabricated 26.2 → 26.3 适配计划

> 撰写日期：2026-09-16
> 目标分支：`arena/01a0ab10-tacz-refabricated-unofficial`（基于 `ece5a4a`，26.2 基线 `1.1.8+fabric.26.2.R3-hotfix2`）
> 上游参考：[Fabric for Minecraft 26.3](https://fabricmc.net/2026/09/15/263.html)（2026-09-15 发布）、[Misode 技术变更表](https://misode.github.io/changelog/)
> 依据文档：本仓 `AGENTS.md` §1/§3/§4/§6、`docs/PORTING_NOTES.md`、`docs/publish/RELEASE_CHECKLIST.md`

---

## 0. 结论先行（TL;DR）

**26.3 相对 26.2 是一个「小幅度 drop」**：
- Fabric API 移除了 5 个旧 Registry（Composting/Fuel/PotionBrewing/Strippable/Tillable/Flattenable），但 TaCZ 源码 **没有使用任何一个**（已全局 grep 确认）。
- Fluid API 有一个 rename（`enableColoredVanillaFluidNames` → `getColoredName`），本仓未使用。
- Vanilla 破坏性变更集中在 *worldgen / number-provider / 战利品 / swing_animation / block transformers*，TaCZ **业务代码基本不触碰**这些点；
- Mixin / 渲染 / 网络 / 资源加载 这几块在 26.3 没有已知会直接打断 TaCZ 的改名或删方法（详见 §4 风险矩阵）。
- 真正的工作量集中在 **①构建脚本与依赖版本刷新** ②**fabric.mod.json / README / pack format 数字同步** ③**26.2 → 26.3 期间依赖 mod 的可用性扫描**（JEI / Mod Menu / REI / Zoomify / Shoulder Surfing / PAL 等） ④**实际编译 + 运行期 smoke**。
- Loom 1.17-SNAPSHOT 官方明确覆盖 26.3，**不需要升级 Loom**，只要 Gradle wrapper 推到 9.6.0。

**预计工作量**：构建/依赖/文档 1~2 小时；代码修复 0~若干个编译错误（乐观 0、保守 <10 个）；运行期冒烟+README 收尾约 1 小时。整体可在一个会话内完成。

---

## 1. 版本基线对照表

| 项 | 26.2 现状（`gradle.properties`） | 26.3 目标 | 来源 |
|---|---|---|---|
| Minecraft | `26.2` | **`26.3`** | fabricmc.net 263 公告 |
| Fabric Loader | `0.19.3` | **`0.19.5`** | fabricmc.net 公告 + mcreference loader 表 |
| Fabric API | `0.155.2+26.2` | **`0.160.6+26.3`** | CurseForge 文件页（2026-09-16 release） |
| Loom | `1.17-SNAPSHOT` | **保持 `1.17-SNAPSHOT`** | 公告："use Loom 1.17"，无需 bump |
| Gradle wrapper | `9.5.1` | **`9.6.0`** | fabricmc.net 公告 |
| Java | `25` | **保持 `25`**（26.x 全线 JDK 25） | fabricmc.net develop 页 |
| Forge Config API Port | `26.2.1` | **`26.3.0`** | Fuzss 仓库 README（26.3.x 分支 primary，maven `fuzs.forgeconfigapiport:forgeconfigapiport-fabric:26.3.0`） |
| Cloth Config | `26.2.155` | **`26.3.158`** | CurseForge（[Fabric 26.3] v26.3.158，2026-09-15） |
| JEI | `30.13.0.86` | **`31.0.0.5`**（Fabric 26.3 beta，2026-09-16） | CurseForge JEI 文件页 |
| Mod Menu | `20.0.1` | **`21.0.1-beta.1`（beta）** → 等 26.3 stable 发布时再升 | CurseForge（v21.0.1-beta.1 for 26.3-rc-1，2026-09-11 仍为最新）；本仓 compileOnly，允许用 beta 先编译 |
| Zoomify | `2.16.1+26.2` | **暂保持 `2.16.1+26.2`**；26.3 build 出现后再 bump | 截至 2026-09-16 Modrinth/CurseForge 无 26.3 构建；compileOnly 不阻碍编译 |
| REI | `26.2.820` | **暂保持 `26.2.820`**；26.3 build 出现后再 bump | 截至 2026-09-16 最新为 26.2.820（2026-06-16）；compileOnly 不阻碍编译；Architectury version 随之保持 `13.0.11`（legacy 占桩） |
| Shoulder Surfing | `26.2-5.0.7+fabric` | **暂保持 `26.2-5.0.7+fabric`**（compileOnly，transitive=false） | CurseForge 最新 Fabric 文件为 `ShoulderSurfing-Fabric-26.2-5.0.11`（2026-08-27），跨小版本通常二进制兼容；若有 26.3 构建再升 |
| Player Animation Lib（zigythebird PAL） | `1.2.5` | **保持 `1.2.5`**；编译隔离层，如 PAL 未更新不影响主仓 | compileOnly |

> **版本号管理纪律**（`AGENTS.md` §1、`gradle.properties` 头部注释）：
> - SemVer 核心 `1.1.8` **绝不动**；
> - 发布身份放在 build metadata（`+` 后）；本次升版拟为 `1.1.8+fabric.26.3.R1`
>   （作为 26.3 线第一版，重开 R 序号；hotfix 序号直接跟在 hotfix 后，**不加分隔符**）。
> - 所有对 `mod_version` 的改动必须同 commit 同步 README 6 处（见 §3 检查表）。

---

## 2. 分阶段执行计划

### 阶段 A：构建脚本与依赖刷新（预计 30 分钟）

A1. **`gradle.properties` 修改**：
- `minecraft_version=26.3`
- `loader_version=0.19.5`
- `fabric_version=0.160.6+26.3`
- `mod_version=1.1.8+fabric.26.3.R1`
- `cloth_config_fabric=26.3.158`
- `jei_version=31.0.0.5`
- `modmenu_version=21.0.1-beta.1`
- 其它（Zoomify/REI/ShoulderSurfing/PAL/Architectury）按上表「暂保持」项不动；加注释标注「待上游 26.3 构建发布后更新」。

A2. **`build.gradle` 修改**：
- Forge Config API Port 坐标从 `26.2.1` → `26.3.0`。
- 注释更新：把「以下依赖经 26.2 可用性核查后保留禁用」改成「经 26.3 可用性核查」；AR/Controllable/KubeJS/Optifine 等排除块的注释中「26.2」改成「26.3」（上游仍无构建的继续排除）。
- `loom.runs.client` vmArg `-Xmx768m` 保持不变（枪包资源体积没变化）。
- `def targetJavaVersion = 25` 不动。

A3. **Gradle wrapper 升级**：
```bash
./gradlew wrapper --gradle-version=9.6.0
```
确认 `gradle/wrapper/gradle-wrapper.properties` 的 distributionUrl 指向 `gradle-9.6.0-bin.zip`。

A4. **`fabric.mod.json` 修改**：
- `name` 从 `"[UNOFFICIAL] TaCZ Refabricated (26.2 R3-hotfix2)"` → `"[UNOFFICIAL] TaCZ Refabricated (26.3 R1)"`。
- `description` 整段重写：首段改为 R1-for-26.3 的变更说明（升版本身、依赖 bump 说明）；末尾提到的版本号统一。
- `depends` 块：
  - `"fabricloader": ">=0.19.5"`
  - `"minecraft": "~26.3"`（**用 `~26.3` 覆盖全部 26.3.x**；参照 ladder-sense 1.3.0 PR 的做法 [PR#2](https://github.com/atperry7/ladder-sense/pull/2)）
  - `"forgeconfigapiport": ">=26.3.0"`
  - `"java": ">=25"`、`"fabric-api": "*"` 不动。
- `suggests` 保留 carryon。

A5. **pack 格式数字**：本仓未显式声明 `pack.mcmeta` 顶层（default pack 由 Loom 自动生成？需确认）。如存在 `src/main/resources/pack.mcmeta` 或枪包 data 目录的 pack.mcmeta，按 misode 表 bump：
  - Resource pack：26.2 → 26.3 为 96 → **97**（26.3-pre-1 的 97.1）；
  - Data pack：26.2 → 26.3 从 115/116 系列 → **121**（26.3-pre-3 121.0）。
  - 执行后用 `grep -rn "pack_format\|pack_format" src/main/resources/` 扫一遍，有则改。

---

### 阶段 B：代码适配（预计 0.5~2 小时，实际量以编译器输出为准）

B1. **首次完整构建**：
```bash
./gradlew clean compileJava 2>&1 | tee build-reports/compile-java-26.3.log
```
按输出逐个修复。预期的**高风险点**（按概率降序）：

| 风险点 | 触发概率 | 处理方式 |
|---|---|---|
| Fabric API 移除的 Registry（Composting/Fuel/Strippable/Tillable/Flattenable/PotionBrewing）| 极低（全局 grep 无命中）| 若触发则换用新 API：Fuel/Compostable→`DataComponents.*`+`DefaultItemComponentEvents`；Strippable/Tillable/Flattenable→`BlockTransformerHelper`；PotionBrewing→data-driven recipes |
| `FluidVariantAttributes.enableColoredVanillaFluidNames` 被移除 | 极低（无命中）| 若命中则改用 `FluidVariantAttributes.getColoredName` |
| `DataComponents.SWING_ANIMATION` 被移除 → `ATTACK_ANIMATION`/`INTERACT_ANIMATION` | 无命中 | 若命中则替换 |
| `DataComponents.MAP_COLOR` 被移除 | 无命中 | 若命中则删除相关逻辑 |
| `number_provider` 拆分为 `context_int_provider`/`context_float_provider` | 无命中（TaCZ 不自定义 number provider）| 若 loot 相关 code 引用则按新 API 改 |
| 方法签名在 PlayerRenderState 加 `FabricRenderState`（26.3-pre-1）| 低（需查 `PlayerRenderer`/`LivingEntityRenderer` 的 mixin）| 检查 `compat/playeranimator/**` 与 LRTactical 第三人称注入点 |
| CreativeModeTabs.CACHED_PARAMETERS nullable（0.160.0+26.3 已修）| 低（0.160.6 已含修复）| 若 TaCZ 直接读该字段则加 null-check |
| Mixin 目标因方法重命名/签名变化失效 | 低（26.3 对 Clientbound 网络/Entity RenderState 小改）| `./gradlew runClient` 看 mixin apply 日志；失败时把失败 mixin 列出来逐一修 |

B2. **Mixin/Access Widener 校验**：
- 构建通过后，用 `./gradlew runClient`（加 `-Xmx768m`）启动到主菜单，搜日志关键字：
  - `Mixin apply for mixin ... failed`
  - `InjectionError`、`InvalidInjectionException`
  - `NoSuchMethodError`、`NoSuchFieldError`（最危险：编译通过但运行炸）
- 同步跑 `./gradlew runServer` 到 `Done!`，确认无服务端 mixin 崩溃。

B3. **26.3 新 API 主动适配（非必须但建议）**：
- 若 TaCZ 任何地方手写了「斧剥树皮/铲平地/锄头锄地」的识别逻辑，统一迁移到 `BlockTransformerHelper`（但本仓作为枪械 mod 基本不涉及，先不做）。
- Tooltip 扩展：`TooltipFlag.shouldDisplayAllInformation` — 若 Ammo Query 在 JEI/REI 内有 tooltip 被 Shift 隐藏，可后续 R1.x 补丁里适配；首版不阻塞。

B4. **被排除源码复核**（`build.gradle` sourceSets exclude 块）：
- AR/Controllable/KubeJS/Optifine/KosmX animation 旧包/AR mixin：这些排除是因「目标 mod 无 26.2 构建」。去 Modrinth/CurseForge 再核查一遍 26.3 是否有了构建；若某个已经有 26.3 正式版，则解除对应 exclude、回补 compat 代码。
  - **优先查**：KubeJS（dev.latvian）、Controllable（mrcrayfish）、Accelerated Rendering（argon4w）。
  - Optifine（非 Fabric）继续永久排除。

---

### 阶段 C：配套 mod 26.3 版本追踪（轻量并行）

为每个 compileOnly/runtimeOnly 可选依赖查 Modrinth/CurseForge 最新 26.3 构建，把结果记入 `docs/pending_dependency_updates.md`（新建）：

| 依赖 | 9月16日现状 | 处理 |
|---|---|---|
| Cloth Config | ✅ 26.3.158 已发布（9月15日） | 直接 bump（A1） |
| Forge Config API Port | ✅ 26.3.0 maven 可用 | 直接 bump（A2） |
| JEI | ⚠️ 31.0.0.5（Fabric 26.3 **beta**，9月16日） | 用 beta 先编译，等 release 再跟进 |
| Mod Menu | ⚠️ v21.0.1-beta.1（for 26.3-rc-1） | beta 编进来；release 出来再升 |
| Zoomify | ❌ 最新仅 2.16.1+26.2（6月16日） | 暂留 + 等更新 |
| REI | ❌ 最新 26.2.820（6月18日） | 暂留 + 等更新 |
| Shoulder Surfing | ❌ 最新 Fabric 26.2-5.0.11（8月27日） | 暂留；跨 26.2/26.3 预计兼容 |
| PAL（player-animation-library） | 需在阶段 B 编译时若报 class not found 再查 | compileOnly 隔离层，通常不阻塞 |
| Carry On | suggest 只提示版本，不编译依赖 | 运行时若 26.2 版在 26.3 正常加载就不改 |

> compileOnly 依赖只要方法签名没被我们引用的部分变掉，就不会引发编译错误；运行时如果老 jar 在 26.3 上加载失败，会由 Fabric Loader 标记为 incompatible 而非崩主 mod。因此这些 **不是首发 blocker**。

---

### 阶段 D：文档同步（`AGENTS.md` §1 硬约束）

D1. **README.md 同步 6 处**（§1 强制规则）：
1. 顶部版本句：`1.1.8+fabric.26.3.R1`
2. 「> 仓库源码已使用 **R1** 版本号」提示行
3. §1 支持环境表：Minecraft 26.3、Fabric Loader 0.19.5+、Fabric API 0.160.6+26.3、Forge Config API Port 26.3.0+、Java 25+、本 mod 行 `1.1.8+fabric.26.3.R1`
4. 依赖版本表里 R3-hotfix2 → R1 的版本与说明（JEI/Cloth Config 等）
5. 「选择你的 Minecraft 版本」导航表：加一行 **26.3** → `arena/01a0ab10-tacz-refabricated-unofficial` 分支链接（或本仓新开 26.3 分支的链接；当前工作分支名见 §6 分支策略）
6. SemVer 说明段示例换成 `+fabric.26.3.R1`

D2. **CHANGELOG 新建** `docs/CHANGELOG_26_3_R1.md`：
- 首行必须是环境行（MC 26.3 + Loader 0.19.5 + Java 25 + Fabric API 0.160.6+26.3 + FCAP 26.3.0）。
- 内容分三块：
  1. 版本基线 bump；
  2. 若阶段 B 有代码修复则逐条列（注明「编译修复」或「实机 PASS」）；
  3. 「待上游 26.3 构建后跟进」的可选依赖列表（Zoomify/REI/ShoulderSurfing 等）。
- 严格按 `AGENTS.md` §2：**禁用 ≠ 修复**，没实测就标「未实测」。

D3. **一致性自检**（`AGENTS.md` §6）：
```bash
bash scripts/check_release_consistency.sh --links
```
通过后才进入发布；分支在不一致状态下不允许声明"完成"。
（注意该脚本 `BRANCHES` 默认是 `26.2(main),26.1.2,1.21.11`，本次只在工作分支做，不跑 `--all --strict`，避免检查未发布到远端的分支误报。）

D4. **`docs/README_26_1_2.md`**：按 `AGENTS.md` §3 规则，本次只动 26.3 线，不影响 26.1.2 文档（它是 26.1.2 分支根 README 替换版，26.2 主线改它只在跨分支复制时需要；本分支保留现状即可，除非它的章节被复制到 26.3 README）。

---

### 阶段 E：冒烟测试（预计 30 分钟）

E1. **服务端**：
```bash
./gradlew runServer -Dorg.gradle.jvmargs="-Xmx384m"
# 等到出现 "Done!"，C-c 退出，grep 日志：ERROR/WARN 中与 tacz 相关的行
```
确认无 ClassNotFound/NoSuchMethod/Mixin apply failed。

E2. **客户端**：
```bash
./gradlew runClient -Dorg.gradle.jvmargs="-Xmx768m"
```
- 进主菜单
- 创建/进入一个超平坦世界
- 默认枪包加载完成（日志找 "Gun pack ... loaded" 之类行）
- `/give` 拿一把默认枪（如 `tacz:glock_17`）
- 开一枪、开一次镜（验证 §4 PIP 掩码路径没回归）
- 退世界、进枪械工作台 GUI（验证 JEI/REI compat 不崩）

E3. **构建产物**：
```bash
./gradlew clean build
```
确认 `build/libs/TACZ-Refabricated-26.3-*.jar` 生成，大小与 26.2 构建相近（~10 MB 量级）。

---

## 3. 风险矩阵与回滚

| 风险 | 触发条件 | 缓解 | 回滚 |
|---|---|---|---|
| 编译错误 > 30 处，暗示更深层 API 重构 | 极罕见；26.2→26.3 官方未列这种级别改动 | 先读 26.3 反编译 jar 中相关类，对照 fabric-mc.net 清单 | `git reset --hard ece5a4a` 回到 26.2 基线 |
| JEI/Mod Menu beta 导致 recipe viewer 集成编译不过 | beta API 可能变动 | 临时降级为 compileOnly 注释，保持 JEI/REI 入口 stub 以门面模式禁用 | 发布前等 stable 出 |
| 运行时 mixin 崩（尤其 PlayerRenderState/EntityRenderState） | 26.3 在渲染态新增字段 | 定位具体 mixin，改注入点；必要时临时 disable 单个 mixin | 单一 mixin 回退到 26.2 写法 |
| 资源包格式没 bump 导致枪包加载 WARN | 未检查的 pack.mcmeta | `grep -rn pack_format src/main/resources/` | 补一行版本号即可 |
| forge-config-api-port 26.3.0 maven 坐标不可达 | Fuzss maven 同步延迟 | 先试 26.3.0 正式；不行就先回 26.2.1 并标记 fabric.mod.json depends 为 `>=26.2.1`（向后兼容） | 不升 FCAP，等 maven 同步 |
| README 六处漏改 | 手工操作 | 跑一致性脚本 | 脚本会指出位置，补改即可 |

---

## 4. 提交策略（遵守 `AGENTS.md` 分支纪律）

- 所有提交在 `arena/01a0ab10-tacz-refabricated-unofficial` 上完成（系统强制分支）。
- 建议拆 3~4 个 commit，便于二分回滚：
  1. `build: bump versions to 26.3 (gradle.properties/build.gradle/wrapper/fabric.mod.json)`
  2. `fix: 26.3 API adaptations (per-compiler errors)`（若无代码改动则跳过）
  3. `docs: sync README/CHANGELOG for 26.3 R1`
  4. `chore: run smoke + record pending dep updates`（若有）
- **不** 推到 `26.2(main)`；不跨分支复制未适配的文件。
- 本次不打 tag、不做 CurseForge/Modrinth 发布（需由仓库所有者在网页端操作，见 AGENTS.md §1），仅留源码在工作分支可发布状态。

---

## 5. 验收 Checklist（执行完逐条打勾）

- [ ] `gradle.properties` 版本与 §1 表格一致；SemVer 核心仍是 `1.1.8`
- [ ] `build.gradle` FCAP 坐标 26.3.0；注释版本号全 26.3
- [ ] Gradle wrapper 9.6.0
- [ ] `fabric.mod.json` depends 块：loader>=0.19.5, minecraft ~26.3, fcap >=26.3.0；name/description 正确
- [ ] `./gradlew clean build` 通过，jar 产出
- [ ] `./gradlew runServer` 到 Done!，无 tacz 相关 ERROR
- [ ] `./gradlew runClient` 进世界 → 开枪 → 开镜 全部正常
- [ ] README 6 处同步完毕
- [ ] `docs/CHANGELOG_26_3_R1.md` 存在，首行是环境行
- [ ] `bash scripts/check_release_consistency.sh` 不报版本不一致
- [ ] 未声明任何未实测的 "fixed"（遵守 AGENTS.md §2）
- [ ] 新建 `docs/pending_dependency_updates.md` 列出等上游 26.3 构建的可选依赖

---

## 6. 待用户确认项

以下 3 点在动手前需要你拍板，避免做了你不想要的方向：

1. **版本号**：本次从 26.2 R3-hotfix2 跳到 26.3，是否新开 R 序列、叫 `1.1.8+fabric.26.3.R1`？（计划默认这样；如果你想续 hotfix 叫 `1.1.8+fabric.26.3.R3-hotfix3` 也可，但按 SemVer "新大版本新 R"更清晰）。
2. **可选依赖 beta 策略**：JEI 31.0.0.5 和 Mod Menu 21.0.1-beta.1 目前是 beta。是否接受用 beta 编译（运行期用户自然也需装 beta），还是首发先禁用 JEI/ModMenu 入口、等 stable？（计划默认：用 beta 编，README/CHANGELOG 里标注 beta。）
3. **Zoomify/REI/Shoulder Surfing 等暂无 26.3 构建的**：compileOnly 依赖保持 26.2 坐标不动（会在 26.3 环境里触发 Loom 的 "dependency minecraft version mismatch" 提示，但 compileOnly 不打进 jar、运行期也不强制）。是否接受？或者加 `loom.ignoreDependencyLoomVersionValidation=true` 抑制警告？（计划默认：加 suppress 属性，CHANGELOG 注明待上游更新。）

确认后我按阶段 A → E 顺序执行，每个阶段结束给你简短状态同步。
