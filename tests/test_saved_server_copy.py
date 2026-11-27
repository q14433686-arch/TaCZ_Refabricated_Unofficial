"""The disposable restart must preserve FCAP's actual server config location, not reseed it."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1] / 'scripts'
sys.path.insert(0, str(SCRIPTS))
SPEC = importlib.util.spec_from_file_location('connection_copy', SCRIPTS / 'test_12111_connection.py')
CONNECTION = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CONNECTION)


class SavedServerCopyTest(unittest.TestCase):
    def test_global_config_world_override_and_world_state_survive_independent_restart(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            instance, saved = root / 'original', root / 'restored'
            files = {
                'world/level.dat': b'world-state',
                'world/serverconfig/other-server.toml': b'world-override',
                'tacz/edited-pack/note.txt': b'keep-edits',
                'config/tacz-server.toml': b'[misc]\nCrawlMaxUpAngle = 12.0\n',
                'eula.txt': b'eula=true\n',
                'server.properties': b'server-port=12345\n',
            }
            for name, data in files.items():
                path = instance / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
            CONNECTION.copy_saved_server(instance, saved)
            for name, data in files.items():
                self.assertEqual((saved / name).read_bytes(), data, name)
            (instance / 'config/tacz-server.toml').write_bytes(b'changed-after-copy')
            self.assertEqual((saved / 'config/tacz-server.toml').read_bytes(), files['config/tacz-server.toml'])
            self.assertFalse((saved / 'defaultconfigs').exists(), 'Restart must not regenerate configs from the initial fixture')


if __name__ == '__main__':
    unittest.main()
