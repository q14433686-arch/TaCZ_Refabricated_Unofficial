# 玩家反馈修复与剩余移植项复查

对照固定为 Fabric `356bdf9d48f2225bac6ceb56adc5576a418517a8`，目标 MC 26.3；不把同名类缺失直接算作功能缺失。此文是当前状态入口，旧 PLAYER_CHECKLIST 中的表格是历史基线，后续追加段落才是逐轮证据。

## 1. 本次反馈定位

### 瞄具安装

- 原 `DelegatingPackResources` 将多个磁盘/ZIP/overlay 作为一个资源包暴露，对重名资源只取第一项。`AttachmentsTagManager` 虽然使用 `scanDirectoryAll`，但已经看不到合成资源包内部被遮掉的标签。
- 与索引/display 的覆盖规则不同，`tacz_tags/attachments/**/*.json`（包括枪械允许表）是可叠加的字符串数组。旧枪包带着同名 `scope*.json` 时会遮掉新版或另一包中的旧镜条目。本次只为这类标签合并去重，**不**改变普通索引、模型、配方、语言的优先规则，也不放宽枪械槽位限制/转接座限制。
- 加入两种包顺序的单测，检查直接查询/枚举一致、允许表叠加、普通索引继续覆盖；外置测试 ZIP 故意带稀疏旧版 `tacz:scope*` 标签，真实安装测试必须在这个冲突环境下通过。
- `OpticInstallSmoke` 对所有可见非内置默认瞄具寻找默认枪宿主，客户端判定通过后发正式 C2S 改装请求，等待枪上附件 ID 和库存消耗同步。不再仅以直接写枪栈后的瞄具渲染测试验收安装。原 QR-24 必需增高座/非法拆座拒绝测试保留。
- 用户日志确认有 14 个枪包，但没有提供这 14 个包的文件，**尚不能断言用户每一组第三方枪/镜组合的唯一原因或全部恢复**。本修复针对可复现的多包标签覆盖缺陷；不会把“所有镜可装所有枪”当正确行为。

### 弹药库 UI

- 新增状态区整宽灰色 fill 覆盖了原版箱子纹理两侧透明边/斜边，因此出现图示凸边。改为延长原纹理的完整横向扫描线，使边框连续。
- 容量和种类各占内部半宽，长翻译/大数按可用宽度缩放，避免相互覆盖；完整容量仍可悬停查看。槽位坐标和点击判定不改。
- 实际库存菜单截图 `tacz_ammo_storage_frame` 在世界重入/库存守恒测试中生成；最终 CI 和人工复核结果在本文末记录。

## 2. 配置复查

| 配置组 | 本端设置/消费路径 | 当前检查方式与边界 |
|---|---|---|
| 受伤倍率、备用枪隐藏、枪口烟 | CLIENT `render`；Cloth Render 页面；hurt pose/背挂层/NativeMuzzleEffects 消费 | 新配置页构造、默认值/save consumer、FCAP 保存后重读 TOML；真实渲染隐藏对照；受伤倍率 0/.37/1 且只计非零原始伤害角；烟关时不增 burst、闪光保留。不是整进程重启。 |
| 方块命中配送距离 | SERVER `misc.BulletHitBlockEffectSendDistance`，默认128；0同维度无限，射手例外 | 服务端 TOML 项，**不伪装成本地 Cloth 项**；实际连接包数量对照。 |
| 空手趴下/趴下角度/空中散布 | SERVER `misc`，`LivingEntityCrawl`、鼠标钳制、`InaccuracyType` 消费 | 原 MovementRulesSmoke 检查空手许可、退出/姿态所有权、空中开关；鼠标俯仰连续输入尚无专项游戏断言。服务器权限，不允许客户端配置页擅改。 |
| 枪声激怒 | COMMON `gun`，Cloth Gun 页面 | 真实服务端敌对/中立、半径、禁用/消音/取消/已有目标对照；非真人听感。 |
| 方块列表/硬度、同队穿透、忽略实体 | COMMON `ammo` | 检查发现五项新规则缺 Cloth 入口，已补齐并沿用中英文键；默认值测试包含列表、1.0硬度、两个false开关。服务端 BulletRulesSmoke 检查规则，不等于双客户端弹道场景。 |
| 爆炸弹破坏方块重置值 | COMMON `ammo.ExplosiveAmmoDestroysBlock` | 修正 Cloth 重置 false 与 spec 默认 true 不一致；不强改已有用户配置。 |

