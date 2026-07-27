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
    public static void onRenderEntity(RenderLivingEvent.Post<?, ?, ?> event) {
        // 【第 35 轮修复】补回 F3+B 门禁。
        //
        // 上游第一行就是：
        //     if (!Minecraft.getInstance().getEntityRenderDispatcher().shouldRenderHitBoxes()) return;
        // 移植时整条丢了，于是爆头线只受配置项控制 —— 一旦在配置里打开，
        // 就<b>再也关不掉</b>（关 F3、关碰撞箱显示都没用），只能回配置文件改。
        // 这正是用户实测反馈的现象。
        //
        // 26.2 的 shouldRenderHitBoxes() 已不存在（全 net.minecraft 字节码 grep 无此方法）。
        // 碰撞箱开关改由<b>调试屏条目系统</b>接管：
        //     Minecraft.debugEntries : DebugScreenEntryList   (public final 字段，无需 AW)
        //     DebugScreenEntryList#isCurrentlyEnabled(Identifier) : boolean
        //     DebugScreenEntries.ENTITY_HITBOXES : Identifier  (public static)
        // 三者均已对 26.2 字节码逐个确认。
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
        event.getSubmitNodeCollector().submitShapeOutline(event.getPoseStack(), net.minecraft.world.phys.shapes.Shapes.create(aabb),
                RenderTypes.lines(), 0xFFFFFF00, 1.0F, false);
    }
}
