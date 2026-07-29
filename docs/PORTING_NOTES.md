# 26.2 移植经验总结

面向**后续开发者**与**其他打算移植到 26.2 的人**。

本文只收录**已被字节码或实测验证过**的结论。凡是「文档这么说」但没验证过的，
一律不写进来 —— 移植期间踩过太多次「照着文档改，结果 API 根本不存在」的坑。

> **验证方法**：全程以 26.2 的 `minecraft-merged-*.jar` 反编译/反汇编字节码为准，
> 对照上游 1.21.1 源码逐行比对。**不采信任何二手文档与记忆**。

---

## 0. 一句话教训

> **绝大多数「移植 bug」不是逻辑写错，而是同一个 API 的语义在新版本变了，
> 而它在旧版本上恰好也能编译通过。**

这类 bug 的共同特征：**编译无错、运行不崩、只是行为不对**，因而最难发现。
下面第 1、2 节几乎全属此类。

---

## 1. 【最高频】颜色必须带 Alpha —— 26.2 会静默丢弃

**这一条在本项目造成了至少 4 个独立 bug，是单一原因中最多的。**

26.2 的 `GuiGraphicsExtractor#text` 第一条指令就是（字节码偏移 0-8）：

```java
if (ARGB.alpha(color) == 0) return;   // alpha 为 0 -> 整段文字【直接不画】
```

1.21.1 的 `GuiGraphics#drawString` **没有**这个短路，会把 `0xCCCCCC` 当作
不透明浅灰照常画出。于是所有从旧版照搬的六位色都变成了「什么都不显示」。

| 踩坑点 | 症状 |
|---|---|
| `GunPropertyDiagrams` 的 `fontColor = 0xCCCCCC` | 改装界面「图表」里所有文字消失，只剩空白进度条 |
| `TextShow.colorInt = 0xFFFFFF` | 枪身上的文字（8 倍镜弹药计数）不可见 |
| 弹药盒 `minecraft:dye` tint 写 `0x727d6b` | 盒体 78 个面全透明，只剩不受 tint 影响的等级环 |

**排查手法**（建议移植时先全局扫一遍）：

```bash
# 找所有传给 text()/centeredText() 的六位色
grep -rn '\.\(text\|centeredText\)(' --include=*.java src | grep -o '0x[0-9A-Fa-f]\{6\}\b'
# 找颜色常量定义
grep -rnE 'int\s+\w*[Cc]olor\w*\s*=\s*0x[0-9A-Fa-f]{6}\b' --include=*.java src
```

**注意 `dye` tint 的特殊性**：`DyedItemColor#getOrDefault` 只对**已染色**的物品
调用 `ARGB.opaque()`，**默认值分支原样返回、不补 alpha**。而承载它的
`ExtraCodecs.RGB_COLOR_CODEC` 就是个普通 INT，也不补。所以 JSON 里必须自己写全
`0xff727d6b`（Java int 语义写作负数 `-9274005`）。

---

## 2. 【高频】数据包目录名全面单数化

26.2 把 `data/<ns>/` 下的一批目录改成了**单数**。旧的复数名**不报错、不加载**，
纯静默失效 —— 这是最阴险的一类。

| 1.20 及以前 | 26.2 | 失效后果 |
|---|---|---|
| `tags/blocks/` | `tags/block/` | 方块 tag 全空 |
| `tags/entity_types/` | `tags/entity_type/` | 实体 tag 全空 |
| `tags/items/` | `tags/item/` | 物品 tag 全空 |
| `recipes/` | `recipe/` | 配方全部不加载 |
| `loot_tables/` | `loot_table/` | 方块破坏不掉落 |
| `advancements/` | `advancement/` | 进度不加载 |

本项目实际踩到的：

- `tags/blocks` + `tags/entity_types` →「持枪交互」与「交互提示」**双双失效**。
  因为 `InteractKeyConfigRead.canInteractBlock/Entity` 最终 `return block.is(TAG)`
  恒为 false，而交互键与提示 overlay **共用**这个判据。
- `loot_tables` → 工作台/标靶/雕像**破坏后不掉落自身**（长期没人发现）。
- `PackConvertor` 输出 `recipes/` → 旧枪包转换后**配方全部不生效**。

**验证方法**（别猜，直接查 jar）：

```bash
unzip -l minecraft-merged-*.jar | grep -oE 'data/minecraft/tags/[a-z_]+/' | sort -u
```

---

## 2.5 LRTactical 内置附属的边界

本仓库当前把 LRTactical（LesRaisins Tactical Equipements）的**部分 GPL-3.0 代码**移植并内置在
`tacz` 测试构建中，而不是拆成一个单独 Fabric mod。这样做的原因是：它强依赖 TACZ 的 Bedrock
模型、Lua 状态机、网络/事件垫片；在 26.2 移植期拆仓库会让同一套 API 垫片维护两份。

边界必须写清楚：

- `fabric.mod.json` 通过 `provides: ["lrtactical"]` 让内容包依赖检查能识别该附属；
- 内置的是运行框架和少量测试数据，不包含原作 All Rights Reserved 的美术资源；
- 近战武器的伤害/攻速来自数据包 `attributes`，并兼容旧写法
  `generic.attack_damage` / `minecraft:generic.movement_speed` → 26.2 的
  `minecraft:attack_damage` / `minecraft:movement_speed`；
- 挖掘能力不是硬编码的“所有刀都是斧/铲”。`data/<ns>/index/melee/*.json` 可选写
  `tool.mineable_tags` 声明具体工具行为；未声明时写入一个 `default_mining_speed=0` 的
  `Tool` 组件，表示普通刀不作为工具挖掘。

示例：

```jsonc
"tool": {
  "mineable_tags": ["minecraft:mineable/axe"],
  "speed": 6.0,
  "damage_per_block": 1,
  "correct_for_drops": true
}
```

---

## 3. 渲染层：26.2 的硬约束

### 3.1 模板缓冲（stencil）彻底没有了

`RenderPipeline` / `DepthStencilState` / `VulkanRenderPipeline` **完全没有 stencil 概念**
（字节码确认，不是「暂时没暴露」）。`GlStateManager` 类也不存在。

上游瞄具的镜内裁剪完全建立在 stencil 上：

```java
// 上游 1.21.1
renderOcularStencil:   colorMask(false×4); stencilOp(KEEP,KEEP,REPLACE); stencilFunc(GREATER, i+1);
scope_body:            stencilFunc(EQUAL, 0);      // 只在目镜【没盖到】处画镜身
renderOcularAndDivision: stencilFunc(EQUAL, i+1);  // 目镜黑片 + 准星约束在镜内
```

**26.2 的等价替代**（本项目采用，已实测可用）：

1. 建一个离屏 `RenderTarget` 当**掩码纹理**；
2. 在**阶段边界**（`FeatureRenderDispatcher#executeSolid` 之前）一次性把当帧目镜几何画进去；
3. 镜身/准星改用自定义 shader，采样该掩码后 `discard`，
   靠一个 `SCOPE_MASK_INVERT` 宏区分「盖到就丢弃」与「没盖到才丢弃」。

**关键教训：离屏 pass 只能放在阶段边界。** 早期把它零散插在 solid 阶段内部，
直接触发 `GpuDeviceLossException: VK_ERROR_DEVICE_LOST`（GPU 设备丢失）。
`FeatureRenderDispatcher#renderAllFeatures` 的结构是
`prepareFrame → executeSolid → executeTranslucent → ... → close`，
**各阶段之间不在任何 render pass 内**，那里切 OutputTarget 才安全。

