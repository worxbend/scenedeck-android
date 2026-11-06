# UI theming workflow

Status: **complete — 2026-11-06**. User authorized both additional palettes and accent/true-black controls,
then explicitly requested a workflow. All checkpoints in plan.md are complete.
The completed operator workflow under `tasks/operator-workflow` is preserved.

## Scope and decisions

- Four original paired palettes: Ocean, Rosewood, Amber and Paper.
- Accent choices: palette default, Azure, Teal, Violet, Rose, Amber and Lime. Override
  primary/highlight roles, preserving program/preview/output/meter semantics.
- Optional true-black backgrounds in dark mode; retain layered readable control surfaces.
  Light mode is unchanged. Do not claim measured battery savings.
- A focused appearance editor with local preview, explicit Apply/Cancel, defaults reset,
  saved settings and visible sanitized failures. Failed saves retain the draft. Drafts
  survive rotation, but are never applied automatically after process interruption.
- Retain light/dark/system mode and wallpaper colors. Selecting a palette turns off
  wallpaper colors. Supported wallpaper schemes resolve before accents/true-black.
  High Contrast without wallpaper colors keeps its own accent/surface treatment.
- Appearance owns one atomic save of its five fields, comparing the opening values and
  preserving other settings. Remove appearance from the app shell's reverse preference
  writer; app state observes committed settings, so storage failure cannot imply success.
- No new libraries, OBS actions, global curation writes, font downloads or arbitrary
  imported themes. The user subsequently authorized pushing main and cutting a release;
  the published release is v0.2.2. Release verification also patches transitive Guava minima;
  see progress.md. Demo keeps its separate ephemeral settings.

Verification: JDK21/uv full quality gate, explicit normal build/tests, F-Droid build,
contrast assertions for new color combinations, persistence/restoration/failure tests,
phone/tablet/compact/large-text renders and representative runtime checks where available.
Keep planned work distinct from shipped behavior. No physical device/performance claims.

## Verified delivery

T0–T6 are complete: 1,169 tests / 203 suites, full JDK21/uv quality/security gate,
normal debug build/tests, F-Droid debug build and required renders pass. Emulator evidence
covers preview/Apply/Cancel, relaunch, rotation, both variants and demo isolation. The visible
emulator is connected to the user's local OBS and left for exploration. See progress.md.

All implementation commits are on main. [SceneDeck 0.2.2](https://github.com/worxbend/scenedeck-android/releases/tag/v0.2.2)
(code 1013) is published with signed APK/AAB and checksums. Main/tag CI, CodeQL, F-Droid
source/release verification and the signed-release workflow passed. Downloaded artifacts
match their checksums and the existing upload certificate; the APK's version is verified.
The completion record is a documentation-only follow-up to the verified release commit.
See progress.md for exact evidence and the remaining auxiliary dependency-graph alert;
this workflow does not claim zero repository-wide dependency advisories.
