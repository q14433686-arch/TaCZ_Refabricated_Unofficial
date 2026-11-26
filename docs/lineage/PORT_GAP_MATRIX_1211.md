# 1.21.11 → 26.1.2 功能差异：整合后的重新盘点

2026-11-26；总状态 **OPEN**。本表更新的是当前接收工作，不是早期「缺 213 个文件」的快照。

参照：`26.1.2@d12a3f22943cc50632f9f5455306f0b71d00650e`；接收基线 `aac343a`；已整合的同版本适配来源 `bb12ad2`。完整执行与本分支验收见 [执行账本](PORT_1_2_TO_1_21_11_20261126.md)。

## 非同路径盘点（R2 施工中剩 13 个 Java 路径）

**同路径缺失不等于功能缺失；反之，同名也不等于行为相同。** 以下列出供体有而接收树没有的路径，避免再用旧的文件缺口数字衡量进度。

| 供体路径（相对 `src/main/java/`） | 接收情况与需要核对的边界 |
|---|---|
| `cn/sh1rocu/tacz/mixin/accessor/MinecraftAccessor.java` | 平台/映射差异；需按调用者核对，不引入 26.x 字段 accessor。 |
| `cn/sh1rocu/tacz/mixin/common/ShapedRecipeMixin.java` | 有本版 `LegacyGunPackRecipeMigrator` 与 aggregate 边界兼容；普通工作台必须经原版配方注册表与真实菜单验收。 |
| `cn/sh1rocu/tacz/util/GunPackLangCompat.java` | 文档明确是 26.3 语言加载异常链路的过滤器；本版保留原生失败兜底，不照搬改变语言资源原字节。 |
| `cn/sh1rocu/tacz/util/RecipeCompat.java` | 本版 `PathPackResources` / `LegacyGunPackRecipeMigrator` / `UniversalPackConverter`，不能未经 codec 验证复制新版本 schema。 |
| `com/tacz/guns/client/input/GunInputHandler.java` | 本版按各键类派发，使用本版 `KeyEvent` / `MouseButtonEvent`，含独立卸弹键和 Alt+Reload；输入边界仍需实机矩阵。 |
| `com/tacz/guns/client/renderer/TargetSkinResolver.java` | 本版 `renderer/other/TargetOwnerSkins`；仍需核对按名字异步加载与失败默认皮肤，不能把有替代类视为已实测。 |
| `com/tacz/guns/compat/iris/IrisScopeFragment.java` | 本版使用独立的深度孔径与 late/final overlay 路径；需在目标 Iris 上测真实片元与时序，不搬 26.x 掩码实现。 |
| `com/tacz/guns/mixin/client/LevelRendererReceiverLightMixin.java` | 粒子/动态光已有本版消费者；receiver light 的 exact 行为尚未关闭。 |
| `com/tacz/guns/mixin/client/ThermalRenderSetupAccessor.java` | 已按 named 1.21.11 JAR 核对；复用 `RenderSetupTextureAccessor`，热像消费者已接入，专项运行仍在修复。 |
| `com/tacz/guns/mixin/client/ThermalRenderTypeAccessor.java` | 已核对本版字段并复用 `RenderTypeStateAccessor` / `TextureBindingAccessor`，不重复注入同字段；专项运行待通过。 |
| `com/tacz/guns/mixin/client/ThermalTextureBindingAccessor.java` | 已核对本版字段并复用 `RenderTypeStateAccessor` / `TextureBindingAccessor`，不重复注入同字段；专项运行待通过。 |
| `me/xjqsh/lrtactical/mixin/client/GuiGraphicsExtractorMixin.java` | 本版有 `GuiGraphicsMixin`，GUI API 不同；需保持本版 Render/Submit 时序。 |
| `me/xjqsh/lrtactical/mixin/client/SoundInstanceVolumeMixin.java` | 音频 API/调用者需单独复核，不能仅移除 Mixin 来声称等价。 |

## 不能从现有矩阵推断的行为

- `optics` / `optics-iris` 保留孔径深度测试，并在 R1 后新增混合分划 contract、活动组与暂停帧逐材质消融测试（已通过）。这仍不证明热像、PIP 运动重投影、全部镜片像素和近场预设对齐。
- Iris 测试安装的是脚本里钉住的发行组合；不代表所有 Iris/Sodium、Unbound 光影或开发依赖组合受支持。
- 控制器夹具使用 SDL2 虚拟手柄，不是实体设备手感/驱动测试。
- REI 给物测试有意分别刻画仅客户端安装、双端安装与显式 vanilla 命令；JSON 往返改变 NBT 数字类型等已知差异不能说成严格全组件相等。
- 默认资源有效图仍有来源快照继承的缺引用，清单记录这些缺口；“精确来源散列通过”不等于“所有引用完备”。

