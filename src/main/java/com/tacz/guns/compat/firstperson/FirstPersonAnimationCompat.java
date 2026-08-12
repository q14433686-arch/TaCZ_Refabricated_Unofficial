package com.tacz.guns.compat.firstperson;

import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Makes generic first-person animation/body mods yield while TACZ owns the viewmodel.
 *
 * <p>The compatibility contract is intentionally one-way: ordinary items remain under the
 * other mod's control, while a TACZ/LRTactical {@link AnimateGeoItemRenderer} with a loaded
 * model keeps its authored gun/hand animation without a second arm rig being layered over it.</p>
 */
@Environment(EnvType.CLIENT)
public final class FirstPersonAnimationCompat {
    private static final String FIRST_PERSON_MODEL = "firstperson";
    private static final String NOT_ENOUGH_ANIMATIONS = "notenoughanimations";

    private static boolean fpmRegistrationAttempted;
    private static Object fpmActivationHandler;

    private static boolean neaLookupAttempted;
    private static @Nullable Field neaInstanceField;
    private static @Nullable Field neaTransformerField;
    private static @Nullable Method neaRenderingFirstPersonArm;

    private FirstPersonAnimationCompat() {
    }

    public static void init() {
        if (FabricLoader.getInstance().isModLoaded(FIRST_PERSON_MODEL)) {
            registerFirstPersonModelHandler();
        }
    }

    /** Returns the kept/main-hand stack that TACZ would actually draw this frame. */
    public static ItemStack getMainRenderStack(LocalPlayer player) {
        ItemStack kept = KeepingItemRenderer.getRenderer().getCurrentItem();
        return kept != null && !kept.isEmpty() ? kept : player.getMainHandItem();
    }

    /** True only when TACZ has both a custom renderer and a real model to submit. */
    public static boolean isTaczViewmodel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        var renderer = BuiltinItemRendererRegistry.INSTANCE.get(stack.getItem());
        return renderer instanceof AnimateGeoItemRenderer<?, ?> animated && animated.getModel(stack) != null;
    }

    public static boolean shouldUseTaczRenderer(@Nullable LocalPlayer player) {
        return player != null && isTaczViewmodel(getMainRenderStack(player));
    }

    private static void registerFirstPersonModelHandler() {
        if (fpmRegistrationAttempted) {
            return;
        }
        fpmRegistrationAttempted = true;
        try {
            ClassLoader loader = FirstPersonAnimationCompat.class.getClassLoader();
            Class<?> handlerClass = Class.forName(
                    "dev.tr7zw.firstperson.api.ActivationHandler", false, loader);
            Class<?> apiClass = Class.forName(
                    "dev.tr7zw.firstperson.api.FirstPersonAPI", false, loader);

            fpmActivationHandler = Proxy.newProxyInstance(loader, new Class<?>[]{handlerClass},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "preventFirstperson" -> shouldUseTaczRenderer(Minecraft.getInstance().player);
                        case "toString" -> "TACZ first-person viewmodel guard";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == (args == null ? null : args[0]);
                        default -> null;
                    });
            apiClass.getMethod("registerPlayerHandler", Object.class)
                    .invoke(null, fpmActivationHandler);
            GunMod.LOGGER.info("Enabled First-person Model handoff for TACZ animated items");
        } catch (ReflectiveOperationException | LinkageError e) {
            GunMod.LOGGER.warn("First-person Model is loaded, but its activation API could not be registered", e);
        }
    }

    /**
     * Marks TACZ's direct AvatarRenderer arm submission as a first-person hand pass for NEA.
     * NEA already exposes this guard for vanilla hands; TACZ bypasses the vanilla entry point,
     * so the same flag has to be bridged around our direct call.
     */
    public static void beginDirectArmRender() {
        setNeaFirstPersonArm(true);
    }

    public static void endDirectArmRender() {
        setNeaFirstPersonArm(false);
    }

    private static void setNeaFirstPersonArm(boolean rendering) {
        if (!FabricLoader.getInstance().isModLoaded(NOT_ENOUGH_ANIMATIONS)) {
            return;
        }
        try {
            if (!neaLookupAttempted) {
                neaLookupAttempted = true;
                Class<?> loaderClass = Class.forName(
                        "dev.tr7zw.notenoughanimations.NEAnimationsLoader", false,
                        FirstPersonAnimationCompat.class.getClassLoader());
                Class<?> transformerClass = Class.forName(
                        "dev.tr7zw.notenoughanimations.logic.PlayerTransformer", false,
                        FirstPersonAnimationCompat.class.getClassLoader());
                neaInstanceField = loaderClass.getField("INSTANCE");
                neaTransformerField = loaderClass.getField("playerTransformer");
                neaRenderingFirstPersonArm = transformerClass.getMethod(
                        "renderingFirstPersonArm", boolean.class);
            }
            if (neaInstanceField == null || neaTransformerField == null
                    || neaRenderingFirstPersonArm == null) {
                return;
            }
            Object instance = neaInstanceField.get(null);
            if (instance == null) {
                return;
            }
            Object transformer = neaTransformerField.get(instance);
            if (transformer != null) {
                neaRenderingFirstPersonArm.invoke(transformer, rendering);
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            neaInstanceField = null;
            neaTransformerField = null;
            neaRenderingFirstPersonArm = null;
            if (rendering) {
                GunMod.LOGGER.warn("Not Enough Animations hand-render guard could not be bridged", e);
            }
        }
    }
}
