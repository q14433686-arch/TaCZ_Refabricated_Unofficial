# 接手说明 · TACZ 26.2 移植

面向下一位接手者。读完这一份就能继续干活，不必先读历史文档。

---

## 0. 先看这个：历史文档可信度

仓库里有 4 份早期文档，**不要直接采信**，它们彼此矛盾且有已证伪的结论：

| 文档 | 可信度 |
|---|---|
| `AUDIT_REPORT_2026-07-25.md` | 环境/依赖/资源部分可信；**渲染部分的归因多处错误** |
| `STAGE1_COMPLETION_REPORT.md` | 阶段 1 的两个修复属实；**第一人称归因错误** |
| `FIX_SUMMARY_QUICK.md` | 与 `AUDIT_REPORT` 就渲染适配状态**结论互斥** |
| `FIX_LOG_STAGE1.md` | 同上 |

**以 `PROGRESS_ROUND1.md` ~ `PROGRESS_ROUND6.md` 为准**（越靠后越新），
其中每条结论都注明了反编译证据出处。

> ⚠️ **注意：进度报告之间也存在自我更正。** 后一轮可能推翻前一轮的结论，
> 冲突时**一律以轮次更大的为准**。已知的自我更正：
> - `ROUND5` §① 推翻并撤销了 `ROUND4` 对"第三人称残缺手臂"的修复（那次修复是倒退）
> - `ROUND6` §② 推翻了 `ROUND4`/`ROUND5` 对"行走抖动"的判断（真因是量纲错误，非插值问题）

---

## 1. 环境搭建（15 分钟）

```bash
# 1. JDK：必须 21+，Gradle 9.5.1 在 JDK 11 上跑不起来。实测用 Temurin 25.0.3
curl -L -o jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse"
mkdir jdk && tar xzf jdk.tar.gz -C jdk --strip-components=1
export JAVA_HOME=$PWD/jdk

# 2. 构建（源码包内 gradlew 可执行位已修复，可直接 ./gradlew）
./gradlew compileJava           # 首次约 2 分钟，会下载并 remap MC 26.2
./gradlew build -x test         # 产出 build/libs/*.jar
```

**内存**：客户端开发运行至少 768 MB 堆，建议 1–2 GB。

### 拿到 26.2 反编译源（做任何渲染改动前必做）

本项目的铁律是**所有签名/写法都要对着 26.2 反编译源核**，不能凭注释或记忆迁移。

```bash
# MC jar 位置（Loom 下载后）
~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.2/minecraft-merged-deobf-26.2.jar

# 快速查签名
javap -p -cp <上面那个jar> net.minecraft.client.renderer.ItemInHandRenderer

# 要读实现就用 Vineflower
curl -L -o vineflower.jar https://github.com/Vineflower/vineflower/releases/download/1.11.1/vineflower-1.11.1.jar
mkdir -p cls && cd cls && unzip -q <mcjar> 'net/minecraft/client/gui/*' && cd ..
java -jar vineflower.jar --silent cls out
```

**同样重要**：上游 1.21.1 源码是判断"这段代码原本想干什么"的唯一可靠参照：
`https://raw.githubusercontent.com/Sh1roCu/TACZ-Refabricated/1.21.1/<路径>`

---

## 1.5 26.2 破坏性变更速查（第 3~6 轮实战踩出来的）

这些都已由反编译证实，是本项目返工最多的地方。**改任何相关代码前先对照本表。**

