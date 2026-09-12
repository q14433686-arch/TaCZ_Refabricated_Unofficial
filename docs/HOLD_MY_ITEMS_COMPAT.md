# Hold My Items 5.1 兼容：Lua 安全层把 TaCZ 脚本 API 全部打成 nil

**结论先说**：这次崩溃既不是 TaCZ 的 bug，也不是 luaj 版本混用的 bug。Hold My Items（下文 HMI）5.x
给 luaj 的 `JavaMethod#invokeMethod` 挂了一个**全局** mixin，把"没有 HMI 自己 `@Safe` 注解"的 Java 方法
返回值一律改写成 `LuaValue.NIL`。Fabric Knot 全 JVM 只加载一份 `JavaMethod`，所以 TaCZ 的每一个
`context:xxx()` / `api:xxx()` 都拿到 `nil`；手持枪械时第一行做数值比较的脚本立刻炸：

```
org.luaj.vm2.LuaError: tacz_default_state_machine:74 bad argument: attempt to compare __le on nil and number
```

本次改动在同一个方法上加一个 `HEAD` 注入，**只对 TaCZ/LRTactical 自己的脚本 API** 直接反射调用并 cancel，
抢在 HMI 的 `RETURN` 改写之前把真实结果交回脚本。HMI 的沙箱对其余一切（包括 HMI 自己的脚本、Minecraft
本体对象）保持原样。

> **状态（2026-09-12 更新）**：维护者已在实机验证通过 —— 与 HMI 5.1 同装时手持枪械不再崩溃，
> 编译与启动均正常。也就是第 4 节矩阵的第 1-4 项。
> 矩阵其余各项（逻辑脚本逐项、专用服务器、**不装 HMI 的回归**、HMI 自身脚本表现、与其他兼容层同装）
> 尚未逐条回报，仍按未验证对待。本次改动是在无 JDK 的环境里写完的：编译验证来自 CI
> （`build-reports/compile-java.log`，success，新增 mixin 无 AP 警告），运行验证来自维护者实机。

---

## 1. 核验基线

### 1.1 崩溃现场（`RawOutput.log`）

环境：Minecraft 1.21.11 / Loader 0.19.3 / `tacz 1.1.8+fabric.1.21.11.R3-hotfix` /
**`holdmyitems: Hold My Items 5.1`**（日志 437 行）/ Iris 1.10.7 / tacztweaks / geckolib。

日志 218-251 行（渲染线程）：

```
org.luaj.vm2.LuaError: tacz_default_state_machine:74 bad argument: attempt to compare __le on nil and number
  at org.luaj.vm2.LuaValue.error(LuaValue.java:1209)
  at org.luaj.vm2.LuaValue.comparemt(LuaValue.java:3437)
  at org.luaj.vm2.LuaValue.lteq_b(LuaValue.java:3179)
  at org.luaj.vm2.LuaClosure.execute(LuaClosure.java:402)
  ...
  at com.tacz.guns.api.client.animation.statemachine.LuaAnimationState.entryAction(LuaAnimationState.java:42)
  at com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine.initialize(AnimationStateMachine.java:138)
  at com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer.tryInit(AnimateGeoItemRenderer.java:150)
  at com.tacz.guns.client.renderer.item.GunItemRendererWrapper.tryInit(GunItemRendererWrapper.java:118)
  at net.minecraft.class_759.wrapOperation$cdc000$tacz$submitArmWithAnimatedItem(class_759.java:3427)
  at net.minecraft.class_759.method_22976(class_759.java:414)
  at net.minecraft.class_759.iris$renderHandsWithCustomRenderer(class_759.java:2313)
  at net.irisshaders.iris.pathways.HandRenderer.renderSolid(HandRenderer.java:116)
```

出事的那一行脚本（`assets/tacz/scripts/default_state_machine.lua:74`）：

```lua
return (not context:hasBulletInBarrel()) and (context:getAmmoCount() <= 0)
```

`hasBulletInBarrel()` 返回 `nil` → `not nil == true` → 继续算右边；`getAmmoCount()` 也返回 `nil`
→ `nil <= 0` → `lteq_b` → `comparemt` → `error`。**两个方法都被打成了 nil**，这就是"每个 Java 方法
调用都被改写"的特征，而不是某一个方法签名对不上。

