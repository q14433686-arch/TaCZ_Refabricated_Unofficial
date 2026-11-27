# 1.21.11 → 26.3 补漏执行记录

2026-11-27。来源：[26.X 施工单](GAPFILL_26X_FROM_12111_UPSTREAM120.md)，取自
`1.21.11@ec18ca4418a5b200502c5c2a6aa1a68988a35aa9`。接收基线为
`26.3@c5d536b7b1e61bcacb0cd660df73de78b7db6796`；本轮只改当前 Arena 工作分支，
没有修改 26.2、26.1.2 或 1.21.11。上游语义基准固定为
`Sh1roCu/TACZ-Refabricated@356bdf9d48f2225bac6ceb56adc5576a418517a8`。

**状态：已落码，编译／JUnit／成品运行待验收，不标 DONE。** 本记录对应当前工作区，
尚无本轮提交 SHA、Actions 作业编号或新成品。保留 `1.2.0+fabric.26.3.R1` 及依赖版本。

## C1–C8 的落点与验收欠项

| 项 | 本轮改写 | 新增／调整测试与待运行项 |
|---|---|---|
| C1 枪口 FOV | 在已有手部基座去除、视图空间换算后，通过 `FirstPersonRenderMath` 将 x/y 乘 `tan(world/2)/tan(item/2)`，保留 z；item tangent ≤ 1e-6 时比例为 1 | 新增 `FirstPersonRenderMathTest`：投影位置、不同 FOV、退化输入、深度不变。`ViewTuningSmoke` 改为从实际 FOV dynamics 计算 k，检查完整 XYZ 偏移，不再接受旧的深度缩放。上述测试以及 `TracerFrameSmoke`、`WorldTracerSmoke` 均待跑 |
| C2 GUI LOD | `RenderDistance` 改成渲染线程嵌套计数 + 本线 `ScreenRenderTracker.isExtractingScreen()`；移除 100ms 标记。`GunPreviewRenderer.renderToTexture` 用 try/finally 覆盖延迟 PIP submit。更新 mesh 路由的旧注释，不改 GPU 路由代码 | 新增 `RenderDistanceTest`：嵌套、异常退出、立即恢复、额外 end 不污染后续作用域。待测工作台 PIP 高模、打开菜单时世界远处枪仍用 LOD、GUI 图标、mesh FIXED/HEAD 语境 |
| C3 制造按钮 | 无选中配方时 active=false、hover UV 偏移为 0，标签为带 alpha 的 `0xFF888888` | 待测空列表／无选中、重新选中、材料不足和正常制造；不是材料不足时一概禁用按钮 |
| C4 材料提示 | 使用 26.3 `GuiGraphicsExtractor.setTooltipForNextFrame`；格子布局、候选解析与每秒轮换与材料绘制一致；插入所属创造栏蓝色标题为第二行，空材料跳过 | 待测多候选 ingredient、空／未解析 ingredient、无所属创造栏及中英悬停 |
| C5 缺显示告警 | 并发 Set 记录本次重载已告警的 display ID，`clear()` 清空；日志包含 `assets/<ns>/display/guns/<path>.json` | **没有移动或新增 revision 递增**，保留异步烘焙代际规则。待测反复查询每 ID 一次、重载后可再次告警、正常显示不告警 |
| C6 暂停姿态 | 暂停只在 PAL 已安装时跳过，原版持枪姿态继续计算；保留 PAL 模型阶段更新的递归保护 | 必须分无 PAL／有 PAL 两组，在暂停界面检查第三人称姿态与无递归；本轮没有运行这两组 |
| C7 充能改装 | 只在打开改装界面前检查 `IClientPlayerGunOperator.isCharging()`，已有关闭界面路径不变 | 待测充能按键、充能结束后打开、已开界面关闭及配件锁 |
| C8 战利品容错 | 每份注入处理捕获 RuntimeException，日志 `Failed to load loot injection {}; skipping`，继续后续文件 | 待以坏结构／运行时解析异常与后续有效文件验证；不捕获 Error |

C9（配件标签 LinkedHashSet）、C10（约束旋转绝对值）原本已有，未重复施工。

## 配置注释的逐项决定

用 `scripts/align_config_text.py` 对照上游 93 键：本地 146 键，原 24 条 COMMENT 差异中
**20 条采用上游说明，4 条保留本线说明**；默认值、范围、键和定义顺序不变。

