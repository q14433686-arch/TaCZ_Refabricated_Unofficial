#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
离线审计：某个枪包的瞄具分划（reticle）里，哪些 cube 会被 ReticleMarkFilter 在 CPU 侧整块丢弃。

背景（docs/lineage/REPORT_REVIEW_ZOOMIFY_SCOPE_20261022.md §4）：
    ``ReticleMarkFilter.isThinMark`` 只保留「AABB 第二长轴 <= THIN_MARK_MAX_EXTENT」的 cube，
    其余一律不提交。把整块大平面当分划贴图的枪包（评论者举的「碧蓝档案」瞄具，以及默认包自己的
    scope_vudu）因此整块丢失。本脚本不需要 JDK / 游戏：直接读枪包里的 bedrock geo JSON，按下面这几处
    Java 源码的语义复刻「哪些 cube 会被提交、哪些会被丢掉」，让枪包作者 / 维护者在进游戏之前就能核对。

复刻的 Java 语义（逐条对过源码，路径相对仓库根）：
    * 节点选择   BedrockAttachmentModel#RETICLE_ETCHED_PATTERN / RETICLE_ILLUMINATED_PATTERN
    * 渲染器选择 ReticleRendererRegistry：有「带几何的发光节点」走 IlluminatedReticleRenderer，
                 否则有「带几何的蚀刻节点」走 EtchedReticleRenderer；两者都对节点整棵子树取 cube
                 （BedrockRenderSnapshot#captureSubtree）
    * 度量       ReticleMarkFilter#isThinMark —— 用 cube 自己的顶点（BedrockCube#getPolygons），
                 只计「声明了 UV 的面」；顶点是 cube 局部坐标：带 rotation 的 cube 在 BedrockModel#loadNewModel
                 里被放进子 BedrockPart，旋转在 part 层（xRot/yRot/zRot），**不烘进顶点**，所以这里量的是
                 旋转前的 size(+inflate) 外包盒
    * 阈值       直接从 ReticleMarkFilter.java 里读 THIN_MARK_MAX_EXTENT（不手抄，避免漂移）

局限（务必读）：
    * 这是对 Java 逻辑的离线复刻，**没有在游戏里对拍过**；Java 侧改了加载/过滤逻辑，这里要同步改。
    * 只量 size(+inflate)，不模拟 float32 舍入；|middle - 阈值| < 1e-3 的 cube 会标 BORDER，请人工判断。
    * 不替你判断「这块平面是遮光板还是真分划」。``-v --textures`` 会把被丢弃大平面的贴图内容统计
      （透明 / 纯黑 / 非黑不透明像素占比）打出来，供人判断；需要 Pillow（没有就跳过这一项）。
    * 退出码恒为 0（这是审计工具，不是门禁）。

用法（输入可以是目录、单个 geo JSON、或整个枪包 .zip，可混用）：
    python3 scripts/audit_reticle_marks.py path/to/gunpack.zip
    python3 scripts/audit_reticle_marks.py -v --textures path/to/gunpack.zip
    python3 scripts/audit_reticle_marks.py -v path/to/assets/tacz/geo_models/attachment
    python3 scripts/audit_reticle_marks.py --threshold 10 path/to/pack   # 假设阈值，看会放进什么