### 1.2 加载的是哪一份 luaj

HMI 自己 nest 了 luaj-jse 3.0.1，TaCZ `include` 的是 `com.github.FiguraMC.luaj:3.0.8-figura`。Knot
只会有一份 `org.luaj.vm2.*` 生效，行号可以直接指纹比对：

| 崩溃栈 | Figura fork 3.0.8 | luaj 3.0.1 |
|---|---|---|
| `LuaValue.error(LuaValue.java:1209)` | 1209 ✅ | 1041 ❌ |
| `LuaValue.comparemt(LuaValue.java:3437)` | 3431-34xx ✅ | 2984 ❌ |
| `LuaValue.lteq_b(LuaValue.java:3179)` | 3179 ✅ | 2733 ❌ |
| `LuaClosure.execute(LuaClosure.java:402)` | 402 = `OP_LE` 的 `lteq_b` 调用行 ✅ | ❌ |

即：**生效的是 TaCZ 打包的 Figura fork**，HMI 的 mixin 打在了这份副本上（mixin 认类名，不认来源 jar）。
本次新增代码调用的 luaj API（`CoerceJavaToLua.coerce(Object)`、`CoerceLuaToJava.coerce(LuaValue, Class)`、
`Varargs.narg/arg`、`LuaError(Throwable)`、`LuaError(String)`）在两份 luaj 里签名一致，已逐个核对。

### 1.3 HMI 的 mixin 原文

HMI 没有官方源码仓库（作者 sapling，CC0）。下面这段取自对官方 1.21.11 jar（5.1.1）的反编译移植
`ThinkofRain1213/HMI-26.2`，路径 `mod/src/main/java/com/holdmylua/source/mixin/safety/JavaMethodMixin.java`：

```java
@Mixin(targets = {"org/luaj/vm2/lib/jse/JavaMethod"}, remap = false)
public class JavaMethodMixin {
   @Shadow @Final private Method method;
   @Shadow @Final private static Map methods;

   @Inject(method = {"invokeMethod"}, at = {@At("RETURN")}, cancellable = true)
   private void isSafe(Object par1, Varargs par2, CallbackInfoReturnable<LuaValue> cir) {
      if (!this.method.isAnnotationPresent(Safe.class)
            && !this.method.getName().equals("getOrDefault")
            && !this.method.getName().equals("put")) {
         cir.setReturnValue(LuaValue.NIL);
      }
   }

   static { System.out.println("HMI Lua safety layer loaded!"); }
}
```

三点需要说明：

1. `com.holdmylua.source.annotation.Safe` 是 `@Retention(RUNTIME)` 的自定义标记注解，**只有 HMI 自己的
   API 类会带它**。TaCZ 不可能带，任何其它用 luaj 的 mod 也不可能带。
2. 反编译代码里没有显式 `cir.cancel()`：Mixin 的 `CallbackInfoReturnable#setReturnValue` 自身就会执行
   取消（不可取消的注入点会抛 `CancellationException`），所以 `setReturnValue(NIL)` 足以替换返回值。
   1.1 节的崩溃日志就是这条语义的经验证据。
3. `holdmyitems.mixins.json` 把这个 mixin 放在**通用 `mixins` 列表**（不是 `client`），且 HMI 的
   `fabric.mod.json` 是 `environment: "*"` —— 也就是说**逻辑侧同样受影响**：`api:shootOnce()`、
   `api:getReloadTime()`、`api:getCachedScriptData()` 这些枪械逻辑脚本调用在 integrated server /
   专用服务器上一样会被打成 nil。默认枪包一共 42 个 `.lua`（`*_state_machine.lua` + `*_gun_logic.lua`），
   脚本里出现的调用全部是 `context:` 与 `api:` 两类（已用 grep 全量统计）。

### 1.4 渲染侧其实不冲突

HMI 另有 `mixin/render/HeldItemRendererMixin`，它 `@Redirect` 掉
`ItemInHandRenderer` 里对**单只手提交**方法的那次调用（26.2 反编译里叫 `submitArmWithItem`，
1.21.11 对应 `renderArmWithItem`）——正是 TaCZ `@WrapOperation` 的同一个调用点
（`com.tacz.guns.mixin.client.ItemInHandRendererMixin#tacz$submitArmWithAnimatedItem`）。

