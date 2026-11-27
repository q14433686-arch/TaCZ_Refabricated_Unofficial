#!/usr/bin/env python3
"""Three-way interface-surface alignment: upstream 1.21.1 vs 26.3 vs this tree (read-only).

Path lists say little about behaviour; the dangerous gaps are surfaces a gun pack, addon or
server sees. This compares, per surface, the sets of literal identifiers in each tree:

  serialized   @SerializedName("...") literals (gun-pack JSON schema)
  config       ForgeConfigSpec .define*/defineInRange/defineEnum/defineList keys
  api          public static methods of com.tacz.guns.api.TimelessAPI
  lua          public methods of Lua-exposed animation/gun-logic contexts
  events       class names under com/tacz/guns/api/event
  lang         en_us keys of tacz + lrtactical
  registry     string ids passed to register helpers in init/ (items, blocks, entities, ...)
  tags         data/*/tags/** file paths (normalised to singular folders)
  payloads     CustomPacketPayload TYPE ids ("tacz:..." / "lrtactical:...")
  commands     literal("...") command words

Usage:  python3 scripts/align_three_way.py [--upstream REF] [--r263 REF] [--local REF] [--surface NAME]
Default refs: upstream 356bdf9 (fetch Sh1roCu/TACZ-Refabricated 1.21.1 first), c5d536b7b1e61bcacb0cd660df73de78b7db6796, HEAD.
Output: one TSV row per identifier that is NOT present in all three trees.
"""
import argparse
import re
import subprocess
import sys

UPSTREAM = '356bdf9d48f2225bac6ceb56adc5576a418517a8'


def git(*args):
    return subprocess.run(['git', *args], capture_output=True, text=True, check=True).stdout


def files(ref, prefix, suffix=''):
    out = git('ls-tree', '-r', '--name-only', ref, '--', prefix)
    return [p for p in out.splitlines() if p.endswith(suffix)]


def blob(ref, path):
    return subprocess.run(['git', 'show', f'{ref}:{path}'], capture_output=True, text=True, check=True).stdout


def strip_comments(src):
    return re.sub(r'//[^\n]*', '', re.sub(r'/\*.*?\*/', '', src, flags=re.S))


def java_sources(ref, prefix='src/main/java'):
    for p in files(ref, prefix, '.java'):
        yield p, strip_comments(blob(ref, p))


def s_serialized(ref):
    out = set()
    for _, src in java_sources(ref):
        for m in re.finditer(r'@SerializedName\(\s*(?:value\s*=\s*)?"([^"]+)"(?:\s*,\s*alternate\s*=\s*\{([^}]*)\})?', src):
            out.add(m.group(1))
            if m.group(2):
                out.update(re.findall(r'"([^"]+)"', m.group(2)))
    return out


def s_config(ref):
    out = set()
    for p, src in java_sources(ref, 'src/main/java/com/tacz/guns/config'):
        for m in re.finditer(r'\.define(?:InRange|Enum|List|ListAllowEmpty)?\(\s*"([^"]+)"', src):
            out.add(m.group(1))
    return out


def s_api(ref):
    src = strip_comments(blob(ref, 'src/main/java/com/tacz/guns/api/TimelessAPI.java'))
    return set(re.findall(r'public\s+static\s+[\w<>\[\],.? ]+?\s+(\w+)\s*\(', src))


def s_lua(ref):
    out = set()
    for p, src in java_sources(ref):
        if not re.search(r'(StateContext|GunLogicContext|LuaGunLogic|ScriptContext)\w*\.java$', p) \
                and 'api/client/animation/statemachine' not in p:
            continue
        cls = p.rsplit('/', 1)[-1][:-5]
        for name in re.findall(r'\n\s*public\s+(?!class|interface|enum|record|static\s+class)[\w<>\[\],.? ]+?\s+(\w+)\s*\(', src):
            if name != cls:
                out.add(f'{cls}#{name}')
    return out


def s_events(ref):
    return {p.rsplit('/', 1)[-1][:-5] for p in files(ref, 'src/main/java/com/tacz/guns/api/event', '.java')}


