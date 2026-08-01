# 第 22 轮进度报告 —— 状态效果图标 + UI 还原度核查

**日期**：2026-08-02
**触发**：社区反馈「耳鸣/致盲没有图标」「热度条、合成台按钮、持枪交互提示不是 100% 还原」

**方法**：对照上游 `Sh1roCu/TACZ-Refabricated` 的 `1.21.1` 分支逐文件比对，
26.2 侧结论一律以 `minecraft-merged-6f7fc6e6bc-26.2.jar` 字节码为准。

---

## 零、总览

| 项 | 判定 | 处置 |
|---|---|---|
| ① 耳鸣无图标 | 成立，从来没有过这张贴图 | 算法生成，见 §1 |
| ② 致盲无图标 | 成立 | 用 atlas 别名复用原版 blindness，**不复制 Mojang 美术资源**，见 §2 |
| ③ 热度条不还原 | **成立且严重** —— 整个控件被换成了自创 UI，两张上游贴图从未被引用 | 按上游重写，见 §3 |
| ④ 合成台按钮不还原 | **成立** —— 9 个贴图按钮全被换成原版灰底按钮 | 移植 ImageButton 并全部改回，见 §4 |
| ⑤ 持枪交互提示不还原 | **成立** —— 丢了 4 处行为，含一整条从未被引用的 lang key | 按上游重写，见 §5 |
| ⑥ 附带发现 | `ChatFormatting#getColor()` 在 26.2 **已不存在** | 见 §6，已写入排查经验 |

---

## 一、耳鸣图标：算法生成

按要求「随便用算法生成一个 MC 风格的耳朵」，脚本保留在 `tools/gen_deafened_icon.py`，
输出 `assets/lrtactical/textures/mob_effect/deafened.png`（18×18 RGBA，与原版同规格）。

**几次失败的尝试值得记下来**，避免后人重走：

1. 直接拿椭圆布尔运算堆「耳廓 + 耳甲 + 耳道」→ 得到的是<b>同心圆环</b>，
   看起来像甜甜圈/木桩，完全不像耳朵。
2. 手写像素画 ASCII 图 → 同样的同心圆问题。
3. 加螺旋曲线描边做对耳轮 → 18×18 扣掉 1px 描边后只剩 16×16，
   螺旋在这个尺度上糊成一团。

**真正的关键**是：耳朵之所以能被认出来，靠的是<b>开口的 "C" 形</b> ——
耳屏间切迹让耳甲腔在<b>右下方连通到外界</b>，把轮廓从闭合环变成开口 C。
代码里就是 `mouth()` 那个函数；加上它之后一次就对了。

其余实现要点：
- 超采样（8×8/像素）取多数覆盖 → 边缘干脆，没有抗锯齿毛边（像素画忌讳）；
- 形态学清理：删 ≤1 邻居的毛刺、补 ≥3 邻居的凹坑，迭代到稳定 —— 椭圆布尔必留这类瑕疵；
- 固定左上光源，5 级肤色 + 1 级描边，与原版 mob_effect 图标的用色规模一致。

图标已在放大预览下逐版目视确认，最终版本可辨认为耳朵。

---

## 二、致盲图标：atlas 别名，不复制原版贴图

要求是「直接复用失明的图标」。最直接的做法是把 `blindness.png` 拷进我们的资源目录，
但那等于**在本仓库里再分发 Mojang 的美术资源**，与仓库既有的资源政策冲突
（见 README「原作美术资源标注为 All Rights Reserved，本仓库不打包、不再分发」）。

26.2 提供了不用复制就能达到同样效果的机制，三条字节码事实支撑：

1. `Hud#getMobEffectSprite` → `id.withPrefix("mob_effect/")`，
   即效果图标就是 GUI 图集里名为 `<ns>:mob_effect/<path>` 的精灵；
2. `SpriteSourceList#load` 用的是 `ResourceManager#getResourceStack`，
   会把**所有资源包里同 id 的 atlas 定义合并**（`addAll`），
   所以往 `assets/minecraft/atlases/gui.json` 追加 source **不会**覆盖原版那两条；
3. `SingleFile` 的 codec 有 `resource` 与 `sprite` 两个字段，
   `run()` 里 `output.add(spriteId.orElse(resourceId), resource)` ——
   **读原版的贴图文件，注册成我们自己的精灵名**。