结论是**不需要再加一个"让 HMI 让位"的渲染 mixin**，依据有两条：

- 崩溃栈里 `wrapOperation$cdc000$tacz$submitArmWithAnimatedItem` 由 `method_22976` 直接调用，说明
  TaCZ 的 wrapper 就是该调用点最外层的替换者（MixinExtras 的 `@WrapOperation` 与原版 `@Redirect`
  在同点共存时，wrapper 在外、`Operation#call` 才走到 redirect 处理函数）。
- TaCZ 的 wrapper 只在"不是 TaCZ 动画视图模型"时才 `original.call(...)`；是 TaCZ 枪械时自己
  `renderFirstPerson` 并且**不调用** `original` → HMI 的 `renderOverhaul` 对 TaCZ 枪械不生效；
  非 TaCZ 物品照旧交给 HMI。这与仓库既有的单向兼容契约（`FirstPersonAnimationCompat`）一致。

第三方同类问题可参考 `StewyDev65/HoldMyGuns`（HMI × Vic's Point Blank 补丁），它用的正是
"shadow 私有提交方法 + WrapOperation 在点名命名空间时直接调用"的让位写法；TaCZ 因为已经自己
接管了这次调用，所以不需要照抄。

---

## 2. 本次修复

### 2.1 三个部件

| 文件 | 作用 |
|---|---|
| `src/main/java/cn/sh1rocu/tacz/mixin/compat/holdmyitems/HoldMyItemsJavaMethodMixin.java` | 在 `JavaMethod#invokeMethod` 的 `HEAD` 上接管 TaCZ 自己的脚本 API 调用 |
| `src/main/java/com/tacz/guns/compat/holdmyitems/LuaBridgeGuard.java` | 判定门 + 运行时探针 + 与 luaj 语义一致的反射调用 |
| `src/main/resources/tacz.fabric.mixins.json` | 注册到**通用** `mixins` 列表（逻辑侧同样需要），并补充 `_comment` |

`cn.sh1rocu.tacz.util.MixinPlugin` 的既有规则会把 `cn.sh1rocu.tacz.mixin.compat.<modid>.*` 按
`<modid>` 过滤，所以这个 mixin **只在 `holdmyitems` 存在时才应用**，与 Punchy / Controllable 的做法一致。

### 2.2 判定门（gate）

只有两种形态会被接管：

1. 方法的**声明者**属于 `com.tacz.` / `me.xjqsh.lrtactical.` / `cn.sh1rocu.tacz.`；
2. 方法调用的**实例**属于上述包（覆盖 `toString()` 这类从 `Object` 或非 TaCZ 父类继承来的成员）。

按类做 `ConcurrentHashMap` 缓存，非 TaCZ 调用只多一次 map 查询。

**刻意不覆盖**的情形：脚本拿到 Minecraft 对象再链式调用，例如 `api:getItemStack():getCount()`、
`api:getShooter():getX()`。这些方法的声明者与实例都不是 TaCZ 类，接管它们等于把 HMI 的沙箱对所有人
关掉，属于越界。默认枪包 42 个脚本里没有这种写法（已全量 grep），但第三方包如果有，仍会被 HMI 打成
nil —— 见第 3 节。

### 2.3 运行时探针（probe）

判定门通过后才会做一次**一次性**探针：把一个 `java.util.concurrent.atomic.AtomicInteger` 交给
`CoerceJavaToLua.coerce`，取出 `get` 方法并 `call`，看回来的还是不是原值。

- 选 `AtomicInteger#get` 是因为它是纯 JDK 方法，任何 mod 都不可能给它加 `@Safe`，因此必然走 HMI
  那条改写路径；同时它不属于 TaCZ 包，**不会被本次的判定门自己放过**（否则探针会永远显示"健康"）。
- 顺序是"先判定门、后探针"，探针自身那次 `invokeMethod` 不会触发接管，也不会递归。
- 探针抛任何异常都**失败即放行**（fail-open）：无法证明桥被改写时，绝不改动 luaj 的分发行为。
- 探针结果缓存在 `volatile` 字段里；检测到改写时打一条 WARN，指向本文档。

这样即使将来 HMI 删掉这个安全层、或者别的 mod 做了同样的事，行为都自动正确：没有改写就不接管。

### 2.4 反射调用与 luaj 的语义对齐

