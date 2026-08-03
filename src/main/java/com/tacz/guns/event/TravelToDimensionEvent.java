package com.tacz.guns.event;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 修正跨纬度时，枪械数据不刷新的问题，这是服务端的刷新
 *
 * <h2>【重要】玩家必须单独注册 AFTER_PLAYER_CHANGE_LEVEL</h2>
 *
 * <p>本类原先<b>只</b>注册在 {@code AFTER_ENTITY_CHANGE_LEVEL} 上，
 * 而该事件的 Fabric 官方 javadoc 明写：</p>
 * <blockquote>
 * This event <b>does not apply to the {@code ServerPlayer}</b> since players are
 * physically moved to the new level instead of being copied over. […]
 * For a {@code ServerPlayer}, please use {@code AFTER_PLAYER_CHANGE_LEVEL}.
 * </blockquote>
 *
 * <p>也就是说这个「专门用来修跨维度枪械数据不刷新」的 handler，
 * <b>对玩家一次都没有执行过</b> —— 原实现里那句
 * {@code newEntity instanceof LivingEntity} 说明作者以为玩家会走进来。
 * 上游 1.21.1 用的旧名 {@code ServerEntityWorldChangeEvents.AFTER_ENTITY_CHANGE_WORLD}
 * 有<b>完全相同</b>的「排除玩家」语义，所以这个缺陷是<b>从上游继承</b>的，
 * 与本移植的任何改动无关（这解释了实测所见「此 bug 早于 Beta-2」）。</p>
 *
 * <h2>造成的可见症状：跨维度后换弹动作连贯、但子弹不变</h2>
 *
 * <p>客户端那侧的刷新走的是另一条路
 * （{@code RefreshClonePlayerDataEvent#onClientTick} 轮询
 * {@code Minecraft#player} 实例变化，跨维度会换 {@code LocalPlayer}，
 * 故<b>客户端照常执行</b>），于是换弹动画、音效一切正常；
 * 而服务端 {@code initialData()} 从未执行，
 * {@code reloadStateType}/{@code reloadTimestamp}/{@code isBolting}/
 * {@code shootTimestamp} 全部保留旧维度的值。
 * 服务端 {@code LivingEntityReload#reload} 首道门禁
 * {@code data.reloadStateType.isReloading()} 因残留而成立，直接 return，
 * 于是「客户端演完整套换弹、服务端压根不认」。
 * <b>客户端与服务端的不对称</b>正是该症状的成因。</p>
 *
 * <h2>为什么这么改是安全的</h2>
 *
 * <p>{@code initialData()} 只重置状态机字段，并把
 * {@code currentGunItem} 重新绑定为 {@code () -> getMainHandItem()}、
 * 刷新配件缓存；它<b>不触碰 {@code drawTimestamp}</b>，
 * 因此不会产生切枪冷却 —— 不会重蹈
 * {@code ServerPlayerMixin} 注释里记录的那次回归
 * （那次是把 {@code currentGunItem} 清成 null，导致
 * {@code shoot} 直接返回 {@code NOT_DRAW}）。</p>
 */
public class TravelToDimensionEvent {
    /**
     * 非玩家生物（如持枪僵尸）跨维度：实体是<b>复制</b>到目标世界的，
     * 所以要对 {@code newEntity} 而非 {@code originalEntity} 操作。
     */
    public static void onTravelToDimension(Entity originalEntity, Entity newEntity, ServerLevel origin, ServerLevel destination) {
        if (newEntity instanceof LivingEntity livingEntity && livingEntity.getMainHandItem().getItem() instanceof IGun) {
            IGunOperator.fromLivingEntity(livingEntity).initialData();
        }
    }

    /**
     * 玩家跨维度。玩家是<b>被物理移动</b>过去的（同一个 {@code ServerPlayer} 实例），
     * 不会触发上面那个事件，必须挂在 {@code AFTER_PLAYER_CHANGE_LEVEL} 上。
     *
     * <p>注意：玩家在<b>异维度重生</b>时也会走到这里，此时传入的是
     * 重生后的新 {@code ServerPlayer}（见 Fabric javadoc）。对这种情况执行
     * {@code initialData()} 同样是正确的 —— 新玩家本就需要初始化。</p>
     */
    public static void onPlayerTravelToDimension(ServerPlayer player, ServerLevel origin, ServerLevel destination) {
        if (player.getMainHandItem().getItem() instanceof IGun) {
            IGunOperator.fromLivingEntity(player).initialData();
        }
    }
}
