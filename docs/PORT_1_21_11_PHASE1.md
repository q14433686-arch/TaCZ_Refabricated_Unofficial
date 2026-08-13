# 1.21.11 移植 · 阶段 1 执行报告（构建层）

执行日期：2026-08-13
分支：`port/1.21.11-phase1`（基于 `1.21.11`，commit `6c409ee`）
对应交接清单：`新建 文本文档 (3).txt` §四.A、§五阶段 1–2、§七 第一批工作 A/B

> 本轮**只做构建层**（清单阶段 1）。业务源码一行未改。
> 与前几轮审计不同，本轮是在**真实 JDK 21 + Loom 工具链**上跑出来的，
> 不是字节码静态推断：Loom 已成功拉取并 remap 了 1.21.11 官方映射 jar，
> `./gradlew help` 通过，`compileJava` 已产出首轮完整错误日志。

---

## 一、起始状态更正（重要）

交接清单假设移植从 `origin/26.1.2` 开始。实际情况是：

**`1.21.11` 分支目前是 26.1.2 工作树的副本，构建配置完全没有改过。**

| 项 | 分支上的实际值 | 说明 |
|---|---|---|
| `minecraft_version` | `26.1.2` | 不是 1.21.11 |
| `loader_version` | `0.19.3` | 与 1.21.11 兼容，可保留 |
| `fabric_version` | `0.155.2+26.1.2` | 26.1.2 专用 |
| Loom | `1.17-SNAPSHOT`（`net.fabricmc.fabric-loom`） | **非混淆**插件 id |
| mappings | 无 | 26.1+ 不需要 |
| mod 依赖配置 | `implementation` / `compileOnly` | 无 remap |
| mixin AP | 注释掉 | 26.1+ 无 refmap |
| `java` | `>=25` | |

也就是说：分支名已经是 1.21.11，内容仍是 26.1.2。本轮补上的正是这一层。
好处是无需从 26.1.2 重新拉分支——业务代码已经在位，直接做阶段 1 即可。

---

## 二、清单中需要修正的三处结论

### 1. Loom 版本：不是 1.14，而是 1.17.19 + `-remap` 插件 id

清单建议 `id 'net.fabricmc.fabric-loom' version '1.14-SNAPSHOT'`。这**会失败**，原因有两个：

* Loom 1.14 起插件 id 一分为二：
  * `net.fabricmc.fabric-loom` → **非混淆** Minecraft（26.1+）；
  * `net.fabricmc.fabric-loom-remap` → **混淆** Minecraft（含 1.21.11）。
  1.21.11 是混淆版本，必须用 `-remap` 后缀那个。沿用清单写法会拿到非混淆插件，
  之后 `mappings` 声明与 `remapJar` 全部失效。
* 仓库 wrapper 已经是 Gradle 9.5.1，而 Loom 1.14 面向 Gradle 9.2。
  维持 wrapper 不动、把 Loom 提到 1.17.19（当前 stable，要求 Gradle ≥9.4）更省事，
  也已实测配置通过。

实际采用：

```groovy
// settings.gradle
plugins { id 'net.fabricmc.fabric-loom-remap' version "${loom_version}" }
// build.gradle
plugins { id 'net.fabricmc.fabric-loom-remap' }
```

### 2. Access widener 头部命名空间必须从 `official` 改成 `named`

清单没有提到这一点，但它是**第一个真实构建错误**：

```
Failed to setup Minecraft, java.lang.RuntimeException:
Namespace mismatch, expected named got official
```

26.1.2 的 AW 头是 `accessWidener v2 official`，因为非混淆 Minecraft 里
official 就是最终命名空间。回到混淆的 1.21.11 后，源码侧命名空间是 `named`
（官方 Mojang 映射），AW 必须声明 `named`，否则 Loom 在处理 jar 阶段直接崩。

已改为 `accessWidener v2 named`。

### 3. Fabric API / 依赖版本（清单给的是估计值，这里是实测可解析值）

全部于 2026-08-13 对活动仓库校验，返回 200：

