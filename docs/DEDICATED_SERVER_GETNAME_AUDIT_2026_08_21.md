# 双端公共方法读 client 索引的修复与全树审计（2026-08-21）

**分支**：`arena/01a0255f-tacz-refabricated-unofficial`（自 `26.2(main)` / `8edac57` 分出）
**范围**：`Item#getName(ItemStack)` 四处病灶 + 全源码树「common 代码引用 client 类」审计
**验证状态**：静态核查完成；**未**在 Fabric 专用服务器上实测（本工作区无 JDK、无 26.2
依赖缓存，无法构建或起服）。changelog 措辞请按第 6 节选用。

---

## 1. 改了什么

| 文件 | 方法 | 改动 |
| --- | --- | --- |
| `com/tacz/guns/api/item/gun/AbstractGunItem.java` | `getName(ItemStack)` | 删 `@Environment(EnvType.CLIENT)`；`getClientGunIndex` → `getCommonGunIndex().getPojo().getName()` |
| `com/tacz/guns/item/AmmoItem.java` | `getName(ItemStack)` | 同上（`CommonAmmoIndex`） |
| `com/tacz/guns/item/AttachmentItem.java` | `getName(ItemStack)` | 同上（`CommonAttachmentIndex`） |
| `com/tacz/guns/item/GunSmithTableItem.java` | `getName(ItemStack)` | 同上（`CommonBlockIndex`） |

配方（四处一致）：

```java
@Override
@Nonnull
public Component getName(@Nonnull ItemStack stack) {
    Identifier id = this.getXxxId(stack);
    Optional<CommonXxxIndex> index = TimelessAPI.getCommonXxxIndex(id);
    if (index.isPresent() && index.get().getPojo() != null) {
        String name = index.get().getPojo().getName();
        return Component.translatable(StringUtils.isBlank(name) ? "custom.tacz.error.no_name" : name);
    }
    return super.getName(stack);
}
```

四个 common 索引<b>都已经暴露 `getPojo()`</b>（`CommonGunIndex` / `CommonAmmoIndex` /
`CommonAttachmentIndex` / `CommonBlockIndex`），对应的 POJO 都有 `getName()`
（`GunIndexPOJO` / `AmmoIndexPOJO` / `AttachmentIndexPOJO` / `BlockIndexPOJO`，
`@SerializedName("name")`）。**本次无需新增任何 getter**。

顺带删掉了四个文件里因此不再使用的 `Client*Index` import（`AmmoItem` 的
`ClientAssetsManager` / `PackInfo` 仍被 `appendHoverText` 使用，保留）。

---

## 2. 关键更正：fabric-loader **会**剥离成员上的 `@Environment`

原始报告的事实链第 1 条写的是「fabric-loader 从不剥离 `@Environment` 成员——该注解只是文档」。
**这一条对 Fabric 不成立**，2026-08-21 逐文件读 fabric-loader 源码确认（loader pin 0.19.3）：

* `MinecraftGameProvider#getBuiltinTransforms(String)`：凡是**不以 `net.minecraft.` /
  `com.mojang.*` 开头、且带包名**的类（即 mod 类），一律返回
  `TRANSFORM_STRIPENV = EnumSet.of(BuiltinTransform.STRIP_ENVIRONMENT)`。
* `FabricTransformer#transform`：`environmentStrip` 为真时用
  `EnvironmentStrippingData` 扫注解，非空则挂 `ClassStripper`。
* `EnvironmentStrippingData`：`visitField` / `visitMethod` 都会收集
  `Lnet/fabricmc/api/Environment;` 且 `value` 与当前 env 不匹配的成员，
  分别进 `stripFields` / `stripMethods`。
* `ClassStripper#visitMethod`：命中 `stripMethods` 直接 `return null`，即**整方法删除**。
* 官方注解 javadoc 亦写明：*"Use with caution, as Fabric-loader will remove the annotated
  element in a mismatched environment!"*

