#!/usr/bin/env python3
"""Boot a clean dedicated JVM and a separate client from the built JAR. Run under Xvfb.
Only writes an EULA in a fresh temporary fixture after explicit opt-in; never reads a player's run directory.
"""
import argparse
import contextlib
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import socket
import subprocess
import tempfile
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / 'verification/packaged-runtime'


@contextlib.contextmanager
def launch(args, log):
    with log.open('w') as output:
        proc = subprocess.Popen(args, stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT,
                                text=True, start_new_session=True)
        try:
            yield proc
        finally:
            proc.stdin.close()
            if proc.poll() is None:
                os.killpg(proc.pid, signal.SIGTERM)
                try:
                    proc.wait(timeout=12)
                except subprocess.TimeoutExpired:
                    os.killpg(proc.pid, signal.SIGKILL)
                    proc.wait()


def await_state(check, processes, timeout, label):
    until = time.monotonic() + timeout
    while not check():
        for proc in processes:
            if proc.poll() is not None:
                raise RuntimeError(f'{label}: JVM/launcher exited early with {proc.returncode}')
        if time.monotonic() >= until:
            raise TimeoutError(label)
        time.sleep(.5)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    parser.add_argument('--accept-test-eula', action='store_true', required=True,
                        help='accept the Minecraft EULA for this ephemeral, loopback-only server')
    args = parser.parse_args()
    jar = args.jar.resolve(strict=True)
    with tempfile.TemporaryDirectory(prefix='tacz-product-') as tmp:
        work = Path(tmp)
        evidence = ROOT / 'build/packaged-evidence'
        evidence.mkdir(parents=True, exist_ok=True)
        for side in ['server', 'client']:
            shutil.copytree(PROJECT, work/side, ignore=shutil.ignore_patterns('build', '.gradle', 'run'))
        dedicated = work/'server/build/dedicated'
        fixture = dedicated/'tacz/net-probe'
        indexes = fixture/'data/net_probe/index/guns'
        indexes.mkdir(parents=True)
        (fixture/'gunpack.meta.json').write_text(json.dumps({'namespace': 'net_probe', 'dependencies': {'minecraft': '[26.2]'}}))
        (indexes/'first.json').write_text(json.dumps({'name': 'remote', 'type': 'rifle', 'data': 'tacz:ak47_data', 'display': 'tacz:ak47_display'}))
        # A pre-upgrade, user-edited default export. The changed pack must be backed
        # up byte-for-byte before replacement; a neighbouring user pack is not ours.
        old_export = dedicated/'tacz/tacz_default_gun'
        previous = {'gunpack.meta.json': b'{"namespace":"edited default"}',
                    'my-notes.txt': b'keep these edits\n', 'data/tacz/index/guns/ak47.json': b'{"custom":true}'}
        for name, content in previous.items():
            path = old_export/name
            path.parent.mkdir(exist_ok=True, parents=True)
            path.write_bytes(content)
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0))
            port = sock.getsockname()[1]
        (dedicated/'eula.txt').write_text('eula=true\n')
        (dedicated/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\nview-distance=3\nsimulation-distance=3\nlevel-type=minecraft:flat\n')
        ready = work/'first-index-installed'
        def gradle(side, *tasks):
            return [str(ROOT/'gradlew'), '-p', str(work/side), '--console=plain', '--no-daemon',
                    '--init-script', str(ROOT/'scripts/ci-central.init.gradle'), f'-PtaczRoot={ROOT}', f'-PproductJar={jar}', *tasks]
        try:
            with launch(gradle('server', 'runServer'), evidence/'server.log') as server:
                # Look at the game log, not just a port that starts listening before registries are ready.
                log = dedicated/'logs/latest.log'
                await_state(lambda: log.is_file() and 'For help, type "help"' in log.read_text(errors='replace'),
                            [server], 600, 'dedicated startup')
                [backup] = (dedicated/'tacz_backup/tacz_default_gun').glob('backup-*.zip')
                with zipfile.ZipFile(backup) as z:
                    if {name: z.read(name) for name in z.namelist()} != previous:
                        raise RuntimeError('incomplete original export in the upgrade backup')
                with zipfile.ZipFile(jar) as z:
                    name = 'data/tacz/index/guns/ak47.json'
                    if (old_export/name).read_bytes() != z.read('assets/tacz/custom/tacz_default_gun/' + name):
                        raise RuntimeError('default export did not upgrade to the distributed version')
                if (old_export/'my-notes.txt').exists(): raise RuntimeError('retired file remained in the upgraded export')
                print('::notice::PORT_EXPORT: replaced old default pack, exact prior files recovered from upgrade backup', flush=True)
                with launch(gradle('client', '-PprobeClient', f'-PprobeReady={ready}', f'-PprobePort={port}', 'runClientGameTest'), evidence/'client.log') as client:
                    await_state(ready.is_file, [server, client], 600, 'client did not receive server-only index')
                    (indexes/'first.json').rename(indexes/'second.json')
                    server.stdin.write('reload\ngive @a minecraft:paper[minecraft:custom_name={text:"target receipt"}] 7\n')
                    server.stdin.flush()
                    if client.wait(timeout=400) != 0 or 'PORT_DISTRIBUTION:' not in (evidence/'client.log').read_text(errors='replace'):
                        raise RuntimeError('packaged client did not complete reload and reconnect')
                server.stdin.write('stop\n'); server.stdin.flush()
                if server.wait(timeout=60) != 0:
                    raise RuntimeError('dedicated shutdown failed')
            digest = hashlib.sha256(jar.read_bytes()).hexdigest()
            print(f'::notice::PORT_DISTRIBUTION: {jar.name} SHA-256 {digest}; separate TCP server and client passed')
        finally:
            for side, run in [('server', dedicated), ('client', work/'client/build/run/clientGameTest')]:
                for name in ('logs/latest.log', 'crash-reports', 'screenshots'):
                    source = run/name
                    if source.is_dir(): shutil.copytree(source, evidence/side/name, dirs_exist_ok=True)
                    elif source.is_file():
                        dest = evidence/side/name; dest.parent.mkdir(parents=True, exist_ok=True); shutil.copy(source, dest)


if __name__ == '__main__':
    main()
