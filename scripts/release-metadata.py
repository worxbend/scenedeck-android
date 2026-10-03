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
    track_key = "PLAY_TAG_TRACK" if tagged else "INPUT_PLAY_TRACK"
    status_key = "PLAY_TAG_RELEASE_STATUS" if tagged else "INPUT_PLAY_RELEASE_STATUS"
    track = env.get(track_key, "") or "internal"
    status = env.get(status_key, "") or "draft"
    upload_metadata = env.get("PLAY_TAG_UPLOAD_METADATA", "false") if tagged else env.get("INPUT_UPLOAD_PLAY_METADATA", "false")
    if track not in {"internal", "beta", "production"}:
        raise ValueError("Google Play track must be internal, beta, or production")
    if status not in {"draft", "completed"}:
        raise ValueError("Google Play release status must be draft or completed")
    if upload_metadata not in {"true", "false"}:
        raise ValueError("Upload Play metadata must be true or false")
    return {"variant": variant, "version_name": version, "version_code": str(int(code)), "publish_play": str(publish).lower(), "tagged": str(tagged).lower(), "play_track": track, "play_release_status": status, "upload_play_metadata": upload_metadata}


if __name__ == "__main__":
    try:
        values = metadata(os.environ)
    except (KeyError, ValueError) as error:
        raise SystemExit(str(error)) from None
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        output.writelines(f"{key}={value}\n" for key, value in values.items())