静态扫描对照全部上游 config `define*` 键。本端缺少的上游键只有：`EnableExperimentalDisplayGunVbo`、`EnableStaticItemGunVbo`（26.3 原生渲染替代，不照搬旧 VBO 开关）；`EnableSableCompat`（集成未移植）；`HideGunPackCompatibilityWarning`（上游进服警告/关闭按钮流程尚未移植）。`PreLoadModConfig` 由本端 `PreLoadConfig` 负责，不因类名不同报缺失。

## 3. 真正未移植 / 部分移植

| 项目 | 已核实的当前缺口 | 优先级/下一组验收 |
|---|---|---|
| 背包直读供弹枪模 `%ammo_count%` | `client/model/papi/AmmoCountPapi.java` 仍只读枪栈；上游在持有该枪时调用 `ClientAmmoCount.getCached`。HUD已有缓存不代表枪模同步。 | P1：独立夹具改变背包/弹盒数量，比较枪模占位符与 HUD；普通弹匣对照。 |
| 动画粒子关键帧与通用效果解释 | `ObjectAnimation` 只有骨骼/声音轨道，没有上游 `ObjectAnimationParticleChannel` / `ParticleEffectKeyframes`；NativeMuzzleEffects 只支持默认两类效果，不解释任意 Snowstorm/Molang 组件。 | P1：按键开火、动画关键帧、重载和离场生命周期一组；不能拿默认烟已有提交冒充完成。 |
| Sable / Simulated | 没有上游装配体射线/命中上下文等集成与对应运行时验证。 | P2：需可用26.3依赖及真实装配体运动命中场景。 |
| EMI | 无上游 EMI 插件的等价集成。JEI/REI 是独立实现与独立运行矩阵。 | P2：依赖支持后单独做查询/转移/重载验收。 |
| Accelerated Rendering | `compat/ar/ARCompat.java` 仍禁用/no-op。 | P2：这是禁用，不是兼容修复；不得通过打开旧开关宣称完成。 |
| 枪包版本不匹配进服提示 | loader有日志与转换命令，但没有上游 `GunPackCompatibilityWarning` 的进服提示、指南/关闭按钮与隐藏配置。 | P2：不兼容包清单、首次入服、点击操作与保存重进对照。 |

## 4. 已有替代实现，但不可写成完整 parity

- 26.3 `GunRigBaker` / `DisplayBake` / collector 替代旧 external/static VBO；瞄具、热成像代码在 `client/render/scope`，不是上游 `client/renderer/scope` 同名类。不按文件缺失再算“未移植”。
- XM157 落点、测距、XL50 热成像、默认枪口火烟、位移/激怒、趴下与命中规则、背挂隐藏、命中配送均有相应实现和限定场景测试；旧清单里的“完全未移植”不再适用。
- 仍缺独立双客户端、Iris+光影、部分色盘/夜视、动态举枪全过程、移动烟雾尺度、六面弹孔像素与声音听感矩阵。**缺实测不自动等于缺实现**。
- 用户日志另有 `ccrp:m14_bipod` 未声明合法配件类型、多把第三方枪缺数据文件，以及 DH/Iris 渲染任务上传字体时的 render-pass 异常。分别记录，未在本轮修复或拿它们解释所有安装失败；日志中的第三方异常不应用空索引兜底绕过。

## 5. 最终执行结果

源码 `c2b6fae` 的[完整构建 44363012970](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44363012970)，job `135009200213` **success**，包括 JUnit、gametest 编译、服务端 overlay/nested MAE、Mesa Vulkan 集成客户端。新增真实门禁：

```text
PORT_OPTIC_INSTALL: 39 default scopes found hosts and installed through C2S with inventory settlement
PORT_CONFIG: 9 Cloth render/ammo controls built with correct defaults and save consumers; client FCAP TOML persistence passed
PORT_FEEDBACK: back-mounted toggle, actual ordinary-hurt render hook and same-level impact delivery controls passed
```

39 个镜各执行一组合法安装；不是 70×39 全部组合均发包，也不是把所有枪镜组合强制允许。多包顺序与普通资源覆盖规则由 JUnit 单独检查，外置旧标签 ZIP 在游戏运行中实际加载。

同次 XM157 恢复12黄色像素、遮墙零提交；XL50 热源经典2814 / PIP2529像素，倍率 stripe 7 / 45:45 / 38:38；枪口与射击结果门禁通过。第一次新增安装压力测试放在固定场景像素测试之前，`44362203041` 的 XM157 无标记对照中心出现黄色而失败；随后将安装压力测试放到原固定场景测试之后，并明确恢复其库存，没有放宽原像素判定，以上运行全部通过。尚未独立归因该次像素干扰。

本地六面弹孔离线回归6/6；严格版本一致性6通过、0失败、1警告。没有版本号/发布声明变更。弹药库实际菜单截图已提名回收，人工像素复核见后续补记。