来源：
<https://github.com/FabricMC/fabric-loader/blob/master/minecraft/src/main/java/net/fabricmc/loader/impl/game/minecraft/MinecraftGameProvider.java>、
`.../impl/transformer/FabricTransformer.java`、`EnvironmentStrippingData.java`、`ClassStripper.java`、
`net/fabricmc/api/Environment.java`。
本仓库里 `me/xjqsh/lrtactical/capability/CombatProperties.java#playAttackSound` 的注释
（"Fabric 会在专用服务器上把该方法整个剥离…抛 NoSuchMethodError"）与源码一致，可互相印证。

### 2.1 因此 Fabric 侧修复前的**真实症状**

不是 `NoClassDefFoundError`，而是：

> 专用服务器上这四个 `getName` 覆写**根本不存在**（被 loader 删掉），调用落回
> `Item#getName`，于是所有**服务端**产生名字的路径都显示原版兜底名
> （`item.tacz.modern_kinetic_gun` 之类），而不是枪包里的名字。

受影响的服务端路径至少有：`/give` 的回执消息、容器/菜单标题、铁砧改名与「是否改过名」
比较、死亡消息里的手持物、其他 mod 在服务端读 `ItemStack#getHoverName`（分拣/日志/
经济类 mod）。客户端自己渲染的 tooltip 与 GUI 不受影响 —— 所以这是个**静默的
客户端/服务端不一致**，不是崩溃，也很难被玩家正确归因。

姊妹项目（NeoForge / TaCZ-Renovated）观察到的 `/give` → `NoClassDefFoundError`
不能直接搬运到 Fabric 侧的 changelog：两边加载器对「环境注解」的运行期处理不同，
症状不同。**根因相同**（双端公共方法依赖 client 侧索引），**表现不同**。

### 2.2 「只删注解不改实现」才是崩服路径

如果有人认为注解只是文档、把注解删掉却仍调 `getClientGunIndex`，专服上就会真的去加载
`com.tacz.guns.client.resource.index.ClientGunIndex`（其字段/方法签名引用
`net.minecraft.client.*`，server 分发里不存在）→ `NoClassDefFoundError`。
本次改动同时消灭了这两种失败模式：既不靠注解，也不碰 client 类。

---

## 3. 行为等价性论证（为什么客户端显示不变）

1. **同源**：`ClientGunIndex#getName` 只是把 `GunIndexPOJO#getName()` 抄进字段，
   空白时替换成 `custom.tacz.error.no_name`；common 索引持有的是**同一个 POJO 对象**
   （`CommonGunIndex.getInstance(pojo)`）。四种索引都一样。
   因此本次实现显式保留了 `isBlank → custom.tacz.error.no_name` 的兜底，
   做到逐字符等价，而不是退回 `super.getName`。
2. **翻译时机**：返回的是 `Component.translatable(key)`，服务端只发 key，客户端渲染时
   用自己的语言文件翻译 —— 与旧实现产出的组件完全一致，无需 dist 分支。
3. **多人游戏客户端也拿得到 common 索引**：`CommonAssetsManager.get()` 在
   `INSTANCE == null`（即连着远程服务器的纯客户端）时回退到 `CommonNetworkCache`，
   后者由 `SYNC_DATA_PACK_CONTENTS` → `ServerMessageSyncGunPack` 下发，
   `fromNetwork` 用 `CommonXxxIndexSerializer` 反序列化，**pojo 字段同样被填充**
   （`CommonGunIndexSerializer#deserialize` → `CommonGunIndex.getInstance(pojo)`）。
   所以纯客户端不会因为改读 common 索引而丢名字。
4. **加载时序**：index 与物品名字都在 datapack 同步完成后才被读取；
   `getPojo() == null` 与 `index.isEmpty()` 两个兜底都落回 `super.getName`，
   与旧代码的 `isPresent()` 判空行为一致。

---

## 4. 全源码树审计（不只 `com.tacz`）

