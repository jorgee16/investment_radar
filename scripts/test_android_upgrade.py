#!/usr/bin/env python3
"""Prove encrypted credentials survive installation of a newer signed APK.

Only runs on a disposable emulator. Initial reset is explicit; no uninstall or
app-data reset is permitted between the seed and verification phases.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
from check_android_update import apk_identity, validate_update


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("previous", type=Path)
    p.add_argument("candidate", type=Path)
    p.add_argument("test_apk", type=Path)
    p.add_argument("--adb", default="adb")
    p.add_argument("--serial", required=True)
    p.add_argument("--build-tools", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--reset-test-device", action="store_true")
    a = p.parse_args()
    if not a.serial.startswith("emulator-") or not a.reset_test_device:
        p.error("Use a disposable emulator and explicitly pass --reset-test-device")
    compatibility = validate_update(apk_identity(a.previous, a.build_tools), apk_identity(a.candidate, a.build_tools))
    test = apk_identity(a.test_apk, a.build_tools)
    package = compatibility["previous"]["package"]
    if test["certificates"] != compatibility["candidate"]["certificates"]:
        p.error("Instrumentation APK must use the release signing certificate")
    a.output.mkdir(parents=True, exist_ok=True)
    result_path = a.output / "result.json"
    result_path.unlink(missing_ok=True)
    adb = [a.adb, "-s", a.serial]
    def call(*args, timeout=180):
        return subprocess.check_output([*adb, *args], text=True, stderr=subprocess.STDOUT, timeout=timeout)
    installed = call("shell", "pm", "list", "packages", package)
    if f"package:{package}\n" in installed:
        call("uninstall", package) # Initial reset only; fixtures belong to this disposable test device.
    call("install", "-r", str(a.previous.resolve()))
    call("install", "-r", str(a.test_apk.resolve()))
    def phase(name, identity):
        log = call("shell", "am", "instrument", "-w", "-r", "-e", "class", "com.investmentradar.app.CredentialUpgradeTest",
                   "-e", "phase", name, "-e", "expectedVersion", identity["versionName"],
                   test["package"] + "/androidx.test.runner.AndroidJUnitRunner", timeout=240)
        (a.output / (name + ".txt")).write_text(log)
        if not re.search(r"OK \(1 test\)", log) or "FAILURES!!!" in log or "INSTRUMENTATION_FAILED" in log:
            raise RuntimeError(f"Credential upgrade phase {name} failed; see its instrumentation report")
        print(name + ": passed", flush=True)
    phase("seed", compatibility["previous"])
    call("install", "-r", str(a.candidate.resolve())) # Actual package replacement; retain data and Keystore.
    phase("verify", compatibility["candidate"])
    result = {"passed": True, "serial": a.serial, "syntheticProvidersVerified": 5,
              "encryptedBlobPreserved": True, "appUidPreserved": True, "keystoreDecryptionPreserved": True,
              "partialSettingsSavePreservesKeys": True, "uiRecognizesSavedKeys": True,
              "uninstalledBetweenVersions": False, "compatibility": compatibility}
    result_path.write_text(json.dumps(result, indent=2) + "\n")
    print("PASS: all five encrypted provider keys survived the APK update")


if __name__ == "__main__":
    main()
