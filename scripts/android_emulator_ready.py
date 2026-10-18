"""Prepare a disposable emulator; never dismiss an ANR from the app under test."""
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def startup_wait_button(xml):
    root = ET.fromstring(xml)
    nodes = list(root.iter('node'))
    messages = [node.get('text', '') for node in nodes if node.get('package') == 'android']
    # Only the OS/launcher startup dialog is eligible, never Investment Radar.
    known = any(re.match(r"^(System UI|Launcher(?:3)?) (?:isn.t responding|is not responding)", text) for text in messages)
    if not known:
        return None
    for node in nodes:
        if node.get('package') == 'android' and node.get('resource-id') == 'android:id/aerr_wait':
            bounds = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
            if len(bounds) == 4:
                return ((bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2)
    return None


def prepare(adb, directory):
    report = {'ready': False, 'startupRecoveries': 0}
    target = Path(directory) / 'emulator-readiness.json'
    def save():
        target.write_text(json.dumps(report, indent=2) + '\n')
    def shell(*args):
        return subprocess.check_output([*adb, 'shell', *args], text=True, timeout=40)
    def focus():
        return [line.strip() for line in shell('dumpsys', 'window', 'windows').splitlines()
                if 'mCurrentFocus=' in line or 'mFocusedApp=' in line]
    try:
        report['initialFocus'] = focus()
        shell('am', 'force-stop', 'com.investmentradar.app')
        shell('input', 'keyevent', 'KEYCODE_WAKEUP')
        shell('wm', 'dismiss-keyguard')
        shell('input', 'keyevent', 'KEYCODE_HOME')
        for _ in range(4):
            shell('uiautomator', 'dump', '/sdcard/ir-startup-state.xml')
            xml = shell('cat', '/sdcard/ir-startup-state.xml')
            button = startup_wait_button(xml)
            if button is None:
                # A different system error must remain a failure, not be clicked away.
                root = ET.fromstring(xml)
                assert not any(n.get('resource-id') == 'android:id/aerr_wait' for n in root.iter('node')), 'Unexpected ANR before instrumentation'
                report['ready'] = True
                report['finalFocus'] = focus()
                return report
            shell('input', 'tap', str(button[0]), str(button[1]))
            report['startupRecoveries'] += 1
            time.sleep(2)
        raise RuntimeError('OS startup ANR persisted; instrumentation was not started')
    finally:
        save()
