# 第 16 轮进度报告

**日期**：2026-07-25

> ⚠️ **未实机验收**：沙盒无 GPU。①② 的根因与修复已有**运行时实验 + 字节码**双重证据，
> ③ 属渲染改动，只能靠你实机确认。

---

## 一、结论速览

**你的三个怀疑全部成立，而且 ①② 是同一个根因。**

| # | 你的反馈 | 结论 | 状态 |
|---|---|---|---|
| ① | 瞄准镜装上去不被承认 | ✅ 属实 | **已修** |
| ② | 扩容弹匣/特殊弹匣完全不生效 | ✅ 属实，**与①同根因** | **已修** |
| ③ | 官方不开镜时不渲染镜片 | ✅ 属实（参考图已确认） | **已修** |

**一句话根因**：`getAttachmentTag` 读的是 **1.20.x 时代的 NBT 布局**，
在 26.2 上恒返回 `null` → **所有配件都被判定为「没装」**。

---

## 二、①② 的共同根因：配件读取用了过时的 NBT 布局

### 2.1 问题代码

```java
default CompoundTag getAttachmentTag(ItemStack gun, AttachmentType type) {
    CompoundTag nbt = ItemNbtUtils.getTag(gun);
    String key = GUN_ATTACHMENT_BASE + type.name();
    if (nbt.contains(key)) {
        CompoundTag allItemStackTag = nbt.getCompoundOrEmpty(key);
        if (allItemStackTag.contains("tag")) {          // <<< 1.20.x 的旧布局
            return allItemStackTag.getCompoundOrEmpty("tag");
        }
    }
    return null;                                        // <<< 26.2 上永远走这里
}
```

`"tag"` 是**物品组件化之前**（1.20.4 及更早）的 ItemStack 结构：`{id, Count, tag:{...}}`。

### 2.2 26.2 的真实布局（实测）

```
=== 26.2 ItemStack NBT 顶层键 ===
  [count, id]        (+ components)
  contains("tag") = false
```

26.2 是 `{id, count, components:{...}}`，**顶层没有 `"tag"` 键**。

### 2.3 完整因果链

```
getAttachmentTag 恒返回 null
   └─> getAttachmentId 恒返回 EMPTY_ATTACHMENT_ID
        ├─> FirstPersonRenderGunEvent: scopeId 判空 -> 走机瞄分支   = 问题①
        ├─> getMagExtendLevel: 拿不到 id -> 恒返回 0 级              = 问题②
        └─> 其余所有依赖 getAttachmentId 的配件逻辑全部失效
```

**这解释了你说的「除了镭射都不太能确认是否生效」**——镭射走的是独立模型渲染路径，
不依赖 `getAttachmentId`，所以只有它看起来正常。你的直觉完全正确。

### 2.4 为什么第 15 轮我没查出来

上一轮我查 ① 时，逐项确认了：
- `scope_pos` 节点存在 ✅
- `scope_view` 节点存在 ✅
- `views` 缺省值合法 ✅
- 分支逻辑与上游一致 ✅

**然后就下了「数据链完整，问题在运行时状态」的结论。**

错就错在——我验证的是「**如果** scopeId 非空，后续逻辑对不对」，
却<b>从没验证过 scopeId 本身能不能取到</b>。链条的第一环我直接假定它是好的。

好在我当时忍住了没瞎改 `currentViewIndex`。否则就是在正确的代码上叠错误的补丁。

### 2.5 上游的正确写法

```java
// Sh1roCu/TACZ-Refabricated @ 1.21.1
CompoundTag stack = nbt.getCompound(key);
if (!stack.contains("components", Tag.TAG_COMPOUND)) return null;
CompoundTag components = stack.getCompound("components");
if (!components.contains(DataComponents.CUSTOM_DATA.toString())) return null;
return components.getCompound(DataComponents.CUSTOM_DATA.toString());
```

即 `<配件槽键> -> components -> minecraft:custom_data`。
（`DataComponents.CUSTOM_DATA.toString()` 的值已实测确认为 `"minecraft:custom_data"`。）

### 2.6 修复 + 验证

按 26.2 的 `getCompoundOrEmpty` API 重写，并**补回移植时整个丢失的 `setAttachmentTag`**
（上游有、我们没有——这是瞄具倍率切换无法保存的原因）。

真实 NBT 往返测试：

```
枪的 NBT: {AttachmentEXTENDED_MAG:{components:{"minecraft:custom_data":
           {AttachmentId:"tacz:extended_mag_3"}},count:1,id:"tacz:attachment"}}

旧实现 getAttachmentTag = null
  -> AttachmentId = (拿不到, 配件视为未安装)

新实现 getAttachmentTag = {AttachmentId:"tacz:extended_mag_3"}
  -> AttachmentId = tacz:extended_mag_3

>>> PASS: 旧实现读不到(=bug复现)，新实现能正确读到配件 ID
```

