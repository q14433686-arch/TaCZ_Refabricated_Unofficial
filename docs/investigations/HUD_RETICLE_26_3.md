# 26.3 HUD 准星：尺寸、中心与击发反馈

入口：手持 TaCZ 枪械，**Esc → TaCZ 持枪与准星 → 准星**。默认仍是静态 `DOT_1`。这里的「动态」是准星**围绕原中心开合**，不是让准星位置追随后坐力，也不改变弹丸、后坐力、开镜零点或服务器脚本。

## 数值怎么读

| 控件 | 可调范围 | 单位与作用 |
|---|---:|---|
| 贴图尺寸 | 0.10–128.00 | GUI 像素，纵向比例 100% 时的边长 |
| 线长 / 中心间距 | 0.00–96.00 / −32.00–96.00 | 间距从中心区域的边缘算起，负值可进入该区域，最多收到轴线，不让左右臂反向 |
| 粗细 / 点径 | 0.05–32.00 / 0.00–48.00 | 点径为 0 表示自动：图形跟随粗细，替换贴图中心时为 1；要隐藏点请关闭中心点 |
| 圆环内半径 | 0.00–128.00 | 独立于线长、间距和点径；外半径为内半径＋粗细。内半径为 0 时可做实心圆 |
| 纵向比例 | 10–400% | 100% 的环是正圆；其他值拉伸内孔，保持横、纵轴上的线宽。中心点不拉伸 |
| 单发扩张量 | 0.25–32.00 | 每发对半径／半间距的视觉增量，默认 3.00，快速连发最多叠至三发幅度 |
| 回收时间 | 80–1500 ms | 默认 300 ms；快速连发从当前开度继续，不会每发先跳回原点 |
| 枪械散布比例 | 0–400% | 默认为 100%；0% 只关掉基于枪械数据的那部分，仍有每发开合 |
| 不透明度 | 0–255 | 8 位 alpha，0 全透明、255 不透明 |

几何量与单发扩张量可以输入两位小数；比例、毫秒和不透明度是整数。输入框可用 `.` 或 `,` 作小数点，分享码用 `.`。数值在浮点坐标中生效，不是把滑杆标签格式化后再取整绘制。**不是每次改 0.01 都会多亮一个屏幕像素**：这取决于 GUI 缩放和边界是否跨过像素采样中心。特别大的设计仅在预览卡片上等比缩小，不限制实际 HUD 尺寸。

## 为什么改中心

反馈原图为 2560×1417。取中央亮白像素（RGB 最小通道 > 210，最大最小通道差 < 23），以屏幕左上为像素下标 `(0,0)`：

| 部件 | x 范围 | y 范围 |
|---|---:|---:|
| 左 / 右刻度 | 1256–1263 / 1300–1307 | 708–711 |
| 上 / 下刻度 | 1280–1283 | 684–691 / 728–735 |
| 点心 | 1277–1281 | 706–709 |

四条刻度的轴线交在 `(1281.5,709.5)`；点心亮部重心约为 `(1279.25,707.38)`。不需要假设画了第二套准星：原先一像素线宽的整数半宽被截成 0，贴图又用了另一套整数半宽。修正是用 **`(帧缓冲宽, 高) / (2 × GUI scale)`** 作统一原点，左右／上下按正负半宽配对。线条、点、贴图 UV 和环都在同一局部坐标下提交，不针对某个分辨率加补偿。任意资源包如果在 PNG 里把图案本身画偏，仍会保留该图案。

## 为什么分成两个动态量

- **枪械散布提示**：按手中枪的姿态、射击模式／配件缓存及热量取客户端已有的散布，由世界视野投影到 GUI。此项不调用 `calcSpread` 等服务端脚本，不预测随机落点。
- **开火反馈**：只观察本地玩家客户端的 `GunFirePostEvent`（包括 Burst 的各个子发），不是鼠标按下，也不是追踪其它玩家的网络通知。独立的动画在 65 ms 内渐开，之后按设置时间渐收，按暂停感知的游戏时钟采样；换枪、离开会话时清掉。静态模式不采样、不积累。试播用编辑器自己的时钟和状态，在暂停菜单里也能看，不发包。
- 十字／T／双线／框拉开间距，环扩大内孔但不加粗；内置点／纯点设计保持点心大小，四个外部刻度移动。其他预设或 PNG 保留原有的以中心向外缩放方式。原有的 ADS、换弹、改装、相机可见性规则优先于这些效果。

## 对照来源与边界

