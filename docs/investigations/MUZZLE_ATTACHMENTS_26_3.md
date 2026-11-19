# 26.3 特性线：枪口配件为何没有缩小枪焰？

**复现了，不是 RC1 没装上。** 在移植的 1.2.X 特性线上，AK47 装 RC1 后，新版 `renew` 路径画出的闪光宽、高与裸枪几乎一样。RC1 写的是 `flash_hider: 0.2`，应当让**两条边**都变成裸枪的 20%，不是彻底熄灭，也不是固定剩下 20% 的屏幕像素。

## 修复前：量的是哪一层？

[`3b3877c`][red-code] 只加测试，不改产品代码。服务端、客户端均确认实际枪口槽 ID；用客户端按键开火。从渲染快照识别射手、视角和 `tacz:muzzle_flash` **定义 ID**，在延迟绘制回调中测同一四边形四个顶点间的距离。表中是绘制空间边长，不是贴图尺寸、烟雾数量或发射器计数。

| 26.3 集成客户端，第一人称 | 裸枪宽 × 高 | 装 RC1 宽 × 高 | 宽比 / 高比（应为 0.2 / 0.2） |
|---|---:|---:|---:|
| [Mesa / Vulkan][red-mesa] | 0.448487 × 0.896975 | 0.448487 × 0.896974 | 1.000 / 1.000 |
| [Iris + Sodium / OpenGL，启用测试光影][red-iris] | 0.450000 × 0.900000 | 0.450000 × 0.900000 | 1.000 / 1.000 |

此前的「有闪光提交、有烟、有粒子几何」绿灯与这次红灯并不矛盾。烟也能贡献几何，存在性检查更不要求装配前后有区别。

## 回溯与原因

- [旧 Fabric 渲染器][old-quad]以及[普通 26.3][plain-quad]的 `default` 是贴图火光，检查配件的 `silence.use_silence_sound` 来隐藏。它不是新版的连续倍率系统。
- [目标 Fabric 1.2.0-hotfix 第一人称][up-fp]和[第三人称][up-tp]都把逐发参数传给粒子定义。有显式 `flash_hider` 时用倍率；没写该字段的消音配件才隐藏火光。烟是另一层。
- 本仓库接入定义播放器之前，[原生后备实现][before-definition]直接以 `flashScale × 0.45` 生成半边长。[`0d74fe8` 的差异][definition-change]加了优先使用已加载定义的分支：配件数据解析仍在，但**没有传 Molang 变量**，改为缩放定位矩阵。刷新骨骼时重建的又是不带这个缩放、也不带配件前移的矩阵。[默认闪光定义][flash-json]读不到 `v.muzzle_scale`，两轴分别落到 `0.5 × 0.45`、`1 × 0.45` 的半边长，解释了实测的 1:2 矩形。

这是**本移植线切换渲染路径时引入的参数传递缺口**。定位到的是代码差异；没有逐版运行所有历史二进制，不据此推断玩家第一次遇到它的发布版本，更不是上游 1.2.0 全部有问题。

## 同一条链上的检查

| 层 | 修正或保留的契约 |
|---|---|
| 配件选择 | 读取当前栈；已装配件优先，其次显示用内置件；枪械专属数据优先于公共数据。`renew` 先判显式 `0` 或消音回退，隐藏时不创建闪光，不能把 `0` 送进定义再触发默认值。`default` 只按有效消音标志隐藏，不套用新版的分数倍率。 |
| 逐发输入 | 在发射器**初始化之前**给实例独立的环境写入 `muzzle_scale`、`muzzle_speed`；烟使用自己的 `smoke_size`、`smoke_vel`、`smoke_count`、方向表/锥角/基向量。大小不缩放骨骼、速度或偏移。动画关键帧作者另外触发的特效不是这次开火请求，不按名字一律削弱。 |
| 默认值与开关 | 枪未写 `smoke_scale`/数量时保留哨兵，交给[烟定义][smoke-json]取默认大小 1、基数 15；配件明确把烟大小、数量或速度倍率设零，则跳过烟层。关枪焰不再顺带截断烟；关烟不影响闪光。各视角显式 `null` 只取消对应层。 |
| 位置与视角 | 配件偏移沿枪口局部 Z 轴，在每次刷新骨骼后重施；手部与第三人称不互相刷新锚点。世界闪光补偿锚点出生后的平移，世界烟粒子不补偿。第三人称烟发射器保留射手归属，发射位置不再跟着枪移动。 |
| 枪模照明 | 用这一枪第一人称配置的非空闪光 ID，不再要求 ID 必须等于 `tacz:muzzle_flash`。消焰不留枪模闪亮。只抬方块光，最多比环境多 10 级、上限 12，不压暗更亮环境、不改天空光，不是世界动态光源。 |

