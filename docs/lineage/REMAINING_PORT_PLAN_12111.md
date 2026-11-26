# 剩余移植的单项闭环计划

2026-11-26；目标仍为 MC 1.21.11 / Java 21 / intermediary / 深度孔径。行为参照 `26.1.2@d12a3f2`，交接参照 `26.3@c5d536b7`。

用户要求：按类型分步、单项闭环；持续推进直到剩余工作完成，不在每一项结束后停下来等待下一条指令。

## 固定工作规则

每次只推进一个活动项。**代码接入 → 目标版编译与单测 → 专项成品运行 → 回执与边界记录**四步齐全才标 DONE；失败就留在该项修复，不用“先做下一项”回避。发现不适用的 26.x API，要证明本版等价路径，不能靠删除注入或关闭功能过关。

## 顺序与关闭条件

| 顺序 | 项目 | 关闭条件 | 状态 |
|---|---|---|---|
| R1 | 混合镜内分划 | 活动 division 统一提交；墨线/发光分层；父子根去重；共享可见性与矩阵恢复；无孔径保守 fallback；失效孔径不泄漏大面；原版/Iris 成品专项测试及现有矩阵回归 | **DONE — `3515cf98`** |
| R2 | 热像 | posed model 捕获、纹理 cutout、材质与场景深度、黑白名单、关闭/切世界清理；原版与 Iris 中遮挡/透明面/降级的像素正反例 | **DONE — `ab452920` + `f1b800d8` 专项** |
| R3 | PIP 运动、镜片与近场 | 对齐供体 ADS/运动/切视图行为；真实帧比较、孔径边界与遮挡；不能仅重跑静态孔径测试 | **DONE — `b2ad0276` 光学；近场散射归 R4** |
| R4 | 动态光 receiver 与材质 | 核对 1.21.11 entity/block submit 时序、世界表面响应、发光材质不重复受光；正反实帧 | **DONE — `574e6506` + `ac2e76f3` 双环境实帧** |
| R5 | 输入与平台小差异 | 键/鼠/scancode/卸弹冲突、可选 Zoomify 装/不装、名字皮肤异步/失败、LRT GUI/音量；逐项证明本版替代或移植并实测 | **DONE — `65010ffc` 双平台实机** |
| R6 | 全量收尾 | 对全部共享代码做行为差异复核，关闭非同路径表里的每个真实缺口；全构建、成品双端/旧档/查看器/光影回归；记录已知外部限制与许可，不冒称第三方协议已修复 | **ACTIVE — 共享行为复核与最终矩阵** |

R1 的专项测试必须增加自己的分划断言，原有 `PORT_OPTIC` 孔径回执不能单独作为 R1 通过证明。每项结果继续写入本文件与主执行账本，供后续会话从唯一 ACTIVE 项继续。

## R1 收口回执（2026-11-26）

**R1 已完成本项闭环，当前活动项为下文 R2。**

实现提交 `269d7091`，专项对照加固 `c7366cf7`，实际验收 SHA **`3515cf985e209600808b28c7073815e4bca8bd08`**。`0b07456e` 仅回写成功编译日志。

### 已落地

- 用 authored division 根统一采集混合分划，避开 grouping 父节点和 housing 装饰；HAMR/Vudu 保留本版活动目镜映射。
- 快照显式记录 `unshaded`，发光标记向子树继承，不能把“packed light 恰好满亮”的日照墨线当发光层。墨线和发光层分别使用本版对应材质。
- 父子 division / luminous 根不重复提交；只临时打开正在捕获的根，可见性与 PoseStack 在 finally 恢复；延迟回放不持有可变骨骼/矩阵。
- 有孔径时使用真实 authored 几何，不再靠 6-unit 尺寸启发式裁掉合法标记；无孔径时只允许经过薄标记过滤的发光 fallback。
- immediate、late-hand、final-overlay 回放同一份已冻结几何。镜内 MASK 缺 program/uniform、深度副本或目标身份不符时拒绝 draw；MASK_OUTSIDE 的原有普通视模 FX fallback 不变。

### 通过的实际验收

| 验收 | 回执 |
|---|---|
| Java 21 main/test/gametest 编译及日志回写 | [45735446629](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45735446629)，**success** |
| 全构建、JUnit（新增 R1 contract）、JAR 审计、开发服/客户端 | [45735446681](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45735446681)，**success** |
| 发行 intermediary 矩阵 | [45735446618](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45735446618)，**10/10 success** |
| 原版分划专项 | [139627448986](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45735446618/job/139627448986)：`ink=88 restoredInk=88 luminous=4 controlNoise=0` |
| Iris 分划专项 | [139627448922](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45735446618/job/139627448922)：`ink=84 restoredInk=80 luminous=20 controlNoise=0` |

像素专项是在真实 ADS 的默认 `scope_standard_8x` 上暂停世界/动画，先取无修改对照，再独立移除墨线、发光层，最后恢复，比较中心视野的实际 framebuffer。`controlNoise=0`，没有用武器动画位移当作分划变化。保留五张原始 PNG。两种环境还执行：相同快照 contract、缺 program 的镜内 draw veto 与孔外 fallback、HAMR/Vudu 两组非空且互斥、TA31 housing 不能被当准星。

[原版帧与日志](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45735446618/artifacts/15078846923) · [Iris 帧与日志](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45735446618/artifacts/15078703234)

[本项测试 JAR](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45735446681/artifacts/15079041680)，JAR 本体 SHA-256：

```text
88843240df59824fe384e00db761e3a500f624ef2455c996bfdafbf75952c1d9
```

边界：这关闭的是 R1 的几何/材质/活动组与目标原版、钉住的 Iris 组合回归，不代表所有第三方模型、所有光影包都实测过；没有把热像或 PIP 运动/近场的验收借此标绿。未提升版本、未发布 Release。


## R2 施工与验收状态（2026-11-26，进行中）

- `ff3abb47` 的 CI 对 **named 1.21.11** JAR 做 `javap`：`ModelFeatureRenderer.Storage.add(RenderType, ModelSubmit<?>)` 可作为 solid / sorted-alpha 模型提交的共同记录点；`ModelSubmit` 保留 model、state、pose、sprite、UV 对应材质。CameraRenderState **没有**供体的 `viewRotationMatrix`，不能直接照抄。
- 实现 `365a71d0`：原生世界遍使用目标版 `renderLevel` 的 `viewMatrix`；Iris 在 `beginHand` 前用本版 camera orientation 的逆旋转冻结世界热源，保留当前世界/窄遍投影。独立颜色/深度附件回放真实模型，cutout coverage、scene-depth sampling、shift-match 和 nested bypass 归还均已接线。复用本分支已存在的材质 accessor，不重复新增同字段 accessor。
- 补齐原先未接线的 `scope_lens` display → model → 每帧观察/预测 → clean scene capture → dynamic uniforms / HeatSampler → 深度孔径镜内合成；保留枪口特效 ownership wrapper 和 native aperture。资源重载/离线销毁热像资源；热像窄遍不做隔帧复用，避免热源与颜色错帧。
- `365a71d0` 目标版 Java 21 main/test/gametest 编译成功：[45737416522](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45737416522)。这**不是** R2 实机通过证明。
- 专项 `69f708a6` 在两套现有 intermediary 光学客户端中增加实际 heat attachment 和最终镜内像素双重读取：空场、透明 slime、opaque wall、glass、关闭/恢复、chicken cutout、invisible、blacklist armor stand、测试资源白名单 minecart。测试临时配置仅改内存模型并在 finally 归还，不改默认包字节。另增加供体的 ScopeLensConfig JUnit。
- 目前仍等专项运行结果；**R2 未关闭，R3–R6 未借此标绿**。后续失败必须在 R2 修复，四步闭环后立即继续 R3，不等待用户逐项批准。

- 首次专项 `69f708a6` 失败于测试按运行时 poly-mesh 子类查找父类字段；`863219b7` 已修正。该轮实机进一步暴露 heat not ready，检查确认 cached HUD 投影只有实例镜像、没有返回 slice 的身份索引，致使 ocular 观察失败。`13e929c7` 补 `CachedPerspectiveProjectionMatrixBuffer.getBuffer` 返回切片镜像，增加失败状态诊断与附件销毁/重建断言；正在重跑。未将失败项标绿。

- `13e929c7` 的实机诊断仍是 expected/current/projection=null、active=false；不能仅凭补 slice 就报修复。`77e8c406` 将 bounds 测量移到真正消费手部 batch 的回调，提取时仅观察 display；`484da3e1` 另修正暂停分划测试打开 Screen 后按键被释放的测试时序，热像专项先重新完成一次真实 ADS。
- **`484da3e1` 原版热像专项已通过**（[139640272453](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45739333635/job/139640272453)）：empty=0、slime=1122、sensorWhite=1122、opaque mask=0 / white=0、glass=841、chicken=349、invisible=0、blacklist=0、whitelist=392，关闭/恢复与附件释放/重建均通过；R1 contract/pixels 与 hip 孔径清理亦通过。该 SHA 的发行矩阵 **9/10**，Iris 专项失败于 slime heat=0 / sensorWhite=0，因此 **R2 不关闭**。该 SHA 全构建和编译通过；产品 SHA-256 `116b0e44047f2933aa46779fbdb3a717e6abb8ae64cc36e3a1f524c55dcfcd7f`。
- `a7cfda59` 将 Iris 捕获视图改为本版 `renderLevel` 的确切 view 参数，不再从 camera quaternion 重建；同时保存提交/材质计数供失败定位。继续验收原版/Iris，不能因原版通过跳到 R3。

