package me.xjqsh.lrtactical.item.throwable;

import com.google.gson.JsonElement;
import me.xjqsh.lrtactical.entity.ThrowableItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 「投掷物类型」—— 把 <b>如何解析配置</b> 与 <b>如何造实体</b> 绑在一起。
 *
 * <p>这是本模组数据驱动的核心：数据包里写 {@code "type": "lrtactical:explode"}，
 * 加载器据此取出对应的 {@code ThrowableType}，用它的 {@code serializer}
 * 解析 {@code data} 段，再用它的 {@code factory} 在投掷时创建实体。
 *
 * <p>类型本身注册在 {@link me.xjqsh.lrtactical.init.ModRegistries} 里
 * （Fabric 上是普通 Map，理由见该类注释）。
 *
 * <p>本类与上游逐字对应，仅包名内的类型引用随之调整。
 */
public record ThrowableType<T extends ThrowableData, E extends ThrowableItemEntity>(
        ThrowableType.ThrowableFactory<T, E> factory,
        ThrowableType.ThrowableDataSerializer<T> serializer
) {

    @FunctionalInterface
    public interface ThrowableFactory<T extends ThrowableData, E extends ThrowableItemEntity> {
        E create(ItemStack stack, LivingEntity thrower, T data);
    }

    @FunctionalInterface
    public interface ThrowableDataSerializer<T extends ThrowableData> {
        T parse(JsonElement json);
    }

    public static class Builder<T extends ThrowableData, E extends ThrowableItemEntity> {
        private ThrowableFactory<T, E> factory;
        private ThrowableDataSerializer<T> serializer;

        public static <T extends ThrowableData, E extends ThrowableItemEntity> Builder<T, E> of() {
            return new Builder<>();
        }

        public Builder<T, E> setFactory(ThrowableFactory<T, E> factory) {
            this.factory = factory;
            return this;
        }

        public Builder<T, E> setSerializer(ThrowableDataSerializer<T> serializer) {
            this.serializer = serializer;
            return this;
        }

        public ThrowableType<T, E> build() {
            return new ThrowableType<>(factory, serializer);
        }
    }
}
