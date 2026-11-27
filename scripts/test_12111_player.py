#!/usr/bin/env python3
"""Run one remapped 1.21.11 display, PAL or controller client, preserving native screenshots even on failure."""
import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request

from test_12111_connection import ROOT, copy_project, launch, runtime_command


IRIS_SEED = {
    # Pinned exactly as in the 26.1.2 optics workflow: Complementary Reimagined r5.8.1.
    'commit': '08e1c2ada5eaf2fc36f08516c316b3d3c3677d8e',
    'sha256': 'ba80162db7752712ce7cb50b20b4fb101d3489254badc52ee2a5a79b7d790798',
    'name': 'ComplementaryReimagined_r5.8.1.zip',
}


def modrinth(path):
    """Read-only Modrinth API call. The version list is recorded; nothing is loaded from 'latest' by name."""
    import json
    request = urllib.request.Request('https://api.modrinth.com/v2' + path, headers={'User-Agent': 'TaCZ-12111-port-probe (github CI)'})
    with urllib.request.urlopen(request, timeout=90) as response: return json.load(response)


def download_checked(url, sha1, target):
    import urllib.request as request
    data = request.urlopen(request.Request(url, headers={'User-Agent': 'TaCZ-12111-port-probe (github CI)'}), timeout=180).read()
    digest = hashlib.sha1(data).hexdigest()
    if digest != sha1: raise AssertionError(f'{target.name}: SHA-1 {digest} != {sha1}')
    target.write_bytes(data)
    return hashlib.sha256(data).hexdigest()


def prepare_zoomify_seed(work, evidence):
    """Install the exact target Zoomify release and its declared required dependency closure.
    Record each resolved release and content hashes; never substitute a fake Zoomify class."""
    import json
    target = work/'client/fixtures/platform-zoomify/mods'
    target.mkdir(parents=True, exist_ok=True)
    versions = modrinth('/project/zoomify/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%221.21.11%22%5D')
    root = next(v for v in versions if v['version_number'] == '2.15.2+1.21.11')
    hashes = {
        '2.15.2+1.21.11': '642b498a6802ad3ed7f37e25556139f5f3cb6ee634927695cec70c7c3fa20312',
        '1.13.8+kotlin.2.3.0': '760953d8d3d1fa32a353593d744e2917d8a91ca8923ae9518b5ef2676a631002',
        '3.8.2+1.21.11-fabric': '49c279bc28c80ca17d6c21c3dd299ec1c115b892272f21b3f034ed782829babd',
    }
    queue, seen, receipt = [root], set(), []
    while queue:
        version = queue.pop(0)
        if version['project_id'] in seen: continue
        seen.add(version['project_id'])
        if len(seen)>12: raise AssertionError('Unexpected Zoomify dependency closure')
        file = next((f for f in version['files'] if f.get('primary')), version['files'][0])
        sha = download_checked(file['url'], file['hashes']['sha1'], target/file['filename'])
        if hashes.get(version['version_number']) != sha:
            raise AssertionError(f"Unaudited Zoomify combination: {version['version_number']} / {sha}")
        receipt.append({'id': version['id'], 'project': version['project_id'], 'version': version['version_number'], 'file': file['filename'], 'sha256': sha})
        print(f"::notice::PORT_ZOOMIFY_MOD: {version['version_number']} {file['filename']} SHA-256 {sha}", flush=True)
        for dep in version['dependencies']:
            if dep.get('dependency_type') != 'required' or not dep.get('project_id'): continue
            project = modrinth('/project/'+dep['project_id'])
            if project['slug'] in ['fabric-api', 'fabricloader']: continue
            if dep.get('version_id'): chosen = modrinth('/version/'+dep['version_id'])
            else:
                available = modrinth('/project/'+dep['project_id']+'/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%221.21.11%22%5D')
                # Zoomify's own 2.15.2 tag builds with this FLK (loader >=0.16.9).
                # Today's FLK 1.14.2 instead requires loader >=0.19.5; do not change
                # the product loader to satisfy an accidentally selected newest helper.
                pinned = {'fabric-language-kotlin': '1.13.8+kotlin.2.3.0',
                          'yacl': '3.8.2+1.21.11-fabric', 'yet-another-config-lib': '3.8.2+1.21.11-fabric'}.get(project['slug'])
                chosen = next(v for v in available if v['version_number']==pinned) if pinned else next(v for v in available if v['version_type']=='release')
            queue.append(chosen)
    (evidence/'zoomify-runtime.json').write_text(json.dumps(receipt, indent=2)+'\n')


