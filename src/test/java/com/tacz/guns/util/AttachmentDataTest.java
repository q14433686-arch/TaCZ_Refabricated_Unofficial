package com.tacz.guns.util;

import com.google.gson.JsonParser;
import com.tacz.guns.resource.manager.GunDataManager;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.modifier.custom.RecoilModifier;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import com.tacz.guns.resource.pojo.data.gun.GunRecoilPushData;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AttachmentDataTest {
    @Test void cyclicTagGraphIncludesWholeRoot() {
        var a = Identifier.parse("test:a");
        var b = Identifier.parse("test:b");
        var graph = Map.of(a, Set.of("#test:b", "test:one"), b, Set.of("#test:a", "test:two", "#test:missing"));
        for (var root : graph.keySet()) {
            assertEquals(Set.of(Identifier.parse("test:one"), Identifier.parse("test:two")),
                    AllowAttachmentTagMatcher.expand(graph.get(root), root, graph::get));
        }
    }

    @Test void fileAndSyncUseTheSameExclusiveModifiers() {
        AttachmentPropertyManager.registerModifier();
        var id = Identifier.parse("fixture:part");
        String json = """
                {"exclusive_attachments":{"fixture:part":{"rpm":{"addend":2,"function":"Y = X * 2 + R"},
                "aim_inaccuracy":{"percent":0.5}}}}
                """;
        var local = GunDataManager.parseData(JsonParser.parseString(json));
        var remote = CommonNetworkCache.INSTANCE;
        try {
            remote.fromNetwork(Map.of(DataType.GUN_DATA, Map.of(id, json)));
            for (var data : List.of(local, remote.getGunData(id))) {
                var mods = data.getExclusiveAttachments().get(id).getModifier();
                assertTrue(mods.containsKey("inaccuracy")); // alias alone triggers parsing
                Modifier rpm = (Modifier) mods.get("rpm").getValue();
                assertNotNull(rpm.getCompiledFunction());
                assertEquals(34, AttachmentPropertyManager.eval(rpm, 10));
                assertEquals(7, AttachmentPropertyManager.eval(rpm, 1));
            }
        } finally { remote.clear(); }
    }

    @Test void omittedCameraAxesHaveZeroCacheWithoutSuppressingPresentAxis() {
        var cache = new RecoilModifier();
        var empty = GunDataManager.parseData(JsonParser.parseString("{}"));
        var defaults = cache.initCache(null, empty).getValue(); // this cache has no stack dependency
        assertEquals(0f, defaults.left().getDefaultValue().floatValue());
        assertEquals(0f, defaults.right().getDefaultValue().floatValue());
        var yawOnly = GunDataManager.parseData(JsonParser.parseString("""
                {"recoil":{"pitch":[],"yaw":[{"time":0.1,"value":[-2,1]}]}}
                """));
        var asymmetric = cache.initCache(null, yawOnly).getValue();
        assertEquals(0f, asymmetric.left().getDefaultValue().floatValue());
        assertEquals(2f, asymmetric.right().getDefaultValue().floatValue());
    }

    @Test void recoilPushSelectsPoseAndCapsProjectiles() {
        var push = new GunRecoilPushData(1, 2, .25, .5, true, 1);
        assertEquals(.5, push.getFinalStrength(true, true, true, 1));
        assertEquals(.25, push.getFinalStrength(true, true, false, 1));
        assertEquals(4, push.getFinalStrength(false, true, true, 8));
    }
}
