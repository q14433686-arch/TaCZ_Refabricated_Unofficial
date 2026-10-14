package cn.sh1rocu.tacz.mixin.client;

import com.tacz.guns.client.render.scope.BorrowedScopeTextures;
import net.minecraft.client.renderer.texture.TextureHandle;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextureManager.class)
public abstract class ScopeTextureManagerMixin {
    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void tacz$borrowScopeTexture(Identifier id, CallbackInfoReturnable<TextureHandle> cir) {
        TextureHandle handle = BorrowedScopeTextures.get(id);
        if (handle != null) {
            cir.setReturnValue(handle);
        }
    }
}
