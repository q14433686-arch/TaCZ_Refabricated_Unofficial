#!/usr/bin/env python3
"""Align source language files with pinned upstream 1.2.0 for Minecraft 26.3.

No fallback bundle is used on this line. Preserve local-only keys and explicitly
registered View settings / recipe-viewer wording. Adapt Forge tags and converter
version labels. Incorrect hit-fragment descriptions in other locales fall back to
accurate en_us text. See docs/lineage/GAPFILL_26_3_EXECUTION.md.

Usage: python3 scripts/port_upstream_lang.py [--check]
Requires the pinned upstream commit to be fetched; missing refs/files are errors.
"""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = '356bdf9d48f2225bac6ceb56adc5576a418517a8'
LANG = 'src/main/resources/assets/tacz/lang/'
ADAPT = [(re.compile(r'#forge:glass(?!_)'), '#c:glass_blocks')]
ADAPT_KEYS = {'message.tacz.converter.universal.skipped': (re.compile(r'1\.21\.1(?!\d)'), '26.3')}
ABSENT_FEATURE = {
    'config.tacz.client.render.display_gun_vbo', 'config.tacz.client.render.display_gun_vbo.desc',
    'config.tacz.client.render.static_item_gun_vbo', 'config.tacz.client.render.static_item_gun_vbo.desc',
    'config.tacz.common.ammo.sable_compat', 'config.tacz.common.ammo.sable_compat.desc',
}
KEEP_LOCAL = {
    (locale + '.json', key)
    for locale in ('en_us', 'zh_cn', 'zh_tw')
    for key in ('gui.tacz.cloth_config_warning.tips', 'config.tacz.client.render.crosshair_type.desc')
} | {
    (locale + '.json', 'jei.tacz.gun_smith_table.recipe_unavailable')
    for locale in ('en_us', 'zh_cn')
}
HIT_DESCRIPTION = 'config.tacz.client.render.hit_particle_enable.desc'
# This is deliberately independent of whether gun/ammo permits a bullet-hole decal.
OWN_TEXT = {
    ('en_us.json', HIT_DESCRIPTION): 'Spawn basic block hit fragments when bullets hit, independently of whether the gun or ammo allows bullet-hole decals.',
    ('zh_cn.json', HIT_DESCRIPTION): '子弹命中时生成基础方块碎屑，独立于枪械或弹药是否允许弹孔贴花。',
}
FALLBACK_TO_EN = {HIT_DESCRIPTION}
OWN_TEXT_LOCALES = {'en_us.json', 'zh_cn.json'}


def git(*args):
    return subprocess.run(['git', '-C', str(ROOT), *args], capture_output=True, text=True, check=True).stdout


def effective(locale):
    return json.loads((ROOT / LANG / locale).read_text(encoding='utf-8'))


def adapt(key, value):
    for pattern, repl in ADAPT:
        value = pattern.sub(repl, value)
    if key in ADAPT_KEYS:
        pattern, repl = ADAPT_KEYS[key]
        value = pattern.sub(repl, value)
    return value


def build(locale):
    upstream = json.loads(git('show', f'{UPSTREAM}:{LANG}{locale}'))
    local = effective(locale)

    def value(key):
        if (locale, key) in OWN_TEXT:
            return OWN_TEXT[locale, key]
        if (locale, key) in KEEP_LOCAL and key in local:
            return local[key]
        return adapt(key, upstream[key]) if key in upstream else local[key]

    def ship(key):
        return key not in ABSENT_FEATURE and (locale in OWN_TEXT_LOCALES or key not in FALLBACK_TO_EN)

    out = {key: value(key) for key in local if ship(key)}
    for key in upstream:
        if key not in out and ship(key):
            out[key] = value(key)
    return out, local


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--check', action='store_true')
    a = ap.parse_args()
    listing = git('ls-tree', '--name-only', UPSTREAM, LANG).splitlines()
    if not listing:
        raise RuntimeError('No upstream language files found')
    stale = []
    for locale in (p.rsplit('/', 1)[1] for p in listing if p.endswith('.json')):
        built, local = build(locale)
        if built == local:
            continue  # preserve existing formatting; compare language data, not indentation
        stale.append(locale)
        if not a.check:
            (ROOT / LANG / locale).write_text(json.dumps(built, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(('stale: ' if a.check else 'written: ') + (', '.join(stale) or 'none'))
    sys.exit(1 if a.check and stale else 0)


if __name__ == '__main__':
    main()
