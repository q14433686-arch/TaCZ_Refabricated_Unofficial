# 1.2.0-hotfix → 26.3：玩家能感觉到什么？

> **批量实现进度（本轮）**：以下原审计结论保留作基线。现已提交 `ShotConsequencesSmoke`、`MovementRulesSmoke`、`BulletRulesSmoke`；趴下/空中散布和命中规则已接线，**新增专服方块/实体筛选测试已通过；集成客户端测试尚待运行，不能整体改标“已验证”**。主源码的龙部件包名错误已修正；最新构建见 [5f78983 工作流](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44337530393)。`renew` 枪口运行时仍缺失，未因本轮修改而变成已实现。


**后续核实**：[5f78983 的完整工作流](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44337530393)最终成功，`PORT_SHOT` 实际确认 DB-2 裸枪/取消/地面/空中速度及双端位移，敌对/中立、半径、消音、禁用、取消和已有目标对照通过；客户端 runner 也要求 `PORT_MOVEMENT`。同次 XL50 stripe 为 7 / 45:45 / 38:38 px，恢复热点 2815/2529，XM157 12 像素框回归通过。仍不覆盖先前列明的键鼠触发、骑乘或独立双客户端矩阵。

**枪口第三批正在实施**：`15ac0b7` / `cc80061` 已导入 `MuzzleFlash`、配件烟雾字段、共用方向解析及独立的逐发参数解析器，测试缺省/显式 null、烟雾与火光独立、消音与 flash_hider、默认值哨兵及方向覆盖。**参数解析器尚未接入渲染**；无纹理 renew 仍由旧加载路径丢弃，未冒充 Glock 已可见，也未触碰现有镜片裁切。上游 SBM 的 first-person/world emitter、Molang 变量与 CameraStateCache 在本端无对应运行时，需原生适配。不能据本次 POJO 导入宣称 renew 完成。


**原生枪口运行时跟进（本轮）**：`2788d9b` 起已接入默认 `tacz:muzzle_flash` / `tacz:muzzle_flash_smoke` 的原生 collector 播放路径，不再只有参数层：第一人称枪口锚点闪光、F5/被追踪实体各自的主手闪光、单次脱离世界空间的烟雾、枪口配件偏移和烟雾独立开关；无纹理 Glock renew 不再被加载器抛弃。第一人称火光沿用目镜裁切材质；瞬时枪模照明不改世界光。断线、连接代次和资源重载清理活动状态。

范围必须单列：这是**默认两种效果的原生播放实现，不是完整 SBM/Snowstorm/Molang 解释器**。任意自定义粒子 ID 和动画 `particle_effects` 尚不支持；默认 ID 的第三方 JSON 组件改写也不由该播放器解释。烟雾采用默认纹理条、生命周期、速度/阻尼、尺寸、方向锥和颜色梯度的原生采样，不宣称逐像素一致。

新增 `MuzzleEffectsSmoke` 经真实客户端按键击发 AK47、RC1、Glock，再切 F5，并让客户端追踪的另一个 Husk 击发。断言实际枪口提交、烟雾 burst、关烟仍有闪光和清空状态，保留截图；**提交计数不等同像素可见，更不等同视觉一致**。`944e138` 的 [Full build/JUnit/专服步骤](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44347504106)通过；截至记录时集成客户端尚在安装 Vulkan 依赖，不能把本轮新增运行时记作游戏验收通过。仍待开火帧像素、移动/转身后的世界烟雾、独立双客户端、Iris 和资源重载画面对照。

本轮验收拆为：
1. **射击结果**：真实生存连接，调用默认枪服务端 `shoot`（含 Lua/扣弹/事件），测 DB-2 裸枪、超压反冲弹、取消、地面/跳跃的有符号速度增量及后续双端 Z 坐标；活体生物的沉默/禁用/取消/半径/已有目标对照。不是键鼠触发链，也尚未覆盖骑乘、创造玩家、负向射击方向和自定义倍率。
2. **玩法规则**：空手趴下接入按键和控制器，进入/退出只写自己持有的姿态；增加空手许可、俯仰和空中腰射配置。服务端断言空手/禁用/姿态所有权/空中 ADS 开关；方块断言 ID、tag、重载清缓存和硬度（玻璃/石头/基岩），实体断言忽略列表进入射线筛选。尚缺客户端按键往返、鼠标角度、队友遮挡后敌人和爆炸免伤实测。
3. **枪口效果**：仍待实施。必须成组验收第一人称、F5、追踪射手，闪光跟随/烟雾脱离、消音器和独立烟雾开关，并回归镜片遮罩、热成像及资源重载。不以补 POJO 或旧贴片截图算完成。


