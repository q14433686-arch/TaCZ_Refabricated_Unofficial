package com.tacz.guns.client.render.light;

import net.minecraft.world.phys.Vec3;

/** A blast is radial. A muzzle has a broad forward lobe with its peak still at the muzzle,
 * not another source placed beyond a wall. |v| - bias·v = radius describes an ellipsoid. */
record SpillField(Vec3 source, Vec3 bias, double radius) {
    static final double MUZZLE_BIAS = .44, MAX_RADIUS = 10;

    float gain(double x, double y, double z) {
        double dx = x - source.x, dy = y - source.y, dz = z - source.z;
        double t = (Math.sqrt(dx*dx + dy*dy + dz*dz) - (bias.x*dx + bias.y*dy + bias.z*dz)) / radius;
        double shoulder = Math.max(0, 1 - t*t);
        return (float)(shoulder * shoulder);
    }

    double boundingRadius() { return radius / (1 - bias.lengthSqr()); }
    Vec3 boundingCenter() { return source.add(bias.scale(boundingRadius())); }
}
