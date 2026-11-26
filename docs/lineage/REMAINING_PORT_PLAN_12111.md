# 剩余移植的单项闭环计划

2026-11-26；目标仍为 MC 1.21.11 / Java 21 / intermediary / 深度孔径。行为参照 `26.1.2@d12a3f2`，交接参照 `26.3@c5d536b7`。

用户要求：按类型分步，但不要在单项实现中途切换项目。

## 固定工作规则

每次只推进一个活动项。**代码接入 → 目标版编译与单测 → 专项成品运行 → 回执与边界记录**四步齐全才标 DONE；失败就留在该项修复，不用“先做下一项”回避。发现不适用的 26.x API，要证明本版等价路径，不能靠删除注入或关闭功能过关。

## 顺序与关闭条件

| 顺序 | 项目 | 关闭条件 | 状态 |
|---|---|---|---|
| R1 | 混合镜内分划 | 活动 division 统一提交；墨线/发光分层；父子根去重；共享可见性与矩阵恢复；无孔径保守 fallback；失效孔径不泄漏大面；原版/Iris 成品专项测试及现有矩阵回归 | **DONE — `3515cf98`** |
| R2 | 热像 | posed model 捕获、纹理 cutout、材质与场景深度、黑白名单、关闭/切世界清理；原版与 Iris 中遮挡/透明面/降级的像素正反例 | QUEUED |
| R3 | PIP 运动、镜片与近场 | 对齐供体 ADS/运动/切视图行为；真实帧比较、孔径边界与遮挡；不能仅重跑静态孔径测试 | QUEUED |
| R4 | 动态光 receiver 与材质 | 核对 1.21.11 entity/block submit 时序、世界表面响应、发光材质不重复受光；正反实帧 | QUEUED |
| R5 | 输入与平台小差异 | 键/鼠/scancode/卸弹冲突、可选 Zoomify 装/不装、名字皮肤异步/失败、LRT GUI/音量；逐项证明本版替代或移植并实测 | QUEUED |
| R6 | 全量收尾 | 对全部共享代码做行为差异复核，关闭非同路径表里的每个真实缺口；全构建、成品双端/旧档/查看器/光影回归；记录已知外部限制与许可，不冒称第三方协议已修复 | QUEUED |

R1 的专项测试必须增加自己的分划断言，原有 `PORT_OPTIC` 孔径回执不能单独作为 R1 通过证明。每项结果继续写入本文件与主执行账本，供后续会话从唯一 ACTIVE 项继续。

## R1 收口回执（2026-11-26）

**R1 已完成本项闭环，下一项为 R2；尚未开始热像代码施工。**

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
