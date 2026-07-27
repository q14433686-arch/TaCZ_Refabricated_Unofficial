# 第 18 轮进度报告

**日期**：2026-07-26

> ⚠️ **未实机验收**：沙盒无 GPU。① 的修复逻辑已定位到真正的代码路径，
> ② 已运行时验证，**P1 本身就是为了让你实机验证一个假设**，见 §三。

---

## 一、① 低倍镜仍然纯黑 —— 我上两轮修错了地方

### 1.1 直接承认：第 16/17 轮的门禁守着一条不执行的代码

我把「不开镜就不画目镜」的判断加在了这两个方法里：

- `renderOcularStencil(...)`
- `renderOcularAndDivision(...)`

**但它们在 26.2 根本不会产生任何绘制。** 两个原因叠加：

1. 它们属于**旧的 `render()` 路径**，而 26.2 走的是 `submit()`；
2. 更要命的是，它们内部实际画图的 `renderTempPart(...)` 在我们的移植里是**彻底的 no-op**：

```java
private void renderTempPart(...) {
    // TODO[26.2]: MultiBufferSource/renderBuffers removed - 需要通过 SubmitNodeCollector 重实现
    // 暂时 no-op，配件的临时部件渲染在 26.2 中不可用
}
```

所以我加的门禁**守着一条空路径**，自然毫无效果。你实测「低倍镜依旧纯黑」完全正确，
而我上一轮还在报告里写「逻辑单测 PASS」——那个单测只验证了判定函数本身的真值表，
**没有验证这个函数会不会被真正用到**。这是我的方法论错误。

### 1.2 真正的绘制路径

```java
public void submit(...) {
    ...
    super.submit(poseStack, transformType, collector, renderType, light, overlay);
}
```

`super.submit()` 按 `ModelRenderer.visible` 无差别提交**所有**节点，
其中就包括 `ocular`——它是一块**不透明黑色实体几何**。这才是黑镜片的来源。

### 1.3 修复：在 submit 之前直接关掉目镜节点的可见性

```java
boolean showOcular = shouldDrawOcularMask();
boolean[] savedVisible = new boolean[ocularWrappers.size()];
for (int i = 0; i < ocularWrappers.size(); i++) {
    OcularWrapper w = ocularWrappers.get(i);
    savedVisible[i] = !w.renderer.isHidden();
    // both 型瞄具：scope 目镜按开镜进度，sight 目镜恒隐藏
    boolean show = (isScope && isSight) ? (w.isScope && showOcular) : showOcular;
    w.renderer.setHidden(!show);
}
try {
    super.submit(...);
} finally {
    // ModelRendererWrapper 是跨帧共享的，必须还原，
    // 否则会污染第三人称 / 物品栏等其他渲染场合
    for (int i = 0; i < ocularWrappers.size(); i++) {
        ocularWrappers.get(i).renderer.setHidden(!savedVisible[i]);
    }
}
```

策略仍沿用第 17 轮核对上游得到的分类：低倍镜恒不显示目镜，高倍镜仅开镜时显示。
`finally` 还原是必须的——第 4 轮就吃过「共享对象不还原」的亏。

---

## 二、② 可变倍瞄具无法切换倍率

### 2.1 根因：改了副本，从没写回

`LivingEntityAim#zoom()`：

```java
CompoundTag scopeTag = iGun.getAttachmentTag(currentGunItem, AttachmentType.SCOPE);
...
AttachmentItemDataAccessor.setZoomNumberToTag(scopeTag, zoomNumber);
// 然后就没有然后了
```

`getAttachmentTag()` 返回的是 `CustomData.copyTag()` 的**副本**。
改副本不写回 = 什么都没发生。

### 2.2 上游有这一行，我们移植时漏了

```java
// 上游 1.21.1 LivingEntityAim#zoom 第 52 行
iGun.setAttachmentTag(currentGunItem, AttachmentType.SCOPE, scopeTag);
```

