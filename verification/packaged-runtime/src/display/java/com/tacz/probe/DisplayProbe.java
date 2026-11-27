package com.tacz.probe;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.resources.Identifier;

public final class DisplayProbe extends PlayerProbe {
    @Override void exercise(ClientGameTestContext ctx, TestSingleplayerContext world) {
        ctx.runOnClient(mc -> mc.options.hideGui = false);
        HintInk.calibrate(ctx);
        RackPreviewSmoke.run(ctx, world, Identifier.parse("tacz:ak47"));
    }
}
