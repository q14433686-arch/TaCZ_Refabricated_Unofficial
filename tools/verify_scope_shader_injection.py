#!/usr/bin/env python3
"""Offline verification for ScopeShaderInjector's GLSL string surgery.

Why this file exists
--------------------
``com.tacz.guns.client.render.scope.ScopeShaderInjector`` rewrites the fragment source of
Iris' *hand* programs at link time (see docs/RENDER_PIPELINE_SCOPE_AUDIT_26_1_2.md section 6.9).
A mistake there does not fail the build and does not throw: it produces a shader that compiles
on one driver, miscompiles on the next, and corrupts the depth buffer on a third. That is
precisely the class of bug section 6.9 is about, so the surgery needs a test that can run
without a GPU and without launching Minecraft.

This script is a line-by-line port of the Java string surgery plus the assertions that matter:

  * declarations land after the whole preprocessor prologue (never between #version and
    #extension, which is a hard error on Mesa/AMD and a silent warning on NVIDIA);
  * gl_FragDepth is written on *every* path, so the static assignment can never leave the
    fragment depth undefined (GLSL 3.30 section 7.2 / 4.60 section 7.1.4);
  * the ocular mask's ``discard`` is the last statement of main(), after the pack's shading and
    after the alpha test Iris appends, so no derivative runs in divergent control flow;
  * only programs whose Iris name starts with ``hand`` are touched - terrain, entities, block
    entities, particles, sky, shadow and composite sources must come back byte for byte;
  * anything unparsable degrades to "return the source untouched" instead of emitting a
    half-rewritten shader;
  * the transformation is idempotent and brace-balanced, and removing the injected blocks
    restores the input exactly.

Usage
-----
    python3 tools/verify_scope_shader_injection.py                 # synthetic cases only
    python3 tools/verify_scope_shader_injection.py --pack <dir>    # also run a real pack

``--pack`` points at an unpacked shader pack's ``shaders`` directory (for example an extracted
ComplementaryUnbound_r5.8.1.zip). The script preprocesses ``world0/gbuffers_hand.fsh`` with the
system C preprocessor the way Iris' jcpp stage does, dresses the result the way Iris'
transformer does, and runs the same assertions on the real thing. If ``tree_sitter`` and
``tree_sitter_glsl`` are installed it also checks that the injection adds no new parse errors.

KEEP IN SYNC with ScopeShaderInjector.java. The Java file is the source of truth; this port
exists so the behaviour can be exercised where no JDK/GPU is available.
"""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

# ---------------------------------------------------------------------------------------------
# Port of ScopeShaderInjector
# ---------------------------------------------------------------------------------------------

MODE_UNIFORM = "tacz_DepthRestoreMode"
MARKER = "tacz_ScopeMaskMode"
APERTURE_SAMPLER_UNIFORM = "tacz_ApertureDepthSampler"
IRIS_WORLD_DEPTH_UNIFORM = "depthtex2"
HAND_PROGRAM_PREFIX = "hand"
MIN_GLSL_VERSION = 130

MAIN_PATTERN = re.compile(r"(?<![0-9A-Za-z_])void\s+main\s*\(\s*(?:void\s*)?\)\s*\{")
VERSION_PATTERN = re.compile(r"#version\s+(\d+)")


def is_hand_program(name: str | None) -> bool:
    return name is not None and name.lower().startswith(HAND_PROGRAM_PREFIX)


def mask_comments_and_strings(source: str) -> str:
    out = list(source)
    length = len(out)
    i = 0
    while i < length:
        c = out[i]
        if c == "/" and i + 1 < length and out[i + 1] == "/":
            while i < length and out[i] != "\n":
                out[i] = " "
                i += 1
        elif c == "/" and i + 1 < length and out[i + 1] == "*":
            out[i] = " "
            i += 1
            out[i] = " "
            i += 1
            while i < length:
                if out[i] == "*" and i + 1 < length and out[i + 1] == "/":
                    out[i] = " "
                    i += 1
                    out[i] = " "
                    i += 1
                    break
                if out[i] != "\n":
                    out[i] = " "
                i += 1
        elif c == '"':
            out[i] = " "
            i += 1
            while i < length and out[i] != '"' and out[i] != "\n":
                out[i] = " "
                i += 1
            if i < length and out[i] == '"':
                out[i] = " "
                i += 1
        else:
            i += 1
    return "".join(out)


def glsl_version(masked: str) -> int:
    match = VERSION_PATTERN.search(masked)
    return int(match.group(1)) if match else -1