## 已补的接收缺陷

同版本来源中的 `ItemNbtUtils.updateRegistries` 没有调用点。本次已接入双侧 `TAGS_LOADED`，在 recipe 初始化前绑定世界动态注册表；退出/停止清理。原有带附魔的实体配件安装、往返、停服重开断言全部保留，另加入开发 GameTest，禁止测试自己手工绑定注册表掩盖漏接事件。`faebdb47` 已在本分支 10 组成品矩阵及完整 build 通过，含 17/17 服务端 GameTest 和换 JVM 存档恢复；这是本分支实测，不是继承来源分支回执。

后续补充：已按 26.1.2 结构将 Zoomify 可选类型隔离到 `ZoomifyCompatInner`，并给成品字节审计增加基线入口不得直接链接可选类的正反例。安装 Zoomify 后的实际缩放 API 矩阵仍未关闭。

## R1 已独立关闭

`ReticleGeometry` 已移植；`3515cf98` 的原版/Iris 分划专项和完整十组矩阵全部通过。当前非同路径清单由 18 减为 17；详见 [剩余分步计划与 R1 回执](REMAINING_PORT_PLAN_12111.md)。后续先做 R2 热像，不把 R1 的像素计数用于证明 R2/R3。


## R2 已接线、尚未验收通过

`365a71d0` 新增 capture/material/scene-depth/body-submit 四个同路径类并接通镜片链，故当前未出现的 Java 路径为 13 个，**不代表 R2 DONE**。`863219b7` 的成品专项发现：本版 cached HUD projection 原先只镜像 buffer 实例，没有记录返回切片，`ScopeLensFrame.current()` 无法识别手部投影；`13e929c7` 补切片镜像后，`77e8c406` 将测量移到 batch 消费，`484da3e1` 修正暂停测试后的 ADS 输入重建；原版专项已通过，Iris 仍 heat=0。当前 `a7cfda59` 用确切世界 view 参数继续修复 Iris。先前 R1 和全构建绿灯不能掩盖该失败。详见 [活动项记录](REMAINING_PORT_PLAN_12111.md)。


### R2 最新专项回执

`08b1bf0b` 的 canonical float-depth copy 已令 Iris heat=1122；仍 white=0 的剩余故障由 `53d55a39` 的精确 Iris 1.10.8 final-overlay gate 修复。该 SHA 编译/全构建与原版/Iris 专项均 success，包含新增独立光学轮廓 ≥83% 填充（原版 992/992）、遮挡/透明面/列表/禁用恢复。尚不关闭 R2：`c1f38fb2` 正跑 16/36 格、三 yaw、classic/PIP/narrow 的 18 组逐帧正反例；完整回执及未验证边界见 [活动计划](REMAINING_PORT_PLAN_12111.md)。未发布、未改版本；R3–R6 仍排队。


### R2 闭环，当前 R3 ACTIVE

`ab452920` 编译/全构建/发行10组成功；`f1b800d8` 追加黑热/铁红与 unavailable/camera 生命周期，成品10/10成功。两环境各18组16/36格×三yaw×三场景路径通过独立光学轮廓填充及实墙零热断言。后者开发建世界超时已单列，不写成通过；产品字节与前者一致。回执、实际产品散列和限制见[阶段计划](REMAINING_PORT_PLAN_12111.md#r2-收口--r3-接续2026-11-26)。R3立即接续，不借R2静态轮廓测试证明移动PIP/镜片。


### R3 光学闭环；R4 接续

`b2ad0276` 原版/Iris 各16组同帧运动PIP、missing mask/reveal floor与镜外宽遍地形/实体/天空全部通过，发行10/10；开发建世界超时独立保留到R6，不报全构建绿。当前R4：目标JAR已证实稳定submitEntities/submitBlockEntities，`1834fc62`接通同步receiver作用域与此前未接线的rack面/slot光照；编译/全构建成功，实际光照专项仍在修测试夹具资源，未验收通过。详见[阶段计划](REMAINING_PORT_PLAN_12111.md)。
