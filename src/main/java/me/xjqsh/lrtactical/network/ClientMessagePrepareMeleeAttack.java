package me.xjqsh.lrtactical.network;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.api.melee.MeleeAction;
import me.xjqsh.lrtactical.init.ModCapabilities;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * C2S：玩家按下攻击键，请求服务端发起一次近战。
 *
 * <h2>为什么只有这一个包（上游有两个）</h2>
 * 上游还有 {@code CMeleeAttackRequest}，用途是把<b>客户端算好的目标 id 列表</b>
 * 发给服务端结算。本移植第 3 步已改为<b>服务端索敌</b>
 * （{@code IMeleeWeapon#performAttack}），那个包因此没有存在意义 ——
 * 少一个包也少一处可被伪造的输入（上游为此还要加 {@code MELEE_MAX_TARGET_PER_PACKET} 限流）。
 *
 * <h2>为什么仍然要传 origin / direction</h2>
 * 严格来说服务端可以自己取。这里传过来<b>只作参考</b>，
 * 实际结算用的是服务端当刻的权威视线（见 {@code CombatProperties.DelayAttack#perform}）。
 * 保留字段是为了将来做「客户端预测校验 / 反作弊比对」时有据可依，
 * 且与上游协议保持形状一致，便于对照。
 *
 * <p><b>安全性</b>：服务端不信任这两个向量，因此伪造它们无法扩大攻击范围。
 */
public record ClientMessagePrepareMeleeAttack(MeleeAction action, Vec3 origin, Vec3 direction)
        implements CustomPacketPayload {

    public static final Identifier PACKET_ID =
            Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "c2s_prepare_melee_attack");
    public static final CustomPacketPayload.Type<ClientMessagePrepareMeleeAttack> TYPE =
            new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientMessagePrepareMeleeAttack> CODEC =
            StreamCodec.ofMember(ClientMessagePrepareMeleeAttack::write, ClientMessagePrepareMeleeAttack::new);

    public ClientMessagePrepareMeleeAttack(FriendlyByteBuf buf) {
        // 读写顺序必须严格一致
        this(buf.readEnum(MeleeAction.class),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeDouble(origin.x);
        buf.writeDouble(origin.y);
        buf.writeDouble(origin.z);
        buf.writeDouble(direction.x);
        buf.writeDouble(direction.y);
        buf.writeDouble(direction.z);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理：校验冷却后排一个延时结算任务。
     *
     * <p>冷却与「该动作是否配置」都在 {@code preAttack} 内部校验，
     * 因此<b>客户端连发也刷不出额外伤害</b> —— 服务端自己有一份冷却计时。
     */
    public void handle(ServerPlayer player, PacketSender responseSender) {
        ModCapabilities.combatProperties(player).preAttack(action, origin, direction);
    }
}