- `a7cfda59` 的 Iris 失败仍为 0 像素，但诊断计数为 submittedModels=2 / texturedModels=2：不是实体分类或丢提交。`187d9453` 的独立测试探针曾因调用未向消费工程开放的 RenderPipelines.register 编译失败，`a2901742` 改为仅 build 测试管线。
- `a2901742` 的 Iris 故障实验明确得到：原生片元 + 普通 LEQUAL 仍 0；**仅在测试模组临时关闭深度时可见 1122 像素**。该实验执行后仍然抛失败，finally 归还生产管线，绝不计作通过或发布无深度热像。当前已将问题缩到场景/回放深度一致性；`e64f698d` 增加测试专用 24-bit window-depth 读回，同时读取 private/main depth，以便查明数值差而不是猜测放宽遮挡阈值。原版专项继续通过，R2 仍 ACTIVE。

- `e64f698d` 的真实读回：replay windowZ=0.9967458669742406、private sceneZ=0.99674094；差值约 4.93e-6，与 Complementary 的 -0.00015 clip-space 位移量在该距离的换算相符。捕获/材质/投影到屏幕的位置成立，不能靠 no-depth 对照冒充修复。
- `08b1bf0b` 尝试适配目标版 **DEPTH_COMPONENT32（UNORM32）** 的精度边界：普通 bit-copy 会保留采样器 float 无法表示的低位，精确 EQUAL 回放因而不能可靠往返。先在独立 private depth 上用深度-only pass 写入同一场景采样器的 float 值，再保留供体的 shifted EQUAL / read-only 判据；不做放宽 LEQUAL、坡度 bias 或关遮挡。新增量化往返 JUnit，测试专用 no-depth 实验仍只在失败后执行、仍抛失败、仍 finally 还原。该轮实机结果与后续合成边界修复见下文。

- `08b1bf0b` 实测已把 Iris heat 从 0 恢复到 **1122**，但最终 white 仍为 0（[139653012655](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45743216043/job/139653012655)）。因此下一步修的是最终镜片合成，不再重复改实体回放。
- `53d55a39` 查明 `supportsFinalScopeOverlay()` 只允许 1.10.7，而成品测试使用 1.10.8。补白名单到精确 1.10.7 / 1.10.8（不泛放 1.10.x）；脚本固定 1.10.8 发行版本与 JAR SHA-256，并用该 **实际 intermediary JAR** 的 `javap` 检查 beginHand 预手部深度、finalizeLevelRendering 世界状态关闭→composite→final 顺序及两次 hand projection 归还。
- `53d55a39` [编译 45743966970](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45743966970)、[全构建 45743966863](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45743966863)、原版 [139655528198](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45743966870/job/139655528198) 与 Iris [139655528194](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45743966870/job/139655528194) 专项均 **success**。新增空场光学参考与蓝背景，独立绿色可见实体轮廓须 ≥83% 白热填充（不是从热图反推轮廓）；原版 **992/992**，empty=0 / heat=1122 / finalWhite=1122 / opaque=0 / glass=841 / chicken=348 / invisible=0 / blacklist=0 / whitelist=392。Iris 同一断言通过，但其后半 notice 被 GitHub 单 step 展示上限截掉，未伪造具体计数；原始日志与 PNG 在 [15082407868](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45743966870/artifacts/15082407868)。R1 Iris 消融仍通过（ink=95 / restored=95 / luminous=29 / noise=0）。该轮整矩阵因接续提交取消部分未完作业，不声称 10/10。
- 此产品 JAR 本体 SHA-256：`c0151ba799eb017a6a81e8fdac13f5591f1ff389dd9cb64577093a787148aaed`。未发版本；R2 仍 ACTIVE。
- `c1f38fb2` 紧接增加实际 extracted LivingEntityRenderState.bodyRot 的测试观察点，覆盖 **16/36 格 × 0/45/90° × classic/PIP/narrow = 18 组**。每组同背景空场/光学/热像/实墙原帧、独立 ≥83% 可见轮廓覆盖、heat 正例与 wall 零热负例；测试 whitelist 同时放 armor_stand，验证 blacklist 优先。聚合 notice 避免 18 组回执被单 step 上限截断。该组尚待运行，不提前标绿。


## R2 收口 / R3 接续（2026-11-26）

`c1f38fb2` 的范围测试确实拦住了一处生产回归，而不是改测试躲过去：经典 authored lens 被错误当作 PIP，抑制了正常 ADS 世界倍率。`ab452920` 恢复供体规则：**只有 PIP 接管世界 FOV；classic 的 1:1 镜片保留原 ADS FOV**。其 [编译 45745251692](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45745251692)、[全构建 45745251694](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45745251694)、[成品 10/10 45745251695](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45745251695) 均 success。

- 原版 [139659709847](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45745251695/job/139659709847)、Iris [139659709627](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45745251695/job/139659709627)：各 18 组 body yaw/range/classic-PIP-narrow 全通过。每组 wall mask=0；不是只测轮廓中心。classic/narrow 的 16 格热源 18392，重投影源 1122；36 格分别 4290 / 210，真实倍率来源有区别。
- 接续测试 `f1b800d8` 不改产品字节；[编译 45746441228](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45746441228) 与 [发行 10/10 45746441247](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45746441247) success。原版 [139663546169](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45746441247/job/139663546169)：white body14743/14743、black14743/14743、ironbow14733/14743；Iris [139663546220](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45746441247/job/139663546220)：white14400/14420、black14420/14420、ironbow14356/14420。两者 opaque mask/white=0、invisible=0、blacklist 优先于 whitelist=0；glass/chicken/minecart 正例、禁用恢复、失败热源实际光学 fallback、三人称清理/第一人称恢复、附件销毁/重新分配全通过。
- 该测试轮 build 的开发 `PackClientSmoke` 在创建世界时超时（[139663545765](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45746441248/job/139663545765)），不是把失败写成 success；`gh run rerun --failed` 被 API 拒绝。产品与 `ab452920` 全构建成功的 JAR 散列相同，下一次 R3 push 继续执行该开发回归。
- [R2 JAR artifact15082847326](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45745251694/artifacts/15082847326)，本体 SHA-256 **`a43e1d7cf22cdbd08d6b4ce006e1f721c6a954f460cea422e5161ed060c44180`**。

**R2 本项闭环完成，当前 R3 ACTIVE**。这不表示所有光影包都支持，只覆盖精确钉住的发行组合；不改版本、不发 Release。R3 继续修接收树仍残留的 0.35 硬阈/分段倍率、镜片移动 UV、经典镜片 foreground 顺序及同帧实像验收；近场散射的 receiver 验证与 R4 合并观察，但不跳过该行为。


## R3 施工（进行中，尚未实机通过）

- `2cf04dd9` 按供体接回同一帧的 picture progress：去掉接收树残留的 0.35 隐含阈与十档倍率；重投影按本帧 `totalZoom / worldZoom`，窄遍直接按本帧 ADS 进度改变源 FOV；经典 authored lens 的 reticle/rim 也延迟到 lens 之后；Iris 移动中打开 source footprint guard，不能抽到枪体/镜环。
- 新增 ScopeZoom JUnit 验证 5 种倍率 × 4 种 world share × 101 个 progress 的乘积恒等式，以及端点/clamp。真实测试按供体 `AdsTransitionSmoke` 适配：使用实际 aim key/插值/模型，仅把耗时固定为每 tick .05 的测试夹具；同一帧读 lens 前、lens 后、ink 后，逐像素检查移动 ocular、90% 合法 UV 颜色匹配、tile 换色仍可见、孔径外改写必须 0；另测 missing mask、用户 reveal floor、half world share 收镜和经典 tint。
- `2cf04dd9` [编译45749118956](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45749118956) / [全构建45749118992](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45749118992) success；上一轮开发 PackClientSmoke 建世界超时没有再现。但独立实机探针编译发现本版 gametest input 没有供体的 lookAt API；不是产品渲染通过。
- `b2ad0276` 改为本版现有 player yaw/pitch 夹具；进一步接入供体 WideSceneSmoke，分别在 narrow ADS / released 下改动镜外 panel、真实 slime、day/night sky，证明窄遍没有烧掉主遍状态。Iris private depth copy 的需求同时覆盖 reveal floor 之前的 foreground ownership，不将“暂不贴 picture”误解为“不需要 reticle 深度”。当前跑 [45749621489](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45749621489)，不提前关闭 R3。


### R3 实机收口，R4 ACTIVE

`b2ad0276` 编译 [45749621496](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45749621496) success；[成品45749621489](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45749621489) **10/10 success**。原版 [139674101005](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45749621489/job/139674101005)、Iris [139674100742](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45749621489/job/139674100742) 各 16 组运动同帧测试通过，全部 outside=0，missing mask/hip/below floor 的 changed=0；实际 ocular 起点与稳态相差约 160 像素。原版合法 UV 匹配 raise4898/4898、steady11464/11464、half-share lower5948/5948；Iris4620/4620、11376/11376、5357/5357。不是只读矩阵或固定孔径。

