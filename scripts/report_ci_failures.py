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
for path in files:
    if not path.is_file():
        continue
    lines = path.read_text(errors='replace').splitlines()
    for i, line in enumerate(lines):
        if 'PORT_' in line:
            markers[line[line.find('PORT_'):][:300]] = None
        if 'PORT_WORLD_STALL:' in line:
            diagnostics[line[line.find('PORT_WORLD_STALL:'):][:3600]] = None
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
if tail:
    report('Runtime tail:\n' + '\n'.join(tail[-18:]))
messages = list(diagnostics)
for i in range(0, min(len(messages), 48), 6):
    report('\n'.join(messages[i:i+6]))
