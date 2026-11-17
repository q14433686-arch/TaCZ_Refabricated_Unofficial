package com.tacz.guns.test;

import com.tacz.guns.api.TimelessAPI;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class PackServerSmoke {
    @GameTest
    public void loadPacks(GameTestHelper helper) throws IOException {
        var resources = helper.getLevel().getServer().getResourceManager();
        try (var stream = resources.open(Identifier.parse("port_test:probe/version.txt"))) {
            helper.assertTrue(new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim().equals("26.3 overlay"),
                    "Server data came from the wrong gun pack layer");
        }
        helper.assertTrue(TimelessAPI.getCommonGunIndex(Identifier.parse("tacz:ak47")).isPresent(), "Default gun index missing");
        helper.succeed();
    }
}
