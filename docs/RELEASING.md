# Building APKs and publishing to Google Play

The **Build and publish Android** workflow (`.github/workflows/release.yml`) builds
installable APKs and, for signed releases, an Android App Bundle for Google Play.
The application ID is `com.worxbend.scenedeck`. Kotlin namespaces remain
`com.scenedeck.android`; the installed package and Play identity use the application ID.

## Infrastructure and releases from Git

Use [infra/google-play](../infra/google-play/README.md) to provision the Google
publishing APIs, service account, and GitHub Workload Identity Federation with
Terraform. This uses temporary GitHub credentials; no Google service-account key
is needed. Set repository variables `PLAY_WORKLOAD_IDENTITY_PROVIDER` and
`PLAY_SERVICE_ACCOUNT_EMAIL` from Terraform outputs. Android signing still uses
the four signing secrets below.

The one-time Play Console work is account verification, app creation, first signed
bundle upload/App Signing, granting the publishing account app access, and policy
declarations. For new personal accounts, closed testing requires at least 12 testers
opted in continuously for 14 days before applying for production access. Testers
must actually test the app; meeting the duration does not guarantee approval.
[Google testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465).

After bootstrap, run releases entirely from GitHub CLI:

```bash
# Publish a build to the closed beta track after configuring it in Play Console.
gh workflow run release.yml -f variant=release -f version_name=0.1.0 \
  -f publish_play=true -f play_track=beta -f play_release_status=completed
```

Configure tag-driven releases once. These retain internal/draft defaults unless
you explicitly change the variables:

```bash
gh variable set PLAY_PUBLISH_ENABLED --body true
gh variable set PLAY_TAG_TRACK --body beta
gh variable set PLAY_TAG_RELEASE_STATUS --body completed
git tag v0.1.1
git push origin v0.1.1
```

After Google grants production access, set `PLAY_TAG_TRACK` to `production` to
submit subsequent tagged releases publicly. `PLAY_TAG_RELEASE_STATUS=completed`
submits releases rather than leaving drafts to complete in Console. Google review
and managed-publishing settings can still require Console action; API submission
does not guarantee immediate availability. Set any desired GitHub environment
protection on `google-play` independently.

## Store listing in Git

Fastlane can upload the listing, release notes, images, and screenshots alongside
the bundle. Import your initial listing with `bundle exec fastlane supply init`
using the package name and local credentials, then commit `fastlane/metadata/android`.
For example, with a local service-account key:

```bash
bundle exec fastlane supply init --package_name com.worxbend.scenedeck \
  --json_key /secure/path/play-service-account.json
```

Keep title/description translations under locale directories such as `en-US`;
release notes go in `en-US/changelogs/default.txt` or `<versionCode>.txt`. Images
go in `en-US/images/` (`icon.png`, `featureGraphic.png`, `phoneScreenshots/*.png`).
Review imported content before committing. Do not put credentials in the metadata.

Enable upload with manual input `upload_play_metadata: true`, or repository variable
`PLAY_TAG_UPLOAD_METADATA=true` for tags. The listing directory must contain at least
one locale with `title.txt`. Defaults preserve the existing Play listing. Uploaded
screenshots replace the corresponding existing sets, so commit complete sets.
Policy declarations, content rating, and privacy-policy setup are separate Console
tasks, not supplied by these listing files.
[Fastlane metadata layout](https://docs.fastlane.tools/actions/upload_to_play_store/).

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
| `PLAY_SERVICE_ACCOUNT_JSON` | Legacy service-account JSON fallback; unnecessary with Workload Identity Federation |

For the new key generated above, use the keystore password for
`ANDROID_KEY_PASSWORD` too; existing keystores may have a separate alias password.

The GitHub CLI can set them without putting passwords into shell arguments:

For a new key at `~/scenedeck-signing/scenedeck-upload.jks`, configure all four
signing secrets with one local hidden password prompt:

```bash
python3 scripts/configure-play-signing.py
```

The helper validates the keystore alias before uploading. Use `--keystore` and
`--alias` for a different key, or `--separate-key-password` for an alias password
that differs from the store password. Passwords are not saved to disk or printed.
Alternatively, set the secrets individually:

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

Create the app in Play Console for `com.worxbend.scenedeck`, enroll in Play App
Signing, and complete the required store listing and policy declarations. Build a
signed release with publication disabled and upload its AAB manually for the first
release. Fastlane requires an existing app with an initial binary upload before its
API upload workflow can be used. [Fastlane setup](https://docs.fastlane.tools/getting-started/android/setup/),
[initial-upload requirement](https://docs.fastlane.tools/actions/upload_to_play_store/).

Prefer the Terraform setup above. For legacy JSON-key authentication, create a Google Cloud project, enable the **Google Play Developer API**, and create
a service account. Invite that account's email through Play Console → Users and
permissions, granting access to this app and release permissions for the intended
tracks. Create a JSON key and save its contents as `PLAY_SERVICE_ACCOUNT_JSON`.
Production publication requires the corresponding production-release permission.
[Google's service-account setup](https://developers.google.com/android-publisher/getting_started).

## Release workflow

Push a version tag such as `v0.1.0` to build a signed release APK and AAB. Tag runs
publish to the configured Play track only when the repository
variable `PLAY_PUBLISH_ENABLED` is exactly `true`; otherwise they build artifacts.
`PLAY_TAG_TRACK` defaults to `internal`; `PLAY_TAG_RELEASE_STATUS` defaults to `draft`.
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
| `upload_play_metadata` | Upload the checked-in listing, notes and images; default `false` |

The publishing job uses the GitHub environment `google-play`. Repository secrets
are available there; environment variables and secrets can override repository
configuration. When `PLAY_WORKLOAD_IDENTITY_PROVIDER` is set, OIDC authenticates as
`PLAY_SERVICE_ACCOUNT_EMAIL`; otherwise the legacy JSON secret is used.

A draft is uploaded for completion in Play Console. A completed release is submitted
for the selected track; Play review, account eligibility, and track requirements
still apply. By default the lane uploads the AAB only; metadata upload is opt-in.
APK downloads from GitHub are signed with the upload
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
PACKAGE_NAME=com.worxbend.scenedeck \
bundle exec fastlane android publish
```

`AAB_PATH` must name an existing `.aab`; `PLAY_JSON_KEY_PATH` or
`GOOGLE_APPLICATION_CREDENTIALS` must name a valid service-account or external-account
JSON file. Track, release status, metadata opt-in, and package name are validated
before the uploader runs. Credential contents are never printed by this lane.

The publishing contract can be checked without credentials or Google Play calls:

```bash
ruby -c fastlane/Fastfile
ruby fastlane/test_publish.rb
```

Actual signing and Play uploads require the owner's keys and Console permissions;
these are not created or supplied by the repository.
