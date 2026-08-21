package com.tacz.guns.client.event;

import cn.sh1rocu.tacz.api.event.RenderLivingEvent;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.util.HeadShotAABBConfigRead;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

@Environment(EnvType.CLIENT)
public class RenderHeadShotAABB {
    private static final float LINE_WIDTH = 2.5F;

    public static void onRenderEntity(RenderLivingEvent.Post<?, ?, ?> event) {
        // 【第 35 轮修复】补回 F3+B 门禁。
        if (!Minecraft.getInstance().debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) {
            return;
        }
        if (!RenderConfig.HEAD_SHOT_DEBUG_HITBOX.get() || event.getSubmitNodeCollector() == null || event.getPoseStack() == null) {
            return;
        }
        var renderState = event.getRenderState();
        Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(renderState.entityType);
        AABB aabb = HeadShotAABBConfigRead.getAABB(entityId);
        if (aabb == null) {
            float width = renderState.boundingBoxWidth;
            float eyeHeight = renderState.eyeHeight;
            // 扩张 0.01，避免和原版显示重合
            aabb = new AABB(-width / 2, eyeHeight - 0.25, -width / 2, width / 2, eyeHeight + 0.25, width / 2).inflate(0.01);
        }
        AABB finalAabb = aabb;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lines(), (entryPose, consumer) -> {
            com.mojang.blaze3d.vertex.PoseStack tempPose = new com.mojang.blaze3d.vertex.PoseStack();
            tempPose.last().pose().set(entryPose.pose());
            tempPose.last().normal().set(entryPose.normal());
            drawLineBox(tempPose, consumer, finalAabb, 1.0F, 1.0F, 0.0F, 1.0F);
        });
    }

    private static void drawLineBox(com.mojang.blaze3d.vertex.PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer consumer, AABB aabb, float r, float g, float b, float a) {
        double minX = aabb.minX;
        double minY = aabb.minY;
        double minZ = aabb.minZ;
        double maxX = aabb.maxX;
        double maxY = aabb.maxY;
        double maxZ = aabb.maxZ;
        
        com.mojang.blaze3d.vertex.PoseStack.Pose pose = poseStack.last();
        org.joml.Matrix4f matrix = pose.pose();
        org.joml.Matrix3f normal = pose.normal();
        
        drawEdge(matrix, normal, consumer, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        drawEdge(matrix, normal, consumer, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        drawEdge(matrix, normal, consumer, minX, minY, minZ, minX, minY, maxZ, r, g, b, a);
        
        drawEdge(matrix, normal, consumer, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        drawEdge(matrix, normal, consumer, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
        drawEdge(matrix, normal, consumer, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        
        drawEdge(matrix, normal, consumer, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawEdge(matrix, normal, consumer, minX, maxY, minZ, minX, maxY, maxZ, r, g, b, a);
        
        drawEdge(matrix, normal, consumer, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawEdge(matrix, normal, consumer, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        
        drawEdge(matrix, normal, consumer, minX, minY, maxZ, maxX, minY, maxZ, r, g, b, a);
        drawEdge(matrix, normal, consumer, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private static void drawEdge(org.joml.Matrix4f matrix, org.joml.Matrix3f normal, com.mojang.blaze3d.vertex.VertexConsumer consumer, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        org.joml.Vector4f pos = new org.joml.Vector4f((float) x1, (float) y1, (float) z1, 1.0f).mul(matrix);
        org.joml.Vector3f norm = new org.joml.Vector3f((float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1)).normalize().mul(normal);
        consumer.addVertex(pos.x(), pos.y(), pos.z()).setColor(r, g, b, a).setNormal(norm.x(), norm.y(), norm.z()).setLineWidth(LINE_WIDTH);
        
        pos = new org.joml.Vector4f((float) x2, (float) y2, (float) z2, 1.0f).mul(matrix);
        consumer.addVertex(pos.x(), pos.y(), pos.z()).setColor(r, g, b, a).setNormal(norm.x(), norm.y(), norm.z()).setLineWidth(LINE_WIDTH);
    }
}
