# SceneDeck Android privacy policy

Effective date: November 5, 2026

SceneDeck Android (`com.worxbend.scenedeck`) is an open-source OBS Studio remote
control app maintained by Worxbend. This policy describes the app's data use.

## OBS connections and local storage

You provide an OBS server address, port, and optionally a password. The app connects
to the OBS server you select and exchanges control commands, scene information,
audio settings, output status, performance readings, and requested scene thumbnails.
This information is used to display and control your OBS session. SceneDeck does
not operate a developer-hosted server that receives your OBS session data.

Connection profiles, appearance settings, scene curation (including local scene labels),
named decks (names, membership, order and selection), local action preset definitions
(names and ordered control targets), keyboard/compatible-pedal shortcut definitions
(key chords, enabled state and explicit control targets), and preflight setup/manual
checklist confirmations are stored on your device. Shortcut definitions belong to a
connection profile and collection; recording a binding stores that chord, not a history
of your typing. Shortcuts have no global/background keyboard listener. Local timestamp bookmarks store their label,
connection-profile identity, collection name, device timestamp, and observed output
elapsed times/states/sample ages. They contain no credentials or recording file paths.
Preflight reports are held in memory for the current workspace; optional signal checks inspect OBS meter values, not microphone
recordings. Live warning detection, cooldowns, speech-monitoring consent and the alert-haptics
preference are held in process memory. Ongoing speech monitoring inspects shared OBS
meter values only after explicit consent; it does not capture or record your device's
microphone. Live alerts add no system notifications or uploads. OBS passwords are stored using Android Keystore-backed encryption. Local
OBS connections normally use `ws://`, which is not TLS encrypted. Network addresses,
passwords, and OBS content are not sent to the SceneDeck developer by the app.

Local History is enabled by default while SceneDeck observes a connected workspace.
It stores local connection-profile identity, collection and program scene names,
device timestamps, elapsed times, output states/counters, warning categories and known
observation gaps. It also associates bookmarks captured during an observation, using
their current saved labels/times. Preset run records can include the preset name and revision,
ordered scene/input/output targets, step results, typed stop reasons and timestamps. These
are local control records, not recordings. History contains no passwords, connection addresses,
raw protocol/server comments, output file paths, or audio/video content. It does not
record your device microphone. You can disable capture in More → History; saved history
remains available offline, and no developer-hosted history service receives this data.

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
on your device. When you export local curation, named decks, history/bookmarks, or share information,
the destination you choose receives that exported/shared information. Review it
before sharing; do not publish your OBS credentials.
History exports can include OBS names, bookmark labels and preset names/targets that you
consider private.
The document provider you choose may sync exported files according to its own settings.

## Offline demo

Demo runs local simulated OBS controls in a separate app process with in-memory settings
and storage. It does not use saved OBS credentials, connect to a real OBS server, access
a camera, create recordings, update widgets or operate the keep-alive service. A real
session opened elsewhere in SceneDeck can continue separately. Demo changes do not modify
live profiles, curation, bookmarks or history. Temporary demo data can survive re-entry
while its process remains alive; Android ending that process clears it. Explicit exports
save to the destination you choose and remain there until deleted. Demo History filenames
start with `demo-`, and exported observations identify the sample DEMO collection.

## Retention and deletion

Local connection information remains until you delete the corresponding profile
or clear the app's storage. Scene curation and deck definitions are retained when a
connection profile is deleted; deleting a deck in Manage decks removes that deck's
local definition and membership. Bookmarks remain when a profile is removed; you can
delete individual marks in Bookmarks. The newest 500 are retained, with older marks
removed automatically. Deleting a bookmark removes its history association, and label
edits appear in subsequent history reads/exports. History retains the newest 30
observations by default (configurable to 10 or 100), each with up to 500 history entries
and 50 preset run records. Preset and shortcut definitions survive connection-profile deletion.
Remove individual bindings from More → Shortcuts; disabled bindings retain their saved definitions.
More → Presets lets you delete a definition without deleting its past History results;
clearing app storage removes both.
You can delete individual observations or clear history under More → History. This
preserves independent bookmarks and does not disable future capture; use the capture
switch to stop recording new history. History remains after connection-profile deletion.
Exported backup/history files remain at the destination you
chose until you delete them there. Clearing app storage or uninstalling removes the app's
local data. You can revoke camera and notification permissions in Android settings.
SceneDeck has no user-account registration and holds no developer-side account
records. For the standard build, Google determines retention for SDK data handled by its services; clearing
the app's local storage does not imply deletion of Google's service records.

## Contact and changes

For privacy questions, contact the maintainer through the
[SceneDeck project](https://github.com/worxbend/scenedeck-android/issues).
Do not include passwords or other private connection details in public issues.
Changes to this policy are published on this page with an updated effective date.
