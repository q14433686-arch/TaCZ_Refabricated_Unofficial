# 1.21.11 → 26.3 补漏执行记录

2026-11-27。来源：[26.X 施工单](GAPFILL_26X_FROM_12111_UPSTREAM120.md)，取自
`1.21.11@ec18ca4418a5b200502c5c2a6aa1a68988a35aa9`。接收基线为
`26.3@c5d536b7b1e61bcacb0cd660df73de78b7db6796`；本轮只改当前 Arena 工作分支，
没有修改 26.2、26.1.2 或 1.21.11。上游语义基准固定为
`Sh1roCu/TACZ-Refabricated@356bdf9d48f2225bac6ceb56adc5576a418517a8`。

**状态：已落码，当前 CI 的编译、JUnit、专服、普通客户端及可选运行矩阵全部通过。**
实现提交 `cd723cd0eae68884ab9e81964a52932063f0d757`，新增暂停专项与 viewer 触发路径的
验证提交 `7648ff4b87c8f13f52eccf6b2fa8e09f6a8cbc6d`。下方逐项仍保留没有专项覆盖的边界，
不把整体流水线通过等同于 C1–C8 的每条手工验收均完成。保留 `1.2.0+fabric.26.3.R1` 及依赖版本。

## C1–C8 的落点与验收欠项

| 项 | 本轮改写 | 新增／调整测试与待运行项 |
|---|---|---|
| C1 枪口 FOV | 在已有手部基座去除、视图空间换算后，通过 `FirstPersonRenderMath` 将 x/y 乘 `tan(world/2)/tan(item/2)`，保留 z；item tangent ≤ 1e-6 时比例为 1 | 新增 `FirstPersonRenderMathTest`：投影位置、不同 FOV、退化输入、深度不变。`ViewTuningSmoke` 改为从实际 FOV dynamics 计算 k，检查完整 XYZ 偏移，不再接受旧的深度缩放。上述测试以及 `TracerFrameSmoke`、`WorldTracerSmoke` 随本次全量 build／普通客户端通过；实际手部 delta=(0.2874, −0.1370, −0.3300)，改变手部像素 25,311 |
| C2 GUI LOD | `RenderDistance` 改成渲染线程嵌套计数 + 本线 `ScreenRenderTracker.isExtractingScreen()`；移除 100ms 标记。`GunPreviewRenderer.renderToTexture` 用 try/finally 覆盖延迟 PIP submit。更新 mesh 路由的旧注释，不改 GPU 路由代码 | 新增 `RenderDistanceTest`：嵌套、异常退出、立即恢复、额外 end 不污染后续作用域，随本次 JUnit 通过。普通客户端的掉落物 LOD 回归通过；仍待专项测工作台 PIP 高模、打开菜单时世界远处枪仍用 LOD、GUI 图标、mesh FIXED/HEAD 语境 |
| C3 制造按钮 | 无选中配方时 active=false、hover UV 偏移为 0，标签为带 alpha 的 `0xFF888888` | 待测空列表／无选中、重新选中、材料不足和正常制造；不是材料不足时一概禁用按钮 |
| C4 材料提示 | 使用 26.3 `GuiGraphicsExtractor.setTooltipForNextFrame`；格子布局、候选解析与每秒轮换与材料绘制一致；插入所属创造栏蓝色标题为第二行，空材料跳过 | 待测多候选 ingredient、空／未解析 ingredient、无所属创造栏及中英悬停 |
| C5 缺显示告警 | 并发 Set 记录本次重载已告警的 display ID，`clear()` 清空；日志包含 `assets/<ns>/display/guns/<path>.json` | **没有移动或新增 revision 递增**，保留异步烘焙代际规则。待测反复查询每 ID 一次、重载后可再次告警、正常显示不告警 |
| C6 暂停姿态 | 暂停只在 PAL 已安装时跳过，原版持枪姿态继续计算；保留 PAL 模型阶段更新的递归保护 | 新增 `PausedPoseSmoke`，在真实暂停的 F5 下运行 5 帧，再对真实玩家调用模型姿态路径：无 PAL 对照持枪动画的双臂 pose，有 PAL 检查跳过模型阶段更新。普通客户端和独立 PAL 两组均通过；不是所有第三方模型的像素验收 |
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
  已随实现提交推送并在本分支实际运行，文本校验步骤通过。