而且 `IGun` 接口里连 `setAttachmentTag` 的**声明都没有**（上游第 314 行有）。
`setAttachmentTag` 的实现是我第 16 轮才补回来的，补回后**一直没有调用方**——
这里就是它唯一的用武之地。

### 2.3 修复

- `IGun` 补 `setAttachmentTag(...)` 声明
- `GunItemDataAccessor` 实现加 `@Override`
- `LivingEntityAim#zoom()` 末尾补写回

### 2.4 运行时验证

```
初始 ZoomNumber = 0

--- 旧行为：改副本、不写回 ---
  副本内 = 1
  枪上实际 = 0   <-- 没变，切不了倍率（bug 复现）

--- 新行为：改完 setAttachmentTag 写回 ---
  第1次切换后 枪上实际 = 1
  第2次切换后 枪上实际 = 2
  第3次切换后 枪上实际 = 3

>>> PASS: 倍率可持久递增
```

这同时解释了你说的「组合镜的低倍镜切不过去」——组合镜靠 `ZoomNumber` 索引
`views[]` 选择 `scope_view` 节点，`ZoomNumber` 存不下来，自然永远停在第一档。

---

## 三、P1：镜内渲染核心假设验证 —— 怎么测

### 3.1 P1 在验证什么（为什么值得单独做一轮）

整个 PIP 方案压在一个假设上：

> `RenderSystem.outputColorTextureOverride` 这套输出重定向机制，
> 在**世界渲染阶段**可用，且用完能干净还原、不污染主画面。

依据是 26.2 反编译里 `LevelRenderer#addAlwaysOnTopPass` 的自用写法。
但「引擎在自己的 FrameGraph pass 里能用」和「我们在任意时机也能用」**是两回事**。

P1 **不做**真正的镜内世界渲染，只做最小闭环：
创建离屏 RT → 重定向 → 纯色清空 → 还原 → 看主画面是否完好。

**若 P1 不成立，P2~P5 全部推翻。** 这就是先做它的理由。

### 3.2 新增文件

| 文件 | 作用 |
|---|---|
| `client/render/scope/ScopeRenderTarget.java` | 离屏 RT 封装：创建/清空/重定向/还原，含尺寸变化重建与资源释放 |
| `client/render/scope/ScopePipTest.java` | P1 验证器：递归守卫 + 异常自禁用 + 日志 |
| `config/client/RenderConfig.java` | 新增开关 `ScopePipTest`（**默认关闭**） |

纹理创建参数严格对齐官方 `PictureInPictureRenderer`（已反编译核对）：

```java
colorTexture = device.createTexture(..., 13, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
depthTexture = device.createTexture(..., 9,  GpuFormat.D32_FLOAT,  w, h, 1, 1);
```
usage 位掩码：13 = COPY_DST|TEXTURE_BINDING|RENDER_ATTACHMENT，9 = COPY_DST|RENDER_ATTACHMENT。

---

### 3.3 【请按这个步骤测】

**第 1 步：开启开关**

编辑 `config/tacz-client.toml`，找到：

```toml
ScopePipTest = false
```
改成：
```toml
ScopePipTest = true
```

（在 Cloth 配置界面里改也行；改完需**重进世界**或重载配置。）

**第 2 步：拿一把装了瞄具的枪，右键开镜**

必须是**装了瞄准镜**的枪，且**开镜进度 > 0.5**（也就是基本瞄稳了）。

**第 3 步：观察三件事**

| 观察点 | PASS 的表现 | FAIL 的表现 |
|---|---|---|
| **A. 主画面** | 开镜后画面**完全正常**，和关闭开关时没有任何区别 | 黑屏 / 闪烁 / 画面错位 / 局部变品红 |
| **B. 日志** | `logs/latest.log` 出现一行：<br>`[TACZ P1] 离屏渲染目标创建并重定向成功 (512x512)，输出已还原。` | 出现 `[TACZ P1] 离屏渲染验证失败` + 堆栈 |
| **C. 稳定性** | 反复开镜/收镜 20 次，帧数无明显下降，不崩溃 | 崩溃 / 帧数暴跌 / 日志刷异常 |

**判定标准（三条全满足才算 PASS）：**

