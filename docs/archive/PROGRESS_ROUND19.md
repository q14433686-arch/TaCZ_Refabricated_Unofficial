# 第 19 轮进度报告

**日期**：2026-07-26
**核心事件**：**P1 验证通过** + **P2 原设计被反编译推翻，已修正**

---

## 一、P1 结果：✅ PASS

你的实测：

| 判据 | 你的反馈 | 判定 |
|---|---|---|
| A. 主画面无差别 | 「画面无差别」 | ✅ |
| B. 帧率无差别 | 「帧率也无差别」 | ✅ |
| C. 日志出现成功行、无失败行 | 日志中 `[TACZ P1] ... (512x512) ...` 反复出现，无 FAILED | ✅ |

**核心假设成立**：

> `RenderSystem.outputColorTextureOverride` 在**世界渲染阶段**可用，
> 且借用渲染目标后能干净还原，不污染其他渲染层。

这正是你最担心的那点（「不影响其他渲染层」），现在有实测背书了。**PIP 地基可用。**

---

## 二、但你的日志暴露了我两个实现缺陷（本轮已修）

### 2.1 中文日志乱码

```
[TACZ P1]       ȾĿ 괴     ض   ɹ  (512x512)      ѻ ԭ
```

游戏控制台编码不是 UTF-8，中文全变乱码。而且我查了一下，
**项目其余所有日志本来都是英文**，只有我新加的这一处用了中文——纯属我的疏忽。

已改为：
```
[TACZ P1] PASS - off-screen render target 512x512 created, output redirected and restored successfully.
[TACZ P1] FAILED - off-screen rendering verification threw; test auto-disabled.
```

### 2.2 日志每秒刷 4 次

我原本用 `lastRan` 做去重，但复位条件写成了 `aimingProgress <= 0.5` 时复位。
开镜过程中进度会反复穿越这个阈值，于是不停地「复位→再打印」。

你的日志里 `00:31:57` 到 `00:32:04` 打了 16 行，就是这个原因。

已改为整个游戏会话**只打印一次**（独立的 `logged` 标志，不再复位）。

---

## 三、【重要】P2 原设计被推翻 —— 反编译发现 override 覆盖不了地形

在动手写 P2 之前，我先去核对了「重定向之后再渲染一遍世界」这条路到底通不通。
**结论：不通。** 逐级证据如下。

### 3.1 只有「立即绘制」路径尊重 override

`PreparedRenderType#drawFromBuffer`（第 32~37 行）：

```java
GpuTextureView colorTexture = RenderSystem.outputColorTextureOverride != null
   ? RenderSystem.outputColorTextureOverride
   : renderTarget.getColorTextureView();
```

✅ 实体、粒子、手部物品等 immediate-draw 内容会被重定向。

### 3.2 但地形（世界主体）**完全不看** override

`ChunkSectionsToRender#renderGroup`（第 42~50 行）：

```java
RenderTarget renderTarget = group.outputTarget();
RenderPass renderPass = RenderSystem.getDevice()
   .createCommandEncoder()
   .createRenderPass(
      () -> "Section layers for " + group.label(),
      renderTarget.getColorTextureView(),   // <<< 硬取，不看 override
      Optional.empty(),
      renderTarget.getDepthTextureView(),
      OptionalDouble.empty());
```

而 `ChunkSectionLayerGroup#outputTarget()`：

```java
RenderTarget renderTarget = switch (this) {
   case TRANSLUCENT -> minecraft.levelRenderer.translucentTarget();
   default -> minecraft.gameRenderer.mainRenderTarget();   // <<< 主 RT
};
```

### 3.3 `LevelRenderer#render` 内部也硬编码主 RT

clear pass 直接写 `this.gameRenderer.mainRenderTarget().getColorTexture()`。

### 3.4 结论

**如果照原方案做，镜内会「只有实体和粒子、没有地形」** ——
等于一个透明的世界，完全不可用。

**幸好在写代码之前先查了。** 这正是第 9 轮教训（没验证就改）的正面应用。

---

## 四、P2 修正后的路径

关键发现：`RenderTarget#getColorTextureView()` 是**普通可覆写方法**，
且 `TextureTarget extends RenderTarget` **可以直接 new**：

```java
public class TextureTarget extends RenderTarget {
   public TextureTarget(@Nullable String label, int width, int height,
                        boolean useDepth, GpuFormat format) { ... }
}
```

