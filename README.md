# [UNOFFICIAL] TaCZ Refabricated — Minecraft 1.21.11 / Fabric

> **非官方社区移植，不是 TaCZ 官方发布，也未获 TACZ Dev Team 审核或背书。**

本分支把 [Sh1roCu/TACZ-Refabricated](https://github.com/Sh1roCu/TACZ-Refabricated)
的 Minecraft 1.21.1 Fabric 分支移植到 **Minecraft 1.21.11 Fabric**（经由本仓库的 26.1.2 分支）。
直接上游的版本号为 `0.7.0-forge1.1.8-hotfix`；本分支当前源码版本为 **`1.1.8+fabric.1.21.11.R9`**。

[下载构建](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/releases)
· [问题反馈](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/issues)
· [1.21.11 源码](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/1.21.11)
· [26.1.2 源码](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/26.1.2)
· [直接上游](https://github.com/Sh1roCu/TACZ-Refabricated/tree/1.21.1)
· [原始 TaCZ 项目](https://github.com/MCModderAnchor/TACZ)

> 仓库源码已使用 R9 版本号；实际可下载版本及其发布日期以 Releases 页面为准。

---

## 1. 支持环境

| 项目 | 1.21.11 分支要求 |
|---|---|
| Minecraft | **1.21.11** |
| 加载器 | **Fabric Loader 0.19.3+** |
| Java | **21+**（注意：26.x 分支要求 Java 25，本分支是 21） |
| Fabric API | **0.141.6+**；R9 构建使用 **0.141.6+1.21.11** |
| Forge Config API Port | **21.11.1+，硬依赖** |
| 本 mod | **`1.1.8+fabric.1.21.11.R9`** |

> 1.21.11 是**混淆**版本，构建使用 Loom 的 remap 模式（`net.fabricmc.fabric-loom-remap`）
> 与官方 Mojang 映射；26.x 分支则是非混淆的。这个差异是本分支绝大多数移植工作的来源。

这里只提供 Fabric 构建，不能与 Forge / NeoForge 版 TaCZ 或 LRTactical 混装。

---

## 2. 项目范围

本仓库包含：

- TaCZ 的 Fabric 1.21.11 端口及随上游带来的默认枪包；
- 为 1.21.11 API 改写的网络、资源加载、GUI 和渲染接线；
- 一套内置的 **LRTactical 兼容框架**；
- 若干可选模组的兼容接线。

这不代表本项目是 TaCZ 或 LRTactical 的官方版本，也不代表所有第三方枪包、
战术装备包或 shader pack 都已经兼容。

### LRTactical 的准确范围

本分支的 `fabric.mod.json` 通过 `provides: ["lrtactical"]` 提供依赖标识；源码中已接入
melee、consumable、detonator，以及 explode / sticky / smoke / stun / effect-cloud
五类 throwable 的数据加载和基础运行路径。

同时必须说明：

- 这是**部分代码与数据驱动框架**，不是原作的完整 Fabric 发行版；
- `flash_shield` 没有移植；
- 仓库没有打包原作的完整美术资源集，内容包仍需自带其获准分发的模型、贴图、动画和音效；
- `provides` 只能满足 Fabric 的依赖 ID 检查，不等于任意 LRTactical 内容包都能直接运行；
- 本移植产生的问题请反馈到本仓库，不要要求原作者为本端口提供支持。

---

## 3. 瞄具渲染：不是 PIP

**TaCZ 直接上游的瞄具不是 Picture-in-Picture，也不会为镜片再渲染一次世界。**

人工核对直接上游 1.21.1 的提交
[`d290355`](https://github.com/Sh1roCu/TACZ-Refabricated/commit/d2903554da039d2355920953a81447784a3f2be2)
中 `BedrockAttachmentModel` 与 `BedrockGunModel` 后，可以确认：上游在当前第一人称场景中
使用 stencil 值控制目镜、镜身、准星和枪体片元；镜片后看到的
仍是同一次世界渲染。它没有第二台相机，也没有第二次 `renderLevel`。

1.21.11 端口无法沿用上游的即时 stencil 调用与绘制时序（1.21.11 的 `RenderPipeline`
没有模板缓冲状态），因此沿用 26.1.2 分支引入的 **深度孔径**路径：

1. 用不可见目镜几何写入孔径深度；
2. 在镜身绘制前保存并复制所需的世界/孔径深度；
3. 枪身、相关配件、枪口火光和准星按孔径关系过滤；
4. 完成后恢复对应的世界深度。

这套流程仍只使用同一次世界渲染，**不是第二份世界画面**。代码中的
`PictureInPictureRenderer` 仅用于枪械工作台的 GUI 模型预览，与瞄具实现无关。

Iris 有专门的 HAND/depth 接线；其他 shader pack 没有因此自动获得兼容保证。

---

## 4. 安装

1. 安装 Minecraft 1.21.11、Fabric Loader 0.19.3+ 与 Java 21+；
2. 安装 Fabric API 0.141.6+ 和 Forge Config API Port 21.11.1+；
3. 从 [Releases](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/releases)
   下载明确标注为 **1.21.11 / Fabric** 的构建；
4. 把三个 mod 的 `.jar` 放入 `.minecraft/mods/`；
5. 启动游戏。第三方枪包按下一节安装。

不要只看文件名中的 `1.1.8`：还必须核对 Minecraft 版本与加载器。

---

## 5. 第三方枪包

### 加载目录

现代枪包放在 **`.minecraft/tacz/`**：

```text
.minecraft/
├── mods/
│   ├── TACZ-Refabricated-....jar
│   ├── fabric-api-....jar
│   └── forgeconfigapiport-....jar
├── tacz/
│   ├── some_pack.zip
│   └── another_pack/
└── tacz_backup/
```

zip 可以直接加载，也可以解压为目录。无论哪种形式，包根目录都必须有：

```text
gunpack.meta.json
```

最小示例：

```json
{ "namespace": "your_pack_namespace" }
```

对 zip 来说，该文件必须位于压缩包根部，不能再多套一层目录。缺少它时，
`GunPackLoader` 不会把该文件当作现代枪包加载；zip 情况会在日志中记录
`No gunpack.meta.json found`。

### 旧包转换

不要只凭“适用于 1.20”之类的游戏版本标签判断是否需要转换：部分 1.20.1 枪包已经是
现代布局，可以直接放进 `tacz/`。应以**包结构**为准。

只有旧布局包才放进 `.minecraft/tacz_backup/`，然后在客户端执行：

```text
/tacz convert
```

转换器会生成带 `gunpack.meta.json` 的输出；它不能保证自动修复所有旧资源、配方或脚本差异，
请保留原包备份并检查游戏日志。

### 版本约束

枪包可以在 `gunpack.meta.json` 的 `dependencies` 中声明版本谓词。本分支用 `1.1.8`
作为 SemVer 核心，`+fabric.1.21.11.R9` 是构建元数据，不参与 Fabric 的版本先后比较。
一个枪包最终是否通过检查，仍取决于它写下的完整谓词，不能笼统理解为“所有旧包都兼容”。

### 依赖 TacZ:Arcana 的内容

本仓库不提供 Arcana，也没有实现 Arcana 的 API 或资产保护/加载流程。
截至 **2026-08-12** 核对，[Arcana 的官方发布页](https://www.curseforge.com/minecraft/mc-mods/tacz-arcana-timeless-and-classics-guns)
提供的是 **Minecraft 1.20.1 Forge** 文件；因此明确要求 Arcana 的内容不能视为本 Fabric
26.x 端口的受支持内容。

请先查看内容包作者列出的前置依赖。紫黑贴图或模型缺失本身不能证明“这个包一定依赖 Arcana”，
也可能是目录层级、资源路径、版本谓词或包本身不完整造成的。

---

## 6. 当前已知边界

- LRTactical 是部分兼容框架，`flash_shield` 未实现；第三方包兼容性需要逐包验证。
- 1.21.11 的瞄具裁剪是 branch-specific 的深度孔径实现，不应描述成上游 PIP，也不保证每个
  shader pack 都得到完全相同的结果。
- 明确依赖 Arcana 的内容不受支持；其他枪包也不能仅凭“能被扫描到”就视为完全兼容。

提交兼容问题时请给出实际包名与版本、完整日志和最小复现环境，不要只给缺失贴图截图。

---

## 7. 许可与来源

本仓库**不只有一套资产许可，也不应把整个仓库简单概括为“两套许可”**：

- TaCZ 与本端口对应代码使用 GPL-3.0；
- 移入的 LRTactical 代码部分沿用其 GPL-3.0；
- 默认枪包的 `gunpack_info.json` 声明其资源为 CC BY-NC-ND 4.0；
- 随 jar 打包的 Mayday Animation Engine 使用 MIT；
- 其他第三方库、资源和外部内容包可能有各自许可。

详见该分支的
[`LICENSE`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/blob/1.21.11/LICENSE)
与 [`LICENSES.md`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/blob/1.21.11/LICENSES.md)。
代码许可不会自动覆盖美术资源，本仓库兼容某个第三方内容包也不代表取得、转授或改变了
该内容包的许可。

本项目按“原样”提供，不附带担保。请勿把本移植的问题提交给 TaCZ 或 LRTactical 原作者。

---

## 8. 从源码构建

需要 Java 21：

```bash
./gradlew build
```

产物位于 `build/libs/`。

若要使用分支内的 Iris/Sodium 开发运行配置：

```bash
./gradlew runClient -PwithIris
```

---

## 9. 1.21.11 移植说明

本分支从 26.1.2 分支移植而来。两者最大的差别是：**1.21.11 是混淆版本，26.x 不是**。
由此产生的坑贯穿整个移植过程，详见 `docs/`：

| 文档 | 内容 |
|---|---|
| `docs/PORT_1_21_11_PHASE1.md` | 构建文件迁移（Loom remap 模式、依赖版本、mixin 配置） |
| `docs/PORT_1_21_11_PHASE2.md` | 编译错误族、逐次启动崩溃与渲染问题的完整定位记录 |
| `docs/verify_mixin_targets.py` | 校验所有 mixin 目标与 `@Inject` 处理函数签名 |
| `docs/verify_shader_imports.py` | 校验所有自定义 shader 的 `#moj_import` 目标真实存在 |

### 两个校验脚本

编译通过 **不等于** 运行期安全。本移植过程中崩了 5 次，每一次都能编译通过：

```bash
./gradlew help --no-daemon          # 先填充 Loom 缓存（脚本依赖它反查真实签名）
python3 docs/verify_mixin_targets.py    # 102 项 / 44 个原版类
python3 docs/verify_shader_imports.py   # 16 条 #moj_import
```

`verify_mixin_targets.py` 覆盖四类检查：目标方法名（含继承）、精确描述符、
`@At(target=...)` 的成员归属、以及 `@Inject` 处理函数的参数列表。
**`lambda$xxx$N` 形式的目标会直接判错** —— 那是非混淆版本的 javac 合成名，
在 1.21.11 上必须写成 intermediary 的 `method_NNNNN`。

`verify_shader_imports.py` 用于捕获悬空的 `#moj_import`。这类问题编译期查不出
（GLSL 不过 javac），mixin 校验也查不出（不是 mixin），但会让 `ShaderManager`
抛 NPE、整个资源重载失败、客户端**黑屏**。

### 低内存环境构建注意

`./gradlew build` 会在 `remapSourcesJar` 阶段占用大量内存。内存紧张时（约 2 GB 以下）
用下面这条只产出可用 jar：

```bash
./gradlew remapJar -x sourcesJar -x remapSourcesJar
```

`remapJar` 本身也是内存峰值所在。若日志出现
`Gradle build daemon disappeared unexpectedly`，先确认 `build/libs/` 下是否已生成
完整 jar（用 `unzip -t` 校验），并删除残留的 `*.jar.tmp` 后重试。
`gradle.properties` 里的 `org.gradle.jvmargs` 是按 2 GB 沙箱调过的，
在正常开发机上可以调大。

---

## 10. 反馈

请在[本仓库 Issues](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/issues)提交：

1. 完整 `latest.log` 或崩溃报告；
2. Minecraft、Fabric Loader、Fabric API、Forge Config API Port 与本 mod 的完整版本；
3. 第三方枪包和可选模组的名称与版本；
4. 是否能在“本 mod + 两项硬依赖”的最小环境复现；
5. 使用 shader pack 时，注明 Iris/Sodium 与 shader pack 的具体版本。
