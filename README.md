# TACZ 26.1.2 移植版（Timeless & Classics Zero — Minecraft 26.1.2 / Fabric）

> **这是一个非官方的社区移植版本，不是 TACZ 官方发布。**

把 **Timeless & Classics Guns: Zero**（枪械 mod）从 Minecraft 1.21.1 Fabric
移植到 **Minecraft 26.1.2 Fabric**。

---

## 1. 这是什么 / 不是什么

| | 说明 |
|---|---|
| **是** | 上游 [TACZ-Refabricated](https://github.com/Sh1roCu/TACZ-Refabricated)（1.21.1 分支）的 26.1.2 移植 |
| **是** | **HOTFIX** 测试构建：已可游玩、可公开测试，但仍不保证无 bug |
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

## 2. HOTFIX 状态与已知限制

HOTFIX 在 Beta-2 的可游玩基线上，按 `26.2(main)` 的 `backport-26.1.2/` 清单回移植了
7 项已在 26.2 验证的修复，并通过签名适配移植补齐了工作台预览模型的缩放/旋转。
HOTFIX2 又同步了 26.2 后续结案的 PAL、弹道/后坐力、法线与镜内视模裁剪修复。
它仍然是测试构建，不保证与上游完全等价。

HOTFIX2 本轮新增 / 修复：

- **PAL 切枪后第三人称动画永久失效**：规避 Player Animation Library 1.2.5
  无法摘除完成态 fade-out modifier 的缺陷，改用可自动摘除的 fade-in-to-null 过渡。
- **曳光弹出生点取整与枪口漂移**：实体生成包改发精确 double 坐标；第一人称枪口
  以手部入口基座矩阵归一回视图空间，再按相机旋转写入世界轴。
- **ADS 开枪/换弹斜向固定侧偏**：动画约束按入口基座的逆变换恢复旧版坐标契约，
  消除随朝向出现的二倍角偏移。
- **枪械法线重复变换**：已经过 normal matrix 的法线改写裸值，修复平视过暗与光照方向错误。
- **镜内视模、目镜黑边与枪口火光**：复用 26.1.2 的深度孔径副本做屏幕空间反向裁剪；
  枪身、非瞄具配件、火光大面片和 energy-swirl 辉光只保留在目镜外；物理 `ocular_ring`
  在 cleanup 后独立重画；cleanup 只恢复仍由 invisible ocular 占据的像素，保留可见镜体深度，
  避免 Iris 的水、粒子和云覆盖低倍镜内部。

此前 HOTFIX 主要新增 / 修复（逐项明细见 `docs/BACKPORT_FROM_26_2_APPLIED.md`）：

- **跨维度后服务端枪械状态不复位**：Fabric 的 `AFTER_ENTITY_CHANGE_LEVEL` 事件
  javadoc 明写不适用于玩家，原实现里玩家一次都没进过 handler；
  已补注册 `AFTER_PLAYER_CHANGE_LEVEL`（修复「跨维度后换弹动作连贯但子弹不变」）。
- **持枪重进同一存档打不出子弹**：主动退出到标题走 `Minecraft#disconnect(Screen;ZZ)V`，
  不经过 `clearClientLevel`，登出清理从未执行；已注入该路径，
  并用「玩家实例 + 连接」检测区分进新世界/跨维度/死亡重生。
- **左利手玩家第三人称主手枪不渲染**：把「左手」硬编码当「副手」是上游缺陷；
  改用主副手判定（`IS_MAIN_HAND_SUBMIT`）。
- **枪包过滤器面板整个不可用**：列表行高参数错位 + 复选框贴图在 26.1.2 已不存在
  （改用 4 张 GUI sprite）+ 标签 alpha 为 0 被丢弃。
- **热度条按上游还原**：改为屏幕准星居中，使用遗留未引用的 `heat_base.png` 贴图，
  补回缩放迟滞、过热闪烁与百分比文案。
- **交互提示补回 4 处上游行为**：工作台过滤第二行提示、黄色主提示、
  准星上方位置、空手时按原版使用键（右键）提示。
- **HUD 版本号防溢出**：按可用宽度自适应缩字号（本移植版本号带构建元数据，比上游长）。
- **工作台预览模型可缩放/旋转**：走 `PictureInPictureRenderer` 离屏渲染再合回 GUI
  （26.1.2 两参 `renderToTexture` 签名适配，`+/-/R` 按钮恢复生效）。

以下 Beta-2 引入的内容继续保留：

- **中高倍镜镜内裁剪**：不再在 `CustomGeometryRenderer` 的顶点提交阶段直接改 GL 状态；
  改为独立 aperture/body/cleanup/reticle 批次，以不可见目镜深度形成孔径并精确恢复世界深度。
  Iris 下通过公开的 `assignPipeline(..., HAND)` API 归类自定义管线，并仅向手部 shader 注入默认休眠的恢复/掩码分支。
- **发光准星修复为真正自发光**：`*_illuminated` 准星改用专用 emissive/no-cardinal-lighting
  渲染类型，避免随玩家朝向在 vanilla/Iris 下反向变亮变暗。
- **LRTactical 继续作为内置兼容层**：throwable、melee、detonator/C4、consumable 基础流程保留；
  Fabric 元数据继续通过 `provides: ["lrtactical"]` 提供依赖标识。
- **曳光弹显示做了阶段性修复**：恢复上游 `energySwirl` 渲染类型与满亮 block light；
  HOTFIX2 进一步修复了出生坐标取整和第一人称枪口空间换算。弹道/命中逻辑本身未改。

当前已知重点限制：

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
> 本移植版的版本号是 **`1.1.8+fabric.26.1.2.HOTFIX2`** —— 前面的 `1.1.8` 是所基于的
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
  写入 ocular depth 前保存原始世界深度：Iris 直接复用其官方 pre-hand `depthtex2`，vanilla 才执行
  同格式 depth copy。镜身完成后只在 ocular 像素写回精确深度，而不是写 far depth。这样水、云、
  雾和粒子既不会被目镜挡掉，也不会无视地形叠在前景上。
  发光准星使用不受 ocular depth 遮挡的 HAND_TRANSLUCENT 管线；纯蚀刻 division 会过滤大面积
  blackout panel 后恢复细线/刻度。客户端配置 `[render]` 下应存在 `ScopeMaskEnable = true`。
- **手臂仍不做镜内排除**：HOTFIX2 已裁掉镜内的枪身、非瞄具配件与两层枪口火光；
  独立玩家手臂提交保持原渲染路径。
- 三个工作台（`workbench_a/b/c`）的名称取自枪包数据，上下游均未提供内置译名。

---

## 9. 贡献与反馈

提交 issue 时请附：

1. 完整崩溃日志 / `latest.log`
2. Minecraft、Fabric Loader、Fabric API、本 mod 的版本号
3. 复现步骤，以及**是否在纯净环境（仅本 mod + Fabric API）下复现**

修改渲染代码前建议先读 `docs/RENDER_PIPELINE_SCOPE_AUDIT_26_1_2.md`，
尤其不要把提交顶点的回调误当成实际 GPU draw 边界。
