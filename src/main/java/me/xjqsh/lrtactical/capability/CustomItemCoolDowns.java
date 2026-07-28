package me.xjqsh.lrtactical.capability;

import me.xjqsh.lrtactical.EquipmentMod;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 原版物品冷却的翻版，但 key 是 {@link Identifier} 而非 {@code Item}。
 *
 * <p>为什么需要它：LRTactical 的所有手雷共用同一个物品
 * （{@code lrtactical:throwable}），具体是哪种由数据决定。
 * 原版 {@code ItemCooldowns} 按 {@code Item} 记冷却，
 * 会导致「扔了 M67 之后所有手雷一起进冷却」。
 *
 * <h2>26.2 移植改动</h2>
 * <ul>
 *   <li>{@code ResourceLocation} → {@link Identifier}（26.2 类名变更，全仓统一）；</li>
 *   <li>暂未接入网络同步（原版在 {@code onCooldownStarted/Ended} 里发包给客户端）。
 *       网络层尚未移植，此处留 TODO 而<b>不是静默删掉</b> ——
 *       缺了它的后果是「客户端冷却遮罩不显示」，属于可感知的功能缺失，
 *       必须显式记录，避免日后被当成新 bug 重查。</li>
 * </ul>
 */
public class CustomItemCoolDowns {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "custom_cooldown");

    private final Player player;
    public final Map<Identifier, CooldownInstance> cooldowns = new HashMap<>();
    private int tickCount;

    public CustomItemCoolDowns(Player player) {
        this.player = player;
    }

    public boolean isOnCooldown(Identifier id) {
        return this.getCooldownPercent(id, 0.0F) > 0.0F;
    }

    public float getCooldownPercent(Identifier id, float partialTicks) {
        CooldownInstance instance = this.cooldowns.get(id);
        if (instance == null) {
            return 0.0F;
        }
        float total = (float) (instance.endTime - instance.startTime);
        float remaining = (float) instance.endTime - ((float) this.tickCount + partialTicks);
        return Mth.clamp(remaining / total, 0.0F, 1.0F);
    }

    public void tick() {
        ++this.tickCount;
        if (this.cooldowns.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Identifier, CooldownInstance>> iterator = this.cooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Identifier, CooldownInstance> entry = iterator.next();
            if (entry.getValue().endTime <= this.tickCount) {
                iterator.remove();
                this.onCooldownEnded(entry.getKey());
            }
        }
    }

    public void addCooldown(Identifier id, int ticks) {
        this.cooldowns.put(id, new CooldownInstance(this.tickCount, this.tickCount + ticks));
        this.onCooldownStarted(id, ticks);
    }

    public void removeCooldown(Identifier id) {
        this.cooldowns.remove(id);
        this.onCooldownEnded(id);
    }

    // TODO(网络层): 上游会在此处向客户端发 SCustomCoolDownMessage 同步冷却。
    //  网络层尚未移植，暂为空实现。缺失后果：客户端物品栏不显示冷却遮罩
    //  （服务端判定仍然正确，不会出现「冷却期间还能用」的逻辑漏洞）。
    protected void onCooldownStarted(Identifier id, int ticks) {
    }

    protected void onCooldownEnded(Identifier id) {
    }

    public Player getPlayer() {
        return player;
    }

    public static class CooldownInstance {
        final int startTime;
        final int endTime;

        CooldownInstance(int startTime, int endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
}
