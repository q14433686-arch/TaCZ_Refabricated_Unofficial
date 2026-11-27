"""Seed an edited native R3-hotfix4 export and compare the actual upgraded tree and backup."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RECEIVER = 'aac343af891858589eaea69bbefc413c0f2b1977'
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
    # Only git objects from the pinned 1.21.11 receiver, never a live player's export.
    old_zip = tacz.parent / 'receiver-source.zip'
    subprocess.run(['git', '-C', ROOT, 'archive', '--format=zip', f'--output={old_zip}',
                    f'{RECEIVER}:src/main/resources/{PACK}'], check=True)
    pack = tacz / 'tacz_default_gun'
    # The old receiver shipped most winners only inside the pinned immutable fallback.
    bundle_path = 'resources/tacz-26.1.2-source-and-resource-bundle.jar'
    bundle = ROOT / bundle_path
    if subprocess.check_output(['git', '-C', ROOT, 'hash-object', bundle], text=True).strip() != subprocess.check_output(
            ['git', '-C', ROOT, 'rev-parse', f'{RECEIVER}:{bundle_path}'], text=True).strip():
        raise ValueError('Changed receiver resource bundle')
    with zipfile.ZipFile(bundle) as fallback:
        for entry in fallback.infolist():
            if entry.is_dir() or not entry.filename.startswith(PACK + '/'):
                continue
            dest = pack / entry.filename.removeprefix(PACK + '/')
            dest.parent.mkdir(parents=True, exist_ok=True)
            with fallback.open(entry) as src, dest.open('wb') as out:
                shutil.copyfileobj(src, out)
    with zipfile.ZipFile(old_zip) as archive:
        archive.extractall(pack)
    old_zip.unlink()
    if len(tree_hashes(pack)) != 3322:
        raise ValueError('Incomplete receiver export')
    # aac343a's directory-backed source fingerprint, BEFORE editing the exported copy.
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


def check_upgrade(instance, product, previous, old_fingerprint):
    live = instance/'tacz/tacz_default_gun'
    [backup] = (instance/'tacz_backup/tacz_default_gun').glob('backup-*.zip')
    assert zip_hashes(backup) == previous, 'backup omitted or changed files'
    shipped = zip_hashes(product, PACK + '/')
    assert len(shipped) == 4176 and tree_hashes(live) == shipped, 'not the complete shipped pack'
    state = json.loads((live.parent/'.export-state.json').read_text())
    assert state['entries'][STATE_KEY] != old_fingerprint, 'upgrade did not advance the export state'
    note = live/'post-upgrade.txt'
    note.write_bytes(b'keep on reload\n')
    print(f'::notice::PORT_UPGRADE: {RECEIVER[:7]} edited old directory; {len(previous)} exact files in backup, '
          f'{len(shipped)} live files match the intermediary JAR', flush=True)
    return note, backup
