#!/usr/bin/env python3
"""Check pinned resource bytes and the native default-pack ammo index/display asset references."""
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
            for key, directory, suffix in [('model', 'geo_models', '.json'), ('texture', 'textures', '.png'), ('slot', 'textures', '.png')]:
                if part.get(key):
                    asset(part[key], directory, suffix)
    print(f'{len(seen)} verified imports; {len(ammo)} ammo indexes resolve their model, slot, entity and shell assets.')


if __name__ == '__main__':
    main()