WideScene 两环境 narrow/released 均保留真实宽遍：原版换色 panel11557、slime1659、day-night sky20000/20000；Iris panel11543/11539、slime1659/1660、sky20000/20000。R2 的18组热像、三色盘及全部生命周期回归仍通过；同帧 pre/lens/final 共48张运动 PNG 与镜外场景均保留。

本体 SHA-256 `2a6be490f038a6f7834e7f42ff1a1142932d547192a70d07f0c3924d81d68706`。`2cf04dd9` 的完整 build/JUnit 曾 success；`b2ad0276` 完成 build/JUnit/JAR 与服务端测试后，开发 PackClientSmoke **再次**建世界超时，不能泛称本 SHA 全构建绿；已作为 R6 必查回归保留，不用放宽光学断言掩盖。当前关闭的是 R3 的实际光学闭环（编译/数值测试/原版及 Iris 成品光学矩阵），近场散射/receiver 光照顺序立即进入 R4；其他光影、Voxy 未实测，版本不变。


## R4 施工（当前活动项）

`6b31bddd` 的目标 named JAR `javap` [45750956437](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45750956437) 证明：**1.21.11 实际存在**稳定 `submitEntities(PoseStack, state.LevelRenderState, SubmitNodeCollector)` 与 `submitBlockEntities(..., SubmitNodeStorage)`；先前接收树声称只能用长事件窗口的注释不正确。`1834fc62` 已适配供体两个 native submit wrapper，不注入 lambda：每类 receiver 在同步 submit 窗口中临时提高 block light，finally 归还字段与 Java21 ThreadLocal；source/terrain/预算每主帧共享一次时钟，shadow/narrow 不借用外层灯。进一步接通此前完全无调用者的 RackSurfaceLight 与 rackSlotLight：冻结 rack modelState/dye、逐真实面投反射、每面 slot light；glowing case contents 保持原 fullbright，预览仍走本版 RackPreviewCollector。

`1834fc62` [编译45751652189](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45751652189) / [全构建45751652128](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45751652128) success；成品原版到达 receiver 夹具后被正常 whitelist 拒绝 emerald，不能算光照通过。`4df18f54` 仅在测试模组添加同显示专项已有的 emerald 白名单 tag（不改默认包），并补近墙/地面、嵌套提交主动抛异常后的字段/context 还原检查。

R4 实机正例必须为真实 stock AK 按键射击并确认服务端少一发；readback 取第一次对应主遍完成帧，读取实际提交的 cow/stand/item/statue/pegboard slot/fullbright case UV2 与真实表面像素。对照为开关、到期、发射者被石墙挡住（仅此负例可用 synthetic event）。新增测试随现有原版/Iris 发行客户端运行，因累积专项增大仅把外层进程预算 720→1200 秒，未改像素/深度/UV2 判据。当前仍待运行，不将 R4 标绿；R5/R6 尚未开始。


### R4 实机收口，R5 ACTIVE

生产 `574e6506` 编译 [45755359924](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45755359924) / 全构建 [45755359933](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45755359933) success。增加明/暗双材质测试的 `ac2e76f3` 编译 [45756198267](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45756198267) success；原版 [139695362232](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45756198346/job/139695362232)、Iris [139695362160](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45756198346/job/139695362160) **receiver 实机均 success**。当前整矩阵仍在运行，不借此报全绿。

除 receiver 作用域和 rack 真实模型接线，还修了两个实帧才暴露的缺口：引用的 `surface_white.png` 根本没入库（80 个 terrain patches 真提交但采到 missing texture 黑色），已按供体原字节恢复并给发行 JAR 钉 SHA；展示柜在本版全块是 TRANSLUCENT，其不透明背板会覆盖 entity 阶段反光，现只延迟该真实 rack atlas/pose 快照到 Fabric END_MAIN、原生最后 endBatch 之前，保留深度测试。事件的 native pose 现显式传递；目标 bytecode 证明通常为 identity，先前“需要补 view rotation”的猜测不成立，未额外旋转世界。

两环境真实 stock AK 扳机均确认服务端各少一发，第一主遍 UV2 OFF `[0,0,0,0,0,15728880]` → ON `[112,144,144,80,112,15728880]`；sky 不变，expiry/隔墙与 OFF 相同，原提交字段归还，嵌套原调用主动抛异常后 Java21 context 与输入均归还。

| 接收部位 ON−OFF 红/绿通道均值 | 原版 | Iris |
|---|---:|---:|
| cow / stand / helmet | 21 / 75 / 78 | 17 / 73 / 73 |
| statue / pegboard | 33 / 54 | 21 / 21 |
| case dark back / lower frame | 23 / 90 | 6 / 37 |
| ordinary rack item / fullbright case item | 55 / 1 | 38 / 0 |
| near wall / near floor | 64 / 47 | 31 / 34 |

所有到期/隔墙残差绝对值≤1。Iris 暗背板上一轮是 4→9（+5），并非无响应；新增独立明亮框架固定≥6 判据，暗背板另需≥2倍底色、≥4 code 且≥4倍实测 control noise，负例噪声≤2；没有把旧的完全无反应当通过。最终两环境暗背板也分别 +23/+6。各六张原始 PNG；receiver 拆为独立原版/Iris 启动，**原有光学全部断言仍保留在独立 optics / optics-iris**，发行矩阵现12项。

本体 SHA-256 **`767e1c68b79653873f622731410c73159bbb448c6592a0a2900981a11392668c`**，文件名/version 未改，未发 Release。R4 四步闭环完成，继续 R5（统一物理输入、Zoomify 装/不装、异步皮肤/失败、LRT GUI/音量）。R6 仍保留开发建世界间歇超时、最终全矩阵与全共享代码复核；其他光影/Voxy不冒称实测。


## R5 施工（ACTIVE，未验收）

`3c88ff3c` 接回供体统一物理输入分发，保留本版 ViewTuning 原生 click queue 与 shoot polling、控制器与混合按住语义；补原来检视/改装/配置只支持键盘的遗漏；物理 key/scancode/mouse 一套身份匹配，unload 及 ALT+reload 优先并 return；鼠标在 refit 屏幕的 Pre 阶段关闭且取消事件，不穿透为新一次开启。`ceaee475` 改用本版已有 public action dispatcher（不访问供体 private helper），[编译45757011850](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45757011850) success。

`6f472f63` 新增独立 platform / platform-zoomify 成品启动，矩阵14项。原版首次已实际通过 native KeyboardHandler/MouseHandler 的 scancode、mouse inspect、repeat/release 不触发、GUI veto、refit鼠标开/关、卸弹冲突与ALT优先，以及原版 getOrDefault 上受控异步 future 的 pending/ready/空结果/异常回退（不冒称实测外网账号服务）。其后音量夹具绕过了正常 resolve 前置步骤而 NPE，已补；Zoomify 首次被误选的最新 Kotlin helper（需 loader>=0.19.5）拦在 loader，改钉 Zoomify 2.15.2 本身 tag 的 FLK1.13.8+kotlin2.3.0（需 loader>=0.16.9），不改产品 loader0.19.3。

新音量专项走实际 SoundEngine.calculateVolume，GUI专项走真实私有 renderItemCooldown + LRT TAIL 注入并存三帧；这两项仍待通过。开发 `PackClientSmoke` 在 `ceaee475` 全构建再次 worldBuilder.create 超时，保留失败；本次只加 pre-context tick/screen/overlay/level/player/集成服tick 与墙钟诊断，不重试、不改 Fabric1200tick 限制，用于 R6 定位。


### R5 实机收口，R6 ACTIVE

`2e96c14b` 的 platform / platform-zoomify 均通过；`65010ffc` 再补原生 MouseHandler Pre 的窗口句柄防护，专项发送外窗口按键确认不能关闭 refit。最终 [编译45759114248](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45759114248) / [全构建45759114240](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45759114240) success；发行 [45759114325](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45759114325) 的原版 [139704770795](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45759114325/job/139704770795)、Zoomify [139704770601](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45759114325/job/139704770601) 均 success。本体 SHA-256 `9e76482c2093f73c00cff5a0497bbd9f28e32e82be7b7af06470e5c7d47f6b65`。

- INPUT：native keyboard/scancode/mouse press、repeat/release、screen veto、refit 开/关/外窗口隔离、卸弹冲突、ALT 优先。
- SKIN：名字/空名、pending 非阻塞、异步 ready 切换、空结果/异常回退；仅受控 future，不冒称外部账号服务可用性。
- AUDIO：真实 SoundEngine PLAYERS/BLOCKS=1%、半到期50.5%，UI/music/master/ringing 豁免、移除后恢复且不双乘。
- GUI：真实 LRT cooldown TAIL 半进度512/1024像素，独立类别/到期0。
- ZOOM：不装时无可选类链接且 FOV 恒等；安装 Zoomify2.15.2+1.21.11 时真实物理键/public divisor/主投影/松开恢复全通过。测试依赖固定 FLK1.13.8+kotlin2.3.0 / YACL3.8.2，接续另钉已验收 SHA256，拒绝默默换组合。

### R6 第一轮共享行为审计（进行中，尚未验收）

