#!/usr/bin/env python3
"""Audit the remapped receiver artifact, not its source tree or historical resource bundle.

This checks packaging invariants only. It is not a substitute for installing the JAR.
"""
import argparse
from collections import Counter
import json
from pathlib import Path
import struct
import zipfile


def audit(path):
    errors = []
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        files = set(names)
        for name, count in Counter(names).items():
            if count > 1:
                errors.append(f'duplicate archive entry: {name}')
        required = {
            'fabric.mod.json', 'tacz.refmap.json', 'tacz.accesswidener',
            'com/tacz/guns/resource/GunPackOverlayResolver.class',
            'com/tacz/guns/resource/network/GunPackSync.class',
            'com/tacz/guns/resource/convert/UniversalPackConverter.class',
            'data/tacz/gunpack_conversion_rules.json',
        }
        errors.extend(f'missing: {name}' for name in sorted(required - files))
        for name in names:
            if name.startswith(('net/minecraft/', 'src/', 'export-test/', 'com/tacz/guns/test/')) or name.endswith('.java'):
                errors.append(f'non-production content: {name}')
            if name.endswith('.class'):
                header = jar.read(name)[:8]
                if len(header) < 8 or header[:4] != b'\xca\xfe\xba\xbe':
                    errors.append(f'invalid class header: {name}')
                elif struct.unpack('>H', header[6:8])[0] > 65:
                    errors.append(f'class requires newer than Java 21: {name}')
        optional_entry = 'com/tacz/guns/compat/zoomify/ZoomifyCompat.class'
        if optional_entry in files and b'dev/isxander/zoomify/' in jar.read(optional_entry):
            errors.append('baseline ZoomifyCompat directly links optional Zoomify classes')
        if 'fabric.mod.json' in files:
            mod = json.loads(jar.read('fabric.mod.json'))
            if '1.21.11' not in mod.get('depends', {}).get('minecraft', ''):
                errors.append('fabric.mod.json is not targeting Minecraft 1.21.11')
            if 'tacz_port_test' == mod.get('id'):
                errors.append('test mod was selected instead of the production mod')
            for entry in mod.get('mixins', []):
                config = entry if isinstance(entry, str) else entry['config']
                if config not in files:
                    errors.append(f'unpackaged mixin config: {config}')
            for entry in mod.get('jars', []):
                if entry['file'] not in files:
                    errors.append(f'unpackaged nested dependency: {entry["file"]}')
        if 'tacz.refmap.json' in files:
            if not json.loads(jar.read('tacz.refmap.json')).get('mappings'):
                errors.append('empty intermediary refmap')
        if 'tacz.accesswidener' in files:
            if jar.read('tacz.accesswidener').decode().splitlines()[0].split()[-1] != 'intermediary':
                errors.append('access widener has not been remapped to intermediary')
        for locale in ('en_us', 'zh_cn'):
            name = f'assets/tacz/lang/{locale}.json'
            if name not in files or 'message.tacz.pack_compatibility.notice' not in json.loads(jar.read(name)):
                errors.append(f'missing conversion notice in effective {locale} locale')
        rules = 'data/tacz/gunpack_conversion_rules.json'
        if rules in files and json.loads(jar.read(rules)).get('target_minecraft') != '1.21.11':
            errors.append('conversion rules target the donor game')
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    args = parser.parse_args()
    errors = audit(args.jar)
    for error in errors:
        print('FAIL:', error)
    if not errors:
        print(f'PASS: {args.jar.name}: Java 21, intermediary AW/refmap, receiver resources, no test/source content')
    return bool(errors)


if __name__ == '__main__':
    raise SystemExit(main())
