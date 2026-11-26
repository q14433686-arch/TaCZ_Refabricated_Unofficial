#!/usr/bin/env python3
"""Exercise the remapped JAR over real TCP. Only write an EULA in a disposable test instance."""
import argparse
import base64
from contextlib import ExitStack, contextmanager
import hashlib
import json
import os
from pathlib import Path
import random
import re
import shutil
import signal
import socket
import subprocess
import tempfile
import time
from zipfile import ZipFile

from probe_12111_conversion import run_conversion
from probe_12111_upgrade import seed_previous_default, check_upgrade

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / 'verification/packaged-runtime'


def files_for(generation, sort):
    meta = {'namespace': 'net_probe', 'dependencies': {'minecraft': '[1.20.1]', 'tacz': '[1.1.8,)'}, 'overlays': {'entries': [
        {'directory': 'early', 'minecraft': '>=1.21.10 <1.22'}, {'directory': 'not_here', 'minecraft': '[26.1.2]'},
        {'directory': 'winner', 'minecraft': '[1.21.11]'}]}}
    index = {'name': 'remote', 'type': 'rifle', 'data': 'tacz:ak47_data', 'display': 'tacz:ak47_display'}
    key = f'data/net_probe/index/guns/{generation}.json'
    # One valid index with an ignored author extension. It must not fit one chunk OR legacy readUtf.
    padding = base64.b64encode(random.Random(sort).randbytes(300 * 1024)).decode('ascii')
    return {'gunpack.meta.json': meta, key: dict(index, sort=1), 'early/' + key: dict(index, sort=2),
            'winner/' + key: dict(index, sort=sort, probe_padding=padding),
            'not_here/data/net_probe/index/guns/wrong_version.json': index,
            f'winner/data/net_probe/recipe/{generation}.json': {'type': 'tacz:gun_smith_table_crafting',
                 'materials': [{'item': 'minecraft:stick'}, {'item': 'minecraft:flint'}],
                 'result': {'type': 'ammo', 'id': 'tacz:12g', 'count': 5}}}


@contextmanager
def launch(args, log):
    with log.open('w') as output, subprocess.Popen(args, stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT,
                                                   text=True, start_new_session=True) as proc:
        try:
            yield proc
        finally:
            if proc.poll() is None:
                os.killpg(proc.pid, signal.SIGTERM)
                try:
                    proc.wait(12)
                except subprocess.TimeoutExpired:
                    os.killpg(proc.pid, signal.SIGKILL)


def wait_for(check, processes, seconds, label):
    deadline = time.monotonic() + seconds
    while not check():
        if any(proc.poll() is not None for proc in processes):
            raise RuntimeError(f'{label}: game or launcher exited before receipt')
        if time.monotonic() > deadline:
            raise TimeoutError(label)
        time.sleep(.5)


def copy_project(work, side):
    shutil.copytree(PROJECT, work/side, ignore=shutil.ignore_patterns('build', '.gradle', 'run'))
    shutil.copyfile(ROOT/'gradle.properties', work/side/'gradle.properties')


def runtime_command(work, side, product, viewer, *args):
    return [ROOT/'gradlew', '-p', work/side, '--no-daemon', '--console=plain', '--init-script', ROOT/'scripts/ci-central.init.gradle',
            f'-PproductJar={product}', f'-Pviewer={viewer}', *args]


