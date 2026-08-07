package com.tacz.guns.entity.shooter;

import cn.sh1rocu.tacz.api.LogicalSide;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunDrawEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.industry.maintenance.IndustryMaintenanceService;
import com.tacz.guns.industry.magazine.EnBlocClipService;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.event.ServerMessageGunDraw;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Supplier;

public class LivingEntityDrawGun {
    private final LivingEntity shooter;
    private final ShooterDataHolder data;

    public LivingEntityDrawGun(LivingEntity shooter, ShooterDataHolder data) {
        this.shooter = shooter;
        this.data = data;
    }

    public void draw(Supplier<ItemStack> gunItemSupplier) {
        // A final-shot ejection normally happens in the fire transaction. This
        // is only a safe server-side recovery point for an already-empty clip
        // from an older stack or an interrupted tick: never let a put-away
        // animation be the only apparent "ejection" while the physical clip
        // remains hidden in the gun NBT.
        ItemStack lastItem = data.currentGunItem == null ? ItemStack.EMPTY : data.currentGunItem.get();
        EnBlocClipService.ejectIfEmpty(shooter, lastItem);
        // First server-side handling of a real industrial gun is the safe
        // migration point: old stacks start full/clean, never pre-worn.
        IndustryMaintenanceService.migrateIfEligible(lastItem);
        IndustryMaintenanceService.migrateIfEligible(gunItemSupplier.get());

        // 重置各个状态
        data.initialData();

        // 更新切枪时间戳
        if (data.drawTimestamp == -1) {
            data.drawTimestamp = System.currentTimeMillis();
        }
        if (data.heatTimestamp == -1) {
            data.heatTimestamp = System.currentTimeMillis();
        }
        long drawTime = System.currentTimeMillis() - data.drawTimestamp;
        if (drawTime >= 0) {
            // 如果不处于收枪状态，则需要计算收枪时长
            if (drawTime < data.currentPutAwayTimeS * 1000) {
                // 从开始切枪到现在，抬枪的时间小于收枪需要的时间，则按抬枪时间计算。
                data.drawTimestamp = System.currentTimeMillis() + drawTime;
            } else {
                // 从开始切枪到现在，抬枪的时间大于收枪需要的时间，则按收枪时间计算。
                data.drawTimestamp = System.currentTimeMillis() + (long) (data.currentPutAwayTimeS * 1000);
            }
        }
        GunDrawEvent.CALLBACK.invoker().post(new GunDrawEvent(shooter, lastItem, gunItemSupplier.get(), LogicalSide.SERVER));
        NetworkHandler.sendToTrackingEntity(new ServerMessageGunDraw(shooter.getId(), lastItem, gunItemSupplier.get()), shooter);
        data.currentGunItem = gunItemSupplier;
        // 刷新配件数据
        AttachmentPropertyManager.postChangeEvent(shooter, gunItemSupplier.get());
        updatePutAwayTime();
    }

    /**
     * 切枪冷却。<b>服务端的 shoot / reload 都以 {@code != 0} 为门禁</b>
     * （{@code LivingEntityShoot#shoot} L93、{@code LivingEntityReload#reload} L52）。
     *
     * <h2>【排查记录】跨维度后 2-4 秒不能开枪/换弹，真因在这里</h2>
     *
     * <p>用户实测：跨维度后短时间内<b>换弹完全按不动、开枪打了没效果</b>；
     * 该现象早于本移植的任何改动就存在（已确认）。日志（5 次切换）显示
     * 切换后首发子弹延迟 2~4 秒，且弹号跳变 285~514
     * —— 即服务端确实在生成实体、客户端却没渲染。</p>
     *
     * <p>两个返回值都会让上面那个 {@code != 0} 成立，从而静默拒绝一切操作：</p>
     * <ul>
     *   <li><b>返回 -1</b>：{@code getCommonGunIndex} 查不到索引时（{@code orElse(-1L)}）。
     *       跨维度瞬间若索引尚未就绪即命中。注意 {@code != 0} 对 -1 成立，
     *       这与「冷却剩余 -1 毫秒＝已结束」的直觉相反。</li>
     *   <li><b>返回正数</b>：{@code draw()} 会把 {@code drawTimestamp} 推到
     *       {@code now + putAwayTime}，于是本值 = {@code drawTime + putAwayTime}
     *       （默认枪包中位数 0.33+0.30≈0.6 秒）。跨维度时客户端换新
     *       {@code LocalPlayer}，{@code InventoryEvent} 里
     *       {@code ItemStack.matches(oldHotbarSelectItem, currentItem)} 因
     *       比较的是<b>跨实例的两个 ItemStack</b> 而可能不成立，
     *       从而触发一次多余的 draw。</li>
     * </ul>
     *
     * <p><b>尚未修复。</b>动手前需要先实测确认命中的是哪一条
     * （加日志打印本方法的返回值与 {@code data.currentGunItem} 是否为 null），
     * 不要凭推理直接改 —— 此处已有过两次基于错误前提的修改，
     * 均引入了新的回归。</p>
     */
    public long getDrawCoolDown() {
        if (data.currentGunItem == null) {
            return 0;
        }
        ItemStack currentGunItem = data.currentGunItem.get();
        if (!(currentGunItem.getItem() instanceof IGun iGun)) {
            return 0;
        }
        Identifier gunId = iGun.getGunId(currentGunItem);
        Optional<CommonGunIndex> gunIndex = TimelessAPI.getCommonGunIndex(gunId);
        return gunIndex.map(index -> {
            long coolDown = (long) (index.getGunData().getDrawTime() * 1000) - (System.currentTimeMillis() - data.drawTimestamp);
            // 给 5 ms 的窗口时间，以平衡延迟
            coolDown = coolDown - 5;
            if (coolDown < 0) {
                return 0L;
            }
            return coolDown;
        }).orElse(-1L);
    }

    private void updatePutAwayTime() {
        ItemStack gunItem = data.currentGunItem == null ? ItemStack.EMPTY : data.currentGunItem.get();
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun != null) {
            Optional<CommonGunIndex> gunIndex = TimelessAPI.getCommonGunIndex(iGun.getGunId(gunItem));
            data.currentPutAwayTimeS = gunIndex.map(index -> index.getGunData().getPutAwayTime()).orElse(0F);
        } else {
            data.currentPutAwayTimeS = 0;
        }
    }
}