### 3.2 管线（RenderPipeline）拼装的坑

- `RenderPipeline#wantsDepthTexture()` 的判据是 **`depthStencilState != null`**，
  与内部是不是 `ALWAYS_PASS` 无关。没有深度附件的 pass 必须显式传
  `withDepthStencilState(Optional.empty())`，否则报「管线要深度附件而 pass 没有」，
  表现为**画了却全黑**。
- `BindGroupLayouts.SAMPLER3` **不存在**（只到 `SAMPLER2`）。额外采样器要仿
  `DISSOLVE_MASK_SAMPLER` 用 `BindGroupLayout.builder().withSampler(name).build()` 自建。
- 缺 uniform 块会在管线编译期报错，例如
  `Unable to find shader defined uniform (Fog)`。直接复用现成的
  `MATRICES_FOG_SNIPPET` 比自己拼 layout 稳妥。
- `RenderSetup` 必须为管线声明的**每个** sampler 都绑定贴图，否则
  `IllegalStateException: Missing sampler Sampler0`。

### 3.3 坐标系与 blit

- **离屏 RenderTarget 纹理原点在左下，GUI 原点在左上** → blit 预览时 V 要翻转
  （`v0=1, v1=0`）。
- `gl_FragCoord.xy` 原点也在左下，与掩码纹理一致，**shader 里不需要翻 Y**。
- `blit(view, sampler, I,I,I,I, F,F,F,F)` 的 4 个 int 是 **`x0,y0,x1,y1`（角坐标）**，
  不是宽高；而 `outline(x,y,w,h,color)` 是宽高。混淆会导致贴图没填满。
- 带贴图总尺寸的 blit 重载，**尺寸必须填真实 PNG 尺寸**。
  1.21.1 的 `blit(TEXTURE,x,y,u,v,w,h)` 走 256×256 默认重载；移植时若把
  256×256 的图误填成 512，UV 会整体减半 → 只采样左半边再拉伸，
  表现为「边框/分隔线/滚动条全部错位变形」。

### 3.5 【已知偏差】镜内裁剪区域：我们用「目镜几何投影」，上游用「固定半径圆」

这是本移植与上游的一处**架构级分歧**，目前<b>尚未修正</b>，记录在此以免重复排查。

**上游 1.21.1**（`renderOcularAndDivision`，逐行确认）：

```java
float rad = 80 * scopeViewRadiusModifier;      // modifier 恒为 1 -> 半径就是【常数 80】
rad *= getClientAimingProgress(partialTick);   // 只随开镜进度缩放
Vector3f ocularCenter = getBedrockPartCenter(matrixStack, ocularNodePaths.get(i));
float centerX = ocularCenter.x() * 16 * 90;    // 圆心 = 目镜投影中心
float centerY = ocularCenter.y() * 16 * 90;
// 然后用 TRIANGLE_FAN 画一个 90 边形的圆，写进 stencil
```

即：**圆心取目镜投影中心，半径是与几何无关的常数**。
（`setScopeViewRadiusModifier` 在上游全仓<b>从未被调用</b>，故 modifier 恒为 1。）

**我们的实现**（`ScopeMaskRenderer`）：把**目镜几何本身**画进离屏掩码，
用它的投影当裁剪区。

**两者何时等价、何时不等价**：

- 默认枪包的 33 个瞄具，`ocular` 基本都是**实心圆盘/大面片**，
  其投影本就接近那个圆 —— 所以肉眼看不出差别，一直没暴露。
- 但只要枪包把 `ocular` 建成**非实心**形状，两者就会分道扬镳。
  实测第三方 PU 镜（GunpowderRevolution）的 `ocular` 是 **6 个细薄片
  （0.30 × 1.125 × 0.0625）绕 Z 轴每 30° 一片**拼成的辐条状环，
  拿它的投影当掩码得到的是**6 条细缝**而不是一个圆，
  准星只在细缝里可见 → 表现为「准星画在了前端/错位」。

**排查时被推翻的三个假设**（都用数据否定了，别再走一遍）：

1. ~~准星节点没被识别~~ — 正则匹配正常，`crosshair_illuminated` 被正确归类；
2. ~~空占位节点导致误分类~~ — 6.5 节那次修复已覆盖，`cross_illuminated`（真空节点）
   已被 `hasGeometry()` 递归过滤掉，分类结果 HYBRID 正确；
3. ~~准星与目镜不共面（ΔY 太大）~~ — 默认枪包里 `scope_qmk152` ΔY 达 **12.82**、
   `scope_mk5hd` 达 **5.41**，都远大于 PU 镜的 6.86，而它们实测正常。
   **几何偏移量本身不是判据。**

**若要对齐上游**，应把掩码从「画目镜几何」改成「以目镜投影中心为圆心、
画一个固定半径的圆」。注意这与早期失败的「固定**屏幕中心**圆」不同 ——
那次错在圆心取了屏幕中心；半径用常数是对的。

> ### ⚠️ 已尝试并回退过一次（务必先读完再动手）
>
> 曾按上述思路实现过：`ScopeMaskGeometry.Entry` 简化为只存圆心矩阵、
> `ScopeMaskRenderer` 改画圆盘（退化四边形绕开 QUADS 拓扑限制）、
> 半径取 `80 / (16*90) ≈ 0.0556` 格。
>
> 纸面推导与交叉验证都通过（该半径与真实 ocular 外径之比为
> `scope_contender` 1.19×、`scope_vudu` 1.01×、PU 镜 0.79×，同量级），
> 但**实测 bug 明显增多，已整体回退**。
>
> 教训：
> 1. **这不是一个「换公式」的小改动。** 它牵动所有瞄具共用的掩码生成路径，
>    而当前这套「几何投影」方案已被 33 个瞄具的实测覆盖过；
>    换掉等于让那 33 个全部回到未验证状态。
> 2. **纸面上的量级吻合 ≠ 实际渲染正确。** 圆心矩阵的朝向、圆盘所在平面
>    （局部 XY 是否真的垂直于视线）、以及与 shader 里那套屏幕空间收缩
>    如何叠加，都是纸面推导覆盖不到的。
> 3. 真要再做，应当**先加开关并行两套实现**、逐个瞄具对拍截图，
>    而不是直接替换。
>
> 现状取舍：保留「几何投影」方案。代价是把 `ocular` 建成非实心形状的
> 第三方枪包会出现准星错位；收益是默认枪包 33 个瞄具的表现是已验证的。

### 3.4 其他渲染 API 变更

| 1.21.1 | 26.2 |
|---|---|
| `RenderSystem.setShaderColor` | 已移除，用 `blit` 的 tint 参数 |
| `GuiGraphics.drawString` | `GuiGraphicsExtractor.text(font, str, x, y, color, shadow)` |
| `PoseStack`（GUI 内） | `Matrix3x2fStack`：`pushMatrix()` / `scale(x,y)` / `popMatrix()` |
| `Screen#renderBlurredBackground` | `Screen#extractBlurredBackground(GuiGraphicsExtractor)`（**protected**） |
| `AbstractContainerScreen#renderBg` | 背景要画在 `extractBackground`（**未平移**坐标系） |
| `renderLabels` | `extractLabels` |

