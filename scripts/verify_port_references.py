#!/usr/bin/env python3
"""端口引用审计（1.21.11 分支）。

用法::

    python3 scripts/verify_port_references.py                 # 只查本树
    python3 scripts/verify_port_references.py --mc <dir>      # 再核对 -> Minecraft 反编译源码树
    python3 scripts/verify_port_references.py --mc <dir> --strict

检查三件事：

1. **树内 import 必须落地**：`com.tacz.*` / `cn.sh1rocu.*` / `me.xjqsh.*` 的 import
   必须在 `src/main/java` 里有同名文件（含嵌套类 `Outer$Inner` 与包通配）。
2. **Minecraft import 必须存在于接收端**（给了 `--mc` 才查）：`net.minecraft.*` /
   `com.mojang.*` 的类必须是 1.21.11 反编译树里真实存在的类。这条是"26.2 代码直接
   拖过来"最容易漏的一类错误：符号在 26.2 有、1.21.11 没有，编译才发现。
3. **已知 26.2-only 符号不得残留**：见 `DONOR_ONLY`。

退出码：默认恒 0（只报告）；`--strict` 有任何发现则 1。
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src" / "main" / "java"

# 26.2 有、1.21.11 没有的类。移植时必须换成接收端等价物（不是保留后改注释）。
DONOR_ONLY = {
    "ScopeMaskRenderer": "26.2 掩码 pass；本线走深度孔径（ScopeRenderTypes/ScopeDepthCopyState）",
    "ScopeMaskGeometry": "同上",
    "ScopeMaskTarget": "同上",
    "ScopeMaskTextureHandle": "同上",
    "ScopeMaskDebugOverlay": "同上（HUD 调试层）",
    "ScopeBodyRenderTypes": "→ ScopeRenderTypes（本线按渲染类型命名）",
    "ScopePipRenderer": "→ ScopePipRenderState + ScopePipRerender",
    "ScopeTextRenderTypes": "→ ScopeRenderTypes.maskedText",
    "ScopePipTarget": "→ ScopePipRenderState 的场景目标",
    "ScopePipTrace": "→ ScopePipDepthDebug / ScopePipResourceProbe（本线已有等价探针）",
    "Projection": "26.2 的 GameRenderer#hudProjection 类型；1.21.11 用 CachedPerspectiveProjectionMatrixBuffer",
    "ProjectionMatrixBuffer": "→ PerspectiveProjectionMatrixBuffer / CachedPerspectiveProjectionMatrixBuffer",
    "GpuFormat": "→ 1.21.11 的 TextureTarget(name,w,h,useDepth) 不接格式参数",
    "ColorTargetState": "→ RenderPipeline.Builder#withBlend / 默认颜色目标",
    "DepthStencilState": "→ DepthTestFunction + withDepthWrite",
    "BindGroupLayout": "→ RenderPipeline.Builder#withSampler",
    "CompareOp": "→ DepthTestFunction",
    "SubmitNode": "→ SubmitNodeStorage.ModelSubmit / ModelPartSubmit 等具体记录",
    "SimpleFeatureRenderPhase": "1.21.11 的 SubmitNodeCollection 没有 phase 拆分",
    "TranslucentFeatureRenderPhase": "同上",
    "TranslucentSubmit": "同上",
    "PreparedFrame": "1.21.11 用 FeatureRenderDispatcher#renderAllFeatures",
}

IMPORT_RE = re.compile(r"^\s*import\s+(static\s+)?([\w.$]+)\s*;", re.M)

# 本 mod 自己的包前缀（跨树的第三方库不算"树内"）
INTERNAL_PREFIXES = ("com.tacz.", "cn.sh1rocu.", "me.xjqsh.")


def declared_types(java_root: Path) -> set[str]:
    """树内可解析的类全名（含嵌套类写法 Outer$Inner）。"""
    names: set[str] = set()
    for path in java_root.rglob("*.java"):
        rel = path.relative_to(java_root).with_suffix("")
        names.add(".".join(rel.parts))
    return names


def resolve_tree(name: str, tree: set[str]) -> bool:
    if name in tree:
        return True
    # 嵌套类: a.b.Outer.Inner -> a.b.Outer（文件级），或 a.b.Outer$Inner
    parts = name.split(".")
    for i in range(len(parts) - 1, 0, -1):
        if ".".join(parts[:i]) in tree:
            return True
    return False


def mc_types(mc_root: Path) -> tuple[set[str], set[str]]:
    classes: set[str] = set()
    packages: set[str] = set()
    for path in mc_root.rglob("*.java"):
        rel = path.relative_to(mc_root).with_suffix("")
        classes.add(".".join(rel.parts))
        packages.add(".".join(rel.parts[:-1]))
    return classes, packages


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mc", type=Path, default=None, help="1.21.11 反编译源码根目录（client/）")
    ap.add_argument("--strict", action="store_true")
    args = ap.parse_args()

    tree = declared_types(JAVA)
    problems: list[str] = []

    # 2) Minecraft 侧可解析性
    mc_classes: set[str] = set()
    mc_packages: set[str] = set()
    if args.mc and args.mc.is_dir():
        mc_classes, mc_packages = mc_types(args.mc)

    for path in sorted(JAVA.rglob("*.java")):
        text = path.read_text(encoding="utf-8", errors="replace")
        rel = path.relative_to(ROOT)
        for m in IMPORT_RE.finditer(text):
            name = m.group(2)
            if name.startswith("java.") or name.startswith("javax."):
                continue
            if name.startswith(INTERNAL_PREFIXES):
                if not resolve_tree(name, tree):
                    problems.append(f"{rel}: 树内 import 无法落地: {name}")
            elif name.startswith("net.minecraft."):
                # com.mojang.*（serialization/brigadier/authlib/logging）与 lwjgl 等在
                # Minecraft jar 里但不在反编译源码树中，故只核对 net.minecraft.*。
                if not mc_classes:
                    continue
                if resolve_tree(name, mc_classes):
                    continue
                parent = ".".join(name.split(".")[:-1])
                if parent in mc_packages:
                    continue
                problems.append(f"{rel}: 接收端没有这个类: {name}")

    # 3) 26.2-only 符号残留（整词，跳过注释行）
    used: dict[str, list[str]] = {}
    for path in sorted(JAVA.rglob("*.java")):
        rel = path.relative_to(ROOT)
        for lineno, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith(("//", "*", "/*")):
                continue
            # 行尾注释不算引用（例如注释里解释"1.21.11 没有 ColorTargetState"）
            code = line.split("//", 1)[0]
            for sym in DONOR_ONLY:
                if re.search(rf"\b{re.escape(sym)}\b", code):
                    used.setdefault(sym, []).append(f"{rel}:{lineno}")
    for sym, places in sorted(used.items()):
        problems.append(f"26.2-only 符号 {sym}（{DONOR_ONLY[sym]}）仍被引用: " + ", ".join(places[:4]))

    if not problems:
        print("port references: OK")
        return 0
    print(f"port references: {len(problems)} 项")
    for p in problems:
        print("  -", p)
    return 1 if args.strict else 0


if __name__ == "__main__":
    sys.exit(main())