| 领域 | 1.21.x 行为 | 26.2 行为 | 踩坑表现 |
|---|---|---|---|
| **文本颜色** | `drawString` 对 alpha=0 自动补不透明 | `GuiGraphicsExtractor#text` 内部 `if (ARGB.alpha(color) != 0)`，**静默丢弃** | 6 位色值（`0x777777`）的文字全部不显示；框还在、尺寸也对 |
| **行走距离** | `Entity.walkDist` / `walkDistO`（`+= 位移*0.6`） | 玩家侧继任者是 **`ClientAvatarState.walkDist/walkDistO`**（`AbstractClientPlayer.avatarState()`，带官方插值 `getInterpolatedWalkDistance(pt)`，量纲同为 ×0.6）。`Entity.moveDist` 虽也是 ×0.6，但**只对 `LocalPlayer` 累加**（见下）；`walkAnimation.position`（`+= min(位移*4,1)`）**是第三个量，量纲差约 6.7 倍** | 误用 position → 动画快 6.7 倍；误用 moveDist → **多人下其他玩家动画完全静止** |
| **手部渲染入口** | SBM 注入 `renderArmWithItem` HEAD 并 cancel | 需注入 `submitArmWithItem` HEAD；且 `ItemInHandRenderer` 是**全局共享实例**，必须自行判 `getCameraType().isFirstPerson()` | 第三人称出现多余残缺手臂 |
| **模型提交** | 立即写顶点 | `submitModel` 只 `poseStack.last().copy()` 拷**矩阵**，`model`/`ModelPart` 是**活引用**，稍后 `renderAllFeatures` 才绘制 | submit 后还原骨骼姿态 → 绘制时读到错误状态（r4 教训） |
| **自定义几何** | — | `submitCustomGeometry` 回调首参 `pose` 才是提交时快照；外层 `poseStack` 那时已被 popPose | 用错 → 图标画到可视区外（物品栏空白） |
| **物品 display** | `builtin/entity` 无 transform | 该 builtin 已移除；换 `item/generated` 会**继承一整套手持偏移** | 第一人称枪械被额外旋转/位移 |
| **GUI 图标路径** | — | 包围盒 >16px 会走 `OversizedItemRenderer` PIP 离屏路径 | `EXTENTS` 设太大 → 图标异常 |

### 1.5.1 【陷阱】`Entity.moveDist` 在客户端只对本机玩家累加

第 20 轮核查发现，r6/r7 写的「`walkDist` 更名 `moveDist`、语义完全一致」**只对了一半**。
26.2 给 `moveDist` 的写入加了门禁（`Entity.move` 偏移 601~648，字节码确认）：

```
if (level.isClientSide() && !isLocalInstanceAuthoritative()) -> 整段跳过
...
applyMovementEmissionAndPlaySound(...)     // moveDist 唯一写入点
```

门禁逐级展开：

```
Entity.isLocalInstanceAuthoritative() -> Player.isLocalClientAuthoritative()
    -> Player.isLocalPlayer()        = iconst_0   (基类恒 false)
       LocalPlayer.isLocalPlayer()   = iconst_1   (只有本机玩家)
```

**即客户端上 `moveDist` 只对 `LocalPlayer` 累加，`RemotePlayer` 恒为 0。**
症状：多人游戏里**其他玩家的持枪行走动画完全静止**，而本机自测发现不了。

**正确做法**：玩家一律走 `AbstractClientPlayer.avatarState().getInterpolatedWalkDistance(pt)`
（`ClientAvatarState` 自带 `walkDist/walkDistO` + `Mth.lerp`，量纲同为 ×0.6，
vanilla 自己在 `AvatarRenderer.extractCapeState` 就这么用）。已在
`GunAnimationStateContext#tacz$walkDistance` 修正；非玩家实体仍回退 `moveDist`。

### 1.5.2 【陷阱】本项目自己给 vanilla 类注入了接口

`fabric.mod.json` 的 `loom:injected_interfaces` 给两个 vanilla 类注入了接口：

| vanilla 类 | 被注入 |
|---|---|
| `com/mojang/blaze3d/pipeline/RenderTarget` | `cn/sh1rocu/tacz/api/mixin/RenderTargetStencil` |
| `net/minecraft/world/entity/LivingEntity` | `cn/sh1rocu/tacz/api/mixin/ItemHandlerCapability` |

**反编译本地 loom 缓存的 jar 时会看到这些注入痕迹**（例如 `RenderTarget implements
RenderTargetStencil`），它们**不是 Mojang 的东西**。第 20 轮核查时曾因此一度误判
「26.2 还残留 stencil 支持」。查 vanilla API 是否存在时务必排除这两个接口。

---

## 2. 26.2 三个必须知道的渲染范式变化

踩过的坑都在这里，改渲染前务必读完。

### 2.1 GUI 拆成 extractBackground / extractContents，后者**自带平移**

