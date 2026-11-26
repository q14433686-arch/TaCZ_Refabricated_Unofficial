#!/usr/bin/env python3
"""Run registry and world assertions in a separate intermediary GameTest JVM against the exact JAR."""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    parser.add_argument('--accept-test-eula', required=True, action='store_true')
    args = parser.parse_args()
    evidence = ROOT/'build/packaged-evidence/checks'
    evidence.mkdir(parents=True, exist_ok=True)
    report = evidence/'server.xml'
    report.unlink(missing_ok=True)
    with tempfile.TemporaryDirectory(prefix='tacz-12111-checks-') as tmp:
        project = Path(tmp)/'checks'
        shutil.copytree(ROOT/'verification/packaged-runtime', project, ignore=shutil.ignore_patterns('build', '.gradle'))
        shutil.copyfile(ROOT/'gradle.properties', project/'gradle.properties')
        seed = Path(tmp)/'seed'
        shutil.copytree(project/'fixtures/refit', seed/'tacz/refit')
        (seed/'eula.txt').write_text('eula=true\n')
        (seed/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\n')
        try:
            with (evidence/'server.log').open('w') as out:
                subprocess.run([ROOT/'gradlew', '-p', project, '--no-daemon', '--console=plain', '--init-script', ROOT/'scripts/ci-central.init.gradle',
                                f'-PproductJar={args.jar.resolve(strict=True)}', f'-PprobeSeed={seed}', f'-PserverChecks={report}', 'probeServer'],
                               check=True, stdout=out, stderr=subprocess.STDOUT, timeout=720)
        finally:
            log = project/'build/instance/logs/latest.log'
            if log.is_file(): shutil.copyfile(log, evidence/'latest.log')
    cases = list(ET.parse(report).iter('testcase'))
    expected = {'tacz_server_checks:' + p.stem for p in (ROOT/'verification/packaged-runtime/src/checks/resources/data/tacz_server_checks/test_instance').glob('*.json')}
    if (len(expected) < 17 or {c.get('name') for c in cases} != expected or len(cases) != len(expected)
            or any(list(c) for c in cases)):
        raise AssertionError(f'missing or failed catalog/storage GameTests: {report.read_text()[:2500]}')
    print(f'::notice::PORT_SERVER: {len(cases)} intermediary GameTests: ' + ', '.join(c.get('name', '?') for c in cases))


if __name__ == '__main__':
    main()