字节码核对：`components` ×5、`minecraft:custom_data` ×4、`setAttachmentTag` 已存在，
**无残留的 `"tag"` 常量**。

---

## 三、③ 不开镜时不渲染镜片

### 3.1 你的观察是对的

参考图（TAC ZERO 官方宣传图）里，右侧红点镜的**镜框是透空的**，
能直接看到后面的雪山背景，没有黑色镜片。

### 3.2 为什么我们这边是黑的

两个方法都在无条件画目镜：

**`renderOcularStencil`** —— 名字叫 stencil，但它原本**只写模板、不写颜色**：

```java
// RenderSystem.colorMask(false, false, false, false);   <-- 被注释掉了
// RenderSystem.depthMask(false);                        <-- 被注释掉了
renderTempPart(...ocularNodePaths.get(i));               // <-- 照常执行
```

26.2 移除 stencil 后，那两行 `colorMask/depthMask` 全被注释，
于是这个**本该完全不可见**的目镜被实打实画成了不透明黑色。

**`renderOcularAndDivision`** —— 注释里直接写着「渲染目镜黑色遮罩」，
上游靠 stencil 裁掉，我们没有 stencil 就原样画出来了。

### 3.3 修复

两处都加开镜门禁：`aimingProgress > 0.05` 才绘制目镜遮罩。
**十字线（division）保持始终绘制**，因为它本身是镂空贴图，不会挡视线。

这既贴近官方观感，也是没有 stencil 时最合理的降级。等 PIP 做完可以再回来重构。

---

## 四、本轮改动文件

| 文件 | 改动 |
|---|---|
| `api/item/nbt/GunItemDataAccessor.java` | **核心**：`getAttachmentTag` 改用 `components/custom_data` 布局；补回 `setAttachmentTag`；新增 2 个常量 |
| `client/model/BedrockAttachmentModel.java` | 新增 `currentAimingProgress()`；`renderOcularStencil` 与 `renderOcularAndDivision` 加开镜门禁 |

---

## 五、TODO

### 请重点验收（本轮改动影响面很大）

- [ ] **扩容弹匣生效**：装上后弹匣容量确实变大（这是最直接的验证）
- [ ] **特殊弹匣插件生效**：燃烧弹等
- [ ] **瞄准镜被承认**：开镜时对齐瞄具而非机瞄（① 应随之解决）
- [ ] **其他配件生效**：枪口（消音/枪焰）、握把（后坐力）、枪托
- [ ] **不开镜时镜片透空**，开镜时正常
- [ ] 卸除配件仍正常、不复制（第 15 轮修复未回归）
- [ ] 配方系统未回归（第 14 轮）

> ⚠️ **一个重要提醒**：如果你之前存档里已经装过配件，那些枪上的 NBT 是用**旧代码**写的。
> 由于 `installAttachment` 一直用的是 `saveItemStack`（写的是 26.2 正确布局），
> 理论上老存档的数据本身是对的、只是读不出来，修复后应能直接识别。
> 但保险起见，**如果发现老枪仍不生效，请卸下重装一次配件**再判断。

### 仍未解决

1. **PIP 镜内渲染** —— 方案已出（`SCOPE_PIP_PLAN.md`），等你拍板性能预算
2. **UI 整体重绘**（含左侧 3D 模型）
3. 后坐力偏左右 / 子弹从眼部生成 —— 上游既有行为
4. 副手开枪 —— 上游不支持
5. 一批 compat 仍是 no-op

### 仍需你决策（第 15 轮遗留）

1. PIP 性能预算：接受多渲一遍世界？还是默认关闭？
2. 下一轮：(a) P1 验证 PIP 核心假设 / (b) UI 重绘 / (c) 其他
3. Iris 光影时降级为静态贴图，可以吗？

---

## 六、自我复盘

1. **第 15 轮我把 ① 判断为「需实机调试」是错的**。当时查了四项都没问题就收手，
   却漏了最基础的一环：**scopeId 本身取不取得到**。
   **教训：排查链路要从第一环开始验，不能默认「输入是好的」。**

2. **这个 bug 潜伏了 16 轮**。它影响面极大（所有配件），但表现很隐蔽——
   因为配件的**模型照常渲染**（走另一套路径），只有**数值和逻辑**失效，
   所以看起来「装上了」，实际系统认为「没装」。

3. **你的反馈方式帮了大忙**。「除了镭射我都不能确认是否生效」这句话信息量极大——
   镭射恰好是唯一不依赖 `getAttachmentId` 的配件。如果只报「瞄准镜有问题」，
   我很可能又只盯着瞄具那条链路查。
