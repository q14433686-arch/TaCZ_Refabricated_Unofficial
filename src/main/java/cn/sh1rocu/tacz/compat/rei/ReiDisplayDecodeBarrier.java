package cn.sh1rocu.tacz.compat.rei;

import com.tacz.guns.GunMod;
import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.transformers.PacketTransformer;
import io.netty.buffer.Unpooled;
import me.shedaniel.rei.api.common.display.DisplaySerializerRegistry;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import me.shedaniel.rei.impl.common.plugins.ReloadManagerImpl;
import me.shedaniel.rei.plugin.common.displays.crafting.DefaultShapedDisplay;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

/** REI 21.11.816 decodes its display SET on the client thread while its START stage can clear
 * the serializers on the reload worker. Its per-display decoder deliberately filters failed
 * decodes out; waiting to add the already-decoded displays is too late. Delay only the original
 * reassembled display bytes during this window. Never reconstruct a display or request a reload. */
public final class ReiDisplayDecodeBarrier implements PacketTransformer {
    public static final Identifier CHANNEL = Identifier.parse("roughlyenoughitems:sync_displays");
    private static final int MAX_PACKETS = 8, MAX_BYTES = 16 * 1024 * 1024;
    private static final long MAX_WAIT = TimeUnit.SECONDS.toNanos(15);
    private record Pending(byte[] bytes, RegistryAccess registries, TransformationSink sink, long received) {}
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private int bytes;
    private ClientPacketListener connection;

    public ReiDisplayDecodeBarrier() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> clear());
    }

    private static boolean ready() {
        return ReloadManagerImpl.countRunningReloadTasks() == 0 && !PluginManager.areAnyReloading()
                && DisplaySerializerRegistry.getInstance().isRegistered(DefaultShapedDisplay.SERIALIZER);
    }

    @Override public void inbound(NetworkManager.Side side, Identifier id, RegistryFriendlyByteBuf buf,
                                  NetworkManager.PacketContext context, TransformationSink sink) {
        var mc = Minecraft.getInstance();
        // Fabric play receivers run on the client thread. Never retain another thread's sink.
        if (!mc.isSameThread() || side != NetworkManager.Side.S2C || !CHANNEL.equals(id) || mc.isLocalServer() || mc.getConnection() == null) {
            sink.accept(side, id, buf);
            return;
        }
        if (connection != mc.getConnection()) clear();
        // Even a presently idle registry may belong to the previous recipe generation. Let the
        // incoming packet batch (including vanilla recipe updates) finish before checking it.
        int length = buf.readableBytes();
        if (pending.size() >= MAX_PACKETS || length > MAX_BYTES - bytes) {
            // Outside the compatibility budget: preserve wire order and native behavior, not an
            // unbounded copy, partial table, or silent removal of an earlier SET.
            GunMod.LOGGER.warn("[TACZ REI] Display sync exceeds deferral budget; using REI's decoder now");
            drain();
            sink.accept(side, id, buf);
            return;
        }
        byte[] owned = new byte[length];
        buf.readBytes(owned); // split-transform buffers are released when inbound returns
        connection = mc.getConnection();
        pending.addLast(new Pending(owned, buf.registryAccess(), sink, System.nanoTime()));
        bytes += length;
    }

    @Override public void outbound(NetworkManager.Side side, Identifier id, RegistryFriendlyByteBuf buf, TransformationSink sink) {
        sink.accept(side, id, buf);
    }

    private void tick(Minecraft mc) {
        if (pending.isEmpty()) return;
        if (mc.getConnection() != connection || mc.level == null) { clear(); return; }
        if (!ready()) {
            if (System.nanoTime() - pending.getFirst().received < MAX_WAIT) return;
            GunMod.LOGGER.warn("[TACZ REI] Display registries still busy after 15s; falling back to REI's decoder");
        } else {
            GunMod.LOGGER.info("[TACZ REI] Forwarding {} deferred display packet(s), {} bytes to the ready REI decoder", pending.size(), bytes);
        }
        drain();
    }

    private void drain() {
        try {
            while (!pending.isEmpty()) {
                var packet = pending.removeFirst();
                var buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(packet.bytes), packet.registries);
                try { packet.sink.accept(NetworkManager.Side.S2C, CHANNEL, buf); }
                finally { buf.release(); }
            }
        } catch (RuntimeException ex) {
            GunMod.LOGGER.warn("[TACZ REI] Could not read server display sync; discarding deferred batch", ex);
        } finally { clear(); }
    }

    private void clear() { pending.clear(); bytes = 0; connection = null; }
}
