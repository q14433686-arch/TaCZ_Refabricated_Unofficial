# 26.3 HUD 准星：尺寸、中心与击发反馈

入口：手持 TaCZ 枪械，**Esc → TaCZ 持枪与准星 → 准星**。默认仍是静态 `DOT_1`。这里的「动态」是准星**围绕原中心开合**，不是让准星位置追随后坐力，也不改变弹丸、后坐力、开镜零点或服务器脚本。

## 共用客户端时如何分开设置

持枪 XYZ 是每个账号一份；HUD 的查找顺序是 **具体枪包 ID → 枪包索引里的 `type` → 账号默认**。不按物品注册名（许多枪共用同一种物品）、翻译显示名或服务器地址分配。打开「准星 → 分配」，选当前手持，或按类别、枪名／ID 查找；样式、颜色、开火反馈、PNG 模式与 HUD 分享码作用于所选目标。覆盖保存的是**完整样式**。浏览不会创建覆盖；「移除覆盖 · 恢复继承」才会重新跟随上一级。按 Esc／取消，连待删除的覆盖也不会生效。

例如：给 `rpg` 类别设圆环，再给 `tacz:m320` 单独导入下面的静态阶梯。`tacz:rpg7` 仍用圆环，步枪也不会跟着变。阶梯没有标距，用线长、粗细、间距、纵向比例和颜色控制外观，不读取弹道。旧版 HUD2 能导入这里已有的形状；不认识 `LADDER` 的旧客户端不能导入这个新形状。

```
TACZ-HUD2:DESIGNER,DOT_1,LADDER,ORIGINAL,1,FF00FF,16.00,8.00,3.00,1.00,100,2.00,255,0,6.00,3.00,300
```

本地文件：`config/tacz/view-profiles/<启动器账号 UUID>.json`。旧 TOML 只用作尚无个人档案的起点。两个账号起初可能一样，但任一个的保存不会修改另一个的起点或文件。换服务器不会创建新档案；离线启动器若重复使用同一 UUID，本功能无法区分，需改用不同启动器身份或不同实例目录。没有账号登录凭据或服务器数据写入配置。个人文件损坏或版本未知时不自动覆盖：退出游戏，先备份再移走该文件，下次可从旧 TOML 或分享码重新开始。其它客户端选项仍使用原 TOML。

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
- 十字／T／双线／框／阶梯拉开间距，环扩大内孔但不加粗；内置点／纯点设计保持点心大小，四个外部刻度移动。其他预设或 PNG 保留原有的以中心向外缩放方式。原有的 ADS、换弹、改装、相机可见性规则优先于这些效果。

## 对照来源与边界