- viewer 工作流补入 `GunSmithTableScreen`、预览渲染器和 `RenderDistance` 的路径过滤，
  这类改动现在会自动触发 JEI/REI；PAL 过滤包含新增的 `PausedPoseSmoke`。
  `docs/ci/viewer-smoke.yml` 与正式件同步。

## 本地离线检查

以下为首次工作区检查；本地缺少 Java 不再是验证终点，后续已通过下节的 GitHub Actions 补齐：

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

## GitHub Actions 回执（2026-11-27）

以下全部是 **`7648ff4b87c8f13f52eccf6b2fa8e09f6a8cbc6d`、本 Arena 分支的 push 作业**，
不是 1.21.11、历史提交或 PR 合成提交的结果。已逐个读取 jobs/steps 和 Checks API；
沙箱无法直连 Actions 日志／artifact 的 blob 域，所以没有声称本地下载、复算了其全部原帧。
`compile-check` 的日志通过仓库既有回写机制同步到 `build-reports/compile-java.log`。

| 流程／作业 | 结果与直接链接 |
|---|---|
| compile-check | [通过](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45948317511/job/140338107401)，Java 25，生产源码编译成功 |
| build | [通过](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45948317506/job/140338128221)，全量 build（含 JUnit 和服务端 GameTest）、229 索引、202 种生存制造与缺一材料负对照、成品版本和嵌套 MAE 检查、普通 Mesa 客户端全部成功 |
| PAL | [通过](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45948317514/job/140338127395)，含新增暂停探针与既有离屏再入镜／双玩家控制器／F5 手部隔离；支撑手 Δz=3.9999998、Δroll=0.33764774 |
| Controllable | [通过](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45948317544/job/140338127726)，现有真实手柄运行场景 |
| JEI | [通过](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45948317529/job/140338044988)，重载前后 70 枪／107 配件／25 弹药匹配工作台与双向查询；中央画面 16,380/17,160 样本为面板 |
| REI | [通过](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45948317529/job/140338044814)，同计数、晚同步保留原版配方；中央画面 15,673/17,160 样本为面板 |
| Iris 常规光影回归 | [通过](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45948317497/job/140338131691) |
| Complementary Reimagined，ACT 关闭 | [通过](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45948317497/job/140338131973)，该套既有材质、阴影与受光场景 |
| Complementary Reimagined，ACT=128 | [通过](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45948317497/job/140338132027)，该套既有材质、阴影、受光与三档准镜粒子场景 |

build 已上传 `TACZ-Refabricated-7648ff4b87c8f13f52eccf6b2fa8e09f6a8cbc6d`
（artifact ID `15176416054`，67,629,661 字节）及 `smoke-7648ff4b87c8f13f52eccf6b2fa8e09f6a8cbc6d`
（ID `15176306456`，34,116,718 字节）。产物入口在上表 build 作业所属 run。

首次 `cd723cd` 的五条作业被后续验证提交的并发规则取消，不算失败，也不作为通过证据。
本轮没有需要靠放宽断言或屏蔽功能才能通过的 CI 失败。上游 Actions 的 Node/setup-java 弃用警告
不属于本次 Java 编译／运行失败，未借机升级依赖。

**仍未覆盖：** C2 的“菜单打开时远处世界枪仍使用 LOD”与 PIP 高模定量对照，
C3 空选中按钮像素／点击矩阵，C4 多候选材料的中英悬停／创造栏行，C5 缺显示告警去重跨重载，
C7 充能与关闭／配件锁组合，C8 人为注入 RuntimeException 后继续加载的负对照。
不能把专服正常加载、202 个制造回归或 viewer 查询通过写成这些专项已经验证；交接仍保留专项验收欠项。

不改变 Sable/Simulated、外部 SBM VBO、独立 EMI 入口和 AR/IF 禁用桩的支持状态；
未对任何第三方光影或用户键位组合新增兼容性承诺。
