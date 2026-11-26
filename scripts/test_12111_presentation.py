#!/usr/bin/env python3
"""Exercise the native editor under three actual 1.21.11 client launches (A/B/A), then verify presented pixels."""
import argparse
import json
from pathlib import Path
import shutil
import tempfile
import uuid

from test_12111_connection import ROOT, copy_project, launch, runtime_command


def test(product, work, evidence):
    copy_project(work, 'client')
    evidence.mkdir(parents=True, exist_ok=True)
    seed = work/'profiles'
    seed.mkdir()
    a, b = (str(uuid.uuid4()) for _ in range(2))
    previous = {}
    for phase, owner in [('a', a), ('b', b), ('a-return', a)]:
        instance = work/'client/build'/f'presentation-{phase}'
        log = evidence/(phase + '.log')
        args = runtime_command(work, 'client', product, 'none', '-PprobeClient', f'-Ppresentation={phase}',
                               f'-PprofileSeed={seed}', f'-PprofileOwner={owner}', 'probeClient')
        try:
            with launch(args, log) as proc:
                if proc.wait(720) != 0: raise AssertionError(f'{phase} presentation client failed: {log}')
        finally:
            latest = instance/'logs/latest.log'
            if latest.is_file(): shutil.copyfile(latest, evidence/(phase + '-latest.log'))
            for image in instance.rglob('*.png'):
                if 'screenshots' in image.parts: shutil.copyfile(image, evidence/(phase + '-' + image.name))
        observed = [row.split('PORT_PROFILE: ',1)[1] for row in log.read_text(errors='replace').splitlines() if 'PORT_PROFILE: ' in row]
        if len(observed) != 1 or not observed[0].startswith(f'{phase} launcher={owner} '):
            raise AssertionError(f'Wrong or missing editor receipt for {phase}: {observed}')
        profiles = instance/'config/tacz/view-profiles'
        files = {p.stem:p.read_bytes() for p in profiles.glob('*.json')}
        if files.keys() != previous.keys() | {owner} or any(files[k] != v for k,v in previous.items()):
            raise AssertionError(f'{phase} overwrote a saved account or wrote an unexpected profile')
        active = json.loads(files[owner]); expected = 'TACZ-POS1:' + ('-0.500,0.000,0.250' if phase == 'b' else '0.237,-0.113,-0.330')
        if active['position'] != expected or ('tacz:m320' in active['guns']) != (owner == a) or ('rpg' in active['types']) != (owner == a):
            raise AssertionError(f'{phase}: wrong position or HUD scopes in saved JSON: {active}')
        if phase == 'a-return':
            text = log.read_text(errors='replace')
            for marker, count in [('PORT_HUD_FRAME:', 1), ('PORT_HAND_FRAME:', 1)]:
                rows = [line.split(marker, 1)[1].strip() for line in text.splitlines() if marker in line]
                if len(rows) != count: raise AssertionError(f'{marker} expected {count} framebuffer receipts, got {rows}')
                for row in rows: print(f'::notice::{marker} {row}', flush=True)
        shutil.copytree(profiles, seed, dirs_exist_ok=True)
        shutil.copytree(profiles, evidence/phase, dirs_exist_ok=True)
        previous = files
        print('::notice::PORT_PROFILE: ' + observed[0], flush=True)
    print('::notice::PORT_HUD_FRAME: A/B/A across three remapped clients; exact ladder, type ring and unrelated default read from native frames', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--accept-test-eula', required=True, action='store_true')
    parser.add_argument('jar', type=Path)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='tacz-12111-hud-') as tmp:
        test(args.jar.resolve(strict=True), Path(tmp), ROOT/'build/packaged-evidence/presentation')
