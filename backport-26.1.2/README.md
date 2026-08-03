# 26.1.2 回移植清单

本目录汇总 `26.2(main)` 上已落地、且**确认对 26.1.2 同样适用**的修复。

每一条都经过以下三重核验，不是纸面推断：

1. **实码判定** —— 用脚本剔除注释行后统计，只有真正改了行为的才进清单
   （本轮 22 个改动文件中，有 3 个是纯注释，已排除）。
2. **补丁干跑** —— 在真实的 26.1.2 工作树（`git worktree` 检出 `origin/26.1.2`）
   上顺序累积 `git apply`，7 个补丁**全部干净应用、零冲突**。
3. **符号核验** —— 补丁新引入的 10 个 `net.minecraft` 导入与全部关键方法，
   逐个对 26.1.2 的 `minecraft-merged-0d09a28b48-26.1.2.jar` 字节码验证存在性与签名。

> 沙箱无 JDK、外网 Maven 不可达，**无法编译验证**。
> 以上核验能排除「类/方法不存在」「补丁冲突」这两类问题，
> 但不能替代 `./gradlew build`。请在本地编译后再合并。

---

## 一、可直接套用（7 项，已验证零冲突）

按序号顺序应用即可。`00` 已在此前 PR #10 合并，此处仅作存档。

| # | 补丁 | 修复内容 |
|---|------|---------|
| 00 | `00-statue-fix.patch` | 雕像放枪渲染线程 NPE 崩溃（**已合并**，存档） |
| 01 | `01-dimension-gun-state.patch` | **跨维度后服务端枪械状态永不复位** |
| 02 | `02-rejoin-world-draw.patch` | **持枪重进同一存档打不出子弹** |
| 03 | `03-lefthand-thirdperson.patch` | **左利手玩家第三人称主手枪不渲染** |
| 04 | `04-gunpacklist-rowheight-checkbox.patch` | 枪包过滤器无效 + 复选框贴图丢失 |
| 05 | `05-heatbar.patch` | 热度条位置/尺寸按上游还原 |
| 06 | `06-interact-key-text.patch` | 交互提示缺失 4 处行为 |
| 07 | `07-hud-version-autofit.patch` | HUD 版本号溢出 |

### 应用方式

```bash
git checkout 26.1.2
git am < backport-26.1.2/patches/01-dimension-gun-state.patch
# ...依次
# 或一次性：
git apply backport-26.1.2/patches/0[1-7]*.patch
```

### 三个核心修复的说明

**01 跨维度枪械状态** —— `TravelToDimensionEvent` 只注册了
`AFTER_ENTITY_CHANGE_LEVEL`，而 Fabric 官方 javadoc 明写该事件
*"does not apply to the ServerPlayer"*。这个专门用来修「跨维度枪械数据不刷新」
的 handler，**对玩家一次都没执行过**。已确认 26.1.2 的
`TaCZFabric.java` 第 142 行与 26.2 修复前**逐字节一致**，同样缺失。
症状：跨维度后换弹动作连贯但子弹不变（客户端有 `RefreshClonePlayerDataEvent`
轮询刷新、服务端没有，两侧不对称）。

**02 重进存档打不出子弹** —— 26.1.2 的 `MinecraftMixin` 同样**只挂
`clearClientLevel`**（已核对，第 67 行），而「主动退出到标题」走的是
`Minecraft#disconnect(Screen,ZZ)`，不经过该方法。导致 `oldHotbarSelected`
跨存档残留、首次 draw 包永不发出。
用户实测特征：首次进存档正常，**同一进程内重进同一存档必触发，交叉进入不同存档不触发**。

**03 左利手第三人称** —— 26.1.2 处于**修了一半**的状态：
`ItemInHandLayerMixin` 已正确用 `state.mainArm` 判定，但
`GunItemRendererWrapper` 第 277 行仍是 `transformType == THIRD_PERSON_LEFT_HAND`
就 return（硬编码「左手 = 副手」）。左利手玩家主手即 LEFT，故主手枪被吞。

