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
| R4 | 动态光 receiver 与材质 | 核对 1.21.11 entity/block submit 时序、世界表面响应、发光材质不重复受光；正反实帧 | **ACTIVE** |
| R5 | 输入与平台小差异 | 键/鼠/scancode/卸弹冲突、可选 Zoomify 装/不装、名字皮肤异步/失败、LRT GUI/音量；逐项证明本版替代或移植并实测 | QUEUED |
| R6 | 全量收尾 | 对全部共享代码做行为差异复核，关闭非同路径表里的每个真实缺口；全构建、成品双端/旧档/查看器/光影回归；记录已知外部限制与许可，不冒称第三方协议已修复 | QUEUED |

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
