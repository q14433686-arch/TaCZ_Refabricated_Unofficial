# Fabric 1.2.0 → 26.3：差异与移植账本（2026-11-17）

**不是 release notes。** 目标是同等玩法/内容包语义，不是把 1.21.1 的 jar、mixin 或资源目录直接覆盖上来。阶段门禁未全部通过前，保留本线 1.1.8 SemVer 核心；不要放宽 `dependencies.tacz` 冒充 1.2.0。

## 固定比较对象

- 旧端：[Sh1roCu 1.21.1 `d290355`](https://github.com/Sh1roCu/TACZ-Refabricated/commit/d2903554da039d2355920953a81447784a3f2be2)，`0.7.0-forge1.1.8-hotfix`。这是 [上次同路径审计](UPSTREAM_PARITY_AND_TODO_AUDIT_2026_08_12.md)的参考树，不是说本仓逐字节等于它。
- 新端：仍是 **Fabric 1.21.1**；`f4bde52` 升 1.2.0，`271d692` 同步 hotfix，`828fbab` 修 build，固定到含 ocular 修正的 [`356bdf9`](https://github.com/Sh1roCu/TACZ-Refabricated/commit/356bdf9d48f2225bac6ceb56adc5576a418517a8)。上游 `gradle.properties` 实际标识 **`1.0.1-neo1.2.0-hotfix+mc1.21.1`**，不是名为 `1.2.0` 的 GitHub release tag。[源码比较](https://github.com/Sh1roCu/TACZ-Refabricated/compare/d290355...356bdf9)。
- 本端：`26.3@cc990c3`，MC 26.3 / JDK 25 / 无 remap / Fabric API 0.160.7。三树而非两树比较，避免把 LRTactical、弹药源 API、TML、PAL、Iris/PIP 等本地工作误判为上游删除项。

按 Git `-M50%`（rename 算一项）共 **2,658** 项；Java **+25,062 / −3,167** 行。以下只比较相同目标路径的 blob：缺失≠缺功能，尤其注意资源目录单数化与跨包重命名。

| 上游变更 | 本端已等于目标（含已删） | 仍等于旧 blob | 无目标路径 | 另有改写 | 总数 |
|---|---:|---:|---:|---:|---:|
| Java | 0 | 44 | 223 | 232 | 499 |
| 内置默认包 | 2 | 727 | 1,260 | 5 | 1,994 |
| 其它资源 / loader 元数据 | 0 | 0 | 117 | 39 | 156 |
| 工具 / 构建 / 文档 | 1 | 0 | 3 | 5 | 9 |

可复跑，不依赖 compare API 的 300 文件截断，也不必切换工作分支：

```sh
git init --bare /tmp/tacz-upstream.git
git -C /tmp/tacz-upstream.git fetch --no-tags --depth=1 https://github.com/Sh1roCu/TACZ-Refabricated.git \
  d2903554da039d2355920953a81447784a3f2be2 356bdf9d48f2225bac6ceb56adc5576a418517a8
python3 scripts/audit_upstream_120.py /tmp/tacz-upstream.git --local cc990c3
# 加 --paths 输出全量 TSV，或省略 --local 比对当前提交。输出只用于定位，不自动复制。
```

## 依赖裁决

依据为上述两棵树的 `build.gradle`、`fabric.mod.json`、`libs/` 及实际 import；`modApi`、`modLocalRuntime` 与玩家硬依赖不是一回事。

| 上游差异 / 使用点 | 本线选择 |
|---|---|
| SimpleBedrockModel **2.3.0.1 → 2.6.3**，上游 flatDir 1.21.1 mod jar，含 external/static gun mesh、坐标变换及 `AnimationClocks` | **不装旧 jar**（[SBM Fabric 分支](https://github.com/Sh1roCu/SimpleBedrockModel-Fabric/branches)为 1.20.1、1.21.1）。外显模型/时钟按实际调用在 RenderPearl / collector 及 pause 生命周期上改写；本地两个 IFP 接口不是完整 SBM。 |
| MAE **1.1.1 → 1.1.4**，上游普通 Java `implementation`，其 SBM jar 又嵌 `META-INF/jars/mae-1.1.4.jar` | 本端没有外置 SBM，沿用**一份** MIT nested MAE，不引入同包桩；验证坐标、编译及产物内版本。 |
| **EMI 1.1.24+1.21.1** API + dev runtime；JEI/REI 代码重组为 jrei，增加正反向查询与 transfer | EMI 旧 artifact 不适用，未确认 26.3 API 前不注册入口。[EMI 源码](https://github.com/emilyploszaj/emi/branches)未见 26.3 分支。保留本线 JEI 31.8 / REI 26.3、Ammo Query 和收到服务端 cache 后的刷新；查询方向与工作台 transfer 需要分别补，不重命名包来假装同步。 |
| **Sable 2.0.6** (`modApi`)，`SableCompat` 在子世界射线、附着、显示姿态走 mod 检测；**Simulated 1.3.2 common** 仅 compileOnly（上游注明等待 Fabric），用于多格装配检查 | **不作硬依赖**，无经验证的 26.3 Fabric API 组合就不上 mixin、不声明支持；维持 vanilla 世界逻辑，日后同时验证缺席加载和装船/移动语义。仓库 URL 或 common jar 不等于目标 MC 的 Fabric 构件。 |
| KosmX、SSR、Cloth、Mod Menu、加速渲染等转 compileOnly + localRuntime；CCA 仍是 1.21.1 依赖 | 保留本线 PAL（非 KosmX）、26.3 SSR、FCAP、实体数据/出生包、Iris HAND 掩码及 TML；不回灌 CCA、intermediary/refmap、旧 OpenGL/Immediate buffer 或 AR 库。Cloth/Mod Menu 不变成玩家新硬依赖。 |
| Loom 1.11 → 1.16.3，Mojmap+Parchment；假人、键位、Flashback/lattice 等 dev-only scope 整理 | 保留 1.17 / Gradle 9.5.1 / 无 mappings；dev 工具不打进成品。LuaJ 3.0.8-figura、BCEL 6.6.1、commons-math3 3.6.1 上游未升。 |

## 分批门禁（有消费方、有测试再打勾）

| 批次 | 源码范围与本端接点 | 完成判据 |
|---|---|---|
| A 包发现/同步 | `PackMeta`、MC 版本 overlay、Maven/Fabric 范围、gzip 分片；保留旧包 recipe/lang 兼容、LR 版本与 TableRecipeManager | 目录与 zip 双侧资源、重载关闭句柄、同名覆盖一致；超单包快照只安装一次、换服清分片、保留 JEI/REI 刷新 |
| B 数据和玩法 | built-in/虚拟 OEM、variant/slot adapter 的 data/index/stack/refit；modifier、Lua attachment script、卸弹与 finish-reload/fire-post 事件、时钟/暂停、AI 声响、进度 | 不把脚本下发；服务端权威检查、联网/旧存档回归；不得绕开本地弹药源或具名 hook |
| C 展示/交互 | 弹药存储块/升级、枪柜/挂板/染色、BE 持久化、菜单和网络校验、creative 筛选、查询和工作台 transfer | 26.3 ValueInput/Output、item component、loot/schema；放置→存取→掉落→重进及专服，验证 Carry On 边界 |
| D 客户端 | ADS 曲线/预设、音效对象、首/三人称动画及粒子、外显模型、测距/热成像、ocular 膨胀 | 保留原 26.3 默认渲染与可选 PIP、Iris、PAL/YSM；无光影截图或光栅模拟只证明相应路径，不能当作 Iris/GPU 矩阵 |
| E 转换器与内置包 | 1.20.1→通用包转换 + `compat_1_21_1`、平移至 26.3 对应格式；脚本/索引/模型/音效/配方一一验证 | 转换到新目录，源包不变。默认美术依原授权与署名、单独追踪来源（[许可说明](../../LICENSES.md)）；不混入旧 loader jar、不先覆盖默认枪包 |
| F 切换基线 | 上述必选行为已消费、缺省第三方项有明确边界，再升 SemVer 与元数据 | `build`、无可选 mod 的专服启动/重载、单机/联网、渲染与旧包矩阵；README/许可/manifest 和 `--strict` 一致 |
