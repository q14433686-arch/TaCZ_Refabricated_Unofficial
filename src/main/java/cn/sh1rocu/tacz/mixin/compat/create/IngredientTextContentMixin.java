package cn.sh1rocu.tacz.mixin.compat.create;

import com.tacz.guns.GunMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Guards Create Fly 6.0.9's sequenced-assembly lore generation during the
 * first datapack reload of an existing world.
 *
 * <p>Create's {@code IngredientTextContent#equals} resolves an item's display
 * components while Mojang's item holders may already be bound but their
 * components are not yet bound. The upstream call then throws
 * {@code NullPointerException: Components not bound yet}, which makes the
 * world-selection flow offer safe mode before a server exists. Semantically,
 * Create already treats an unresolved ingredient name as equal; this bridge
 * preserves that fallback instead of resolving it too early.</p>
 *
 * <p>The project deliberately has no compile-time Create dependency. The
 * target is named as a string and this mixin is registered through the existing
 * optional {@code compat.create} gate, so LEGACY/no-Create installations never
 * load or link this class.</p>
 */
@Mixin(targets = "com.zurrtum.create.foundation.recipe.IngredientTextContent", remap = false)
public abstract class IngredientTextContentMixin {
    private static final String TARGET_CLASS = "com.zurrtum.create.foundation.recipe.IngredientTextContent";

    @Unique
    private static boolean tacz$loggedUnboundComponents;

    @Inject(method = "equals", at = @At("HEAD"), cancellable = true, require = 1)
    private void tacz$avoidEarlyComponentResolution(Object other, CallbackInfoReturnable<Boolean> cir) {
        if (other == null || !TARGET_CLASS.equals(other.getClass().getName())) {
            return;
        }

        Optional<?> ownName = tacz$resolveNameSafely(this);
        Optional<?> otherName = tacz$resolveNameSafely(other);
        if (ownName == null || otherName == null) {
            // The upstream implementation returns true when it cannot resolve
            // either ingredient text yet. Mirror that behavior for the narrow
            // "components not bound" window instead of escalating it into a
            // fatal world-load failure.
            if (!tacz$loggedUnboundComponents) {
                tacz$loggedUnboundComponents = true;
                GunMod.LOGGER.warn("Deferred Create Fly sequenced-assembly ingredient-name comparison until item components are bound");
            }
            cir.setReturnValue(true);
            return;
        }

        if (ownName.isPresent() && otherName.isPresent()) {
            cir.setReturnValue(ownName.get().equals(otherName.get()));
        } else {
            cir.setReturnValue(true);
        }
    }

    /**
     * Calls Create's public {@code getName()} without a static Create class
     * reference, retaining optional-mod compilation for this Fabric project.
     * A {@code null} return denotes precisely the unbound-component failure.
     */
    private static Optional<?> tacz$resolveNameSafely(Object content) {
        try {
            Method getName = content.getClass().getMethod("getName");
            Object result = getName.invoke(content);
            return result instanceof Optional<?> optional ? optional : Optional.empty();
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof NullPointerException) {
                return null;
            }
            return Optional.empty();
        } catch (ReflectiveOperationException exception) {
            return Optional.empty();
        }
    }
}
