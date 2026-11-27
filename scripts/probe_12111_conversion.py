"""Operator-command test in the disposable production server. Never installs or edits a player's pack."""
import hashlib
import json
from pathlib import Path
import shutil
from zipfile import ZipFile

BASE = 'data/convert_probe/'
LAYER = 'compat_1_21_11/'


def legacy_files():
    meta = {'namespace': 'convert_probe', 'dependencies': {'minecraft': '[1.20.1],[1.21.1]'},
            'overlays': {'entries': [{'minecraft': '[1.21.1]', 'directory': 'older'}]}}
    table = {'type': 'tacz:gun_smith_table_crafting', 'materials': [{'item': {'type': 'forge:partial_nbt',
             'item': 'minecraft:stick', 'nbt': '{marked:1b}'}, 'count': 2}], 'result': {'type': 'ammo',
             'id': 'tacz:12g', 'count': 5, 'nbt': "{reloaded:7b,AttachmentSCOPE:{id:'tacz:attachment',Count:1b,tag:{AttachmentId:'demo:sight'}}}"}}
    paper = {'type': 'minecraft:crafting_shapeless', 'ingredients': [{'item': 'minecraft:dirt'}],
             'result': {'item': 'minecraft:paper', 'count': 2}}
    loot = {'type': 'minecraft:command', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item',
            'name': 'minecraft:stick', 'functions': [{'function': 'minecraft:set_nbt', 'tag': '{delivered:9b}'}]}]}]}
    files = {'gunpack.meta.json': meta, 'older/assets/convert_probe/lang/en_us.json': {'older': 'keep'},
             BASE+'recipes/shells.json': table, BASE+'recipes/off.json': dict(table, conditions=[{'type': 'forge:false'}]),
             BASE+'recipes/paper.json': paper, BASE+'recipes/unsupported.json': {'type': 'example:unknown'},
             BASE+'recipes/empty_tag.json': dict(paper, conditions=[{'type': 'forge:tag_empty', 'tag': 'demo:empty'}]),
             BASE+'recipes/missing.json': dict(paper, ingredients=[{'item': 'absent:part'}], conditions=[{'type': 'forge:mod_loaded', 'modid': 'absent'}]),
             BASE+'loot_tables/reward.json': loot}
    return {n: json.dumps(v).encode() for n, v in files.items()}


def run_conversion(instance, server, wait_for, evidence):
    root, out = instance/'tacz', instance/'tacz-converted'
    directory = root/'legacy-dir'
    archive = root/'legacy.zip'
    files = legacy_files()
    for name, data in files.items():
        p = directory/name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(data)
    with ZipFile(archive, 'x') as z:
        for name, data in files.items():
            z.writestr(name, data)
    original_zip = archive.read_bytes()
    reports = evidence/'conversion'
    reports.mkdir(exist_ok=True)

    def command(pack, mode, expected):
        seen = set(out.glob('*.json'))
        server.stdin.write(f'tacz convert universal {pack} {mode}\n')
        server.stdin.flush()
        def received():
            for p in sorted(set(out.glob('*.json')) - seen):
                try:
                    report = json.loads(p.read_bytes())
                except json.JSONDecodeError:  # worker hasn't closed its report yet
                    continue
                shutil.copyfile(p, reports/p.name)
                if report['source'] != pack or report['status'] != expected:
                    raise AssertionError(f'{pack} {mode}: {report}')
                return report
        wait_for(received, [server], 70, f'{pack} {mode} report')
        return received()

    for name, mode in [(directory.name, 'check'), (archive.name, 'export')]:
        r = command(name, mode, 'partial')
        assert r['targetVersions'] == ['1.20.1', '1.21.11'] and r['targetDataVersion'] == 4671
        assert r['targetFormatValidated'] and not r['gameValidated'] and r['registryValidationDeferred']
        assert r['missingItemIds'] == ['absent:part'] and r['skippedRecipes'] == 2 and r['convertedRecipes'] == 4, r
        assert {s['code'] for s in r['skippedFiles']} == {'TAG_EMPTY', 'RECIPE_ADAPTER'}
        if mode == 'check':
            assert not list(out.glob('*.zip')), 'check must only write a report'
    exported = out/r['output']
    first_bytes = exported.read_bytes()
    with ZipFile(exported) as z:
        for n, b in files.items():
            if n != 'gunpack.meta.json':
                assert z.read(n) == b, n
        meta = json.loads(z.read('gunpack.meta.json'))
        assert meta['dependencies']['minecraft'] == '[1.20.1],[1.21.1],[1.21.11]'
        assert meta['overlays']['entries'][-1] == {'directory': 'compat_1_21_11', 'minecraft': '[1.21.11]'}
        def recipe(name): return json.loads(z.read(LAYER+BASE+'recipe/'+name+'.json'))
        assert recipe('missing')['ingredients'] == ['absent:part']
        assert recipe('unsupported')['fabric:load_conditions'] == {'condition': 'fabric:not', 'value': {'condition': 'fabric:true'}}
        assert recipe('paper')['result'] == {'id': 'minecraft:paper', 'count': 2}
    r = command(archive.name, 'export', 'failed')
    assert any(d['code'] == 'OUTPUT_EXISTS' for d in r['diagnostics']) and exported.read_bytes() == first_bytes
    assert archive.read_bytes() == original_zip and {p.relative_to(directory).as_posix(): p.read_bytes()
            for p in directory.rglob('*') if p.is_file()} == files
    shutil.rmtree(directory)
    archive.unlink()
    # Install only this exact exported ZIP in the server's live pack directory; client has no copy.
    installed = root/'converted.zip'
    shutil.copyfile(exported, installed)
    r = command(installed.name, 'check', 'skipped')
    assert any(d['code'] == 'EXISTING_OVERLAY' for d in r['diagnostics'])
    print(f'::notice::PORT_CONVERT: operator dir check, ZIP export, output collision and existing layer; '
          f'DFU 3465 -> 4671; 4 converted, 2 disabled, absent:part preserved; ZIP SHA-256 {hashlib.sha256(first_bytes).hexdigest()}', flush=True)