于是新增 `assets/minecraft/atlases/gui.json`，把
`lrtactical:mob_effect/blinded` 指向 `minecraft:mob_effect/blindness`。
原版贴图缺失时 `SingleFile#run` 只会 warn 一行 `Missing sprite`，不崩。

> 耳鸣不走这条路：它有自己的 png，由原版 `gui.json` 里那条
> `prefix=mob_effect/` 的 `DirectoryLister` 自动收录 ——
> `DirectoryLister#run` 走 `listMatchingResources`，**扫所有命名空间**（字节码确认），
> 所以 `lrtactical` 下的贴图不需要任何额外注册。

---

## 三、热度条：整个控件此前是自创 UI

这条比反馈者说的严重得多。

**贴图从来没被引用过。** `heat_base.png` / `heat_bar.png` 两张图静静躺在
`assets/tacz/textures/hud/` 下，与上游 **md5 完全相同**（`7cc001d7` / `47a9d233`），
但移植版的 `HeatBarOverlay` 里一次都没提到它们。

**旧实现画的是**：屏幕右下角一条 104×5 的纯色矩形 + `outline` 描边，
位置 `(width-116, height-58)` 附近。

**上游画的是**：屏幕**正中**（准星周围）的一整套仪表 ——
128×128 的 `heat_base` 底图、中心下方 60px 宽的热度条、
底部居中的百分比文字，且整体随热度在 0.75..0.875 之间平滑伸缩。

也就是说不是"像素级偏差"，是控件被整个换掉了。已按上游重写，逐项对齐：
位置、底图 UV、条的坐标与长度、缩放迟滞（升 +0.05 / 降 -0.025 那组不对称阈值）、
`fontFilterFishy` 带阴影文字、`!OVERHEAT!` 文案、红黄交替闪烁。

26.2 的两处必要改写：
- `RenderSystem.setShaderColor` 已移除（上游靠它给底图整体染色）→
  改用 26.2 `blit` 末位的 `tint` 参数，语义等价且不依赖全局状态；
- `graphics.pose()` 由 `PoseStack` 变为 `Matrix3x2fStack`，`scale` 只收两个分量。

tick 源：上游 `mc.gui.getGuiTicks()`，26.2 该方法搬到了 `Hud`
（`Gui#hud` 公开字段 + `Hud#getGuiTicks()` 公开方法，均已确认）→ `mc.gui.hud.getGuiTicks()`。
用 GUI tick 而非 `player.tickCount`，暂停时闪烁才会停，与上游一致。

---

## 四、合成台按钮：9 个贴图按钮全被换成了原版灰底按钮

上游这些按钮走它自带的 `ImageButton`，UV 直接指向 `gun_smith_table.png` 里画好的图案。
移植时全部替换成了原版 `Button` + ASCII 标签（`^ v < > + - R URL`）。
贴图本身与上游 md5 相同（`bef0fc31`），只是没人采样。

新增 `client/gui/components/TaczImageButton`（三态 UV：普通 / 悬停 / 禁用，
与上游 `ImageButton#renderTexture` 同约定），9 处全部改回：

| 按钮 | 位置 | 尺寸 | UV |
|---|---|---|---|
| 制造 | `+289,+162` | 48×18 | 138,164 |
| URL | `+112,+164` | 18×18 | 149,211 |
| 配方上翻 / 下翻 | `+143,+56` / `+143,+171` | 96×6 | 40,166 / 40,186 |
| 分类左翻 / 右翻 | `+136,+4` / `+327,+4` | 18×20 | 0,162 / 20,162 |
| 放大 / 缩小 / 复位 | `+5/+17/+29,+5` | 10×10 | 188 / 200 / 212,173 |

**顺带纠正第 15 轮的一处误判。** 那一轮记录了「"制造"叠字成 Cmilaft」，
处置是删掉手动绘制的那一行。真因其实是：上游的贴图按钮标签是
`CommonComponents.EMPTY`（自身不画字），面板上的「制造」二字**一直**由
`render` 里那句 `drawModCenteredString` 负责；移植时换成带标签的原版 Button，
才多出第二处绘制造成重叠。删手动那行属于对症不对因 —— 叠字没了，但外观仍不对。
现在恢复贴图按钮后，已按上游把那一行补回。

---

## 五、持枪交互提示：丢了 4 处行为

对照上游，旧实现是一个被简化过头的重写：

1. **整条「按手中物品过滤工作台」提示消失。** 上游瞄准工作台且
   `AUTO_SELECT_GUN_SMITH_TABLE_FILTER` 开启时，会在主提示下方多画一行灰字。
   该 lang key `gui.tacz.interact_key.text.gun_smith_table_filter`
   **20 个语言文件里全都有**，却没有任何代码引用 —— 翻译白翻了。
