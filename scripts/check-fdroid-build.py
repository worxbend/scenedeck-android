#!/usr/bin/env python3
"""Check F-Droid's unsigned release, dependency graph, and optionally a Git source snapshot.

Run with: uv run --with fdroidserver==2.4.5 python3 scripts/check-fdroid-build.py ...
"""

import argparse
import hashlib
import logging
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import zipfile

from fdroidserver import common, metadata, scanner

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED_QR_SOURCE = "feature/connections/src/play"
FORBIDDEN_GROUPS = ("com.google.mlkit:", "com.google.android.gms:", "com.google.firebase:", "androidx.camera:")


def sdk_tool(name):
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "")
    candidates = sorted(sdk.glob(f"build-tools/*/{name}"), reverse=True)
    if not candidates:
        raise SystemExit(f"Set ANDROID_HOME to an Android SDK with build-tools/{name}")
    return candidates[0]


def check_dependencies(path):
    dependencies = path.read_text().splitlines()
    if not dependencies:
        raise SystemExit("Runtime dependency report is empty")
    rejected = [line for line in dependencies if line.startswith(FORBIDDEN_GROUPS) or "ui-text-google-fonts:" in line]
    if rejected:
        raise SystemExit("Excluded dependencies remain: " + ", ".join(rejected))
    print(f"Runtime graph checked: {len(dependencies)} components, no QR/Google services SDKs")


def check_apk(apk):
    result = subprocess.run([str(sdk_tool("aapt")), "dump", "badging", str(apk)], capture_output=True, text=True, check=True)
    if "package: name='com.worxbend.scenedeck'" not in result.stdout:
        raise SystemExit("Wrong application ID")
    if "android.permission.CAMERA" in result.stdout or "android.hardware.camera" in result.stdout:
        raise SystemExit("F-Droid APK still declares camera access")
    if "application-debuggable" in result.stdout:
        raise SystemExit("F-Droid package must be a release APK")
    signature = subprocess.run([str(sdk_tool("apksigner")), "verify", str(apk)], capture_output=True, text=True)
    if signature.returncode == 0:
        raise SystemExit("F-Droid source builds must be unsigned; release signing was enabled")
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        for license_name in ["Inter-OFL-1.1.txt", "JetBrainsMono-OFL-1.1.txt", "SceneDeck-MIT.txt"]:
            if f"assets/{license_name}" not in names:
                raise SystemExit(f"Missing bundled license: {license_name}")
        # AAPT can shorten resource paths; compare font contents rather than APK names.
        expected_fonts = sorted(hashlib.sha256(path.read_bytes()).hexdigest()
                                for path in (ROOT / "core/designsystem/src/main/res/font").glob("*.ttf"))
        packaged_fonts = sorted(hashlib.sha256(archive.read(name)).hexdigest()
                                for name in names if name.startswith("res/") and name.endswith(".ttf"))
        if len(expected_fonts) != 8 or packaged_fonts != expected_fonts:
            raise SystemExit("Bundled font set does not match the eight licensed source files")
    problems = scanner.scan_binary(str(apk))
    if problems:
        raise SystemExit(f"F-Droid binary scanner found {problems} problems")
    print(result.stdout.splitlines()[0])
    print(f"Unsigned APK passed F-Droid binary scan: SHA-256 {hashlib.sha256(apk.read_bytes()).hexdigest()}")


def check_source(ref):
    commit = subprocess.run(["git", "rev-parse", "--verify", f"{ref}^{{commit}}"], cwd=ROOT, capture_output=True, text=True, check=True).stdout.strip()
    with tempfile.TemporaryDirectory(prefix="scenedeck-fdroid-source-") as directory:
        source = Path(directory)
        with tempfile.TemporaryFile() as archive:
            subprocess.run(["git", "archive", "--format=tar", commit], cwd=ROOT, stdout=archive, check=True)
            archive.seek(0)
            with tarfile.open(fileobj=archive) as contents:
                contents.extractall(source, filter="data")
        excluded = source / EXCLUDED_QR_SOURCE
        if not excluded.is_dir():
            raise SystemExit("Source snapshot lacks the distribution split; use a prepared commit")
        shutil.rmtree(excluded)
        build = metadata.Build({"gradle": ["yes"], "gradleprops": ["fdroidBuild=true"]})
        problems = scanner.scan_source(str(source), build)
        if problems:
            raise SystemExit(f"F-Droid source scanner found {problems} problems")
    print(f"F-Droid source scan passed for {commit}; removed only {EXCLUDED_QR_SOURCE}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--dependencies", type=Path, required=True)
    parser.add_argument("--source-ref", help="Scan this exact Git commit after the packaging recipe's QR exclusion")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    common.read_config()
    check_dependencies(args.dependencies)
    check_apk(args.apk.resolve())
    if args.source_ref:
        check_source(args.source_ref)


if __name__ == "__main__":
    main()
