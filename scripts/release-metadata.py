#!/usr/bin/env python3
"""Validate workflow input before it becomes Gradle properties or artifact names."""

import os
import re

SEMVER = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?")


def metadata(env):
    tagged = env.get("GITHUB_REF_TYPE") == "tag"
    tag = env.get("GITHUB_REF_NAME", "")
    version = tag.removeprefix("v") if tagged else env.get("INPUT_VERSION_NAME", "") or "0.1.0"
    if (tagged and not tag.startswith("v")) or not SEMVER.fullmatch(version):
        raise ValueError("Version must be X.Y.Z or X.Y.Z-prerelease; release tags must start with v")
    variant = "release" if tagged else env.get("INPUT_VARIANT", "debug")
    if variant not in {"debug", "release"}:
        raise ValueError("Build variant must be debug or release")
    code = env.get("INPUT_VERSION_CODE", "") or str(1000 + int(env["GITHUB_RUN_NUMBER"]))
    if not code.isascii() or not code.isdecimal() or not 1 <= int(code) <= 2_100_000_000:
        raise ValueError("Version code must be an integer between 1 and 2100000000")
    publish = env.get("PLAY_PUBLISH_ENABLED", "false") == "true" if tagged else env.get("INPUT_PUBLISH_PLAY", "false") == "true"
    if publish and variant != "release":
        raise ValueError("Google Play publishing requires the release variant")
    return {"variant": variant, "version_name": version, "version_code": str(int(code)), "publish_play": str(publish).lower(), "tagged": str(tagged).lower()}


if __name__ == "__main__":
    try:
        values = metadata(os.environ)
    except (KeyError, ValueError) as error:
        raise SystemExit(str(error)) from None
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        output.writelines(f"{key}={value}\n" for key, value in values.items())
