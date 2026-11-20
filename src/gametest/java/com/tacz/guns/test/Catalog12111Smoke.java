package com.tacz.guns.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class Catalog12111Smoke {
    @GameTest
    public void loadEntireDefaultCatalog(GameTestHelper test) {
        DefaultCatalogSmoke.server(test.getLevel().getServer());
        test.succeed();
    }
}
