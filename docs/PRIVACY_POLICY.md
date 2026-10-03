# SceneDeck Android privacy policy

Effective date: October 3, 2026

SceneDeck Android (`com.worxbend.scenedeck`) is an open-source OBS Studio remote
control app maintained by Worxbend. This policy describes the app's data use.

## OBS connections and local storage

You provide an OBS server address, port, and optionally a password. The app connects
to the OBS server you select and exchanges control commands, scene information,
audio settings, output status, performance readings, and requested scene thumbnails.
This information is used to display and control your OBS session. SceneDeck does
not operate a developer-hosted server that receives your OBS session data.

Connection profiles, appearance settings, and scene curation are stored on your
device. OBS passwords are stored using Android Keystore-backed encryption. Local
OBS connections normally use `ws://`, which is not TLS encrypted. Network addresses,
passwords, and OBS content are not sent to the SceneDeck developer by the app.

## Camera and QR pairing

In the standard Play/GitHub distribution, camera access is optional and used to
scan OBS connection QR codes when you open the scanner. Barcode recognition runs
on the device using Google's ML Kit. The app
does not save camera frames or upload those frames to the SceneDeck developer.
You can enter connection details manually without granting camera permission.

## Distribution differences and Google components

The F-Droid build excludes QR scanning and ML Kit entirely. It requests no camera
permission and uses manual OBS connection entry. Starting with the F-Droid-prepared
0.1.1 source, fonts are bundled in both distributions and do not use a Google Play
services font provider. Earlier 0.1.0 Play/GitHub builds requested downloadable
fonts through Google Play services under the platform's and Google's privacy policies.

In the standard Play/GitHub build, ML Kit can send technical information to Google for diagnostics and usage analytics,
including device/app information, installation identifiers, feature events and
performance/error information. Google's documentation states that this SDK data is
sent using HTTPS. See [ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)
and [Google's privacy policy](https://policies.google.com/privacy).

SceneDeck does not include a developer-operated advertising or analytics service.

## Optional actions and permissions

Notifications and an optional foreground service support keeping your OBS connection
active in the background. Home-screen widgets display selected session information
on your device. When you export local curation metadata or share information,
the destination you choose receives that exported/shared information. Review it
before sharing; do not publish your OBS credentials.

## Retention and deletion

Local connection information remains until you delete the corresponding profile
or clear the app's storage. Clearing app storage or uninstalling removes the app's
local data. You can revoke camera and notification permissions in Android settings.
SceneDeck has no user-account registration and holds no developer-side account
records. For the standard build, Google determines retention for SDK data handled by its services; clearing
the app's local storage does not imply deletion of Google's service records.

## Contact and changes

For privacy questions, contact the maintainer through the
[SceneDeck project](https://github.com/worxbend/scenedeck-android/issues).
Do not include passwords or other private connection details in public issues.
Changes to this policy are published on this page with an updated effective date.