> `AbstractContainerScreen#extractContents` 内部已经
> `pose().translate(leftPos, topPos)`，在里面再加一次偏移会让背景**错位一倍**。
> 所有原版容器界面都把背景放在 `extractBackground` 里画。

---

## 4. 物品 / 模型系统

### 4.1 每个物品都必须有 `assets/<ns>/items/<name>.json`

缺了就**没有图标**。最简形式：

```json
{ "model": { "type": "minecraft:model", "model": "tacz:item/<name>" } }
```

旧的 `overrides` 谓词格式**已彻底失效**，替代品是
`minecraft:select` / `minecraft:condition` / `minecraft:range_dispatch`。

### 4.2 `BlockItem` 不再自动用 `block.` 前缀

26.2 的 `BlockItem` **不再覆写 `getDescriptionId()`**，统一继承 `Item` 的实现，
而 `Item.Properties` 的 `descriptionId` **默认是 `ITEM_DESCRIPTION_ID`**。
所以方块物品必须显式：

```java
new Item.Properties().setId(key).useBlockDescriptionPrefix()
```

否则译名键变成 `item.<ns>.<name>`，而语言文件里通常只有 `block.<ns>.<name>`，
界面上就直接显示原始键名。vanilla 的 `Items#registerBlock` 正是这么调的。

### 4.3 自定义 select 属性：读不了组件内部字段

内置的 `minecraft:component` 实现只有一行 `stack.get(componentType)`，
只能**整体取出组件再与字面量比较**。若外观取决于
`CUSTOM_DATA` 内部的若干字段（还要做组合运算），必须自建
`SelectItemModelProperty` 并注册进 `SelectItemModelProperties.ID_MAPPER`。

### 4.4 掉落物高度由包围盒决定（易被误判为「模型太大」）

26.2 的 `ItemEntityRenderer#submit`：

```java
AABB aabb = state.item.getModelBoundingBox();
poseStack.translate(0, -aabb.minY() * ... + 0.0625F, 0);
```

**抬升高度直接由 `minY` 决定**。若自定义 `ItemModel` 的 extents 写成对称的
±0.5，`minY = -0.5`，每个掉落物就被无条件额外抬高 0.5 格。

同时受另一条**互相冲突**的约束：`GuiItemRenderState#calculateOversizedItemBounds`
按 `ceil(getXsize()*16) > 16` 判定 oversized，一旦判中就走 PIP 离屏路径，
在 16px 槽位里图标会变空白。

**同时满足两者的解**：`Y ∈ [0,1]`、`XZ ∈ [-0.5,0.5]`
（边长仍是 1.0 满足 GUI，`minY = 0` 满足掉落物）。

---

## 5. 其他确认过的破坏性变更

| 项目 | 26.2 的样子 |
|---|---|
| `KeyMapping.Category` | 标题从 Identifier 推导：`id.toLanguageKey("key.category")` = `key.category.<ns>.<path>`。用 `Identifier.parse("x")` 会套用默认命名空间 `minecraft` |
| `Explosion` | 已变成**接口**，实现类是 `ServerExplosion` |
| `AbstractMinecart` | 换包到 `world.entity.vehicle.minecart` |
| 战利品表 | 在 **RELOADABLE 层**，须用 `MinecraftServer#reloadableRegistries().lookup()` |
| `Item#getMaxStackSize(ItemStack)` | 不存在，只有 `getDefaultMaxStackSize()`；上限改由 `DataComponents.MAX_STACK_SIZE` 组件决定 |
| `SharedConstants` | `getCurrentVersion().name()`（不是 `getName()`） |
| 实体碰撞箱调试 | `Minecraft.debugEntries` + `DebugScreenEntryList#isCurrentlyEnabled(Identifier)` |
| `AvatarRenderer` | 在 `client.renderer.entity.player`，**不在** `entity` 下 |

### 5.1 客户端粒子系统整体重组

移植任何自定义粒子都会撞上，**基类直接没了**：

| 1.21.1 | 26.2 |
|---|---|
| `extends TextureSheetParticle` | **该类已删除** → `extends SingleQuadParticle` |
| `getRenderType()` 一个方法 | **拆成两个**：`getGroup()` 返回 `ParticleRenderType`，`getLayer()` 返回 `SingleQuadParticle.Layer` |
| `ParticleRenderType.PARTICLE_SHEET_LIT` | 该常量已无 → `SINGLE_QUADS` + `Layer.TRANSLUCENT` |
| `ParticleProvider#createParticle(..., double zSpeed)` | 末尾**新增 `RandomSource` 参数** |
| `render(VertexConsumer, Camera, float)` | → `extract(QuadParticleRenderState, Camera, float)` |

`Layer` 可选值：`OPAQUE` / `TRANSLUCENT` / `OPAQUE_TERRAIN` /
`TRANSLUCENT_TERRAIN` / `OPAQUE_ITEMS` / `TRANSLUCENT_ITEMS`。

另注意 **`Level#addParticle` 是两个 boolean**：
`(options, overrideLimiter, alwaysShow, x,y,z, xd,yd,zd)` —— 1.21.1 只有一个。

> 本仓库 `com.tacz.guns.client.particle.BulletHoleParticle` 是已适配好的
> `SingleQuadParticle` 子类范例，**照抄它比照抄上游可靠**。

---

## 6. NBT / 物品数据的陷阱

### 6.1 `getAttachment()` 返回的是**反序列化副本**

```java
// 这是错的：改的是临时副本，枪上的数据一个字节没变
ItemStack attachment = iGun.getAttachment(gunItem, type);
iAttachment.setLaserColor(attachment, color);
```

`getAttachment` 内部是 `ItemNbtUtils.loadItemStack(...)`，每次都用 Codec
**反序列化出一个全新 ItemStack**。已安装在枪上的配件**不存在独立的 ItemStack**，
它只是枪 NBT 里的一段数据。正确做法是改 tag 再写回：

```java
CompoundTag tag = iGun.getAttachmentTag(gunItem, type);
if (tag != null) {
    AttachmentItemDataAccessor.setLaserColorToTag(tag, color);
    iGun.setAttachmentTag(gunItem, type, tag);
}
```

这类 bug 的典型表现：**改的时候界面上有效果，一退出/重载就回到默认值**。

### 6.2 `ItemStack.CODEC` 对 `EMPTY` 会抛异常

`ItemStack.MAP_CODEC` 里 count 是 `intRange(1, 99)`，而 `ItemStack.EMPTY` 的
count 为 0，**超出范围**，`encodeStart` 直接失败。序列化可能为空的 ItemStack
必须用 `ItemStack.OPTIONAL_CODEC`。

> 本项目曾因此产生「卸除配件会复制配件」的严重 bug：
> 先把配件给了玩家，随后保存 EMPTY 时抛异常，枪上的配件 NBT 没被清空 → 无限复制。

#### 同一个坑的**网络版**：`ItemStack.STREAM_CODEC` —— 后果是**全服踢线**

`ItemStack.STREAM_CODEC` 对 `EMPTY` 同样抛异常
（`ItemStack$2#encode`：`EncoderException("Empty ItemStack not allowed")`）。
可能为空的字段必须用 **`ItemStack.OPTIONAL_STREAM_CODEC`**。

四个变体别记混：

