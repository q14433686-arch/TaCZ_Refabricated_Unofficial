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
