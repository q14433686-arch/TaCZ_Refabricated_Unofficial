import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('persistence_audit', Path(__file__).resolve().parents[1] / 'scripts/verify_block_entity_persistence.py')
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class PersistenceAuditTests(unittest.TestCase):
    def check_source(self, read, write, marker=''):
        audit.errors.clear()
        source = f'''class Fixture extends BlockEntity {{
            {marker}
            public void loadAdditional(ValueInput input) {{ {read} }}
            protected void saveAdditional(ValueOutput output) {{ {write} }}
        }}'''
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'Fixture.java'
            path.write_text(source)
            audit.check_block_entity(path)
        return list(audit.errors)

    def test_optional_string_read_is_a_read(self):
        self.assertEqual([], self.check_source('input.getString("owner_name").orElse("");', 'output.putString("owner_name", name);'))

    def test_valid_alias_migration_still_requires_canonical_read(self):
        marker = '// tacz:read-only-key:OwnerProfile->profile'
        self.assertEqual([], self.check_source('input.read("OwnerProfile", CODEC); input.read("profile", CODEC);', 'output.store("profile", CODEC, owner);', marker))
        self.assertTrue(self.check_source('input.read("OwnerProfile", CODEC);', 'output.store("profile", CODEC, owner);', marker))

    def test_marker_cannot_hide_missing_destination_write(self):
        errors = self.check_source('input.read("OwnerProfile", CODEC);', '', '// tacz:read-only-key:OwnerProfile->profile')
        self.assertTrue(any('invalid key migration' in error for error in errors))

    def test_unannotated_asymmetry_is_still_rejected(self):
        self.assertTrue(self.check_source('input.read("old", CODEC);', 'output.store("new", CODEC, value);'))


if __name__ == '__main__':
    unittest.main()