| 依赖 | 清单说法 | 实际锁定 | 来源 |
|---|---|---|---|
| Fabric API | `0.141.x` | **`0.141.6+1.21.11`** | Modrinth |
| Forge Config API Port | `21.11.1` ✅ | `21.11.1` | Fuzs modresources |
| Fabric Loader | — | `0.19.3`（1.21.11 支持，无需降级） | Fabric meta |
| JEI | 二阶段 | `27.23.0.71` | Modrinth |
| Cloth Config | — | `21.11.153` | shedaniel |
| ModMenu | — | `17.0.0`（26.1.2 用 18.0.0-alpha.8） | Modrinth |
| PAL | `1.1.9` ✅ | `1.1.9`（26.1.2 用 1.2.5） | Modrinth |
| Shoulder Surfing | — | `1.21.11-5.0.10+fabric`（id `9T2YSavE`） | Modrinth |
| Zoomify | — | `2.15.2+1.21.11` | Modrinth |
| REI | — | `21.11.816` | shedaniel |
| Architectury | — | `19.0.1`（26.1.2 用 20.0.12） | architectury |
| Iris / Sodium | — | `1.10.7+mc1.21.11` / `0.8.13+mc1.21.11`（curse id 7805348 / 8382544） | CurseForge |
| **Controllable** | 可延后 | **`0.25.7`（curse id 7411286）** | CurseForge |

⚠️ **Controllable 需要注意**：1.21.11 上最新的 Fabric 构建是 **0.25.7**，
而 26.1.2 分支用的是 0.26.0。这是**向下**跨版本，API 很可能不同。
`fabric.mod.json` 的 suggests 已经改成 `>=0.25.7`，但
`com/tacz/guns/compat/controllable/**` 在阶段 10 恢复时必须重新核对符号。

---

## 三、本轮实际改动

只有 4 类文件，业务源码零改动：

```
settings.gradle                          + pluginManagement.plugins 声明 loom-remap
build.gradle                             插件 id / mappings / mod* 配置 / mixin AP / Java 21
gradle.properties                        MC 1.21.11 + 全部依赖版本 + 堆参数
src/main/resources/fabric.mod.json       java>=21、fabric-api>=0.141.6、fcap>=21.11.1、suggests
src/main/resources/tacz.accesswidener    头部 official -> named
src/main/resources/*.mixins.json (5)     compatibilityLevel JAVA_17 -> JAVA_21
```

### build.gradle 关键差异

```groovy
// 1) 混淆版本 -> remap 插件
id 'net.fabricmc.fabric-loom-remap'

// 2) mappings 回归
minecraft "com.mojang:minecraft:1.21.11"
mappings loom.officialMojangMappings()

// 3) mod 依赖必须走 mod* 配置，否则运行期必 NoClassDefFound
modImplementation "net.fabricmc:fabric-loader:..."
modImplementation "net.fabricmc.fabric-api:fabric-api:..."
modCompileOnly    "maven.modrinth:jei:..."   // 等等

// 4) mixin AP / refmap 回归（26.1.2 因无映射而注释掉）
loom {
    mixin {
        useLegacyMixinAp = true
        defaultRefmapName = "tacz.refmap.json"
    }
}

// 5) Java 21，且是"钉死"而不是"不足才提升"
def targetJavaVersion = 21
java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
```

注：**不要**再手工加 `annotationProcessor` 指向 mixin/mappings。
`useLegacyMixinAp = true` 时 Loom 自行接线，手工再加会重复生成 refmap。

### mixin compatibilityLevel

已核对 `sponge-mixin 0.17.3+mixin.0.8.7`（1.21.11 / Loader 0.19.3 实际加载的版本）
的 `CompatibilityLevel` 枚举，`JAVA_21` 存在。5 个 mixin 配置全部从 `JAVA_17` 提到 `JAVA_21`。

### 堆参数

沙箱只有 2 GB 物理内存，`org.gradle.jvmargs=-Xmx2G` 会让 Loom 处理 AW jar 时
GC 抖死（实测卡住 13 分钟、RSS 打满）。已改为
`-Xmx1280m -XX:MaxMetaspaceSize=512m`。**开发机内存充足的话可以调回 2G 或更高**，
这一行是沙箱适配，不是移植要求。

