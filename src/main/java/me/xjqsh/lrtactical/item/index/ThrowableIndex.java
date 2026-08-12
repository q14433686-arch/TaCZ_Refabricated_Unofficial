package me.xjqsh.lrtactical.item.index;

import com.google.gson.JsonElement;
import me.xjqsh.lrtactical.api.index.ICustomItemIndex;
import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.entity.ThrowableItemEntity;
import me.xjqsh.lrtactical.item.throwable.ThrowableData;
import me.xjqsh.lrtactical.item.throwable.ThrowableType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 一种具体投掷物的索引（如「M67 破片手雷」）。
 *
 * <p>由数据包 {@code data/<ns>/index/throwable/<name>.json} 反序列化而来，
 * 持有该投掷物的类型、配置数据与基础物品，并负责按需创建物品堆与实体。
 *
 * <p>26.2 变更：{@code ResourceLocation} → {@link Identifier}，其余与上游一致。
 */
public class ThrowableIndex<T extends ThrowableData, E extends ThrowableItemEntity> implements ICustomItemIndex {
    private final ThrowableType<T, E> type;
    private final Item baseItem;
    private final T data;
    private final Identifier id;
    private final String name;
    private final @Nullable String tooltip;

    private ThrowableIndex(@NotNull ThrowableType<T, E> type, T data,
                           String name, @Nullable String tooltip, Identifier id, Item baseItem) {
        this.type = type;
        this.data = data;
        this.id = id;
        this.baseItem = baseItem;
        this.name = name;
        this.tooltip = tooltip;
    }

    @Nullable
    public static <T extends ThrowableData, E extends ThrowableItemEntity> ThrowableIndex<T, E> deserialize(
            @NotNull ThrowableType<T, E> type, JsonElement data, String name,
            @Nullable String tooltip, Identifier id, Item baseItem
    ) {
        T throwableData = type.serializer().parse(data);
        if (throwableData == null) {
            return null;
        }
        return new ThrowableIndex<>(type, throwableData, name, tooltip, id, baseItem);
    }

    public T getData() {
        return data;
    }

    @Nullable
    public String getTooltip() {
        return tooltip;
    }

    @Override
    public int getMaxStackSize() {
        return data.getStackSize();
    }

    public ThrowableType<T, E> getType() {
        return type;
    }

    @Override
    public String getDescriptionId() {
        return name;
    }

    public E createEntity(ItemStack stack, LivingEntity thrower) {
        return type.factory().create(stack, thrower, data);
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Item getBaseItem() {
        return baseItem;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack stack = new ItemStack(baseItem);
        if (stack.getItem() instanceof IThrowable iThrowable) {
            iThrowable.setId(stack, this.getId());
        }
        return stack;
    }
}
