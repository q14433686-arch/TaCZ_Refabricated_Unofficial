#!/usr/bin/env python3
"""Reproduce the shipped 26.2 default pack from a local Git object database (no network).

Check is the default. --write updates only the built-in pack and its provenance ledger;
never point this at a player's exported pack. No generated 1.20.1/1.21.1 overlays in the jar.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
REL = Path('src/main/resources/assets/tacz/custom/tacz_default_gun')
PACK = ROOT / REL
PIN = '356bdf9d48f2225bac6ceb56adc5576a418517a8'
LEDGER = ROOT / 'DEFAULT_PACK_120.tsv'
OVERLAY = 'compat_1_21_1/'
# Removed upstream, not 26.2 additions. In particular OEM replacements are no longer craftable.
# Never delete arbitrary extras.
RETIRED = {f'data/tacz/recipe/attachments/oem_stock_{n}.json' for n in ('light', 'heavy', 'tactical')} | {
    f'assets/tacz/tacz_sounds/rpk/rpk_{n}.ogg' for n in ('inspect', 'reload_empty', 'reload_tactical')} | {
    'assets/tacz/textures/attachment/uv/grip_magpul_afg_2_n.png',
    'assets/tacz/textures/attachment/uv/sight_sro_dot_n.png'}


def effective_paths(repo):
    tree = subprocess.check_output(['git', '-C', repo, 'ls-tree', '-rz', PIN, '--', str(REL)])
    base, overlay = {}, {}
    for row in tree.rstrip(b'\0').split(b'\0'):
        attr, name = row.decode().split('\t', 1)
        name = Path(name).relative_to(REL).as_posix()
        blob = attr.split()[2]
        if name.startswith(OVERLAY):
            overlay[name.removeprefix(OVERLAY)] = (name, blob)
        else:
            dest = name.replace('data/tacz/recipes/', 'data/tacz/recipe/').replace('data/tacz/advancements/', 'data/tacz/advancement/')
            base[dest] = (name, blob)
    # Every legacy-format file must have a component-era counterpart. Extras are not silently skipped.
    assert all(k in overlay for k, (src, _) in base.items() if k != src)
    return base | overlay


def encode(value):
    return (json.dumps(value, indent=2, ensure_ascii=False) + '\n').encode()


def advancement(data):
    obj = json.loads(data)
    display = obj['display']
    if 'background' in display:
        display['background'] = display['background'].replace(':textures/', ':').removesuffix('.png')
    for criterion in obj['criteria'].values():
        trigger = criterion['trigger']
        c = criterion.get('conditions', {})
        if trigger == 'minecraft:recipe_crafted':
            if c['recipe_id'] == 'minecraft:armor_dye':
                c['recipe_id'] = 'tacz:glow_gun_pegboard_dye'
        elif trigger == 'minecraft:item_used_on_block':
            # 26.2 ContextAwarePredicate is a condition list, not a 26.3 holder.
            c['location'].append({'condition': 'minecraft:match_tool', 'predicate': c.pop('item')})
        elif trigger == 'minecraft:player_killed_entity':
            for field in ('player', 'entity'):
                if field not in c:
                    continue
                pred = {}
                for key, value in c[field].items():
                    if key == 'nbt' and value == '{FallFlying: 1b}':
                        pred['minecraft:flags'] = {'is_fall_flying': True}
                    elif key in ('type', 'equipment', 'distance'):
                        pred['minecraft:' + ('entity_type' if key == 'type' else key)] = value
                    else:
                        raise ValueError(f'Unmapped entity predicate: {key}')
                c[field] = [{'condition': 'minecraft:entity_properties', 'entity': 'this', 'predicate': pred}]
    return encode(obj)


def adapt(name, data):
    if name.startswith('data/tacz/recipe/'):
        obj = json.loads(data)
        for mat in obj['materials']:
            spec = mat['item']
            if set(spec) == {'tag'}:
                mat['item'] = '#' + spec['tag']
            elif set(spec) == {'item'}:
                mat['item'] = spec['item']
            else:
                raise ValueError(f'{name}: unmapped ingredient {spec}')
        return encode(obj), '26.2-ingredient'
    if name.startswith('data/tacz/advancement/'):
        return advancement(data), '26.2-advancement'
    if name.startswith('data/tacz/tacz_loot_injectors/'):
        obj = json.loads(data)
        for pool in obj['pools']:
            for entry in pool['entries']:
                for mod in entry['functions']:
                    if mod['function'] == 'minecraft:set_count':
                        mod['count']['type'] = 'minecraft:uniform'
        return encode(obj), '26.2-loot'
    if name == 'gunpack.meta.json':
        return encode({'namespace': 'tacz', 'dependencies': {'minecraft': '[26.2]'}}), '26.2-metadata'
    if name == 'README.txt':
        return b'''TaCZ default pack for Minecraft 26.2 (Fabric). See assets/tacz/gunpack_info.json for credits and license.
The effective common assets and data are shipped once. Recipes, advancements, loot and
workbench icons use the 26.2 base directories; no 1.21.1 overlay is required.
To keep local changes, enable DefaultPackDebug in tacz/tacz-pre.toml before updating.
To regenerate the bundled pack, back up local edits, disable DefaultPackDebug, remove
tacz/tacz_default_gun and restart the game.\n''', '26.2-readme'
    if name in ('assets/tacz/scripts/default_state_machine.lua', 'assets/tacz/scripts/default_state_machine_v2.lua'):
        # Preserve this branch's early ADS walk transition, including scripts inheriting v2.
        old = b'elseif (context:getAimingProgress() > 0.5) then'
        run = b'context:runAnimation("walk_aiming", track, true, LOOP, 0.3)'
        assert data.count(old) == data.count(run) == 1
        return data.replace(old, b'elseif (context:isAiming() or context:getAimingProgress() > 0.05) then').replace(
            run, run + b'\n            context:anchorWalkDist()'), 'native-ads-walk'
    if name.startswith('assets/tacz/scripts/'):
        # A dozen upstream scripts copied the put-away branch but never allocated a switch-mode
        # track. Do not stop track zero (Java int coercion of Lua nil), or invent a mode animation.
        stop = b'        context:stopAnimation(context:getTrack(STATIC_TRACK_LINE, SWITCH_MODE_TRACK))'
        if stop in data and b'local SWITCH_MODE_TRACK =' not in data:
            return data.replace(stop, b'        -- No switch-mode track on this rig.'), 'unallocated-track'
    return data, 'source'


def adapted_reload_scripts(repo, effective):
    """Apply the reviewed Lua diff to the five original blobs, never to a player's installed files."""
    patch = ROOT / 'scripts/patches/default-pack-120-reloads.patch'
    paths = [f'data/tacz/scripts/{name}.lua' for name in
             ('hk_mk23_logic', 'kar98_gun_logic', 'm1014_gun_logic', 'm1866_gun_logic', 'spas_12_gun_logic')]
    with tempfile.TemporaryDirectory() as tmp:
        base = Path(tmp)
        for name in paths:
            dst = base / name
            dst.parent.mkdir(parents=True, exist_ok=True)
            dst.write_bytes(subprocess.check_output(['git', '-C', repo, 'cat-file', 'blob', effective[name][1]]))
        subprocess.run(['git', 'apply', str(patch)], cwd=base, check=True)
        return {p: (base / p).read_bytes() for p in paths}