这是**移植验收单**，不是已发布版本的功能介绍。比较的是 [Fabric 1.21.1 的旧版 `d290355` → 目标 `356bdf9`][delta]，对照本工作分支（运行时代码 `e92a998`）。旧的[四树对照](FABRIC_120_COMPARISON.md)按文件和子系统分组；这里反过来，从玩家的一次操作查到触发条件、本端实现和测试。一项机制可以有几个不同结果：会掉弹药不等于射手会移动，有枪声不等于生物会转身，有贴图不等于有烟。

- **已实测**：已有[游戏测试运行][game-run]或下文链接的专项运行看到了**所写的那一个结果**。服端状态、客户端像素、真人听感不是同一种证据。本轮是源码与既有证据审计，没有新开游戏测试。
- **已接线，待实测**：找到了生效数据和消费它的代码；只有单测或没有针对该现象的游戏断言。
- **部分 / 未移植**：注明哪部分仍是旧行为，或目标行为在本端缺哪个处理环节。没有测试本身不是「未移植」的证据。

## 射手位移

**DB-2 杜林人（`db_short`）装“超压反冲弹”后，开火会反推射手。26.3 已接线，未实测玩家位移。** 不是所有霰弹枪自带这个效果。短双管的[允许表][db-allow]点名 `tacz:ammo_mod_overpressure_recoil`；它在游戏里是[扩容弹匣槽配件][db-part]，不是往背包里放一叠普通 12g。[该枪数据][db-data]在这个配件的专属配置里启用 `recoil_push`，裸枪没有；其中的 `bullet.knockback` 是**命中对象**的击退，`recoil.pitch/yaw` 是**画面**后坐，两者都不是射手位移。

| 默认包对照 | 每次成功击发的预期（数据推算，不是实测距离） |
|---|---|
| 短双管单发档：裸枪 → 装超压反冲弹 | 默认 16 枚弹丸，推力按 `16 × 0.35` 缩放。平地 `0.02 × 16 × 0.35 = 0.112`，空中 `0.1 × 16 × 0.35 = 0.56`；加到**反发射方向速度**上，不是后退这么多格。用单发档是为了不混入[双发脚本][db-script]的第二次击发。 |
| M95 / M107，裸枪 | 这两把的枪本体有 `recoil_push`：站立 0.05、空中 0.25、潜行 0.025、趴下 0；可与短双管的配件触发对照。AK47 数据里的同名段是注释，不算第三把。 |

[上游击发实现][up-shot]和[本端 `runShootCycle → applyRecoilPush`][shot]都是对**不在坐骑上的玩家**，在击发未取消、需要扣弹时已扣弹之后，从配件缓存取值，用 `setDeltaMovement` 叠加，再给射手发 `ClientboundSetEntityMotionPacket`。不用打中东西。[AttachmentDataTest][push-test]测了姿态选择和多弹丸封顶；重进世界后打一枪的测试测的是动作次数和弹量，没测坐标。

**验收方法：** 生存玩家，不按移动键，固定朝向，对空单发；记录服务端击发前后速度、随后几 tick 的位置和客户端位置。对比同枪拆/装配件、地面/空中；另测取消击发、骑乘不增加推力。只有准星上跳、怪物后退或服务器算出一个非零数都不能通过。

## 枪口火光和烟雾

**新版 `renew` 火光和烟雾未移植；部分枪仍可走旧贴片。** 目标与本端默认包都是 70 个枪械 display：68 个选 `renew`，其中 **67 个还带旧 `texture`**（如 [AK47][ak-display]），**[Glock 17][glock-display] 只有 `renew` 没有旧贴图**；M320、RPG7 没有 `muzzle_flash` 段。本端 [MuzzleFlash][local-flash]只读 `texture/scale`，[加载器][local-display]把无贴图的整段置空。所以在 Glock 17 上连这个旧回退也走不到；而在 AK47 上看到一闪，也不能给新版打勾。