| 用途 | 允许 EMPTY | 不允许 EMPTY |
|---|---|---|
| NBT / JSON | `OPTIONAL_CODEC` | `CODEC` |
| 网络 | `OPTIONAL_STREAM_CODEC` | `STREAM_CODEC` |

**为什么这个比 NBT 版严重得多**：编码异常发生在 Netty 线程的
`Connection#doSendPacket` 里，会**直接踢掉该连接**。如果这个包又是用
「发给所有能看见该实体的玩家」之类的广播方式发出去的
（本项目的 `NetworkHandler#sendToTrackingEntity`），
那么**一次空栈就会把视野内的每个人全部踢下线，唯独动作发起者自己没事**。

本项目实际踩到（`ServerMessageGunDraw`）：

- 症状 A：**服主丢弃任意物品 → 除服主外全部掉线**
  （丢弃后槽位变空 → 以空栈触发 `draw`）
- 症状 B：**某玩家一进服就让全服掉线，且必然复现**
  （`LivingEntityDrawGun#draw` 里 `data.currentGunItem == null ? ItemStack.EMPTY : ...`
  —— 第一次切枪没有「上一把枪」，恒为空栈）

> **排查提示**：这类崩溃在日志里长这样，`Caused by` 那行才是重点：
> ```
> EncoderException: Failed to encode packet '...custom_payload' (tacz:s2c_gundraw)
>   Caused by: EncoderException: Empty ItemStack not allowed
>     at ServerMessageGunDraw.write(ServerMessageGunDraw.java:40)
> ```
> 堆栈会**直接给出文件名和行号**，不用猜。

**教训**：上游 1.21.1 这里用的就是 `OPTIONAL_STREAM_CODEC`，
是我们移植成手写 `write`/read 时改错的 —— 又一次
「把 `StreamCodec.composite` 声明式写法翻译成手写方法时丢语义」。
把上游的 codec 声明改写成手写读写时，**逐字段核对用的是不是 OPTIONAL 版本**。

**但也不要无差别全仓替换**：同目录另外 5 个事件消息上游用的确实是非 OPTIONAL 版，
且发送处都有 `instanceof IGun` 守卫（空栈的 item 是 `air`，进不去）。
无脑替换会掩盖「本不该出现空栈」的真实逻辑错误。

### 6.3 `ItemStack` 没有覆写 `equals/hashCode`

26.2 的 `ItemStack` 走对象身份比较（只有静态的 `ItemStack.matches`）。
把 `stack.copy()` 放进 `GuiItemAtlas` 的 model identity 里，会导致**每帧都不相等**，
atlas 不断重新分配槽位 → **物品栏图标一片空白**。
identity 只应放具备值语义的量（Item 单例、display context、内容 id 等）。

---

## 6.4 【非 bug】子弹实体本来就生成在眼睛处

排查「曳光弹不是从枪口射出」时确认：**上游就是这么设计的**，两版代码逐行相同。

```java
// EntityKineticBullet 构造函数（上下游一致）
this(type, throwerIn.getX(), throwerIn.getEyeY() - 0.1F, throwerIn.getZ(), worldIn);
```

子弹**实体**（含碰撞、弹道）始终从**眼睛下方 0.1** 出发 —— 这是刻意的：
弹道必须与准星/射线检测一致，从枪口发射反而会打不准。

「看起来从枪口射出」**只是第一人称的视觉补偿**：
`GunItemRendererWrapper.muzzleRenderOffset` 记录枪口相对摄像机的坐标，
`EntityBulletRenderer` 仅在 `isFirstPerson` 分支里把曳光弹**渲染位置**平移过去，
并且随距离衰减（`offsetReducer`），远处自然收敛回真实弹道。

**第三人称没有这个补偿**（上游只给了个 `-0.2` 的 Y 微调），
所以第三人称看曳光弹从胸口出来是**上游的原生表现**，不是移植缺陷。

> 教训：报「位置不对」类问题时，先分清**实体位置**与**渲染位置**。
> 这两者在 TACZ 里是故意分离的。

---

## 6.5 【易漏】自建工具类「意外覆写」了 vanilla 新增的同名方法

移植时若把某个平台库类（Forge 的 `ExtendedSlider` 等）**手写重实现**，
要特别小心它继承的 vanilla 基类**在新版本里新增了同名方法**。

实例：`ForgeSlider extends AbstractSliderButton`，自己定义了

```java
public void setValue(double value)   // 语义：真实值(min~max)，且不回调 applyValue
```

而 26.2 的 `AbstractSliderButton` **也有** `setValue(double)`，语义却是

```java
this.value = Mth.clamp(value, 0.0, 1.0);   // 语义：0~1 比例
if (d != this.value) { this.applyValue(); }  // 变了才回调
this.updateMessage();
```

于是自建方法**无意中覆写**了父类方法，多态分派把 vanilla 内部调用
（`onDrag → setValueFromMouse(event) → setValue(double)`）
导流进了语义不同、且不回调 `applyValue()` 的实现。

**症状极具迷惑性**：
- **拖动滑块无效**（走 vanilla 路径 → 落进覆写版 → 无回调）
- **点击滑块却有效**（走自建的 `onClick → setSliderValue` → 显式调了 `applyValue()`）

「同一控件，点击生效、拖动不生效」基本就是这类分派冲突的指纹。

**排查手法**：把自建类的所有 `public`/`protected` 方法与父类做一次签名比对，
凡是**非有意覆写**却同名同签名的，一律改名。

**修法**：给自建方法改个不冲突的名字（如 `setValueReal`），
让父类方法恢复原有语义。同时注意，若自建类还重写了 `onDrag` 之类，
不要「先 `super.onDrag` 再自己算一遍」—— 那会在一次拖动里写两次值、
触发重复回调，且步进吸附语义变得不确定。

---

## 6.6 【高频】裸名 id 会被补成 `minecraft:`，不是你的命名空间

`Identifier.CODEC` 是 `Codec.STRING.comapFlatMap(Identifier::read, ...)`，
链路 `parse → bySeparator(s, ':')`：**串里没有 `':'` 时落到 `withDefaultNamespace`，
而该方法把命名空间硬编码成 `"minecraft"`**（字节码偏移 4/6 两处常量 `'minecraft'`）。

枪包 JSON 里大量字段惯例写**裸名**（如 `"group": "shotgun_shells"`），
用 `Identifier.CODEC` 一解析就变成 `minecraft:shotgun_shells`，
与实际的 `tacz:shotgun_shells` 永不相等 —— 且**不会报错**，只是恒不匹配。

> 本项目踩到的实例：`result.group`（工作台页签归属）。
> 后果是 24 条弹药配方全部合不出来，而枪械/配件因为压根没写 `group` 字段反而没事。
> 详见下一节。

**修法**：凡是「枪包可能写裸名」的 id 字段，都不要直接用 `Identifier.CODEC`，
要自建一个补默认命名空间的 codec：

```java
Codec.STRING.xmap(
    raw -> Identifier.parse(raw.contains(":") ? raw : GunMod.MOD_ID + ":" + raw),
    Identifier::toString)
```

上游 1.21.1 一直有这条归一化（`raw.contains(":") ? raw : MOD_ID + ":" + raw`）。
**移植时把手写反序列化改成 `RecordCodecBuilder` 最容易把它弄丢** ——
因为 `Identifier.CODEC` 看上去「就该是对的」。

---

## 6.7 【架构级】同一份数据有两条读取路径时，必然分叉

症状指纹：**「界面看得见、点了没反应」**，且无任何日志。

