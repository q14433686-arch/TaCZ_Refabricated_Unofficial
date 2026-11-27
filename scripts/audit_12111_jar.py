#!/usr/bin/env python3
"""Audit the 1.21.11 intermediary distributable; do not accept a named or sources JAR."""
import hashlib
import json
from pathlib import Path
import sys
from zipfile import ZipFile

from verify_12111_pack import pack_entries, verify

ROOT = Path(__file__).resolve().parents[1]
PACK = 'assets/tacz/custom/tacz_default_gun/'


def audit(product):
    source = ROOT / 'src/main/resources'
    with ZipFile(product) as jar:
        files = [entry.filename for entry in jar.infolist() if not entry.is_dir()]
        assert len(set(files)) == len(files), 'duplicate resources in distributable'
        meta = json.loads(jar.read('fabric.mod.json'))
        assert meta['id'] == 'tacz' and meta['depends']['minecraft'] == '1.21.11' and meta['depends']['java'] == '>=21'
        assert f"mod_version={meta['version']}" in (ROOT / 'gradle.properties').read_text().splitlines()
        assert jar.read(meta['accessWidener']).splitlines()[0].split()[-1] == b'intermediary', 'not a remapJar'
        refmap = json.loads(jar.read('tacz.refmap.json'))
        assert refmap['mappings'] and 'net/minecraft/class_' in json.dumps(refmap), 'empty or named refmap'
        for config in meta['mixins']:
            assert json.loads(jar.read(config))['refmap'] == 'tacz.refmap.json', config
        assert int.from_bytes(jar.read('com/tacz/guns/resource/GunPackLoader.class')[6:8], 'big') == 65
        assert not any(n.startswith('com/tacz/probe/') or n.endswith(('.java', 'Test.class')) for n in files)
        nested = {n for n in files if n.endswith('.jar')}
        assert nested == {e['file'] for e in meta['jars']} == {'META-INF/jars/' + name for name in (
            'mae-1.1.1.jar', 'commons-math3-3.6.1.jar', 'luaj-core-3.0.8-figura.jar', 'luaj-jse-3.0.8-figura.jar', 'bcel-6.6.1.jar')}, nested
        # The manifest pins winning bytes, not just files that happen to exist in this checkout.
        expected = {row[0]: row[1] for line in (ROOT/'DEFAULT_PACK_120.tsv').read_text().splitlines()
                    if not line.startswith('#') for row in [line.split('\t')]}
        verify(pack_entries(jar), lambda name: jar.read(PACK + name), expected)
        for line in (ROOT/'RESOURCE_IMPORT_120.tsv').read_text().splitlines():
            if not line.startswith('#'):
                sha, name = line.split('\t')
                assert hashlib.sha256(jar.read(name)).hexdigest() == sha, name
        # Native surface light samples the center of this albedo. Missing-texture black
        # looked like a valid zero-light result even while 80 terrain patches were submitted.
        white = 'assets/tacz/textures/effect/surface_white.png'
        assert hashlib.sha256(jar.read(white)).hexdigest() == '7ac2abfce2be8b46dd9826d597d94edeefc1570b4a992622721b90a1850e3ee9', white
        shader = 'assets/tacz/shaders/core/scope_body.vsh'
        assert jar.read(shader) == (source / shader).read_bytes(), '26.x bundle shadowed the target shader'
    with product.open('rb') as f:
        digest = hashlib.file_digest(f, 'sha256').hexdigest()
    print(f'::notice::PORT_JAR: {product.name} SHA-256 {digest}; Java 21, intermediary AW and refmap, {len(expected)} effective default pack files, {len(nested)} libraries')


if __name__ == '__main__':
    [binary] = [Path(p) for p in sys.argv[1:] if not p.endswith(('-dev.jar', '-sources.jar', '-javadoc.jar'))]
    audit(binary)
