"""
Procedurally generate an 18x18 Minecraft-style "ear" icon for the lrtactical
deafened mob effect.

Design notes
------------
Vanilla mob_effect icons are 18x18 with a 1px dark outline and a small,
hand-quantised palette. An ear only reads at this size if the silhouette is an
open "C": a thick helix rim wrapping the top and left, an ear lobe at the
bottom, and a hollow that BREAKS the outline on the lower right (the
intertragic notch). Earlier attempts that used a closed inner ellipse produced
concentric rings that read as a doughnut/log, not an ear - hence `mouth()`.

Shapes are defined as ellipse booleans, rasterised by supersampled majority
coverage (crisp edges, no AA fringe), then cleaned up morphologically because
raw ellipse booleans always leave 1px spurs and dents that look wrong in pixel
art. Shading is a fixed upper-left light source.
"""
from PIL import Image
import math
import sys

S = 18          # vanilla mob_effect icon size
SS = 8          # supersample factor per axis

OUTLINE = (52, 31, 20, 255)
SHADOW  = (126, 76, 48, 255)
MID     = (182, 120, 78, 255)
LIGHT   = (214, 156, 108, 255)
HILIGHT = (242, 202, 160, 255)
NONE    = (0, 0, 0, 0)

TILT = math.radians(-8)     # slight backward lean, as ears sit on a head


def ell(x, y, ox, oy, rx, ry, tilt=TILT):
    dx, dy = x - ox, y - oy
    c, s = math.cos(tilt), math.sin(tilt)
    a, b = dx * c - dy * s, dx * s + dy * c
    return (a / rx) ** 2 + (b / ry) ** 2


def helix(x, y):   return ell(x, y, 9.3, 8.0, 5.6, 6.9) <= 1.0   # outer rim
def lobe(x, y):    return ell(x, y, 9.7, 12.6, 3.7, 3.9) <= 1.0  # ear lobe
def concha(x, y):  return ell(x, y, 10.6, 8.2, 2.6, 3.7) <= 1.0  # hollow
def mouth(x, y):                                                  # notch: opens the C
    return 8.2 <= y <= 11.0 and x >= 10.2 and not lobe(x, y)


def covered(x, y):
    inside = helix(x, y) or lobe(x, y)
    if not inside:
        return False
    if (concha(x, y) or mouth(x, y)) and not lobe(x, y):
        return False
    return True


def rasterise():
    mask = [[False] * S for _ in range(S)]
    for y in range(S):
        for x in range(S):
            hits = 0
            for sy in range(SS):
                for sx in range(SS):
                    if covered(x + (sx + 0.5) / SS, y + (sy + 0.5) / SS):
                        hits += 1
            mask[y][x] = hits * 2 > SS * SS
    return mask


def cleanup(mask):
    """Drop 1px spurs, fill 1px dents. Iterate until stable."""
    def m(x, y):
        return 0 <= x < S and 0 <= y < S and mask[y][x]
    for _ in range(4):
        changed = False
        for y in range(S):
            for x in range(S):
                n = sum(m(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if mask[y][x] and n <= 1:
                    mask[y][x] = False
                    changed = True
                elif not mask[y][x] and n >= 3:
                    mask[y][x] = True
                    changed = True
        if not changed:
            break
    return mask


def shade(mask):
    img = Image.new("RGBA", (S, S), NONE)
    px = img.load()
    for y in range(S):
        for x in range(S):
            if mask[y][x]:
                px[x, y] = LIGHT

    def solid(x, y):
        return 0 <= x < S and 0 <= y < S and px[x, y][3] != 0

    # 1px outline on every edge, inner hollow included
    edge = [(x, y) for y in range(S) for x in range(S)
            if solid(x, y)
            and not all(solid(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for x, y in edge:
        px[x, y] = OUTLINE

    # upper-left light source
    for y in range(S):
        for x in range(S):
            if px[x, y] != LIGHT:
                continue
            up_left = (px[x - 1, y] == OUTLINE if solid(x - 1, y) else True) or \
                      (px[x, y - 1] == OUTLINE if solid(x, y - 1) else True)
            dn_right = (px[x + 1, y] == OUTLINE if solid(x + 1, y) else True) or \
                       (px[x, y + 1] == OUTLINE if solid(x, y + 1) else True)
            if up_left and not dn_right:
                px[x, y] = HILIGHT
            elif dn_right and not up_left:
                px[x, y] = SHADOW
            elif dn_right and up_left:
                px[x, y] = MID
    return img


def main(out):
    img = shade(cleanup(rasterise()))
    img.save(out)
    px = img.load()
    ch = {NONE: '.', OUTLINE: '#', SHADOW: 's', MID: 'm', LIGHT: 'l', HILIGHT: 'h'}
    for y in range(S):
        print(''.join(ch.get(px[x, y], '?') for x in range(S)))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "deafened.png")