`LuaBridgeGuard#invokeDirectly` 逐行对齐 `JavaMethod#invokeMethod`：

```java
// luaj 原文
Object[] a = convertArgs(args);
try { return CoerceJavaToLua.coerce(method.invoke(instance, a)); }
catch (InvocationTargetException e) { throw new LuaError(e.getTargetException()); }
catch (Exception e) { return LuaValue.error("coercion error " + e); }
```

- `convertArgs` 是 `JavaMember` 的 `protected` 成员。这里没有再 shadow 一个方法，而是按同样算法用
  luaj 的**公开**入口重算：`CoerceLuaToJava.coerce(LuaValue, Class)` 的实现就是
  `getCoercion(clazz).coerce(value)`，与 `convertArgs` 每个参数做的事完全相同（含 varargs 分支）。
  于是 mixin 只需要 shadow 一个字段（`@Shadow @Final private Method method;`，HMI 自己也是这么 shadow 的）。
- `LuaValue.error(String)` 在两份 luaj 里都声明为**返回 `LuaValue`**（方法体直接 `throw`），所以这里
  不能写 `throw LuaValue.error(...)`（编译不过），改成 `throw new LuaError("coercion error " + e)`，
  运行期等价。
- `InvocationTargetException` 仍然包成 `LuaError(cause)`，保证枪包脚本自己的报错行号不被吞掉。
- TaCZ 的 Lua API 目前没有可变参数方法（已 grep 确认），varargs 分支只为第三方包保持与原版一致。

### 2.5 被否掉的方案

| 方案 | 为什么不用 |
|---|---|
| 给 TaCZ 所有 Lua 暴露方法加 HMI 的 `@Safe`（自带一份同名注解类） | 需要动 ~160+ 个方法（`AnimationStateContext` 24、`GunAnimationStateContext` 36、`ModernKineticGunScriptAPI` 70、`LuaNbtAccessor` 21、lrtactical 若干），还要往 TaCZ 的 jar 里塞一个别人包名的注解类；每加一个新 API 都要记得补注解，漏一个就是又一次崩溃。属于用别人的私有逃生口做长期维护负担 |
| 反向 mixin 里再 shadow `convertArgs` | 多一个跨包 `protected` 方法 shadow，收益为零（公开 API 已能等价重算） |
| 把 TaCZ 的脚本对象包成 `LuaTable` 代理 | 改变脚本可见语义（`type()`、metatable、`tostring`），且无法在本地验证 |
| shadow + relocate 自带一份 luaj | TaCZ 有 22 处公开 API 签名直接暴露 `LuaValue`/`LuaTable`，重定位等于破坏公共 API |
| 在 Lua 调用点吞掉 `LuaError` | 那是"绕过"不是"修复"，还会掩盖真实枪包错误 |

---

## 3. 明确的边界（不承诺的部分）

1. **验证覆盖不完整**。2026-09-12 维护者实机通过的是核心项（编译、启动、手持枪械不再崩溃、
   空仓那条判断），即第 4 节矩阵的 1-4 项；5-10 项（逻辑脚本逐项、专用服务器、不装 HMI 的回归、
   HMI 自身脚本、其他兼容层同装）**尚未逐条确认**，其中第 8 项是回归底线，发版前应当补跑。
2. **脚本链式调用 Minecraft 对象仍然会被 HMI 打成 nil**（`api:getItemStack():getCount()` 一类）。
   这是刻意的：TaCZ 不替 HMI 决定它的沙箱该多宽。枪包作者应改用 TaCZ 自己的门面
   （`LuaNbtAccessor` / `LuaEntityAccessor`）；玩家侧的彻底解法是不装 HMI。
3. **HMI 侧才是根治点**。它的安全层对"全 JVM 唯一一份 luaj"生效，任何其它使用 luaj JSE 桥的 mod
   都会被同样打死。建议按第 5 节向上游反馈（把判定限定到 HMI 自己声明的类，或检查调用来源）。
4. TaCZ 只保证**自己的**脚本 API 不受影响；HMI 自身的物品脚本、以及 HMI 与别的 mod 的组合，
   不在本次改动的射程内。
5. 渲染侧的"不冲突"结论来自崩溃栈与两边源码的对照（1.4 节），**不是**一次实机目视验证；
   若实测出现枪械不显示或双重绘制，按 `HoldMyGuns` 的让位写法补一个 mixin。

