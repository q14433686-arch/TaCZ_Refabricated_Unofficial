package cn.sh1rocu.tacz.api.extension;

import com.tacz.guns.GunMod;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.List;

// Porting_Lib
public interface IEntityAdditionalSpawnData {
    Identifier EXTRA_DATA_PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "extra_entity_spawn_data");
    CustomPacketPayload.Type<ExtraSpawnDataPayload> EXTRA_DATA_TYPE = new CustomPacketPayload.Type<>(EXTRA_DATA_PACKET_ID);

    StreamCodec<RegistryFriendlyByteBuf, ExtraSpawnDataPayload> EXTRA_DATA_CODEC =
            StreamCodec.ofMember(
                    (payload, buf) -> {
                        buf.writeVarInt(payload.entityId());
                        buf.writeByteArray(payload.data());
                    },
                    buf -> new ExtraSpawnDataPayload(buf.readVarInt(), buf.readByteArray())
            );

    static void registerPayload() {
        PayloadTypeRegistry.clientboundPlay().register(EXTRA_DATA_TYPE, EXTRA_DATA_CODEC);
    }

    void readSpawnData(FriendlyByteBuf buf);

    void writeSpawnData(FriendlyByteBuf buf);

    static Packet<ClientGamePacketListener> getEntitySpawningPacket(Entity entity) {
        // 【26.2 移植陷阱：实体生成包出生坐标被 BlockPos 取整】
        // 1.21.1 的 ClientboundAddEntityPacket(Entity, int, BlockPos) 里 BlockPos 只是
        // "pos" 附属字段（供画/展示框等贴方块实体用），x/y/z 取自实体精确坐标；
        // 但 26.2 字节码确认该构造器已改为：
        //   this(entity.getId(), entity.getUUID(),
        //        pos.getX(), pos.getY(), pos.getZ(),   // ← int！x/y/z 全部块对齐
        //        entity.getXRot(), entity.getYRot(), entity.getType(), data,
        //        entity.getDeltaMovement(), entity.getYHeadRot());
        // 即 26.2 中 BlockPos 直接【替代】了 x/y/z（新签名已无独立 BlockPos 字段，
        // 画/展示框的客户端处理器直接把 x/y/z 当贴块坐标读）。
        // 本方法若沿用 1.21.1 时代的 `new ClientboundAddEntityPacket(entity, 0, entity.blockPosition())`，
        // 客户端收到的子弹出生位置 = floor(服务器精确坐标)：所有子弹从玩家脚下方块的负角飞出，
        // 出生点与瞄准眼线的偏差是一个【与视线朝向无关的世界轴固定向量】≈ (0, -1, 0)^3 内随机。
        // 视觉上：第一人称曳光弹近端锚定枪口（第 26 轮修复），远端弹道收敛到被取整的弹道线，
        // 其屏幕左右偏量 = offset · right_world，随 yaw 正弦摆动（朝某方位"回正"，
        // 其反方向偏最大）——实测日志：全朝向 113 发子弹 spawn−eye 恒为
        // (-0.309, -0.620, -0.755)（= 站立点块内小数部分的相反数），按 yaw 重新投影
        // 即得报告的"北偏右、南偏左、西/东回正"现象。服务器端命中判定不受影响（逻辑在服务端）。
        // 修法：改用公开全参构造器直接写入实体精确 double 坐标，恢复 1.21.1 语义。
        return getEntitySpawningPacket(entity, new ClientboundAddEntityPacket(
                entity.getId(), entity.getUUID(),
                entity.getX(), entity.getY(), entity.getZ(),
                entity.getXRot(), entity.getYRot(),
                entity.getType(), 0,
                entity.getDeltaMovement(), entity.getYHeadRot()));
    }

    static Packet<ClientGamePacketListener> getEntitySpawningPacket(Entity entity, Packet<ClientGamePacketListener> base) {
        if (entity instanceof IEntityAdditionalSpawnData extra) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            // BUG FIX: Do NOT write entity.getId() here — the EXTRA_DATA_CODEC already
            // encodes entityId separately. Writing it again corrupts readSpawnData() which
            // reads the redundant varint as its first float field (xRot), causing every
            // subsequent field to desync and eventually "Not enough bytes in buffer".
            extra.writeSpawnData(buf);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            buf.release();
            ExtraSpawnDataPayload payload = new ExtraSpawnDataPayload(entity.getId(), data);
            var extraPacket = ServerPlayNetworking.createClientboundPacket(payload);
            return new ClientboundBundlePacket(List.of(base, extraPacket));
        }
        return base;
    }

    record ExtraSpawnDataPayload(int entityId, byte[] data) implements CustomPacketPayload {
        @Override
        public Type<ExtraSpawnDataPayload> type() {
            return EXTRA_DATA_TYPE;
        }
    }
}