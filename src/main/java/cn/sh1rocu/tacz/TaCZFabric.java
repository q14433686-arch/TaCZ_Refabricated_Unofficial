package cn.sh1rocu.tacz;

import cn.sh1rocu.tacz.api.event.*;
import cn.sh1rocu.tacz.util.forge.EnumArgument;
import cn.sh1rocu.tacz.util.forge.PartialNBTIngredient;
import cn.sh1rocu.tacz.util.forge.StrictNBTIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.config.ClientConfig;
import com.tacz.guns.config.ConfigPersist;
import com.tacz.guns.config.CommonConfig;
import com.tacz.guns.config.PreLoadConfig;
import com.tacz.guns.config.ServerConfig;
import com.tacz.guns.event.*;
import com.tacz.guns.event.ammo.BellRing;
import com.tacz.guns.event.ammo.DestroyGlassBlock;
import com.tacz.guns.init.CapabilityRegistry;
import com.tacz.guns.init.CommandRegistry;
import com.tacz.guns.init.CommonRegistry;
import com.tacz.guns.init.CompatRegistry;
import com.tacz.guns.resource.CommonAssetsManager;
import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;

public class TaCZFabric implements ModInitializer {
    public static final Identifier HIGHEST = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "event_highest_priority");
    public static final Identifier HIGH = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "event_high_priority");
    public static final Identifier LOW = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "event_low_priority");
    public static final Identifier LOWEST = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "event_lowest_priority");

    @Nullable
    private static WeakReference<MinecraftServer> server;

    @Nullable
    public static MinecraftServer getServer() {
        if (server == null) {
            return null;
        }
        return server.get();
    }

    @Override
    public void onInitialize() {
        // 确保配置文件加载，这个阶段将比标准的forge配置文件加载早
        PreLoadConfig.init();

        // spec 引用交给 ConfigPersist：FCAP v26.1.5 的 ConfigValue.set 只改内存、
        // ForgeConfigSpec.save() 在新架构下是 no-op，Cloth 保存后的落盘由
        // ConfigPersist.saveAll() 显式写回 TOML 闭合（根因见该类 javadoc）。
        // 文件名用 Forge 惯例 <modid>-<type>.toml 显式钉死（与 FCAP 默认命名一致）。
        ForgeConfigSpec commonSpec = CommonConfig.init();
        ConfigRegistry.INSTANCE.register(GunMod.MOD_ID, ModConfig.Type.COMMON, commonSpec,
                ConfigPersist.record(ModConfig.Type.COMMON, commonSpec));
        ConfigRegistry.INSTANCE.register(GunMod.MOD_ID, ModConfig.Type.SERVER, ServerConfig.init());
        ForgeConfigSpec clientSpec = ClientConfig.init();
        ConfigRegistry.INSTANCE.register(GunMod.MOD_ID, ModConfig.Type.CLIENT, clientSpec,
                ConfigPersist.record(ModConfig.Type.CLIENT, clientSpec));

        GunMod.setup();

        // 附属模块 LRTactical（非官方 26.2 移植）的注册入口。
        //
        // 必须显式调用：Fabric 无 DeferredRegister 自动注册机制，
        // 注册动作写在各 ModXxx 的静态字段里，而 Java 类加载是惰性的 ——
        // 没有调用方，这些类永不加载，物品/实体也就永不注册。
        me.xjqsh.lrtactical.EquipmentMod.init();

        CommandRegistry.onServerStaring();
        CompatRegistry.onEnqueue();
        // 注册 Forge 遗留的自定义 Ingredient 类型。
        //
        // 这两个类一直躺在 util/forge 下但【从未被注册过】—— 上游 1.21.1 在本方法
        // 对应位置有一行 CustomIngredientSerializer.register(NBTIngredient.Serializer.INSTANCE)，
        // 移植时漏掉了。没注册的后果不是报错，而是 Fabric 的 CustomIngredientImpl.CODEC
        // 在 REGISTERED_SERIALIZERS 里查不到该 id、返回
        // "Unknown custom ingredient serializer" -> 整条配方解析失败。
        //
        // 实测症状：第三方包里用 forge:partial_nbt 写的 2 条配方
        //（「迈卡的佩枪」= 2 把柯尔特 M1892、「m1887_hc」= m1887 + 斧头）
        // 材料格空白且无法合成。
        CustomIngredientSerializer.register(PartialNBTIngredient.Serializer.INSTANCE);
        CustomIngredientSerializer.register(StrictNBTIngredient.Serializer.INSTANCE);

        Class<? extends EnumArgument<?>> enumArgumentClass = (Class<? extends EnumArgument<?>>) (Class) EnumArgument.class;
        ArgumentTypeRegistry.registerArgumentType(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "enum_argument"), enumArgumentClass,
                EnumArgument.Info.INSTANCE);
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            CommonLoadPack.loadGunPack();
        }
        ServerLifecycleEvents.SERVER_STARTING.register((server) -> TaCZFabric.server = new WeakReference<>(server));

        subscribeEvents();
    }

    private void subscribeEvents() {
        CapabilityRegistry.init();

        AddReloadListenerEvent.CALLBACK.register(CommonAssetsManager::onReload);
        CommonLifecycleEvents.TAGS_LOADED.register(CommonAssetsManager::onReload);
        ServerLifecycleEvents.SERVER_STOPPED.register(CommonAssetsManager::onServerStopped);
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register(CommonAssetsManager::OnDatapackSync);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> CommonRegistry.onLoadComplete());

        AmmoHitBlockEvent.CALLBACK.register(BellRing::onAmmoHitBlock);

        AmmoHitBlockEvent.CALLBACK.register(DestroyGlassBlock::onAmmoHitBlock);

        LivingHurtEvent.CALLBACK.register(LOW, EntityDamageEvent::onLivingHurt);

        PlayerTickEvent.END.register(HitboxHelperEvent::onPlayerTick);
        PlayerEvent.LOGGED_OUT.register(HitboxHelperEvent::onPlayerLoggedOut);

        LivingKnockBackEvent.CALLBACK.register(KnockbackChange::onKnockback);

        ModConfigEvents.loading(GunMod.MOD_ID).register(LoadingConfigEvent::onLoadingConfig);
        ModConfigEvents.reloading(GunMod.MOD_ID).register(LoadingConfigEvent::onReloadingConfig);

        ServerPlayerEvents.AFTER_RESPAWN.register(PlayerRespawnEvent::onPlayerRespawn);

        AttackBlockCallback.EVENT.register(PreventGunClick::onLeftClickBlock);

        ServerTickEvents.START_SERVER_TICK.register(ServerTickEvent::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(ServerTickEvent::onServerTick);

        EntityJoinLevelEvent.CALLBACK.register(SyncBaseTimestamp::onPlayerJoinWorld);

        EntityTrackingEvents.START_TRACKING.register(SyncedEntityDataEvent::onStartTracking);
        EntityJoinLevelEvent.CALLBACK.register(SyncedEntityDataEvent::onPlayerJoinWorld);
        ServerPlayerEvents.COPY_FROM.register(SyncedEntityDataEvent::onPlayerClone);
        ServerTickEvents.END_SERVER_TICK.register(SyncedEntityDataEvent::onServerTick);

        // 非玩家生物跨维度（持枪僵尸等）。
        ServerEntityLevelChangeEvents.AFTER_ENTITY_CHANGE_LEVEL.register(TravelToDimensionEvent::onTravelToDimension);
        // 玩家跨维度。【必须单独注册】上面那个事件的 javadoc 明写
        // "does not apply to the ServerPlayer"，玩家是被物理移动过去的，
        // 从不触发 AFTER_ENTITY_CHANGE_LEVEL。缺了这一行，
        // 服务端的枪械状态在跨维度后永远不会复位，
        // 表现为「换弹动作连贯但子弹不变」。详见 TravelToDimensionEvent 的类注释。
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(TravelToDimensionEvent::onPlayerTravelToDimension);
    }
}