---

## 4. 验证矩阵

环境：1.21.11 + TaCZ 本次改动 + `holdmyitems 5.1` + Iris（复现原崩溃的组合）。

**2026-09-12 进度**：

- 第 1 项由 CI 编译覆盖：`build-reports/compile-java.log`（commit `3164deb`，`job status: success`）。
  8 条 warning 全是既有的 Iris / vanilla mixin 提示，**新增的 luaj mixin 没有触发任何 Mixin AP 警告**
  —— `targets` 指向包级私有库类、`@Shadow @Final private Method method`、
  `@Inject(method = "invokeMethod")` 三处都被 AP 接受。
- 第 2-4 项由维护者实机通过。
- 第 5-10 项待逐条确认。

| # | 操作 | 期望 |
|---|---|---|
| 1 | `./gradlew build` | 通过；`docs/verify_mixin_targets.py` 不应因新增 mixin 报错（目标是库类，不在原版类清单内） |
| 2 | 启动游戏 | 日志出现一次 `HMI Lua safety layer loaded!`（HMI 自己打的）与 TaCZ 的 WARN（检测到桥被改写） |
| 3 | 手持任意默认包枪械（第一人称） | 不再崩溃；`default_state_machine` 正常推进，抽枪/待机/换弹动画正常 |
| 4 | 空仓持枪（触发 74 行那条判断） | 进入空仓待机状态，无 `attempt to compare __le on nil and number` |
| 5 | 开火 / 换弹 / 切枪 / 拉栓（`kar98`、`db_short`、`m1014`） | 逻辑脚本生效：弹药消耗、`api:shootOnce`、`api:getReloadTime` 正常 |
| 6 | 连射（`devotion_lmg_logic.lua` 的加速逻辑） | 射速随连射次数变化，说明 `api:getCachedScriptData` / `cacheScriptData` 往返正常 |
| 7 | 专用服务器（或局域网开服）装 HMI + TaCZ | 逻辑侧脚本同样生效（HMI 的 mixin 在通用列表，`environment: "*"`） |
| 8 | **不装 HMI** 再跑 3-6 | 行为与改动前完全一致；日志中**不应**出现那条 WARN（探针判定为健康 → 完全不接管） |
| 9 | 装 HMI，手里拿非 TaCZ 物品（含 HMI 自己配了脚本的物品） | HMI 的第一人称与脚本表现不变，说明沙箱没有被 TaCZ 放宽 |
| 10 | 装 HMI + Punchy / First-person Model / Iris 各一次 | 既有兼容层不受影响 |

---

## 5. 建议向上游反馈的内容

> **已成稿**：可直接投递的英文正文、投递入口（`thesapsapling/hmi-docs`，作者名下唯一公开仓库，
> Issues 已开启）、措辞红线与四个修复选项见
> [`HMI_UPSTREAM_REPORT_20260912.md`](HMI_UPSTREAM_REPORT_20260912.md)。下面是要点备忘。

给 HMI 作者（或在其发布页留言）时，可以直接引用以下事实：

- `safety.JavaMethodMixin` 的目标是 `org/luaj/vm2/lib/jse/JavaMethod`，这是**整个 JVM 唯一一份**的类；
  Knot 不区分它来自哪个 mod 的 jar。
- 判定条件是 `method.isAnnotationPresent(com.holdmylua.source.annotation.Safe)`，而 `@Safe` 是 HMI
  私有注解，第三方 mod 的 API 不可能带它 → 所有其它使用 luaj JSE 桥的 mod（TaCZ、以及任何用
  `CoerceJavaToLua` 的 mod）的 Lua→Java 调用会**静默返回 nil**。
- 静默返回 nil 比抛异常更难排查：调用方看到的是 `attempt to compare/index nil`，栈里完全没有 HMI 的影子。
- 可行的收敛方式：把白名单判定改成"声明者属于 HMI 自己的包"或"实例属于 HMI 自己的类"；
  或者在 `JavaClass`/`CoerceJavaToLua` 层面只接管 HMI 自己 coerce 出去的对象；
  或者至少提供一条可关闭该层的配置项，并在改写时打一次日志。

按仓库约定：玩家侧的问题请提到本仓库 Issues，不要直接去上游刷屏。