本项目第 12 轮把工作台配方迁到自建的 `DataType.RECIPES` 通道
（因为 26.2 客户端已没有完整配方表）。界面、JEI、REI 三处都改了，
**唯独真正执行合成的 `GunSmithTableMenu#getRecipe` 还留在原版 `recipeManager.byKey`**。
于是「列表用 A 数据源、校验用 B 数据源」，凡是只存在于 A 的配方全都点不动。

放大这个分叉的是原版目录名约束：`RecipeManager.RECIPE_LISTER` =
`FileToIdConverter.registry(Registries.RECIPE)`，其目录名取自
`registryDirPath → ResourceKey.identifier().getPath()` = **`"recipe"`（单数，常量，不可扩展）**。
所以旧枪包放在 `recipes/`（复数）里的配方对原版通道**永远不可见**，
哪怕我们自己的加载器已经兼容了该目录。

**教训**：迁移数据通道时，要 `grep` 出**所有**消费方一起改。
只改「看得见的那几处」，剩下那处就会变成静默失败。
校验方与展示方必须同源 —— 否则用户看到的和服务端认的根本不是一回事。

**另注**：迁移后留下的空壳 API（`return Optional.empty();` 配一句
「请用原版 RecipeManager」的过时注释）比删掉更有害 ——
调用方会把「空壳」当成「真的没有这条数据」。

---

## 7. 线程与生命周期

- 自建线程池**必须是 daemon**。否则退出游戏后进程不退出，
  表现为「退出游戏 15 秒后弹崩溃报告」。
- 配方加载器若不按 `type` 过滤，会**吞掉全部原版配方**
  （本项目曾一次性吞掉 1585 条）。

---

## 8. 旧版枪包兼容（实际结论）

**能自动转的**（`PackConvertor`）：目录结构重排、`pack.json` → `gunpack_info.json`、
配方补 `type` 字段、模型/贴图/动画/语言/音效搬运。

**转换器管不到、需要人工处理的**：

1. **物品模型定义** —— 枪包若自带 `items/*.json`，旧 `overrides` 格式已失效；
2. **枪包内的 tags** —— 若带 `tags/blocks` 这类复数目录，会遇到第 2 节完全相同的问题；
3. **`texture_size` 字段** —— 26.2 已不解析（全 jar grep 零命中），
   依赖它做 UV 缩放的模型需要重算 UV；
4. **`dependencies` 版本约束** —— 见下。

### 移植版特有的坑：`Mod version mismatch`

`GunPackLoader#modVersionMatch` 会拿枪包 `gunpack.meta.json` 里的
`dependencies` 与**实际 mod 版本**比对：

```java
VersionPredicate.parse(version).test(mod.getMetadata().getVersion())
```

移植版早期的版本号是 `0.0.0-26.2-audit`，所以枪包若写
`"tacz": ">=1.0.4"`，**必然不满足**，于是被静默拒绝
（日志 `Mod version mismatch`）。

**已解决**：版本号改为 `1.1.8+fabric.26.2.alpha.N`（见下）。

### 版本号怎么起：`+` 与 `-` 的天壤之别

需求是「既声明基于上游 1.1.8-hotfix，又表明这是 Fabric 移植测试版」。
直觉写法 `1.1.8-fabric.26.2` 是**错的**，会让上面那个 bug 原样复发。

逐行读 `SemanticVersionImpl#compareTo` 源码后确认：

- 先逐段比数字；
- 再比 **prerelease**（`-` 之后那段）；
- **build（`+` 之后那段）自始至终不参与比较**（`compareTo` 里根本没读 `build`）；
- prerelease 存在时**小于**同数字段的正式版（源码：`prereleaseA.isPresent()` 分支 `return -1`）。

于是：

| 版本号 | vs `1.1.8` | 满足 `>=1.1.8` |
|---|---|---|
| `1.1.8+fabric.26.2.alpha.N` | `0` | ✅ |
| `1.1.8-fabric.1` | `-1` | ❌ |

两者对 `>=1.0.4` 都成立，**差别只在要求 `>=1.1.8` 的枪包上暴露** ——
属于「装了大部分包都正常、偏偏某几个包静默失效」的隐蔽坑。

> **结论**：想「如实声明基线又标注是移植版」，标注要放**构建元数据**（`+`）里，
> 绝不能放 prerelease（`-`）。
> 「移植/测试版」的身份改由 `fabric.mod.json` 的 `name` / `description` 承载 ——
> 那里是纯展示字段，玩家在 Mod 列表看得到，且不参与任何版本比较。

顺带一提，`+` 在 Gradle 版本号里完全合法，本项目依赖的 Fabric API
自身就是 `0.155.2+26.2` 这种写法。Fabric 官方 `fabric.mod.json` 文档也明确写明：
Fabric 使用 Semantic Versioning 的超集；`+` 后的 build metadata 在版本比较中会被忽略，
例如 `0.154+26.3` 与 `0.154+26.2` 在比较上等价。

> 发布时不要为了“Alpha N”把版本写成 `1.1.8-alpha.N` 或
> `1.1.8-fabric.26.2.alpha.N`。那会变成 prerelease，低于 `1.1.8`，导致要求
> `>=1.1.8` 的枪包失配。当前采用 `1.1.8+fabric.26.2.alpha.N`，Alpha 信息在
> build metadata 与 `fabric.mod.json` 展示文本中表达。

另一个容易踩的 Fabric 端细节：官方文档提到 Loader 0.19.3 对超过 3 段版本的 `.x`
通配符有 bug。因此本项目 `fabric.mod.json` 里 Minecraft 版本固定写 `"26.2"`，
不要写类似 `26.2.x` 的范围来“图省事”。

### 【无解】依赖 TacZ:Arcana 的加密枪包

**症状指纹**：枪械条目、名称、配方全都正常显示，**唯独模型不加载、贴图是紫黑块**。

这种「数据在、资源不在」的组合很像我们自己踩过的资源路径 bug，
但**先别急着查渲染代码** —— 先解压枪包数一下资源文件：

```bash
find . \( -name '*.png' -o -name '*.ogg' \) | wc -l     # 若为 0，基本可断定
find . -type d \( -name geo_models -o -name textures \) # 若为 0 个目录
ls recursion/taczpack.dat data/*/expansions/taczexpands.data 2>/dev/null
```

实测 *Tacz from Tarkov v1.1a*：

| 项目 | 结果 |
|---|---|
| 包内 `.png` / `.ogg` / 动画文件 | **0 个** |
| `geo_models/` / `textures/` 目录 | **0 个** |
| `recursion/taczpack.dat` | 17,336,143 B（占整包 18,185,340 B 的 95%） |
| 该 `.dat` 的香农熵 | **8.0000 bits/byte**（满熵 = 加密/已压缩），无任何容器 magic |
| `display` JSON 引用的资源条目 | 498 条，**全部指向不存在的文件** |

也就是说模型/贴图/动画/音效**全被加密塞进那个 `.dat`**，磁盘上只剩 JSON 索引。

解密由第三方前置 **TacZ:Arcana**（Forge 专有、闭源、All Rights Reserved）负责，
其功能列表里明写 "Resource gunpack assets protection for encrypted"。
TACZ 本体不含该实现 —— 全仓 grep `taczpack` / `recursion` / `expansions`
在**本项目和上游 1.21.1 里都是零命中**。