def s_lang(ref):
    import json
    out = set()
    for ns in ('tacz', 'lrtactical'):
        raw = blob(ref, f'src/main/resources/assets/{ns}/lang/en_us.json')
        if raw:
            try:
                out.update(f'{ns}:{k}' for k in json.loads(raw))
            except ValueError:
                out.add(f'{ns}:<unparseable en_us.json>')
    return out


def s_registry(ref):
    out = set()
    for p, src in java_sources(ref):
        if '/init/' not in p and not p.endswith(('ModItems.java', 'ModBlocks.java')):
            continue
        kind = p.rsplit('/', 1)[-1][:-5]
        # any helper whose first argument is a bare id literal (register/regiser/storage/item/...)
        for m in re.finditer(r'\b\w+\(\s*"([a-z0-9_./]+)"', src):
            out.add(f'{kind}:{m.group(1)}')
    return out


def s_tags(ref):
    out = set()
    for p in files(ref, 'src/main/resources/data'):
        m = re.match(r'src/main/resources/data/([^/]+)/tags/(.+)\.json$', p)
        if m:
            path = m.group(2)
            for a, b in (('items/', 'item/'), ('blocks/', 'block/'), ('entity_types/', 'entity_type/'),
                         ('damage_types/', 'damage_type/'), ('fluids/', 'fluid/')):
                if path.startswith(a):
                    path = b + path[len(a):]
            out.add(f'{m.group(1)}:{path}')
    return out


def s_payloads(ref):
    """Payload message classes (wire ids may legitimately differ between MC lines)."""
    out = set()
    for p, src in java_sources(ref):
        if 'CustomPacketPayload' not in src:
            continue
        outer = p.rsplit('/', 1)[-1][:-5]
        for name in re.findall(r'Type<\s*(\w+)\s*>\s+TYPE\s*=', src):
            out.add(outer if name == outer else f'{outer}.{name}')
    return out


def s_commands(ref):
    out = set()
    for p, src in java_sources(ref):
        if '/command' in p:
            out.update(re.findall(r'\bliteral\(\s*"([^"]+)"', src))
    return out


SURFACES = {n[2:]: f for n, f in globals().items() if n.startswith('s_')}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--upstream', default=UPSTREAM)
    ap.add_argument('--r263', default='c5d536b7b1e61bcacb0cd660df73de78b7db6796')
    ap.add_argument('--local', default='HEAD')
    ap.add_argument('--surface', action='append', choices=sorted(SURFACES))
    ap.add_argument('--summary', action='store_true', help='only per-surface counts')
    a = ap.parse_args()
    refs = {'upstream': a.upstream, '26.3': a.r263, 'local': a.local}
    print('surface\tidentifier\tupstream\t26.3\tlocal\tclass')
    summary = []
    for name in a.surface or sorted(SURFACES):
        sets = {k: SURFACES[name](r) for k, r in refs.items()}
        allids = set().union(*sets.values())
        counts = {}
        for ident in sorted(allids):
            u, t, l = (ident in sets[k] for k in ('upstream', '26.3', 'local'))
            if u and t and l:
                continue
            cls = ('MISSING_LOCAL(both)' if u and t and not l else
                   'MISSING_LOCAL(upstream-only)' if u and not t and not l else
                   'MISSING_LOCAL(26.3-only)' if t and not u and not l else
                   'local+26.3 (not upstream)' if t and l and not u else
                   'local+upstream (not 26.3)' if u and l and not t else
                   'local-only')
            counts[cls] = counts.get(cls, 0) + 1
            if not a.summary:
                print(name, ident, *('x' if v else '-' for v in (u, t, l)), cls, sep='\t')
        summary.append((name, {k: len(v) for k, v in sets.items()}, counts))
    for name, sizes, counts in summary:
        print(f'# {name}: sizes {sizes}; ' + ', '.join(f'{k}={v}' for k, v in sorted(counts.items())), file=sys.stderr)


if __name__ == '__main__':
    main()