两条路，**推荐 A**：

| 方案 | 做法 | 优点 | 风险 |
|---|---|---|---|
| **A（推荐）** | Mixin/Accessor 把 `GameRenderer.mainRenderTarget` 临时指向我们的 `TextureTarget`，渲一遍，再还原 | 地形/实体/粒子**全部**自动进离屏 RT，无需逐路径改 | 字段可能被别处缓存；必须严格 try/finally 还原；尺寸要对齐 |
| B | Mixin 注入 `ChunkSectionLayerGroup#outputTarget()` | 改动面小 | 还要额外处理 `LevelRenderer#render` 里硬编码的 clear pass 等 |

### 4.1 性能预算要加码（这点必须跟你说清楚）

原先估计「多渲一遍世界」，现在确认**必须走完整 `renderLevel`（含地形区块重新提交）**，
代价比我上一轮估的更高。所以对策要升级：

- **默认关闭**，玩家显式开启
- **隔帧更新**：每 2~3 帧才更新一次镜内画面（复用官方 `textureIsReadyToBlit` 思路）
- 离屏分辨率压到 **256²~512²**
- 仅 `aimingProgress > 0.9` 时启用
- 考虑镜内使用**独立的（更短的）渲染距离**

### 4.2 建议把 P2 拆成两步

- **P2a**：用方案 A 把世界渲染进离屏 RT，但**先不碰瞄具**，
  把离屏纹理**平铺到屏幕角落**当调试 HUD。
  好处：一眼看出「画面渲出来没有、内容对不对、掉多少帧」，
  且完全不影响现有渲染，出问题好回退。
- **P2b**：P2a 的画质和性能你认可后，再贴到镜片上（配合圆形蒙版）。

---

## 五、本轮改动

| 文件 | 改动 |
|---|---|
| `client/render/scope/ScopePipTest.java` | 日志英文化（修乱码）；改为整会话只打印一次（修刷屏） |
| `SCOPE_PIP_PLAN.md` | 新增 §8：P1 结论 + P2 路径修正 + 性能预算重估 |

字节码核对：日志字符串已确认为英文 `PASS` / `FAILED`。

> 本轮**没有改动任何渲染行为**，只修了日志和文档。
> 第 18 轮的黑镜片修复、倍率切换修复保持原样，等你验收。

---

## 六、TODO

### 请验收（主要是第 18 轮的内容，本轮只修日志）

- [ ] **低倍镜/红点**：不再是黑镜片
- [ ] **高倍镜**：不开镜透空，开镜时有镜片
- [ ] **可变倍瞄具 / 组合镜**：倍率能切换（FOV 有变化）
- [ ] **第三人称 / 物品栏**里的枪：目镜正常（验证 finally 还原没漏）
- [ ] 日志不再乱码、不再刷屏（开着 `ScopePipTest` 时只出现一行英文 PASS）

### 需要你拍板

1. **P2 走方案 A 还是 B？** 我推荐 A（Mixin 临时替换 `mainRenderTarget`）。
2. **同意先做 P2a（纹理平铺到屏幕角落调试）再做 P2b（贴到镜片）吗？**
3. **性能底线**：镜内渲染你能接受掉多少帧？
   我倾向默认关闭 + 隔帧更新 + 256²，把开销压到最低。

### 未解决

1. PIP P2~P5
2. UI 整体重绘 / 左侧 3D 模型
3. 后坐力偏左右、子弹从眼部生成 —— 上游既有行为
4. 副手开枪 —— 上游不支持
5. 一批 compat 仍是 no-op

---

## 七、自我复盘

1. **P1 这一步做对了**。花一轮验证一个假设看似慢，但正因为有了 P1 的成功，
   我才敢继续往下走；也正因为在 P2 动手前先反编译，才发现 override 覆盖不了地形——
   否则就是写完一整套代码、你实机一看「镜内没有地形」，白费一轮。

2. **但 P1 的实现细节我做得毛糙**：中文日志（项目其余都是英文）、
   去重条件写错导致刷屏。这两个都是**只要我自己多想一步就能避免**的低级问题，
   却要占用你的实测反馈来发现。

3. **性能预算我上一轮估低了**。当时以为「重定向 + 再渲一遍」就行，
   没意识到地形是独立路径、必须走完整 `renderLevel`。这轮已如实上调预期。
