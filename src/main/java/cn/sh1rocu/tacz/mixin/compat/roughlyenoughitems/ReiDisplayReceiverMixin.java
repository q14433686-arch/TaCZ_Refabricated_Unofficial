package cn.sh1rocu.tacz.mixin.compat.roughlyenoughitems;

import cn.sh1rocu.tacz.compat.rei.ReiDisplayDecodeBarrier;
import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.transformers.PacketTransformer;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.injection.Desc;
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
    // Third-party names stay literal; class literals in @Desc remap Type and StreamCodec in
    // the production JAR. A remap=false named descriptor string would only work in development.
    @ModifyArgs(method = "onInitializeClient", at = @At(value = "INVOKE", desc = @Desc(owner = NetworkManager.class,
            value = "registerReceiver", args = {NetworkManager.Side.class, CustomPacketPayload.Type.class,
            StreamCodec.class, List.class, NetworkManager.NetworkReceiver.class})), require = 1)
    private void tacz$holdDisplayBytesDuringReload(Args args) {
        CustomPacketPayload.Type<?> type = args.get(1);
        if (!ReiDisplayDecodeBarrier.CHANNEL.equals(type.id())) return;
        List<PacketTransformer> original = args.get(3);
        var chain = new ArrayList<>(original);
        chain.add(new ReiDisplayDecodeBarrier());
        args.set(3, List.copyOf(chain));
    }
}