> **结论：这类包无解，且不该尝试去"修"。**
> Arcana 没有 Fabric / 26.2 版本，格式与密钥均未公开；
> 自行逆向既不现实，也涉及绕过作者的资源保护。
> 正确做法是在 README 里明确标注不受支持，让用户免于反复排查。

**另一个容易误判的点**：这类包通常要求 TACZ `1.1.5~1.1.8`。
我们的版本号已对齐 `1.1.8`，所以它**能通过版本校验、正常建出物品条目** ——
这恰恰是「看得到枪、却全是紫黑块」的原因，很容易被误读成「我们的渲染坏了」。

---

## 9. 方法论：那些走过的弯路

记录下来是为了让后来者**不要重复**。这几次都是「自己发明几何/光学近似」，
全部以推翻告终：

| 弯路 | 为什么错 |
|---|---|
| 用 PIP（离屏渲染世界）实现镜内放大 | 上游根本没有 PIP。放大是**摄像机 FOV 变焦**做的，全仓 grep `renderLevel`/`TextureTarget` 零命中 |
| 「按半径分内外壁」 | 凭空发明的几何划分，模型里没有这种结构 |
| 「准直光学补偿」把准星前推 | 上游 grep `collimat`/`parallax`/`billboard` **零命中**；准星随视角移动本就是真实透视的自然结果 |
| 「固定屏幕**中心**圆」做裁剪 | 圆心错了。上游取的是**目镜投影中心**，不是屏幕中心（半径倒确实是固定的，见 3.5） |
| 按 3D 几何缩放做开镜过渡 | 透视投影下缩放会同时改变投影**位置**，表现为镜圈从画面外「飞」进来。上游是纯二维操作：圆心固定、只有半径随进度长 |

**共同教训**：
> 遇到「上游怎么实现的？」这类问题，**先去 grep 上游源码**，
> 而不是先设计一个自认为合理的方案。
> 本项目每一次「我觉得应该这样」的发挥，最后都被上游源码打脸。

### 查第三方库 API：必须认准与依赖版本对应的分支

一次实打实的编译失败：给 `PartialNBTIngredient` 加了
`@Override toDisplay()`，报「方法不会覆盖或实现超类型的方法」。

根因是**参考了错误分支的源码**。搜索引擎给到的 Fabric API 页面
基本都是 1.21.x 的 **yarn 命名**，而本项目用 **Mojang 官方映射**，
且 Fabric API 从 26.x 起官方分支已改用 Mojang 名：

| yarn（1.21.9 及以前） | 26.2 官方分支 |
|---|---|
| `getMatchingItems()` | `items()` |
| `toDisplay()` | `display()` |
| `getPacketCodec()` | `getStreamCodec()` |

对应关系要去 `github.com/FabricMC/fabric` 的 **`26.2` 分支**查
（与 `gradle.properties` 里的 `fabric_version=0.155.2+26.2` 严格对应）。

**这类错误只有编译期才暴露**，字节码比对法（第 10 节）也帮不上忙 ——
因为 Fabric API 不在 `minecraft-merged` jar 里。
唯一可靠的办法就是核对正确分支的源码。

> 讽刺的是：仓库里原有的两个 Ingredient 类用的一直是正确的
> `items()` / `getStreamCodec()`。**只要照抄同文件里已有的写法就不会错** ——
> 舍近求远去搜外部文档反而翻车。改动既有类时，先看它自己怎么写的。

另一条：**一次只引入一个变量**。
瞄具渲染反复出现「叠叠乐式 bug」——每发现一种没覆盖的形态就再叠一个 `if`。
后来改成按**上游的分类维度**（目镜序号）重构，一次性解决，
且能证明「33 个瞄具里只有 1 个分类发生变化」。

### 9.1 【最高频】「方法没了」比「方法改名」危险得多

改名会**编译期报错**，你一定会发现。真正的杀手是**方法整个消失**，
而它原本承担的职责**没有任何人接手** —— 编译照过，功能静默失效。

两个已发生的实例，根因**完全相同**：

| 消失的方法 | 原职责 | 后果 | 接手方案 |
|---|---|---|---|
| `Item#getMaxStackSize(ItemStack)` | 同物品不同 NBT 各有堆叠上限 | 全部停在 `stacksTo(1)`，**不能堆叠** | 写 `DataComponents.MAX_STACK_SIZE` 组件 |
| `Item#getDescriptionId(ItemStack)` | 同物品不同 NBT 各有名字 | 全都显示同一个通用名 | 覆写 `getName(ItemStack)` |

> **检查项**：移植时每删掉一个「26.2 已不存在」的覆写，
> 必须当场回答「**这个方法原本在干什么？现在谁来干？**」。
> 答不上来就是埋了一个静默 bug。TACZ 的子弹（第 34 轮）与
> LRTactical 的手雷（本轮）**踩的是同一个坑**，隔了几十轮又踩一次。

配套结论（两次都验证过）：`stacksTo(n)` 的实现就是
`component(MAX_STACK_SIZE, n)`，写的是**物品级默认组件(prototype)**。
把它设成 1 还有第二重危害 —— `PatchedDataComponentMap#equals`
**同时比较 prototype 与 patch**，于是「写过组件的」和「没写过的」
两堆看起来一样的物品 `isSameItemSameComponents` 为 false，**永不合并**。
正确做法是把物品级默认值抬到 99（`Item.ABSOLUTE_MAX_STACK_SIZE`），
精确上限再逐个写进组件。

### 9.2 【易漏】Forge/NeoForge 的「自动订阅」在 Fabric 上会整类蒸发

`@EventBusSubscriber` 标注的类，Forge/NeoForge 会**自动扫描并注册**，
类本身没有任何显式调用方。移植到 Fabric 时，这类文件极易被整个漏掉 ——
它在源码树里看起来就像一个「没人用的类」。

已发生：LRTactical 的 `capability/TickHandler`（每 tick 驱动冷却计时器）
移植时漏掉，导致 `CustomItemCoolDowns#tickCount` 永远是 0、
`isOnCooldown` **恒为 true**，表现为「一局游戏里手雷只能用一次，
小退才能再用一次」。这与 Fabric 无 `DeferredRegister`（注册类必须显式 `init()`）
是**同一类问题的两个面**：

> **Fabric 上没有任何东西是自动发生的。**
> 注册要显式调用，事件订阅也要显式 `register`。

**检查项**：对照上游逐一列出所有 `@EventBusSubscriber` /
`@SubscribeEvent` 类，确认每一个都在 Fabric 侧有对应的显式注册。

### 9.3 `Entity#equals` 是按网络 id 比较的 —— 别拿实体直接当 map key

字节码确认：`Entity#equals` 实现是 `this.getId() == other.getId()`，
`hashCode()` 直接返回 `getId()`。

因此 `WeakHashMap<Player, X>` 这类「按实体挂数据」的写法，
在**单人游戏**里会让客户端玩家与服务端玩家（两个不同对象、网络 id 相同）
**撞进同一个条目**，被两端各 tick 一次。

解法（本仓库采用）：**按端分成两张表**，用 `player.level().isClientSide()` 选表。
比「包一层 identity key」更简单且无生命周期陷阱 ——
弱引用包装对象若无人强引用会立刻被回收，强引用又会钉住实体，两头不对。

#### 9.3.1 更狠的后果：**死亡重生会复用网络 id**