```java
// Screen#extractRenderStateWithTooltipAndSubtitles
graphics.nextStratum();
this.extractBackground(...);   // 未平移
graphics.nextStratum();
this.extractRenderState(...);

// AbstractContainerScreen#extractContents —— 注意这里
graphics.pose().pushMatrix();
graphics.pose().translate(leftPos, topPos);   // ← 方法体内所有绘制都已带偏移
this.extractSlots(...);
graphics.pose().popMatrix();
```

**规则**：容器界面的背景一律画在 `extractBackground` 里并使用 `leftPos/topPos`；
**不要**在 `extractContents` 里再加 `leftPos/topPos`（会偏移翻倍）。
20 个 vanilla 容器界面无一例外遵守此规则。

Widget 侧：`AbstractWidget#extractWidgetRenderState` 是抽象方法，
`AbstractButton` 把它实现为 final 并转发到 `extractContents` —— 所以**按钮覆写
`extractContents` 是正确的**，别改。

### 2.2 物品渲染：display transform 在 special renderer **之前**叠加

```java
// ItemStackRenderState.LayerRenderState#submit
poseStack.pushPose();
this.applyTransform(poseStack.last());   // ← 模型 JSON 的 display 在此生效
if (this.specialRenderer != null) {
    this.specialRenderer.submit(...);    // TACZ 的渲染在这之后
}
```

且 `ResolvedModel#findTopTransform` 会**沿 parent 链继承** transform。

**规则**：TACZ 自绘的物品，其 base 模型**不能**继承 `minecraft:item/generated`
（它带 `firstperson_righthand` 等一整套偏移）。上游用的 `builtin/entity` 在 26.2 已移除，
等价做法是**写成无 parent 的根模型**（`ModelDiscovery#isRoot` 认 `parent()==null` 合法）。
第 1 轮就是栽在这上面 —— 第一人称枪械被额外转了近 90°。

### 2.3 实体层渲染：extract → submit 两段式，且快照对 functional 节点有硬性要求

旧的 `ItemRenderer#renderStatic(...)` 已删除。等价链路：

```java
ItemStackRenderState st = new ItemStackRenderState();
mc.getItemModelResolver().updateForTopItem(st, stack, ctx, level, owner, seed);
st.submit(poseStack, collector, light, overlay, 0);
```

**坑**：`BedrockRenderSnapshot#capturePart` 遇到 `FunctionalBedrockPart` 的 provider
**返回了 renderer 但它不是 `IFunctionalSubmitter`** 时，会 `skippedFunctionalNodes++`
并**直接 return，连子节点都不遍历** → 整棵子树静默消失，且不报任何错。

**规则**：写 functional 节点 provider 时，要么 `return null`（纯可见性钩子，几何交给遍历器），
要么返回实现了 `IFunctionalSubmitter` 的对象。**绝不能返回裸 `IFunctionalRenderer` lambda。**
副弹匣就是这么丢的（第 2 轮已修）。

---

## 3. 当前代码地图（渲染相关）

```
第一人称枪械（活路径）
  assets/tacz/items/*.json  ──"type": "tacz:dynamic_item"
    └─ TaczDynamicItemModel (ItemModel)
         └─ TaczSpecialRenderer (SpecialModelRenderer)
              └─ BuiltinItemRendererRegistry → AnimateGeoItemRenderer#render
                   ├─ mode.firstPerson() → renderFirstPerson()   ← 第一人称
                   └─ else               → renderByItem()        ← 三人称/GUI/掉落物（这里才查 LOD）

第三人称"背挂"枪械
  ItemInHandLayerMixin#submitTail → HumanoidOffhandRender.renderGun()
                                      ├─ 副手枪
                                      └─ 快捷栏未手持的枪

Bedrock 模型几何
  BedrockModel#submit → BedrockRenderSnapshot.capture()  ← 快照，避免延迟提交时读到被 mutate 的状态
                          ├─ DrawCommand[]        （静态几何）
                          └─ IFunctionalSubmitter （抛壳/枪焰/配件/文字/手臂）
```

