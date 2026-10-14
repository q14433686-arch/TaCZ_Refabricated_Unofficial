package com.tacz.guns.client.init;

import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.block.entity.StatueBlockEntity;
import com.tacz.guns.block.entity.TargetBlockEntity;
import com.tacz.guns.client.renderer.block.GunSmithTableRenderer;
import com.tacz.guns.client.renderer.block.StatueRenderer;
import com.tacz.guns.client.renderer.block.TargetRenderer;
import com.tacz.guns.client.renderer.entity.EntityBulletRenderer;
import com.tacz.guns.client.renderer.entity.TargetMinecartRenderer;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.entity.TargetMinecart;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import cn.sh1rocu.tacz.client.ClientRendererRegistration;

@Environment(EnvType.CLIENT)
public class ModEntitiesRender {
    public static void registerEntityRenderers() {
        ClientRendererRegistration.entity(EntityKineticBullet.TYPE, EntityBulletRenderer::new);
        ClientRendererRegistration.entity(TargetMinecart.TYPE, TargetMinecartRenderer::new);
        ClientRendererRegistration.blockEntity(GunSmithTableBlockEntity.TYPE, GunSmithTableRenderer::new);
        ClientRendererRegistration.blockEntity(TargetBlockEntity.TYPE, TargetRenderer::new);
        ClientRendererRegistration.blockEntity(StatueBlockEntity.TYPE, StatueRenderer::new);
    }
}