def after_preprocessor_prologue(masked: str) -> int:
    length = len(masked)
    index = 0
    while index < length:
        line_end = masked.find("\n", index)
        limit = length if line_end < 0 else line_end
        line = masked[index:limit].strip()
        if line and line[0] != "#":
            return index
        if line_end < 0:
            return length
        index = line_end + 1
        while line.endswith("\\") and index < length:
            next_end = masked.find("\n", index)
            next_limit = length if next_end < 0 else next_end
            line = masked[index:next_limit].strip()
            index = length if next_end < 0 else next_end + 1
    return length


def match_closing_brace(masked: str, open_brace: int) -> int:
    depth = 0
    for i in range(open_brace, len(masked)):
        if masked[i] == "{":
            depth += 1
        elif masked[i] == "}":
            depth -= 1
            if depth == 0:
                return i
    return -1


def _is_identifier_char(c: str) -> bool:
    return c == "_" or c.isalnum()


def declares_identifier(masked: str, source: str, identifier: str) -> bool:
    start = 0
    while True:
        at = masked.find(identifier, start)
        if at < 0:
            return False
        after = at + len(identifier)
        if (at == 0 or not _is_identifier_char(source[at - 1])) and (
            after >= len(source) or not _is_identifier_char(source[after])
        ):
            return True
        start = at + 1


def build_declarations(declares_depthtex2: bool) -> str:
    return (
        "\n// ---- TACZ ocular scope branches (hand programs only; dormant for ordinary draws) ----\n"
        + "uniform int " + MODE_UNIFORM + ";\n"
        + "uniform int " + MARKER + ";\n"
        + "uniform sampler2D " + APERTURE_SAMPLER_UNIFORM + ";\n"
        + ("" if declares_depthtex2 else "uniform sampler2D " + IRIS_WORLD_DEPTH_UNIFORM + ";\n")
        + "// ---- end TACZ scope declarations ----\n"
    )


def build_main_prologue() -> str:
    return (
        "\n    // ---- TACZ scope: dormant unless ScopeDepthCopyState enables a mode ----\n"
        + "    bool tacz_scopeMaskDiscard = false;\n"
        + "    gl_FragDepth = gl_FragCoord.z;\n"
        + "    if (" + MODE_UNIFORM + " != 0) {\n"
        + "        vec2 tacz_restoreUv = gl_FragCoord.xy / max(vec2(textureSize("
        + IRIS_WORLD_DEPTH_UNIFORM + ", 0)), vec2(1.0));\n"
        + "        gl_FragDepth = textureLod("
        + IRIS_WORLD_DEPTH_UNIFORM + ", tacz_restoreUv, 0.0).r;\n"
        + "        return;\n"
        + "    }\n"
        + "    if (" + MARKER + " != 0) {\n"
        + "        vec2 tacz_maskWorldUv = gl_FragCoord.xy / max(vec2(textureSize("
        + IRIS_WORLD_DEPTH_UNIFORM + ", 0)), vec2(1.0));\n"
        + "        vec2 tacz_maskApertureUv = gl_FragCoord.xy / max(vec2(textureSize("
        + APERTURE_SAMPLER_UNIFORM + ", 0)), vec2(1.0));\n"
        + "        float tacz_maskWorldDepth = textureLod("
        + IRIS_WORLD_DEPTH_UNIFORM + ", tacz_maskWorldUv, 0.0).r;\n"
        + "        float tacz_maskApertureDepth = textureLod("
        + APERTURE_SAMPLER_UNIFORM + ", tacz_maskApertureUv, 0.0).r;\n"
        + "        tacz_scopeMaskDiscard = !(tacz_maskApertureDepth < tacz_maskWorldDepth - 1.0e-6);\n"
        + "    }\n"
        + "    // ---- end TACZ scope prologue ----\n"
    )


def build_main_epilogue() -> str:
    return (
        "\n    // ---- TACZ scope: ocular mask, last so the pack's derivatives stay uniform ----\n"
        + "    if (tacz_scopeMaskDiscard) { discard; }\n"
    )


