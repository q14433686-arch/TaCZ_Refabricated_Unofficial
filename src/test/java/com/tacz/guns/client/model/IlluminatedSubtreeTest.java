package com.tacz.guns.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.bedrock.BedrockPolygon;
import com.tacz.guns.util.IlluminatedLights;
import net.minecraft.world.item.ItemDisplayContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IlluminatedSubtreeTest {
    private static VertexConsumer token() {
        return (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(), new Class[]{VertexConsumer.class},
                (self, method, args) -> { throw new AssertionError("The probe need not issue vertices"); });
    }

    @Test void aFunctionalParentsMaterialReachesItsChildButNotItsSibling() {
        var plain = token(); var glow = token();
        List<VertexConsumer> seen = new ArrayList<>();
        BedrockCube probe = new BedrockCube() {
            public BedrockPolygon[] getPolygons() { throw new UnsupportedOperationException(); }
            public void compile(PoseStack.Pose pose, VertexConsumer out, int light, int overlay, float r, float g, float b, float a) {
                assertEquals(IlluminatedLights.FULL_BRIGHT, light); // same UV2 for all three
                seen.add(out);
            }
        };
        var root = new BedrockPart("root"); root.cubes.add(probe);
        var lamp = new FunctionalBedrockPart(null, "lamp_illuminated"); lamp.illuminated = true;
        var child = new BedrockPart("child"); child.cubes.add(probe); lamp.addChild(child); root.addChild(lamp);
        var sibling = new BedrockPart("sibling"); sibling.cubes.add(probe); root.addChild(sibling);
        root.render(new PoseStack(), ItemDisplayContext.FIXED, plain, glow, IlluminatedLights.FULL_BRIGHT, 0, 1, 1, 1, 1);
        assertEquals(3, seen.size());
        assertSame(plain, seen.get(0)); assertSame(glow, seen.get(1)); assertSame(plain, seen.get(2));
    }
}