def prepare_iris_seed(work, evidence):
    """Shader pack + the Iris/Sodium pair that Modrinth's 1.21.11 Iris release requires.

    The pinned CurseForge pair in gradle.properties (Iris 1.10.7 / Sodium 0.8.13) is rejected by the loader:
    Sodium 0.8.13 declares that it breaks Iris <=1.10.7. Only the official dependency edge is used here."""
    import io, tarfile, zipfile
    seed = work/'client/fixtures/optics-iris'
    (seed/'shaderpacks').mkdir(parents=True, exist_ok=True); (seed/'config').mkdir(parents=True, exist_ok=True)
    (seed/'mods').mkdir(parents=True, exist_ok=True)
    url = f"https://codeload.github.com/ComplementaryDevelopment/ComplementaryReimagined/tar.gz/{IRIS_SEED['commit']}"
    data = urllib.request.urlopen(url, timeout=120).read()
    digest = hashlib.sha256(data).hexdigest()
    if digest != IRIS_SEED['sha256']: raise AssertionError(f'Wrong Complementary archive: {digest}')
    with tarfile.open(fileobj=io.BytesIO(data), mode='r:gz') as tar, zipfile.ZipFile(seed/'shaderpacks'/IRIS_SEED['name'], 'w', zipfile.ZIP_DEFLATED) as pack:
        for entry in tar:
            if entry.isfile(): pack.writestr(entry.name.split('/', 1)[1], tar.extractfile(entry).read())
    (seed/'config/iris.properties').write_text('enableShaders=true\nshaderPack=' + IRIS_SEED['name'] + '\n')
    versions = modrinth('/project/iris/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%221.21.11%22%5D')
    if not versions: raise AssertionError('No Iris release for 1.21.11 on Modrinth')
    iris = next(v for v in versions if v["version_number"] == "1.10.8+1.21.11-fabric")
    sodium_id = modrinth('/project/sodium')['id']
    edge = [d for d in iris['dependencies'] if d.get('project_id') == sodium_id and d.get('dependency_type') == 'required']
    if not edge: raise AssertionError('Iris release has no required Sodium edge')
    if edge[0].get('version_id'):
        sodium = modrinth('/version/' + edge[0]['version_id'])
    else:
        sodium = next(v for v in modrinth('/project/sodium/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%221.21.11%22%5D') if v['version_number'].startswith('0.8'))
    for version, label in ((iris, 'iris'), (sodium, 'sodium')):
        file = next(f for f in version['files'] if f.get('primary')) if any(f.get('primary') for f in version['files']) else version['files'][0]
        jar = seed/'mods'/file['filename']
        sha256 = download_checked(file['url'], file['hashes']['sha1'], jar)
        if label == 'iris':
            if sha256 != '5b6b5124c74407ffe4945f8da61491731cbf697f0bba4b14257e0ff977dac087':
                raise AssertionError(f'Unaudited Iris bytes: {sha256}')
            audit_iris_hooks(jar, evidence)
        print(f"::notice::PORT_IRIS_MOD: {label} {version['version_number']} {file['filename']} SHA-256 {sha256}", flush=True)
    print(f"::notice::PORT_IRIS_PACK: {IRIS_SEED['name']} commit {IRIS_SEED['commit'][:7]} archive SHA-256 {digest}", flush=True)


USER_ENV = {
    # Exactly the combination in the 2026-11-26 user report (latest.log): Iris 1.10.7+mc1.21.11,
    # Sodium 0.8.12+mc1.21.11 and the ComplementaryUnbound_r5.8.1 shader pack. Unbound is not on
    # GitHub; Modrinth's own SHA-1 is checked on download and the SHA-256 is recorded, not pinned.
    'iris': '1.10.7', 'sodium': '0.8.12', 'pack_project': 'complementary-unbound', 'pack': 'r5.8.1',
}


