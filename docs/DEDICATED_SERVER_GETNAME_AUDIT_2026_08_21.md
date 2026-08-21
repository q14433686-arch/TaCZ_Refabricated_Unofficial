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
   带上枪械 id 数据）发一把枪，记录**服务端回执消息**里显示的是枪包名字还是
   `item.tacz.modern_kinetic_gun` 这类原版兜底名。
   按第 2 节推导，**预期是后者，并且不崩服**。
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
