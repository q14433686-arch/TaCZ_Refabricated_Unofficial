import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / 'scripts/report_ci_failures.py'


class FailureReportTest(unittest.TestCase):
    def test_independent_world_threads_do_not_disappear_inside_grouped_annotations(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'client.log'
            records = ['PORT_WORLD_STALL: ASYNC cycle=1 phase=3617 registered=3 arrived=2']
            for name in ('Server', 'Render', 'Test'):
                records.append(f'PORT_WORLD_STALL: ASYNC {name} thread WAITING ' + 'x' * 2600 + f' {name}_LAST_FRAME')
            records += [f'PORT_WORLD_STALL: IO-Worker-{i} ' + 'y' * 2500 for i in range(15)]
            records.append('java.lang.AssertionError: Timeout loading world')
            path.write_text('\n'.join(records))
            result = subprocess.run([sys.executable, str(SCRIPT), str(path)], cwd=directory,
                                    text=True, capture_output=True, check=True)
            errors = [line for line in result.stdout.splitlines() if line.startswith('::error::')]
            self.assertLessEqual(len(errors), 10)
            for name in ('Server', 'Render', 'Test'):
                self.assertTrue(any(f'ASYNC {name} thread' in line and f'{name}_LAST_FRAME' in line for line in errors), name)
            self.assertIn('ASYNC cycle=1 phase=3617', errors[0])

    def test_ordinary_failures_are_still_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'build.log'
            path.write_text('SomeFile.java:3: error: incompatible types\nactual compiler details\n')
            result = subprocess.run([sys.executable, str(SCRIPT), str(path)], cwd=directory,
                                    text=True, capture_output=True, check=True)
            self.assertIn('error: incompatible types', result.stdout)
            self.assertIn('actual compiler details', result.stdout)


if __name__ == '__main__':
    unittest.main()