| 玩家看到的区别 | 目标版本怎么触发 | 26.3 要验收什么（均未通过新版画面验收） |
|---|---|---|
| 第一人称：枪焰贴在枪口，烟离开枪口留在世界里 | [第一人称发射器][up-fp]按开火和模型定位点产生两个效果。[默认定义][defs]里火光 `to_world: false`，烟 `true`。还能短暂提亮手中模型，不是给地图放光源。 | 现在是[按本机击发时间提交的旧四边形][old-quad]，不消费这两份定义。用有 / 无旧贴图的两把枪，拍开火前、开火、转动镜头后的连续帧，分辨跟枪的光和脱离的烟。 |
| 第三人称：F5 看自己、旁人看射手，火光和烟不应串到别人身上 | [第三人称发射器][up-tp]按实体维护枪口，短窗口内火光跟随锚点；烟在枪口的世界位置一次发出。本机第一人称不积攒 F5 请求。 | 本端无这条按射手处理的 `renew` 路径。分别测 F5、第二玩家观察与切换视角；不能用第一人称帧或收到开火包替代。 |
| 换枪口配件改变火光大小、排烟方向；可只关烟保留火光 | 上游火与烟可独立选定义/显式设 `null`；`RenderMuzzleSmoke`、`flash_hider`、烟量/大小/方向分别生效。如默认 RC1 配件有 `flash_hider: 0.2` 和排烟角度。 | 用同枪裸枪/配件、只关烟、只关火、资源重载作对照。旧消音器把贴片隐藏，不等于上述调节。另用测试包验证动画 `particle_effects` 的触发与清理；默认动画没有这类关键帧，**不妨碍上游按开火触发火烟**。 |

## 枪声与生物仇恨

**「AI 寻枪声」的实际机制是枪声引怪，不是让 AI 去上次响枪的坐标搜索。26.3 有服务端处理，活体反应待实测。** [上游][up-anger]监听低优先级、未取消的 `GunFireEvent`；[26.3 注册点][anger-reg]改为成功结算后的 `GunFirePostEvent`。两边都不是监听声卡音量、客户端听到的声音或脚步声。

| 开枪条件 | 服务端预期结果 |
|---|---|
| 普通生存玩家，未装消音，生物空闲且可攻击射手 | [配置][anger-config]默认开启。中立生物基础半径 **24 格**，敌对 **32 格**，乘该枪 `fire_sound.fire_multiplier` 并取整，各封顶 **64 格**；按距离而非沿路寻声。`NeutralMob` 记射手为 `lastHurtByMob`，不是强制所有中立生物立刻冲锋；其余 `Enemy` 直接以射手为目标。 |
| 装有带 `silence` 修饰符的配件；或创造/旁观/假玩家开枪 | [处理器][anger]跳过这次枪声反应，不是仅把半径缩短，也不保证怪物不能通过原版视野发现玩家。不改已有目标，不激怒盟友、已驯服的中立宠物及玩家制造的铁傀儡。 |

[GunSoundAngerTest][anger-test]只验倍率、无效值和半径上限；现有追踪测试里的牛是**击发者**，不是被响声吸引的怪。公共配置已有 `EnableGunSoundAnger`、`DefaultGunSoundNeutralMobAngerDistance` 和 `DefaultGunSoundHostileMobAngerDistance`；它们不是播放音效的距离。[26.3 的 Cloth 枪械设置页][anger-cloth]还没有上游对应的开关和两个半径控件。

**验收方法：** 用真实连接的生存玩家和默认倍率 1 的 AK47，不打中生物，先隔断视线确认不开枪时没有目标；在两个半径的内外放空闲僵尸与野狼。成功开火后分别读 `target`、`lastHurtByMob`，再观察 AI 动作。用允许的消音器、关闭配置、取消击发和已有目标各做负对照；不要拿 `FakePlayer` 或创造模式的无反应判故障。

## 其他可感差异

以下对照同一对提交的 Java、有效枪包（数据、脚本、display）、配置和菜单。上游默认值可核对[动作与同步配置][up-sync]、[命中规则][up-ammo]、[显示选项][up-render]；需要开选项、装可选模组或自制枪包的效果，不算人人开局都会遇到的变化。[运行记录][runs]按提交保存；下面点名的用例才是相应行为的证据。

### 移动与射击反馈