---

## 四、验证结果

### ✅ `./gradlew help` — BUILD SUCCESSFUL（1m29s）

* Loom 1.17.19 启动，接受 Gradle 9.5.1；
* Minecraft 1.21.11 下载、merge、以官方 Mojang 映射 remap 完成；
* Access widener 校验通过；
* 全部 mod 依赖解析并 remap 完成（JEI / Cloth / REI / Architectury / PAL /
  Shoulder Surfing / Zoomify / ModMenu / Forge Config API Port）。

产物：`.gradle/loom-cache/.../minecraft-merged-e4eee44143-1.21.11-...jar`
（**named 命名空间**，可直接用 `javap` 逐符号核验 1.21.11 API，后续阶段应当用它）。

### ✅ AW 五个目标逐一核验（对 1.21.11 named jar）

| 目标 | 1.21.11 状态 |
|---|---|
| `LivingEntity.jumping Z` | ✅ `protected boolean jumping` |
| `MultiPlayerGameMode.ensureHasSentCarriedItem ()V` | ✅ private，存在 |
| `Minecraft.startUseItem ()V` | ✅ private，存在 |
| `rendertype/RenderType.<init>(String;RenderSetup)V` | ✅ private，描述符完全一致 |
| `Player.canCriticalAttack(Entity)Z` | ✅ **private**（26.1.2 是包级私有），AW 依然必要 |

**五个全部命中，AW 无需改动。**

### ⚠️ `./gradlew compileJava` — 首轮 292 行错误输出 / **146 个编译错误**

完整日志：`docs/port-compile-01.log`
机器可读分类：`docs/port-1.21.11-error-families.json`

清单预测「数百到上千条，归并为 8–12 个错误族」。实测 **146 个错误，11 个有效族**
（json 里 28 项是把每个缺失符号单列；按根因归并是 11 个）。比预期乐观。

| # | 错误族 | 错误数 | 涉及文件 | 根因 / 1.21.11 对应符号 |
|---|---|---:|---:|---|
| 1 | `GuiGraphicsExtractor` 不存在 | 86 | 32 | 26.x 重构产物。1.21.11 用 `net.minecraft.client.gui.GuiGraphics` |
| 2 | `CameraRenderState` 包位置变化 | 10 | 4 | `renderer.state.level.*` → **`net.minecraft.client.renderer.state.CameraRenderState`** |
| 3 | 包 `renderer.state.level` 不存在 | 9 | 9 | 同上，整包在 1.21.11 是 `renderer.state` |
| 4 | 包 `resources.model.cuboid` 不存在 | 8 | 7 | 1.21.11 无该包，模型 cuboid 类需重新定位 |
| 5 | `ItemTransforms` / `ItemTransform` | 8 | 4 | → `net.minecraft.client.renderer.block.model.ItemTransform(s)` |
| 6 | 包 `fabric.api.menu.v1` 不存在 | 2+2 | 2 | `ExtendedMenuProvider` / `ExtendedMenuType`：FAPI 0.141 无此模块，改用 `ExtendedScreenHandlerType` 路线 |
| 7 | `BlockModelRenderState` | 2 | 1 | 1.21.11 不存在，需按 vanilla 等价路径重写 |
| 8 | 包 `resources.model.sprite` 不存在 | 2 | 2 | 同族 4 |
| 9 | `PictureInPictureRenderState` / `state.gui.pip` | 2 | 2 | → **`net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState`**（包存在，路径不同） |
| 10 | FAPI 注册入口缺失 | 5 | 5 | `ServerEntityLevelChangeEvents`、`PictureInPictureRendererRegistry`、`ClientTooltipComponentCallback`、`ParticleProviderRegistry`、`keymapping.v1` —— FAPI 0.141 的模块/包名与 0.155 不同 |
| 11 | 杂项单点 | 8 | 8 | `ItemStackTemplate`（1.21.11 **确认不存在**）、`LightCoordsUtil`、`QuadParticleRenderState`（→`renderer.state`）、`ColorTargetState`/`DepthStencilState`/`CompareOp`（26.1 新 RenderPipeline 状态对象）、`FriendlyByteBufs` |

