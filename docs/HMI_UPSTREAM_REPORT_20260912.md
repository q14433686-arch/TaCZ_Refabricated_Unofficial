# 给 Hold My Items 作者的上游报告草稿（2026-09-12）

本文件是**投递用的草稿**，不是本仓库的技术文档。技术定位与证据在
[`HOLD_MY_ITEMS_COMPAT.md`](HOLD_MY_ITEMS_COMPAT.md)。

## 投递方式

- 目标仓库：`thesapsapling/hmi-docs`（作者 sapling 名下唯一公开仓库，README 目前只有占位文字，
  **Issues 已开启**）——这是我们能找到的唯一公开直达作者的入口。HMI 本体没有官方源码仓库。
- 若 HMI 的 Modrinth / CurseForge 项目页有 issue 区，同一份正文可直接复用；两处都发不算刷屏，
  但**不要在评论区和玩家线程里重复贴**，玩家侧一律引导回本仓库 Issues（`AGENTS.md` §5）。
- 正文用英文（作者与移植者都用英文）。下面的「English body」可整段复制。
- 附件：`RawOutput.log` 的 218-251 行栈、`HOLD_MY_ITEMS_COMPAT.md` 第 1.2 节的行号指纹表。
  不要把整份玩家日志贴进 issue（含模组列表，太长），给片段 + 说明即可。

## 措辞红线（发出去之前自查）

1. 我们引用的 mixin 原文来自**第三方反编译移植**（`ThinkofRain1213/HMI-26.2`，对官方 1.21.11 jar
   5.1.1 的反编译），不是官方源码。必须写明这一点，并请作者确认 1.21.11 正式版是否一致。
2. 不指控、不定性为"bug"以外的东西：这是**设计范围问题**（沙箱挂在了全 JVM 共享的库类上），
   作者的本意（限制自己脚本能调什么）是合理的。
3. 不承诺我们没有的东西：TaCZ 的旁路是**临时且窄**的，明确说"上游收敛后我们愿意移除"。
4. 不要求作者按我们的方案改；给选项，附最小可行缓解（日志 + 开关）。

---

## English body（可直接复制）

**Title**: `JavaMethodMixin` ("Lua safety layer") nils every other mod's Lua→Java calls, because Knot loads only one `org.luaj.vm2.lib.jse.JavaMethod`

**Affected version**: Hold My Items 5.1 (Fabric, Minecraft 1.21.11). The same mixin text appears in the
26.2 decompiled port, so this does not look version-specific — please correct us if the 1.21.11 build differs.

### TL;DR

`com.holdmylua.source.mixin.safety.JavaMethodMixin` injects at `RETURN` of
`org.luaj.vm2.lib.jse.JavaMethod.invokeMethod` and replaces the result with `LuaValue.NIL` for every
method that does not carry HMI's own `@Safe` annotation (plus a name allowlist of `getOrDefault` / `put`).

Fabric Knot loads **exactly one** `org.luaj.vm2.lib.jse.JavaMethod` class per JVM, no matter which mod's
jar (or nested jar) it came from. The mixin therefore applies to *every* mod that uses luaj's JSE bridge,
not only to HMI's own scripts. For TaCZ — a gun mod whose first-person animation state machines and gun
logic are Lua-driven — this means every `context:getAmmoCount()`, `api:shootOnce()`, `api:getReloadTime()`
returns `nil`, and the game crashes the first frame a gun is held.

### Symptom (player-visible)

```
org.luaj.vm2.LuaError: tacz_default_state_machine:74 bad argument: attempt to compare __le on nil and number
```

from the script line

```lua
return (not context:hasBulletInBarrel()) and (context:getAmmoCount() <= 0)
```

Both calls returned `nil`: `not nil` is `true`, then `nil <= 0` throws. The stack contains only luaj and
TaCZ frames — no HMI frame anywhere (see "Why this is expensive to diagnose").

### The mixin, as we understand it

Quoted from the third-party decompiled port of the official 1.21.11 jar (HMI 5.1.1) at
`ThinkofRain1213/HMI-26.2`, `mod/src/main/java/com/holdmylua/source/mixin/safety/JavaMethodMixin.java`
(HMI has no official source repository, so this is the only text we could read — please confirm):

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

Three properties turn a local sandbox into a global one:

1. **The target is a library class, not an HMI class.** In our test the loaded `JavaMethod` was the copy
   *TaCZ* bundles (`com.github.FiguraMC.luaj:3.0.8-figura`), i.e. HMI's mixin patched another mod's luaj.
   We proved this by line-number fingerprinting the crash stack: `LuaValue.error:1209`, `comparemt:3437`,
   `lteq_b:3179`, `LuaClosure.execute:402` all match the Figura fork and none match the luaj 3.0.1 that
   HMI nests (1041 / 2984 / 2733).
2. **The escape hatch is HMI-private.** `com.holdmylua.source.annotation.Safe` is HMI's own marker.
   No third-party mod can reasonably take a compile-time dependency on an item-rendering mod just to keep
   its own scripting API alive — and there is no way to annotate methods you do not own.
3. **The check has no notion of who is calling.** `CallbackInfoReturnable#setReturnValue` cancels the
   callback, so the substitution is unconditional for every Lua→Java call in the JVM, including calls made
   by another mod's scripts inside another mod's `Globals`.

