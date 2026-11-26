#!/usr/bin/env python3
"""Diagnose the real REI 21.11 give backend separately from recipe transfer. No product patches."""
import argparse
from pathlib import Path
import shutil
import tempfile

from test_12111_connection import ROOT, copy_project, launch, runtime_command, seed_loopback_server, wait_for


def test(product, backend, work, evidence, *, characterize_wire=False):
    matched = backend == 'matched'
    evidence.mkdir(parents=True, exist_ok=True)
    for side in ('server', 'client'): copy_project(work, side)
    port = seed_loopback_server(work/'seed')
    instance = work/'server/build/instance'
    latest = instance/'logs/latest.log'
    args = runtime_command(work, 'server', product, 'rei' if matched else 'none', f'-PprobeSeed={work}/seed', 'probeServer')
    try:
        with launch(args, evidence/'server.log') as server:
            def console(*lines):
                server.stdin.write(''.join(line + '\n' for line in lines))
                server.stdin.flush()
            wait_for(lambda: latest.is_file() and 'For help, type "help"' in latest.read_text(errors='replace'),
                     [server], 720, 'REI control server startup')
            server_mods = latest.read_text(errors='replace')
            if any((f' {mod} ' in server_mods) != matched for mod in ('roughlyenoughitems', 'architectury', 'cloth-config')):
                raise AssertionError('Wrong optional mod set on dedicated server')
            args = runtime_command(work, 'client', product, 'rei', '-PprobeClient', f'-PprobeReady={work}',
                                   f'-PprobePort={port}', f'-PgiveBackend={backend}', 'probeClient')
            with launch(args, evidence/'client.log') as client:
                def wait(check, label, timeout=180): wait_for(check, [client, server], timeout, label)
                def inspect(phase, total, typed):
                    # /clear ... 0 is vanilla's count-only query. Both tests must pass on the SERVER.
                    # Log the complete stacks as well, so the component comparison is independently auditable.
                    marker = f'confirmed-{phase}-{total}-{typed}'
                    console('data get entity PackProbe Inventory',
                            'execute store result score #total tacz_give run clear PackProbe tacz:modern_kinetic_gun 0',
                            'execute store result score #typed tacz_give run clear PackProbe tacz:modern_kinetic_gun[minecraft:custom_data~{GunId:"tacz:ak47"}] 0',
                            'execute store result score #cmd tacz_give run clear PackProbe tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"tacz:ak47"}] 0',
                            f'execute if score #total tacz_give matches {total} if score #typed tacz_give matches {typed} if score #cmd tacz_give matches {int(phase == "command")} run say {marker}')
                    wait(lambda: marker in latest.read_text(errors='replace'), f'{phase}: authoritative inventory total={total}, AK47={typed}', 30)
                wait((work/'joined').is_file, 'REI client play', 720)
                console('scoreboard objectives add tacz_give dummy', 'forceload add 8 8',
                        'fill 6 200 6 10 200 10 minecraft:stone', 'setblock 9 201 8 minecraft:crafting_table',
                        'tp PackProbe 8.5 201 8.5', 'item replace entity PackProbe hotbar.0 with minecraft:stick')
                wait((work/'refused').is_file, 'non-op request and inventory round trip')
                inspect('refusal', 0, 0)
                # Only the disposable loopback test world, only after checking the refusal.
                console('op PackProbe')
                wait(lambda: any(work.glob('received-[01]')), 'operator REI entry action')
                typed = int(next(work.glob('received-[01]')).name[-1])
                inspect('rei', 1, typed)
                (work/'control-ready').touch()
                wait((work/'control').is_file, 'operator vanilla command')
                inspect('command', 2, typed + 1)
                console('deop PackProbe')
                (work/'verified').touch()
                if client.wait(90) != 0: raise AssertionError('REI diagnostic client failed')
                receipts = [line.split('PORT_REI_GIVE:', 1)[1] for line in (evidence/'client.log').read_text(errors='replace').splitlines() if 'PORT_REI_GIVE:' in line]
                if len(receipts) != 1: raise AssertionError('Missing or duplicate REI give receipt')
                print('::notice::PORT_REI_GIVE:' + receipts[0], flush=True)
            console('stop')
            if server.wait(90) != 0: raise AssertionError('REI control server failed to save')
            if matched and 'fullComponentMatch=false' in receipts[0]:
                message = 'Matched REI delivered the selected AK47 but not identical NBT types; cursor and inventory agree with its JSON codec. Not lossless.'
                if not characterize_wire: raise AssertionError(message)
                print('::warning::' + message, flush=True)
    finally:
        for side in ('client', 'server'):
            log = work/side/'build/instance/logs/latest.log'
            if log.is_file(): shutil.copyfile(log, evidence/(side+'-latest.log'))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--accept-test-eula', required=True, action='store_true')
    parser.add_argument('--backend', choices=['client-only', 'matched'], required=True)
    parser.add_argument('--characterize-wire', action='store_true', help='Report a known JSON numeric-width difference instead of requiring byte-identical matched NBT; the exact codec, authoritative inventory, refusal and command checks still apply')
    parser.add_argument('jar', type=Path)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='tacz-12111-rei-give-') as tmp:
        test(args.jar.resolve(strict=True), args.backend, Path(tmp), ROOT/'build/packaged-evidence'/('give-' + args.backend), characterize_wire=args.characterize_wire)