外加 3 个 mixin AP 层面的错误（不计入 146）：

* `IrisDepthRestoreShaderMixin` → `net.irisshaders.iris.pipeline.programs.ShaderCreator`
  找不到 —— 预期内，Iris 1.10.7 内部结构不同，属清单阶段 9；
* `GuiGraphicsExtractorMixin` → "Mixin has no targets" —— 族 1 的连带；
* `MinecraftAccessor` → `pausePartialTick` 在 1.21.11 `Minecraft` 中**不存在**（已用 javap 确认）。

### 已确认清单结论正确的部分

| 清单结论 | 实测 |
|---|---|
| `Identifier` 不要全仓替换 | ✅ `net/minecraft/resources/Identifier.class` 在 1.21.11 jar 中存在。288 个引用文件**零错误** |
| 保留 SubmitNodeCollector 架构 | ✅ 1.21.11 有 `SubmitNodeCollector`，相关文件未报"类不存在"，只报 render-state **包路径**变化 |
| `ItemStackTemplate` 要去掉 | ✅ 1.21.11 jar 中不存在，`PartialNBTIngredient` 已报错 |
| `Recipe.assemble` 签名要改 | ✅ 1.21.11 是 `assemble(T, HolderLookup$Provider)`，源码 `assemble(SingleRecipeInput)` 单参 |
| 枪械业务、payload、StreamCodec 不重写 | ✅ `network/**` 未出现在错误文件列表中 |

### 清单里"预计要改"但实测**没有报错**的

* `PayloadTypeRegistry.serverboundPlay/clientboundPlay` —— 清单说要改成
  `playC2S/playS2C`。实测 `NetworkHandler` 与 `IEntityAdditionalSpawnData`
  **没有任何错误**，说明 FAPI 0.141 里这两个方法名就是现在这样。**不要改。**
* 整个 `crafting/` 只有 `assemble` 一处签名问题，`RecipeSerializer` 未报接口错误。

---

## 五、下一步（阶段 2 起点）

按错误族逐个收敛，建议顺序（先收大头，一次一族，每族后重编）：

1. **族 1（86 错 / 32 文件）**：`GuiGraphicsExtractor` → `GuiGraphics`。
   多为纯改名，但 `GuiGraphicsExtractorMixin` 里用到的 `itemCooldown(ItemStack;II)V`
   在 1.21.11 是 **private `renderItemCooldown(ItemStack,int,int)`** —— 描述符和可见性都变了，
   @Inject 目标要跟着改。`fill(RenderPipeline,int,int,int,int,int)` 描述符不变，可直接用。
2. **族 2+3+9+11 的 render-state 部分**：统一的包迁移
   `renderer.state.level.X` / `state.gui.pip.X` → 已在上表给出确切新路径。
3. **族 10**：FAPI 0.141 入口对照，逐个查 0.141 的实际模块。
4. **族 4+5+7+8**：模型/cuboid/sprite，这块要看 1.21.11 的模型系统，工作量最不确定。
5. **族 6**：菜单 API 换 `ExtendedScreenHandlerType`。
6. `assemble` 签名、`ItemStackTemplate` 摘除、`pausePartialTick` accessor 处理。
7. Iris mixin 保持关闭直到阶段 9。

所有符号请用这个 jar 核验（named 命名空间，可直接 javap）：

```
~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/\
1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2/minecraft-merged-*.jar
```

重跑首轮编译：

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew compileJava --no-daemon --continue
```

---

## 六、本轮未做

* 任何业务源码改动（阶段 2 起）；
* `runClient` / `runServer` 实测（沙箱 2 GB 内存 + 无显示设备，跑不了）；
* `remapJar` 产物检查（要等 `compileJava` 先通过）；
* Iris / PAL / KubeJS / AR 等可选兼容（清单阶段 9–10）。