Because HMI's `fabric.mod.json` is `environment: "*"` and this mixin sits in the common `mixins` list,
the same applies on an integrated/dedicated server — i.e. gameplay logic, not just rendering.

### Secondary observation (the allowlist cuts both ways)

`getOrDefault` / `put` are matched **by name only**, so any class's `put` or `getOrDefault`
(`java.util.Map`, mod classes, Minecraft classes) escapes the sandbox regardless of who declares it, while
everything else is blocked. If the intent is "HMI scripts may only touch HMI's API", name matching is both
too loose and too strict at the same time.

### Why this is expensive to diagnose

The failure mode is *silent data corruption in someone else's mod*: the victim sees `nil` arithmetic or
`attempt to index nil` inside its own Lua, with no HMI class in the stack and nothing in the log except
`HMI Lua safety layer loaded!` on stdout. We spent a considerable amount of time diffing luaj versions and
classloader interleaving before finding the mixin. Any other Lua-scripting mod will land in the same place.

### Minimal reproduction

1. Fabric 1.21.11 + TaCZ Refabricated (R3 build) + Hold My Items 5.1.
2. Enter a world, hold any gun from the default pack in first person.
3. Crash on the first frame the animation state machine evaluates a numeric comparison.

Generic reproduction without TaCZ: install HMI alongside any mod that coerces a Java object into luaj
(`CoerceJavaToLua.coerce(obj)`) and calls one of its methods from a script — the call returns `nil`.
We can provide a ~20-line test mod if that is useful.

### Suggested fixes (any of these preserves your sandbox intent)

- **A. Scope enforcement to HMI's own runtime.** You already own the script lifecycle
  (`LuaScriptManager` / `GlobalsStorage`). Set a marker (a `ThreadLocal`, or a flag on your `Globals`)
  around HMI script execution and only substitute `NIL` while that marker is set. Lua→Java calls are
  synchronous on the calling thread, so this is a small change and it makes the sandbox exactly as wide as
  "code HMI is running".
- **B. Sandbox the runtime instead of the shared class.** Build HMI's `Globals` with a restricted JSE
  facade that only exposes `@Safe` members for the objects *HMI itself* coerced (track them in an identity
  set). No mixin into luaj needed at all, and other mods' luaj copies stay untouched.
- **C. Enforce at the coercion boundary rather than the dispatch boundary.** Decide what HMI hands *into*
  Lua (which objects, which members), instead of filtering what comes *out* of every Java method in the JVM.
- **D. Minimum viable mitigation while A/B/C are pending.**
  1. put the layer behind a config option (default on);
  2. replace `System.out.println("HMI Lua safety layer loaded!")` with a real logger line naming the mod;
  3. when about to nil a method whose declaring class is *not* an HMI class, log once at WARN with the
     declaring class and method name. That single line would have turned our investigation from days into
     minutes, and it would tell you immediately which other mods you are affecting.

### What TaCZ does in the meantime (transparency, not a demand)

We ship a narrow bypass so our users are not stuck: a `HEAD` injector on the same method that cancels
**only** for methods declared by TaCZ/LRTactical classes (or invoked on their instances), and only after a
one-shot runtime probe has proven that Java method results really are being nil'd. HMI's sandbox keeps
applying to everything else, including HMI's own scripts and Minecraft objects, and the mixin is only
loaded when `holdmyitems` is present.

- `cn/sh1rocu/tacz/mixin/compat/holdmyitems/HoldMyItemsJavaMethodMixin.java`
- `com/tacz/guns/compat/holdmyitems/LuaBridgeGuard.java`

Verified in game on 2026-09-12 (1.21.11, HMI 5.1): the crash is gone and gun animations/logic run normally.
We will happily delete this bypass once the safety layer is scoped to HMI's own runtime, and we are glad to
test any patch you produce — we have a reproduction environment and a written verification matrix.

One residual gap we deliberately did **not** close, because it would mean widening access on your behalf:
if a gun pack script chains into a Minecraft object we handed it (`api:getItemStack():getCount()`), that
call is still nil'd by your layer. Our own default pack does not do this, third-party packs might.

---

## 中文摘要（给维护者，不随正文投递）

- 我们要说的只有一件事：**沙箱挂错了层**。挂在 luaj 的 `JavaMethod` 上 = 挂在全 JVM 唯一一份共享库类上
  = 影响所有用 luaj JSE 桥的 mod。作者限制自己脚本的本意没问题，问题在实现位置。
- 我们给了 4 个选项，A（按运行时刻收敛，ThreadLocal 标记）改动最小、语义最准；B/C 是架构层面；
  D 是"什么都不改也先做"的最小缓解（配置开关 + logger + 对非 HMI 类打一次 WARN）。
- 我们主动交代了自己的旁路实现与文件路径，并承诺上游收敛后可移除 —— 这既是对等，也避免作者以为我们在
  绕过他的安全设计搞对抗。
- 明确保留了一条不修的口子（脚本链式调用 Minecraft 对象），并说明理由。这符合 `AGENTS.md` §2：
  不把"绕过"包装成"修复"，也不替别人决定沙箱宽度。
- 如果作者回应并给了补丁，我们要做的验证就是 `HOLD_MY_ITEMS_COMPAT.md` 第 4 节矩阵的第 8、9 项
  （不装 HMI 的回归 + HMI 自身脚本表现不变）。