1. ✅ 日志出现「创建并重定向成功」，且**没有**「验证失败」
2. ✅ 主画面全程正常——**这条最关键**。它证明我们借用了输出目标之后
   **干净地还给了引擎**，没有影响其他渲染层（正是你担心的那点）
3. ✅ 反复开收镜不崩、不掉帧

**特别说明**：P1 阶段你**不会**在镜片里看到品红色。
品红只画进了离屏纹理，还没有贴回镜片上（那是 P4 的事）。
**「看起来什么都没发生」+「日志有成功行」= 正是 PASS。**

**如果你装了 Iris/光影**：请**同时测一次开光影和不开光影**。
如果开光影时 FAIL，不影响整体结论——按既定策略，检测到光影就降级，不硬兼容。
但我需要知道结果，以便确定降级触发条件。

**万一出问题**：把 `ScopePipTest` 改回 `false` 即可完全恢复。
代码里也有异常自禁用——出错会自动停止并记日志，不会让你卡在黑屏里。

---

## 四、本轮改动

| 文件 | 改动 |
|---|---|
| `client/model/BedrockAttachmentModel.java` | **真正的黑镜片修复**：`submit()` 里控制目镜节点可见性 + finally 还原；新增 `ocularWrappers` 字段 |
| `entity/shooter/LivingEntityAim.java` | `zoom()` 补 `setAttachmentTag` 写回 |
| `api/item/IGun.java` | 补 `setAttachmentTag` 接口声明 |
| `api/item/nbt/GunItemDataAccessor.java` | 实现加 `@Override` |
| `client/render/scope/ScopeRenderTarget.java` | **新增**：离屏 RT 封装 |
| `client/render/scope/ScopePipTest.java` | **新增**：P1 验证器 |
| `config/client/RenderConfig.java` | 新增 `ScopePipTest` 开关（默认关） |
| `client/event/FirstPersonRenderGunEvent.java` | 接入 P1 钩子 |

字节码核对：`setHidden` ×4、`setAttachmentTag` ×1、P1 两个类均已打包。

---

## 五、TODO

### 请验收

- [ ] **低倍镜/红点**（EXP3、OKP-7 等）：不开镜、开镜都**不再是黑镜片**
- [ ] **高倍镜**（TA31、6x）：不开镜透空，开镜时有镜片
- [ ] **组合镜**：低倍/高倍档位可正常切换
- [ ] **可变倍瞄具**：按切换键倍率确实变化（FOV 有变化）
- [ ] **第三人称 / 物品栏**里的枪：目镜显示正常（验证 finally 还原没漏）
- [ ] **P1 三项判据**（见 §3.3）
- [ ] 前几轮修复未回归（配件生效、配方系统）

### 未解决

1. PIP P2~P5（真正的镜内世界渲染）—— 等 P1 结果
2. UI 整体重绘 / 左侧 3D 模型
3. 后坐力偏左右、子弹从眼部生成 —— 上游既有行为
4. 副手开枪 —— 上游不支持
5. 一批 compat 仍是 no-op

---

## 六、自我复盘

1. **第 16/17 轮连续两轮修错位置**，根本原因是我**没有先确认「这段代码到底会不会被执行」**
   就开始改。第 17 轮我甚至写了个单测证明判定函数正确——但那只是证明了
   「函数的真值表对」，完全没触及「函数根本没被调用」这个事实。
   **教训：改渲染代码前，先确认调用链真的能走到，必要时加日志或看字节码调用点。**

2. **`renderTempPart` 是 no-op 这件事，代码注释里白纸黑字写着**，我前两轮却视而不见。
   这类「TODO: 暂时 no-op」的注释应该当成红旗，而不是背景噪音。

3. **②「改副本不写回」这类 bug 很隐蔽**，因为代码读起来完全合理。
   只有对照上游逐行比对才能发现少了一句。第 16 轮我补了 `setAttachmentTag`
   却没去找「谁该调用它」，如果当时顺手查一下调用方为 0，这轮的 bug 上轮就能修掉。