**已废弃的 legacy 路径**（活路径只有 `submit(...)`，勿再往里加东西）：
`BedrockModel#render(...)`、`BedrockGunModel#render(...)`、
`BedrockAttachmentModel#render(...)`、`renderAccelerated(...)`、
各 functional renderer 的 legacy `render(...)` 重载。

> ⚠️ **更正（第 20 轮）**：此处原文写作「全仓无调用点，建议整体删除」，
> 该表述**不准确**，照字面直接删会编译失败。这些方法处在**不可达的分支**里，
> 但**彼此之间仍有调用**，实测残留：
> - `BedrockGunModel.java:325`、`:424` —— `super.render(...)`
> - `BedrockGunModel.java:296` —— `renderAccelerated(...)`
> - `BedrockAttachmentModel.java` —— 多处 `renderTempPart(...)`（本身已是 no-op）
>
> 准确说法是「**从活路径（`submit`）不可达**」。删除前需先自底向上摘掉这些内部调用。

---

## 4. 已知未解决问题（按优先级）

1. **瞄具 stencil / PIP 未实现** —— `submit` 路径降级为普通几何，镜内能看到枪体。
2. **副手开枪**：上游 1.21.1 即不支持（所有输入门禁都是 `mainHandHoldGun`），
   属新功能而非移植缺陷，详见 `PROGRESS_ROUND3.md` §④。
3. **"第三人称手臂消失"若实测仍在**，排查 `PlayerModelMixin` / `InnerThirdPersonManager`。
   **不要**再去查 `ItemInHandLayerMixin` —— 已证伪，理由见 `PROGRESS_ROUND2.md` §1。
3. **PAL（Player Animation Library）迁移层** `PlayerAnimatorCompat` 未实机验证。
4. **一批 compat 是 no-op**：Iris/Sulkan、ImmediatelyFast、Shoulder Surfing、Controllable、
   Carry On、KubeJS、AcceleratedRendering。别对外宣称支持。
5. **`gradlew` 可执行位**、**缺 README/LICENSE**。
6. **没有任何自动化测试**。建议先补两条 GameTest：
   断言 item 模型 display 为恒等；断言 `skippedFunctionalNodes == 0`。

---

## 5. 工作方式约定（沿用）

1. **凡涉及签名、API 写法 → 必须反编译 26.2 核对**，不接受"注释这么写的"。
2. **凡涉及"这段代码原本要干什么" → 对照上游 1.21.1**。
3. 改完至少跑 `bash gradlew build`；渲染改动**额外校验字节码引用**是否解析到真实签名：
   `javap -p -c <class>` 看 `// Method ...` 行。
4. 每轮产出一份 `PROGRESS_ROUNDn.md`，写清：**证据出处、改了什么、没验证什么**。
   不要把"编译通过"写成"功能正常"。
5. 沙盒/CI 无 GPU 时，**明确标注"未实机验收"**，不要含糊。

---

## 6. 沙盒/CI 工作区注意事项（踩过多次）

如果你也在受快照大小限制的环境里工作（本项目开发时约 **128 MB** 上限）：

- **完整环境约 1.3 GB**（JDK 303 MB + `.gradle` Loom 缓存 549 MB + repo 365 MB），
  一旦超限，**整个快照会被静默丢弃** —— 表现为"下一轮回来时源码和 JDK 都没了"。
- 因此每轮结束前务必清掉可重建产物：
  ```bash
  rm -rf jdk mc262 .gradle vineflower.jar repo   # 全部可从网络/源码包重建
  ```
  只保留 `deliver/`（jar + 源码 zip + 进度报告）。
- 根目录的 `patch_r4.py` / `patch_r5.py` / `fix_alpha.py` / `fix_slotpose.py`
  是各轮修改的可重放脚本（幂等），环境丢失后可据此快速复现改动。

## 6.5 数据来源替换的注意事项（第 13 轮教训）

`GunSmithTableResult` 这类对象存在**两阶段初始化**：Gson 反序列化只填充
`RawGunTableResult`，真正的 `ItemStack`/`group` 要调用 `init()` 才解析。

第 12 轮把配方来源从 `RecipeManager`（已 init 过）换成同步来的原始 POJO 时漏掉了
`init()`，导致**所有配方的 result 为 EMPTY、group 为 null，被全部过滤掉**。

