"""Packaging checks run without Gradle; these are not game/runtime tests."""
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

SPEC = importlib.util.spec_from_file_location('audit_12111', Path(__file__).resolve().parents[1] / 'scripts/audit_12111_foundation_jar.py')
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)


class ProductionJarAuditTest(unittest.TestCase):
    def fixture(self, replacements=None):
        files = {
            'fabric.mod.json': json.dumps({'id': 'tacz', 'depends': {'minecraft': '1.21.11'}, 'mixins': ['tacz.mixins.json']}),
            'tacz.mixins.json': '{}',
            'tacz.refmap.json': '{"mappings":{"example":{"named":"intermediary"}}}',
            'tacz.accesswidener': 'accessWidener v2 intermediary\n',
            'data/tacz/gunpack_conversion_rules.json': '{"target_minecraft":"1.21.11"}',
        }
        for locale in ('en_us', 'zh_cn'):
            files[f'assets/tacz/lang/{locale}.json'] = '{"message.tacz.pack_compatibility.notice":"notice"}'
        for name in ('GunPackOverlayResolver', 'network/GunPackSync', 'convert/UniversalPackConverter'):
            files[f'com/tacz/guns/resource/{name}.class'] = b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, 65)
        files.update(replacements or {})
        return files

    def check(self, files):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'product.jar'
            with zipfile.ZipFile(path, 'w') as jar:
                for name, data in files.items():
                    jar.writestr(name, data)
            return AUDIT.audit(path)

    def test_correct_receiver_packaging(self):
        self.assertEqual([], self.check(self.fixture()))

    def test_dev_jar_is_not_a_distribution(self):
        errors = self.check(self.fixture({'tacz.accesswidener': 'accessWidener v2 named\n', 'tacz.refmap.json': '{"mappings":{}}'}))
        self.assertTrue(any('access widener' in error for error in errors))
        self.assertTrue(any('refmap' in error for error in errors))

    def test_donor_classes_and_test_sources_fail(self):
        errors = self.check(self.fixture({
            'com/tacz/guns/resource/GunPackOverlayResolver.class': b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, 69),
            'export-test/probe.txt': 'not for shipping',
            'com/tacz/guns/test/Probe.java': 'not for shipping',
            'data/tacz/gunpack_conversion_rules.json': '{"target_minecraft":"26.1.2"}',
        }))
        self.assertTrue(any('Java 21' in error for error in errors))
        self.assertEqual(2, sum('non-production content' in error for error in errors))
        self.assertTrue(any('donor game' in error for error in errors))

    def test_missing_nested_dependency_fails(self):
        mod = {'id': 'tacz', 'depends': {'minecraft': '1.21.11'}, 'jars': [{'file': 'META-INF/jars/mae.jar'}]}
        self.assertTrue(any('nested dependency' in error for error in self.check(self.fixture({'fabric.mod.json': json.dumps(mod)}))))


if __name__ == '__main__':
    unittest.main()