| 怎样看出区别 | 26.3 状态、证据和下一项对照 |
|---|---|
| **空手也能按趴下键；持枪趴下时可调抬头/低头范围。** 上游 `EnableCrawlWithoutGun` 默认开，趴姿仰/俯上限默认 45°/30°，松键退出不应卡在进入冷却。 | **未移植这些变化**：[本端按键与姿态][crawl]仍要求主手持枪，`MouseHandlerMixin` 仍用固定阈值。区分模组按键和原版一格空间强制爬行；测空手按/松、切枪、两端姿态和调整角度。 |
| **跳起/下落时即便保持开镜，也可以按腰射散布。** 上游 `UseHipFireInaccuracyWhileAirborne` 默认关；开启时在空中按移动/静止选择。 | **未移植**：[本端散布选择][inaccuracy]仍先判满瞄准，本端 `SyncConfig` 无此选项。要在相同姿态下比较开关两档，而不是用单发落点推断。 |
| **服务器可让子弹穿过关闭友伤的同队队友**，也可按实体 ID 对画、物品展示框等免疫；是两个默认关的选项。 | **未移植**：[上游命中筛选][up-entity]在选取最近交点前排除，[本端][entity-util]无相应过滤。测队友在前、目标在后，并独立开/关展示框免疫；「队友没掉血」不证明子弹穿过去了。 |
| **打碎哪些方块由列表/标签和硬度共同决定。** 上游 `DestroyGlass` 开时，默认玻璃块/玻璃板标签，硬度上限 1.0；不只是能不能打碎普通玻璃。 | **部分**：[本端][glass]仍按方块类/乐器属性判断玻璃，没有 `DestroyBlocks` 和 `DestroyBlockHardnessThreshold`。测加入/移出标签的方块，以及超过阈值的同标签方块。 |
| **命中墙面的碎屑、烟尘与弹孔可分开。** 例如默认 12g display 有表面云尘，关弹孔不应连同命中效果一起关掉。 | **部分实测**：`PackClientSmoke` 对同一客户端粒子工厂测了贴花启/禁，[HitSurfaceParticleTest][hit-test]测配置边界。上游还有每弹孔随机表面偏移、服务端 `BulletHitBlockEffectSendDistance`（默认 128，射手例外）；本端仍按贴花尺寸偏移、由原粒子协议下发。重叠闪烁、远处旁观者和击中声需独立对照，不能拿枪口烟验收。 |
| **手持枪受普通伤害时的视角倾斜减弱**，正面/背面相对侧面再减半；不是射击后坐。 | **未移植此调节**：上游 `GunHurtBobMultiplier` 默认 0.1；本端 `GunHurtBobTweak` 保留的是**被枪击**的处理，没有新倍率。测持枪/空手受到相同方向的普通伤害。 |
| **举枪过程不只是换最终倍率**：模型的平移和旋转、手模与世界 FOV 可用不同的时段与曲线。 | **已接线，过渡手感待实测**：67 份默认 display 选 `ads_preset: tacz:renew`（与枪口的 `renew` 无关）；[曲线单测][ads-test]及已有开镜场景不证明每一帧。用同枪 `legacy` / `renew` 对比举枪全过程。 |
| **同一个动作的声音可有不同响度和音高。** 枪包可用声音对象覆盖或按次随机；旧字符串仍合法。 | **已接线，未试听验收**：`SoundPlayManager` 逐次取样，[SoundDataTest][sound-test]测字符串/对象。默认枪 display 的 `sounds` 是字符串；要用显式对象测试，不要把本地听感当成怪物仇恨半径。 |
| **第三人称可只隐藏副手和未选中快捷栏的背挂枪**，主手照常显示。 | **未移植此开关**：本端 `HumanoidOffhandRender` 有背挂绘制，但无目标的 `DisableThirdPersonOffhandAndHotbarGunRender`。外显/LOD 的模型提交测试也不验证隐藏开关；需用主手、副手和另一个快捷栏槽同时对照。 |

### 默认内容、改装和武器操作

