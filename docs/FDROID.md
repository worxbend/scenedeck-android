# F-Droid publishing

SceneDeck Android is MIT licensed, matching desktop SceneDeck. The F-Droid build
keeps OBS control, encrypted profiles, themes, widgets, and background connection.
Camera QR pairing is omitted; Connections offers manual OBS host/port entry.
The normal Play/GitHub distribution retains CameraX and ML Kit QR pairing.

Both distributions bundle unmodified Inter 4.1 and JetBrains Mono 2.304 fonts under
SIL OFL 1.1. Font sources/checksums and copyright/license texts are in `LICENSES`,
and the license texts are also packaged inside the APK. Neither build needs a
Google Play services font provider. The privacy policy distinguishes ML Kit's
standard-build diagnostics from the F-Droid build, which excludes the SDK.

## Submission status

SceneDeck **0.1.1 (1007)** was submitted in
[fdroiddata merge request !51067](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51067).
It is awaiting maintainer review and is not yet listed in the official repository.
Review feedback (R8, category, reproducible builds, cleartext question) was answered
by releasing **0.1.2 (1008)**, which replaces 0.1.1 in the merge request.
The initial [F-Droid pipeline](https://gitlab.com/worxbend/fdroiddata/-/pipelines/2909789939)
passed all nine jobs, including build, APK/source checks, metadata schema, lint,
formatting and update detection. GitLab reruns checks when the merge request opens.

The release source is tagged `fdroid-v0.1.1` at
`64b0f81a195b666614d189036c7300fa017789c4`. The recipe was also built locally using
F-Droid's `buildserver-trixie` image and its Gradle wrapper; the resulting unsigned
APK passed the dependency, permission, font/license and F-Droid binary checks.
Regular debug/unit/Roborazzi/security checks and the upstream F-Droid CI passed.

Release builds use R8 code and resource shrinking (about 7 MiB instead of 40 MiB).
The F-Droid build is reproducible: building the same commit with Temurin 21 on the
host and with Debian OpenJDK 21 inside F-Droid's `buildserver-trixie` image under a
different path produced a byte-identical unsigned APK. AGP's VCS-info file and
encrypted dependency-metadata block are disabled because they break reproducibility.

F-Droid therefore publishes the **upstream-signed** APK. The recipe lists `Binaries`
(the GitHub release asset) and `AllowedAPKSigningKeys` (the upload certificate,
SHA-256 `48e613d25d603670a423da9ee69f980ef8f687cd4518756724e372f4427f8800`). F-Droid
builds from source, copies the upstream signature onto its own unsigned output with
apksigcopier and only publishes when it verifies. GitHub release APKs use the same
upload key; Play installs are re-signed by Google Play App Signing, so moving between
Play and F-Droid still requires uninstalling first.

Cleartext traffic is enabled on purpose: obs-websocket only speaks plain `ws://` on
the local network (OBS has no TLS), and hosts are arbitrary LAN IPs that a network
security config cannot enumerate. Passwords use the protocol's challenge/response
and stay Keystore-encrypted at rest.

## Build from source

Use JDK 21, Gradle 9.6.0 (provided by the wrapper), and Android SDK platform 37
(the installed SDK package is `platforms;android-37.0`). No Play Console, Google Cloud account, upload key,
service-account JSON, or signing passwords are required:

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:writeReleaseRuntimeDependencies \
  :feature:connections:testDebugUnitTest --no-configuration-cache \
  -PfdroidBuild=true
```

F-Droid versions come from `fdroid/version.properties` (currently 0.1.2 / 1008).
The normal GitHub/Play workflow can still pass explicit release version properties.

The release output is `app/build/outputs/apk/release/app-release-unsigned.apk`.
F-Droid signs its own source-built packages. `fdroidBuild=true` ignores upload
signing environment variables. For local device testing, install the debug APK;
the unsigned release APK is not directly installable.

The build selects Kotlin sources and the manifest under
`feature/connections/src/fdroid`. It never loads the normal scanner dependency
list under `src/play`; CameraX, ML Kit, Google Play services libraries and camera
permission are absent from the resulting package. Shared manual profiles and
credential storage remain unchanged. Invalid `fdroidBuild` values fail the build.

## Validate

After committing the prepared source, run:

```bash
uv run --with fdroidserver==2.4.5 python3 scripts/check-fdroid-build.py \
  --apk app/build/outputs/apk/release/app-release-unsigned.apk \
  --dependencies app/build/reports/fdroid/release-runtime-dependencies.txt \
  --source-ref HEAD
```

This checks the resolved dependency graph, application ID, absence of camera
permission, bundled fonts/license notices, and unsigned release output. It runs
F-Droid's real binary and source scanners, including current non-free signatures.
The source scan uses an exact Git snapshot in a temporary directory and removes
only `feature/connections/src/play`, matching the packaging recipe; it does not
modify your checkout or suppress other scanner findings.

Run the normal `scripts/check-quality.sh` gate as well. The dedicated
`Verify F-Droid source build` GitHub workflow checks each main push/PR and retains
the unsigned package and dependency report as downloadable artifacts.

## Submit to the official repository

The packaging recipe is `fdroid/metadata/com.worxbend.scenedeck.yml`. It pins a full
prepared Git commit, passes the distribution/version properties, and removes only
the excluded Play QR source folder before scanning. Descriptions and screenshots
come from `fastlane/metadata/android/en-US`; the description explicitly states the
F-Droid QR limitation. Version-specific release notes are in `changelogs/<versionCode>.txt`.

Copy the recipe into a checkout of `https://gitlab.com/fdroid/fdroiddata`, verify
it with `fdroid lint com.worxbend.scenedeck`, and submit a merge request following
the [official quick-start guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/).
If a recipe changes, use `fdroid rewritemeta` to retain canonical YAML formatting.
The recipe's build server must have JDK 21, Gradle 9.6.0 and SDK 37 available.
Maintainers may adjust provisioning to their current environment.

F-Droid releases use `fdroid-vX.Y.Z` tags, with `AutoUpdateMode: Version` and
`UpdateCheckData` reading the static version file. To ship an update, increase both
values in `fdroid/version.properties`, add `changelogs/<versionCode>.txt`, validate
and commit the changes, then push the matching tag. The dedicated F-Droid workflow
verifies the tag, then its `signed-release` job signs that exact scanned APK with the
upload key (`ANDROID_KEYSTORE_*` secrets; v2/v3 only with `--alignment-preserved`, so
the ZIP stays byte-identical), checks the certificate digest and publishes
`scenedeck-fdroid-X.Y.Z.apk` as the GitHub release F-Droid's `Binaries` URL points at.
Never rebuild or re-sign that APK by other means: F-Droid will refuse an update whose
upstream signature does not match its own build. These tags do not trigger the normal `v*` GitHub/Play
publishing workflow. The earlier `v0.1.0` tag predates F-Droid preparation and must
not be used for the first F-Droid build.

Official inclusion still requires maintainer review and a successful build on
F-Droid's infrastructure. Passing local/CI scans is evidence for the submission;
it is not a guarantee of acceptance or a claim that the app is already listed.
