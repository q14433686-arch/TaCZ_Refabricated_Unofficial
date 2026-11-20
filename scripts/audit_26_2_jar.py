#!/usr/bin/env python3
"""Check the distributable (not the sources archive or Loom classpath)."""
import hashlib
import json
import os
from pathlib import Path
import sys
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
PREFIX = 'assets/tacz/custom/tacz_default_gun/'


def audit(path):
    with ZipFile(path) as jar:
        names = [f.filename for f in jar.infolist() if not f.is_dir()]
        assert len(set(names)) == len(names), 'duplicate ZIP entries'
        expected = {}
        for line in (ROOT / 'DEFAULT_PACK_120.tsv').read_text().splitlines():
            if not line.startswith('#'):
                name, sha, *_ = line.split('\t')
                expected[PREFIX + name] = sha
        assert set(expected) == {n for n in names if n.startswith(PREFIX)}, 'pack inventory differs from provenance ledger'
        for line in (ROOT / 'RESOURCE_IMPORT_120.tsv').read_text().splitlines():
            if not line.startswith('#'):
                sha, name = line.split('\t')
                expected[name] = sha
        for name, sha in expected.items():
            assert hashlib.sha256(jar.read(name)).hexdigest() == sha, name
        meta = json.loads(jar.read('fabric.mod.json'))
        assert meta['depends']['minecraft'] == '26.2' and meta['version'].endswith('+fabric.26.2.R3-hotfix4'), meta
        for notice in ('SimpleBedrockModel.NOTICE.md', 'SimpleBedrockModel-LGPL-3.0.txt'):
            assert jar.read('META-INF/licenses/' + notice) == (ROOT / 'LICENSES' / notice).read_bytes(), notice
        nested = {n for n in names if n.endswith('.jar')}
        required = {'mae-1.1.1.jar', 'commons-math3-3.6.1.jar', 'luaj-core-3.0.8-figura.jar', 'luaj-jse-3.0.8-figura.jar', 'bcel-6.6.1.jar'}
        assert nested == {'META-INF/jars/' + n for n in required} == {j['file'] for j in meta['jars']}, nested
        assert not any(n.endswith('.java') or n.startswith(('com/tacz/guns/test/', 'assets/port_test/', 'data/port_test/', 'com/maydaymemory/')) for n in names)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        prefix = '::notice::' if os.getenv('GITHUB_ACTIONS') else ''
        print(f'{prefix}PORT_JAR: {path.name} SHA-256 {digest}; {len(expected)} resource hashes, target metadata, no test sources, {len(nested)} declared libraries')


if __name__ == '__main__':
    [binary] = [Path(arg) for arg in sys.argv[1:] if not arg.endswith(('-sources.jar', '-javadoc.jar'))]
    audit(binary)