def seed_loopback_server(seed):
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    seed.mkdir(parents=True, exist_ok=True)
    (seed/'eula.txt').write_text('eula=true\n')
    (seed/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nspawn-protection=0\ndifficulty=peaceful\nenforce-secure-profile=false\nview-distance=3\nsimulation-distance=3\nlevel-type=minecraft:flat\n')
    return port


def copy_saved_server(instance, saved):
    # FCAP 21.11.1 loads server configs from instance/config by default; world/serverconfig
    # (singular) only overrides files explicitly placed there. Preserve both, not just the world.
    for directory in ('world', 'tacz', 'config'):
        shutil.copytree(instance/directory, saved/directory)
    for name in ('eula.txt', 'server.properties'):
        shutil.copyfile(instance/name, saved/name)


def test(product, work, evidence, viewer):
    evidence.mkdir(parents=True, exist_ok=True)
    for side in ('server', 'client'): copy_project(work, side)
    instance = work/'server/build/instance'
    seed = work/'server-seed'  # outside Gradle's output cleanup
    directory = instance/'tacz/probe'
    for name, data in files_for('first', 111).items():
        file = seed/'tacz/probe'/name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(json.dumps(data))
    if viewer == 'none': shutil.copytree(PROJECT/'fixtures/refit', seed/'tacz/refit')
    _, previous, old_fingerprint = seed_previous_default(seed/'tacz')
    port = seed_loopback_server(seed)
    defaults = seed/'defaultconfigs'
    defaults.mkdir()
    (defaults/'tacz-server.toml').write_text('[misc]\nCrawlMaxUpAngle = 12.0\nCrawlMaxDownAngle = 7.0\nUseHipFireInaccuracyWhileAirborne = true\n')
    ready = work/'directory-received'

    def gradle(side, *args): return runtime_command(work, side, product, viewer, *args)

    try:
        with ExitStack() as servers:
            server = servers.enter_context(launch(gradle('server', f'-PprobeSeed={seed}', 'probeServer'), evidence/'server.log'))
            log = instance/'logs/latest.log'
            wait_for(lambda: log.exists() and 'For help, type "help"' in log.read_text(errors='replace'), [server], 720, 'dedicated startup')
            note, backup = check_upgrade(instance, product, previous, old_fingerprint)
            run_conversion(instance, server, wait_for, evidence)
            with launch(gradle('client', '-PprobeClient', f'-PprobePort={port}', f'-PprobeReady={ready}', 'probeClient'), evidence/'client.log') as client:
                wait_for((work/'joined').is_file, [server, client], 720, 'directory index and play session')
                server.stdin.write('forceload add 8 8\nexecute positioned 8 201 8 run fill ~-2 ~-1 ~-2 ~2 ~-1 ~2 minecraft:stone\n'
                                   'setblock 9 201 8 minecraft:crafting_table\nsetblock 7 201 8 tacz:workbench_a{BlockId:"tacz:ammo_workbench"}\n'
                                   'tp @a 8.5 201 8.5\ngive @a minecraft:oak_log 6\ngive @a minecraft:iron_ingot 8\n'
                                   'give @a minecraft:iron_block 2\ngive @a minecraft:stick 2\n')
                server.stdin.flush()
                def fund_after_short(generation):
                    wait_for((work/f'short-{generation}').is_file, [server, client], 360, f'{viewer} short craft: {generation}')
                    server.stdin.write('give @a minecraft:flint 1\n')
                    server.stdin.flush()
                fund_after_short('first')
                wait_for(ready.is_file, [server, client], 180, 'directory generation survival craft')
                directory.rename(work/'retired')
                with ZipFile(directory.with_suffix('.zip'), 'w') as archive:
                    for name, data in files_for('second', 222).items():
                        archive.writestr(name, json.dumps(data))
                server.stdin.write('reload\n')
                server.stdin.flush()
                fund_after_short('second')
                wait_for((work/'converter-reloaded').is_file, [server, client], 100, 'converted server recipes synced')
                # An actual recipe-book unlock and loot drop, not an inspection of pack bytes.
                server.stdin.write('recipe give PackProbe convert_probe:paper\nloot give PackProbe loot convert_probe:reward\n')
                server.stdin.flush()
                wait_for(lambda: 'Unlocked 1 recipe(s) for PackProbe' in log.read_text(errors='replace'), [server], 40, 'vanilla recipe registered')
                if viewer == 'none':
                    # Supply only starting stock and empty fixtures; the non-op client must move every item.
                    server.stdin.write('setblock 8 201 7 tacz:ammo_storage\nsetblock 8 201 9 tacz:gun_display_case[facing=north,locked=false]\n'
                        'give PackProbe tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"port_test:oem",GunCurrentAmmoCount:3,HasBulletInBarrel:1b}] 1\n'
                        'give PackProbe tacz:attachment[minecraft:custom_name="Marked optic",minecraft:enchantments={"minecraft:unbreaking":1},minecraft:custom_data={AttachmentId:"tacz:sight_deltapoint_rifle",Serial:73}] 1\n'
                        'give PackProbe tacz:ammo[minecraft:custom_name="Saved lot",minecraft:max_stack_size=60,minecraft:enchantments={"minecraft:unbreaking":1},minecraft:custom_data={AmmoId:"tacz:762x39",ProbeLot:91}] 7\n')
                    server.stdin.flush()
                    wait_for((work/'restart-requested').is_file, [server, client], 300, 'survival deposit, refit and logout')
                    server.stdin.write('stop\n'); server.stdin.flush()
                    if server.wait(90) != 0: raise AssertionError('world did not save and close normally')
                    # No in-memory BE, client mirror, new-world seed or first-generation pack survives this boundary.
                    copy_project(work, 'restored')
                    saved = work/'restart-seed'
                    copy_saved_server(instance, saved)
                    server = servers.enter_context(launch(gradle('restored', f'-PprobeSeed={saved}', 'probeServer'), evidence/'restored.log'))
                    new_log = work/'restored/build/instance/logs/latest.log'
                    wait_for(lambda: new_log.is_file() and 'For help, type "help"' in new_log.read_text(errors='replace'), [server, client], 360, 'saved-world dedicated startup')
                    (work/'restart-ready').touch()
                if client.wait(360) != 0 or 'PORT_TCP: directory winner' not in (evidence/'client.log').read_text(errors='replace'):
                    raise AssertionError('missing client reload/logout/rejoin receipt')
            if note.read_bytes() != b'keep on reload\n' or list(backup.parent.glob('backup-*.zip')) != [backup]:
                raise AssertionError('Ordinary /reload replaced an unchanged edited export')
            server.stdin.write('stop\n')
            server.stdin.flush()
            if server.wait(90) != 0:
                raise AssertionError('dedicated shutdown failed')
            parts = re.findall(r'Prepared gun pack sync: \d+ bytes -> \d+ compressed bytes, (\d+) part\(s\)', log.read_text())
            if len(parts) < 2 or min(int(n) for n in parts[-2:]) < 2:
                raise AssertionError(f'expected multi-part transfers at startup and reload, saw {parts}')
        receipts = [l[l.index('PORT_'):] for l in (evidence/'client.log').read_text(errors='replace').splitlines() if any(tag in l for tag in ('PORT_CRAFT:', 'PORT_VIEWER:', 'PORT_INVENTORY:', 'PORT_PERSIST:', 'PORT_CLOCK:', 'PORT_SERVER_CONFIG:'))]
        if not any(l.startswith('PORT_CRAFT: ' + viewer) for l in receipts): raise AssertionError('Missing survival craft receipt')
        if sum(l.startswith('PORT_SERVER_CONFIG:') for l in receipts) != 1: raise AssertionError('Missing or duplicate actual TCP server config receipt')
        if viewer == 'none':
            for tag in ('PORT_INVENTORY:', 'PORT_PERSIST:', 'PORT_CLOCK:'):
                if sum(l.startswith(tag) for l in receipts) != 1: raise AssertionError(f'Missing or duplicate {tag} receipt')
        if viewer != 'none' and sum(l.startswith('PORT_VIEWER:') for l in receipts) != 2: raise AssertionError('Missing pre/post-reload transfer receipts')
        if viewer == 'rei':
            client_log = (work/'client/build/instance/logs/latest.log').read_text(errors='replace')
            forwards = [part.count('[TACZ REI] Forwarding') for part in client_log.split('PORT_REI: first connection closed; starting rejoin')]
            if len(forwards) != 2 or min(forwards) < 1 or any(reason in client_log for reason in (
                    '[TACZ REI] Display registries still busy', '[TACZ REI] Display sync exceeds', '[TACZ REI] Could not read')):
                raise AssertionError(f'Missing REI ready-decoder receipt on join/rejoin, or fallback: {forwards}')
            print(f'::notice::PORT_REI: ready decoder drains on join/rejoin: {forwards}; no fallback', flush=True)
        for line in receipts: print('::notice::'+line, flush=True)
        print('::notice::PORT_LOAD: exported ZIP supplied the vanilla recipe unlock, tagged loot, synced table and nested stack; disabled table absent on reload/rejoin', flush=True)
        with product.open('rb') as f:
            digest = hashlib.file_digest(f, 'sha256').hexdigest()
        print(f'::notice::PORT_TCP: {digest}; 1.21.11 intermediary dedicated + client; directory to ZIP via /reload, disconnect and rejoin. Parts: {parts}')
    finally:
        for side in ['server', 'client', 'restored']:
            game = work/side/'build/instance'
            for name in ['logs/latest.log', 'crash-reports']:
                src, dst = game/name, evidence/side/name
                if src.is_file():
                    dst.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(src, dst)
                elif src.is_dir():
                    shutil.copytree(src, dst, dirs_exist_ok=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    parser.add_argument('--accept-test-eula', action='store_true', required=True, help='only for the temporary loopback server')
    parser.add_argument('--viewer', choices=('none', 'jei', 'rei'), default='none')
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='tacz-12111-') as tmp:
        test(args.jar.resolve(strict=True), Path(tmp), ROOT/'build/packaged-evidence', args.viewer)