**默认枪包范围（静态统计）**：70 个枪械 display 中 68 个声明 `renew`，另 2 个未声明 `muzzle_flash`。18 个 `muzzle_*_data.json` 中，9 个消音件都显式写了 0.2 或 0.13；另有非消音的 Cthulhu 写 0.13，8 个既未配置倍率也未配置消音。因此「装任意枪口都应该完全消焰」不是包的规则。没有改默认枪包资源，也没有把 68 个 display 算作 68 次游戏实测。

## 修复后：游戏里测到了什么？

产品与测试源码 [`b7eca81`][green-code]。同一修订的 [普通 Mesa 客户端、完整构建、JUnit 与专服][green-mesa]和[启用测试光影的 Iris 客户端][green-iris]都通过。没有改发布版本。

| 默认包实际绘制，AK47 | 裸枪宽 × 高 | RC1 宽 × 高 | 实测装/不装 |
|---|---:|---:|---:|
| 第一人称，两种后端 | 0.675000 × 0.675000 | 0.135000 × 0.135000 | 宽、高均为 **0.2000** |
| 自己按 F5，两种后端 | 0.379688 × 0.379688 | 0.075938 × 0.075938 | 宽、高均为 **0.2000** |
| 旁观另一个持枪 Husk，两种后端 | — | 0.086063 × 0.086063 | 除以该模型缩放 0.6375 后为 **0.135**；不是第二个玩家进程 |

- **装、换、拆都有对照**：生存按键单发；先等客户端确认枪 ID、显示配置与枪口槽，再要求服务端弹匣正好少一发。实测卸下同一栈上的 RC1 后恢复原大小；非消音 Cthulhu 命中 `0.13` 倍，Cyclone 无消焰字段保持原大，Glock 无旧贴图仍走新版。
- **熄灭不是漏开枪**：测试包让枪械专属规则覆盖配件公共 `0.2` 为显式零；另一个内置件只在该枪上消音、未写倍率。两例都扣弹，**无闪光、无旧贴片任务、有世界烟几何**。反向测试中，配件关烟但增大闪光、客户端关烟保留闪光、调试关火保留缺省大小的烟均通过。旧 `default` 用同样的按键和扣弹检查，非消音件仍提交旧贴片，RC1 和专属消音内置件不提交。
- **枪口位置不是靠截图猜**：从一发 F5 受控长寿命粒子冻结模拟，改变模型根而不改变粒子速度和相机。相对裸枪骨骼的刷新后局部 Z：普通 **−0.4624997**，Iris **−0.4625001**（RC1 配置 `−7.4/16`）。根再平移 +1.25 时，两边的闪光绘制位置均移 **+1.25**，烟粒子均为 **0**，烟发射器本身也没有被拖走。手部提取没有改写此 F5 锚点。这是可重复的矩阵/绘制快照对照，不是两名玩家奔跑录像。
- **烟的独立输入**：[JUnit][unit]直接编译随包 `muzzle_flash_smoke.particle.json`，实跑发射和粒子组件。用不重叠的随机计数区间（默认 3–15、倍率后 30–120）、依随机种子计算的宽高、初速模长 9 / 22.5、零角单向及非零角双向锥的逐粒子速度分量作断言。显式烟大小/数量/速度倍率零与枪械缺省值分开检查。不是把三个 getter 打印一遍。

### 像素与证据边界

默认贴图的 UV、旋转随机，寿命只有 20 ms；上表量**真实默认闪光顶点**，不把按键后几 tick 的截图说成那一帧。另让真实 AK 用测试包的长寿命亮紫方片，在 `creation_expression` 里读取 `v.muzzle_scale` 决定尺寸，烟层为 `null`。该方片足够亮、大且略微横移，便于从枪身、HUD 和草地分离。每个栈都有自己的未开火参照。

| 后端 | 裸枪（对照 / 开火） | RC1（对照 / 开火） | 比各自对照新增紫色像素 |
|---|---|---|---:|
| 普通 | [未开火][mb0] / [开火][mb1] | [未开火][mr0] / [开火][mr1] | **5821 → 65** |
| Iris | [未开火][ib0] / [开火][ib1] | [未开火][ir0] / [开火][ir1] | **5570 → 217** |

在每张 854×480 原图内取 `x ∈ [W/5,4W/5)`、`y ∈ [H/5,3H/4)`（整数除法），比较同坐标 `C = min(R,B) − G`，数 `C_on > 60` 且 `C_on − C_off > 50` 的像素。两后端均要求 RC1 非空（至少 12 像素）且裸枪覆盖大于其 5 倍。已从[回传的八张原始 PNG][frames]在工作区独立复算，四个计数与客户端完全相同。**不要求面积比刚好为 0.04**：枪身遮挡、配件前移和投影都会改变可见面积。另在同一发的枪模光照函数以黑暗环境采样，裸枪 / RC1 的方块光等级为 **10 / 3**；不是世界被照亮的像素测量。

