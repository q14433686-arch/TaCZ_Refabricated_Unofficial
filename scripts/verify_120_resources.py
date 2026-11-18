#!/usr/bin/env python3
"""Verify the import ledger and follow asset references for the migrated default-pack content."""
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'src/main/resources'
PACK = RESOURCES / 'assets/tacz/custom/tacz_default_gun'


def read(path):
    # TaCZ resources use Gson's line comments. Don't remove // inside a quoted value.
    text = re.sub(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*.*?\*/', lambda match:
                  match[0] if match[0].startswith('"') else '', path.read_text(), flags=re.S)
    return json.loads(text)


def asset(identifier, directory, suffix):
    namespace, name = identifier.split(':', 1)
    result = PACK / 'assets' / namespace / directory / (name + suffix)
    assert result.is_file(), f'Missing default-pack asset: {result.relative_to(PACK)}'
    return result


def check_model(part):
    for key, directory, suffix in [('model', 'geo_models', '.json'), ('texture', 'textures', '.png'),
                                   ('slot', 'textures', '.png'), ('hud', 'textures', '.png')]:
        if part.get(key):
            asset(part[key], directory, suffix)


def check_state_machine(identifier, visited):
    if identifier in visited:
        return
    visited.add(identifier)
    lua = asset(identifier, 'scripts', '.lua').read_text()
    for namespace, name in re.findall(r'^\s*local\s+\w+\s*=\s*require\("([^"_]+)_([^"]+)"\)', lua, re.M):
        check_state_machine(f'{namespace}:{name}', visited)


def check_content(index_path):
    kind = index_path.parent.name
    index = read(index_path)
    ns, name = index['data'].split(':', 1)
    data = read(PACK / 'data' / ns / 'data' / kind / (name + '.json'))
    display = read(asset(index['display'], f'display/{kind}', '.json'))
    for part in (display, display.get('lod', {}), display.get('muzzle_flash', {})):
        check_model(part)
    for sound in display.get('sounds', {}).values():
        asset(sound if isinstance(sound, str) else sound['id'], 'tacz_sounds', '.ogg')
    if kind == 'guns':
        ns, name = data['ammo'].split(':', 1)
        assert (PACK / 'data' / ns / 'index/ammo' / (name + '.json')).is_file(), data['ammo']
        check_state_machine(display['state_machine'], set())
        animation = read(asset(display['animation'], 'animations', '.animation.json'))
        for clip in animation['animations'].values():
            for value in clip.get('sound_effects', {}).values():
                for effect in value if isinstance(value, list) else [value]:
                    asset(effect['effect'], 'tacz_sounds', '.ogg')
        if display.get('ads_preset'):
            asset(display['ads_preset'], 'ads_presets', '.json')
    if kind == 'slot_adapters':
        for entry in index['allowed_attachments']:
            ns, name = entry.lstrip('#').split(':', 1)
            directory = 'tacz_tags/attachments' if entry.startswith('#') else 'index/attachments'
            assert (PACK / 'data' / ns / directory / (name + '.json')).is_file(), entry


def main():
    seen = set()
    for line in (ROOT / 'RESOURCE_IMPORT_120.tsv').read_text().splitlines():
        if not line or line.startswith('#'):
            continue
        digest, path = line.split('\t')
        assert path not in seen, f'Duplicate import: {path}'
        assert hashlib.sha256((RESOURCES / path).read_bytes()).hexdigest() == digest, f'Import checksum mismatch: {path}'
        seen.add(path)
    ammo = sorted((PACK / 'data/tacz/index/ammo').glob('*.json'))
    for path in ammo:
        display = read(asset(read(path)['display'], 'display/ammo', '.json'))
        for part in (display, display.get('shell', {}), display.get('entity', {})):
            check_model(part)
    index_root = PACK / 'data/tacz/index'
    content = [p for p in sorted(RESOURCES / path for path in seen)
               if p.is_relative_to(index_root) and p.parent.name in ('guns', 'attachments', 'slot_adapters')]
    for path in content:
        check_content(path)
    print(f'{len(seen)} verified imports; resolved {len(ammo)} ammo and {len(content)} migrated gun/attachment/adapter indexes.')


if __name__ == '__main__':
    main()
