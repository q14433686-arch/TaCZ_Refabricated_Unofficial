# TACZ 26.1.2 移植版（Timeless & Classics Zero — Minecraft 26.1.2 / Fabric）

> **这是一个非官方的社区移植版本，不是 TACZ 官方发布。**

把 **Timeless & Classics Guns: Zero**（枪械 mod）从 Minecraft 1.21.1 Fabric
移植到 **Minecraft 26.1.2 Fabric**。

---

## 1. 这是什么 / 不是什么

| | 说明 |
|---|---|
| **是** | 上游 [TACZ-Refabricated](https://github.com/Sh1roCu/TACZ-Refabricated)（1.21.1 分支）的 26.1.2 移植 |
| **是** | **Alpha 1** 测试构建：已可游玩、可公开测试，但仍不保证无 bug |
| **不是** | TACZ 官方版本，也不隶属于 TACZ Dev Team |
| **不是** | 内容扩展 —— 除移植必需的改动外，不新增枪械/玩法 |

**上游项目**
- 原版 mod：Timeless & Classics Guns: Zero（TACZ Dev Team）
- 直接移植来源：`Sh1roCu/TACZ-Refabricated` 的 `1.21.1` 分支

### 内置附属：LRTactical（部分代码移植）

本测试构建还**移植并内置了 LesRaisins Tactical Equipements / LRTactical 的部分 GPL-3.0 代码**，
用于让依赖 `lrtactical` 的战术装备内容包在 26.1.2 Fabric 下有运行框架。

- 目前内置的是代码与数据驱动框架，不是完整原作资源包；
- 原作美术资源（贴图、模型、音效）标注为 All Rights Reserved，本仓库不打包、不再分发；
- Fabric 元数据通过 `provides: ["lrtactical"]` 声明本构建提供该附属接口，方便内容包依赖检查通过；
- 请不要把 LRTactical 相关问题反馈给原作者，本构建是非官方移植与整合。

---

## 2. Alpha 1 状态与已知限制

Alpha 1 以 26.1.2 可游玩为基线，重点补齐 vanilla/Iris 两条第一人称调度路径下的瞄准镜兼容，
并继续完善 LRTactical 基础闭环。它仍然是测试构建，不保证与上游完全等价。

Alpha 1 主要新增 / 修复：

- **中高倍镜镜内裁剪**：不再在 `CustomGeometryRenderer` 的顶点提交阶段直接改 GL 状态；
  改为独立 mask/body/reticle 批次，并在 `GlCommandEncoder` 真正发出 draw 前配置 stencil。
  Iris 下通过公开的 `assignPipeline(..., HAND)` API 把自定义掩码管线归入手部 pass，不再修改 Iris shader 源码。
- **发光准星修复为真正自发光**：`*_illuminated` 准星改用专用 emissive/no-cardinal-lighting
  渲染类型，避免随玩家朝向在 vanilla/Iris 下反向变亮变暗。
- **LRTactical 继续作为内置兼容层**：throwable、melee、detonator/C4、consumable 基础流程保留；
  Fabric 元数据继续通过 `provides: ["lrtactical"]` 提供依赖标识。
- **曳光弹显示做了阶段性修复**：恢复上游 `energySwirl` 渲染类型、满亮 block light，并让
  第一人称枪口视觉偏移按每发子弹缓存。弹道/命中本身未改。

当前已知重点限制：

- **曳光弹仍可能存在视觉偏移差异**：目前观察到弹道/最终交汇点基本正确，问题集中在挂在
  子弹实体上的曳光几何显示；它不影响命中判定，后续仍需继续细查。
- **PIP / 二次世界渲染不默认启用**：此前验证显示 26.1.2 地形渲染、RenderTarget 与光影 pass
  耦合很深，已暂停作为主线方案。
- **LRTactical 仍是部分内置移植**：flash shield 等上游模块仍未完整移植。
- **需要 TacZ:Arcana 解密能力的加密枪包不支持**，见下文专节。

---

## 3. 免责声明

1. **本项目按「原样」提供，不附带任何形式的担保。** 使用本 mod 造成的任何后果
   （包括但不限于存档损坏、崩溃、数据丢失、与其他 mod 冲突）由使用者自行承担。
2. **本项目与 TACZ 官方团队无隶属关系**，未经其审阅或背书。请勿因本移植版的问题
   去打扰上游作者；上游没有义务为本移植版提供支持。
3. **请勿将本移植版的问题报告到上游仓库。** 若确认是上游本身的逻辑问题，
   请先在本仓库确认，再考虑向上游反馈。
4. 本项目**不用于商业用途**。默认枪包资源采用 `CC BY-NC-ND 4.0` 许可
   （署名 - 非商业性使用 - 禁止演绎），这本身即排除商业分发。
5. 移植过程中对 26.1.2 的延迟 Feature Rendering 与 Iris HAND pass 做了适配，
   **部分视觉效果与上游存在可感知差异**，属已知取舍，详见
   `docs/RENDER_PIPELINE_SCOPE_AUDIT_26_1_2.md`。

---

## 4. 许可

本仓库涉及**两套互相独立**的许可，请分别遵守：

| 范围 | 许可 | 含义 |
|---|---|---|
| **代码**（`src/main/java/**`） | **GPL-3.0**（继承自上游） | 修改与再分发必须同样以 GPL-3.0 开源 |
| **默认枪包资源**（`src/main/resources/assets/tacz/custom/tacz_default_gun/**`） | **CC BY-NC-ND 4.0** | 署名、禁止商用、**禁止演绎**（不得修改后再分发） |

> ⚠️ 两者不可混同。代码是 copyleft（GPL），要求衍生作品开源；
> 枪包资源是 ND（禁止演绎），**不允许**修改后再分发。
> 如需自制枪包，请另建独立枪包，不要在默认枪包上改。

许可信息来源：上游仓库根目录 `LICENSE`（GNU GPL v3）与默认枪包
`gunpack_info.json` 中的 `"license": "CC BY-NC-ND 4.0"` 字段。

---

## 5. 环境要求

| 项目 | 版本 |
|---|---|
| Minecraft | 26.1.2 |
| Java / 构建 JDK | 25 |
| 加载器 | Fabric Loader 0.19.3+ |
| Fabric API | 0.155.2+26.1.2 |
| 可选光影验证 | Iris 1.11.2 + Sodium 0.9.1 |

构建：

```bash
./gradlew clean build
# 可选：把当前 26.1.2 Iris/Sodium 组合加入开发运行环境
./gradlew runClient -PwithIris
```

产物在 `build/libs/`。

---

## 6. 怎么装枪包（**最常见的坑**）

**枪包目录是 `.minecraft/tacz/`**，不是 `tacz_backup`。

```
.minecraft/
├── tacz/                     ← 枪包放这里（游戏首次启动会自动创建）
│   ├── some_pack.zip         ← zip 直接放，不用解压
│   └── another_pack/         ← 或解压成文件夹，两种都支持
└── tacz_backup/              ← 这是【旧版枪包转换器】的输入目录，不是加载目录
```

一个目录/压缩包要被识别为枪包，**根目录下必须有 `gunpack.meta.json`**，
最小内容为：

```json
{ "namespace": "your_pack_namespace" }
```

**没有这个文件就会被静默跳过**（日志里会打印
`No gunpack.meta.json found` 或直接不计入 `Found N possible gunpack(s)`）。

### 旧版本枪包（1.16 / 1.18 / 1.20 时代）

那些包的目录结构与现在不同，**不能直接用**，需要先转换：

1. 把旧枪包的 `.zip` 放进 `.minecraft/tacz_backup/`
2. 进游戏执行转换指令（见 `PackConvertor`，指令会把结果输出到 `tacz/`）
3. 转换后仍可能需要按枪包格式人工调整；转换前请保留原包备份

**排错顺序**（枪包没生效时按此检查）：

1. 放对目录了吗？必须是 `tacz/`，不是 `tacz_backup/`
2. 根目录有 `gunpack.meta.json` 吗？
3. 压缩包**层级**对吗？`gunpack.meta.json` 必须在 zip 的**根**，
   不能是 `zip/包名/gunpack.meta.json` 这种多套了一层的结构
4. 看日志里有没有 `Mod version mismatch`

> ℹ️ **关于 `Mod version mismatch`**：枪包 `gunpack.meta.json` 里的
> `"dependencies": { "tacz": ">=1.0.4" }` 这类约束，会与本模组的版本号比对。
>
> 本移植版的版本号是 **`1.1.8+fabric.26.1.2.alpha.N`** —— 前面的 `1.1.8` 是所基于的
> 上游版本（Forge 的 `1.1.8-hotfix`），`+` 之后是 SemVer 的**构建元数据**。
> 按 SemVer 规则，构建元数据**不参与版本比较**，因此它等价于 `1.1.8`，
> 面向 `1.1.8` 及更早版本的枪包都能正常通过校验。
>
> （早期 26.2 移植阶段的版本号曾是 `0.0.0-26.2-audit`，那会导致几乎所有带 `dependencies`
> 的枪包被静默拒绝。若你用的是旧构建，升级即可，不必再手动删 `dependencies`。）

### ⛔ 不受支持：依赖 **TacZ:Arcana** 的加密枪包

有一类枪包**本移植版无法加载**，症状是**紫黑块 + 模型不显示**（但枪械条目、
名称、配方都在）。典型例子：*Tacz from Tarkov*、部分标注「需要 Arcana」的包。

**怎么判断**：解压后看包内结构 ——

- 有 `recursion/taczpack.dat`（通常十几 MB，占整包绝大部分体积）
- 或有 `data/<ns>/expansions/taczexpands.data`
- 且**全包搜不到任何 `.png` / `.ogg` / `geo_models/` / `textures/`**

这类包把模型、贴图、动画、音效**全部加密打包**进了那个 `.dat`，
磁盘上只留下 JSON 索引。加载后每一条 `display` 定义引用的资源都不存在，
于是渲染成缺失贴图（紫黑块）。

**原因**：解密由第三方前置模组 **TacZ:Arcana**（Forge 专有，闭源、
All Rights Reserved）负责，它提供「Resource gunpack assets protection for
encrypted」功能。TACZ 本体（无论上游 Forge 版、`Sh1roCu` 的 1.21.1 Fabric 版，
还是本移植版）都**不包含**该解密实现。

**因此这不是本移植版的缺陷，也不是能靠改我们的代码修好的问题**：
Arcana 没有 Fabric / 26.1.2 版本，且其格式与密钥未公开。

> 顺带一提，这类包往往还要求 TACZ `1.1.5~1.1.8`，我们的版本号已对齐 `1.1.8`，
> 所以它们能通过版本校验、正常显示条目 —— 这恰恰是「看得到枪、却全是紫黑块」的原因。

---

## 7. 文档

| 文档 | 用途 |
|---|---|
| `README.md`（本文） | 项目说明、免责声明、许可、装包方法 |
| `PORTING_26_1_2_SUCCESS_REPORT.md` | 26.1.2 降级移植记录（其中渲染结论以最新审计为准） |
| `docs/RENDER_PIPELINE_SCOPE_AUDIT_26_1_2.md` | vanilla / Fabric / Iris 调度、上游 stencil 与依赖版本审计 |

---

## 8. 已知差异与限制

- **镜内渲染是按 26.1.2 调度重写的**：实机证明 AMD 驱动拒绝在 DEPTH32 FBO 上追加
  standalone STENCIL8，而替换 depth attachment 又会破坏 Iris 的 hand 合成。当前实现不再修改 FBO：
  活动 ocular 先以“只写深度、不写颜色”的管线绘制，后方镜身自然无法通过深度测试，世界颜色从
  孔中保留；小型发光准星使用不受 ocular depth 遮挡的专用 HAND_TRANSLUCENT 管线。
  客户端配置 `[render]` 下应存在 `ScopeMaskEnable = true`；纯蚀刻 division 暂时安全跳过。
- **枪身/手臂不做镜内排除**：上游同样不做，非移植缺陷。
- 三个工作台（`workbench_a/b/c`）的名称取自枪包数据，上下游均未提供内置译名。

---

## 9. 贡献与反馈

提交 issue 时请附：

1. 完整崩溃日志 / `latest.log`
2. Minecraft、Fabric Loader、Fabric API、本 mod 的版本号
3. 复现步骤，以及**是否在纯净环境（仅本 mod + Fabric API）下复现**

修改渲染代码前建议先读 `docs/RENDER_PIPELINE_SCOPE_AUDIT_26_1_2.md`，
尤其不要把提交顶点的回调误当成实际 GPU draw 边界。
