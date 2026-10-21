#!/usr/bin/env python3
"""Boot a disposable Android 15 AVD with the SDK and validate immutable APKs.

Own the SDK/boot/install logs so a pre-instrumentation failure is observable in
public CI annotations. Signing keys stay in the separate build/gate steps.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time


def annotation(kind, title, message):
    message = re.sub(r'https?://\S+', '[URL omitted]', message)
    message = message.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::{kind} title={title}::{message}', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--cli', type=Path, required=True)
    parser.add_argument('--inputs', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    logs = args.output / 'runner-logs'
    logs.mkdir(exist_ok=True)
    inputs = args.inputs.resolve()
    for name in ('investment-radar-0.6.9.apk', 'instrumentation.apk', 'upgrade-probe.apk', 'previous-release.apk', 'previous-plan-release.apk'):
        if not (inputs / name).is_file():
            annotation('error', 'Android inputs', f'Missing build input: {name}')
            return 1
    if not Path('/dev/kvm').exists() or not os.access('/dev/kvm', os.R_OK | os.W_OK):
        annotation('error', 'Android acceleration', 'This CI job requires readable/writable /dev/kvm')
        return 1
    # Disk images and device data must never enter the downloadable reports.
    avd = tempfile.TemporaryDirectory(prefix='investment-radar-avd-')
    env = dict(os.environ, ANDROID_HOME=str(args.sdk.resolve()), ANDROID_SDK_ROOT=str(args.sdk.resolve()),
               ANDROID_AVD_HOME=avd.name)
    adb = [str(args.sdk / 'platform-tools/adb'), '-s', 'emulator-5554']

    def run(label, command, timeout, stdin=None):
        annotation('notice', 'Android validation', label)
        target = logs / (re.sub(r'[^a-z0-9]+', '-', label.lower()).strip('-') + '.txt')
        try:
            with target.open('w') as output:
                result = subprocess.run([str(x) for x in command], input=stdin, text=True,
                                        stdout=output, stderr=subprocess.STDOUT, env=env, timeout=timeout)
            if result.returncode:
                raise RuntimeError(f'{label} exited with code {result.returncode}')
        except (subprocess.TimeoutExpired, RuntimeError, OSError) as error:
            tail = target.read_text(errors='replace')[-9000:] if target.exists() else ''
            annotation('error', label, str(error) + '\n' + tail)
            raise

    def shell(*command):
        return subprocess.check_output([*adb, 'shell', *command], text=True,
                                       stderr=subprocess.STDOUT, timeout=20, env=env).strip()

    emulator = None
    try:
        run('Install emulator and Android 15 system image',
            [args.cli / 'sdkmanager', '--sdk_root=' + str(args.sdk), '--install',
             'emulator', 'system-images;android-35;default;x86_64'], 600, 'y\n' * 30)
        image = args.sdk / 'system-images/android-35/default/x86_64'
        if not (image / 'package.xml').is_file() or not (image / 'system.img').is_file():
            install_log = logs / 'install-emulator-and-android-15-system-image.txt'
            raise RuntimeError('The requested system image is missing from the SDK root\n' +
                               install_log.read_text(errors='replace')[-9000:])
        run('Create disposable Pixel 5 AVD',
            [args.cli / 'avdmanager', 'create', 'avd', '--force', '--name', 'ir_validation',
             '--package', 'system-images;android-35;default;x86_64', '--device', 'pixel_5'], 120, 'no\n')
        bootlog = logs / 'emulator.txt'
        with bootlog.open('w') as output:
            emulator = subprocess.Popen([str(args.sdk / 'emulator/emulator'), '-avd', 'ir_validation',
                '-port', '5554', '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot',
                '-gpu', 'swiftshader', '-accel', 'on', '-cores', '2', '-memory', '4096'],
                stdout=output, stderr=subprocess.STDOUT, env=env)
        deadline = time.monotonic() + 900
        while time.monotonic() < deadline:
            if emulator.poll() is not None:
                raise RuntimeError('Emulator exited during boot\n' + bootlog.read_text(errors='replace')[-9000:])
            try:
                if shell('getprop', 'sys.boot_completed') == '1' and shell('service', 'check', 'package').endswith(': found'):
                    break
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
                pass
            time.sleep(2)
        else:
            raise RuntimeError('Emulator/package service did not become ready within 900 seconds\n' + bootlog.read_text(errors='replace')[-9000:])
        for setting in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
            shell('settings', 'put', 'global', setting, '0')
        run('Run all 33 installed APK UI tests',
            [sys.executable, 'scripts/test_android_apk.py', inputs / 'investment-radar-0.6.9.apk',
             inputs / 'instrumentation.apk', '--adb', adb[0], '--serial', 'emulator-5554',
             '--output', args.output, '--test-class', 'com.investmentradar.app.AndroidFlowTest'], 1200)
        run('Verify update from published 0.6.4 preserves encrypted keys and portfolio',
            [sys.executable, 'scripts/test_android_upgrade.py', inputs / 'previous-release.apk',
             inputs / 'investment-radar-0.6.9.apk', inputs / 'instrumentation.apk', '--adb', adb[0],
             '--serial', 'emulator-5554', '--build-tools', args.sdk / 'build-tools/35.0.0',
             '--output', args.output / 'previous-upgrade', '--reset-test-device'], 600)
        run('Verify update from published plan release preserves saved plans',
            [sys.executable, 'scripts/test_android_upgrade.py', inputs / 'previous-plan-release.apk',
             inputs / 'investment-radar-0.6.9.apk', inputs / 'instrumentation.apk', '--adb', adb[0],
             '--serial', 'emulator-5554', '--build-tools', args.sdk / 'build-tools/35.0.0',
             '--output', args.output / 'plan-upgrade', '--reset-test-device'], 600)
        run('Verify future upgrade preserves encrypted keys and portfolio',
            [sys.executable, 'scripts/test_android_upgrade.py', inputs / 'investment-radar-0.6.9.apk',
             inputs / 'upgrade-probe.apk', inputs / 'instrumentation.apk', '--adb', adb[0],
             '--serial', 'emulator-5554', '--build-tools', args.sdk / 'build-tools/35.0.0',
             '--output', args.output / 'future-upgrade', '--reset-test-device'], 600)
        return 0
    except Exception as error:
        annotation('error', 'Android emulator validation', str(error))
        return 1
    finally:
        if emulator is not None:
            try:
                subprocess.run([*adb, 'emu', 'kill'], env=env, stdout=subprocess.DEVNULL,
                               stderr=subprocess.DEVNULL, timeout=20, check=False)
            except subprocess.TimeoutExpired:
                pass
            if emulator.poll() is None:
                emulator.terminate()
                try:
                    emulator.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    emulator.kill()
                    emulator.wait(timeout=15)
        avd.cleanup()


if __name__ == '__main__':
    raise SystemExit(main())
