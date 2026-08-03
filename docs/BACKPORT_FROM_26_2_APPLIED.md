# 26.2 → 26.1.2 回移植执行记录（2026-08-03）

来源：`26.2(main)` 分支 `backport-26.1.2/` 目录（清单 README + 8 个补丁）。
本记录对应 README 各节的执行结果与**额外的符号级适配**。

## 一、已按序套用补丁 01–07（00 已在 PR #10 合并，跳过）

| # | 补丁 | 修复内容 | 应用结果 |
|---|------|---------|---------|
| 01 | dimension-gun-state | 跨维度后服务端枪械状态永不复位（补注册 `AFTER_PLAYER_CHANGE_LEVEL`） | ✅ 干净应用 |
| 02 | rejoin-world-draw | 持枪重进同一存档打不出子弹（补注入 `Minecraft#disconnect(Screen;ZZ)V` + 玩家实例/连接检测） | ✅ 干净应用 |
| 03 | lefthand-thirdperson | 左利手玩家第三人称主手枪不渲染（`IS_MAIN_HAND_SUBMIT` 透传主副手判定） | ✅ 干净应用 |
| 04 | gunpacklist-rowheight-checkbox | 枪包过滤器行高错位 + 复选框改用 4 张 GUI sprite + 标签补 alpha | ✅ 干净应用 |
| 05 | heatbar | 热度条按上游还原（贴图/位置/缩放迟滞/闪烁/文案） | ⚠️ 干净应用，但含 1 处符号适配，见下 |
| 06 | interact-key-text | 交互提示补回 4 处上游行为 | ⚠️ 干净应用，但含 1 处符号适配，见下 |
| 07 | hud-version-autofit | HUD 版本号按可用宽度自适应缩字号 | ✅ 干净应用 |

## 二、补丁携带的两处 26.2-only 符号引用（已适配回 26.1.2）

补丁本身是在 26.2 工作树上生成的；README 的符号核验只覆盖了**导入的类**，
以下两个**成员级**差异在逐符号比对 `minecraft-merged-0d09a28b48-26.1.2.jar` 时发现并修正：

1. **`mc.gui.hud.getGuiTicks()`（补丁 05）** —— `Gui#hud` 字段与
   `net.minecraft.client.gui.Hud` 类都是 26.2 的重构产物，26.1.2 不存在。
   26.1.2 是公开方法 `Gui#getGuiTicks()I`。已改为 `mc.gui.getGuiTicks()`。
2. **`TextColor.YELLOW / TextColor.GRAY`（补丁 06）** —— 26.1.2 的 `TextColor`
   只有 `CODEC / NAMED_COLORS / value / name` 等字段，没有任何具名颜色常量。
   而 `ChatFormatting#getColor()Ljava/lang/Integer;` 在 26.1.2 **仍然存在**
   （「已删除」是 26.2 的变更）。已改回上游同款
   `ChatFormatting.YELLOW.getColor()` / `.GRAY.getColor()`，保留补丁补 alpha 的逻辑。

验证手段备注：本沙箱无 JDK，无法 `./gradlew build`；以上结论均通过解析
26.1.2 merged jar 的 class 文件常量池逐符号确认（类存在性、方法描述符、字段访问标志）。

## 三、README §2「需适配后移植」项：工作台预览缩放/旋转（PIP 渲染器）——已完成

按 README 的适配指引执行，差异点全部对 26.1.2 jar 字节码核验：

- `GunSmithTableScreen.java`、`GunPreviewRenderState.java`、`TaczImageButton.java`
  按 main 原样移植（README 明确可原样移植；其依赖符号均已核实）；
- **`GunPreviewRenderer.java` 为适配重写**（非直接复制）：
  - `renderToTexture` 改回两参签名（26.1.2 基类无 `SubmitNodeCollector` 参数，字节码确认）；
  - 26.1.2 基类构造器要求 `MultiBufferSource.BufferSource`，故新增构造器，
    注册处（`TaCZFabricClient`）改传 `ctx.bufferSource()`；
  - 物品提交路径照抄 vanilla 26.1.2 `OversizedItemRenderer`：
    `state.item().submit(poseStack, mc.gameRenderer.getFeatureRenderDispatcher().getSubmitNodeStorage(), 15728880, OverlayTexture.NO_OVERLAY, 0)`
    + 紧随 `renderAllFeatures()`；
  - 光照 API 用 26.1.2 的 `GameRenderer#getLighting()`（26.2 的公开名是 `lighting()`）。

## 四、按 README 指引**未做**的事

- §3 曳光弹起点修正：26.2 尚未实测确认，暂缓（届时只改 `EntityBulletRenderer` 一行）。
- §3 耳鸣/致盲图标：属资源文件拷贝，本次未列入。
- §4 三个纯注释文件（`CommonNetworkCacheEvent` / `LivingEntityDrawGun` /
  `ServerPlayerMixin`）：无行为变更，不带入；**尤其注意**不要把
  `ServerPlayerMixin#restoreFrom` 清 `currentGunItem` 的那段错误逻辑带过来。