def patch_fragment(name: str | None, source: str | None):
    """Returns (result, bail_out_reason). ``reason is None`` means the source was patched."""
    if not source or not is_hand_program(name) or MARKER in source:
        return source, "not a hand program / empty / already patched"

    masked = mask_comments_and_strings(source)

    version = glsl_version(masked)
    if version < MIN_GLSL_VERSION:
        return source, f"GLSL version {version} is below {MIN_GLSL_VERSION}"
    for qualifier in ("depth_any", "depth_greater", "depth_less", "depth_unchanged"):
        if qualifier in masked:
            return source, "the pack already constrains gl_FragDepth with a layout qualifier"

    match = MAIN_PATTERN.search(masked)
    if not match:
        return source, "no parsable main()"
    open_brace = match.end() - 1
    close_brace = match_closing_brace(masked, open_brace)
    if close_brace < 0:
        return source, "main() has unbalanced braces"

    declaration_pos = after_preprocessor_prologue(masked)
    if declaration_pos > match.start():
        return source, "the preprocessor prologue overlaps main()"

    declarations = build_declarations(declares_identifier(masked, source, IRIS_WORLD_DEPTH_UNIFORM))
    prologue = build_main_prologue()
    epilogue = build_main_epilogue()

    patched = (
        source[:declaration_pos]
        + declarations
        + source[declaration_pos:open_brace + 1]
        + prologue
        + source[open_brace + 1:close_brace]
        + epilogue
        + source[close_brace:]
    )
    return patched, None


# ---------------------------------------------------------------------------------------------
# Assertions
# ---------------------------------------------------------------------------------------------

FAILURES: list[str] = []


def check(condition: bool, message: str) -> None:
    print(("  PASS  " if condition else "  FAIL  ") + message)
    if not condition:
        FAILURES.append(message)


def strip_injection(patched: str) -> str:
    """Removes the three injected blocks; the result must equal the original source."""
    decl_start = patched.index("\n// ---- TACZ ocular scope branches")
    decl_end = patched.index("// ---- end TACZ scope declarations ----\n") + len(
        "// ---- end TACZ scope declarations ----\n")
    out = patched[:decl_start] + patched[decl_end:]

    pro_start = out.index("\n    // ---- TACZ scope: dormant")
    pro_end = out.index("// ---- end TACZ scope prologue ----\n") + len(
        "// ---- end TACZ scope prologue ----\n")
    out = out[:pro_start] + out[pro_end:]

    epi_start = out.index("\n    // ---- TACZ scope: ocular mask")
    epi_end = out.index("if (tacz_scopeMaskDiscard) { discard; }\n") + len(
        "if (tacz_scopeMaskDiscard) { discard; }\n")
    return out[:epi_start] + out[epi_end:]


def assert_well_formed(label: str, source: str, patched: str) -> None:
    print(label)
    check("#extension" not in patched or
          patched.index("uniform int " + MODE_UNIFORM + ";") > patched.rindex("#extension"),
          "declarations land after the last #extension")
    head = patched[:patched.rindex("#extension")] if "#extension" in patched else ""
    check(all(line.strip() == "" or line.strip().startswith("#") for line in head.splitlines()),
          "no non-preprocessor token precedes any #extension")

    for uniform, kind in ((MODE_UNIFORM, "int"), (MARKER, "int"),
                          (APERTURE_SAMPLER_UNIFORM, "sampler2D"),
                          (IRIS_WORLD_DEPTH_UNIFORM, "sampler2D")):
        found = len(re.findall(rf"uniform\s+{kind}\s+{uniform}\s*;", patched))
        check(found == 1, f"uniform {uniform} declared exactly once (found {found})")

    seed = patched.index("gl_FragDepth = gl_FragCoord.z;")
    restore = patched.index("gl_FragDepth = textureLod(" + IRIS_WORLD_DEPTH_UNIFORM)
    check(seed < restore, "gl_FragDepth is seeded before the restore branch, i.e. on every path")
    check(patched.count("gl_FragDepth") == source.count("gl_FragDepth") + 2,
          "gl_FragDepth is written exactly twice more than the pack does by itself")

    prologue = patched[seed:patched.index("// ---- end TACZ scope prologue ----")]
    check("texture(" not in prologue, "injected depth fetches use textureLod (no implicit LOD)")
    check(prologue.count("textureLod(") == 3, "three textureLod fetches (1 restore + 2 mask)")

    mask = patched.index("if (tacz_scopeMaskDiscard) { discard; }")
    check(patched[mask + len("if (tacz_scopeMaskDiscard) { discard; }"):].strip() == "}",
          "the mask discard is the very last statement of main()")
    check(patched.count("{") == source.count("{") + 3, "exactly three new brace pairs")
    check(patched.count("{") == patched.count("}"), "braces balanced")
    check(strip_injection(patched) == source,
          "removing the injected blocks reproduces the source byte for byte")

    again, _ = patch_fragment("hand_cutout", patched)
    check(again == patched, "patching an already patched source is a no-op (idempotent)")


