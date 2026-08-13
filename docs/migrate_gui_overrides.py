#!/usr/bin/env python3
"""
26.1 renamed the whole vanilla GUI "render*" family to "extract*".
1.21.11 uses the render* names, so every override/super-call must be renamed back.

CRITICAL: only names that are genuinely vanilla overrides may be touched.
These extract* names are NOT vanilla renames and must be left alone:
  extractItem      - TACZ's own IItemHandler API (CombinedInvWrapper/InvWrapper)
  extractIndices   - TACZ's own glTF accessor helper
  extractArgument  - genuine 1.21.11 SpecialModelRenderer#extractArgument (verified)
  extractRotatedQuad - genuine 1.21.11 SingleQuadParticle#extractRotatedQuad (verified)
  extract          - genuine 1.21.11 SingleQuadParticle#extract (verified)

Each mapping below was confirmed by javap on BOTH jars: the 26.1.2 class has the
extract* name and the 1.21.11 class has the render* name with the same descriptor.
"""
import re, glob, collections

RENAMES = {
    # Screen
    'extractRenderState':            'render',
    'extractRenderStateWithTooltipAndSubtitles': 'renderWithTooltipAndSubtitles',
    'extractBackground':             'renderBackground',
    'extractBlurredBackground':      'renderBlurredBackground',
    'extractPanorama':               'renderPanorama',
    'extractMenuBackground':         'renderMenuBackground',
    'extractMenuBackgroundTexture':  'renderMenuBackgroundTexture',
    # AbstractContainerScreen
    'extractContents':               'renderContents',
    'extractCarriedItem':            'renderCarriedItem',
    'extractSnapbackItem':           'renderSnapbackItem',
    'extractSlots':                  'renderSlots',
    'extractTooltip':                'renderTooltip',
    'extractFloatingItem':           'renderFloatingItem',
    'extractLabels':                 'renderLabels',
    'extractBg':                     'renderBg',
    # AbstractWidget / AbstractButton
    'extractWidgetRenderState':      'renderWidget',
    'extractScrollingStringOverContents': 'renderScrollingStringOverContents',
    'extractDefaultLabel':           'renderDefaultLabel',
    'extractDefaultSprite':          'renderDefaultSprite',
    # AbstractSelectionList
    'extractListSeparators':         'renderListSeparators',
    'extractListBackground':         'renderListBackground',
    'extractListItems':              'renderListItems',
    'extractSelection':              'renderSelection',
    # list entry
    'extractContent':                'renderContent',
    # ClientTooltipComponent
    'extractText':                   'renderText',
    'extractImage':                  'renderImage',
}

# Never rename these, even though they start with "extract".
KEEP = {'extractItem', 'extractIndices', 'extractArgument',
        'extractRotatedQuad', 'extract', 'extractSlot'}

stats = collections.Counter()
changed = set()

for f in glob.glob('src/main/java/**/*.java', recursive=True):
    src = orig = open(f, encoding='utf-8').read()
    for old, new in RENAMES.items():
        if old in KEEP:
            continue
        # method declarations, calls, super calls, and method references
        pat = re.compile(r'(?<![\w.])' + re.escape(old) + r'(?=\s*\()')
        src, n1 = pat.subn(new, src)
        pat2 = re.compile(r'(?<=::)' + re.escape(old) + r'\b')
        src, n2 = pat2.subn(new, src)
        if n1 + n2:
            stats[f'{old} -> {new}'] += n1 + n2
            changed.add(f)
    if src != orig:
        open(f, 'w', encoding='utf-8').write(src)

print(f'changed {len(changed)} files')
for k, v in stats.most_common():
    print(f'  {v:4d}  {k}')