扫描命令（refab 独有的 LR 内置框架 `me.xjqsh.lrtactical.*` 与 `cn.sh1rocu.*` 扩展一并纳入）：

```bash
grep -rn "getClient\|ClientIndexManager\|client\.resource" src/main/java --include="*.java" \
  | grep -v "/client/"
# 以及：列出 client 包外所有 @Environment(EnvType.CLIENT) 成员
```

判定标准（沿用报告第二节）：**方法是否可能被服务端路径调用**。
仅由 tooltip / 渲染 / 相机 / GUI / S2C 包处理器调用的，惰性解析下不会在专服触发类加载，
判「安全可留」。

| 位置 | 引用的 client 内容 | 谁会调 | 判定 |
| --- | --- | --- | --- |
| `AbstractGunItem#getName`、`AmmoItem#getName`、`AttachmentItem#getName`、`GunSmithTableItem#getName` | `Client*Index` | **双端**（/give、容器标题、铁砧…） | ❌ 病灶 → **本次已修** |
| `AmmoItem#appendHoverText` | `ClientAssetsManager`、`PackInfo`、`getClientAmmoIndex` | 仅 `ItemStack#getTooltipLines`（客户端） | ✅ 保留（带 `@Environment`，专服上被剥离即可） |
| `*Item#getCustomRenderer`（Gun/Ammo/AmmoBox/Attachment/GunSmithTable/Melee/Throwable） | `BuiltinItemRendererRegistry` | 仅客户端渲染注册 | ✅ 保留 |
| `GunItemDataAccessor#getAimingZoom`（`IGun` 默认方法，**未**标注解） | `getClientAttachmentIndex`、`GunDisplayInstance` | 全部调用点为 `client/event/CameraSetupEvent`、`client/renderer/item/GunItemRendererWrapper` | ⚠️ 安全但脆弱：它是 `IGun` 的公共默认方法，第三方若在服务端调用即 `NoClassDefFoundError`。本次不动（改它等于改 API 语义），已在此备案 |
| `util/LaserColorUtil`（common 包，未标注解） | `ClientAttachmentIndex`、`GunDisplayInstance`、`LaserConfig` | 仅 `client/gui/...HSVSliderGroup`、`client/model/functional/BeamRenderer` | ⚠️ 同上；建议后续挪进 `client` 包，本次不动 |
| `util/RenderHelper`、`util/InputExtraCheck`、`util/RenderDistance`、`util/DelayedTask` | `net.minecraft.client.*` | 仅客户端 | ✅ 已在**类级** `@Environment(CLIENT)`，专服上根本不加载 |
| `command/sub/ReloadCommand#reloadAllPack` | `ClientAssetsManager`（在**独立方法** `reloadClient()` 里） | 命令在服务端执行 | ✅ 安全：先 `FabricLoader.getEnvironmentType() == CLIENT` 再调独立方法，client 类引用不在服务端会执行的方法体内 |
| `entity/EntityKineticBullet#tick` | `AmmoParticleSpawner` | 双端 tick | ✅ 安全：`if (level().isClientSide())` 守卫，常量池惰性解析；专服不会走到 |
| `capability/CombatProperties#playAttackSound` | `LrTacticalAPI#getMeleeDisplay`（标了 CLIENT） | 双端 | ✅ 安全：已显式 `if (!level().isClientSide()) return;`（该文件注释已正确记载剥离语义） |
| `resource/PackConvertor`、`resource/GunPackLoader` | `com.tacz.guns.client.resource.pojo.PackInfo` | **服务端也会执行**（枪包转换/加载） | ✅ 安全：`PackInfo` 是纯 gson POJO，字段只有 `String`/`List<String>`，不引用任何 `net.minecraft.client.*`；仅仅是**包名放错位置**。可选清理项，本次不动 |
| `network/message/Server*#handle(LocalPlayer, PacketSender)` 及其 `doClientEvent/updateScreen/...` | `LocalPlayer`、`ClientIndexManager` 等 | 仅 S2C 客户端处理 | ✅ 保留（均带 `@Environment`，且 S2C 注册本身在 `registerS2CPackets` 里） |
| `resource/modifier/custom/*#getPropertyDiagramsData`、`#getDiagramsDataSize`（含 `IAttachmentModifier` 默认实现） | 改装界面数据 | 仅改装 GUI | ✅ 保留 |
| `me/xjqsh/lrtactical/item/{ThrowableItem,MeleeItem,ConsumableItem}#getName` | — | 双端 | ✅ **已经是 common**：走 `LrTacticalAPI.getThrowableIndex/getMeleeIndex/getConsumableIndex` → `me.xjqsh.lrtactical.resource.CommonAssetsManager`，返回 `index.getDescriptionId()`。无需修改 |
| `me/xjqsh/lrtactical/api/LrTacticalAPI#getThrowableDisplay/getMeleeDisplay` | `LrClientAssetsManager`、`*DisplayInstance` | 仅客户端渲染 + 上面那处带守卫的音效 | ✅ 保留 |
| `me/xjqsh/lrtactical/init/ModCapabilities#onClientPlayerTick` | `LocalPlayer` | 仅 `TaCZFabricClient` 客户端 tick | ✅ 保留 |
| `cn/sh1rocu/tacz/api/event/*`、`compat/{jei,rei,cloth,iris,sodium,playeranimator,controllable,firstperson}` | 各类渲染/GUI | 仅客户端入口注册 | ✅ 保留 |

