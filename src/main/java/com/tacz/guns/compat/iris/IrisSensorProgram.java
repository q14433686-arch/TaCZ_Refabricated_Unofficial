package com.tacz.guns.compat.iris;

import java.lang.reflect.Field;

/** Draw the coverage/heat masks into their private targets, not an Iris gbuffer. Restore a nested bypass. */
public final class IrisSensorProgram implements AutoCloseable {
    private static final IrisSensorProgram UNCHANGED = new IrisSensorProgram(false), CHANGED = new IrisSensorProgram(true);
    private static Field bypass;
    private final boolean changed;
    private IrisSensorProgram(boolean changed) { this.changed = changed; }
    public static IrisSensorProgram begin() throws ReflectiveOperationException {
        if (!IrisCompat.isUsingRenderPack()) return UNCHANGED;
        if (bypass == null) bypass = Class.forName("net.irisshaders.iris.vertices.ImmediateState").getField("bypass");
        boolean change = !bypass.getBoolean(null);
        if (change) bypass.setBoolean(null, true);
        return change ? CHANGED : UNCHANGED;
    }
    @Override public void close() throws IllegalAccessException { if (changed) bypass.setBoolean(null, false); }
}
