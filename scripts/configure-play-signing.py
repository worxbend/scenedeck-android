#!/usr/bin/env python3
"""Prompt locally for signing passwords and upload them to GitHub Actions secrets."""

import argparse
import base64
import getpass
import os
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--keystore", type=Path, default=Path.home() / "scenedeck-signing/scenedeck-upload.jks")
    parser.add_argument("--alias", default="scenedeck-upload")
    parser.add_argument("--repo", default="worxbend/scenedeck-android")
    parser.add_argument("--separate-key-password", action="store_true")
    args = parser.parse_args()
    if not args.keystore.is_file():
        parser.error("Keystore file does not exist")
    subprocess.run(["gh", "auth", "status"], check=True)
    password = getpass.getpass("Upload keystore password (hidden): ")
    if not password:
        parser.error("Password must not be empty")
    environment = os.environ.copy()
    environment["SCENEDECK_UPLOAD_PASSWORD"] = password
    result = subprocess.run(
        ["keytool", "-list", "-keystore", str(args.keystore), "-alias", args.alias,
         "-storepass:env", "SCENEDECK_UPLOAD_PASSWORD"],
        env=environment, capture_output=True,
    )
    if result.returncode:
        raise SystemExit("Could not unlock this keystore/alias. No secrets were uploaded.")
    key_password = getpass.getpass("Alias password (hidden): ") if args.separate_key_password else password
    secrets = {
        "ANDROID_KEYSTORE_BASE64": base64.b64encode(args.keystore.read_bytes()),
        "ANDROID_KEYSTORE_PASSWORD": password.encode(),
        "ANDROID_KEY_ALIAS": args.alias.encode(),
        "ANDROID_KEY_PASSWORD": key_password.encode(),
    }
    for name, value in secrets.items():
        subprocess.run(["gh", "secret", "set", name, "--repo", args.repo], input=value, check=True)
    print("All four signing secrets configured. Passwords were not saved to disk.")


if __name__ == "__main__":
    main()