**结论**：client 包之外，覆写「双端公共方法」却读 client 数据的，只有这四处 `getName`；
LR 内置框架与 `cn.sh1rocu.*` 扩展里的同类物品方法（`ThrowableItem` / `MeleeItem` /
`ConsumableItem` 的 `getName`）本来就走 common 索引，无需跟改。
另有两处「安全但脆弱」（`IGun#getAimingZoom`、`LaserColorUtil`）已备案，未改动。

---

## 5. 未完成的验证（必须在有构建环境的机器上补）

本工作区**没有 JDK、没有 Gradle 缓存、也没有 26.2 依赖**，因此以下三项都没做，
不要在 changelog 里宣称做过：

1. `./gradlew build` —— 编译未跑通过。改动只做了静态核对：
   四个 common 索引的 `getPojo()`、四个 POJO 的 `getName()`、`TimelessAPI`
   的四个 `getCommonXxxIndex` 均已逐个确认存在；新增 import 只有
   `org.apache.commons.lang3.StringUtils`（`CommonGunIndex` 等已在用，
   classpath 必然有）；删掉的 import 已确认在文件内无其他引用。
2. **Fabric 专用服务器复现（修复前）**：起一个 26.2 dedicated + 本 mod（修复前版本），
   装任意枪包，用 `/give @p tacz:modern_kinetic_gun[...]`（26.2 的组件语法，
   **必须带上填充了真实枪 id 的 `minecraft:custom_data`**，写法见第 8 节）发一把枪，
   记录**服务端回执消息**里显示的是枪包名字还是 `item.tacz.modern_kinetic_gun`
   这类原版兜底名。按第 2 节推导，**预期是后者，并且不崩服**。
   ⚠️ 只写 `/give @p tacz:modern_kinetic_gun`（不带组件）测不出本修复——
   第 8 节说明了原因与正确命令。
3. **修复后复测**：同样步骤，预期回执显示枪包名字；同时验证
   （a）单人存档、（b）客户端连远程专服 两种环境下 GUI/tooltip 名字与修复前一致，
   （c）弹药 / 配件 / 制造台三类物品同样正确。

建议把结果写进 `records/SERVER_TEST_<日期>_DEDICATED.md`，与 NeoForge 侧记录对齐。

---

## 6. changelog 措辞（按验证结果二选一）

**A. 若尚未在 Fabric 专服实测**（当前状态，推荐用这条）：

