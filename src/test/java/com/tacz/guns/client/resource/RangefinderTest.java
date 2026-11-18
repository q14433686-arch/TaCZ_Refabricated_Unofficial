package com.tacz.guns.client.resource;

import com.google.gson.Gson;
import com.tacz.guns.client.resource.pojo.display.gun.Rangefinder;
import com.tacz.guns.client.resource.pojo.display.gun.TextShow;
import org.junit.jupiter.api.Test;

import static com.tacz.guns.client.model.papi.ImpactPreview.slope;
import static org.junit.jupiter.api.Assertions.*;

class RangefinderTest {
    private final Gson gson = new Gson();

    @Test void nodeIsOptInAndRoundsToDisplayWidth() {
        assertNull(gson.fromJson("{}", TextShow.class).getRangefinder());
        var node = gson.fromJson("{\"rangefinder\":{\"max_distance\":99.6,\"refresh_tick\":4}}", TextShow.class);
        var config = node.getRangefinder();
        assertEquals(4, config.getRefreshTick());
        assertEquals("010", config.format(9.5));
        assertEquals("---", config.format(100));
        assertEquals("---", config.format(-1));
        assertEquals("---", config.format(Double.NaN));
    }

    @Test void markerIsOptInAndUsesOpaquePackColor() {
        assertFalse(gson.fromJson("{}", Rangefinder.class).showsImpact());
        var node = gson.fromJson("{\"show_impact_prediction\":true,\"impact_prediction_color\":\"fcfe00\",\"impact_prediction_scale\":2}", Rangefinder.class);
        assertTrue(node.showsImpact());
        assertEquals(0xfffcfe00, node.getImpactColor());
        assertEquals(2f, node.getImpactScale());
    }

    @Test void blockTickPreviewEndsAtRangeOrExpiry() {
        assertEquals(0, slope(5, 10, .5, .5, 4));
        assertEquals(.25/12.5, slope(12.5, 10, .5, .5, 4), 1e-6);
        assertTrue(Double.isNaN(slope(99, 10, .5, .5, 2)));
        assertTrue(Double.isNaN(slope(10, 0, .5, 0, 10)));
        assertTrue(Double.isNaN(slope(10, Double.NaN, .5, 0, 10)));
    }

    @Test void boundsWorldQueriesButHonorsZeroDistance() {
        var bad = gson.fromJson("{\"max_distance\":\"NaN\",\"refresh_tick\":0}", Rangefinder.class);
        assertEquals(512f, bad.getMaxDistance());
        assertEquals(2, bad.getRefreshTick());
        assertEquals(0f, gson.fromJson("{\"max_distance\":0}", Rangefinder.class).getMaxDistance());
        assertEquals(1024f, gson.fromJson("{\"max_distance\":999999}", Rangefinder.class).getMaxDistance());
    }
}
