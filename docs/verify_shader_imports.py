#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
校验所有自定义 shader 的 #moj_import 目标在目标 MC 版本里真实存在。

背景（1.21.11 移植第 5 号运行期故障）：
    scope_body.vsh 是从 26.2 逐字节抄来的 entity.vsh，里面 import 了
    <minecraft:sample_lightmap.glsl>。这个 include 是 26.x 才有的，
    1.21.11 根本没有。ShaderManager 解析 import 时 Map.get() 返回 null，
    抛 NPE -> "Caught error loading resourcepacks, removing all selected
    resourcepacks" -> 资源重载失败 -> 黑屏。

    编译期查不出来（GLSL 不参与 javac），mixin 校验也查不出来（不是 mixin）。
    只能靠这个脚本。

用法（仓库根目录，需先跑过 ./gradlew help 以填充 Loom 缓存）：
    python3 docs/verify_shader_imports.py
退出码 0 = 全部 OK，1 = 存在悬空 import。
"""
import io
import os
import re
import sys
import glob
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IMPORT_RE = re.compile(r'^\s*#moj_import\s*<\s*([a-z0-9_.-]+)\s*:\s*([^>\s]+)\s*>', re.M)


def find_vanilla_jar():
    pats = [
        os.path.expanduser(
            "~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
            "minecraft-merged/*/minecraft-merged-*.jar"),
        os.path.expanduser(
            "~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
            "minecraft-merged/**/*.jar"),
    ]
    for p in pats:
        hits = [h for h in glob.glob(p, recursive=True) if h.endswith(".jar")]
        if hits:
            return sorted(hits)[-1]
    return None


def collect_vanilla_includes(jar):
    """原版提供的 include：assets/<ns>/shaders/include/<file>"""
    out = set()
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            m = re.match(r'assets/([^/]+)/shaders/include/(.+)$', n)
            if m and not n.endswith('/'):
                out.add((m.group(1), m.group(2)))
    return out


def collect_mod_includes():
    """本模组自己提供的 include（如果有）。"""
    out = set()
    root = os.path.join(REPO, "src", "main", "resources", "assets")
    for ns in os.listdir(root) if os.path.isdir(root) else []:
        inc = os.path.join(root, ns, "shaders", "include")
        for dirpath, _, files in os.walk(inc):
            for f in files:
                rel = os.path.relpath(os.path.join(dirpath, f), inc)
                out.add((ns, rel.replace(os.sep, "/")))
    return out


def mod_shader_files():
    """待检查的 shader：源码树 + 资源 bundle jar 里的。"""
    items = []  # (label, text)
    root = os.path.join(REPO, "src", "main", "resources", "assets")
    for dirpath, _, files in os.walk(root):
        if os.sep + "shaders" + os.sep not in dirpath + os.sep:
            continue
        for f in files:
            if f.rsplit(".", 1)[-1] in ("vsh", "fsh", "glsl", "csh"):
                p = os.path.join(dirpath, f)
                items.append((os.path.relpath(p, REPO),
                              io.open(p, encoding="utf-8", errors="replace").read()))
    # 资源 bundle
    for b in glob.glob(os.path.join(REPO, "resources", "*.jar")):
        with zipfile.ZipFile(b) as z:
            for n in z.namelist():
                if re.match(r'assets/[^/]+/shaders/.+\.(vsh|fsh|glsl|csh)$', n):
                    items.append(("%s!%s" % (os.path.basename(b), n),
                                  z.read(n).decode("utf-8", "replace")))
    return items


def main():
    jar = find_vanilla_jar()
    if not jar:
        print("!! 找不到 Loom 的 minecraft-merged jar。先跑: ./gradlew help --no-daemon")
        return 2
    print("vanilla jar: %s" % os.path.basename(jar))

    available = collect_vanilla_includes(jar) | collect_mod_includes()
    print("可用 include: %d 个" % len(available))
    for ns, f in sorted(available):
        print("    %s:%s" % (ns, f))

    files = mod_shader_files()
    # 源码树优先：同名时 src/main/resources 覆盖 bundle
    seen_names = {}
    for label, _ in files:
        base = label.split("!")[-1]
        seen_names.setdefault(base, []).append(label)

    # 源码树里存在同名文件时，bundle 里的那份会被 processResources 排除掉，
    # 不会进入成品 jar —— 这类命中降级为「已被覆盖」，不算失败。
    src_overrides = set()
    for label, _ in files:
        if "!" not in label:
            norm = label.replace(os.sep, "/")
            if norm.startswith("src/main/resources/"):
                src_overrides.add(norm[len("src/main/resources/"):])

    print("\n检查 %d 个 shader 文件的 import ..." % len(files))
    bad, shadowed = [], []
    checked = 0
    for label, text in files:
        hits = IMPORT_RE.findall(text)
        checked += len(hits)
        in_bundle = "!" in label
        asset = label.split("!")[-1] if in_bundle else None
        overridden = in_bundle and asset in src_overrides
        for ns, fname in hits:
            if (ns, fname) in available:
                continue
            (shadowed if overridden else bad).append((label, ns, fname))

    print("共检查 %d 条 #moj_import。" % checked)

    if shadowed:
        print("\n(i) %d 条悬空 import 位于 bundle 中、但已被 src/main/resources 的同名文件覆盖，"
              "不会进入成品 jar：" % len(shadowed))
        for label, ns, fname in shadowed:
            print("      %s -> <%s:%s>" % (label, ns, fname))

    if not bad:
        print("\n所有会进入成品 jar 的 import 均存在 ✓")
        return 0

    print("\n!! %d 条悬空 import（会导致 ShaderManager NPE / 资源重载失败 / 黑屏）:" % len(bad))
    for label, ns, fname in bad:
        print("  %-70s -> <%s:%s>" % (label, ns, fname))
    return 1


if __name__ == "__main__":
    sys.exit(main())
