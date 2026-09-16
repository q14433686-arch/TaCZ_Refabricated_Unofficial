# 26.3 R1 可选依赖 26.3 构建跟踪

> 本文件记录 26.3 R1 首发时仍未发布 26.3 正式构建、暂时沿用 26.2 坐标 compileOnly 的可选依赖。
> 上游一旦发布 26.3 构建，请按下面表格 bump 版本号，并在此时去掉
> `build.gradle` 中的 `loom.ignoreDependencyLoomVersionValidation=true`（如全部都升级了）。
>
> 本文件**不跟踪硬依赖**（Fabric API / Loader / FCAP / Cloth Config）— 它们在 R1 发布时已 pin 到 26.3 构建。
>
> 最后更新：2026-09-16

| 依赖 | 当前坐标 (gradle.properties) | 状态 (2026-09-16) | 发布后操作 |
|---|---|---|---|
| **JEI** (maven.modrinth:jei) | `31.0.0.5` (Fabric 26.3 **beta**) | beta（CurseForge 2026-09-16）| stable 发布后 bump `jei_version`；验证 Ammo Query / 工作台类别仍正常注册 |
| **Mod Menu** (maven.modrinth:modmenu) | `21.0.1-beta.1` (26.3-rc-1 beta) | beta（CurseForge 2026-09-11 仍为最新 beta） | stable 发布后 bump `modmenu_version`；注意 Mod Menu API 入口签名稳定则无代码改动 |
| **Zoomify** (maven.modrinth:zoomify) | `2.16.1+26.2` | 最新 Fabric 构建停在 2.16.1+26.2（2026-06-16），Modrinth/CurseForge 尚未出现 26.3 文件 | bump `zoomify_version`；`compat/zoomify/**` 包为 compileOnly 门面，预计无代码改动 |
| **REI** (me.shedaniel:RoughlyEnoughItems-*) | `26.2.820` | 最新 Fabric 构建为 26.2.820（2026-06-18），尚未发布 26.3 构建 | bump `rei_version`；`compat/rei/**` 需验证 `REIClientPlugin/REIPlugin` 入口 API 是否变化；必要时同步升级 Architectury 坐标 |
| **Shoulder Surfing Reloaded** (maven.modrinth:shoulder-surfing-reloaded) | `26.2-5.0.7+fabric` | 最新 Fabric 构建为 ShoulderSurfing-Fabric-26.2-5.0.11（2026-08-27），plugin API 5.x 跨小版本大概率二进制兼容 | bump `shoulder_surfing_version`；`compat/shouldersurfing/**` 需验证 plugin API 5.x 在 26.3 下未变 |
| **Player Animation Library** (zigythebird PAL, maven.modrinth:player-animation-library) | `1.2.5` | 经源码隔离层（`compat/playeranimator/pal/**`）compileOnly，PAL 作者自 26.2 起尚未发新版本 | bump `player_animation_lib`；若 PAL API 变化仅改隔离层即可 |
| **Carry On** (suggests, 非编译依赖) | `>=2.11.0` | fabric.mod.json 里仅 suggests；2.11 在 26.2 已实测通过；运行期若 26.2 jar 在 26.3 加载正常则无动作 | 若 Carry On 发 26.3 构建，改 suggests 版本号提示即可 |

## 已从 26.2 起继续排除、尚未出现 26.3 构建的 compat 模块

`build.gradle` 的 `sourceSets.main.java.exclude` 列表：

| 模块 | 上游 | 状态 |
|---|---|---|
| `compat/ar/ARCompatImpl.java`、`compat/ar/AcceleratedBeamRenderer.java`、`mixin/client/ar/**` | Accelerated Rendering (argon4w) | 无 26.3 构建；AR 门面保留（禁用态） |
| `compat/controllable/ControllableInner.java` | Controllable (mrcrayfish) | 无 26.3 构建；ControllableCompat 门面保留 |
| `compat/kubejs/**` | KubeJS (dev.latvian) | 无 26.3 构建；GunEventPoster/GunMod 改为禁用态门面 |
| `compat/playeranimator/animation/**`、`PlayerAnimatorAssetManager.java` | KosmX playerAnim（已弃用，被 PAL 替代） | 永久排除（被 PAL 路径替代） |
| `compat/optifine/**` | Optifine（非 Fabric） | 永久排除 |

**操作流程（任一依赖发布 26.3 构建时）**：

1. 改 `gradle.properties` 中对应 version 号；
2. 如该依赖的 26.3 jar 其 Maven pom 声明了正确的 MC 版本且 Loom 不再告警，无需其他改动；
3. `./gradlew clean compileJava` → 若编译错误，定位 compat 门面或 API 签名变化做修正；
4. 同步更新本文件表格和根 README §1 可选依赖表；
5. 如果**所有**被 Loom 版本校验压制的依赖都已升级到 26.3 原生构建，删除
   `build.gradle` 中 `loom.ignoreDependencyLoomVersionValidation=true` 一行。
