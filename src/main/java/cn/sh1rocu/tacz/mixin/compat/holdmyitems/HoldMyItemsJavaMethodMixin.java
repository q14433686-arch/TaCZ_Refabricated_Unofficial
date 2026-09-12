package cn.sh1rocu.tacz.mixin.compat.holdmyitems;

import com.tacz.guns.compat.holdmyitems.LuaBridgeGuard;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

/**
 * Stops Hold My Items' global "Lua safety layer" from nil'ing TaCZ's own script API.
 *
 * <p>HMI's {@code com.holdmylua.source.mixin.safety.JavaMethodMixin} injects at {@code RETURN} of
 * {@code JavaMethod#invokeMethod} and replaces the result with {@code LuaValue.NIL} unless the
 * method carries HMI's own {@code @Safe} marker. Knot loads a single {@code JavaMethod} class for
 * the whole JVM, so TaCZ's calls are caught by it too: {@code context:getAmmoCount()} yields
 * {@code nil}, and holding a gun dies on
 * {@code tacz_default_state_machine:74 attempt to compare __le on nil and number}. Gun logic scripts
 * ({@code api:shootOnce(...)}, {@code api:getReloadTime()}, ...) are silenced the same way.</p>
 *
 * <p>Injecting at {@code HEAD} and cancelling runs before HMI's {@code RETURN} handler, so the
 * result TaCZ computes is the result the script receives. {@link LuaBridgeGuard} keeps the bypass
 * narrow: only methods declared by a TaCZ/LRTactical class or invoked on one of their instances, and
 * only once a runtime probe confirms the bridge is really being nil'd. HMI's sandbox keeps applying
 * to everything else, including HMI's own scripts.</p>
 *
 * <p>Applied only when {@code holdmyitems} is loaded (see {@code cn.sh1rocu.tacz.util.MixinPlugin}).
 * {@code require = 0} means a future luaj that renames this method degrades to today's behaviour
 * instead of failing to launch.</p>
 */
@Mixin(targets = "org.luaj.vm2.lib.jse.JavaMethod", remap = false)
public abstract class HoldMyItemsJavaMethodMixin {

    @Shadow
    @Final
    private Method method;

    @Inject(method = "invokeMethod", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void tacz$invokeScriptApiDirectly(Object instance, Varargs args, CallbackInfoReturnable<LuaValue> cir) {
        if (!LuaBridgeGuard.shouldInvokeDirectly(this.method, instance)) {
            return;
        }
        LuaValue result = LuaBridgeGuard.invokeDirectly(this.method, instance, args);
        if (result != null) {
            cir.setReturnValue(result);
            cir.cancel();
        }
    }
}