- 不沿用旧表“缺13个文件”：当前 donor 同路径实际缺11个，GunInputHandler 与 LevelRendererReceiverLightMixin 已出现；平台替代详见差异表。
- 共享源码复核发现额外真实缺口：AmmoConfig 的硬度/忽略队友/忽略实体项缺失；AmmoBlockConfigRead 只有定义无 loading/reload 调用；DestroyGlassBlock 仍按玻璃类判断；BlockRayTrace 未消费 tag policy，bullet/explosion 未消费实体忽略；已接实际调用链及 Cloth 面板/中英键，新增两项成品专服正反测试，未提前记通过。
- 同批补 GunFireEvent 的 Fabric 优先级阶段、动画 exit deadline 的世界 epoch 失效、公共枪名 blank fallback；新增原生事件逆序注册验证、真实 TCP disconnect 后 deadline 失效断言。
- 继续待核实：CameraSetupEvent 未消费已有 AdsFov authored 通道、配件 variant FOV；第一人称 tracer 的出生帧 offset/quaternion 冻结；modifier UI 的 RPM/弹速/射程/小数精度与可选字段消费者。文件同名不代表这些已对齐，后续必须逐项接完与实测。
- 开发建世界超时在 `65010ffc` 全构建没有重现，但不宣布已修复。前轮实际诊断 integratedTicks=0 持续约60秒；本轮增加失败前 server/worker thread stacks 与 Checks 长诊断，不重试、不放宽1200tick限制。根因尚未定位。

### R6 共享消费者接续（未验收）

`94270755` 编译 [45760609909](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45760609909) / 全构建 [45760609950](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45760609950) success；本体 SHA-256 `beb468f4b852b5e2ec531acf6141462cf2e44a6aeb9e27fafa2a3cefe3cfa309`。发行 [45760609912](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45760609912) **13/14**：原有光学、receiver、平台均通过，none 已通过真正 TCP/旧档恢复/动画 epoch 断线失效，但新增专服 policy 测试工程缺 FCAP compile-only，尚未运行这3项，不能称20项GameTest成功。`01137d6c` 只补测试consumer编译类路径，不改变产品依赖。

本轮继续接回已定义却无消费者的 AdsPath 分轴/AdsFov 双通道、scope variant views/FOV 和 adapter mount offset；后坐力/枪口动画订阅成功的 GunFirePostEvent，而不是先于最后取消投票的 GunFireEvent。保留 public legacy 调用并检查 canceled。新增客户端实际 viewport callback（5进度点）、实际 first-person positioning XYZ/恢复、真实物理扳机的末位视觉取消/成功对照；客户端视觉 veto 不假装取消服务器独立结算。补 modifier 面板消费者的全局弹速倍率、已修改射程、爆头/伤害小数、burst BPM、独立准确度字段识别与四行计数，逐项测试真实返回给面板的数据。

另恢复 LazyJsonDataManager 的线程安全命中缓存、lazy parse/reload apply 共用锁与独立 ID 快照，增加阻塞旧worker与reload并发的JUnit对照，不允许旧parse覆盖新generation。开发客户端新增3个独立fresh-world生命周期门禁，任一失败即失败，不是重试、没有放宽Fabric限制；保留失败前线程栈诊断。上述新接续尚待目标编译与实机结果。

后续仍需复核/接完的已知共享差异：第一人称 tracer 的出生帧 offset/quaternion；外部模型高/低精度异步 bake 与reload代数、shell display override；动画粒子在真实posed rig提交的消费者；雕像owner兼容存档/异步身份。不得因非同路径表只剩平台替代就将这些同名类行为漏项标绿。

### R6 2026-11-26 接续：真实回归拦截，不提前关闭

`7442792f` 的编译/全构建/发行均因 `ClientAttachmentIndex.getIdleScopeHeightAdjust` 缺失失败；`003bdda2` 补上 POJO → index → first-person consumer 及空 views 数组防护。该提交编译成功，但全构建与发行被后续 push 取消，不记作通过。

`42cbc3b6` [编译45762450080](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45762450080) success；本体 SHA-256 `1713a402d0e49b7200f6e35bb1ae55060f59ff8786f8d1794ad1311abe212283`。成品 [45762450092](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45762450092) 的 platform / platform-zoomify 均 success：实际注册回调的 ADS 双通道五进度点、第一人称 XYZ/恢复、末位 client-pre veto 与独立服务端 ammo=7/6、modifier 面板精度/倍率/准确度 parser 均通过。两环境实际提交 tracer 顶点跨 live yaw/pitch/roll 与 muzzle 变化保持不动，legacy Euler setter 使 quaternion 失效对照通过。雕像单件副本、disk/update-tag 组件往返、替换/重复 drop 只有两件、三个方块原生 facing rotation 的专服断言也通过。

但 none 专服 **20项中2项失败**，不能宣称通过：common config 首次加载未初始化 block policy；entity policy 控制组因 tick-zero teleport 与真实 HitboxHelper 速度补偿冲突而无命中。本轮把 FCAP loading/reloading 注册移到最早 ConfigRegistry.register 之前（注册时即同步加载），保留首次无手动 init 断言；静止命中夹具初始化 oldPos，不改变产品补偿算法。

全构建 [45762450088](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45762450088) 失败在第一世界成功之后、第二世界之前的 PackClientSmoke:44：测试错误要求 memory connection 清空公共 cache，而生产明确保留与 integrated server 共用的 cache。修正门禁为 native level/connection 真正断开，以及下次 join **必须换成新 index 对象**；仍要求三个独立 fresh-world，不重试、不放宽超时。该失败不同于之前 worldBuilder.create / integratedTicks=0 超时，不能拿来宣布旧问题已修复。

接续模型补丁（待编译/目标实机）：世界/FIXED/ground 从独立 external high/LOD 的 revision-aware DisplayBake 获取，不再等待 loader 或借用可动画 hand rig；GunRigBaker 统一 hi/LOD/mesh 分派，移除旧 mesh 尾注入避免重复加载；reload 退役已创建 GPU 模型，geo parse 换代 map 防止旧 worker 回写；shell override 走 native extraction，shell event 只投递给 ready 外部 rig。新增五项 DisplayBake JUnit 与真实 display probe 的独立 rig 断言。继续保留 Java21 / intermediary / native aperture，版本不变。

仍未完成：Target owner 的 profile/UUID/patch/legacy migration 与受 generation 保护异步身份（先从本版 javap 取证，不照搬26.x resolver）；native muzzle effects/materials/定位器的实际消费者；posed-rig animation particle 复核；共享文件审计与全矩阵最终验收。

### R6 模型/策略实机结果与下一批原生消费者

`c54ab282` 编译 [45763696040](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45763696040) success；全构建 [45763695988](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45763695988) 的 build/JUnit、产物审计、原专服 GameTests 步骤 success，但开发客户端第一次建世界再次超时。成品 [45763696078](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45763696078) 仍未整体通过；display 已实际验证独立 external rig，platform / Zoomify、receiver / Iris、原版 optics 等通过。产品 SHA256 `3326d7c833def042fb2e5696c55b431b715c23b8c5de1fca50203968c4ec9ecc`。

none 的 entity policy 已通过真实最近命中/穿透命中/队友伤害开关/实体忽略/爆炸正反例；block policy 已越过首次加载断言及破坏/硬度/开关检查，失败在射线 pass-through 夹具穿出很小的原生测试室、碰到周围实体边界块。本轮把正反射线端点都放在待测方块内部，另直接断言 tag 已绑定；不改产品 ray traversal。新一轮仍需通过，不能把19/20记为20/20。

开发超时栈这次捕获到 server=WAITING `ThreadingImpl.enterPhase(PHASE_SERVER_TASKS)` / `MinecraftServer.preRunTasks`、其它worker空闲，integratedTicks=0。属于已定位到 Fabric client-test barrier 的证据，不足以宣布产品无问题或框架有bug；只追加 phase/parties/arrived/serverTasks 只读诊断，保持三世界门禁、原超时、零重试。

下一批（待目标编译/实机）：
- 目标 JAR javap 已证实 ResolvableProfile CODEC/Dynamic/skinPatch 与 Services.profileResolver。标靶接回原生 profile、OwnerProfile alias、旧 Owner 复合/flat UUID/name、自定义名JSON迁移；服务端 lookup异步完成后仍须 owner/level/generation/removal 一致，保留动态UUID给客户端皮肤cache。新增两个成品专服测试，身份服务回复受控，不声称外网账号服务验证。
- MuzzleFlashRender 接上 NativeMuzzleEffects、MuzzleAttachment、SurfaceLights.muzzleLocator、FlashMaterials；继续使用本版 shouldClipViewmodel 的 native depth aperture，不引入26.x stencil/mask。新增真实物理射击 → posed locator 的 flash/smoke 独立开关对照，并检查 canceled client-pre 不发出两层效果。
- BedrockGunModel.submit 补回 AnimationParticleDispatcher 调用，不能只让类存在；新增实际 held-gun controller 上的原生粒子关键帧、真实posed-submit消费、只发一次与missing locator无替代的对照。
- 持久化静态检查识别 ValueInput.getString Optional 形式；仅允许显式 `tacz:read-only-key:old->new` 且有真实旧read/新write的迁移，不全类豁免。四个Python回归保护 Optional/合法迁移/无目标写入/未标注不对称；本地13unit+2baseline+4recipe和严格持久化检查通过。

R6 仍 ACTIVE，不发版、不改版本；新消费者、开发三世界稳定性与完整共享行为验收尚未完成。

### R6 `c1570e66` 消费者回执（编译/专服/平台通过，开发稳定性仍失败）

