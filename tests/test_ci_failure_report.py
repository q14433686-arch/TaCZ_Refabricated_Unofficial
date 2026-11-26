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
            records += ['PORT_WORLD_STALL: ConfigurationState client=RegistryCollector pending=true',
                        'PORT_WORLD_STALL: ConfigurationState server=currentTask=fabric:registry_sync']
            records += [f'PORT_WORLD_STALL: IO-Worker-{i} ' + 'y' * 2500 for i in range(15)]
            records.append('java.lang.AssertionError: Timeout loading world')
            path.write_text('\n'.join(records))
            result = subprocess.run([sys.executable, str(SCRIPT), str(path)], cwd=directory,
                                    text=True, capture_output=True, check=True)
            errors = [line for line in result.stdout.splitlines() if line.startswith('::error::')]
            self.assertLessEqual(len(errors), 10)
            for name in ('Server', 'Render', 'Test'):
                self.assertTrue(any(f'ASYNC {name} thread' in line and f'{name}_LAST_FRAME' in line for line in errors), name)
            self.assertIn('ConfigurationState client=RegistryCollector pending=true', errors[0])
            self.assertIn('ConfigurationState server=currentTask=fabric:registry_sync', errors[1])
            self.assertIn('ASYNC cycle=1 phase=3617', errors[2])

    def test_junit_failure_retains_test_identity_and_project_source_line(self):
        with tempfile.TemporaryDirectory() as directory:
            result_dir = Path(directory) / 'build/test-results/test'
            result_dir.mkdir(parents=True)
            (result_dir / 'TEST-Example.xml').write_text('''<testsuite><testcase classname="cn.sh1rocu.tacz.util.ExampleTest" name="validInput()">
                <failure message="expected: true but was: false">org.opentest4j.AssertionFailedError
                    at org.junit.jupiter.api.Assertions.assertTrue(Assertions.java:1)
                    at cn.sh1rocu.tacz.util.ExampleTest.validInput(ExampleTest.java:21)
                </failure></testcase></testsuite>''')
            result = subprocess.run([sys.executable, str(SCRIPT)], cwd=directory,
                                    text=True, capture_output=True, check=True)
            self.assertIn('JUnit cn.sh1rocu.tacz.util.ExampleTest#validInput()', result.stdout)
            self.assertIn('ExampleTest.java:21', result.stdout)
            self.assertIn('expected: true but was: false', result.stdout)

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
