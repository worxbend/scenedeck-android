#!/usr/bin/env python3
"""Run publishing Terraform using the current gcloud login without storing tokens."""

import os
from pathlib import Path
import subprocess
import sys


def main():
    if len(sys.argv) < 2:
        raise SystemExit("Usage: python3 scripts/play-infra.py <terraform command> [arguments]")
    directory = Path(__file__).resolve().parents[1] / "infra/google-play"
    environment = os.environ.copy()
    token = subprocess.run(
        ["gcloud", "auth", "print-access-token"], capture_output=True, text=True, check=True,
    ).stdout.strip()
    if not token:
        raise SystemExit("No Google Cloud access token. Run gcloud auth login first.")
    environment["GOOGLE_OAUTH_ACCESS_TOKEN"] = token
    binary = environment.get("SCENEDECK_TERRAFORM_BINARY", "terraform")
    return subprocess.run([binary, f"-chdir={directory}", *sys.argv[1:]], env=environment).returncode


if __name__ == "__main__":
    raise SystemExit(main())