> 修复枪械/子弹/配件/枪械制造台的 `Item#getName` 依赖客户端索引的问题。该方法是双端
> 公共方法，此前挂着 `@Environment(EnvType.CLIENT)`，会被 fabric-loader 在专用服务器上
> 整体剥离，导致 `/give` 回执、容器标题、铁砧改名等服务端路径显示原版兜底名而不是枪包
> 名字。现改为读取 common 索引（与客户端同一份 index json、同一个翻译键），客户端显示
> 不变。同源问题在 NeoForge 姊妹项目上表现为专服 `NoClassDefFoundError`；Fabric 侧的
> 表现差异源于两个加载器对环境注解的运行期处理不同。

**B. 若第 5 节第 2 步实测确认了崩溃**（只有拿到堆栈才可用）：

> …此前在专用服务器上执行 `/give` 会抛 `NoClassDefFoundError:
> com/tacz/guns/client/resource/index/ClientGunIndex`（见
> `records/SERVER_TEST_<日期>_DEDICATED.md`），现已修复。

**不要**写成「与 NeoForge 同款崩溃，已修复」——那是未验证的移植结论。

---

## 7. 给 `26.1.2` 与 `1.21.11` 分支的移植提示

* 配方完全一致；三条分支的 common 索引都已有 `getPojo()`，同样不需要补 getter
  （移植时仍请逐文件核一次，尤其 `1.21.11` 的 `CommonBlockIndex`）。
* 若某分支的 `Client*Index#getName` 兜底键不是 `custom.tacz.error.no_name`，
  以该分支实际字符串为准，保持逐字符等价。
* 第 4 节的审计表要在各分支重跑一次 grep：`compat/` 与 `me.xjqsh.lrtactical.*`
  的文件集在三条分支上并不相同。

---

## 8. 2026-08-22 用户专服实测结果的解读（重要）

用户在 26.2 专用服务器上实测（修复后版本）：

- `/give` 不崩服（符合第 2.1 节预期）；
- 但 `/give` 与 REI/JEI「作弊拿」得到的枪/弹药/配件全是**紫黑片** + `item.` 前缀原始键
  （如 `item.tacz.modern_kinetic_gun`、`item.tacz.attachment`）；
- 只有石像、Tacz 枪械工作台、标靶、标靶车、铁弹药盒正常。

**结论：这不是修好的 `getName` 失效，也不是「只删注解」的崩溃路径；这些现象全部来自
「裸物品」——即 ItemStack 上没有携带 id 的 `minecraft:custom_data`。该行为在修复前、
修复后、以及上游 1.20.1 上完全相同，属于 TaCZ 的既有语义。**

### 8.1 源码证据链（裸物品 → 回退名 + 无模型）

1. **id 取值**：`GunItemDataAccessor#getGunId`（~L117）与 `AmmoItemDataAccessor#getAmmoId`、
   `AttachmentItemDataAccessor#getAttachmentId`、`BlockItemDataAccessor#getBlockId` 都只从
   `ItemNbtUtils.getTag(stack)`（即 `minecraft:custom_data` 组件）里读 id；
   没有该组件时一律返回 `DefaultAssets.EMPTY_*_ID` = `tacz:empty`。
2. **名字**：`getName` 用 `tacz:empty` 去查 `Common*Index` 必然为空 → 落回
   `super.getName(stack)` → `item.tacz.modern_kinetic_gun` / `item.tacz.attachment`。
   这个 `item.*` 键在 mod 自带 lang 里不存在（默认枪包 lang 用的是
   `tacz.gun.<id>.name` / `tacz.ammo.<id>.name` / `tacz.attachment.<id>.name` 这类键），
   所以客户端/服务端都直接显示原始键。作为对照，「铁弹药盒」能显示是因为
   `item.tacz.ammo_box.iron` 写在 mod jar 的 `assets/tacz/lang/*.json` 里。
