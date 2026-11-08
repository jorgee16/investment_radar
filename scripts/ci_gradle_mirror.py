#!/usr/bin/env python3
"""CI-only fallback to a SHA-256-pinned Gradle 8.9 archive.

The normal wrapper URL is unchanged in Git. Gradle's service currently redirects
to a GitHub release (HTTP 504 on hosted runners). MacPorts mirrors the exact
distribution file. The existing Gradle wrapper verifies its *official* SHA-256
before extracting or executing this download.

No credentials are read; the URL change is local to each ephemeral CI checkout.
"""
from pathlib import Path
import sys

WRAPPER = Path("android/gradle/wrapper/gradle-wrapper.properties")
SHA256 = "d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab"
MIRROR = r"https\://distfiles.macports.org/gradle/gradle-8.9-bin.zip"
ORIGINS = (
    r"https\://services.gradle.org/distributions/gradle-8.9-bin.zip",
    r"https\://downloads.gradle.org/distributions/gradle-8.9-bin.zip",
)


def prepare(path: Path) -> None:
    content = path.read_text(encoding="utf-8")
    if "distributionSha256Sum=" + SHA256 not in content:
        raise ValueError("Gradle distribution checksum is missing or unapproved")
    if "validateDistributionUrl=true" not in content:
        raise ValueError("Gradle wrapper URL validation must remain enabled")
    if "distributionUrl=" + MIRROR in content:
        return
    for url in ORIGINS:
        original = "distributionUrl=" + url
        if original in content:
            path.write_text(content.replace(original, "distributionUrl=" + MIRROR),
                            encoding="utf-8")
            return
    raise ValueError("Unexpected Gradle wrapper distribution URL")


if __name__ == "__main__":
    prepare(Path(sys.argv[1]) if len(sys.argv) > 1 else WRAPPER)
    print("CI mirror: MacPorts Gradle 8.9, SHA-256 pinned and verified by wrapper")
