#!/usr/bin/env python3
"""Offline quaternion-model + source-wiring checks for the 26.1.2 bullet-hole backport.

Backported from the 26.3 line on 2026-10-21. Direction rotations and XY vertex/UV order
were checked against the Minecraft 26.1.2 `QuadParticleRenderState`/`SingleQuadParticle`
API used by this checkout; legacy XZ corners were checked against upstream
1.21.1 BulletHoleParticle.render.
This models JOML's right-multiplication convention, NOT execution of Java/JOML, shader
rendering, or a replacement for compilation and six-face in-game tests.
Run: python3 scripts/verify_bullet_hole_orientation.py
"""
from math import cos, pi, sin
from pathlib import Path
import re
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "src/main/java/com/tacz/guns/client/particle/BulletHoleParticle.java"


def mul(a, b):
    x, y, z, w = a
    X, Y, Z, W = b
    return (w * X + x * W + y * Z - z * Y,
            w * Y - x * Z + y * W + z * X,
            w * Z + x * Y - y * X + z * W,
            w * W - x * X - y * Y - z * Z)


def axis(index, angle):
    xyz = [0.0, 0.0, 0.0]
    xyz[index] = sin(angle / 2)
    return (*xyz, cos(angle / 2))


def rotate(q, vector):
    return mul(mul(q, (*vector, 0)), (-q[0], -q[1], -q[2], q[3]))[:3]


# Direction.getRotation(): rotationXYZ is Rx * Ry * Rz (Ry is zero here).
FACES = {
    "DOWN": ((0, -1, 0), axis(0, pi)),
    "UP": ((0, 1, 0), (0, 0, 0, 1)),
    "NORTH": ((0, 0, -1), mul(axis(0, pi / 2), axis(2, pi))),
    "SOUTH": ((0, 0, 1), axis(0, pi / 2)),
    "WEST": ((-1, 0, 0), mul(axis(0, pi / 2), axis(2, pi / 2))),
    "EAST": ((1, 0, 0), mul(axis(0, pi / 2), axis(2, -pi / 2))),
}
BASIS = mul(axis(0, -pi / 2), axis(2, pi))
# Both pipelines use this UV order; positions must match without permuting UVs.
UV = ((1, 1), (1, 0), (0, 0), (0, 1))
XY = ((1, -1, 0), (1, 1, 0), (-1, 1, 0), (-1, -1, 0))
LEGACY_XZ = ((-1, .01, -1), (-1, .01, 1), (1, .01, 1), (1, .01, -1))


class BulletHoleOrientationChecks(unittest.TestCase):
    def assertVector(self, actual, expected):
        for a, e in zip(actual, expected):
            self.assertAlmostEqual(a, e, places=8)

    def test_corrected_normals_match_all_six_hit_faces(self):
        for face, (normal, direction) in FACES.items():
            with self.subTest(face=face):
                self.assertVector(rotate(direction, (0, 1, 0)), normal)
                self.assertVector(rotate(mul(direction, BASIS), (0, 0, 1)), normal)
                # Regression witness: the uncorrected +Z normal is wrong on every face.
                self.assertGreater(sum((a - b) ** 2 for a, b in
                                       zip(rotate(direction, (0, 0, 1)), normal)), 1)

    def test_each_uv_corner_and_normal_offset_matches_legacy(self):
        for face, (normal, direction) in FACES.items():
            for size in (.025, .05, .2):
                for uv, xy, legacy in zip(UV, XY, LEGACY_XZ):
                    with self.subTest(face=face, size=size, uv=uv):
                        rotated = rotate(mul(direction, BASIS), xy)
                        actual = tuple(v * size + n * .01 * size for v, n in zip(rotated, normal))
                        expected = tuple(v * size for v in rotate(direction, legacy))
                        self.assertVector(actual, expected)

    def test_camera_translation_and_partial_tick_are_preserved(self):
        # Hit position must remain world-relative, not become a fixed screen/RGB offset.
        for camera in ((0, 0, 0), (135.5, -48, 278.25)):
            for partial_tick in (0, .375, 1):
                previous, current = (101, 60, -202), (102, 62, -204)
                center = tuple(a + partial_tick * (b - a) - c
                               for a, b, c in zip(previous, current, camera))
                for normal, direction in FACES.values():
                    for xy, legacy in zip(XY, LEGACY_XZ):
                        actual = tuple(c + v * .05 + n * .0005 for c, v, n in
                                       zip(center, rotate(mul(direction, BASIS), xy), normal))
                        expected = tuple(c + v * .05 for c, v in zip(center, rotate(direction, legacy)))
                        self.assertVector(actual, expected)

    def test_java_source_uses_tested_basis_and_coordinate_overload(self):
        source = re.sub(r"/\*.*?\*/|//[^\n]*", "", SOURCE.read_text(), flags=re.S)
        source = re.sub(r"\s+", " ", source)
        self.assertIn("this.direction.getRotation() .mul(new Quaternionf().rotationX(-Mth.HALF_PI).rotateZ(Mth.PI))", source)
        self.assertIn("float offset = 0.01F * this.getQuadSize(partialTicks);", source)
        self.assertIn("Vec3 camPos = camera.position();", source)
        for coordinate, step in (("x", "X"), ("y", "Y"), ("z", "Z")):
            self.assertIn(
                f"float {coordinate} = (float) (Mth.lerp(partialTicks, this.{coordinate}o, this.{coordinate}) - camPos.{coordinate}()) + this.direction.getStep{step}() * offset;",
                source,
            )
        self.assertIn("this.extractRotatedQuad(state, quaternion, x, y, z, partialTicks);", source)
        self.assertNotIn("this.extractRotatedQuad(state, camera, quaternion, partialTicks)", source)

    def test_color_and_alpha_are_still_restored_after_extraction(self):
        source = SOURCE.read_text().split("} finally {", 1)[1].split("}", 1)[0]
        for field, base in (("rCol", "R"), ("gCol", "G"), ("bCol", "B"), ("alpha", "A")):
            self.assertIn(f"this.{field} = base{base};", source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
