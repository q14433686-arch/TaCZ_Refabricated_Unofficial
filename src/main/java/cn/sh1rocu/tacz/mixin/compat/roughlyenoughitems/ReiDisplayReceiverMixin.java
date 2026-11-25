package cn.sh1rocu.tacz.mixin.compat.roughlyenoughitems;

import cn.sh1rocu.tacz.compat.rei.ReiDisplayDecodeBarrier;
import dev.architectury.networking.transformers.PacketTransformer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.ArrayList;
import java.util.List;

/** Only the pinned client's display-sync receiver. Keep Architectury's own split/reassembly
 * first and REI's own codec, receiver, SET/APPEND semantics and origin tracking downstream. */
@Pseudo
@Mixin(targets = "me.shedaniel.rei.RoughlyEnoughItemsCoreClient", remap = false)
abstract class ReiDisplayReceiverMixin {
    @ModifyArgs(method = "onInitializeClient", at = @At(value = "INVOKE", target =
            "Ldev/architectury/networking/NetworkManager;registerReceiver(Ldev/architectury/networking/NetworkManager$Side;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;Lnet/minecraft/network/codec/StreamCodec;Ljava/util/List;Ldev/architectury/networking/NetworkManager$NetworkReceiver;)V"))
    private void tacz$holdDisplayBytesDuringReload(Args args) {
        CustomPacketPayload.Type<?> type = args.get(1);
        if (!ReiDisplayDecodeBarrier.CHANNEL.equals(type.id())) return;
        List<PacketTransformer> original = args.get(3);
        var chain = new ArrayList<>(original);
        chain.add(new ReiDisplayDecodeBarrier());
        args.set(3, List.copyOf(chain));
    }
}
