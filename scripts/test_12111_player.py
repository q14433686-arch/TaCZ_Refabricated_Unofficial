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


def audit_iris_hooks(jar, evidence):
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
    (evidence/'iris-runtime-hooks.txt').write_text('\n\n'.join(chosen.values()))
    print('::notice::PORT_IRIS_API: hash-pinned 1.10.8 runtime bytecode: pre-hand depth and post-world final overlay boundaries verified', flush=True)


def test(product, work, evidence, suite):
    copy_project(work, 'client')
    evidence.mkdir(parents=True, exist_ok=True)
    if suite == 'optics-iris': prepare_iris_seed(work, evidence)
    instance = work/'client/build/instance'
    log = evidence/'client.log'
    args = runtime_command(work, 'client', product, 'none', '-PprobeClient', f'-PplayerSuite={suite}', 'probeClient')
    try:
        with launch(args, log) as proc:
            if proc.wait(720) != 0:
                raise AssertionError(f'Production {suite} client failed: {log}')
    finally:
        latest = instance/'logs/latest.log'
        if latest.is_file(): shutil.copyfile(latest, evidence/'latest.log')
        if (instance/'screenshots').exists(): shutil.copytree(instance/'screenshots', evidence/'frames', dirs_exist_ok=True)
    markers = {
        'display': {'PORT_INK:': 1, 'PORT_OUTLINE:': 1, 'PORT_RACK_PREVIEW:': 5, 'PORT_CASE_HINT:': 1},
        'controller': {'PORT_CONTROLLER:': 1},
        'pal': {'PORT_PAL:': 2},
        'optics': {'PORT_PIP_MOTION:': 16, 'PORT_PIP_WIDE:': 2, 'PORT_THERMAL_PIXELS:': 1, 'PORT_THERMAL_MODE:': 2, 'PORT_THERMAL_POSE:': 18, 'PORT_OPTIC:': 3, 'PORT_RETICLE_CONTRACT:': 1, 'PORT_RETICLE_PIXELS:': 1},
        'optics-iris': {'PORT_PIP_MOTION:': 16, 'PORT_PIP_WIDE:': 2, 'PORT_THERMAL_PIXELS:': 1, 'PORT_THERMAL_MODE:': 2, 'PORT_THERMAL_POSE:': 18, 'PORT_OPTIC:': 3, 'PORT_IRIS:': 1, 'PORT_RETICLE_CONTRACT:': 1, 'PORT_RETICLE_PIXELS:': 1},
    }[suite]
    text = log.read_text(errors='replace')
    for marker, count in {**markers, 'PORT_PLAYER:': 1}.items():
        rows = [row.split(marker, 1)[1].strip() for row in text.splitlines() if marker in row]
        if len(rows) != count: raise AssertionError(f'{suite}: expected {count} {marker} receipts, got {rows}')
        print(f'::notice::{marker} ' + ' | '.join(rows), flush=True)
    frames = {'optics': ['optic_raise', 'optic_steady', 'optic_hip'], 'optics-iris': ['optic_raise', 'optic_steady', 'optic_hip'], 'pal': ['pal_remote_rifle', 'pal_remote_pistol'], 'controller': [], 'display': [
        f'{scene}_{state}' for scene in ['rack', 'case', 'corner', 'vanilla_quad', 'mesh'] for state in ['empty', 'ghost', 'placed']
    ] + [f'case_hint_{s}' for s in ['gun_hover','gun_interact','empty_hover','look_away','tick_39','tick_40_held','sneak_unlock','unlocked']]}[suite]
    if suite.startswith('optics'):
        frames += ['reticle_full', 'reticle_control', 'reticle_luminous_only', 'reticle_none', 'reticle_restored',
                   'thermal_empty_optical', 'thermal_exposed', 'thermal_occluded', 'thermal_glass', 'thermal_disabled', 'thermal_unavailable', 'thermal_thermal_black_hot', 'thermal_thermal_ironbow']
        frames += [f'thermal_{mode}_{range}_{kind}' for mode in ['classic','pip','narrow'] for range in [16,36]
                   for kind in ['empty'] + [f'{stage}_{yaw}' for stage in ['optical','heat','wall'] for yaw in [0,45,90]]]
    for frame in frames:
        if not any((instance/'screenshots').glob(f'*tacz_{frame}.png')): raise AssertionError(f'Missing raw frame: {frame}')
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
    parser.add_argument('--suite', required=True, choices=['display','pal','controller','optics','optics-iris'])
    parser.add_argument('jar', type=Path)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='tacz-12111-player-') as tmp:
        test(args.jar.resolve(strict=True), Path(tmp), ROOT/'build/packaged-evidence'/f'player-{args.suite}', args.suite)
