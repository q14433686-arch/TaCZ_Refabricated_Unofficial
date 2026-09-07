package com.tacz.guns.init;

import cn.sh1rocu.tacz.api.event.AddPackFindersEvent;
import com.tacz.guns.entity.sync.ModSyncedEntityData;
import com.tacz.guns.network.HandshakeNetworking;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.resource.GunPackLoader;

public final class CommonRegistry {
    private static boolean LOAD_COMPLETE = false;

    public static void onSetupEvent() {
        AddPackFindersEvent.CALLBACK.register(CommonRegistry::onAddPackFinders);
        NetworkHandler.registerC2SPackets();
        HandshakeNetworking.init();
        ModSyncedEntityData.init();
    }

    public static void onLoadComplete() {
        LOAD_COMPLETE = true;
    }

    public static boolean isLoadComplete() {
        return LOAD_COMPLETE;
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        // 26.2 移植（810fb04f）：同一个 JVM 里 PackRepository 会分两次、以不同的
        // PackType 各查一次 finders（单人模式：Minecraft 客户端构造 CLIENT_RESOURCES
        // 仓库、集成服务器构造 SERVER_DATA 仓库）。此前 packType 只在 setup() 时写死
        // 一次（单人模式恒为 CLIENT_RESOURCES），服务器仓库收到的枪包类型不匹配，
        // 集成服务器读不到任何枪包的 data/（gun_index、blocks、table 配方、过滤配置）——
        // 表现为枪包里的其他工作台 GUI 无标签页/无配方、JEI 无对应分类、工作台里合成
        // 不出结果，而工作台物品本身的原版合成配方（在 mod 自身 data 里）不受影响。
        GunPackLoader.INSTANCE.packType = event.getPackType();
        event.addRepositorySource(GunPackLoader.INSTANCE);
    }
}
