#!/usr/bin/env python3
"""Reject APK updates that would require uninstalling the previous release.

Uses public APK metadata and signing certificates only. Never opens a keystore.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess


def apk_identity(apk, tools):
    badging = subprocess.check_output([str(tools / "aapt"), "dump", "badging", str(apk)], text=True)
    package = re.search(r"package: name='([^']+)' versionCode='(\d*)' versionName='([^']*)'", badging)
    if not package:
        raise ValueError("APK package/version metadata is missing")
    certificates = subprocess.check_output([str(tools / "apksigner"), "verify", "--print-certs", str(apk)], text=True)
    signers = re.findall(r"certificate SHA-256 digest: ([0-9a-fA-F]+)", certificates)
    if not signers:
        raise ValueError("APK has no verified signing certificate")
    return {"package": package[1], "versionCode": int(package[2] or 0), "versionName": package[3],
            "certificates": sorted(s.lower() for s in signers),
            "sha256": hashlib.sha256(Path(apk).read_bytes()).hexdigest()}


def validate_update(previous, candidate):
    if previous["package"] != candidate["package"]:
        raise ValueError("Application ID changed: this would install a separate app with empty settings")
    if previous["certificates"] != candidate["certificates"]:
        raise ValueError("Signing certificate changed: do not uninstall; sign the update with the original release key")
    if candidate["versionCode"] <= previous["versionCode"]:
        raise ValueError("The update must increase versionCode")
    return {"compatible": True, "previous": previous, "candidate": candidate}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("previous", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--build-tools", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = validate_update(apk_identity(args.previous, args.build_tools), apk_identity(args.candidate, args.build_tools))
    except (ValueError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Update rejected: {error}\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
