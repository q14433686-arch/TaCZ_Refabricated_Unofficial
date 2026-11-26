"""Report the actionable part of target compiler/game failures in the Checks UI."""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def report(text):
    print('::error::' + text[:3800].strip().replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))


def excerpt(lines, start):
    # Minecraft can print its entire classpath as one line in a native-library warning.
    return '\n'.join(s.rsplit('/src/', 1)[-1][:1600] for s in lines[start:start + 5])


files = [Path(name) for name in sys.argv[1:]] + list(Path('build/run').glob('*/crash-reports/*.txt'))
diagnostics, markers, tail = {}, {}, []
# Preserve the independently sampled phase + primary threads as separate annotations.
# Grouping six 3.6 KB stacks under one 3.8 KB annotation silently lost the later threads.
world_snapshot = {}
for path in files:
    if not path.is_file():
        continue
    lines = path.read_text(errors='replace').splitlines()
    for i, line in enumerate(lines):
        if 'PORT_' in line:
            markers[line[line.find('PORT_'):][:300]] = None
        if 'PORT_WORLD_STALL:' in line:
            state = line[line.find('PORT_WORLD_STALL:'):][:3600]
            key = next((key for key in ('cycle=', 'Server thread ', 'Render thread ', 'Test thread ')
                        if f'PORT_WORLD_STALL: ASYNC {key}' in state), None)
            if key is not None:
                world_snapshot[key] = state
            else:
                diagnostics[state] = None
        if re.search(r'error:|AssertionError|GameTestAssertException|Test .* failed|Exception in thread|Caused by:|^Description:|^java\.lang\.|^\* What went wrong:', line):
            if any(s in line for s in ('flite', 'MinecraftClientHttpException', 'InvalidCredentialsException')):
                continue
            diagnostics[excerpt(lines, i)] = None
    if path.name in ('client.log', 'tacz-client.log', 'latest.log'):
        tail += [l[:300] for l in lines[-18:] if l.strip()]
for path in Path('build/test-results/test').glob('*.xml'):
    for failure in ET.parse(path).iter('failure'):
        diagnostics[failure.get('message', str(path))] = None
if markers:
    print('::notice::Last target receipts: ' + ' | '.join(list(markers)[-7:]).replace('%', '%25'))
for key in ('cycle=', 'Server thread ', 'Render thread ', 'Test thread '):
    if key in world_snapshot:
        report(world_snapshot[key])
if tail:
    report('Runtime tail:\n' + '\n'.join(tail[-18:]))
messages = list(diagnostics)
# Stay within ten error annotations per step, including the independent snapshots and tail.
limit = 6 * max(0, 10 - len(world_snapshot) - bool(tail))
for i in range(0, min(len(messages), limit), 6):
    report('\n'.join(messages[i:i+6]))