重跑入口：[实际开火用例][game-test]、[四顶点探针][probe]、[锚点对照][anchor-test]、[默认烟定义单测][unit]。没有跑独立双客户端、实体显卡或每一种第三方枪包；未声称所有武器或任意自写 `particle_effects` 动画与上游逐像素一致。若某包仍显示大闪光，应先分开检查实际生效的 `default`/`renew`、枪械专属覆盖、每个视角的定义 ID，以及动画是否另画了一层。

### 保留的中间失败

第一轮修复（`cd25f1e`）[构建 / JUnit / 专服通过][first-mesa]，普通客户端通过默认枪焰边长、消焰、旧火光等断言，最后的自定义颜色对照 **失败（0 / 0）**。原始 [裸枪开火帧](../../build-reports/visual/44448317547-tacz_muzzle_marker_bare_on.png) 与 [RC1 开火帧](../../build-reports/visual/44448317547-tacz_muzzle_marker_rc1_on.png) 中，枪口有被枪身遮住一部分的暗紫色样本；与各自未开火帧比较，新增区域 `min(R,B) – G` 最高只有 59，未过原定 >60。没有用这个失败证明「已经可见」，也没有把门槛降到 59：后续只改测试用颜色材质、扩大/横移标记、固定背景方向，并保留逐发几何和像素两道断言。同修订的 [Iris][first-iris] 在更早的 XL50 光学对照失败，**没有运行到枪口测试**。

[first-mesa]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44448317547/job/135305794377
[first-iris]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44448317569/job/135305794242
[red-code]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/3b3877c87ca1e54a3823e733b66636ad00a2acf2
[red-mesa]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44447751112/job/135303923350
[red-iris]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44447751123/job/135303923244
[old-quad]: https://github.com/Sh1roCu/TACZ-Refabricated/blob/d2903554da039d2355920953a81447784a3f2be2/src/main/java/com/tacz/guns/client/model/functional/MuzzleFlashRender.java
[plain-quad]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/blob/cc990c3f784aa31c8fd7340f8cc158609c9bd16d/src/main/java/com/tacz/guns/client/model/functional/MuzzleFlashRender.java
[up-fp]: https://github.com/Sh1roCu/TACZ-Refabricated/blob/356bdf9d48f2225bac6ceb56adc5576a418517a8/src/main/java/com/tacz/guns/client/particle/MuzzleParticleManager.java
[up-tp]: https://github.com/Sh1roCu/TACZ-Refabricated/blob/356bdf9d48f2225bac6ceb56adc5576a418517a8/src/main/java/com/tacz/guns/client/particle/ThirdPersonMuzzleParticleManager.java
[before-definition]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/blob/e7ef8e0d1487a843bb5f962d84482fa1d3a90080/src/main/java/com/tacz/guns/client/particle/NativeMuzzleEffects.java
[definition-change]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/0d74fe8bec14c48967d6c3d902e7f2057dc46e41
[flash-json]: ../../src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/particle_definitions/muzzle_flash.particle.json
[smoke-json]: ../../src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/particle_definitions/muzzle_flash_smoke.particle.json

[green-code]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/commit/b7eca8146d16e61defb2f47d3381f8427a1d3779
[green-mesa]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44449218116/job/135308741827
[green-iris]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44449218152/job/135308741538
[unit]: ../../src/test/java/com/tacz/guns/client/particle/MuzzleDefinitionTest.java
[game-test]: ../../src/gametest/java/com/tacz/guns/test/MuzzleEffectsSmoke.java
[probe]: ../../src/gametest/java/com/tacz/guns/test/MuzzleQuadProbe.java
[anchor-test]: ../../src/gametest/java/com/tacz/guns/test/MuzzleBindingSmoke.java
[mb0]: ../../build-reports/visual/44449218116-tacz_muzzle_marker_bare_off.png
[mb1]: ../../build-reports/visual/44449218116-tacz_muzzle_marker_bare_on.png
[mr0]: ../../build-reports/visual/44449218116-tacz_muzzle_marker_rc1_off.png
[mr1]: ../../build-reports/visual/44449218116-tacz_muzzle_marker_rc1_on.png
[ib0]: ../../build-reports/visual/44449218152-tacz_muzzle_marker_bare_off.png
[ib1]: ../../build-reports/visual/44449218152-tacz_muzzle_marker_bare_on.png
[ir0]: ../../build-reports/visual/44449218152-tacz_muzzle_marker_rc1_off.png
[ir1]: ../../build-reports/visual/44449218152-tacz_muzzle_marker_rc1_on.png
[frames]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44450021773
