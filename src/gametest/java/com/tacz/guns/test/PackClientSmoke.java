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
        var viewer = System.getProperty("tacz.test.viewer");
        if (viewer != null) { ViewerSmoke.run(context, viewer); return; }
        var id = Identifier.parse("tacz:ak47");
        try (var world = context.worldBuilder().create()) {
            context.waitFor(mc -> CommonNetworkCache.INSTANCE.getGunIndex(id) != null && TimelessAPI.getClientGunIndex(id).isPresent());
            context.runOnClient(TargetPoseSmoke::run);
            context.runOnClient(mc -> DefaultClientCatalogSmoke.client());
            RackPreviewSmoke.equip(world, com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(id).setAmmoCount(3).build());
            world.getClientLevel().waitForChunksRender();
            ViewTuningSmoke.run(context, world);
            RackPreviewSmoke.run(context, world, id);
            ConversionReloadSmoke.run(context, world);
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
}
