# Building APKs and publishing to Google Play

The **Build and publish Android** workflow (`.github/workflows/release.yml`) builds
installable APKs and, for signed releases, an Android App Bundle for Google Play.
The application ID is `com.scenedeck.android`.

## Download a development APK

Open GitHub → Actions → **Build and publish Android** → **Run workflow**. Select
`variant: debug` and leave `publish_play: false`. Download the APK artifact from the
finished run. Debug builds require no signing or Play credentials.

## Prepare release signing

Create an upload keystore, or use the existing upload key registered for this app.
Keep the same key for future releases. Google Play App Signing manages the separate
app-signing key; the upload key authenticates the bundle you submit.
[Android signing guidance](https://developer.android.com/studio/publish/app-signing).

For a new upload key, run this locally and enter passwords interactively:

```bash
umask 077
keytool -genkeypair -keystore scenedeck-upload.jks -alias scenedeck-upload \
  -keyalg RSA -keysize 3072 -validity 10000
```

Add these **repository Actions secrets** in GitHub → Settings → Secrets and
variables → Actions. Values are credentials, not repository variables.

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded upload keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Alias inside the keystore, such as `scenedeck-upload` |
| `ANDROID_KEY_PASSWORD` | Password for that alias |
| `PLAY_SERVICE_ACCOUNT_JSON` | Complete Google service-account JSON, required only for Play publication |

For the new key generated above, use the keystore password for
`ANDROID_KEY_PASSWORD` too; existing keystores may have a separate alias password.

The GitHub CLI can set them without putting passwords into shell arguments:

```bash
base64 -w0 /secure/path/scenedeck-upload.jks | gh secret set ANDROID_KEYSTORE_BASE64
gh secret set ANDROID_KEYSTORE_PASSWORD
gh secret set ANDROID_KEY_ALIAS
gh secret set ANDROID_KEY_PASSWORD
gh secret set PLAY_SERVICE_ACCOUNT_JSON < /secure/path/play-service-account.json
```

The keystore and JSON files stay outside the repository. Generated runner copies
are temporary files; release artifacts contain only the APK and AAB.

## Set up the Play app once

Create the app in Play Console for `com.scenedeck.android`, enroll in Play App
Signing, and complete the required store listing and policy declarations. Build a
signed release with publication disabled and upload its AAB manually for the first
release. Fastlane requires an existing app with an initial binary upload before its
API upload workflow can be used. [Fastlane setup](https://docs.fastlane.tools/getting-started/android/setup/),
[initial-upload requirement](https://docs.fastlane.tools/actions/upload_to_play_store/).

Create a Google Cloud project, enable the **Google Play Developer API**, and create
a service account. Invite that account's email through Play Console → Users and
permissions, granting access to this app and release permissions for the intended
tracks. Create a JSON key and save its contents as `PLAY_SERVICE_ACCOUNT_JSON`.
Production publication requires the corresponding production-release permission.
[Google's service-account setup](https://developers.google.com/android-publisher/getting_started).

## Release workflow

Push a version tag such as `v0.1.0` to build a signed release APK and AAB. Tag runs
publish to the Play **internal** track as a **draft** only when the repository
variable `PLAY_PUBLISH_ENABLED` is exactly `true`; otherwise they build artifacts.
Enable that variable after the initial Play app and credentials are ready:

```bash
gh variable set PLAY_PUBLISH_ENABLED --body true
```

For manual runs, the controls are:

| Input | Behavior |
|---|---|
| `variant` | `debug` or `release`; default `debug` |
| `version_name` | Optional `X.Y.Z` or `X.Y.Z-prerelease`; default `0.1.0` for manual runs, derived from the tag for tag runs |
| `version_code` | Optional integer; default `1000 + GITHUB_RUN_NUMBER`; must exceed every previously uploaded Play version |
| `publish_play` | Explicit Play upload; default `false`, requires `release` |
| `play_track` | `internal`, `beta`, or `production`; default `internal` |
| `play_release_status` | `draft` or `completed`; default `draft` |

The publishing job uses the GitHub environment `google-play`. Repository secrets
are available there; an environment-scoped `PLAY_SERVICE_ACCOUNT_JSON` can override
the repository secret if configured.

A draft is uploaded for completion in Play Console. A completed release is submitted
for the selected track; Play review, account eligibility, and track requirements
still apply. The lane uploads the AAB only, preserving existing metadata, release
notes, images, and screenshots. APK downloads from GitHub are signed with the upload
key; Play-distributed APKs use the Play app-signing key, so switching between those
install channels may require reinstalling if their signing certificates differ.

## Run the publishing lane locally

Use Ruby 3.3 and Bundler. `Gemfile` pins fastlane **2.240.1** and `Gemfile.lock`
locks its dependencies for the Linux runner and portable Ruby platform.
[Official fastlane release](https://github.com/fastlane/fastlane/releases/tag/2.240.1).

```bash
bundle install
AAB_PATH=/absolute/path/app-release.aab \
PLAY_JSON_KEY_PATH=/secure/path/play-service-account.json \
PLAY_TRACK=internal PLAY_RELEASE_STATUS=draft \
PACKAGE_NAME=com.scenedeck.android \
bundle exec fastlane android publish
```

`AAB_PATH` must name an existing `.aab`; `PLAY_JSON_KEY_PATH` must name a valid
service-account JSON file. Track, release status, and package name are validated
before the uploader runs. Credential contents are never printed by this lane.

The publishing contract can be checked without credentials or Google Play calls:

```bash
ruby -c fastlane/Fastfile
ruby fastlane/test_publish.rb
```

Actual signing and Play uploads require the owner's keys and Console permissions;
these are not created or supplied by the repository.