---

## 二、需要适配后才能移植（1 项）

### 工作台预览无法缩放 / 旋转（PIP 渲染器）

**不要直接套用 26.2 的补丁，会编译失败。**

根因是 `PictureInPictureRenderer#renderToTexture` 两版签名不同（字节码确认）：

```
26.1.2 : renderToTexture(PictureInPictureRenderState, PoseStack)
26.2   : renderToTexture(PictureInPictureRenderState, PoseStack, SubmitNodeCollector)
```

26.1.2 没有 `collector` 参数，渲染要走基类的 `protected bufferSource` 字段
（参照 vanilla 同版本的 `OversizedItemRenderer` / `GuiEntityRenderer`，
二者的 `renderToTexture` 都是两参形式）。

**好消息**：其余前提在 26.1.2 全部成立，已逐一验证——

- `net.minecraft.client.gui.render.pip.PictureInPictureRenderer` ✅
- `net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState` ✅
  （含静态 `getBounds(IIII,ScreenRectangle)`）
- `GuiRenderState#addPicturesInPictureState(...)` ✅
- 抽象方法 `getRenderStateClass` / `getTextureLabel` / `getTranslateY(II)F` 签名一致 ✅

> 注意：这两个类的**包路径**与直觉不同
> （`gui.render.pip` 与 `renderer.state.gui.pip`，并非同一父包），
> 我最初按 `gui.render.state.pip` 查找曾误判为「不存在」。

适配工作量：改 `GunPreviewRenderer#renderToTexture` 的签名，
把 `state.item().submit(..., collector, ...)` 换成基于 `bufferSource` 的等价调用。
`GunPreviewRenderState`、`TaczImageButton` 与 `GunSmithTableScreen` 的改动可原样移植。

---

## 三、不建议移植（2 项）

**曳光弹起点修正** —— 26.2 的补丁依赖整套 TracerDebug 诊断设施
（`firstPersonWorldOffset` 参数、日志格式串），26.1.2 没有这些代码，
且 26.1.2 的衰减曲线本身就与 26.2 不同（**12 格二次衰减 ×0.65**，
而非 26.2 的 50 格线性），直接套用会连带改掉衰减手感。

26.1.2 若要修同一 bug，只需改一行 —— 在
`EntityBulletRenderer` 约第 138 行，把

```java
poseStack.translate(offset.x * offsetReducer, offset.y * offsetReducer, offset.z * offsetReducer);
```

改为

```java
Vector3f w = new Vector3f(offset).rotate(Minecraft.getInstance().gameRenderer.mainCamera().rotation());
poseStack.translate(w.x * offsetReducer, w.y * offsetReducer, w.z * offsetReducer);
```

（`Camera#rotation()Lorg/joml/Quaternionf;` 在 26.1.2 已确认存在。）
**但该修复在 26.2 上尚未经用户实测确认**，建议等 26.2 验证通过后再移植。

**耳鸣/致盲图标** —— 依赖新增的 `deafened.png` 与
`assets/minecraft/atlases/gui.json`，属于资源文件而非补丁，
若 26.1.2 需要请直接复制这两个文件。

---

## 四、明确排除：纯注释，无需移植

以下 3 个文件在本轮只增加了排查记录注释，**没有任何行为变更**：

- `CommonNetworkCacheEvent.java`（+36 行全注释）
- `LivingEntityDrawGun.java`（+30 行全注释）
- `ServerPlayerMixin.java`（+33 行全注释）

其中 `ServerPlayerMixin` 的注释记录了一次**已回退的错误修复**
（曾在 `restoreFrom` 里清 `currentGunItem`，导致跨维度后一段时间完全无法操作枪械）。
移植时**不要**把那段逻辑带过去 —— 注释本身就是为了防止重蹈覆辙。
