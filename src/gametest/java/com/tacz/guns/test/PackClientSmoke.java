package com.tacz.guns.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.resource.network.CommonNetworkCache;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;

public final class PackClientSmoke implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var id = Identifier.parse("tacz:ak47");
        try (var world = context.worldBuilder().create()) {
            context.waitFor(mc -> CommonNetworkCache.INSTANCE.getGunIndex(id) != null && TimelessAPI.getClientGunIndex(id).isPresent());
            context.runOnClient(mc -> DefaultClientCatalogSmoke.client());
            ConversionReloadSmoke.run(context, world);
            context.runOnClient(mc -> DefaultClientCatalogSmoke.client());
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
}
