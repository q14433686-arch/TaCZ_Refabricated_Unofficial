# 26.3：Reimagined 夜间枪焰、第一人称照明与 SVD 长影

**这是三条渲染路径，不是同一个亮度参数。** 默认枪焰应显示纹理自己的白芯／黄焰；枪身和手臂应保留环境光和自身颜色，只在击发瞬间受到额外照明；SVD 在太阳的阴影图中应该有一个随枪移动的正常投影。没有关闭彩光、实体阴影或背挂，也没有改默认枪包。本次结果只适用于这条 26.3 特性工作分支，不是普通 26.3 的发布记录。

**可试用的构建：** [`e98a328` 的测试 JAR（Actions 压缩包）][jar]。同一修订的[完整构建、专服、普通客户端][green-build]、[三组 Iris 客户端][green-iris]和[编译][green-compile]全部成功；可选模组的独立运行见下文。没有升版或发布 release。

## 在什么环境复现？

真实 MC 26.3 集成客户端、Iris 1.11.6、Sodium 0.9.2、Mesa 软件 OpenGL。[准备脚本](../../scripts/prepare_iris_smoke.py)按 SHA-512 核对两个 Fabric 模组，从[官方 r5.9.3 提交](https://github.com/ComplementaryDevelopment/ComplementaryReimagined/tree/54ae23a44dd21dcf1c8fe70b4f0683d973b34cd0)核对源码归档 SHA-256 后，原样打包到临时游戏目录。**不是**用同名的简化着色器冒充，也不把光影包随 mod 分发。测试核对 Iris 的当前包名、解析后的 `COLORED_LIGHTING`、实际 `floodfill_img`（0 不存在，128 存在）以及 1024² 的 `shadowtex0`。`shadowDistance=64.0`、实体阴影开启、云质量 0；这不等于知道截图作者所有光影选项。

[改前 `ed65916` 的两组 Reimagined 测试均红，简化 Iris 绿][before]；第一次尝试因测试引用了 26.3 没有的 `Blocks.WHITE_CONCRETE`，未启动游戏，不算复现。[改后 `76a964b` 两组及简化 Iris 均绿][after]。以下夜间像素表从回传的 **854×480 原始 PNG** 独立复算；不是材质名、顶点 RGB 或方块光等级。阴影空间测量使用后续校正了读回行距的版本，见下一节。

## 夜间：分开量枪焰和受光面

时间 18000，镜头背后放灵魂灯。同一把原包 AK47，同一机位，比较不击发、只保留击发照明（测试钩子仅拦枪口层）、正常提交原包枪焰三帧。**为抓到 20 ms 的随机纹理，每次第一人称提取时在测试里重放客户端的 `onShot`**；这是受控曝光，不是一次按键的录像，也没在产品里延长寿命。扣弹、实际按键及配件大小另有[单独的端到端用例](MUZZLE_ATTACHMENTS_26_3.md)。

| 后处理后的画面测量 | 彩光关（0）：改前 → 改后 | 彩光开（128）：改前 → 改后 |
|---|---|---|
| 同一张默认条带上的同一白色 texel，受控方片 RGB | `39,49,57` → **`203,206,207`** | `40,50,58` → **`203,207,208`** |
| 灰色机匣的击发增量 ΔRGB（仅照明 − 环境） | `+21,+13,+8` → **`+34,+36,+40`** | `+6,+22,+20` → **`+21,+21,+22`** |
| 皮肤的击发增量 ΔRGB | `+71,+28,+8` → **`+94,+46,+19`** | `+23,+44,+30` → **`+91,+46,+18`** |
| 与枪无关的地面 ΔRGB | `−3,−3,−3` → `−3,−2,−4` | `−2,−2,−3` → `−2,−2,−4` |

- **白 texel 校准**：离开手部，画同一 UV、顶点色、深度和光照的两块校准片，只换材质。改前两组的包内 `eyes` 参考均为 `205,209,210`；改后彩光开为 `206,210,210`。取中心 36² 去掉准星 13² 后的通道均值取整。并不要求高动态范围、色调映射后的白色恰好是 `255,255,255`。
- **实际枪口而非校准片**：在 `x=[49%,59%)`、`y=[46%,59%)` 对比 `stock` 和 `body`。最大通道增量 >55 的像素中，再数最暗通道 >165 且三通道极差 <45 的亮芯。彩光开的改前是 **0/427**，改后是 **54/829**；彩光关的改后是 **121/398**。分母是变化像素，不是纹理面积。旋转、UV 帧、模型遮挡均会变，不要求整片枪焰变白或两次形状一致。
- **照明**：[独立断言](../../src/gametest/java/com/tacz/guns/test/ReimaginedFrames.java)取机匣 `(62,71)–(65,74)%`、皮肤 `(49,67)–(51,70)%`、地面 `(32,61)–(36,65)%`，每个通道对窗口求均值取整，再做差。彩光开时，改后的机匣环境仍是 `19,24,34`，手臂击发后仍是肤色 `135,85,56`；**中性光不等于把所有表面刷成白色**。

在后续同一产品代码的[彩光关][off-final]／[彩光开][act-final]独立客户端里停止重放，不手动清理光照状态，20 tick 后原肤色和暗枪身恢复。两次测试分别有 65 / 63 个实际枪口亮芯。远离枪口的地面没有随枪变亮。AMG10 镜前加入同材质的本地归属、已脱离手部的洋红粒子，两个需要裁剪的倍率均为 **0 / 0** 个镜心新增洋红像素；不裁剪的倍率分别为 **1,835 / 1,828**（[裁剪档原帧](../../build-reports/visual/44457885522-reimagined-act-tacz_amg10_particle_1.png) / [放行档原帧](../../build-reports/visual/44457885522-reimagined-act-tacz_amg10_particle_2.png)）。这是遮挡／放行的正负对照，不是对所有第三方镜片材质的承诺。

白芯验证不要求枪焰边缘也中性：原包条带本来有黄、橙色纹理。

原帧：彩光开，[改前无火](../../build-reports/visual/44454814995-reimagined-act-tacz_reimagined_night_off.png) / [改前开火](../../build-reports/visual/44454814995-reimagined-act-tacz_reimagined_night_stock.png)，[改后无火](../../build-reports/visual/44455729177-reimagined-act-tacz_reimagined_night_off.png) / [只照枪身和手](../../build-reports/visual/44455729177-reimagined-act-tacz_reimagined_night_body.png) / [改后开火](../../build-reports/visual/44455729177-reimagined-act-tacz_reimagined_night_stock.png)。彩光关：[改后开火](../../build-reports/visual/44455729177-reimagined-off-tacz_reimagined_night_stock.png)。

**为什么改这里：** 原包默认闪光定义用 `energy_swirl`。[Iris 的映射表](https://github.com/IrisShaders/Iris/blob/671a5c87747d33983d7ed659461a64f46dfcb1f0/common/src/main/java/net/irisshaders/iris/pipeline/IrisPipelines.java)将未裁剪的 vanilla `ENERGY_SWIRL` 映到 `ENTITIES_CUTOUT`；此前的裁剪管线走 HAND。这两者在 Reimagined 仍执行环境照明，[HAND 高级彩光](https://github.com/ComplementaryDevelopment/ComplementaryReimagined/blob/54ae23a44dd21dcf1c8fe70b4f0683d973b34cd0/shaders/lib/lighting/mainLighting.glsl)还采体素灯色；提高 UV2 不会变成独立的白光。现在[枪焰材质](../../src/main/java/com/tacz/guns/client/render/FlashMaterials.java)只给 TaCZ 自己的自发光表面分配 Iris 的 `EMISSIVE_ENTITIES`，实跑匹配 `ENTITIES_EYES_TRANS`。普通烟雾／受光 add 不被改成自发光。活跃 Iris 下，[第一人称曝光](../../src/main/java/com/tacz/guns/client/render/ViewmodelFlash.java)把原来的受限方块光增量用于一次仅在当前枪和手部提取中有效的反射层：用原纹理、原姿态、等深测试、不写深度，不给其他玩家、方块或天空打光。无 Iris 光影沿用旧 UV2 路径；不把 Sulkan 的“已安装”当作 Iris 可用。普通材质、旧贴片和原生后备没有被全局替换。

## 白天：正常枪影与横穿阴影图的写入

用户的空手截图里，SVD 仍在快捷栏。默认 SVD 的 `hotbar_show[0]` 有外显位置，**切空手不等于移除投影物**。本地源码里，`81f474e` 增设了 `_illuminated` 子树的无方向光材质，只分配了 Iris 的可见程序，并未同时取得实体的太阳阴影程序。Reimagined 的阴影顶点转换与普通实体程序不同。现在[只在外显模型的 Iris shadow pass 中复用原物体材质](../../src/main/java/com/tacz/guns/client/model/bedrock/IlluminatedMaterials.java)：保留同一几何、UV 和 alpha 孔洞，普通可见 pass 继续使用发光材质。不是把 SVD 或准星从阴影里删掉。

时间 4000，白色接收面。[用例](../../src/gametest/java/com/tacz/guns/test/ReimaginedSmoke.java)在**同一客户端 tick**重复完整渲染，读真实 `shadowtex0`，分别 (a) 测试专用：没有 Bedrock 投影；(b) 所有子树用原 body；(c) 产品实际选择；(d) 仅发光子树故意换回旧类型。这样不把掉落实体转动的差异误算成长影。下表是 `8de1322` 的[彩光关][off-final]／[彩光开][act-final]，括号内是 (d) 相对 (a) 在正常枪影之外多出的深度纹素。两边均确认 (d) 实际选中了原来的发光子树材质，而不是猜测纹理名：

| SVD 所在位置 | 彩光关：正常枪影／深度吻合（旧材质多写） | 彩光开：正常枪影／深度吻合（旧材质多写） |
|---|---:|---:|
| 手持 | 41 / 41（2,038） | 40 / 40（0） |
| 空手，枪在可外显的第 0 槽 | 36 / 36（3,065） | 36 / 36（0） |
| 掉落 | 50 / 50（15,399） | 47 / 47（0） |
| 掉落后平移 | 53 / 53（19,516） | 56 / 56（0） |

修复后，两组的产品枪影与原材质参考均吻合，轮廓外多写 **0**，完全移除后残留 **0**。例如彩光关的掉落枪只占 `(493,433)..(521,456)`，平移后中心移 **54.54** 个纹素、53/53 落在原轮廓外；彩光开移 **55.53**，56/56 落在原轮廓外。负对照里，彩光关的旧材质有一行连续多写 **512–515** 个；这次彩光开的旧材质没有多写。是否出现取决于当时的绘制／投影，不能断言彩光开关是条纹的原因，也不能仅凭玩家一张图排除别的投影物。深度差门槛 `0.00015`；纹素不是玩家屏幕像素。

**为何不沿用早先的深度数字：** 早版 probe 未指定 `GL_PACK_ROW_LENGTH`。`fb67e68` 要求参考枪影也要局限在物体周围时，手持那 41 个点的 x 范围竟报 0..1023。新读回实测继承了 **854**（上一次截屏的宽），而光影图是 **1024**。读回时显式指定紧凑布局，用完还原 GL 状态，然后验证正确的局部坐标、平移和完全移除。

可看白色接收面：[改前错误材质](../../build-reports/visual/44454814995-reimagined-off-tacz_reimagined_drop.png)、[同一旧程序测试用原材质参考](../../build-reports/visual/44454814995-reimagined-off-tacz_reimagined_drop_reference.png)、[改后仍有掉落枪的正常阴影](../../build-reports/visual/44455729177-reimagined-off-tacz_reimagined_drop.png)、[完全移除](../../build-reports/visual/44455729177-reimagined-off-tacz_reimagined_removed.png)。截图之间物品有自转，数字来自同 tick 的深度对照，非逐张截图相减。

## 旧移植的运行门禁

`fb67e68` 的[完整构建、JUnit、专服、普通 Mesa 客户端](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44456705157)、[简化 Iris 客户端](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44456705144/job/135333356729)、[JEI 与 REI](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44456705145)、[PAL](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44456705156)、[Controllable](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44456705173)均实际重新运行并通过。

- AK47 装 RC1：普通／简化 Iris 的第一人称边长比为 0.1993 / 0.2000，F5 均为 0.2000；测试标记像素分别从 4,021 降至 77、5,655 降至 140。不是上面原包纹理的颜色测量。世界近片为 add 时仍看得见远片（G=232 / 225），emissive 深度写入时 G=0 / 0。
- JEI / REI 的独立客户端重载后双向查询、打开页面分别有 16,348 / 15,643 个中心像素不同于世界对照（分母 17,160）。PAL 复测了离屏切枪和 F5 隔离，回到画面时支撑臂 Δz=4.00、Δroll=0.338。装 Controllable 的独立客户端通过了真实模组轮询的虚拟 SDL 输入、实际扣一发和震动回调。

**复查中还抓到一个独立的旧测试问题。** 两个真实光影作业在 `8de1322` 都通过，但[同次简化 Iris 作业](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44457885522/job/135337262885)在更早的 XL50 PIP 色条对照失败，不能把整组写成绿。[1× 输入](../../build-reports/visual/44457885522-tacz_lens_pip_1x.png)、[裸 PIP](../../build-reports/visual/44457885522-tacz_lens_pip_bare.png)、[开启镜片色调](../../build-reports/visual/44457885522-tacz_lens_pip_optical.png)中仍是同一条红墙，6× 重投影把 1 个源像素的边界差变成约 6 屏幕像素。两帧红条 q75 宽 48 / 42，旧窗口原红 4,861、相同坐标仍判红 4,102。`e98a328` 对**重投影**量完整色条宽度及去掉一源像素和分类边界后的内部，要求保留 ≥98%，并检查取错到左、右 3 个源像素时均不能通过。回算该红帧：内部 **3,847/3,847** 保留，故意错位只剩 **2,560 / 1,826**。经典路径仍用原来的重合断言，镜外、变暗量、实际 1×/6× 对照、热源和墙后负例都没放宽。没有为此改产品倍率或光学着色器。

**最终复跑 `e98a328`：** 普通客户端与三个 Iris 作业全部通过；上述旧 XL50 红灯保留原帧，不拿重试替代定位。原包 AK 的新增亮芯（彩光关／开）**449 / 110**，白 texel **203,206,207 / 203,207,208**；机匣击发 ΔRGB **21,21,21 / 23,23,24**，皮肤分别 **93,45,18 / 91,46,19**。两组 SVD 的手持、背挂、掉落、移位均与参考轮廓逐点吻合，多写／彻底移除后的残留均为零；故意换回旧材质在此次两个运行中均未重现额外条纹，不能拿它们覆盖上一轮已捕获的负例。简化 Iris 与普通客户端再次测到 RC1 宽高比约 0.2000。`fb67e68` 到 `e98a328` 只有测试、证据和 CI 变动，无 `src/main` 变更。

## 是否还有旧移植缺口？

没有用“资源齐”推出“全部行为等价”。本轮重新对照固定的 Fabric 1.21.1 旧树 `d290355`、1.2.0-hotfix 目标 `356bdf9`、原生 26.3 基线 `cc990c3` 和当前 `fb67e68`。四树分别 4,645 / 6,037 / 4,960 / 6,620 路径，合并 7,611。上游改过的 499 个 Java 路径中，34 已是目标 blob、6 仍是旧 blob、134 不在同路径、325 有本线改写；**134 不是缺了 134 项功能**。复算方法见[四树审计与旧线回移表](PORT_GAPS_26X_2026_11_18.md#复算只读不切换分支)。

| 重新核对的范围 | 能确认什么 | 仍不能确认什么 |
|---|---|---|
| 默认枪包及各版 | 本线 4,176 个有效目的地，0 缺、0 多，3,929 原样；229 索引（70 枪、126 配件、25 弹药、5 转接座、3 工作台）。246 个上游引用例外仍逐项记录。`f6678ba` 至本次没有改分发的默认枪包。 | 26.1.2（含 bundle）、26.2、普通 26.3、两棵 26.4 快照树各 3,322 个有效路径，相对目标缺 862、多 8；只读清点，不代表这些线已回移或跑过游戏。 |
| 内容消费、网络与画面 | 以前十二项缺口的生产入口、测试及演进见[验收账本](PORT_GAPS_26_3_VALIDATION.md)。这轮把以前没测出的两个真实光影缺口另加了像素／深度门槛。默认包验证、掉落／BE 静态检查、弹孔 6 项、viewer 合约 5 项、YSM 接线 11 项、LR 五脚本 46 个上下文方法及 3 项资源检查均重新通过。 | 静态 YSM 合约不是和真实 YSM 客户端联调；单机追踪实体不等于两台客户端。虚拟 SDL 回调不等于实体马达。 |
| 可选集成 | 仍没有 EMI 独立插件或 Sable／Simulated 移动子世界适配；AR 加速路径仍禁用。固定 Fabric 目标自己的三个 KubeJS 投递体也没启用。 | 不把 JEI/REI 写成 EMI、不把普通世界命中写成移动子世界支持，也没验证所有 26.X 的依赖组合。 |

边界：这些是软件渲染、固定视角和设置的可重复结果。没有测每个 GPU、所有 PBR／光影选项、Euphoria/DH、其他整合包或独立双客户端。枪身反射层的等深匹配不是任意重叠面都逐像素平滑的保证；原帧 AK 机匣近端可见局部细纹。上游对照的是内容和玩法语义，本次中性击发照明是本线针对 Iris 的适配，不是声称上游 1.21.1 已实现这一光影算法。

[before]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44454814995
[after]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44455729177

[off-final]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44457885522/job/135337262888
[act-final]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44457885522/job/135337262605

[jar]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44459203233/artifacts/14500160491
[green-build]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44459203233
[green-iris]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44459203207
[green-compile]: https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44459203219
