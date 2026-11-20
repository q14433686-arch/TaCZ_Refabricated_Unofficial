"""Expose actionable CI failures even when the Actions log archive isn't accessible."""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def report(text):
    print('::error::' + text[:3200].strip().replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))


for filename in sys.argv[1:]:
    path = Path(filename)
    if path.is_file():
        lines = path.read_text(errors='replace').splitlines()
        for i in [i for i, line in enumerate(lines) if re.search(r'error:|Caused by:|AssertionError|Test .* failed|^> |^\* What went wrong:', line)][:80]:
            report('\n'.join(lines[i:i + 5]))
for path in Path('build/test-results/test').glob('*.xml'):
    for failure in ET.parse(path).iter('failure'):
        report(failure.get('message', str(path)))
