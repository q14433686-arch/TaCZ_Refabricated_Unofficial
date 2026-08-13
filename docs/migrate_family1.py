#!/usr/bin/env python3
"""
Family 1: GuiGraphicsExtractor (26.1.2) -> GuiGraphics (1.21.11).

26.1 renamed GuiGraphics to GuiGraphicsExtractor AND renamed many of its methods
to the short "extract" style. Going back to 1.21.11 needs both undone.

Every mapping below was verified with javap against BOTH jars:
  1.21.11 : ~/.gradle/caches/fabric-loom/minecraftMaven/.../minecraft-merged-1.21.11-*.jar
  26.1.2  : repo/.gradle/loom-cache/.../minecraft-merged-0d09a28b48-26.1.2.jar
"""
import re, glob, sys, collections

# --- method renames on a GuiGraphicsExtractor receiver -----------------------
# 26.1.2 name -> 1.21.11 name. Overload sets collapse to one name on both sides,
# and argument lists are identical, so a receiver-scoped name swap is sufficient.
METHOD_MAP = {
    "text":              "drawString",       # (Font,String|Component|FormattedCharSequence,int,int,int[,boolean])
    "centeredText":      "drawCenteredString",
    "textWithWordWrap":  "drawWordWrap",
    "textWithBackdrop":  "drawStringWithBackdrop",
    "item":              "renderItem",
    "fakeItem":          "renderFakeItem",
    "itemDecorations":   "renderItemDecorations",
    "outline":           "renderOutline",
    "horizontalLine":    "hLine",
    "verticalLine":      "vLine",
    "tooltip":           "renderTooltip",
    "map":               "submitMapRenderState",
    "entity":            "submitEntityRenderState",
    "skin":              "submitSkinRenderState",
    "book":              "submitBookModelRenderState",
    "bannerPattern":     "submitBannerPatternRenderState",
    "sign":              "submitSignRenderState",
    "profilerChart":     "submitProfilerChartRenderState",
}
# unchanged (verified present with same descriptors in 1.21.11):
#   fill, fillGradient, blit, blitSprite, pose, guiWidth, guiHeight,
#   enableScissor, disableScissor, containsPointInScissor, textHighlight,
#   nextStratum, blurBeforeThisStratum, setTooltipForNextFrame,
#   setComponentTooltipForNextFrame, textRenderer, getSprite, requestCursor

# --- Gui.class mixin target renames (26.1 "extract*" -> 1.21.11 "render*") ---
GUI_TARGET_MAP = {
    "extractRenderState": "render",
    "extractCrosshair":   "renderCrosshair",
    "extractSlot":        "renderSlot",
}

TYPE_OLD = "GuiGraphicsExtractor"
TYPE_NEW = "GuiGraphics"
IMPORT_OLD = "net.minecraft.client.gui.GuiGraphicsExtractor"
IMPORT_NEW = "net.minecraft.client.gui.GuiGraphics"

stats = collections.Counter()


def receiver_vars(src: str):
    """Names of locals/params/fields declared as GuiGraphicsExtractor."""
    v = set(re.findall(r'\bGuiGraphicsExtractor\s+(\w+)', src))
    v |= set(re.findall(r'\bGuiGraphicsExtractor\)\s*\(Object\)\s*(this)\b', src))
    return v


def migrate(path: str) -> bool:
    src = open(path, encoding='utf-8').read()
    if TYPE_OLD not in src:
        return False
    orig = src

    vars_ = receiver_vars(src)

    # 1. rename methods invoked on those receivers
    for var in sorted(vars_):
        for old, new in METHOD_MAP.items():
            pat = re.compile(r'(\b' + re.escape(var) + r'\s*\.\s*)' + re.escape(old) + r'(\s*\()')
            src, n = pat.subn(lambda m: m.group(1) + new + m.group(2), src)
            if n:
                stats[f'method {old}->{new}'] += n

    # 1b. ((GuiGraphicsExtractor) (Object) this).foo(...)
    for old, new in METHOD_MAP.items():
        pat = re.compile(r'(\)\s*\(Object\)\s*this\s*\)\s*\.\s*)' + re.escape(old) + r'(\s*\()')
        src, n = pat.subn(lambda m: m.group(1) + new + m.group(2), src)
        if n:
            stats[f'method(this) {old}->{new}'] += n

    # 2. Gui mixin @Inject(method="extract*") targets
    if 'Gui.class' in src or 'GuiGraphicsExtractor.class' in src:
        for old, new in GUI_TARGET_MAP.items():
            pat = re.compile(r'(method\s*=\s*")' + re.escape(old) + r'(")')
            src, n = pat.subn(lambda m: m.group(1) + new + m.group(2), src)
            if n:
                stats[f'mixin-target {old}->{new}'] += n

    # 3. the type itself (import + every usage)
    src, n = re.subn(re.escape(IMPORT_OLD), IMPORT_NEW, src)
    if n:
        stats['import'] += n
    src, n = re.subn(r'\b' + re.escape(TYPE_OLD) + r'\b', TYPE_NEW, src)
    if n:
        stats['type'] += n

    if src != orig:
        open(path, 'w', encoding='utf-8').write(src)
        return True
    return False


def main():
    files = sorted(glob.glob('src/main/java/**/*.java', recursive=True))
    changed = [f for f in files if migrate(f)]
    print(f"changed {len(changed)} files")
    for k, v in sorted(stats.items(), key=lambda kv: -kv[1]):
        print(f"  {v:4d}  {k}")


if __name__ == '__main__':
    sys.exit(main())