3. **模型**：`TaczDynamicItemModel` 把枪/弹/配件的 26.2 item model 挂到
   `tacz:dynamic_item`，真正内容由 `AnimateGeoItemRenderer#renderByItem` →
   `GunItemRendererWrapper#getModel` → `TimelessAPI.getGunDisplay(stack)` 决定；
   而 `getGunDisplay` 开头就是 `getCommonGunIndex(gunId).isEmpty() → return Optional.empty()`。
   `tacz:empty` 查不到 → 没有任何几何 → 物品栏/REI/JEI 里只剩 `items/*.json` 那个
   「粒子贴图 = barrier」的空占位模型 = 紫黑片。
4. **为什么只有那几个物品正常**：石像/标靶/标靶车/枪械工作台是普通
   `BlockItem`，模型来自原版方块模型、键来自 mod 自带 `block.tacz.*` lang；
   铁弹药盒是 `AmmoBoxItem`，名字是写死的 `item.tacz.ammo_box.*` 键——它们都不依赖
   枪包 index。剩下的枪/弹药/配件全部依赖 index，于是全军覆没。

### 8.2 关键认知修正：物品 id ≠ 枪 id

`tacz:modern_kinetic_gun` 是**物品类**的注册 id（`ModItems.MODERN_KINETIC_GUN`，
`ModernKineticGunItem` = 「现代动能枪械」这一类枪共用的容器物品），
**不是**默认枪包里某把枪的索引 id。默认枪包 `data/tacz/index/guns/` 下是
`tacz:ak47`、`tacz:glock_17`、`tacz:vector45` 等 54 个索引，**没有**
`modern_kinetic_gun`。因此即使组件里写 `GunId:"tacz:modern_kinetic_gun"` 也会回退。
（同理弹药为 `tacz:762x39` 等，配件为 `tacz:sight_sro_dot` 等。）

### 8.3 修复后正确的复测命令（26.2 组件语法）

```mcfunction
# 枪：物品 tacz:modern_kinetic_gun + GunId = tacz:ak47
/give @p tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"tacz:ak47"}]

# 弹药：物品 tacz:ammo + AmmoId = tacz:762x39
/give @p tacz:ammo[minecraft:custom_data={AmmoId:"tacz:762x39"}]

# 配件：物品 tacz:attachment + AttachmentId = tacz:sight_sro_dot
/give @p tacz:attachment[minecraft:custom_data={AttachmentId:"tacz:sight_sro_dot"}]

# 工作台：物品 tacz:workbench_a + BlockId = tacz:gun_smith_table
/give @p tacz:workbench_a[minecraft:custom_data={BlockId:"tacz:gun_smith_table"}]
```

预期：
- 服务端回执不再是 `item.tacz.modern_kinetic_gun`，而是 `tacz.gun.ak47.name`
  （若服务端语言表里没有枪包 lang，日志显示的是这个键本身——**这仍算通过**，
  因为键已经换成枪包翻译键；客户端聊天/GUI 一定显示中文/英文枪名）。
- 客户端拿到后名字与模型都正常：
  `tacz.gun.ak47.name` → zh_cn「AKM 突击步枪」，模型来自 `display/guns/ak47_display`。
- 若用了正确组件后**仍然**是 `item.tacz.*`，才说明服务端 common 索引为空
  （枪包没在服务端加载），需要继续查 `latest.log` 里
  `GunPackFinder: Start scanning for gun packs in <server>/tacz`、
  `Found N possible gunpack(s)`、`- tacz_default_gun, Main namespace: tacz` 等行，
  以及服务端 `.minecraft/tacz/` 目录内容。

### 8.4 REI/JEI 紫黑片说明（上游一致的既有行为 + 可用的替代入口）

REI/JEI 的物品列表条目是**注册表里的裸 ItemStack**（物品查看器只枚举注册的 Item，
不会给 TACZ 补 id 组件）；上游 1.20.1 的 `GunModPlugin` 也只注册了 subtype 解释器
与配方分类，**同样不注册带 NBT 的变体条目**。因此裸条目紫黑 + `item.*` 键不是本次
修复引入，也不是 Fabric 26.2 独有的移植缺陷。