NON_HAND_PROGRAMS = [
    "terrain_solid", "terrain_cutout", "entities_cutout", "entities_translucent",
    "block_entities", "particles", "particles_translucent", "clouds", "weather", "sky_basic",
    "sky_textured", "basic", "lines", "damaged_block", "text", "text_intensity", "glint",
    "shadow_terrain_cutout", "shadow_entities_cutout", "shadow_basic", "sodium_terrain",
    "composite1", "final", "deferred1",
]

HAND_PROGRAMS = [
    "hand_cutout", "hand_cutout_bright", "hand_cutout_diffuse", "hand_text",
    "hand_text_translucent", "hand_text_intensity", "hand_translucent", "hand_water_bright",
    "hand_water_diffuse",
]

SYNTHETIC = """#version 330 core
#extension GL_ARB_shader_texture_lod : enable
#extension GL_ARB_explicit_attrib_location : enable
// post-Iris transform output, shaped like a Complementary gbuffers_hand
/* a decoy inside a comment:
void main() {
   with a stray brace } that must not steer the injection
*/
uniform sampler2D gtexture;
uniform sampler2D noisetex;
uniform float iris_currentAlphaTest;
layout (location = 0) out vec4 iris_FragData0;
layout (location = 1) out vec4 iris_FragData1;
in vec2 texCoord;
in vec4 glColor;

vec2 ComputeTexelOffset(sampler2D tex, vec2 coord) {
    vec2 halfPixel = 0.5 / vec2(textureSize(tex, 0));
    return halfPixel * sign(dFdx(coord) + dFdy(coord));
}

void main() {
    vec4 color = texture(gtexture, texCoord) * glColor;
    float alphaCheck = color.a;
    alphaCheck = max(fwidth(color.a), alphaCheck); // non-nvidia edge artifact fix
    if (alphaCheck < 0.001) discard;
    vec3 shaded = color.rgb * (1.0 - 0.5 * ComputeTexelOffset(gtexture, texCoord).x);
    /* DRAWBUFFERS:01 */
    iris_FragData0 = vec4(shaded, color.a);
    iris_FragData1 = vec4(0.0);
\tif (iris_FragData0.a < iris_currentAlphaTest) {
\t\tdiscard;
\t}
}
"""


def run_synthetic() -> None:
    print("== synthetic Iris hand fragment ==")
    patched, reason = patch_fragment("hand_cutout", SYNTHETIC)
    check(reason is None, f"hand_cutout is patched (reason={reason})")
    assert_well_formed("-- structure", SYNTHETIC, patched)

    fwidth_at = patched.index("fwidth(color.a)")
    check(fwidth_at < patched.index("if (tacz_scopeMaskDiscard)"),
          "the pack's fwidth() still runs before our discard, i.e. in uniform control flow")

    print()
    print("== program name filter ==")
    for program in NON_HAND_PROGRAMS:
        out, _ = patch_fragment(program, SYNTHETIC)
        check(out == SYNTHETIC, f"{program:<24} untouched")
    for program in HAND_PROGRAMS:
        out, _ = patch_fragment(program, SYNTHETIC)
        check(out != SYNTHETIC, f"{program:<24} patched")

    print()
    print("== bail-outs return the source untouched ==")
    malformed = [
        ("legacy #version 120", SYNTHETIC.replace("#version 330 core", "#version 120")),
        ("no #version at all", SYNTHETIC.replace("#version 330 core\n", "")),
        ("depth layout qualifier",
         SYNTHETIC.replace("in vec4 glColor;",
                           "layout (depth_greater) out float gl_FragDepth;\nin vec4 glColor;")),
        ("no main()", SYNTHETIC.replace("void main() {", "void notMain() {")),
        ("truncated main()", SYNTHETIC[:SYNTHETIC.index("void main() {") + 40]),
    ]
    for label, source in malformed:
        out, reason = patch_fragment("hand_cutout", source)
        check(out == source and reason is not None, f"{label:<24} -> untouched ({reason})")

    print()
    print("== pack that declares depthtex2 itself ==")
    source = SYNTHETIC.replace("uniform sampler2D noisetex;",
                               "uniform sampler2D noisetex;\nuniform sampler2D depthtex2;")
    out, reason = patch_fragment("hand_water_diffuse", source)
    check(reason is None, "patched")
    check(len(re.findall(r"uniform\s+sampler2D\s+depthtex2\s*;", out)) == 1,
          "depthtex2 is not declared a second time")


