package cn.sh1rocu.tacz.util.forge;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 26.2 重构版 - {@link ArgumentTypeInfo} API 适配
 * <p>
 * 26.1+ Mojang 重构:
 * <ul>
 *   <li>{@code ArgumentTypeInfo.Template} 改为独立泛型, 之前是 {@code ArgumentTypeInfo<T>.Template} 内部类</li>
 *   <li>新签名: {@code ArgumentTypeInfo<A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>>}</li>
 *   <li>Template 现在是 {@code ArgumentTypeInfo.Template<A>}, 提供 {@code instantiate(CommandBuildContext)}</li>
 * </ul>
 */
public class EnumArgument<T extends Enum<T>> implements ArgumentType<T> {
    private static final Dynamic2CommandExceptionType INVALID_ENUM = new Dynamic2CommandExceptionType(
            (found, constants) -> Component.translatable("commands.tacz.arguments.enum.invalid", constants, found));
    private final Class<T> enumClass;

    public static <R extends Enum<R>> EnumArgument<R> enumArgument(Class<R> enumClass) {
        return new EnumArgument<>(enumClass);
    }

    private EnumArgument(final Class<T> enumClass) {
        this.enumClass = enumClass;
    }

    public Class<T> getEnumClass() {
        return enumClass;
    }

    @Override
    public T parse(final StringReader reader) throws CommandSyntaxException {
        String name = reader.readUnquotedString();
        try {
            return Enum.valueOf(enumClass, name);
        } catch (IllegalArgumentException e) {
            throw INVALID_ENUM.createWithContext(reader, name,
                    Arrays.toString(Arrays.stream(enumClass.getEnumConstants()).map(Enum::name).toArray()));
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context, final SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Stream.of(enumClass.getEnumConstants()).map(Enum::name), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return Stream.of(enumClass.getEnumConstants()).map(Enum::name).collect(Collectors.toList());
    }

    /**
     * 26.2 ArgumentTypeInfo 实现
     * <p>
     * 签名: {@code ArgumentTypeInfo<A, T>} 其中:
     * <ul>
     *   <li>A = EnumArgument (具体类, 但因为泛型擦除, 实际使用时 cast)</li>
     *   <li>T = Info.Template (具体内部类)</li>
     * </ul>
     */
    public static class Info implements ArgumentTypeInfo<EnumArgument<?>, Info.Template> {
        public static final Info INSTANCE = new Info();

        @Override
        public void serializeToNetwork(Template template, FriendlyByteBuf buffer) {
            buffer.writeUtf(template.enumClass.getName());
        }

        @SuppressWarnings("unchecked")
        @Override
        public Template deserializeFromNetwork(FriendlyByteBuf buffer) {
            try {
                String name = buffer.readUtf();
                return new Template((Class<? extends Enum<?>>) Class.forName(name));
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        @Override
        public void serializeToJson(Template template, JsonObject json) {
            json.addProperty("enum", template.enumClass.getName());
        }

        @SuppressWarnings("unchecked")
        @Override
        public Template unpack(EnumArgument<?> argument) {
            return new Template(argument.getEnumClass());
        }

        /**
         * 26.2 Template 类 - 独立泛型, 实现 {@code ArgumentTypeInfo.Template<EnumArgument<?>>}
         */
        public static class Template implements ArgumentTypeInfo.Template<EnumArgument<?>> {
            final Class<? extends Enum<?>> enumClass;

            Template(Class<? extends Enum<?>> enumClass) {
                this.enumClass = enumClass;
            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public EnumArgument<?> instantiate(CommandBuildContext pStructure) {
                return new EnumArgument(enumClass);
            }

            @Override
            public ArgumentTypeInfo<EnumArgument<?>, ?> type() {
                return INSTANCE;
            }
        }
    }
}
