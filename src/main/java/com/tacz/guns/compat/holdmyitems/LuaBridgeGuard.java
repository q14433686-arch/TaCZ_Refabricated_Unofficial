package com.tacz.guns.compat.holdmyitems;

import com.tacz.guns.GunMod;
import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.CoerceLuaToJava;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps TaCZ's Lua scripting API callable while another mod sandboxes luaj's JSE bridge.
 *
 * <p>Hold My Items 5.x ships {@code com.holdmylua.source.mixin.safety.JavaMethodMixin}. It injects
 * at {@code RETURN} of {@code org.luaj.vm2.lib.jse.JavaMethod#invokeMethod} and replaces the result
 * with {@link LuaValue#NIL} for every method that does not carry HMI's own
 * {@code com.holdmylua.source.annotation.Safe} marker (plus a two-entry {@code getOrDefault}/{@code put}
 * allow list). Fabric Knot loads exactly one {@code JavaMethod} class per JVM, so that mixin also
 * silences every Lua call TaCZ makes: {@code context:getAmmoCount()} returns {@code nil} instead of a
 * number, which is what turns
 * {@code return (not context:hasBulletInBarrel()) and (context:getAmmoCount() <= 0)} into
 * {@code attempt to compare __le on nil and number} the moment a gun is held.</p>
 *
 * <p>This guard restores those calls without widening anyone else's sandbox. It only ever acts on
 * methods declared by TaCZ/LRTactical classes, or on methods invoked on a TaCZ/LRTactical instance,
 * and only after a runtime probe has proven that the bridge really is being nil'd. Everything else
 * keeps going through luaj untouched, so HMI's own scripting sandbox stays exactly as strict as its
 * author intended.</p>
 *
 * <p>See {@code docs/HOLD_MY_ITEMS_COMPAT.md} for the full evidence chain and the verification
 * checklist.</p>
 */
public final class LuaBridgeGuard {

    /** Bridge not probed yet. */
    private static final int UNPROBED = 0;
    /** Java method results come back as authored. */
    private static final int HEALTHY = 1;
    /** Something is replacing Java method results with {@code nil}. */
    private static final int SANDBOXED = 2;

    /**
     * Package prefixes of every class TaCZ exposes to Lua. Anything outside these prefixes belongs
     * to another mod or to Minecraft, and stays under the foreign sandbox's rules.
     */
    private static final String[] SCRIPT_API_PACKAGES = {
            "com.tacz.",
            "me.xjqsh.lrtactical.",
            "cn.sh1rocu.tacz."
    };

    private static final Map<Class<?>, Boolean> SCRIPT_API_CLASSES = new ConcurrentHashMap<>();

    private static final Object PROBE_LOCK = new Object();

    /**
     * Probe constant. {@link AtomicInteger#get()} is a plain JDK method no mod can legitimately have
     * annotated, so it travels the very same luaj dispatch path TaCZ's script API uses.
     */
    private static final int PROBE_VALUE = 0x7AC2;

    private static volatile int bridgeState = UNPROBED;

    private LuaBridgeGuard() {
    }

    /**
     * Whether the pending {@code JavaMethod#invokeMethod} call must be executed by TaCZ instead of
     * being left to a sandbox that would nil its result.
     *
     * <p>Two shapes count as "TaCZ's own script API": a method declared by a TaCZ/LRTactical class,
     * and any method invoked on a TaCZ/LRTactical instance (this second one covers {@code toString()}
     * and other members inherited from {@code Object} or from a non-TaCZ superclass). A Minecraft
     * object handed to a script - {@code api:getItemStack()} for example - is deliberately not
     * covered: widening the bypass that far would switch off HMI's sandbox for everyone.</p>
     *
     * @param instance the userdata target, {@code null} for a static method call
     */
    public static boolean shouldInvokeDirectly(@Nullable Method method, @Nullable Object instance) {
        if (method == null) {
            return false;
        }
        if (!isScriptApiClass(method.getDeclaringClass())
                && (instance == null || !isScriptApiClass(instance.getClass()))) {
            return false;
        }
        // Gate first, probe second: the probe itself dispatches a Java method through luaj and must
        // never be intercepted by the bypass it is measuring.
        return isBridgeSandboxed();
    }

    /**
     * True when an outside mod is currently forcing Java method results to {@code nil}.
     *
     * <p>Probed once, lazily, on the first TaCZ script API call, and cached for the JVM lifetime.</p>
     */
    public static boolean isBridgeSandboxed() {
        int state = bridgeState;
        if (state != UNPROBED) {
            return state == SANDBOXED;
        }
        synchronized (PROBE_LOCK) {
            state = bridgeState;
            if (state == UNPROBED) {
                state = probeBridge() ? HEALTHY : SANDBOXED;
                bridgeState = state;
                if (state == SANDBOXED) {
                    GunMod.LOGGER.warn("Another mod is forcing every unannotated Java method result to nil inside luaj " +
                            "(Hold My Items' JavaMethodMixin is the known source). TaCZ now invokes its own script API " +
                            "directly, so gun animations and gun logic keep working. Details: docs/HOLD_MY_ITEMS_COMPAT.md");
                }
            }
            return state == SANDBOXED;
        }
    }

    /**
     * Invokes {@code method} exactly the way luaj's {@code JavaMethod#invokeMethod} does, so a
     * foreign mixin cannot substitute the result.
     *
     * @return the coerced result. Never {@code null} with the luaj versions TaCZ and HMI ship, but
     * callers treat {@code null} as "declined" and fall back to luaj's own dispatch, so a future luaj
     * cannot make TaCZ silently swallow a call.
     */
    public static @Nullable LuaValue invokeDirectly(Method method, @Nullable Object instance, Varargs args) {
        try {
            return CoerceJavaToLua.coerce(method.invoke(instance, convertArgs(method, args)));
        } catch (InvocationTargetException e) {
            // Same contract as luaj: the script-visible cause becomes a LuaError, so a broken gun
            // pack still reports its own line number instead of being swallowed here.
            throw new LuaError(e.getTargetException());
        } catch (Exception e) {
            // luaj's own invokeMethod ends with LuaValue.error("coercion error " + e), which is
            // declared to return LuaValue but always throws; construct the same LuaError directly so
            // this compiles against every luaj build in use.
            throw new LuaError("coercion error " + e);
        }
    }

    /**
     * Faithful copy of luaj's {@code JavaMember#convertArgs}, built on luaj's public coercion entry
     * point ({@code CoerceLuaToJava.coerce(LuaValue, Class)} is {@code getCoercion(clazz).coerce(value)},
     * which is precisely what {@code convertArgs} does per parameter). Re-implementing it here keeps
     * the mixin down to a single shadowed field.
     *
     * <p>None of TaCZ's Lua-exposed API methods are variable-arity; the varargs branch only exists
     * so third-party packs that reach such a method still behave like stock luaj.</p>
     */
    private static Object[] convertArgs(Method method, Varargs args) {
        Class<?>[] params = method.getParameterTypes();
        boolean varargs = method.isVarArgs();
        int fixed = varargs ? params.length - 1 : params.length;
        int total = varargs ? Math.max(fixed, args.narg()) : fixed;
        Object[] converted = new Object[total];
        for (int i = 0; i < fixed && i < total; i++) {
            converted[i] = CoerceLuaToJava.coerce(args.arg(i + 1), params[i]);
        }
        if (varargs) {
            Class<?> varArgType = params[params.length - 1];
            for (int i = fixed; i < total; i++) {
                converted[i] = CoerceLuaToJava.coerce(args.arg(i + 1), varArgType);
            }
        }
        return converted;
    }

    private static boolean isScriptApiClass(@Nullable Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        Boolean cached = SCRIPT_API_CLASSES.get(clazz);
        if (cached != null) {
            return cached;
        }
        String name = clazz.getName();
        boolean match = false;
        for (String prefix : SCRIPT_API_PACKAGES) {
            if (name.startsWith(prefix)) {
                match = true;
                break;
            }
        }
        SCRIPT_API_CLASSES.put(clazz, match);
        return match;
    }

    /** Returns {@code true} when Java method results still arrive as authored. */
    private static boolean probeBridge() {
        try {
            LuaValue instance = CoerceJavaToLua.coerce(new AtomicInteger(PROBE_VALUE));
            LuaValue function = instance.get(LuaValue.valueOf("get"));
            if (!function.isfunction()) {
                // Not the failure mode this guard exists for; leave luaj alone.
                return true;
            }
            LuaValue result = function.call(instance);
            return result.isnumber() && result.toint() == PROBE_VALUE;
        } catch (Throwable t) {
            // Fail open: if the probe itself cannot run, do not change luaj's dispatch at all.
            GunMod.LOGGER.warn("TaCZ could not probe the Lua-Java bridge; leaving luaj's dispatch untouched", t);
            return true;
        }
    }
}
