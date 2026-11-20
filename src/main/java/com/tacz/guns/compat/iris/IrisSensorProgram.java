package com.tacz.guns.compat.iris;

import java.lang.reflect.Field;

/** Draw the sensor into its private R8/RGBA target, not an Iris gbuffer. Restore a nested bypass. */
public final class IrisSensorProgram implements AutoCloseable {
    private static Field bypass;
    private final boolean changed;
    private IrisSensorProgram(boolean changed) { this.changed = changed; }
    public static IrisSensorProgram begin() throws ReflectiveOperationException {
        if (!IrisCompat.hasActiveIrisPack()) return new IrisSensorProgram(false);
        if (bypass == null) bypass = Class.forName("net.irisshaders.iris.vertices.ImmediateState").getField("bypass");
        boolean change = !bypass.getBoolean(null);
        if (change) bypass.setBoolean(null, true);
        return new IrisSensorProgram(change);
    }
    @Override public void close() throws IllegalAccessException { if (changed) bypass.setBoolean(null, false); }
}
