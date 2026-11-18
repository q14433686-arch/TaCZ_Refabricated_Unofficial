package com.tacz.guns.client.resource;

import com.google.gson.Gson;
import com.tacz.guns.client.resource.pojo.display.gun.Rangefinder;
import com.tacz.guns.client.resource.pojo.display.gun.TextShow;
import org.junit.jupiter.api.Test;

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

    @Test void boundsWorldQueriesButHonorsZeroDistance() {
        var bad = gson.fromJson("{\"max_distance\":\"NaN\",\"refresh_tick\":0}", Rangefinder.class);
        assertEquals(512f, bad.getMaxDistance());
        assertEquals(2, bad.getRefreshTick());
        assertEquals(0f, gson.fromJson("{\"max_distance\":0}", Rangefinder.class).getMaxDistance());
        assertEquals(1024f, gson.fromJson("{\"max_distance\":999999}", Rangefinder.class).getMaxDistance());
    }
}