- 编译 [45765549810](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45765549810) success。本体 SHA256 **`7fa0ccd804c8aa02241528aef16221daffc50e77cd27b060bef0927fae1bd57f`**。
- 成品 none [139725473821](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45765549848/job/139725473821) success：**22/22 intermediary GameTests**，包括 ammo block/entity policy、事件阶段、statue/rotation、target_profile_roundtrip 与 target_identity_generations。之前2项弹药失败已实际转绿；TCP/目录→ZIP/reload/rejoin、旧档/部件/弹药组件保存与转换验收仍通过。
- platform [139725473517](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45765549848/job/139725473517) / Zoomify [139725473654](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45765549848/job/139725473654) success。脚本强制包含11种平台回执，新增实际物理射击→posed muzzle 的flash/smoke独立开关（ammo6/5/4）、canceled visual pre无效果；关键帧→posed model→原生粒子一次消费与missing locator无替代也在必须回执内（GitHub单step notice数量会截去末项展示，不是跳过断言）。
- 全构建 [45765549899](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45765549899) 的开发初次建世界依然失败。诊断 phase4421（mod4=SERVER_TASKS）、registered3/arrived2/unarrived1、serverTasks=true；但这个快照在END_CLIENT_TICK取得，本来就在正常屏障边界，不能凭它认定屏障死锁。本轮新增独立50秒只读watchdog，观察Render/Test/Server/Netty的同时栈与phaser计数，不触碰屏障、不重试、不增大超时。

接续还发现供体 `MeshGpuBakeBudgetPerFrame` 被漏掉，现有实现误把LRU缓存容量当每帧烘焙额度。本轮补独立1–64/默认4配置、Cloth与中英键及实际tryReserveBake消费者，保留旧带int签名为同一闸门的兼容入口；新增真实门禁在LRU1/16×budget1/2/7的精确计数对照。**该接续尚未编译/实机验收**，19项mesh TOML/Cloth/语言键静态齐平通过。

共享审计尚不能收尾：BedrockModel/BedrockRenderSnapshot 还没有消费已有 IlluminatedMaterials 的body材质划分与 ViewmodelFlash.gain 的中性反射重放；快照发光light还硬编码fullbright，未消费ILLUMINATED_REAL_SKY。另有ocular inflate配置/装配链缺口需要与已验收native depth aperture/reticle互测，不能用供体整文件替换已验收R1–R3实现。上述是已确认待办，不记为平台替代或已实现。


### R6 `461cc6fc` 回执与本体材质接续

- 编译 [45767016402](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45767016402) success；完整开发 build [45767016448](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45767016448) success，包含三次独立 fresh-world 门禁。本轮未复现旧超时，**不等于已找到并修复间歇原因**；独立只读 watchdog 保留。
- 发行 [45767016376](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45767016376) 原生 platform [139730236106](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45767016376/job/139730236106) / Zoomify [139730236198](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/45767016376/job/139730236198) 均 success，12种强制marker含独立 MESH_BUDGET；LRU容量1/16 × bake额度1/2/7已由真实consumer通过。其余最终状态待记录。本体 SHA256 `71d00bc3c07eff82613e54e548c56fd7330c420a5eeb3adc13df39c813f04599`。
- 补录：上一轮 `c1570e66` 的发行45765549848最终 **14/14 success**，不是仍待定；其开发45765549899失败仍保留。

下一批本体材质实现已接线、**尚待编译及原生/Iris目标实机**：BedrockModel的不可变快照按authored illuminated位保序分批，发光子树与独立captureSubtree均保留祖先发光/real-sky；ViewmodelFlash.gain在extract时冻结，中性反射仅重放非发光body，不替换未知特效材质；rack preview保留unshaded材质。即时路径补双buffer overload及Functional wrapper发光位，另修供体BedrockPart只传递unshadedConsumer却未在发光节点切换consumer的问题。新增PORT_BODY_MATERIAL强制合约在两种成品optics客户端执行真实延迟任务与即时buffer/functional hook正反例；不以这组数值合约代替已有镜片/热像像素矩阵。ocular inflate链与共享音频语义仍需继续审计，R6不关闭、不改版本。

补充同批接线：BedrockAttachmentModel 的非ADS与有序ADS housing也消费上述材质划分；APERTURE_COPY包装在识别材质之后添加，depth-copy周期每次BACKUP只允许一次aperture尝试（包括失败），防止第二个body材质批次把自身深度误当目镜。仍是本版原生depth aperture，不移植stencil；必须重新跑两环境完整光学矩阵。新增合约同时检查包装后的材质身份。两项本地verify脚本因缺少named MC缓存均未运行成功，不能写成通过；无本地JDK，目标编译/混淆运行交CI验证。

进一步音频审计发现SoundData解析器/元数据已有，但SoundPlayManager实际入口仍主要取Identifier、丢弃authored volume/pitch/random参数，且GunSoundInstance缺少非tacz_sounds的原生event回退；这不在已关闭的R5耳鸣volume/input矩阵覆盖范围内，作为R6真实后续缺口保留。

### R6 本体材质编译拦截与 ocular 烘焙接续

`864f87bf` 的编译45768902651、全构建45768902685及发行45768902627均因新增测试替身的`order(int)`返回类型不兼容失败，尚未进入目标材质实测。1.21.11 `SubmitNodeStorage.order`是具体`SubmitNodeCollection`返回类型，不能以26.x的宽接口override；`14ec0094`改从SubmitNodeCollector接口代理捕获延迟任务。合并机器编译报告后的`1dfffa89`正在重验（compile45769237044 / full45769237322 / distribution45769237041），不把失败记成成功。

补录`461cc6fc`发行45767016376最终 **14/14 success**；编译/全构建也success，mesh预算已闭环。它不证明后续材质SHA通过。

工作区下一批ocular烘焙链：保留原生深度孔径，对NEW/LEGACY loader增加只作用于attachment ocular和后代的额外cube inflate，默认开启/0.0005模型单位；轮廓环和分划不扩大。新增TOML/Cloth/中英键、config-loaded保护、非有限值/损坏parent链保护。JUnit覆盖三种精确命名、编号、后代、负例与环；PORT_OCULAR_BAKE成品门禁实际构造两代attachment模型，对禁用/0/启用、authored inflate相加、box/per-face与旋转cube逐分支读真实顶点。既有原版/Iris完整PIP/热像/分划像素门禁仍强制，不用包围盒代替孔径裁剪。**尚未编译/实机验收**，不关闭R6。

剩余共享消费者审计新增确认：attachment SoundData字段也仍是旧Identifier map；rangefinder/impact/ammo-counter tooltip capability消费者仍缺失。mesh attachment的hi/LOD加载并非缺失，由本版ClientAttachmentIndexMixin分别接线，不能为同路径差异重复加第二个loader。后续仍需核实这些消费者，不将同名文件或供体回执视作验收。

`1dfffa89`编译45769237044、完整开发45769237322已success；发行原版/Iris在新增reflection合约中失败（optics139737590532 / optics-iris139737590754）。已定位另一处测试替身错误：代理比较反射method.getName()=="order"，字符串不会随Loom改为intermediary名，导致order(1)重放落入未观察的原生storage。现改为参数/返回接口签名判定，不使用映射名字符串，保留4次材质提交/6次cube写入的严格断言；不放宽产品判据，不把两环境失败记成通过。后续目标重验包含独立ocular marker。

### R6 authored audio接续（实现待验收）

音频实际入口改消费SoundData（gun/attachment的字符串与对象都保留兼容getter），在播放前解析volume/pitch/random；native sound-event回退不再强行重定向到tacz_sounds，直接OGG仍优先，旧构造签名继续委托兼容。保留本版缺失reload/inspect aggregate静默让动画片段接管的行为；本地普通/动画声音按tracking选项选择listener-relative或entity-bound，显式3P射击/命中策略不被覆盖；空owner提前返回。

新增2项JUnit及原生/Zoomify的PORT_PLATFORM_SOUND_DATA强制marker：实际SoundManager解析的volume/pitch/random、原生harp event与包内真实svd_dry_fire.ogg路径、本地tracking正反、实际draw consumer元数据、可选aggregate不误报、attachment双JSON形式。尚未编译或目标运行，不借旧R5的耳鸣音量矩阵证明这个新消费者。52e76e0e仍在验证前一批body/ocular，音频未混入该SHA。

### R6 attachment tooltip消费者接续（待编译/实机）

补回builtin、显式flash/smoke正反倍率、thermal sensor、rangefinder、impact prediction与scope-only ammo counter的真实tooltip消费者；ClientAttachmentIndex从当前display的text_show读能力，ammo marker在查询时走当前Language而非缓存翻译。补11个原先缺失的中英标题键。新增PORT_PLATFORM_ATTACHMENT_TIPS，原生/Zoomify都实际构造ClientAttachmentItemTooltip，检查正反/neutral/非scope/changed-display、builtin开关及加载后的翻译，而非只测试几个helper布尔值。与此同时unshaded world material按供体进入Iris EMISSIVE_ENTITIES，保留shadow原材质与HAND/HAND_TRANSLUCENT分类。新改动仍待目标回执；52e76e0e的结果不包含它们。

### R6 `52e76e0e` 部分回执与开发加载诊断

编译45770219491 success；成品原版optics139740800792 success，新增PORT_BODY_MATERIAL与PORT_OCULAR_BAKE均实际通过，现有分划/热像/移动PIP像素矩阵也通过；Iris最终状态仍待记录。本体SHA256 `1b1fb2c3f02b3d77dcb755e58d5d988be768a2d131381d856e7b97eb85dc1bfd`。