def prepare_user_seed(work, evidence):
    """The user's Iris/Sodium/shader-pack combination, resolved from Modrinth by exact version."""
    import json
    seed = work/'client/fixtures/optics-iris'
    for sub in ('shaderpacks', 'config', 'mods'): (seed/sub).mkdir(parents=True, exist_ok=True)
    query = '?loaders=%5B%22fabric%22%5D&game_versions=%5B%221.21.11%22%5D'
    def pick(project, prefix, q=query):
        versions = modrinth(f'/project/{project}/version{q}')
        chosen = next((v for v in versions if v['version_number'].startswith(prefix)), None)
        if chosen is None:
            raise AssertionError(f'{project} {prefix} not found; available: {[v["version_number"] for v in versions]}')
        return chosen
    receipt = []
    for project, prefix in (('iris', USER_ENV['iris'] + '+'), ('sodium', USER_ENV['sodium'] + '+')):
        version = pick(project, prefix)
        file = next((f for f in version['files'] if f.get('primary')), version['files'][0])
        jar = seed/'mods'/file['filename']
        sha256 = download_checked(file['url'], file['hashes']['sha1'], jar)
        receipt.append({'project': project, 'version': version['version_number'], 'file': file['filename'], 'sha256': sha256})
        print(f"::notice::PORT_USER_MOD: {project} {version['version_number']} {file['filename']} SHA-256 {sha256}", flush=True)
        if project == 'iris':
            try: audit_iris_hooks(jar, evidence, version['version_number'])
            except Exception as e: print(f'::warning::PORT_USER_IRIS_AUDIT: {version["version_number"]}: {e}', flush=True)
    versions = modrinth(f"/project/{USER_ENV['pack_project']}/version")
    pack = next((v for v in versions if USER_ENV['pack'] in v['version_number'] or USER_ENV['pack'] in v.get('name', '')), None)
    if pack is None:
        raise AssertionError(f"{USER_ENV['pack_project']} {USER_ENV['pack']} not found; available: {[v['version_number'] for v in versions][:20]}")
    file = next((f for f in pack['files'] if f.get('primary')), pack['files'][0])
    sha256 = download_checked(file['url'], file['hashes']['sha1'], seed/'shaderpacks'/file['filename'])
    receipt.append({'project': USER_ENV['pack_project'], 'version': pack['version_number'], 'file': file['filename'], 'sha256': sha256})
    print(f"::notice::PORT_USER_PACK: {file['filename']} ({pack['version_number']}) SHA-256 {sha256}", flush=True)
    (seed/'config/iris.properties').write_text('enableShaders=true\nshaderPack=' + file['filename'] + '\n')
    (evidence/'user-env.json').write_text(json.dumps(receipt, indent=2) + '\n')


def audit_iris_hooks(jar, evidence, label='1.10.8'):
    """Audit the actual intermediary runtime JAR, not a source branch or a dev dependency."""
    chosen = {}
    for name, methods in {
        'net.irisshaders.iris.pipeline.IrisRenderingPipeline': ['beginHand(', 'finalizeLevelRendering('],
        'net.irisshaders.iris.pathways.HandRenderer': ['renderSolid(', 'renderTranslucent('],
    }.items():
        text = subprocess.check_output(['javap', '-p', '-c', '-classpath', str(jar), name], text=True)
        selected = None
        for line in text.splitlines():
            if line.startswith('  ') and not line.startswith('    ') and '(' in line:
                selected = next((method for method in methods if method in line), None)
                if selected: chosen[selected] = []
            if selected: chosen[selected].append(line)
    chosen = {key: '\n'.join(lines) for key, lines in chosen.items()}
    final = chosen['finalizeLevelRendering(']
    order = [final.index(token) for token in ['iconst_0', 'isRenderingWorld', 'renderAll', 'renderFinalPass']]
    if order != sorted(order): raise AssertionError('Iris final overlay boundary moved')
    if 'copyPreHandDepth' not in chosen['beginHand(']: raise AssertionError('Iris pre-hand depth boundary moved')
    for method in ['renderSolid(', 'renderTranslucent(']:
        if 'iris$renderHandsWithCustomRenderer' not in chosen[method] or 'restoreProjectionMatrix' not in chosen[method]:
            raise AssertionError('Iris hand flush contract moved: ' + method)
    (evidence/('iris-runtime-hooks.txt' if label == '1.10.8' else f'iris-runtime-hooks-{label}.txt')).write_text('\n\n'.join(chosen.values()))
    print(f'::notice::PORT_IRIS_API: {label} runtime bytecode: pre-hand depth and post-world final overlay boundaries verified', flush=True)