# ---------------------------------------------------------------------------------------------
# Optional: run against a real shader pack
# ---------------------------------------------------------------------------------------------

def preprocess_pack(shaders_dir: Path) -> str | None:
    """Expands <shaders>/world0/gbuffers_hand.fsh the way Iris' jcpp stage does."""
    entry = shaders_dir / "world0" / "gbuffers_hand.fsh"
    if not entry.is_file():
        print(f"  !! {entry} not found, skipping the real-pack check")
        return None
    if shutil.which("cpp") is None:
        print("  !! no C preprocessor on PATH, skipping the real-pack check")
        return None

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp) / "shaders"
        shutil.copytree(shaders_dir, root)
        # Optifine/Iris resolve #include "/x" against the shaders root; make that work with cpp -I.
        for path in root.rglob("*"):
            if path.is_file():
                try:
                    text = path.read_text(encoding="utf-8", errors="ignore")
                except OSError:
                    continue
                if '#include "/' in text:
                    path.write_text(text.replace('#include "/', '#include "'), encoding="utf-8")
        source = (root / "world0" / "gbuffers_hand.fsh").read_text(encoding="utf-8",
                                                                   errors="ignore")
        # #version is not a C directive; strip it and let the caller add Iris' 330 core back.
        body = "\n".join(line for line in source.splitlines()
                         if not line.lstrip().startswith("#version"))
        stage = Path(tmp) / "hand.glsl"
        stage.write_text(body, encoding="utf-8")
        result = subprocess.run(
            ["cpp", "-P", "-undef", "-nostdinc", "-I", str(root),
             "-DIS_IRIS", "-DMC_VERSION=12100", "-DMC_GL_VERSION=330", str(stage)],
            capture_output=True, text=True)
        if result.returncode != 0:
            print("  !! preprocessing failed:\n" + result.stderr[:500])
            return None
        return result.stdout


def glsl_parse_errors(source: str):
    try:
        from tree_sitter import Language, Parser
        import tree_sitter_glsl
    except ImportError:
        return None
    parser = Parser(Language(tree_sitter_glsl.language()))
    tree = parser.parse(source.encode())
    errors = []

    def walk(node):
        if node.type == "ERROR" or node.is_missing:
            errors.append(node.start_point[0] + 1)
        for child in node.children:
            walk(child)

    walk(tree.root_node)
    return errors


def run_pack(shaders_dir: Path) -> None:
    print()
    print(f"== real shader pack: {shaders_dir} ==")
    body = preprocess_pack(shaders_dir)
    if body is None:
        return
    iris_like = ("#version 330 core\n"
                 "#extension GL_ARB_explicit_attrib_location : enable\n"
                 + body)
    close = iris_like.rindex("}")
    iris_like = (iris_like[:close]
                 + "\tif (iris_FragData0.a < iris_currentAlphaTest) {\n\t\tdiscard;\n\t}\n"
                 + iris_like[close:])
    print(f"  preprocessed gbuffers_hand: {len(iris_like.splitlines())} lines, "
          f"{iris_like.count('gl_FragDepth')} gl_FragDepth use(s) by the pack itself")

    patched, reason = patch_fragment("hand_cutout", iris_like)
    check(reason is None, f"the real hand fragment is patched (reason={reason})")
    if reason is not None:
        return
    assert_well_formed("-- structure", iris_like, patched)

    before = glsl_parse_errors(iris_like)
    if before is None:
        print("  (install tree_sitter and tree_sitter_glsl for the parse check)")
    else:
        after = glsl_parse_errors(patched)
        check(len(after) == len(before),
              f"injection adds no new GLSL parse errors ({len(before)} -> {len(after)})")

    for program in ("terrain_cutout", "entities_cutout", "shadow_terrain_cutout"):
        out, _ = patch_fragment(program, iris_like)
        check(out == iris_like, f"{program:<24} untouched")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--pack", type=Path, default=None,
                        help="path to an unpacked shader pack's 'shaders' directory")
    args = parser.parse_args()

    run_synthetic()
    if args.pack is not None:
        run_pack(args.pack)

    print()
    print(f"FAILURES: {len(FAILURES)}")
    for failure in FAILURES:
        print("  - " + failure)
    return 1 if FAILURES else 0


if __name__ == "__main__":
    sys.exit(main())