| 怎样看出区别 | 26.3 状态、证据和下一项对照 |
|---|---|
| **工作台/创造栏有新枪、新弹药和配件**，例如 QR-24、M1897、M1866、PKM、SVD、MCX Spear LT，后者用新 `.300 BLK`。旧枪也调过：如 M4A1 的基础 `aim_time` 0.16 → 0.20 秒、`rpm` 810 → 800。 | **内容已加载，非逐枪玩法实测**：默认 gun ID 54 → 70，ammo 24 → 25，attachment 99 → 126（含内置件，不等于 27 个可合成物品），另新增 5 个转接座。`DefaultCatalogSmoke` 查索引/配方产物，`DefaultClientCatalogSmoke` 查 70 个主模型/状态机和 M1897 专用弹壳；都不测实际射速或全部动作。 |
| **不用消耗库存即可选枪包指定的原厂件；有些瞄具需先装增高座**，带瞄具时不能直接拔掉必需的座。 | **部分组合已实测**：[DefaultRefitSmoke][refit]用实际配方产物与改装请求检查 QR-24、Troy 握把和转接座；`PackServerSmoke` 测虚拟件不变库存物品、非法拆卸被拒。不是任意枪/镜都兼容。 |
| **主动卸弹与换弹中退弹是两件事。** 支持的枪在本端按 Alt + 换弹键（默认 Alt+R）退回备弹，不应马上自动装回。Taurus 943 的新脚本则在普通换弹的指定时刻退出弹巢余弹，再逐发装填。 | **部分实测**：[GunActionSmoke][actions]验脚本退弹到弹盒、虚拟备弹上限和拒绝错误回收；[DefaultReloadSmoke][reloads]验 M1866 等六组真实脚本的弹量守恒，**不含 Taurus 943**。按键入口见 `ReloadKey`；还需按真实按键，看 943 中断前后弹量和动画是否对上。 |
| **部分枪换弹会退出瞄准**，并非所有枪在换弹/拉栓期间都能一直贴镜。 | **部分实测**：默认 8 把声明 `force_aim_exit_on_reload: true`（含短双管、M1897、PKM）；`GunActionSmoke` 用夹具断言换弹中拒绝瞄准、结束后可瞄。`force_aim_exit_on_bolt` 是可配置能力，默认包未显式启用；需另测拉栓和客户端画面。 |
| **转管枪不拿在主手也能在背包散热，冷到零才解锁。** | **服端已实测**：[ScriptGuardSmoke][heat]对默认 `minigun` 分别 tick 主手和库存：库存热度 4 → 0.75 → 0 并解锁，主手不被这条路径重复扣热。不是整段过热 HUD 录像。 |
| **背包直读供弹时，枪模上的弹量数字应与 HUD 同步。** | **部分**：目标的 `%ammo_count%` 和 HUD 共用备弹计数；本端 HUD 已用 `ClientAmmoCount`，但[枪模占位符][ammo-papi]仍读枪栈内弹数。用有 `%ammo_count%` 的直读枪夹具，只改背包/弹盒数量，比较两个数字；普通弹匣枪不能验到这项。 |
| **测距、火控框、热成像是三个现象**：读方块距离；在可见实体处出现落点标记；镜内热源会被墙挡住。 | **指定场景已实测**：UH-1 两个距离；XM157 实体/遮墙对照；M700 + XL50 白热在经典变焦与重投影 PIP 的[原始帧与边界][optics]。别的热镜、夜视/其它色盘、Iris 光影和独立多人不据此勾选。 |

### 界面、世界交互和存档

