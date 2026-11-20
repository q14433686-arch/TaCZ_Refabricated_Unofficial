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
RECEIVER = 'f9ae55af2b507a2d556c3846127aba1c57e47d1c'
PACK = 'assets/tacz/custom/tacz_default_gun'
STATE_KEY = f'com.tacz.guns.GunMod|/{PACK}|tacz_default_gun'


def tree_hashes(root):
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in root.rglob('*') if p.is_file()}


def zip_hashes(path, prefix=''):
    result = {}
    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            if info.is_dir() or not info.filename.startswith(prefix):
                continue
            name = info.filename.removeprefix(prefix)
            if name in result:
                raise ValueError(f'duplicate entry {name} in {path.name}')
            with archive.open(info) as stream:
                result[name] = hashlib.file_digest(stream, 'sha256').hexdigest()
    return result


def seed_previous_default(tacz):
    # Only git objects from the pinned 26.2 receiver, never a live player's export.
    old_zip = tacz.parent / 'receiver-source.zip'
    subprocess.run(['git', '-C', ROOT, 'archive', '--format=zip', f'--output={old_zip}',
                    f'{RECEIVER}:src/main/resources/{PACK}'], check=True)
    pack = tacz / 'tacz_default_gun'
    with zipfile.ZipFile(old_zip) as archive:
        archive.extractall(pack)
    old_zip.unlink()
    # f9ae55a's directory-backed source fingerprint, BEFORE editing the exported copy.
    lines = []
    for file in pack.rglob('*'):
        if file.is_file():
            st = file.stat()
            lines.append(f'{file.relative_to(pack).as_posix()}|{st.st_size}|{st.st_mtime_ns // 1_000_000}')
    fingerprint = hashlib.md5('\n'.join(sorted(lines)).encode(), usedforsecurity=False).hexdigest()
    (tacz / '.export-state.json').write_text(json.dumps({'version': 1, 'entries': {STATE_KEY: fingerprint}}))
    index = pack / 'data/tacz/index/guns/ak47.json'
    original = index.read_bytes()
    edited = original.replace(b'tacz.gun.ak47.name', b'custom.upgrade.label', 1)
    if edited == original: raise RuntimeError('expected a real legacy AK index to edit')
    index.write_bytes(edited)
    (pack / 'my-notes.txt').write_bytes(b'keep these edits\n')
    return pack, tree_hashes(pack), fingerprint


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
        old_export, previous, old_fingerprint = seed_previous_default(dedicated/'tacz')
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
                if zip_hashes(backup) != previous:
                    raise RuntimeError('incomplete or changed files in the previous default-pack backup')
                shipped = zip_hashes(jar, PACK + '/')
                if not shipped or tree_hashes(old_export) != shipped:
                    raise RuntimeError('upgraded directory does not match the distributed default pack')
                state = json.loads((old_export.parent/'.export-state.json').read_text())
                if state['entries'][STATE_KEY] == old_fingerprint:
                    raise RuntimeError('old export fingerprint survived the upgrade')
                # The following real /reload must not undo a new user edit on unchanged sources.
                note = old_export/'post-upgrade.txt'
                note.write_bytes(b'keep on reload\n')
                print(f'::notice::PORT_EXPORT: {RECEIVER[:7]} edited export: {len(previous)} exact files in backup; '
                      f'{len(shipped)} upgraded files match the JAR', flush=True)
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
            if note.read_bytes() != b'keep on reload\n' or list(backup.parent.glob('backup-*.zip')) != [backup]:
                raise RuntimeError('ordinary reload replaced the edited export or created another backup')
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