2. **颜色错**：上游黄色（可交互的视觉信号），旧实现白色。
3. **位置错**：上游在准星**上方** `h/2-25`，旧实现在下方 `h/2+44`
   （该位置在 16:9 下会与弹药 HUD 打架）。
4. **空手时不再提示**：上游有专门分支 —— 瞄准工作台、手里有枪/配件/弹药但
   **未持枪**时，用**原版使用键**而非 TACZ 交互键提示（这种情况是右键开台子）。
   旧实现开头就 `!mainHandHoldGun return`，把这条路堵死了。

另外上游对键名做 `StringUtils.capitalize`（`r` → `R`），旧实现直接塞 `Component`，
小写键名看着像 bug。一并对齐。

---

## 六、【新坑】`ChatFormatting#getColor()` 在 26.2 已不存在

照抄上游的 `ChatFormatting.YELLOW.getColor()` 时发现**编译不过**。
字节码确认：26.2 的 `ChatFormatting` 枚举只剩 `code`（char）与 `toString` 两个字段，
**没有任何颜色访问器**。颜色搬到了 `net.minecraft.network.chat.TextColor` 的具名常量上，
取值用 `getValue()`：

```java
ChatFormatting.YELLOW.getColor()   // 1.21.1 ✔   26.2 ✘ 方法不存在
TextColor.YELLOW.getValue()        // 26.2 ✔ = 0xFFFF55
TextColor.GRAY.getValue()          // 26.2 ✔ = 0xAAAAAA
```

且返回的仍是**六位色**，`GuiGraphicsExtractor#text` 会因 alpha=0 整段丢弃
（`PORTING_NOTES.md §1` 的老坑），必须自己 `| 0xFF000000`。

已全仓扫描：除本次新写的两处外，其余 `ChatFormatting` 用法都是 `withStyle(...)`，不受影响。

---

## 七、改动清单

| 文件 | 改动 |
|---|---|
| `tools/gen_deafened_icon.py` | **新增** 耳鸣图标生成脚本 |
| `assets/lrtactical/textures/mob_effect/deafened.png` | **新增** 生成产物 18×18 |
| `assets/minecraft/atlases/gui.json` | **新增** 把 `lrtactical:mob_effect/blinded` 别名到原版 blindness |
| `client/gui/components/TaczImageButton.java` | **新增** 三态贴图按钮 |
| `client/gui/overlay/HeatBarOverlay.java` | 按上游重写（位置/底图/缩放/文字/闪烁） |
| `client/gui/overlay/InteractKeyTextOverlay.java` | 按上游重写（补回过滤提示、颜色、位置、空手分支） |
| `client/gui/GunSmithTableScreen.java` | 9 个按钮改回贴图按钮；补回「制造」文字 |

---

## 八、待实机验证

> 与上轮同样的限制：沙箱除 GitHub 外外网不可达，Gradle 与 Fabric 依赖拉不到，
> **未编译、未实机**。所用 26.2 API 均已逐符号核对签名
> （`SingleFile` codec 字段、`DirectoryLister#listMatchingResources` 的跨命名空间行为、
> `Hud#getGuiTicks`、`Gui#hud`、`ARGB#srgbLerp`、`TextColor#getValue`、
> `Minecraft#fontFilterFishy`、`Options#keyUse`、两个 `blit` 重载、
> `AbstractWidget#isHoveredOrFocused/isActive`）。

- [ ] 中闪光弹：状态栏出现**耳鸣**图标（耳朵）与**致盲**图标（复用原版失明图标），均非紫黑格
- [ ] 过热：热度仪表出现在**屏幕中央**、有底图、随热度伸缩、百分比文字可见
- [ ] 过热锁定：底图与文字在红/黄之间闪烁，文案为 `!OVERHEAT!`；暂停时闪烁停止
- [ ] 合成台：9 个按钮均为贴图样式（含悬停态变化），非灰底方块
- [ ] 合成台：「制造」二字正常显示且**不叠字**
- [ ] 持枪瞄准工作台：提示为**黄色**、在准星**上方**；开启自动过滤时下方多一行灰字
- [ ] **空手但手持枪械/配件/弹药**瞄准工作台：提示出现且显示的是**原版使用键**
- [ ] 持枪瞄准可交互实体：提示正常
