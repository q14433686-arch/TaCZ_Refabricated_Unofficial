#!/usr/bin/env python3
"""Repair transparent-pixel omissions and canvas artifacts in TACZ icon packs.

The source pack supplied by the project is 32x32 opaque pixel art with some
pixels retaining RGB colour while their alpha is accidentally zero.  This tool
only restores *enclosed* missing pixels, removes explicitly detected dark
edge-line artifacts, and flood-removes three opaque canvas backgrounds that
are demonstrably outside their icons.  It intentionally avoids AI repainting
or broad blur/resize operations so the author pixel art stays pixel-perfect.

Requires ImageMagick's ``convert`` executable, which is available in the
Arena authoring environment.  The script is an author/CI tool, not a player
runtime dependency.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import zipfile
from collections import deque
from pathlib import Path
from typing import Any

SIZE = 32
# Minecraft 26.2 version.json declares resource_major = 88.
RESOURCE_PACK_FORMAT = 88
# These three source sprites contain a near-uniform opaque brown canvas that
# touches the border; it is not part of the cartridge/rocket silhouette.
CANVAS_BACKGROUNDS: dict[str, tuple[tuple[int, int, int], int]] = {
    "ammo_12gauge.png": ((53, 48, 42), 10),
    "ammo_40mm.png": ((53, 48, 42), 10),
    "ammo_rpg7.png": ((53, 48, 42), 10),
}
# Detached one-pixel scan lines discovered by component analysis. They are
# outside the actual cartridge silhouettes and survive a generic component
# pass because a few stray edge pixels bridge their dark component.
EDGE_STRIPS: dict[str, tuple[str, int, int, int]] = {
    "ammo_762x25.png": ("x", 30, 1, 30),
}


def rgba_index(x: int, y: int) -> int:
    return (y * SIZE + x) * 4


def pixel(raw: bytearray | bytes, x: int, y: int) -> tuple[int, int, int, int]:
    index = rgba_index(x, y)
    return tuple(raw[index:index + 4])  # type: ignore[return-value]


def set_alpha(raw: bytearray, x: int, y: int, alpha: int) -> None:
    raw[rgba_index(x, y) + 3] = alpha


def color_distance(a: tuple[int, int, int, int], b: tuple[int, int, int]) -> int:
    return max(abs(a[0] - b[0]), abs(a[1] - b[1]), abs(a[2] - b[2]))


def load_rgba(path: Path) -> bytearray:
    raw = subprocess.check_output(["convert", str(path), "-depth", "8", "rgba:-"])
    expected = SIZE * SIZE * 4
    if len(raw) != expected:
        raise ValueError(f"{path}: expected {SIZE}x{SIZE} RGBA, got {len(raw)} bytes")
    return bytearray(raw)


def write_rgba(path: Path, raw: bytearray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["convert", "-size", f"{SIZE}x{SIZE}", "-depth", "8", "rgba:-", str(path)],
        input=bytes(raw),
        check=True,
    )


def remove_canvas_background(raw: bytearray, target: tuple[int, int, int], tolerance: int) -> int:
    """Flood-remove only matching opaque canvas pixels reachable from an edge."""
    queue: deque[tuple[int, int]] = deque()
    visited: set[tuple[int, int]] = set()
    for coordinate in range(SIZE):
        for point in ((coordinate, 0), (coordinate, SIZE - 1), (0, coordinate), (SIZE - 1, coordinate)):
            if point in visited:
                continue
            value = pixel(raw, *point)
            if value[3] > 0 and color_distance(value, target) <= tolerance:
                visited.add(point)
                queue.append(point)
    removed = 0
    while queue:
        x, y = queue.popleft()
        value = pixel(raw, x, y)
        if value[3] == 0 or color_distance(value, target) > tolerance:
            continue
        set_alpha(raw, x, y, 0)
        removed += 1
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < SIZE and 0 <= ny < SIZE and (nx, ny) not in visited:
                neighbor = pixel(raw, nx, ny)
                if neighbor[3] > 0 and color_distance(neighbor, target) <= tolerance:
                    visited.add((nx, ny))
                    queue.append((nx, ny))
    return removed


def opaque_components(raw: bytearray) -> list[set[tuple[int, int]]]:
    remaining = {(x, y) for y in range(SIZE) for x in range(SIZE) if pixel(raw, x, y)[3] > 0}
    components: list[set[tuple[int, int]]] = []
    while remaining:
        seed = remaining.pop()
        component = {seed}
        queue = [seed]
        while queue:
            x, y = queue.pop()
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                point = (x + dx, y + dy)
                if point in remaining:
                    remaining.remove(point)
                    component.add(point)
                    queue.append(point)
        components.append(component)
    return sorted(components, key=len, reverse=True)


def remove_dark_border_lines(raw: bytearray, enabled: bool) -> int:
    """Remove detached, one-pixel-wide black scan-line artifacts on ammo icons."""
    if not enabled:
        return 0
    removed = 0
    for component in opaque_components(raw)[1:]:
        xs = [point[0] for point in component]
        ys = [point[1] for point in component]
        width = max(xs) - min(xs) + 1
        height = max(ys) - min(ys) + 1
        touches_edge = any(x in {0, SIZE - 1} or y in {0, SIZE - 1} for x, y in component)
        average = sum(sum(pixel(raw, x, y)[:3]) for x, y in component) / (3 * len(component))
        narrow = width == 1 or height == 1
        if touches_edge and narrow and len(component) >= 8 and average < 25:
            for x, y in component:
                set_alpha(raw, x, y, 0)
                removed += 1
    return removed


def remove_explicit_edge_strip(raw: bytearray, name: str) -> int:
    strip = EDGE_STRIPS.get(name)
    if strip is None:
        return 0
    axis, coordinate, start, end = strip
    removed = 0
    for value in range(start, end + 1):
        x, y = (coordinate, value) if axis == "x" else (value, coordinate)
        if pixel(raw, x, y)[3] > 0:
            set_alpha(raw, x, y, 0)
            removed += 1
    return removed


def restore_enclosed_pixels(raw: bytearray, original: bytes) -> int:
    """Restore alpha only where a source-coloured pixel is enclosed by art."""
    restored = 0
    for y in range(1, SIZE - 1):
        for x in range(1, SIZE - 1):
            source = pixel(original, x, y)
            if source[3] != 0:
                continue
            left = pixel(raw, x - 1, y)[3] > 0
            right = pixel(raw, x + 1, y)[3] > 0
            up = pixel(raw, x, y - 1)[3] > 0
            down = pixel(raw, x, y + 1)[3] > 0
            # Opposing opaque pixels form a genuine raster hole.  A non-black
            # source colour prevents random transparent black canvas residue
            # from being promoted into foreground pixels.
            if (left and right) or (up and down):
                if sum(source[:3]) > 10 or sum((left, right, up, down)) >= 3:
                    raw[rgba_index(x, y):rgba_index(x, y) + 4] = bytes((source[0], source[1], source[2], 255))
                    restored += 1
    return restored


def repair_pack_metadata(root: Path) -> dict[str, int | None]:
    """Upgrade the supplied standalone pack descriptor to Minecraft 26.2."""
    metadata_path = root / "pack.mcmeta"
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    pack = metadata.setdefault("pack", {})
    previous = pack.get("pack_format")
    pack["pack_format"] = RESOURCE_PACK_FORMAT
    metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {"from": previous if isinstance(previous, int) else None, "to": RESOURCE_PACK_FORMAT}


def repair_texture(source: Path, destination: Path) -> dict[str, int]:
    raw = load_rgba(source)
    original = bytes(raw)
    canvas_removed = 0
    if source.name in CANVAS_BACKGROUNDS:
        color, tolerance = CANVAS_BACKGROUNDS[source.name]
        canvas_removed = remove_canvas_background(raw, color, tolerance)
    border_removed = remove_dark_border_lines(raw, source.name.startswith("ammo_"))
    explicit_strip_removed = remove_explicit_edge_strip(raw, source.name)
    restored = restore_enclosed_pixels(raw, original)
    write_rgba(destination, raw)
    return {
        "canvas_removed": canvas_removed,
        "border_artifacts_removed": border_removed + explicit_strip_removed,
        "enclosed_pixels_restored": restored,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input_zip", type=Path)
    parser.add_argument("output_zip", type=Path)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()

    work = args.output_zip.parent / ".tacz_icon_repair_work"
    if work.exists():
        shutil.rmtree(work)
    with zipfile.ZipFile(args.input_zip) as archive:
        archive.extractall(work)

    textures = sorted((work / "assets/tacz_extra/textures/item").glob("*.png"))
    report: dict[str, Any] = {
        "source": args.input_zip.name,
        "source_sha256": hashlib.sha256(args.input_zip.read_bytes()).hexdigest(),
        "texture_count": len(textures),
        "pack_format": repair_pack_metadata(work),
        "textures": {},
    }
    totals = {"canvas_removed": 0, "border_artifacts_removed": 0, "enclosed_pixels_restored": 0}
    for texture in textures:
        repaired = repair_texture(texture, texture)
        report["textures"][texture.name] = repaired
        for key in totals:
            totals[key] += repaired[key]
    report["totals"] = totals

    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    args.output_zip.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output_zip, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(work.rglob("*")):
            if path.is_file():
                archive.write(path, path.relative_to(work).as_posix())
    shutil.rmtree(work)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
