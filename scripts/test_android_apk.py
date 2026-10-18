"""Run installed-APK instrumentation; a zero adb exit is not proof of success.

Build app and androidTest APKs first, using the same signing certificate.
Resets app test data. Use a disposable Android emulator. Requires adb and adb on PATH (or --adb).
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path)
parser.add_argument('test_apk', type=Path)
parser.add_argument('--adb', default='adb')
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--test-class', default='com.investmentradar.app.AndroidFlowTest')
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
apk_hash = hashlib.sha256(args.apk.read_bytes()).hexdigest()
adb = [args.adb, '-s', args.serial]
for package in (args.apk, args.test_apk):
    if not package.is_file():
        raise SystemExit(f'APK missing: {package}')
    subprocess.run([*adb, 'install', '--no-streaming', '-r', '-t', str(package.resolve())], check=True)
# Evidence belongs to this run, never to an earlier installation.
subprocess.run([*adb, 'shell', 'rm', '-rf', '/sdcard/Pictures/InvestmentRadarValidation'], check=True)
command = [*adb, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', args.test_class,
           'com.investmentradar.app.test/androidx.test.runner.AndroidJUnitRunner']
result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
log = result.stdout
(args.output / 'instrumentation.txt').write_text(log, encoding='utf-8')
print(log)
match = re.search(r'^OK \((\d+) tests?\)', log, re.MULTILINE)
passed = hashlib.sha256(args.apk.read_bytes()).hexdigest() == apk_hash and result.returncode == 0 and match is not None and int(match[1]) > 0 and not any(
    error in log for error in ('FAILURES!!!', 'Process crashed', 'INSTRUMENTATION_FAILED',
                              'INSTRUMENTATION_STATUS_CODE: -1', 'INSTRUMENTATION_STATUS_CODE: -2'))
report = {'apkSha256': apk_hash, 'serial': args.serial,
          'testClass': args.test_class, 'passed': passed, 'tests': int(match[1]) if match else None}
(args.output / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
# Keep evidence even when a test fails; never promote a failing APK.
screens = subprocess.run([*adb, 'pull', '/sdcard/Pictures/InvestmentRadarValidation',
                          str(args.output / 'screenshots')], check=False)
if screens.returncode:
    subprocess.run([*adb, 'pull', '/sdcard/Android/data/com.investmentradar.app/files/validation-screenshots',
                    str(args.output / 'screenshots')], check=False)
raise SystemExit(0 if passed else 1)