完整开发45770219489再次初次worldBuilder.create超时，不能宣称稳定性通过。独立50秒观察确实取得phase3617，后续tick诊断phase4409，registered3/arrived2；相位仍推进，因此不是停死在单个Phaser相位的证据。Render线程独立样本在native framebuffer blit，Test线程在Fabric waitTick，Netty闲等；仍不能仅凭采样认定产品根因。CI reporter把6条长stack拼进3.8KB一条annotation，实际丢失后面的Server样本；本批改为独立phase/Server/Render/Test各一条优先annotation，并保留10-error上限，新Python正反例验证长stack末尾未丢。观察器优先这3线程+1Netty，另在正常client tick只读server primitive readiness/tick/pause标志，继续不触碰Phaser/Semaphore、不重试、不放宽1200tick门禁。

共享方法差异复核又确认两个尚漏的render配置：GunHurtBobMultiplier（默认0.1；普通持枪受伤倾斜；不能动gun-hit专用路径、方向旋转或死亡roll）与DisableThirdPersonOffhandAndHotbarGunRender（默认false；只隐藏背负/副手与非选中快捷栏，不隐藏主手）。先给native API探针补本版GameRenderer.bobHurt字节码，取得1.21.11真实调用/切片后再写注入，绝不复制26.x OptionsRenderState字段目标。TargetMinecart的档案创建是既有TargetOwnerProfiles原生unresolved缓存替代，不是需要照搬新的resolver；命名/清名/改名仍需补目标测试。headshot Gizmos与SmokeCloud getLightColor等为本版原生API替代，不按方法名缺失恢复错代API。

52e76e0e分发最终回执：45770219504 **SUCCESS 14/14**，optics-iris job139740801014亦SUCCESS；同一JAR SHA-256 `1b1fb2c3f02b3d77dcb755e58d5d988be768a2d131381d856e7b97eb85dc1bfd`。原生及Iris均记录PORT_BODY_MATERIAL与PORT_OCULAR_BAKE实际提交/顶点验证（Iris 1.10.8/Sodium0.8.14/Complementary r5.8.1固定哈希，不外推别的组合）。这不覆盖随后audio/tooltip改动，也不消除dev完整测试进世界超时。

下一增量：恢复DisableThirdPersonOffhandAndHotbarGunRender默认false、Cloth与en/zh，门控只在HumanoidOffhandRender入口，不碰主手层。PlatformCarriedGuns使用真实玩家/AvatarRenderState/枪械item-model解析捕获native submissions，分别测副手、未选中槽位、运行时隐藏与selected槽不重复提交；恢复玩家/显示数据/config；强制platform及platform-zoomify回执。待编译与runtime，不能据此关闭R6。

TargetMinecart额外补验证（production未改）：实际本版entity.getOwnerProfile及TargetMinecartRenderer.extractRenderState分别覆盖无名→命名→改名→清名→空白，缓存稳定性、render state不残留旧头像、pick物品保留新名字；复用现有受控native lookup pending/ready/exception/empty测试，不依赖Mojang网络也不声称在线解析已实测。待下一轮runtime。

fd77336a compile-check 45772306081 SUCCESS（audio/tooltip/独立thread诊断编译通过；生产runtime尚在跑）。实际1.21.11 bobHurt字节码已在该轮日志取得：Options.damageTiltStrength()偏移154，ordinary Z rotation176；之前有death Z及方向Y，之后有反向Y。按此native调用切片接回GunHurtBobMultiplier默认0.1/range0..1、Cloth/en/zh与donor方向公式，严格本机第一人称活玩家主手AbstractGunItem。gun-hit路径保持HEAD独立处理，窗口改用现有GunTime.isWithin避免未标记/倒退时间戳误入。JUnit覆盖方向/符号/0/.1/1；PlatformHurtTilt实际调用经生产mixin后的native bobHurt，比较pose矩阵：普通倍率×vanilla Damage Tilt、非枪/第三人称/死亡roll/gun-hit独立路径。强制两种platform runtime marker。新hook/probe仍待编译及runtime，本机15 Python测试PASS、diff-check PASS；两个native validator本机缺named MC jar，均BLOCKED而非PASS。

共享GUI差异复核发现GunSmithTableScreen缺donor空手持筛选状态恢复：已用本版GuiGraphics/drawString接回多行提示、灰色空面板、取消手持筛选按钮，空状态不画制造按钮。PlatformSmithFilter用真实同步默认ammo_workbench和手持配件触发无匹配、截图，并执行真实Button callback验证清除筛选后恢复配方/选中项；强制platform/platform-zoomify marker+两张截图。已有en/zh翻译复用，未动26.x extractor API或资源。该增量待编译/runtime；MAX_AMMO_COUNT缺失只是ClientAmmoCount.MAX_DISPLAY_COUNT统一上限的既有替代（含long防溢出），无需重复恢复常量。

语言恢复差异：donor GunPackLangCompat及aggregate get/list包装在本线均缺失。移植词法恢复器（Java21可用，无26.x类），只对gun-pack lang原地内存副本恢复/诊断，合法字节不改、嵌套/null/array值丢弃、缺逗号尽量救回。**不照搬donor“26.3全局翻译加载中断”宣称**；1.21.11语义是保留原本会被跳过资源里的可用文本。未改默认资源/磁盘源文件。JUnit三组；PlatformLanguages安装独立故意缺逗号的测试枪包，走真实aggregate及native I18n loader，核对三条有效翻译、嵌套不泄漏、原文件未被改写；两组platform强制marker。待编译/runtime。

旧data目录差异：本线LegacyGunPackRecipeMigrator已覆盖recipe迁移，但donor RecipeCompat的其余七种别名未替代。新增纯路径LegacyGunPackPaths，PathPackResources与aggregate的get/list均接回loot_tables→loot_table、advancements→advancement及tags的blocks/items/entity_types/fluids/game_events单数目录；不碰assets、tacz_tags、recipe格式迁移和分层规则。单包current优先，跨包higher legacy仍胜lower current，get/list去重一致。JUnit测边界/前缀；PlatformLegacyPaths真实目录/ZIP filesystem的native资源API测七类的回退/优先级/去重/客户端隔离；两组platform强制marker。待编译/runtime。

fd77336a最终回执：compile45772306081 **SUCCESS**；完整build45772306080 **SUCCESS**；remapped分发/TCP45772306096 **SUCCESS 14/14**，含platform/platform-zoomify强制PORT_PLATFORM_SOUND_DATA与PORT_PLATFORM_ATTACHMENT_TIPS、native及Iris optics。JAR SHA-256 `4c464833043fd553deacb6d2793d2ff0c05d36fc17f82ddc17ae601a61b691b0`，4176默认包文件/5库、Java21/intermediary。由此audio/tooltip及Iris世界unshaded分类有本轮运行回执；不能外推本地后续carried guns/hurt tilt/smith/language/path增量。完整build本次成功不等于间歇性startup超时已修复，保留诊断与开放项。

组合镜二次审计确认原生实现仍把views[]=1/2当作固定sight/scope角色；HAMR/Vudu旧探针无法暴露AMG10“view1对应ocular_scope_2”的反序缺口。已按donor几何语义在rest pose将scope_view[_N]光轴匹配到ocular，再由ocular suffix选division，同时lens启用条件取该ocular真实scope角色；不改native aperture/材质/reticle顺序，不抄stencil。扩展两环境ReticleGroups：真实HAMR/Vudu/AMG10每组的目镜/分划/活动孔径及lens角色，Standard8x view1保持高倍；新增强制PORT_RETICLE_GROUPS。待目标编译/完整optics runtime，R1–R3之前回执不代替本增量验收。

34c8e2b7初步回执：compile45773853386 SUCCESS；platform及platform-zoomify均SUCCESS，carried guns/native hurt poses/cart rename/smith empty-state+截图/native malformed I18n/7类目录与ZIP别名的强制marker均通过。其余分发尚在跑。完整45773853065失败在JUnit（不是world-load阶段），现有annotation只显示expected true/false、不含case/source line；原始日志与artifact下载被环境出站限制EOF，不能猜失败方法。本轮补XML reporter独立标注classname#testcase及项目栈帧（Python16测试PASS），下一轮取得精确失败后修复，不跳过JUnit。

进一步消费者差异：TextShow已有rangefinder配置及RangefinderReadout，但PapiManager未注册%rangefinder%、TextShowRender没传config，实际文本无法测距。接回双参数placeholder表/兼容旧getter及addPapi、只运行文本中出现的placeholder；PlatformRangefinderText实际抽取TextShowRender任务，验证authored max_distance0得到range:---、无config/字面%兼容与未引用callback零次。这里只证明text consumer接线，不冒充world entity/ballistic距离验收。

Loose attachment variant：held-gun路径已有，但AttachmentItemRenderer丢了variant lookup与replace/overlay消费者。接回本版同签名路径，replace不被base LOD覆盖、overlay叠加、GUI slot图标保持原状。新增独立测试枪包的replace/overlay定义与原创1cube模型，PlatformLooseVariants通过实际tagged index+真实renderByItem的native材质提交测FIXED/远GROUND/GUI/missing fallback；不修改默认包。两项新增platform/Zoomify强制marker，均待下一轮目标编译/runtime。

