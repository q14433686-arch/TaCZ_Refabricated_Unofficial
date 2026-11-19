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

## 验证记录

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