按端分表只解决了「两端互撞」，还有第二个坑 —— **同一端内部的新旧玩家也会撞**。

字节码确认 `PlayerList#respawn`：

```java
ServerPlayer newPlayer = new ServerPlayer(...);   // 全新对象
newPlayer.setId(oldPlayer.getId());               // 沿用旧的网络 id  ← 关键
```

客户端 `ClientPacketListener#handleRespawn` 同样是
`createPlayer(...)` 之后 `setId(旧 id)`。

于是重生后新玩家去查 `WeakHashMap<Player, X>`，**`computeIfAbsent` 会命中旧条目**
（id 相同 → `equals` 判定为同一 key），拿回的状态对象内部还持有**已死亡的旧玩家引用**。
若该状态里有计时器，就会**永远停在死亡那一刻**。

本项目的实际症状：**死后近战整局无法攻击，小退恢复，再死再犯**。
根因是 `coolDownTick` 不再递减 → `preAttack` 的 `if (coolDownTick > 0) return false` 恒真。

> **检查项**：凡是「按玩家对象挂载状态」且状态内部**持有玩家引用**或**含计时器**的，
> 都必须在重生时清理。
> - 服务端：`ServerPlayerEvents.AFTER_RESPAWN`
>   （其 javadoc 原文就是 "for reference clean up on the old player"）；
> - 客户端：**没有可用事件** —— `ClientPlayerNetworkEvent.CLONE` 在 26.2 永不触发
>   （原 mixin 依赖的 `ClientLevel#addPlayer` 已不存在）。
>   只能每 tick 比对 `Minecraft#player` 引用，见 `RefreshClonePlayerDataEvent`。

### 9.4 【血泪】会回调用户代码的引擎方法，必须查它的**内部调用序列**

9.1 说的是「只查方法名不查完整签名」。这一条是它的**升级版**，
代价是一次线上 StackOverflow。

事发经过：为了统一「提前结束使用物品」的行为，把 `ThrowableItem#onUseTick`
里的 `entity.stopUsingItem()` 换成了 `entity.releaseUsingItem()`。
换之前**确实比对过**两者的字段效果 —— 都会清空 `useItem` 与 `useItemRemaining`，
看起来完全可以互换。结果一按住手雷就爆栈。

真正的问题在**中间那一步回调**（字节码逐帧确认）：

```
onUseTick
  -> releaseUsingItem()
       -> ItemStack#releaseUsing        (offset 48)
       -> updatingUsingItem()           (offset 62)  ← 祸根
            -> updateUsingItem(stack)
                 -> ItemStack#onUseTick
                      -> 回到起点，且 useItem 尚未清空、
                         isUsingItem() 仍为 true -> 无限递归
       -> stopUsingItem()               (offset 66)  ← 永远走不到
```

两个致命细节，光看签名和字段副作用**一个都发现不了**：

1. `releaseUsingItem()` 在真正 `stopUsingItem()`（offset 66）**之前**，
   先调了一次 `updatingUsingItem()`（offset 62），而后者会**回调 `onUseTick`**；
2. `releaseUsingItem()` 自身**没有任何防重入门禁** —— offset 0 起直接取
   `useItem`，全程无 `isUsingItem` 检查。

（对比之下 `updatingUsingItem` 在 offset 1 就有 `isUsingItem` 门禁，
`startUsingItem` 在 offset 14 也有 —— 有没有门禁**因方法而异，必须逐个查**。）

> **检查项**：当你在一个**会被引擎回调的方法**（`onUseTick` / `inventoryTick` /
> `tick` 等）里调用引擎 API 时，必须反汇编那个 API，
> 确认它的调用序列里**不会绕回自己**。
> 只比对「两个方法的最终字段效果相同」是**不充分**的。

**修法**：调换顺序，`stopUsingItem()` 先于业务逻辑执行。
停用后 `isUsingItem()` 为 false，`updatingUsingItem` 的门禁直接拦下，
递归从根上断开。

**连带坑**：一旦先停用，`getTicksUsingItem()` 就会返回 0
（其 offset 1-4 是 `if (!isUsingItem()) return 0`）。
若业务逻辑内部还在现取这个值，会被静默算成 0。
本例中「预燃时长」正是如此，最终改为**由调用方在停用前取好、显式传参**。
这类「顺序调整引发的连锁失效」同样不报错，只能靠通读数据流发现。

---

### 9.5 【实测翻车】只查「类」不查「Builder / Properties 链式方法」

一次真实的编译失败：`MeleeItem` 里照抄上游写了

```java
super(properties.stacksTo(1).setNoRepair());   // 26.2 无 setNoRepair
```

移植前**确实核对过** `Item`、`ItemStack`、`DataComponents` 等一大批类，
唯独漏了 `Item.Properties` 这个**内部类**上的链式方法。

> **教训**：`XxxBuilder` / `Xxx.Properties` 这类**流式 API** 最容易漏 ——
> 因为它们不出现在「主类」的方法列表里，而人工核对时习惯只查主类。
> **凡是 `a().b().c()` 形式的链式调用，每一环都要单独核对。**

顺带记录 26.2 的语义反转（这也是 `setNoRepair` 消失的原因）：

| | 1.20 / 1.21 | 26.2 |
|---|---|---|
| 默认能否铁砧修复 | **能**，要禁止得显式 `setNoRepair()` | **不能** |
| 如何声明可修复 | 无（靠材料匹配） | 写 `DataComponents.REPAIRABLE` 组件（白名单） |

因此上游的 `setNoRepair()` 在 26.2 是**多余的**，直接删掉即为正确移植 ——
职责由「不写 REPAIRABLE 组件」这一默认行为承担，不属于 9.1 说的静默失效。

**已把该检查自动化**：`/tmp/inst.py` 会沿着变量声明 → 链式调用的返回类型
逐环反查字节码。写这类工具时**必须先用已知 bug 做自检**，
本次自检就暴露了三个会导致漏报的缺陷：

1. 正则只匹配首个 `.method(`，**链式的后续环节完全没扫到**；
2. 内部类 `Properties` 未映射到 `Item$Properties`；
3. **最隐蔽**：`has()` 沿继承链递归时，只要遇到 jar 外的类型
   （`java.lang.Object`、Fabric 注入的 `FabricItem$Properties`）就返回
   `None`（无法判定），把「确定缺失」误吞成「不确定」而不报错。

> 第 3 点值得单独记住：**「无法判定」与「不存在」必须分开处理**，
> 否则工具会安静地放过真 bug。修法是给 jar 外但可从源码核实的接口
> 建立成员白名单，并把 `java.lang.Object` 视为「已查完」。

**没有通过自检的检查工具，其「0 问题」结论毫无意义。**

### 9.6 核对 API 时，「存在」不等于「能用」—— 必须连 access flags 一起看

移植烟雾弹时一次编译失败暴露两个盲点，**都是核对做了一半**：

| 我查了什么 | 漏了什么 | 后果 |
|---|---|---|
| `SimpleParticleType(boolean)` 构造器**存在**、参数类型对 | **没查 access flags** —— 它是 `protected` | `new` 不出来 |
| `SingleQuadParticle` 的构造、`getLayer`/`getGroup` | `getLightColor` **想当然照抄了上游** | 26.2 已改名 `getLightCoords` |

两条具体结论：

