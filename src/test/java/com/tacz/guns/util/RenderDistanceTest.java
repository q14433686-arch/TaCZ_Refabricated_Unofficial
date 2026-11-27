package com.tacz.guns.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RenderDistanceTest {
    @Test void nestedPreviewScopesEndImmediatelyAndRestoreAfterFailure() {
        assertFalse(RenderDistance.isGuiRender());
        RenderDistance.beginScreenItemDraw();
        try {
            assertTrue(RenderDistance.isGuiRender());
            assertThrows(IllegalStateException.class, () -> {
                RenderDistance.beginScreenItemDraw();
                try {
                    assertTrue(RenderDistance.isGuiRender());
                    throw new IllegalStateException("preview submit failed");
                } finally {
                    RenderDistance.endScreenItemDraw();
                }
            });
            assertTrue(RenderDistance.isGuiRender());
        } finally {
            RenderDistance.endScreenItemDraw();
        }
        assertFalse(RenderDistance.isGuiRender()); // no 100 ms grace period in the next world draw
        RenderDistance.endScreenItemDraw(); // defensive unmatched end must not poison later scopes
        RenderDistance.beginScreenItemDraw();
        try {
            assertTrue(RenderDistance.isGuiRender());
        } finally {
            RenderDistance.endScreenItemDraw();
        }
        assertFalse(RenderDistance.isGuiRender());
    }
}
