# F-Droid publishing preparation

SceneDeck Android is MIT licensed, matching desktop SceneDeck. The F-Droid build
keeps OBS control, encrypted profiles, themes, widgets, and background connection.
Camera QR pairing is omitted; Connections offers manual OBS host/port entry.
The normal Play/GitHub distribution retains CameraX and ML Kit QR pairing.

Both distributions bundle unmodified Inter 4.1 and JetBrains Mono 2.304 fonts under
SIL OFL 1.1. Font sources/checksums and copyright/license texts are in `LICENSES`,
and the license texts are also packaged inside the APK. Neither build needs a
Google Play services font provider. The privacy policy distinguishes ML Kit's
standard-build diagnostics from the F-Droid build, which excludes the SDK.

## Build from source

Use JDK 21, Gradle 9.6.0 (provided by the wrapper), and Android SDK platform 37
(the installed SDK package is `platforms;android-37.0`). No Play Console, Google Cloud account, upload key,
service-account JSON, or signing passwords are required:

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:writeReleaseRuntimeDependencies \
  :feature:connections:testDebugUnitTest --no-configuration-cache \
  -PfdroidBuild=true -PreleaseVersionName=0.1.1 -PreleaseVersionCode=1007
```

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
F-Droid QR limitation. Version-specific release notes are in `changelogs/1007.txt`.

Copy the recipe into a checkout of `https://gitlab.com/fdroid/fdroiddata`, verify
it with `fdroid lint com.worxbend.scenedeck`, and submit a merge request following
the [official quick-start guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/).
If a recipe changes, use `fdroid rewritemeta` to retain canonical YAML formatting.
The recipe's build server must have JDK 21, Gradle 9.6.0 and SDK 37 available.
Maintainers may adjust provisioning to their current environment.

Initial submission uses explicit commit/version metadata and no automatic update
mode. Enable tag updates only after a release tag containing this build split
exists. The earlier GitHub `v0.1.0` tag predates F-Droid preparation and must not be
used for the first F-Droid source build. Normal Play/GitHub releases can continue
using the existing signed delivery workflow.

Official inclusion still requires maintainer review and a successful build on
F-Droid's infrastructure. Passing local/CI scans is evidence for the submission;
it is not a guarantee of acceptance or a claim that the app is already listed.
F-Droid's signing key usually differs from other distributions, so switching
between channels with the same package ID can require uninstalling first.