1. **`SimpleParticleType` 的构造器是 protected**，模组不能直接 `new`。
   用 Fabric 官方工厂 `FabricParticleTypes.simple(boolean)` ——
   它内部是 `new SimpleParticleType(alwaysSpawn) { }`（匿名子类绕开限制），
   是为此专门提供的公开入口。
2. **`Particle#getLightColor` → `getLightCoords(float)`**，且是 `protected`。

> **检查项**：用 `javatools` 查 API 时，除了方法名与描述符，
> **必须同时打印 `is_public()/is_protected()`**。
> 「方法存在」和「你调得到」是两件事 —— 私有/保护成员、
> 以及第 9.1 节说的「方法整个消失」，都只有编译期才暴露。

**已把该检查自动化**：`/tmp/ovr.py` 校验所有 `@Override` 方法在 26.2
父类链上真实存在（现有工具都覆盖不到这一类）。
同样按 9.5 的规矩先用已知 bug 自检，并修掉两个误报：
一个文件多个 class 时的父类张冠李戴、以及嵌套类闭合后方法应归属外层类。

### 9.7 Mojang 会**修正拼写**——只差一个字母的改名最容易照抄翻车

移植效果云时遇到：

| 1.21.1 | 26.2 |
|---|---|
| `MobEffect#isInstant**e**neous()` | `isInstant**a**neous()` |
| `applyInstant**e**neousEffect(...)` | `applyInstant**a**neousEffect(...)`，且**新增 `ServerLevel` 首参** |

`e` → `a` 一个字母之差。这类改动的危险在于：**肉眼扫过去觉得"名字对上了"**，
不像 `getNormal` → `getUnitVec3i` 那样一眼看出不同。

> **检查项**：核对 API 时不要用「看着差不多」判断，
> 一律以 `grep` 出的**完整方法名字符串**比对。
> 拼写修正类改名往往还**顺带改签名**（本例加了 `ServerLevel`），
> 两个变更叠在一起，只对其中一个更容易漏。

同类已记录的还有 9.1 的「方法整个消失」与 9.6 的「access flags」——
三者的共同点是：**编译期才暴露，且报错信息不会告诉你新名字是什么**。

### 9.8 【最危险】签名没变、**语义变成空实现**——编译通过，什么都不画

补完 LRTactical 动画层时遇到的最阴的一个坑。

`BedrockModel#render(PoseStack, ItemDisplayContext, RenderType, int, int)`
在 1.21.1 与 26.2 上**签名完全一致**，照抄上游代码可以正常编译、正常运行、
不报任何错 —— 但**屏幕上什么都没有**。

原因是本仓库 26.2 移植时把它改成了这样：

```java
@Deprecated
public void render(PoseStack matrixStack, ItemDisplayContext transformType,
                   RenderType renderType, int light, int overlay, ...) {
    // 26.2: Minecraft.renderBuffers() removed. Use submit() or renderInto() instead.
    // Fallback: no-op for deprecated path. Callers should migrate to submit().
}
```

**方法体是空的**。真正干活的是新增的
`submit(PoseStack, ItemDisplayContext, SubmitNodeCollector, RenderType, int, int)`。

这比 9.1（方法消失）和 9.7（拼写修正）都更难发现：
那两类**至少会编译失败**，而这一类**一路绿灯**，
只在实机测试时表现为「模型不显示」，而排查方向会被引向
「是不是资源没加载」「是不是 display 没解析」等完全无关的地方。

> **检查项**：移植渲染 / 事件 / 回调类代码时，凡是「上游调了、26.2 也存在」的方法，
> **必须打开本仓库的实现看一眼方法体**，确认它不是：
> - 空实现 / 只有一行注释；
> - 标了 `@Deprecated` 且注释里写着 "use X instead"；
> - 直接 `return` 默认值的 stub。
>
> `grep -n "方法名" -A5` 看五行就够，成本极低。
> 本次若省了这一步，交付的就是一个「编译通过但整个动画层不工作」的版本。

### 9.9 【实测翻车】字段被私有化，但**同名 getter 还在**

动画层第一次 `gradlew build` 的**唯一**一个编译错误：

```
BaseAnimationStateContext.java:69: 错误: 找不到符号
        Entity entity = Minecraft.getInstance().cameraEntity;
  符号:   变量 cameraEntity
  位置: 类 Minecraft
```

| 1.21.1 | 26.2 |
|---|---|
| `public Entity cameraEntity;`（公开字段） | 字段不可访问，改用 `getCameraEntity()` |

字节码确认：`Minecraft` 上公开字段只剩 `crosshairPickEntity`，
取相机实体只有 `getCameraEntity()` / `setCameraEntity(Entity)`。

**真正该反省的不是这个 API，而是流程**：本仓库
`GunAnimationStateContext#processCameraEntity` **早就写成 `getCameraEntity()` 了**，
一模一样的方法名、一模一样的用途。这是「**没先 grep 仓库既有解法，直接照抄上游**」——
用户已多次强调过的那条，又犯了一次。

> **检查项（新增自检脚本 `fieldchk.py`）**：
> 移植时对所有 `obj.field` 形式的**字段访问**（区别于 `obj.method()`），
> 按变量声明类型去字节码里确认该字段真实存在。
> 字段与方法在 Java 里是两个命名空间，**`getXxx()` 存在完全不能推出 `xxx` 字段可访问**。
> 脚本本身必须先自检：故意注入 `cameraEntity` 确认能报出来，修好后确认归零 ——
> 否则「0 问题」毫无意义（见 9.5）。

---

## 10. 沙盒/无 JDK 环境下的验证手法

本项目全程在**没有 JDK、没有 GPU**的环境下开发，用以下方法替代编译验证：

```bash
# 1. 用 javatools 反汇编 26.2 字节码，确认 API 真实存在
pip install javatools
# 反汇编某个方法，看它到底调了什么
python3 dis.py net.minecraft.client.gui.GuiGraphicsExtractor text

# 2. 直接查 jar 里的资源路径，确认目录名
unzip -l minecraft-merged-*.jar | grep -oE 'data/minecraft/tags/[a-z_]+/' | sort -u

# 3. 括号平衡检查（剔除注释与字符串后）代替语法检查
```

**目前一共 7 个自建校验脚本，缺一不可**（每轮交付前全部跑一遍）：

| 脚本 | 作用 | 对应踩过的坑 |
|---|---|---|
| `q.py <类> [过滤]` | 列 fields/methods 的**完整描述符** | 9.6 access flags |
| `dis.py <类> <方法>` | 反汇编，看内部调用序列 | 9.4 引擎回调顺序 |
| `bal.py <文件...>` | 括号平衡（剔注释/字符串） | 语法 |
| `missing.py` | 缺失 import | — |
| `inst.py` | **链式调用逐环反查** | 9.5 只查首环 |
| `ovr.py` | `@Override` 在父类链上是否真实存在 | 9.1 方法消失 |
| `fieldchk.py` | **字段式访问**是否真实存在（区别于方法） | 9.9 字段私有化 |

> **每个脚本自己也必须先自检**：故意注入一个已知错误，确认它报得出来，
> 修好后确认归零。**没通过自检的检查工具，它给出的「0 问题」毫无意义**（见 9.5）。

> `javatools` 的 `get_descriptor()` **会擦除泛型**，
> 要看泛型签名得用 `get_signature()`。

**这不能替代真正的编译**。每次交付都应如实标注「未编译验证」，
并建议先 `./gradlew build`。
