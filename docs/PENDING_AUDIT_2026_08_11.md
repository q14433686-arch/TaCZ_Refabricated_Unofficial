# 全量待办 / 技术债 / 兼容缺口盘点（2026-08-11）

> 范围：`src/main/java` 全量 grep（TODO/FIXME/XXX/HACK + 中文「暂未/未实现/禁用/回退/待」语义）+
> `compat/` 全目录过目 + docs 已知事项对照。每条给出：现状 → 玩家影响 → 建议处置。
> 本清单是**静态盘点**；案例①~⑦的诊疗史见 `COMPAT_AND_ROADMAP.md`，不重复收录。

## A. 玩家可见的上游功能缺口（按影响排序）

| # | 项 | 现状（代码位置） | 玩家影响 | 建议 |
|---|---|---|---|---|
| A1 | **枪身/镜身文字显示（TextShow）未实现** | `TextShowRender.java:77` TODO：`Font.drawInBatch`/`MultiBufferSource` 已移除，需用 `submitCustomGeometry` 或 `Font.prepareText` 重写 | **默认枪包在用**：ak47 / minigun / rpk 的 display + scope_mk5hd / scope_standard_8x 都声明了 `text_show` —— 26.2 上这些文字（弹药计数等）**完全不渲染** | 值得做。`submitText` 系 API 在 26.2 存在（SubmitNodeCollector 有 submitText/缊 OrderedSubmitNodeCollector.submitText，字节码已见），工作量中等 |
| A2 | **LRTactical 部分集成** | 发布页已知事项在列；`CustomItemCoolDowns.java:79` TODO(网络层)：冷却完成不发 `SCustomCoolDownMessage` | 服务端判定正确；**客户端物品栏不显示冷却遮罩**（flash shield 等手雷类） | 补一条冷却同步包即可，工作量小；与防弹盾等「部分系统」缺口分开评估 |
| A3 | **LRTactical 手雷音效缺失** | `ThrowableItemEntity.java:189,380` TODO(音效)：弹跳音 `GRENADE_BOUNCE`、死亡音未移植 | 纯观感 | 顺带 A2 一起做 |
| A4 | **光影开启时镜内裁切走安全回退** | `IrisCompat`（sulkan 检测外默认 false，但保守回退路径仍挂）；发布页已知事项在列 | 开光影时镜内可能看见镜筒内壁（保守回退），不崩 | 维持。要做就得把掩码 pass 挂进 Iris 的 hand pass 批边界——风险收益比一般，等用户声量 |
| A5 | **多部件实体命中近似** | `EntityKineticBullet.java:388` `MaybeMultipartEntity.of`：PartEntity 父级解析被注释 | 打末影龙等多部件实体的部件时，核心归属按部件自身记账；普通生物无感 | 低优先；Fabric 无原生 PartEntity，上游决定如何映射前先不动 |
| A6 | **镜内枪口烟雾不裁** | 案例④收尾时定案：烟雾属世界粒子 pass，裁剪需侵入全局粒子着色器 | 开镜开火镜片里仍见烟雾 | 维持不做（上游是否裁未核实，风险高） |

## B. Mod 兼容桩（等外部条件，非我方债务）

| # | Mod | 现状 | 解锁条件 |
|---|---|---|---|
| B1 | **Accelerated Rendering** | 全面 no-op（`ARCompat` 强关 + `AcceleratedBeamRenderer`/`ARCompatImpl` 去接口）；激光无加速路径——但普通路径工作正常 | AR 发布 26.2 构建后恢复 implements 与开关 |
| B2 | **KubeJS** | `KubeJSGunEventPoster` 桩 | KubeJS 支持 26.2 |
| B3 | **Controllable（手柄）** | `ControllableCompat` 桩 | Controllable 支持 26.2 |
| B4 | **Sulkan（Vulkan）** | `IrisCompat.getIrisVersion` 注释：Sulkan 无公开等价 API；`shouldDisableScopeMaskUnderShaderPack` 对 sulkan 有特判入口但检测未实现（恒按非 sulkan） | Sulkan 出公开 API；目前行为=保守回退，安全 |
| B5 | Optifine / ShoulderSurfing / CarryOn / ImmediatelyFast / JEI / REI / Zoomify / PAL | 均为检测或浅兼容，**在役正常**（JEI/REI 配方 PR#22 已修；PAL 案例⑥已闭环） | — |

## C. 死代码 / 陈旧标记（无玩家影响，可择机清理）

| # | 位置 | 说明 |
|---|---|---|
| C1 | `MuzzleFlashRender.renderMuzzleFlash/doRender` + `:77` TODO | 旧即时渲染路径的死代码（主链路 `extract()` 在役正常）。TODO 描述的是死路径，建议删代码或改注「历史路径，勿启用」 |
| C2 | `BeamRenderer:61` collector==null 分支 TODO | 实际调用方（`BedrockGunModel:317`、`BedrockAttachmentModel:643`）都传 collector，**不可达**；5 参便捷方法没有调用方。可把 TODO 改为「无 collector 即不画（现状如此）」 |
| C3 | `RenderHelper.enableItemEntityStencilTest/disable` | 已 no-op 化（Vulkan/无模板缓冲），**无调用方**（仅在注释里被提及）。可整段删除 |
| C4 | `SoundEngineMixin:78`、`ChannelAccessHandleMixin:54`「暂时用不到」 | mixin 空档位，无害；如确认永久不用宜删注入点减面 |
| C5 | `GunModelSubmit`「暂未实现 TranslucentSubmit」 | 注释自述「留待确认是否真的需要半透明排序」——至今无人目击需要，保持观察 |
| C6 | `ServerMessageLevelUp:56`「枪械升级逻辑完成后解封」 | 上游封印的功能块，升级系统上游 1.21.1 也是半成品——跟进上游 |

## D. 悬置的技术审视项（有意不做的决定，记录在案）

| # | 项 | 定案 |
|---|---|---|
| D1 | 案例③ 目镜八边形镜框内圈被凸包掩码啃边 | **用户挂起**；`ScopeMaskHullFill=false` 秒退 |
| D2 | 案例⑦ 炮弹/炮烟第一人称从眼位出发 | **已回退为上游原生行为**（用户定）；重启入口=射击当帧采视模世界矩阵，勿复用曳光锚定缓存向量 |
| D3 | PIP / 第二世界镜内渲染 | 暂停（文档在案；上游本无 PIP，放大靠 FOV 变焦） |
| D4 | `CommonNetworkCacheEvent` 单人存档缓存豁免 | 「尚未修复也未验证」注释在案：多次基于推理的误改史，**改动前必须先加日志取证** |
| D5 | 案例⑤ 激光改色 | 结案：光影包限制（无彩色自发光），LaserDebug 探针保留备查 |

## E. 上游自带 TODO（继承债，不动）

`LivingEntityShoot:270`（耗弹方式）、`EntityKineticBullet:439`（暴击 flag 透传）、
`ModernKineticGunScriptAPI:843`（Lua enum 直调）。这些等上游定，跟上游对齐即可。

## 建议行动顺序

1. **A1 TextShow 重实现**（默认枪包可感知的功能缺失，收益最实）；
2. **A2+A3 LRTactical 冷却同步 + 手雷音效**（小工作量、观感闭环）；
3. C 类清理选一个顺手轮次带掉（C1/C3 是删码零风险件）；
4. B 类全部等外部版本，A4/A5/A6 与 D 类维持现状。
