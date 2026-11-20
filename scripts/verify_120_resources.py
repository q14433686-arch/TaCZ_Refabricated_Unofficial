#!/usr/bin/env python3
"""Offline CI: verify the complete effective default pack, then traverse its content graph."""
from collections import Counter, defaultdict
from functools import lru_cache
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'src/main/resources'
PACK = RESOURCES / 'assets/tacz/custom/tacz_default_gun'
GAPS = ROOT / 'docs/investigations/DEFAULT_PACK_120_SOURCE_GAPS.tsv'


@lru_cache(maxsize=128)
def read(path):
    # TaCZ resources use Gson's line comments. Don't remove // inside a quoted value.
    text = re.sub(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*.*?\*/', lambda match:
                  match[0] if match[0].startswith('"') else '', path.read_text(encoding='utf-8-sig'), flags=re.S)
    return json.loads(text)


def resolve(root, side, directory, id_, suffix):
    ns, _, path = id_.rpartition(':')
    return root / side / (ns or 'minecraft') / directory / (path + suffix)


def graph(root):
    """Missing file -> referrers, including optional resources. Works on the pinned source too."""
    missing = defaultdict(set)
    a, d = root/'assets/tacz', root/'data/tacz'

    def check(owner, side, directory, id_, suffix='.json'):
        target = resolve(root, side, directory, id_, suffix)
        if not target.is_file():
            missing[target.relative_to(root).as_posix()].add(owner.relative_to(root).as_posix())
        return target

    def part_ref(owner, value):
        if value.startswith('#'):
            check(owner, 'data', 'tacz_tags/attachments', value[1:])
        elif not resolve(root, 'data', 'index/slot_adapters', value, '.json').is_file():
            check(owner, 'data', 'index/attachments', value)

    for p in (d/'index').rglob('*.json'):
        v = read(p)
        for side, key in [('assets', 'display'), ('data', 'data')]:
            if key in v:
                check(p, side, key + '/' + p.parent.name, v[key])
        for id_ in v.get('allowed_attachments', []):
            part_ref(p, id_)
    for p in (d/'data/guns').glob('*.json'):
        v = read(p)
        check(p, 'data', 'index/ammo', v['ammo'])
        if 'script' in v:
            check(p, 'data', 'scripts', v['script'], '.lua')
        for spec in v.get('builtin_attachments', {}).values():
            if isinstance(spec, str):
                part_ref(p, spec)
            else:
                for id_ in spec.get('attachments', []):
                    part_ref(p, id_)
                if spec.get('default_attached'):
                    part_ref(p, spec['default_attached'])
        for id_ in v.get('exclusive_attachments', {}):
            part_ref(p, id_)
    for p in (d/'tacz_tags/attachments').rglob('*.json'):
        for id_ in read(p):
            part_ref(p, id_)
    for p in (a/'display').rglob('*.json'):
        v = read(p)
        for obj in (v, *(v.get(k, {}) for k in ('lod', 'muzzle_flash', 'shell', 'entity'))):
            for key, directory, suffix in [('model', 'geo_models', '.json'), ('texture', 'textures', '.png'),
                                           ('slot', 'textures', '.png'), ('hud', 'textures', '.png')]:
                if obj.get(key):
                    check(p, 'assets', directory, obj[key], suffix)
        for key, directory, suffix in [('state_machine', 'scripts', '.lua'), ('animation', 'animations', '.animation.json'),
                                       ('player_animator_3rd', 'player_animator', '.json'), ('ads_preset', 'ads_presets', '.json')]:
            if v.get(key):
                check(p, 'assets', directory, v[key], suffix)
        for sound in v.get('sounds', {}).values():
            id_ = sound if isinstance(sound, str) else sound['id']
            if id_:  # empty string disables this sound
                check(p, 'assets', 'tacz_sounds', id_, '.ogg')
        if v.get('text_show'):
            geo = read(check(p, 'assets', 'geo_models', v['model']))
            geometries = geo.get('minecraft:geometry', [g for k, g in geo.items() if k.startswith('geometry.')])
            bones = {b['name'] for g in geometries for b in g.get('bones', [])}
            for node in v['text_show'].keys() - bones:
                missing[v['model'] + '#' + node].add(p.relative_to(root).as_posix())
    for p in (a/'animations').glob('*.json'):
        for clip in read(p)['animations'].values():
            for val in clip.get('sound_effects', {}).values():
                for effect in val if isinstance(val, list) else [val]:
                    if effect.get('effect'):
                        check(p, 'assets', 'tacz_sounds', effect['effect'], '.ogg')
    for side in ('assets', 'data'):
        for p in (root/side/'tacz/scripts').glob('*.lua'):
            # Match actual calls, not the explanatory 'require' comments.
            for ns, sub in re.findall(r'''(?m)^\s*local\s+\w+\s*=\s*require\(["']([^"'_]+)_([^"']+)["']\)''', p.read_text()):
                check(p, side, 'scripts', ns + ':' + sub, '.lua')
    recipes = d/'recipe' if (d/'recipe').exists() else d/'recipes'
    for p in recipes.rglob('*.json'):
        v = read(p)['result']
        check(p, 'data', 'index/' + {'attachment': 'attachments', 'gun': 'guns', 'ammo': 'ammo'}[v['type']], v['id'])
        for id_ in v.get('attachments', {}).values():
            part_ref(p, id_)
    for p in (a/'particle_definitions').rglob('*.json'):
        texture = read(p)['particle_effect']['description']['basic_render_parameters']['texture']
        check(p, 'assets', 'textures', texture, '.png')
    return sorted('\t'.join((key, ','.join(sorted(refs)))) for key, refs in missing.items())


def main():
    for line in (ROOT/'RESOURCE_IMPORT_120.tsv').read_text().splitlines():
        if line and not line.startswith('#'):
            digest, path = line.split('\t')
            assert hashlib.sha256((RESOURCES/path).read_bytes()).hexdigest() == digest, path
    seen = set()
    for line in (ROOT/'DEFAULT_PACK_120.tsv').read_text().splitlines():
        if line.startswith('#'):
            continue
        path, digest, blob, rule, source = line.split('\t')
        assert path not in seen, path
        data = (PACK/path).read_bytes()
        assert hashlib.sha256(data).hexdigest() == digest, path
        if rule == 'source':
            assert hashlib.sha1(f'blob {len(data)}\0'.encode() + data).hexdigest() == blob, path
        seen.add(path)
    files = {p.relative_to(PACK).as_posix() for p in PACK.rglob('*') if p.is_file()}
    assert files == seen, sorted(files ^ seen)
    expected = {line for line in GAPS.read_text().splitlines() if line and not line.startswith('#')}
    actual = set(graph(PACK))
    assert expected == actual, 'Reference graph changed: ' + '\n'.join(sorted(expected ^ actual)[:10])
    counts = Counter(p.parent.name for p in (PACK/'data/tacz/index').rglob('*.json'))
    assert counts == {'guns': 70, 'ammo': 25, 'attachments': 126, 'slot_adapters': 5, 'blocks': 3}, counts
    for p in (PACK/'data/tacz/advancement').glob('*.json'):
        obj = read(p)
        if 'parent' in obj:
            assert resolve(PACK, 'data', 'advancement', obj['parent'], '.json').is_file(), p
    print(f'{len(files)} pinned default-pack files; {sum(counts.values())} indexes; {len(actual)} documented upstream reference gaps.')


if __name__ == '__main__':
    main()
