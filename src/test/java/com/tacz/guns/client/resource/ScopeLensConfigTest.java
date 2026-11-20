package com.tacz.guns.client.resource;

import com.google.gson.Gson;
import com.tacz.guns.client.render.scope.ScopeVisionMode;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import com.tacz.guns.client.resource.pojo.display.attachment.ScopeLensConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScopeLensConfigTest {
    private ScopeLensConfig load(String json) {
        var display = new Gson().fromJson(json, AttachmentDisplay.class);
        display.init();
        return display.getScopeLens();
    }

    @Test void missingOrExplicitNullKeepsDefaultOptics() {
        for (String json : new String[]{"{}", "{\"scope_lens\":null}"}) {
            var lens = load(json);
            assertEquals(0x70aee8, lens.getTint());
            assertEquals(.2f, lens.getTintStrength());
            assertEquals(ScopeVisionMode.NORMAL, lens.getVisionMode());
            assertNull(lens.getVisionMode().tooltip());
        }
    }

    @Test void validatePackNumbersAndUnknownPalette() {
        var lens = load("""
                {"scope_lens":{"distortion":"NaN","aberration":1,"vignette_strength":-1,"edge_width":-3,
                "vision_gain":32,"vision_noise":1,"tint":"not RGB","vision_mode":"NOT_SUPPORTED"}}
                """);
        assertEquals(.05f, lens.getDistortion());
        assertEquals(.03f, lens.getAberration());
        assertEquals(0f, lens.getVignetteStrength());
        assertEquals(0f, lens.getEdgeWidth());
        assertEquals(12f, lens.getVisionGain());
        assertEquals(.2f, lens.getVisionNoise());
        assertEquals(0xffffff, lens.getTint());
        assertEquals(ScopeVisionMode.NORMAL, lens.getVisionMode());
    }

    @Test void allDisplayPalettesResolve() {
        for (var mode : ScopeVisionMode.values()) {
            var lens = load("{\"scope_lens\":{\"vision_mode\":\"" + mode.name() + "\"}}");
            assertEquals(mode, lens.getVisionMode());
            assertEquals(mode.isThermal(), lens.getVisionMode().tooltip() != null);
        }
    }
}
