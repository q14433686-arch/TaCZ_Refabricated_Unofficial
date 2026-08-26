package cn.sh1rocu.tacz.mixin.compat.punchy;

import com.tacz.guns.compat.firstperson.FirstPersonAnimationCompat;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents Punchy from submitting its independent arm rig over a TACZ viewmodel.
 *
 * <p>TACZ first-person guns already attach player skin arms at authored
 * {@code left_hand}/{@code right_hand} locators. Punchy's own first-person arm
 * pass is a second rig and appears detached from the gun.</p>
 */
@Pseudo
@Mixin(targets = "punchy.client.render.PunchyArmRenderer", remap = false)
public abstract class PunchyArmRendererMixin {
    @Inject(method = "renderFirstPerson", at = @At("HEAD"), cancellable = true,
            require = 0, remap = false)
    private static void tacz$yieldFirstPersonRenderer(CallbackInfo ci) {
        if (FirstPersonAnimationCompat.shouldUseTaczRenderer(Minecraft.getInstance().player)) {
            ci.cancel();
        }
    }
}