| 范围 | 决定与源码依据 |
|---|---|
| ammo 7 条 | 采用。`DestroyGlassBlock` 按列表与非负硬度阈值无掉落破坏；`AmmoBlockConfigRead` 解析 ID/#tag；`EntityUtil.getHitResult/isBulletDamageIgnored` 与 `ProjectileExplosion` 处理队伍穿透与实体伤害忽略 |
| gun_pegboard 2 条 | 采用。`GunPegboardItemConfigRead.canPlace` 的额外白名单与优先黑名单；不改注册或默认允许类型 |
| 两个 DefaultPackDebug | 采用。`GunPackLoader.prepareDirectory` 启动时读 PreLoadConfig 决定导出，`GetJarResources` 按版本／内容指纹替换；旧 OtherConfig 字段不再参与覆盖判断 |
| HideGunPackCompatibilityWarning | **保留**。`PackCompatibilityNotice` 仅隐藏警告，不转换或放行包；登记为本线文本差异 |
| BulletHitBlockEffectSendDistance | **保留**。`ServerMessageBulletHitBlock.broadcast/shouldSend` 在同 level 内始终包含射手，不能丢掉这条事实 |
| CrosshairType | **保留**。`TuningProfiles` 用旧值作个人视图配置的预设种子，不是完整的当前准星选项 |
| HitParticleMaxDistance | **保留**。`HitSurfaceParticleSpawner.isCloseEnough` 在基本碎屑和 extra 的共同入口，不仅限制 extra |
| 其余 render 9 条 | 采用。`GunHurtBobTweak.heldGunTilt` 与 GameRendererMixin 验证受击权重和原版 Damage Tilt 相乘；`ScopeLensFrame.beginFrame/needsHeat` 验证 lens/thermal 门控；ocular 烘焙、extra 开关、创造栏筛选恢复、独立 renew 烟均按各自现有调用点核对 |

另保留原已准确的 `HitParticleEnable` 配置注释，作为第五条已登记文本差异。
Sable 和两个上游 VBO 配置不在本线，记录为已知缺项，**不是支持声明**。

## L2 与工具

- en_us / zh_cn 的命中碎屑说明不再与弹孔贴花开关绑定；其他语言删除该说明键以回落 en_us。
- 语言对齐工具只读本线源码语言文件，**不读取 26.1.2 bundle**；保留个人 View settings
  与工作台 recipe-unavailable 的本线措辞，converter 标签改成 26.3，玻璃标签示例改成 `#c:glass_blocks`。
- 删除语言里未实现的 Sable/VBO 配置标签；JSON 重写也消除了 en/zh 原有的重复键，保留原最后胜出的有效值。
  `tr-TR.json` 按施工单不处理。没有给上游暂缺翻译的条目臆造新译文。
- 新增 config/lang 两个校验脚本与三方表面、common-token 两个只读诊断工具。
  后两者的 26.3 对照固定为接收基线，可用 `--r263` 覆盖；只比较提交树，不代表工作区验收。
  common-token 的 `--only delta` 另需抓取旧上游 `d2903554da039d2355920953a81447784a3f2be2`。
- `.github/workflows/build.yml` 增加 pinned upstream fetch、文本检查及 7 个工具回归测试，
  `docs/ci/build.yml` 与其逐字一致（同时补齐镜像原先缺的 Central init script 与 settlement 日志检查）。
  **只是工作区配置，尚未部署或触发 Actions**；如凭据无 workflows 权限仍须维护者上线。

## 本轮实际执行

以下均在当前工作区执行，不借用 1.21.11 或历史作业的结果：

| 检查 | 结果 |
|---|---|
| `python3 scripts/test_text_alignment.py` | 7 项通过；包括已登记注释不掩盖默认值漂移、缺项／未知注释差异失败、L2 回落、版本／标签适配、缺少 ref 时失败 |
| `python3 scripts/align_config_text.py` | 上游 93、本地 146，未登记差异 0；5 条文本偏差、3 个未实现配置有明确登记 |
| `python3 scripts/port_upstream_lang.py --check` | `stale: none` |
| `bash scripts/check_release_consistency.sh --strict` | 6 通过、0 失败；Arena 分支名不是 MC 版本号产生 1 条预期警告 |
| `python3 scripts/test_block_guards.py` | 14 项通过（静态门禁的变异测试） |
| `python3 scripts/verify_recipe_viewer_reload.py` | 7 项通过（静态接线） |
| `python3 scripts/verify_120_resources.py` | 4,176 默认包文件、229 索引，246 条既有上游引用例外 |
| `python3 scripts/verify_block_drops.py --strict` / `verify_block_entity_persistence.py --strict` | 通过（静态检查） |
| `git diff --check` | 通过 |
| `./gradlew compileJava compileTestJava compileGametestJava test --offline` | **未进入 Gradle**：沙箱没有 java，JAVA_HOME 未设置；不算编译或 JUnit 通过 |

环境补齐 Java 25 和依赖后，先编译生产、测试与带 `-PtaczClientSmoke` 的 gametest 源集并跑 JUnit，
再跑普通客户端、PAL 专项、两种 viewer 和专服／战利品专项。每项取得自己的产物与运行记录后，
回填提交 SHA、作业编号及未覆盖项，才可把本交接改成 DONE。

不改变 Sable/Simulated、外部 SBM VBO、独立 EMI 入口和 AR/IF 禁用桩的支持状态；
未对任何第三方光影或用户键位组合新增兼容性承诺。