---

## 6. 跨分支移植（26.1.2 / 26.2 main）

本次改动的两个 Java 文件**不含任何 Minecraft 名字**：mixin 目标是库类
`org.luaj.vm2.lib.jse.JavaMethod`，判定门与探针只用 luaj 与 JDK 的公开 API。因此可以原样复制到
26.1.2 与 26.2(main) 分支，需要注意的只有三点：

1. mixin 注册条目要落到各分支自己的通用 `mixins` 列表里（本分支是 `tacz.fabric.mixins.json`），
   并且包名保持 `cn.sh1rocu.tacz.mixin.compat.holdmyitems`，`MixinPlugin` 的 modid 过滤才继续生效。
2. 两个分支不是混淆环境，`remap = false` 与 intermediary 名字的问题都不存在；`docs/verify_mixin_targets.py`
   只校验 `net.minecraft` / `com.mojang` 目标，会跳过这个 mixin（不需要为它改脚本）。
3. 第 1.4 节的渲染侧结论里，1.21.11 的 `renderArmWithItem` / `renderHandsWithItems` 在 26.2 叫
   `submitArmWithItem` / `submitHandsWithItems`；移植时按各分支自己的 `ItemInHandRendererMixin`
   注入点核对一遍"TaCZ 自己渲染时不调用 `original`"这个前提即可。

### 6.1 值不值得现在就移植：**不建议，按需再做**

已在 `26.2(main)` 上核对过前提（本 clone 里只有 `26.2(main)` 与本分支，26.1.2 无法直接核对）：
luaj 依赖同为 `com.github.FiguraMC.luaj:3.0.8-figura`（`include`，build.gradle 162/165 行）、
mixin 配置同名、`MixinPlugin` 的 modid 规则一致、Lua 暴露类齐全、
`ItemInHandRendererMixin` 的接管点是 `submitHandsWithItems` → `submitArmWithItem`
（正是 HMI 26.2 反编译移植里被 `@Redirect` 的那个调用点）。也就是说**技术上可原样复制**。

但不建议预先移植，理由是它并不是通用的"加固"：

- 探针判定为健康时，这套代码**什么都不做**。没有 HMI（或同类全局沙箱）就没有任何健壮性、
  诊断或性能收益，最多是启动日志里安静一行。
- 代价却是实打实的：多一个打进库类的 mixin（不可静态验证的运行期面），外加每条分支都要重跑一遍
  第 4 节矩阵。按 `AGENTS.md` §2/§6 的口径，这属于"没有证据支撑就先背上的负担"。
- 26.x 上 HMI 目前只有第三方反编译移植（`ThinkofRain1213/HMI-26.2` 保留 modid `holdmyitems`
  与同一段 `safety.JavaMethodMixin`；曾出现过 `ByteMe6/HMI-26.1.2` 仓库，现已 404 无法核对内容），
  官方是否出 26.x 版本未知。

**触发条件（满足任一条就移植，约 15 分钟）**：

1. 26.x 玩家日志出现同一签名：TaCZ 的 lua 脚本报 `attempt to compare/index ... nil`、
   stdout 有 `HMI Lua safety layer loaded!`、模组列表含 `holdmyitems`；
2. 26.x 的 HMI 移植版开始被普遍使用。

一个需要注意的坑：`MixinPlugin` 用包名第 6 段当 modid 过滤，所以**如果 26.x 的移植版换了 modid**，
要么再开一个 `compat.<新modid>` 包放同一份 mixin，要么把这条规则改成显式白名单。

---

## 7. 相关文件

- `src/main/java/com/tacz/guns/compat/holdmyitems/LuaBridgeGuard.java`
- `src/main/java/cn/sh1rocu/tacz/mixin/compat/holdmyitems/HoldMyItemsJavaMethodMixin.java`
- `src/main/resources/tacz.fabric.mixins.json`
- `docs/HMI_UPSTREAM_REPORT_20260912.md`（可直接投递的上游报告草稿）
- 参考：`docs/CARRYON_COMPAT.md`（同类兼容文档）、`src/main/java/com/tacz/guns/compat/firstperson/FirstPersonAnimationCompat.java`（单向让位契约）、
  `src/main/java/com/tacz/guns/mixin/client/ItemInHandRendererMixin.java`（第一人称接管点）