"""
import argparse
import io
import json
import os
import posixpath
import re
import sys
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_JAVA = os.path.join(
    REPO, "src", "main", "java", "com", "tacz", "guns", "client", "render", "scope", "ReticleMarkFilter.java")

# 与 BedrockAttachmentModel 里的 Java 正则逐字对应（Java 的 matches() == 整串匹配）。
ETCHED = re.compile(r"^(division|divisions)(_(\d+))?$")
ILLUMINATED = re.compile(r"^(.*_)?(division|divisions|dot|cross|crosshair|reticle|red)(_\d+)?_illuminated\d*$")

BORDER_EPS = 1.0e-3


def read_threshold(java_path):
    with open(java_path, encoding="utf-8") as fh:
        m = re.search(r"THIN_MARK_MAX_EXTENT\s*=\s*([0-9.]+)F", fh.read())
    if not m:
        raise SystemExit("cannot find THIN_MARK_MAX_EXTENT in %s" % java_path)
    return float(m.group(1))


# ---------------------------------------------------------------------------------------------
# 与 Java 过滤器等价的度量
# ---------------------------------------------------------------------------------------------

def cube_extents(cube):
    """复刻 isThinMark 会看到的 (x, y, z) 外包盒尺寸；没有任何可量顶点时返回 None。"""
    size = cube.get("size")
    if not size or len(size) != 3:
        return None
    w, h, d = (float(v) for v in size)
    delta = float(cube.get("inflate", 0.0) or 0.0)
    uv = cube.get("uv")
    if not isinstance(uv, dict):
        # BedrockCubeBox（盒 UV）：六个面都在，外包盒 = size + 2*inflate。
        return (w + 2 * delta, h + 2 * delta, d + 2 * delta)
    # BedrockCubePerFace：只有声明了的面才有真实顶点，其余面是全 (0,0,0) 的占位面，被
    # ReticleMarkFilter#isDegenerateEmptyFace 跳过。FaceUVsItem 会把 east/west、up/down 的键
    # 与多边形方向互换，但互换是关于 cube 中心的镜像，不改变任何一组面的外包盒尺寸。
    x0, x1 = -delta, w + delta
    y0, y1 = -delta, h + delta
    z0, z1 = -delta, d + delta
    xs, ys, zs = [], [], []
    for face in uv:
        if face == "down":
            xs += [x0, x1]; ys += [y0]; zs += [z0, z1]
        elif face == "up":
            xs += [x0, x1]; ys += [y1]; zs += [z0, z1]
        elif face == "west":
            xs += [x0]; ys += [y0, y1]; zs += [z0, z1]
        elif face == "east":
            xs += [x1]; ys += [y0, y1]; zs += [z0, z1]
        elif face == "north":
            xs += [x0, x1]; ys += [y0, y1]; zs += [z0]
        elif face == "south":
            xs += [x0, x1]; ys += [y0, y1]; zs += [z1]
    if not xs:
        return None
    return (max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs))


def middle_extent(ext):
    x, y, z = ext
    return x + y + z - max(x, y, z) - min(x, y, z)


def visible_face_uv(cube):
    """返回 (面名, u, v, du, dv)：分划平面朝向玩家的那一面。

    分划平面都画在 -Z 远处、由 +Z 侧的玩家去看，所以优先取 ``south`` 面；没有 south 才退到 UV 面积
    最大的那一面。盒 UV / 无 UV 返回 None。
    """
    uv = cube.get("uv")
    if not isinstance(uv, dict):
        return None
    faces = []
    for name, face in uv.items():
        try:
            u, v = float(face["uv"][0]), float(face["uv"][1])
            du, dv = float(face["uv_size"][0]), float(face["uv_size"][1])
        except (KeyError, TypeError, IndexError, ValueError):
            continue
        faces.append((name, u, v, du, dv))
    if not faces:
        return None
    south = [f for f in faces if f[0] == "south" and f[3] != 0 and f[4] != 0]
    if south:
        return south[0]
    return max(faces, key=lambda f: abs(f[3] * f[4]))


def uv_hint(cube):
    """给人看的提示：实心单像素（典型遮光板）还是一块大贴图（可能是分划图 / 圆窗遮罩）。"""
    if not isinstance(cube.get("uv"), dict):
        return "box-uv"
    face = visible_face_uv(cube)
    if face is None:
        return "uv?"
    du, dv = abs(face[3]), abs(face[4])
    if du <= 2 and dv <= 2:
        return "solid-texel"
    return "texture %gx%g" % (du, dv)


# ---------------------------------------------------------------------------------------------
# 枪包读取（目录 / zip），以及可选的贴图内容统计
# ---------------------------------------------------------------------------------------------

class Source:
    """一个输入：目录、zip，或单个 json。统一成 name(posix 相对路径) -> bytes。

    目录输入如果落在 ``.../assets/<ns>/geo_models/...`` 里面，会把根提升到 ``assets`` 的上一级，
    这样同一个枪包里的 display / 贴图也能找到（只审计你指的那个子目录）。
    """

    def __init__(self, path):
        self.path = path
        self.zf = None
        self.only = ""
        if os.path.isdir(path):
            self.kind = "dir"
            self.root = os.path.abspath(path)
            m = re.search(r"^(.*?)/assets/[^/]+/geo_models(/|$)", self.root.replace(os.sep, "/"))
            if m and m.group(1):
                self.root = m.group(1).replace("/", os.sep)
                self.only = os.path.relpath(os.path.abspath(path), self.root).replace(os.sep, "/")
        elif zipfile.is_zipfile(path):
            self.kind = "zip"
            self.zf = zipfile.ZipFile(path)
        else:
            self.kind = "file"

    def names(self):
        if self.kind == "zip":
            return sorted(i.filename for i in self.zf.infolist() if not i.filename.endswith("/"))
        if self.kind == "dir":
            out = []
            for root, _dirs, files in os.walk(self.root):
                for name in files:
                    out.append(os.path.relpath(os.path.join(root, name), self.root).replace(os.sep, "/"))
            return sorted(out)
        return [os.path.basename(self.path)]

    def geometry_candidates(self):
        """可能是 geo 模型的 json 名字（zip 只看 geo_models 下；目录看你指的子树）。"""
        for name in self.names():
            if not name.endswith(".json"):
                continue
            if self.kind == "zip" and "/geo_models/" not in "/" + name:
                continue
            if self.kind == "dir" and self.only and not name.startswith(self.only.rstrip("/") + "/"):
                continue
            yield name

    def read(self, name):
        if self.kind == "zip":
            return self.zf.read(name)
        full = self.path if self.kind == "file" else os.path.join(self.root, *name.split("/"))
        with open(full, "rb") as fh:
            return fh.read()


def short_label(name):
    return re.sub(r"^.*?assets/[^/]+/geo_models/", "", name)


def is_lod(name):
    """lod/ 目录下是远景低模副本；分划只在第一人称、用主模型提交，所以默认不统计它们。"""
    return "lod" in name.split("/")[:-1]


def load_geometry(raw):
    try:
        data = json.loads(raw.decode("utf-8-sig"))
    except (ValueError, UnicodeDecodeError):
        return None
    geos = data.get("minecraft:geometry") if isinstance(data, dict) else None
    return geos[0] if isinstance(geos, list) and geos else None


class TextureIndex:
    """geo 模型 id -> 贴图 PNG。靠枪包里的 display/attachments/*.json（model / texture 两个字段）配对。"""

    def __init__(self, source):
        self.source = source
        self.names = set(source.names())
        self.model_to_texture = {}
        for name in self.names:
            m = re.search(r"(^|/)assets/([^/]+)/display/attachments/[^/]+\.json$", name)
            if not m:
                continue
            try:
                disp = json.loads(source.read(name).decode("utf-8-sig"))
                self.model_to_texture[disp["model"]] = disp["texture"]
            except (KeyError, ValueError, UnicodeDecodeError, TypeError):
                continue

    def png_for(self, geo_name):
        """geo 文件在枪包里的相对路径 -> PNG 字节（找不到返回 None）。"""
        m = re.search(r"^(.*?)assets/([^/]+)/geo_models/(.+)\.json$", geo_name)
        if not m:
            return None
        prefix, ns, rest = m.group(1), m.group(2), m.group(3)
        tex_id = self.model_to_texture.get("%s:%s" % (ns, rest))
        if not tex_id or ":" not in tex_id:
            return None
        tns, tpath = tex_id.split(":", 1)
        png = "%sassets/%s/textures/%s.png" % (prefix, tns, tpath)
        return self.source.read(png) if png in self.names else None


_pillow_missing_noted = False


def texture_stats(png_bytes, geo, cube):
    """被丢弃的大平面的贴图内容统计；没有 Pillow / 解不出来时返回 None。"""
    global _pillow_missing_noted
    try:
        from PIL import Image  # 可选依赖
    except ImportError:
        if not _pillow_missing_noted:
            _pillow_missing_noted = True
            print("    (Pillow not installed: texture stats skipped; `pip install pillow` to enable)")
        return None
    face = visible_face_uv(cube)
    if face is None:
        return None
    _name, u, v, du, dv = face
    desc = geo.get("description") or {}
    tw, th = float(desc.get("texture_width") or 16), float(desc.get("texture_height") or 16)
    try:
        img = Image.open(io.BytesIO(png_bytes)).convert("RGBA")
    except Exception:  # noqa: BLE001 - 坏 PNG 一律当作没有统计
        return None
    sx, sy = img.width / tw, img.height / th
    u0, u1 = sorted((u, u + du))
    v0, v1 = sorted((v, v + dv))
    box = (int(round(u0 * sx)), int(round(v0 * sy)), int(round(u1 * sx)), int(round(v1 * sy)))
    if box[2] <= box[0] or box[3] <= box[1]:
        return None
    raw = img.crop(box).tobytes()  # RGBA，每像素 4 字节
    total = len(raw) // 4
    opaque = colored = 0
    for i in range(0, len(raw), 4):
        if raw[i + 3] > 127:
            opaque += 1
            if max(raw[i], raw[i + 1], raw[i + 2]) > 24:
                colored += 1
    return {
        "transparent": 1.0 - opaque / total,
        "black": (opaque - colored) / total,
        "colored_px": colored,
    }


# ---------------------------------------------------------------------------------------------
# 审计主体
# ---------------------------------------------------------------------------------------------

def subtree(bones, root):
    names = {root}
    changed = True
    while changed:
        changed = False
        for name, bone in bones.items():
            if name not in names and bone.get("parent") in names:
                names.add(name)
                changed = True
    return [n for n in bones if n in names]


def has_geometry(bones, root):
    return any(bones[n].get("cubes") for n in subtree(bones, root))


def audit_model(geo):
    """返回 (模式, [cube 记录])；模式 ∈ illuminated / etched / none。"""
    bones = {}
    for bone in geo.get("bones") or []:
        bones.setdefault(bone.get("name"), bone)  # 与 BedrockModel 的 putIfAbsent 一致
    illuminated = [n for n in bones if n and ILLUMINATED.match(n) and has_geometry(bones, n)]
    etched = [n for n in bones if n and ETCHED.match(n) and has_geometry(bones, n)]
    if illuminated:
        mode, roots = "illuminated", illuminated
    elif etched:
        mode, roots = "etched", etched
    else:
        return "none", []
    records = {}
    for root in roots:
        for name in subtree(bones, root):
            for idx, cube in enumerate(bones[name].get("cubes") or []):
                ext = cube_extents(cube)
                if ext is None:
                    continue  # 没有任何声明面：什么都不画，无所谓过不过滤
                records[(name, idx)] = {
                    "bone": name,
                    "size": cube.get("size"),
                    "middle": middle_extent(ext),
                    "rotated": "rotation" in cube,
                    "uv": uv_hint(cube),
                    "cube": cube,
                }
    return mode, list(records.values())


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("paths", nargs="+", help="目录 / geo json / 枪包 zip")
    parser.add_argument("--java", default=DEFAULT_JAVA, help="ReticleMarkFilter.java 路径（读阈值）")
    parser.add_argument("--threshold", type=float, default=None, help="覆盖阈值（假设分析用）")
    parser.add_argument("-v", "--verbose", action="store_true", help="列出每个被丢弃的 cube")
    parser.add_argument("--textures", action="store_true",
                        help="配合 -v：统计被丢弃大平面的贴图内容（需要 Pillow 与 display/attachments 配对）")
    parser.add_argument("--include-lod", action="store_true", help="也统计 lod/ 下的远景低模（默认不统计）")
    args = parser.parse_args()

    threshold = args.threshold if args.threshold is not None else read_threshold(args.java)
    print("threshold (second-longest AABB axis, model units): %g%s" % (
        threshold, "" if args.threshold is None else "  [overridden]"))

    all_rows = []
    models = 0
    with_reticle = 0
    texture_note_printed = False
    for path in args.paths:
        source = Source(path)
        textures = TextureIndex(source) if args.textures and source.kind != "file" else None
        for name in source.geometry_candidates():
            if not args.include_lod and is_lod(name):
                continue
            geo = load_geometry(source.read(name))
            if geo is None:
                continue
            models += 1
            mode, rows = audit_model(geo)
            if mode == "none":
                continue
            with_reticle += 1
            dropped = [r for r in rows if r["middle"] > threshold]
            for r in rows:
                r["file"] = short_label(name)
            all_rows.extend(rows)
            print("%-44s %-11s cubes=%-4d dropped=%d" % (short_label(name), mode, len(rows), len(dropped)))
            if not args.verbose:
                continue
            png = textures.png_for(name) if textures else None
            if args.textures and png is None and not texture_note_printed:
                texture_note_printed = True
                print("    (texture stats unavailable for some models: need a pack dir/zip that contains "
                      "assets/<ns>/display/attachments and textures, plus Pillow)")
            for r in sorted(dropped, key=lambda r: r["middle"]):
                flag = " BORDER" if abs(r["middle"] - threshold) < BORDER_EPS else ""
                line = "    drop %-26s size=%-34s middle=%8.3f %s%s%s" % (
                    r["bone"], r["size"], r["middle"], r["uv"], " rotated-cube" if r["rotated"] else "", flag)
                if png is not None and r["uv"].startswith("texture"):
                    st = texture_stats(png, geo, r["cube"])
                    if st is not None:
                        line += "  [tex: transparent %.1f%%, black %.1f%%, non-black opaque px %d]" % (
                            st["transparent"] * 100, st["black"] * 100, st["colored_px"])
                print(line)

    kept = [r for r in all_rows if r["middle"] <= threshold]
    dropped = [r for r in all_rows if r["middle"] > threshold]
    print("-" * 72)
    print("geo models read: %d, with reticle nodes: %d, reticle cubes in the submitted set: %d" % (
        models, with_reticle, len(all_rows)))
    print("kept: %d, dropped: %d" % (len(kept), len(dropped)))
    if kept:
        top = max(kept, key=lambda r: r["middle"])
        print("largest kept    middle=%.3f  (%s / %s)" % (top["middle"], top["file"], top["bone"]))
    if dropped:
        low = min(dropped, key=lambda r: r["middle"])
        print("smallest dropped middle=%.3f  (%s / %s)" % (low["middle"], low["file"], low["bone"]))
    print("NOTE: offline mirror of the Java filter, not verified in game. See the docstring.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