获取「正常物品」的现有路径：
- TACZ 自己的创造标签页（`ModCreativeTabs` 的 `GUN_PISTOL_TAB` 等，
  `fillItemCategory` 用 `GunItemBuilder.setId(...)` 构造，带完整 id）；
- JEI/REI 里 TACZ 自带的 **Ammo Query / Attachment Query / 枪械工作台配方**分类：
  条目由 `AmmoItemBuilder` / `GunItemBuilder` / `AttachmentItemBuilder` 构造，
  带 id、可作弊拿取。

若需要「REI/JEI 物品列表直接列出全部带 id 的枪械/弹药/配件变体」，是独立的增强项
（JEI/REI 各需条目注册或 ItemStackProvider，改动与本次 `getName` 修复无关），
建议单独立项，不混进本轮提交。

---

## 9. 2026-08-22 追根：REI/JEI「列表正常、拿取紫黑、单机/局域网正常」的完整解释

用户补充：在专服上用**正确 `/give` 组件语法**发物品一切正常；但 REI/JEI 里**列表显示正常**
（有名字有模型），**作弊拿取后**才变紫黑 + `item.*` 原始键；单人游戏/局域网联机没有此问题。

### 9.1 这说明什么（一句话）

> **条目数据在客户端是完整的（所以列表渲染正常），坏的是「客户端 → 专服」的给物通路：
> 当专用服务器上没有安装 REI 时，REI 的作弊给物退回「客户端拼装 `/give` 命令」的兜底，
> 而这条兜底把物品的 NBT/数据组件**硬编码为空**，于是服务端只拿到裸物品。**

### 9.2 REI 源码实锤（对应仓库 CHANGELOG 核过的 26.2.820 / 源码 commit 2be20928）

`runtime/src/main/java/me/shedaniel/rei/impl/client/ClientHelperImpl.java#tryCheatingEntry`
共三条路径，按环境选择：

1. **创造界面 +「抓取」模式**：`menu.setCarried(copy.getValue().copy())` —— 纯客户端，
   单机/局域网/专服行为一致；
2. **`ClientHelperImpl.getInstance().canUsePackets()` 为真**：发送
   `REIPackets.CreateItems` / `CreateItemsGrab`（`ItemStack.OPTIONAL_STREAM_CODEC`，
   **组件完整**）。`canUsePackets()` 定义为
   `NetworkManager.canServerReceive(CREATE_ITEMS_PACKET) && ...`——**只有服务器端也装了 REI
   才为真**。单机/局域网时集成服务器与客户端同 JVM，REI 必然在，所以走这条路 → 正常；
3. **服务器没装 REI（兜底）**：
   ```java
   String tagMessage = /* TODO 24w09a: cheatedStack.copy().getTag() ... */ "";
   String madeUpCommand = og.replaceAll("{item_identifier}", identifier.toString())
                             .replaceAll("{nbt}", tagMessage) ...;
   Minecraft.getInstance().player.connection.sendCommand(...);
   ```
   默认模板（`ConfigObjectImpl`：`public String giveCommand =
   "/give {player_name} {item_identifier}{nbt} {count}"`）最终被替换成
   `give <玩家> tacz:modern_kinetic_gun 1`——`{item_identifier}` 是 **Item 的注册表 id**
   （`ItemEntryDefinition#getIdentifier` = `BuiltInRegistries.ITEM.getKey(...)`），
   `{nbt}` **恒为空**（源码注释 `TODO 24w09a` 明示：1.20.5 组件化后 `getTag()` 不再拼入）。

REI 自己的语言文件也写明该配置项的用途：
`config.rei.options.cheats.give_command.desc` =
「当服务器上未安装 REI 时，用于作弊物品的指令」。

### 9.3 为什么这精确复现了用户的每个观测