def report_receipts(receipts):
    """Keep every enforced receipt visible under Actions' ten-notices-per-step limit.

    Individual notices silently hid the later platform contracts. Preserve readable full
    stdout too; batch escaped payloads instead of truncating or weakening assertions.
    """
    chunks, group = [], ''
    for receipt in receipts:
        print(receipt, flush=True)
        # Bound even an unusually long pixel matrix without cutting a workflow escape.
        for start in range(0, len(receipt), 1000):
            part = receipt[start:start + 1000].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
            if group and len(group) + len(part) + 3 > 3500:
                chunks.append(group)
                group = ''
            group += (' | ' if group else '') + part
    if group:
        chunks.append(group)
    for group in chunks:
        print('::notice::' + group, flush=True)


def test(product, work, evidence, suite):
    copy_project(work, 'client')
    evidence.mkdir(parents=True, exist_ok=True)
    if suite == 'user-unbound-iris': prepare_user_seed(work, evidence)
    elif suite.endswith('-iris'): prepare_iris_seed(work, evidence)
    if suite == 'platform-zoomify': prepare_zoomify_seed(work, evidence)
    instance = work/'client/build/instance'
    log = evidence/'client.log'
    args = runtime_command(work, 'client', product, 'none', '-PprobeClient', f'-PplayerSuite={suite}', 'probeClient')
    try:
        with launch(args, log) as proc:
            if proc.wait(1200 if suite.startswith(('optics', 'user')) else 720) != 0:
                raise AssertionError(f'Production {suite} client failed: {log}')
    finally:
        latest = instance/'logs/latest.log'
        if latest.is_file(): shutil.copyfile(latest, evidence/'latest.log')
        if (instance/'screenshots').exists(): shutil.copytree(instance/'screenshots', evidence/'frames', dirs_exist_ok=True)
        if suite == 'user-unbound-iris' and log.is_file():
            # Pass or fail, every stage result and gate dump must reach the Checks UI in full.
            rows = [row[row.find('PORT_USER_') if 'PORT_USER_' in row else row.find('USER_DIAG'):]
                    for row in log.read_text(errors='replace').splitlines() if 'PORT_USER_' in row or 'USER_DIAG' in row]
            if rows: report_receipts(rows)
    markers = {
        'display': {'PORT_INK:': 1, 'PORT_OUTLINE:': 1, 'PORT_RACK_PREVIEW:': 5, 'PORT_CASE_HINT:': 1},
        'controller': {'PORT_CONTROLLER:': 1},
        'pal': {'PORT_PAL:': 2},
        **{name: {f'PORT_PLATFORM_{kind}:': 1 for kind in ['INPUT','SKIN','AUDIO','GUI','ZOOM','ADS','RECOIL','MODIFIERS','TRACER','MUZZLE','PARTICLES','MESH_BUDGET','SOUND_DATA','ATTACHMENT_TIPS','CARRIED_GUNS','HURT_TILT','SMITH_FILTER','LANGUAGES','LEGACY_PATHS','RANGEFINDER_TEXT','LOOSE_VARIANTS','IMPACTS','CLOTH_SETTINGS','SPRITE_TRANSFORM','INDEX_LIFECYCLE','SNOWSTORM_SPACE','STANCE','UI_LABELS']} for name in ['platform','platform-zoomify']},
        'receiver': {'PORT_RECEIVER_INPUT:': 1, 'PORT_RECEIVER_PIXEL:': 11, 'PORT_RECEIVER_PACK:': 1},
        'receiver-iris': {'PORT_RECEIVER_INPUT:': 1, 'PORT_RECEIVER_PIXEL:': 11, 'PORT_RECEIVER_PACK:': 1},
        'user-unbound-iris': {'PORT_USER_ENV:': 1, 'PORT_USER_FORMAT:': 1, 'PORT_USER_THERMAL:': 2, 'PORT_USER_LENS_EDGE:': 2, 'PORT_USER_SHADER_TOGGLE:': 1},
        'optics': {'PORT_PIP_MOTION:': 16, 'PORT_PIP_WIDE:': 2, 'PORT_THERMAL_PIXELS:': 1, 'PORT_THERMAL_MODE:': 2, 'PORT_THERMAL_POSE:': 18, 'PORT_OPTIC:': 3, 'PORT_RETICLE_CONTRACT:': 1, 'PORT_RETICLE_GROUPS:': 1, 'PORT_BODY_MATERIAL:': 1, 'PORT_OCULAR_BAKE:': 1, 'PORT_RETICLE_PIXELS:': 1},
        'optics-iris': {'PORT_PIP_MOTION:': 16, 'PORT_PIP_WIDE:': 2, 'PORT_THERMAL_PIXELS:': 1, 'PORT_THERMAL_MODE:': 2, 'PORT_THERMAL_POSE:': 18, 'PORT_OPTIC:': 3, 'PORT_IRIS:': 1, 'PORT_RETICLE_CONTRACT:': 1, 'PORT_RETICLE_GROUPS:': 1, 'PORT_BODY_MATERIAL:': 1, 'PORT_OCULAR_BAKE:': 1, 'PORT_RETICLE_PIXELS:': 1},
    }[suite]
    text = log.read_text(errors='replace')
    receipts = []
    for marker, count in {**markers, 'PORT_PLAYER:': 1}.items():
        rows = [row.split(marker, 1)[1].strip() for row in text.splitlines() if marker in row]
        if len(rows) != count: raise AssertionError(f'{suite}: expected {count} {marker} receipts, got {rows}')
        receipts.append(f'{marker} ' + ' | '.join(rows))
    report_receipts(receipts)
    frames = {'optics': ['optic_raise', 'optic_steady', 'optic_hip'], 'optics-iris': ['optic_raise', 'optic_steady', 'optic_hip'], 'pal': ['pal_remote_rifle', 'pal_remote_pistol'], 'controller': [], 'receiver': [], 'receiver-iris': [], 'user-unbound-iris': [f'user_{m}_{k}' for m in ['iris', 'vanilla'] for k in ['empty', 'thermal', 'edge_on', 'edge_off']] + ['user_after_toggle'], 'platform': [], 'platform-zoomify': [], 'display': [
        f'{scene}_{state}' for scene in ['rack', 'case', 'corner', 'vanilla_quad', 'mesh'] for state in ['empty', 'ghost', 'placed']
    ] + [f'case_hint_{s}' for s in ['gun_hover','gun_interact','empty_hover','look_away','tick_39','tick_40_held','sneak_unlock','unlocked']]}[suite]
    if suite.startswith('platform'):
        frames += [f'platform_cooldown_{stage}' for stage in ['before','half','expired']]
        frames += [f'platform_smith_filter_{stage}' for stage in ['empty','cleared']]
    if suite.startswith('optics'):
        frames += ['reticle_full', 'reticle_control', 'reticle_luminous_only', 'reticle_none', 'reticle_restored',
                   'thermal_empty_optical', 'thermal_exposed', 'thermal_occluded', 'thermal_glass', 'thermal_disabled', 'thermal_unavailable', 'thermal_thermal_black_hot', 'thermal_thermal_ironbow']
        frames += [f'thermal_{mode}_{range}_{kind}' for mode in ['classic','pip','narrow'] for range in [16,36]
                   for kind in ['empty'] + [f'{stage}_{yaw}' for stage in ['optical','heat','wall'] for yaw in [0,45,90]]]
    for frame in frames:
        if not any((instance/'screenshots').glob(f'*tacz_{frame}.png')): raise AssertionError(f'Missing raw frame: {frame}')
    if suite.startswith('receiver'):
        for name in ['rest','off','on','expired','blocked_rest','blocked']:
            if not (instance/'screenshots'/f'tacz_receiver_{name}.png').is_file():
                raise AssertionError(f'Missing receiver frame: {name}')
    if suite.startswith('optics'):
        transition = instance/'screenshots/ads-transition'
        samples = [f'{mode}_{phase}' for mode in ['reproject','narrow']
                   for phase in ['raise','mid','steady','missing_mask','below_floor','lower','hip']] + ['classic_raise','classic_lower']
        for sample in samples:
            for suffix in ['', '_before', '_lens']:
                if not (transition/f'{sample}{suffix}.png').is_file():
                    raise AssertionError(f'Missing same-frame motion evidence: {sample}{suffix}')
    with product.open('rb') as f: digest = hashlib.file_digest(f, 'sha256').hexdigest()
    print(f'::notice::PORT_CLIENT_JAR: {suite} used {product.name}, SHA-256 {digest}', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--accept-test-eula', required=True, action='store_true')
    parser.add_argument('--suite', required=True, choices=['display','pal','controller','optics','optics-iris','receiver','receiver-iris','user-unbound-iris','platform','platform-zoomify'])
    parser.add_argument('jar', type=Path)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='tacz-12111-player-') as tmp:
        test(args.jar.resolve(strict=True), Path(tmp), ROOT/'build/packaged-evidence'/f'player-{args.suite}', args.suite)
