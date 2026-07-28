package me.xjqsh.lrtactical.item;

import me.xjqsh.lrtactical.entity.GrenadeEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 遥控起爆器。
 *
 * <p>本移植采用“玩家归属”模型：一个玩家只需要一个起爆器，右键时引爆该玩家当前加载中的
 * 所有 {@code remote_detonation=true} C4/遥控雷。这样不会误爆其他玩家的 C4，也不会像上游
 * 那样每扔一个 C4 就生成一个一次性、绑定单实体的起爆器。</p>
 *
 * <h2>兼容旧绑定栈</h2>
 * 早期移植曾把实体 UUID 写入 detonator。若背包里还有这种旧起爆器，在找不到任何“本玩家遥控雷”时，
 * 仍会尝试按旧 UUID 引爆一次；新生成的起爆器不再写该字段，也不会使用后消失。
 */
public class DetonatorItem extends Item {
    private static final String LINKED_ENTITY = "linked_entity";

    public DetonatorItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** @deprecated 新逻辑不再绑定单个实体；保留用于兼容旧栈。 */
    @Deprecated
    public void recordEntity(Entity entity, ItemStack detonatorStack) {
        UUID entityId = entity.getUUID();
        CustomData.update(DataComponents.CUSTOM_DATA, detonatorStack,
                nbt -> nbt.putString(LINKED_ENTITY, entityId.toString()));
    }

    @Nullable
    public UUID getLinkedEntityId(ItemStack detonatorStack) {
        CustomData customData = detonatorStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        CompoundTag nbt = customData.copyTag();
        String raw = nbt.getStringOr(LINKED_ENTITY, "");
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 引爆该玩家所有当前加载中的遥控雷。
     *
     * <p>不直接在遍历中 {@code onDeath}：爆炸会修改实体列表；先收集再引爆，避免遍历期间结构变化。</p>
     */
    public int detonateOwned(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return 0;
        }
        UUID ownerId = player.getUUID();
        List<GrenadeEntity> targets = new ArrayList<>();
        for (Entity entity : serverLevel.getAllEntities()) {
            if (!(entity instanceof GrenadeEntity grenade) || !grenade.isRemoteDetonation() || !grenade.isAlive()) {
                continue;
            }
            Entity owner = grenade.getOwner();
            if (owner != null && owner.getUUID().equals(ownerId)) {
                targets.add(grenade);
            }
        }
        for (GrenadeEntity grenade : targets) {
            grenade.onDeath(null);
        }
        return targets.size();
    }

    /** 旧版“绑定单个实体”的兼容路径。 */
    public boolean detonateLinked(ItemStack detonatorStack, Entity detonator) {
        UUID linkedId = getLinkedEntityId(detonatorStack);
        if (linkedId == null) {
            return false;
        }
        if (detonator.level() instanceof ServerLevel serverLevel
                && serverLevel.getEntity(linkedId) instanceof GrenadeEntity grenadeEntity) {
            grenadeEntity.onDeath(null);
            return true;
        }
        return false;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack detonatorStack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            // 客户端只做乐观预测；服务端会真正判断并发 actionbar。
            return InteractionResult.SUCCESS;
        }

        int count = detonateOwned(player);
        if (count <= 0 && detonateLinked(detonatorStack, player)) {
            count = 1;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            if (count > 0) {
                serverPlayer.sendSystemMessage(Component.literal("BOOM! x" + count).withStyle(ChatFormatting.RED), true);
            } else {
                serverPlayer.sendSystemMessage(Component.literal("- NO SIGNAL -").withStyle(ChatFormatting.RED), true);
            }
        }
        return count > 0 ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
