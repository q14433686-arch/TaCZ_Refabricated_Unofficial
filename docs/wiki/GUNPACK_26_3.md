# 枪包 Wiki 补遗：移植到 Fabric 26.3 时哪里不同？

这是**本移植的文档**，不是 TaCZ 官方 Wiki 页面。原作 [README](https://github.com/MCModderAnchor/TACZ#readme) 指向 [TaCZ Wiki](https://tacwiki.mcma.club/zh/)；其中已有实际的配方过滤、低模、槽位图、枪口火焰和配件章节。下面链接到核对过的[中文 Wiki 源稿][tacwiki]，只补充 **`1.2.0+fabric.26.3.R1` 开发构建**的差异。[直接 Fabric 上游](https://github.com/Sh1roCu/TACZ-Refabricated/blob/356bdf9d48f2225bac6ceb56adc5576a418517a8/README.md) 的安装提示针对 1.21.1，不是本线的依赖清单。只想进游戏玩的读者请看[玩家指南](../PLAYER_GUIDE_26_3.md)。

## 先分清：包的版本、加载层、同步数据

- 目录和 ZIP 都从实例的 `tacz/` 加载；`gunpack.meta.json` 位于包根。不要把它与 [`assets/<命名空间>/gunpack_info.json` 的标题、作者、许可][info]混淆。`dependencies.tacz` 比较核心版本 **1.2.0**；`+fabric.26.3.R1` 不参与比较。通过这个检查并不代表脚本、模型、配方都适配。
- `overlays.entries` 按 **Minecraft 版本**选择，后面的匹配层优先于较早层，覆盖层优先于包根。服务器安装或重载枪包后，客机按收到的完整数据重建索引；这不把服务器私有的模型、脚本当客户端资源分发。两端使用匹配的包和模组构建。
- 遇到旧配方或物品数据，可用管理员命令 `/tacz convert universal "Example Pack.zip" check` **只生成报告**；需要时把 `check` 改成 `export`。产物在 `tacz-converted/`，不会改写原包或替你安装。先在备份实例测试，不要同时加载原件与导出件。见[导出范围、冲突及不支持项](../GUNPACK_CONVERSION_26_3.md)。

- 另一个容易混淆的是**内置资源再导出**：启用自动导出时，模组版本或内置源资源指纹变化会刷新导出目录；原有目录先备份到实例 `tacz_backup/<目录名>/`。这是内置资源的更新机制，不是 `convert universal` 的目标 `tacz-converted/`，也不是给第三方包解密。自己的包请另建目录并保留备份。

## 配方过滤：为什么服主看得到，客机却空白？

上游的[工作台配方][recipe]定义**做什么**；[配方过滤器][filter]定义**哪张台能列它**。匹配的是配方完整 ID（例如 `tacz:gun/ak47`），不是物品注册名。26.3 默认包可参照[实际配方](../../src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/recipe/gun/ak47.json)和[默认台的过滤器](../../src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/recipe_filters/default.json)。原版工作台的配方书不读这个过滤器。

例如，在自己的测试枪包中写 `data/tacz/recipe_filters/default.json`，仅排除自己的一个测试配方：

```json
{ "blacklist": ["example:gun/demo"] }
```

不同枪包的**同 ID 过滤规则合并**：白名单取并集、黑名单优先；合并后白名单为空或缺省时，表示不以白名单限制。不是把多个包里同 ID 的普通配方也拼起来。同一个包的根文件和选中 overlay 仍**只取优先层**。服务器将只带黑名单的过滤器发给客机时，即使下发 `"whitelist": []`，也不应变成「全部拒绝」。

**验证：** 服务端与客机换同一测试构建，再进服。空手开枪械、配件、弹药台，分别找 AK47、ACOG TA31、9mm 的**制作**页；被明确排除的 ID 则不应出现。[台子实图](../../build-reports/visual/44627993715-tacz_hand_filter_cleared.png) · [区分 received、decoded、displayed 与原版合成](../investigations/ORDINARY_CRAFTING_26_3.md)。

## 外观：作者低模、槽位图和掉落图标

先看上游[模型与贴图][model]、[低精度模型][lod]和 [HUD 与 GUI 中的 `slot`][slot]。本线请以[实际 AK display](../../src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/display/guns/ak47_display.json)为例：`lod.model`、`lod.texture` 是一对；模型在本线默认包的 `geo_models/`，`slot: "tacz:gun/slot/ak47"` 对应 `textures/gun/slot/ak47.png`，与枪模贴图、右下角 HUD 剪影不是同一项。

| 作者提供了什么 | 26.3 客机实际画什么 |
|---|---|
| 有可用 `lod` 模型和纹理 | 沿用包的外显模型选择；不因异步烘焙尚在进行就把作者低模当成缺失。 |
| 无可用 LOD，但 `slot` 纹理存在 | 只对**真正掉落的枪**，到 `DroppedGunSpriteDistance`（默认 8 格；正距离随瞄具放大调整）才换成面向相机、受当地光照的槽位图。不自动生成低模。 |
| 无可用 `slot`，或玩家把上述键设为 `-1` | 沿用原 3D 路径。未写出的 Bedrock 立方体面不再提交重叠顶点；作者明确写了面的零尺寸 UV 仍有效。 |

验证选**没有可用低模**的 display，在同一位置比较 `-1`、`8`，脚边可比 `-1`、`0`。默认 AK 自带低模，不适合作负例。[3D 与青色测试图标两帧](../SCREENSHOTS_26_3.md#第三人称与地上的枪)；这个回退不改变手持、展示框或挂板。需要 `poly_mesh` 的包见 [TML 支持边界](../MESH_LOADER.md)。

## 枪口：分别检查焰、烟、枪手曝光和墙面补光

[上游枪口火焰][muzzle]把 `muzzle_flash` 分为旧贴片 `default` 和粒子 `renew`。本线[无旧贴图的 Glock 17](../../src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/display/guns/glock_17_display.json)也是 `renew` 示例。不要为了出焰给它硬加旧贴图。复核要分四层：

1. **枪焰**：`scale` 改焰的大小；配件显式 `flash_hider` 按比例缩放，不是一个「消焰器必定完全熄灭」的开关。未写倍率时消音回退与显式零不同。`first_person_flash` / `third_person_flash` 可分设；显式 `null` 关闭对应层，别用缺省代替关闭。
2. **烟和动画特效**：`smoke_scale`、`initial_speed_scale`、`smoke_particle_count` 与烟的方向控制独立于焰；客户端关 `RenderMuzzleSmoke` 不关焰。自定义定义放 `assets/<命名空间>/particle_definitions/`，可由动画 `particle_effects`、定位组与 `pre_effect_script` 触发。世界烟与手部粒子分开，支持的对端可看到跟踪角色的事件。效果组件、Molang、材质和预算见[26.3 的实际实现与限制](../investigations/SNOWSTORM_RUNTIME_26_3.md)、[SBM 上游的定义示例][particle]。不用安装 SBM 的 1.21.1 JAR，也不要假设每个 Bedrock 扩展都受支持。
3. **第一人称曝光**：`renew` 的 `flash_light_level`（默认 15，零为不补光）影响短暂的枪／手曝光，消焰、视角配置仍生效。`LegacyMuzzleLight` 是玩家**默认关**的旧包估算开关；它只为符合条件的旧贴图火焰或省略整段声明的爆炸发射器估算，不替显式 `muzzle_flash: null` 或普通无焰枪造粒子。
4. **场景表面**：`MuzzleSurfaceLight` 和 `ExplosionSurfaceLight` 是另两个**客户端配置**，不属于 display JSON，默认关闭。前者作用在发射处，后者取已收到的真实爆炸中心。较新 26.3 构建除墙地外也处理[使用世界光照的实体、装备与陈列物](../investigations/SURFACE_LIGHT_26_3.md#实体装备和陈列物)，原本全亮的部分不再叠亮；不改变世界光照或 `renew` 曝光寿命。不额外画镭射落点。[两套后端的单发原图](../investigations/SURFACE_LIGHT_26_3.md#实际游戏帧不是效果图)与[配件倍率的测试图](../investigations/MUZZLE_ATTACHMENTS_26_3.md#像素与证据边界)用途不同。

## 配件、瞄具、脚本还看哪里？

| 上游已写的主题 | 本线的对应做法与验证 |
|---|---|
| [槽位适配器][adapter] | 安装座与普通配件不同。先查枪的 `allow_attachments`、`adapter/` 标签、对应槽位和座的 `allowed_attachments`；选中座后不能再把枪原来的直装列表当准入规则。生存改装由服务端结算，虚拟 OEM 件不能拆成库存。[默认座的索引实例](../../src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/index/slot_adapters/riser_rail_mid.json)。 |
| [枪身文字][text]、[HUD 与 GUI][slot] | 枪包 `text_show`、`%ammo_count%`、测距数字和镜内标记属于该 display；不要把玩家的个性准星画进瞄具。`scope_lens` 和热源遮挡可在经典变焦／PIP 分开验证；`tacz:thermal_imaging/blacklist` 优先于 `whitelist`。[镜片与文字原帧](../SCREENSHOTS_26_3.md#看镜内看枪口看墙面)。账号 HUD 与分享码是[本移植的玩家设置](../investigations/HUD_RETICLE_26_3.md)，不在包的 `slot` 中配置。 |
| [曳光][tracer]、[声音][sound]、[手柄震动][pad] | 按枪包声明的间隔检查世界中曳光的位置；声音可为字符串或含响度／音高参数的对象，不等于生物仇恨半径。手柄反馈要用匹配的可选 Controllable 与 Framework，已测虚拟设备回调，不承诺全部实体设备。 |

脚本作者另见[游戏会话时间与系统时间](../GUNPACK_CONVERSION_26_3.md#脚本里的时间)、[自定义弹药源 API](../AMMO_SOURCE_API.md)。物品栈、配件变体、同步后的索引以**当前客户端与服务端**为准，不把旧 Forge／Fabric 二进制当 26.3 接口。若单个包出问题，请带资源 ID、包版本和最小示例到[本移植 Issues](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/issues)，不要向上游报本端的扩展行为。

[tacwiki]: https://github.com/MCModderAnchor/tacwiki/tree/8379d85ae22461f1cdb4dac329b897586f637d62/docs/zh/gunpack
[info]: https://github.com/MCModderAnchor/tacwiki/blob/8379d85ae22461f1cdb4dac329b897586f637d62/docs/zh/gunpack/03_pack_info.md
[recipe]: https://github.com/MCModderAnchor/tacwiki/blob/8379d85ae22461f1cdb4dac329b897586f637d62/docs/zh/gunpack/60_recipe.md
[filter]: https://github.com/MCModderAnchor/tacwiki/blob/8379d85ae22461f1cdb4dac329b897586f637d62/docs/zh/gunpack/block/03_recipe_filter.md
[model]: https://github.com/MCModderAnchor/tacwiki/blob/8379d85ae22461f1cdb4dac329b897586f637d62/docs/zh/gunpack/gun/display/01_basic.md
[lod]: https://github.com/MCModderAnchor/tacwiki/blob/8379d85ae22461f1cdb4dac329b897586f637d62/docs/zh/gunpack/gun/06_lod.md
[slot]: https://github.com/MCModderAnchor/tacwiki/blob/8379d85ae22461f1cdb4dac329b897586f637d62/docs/zh/gunpack/gun/display/03_hud.md
[muzzle]: https://github.com/MCModderAnchor/tacwiki/blob/8379d85ae22461f1cdb4dac329b897586f637d62/docs/zh/gunpack/gun/display/05_muzzle_flash.md
[adapter]: https://github.com/MCModderAnchor/tacwiki/blob/8379d85ae22461f1cdb4dac329b897586f637d62/docs/zh/gunpack/attachment/slot_adapter.md
[text]: https://github.com/MCModderAnchor/tacwiki/blob/8379d85ae22461f1cdb4dac329b897586f637d62/docs/zh/gunpack/gun/display/10_text_show.md
[tracer]: https://github.com/MCModderAnchor/tacwiki/blob/8379d85ae22461f1cdb4dac329b897586f637d62/docs/zh/gunpack/gun/data/07_tracer.md
[sound]: https://github.com/MCModderAnchor/tacwiki/blob/8379d85ae22461f1cdb4dac329b897586f637d62/docs/zh/gunpack/gun/display/08_sounds.md
[pad]: https://github.com/MCModderAnchor/tacwiki/blob/8379d85ae22461f1cdb4dac329b897586f637d62/docs/zh/gunpack/gun/display/11_controllable.md
[particle]: https://github.com/MCModderAnchor/SimpleBedrockModel-Wiki/blob/671d614d1360b347c9fd3719d2aa2aee9bc52116/docs/particle/definition.md
