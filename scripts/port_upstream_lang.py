#!/usr/bin/env python3
"""Bring every shipped TACZ language file up to upstream 1.2.0 (Sh1roCu/TACZ-Refabricated 356bdf9).

Effective product language = src/main/resources/assets/tacz/lang/<l>.json when present, otherwise the
copy inside resources/tacz-26.1.2-source-and-resource-bundle.jar (see build.gradle processResources).
Before this script, 18 locales shipped only from that 26.1.2 bundle and lacked 161 upstream keys each.

Rules (each one is recorded in docs/lineage/UPSTREAM_120_ALIGNMENT_12111.md):
  * every upstream key gets the upstream value; existing keys keep their position and new upstream keys
    are appended in upstream order (keeps review diffs to real changes);
  * keys that exist only locally (1.21.11 features, lrtactical, probes) are kept unchanged;
  * a locale whose bundled copy already matches is not duplicated into src/main/resources;
  * ADAPT rewrites strings that name another loader's tag namespace or Minecraft line;
  * ABSENT_FEATURE keys are not shipped because the feature/config they label is not in this port;
  * KEEP_LOCAL keeps a local value where upstream's text misdescribes the shared behaviour;
  * FALLBACK_TO_EN drops such a key from the other locales, so they show the accurate en_us text
    instead of a translation of the wrong description (no verified translation exists).

Usage: python3 scripts/port_upstream_lang.py [--check]
--check exits 1 if any effective language file differs from what this script would write.
"""
import argparse
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = '356bdf9d48f2225bac6ceb56adc5576a418517a8'
LANG = 'src/main/resources/assets/tacz/lang/'
BUNDLE = ROOT / 'resources/tacz-26.1.2-source-and-resource-bundle.jar'

ADAPT = [
    # Fabric has no forge: tag namespace; the config comment and default use the c: convention tag.
    (re.compile(r'#forge:glass(?!_)'), '#c:glass_blocks'),
]
# The universal converter writes a 1.21.11 overlay on this line (UniversalPackConverter).
ADAPT_KEYS = {'message.tacz.converter.universal.skipped': (re.compile(r'1\.21\.1(?!\d)'), '1.21.11')}
ABSENT_FEATURE = {
    'config.tacz.client.render.display_gun_vbo', 'config.tacz.client.render.display_gun_vbo.desc',
    'config.tacz.client.render.static_item_gun_vbo', 'config.tacz.client.render.static_item_gun_vbo.desc',
    'config.tacz.common.ammo.sable_compat', 'config.tacz.common.ammo.sable_compat.desc',
}
# Both upstream and this port spawn hit fragments whether or not the bullet-hole decal is enabled
# (upstream HitBlockEffectHandler / local BulletHoleParticle.Provider call the spawner first).
KEEP_LOCAL = {('en_us.json', 'config.tacz.client.render.hit_particle_enable.desc'),
              ('zh_cn.json', 'config.tacz.client.render.hit_particle_enable.desc')}
FALLBACK_TO_EN = {'config.tacz.client.render.hit_particle_enable.desc'}
OWN_TEXT_LOCALES = {'en_us.json', 'zh_cn.json'}


def git_json(ref, path):
    out = subprocess.run(['git', '-C', str(ROOT), 'show', f'{ref}:{path}'], capture_output=True, text=True)
    return json.loads(out.stdout) if out.returncode == 0 else None


def effective(locale):
    source = ROOT / LANG / locale
    if source.is_file():
        return json.loads(source.read_text(encoding='utf-8'))
    with zipfile.ZipFile(BUNDLE) as bundle:
        return json.loads(bundle.read('assets/tacz/lang/' + locale))


def adapt(key, value):
    for pattern, repl in ADAPT:
        value = pattern.sub(repl, value)
    if key in ADAPT_KEYS:
        pattern, repl = ADAPT_KEYS[key]
        value = pattern.sub(repl, value)
    return value


def build(locale):
    upstream = git_json(UPSTREAM, LANG + locale)
    local = effective(locale)
    def value(key):
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
    listing = subprocess.run(['git', '-C', str(ROOT), 'ls-tree', '--name-only', UPSTREAM, LANG],
                             capture_output=True, text=True, check=True).stdout.split()
    stale = []
    for locale in (p.rsplit('/', 1)[1] for p in listing):
        built, local = build(locale)
        target = ROOT / LANG / locale
        if not target.is_file() and built == local:
            continue  # the bundled copy already is the aligned text
        text = json.dumps(built, ensure_ascii=False, indent=2) + '\n'
        current = target.read_text(encoding='utf-8') if target.is_file() else None
        if current == text:
            continue
        stale.append(locale)
        if not a.check:
            target.write_text(text, encoding='utf-8')
    print(('stale: ' if a.check else 'written: ') + (', '.join(stale) or 'none'))
    sys.exit(1 if a.check and stale else 0)


if __name__ == '__main__':
    main()