- **CF 端游，不是手游或 CFHD**：[腾讯版本说明](https://cf.qq.com/webplat/info/news_version3/125/860/862/m640/202307/937743.shtml)、[策划对旧版扩散手感的回应](https://cf.qq.com/webplat/info/news_version3/125/860/m640/202309/940507.shtml)和[官方 PC 指南的「准星设置」](https://cf.qq.com/cp/a20241120mguide/pc/index.html)。参考的是可调颜色、线长、粗细、外围线、中心间距，圆形／十字加点，静态开关和代码分享。可核实的自定义设计说明始于 2023 年；[2026 年 1 月的端游更新](https://cf.qq.com/webplat/info/news_version3/125/860/862/m640/202601/981357.shtml)是给匠心工坊加了关闭准星的能力，不是重写玩家编辑参数。
- **CS2，而非旧 CS:GO CVars**：Valve [2026-09-23 更新](https://www.counter-strike.net/news/updates)解释新版像素长度／厚度、分辨率缩放、零间距、静态方形和动态散布显示上限；[09-30 更新](https://store.steampowered.com/news/app/730/view/675133527946166402)又补了经典动态负间距、描边颜色和分享码字段。TaCZ 用 Minecraft **GUI 像素**；这里的 0.01 几何精度是本编辑器的设计，不是把 Valve 所说的 **alpha 步长 0.01** 当作线宽，也不照搬其真实散布算法或分享码。
- **Minecraft 模组**：读了 MrCrayfish's Gun Mod 1.19.X 的 [`CrosshairHandler.onGunFired`](https://github.com/MrCrayfish/MrCrayfishGunMod/blob/2ddb4d43f567e23dbb2073a5b93dcea5decfbe0d/src/main/java/com/mrcrayfish/guns/client/handler/CrosshairHandler.java) 与 [`TechCrosshair`](https://github.com/MrCrayfish/MrCrayfishGunMod/blob/2ddb4d43f567e23dbb2073a5b93dcea5decfbe0d/src/main/java/com/mrcrayfish/guns/client/render/crosshair/TechCrosshair.java)。它把击发后事件传给准星，在点心外单独画动画，按 tick 衰减并插值。沿用「击发通知与视效分开」的思路；这里按经过的时间做渐开和有界回收，不复制其 Forge 渲染器、素材或数值。
- **另一个对照是 ModularWarfare**：[`GunUI`](https://github.com/ModularMods/ModularWarfare/blob/3d3b929d9ae93e15c75a35c6f32e3f2c7b656bca/src/main/java/com/modularwarfare/client/hud/GunUI.java#L154-L198) 分开画固定点与四条外线；杆距由 [`calculateAccuracyClient`](https://github.com/ModularMods/ModularWarfare/blob/3d3b929d9ae93e15c75a35c6f32e3f2c7b656bca/src/main/java/com/modularwarfare/utility/RayUtil.java#L74-L102) 的枪械、移动和姿态等因素算出。它的动态旋转是另一回事。固定点与可动外线值得借鉴，但只看这个精度值不能满足「站定、散布不变，开火也要张开」；所以在 TaCZ 单独监听每发击发，而不让整套准星跟着枪模转。

## 兼容与验证

`TACZ-HUD2:` 在 HUD1 字段之后增加圆环内半径、单发扩张量和回收毫秒。HUD1 按它原有的整数格式解析，新字段取默认值；`TACZ-POS1:` 不变。旧 `tacz-client.toml` 的键名未变，几何项由数值读取，整数也能使用。导入先校验所有字段，只更新当前页签的草稿，保存才写盘。

`ShotEnvelopeTest` 按毫秒检查渐开、渐收、连发叠加和不同采样频率。`ViewTuningSmoke` / `HudPrecisionSmoke` 驱动真实的编辑器、剪贴板、磁盘配置和游戏 HUD，包括奇偶尺寸下的原图点心＋四个轴、圆与椭圆孔、跨像素的 0.02 调整，以及客户端散布固定为零时实际三连发与静态／取消／空击的对照。运行结果以对应源码的 build 和 Iris 客户端日志为准。

### 中心与击发反馈的前一轮基线（源码 `e2380d1`）

[26.3 完整构建、JUnit、服务端与原生 Mesa 客户端](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44475620919)和 [Iris 客户端矩阵](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44475621065)均通过。HUD 像素断言在原生客户端和 Iris 基础着色包上各执行一次；两个 Complementary Reimagined 配置运行的是原有光影回归，不算两次额外的 HUD 测量。

| 对照 | 两个 HUD 客户端的结果（物理像素） |
|---|---|
| 原图尺寸 **2560×1417、GUI 4×**，原生 `DOT_1`＋四条刻度，仅将 HUD 染为品红以便从背景分离 | 亮部点心 `(1279.25,708.375)`，纵、横刻度轴 `(1279.5,708.5)`；相差 **0.25 / 0.125**，而反馈图为 **2.25 / 2.125**。几何中心是同一个，不把边界取样产生的小数差说成零。|
| 853×481、GUI 2×，只把中心点径从 2.99 改成 3.01 | 点的覆盖从 **25 → 49** 像素；若在 GUI 层四舍五入，这两帧会完全相同。|
| B93R 实际三连发，客户端姿态散布缓存各项均为 0 | 圆环宽 **44 → 130 → 44**（待机／三连发后截帧／回收完毕）。130 是截帧宽度，不是所有配置的固定峰值。静态、取消击发和空击对照通过。|
| 单独的枪械散布提示 | AK 站立、潜行、零缓存的投影半径分别约为 **13.29 / 7.38 / 0 GUI 像素**。ADS 和不允许显示的第三人称场景均没有绘出调色用的 HUD 像素。|

[三连发中心裁剪对照（同倍率）](../../build-reports/visual/44475620919-hud-shot-comparison.png) · [同尺寸中心裁剪](../../build-reports/visual/44475620919-tacz_view_tuning_reported_crop.png) · [游戏内圆环编辑器](../../build-reports/visual/44475620919-tacz_view_tuning_ring_editor.png) · [圆环](../../build-reports/visual/44475620919-tacz_view_tuning_ring_rest.png) / [无点椭圆环](../../build-reports/visual/44475620919-tacz_view_tuning_ring_ellipse.png) · 三连发原帧：[开火前](../../build-reports/visual/44475620919-tacz_view_tuning_burst_rest.png) → [张开](../../build-reports/visual/44475620919-tacz_view_tuning_burst_open.png) → [回收](../../build-reports/visual/44475620919-tacz_view_tuning_burst_return.png)。

### 账号、分配与阶梯（源码 `de43f82`）

[完整构建／原生 Mesa 客户端](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44482591156/job/135418163014)与 [Iris 基础着色包客户端](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/actions/runs/44482591124/job/135418280431)在 GUI 2× 均通过：

- 同目录下两枚**测试 UUID** A→B→A 重读，分别保留各自的 XYZ、默认 HUD 和单枪覆盖；拒绝旧编辑器身份的保存，遇到未知档案版本也不覆盖原件。这不是用两枚真实账号登录私人服的测试。
- 原生编辑器给 `rpg` 设静态圆环，给 `tacz:m320` 设阶梯。切到 M320 验证三档横线，切到 `tacz:rpg7` 验证仍是圆环；取消删除不改档，保存删除后先回到类别，再回到账号默认。
- 账号默认**静态**、B93R 单枪覆盖**动态**：真实三连发且客户端散布固定为零时，圆环截帧外径为 **44 → 132 → 44** 物理像素。静态覆盖、取消与空击对照通过；继承层级不仅在编辑器显示，也参与本地开火事件的选择。

[两把枪的准星对照](../../build-reports/visual/44482591156-hud-rules.png)取同一构建两张 854×480 原始画面的同一中心区域，最近邻放大 2 倍；没有重绘准星。原帧中 M320 三道横线从上到下分别占 **16、24、32 个物理像素**。未裁剪：[M320 单枪分配页](../../build-reports/visual/44482591156-tacz_view_tuning_m320_rule.png) · [M320 阶梯](../../build-reports/visual/44482591156-tacz_view_tuning_m320_ladder.png) · [RPG-7 类别圆环](../../build-reports/visual/44482591156-tacz_view_tuning_rpg7_type.png)。
