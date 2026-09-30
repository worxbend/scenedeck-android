# Code hardening

The hardening pipeline checks source reliability, security, maintainability and UI
regressions. It runs locally and in GitHub Actions.

## Run the pipeline

Use JDK 21, Android SDK 37, `uv`, `curl`, `tar` and a Linux x64 environment:

```bash
scripts/check-quality.sh
```

`./gradlew qualityCheck --no-configuration-cache` runs the Gradle portion separately.
`./gradlew ktfmtFormat` applies formatting. Reports appear in each module’s
`build/reports`, with secret/security reports in root `build/reports/security`.

Editor settings are shared via `.editorconfig` (mirrors the ktfmt kotlinlang style).
`scripts/install-git-hooks.sh` points git at `.githooks/`, installing a pre-commit
hook that runs `ktfmtCheck` and `detekt` when Kotlin sources are staged.

| Check | Gate |
|---|---|
| ktfmt 0.64 | Kotlin-style source must be formatted |
| Detekt 1.23.8 | Default rules plus cognitive and cyclomatic complexity; no baseline |
| Android Lint | All Android modules; warnings treated as errors |
| Unit and UI previews | Regression tests plus verified Roborazzi goldens |
| Build | Debug APK must assemble |
| Semgrep 1.156.0 | Checked-in Kotlin/security patterns, local execution, metrics disabled |
| Gitleaks 8.30.1 | Redacted working-tree and full Git-history scans |
| GitHub CodeQL | Kotlin/Java security-extended analysis on push/PR and weekly |
| Dependabot | Weekly Gradle and Actions update proposals |
| CI pipeline | Parallel jobs: wrapper validation, static analysis, tests, APK build, security scans, PR dependency review; detekt/Semgrep/Gitleaks SARIF published to code scanning |

The pipeline pins Detekt’s JVM target to 17 and runs on JDK 21, avoiding its embedded
compiler’s incompatibility with JDK 25. Dependency constraints align application and
instrumentation runtime versions so Android Lint can resolve test dependencies.

## Fixed findings

| Area | Result |
|---|---|
| Coroutine cancellation | Best-effort request helpers preserve cancellation rather than converting it to failures |
| Doctor | Successful audio probes no longer become broken-input warnings; failed refreshes retain prior results |
| Mixer | Obsolete discoveries cancel, latest selection wins, and media state exists before collectors start |
| Settings and locks | Transformations execute atomically in DataStore; concurrent changes do not overwrite one another |
| Repository reducers | StateFlow updates are atomic; disconnect clears studio metadata |
| Stats | UI snapshots combine telemetry and history consistently; invalid history capacity and bitrate overflow are guarded |
| Connection sessions | Commands cancel/join prior preparation, failures are observable and sanitized, explicit actions supersede auto-connect |
| Password state | Drafts stay outside saved-state Bundles; secret-bearing model strings redact credentials |
| Credential storage | Keystore writes check durable persistence on IO; failed profile writes attempt rollback, preserve form drafts and expose sanitized errors |
| Networking and camera | Pending connects and owned clients close on cancellation; QR camera releases owned use cases and ignores stale callbacks |
| Pairing and deep links | Invalid authorities/ports, duplicate parameters, unexpected paths/fragments, oversized URIs and NUL names are rejected |
| Widgets | Scene counts are bounded before allocation; updates serialize and removed-widget failures do not kill the collector |
| Background service | Correct Android 15 dataSync timeout callback, API-28-compatible exception handling, guarded foreground type and notification permission |
| Backups | Legacy backups disabled; Android 12+ cloud backup and device transfer explicitly exclude application data |
| UI structure | Large scene, mixer, transport, profile and app-shell functions split into focused helpers; unused embedded mixer paths removed |
| Platform APIs | API-28-compatible URL decoding; current pinned sheet and slider APIs replace deprecated production calls |

Regression tests exercise cancellation, password restoration/redaction, invalid pairing
and widget input, concurrent preferences, pending connection replacement, mixer selection,
Doctor probes, telemetry and output UI states.

## Complexity and reviewed exceptions

Both cognitive and cyclomatic complexity checks include Compose. The configured
threshold is **15**; functions reaching it must be refactored. Initial cognitive
findings included Live deck **88**, Mixer strip **82**, Scene card **73**, Transport
**60**, connection form **38** and app shell **31**. Those findings were eliminated
through helper extraction and removal of obsolete paths, without complexity suppressions.

Declarative Compose function length/parameter counts retain the repository’s existing
UI-specific allowances. Magic numbers remain exempt for visual tokens. The centralized
recoverable-error boundary catches Exception with a documented local suppression and
rethrows CancellationException.

Android Lint’s upgrade suggestions are scoped to version-catalog/toolchain pins and
the documented target SDK; upgrades require compatibility verification and updating
TECH_STACK.md. The adaptive-icon API qualifier is retained because removing it breaks
AAPT resource linking on the pinned toolchain. Certificate arrays exclude typo analysis
because their contents are opaque encoded certificates. No broad lint baseline is used.

## Security scan scope

Local Semgrep rules cover insecure TLS trust, password saved state, credential logging,
unowned coroutine scopes, JavaScript-enabled WebViews, weak ciphers and prohibited
OBS collection-mutation requests. An additional Kotlin registry-rule scan was run.
Gitleaks scans the repository history and working tree before publication.

CodeQL uses a manual compilation so Kotlin is analyzed, following
[GitHub’s compiled-language guidance](https://docs.github.com/en/code-security/concepts/code-scanning/codeql/codeql-for-compiled-languages).
Its hosted result is separate from the local checks. Passing the configured scans
cannot prove the absence of every defect or vulnerability.

## UI evidence

Output previews cover idle Stream, active red Stop, recording, studio preview,
reduced motion and light-theme recording. The README tour uses rendered fixture data;
no broadcast was started to produce it. Regenerate it after recording previews with:

```bash
python3 scripts/render-readme-tour.py
```

The tour script requires ffmpeg and the DejaVu Sans font at the documented Linux path.

## Verified results — 2026-09-30

The local quality pipeline passed: debug assembly, 324 tests with zero failures,
Android Lint, Detekt (cognitive and cyclomatic thresholds of 15), strict Semgrep,
and Gitleaks history/working-tree scans. Updated Compose screens rendered through
Roborazzi previews. Hosted CodeQL results are reported separately by GitHub Actions.

Replay Buffer was removed from Android scope: controls, actions, telemetry polling,
protocol methods/events and fixtures. A menu regression check verifies that replay
actions are absent; virtual camera protocol coverage remains.

The subsequent [codebase review](REVIEW_2026-09-30.md) documents additional
session, telemetry, interaction and CI fixes accompanying the persistent status bar.

Persistent-bar and review verification: **378 tests, zero failures**, debug assembly,
Android Lint, formatting, Detekt complexity checks, strict Semgrep and Gitleaks all
passed. Updated app/design-system/mixer previews rendered successfully.