- **CF 端游，不是手游或 CFHD**：[腾讯版本说明](https://cf.qq.com/webplat/info/news_version3/125/860/862/m640/202307/937743.shtml)、[策划对旧版扩散手感的回应](https://cf.qq.com/webplat/info/news_version3/125/860/m640/202309/940507.shtml)和[官方 PC 指南的「准星设置」](https://cf.qq.com/cp/a20241120mguide/pc/index.html)。参考的是可调颜色、线长、粗细、外围线、中心间距，圆形／十字加点，静态开关和代码分享。可核实的自定义设计说明始于 2023 年；[2026 年 1 月的端游更新](https://cf.qq.com/webplat/info/news_version3/125/860/862/m640/202601/981357.shtml)是给匠心工坊加了关闭准星的能力，不是重写玩家编辑参数。
- **CS2，而非旧 CS:GO CVars**：Valve [2026-09-23 更新](https://www.counter-strike.net/news/updates)解释新版像素长度／厚度、分辨率缩放、零间距、静态方形和动态散布显示上限；[09-30 更新](https://store.steampowered.com/news/app/730/view/675133527946166402)又补了经典动态负间距、描边颜色和分享码字段。TaCZ 用 Minecraft **GUI 像素**；这里的 0.01 几何精度是本编辑器的设计，不是把 Valve 所说的 **alpha 步长 0.01** 当作线宽，也不照搬其真实散布算法或分享码。
- **Minecraft 模组**：读了 MrCrayfish's Gun Mod 1.19.X 的 [`CrosshairHandler.onGunFired`](https://github.com/MrCrayfish/MrCrayfishGunMod/blob/2ddb4d43f567e23dbb2073a5b93dcea5decfbe0d/src/main/java/com/mrcrayfish/guns/client/handler/CrosshairHandler.java) 与 [`TechCrosshair`](https://github.com/MrCrayfish/MrCrayfishGunMod/blob/2ddb4d43f567e23dbb2073a5b93dcea5decfbe0d/src/main/java/com/mrcrayfish/guns/client/render/crosshair/TechCrosshair.java)。它把击发后事件传给准星，在点心外单独画动画，按 tick 衰减并插值。沿用「击发通知与视效分开」的思路；这里按经过的时间做渐开和有界回收，不复制其 Forge 渲染器、素材或数值。
- **另一个对照是 ModularWarfare**：[`GunUI`](https://github.com/ModularMods/ModularWarfare/blob/3d3b929d9ae93e15c75a35c6f32e3f2c7b656bca/src/main/java/com/modularwarfare/client/hud/GunUI.java#L154-L198) 分开画固定点与四条外线；杆距由 [`calculateAccuracyClient`](https://github.com/ModularMods/ModularWarfare/blob/3d3b929d9ae93e15c75a35c6f32e3f2c7b656bca/src/main/java/com/modularwarfare/utility/RayUtil.java#L74-L102) 的枪械、移动和姿态等因素算出。它的动态旋转是另一回事。固定点与可动外线值得借鉴，但只看这个精度值不能满足「站定、散布不变，开火也要张开」；所以在 TaCZ 单独监听每发击发，而不让整套准星跟着枪模转。

## 兼容与验证

`TACZ-HUD2:` 在 HUD1 字段之后增加圆环内半径、单发扩张量和回收毫秒。HUD1 按它原有的整数格式解析，新字段取默认值；`TACZ-POS1:` 不变。旧 `tacz-client.toml` 的键名未变，几何项由数值读取，整数也能使用。导入先校验所有字段，只更新当前页签的草稿，保存才写盘。

`ShotEnvelopeTest` 按毫秒检查渐开、渐收、连发叠加和不同采样频率。`ViewTuningSmoke` / `HudPrecisionSmoke` 驱动真实的编辑器、剪贴板、磁盘配置和游戏 HUD，包括奇偶尺寸下的原图点心＋四个轴、圆与椭圆孔、跨像素的 0.02 调整，以及客户端散布固定为零时实际三连发与静态／取消／空击的对照。运行结果以对应源码的 build 和 Iris 客户端日志为准。

### 本次结果（源码 `e2380d1`）

[26.3 完整构建、JUnit、服务端与原生 Mesa 客户端](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44475620919)和 [Iris 客户端矩阵](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44475621065)均通过。HUD 像素断言在原生客户端和 Iris 基础着色包上各执行一次；两个 Complementary Reimagined 配置运行的是原有光影回归，不算两次额外的 HUD 测量。

| 对照 | 两个 HUD 客户端的结果（物理像素） |
|---|---|
| 原图尺寸 **2560×1417、GUI 4×**，原生 `DOT_1`＋四条刻度，仅将 HUD 染为品红以便从背景分离 | 亮部点心 `(1279.25,708.375)`，纵、横刻度轴 `(1279.5,708.5)`；相差 **0.25 / 0.125**，而反馈图为 **2.25 / 2.125**。几何中心是同一个，不把边界取样产生的小数差说成零。|
| 853×481、GUI 2×，只把中心点径从 2.99 改成 3.01 | 点的覆盖从 **25 → 49** 像素；若在 GUI 层四舍五入，这两帧会完全相同。|
| B93R 实际三连发，客户端姿态散布缓存各项均为 0 | 圆环宽 **44 → 130 → 44**（待机／三连发后截帧／回收完毕）。130 是截帧宽度，不是所有配置的固定峰值。静态、取消击发和空击对照通过。|
| 单独的枪械散布提示 | AK 站立、潜行、零缓存的投影半径分别约为 **13.29 / 7.38 / 0 GUI 像素**。ADS 和不允许显示的第三人称场景均没有绘出调色用的 HUD 像素。|

[同尺寸中心裁剪](../../build-reports/visual/44475620919-tacz_view_tuning_reported_crop.png) · [游戏内圆环编辑器](../../build-reports/visual/44475620919-tacz_view_tuning_ring_editor.png) · [圆环](../../build-reports/visual/44475620919-tacz_view_tuning_ring_rest.png) / [无点椭圆环](../../build-reports/visual/44475620919-tacz_view_tuning_ring_ellipse.png) · 三连发原帧：[开火前](../../build-reports/visual/44475620919-tacz_view_tuning_burst_rest.png) → [张开](../../build-reports/visual/44475620919-tacz_view_tuning_burst_open.png) → [回收](../../build-reports/visual/44475620919-tacz_view_tuning_burst_return.png)。
