package me.xjqsh.lrtactical.api.item;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.item.index.ConsumableIndex;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/** 数据驱动消耗品。 */
public interface IConsumable extends ICustomItem {
    String ID_TAG = "ConsumableId";
    String OVERRIDE_DISPLAY_ID = "DisplayId";
    Identifier EMPTY = Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "empty");

    @Nullable
    static IConsumable of(ItemStack stack) {
        return stack.getItem() instanceof IConsumable item ? item : null;
    }

    @Override
    default Identifier getId(ItemStack stack) {
        return readId(stack, ID_TAG).orElse(EMPTY);
    }

    @Override
    default Identifier getDisplayId(ItemStack stack) {
        return readId(stack, OVERRIDE_DISPLAY_ID).orElseGet(() -> getId(stack));
    }

    private static Optional<Identifier> readId(ItemStack stack, String key) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return Optional.empty();
        }
        CompoundTag nbt = customData.copyTag();
        String raw = nbt.getStringOr(key, "");
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(Identifier.tryParse(raw));
    }

    @Override
    default void setId(ItemStack stack, Identifier id) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> nbt.putString(ID_TAG, id.toString()));
        applyComponents(stack, true);
    }

    static void applyComponents(ItemStack stack, boolean freshStack) {
        me.xjqsh.lrtactical.api.LrTacticalAPI.getConsumableIndex(stack).ifPresent(index ->
                index.applyDataComponents(stack, freshStack));
    }

    @Override
    default boolean isSame(ItemStack i, ItemStack j) {
        IConsumable a = IConsumable.of(i);
        IConsumable b = IConsumable.of(j);
        if (a != null && b != null) {
            return Objects.equals(a.getId(i), b.getId(j));
        }
        if (i.isEmpty() || j.isEmpty()) {
            return i.isEmpty() && j.isEmpty();
        }
        return false;
    }

    @Override
    default Optional<Identifier> getCoolDownId(ItemStack stack) {
        return getConsumableIndex(stack).map(index -> index.getData().getCooldownCategory());
    }

    @Override
    default int getMaxUsingTick(ItemStack stack) {
        return getConsumableIndex(stack).map(index -> index.getData().getUseDuration()).orElse(0);
    }

    default Optional<ConsumableIndex> getConsumableIndex(ItemStack stack) {
        return me.xjqsh.lrtactical.api.LrTacticalAPI.getConsumableIndex(stack);
    }
}
