package com.tacz.guns.client.model;

import com.google.gson.Gson;
import com.tacz.guns.client.resource.pojo.model.BonesItem;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OcularBakeTest {
    private static BonesItem bone(String name, String parent) {
        return new Gson().fromJson("{name:'" + name + "',parent:'" + parent + "'}", BonesItem.class);
    }
    @Test void numberedOcularAndDescendantsOnly() {
        var ocular = bone("ocular_scope_2", "root");
        var child = bone("glass", "ocular_scope_2");
        var ring = bone("ocular_ring", "root");
        var cycle = bone("cycle", "cycle");
        var index = Map.of("ocular_scope_2", ocular, "glass", child, "ocular_ring", ring, "cycle", cycle);
        assertEquals(0.0005f, BedrockAttachmentModel.ocularInflate(child, index, 0.0005f));
        assertEquals(0f, BedrockAttachmentModel.ocularInflate(ring, index, 1));
        assertEquals(0f, BedrockAttachmentModel.ocularInflate(cycle, index, 1));
        assertEquals(0f, BedrockAttachmentModel.ocularInflate(ocular, index, Float.NaN));
    }
}
