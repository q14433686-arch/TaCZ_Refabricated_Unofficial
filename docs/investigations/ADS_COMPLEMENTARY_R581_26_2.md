# 26.2：Complementary Reimagined r5.8.1 的开镜红板与热成像缺面

这是两种不同的故障。截图可以用来比对形状，不能仅凭截图认定瞄具 ID、热目标物种或某个粒子出错。下文的像素对照来自运行中的 Minecraft 26.2 客户端、第一人称实际持枪开镜；不是物品栏图标或 CPU 投影示意图。

## 复现

固定 Fabric / Iris **1.11.2** / Sodium **0.9.1**，r5.8.1 使用[上游源码 `08e1c2a`](https://github.com/ComplementaryDevelopment/ComplementaryReimagined/tree/08e1c2ada5eaf2fc36f08516c316b3d3c3677d8e)的默认选项。`scripts/prepare_iris_smoke.py` 校验下载摘要并写出运行清单。测试不将光影包装入 mod、仓库或证据包。

[仅加测试的基线 `2604e9d`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44663486273)：

- QMK152 镜内最大连续红方块边长为 **31 px**，抑制分划后反而是 **48 px**；标准 8× 的两档分别是 **36 / 59 px**，抑制分划后仍在。不是简单把红色准星画得太大。绑定的资源要按 **index → display → model** 查找：`scope_standard_8x` 实际指向 `scope_scout_display`，不是同名模型。
- 两款 DeltaPoint 的正常帧和去分划帧在测量区毫无区别。它们的 UV 是 11×11 纹素的透明小十字，只有 16 个着色纹素。Vudu 的副瞄具更小，采样的是单个红纹素。
- REAP-IR 2.5×、16 格，暴露正面取样原为 **0/560、0/560、16/560** 个热像素；36 格则三次都是 **0/90**。基线随后卡在 2.5× 等待 PIP（默认门槛是 4×），并未跑完 XL50 或双模式矩阵。

## 渲染路径

1. **镀膜是镜身材质，不是分划。** 例如 QMK152 的 `scope_body/lens_illuminated` 含三片旋转的、不透明红纹理几何；实际标记在 `division` 下。Iris 替换手部着色程序之后，按原 Blaze3D 管线的 fragment shader 和 `SCOPE_MASK` / `SCOPE_MASK_INVERT` 标志传递裁剪方向。这样发光镜身也在镜孔内 discard；不裁借用同一个 Iris 程序的普通模型。
2. **恢复分划不是补一个 HUD 红点。** 冻结当前视角配对的枪包分划节点 `division`，分开提交受光的刻线与自发光子树。光影收工后沿真实手持投影、在真实镜孔内重画，保留材质、UV、位置和目镜框。对细线按像素覆盖率采样；扩大的是光栅采样域，不是标记本身，片元不取原四边形外的颜色。缺失的 `face_uv` 也不能把骨骼 pivot 撑进镜孔凸包（例如 RMR）。
3. **热像缺面是深度归属问题。** 该包的 `gbuffers_entities` 为 `FLICKERING_FIX` 中的 slime/chicken 移动 clip Z。正对相机的面没有斜率，普通 polygon offset 不能还原它。在真实实体模型/UV/姿势的热覆盖重放中，对该位移的表面另作一次 **EQUAL、不写深度**的匹配；固定功能深度测试用实际场景深度的私有副本。普通面与位移面都读取原场景的深度，不再用斜率无上限的 `glPolygonOffset`：接近侧立的面曾被它拉到墙前，PIP 又会把少数漏点放大。先容许深度量化舍入；更大的差值只在两个有符号平面梯度相符、且未超出当前投影下的亚像素上限时对齐，其余片元仍走真实深度比较。读取的纹理和写入的附件不是同一张。材质透明孔和热标签仍然有效。不按光影文件名开关、不填实体包围盒，也不把其他物体后的模型无条件画成白色。

## 怎么复查

运行 [ADS on Complementary r5.8.1](../../.github/workflows/shader-optics.yml)。每个 job 从游戏注册表选合法宿主，等服务端与客户端的物品、倍率档和 ADS 状态就绪，再比较帧缓冲。逐档核对的是客户端实际选取的倍率和视角；条纹宽度对照另在 XL50 的原生／Iris 回归中执行，不是说这 59 档都做了像素级倍率标定。工作流保留原始 PNG（含负对照）、`latest.log` 与 `manifest.json`；`FRAME` / `HEAT` 注释是逐视图的测量值。

| 组 | 证据 |
|---|---|
| `scopes`、`sights` | 全部 **46** 个默认光学/机械瞄具、**59** 个配置档（34 + 25），包括虚拟 OEM。正常帧对去分划帧；机械瞄具有两项独立对照：移除机械瞄具后消失的暗色瞄具实体、把蓝色背景换绿后在瞄准线附近随之变色的空隙；筒镜另取去镀膜对照。测连续红块而不是统计一切红像素。混合分划还测中心点以外的刻线。 |
| `pip` | 打开光影 PIP；按默认 4× 门槛逐一检查 20 个符合条件的档位。 |
| `report-size`、`report-marks` | **2560×1417** 再测 QMK152、标准 8× 两档、LPVO 最低档、Vudu 两档、两款 DeltaPoint、RMR；还要求有来自分划而非背景的着色笔画。 |
| `thermal-reap`、`thermal-xl50` | REAP-IR、XL50 各两档 × 整屏变焦 / 镜内重投影 × 16 / 36 格 × 0° / 45° / 90°。读取真正的渲染姿势；既测正面内区域，也对照同一实体的可见光画面与移开实体的空景。放入实心墙，独立读取热掩码和叠加显示字**以前**的传感器画面，避免把白字或准星的移动当成透视。为覆盖 REAP-IR 2.5× 的 PIP，只在此测试里降低启动门槛。 |

这些是 Mesa 软件 OpenGL 上的定向回归，不等同于所有 GPU、第三方枪包、任意光影设置或所有可选渲染后端的实机验收。默认枪包文件、PIP 默认门槛与发布版本均未改动。

## 验收提交 `a119e32`

[同一提交的 r5.8.1 矩阵](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44671407742)全部通过。保留两份由该次 Checks 注释提取的读数（不含美术资产）：

- [逐瞄具、宿主与倍率档，含 PIP 和报告分辨率的重复测量](ADS_R581_26_2_VIEWS.tsv)。`red_square_px` 是连续红方块的边长；细红线并不要求降为零。`coating_delta` 对比已去分划和再去镀膜的两幅图，`mark_delta` 则比较保留／去掉分划（机械瞄具比较装／卸）。
- [两只热瞄具的全部 48 个测量](ADS_R581_26_2_HEAT.tsv)。真实模型 yaw 与设定角对应；在独立可见光参考中识别出的有效目标像素里，呈热色的比例最低约 **97.6%**（6,718 / 6,882），中心正面取样最低约 **86.7%**（482 / 556）。每次遮挡时，叠显示字前的墙面漏热和整张热掩码的亮像素均是 **0**。`wall_display_bright` 允许已完成画面中的白色显示笔画，不是热信号。

同一画幅下，基线与修复后的直接对照：

| 项目 | `2604e9d` | `a119e32` |
|---|---:|---:|
| QMK152，最大红方块边长（正常／去分划） | 31 / 48 px | 2 / 0 px |
| 标准 8×，4.5× 档；同上 | 36 / 36 px | 2 / 0 px |
| 标准 8×，10× 档；同上 | 59 / 59 px | 3 / 0 px |
| REAP-IR 2.5×，经典模式，16 格；正面热色／有效取样（0°、45°、90° 三帧） | 0/560，0/560，16/560 | 538/538，545/545，542/542 |

基线的三个角度是请求值；只有最终矩阵还逐个确认了提取到的实际模型朝向。在报告的 2560×1417 下，QMK152 去分划后红方块也是 0；保留分划时还能测到 **1,597** 个中心以外的刻线像素。因此结果不是靠把标记一起删掉得到的。

[原生 26.2 客户端回归](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44671407715)、[Iris 回归光影与 r5.9.3 彩光 0 / 128](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44671407713)通过。彩光 0 和 128 两组都开着光影：AMG10 前两档的粉色粒子在叠分划前均为 0；开放档分别为 1,833 / 1,844，叠分划后为 1,828 / 1,840。

校验[构建](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44671407734)的 **JAR 本体**与[独立双端 TCP 测试](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44670464350)（`24eedb2`）所用文件，二者 SHA-256 均为 `d7aa89ea70929d4e745892efe36ffc7f6f5b07ef130a36a5846508ce38f1e388`。[下载此测试构建](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44671407734/artifacts/14599942773)（Actions 有保留期限；不是 Release）。