| 观测 | 解释 |
| --- | --- |
| REI/JEI 列表里名字/模型正常 | 列表/分类条目是客户端 `AmmoItemBuilder` / `GunItemBuilder` 等构造的**完整物品**（带 `minecraft:custom_data`），客户端渲染数据齐全 |
| 拿取后变紫黑、`item.*` 键 | 兜底命令给的是裸物品：`getGunId/...` 读到 `tacz:empty` → `getName` 落回 `super.getName` → `item.tacz.modern_kinetic_gun`（无 lang 键 → 原始键）；`getGunDisplay` 空 → 无模型 → 紫黑 |
| 只有铁弹药盒「正常」 | 裸 `tacz:ammo_box` 的 `AmmoBoxItem#getName` 走 `getAmmoLevel`=0 → `item.tacz.ammo_box.iron`（**mod jar 自带该键**，服务端可翻译成 "Iron Ammo Box"）—— 它恰恰证明这条路径给的就是裸物品 |
| 石像/工作台/标靶/标靶车正常 | 普通 `BlockItem`，`block.tacz.*` 键在 mod jar，模型不依赖枪包索引 |
| 单人/局域网正常 | REI 与集成服务器同 JVM → `canUsePackets()`=true → `CreateItems`（OPTIONAL_STREAM_CODEC）→ 组件完整 |
| 正确 `/give` 组件语法正常 | 服务端 common 索引、修复后的 `getName`、客户端索引全部工作——因此 **TACZ 侧没有任何缺陷；坏在 REI 的兜底命令无法表达组件** |

### 9.4 零代码修复与验证

1. **在专用服务器上也装与客户端同版本的 REI**（或 JEI）：
   - REI：`canUsePackets()` 变真 → 走 `CreateItems` 包，组件完整；
   - JEI 同理：`serverConnection.isJeiOnServer()` 为真 → `PacketGiveItemStack`
     （`ItemStack.STREAM_CODEC`，组件完整）。JEI 没装在服务端时走的是原版
     创造背包动作包（`ServerboundSetCreativeModeSlotPacket`，1.20.5+ 也携带组件），
     理论不受影响；若用户确认 JEI 单独也复现，需另查 26.2 的该包，但 REI 这条已实锤。
2. 验证步骤：专服装 REI 后，从 REI 的「弹药查询 / 配件查询 / 枪械工作台」分类或
   TACZ 创造标签页拿一把枪 → 预期名字与模型正常、服务端无 `item.tacz.*` 回退名。
3. 不想装服务端 REI 的替代用法：直接用 **TACZ 自己的创造标签页**（`ModCreativeTabs`），
   或把 REI 配置里的「give 命令」交给支持 `{nbt}` 的外部实现（REI 默认 `{nbt}` 恒空，无解）。
4. 上游跟进：给 REI 提 issue——`ClientHelperImpl#tryCheatingEntry` 的
   `tagMessage = /* TODO 24w09a: ... */ ""` 从未适配 1.20.5+ 数据组件，
   兜底 `/give` 命令丢掉了 `minecraft:custom_data`，且超 256 字符时二次清空。

### 9.5 与本次 `getName` 修复的关系（结论）

`getName` 修复解决的是「**同一条 /give 命令或同一个完整物品**在服务端是否显示枪包名」；
本节的 REI 兜底解决不了——它把物品本身变成了裸物品，属于查看器给物协议的缺陷。
两者不冲突、不重复：修复仍然必须保留（否则带上组件的物品在专服 `/give` 回执、容器标题、
铁砧改名等路径仍然会显示原版兜底名），而「REI/JEI 拿取紫黑」需要 9.4 的方案解决。

> 本节已同步至玩家可读的发布文案：`docs/publish/Modrinth.md`、`docs/publish/CurseForge.md`
> （英文 FAQ 章节）、`docs/publish/MCMOD.md`（正文第八节）与
> `docs/CHANGELOG_26_2_R2.md`（文末 FAQ），用于减少发布后重复答疑。