34c8e2b7分发最终 **45773853107 SUCCESS 14/14**，本体SHA-256 `cac8c0045413e18322de8b0b57790978bde8c2415fd1e36afb7ad741b7d80c47`。本轮完整build的JUnit源码复核发现确定的旧断言冲突：GunPackOverlayTest.layersAndLifecycle在目录/ZIP两例都要求`{"ok":"kept","bad":[]}`的bad数组原样穿过aggregate，而新语言恢复已明确改为合法primitive对象（platform实机已通过）。更新成更严格的确切对象内容、get/list一致，并另从原overlay层读取原字节保证磁盘/ZIP未重写；保留全部lifecycle/recipe/priority断言。不把旧failed run改记success，下一完整构建仍必须重新通过；增强case/source reporter继续保留以确认是否还有别的失败。

c8b02be1三工作流均编译拦截：组合镜restPose辅助方法漏import org.joml.Matrix4f（compile45775251885/full45775251841/distribution45775251840，未进入新optics/rangefinder/variant runtime，也未重验overlay JUnit）。补明确import，保持native API不变。

共享粒子消费者又确认BulletHoleParticle只调用了surface spawner，却没消费gun/ammo enable_bullet_hole，Provider缺sound及rate limit、target/air/碰撞有效性提前闸门，构造器对坏ID仍parse抛异常。现接回ImpactStyle安全解析、gun优先ammo开关/颜色、仅decal遵循life开关而不压掉fragments/step、每tick每surface-sound去重并按world重置；表面偏移按donor每粒子冻结0.005..0.01。保留本版getBlockModelShaper/QuadParticleRenderState包名/Layer.TERRAIN及已修复六面基变换和相机相对重载。新增PlatformImpacts原生ParticleEngine/SoundManager只读观察：实际Provider覆盖开关优先级/zero-life仍fragments/sound同tick一次下tick重启/坏ID/target-air提前返回/offset范围；两平台强制marker。Python六面数学+接线检查更新为六项PASS（新增冻结offset沿法线，不改变UV），16Python测试PASS；新粒子native路径仍待编译/runtime，未声称六面视觉截图已验收。

06c11af2：compile45775636194 SUCCESS，full45775636212已越过JUnit进入runClientGameTest但第一world再次1200tick超时（serverTicks0/paused/connection none、Fabric phase4421与异步3665，仍保留原限制不重试）。distribution45775636276完成12/14 SUCCESS，包括native+Iris optics强制PORT_RETICLE_GROUPS；platform两项探针编译失败：native SoundInstance API应为getIdentifier而非getLocation，已按既有AbstractSoundInstance javap更正；产品本身已编译。此轮jar SHA256 261e48df2a82008ed1715d71d4ea3334cb37aa34c74f9e4814ecf4808dd40b52。补ImpactColorView remapped accessor和实际gun/ammo color及fragments config/distance断言（不反射native命名字段）。下一compile只读dump native bootstrap bytecode以诊断world加载，不修改Fabric同步器。

R6继续审到Cloth消费者：17项render+3项gun-anger+1项compat-warning只有TOML键、面板入口丢失。逐entry接回donor同值/同范围/同默认/同saveConsumer，保留本版account-aware crosshair与18mesh键。两platform探针增加原版Cloth21.11.153（无Cloth的none/display等路径不变），检查21entry current/reset和14boolean原生widget→screen.saveAll→consumer→client/common实际TOML，另开编辑器并通过原生ESC/discard验证未保存编辑不泄漏；这是进程内save/reopen，不称restart实测。待新CI编译与target-runtime。

80b042b9：compile45777778756 SUCCESS，full45777778741 SUCCESS（含overlay修正后的JUnit、server与原时限三次独立fresh-world）；distribution45777778761最终12/14，platform+Zoomify未进入impact/Cloth：variant测试新建Bedrock fixture缺visible_bounds_offset，引发loader明确NPE，补完整bounds而不改生产loader。其它12项SUCCESS（含native/Iris optics）。SHA256 cb92ba0e1416a4e20ba06cda959b55890799f380ebbf8b71c9b52cb0fcbbf8bb。新增离线fixture schema检查防同类错误；Python18PASS/六面6PASS。

R6 client-lifecycle续审确认：ClientIndexManager.clear缺RangefinderReadout.clear；reload缺原生CreativeModeTabs.CACHED_PARAMETERS失效（同features/permissions/registries换包不触发原版刷新）。接回前者并用本版remapped静态Accessor完成后者（不照搬26.x AW）。PlatformIndexLifecycle准备真实sample→reload清空、临时common type fixture改变→同参数creative内容重建且后续cache仍命中；只在Fabric测试停驻的客户端任务里改fixture并finally还原。ShellRender队列界限按donor从>128改>=128，140次真实addShell验证128/FIFO。另发现本版无LayerRenderState.setLocalTransform时只给全模型special submit传了matrix，sprite early return同时漏properties；改为两分支共用properties，sprite只冻结texture+matrix并在CPU合成（不保存entity/stack/高模）；SpriteTransformProbe验证实际四角/非恒等平移缩放/相机对齐/冻结矩阵/调用pose不变，离线检查update接线。以上新增native检查仍待CI，不据此关闭R6。

剩余审计候选（尚未确认缺陷，不能写修复声明）：WorldEmitterManager在BEFORE_ENTITIES提交world Snowstorm时未显式带ctx.matrices，与已验证NativeMuzzleEffects适配不同；需目标渲染空间证据判定是否真的缺view变换，不能仅据donor API差异盲加旋转。开发world-load本轮SUCCESS不等于间歇停滞根治；native tickServer字节码证实无玩家即paused且tickCount可为0，故先前0tick并不能证明服务端没运行，后续需观察pendingConnection/packet进度而非修改phaser或延长时限。

26de505e：compile45779243434 SUCCESS/full45779243442 SUCCESS；distribution45779243475最终12/14，其余12（含native/Iris optics）SUCCESS，platform两项已实际通过rangefinder text、loose variants及impact消费者（含颜色/fragment独立性/step按tick）。两平台阻塞于新增Cloth断言：本版IlluminatedRealSky默认true而donor UI硬编码false；修为读本版spec.getDefault，不篡改本版已有默认。顺手对齐idle-release面板30..1200与本版既有spec（donor UI的1..600不适用于此版），补原生slider边界检查；不改本版范围。发现21项共42个label/tooltip在两语言均缺，补donor已有40项及自编真实天空光两项，并按本版默认/范围和fragment独立语义适配。离线两locale全键检查+native I18n断言防只出现翻译key。清理已被运行证据推翻的旧thermal/光影rerender“尚未移植”注释。此轮jar SHA256 88e013b58063830d99a63f1b64832f30cf9c11eecae5f2588f7d2814b99c41d7。Cloth后面的sprite/index检查尚未执行，不称已过。

新增PlatformSnowstormSpace：在实际BEFORE_ENTITIES事件用当帧world matrices/ModelView/camera审计独立真实WorldEmitterManager的deferred quad中心，三组yaw/pitch，提交后改变simulation/anchor检验冻结；当前只新增审计，未根据猜测改生产world变换。另给开发world timeout诊断补pendingClient与server connection listener/channel/queue/packet字段快照（仅named gametest，非remapped产品），避免把getConnection()==null/paused0tick误读为没有bootstrap进展。测试不修改网络队列、phaser、原1200tick限制或自动重试。

### R6 5ce0b94f 全链回执（2026-11-26）

- compile-check **45780983016 SUCCESS**；完整build **45780982980 SUCCESS**；remapped分发/TCP **45780982969 SUCCESS 14/14**。
- JAR SHA-256 `be842ad3732a02442bca79ab65dee03181d6cbb0171758081560467ed599c2a9`，Java21/intermediary AW/refmap，4176有效默认包文件/5库。platform job139776080632、platform-zoomify job139776080760均SUCCESS，脚本强制26个PORT_PLATFORM marker各恰好一次及screenshots，不是只凭进程退出码。
- 因此Cloth21项current/reset/14布尔save+真实TOML/reopen/discard、slider30..1200与native语言键、sprite实际四顶点/冻结local transform/调用者隔离、index reload真实range sample/vanilla creative缓存与128shell FIFO均有本轮原生及Zoomify回执。不能扩大为进程重启后Cloth保存证明。
- SNOWSTORM_SPACE三组真实BEFORE_ENTITIES坐标/延迟模拟状态变化验证均通过。此前“没用ctx.matrices可能漏world transform”的猜测**未被本版事件实际坐标合约证实**；不为追求donor形状盲目改生产变换。此门禁是事件当帧quad中心/冻结检查，不是烟雾全FaceCameraMode像素矩阵。
- native/Iris optics与receiver仍均SUCCESS，光影组合范围保持既有固定哈希，不外推Voxy或别的光影。
- full本次3次独立fresh-world成功；仍不称间歇性bootstrap超时根因已修。pendingClient/server transport诊断已编译，等待真实失败时观察，保留原超时/零重试。

再次复核donor-only十个Java路径：MinecraftAccessor由本版原生调用/已注册accessor替代；ShapedRecipeMixin/RecipeCompat由本版LegacyGunPackRecipeMigrator+LegacyGunPackPaths处理；TargetSkinResolver由TargetOwnerProfiles及native skin cache替代；IrisScopeFragment/Thermal三个accessor由本版native depth aperture/RenderTypeStateAccessor/RenderSetupTextureAccessor/TextureBindingAccessor替代；GuiGraphicsExtractorMixin换本版GuiGraphicsMixin；SoundInstanceVolumeMixin换实际SoundEngine音量hook。不能按文件缺失把错代API复制回来。共享方法清单余项复核含GUI extract→render、native Gizmos爆头盒、Fabric1.21.11 ingredient接口、input集中注册→原生各key注册、ConfigPersist实际Fcap写入及scope/mesh本版helper合并；同名符号存在与否仍只是线索，不作为完成证明。