| 怎样看出区别 | 26.3 状态、证据和下一项对照 |
|---|---|
| **弹药库分铁/金/钻石级，升级、拆放后保留库存；可按弹种取用。** | **指定流程已实测**：`AmmoStorageSmoke` 真配方升级并读回库存；`WorldReturnSmoke` 重进同一存档后从真实菜单取出具名弹药，没有合并到普通弹药。不是任意外置存储/搬运 mod 的兼容证明。 |
| **展示柜可连片上锁；挂板可转动展品、染色/继承邻板颜色。** | **指定流程已实测**：`DisplayRackSmoke` 查锁传播、存放/旋转不重扣、掉落和镜像；重进测试读回槽位、角度、颜色。密集展示、移动载具上的摆放和可选模组另验。 |
| **创造栏可按内容包筛选，DMR 有自己的分类；重载时不必退出原搜索页。** | **客户端已实测**：`CreativeReloadSmoke` 实际点击包名与「全部」，在已打开的搜索页收到新物品，切 DMR 后检查 MK14。不代表每种语言布局已看过。 |
| **从枪查弹/配件，从弹/配件反查枪；查看器可跳到已开的对应工作台配方。** | **部分实测**：JEI、REI 分别有重载后的真实 gun→ammo 页面与世界负对照；菜单有禁止跨台选择的服端断言。尚非玩家点击 transfer 的端到端验收，也不表示 EMI 可用。[页面证据][compare]。 |
| **手持筛选让工作台没有配方时，上游明确提示原因并给「取消手持筛选」按钮；材料可悬停查看。** | **部分**：本端 [GunSmithTableScreen][smith]会筛选、也会处理查看器跳转，但没有上游 `addEmptyByHandFilterHint` 的空态/按钮和 `renderIngredientTooltip` 的材料格逻辑。应以筛到零条再清除、悬停材料格验收；打开查看器页面不是这项测试。 |
| **击中远靶、改装武器可解锁进度。** 例如 `target_hit` 要求距离至少 128 格。 | **已接线，授予待实测**：服端解码 23 个默认进度；`TargetHitTrigger` / `GunRefitTrigger` 有实际命中/改装调用。还需验证距离线内/外与实际改装前后玩家是否获得进度，不把 JSON 加载当授予。 |
| **装入旧枪包后得到转换提示；不用改写原包就能导出再重载。** | **部分**：`ConversionReloadSmoke` 验过旧夹具导出、重载以及客户端同步；目标的登录聊天警告、可点指南/不再提示（`GunPackCompatibilityWarning`）在 26.3 没有同等登录入口。不是见到转换命令就等于玩家收到了提示。 |

## 怎样复核或补一项

在[四树复核命令][compare]取到的两个固定 Git 对象上比较，不拿另一个加载器的更新日志代替 Fabric 目标。例如只看短双管在 **旧 → 新** 时增加了什么：

```sh
P=src/main/resources/assets/tacz/custom/tacz_default_gun
git -C /tmp/tacz-reference.git diff d290355 356bdf9 -- \
  "$P/data/tacz/data/guns/db_short_data.json" "$P/data/tacz/tacz_tags/attachments/allow_attachments/db_short.json"
```

本轮用 `scripts/port_default_pack_120.py`（只读）与 `scripts/verify_120_resources.py` 复核 4,176 个有效文件。要复算枪口的 68/67 和那把没有旧贴图的枪，在仓库根解析**有效字段**（不要把注释里的示例算进去）：

```sh
python3 -B - <<'PY'
from scripts.verify_120_resources import PACK, read
flashes = {p.stem: read(p).get('muzzle_flash') or {} for p in (PACK/'assets/tacz/display/guns').glob('*.json')}
renew = {name: fx for name, fx in flashes.items() if fx.get('type') == 'renew'}
print(len(renew), sum(bool(fx.get('texture')) for fx in renew.values()), sorted(name for name, fx in renew.items() if not fx.get('texture')))
PY
```

实现另查 Java 调用、事件注册、Lua；证据另查 [JUnit][junit]、[GameTest][gametest] 和具体运行。后续补项请写明「谁做什么 / 开关与负对照 / 26.3 断言或可复核的帧」；优先补**真实射手位移、活体仇恨**两项行为测试，并将**枪口火光、脱离的烟、旁人视角**作为缺失的客户端实现分别验收。这不是对每把枪、每段动画或每个外部包的穷尽声明。