**规则**：替换任何数据来源前，先确认目标对象是否需要额外的生命周期调用。
可全仓 grep `\.init()` 确认。

---

### 6.6 【铁律】数据 codec 的解析时机 —— tag 必须等绑定之后

第 14 轮踩到的坑，**代价是 172 条配方里 171 条静默消失**。

**现象**：只有 `attachments/ammo_mod_he.json`（唯一不含 `#tag` 的配方）能显示。

**机制**（逐级反编译证实）：

1. `Ingredient.CODEC` → `HolderSetCodec.create(Registries.ITEM, ...)`
2. `HolderSetCodec#decode` 遇 `#tag` → `lookupTag()` → 未绑定则 `DataResult.error("Missing tag: ...")`
3. `MappedRegistry#get(TagKey)` 读 `allTags`，需 `PendingTags#apply()` 后才有内容
4. `ReloadableServerResources#loadResources` 只**存** `postponedTags`；
   真正 `apply()` 在 `MinecraftServer#reloadResources` 的 `thenAcceptAsync` 里，
   **晚于所有 reload listener**

**结论**：任何在 **reload listener 内部**执行的 codec 解析，
只要涉及 `#tag`（`Ingredient` / `HolderSet` / `TagKey`），**必然失败**。

**正确姿势**：
- 要么像 vanilla `RecipeManager` 那样，拿 `fullRegistries.lookupWithUpdatedTags()` 构造 ops；
- 要么**延迟解析**——反序列化只存原始 JSON，首次取用时再解析（本项目采用）。

**额外警告**：`JsonDataManager#apply` 会把 `JsonParseException` catch 住只打一行 error，
所以这类失败**不会崩服，只会让数据神秘消失**。排查时务必先数「到底加载进来几条」。

**关联**：这是 §6.5「数据来源替换的注意事项」的具体案例——
从 vanilla 通道换到自建通道时，丢掉的不只是数据本身，
还有原通道提供的**隐含保障**（这里是「带 tag 的 lookup」）。

## 7. 各轮修复索引

| 轮次 | 修复内容 | 报告 |
|---|---|---|
| 1 | 工作台 UI 错位（`extractBackground` vs `extractContents` 平移）；第一人称错位（item 模型 parent） | `PROGRESS_ROUND1.md` |
| 2 | `HumanoidOffhandRender` 背挂枪实现；mixin 副手判定（`mainArm` 而非 LEFT）；副弹匣静默丢弃 | `PROGRESS_ROUND2.md` |
| 3 | 第一人称注入点对齐 SBM；装配台手持模型缩放（`ClientBlockIndex` transforms） | `PROGRESS_ROUND3.md` |
| 4 | tooltip 文字 alpha（43 处）；GUI identity key | `PROGRESS_ROUND4.md` |
| 5 | **撤销 r4 的手臂"还原"**（是倒退）；第一人称视角门禁；slot 回调用错矩阵（7 处） | `PROGRESS_ROUND5.md` |
| 6 | **行走动画快 6.7 倍**（`walkAnimation.position` → `moveDist` 量纲修正） | `PROGRESS_ROUND6.md` |
| 7 | 行走动画插值（`EntityMixin` 重建 `walkDistO`） | `PROGRESS_ROUND7.md` |
| 8 | 副弹匣镜像几何；袖子双重变换；标靶车继承 `AbstractMinecartRenderer`；弹孔粒子重载错绑 | `PROGRESS_ROUND8.md` |
| 9 | 曳光弹摄像机角度硬编码为 0 | `PROGRESS_ROUND9.md` |
| 10 | **撤销 r9 的瞄具黑方块回归**；确认 26.2 无 stencil | `PROGRESS_ROUND10.md` |
| 11 | 仅诊断（工作台/后坐力/曳光弹三项根因） | `PROGRESS_ROUND11.md` |
| 12 | 接上 `DataType.RECIPES` 同步通道 | `PROGRESS_ROUND12.md` |
| 13 | **修 r12 回归**：补 `GunSmithTableRecipe.init()` | `PROGRESS_ROUND13.md` |
