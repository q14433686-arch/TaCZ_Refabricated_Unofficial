package com.tacz.guns.command.sub;

import cn.sh1rocu.tacz.util.forge.EnumArgument;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tacz.guns.config.ConfigPersist;
import com.tacz.guns.config.sync.SyncConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ConfigCommand {
    private static final String CONFIG_NAME = "config";
    private static final String KEY = "key";
    private static final String ENABLE = "state";

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        var config = Commands.literal(CONFIG_NAME);
        var configKey = Commands.argument(KEY, EnumArgument.enumArgument(ConfigKey.class));
        var state = Commands.argument(ENABLE, BoolArgumentType.bool());
        return config.then(configKey.then(state.executes(ConfigCommand::setConfig)));
    }

    private static int setConfig(CommandContext<CommandSourceStack> context) {
        ConfigKey key = context.getArgument(KEY, ConfigKey.class);
        boolean state = BoolArgumentType.getBool(context, ENABLE);

        if (key == null) {
            return 0;
        }
        switch (key) {
            case defaultTableLimit -> SyncConfig.ENABLE_TABLE_FILTER.set(state);
            case serverShootNetworkCheck -> SyncConfig.SERVER_SHOOT_NETWORK_V.set(state);
            case serverShootCooldownCheck -> SyncConfig.SERVER_SHOOT_COOLDOWN_V.set(state);
        }
        // 这里**不**调 ConfigPersist.saveAll()：这三条键都在 SERVER spec 里（ServerConfig.init →
        // SyncConfig），而 SERVER 配置的落盘与"首次进世界时拷贝到 <world>/serverconfigs/"由 FCAP 自己管。
        // ConfigPersist 只钉了 client/common 两个文件名，此处调用看着像"顺手保存"，实际对本命令毫无作用，
        // 而且一旦去猜 SERVER 的路径就会把副本写到错的位置 —— 所以宁可不做，也不留假动作。
        // 面板能编辑的 client/common 那批才走 ConfigPersist（见其 javadoc 的适用范围）。
        context.getSource().sendSystemMessage(Component.translatable(key.lang + "." + (state ? "enabled" : "disabled")));

        return Command.SINGLE_SUCCESS;
    }

    public enum ConfigKey {
        defaultTableLimit("commands.tacz.config.default_table_limit"),
        serverShootNetworkCheck("commands.tacz.config.server_shoot_network_check"),
        serverShootCooldownCheck("commands.tacz.config.server_shoot_cooldown_check"),
        ;

        public final String lang;

        ConfigKey(String lang) {
            this.lang = lang;
        }
    }
}