[delta]: https://github.com/Sh1roCu/TACZ-Refabricated/compare/d2903554da039d2355920953a81447784a3f2be2...356bdf9d48f2225bac6ceb56adc5576a418517a8
[compare]: FABRIC_120_COMPARISON.md
[runs]: FABRIC_120_PORT_26_3.md
[game-run]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44244608846/job/134600441865
[glock-display]: ../../src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/display/guns/glock_17_display.json
[up-sync]: https://github.com/Sh1roCu/TACZ-Refabricated/blob/356bdf9d48f2225bac6ceb56adc5576a418517a8/src/main/java/com/tacz/guns/config/sync/SyncConfig.java
[up-ammo]: https://github.com/Sh1roCu/TACZ-Refabricated/blob/356bdf9d48f2225bac6ceb56adc5576a418517a8/src/main/java/com/tacz/guns/config/common/AmmoConfig.java
[up-render]: https://github.com/Sh1roCu/TACZ-Refabricated/blob/356bdf9d48f2225bac6ceb56adc5576a418517a8/src/main/java/com/tacz/guns/config/client/RenderConfig.java
[optics]: FABRIC_120_COMPARISON.md#原生镜片与热成像
[db-allow]: ../../src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/tacz_tags/attachments/allow_attachments/db_short.json
[db-data]: ../../src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/db_short_data.json
[db-part]: ../../src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/index/attachments/ammo_mod_overpressure_recoil.json
[db-script]: ../../src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/scripts/db_short_gun_logic.lua
[up-shot]: https://github.com/Sh1roCu/TACZ-Refabricated/blob/356bdf9d48f2225bac6ceb56adc5576a418517a8/src/main/java/com/tacz/guns/item/ModernKineticGunScriptAPI.java#L170-L240
[shot]: ../../src/main/java/com/tacz/guns/item/ModernKineticGunScriptAPI.java
[push-test]: ../../src/test/java/com/tacz/guns/util/AttachmentDataTest.java
[ak-display]: ../../src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/display/guns/ak47_display.json
[defs]: ../../src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/particle_definitions
[up-fp]: https://github.com/Sh1roCu/TACZ-Refabricated/blob/356bdf9d48f2225bac6ceb56adc5576a418517a8/src/main/java/com/tacz/guns/client/particle/MuzzleParticleManager.java
[up-tp]: https://github.com/Sh1roCu/TACZ-Refabricated/blob/356bdf9d48f2225bac6ceb56adc5576a418517a8/src/main/java/com/tacz/guns/client/particle/ThirdPersonMuzzleParticleManager.java
[local-flash]: ../../src/main/java/com/tacz/guns/client/resource/pojo/display/gun/MuzzleFlash.java
[local-display]: ../../src/main/java/com/tacz/guns/client/resource/GunDisplayInstance.java
[old-quad]: ../../src/main/java/com/tacz/guns/client/model/functional/MuzzleFlashRender.java
[up-anger]: https://github.com/Sh1roCu/TACZ-Refabricated/blob/356bdf9d48f2225bac6ceb56adc5576a418517a8/src/main/java/com/tacz/guns/event/GunSoundAngerEvent.java
[anger-reg]: ../../src/main/java/cn/sh1rocu/tacz/TaCZFabric.java
[anger]: ../../src/main/java/com/tacz/guns/event/GunSoundAngerEvent.java
[anger-config]: ../../src/main/java/com/tacz/guns/config/common/GunConfig.java
[anger-cloth]: ../../src/main/java/com/tacz/guns/compat/cloth/common/GunClothConfig.java
[anger-test]: ../../src/test/java/com/tacz/guns/event/GunSoundAngerTest.java
[crawl]: ../../src/main/java/com/tacz/guns/client/gameplay/LocalPlayerCrawl.java
[inaccuracy]: ../../src/main/java/com/tacz/guns/resource/pojo/data/gun/InaccuracyType.java
[up-entity]: https://github.com/Sh1roCu/TACZ-Refabricated/blob/356bdf9d48f2225bac6ceb56adc5576a418517a8/src/main/java/com/tacz/guns/util/EntityUtil.java
[entity-util]: ../../src/main/java/com/tacz/guns/util/EntityUtil.java
[glass]: ../../src/main/java/com/tacz/guns/event/ammo/DestroyGlassBlock.java
[hit-test]: ../../src/test/java/com/tacz/guns/client/resource/HitSurfaceParticleTest.java
[ads-test]: ../../src/test/java/com/tacz/guns/client/resource/AdsPresetTest.java
[sound-test]: ../../src/test/java/com/tacz/guns/client/resource/SoundDataTest.java
[refit]: ../../src/gametest/java/com/tacz/guns/test/DefaultRefitSmoke.java
[actions]: ../../src/gametest/java/com/tacz/guns/test/GunActionSmoke.java
[reloads]: ../../src/gametest/java/com/tacz/guns/test/DefaultReloadSmoke.java
[heat]: ../../src/gametest/java/com/tacz/guns/test/ScriptGuardSmoke.java
[ammo-papi]: ../../src/main/java/com/tacz/guns/client/model/papi/AmmoCountPapi.java
[smith]: ../../src/main/java/com/tacz/guns/client/gui/GunSmithTableScreen.java
[junit]: ../../src/test/java/com/tacz/guns
[gametest]: ../../src/gametest/java/com/tacz/guns/test