验收日志可见性改进：GitHub单step最多展示10个notice，旧platform每项各发notice导致后半新回执在Checks UI被静默隐藏（断言仍全执行）。改为保留完整stdout并按3500字符分批、转义workflow字符；新增离线27项末尾可见及超长/特殊字符不截断测试。下一轮目标重新验证；不反写旧run数值、不捏造未取到的Snowstorm centerError/rootIdentity详细值。

### R6 字段级漏项接续（待目标验证）

不能只凭共享方法清单结束审计。再做donor public/protected常量字段与全树消费者核对，发现SyncConfig仍缺CrawlMaxUpAngle默认45/0..90、CrawlMaxDownAngle默认30/0..90及UseHipFireInaccuracyWhileAirborne默认false；MouseHandler仍硬编码45/30，InaccuracyType缺空中优先分支。接回本版同步spec，保持donor默认/优先级：仅开关开启且离地时使用move/stand，忽略ADS/趴姿/蹲姿；关闭或落地仍保留原先优先级。趴姿pitch仍仅持枪且非真实游泳；只拦超限向外转，允许向内恢复，90/90为原版pitch范围。

MouseHandler也仍走旧scope索引手拆zoom，没有消费已恢复的variant倍率/安全负ZoomNumber取模。改取已有IGun.getAimingZoom，避免第二套查询。唯一余下public常量VIEW_TUNING_KEY是本版ViewTuningKey.KEY/原生queued-click注册替代；DTO的@SerializedName字段对照无缺项。这些静态符号/JSON字段扫描只是找漏线索，不冒充逻辑等价证明。

PlatformStance新增强制marker：真实client/server实体的2开关×2落地×3pose×2ADS×3移动阈值=每侧72组合；实际经mixin合并的mouse wrapper在4组limits/9个pitch/2个方向共72组合，续调真实LocalPlayer.turn，另测游泳/无枪豁免及真实安装tagged variant、ZoomNumber=-1选择8倍的灵敏度。按参数类型/返回类型查找handler，不依赖remap方法名字符串；这是merged-handler/原生turn测试，不声称物理cursor callback。恢复entity/config/手持/同步ADS及插值旋转字段。测试包zoom只改独立fixture，不改默认枪包。新实现仍待编译/目标运行。

491f0f3d的分批notice实机已显示此前被Checks隐藏的详细回执：native Snowstorm三组rootIdentity均true；centerError分别0、9.902267E-7、5.840039E-7。支持保留当前world提交变换，不往hand路径混入无根据的world矩阵。该轮compile45828158644/full45828158699均SUCCESS，分发最终待记录；不覆盖上述新stance源改动。

491f0f3d分发45828158652最终 **SUCCESS 14/14**（含optics-iris）；与5ce0b94f相同本体SHA-256 `be842ad3732a02442bca79ab65dee03181d6cbb0171758081560467ed599c2a9`。分批Checks notice在两platform均成功，保留全量门禁，不再丢后半回执。

### R6 成品UI语言及真实TCP配置验收接续（待编译/目标）

资源键复核发现主mod语言缺51个donor已有键：48个advancement标题/说明（含6个兼容旧ID）及3个creative sidebar文本。实际默认23项advancement中有42个标题/说明缺失，之前catalog解码/触发通过不等于显示语言正确。补两语言原文，不改默认包数据；新增离线默认23项/46component+3sidebar全键检查；PlatformLanguages从实际产品JAR读23个display.title/description，再走native I18n验证49个键非空/非原key，强制PORT_PLATFORM_UI_LABELS。静态全源码Component.translatable/I18n.get直写tacz/lrtactical键复核无其它缺项（两个动态key前缀不算漏项）。donor剩余7个未合入键仅未使用的旧VBO/Sable面板及旧thermal提示，不为不存在的本版UI硬加空功能。

为避免仅静态声明或测试端直接set误称“服务器同步”，TCP夹具在独立server-seed/defaultconfigs/tacz-server.toml设置12/7/true三个非默认值，client不写这三个配置、不发测试payload；真实FCAP连接后读值，两次join均必须满足，none套件还跨原有服务端保存/停机/从world复制重启。新强制PORT_SERVER_CONFIG，none/JEI/REI均执行；probe项目只增加FCAP compileOnly以读取已有产品配置类型，不给生产新增依赖。该增量待下一目标编译与真实TCP，不能借PlatformStance直接设内存的matrix声称已通过远程同步。

3d2da078当前compile45830221409/full45830221413 SUCCESS；两个platform（139944733982/139944733875）均已实际PASS新增STANCE，分发最终仍待最后Iris optics。Python22项PASS、diff-check PASS；UI/TCP新增量不在该轮SHA内。

3d2da078分发45830221480最终 **SUCCESS 14/14**；本体SHA-256 `93d3f638e3cf8bd8060695c3bdfec1022e45ec3c3e2f5a818328172c74438b5f`。因此本轮新增三个sync配置定义/stance两端分支/merged mouse wrapper与tagged variant灵敏度全部通过本轮native+Zoomify，native/Iris optics回归同过。不把此回执扩大成下一轮server-only非默认配置的TCP同步证明。

f1f47537：compile45832954469 SUCCESS；full45832954527再次FAIL于首个worldBuilder.create原1200tick，不能称完整全绿。新增bootstrap诊断实际取得双方Connection存在、pendingActions0，client/server listener分别ClientConfigurationPacketListenerImpl/ServerConfigurationPacketListenerImpl，均未断连；因此已明确卡在configuration而非根本没连上/pack play sync。phase独立3641→tick观察4385仍推进，server paused/tick0并非根因证明；render样本本次在原生PostPass blur draw（此前在blit，均不可据此归因）。下一诊断只读双方configuration task/queue/future及Fabric网络in-flight/queued/invalid标量，并在Checks优先单列两条configuration状态，避免IO worker堆栈挤掉关键证据；不动同步器、限制或队列。

该轮两platform均SUCCESS，新增UI_LABELS确认实际产品23×title/description+3sidebar共49键走native I18n。JEI与REI的真实FCAP TCP双join非默认12/7/true均SUCCESS。none套件首join通过、停机复制后第二join配置断言FAIL，未冒称restart通过。核对**实际pin v21.11.1-mc1.21.11/fabric commit e78e07380844d4c573f947347402cfe3830de511 的1.21.11/Fabric/.../ServerLifecycleHandler.java**：默认server配置在instance/config，world/serverconfig（单数）仅显式文件覆盖；旧测试只复制world/tacz，遗漏保存过的config，不是本轮产品sync失效的证据。修restart supervisor同时复制实际config，不再从初始defaultconfigs重播；离线测试验证global config、world override、world状态和包编辑均为独立字节副本；修正ConfigPersist内错误的自动world/serverconfigs注释（实现原本就不接管SERVER，不改实现）。none实际停机/重连仍须下一轮重验。

f1f47537分发45832954441最终 **13/14**：仅none上述重启夹具遗漏config失败；其它13项（含optics-iris139954214890、两platform、JEI/REI非默认配置双join）SUCCESS。本体SHA-256 `98b326b73028be8cf55288fbe99118e300c9da4ca635d0c5c4937c1e9d939a83`。不忽略none失败，修正夹具后仍须重新完整验收。

### R6 eebcb7a0 最新完整回执（2026-11-26）

- compile-check **45835740961 SUCCESS**；完整build **45835740955 SUCCESS**；remapped发行/TCP **45835740995 SUCCESS 14/14**（原生及Iris optics/receiver、platform/Zoomify全过）。
- 本体SHA-256 `ff98d21ec027852356dd56219f397c9339ed706b4336b49160e9706256ca3db0`，Java21/intermediary AW/refmap，4176有效默认包文件/5库。
- none job139964000113实际PORT_SERVER_CONFIG及PORT_PERSIST同时通过：独立专服进程关停、从真实config/world/tacz字节副本启动新JVM，第二次TCP join仍收到server-only非默认12/7/true；原有组件库存/挂载/实体持久化与目录→ZIP /reload、两次2-part同步不退化。JEI/REI非默认配置双join同过。
- 两platform强制STANCE/UI_LABELS及之前全部消费者门禁通过；默认23项成就46个标题/说明+3sidebar在native语言loader有实际回执；中英离线全键检查通过。Python23项、弹孔方向数学6项、默认包graph4176文件、block drops/persistence静态守门均PASS（这些离线检查不替代目标运行）。
- full本次三次fresh-world成功，新增configuration任务/队列/网络标量诊断编译通过；但f1f47537已实际复现的间歇性configuration-stage超时**仍未根治，不因后来一次SUCCESS而抹掉**。新增失败路径的task/future快照要等下一次真实复现，不能捏造未运行的数据。

**当前边界：R1–R5保持已关闭；R6仍ACTIVE，剩余明确开放项为间歇性dev configuration bootstrap定位/修复及任何后续审计发现。未升级版本、未发布，也未声明整体parity完成。**