def run(repo, write):
    effective = effective_paths(repo)
    scripts = adapted_reload_scripts(repo, effective)
    rows = ['# Sh1roCu/TACZ-Refabricated ' + PIN,
            '# destination relative to built-in pack\tSHA-256\tupstream git blob\tadaptation (source = verbatim)\tsource path (= if unchanged)']
    kinds = Counter()
    errors = []
    # One persistent git reader, even for the 1,704 sound clips.
    with subprocess.Popen(['git', '-C', repo, 'cat-file', '--batch'], stdin=subprocess.PIPE, stdout=subprocess.PIPE) as git:
        for dest, (src, blob) in sorted(effective.items()):
            git.stdin.write((blob + '\n').encode()); git.stdin.flush()
            _, kind, size = git.stdout.readline().split()
            assert kind == b'blob'
            data = git.stdout.read(int(size)); assert git.stdout.read(1) == b'\n'
            converted, rule = (scripts[dest], 'native-reload-accounting') if dest in scripts else adapt(dest, data)
            kinds[rule] += 1
            rows.append('\t'.join((dest, hashlib.sha256(converted).hexdigest(), blob, rule, '=' if dest == src else src)))
            path = PACK / dest
            if not path.is_file() or path.read_bytes() != converted:
                if write:
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(converted)
                else:
                    errors.append(dest)
        git.stdin.close()
    for p in PACK.rglob('*'):
        if p.is_file() and (name := p.relative_to(PACK).as_posix()) not in effective:
            if write and name in RETIRED:
                p.unlink()
            else:
                errors.append('extra: ' + name)
    expected = '\n'.join(rows) + '\n'
    if write:
        LEDGER.write_text(expected)
    elif LEDGER.read_text() != expected:
        errors.append(LEDGER.name)
    if errors:
        sys.exit('Default pack differs: ' + ', '.join(errors[:20]) + f' ({len(errors)} files)')
    print(f'{len(effective)} effective paths from {PIN}: {dict(kinds)}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('upstream', help='local upstream repository containing the pinned commit')
    parser.add_argument('--write', action='store_true', help='materialize the pack; by default verify only')
    args = parser.parse_args()
    run(args.upstream, args.write)
